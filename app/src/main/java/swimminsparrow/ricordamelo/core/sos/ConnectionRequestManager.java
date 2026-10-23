package swimminsparrow.ricordamelo.core.sos;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.config.Constants;
import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.core.utils.CryptoUtils;
import swimminsparrow.ricordamelo.ui.BaseActivity;
import swimminsparrow.ricordamelo.ui.alarms.MainActivity;
import swimminsparrow.ricordamelo.ui.common.Dialogs.DialogBuilder;

public class ConnectionRequestManager {

    private static final String TAG = "ConnectionRequestMgr";
    public static final String ACTION_CONNECTION_REQUEST_RECEIVED = "swimminsparrow.ricordamelo.ACTION_CONNECTION_REQUEST_RECEIVED";

    public static final int NOTIFICATION_ID_CONNECTION_REQ = 1007;
    public static final int NOTIFICATION_ID_PAIRING_WAIT = 1008;
    public static final long CONNECTION_REQUEST_VALIDITY_MS = 120000L; // 120 seconds validity window

    private static boolean isListeningActive = false;
    private static Thread listenThread = null;
    private static boolean isApprovalDialogShowing = false;
    private static DialogBuilder activeApprovalDialog = null;
    private static ConnectionRequest activePendingRequest = null;
    private static Runnable autoDismissRunnable = null;
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static synchronized void dismissActiveApprovalDialog() {
        if (autoDismissRunnable != null) {
            mainHandler.removeCallbacks(autoDismissRunnable);
            autoDismissRunnable = null;
        }
        if (activeApprovalDialog != null) {
            try {
                activeApprovalDialog.dismiss();
            } catch (Exception ignored) {}
            activeApprovalDialog = null;
        }
        isApprovalDialogShowing = false;
        activePendingRequest = null;
    }

    public static boolean isRequestExpired(long requestTs) {
        if (requestTs <= 0) return false;
        return (System.currentTimeMillis() - requestTs) > CONNECTION_REQUEST_VALIDITY_MS;
    }

    public static void cancelConnectionNotification(Context context) {
        if (context == null) return;
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_CONNECTION_REQ);
        } catch (Exception ignored) {}
    }

    public static void postConnectionRequestNotification(Context context, ConnectionRequest request) {
        if (context == null || request == null) return;
        try {
            String channelId = Constants.SOS_CHANNEL_ID;
            String title = context.getString(R.string.sos_connection_request_title);
            String message = context.getString(R.string.sos_connection_request_desc, request.name);

            Intent contentIntent = new Intent(context, MainActivity.class);
            contentIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            request.toIntent(contentIntent);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID_CONNECTION_REQ, contentIntent, flags);

            Intent approveIntent = new Intent(context, ConnectionRequestActionReceiver.class);
            approveIntent.setAction(ConnectionRequestActionReceiver.ACTION_APPROVE);
            request.toIntent(approveIntent);
            PendingIntent approvePendingIntent = PendingIntent.getBroadcast(context, NOTIFICATION_ID_CONNECTION_REQ + 1, approveIntent, flags);

            Intent rejectIntent = new Intent(context, ConnectionRequestActionReceiver.class);
            rejectIntent.setAction(ConnectionRequestActionReceiver.ACTION_REJECT);
            request.toIntent(rejectIntent);
            PendingIntent rejectPendingIntent = PendingIntent.getBroadcast(context, NOTIFICATION_ID_CONNECTION_REQ + 2, rejectIntent, flags);

            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            long[] vibPattern = new long[]{0, 400, 200, 400};

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.icon)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setSound(soundUri)
                    .setVibrate(vibPattern)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .addAction(R.drawable.ic_check_circle_24, context.getString(R.string.btn_approve), approvePendingIntent)
                    .addAction(R.drawable.ic_close_white_24dp, context.getString(R.string.btn_reject), rejectPendingIntent);

            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_CONNECTION_REQ, builder.build());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error posting connection request notification", e);
        }
    }

    public static void postWaitingForApprovalNotification(Context context, String targetName, String reqId) {
        if (context == null) return;
        try {
            String channelId = Constants.SERVICE_CHANNEL_ID;
            String title = context.getString(R.string.sos_connecting_notif_title);
            String message = context.getString(R.string.sos_waiting_for_approval_notif, targetName != null ? targetName : "");

            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

            Intent contentIntent = new Intent(context, MainActivity.class);
            contentIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent contentPendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID_PAIRING_WAIT, contentIntent, flags);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.icon)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
                    .setOngoing(true)
                    .setProgress(0, 0, true)
                    .setContentIntent(contentPendingIntent);

            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PAIRING_WAIT, builder.build());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error posting waiting notification", e);
        }
    }

    public static void cancelWaitingNotification(Context context) {
        if (context == null) return;
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PAIRING_WAIT);
            PinPairingManager.stopApprovalListening();
        } catch (Exception ignored) {}
    }

    public static void postApprovalResultNotification(Context context, String title, String message) {
        if (context == null) return;
        try {
            String channelId = Constants.SOS_CHANNEL_ID;

            Intent contentIntent = new Intent(context, MainActivity.class);
            contentIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent contentPendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID_PAIRING_WAIT + 2, contentIntent, flags);

            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            long[] vibPattern = new long[]{0, 300, 150, 300};

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.icon)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setSound(soundUri)
                    .setVibrate(vibPattern)
                    .setAutoCancel(true)
                    .setContentIntent(contentPendingIntent);

            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PAIRING_WAIT, builder.build());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error posting result notification", e);
        }
    }

    public static void acceptRequest(Context context, ConnectionRequest request) {
        if (context == null || request == null) return;
        cancelConnectionNotification(context);
        dismissActiveApprovalDialog();

        if (request.isExpired()) {
            SOSContactsManager.markRequestHandled(context, request.topic, request.timestamp);
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, R.string.sos_request_expired, Toast.LENGTH_SHORT).show());
            return;
        }

        SOSContactsManager.markRequestHandled(context, request.topic, request.timestamp);
        SOSContactsManager.addMutualContact(context, request.topic, request.name, request.key, request.avatar);
        SOSManager.startServices(context);
        PinPairingManager.sendApprovalResponse(context, request.topic, true, request.key, request.reqId);

        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, context.getString(R.string.sos_connection_approved_for, request.name), Toast.LENGTH_SHORT).show());
    }

    public static void rejectRequest(Context context, ConnectionRequest request) {
        if (context == null || request == null) return;
        cancelConnectionNotification(context);
        dismissActiveApprovalDialog();

        SOSContactsManager.markRequestHandled(context, request.topic, request.timestamp);
        PinPairingManager.sendApprovalResponse(context, request.topic, false, request.key, request.reqId);

        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, R.string.sos_request_rejected_toast, Toast.LENGTH_SHORT).show());
    }

    private static String getLastRequestMessageId(Context context, String topic) {
        if (context == null || topic == null) return null;
        return context.getSharedPreferences("ntfy_prefs", Context.MODE_PRIVATE)
                .getString("last_req_msg_id_" + topic, null);
    }

    private static void saveLastRequestMessageId(Context context, String topic, String msgId) {
        if (context == null || topic == null || msgId == null) return;
        context.getSharedPreferences("ntfy_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("last_req_msg_id_" + topic, msgId)
                .apply();
    }

    public static synchronized void startListening(Context context) {
        if (context == null) return;
        final Context appContext = context.getApplicationContext();
        String myUuid = SettingsDB.getInstance(appContext).getUserUUID();
        if (myUuid == null || myUuid.isEmpty()) return;

        if (isListeningActive) return;
        isListeningActive = true;

        listenThread = new Thread(() -> {
            while (isListeningActive) {
                HttpURLConnection conn = null;
                try {
                    String reqTopic = "pillsos_req_" + myUuid;
                    String lastId = getLastRequestMessageId(appContext, reqTopic);
                    String endpoint = (lastId != null && !lastId.trim().isEmpty())
                            ? reqTopic + "/json?since=" + lastId
                            : reqTopic + "/json?since=all";

                    URL url = new URL(Constants.getNtfyTopicUrl(endpoint));
                    conn = (HttpURLConnection) url.openConnection();
                    swimminsparrow.ricordamelo.core.utils.ForceIPv4SSLSocketFactory.applyTo(conn);
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept", "text/event-stream");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(0); // Infinite read timeout for SSE stream

                    try (InputStream in = conn.getInputStream();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
                        String line;

                        Log.d(TAG, "Connected to connection request SSE stream: " + reqTopic + " (endpoint: " + endpoint + ")");

                        while (isListeningActive && (line = reader.readLine()) != null) {
                        if (line.trim().isEmpty()) continue;
                        try {
                            JSONObject json = new JSONObject(line);
                            if ("message".equals(json.optString("event"))) {
                                String msgId = json.optString("id");
                                if (msgId != null && !msgId.trim().isEmpty()) {
                                    String currentLastId = getLastRequestMessageId(appContext, reqTopic);
                                    if (msgId.equals(currentLastId)) {
                                        continue;
                                    }
                                    saveLastRequestMessageId(appContext, reqTopic, msgId);
                                }

                                String message = PinPairingManager.extractMessageFromJson(json);
                                if (message != null) {
                                    String myKey = SettingsDB.getInstance(appContext).getSecurePairingKey();
                                    String decrypted = CryptoUtils.decrypt(message, myKey);
                                    JSONObject reqJson = new JSONObject(decrypted != null ? decrypted : message);

                                    ConnectionRequest req = ConnectionRequest.fromJson(reqJson, myKey);
                                    if (req != null) {
                                        if (SOSContactsManager.isContactPaired(appContext, req.topic)) {
                                            handleAlreadyConnected(appContext, req);
                                        } else if (req.isExpired()) {
                                            SOSContactsManager.markRequestHandled(appContext, req.topic, req.timestamp);
                                        } else if (!SOSContactsManager.isRequestHandled(appContext, req.topic, req.timestamp)) {
                                            notifyRequestReceived(appContext, req);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing connection request SSE stream line", e);
                        }
                    }
                }

                } catch (Exception e) {
                    Log.d(TAG, "Connection request stream disconnected, reconnecting...");
                } finally {
                    if (conn != null) conn.disconnect();
                }

                if (isListeningActive) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {}
                }
            }
        });
        listenThread.start();
    }

    public static void stopListening() {
        isListeningActive = false;
        if (listenThread != null) {
            listenThread.interrupt();
            listenThread = null;
        }
    }

    public static void notifyRequestReceived(Context context, ConnectionRequest request) {
        if (context == null || request == null) return;
        try {
            Intent intent = new Intent(ACTION_CONNECTION_REQUEST_RECEIVED);
            intent.setPackage(context.getPackageName());
            request.toIntent(intent);
            context.sendBroadcast(intent);

            // If app is in background or screen is locked, alert user via heads-up notification
            if (!BaseActivity.isForeground) {
                postConnectionRequestNotification(context, request);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending connection request broadcast", e);
        }
    }

    public static void checkPendingRequests(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || isApprovalDialogShowing) return;
        PinPairingManager.checkForConnectionRequests(activity, req -> {
            if (activity == null || activity.isFinishing() || activity.isDestroyed() || isApprovalDialogShowing) return;
            if (req.isExpired()) {
                SOSContactsManager.markRequestHandled(activity, req.topic, req.timestamp);
                cancelConnectionNotification(activity);
                return;
            }
            showApprovalDialog(activity, req);
        });
    }

    private static void handleAlreadyConnected(Context context, ConnectionRequest req) {
        if (context == null || req == null) return;
        SOSContact existing = SOSContactsManager.getContactByTopic(context, req.topic);
        long addedTs = existing != null ? existing.getAddedTimestamp() : req.timestamp;
        SOSContact updated = new SOSContact(
                req.topic, req.name, req.topic, req.key, "MUTUAL", "APPROVED", addedTs, System.currentTimeMillis(), "", 0, req.avatar);
        SOSContactsManager.addContact(context, updated);
        SOSManager.startServices(context);

        PinPairingManager.sendApprovalResponse(context, req.topic, "ALREADY_CONNECTED", req.key, req.reqId);
        SOSContactsManager.markRequestHandled(context, req.topic, req.timestamp);
    }

    public static synchronized void showApprovalDialog(Activity activity, ConnectionRequest req) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || req == null) return;

        // If an approval dialog is already showing:
        if (isApprovalDialogShowing) {
            if (activePendingRequest != null) {
                // If the incoming request is newer or has a different req_id, supersede the old one!
                boolean isNewer = req.timestamp > activePendingRequest.timestamp
                        || (req.reqId != null && !req.reqId.equals(activePendingRequest.reqId));
                if (isNewer) {
                    Log.d(TAG, "Superseding older pending request (" + activePendingRequest.reqId + ") with newer request (" + req.reqId + ")");
                    SOSContactsManager.markRequestHandled(activity, activePendingRequest.topic, activePendingRequest.timestamp);
                    dismissActiveApprovalDialog();
                } else {
                    return; // Same or older request already showing
                }
            } else {
                dismissActiveApprovalDialog();
            }
        }

        if (SOSContactsManager.isContactPaired(activity, req.topic)) {
            handleAlreadyConnected(activity, req);
            cancelConnectionNotification(activity);
            return;
        }

        if (req.isExpired()) {
            SOSContactsManager.markRequestHandled(activity, req.topic, req.timestamp);
            cancelConnectionNotification(activity);
            Toast.makeText(activity, R.string.sos_request_expired, Toast.LENGTH_SHORT).show();
            return;
        }

        if (SOSContactsManager.isRequestHandled(activity, req.topic, req.timestamp)) return;

        isApprovalDialogShowing = true;
        activePendingRequest = req;
        cancelConnectionNotification(activity);

        // Schedule auto-dismiss when the request validity window expires
        long elapsed = System.currentTimeMillis() - req.timestamp;
        long remainingMs = Math.max(2000L, CONNECTION_REQUEST_VALIDITY_MS - elapsed);
        if (autoDismissRunnable != null) {
            mainHandler.removeCallbacks(autoDismissRunnable);
        }
        autoDismissRunnable = () -> {
            synchronized (ConnectionRequestManager.class) {
                if (activePendingRequest != null && activePendingRequest.reqId != null && activePendingRequest.reqId.equals(req.reqId)) {
                    Log.d(TAG, "Request validity expired, auto-dismissing approval dialog: " + req.reqId);
                    SOSContactsManager.markRequestHandled(activity, req.topic, req.timestamp);
                    dismissActiveApprovalDialog();
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        Toast.makeText(activity, R.string.sos_request_expired, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        };
        mainHandler.postDelayed(autoDismissRunnable, remainingMs);

        try {
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(activity.getApplicationContext(), soundUri);
            if (r != null) r.play();
            Vibrator v = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(new long[]{0, 250, 150, 250}, -1));
                } else {
                    v.vibrate(new long[]{0, 250, 150, 250}, -1);
                }
            }
        } catch (Exception ignored) {}

        DialogBuilder dialogBuilder = new DialogBuilder(activity, DialogBuilder.Type.APPROVAL)
                .setTitleText(activity.getString(R.string.sos_connection_request_title))
                .setContentText(activity.getString(R.string.sos_connection_request_desc, req.name))
                .setConfirmText(activity.getString(R.string.btn_approve))
                .setCancelText(activity.getString(R.string.btn_reject))
                .setCancelable(false)
                .setConfirmClickListener(dialog -> {
                    if (autoDismissRunnable != null) {
                        mainHandler.removeCallbacks(autoDismissRunnable);
                        autoDismissRunnable = null;
                    }
                    isApprovalDialogShowing = false;
                    activeApprovalDialog = null;
                    activePendingRequest = null;
                    acceptRequest(activity, req);
                })
                .setCancelClickListener(dialog -> {
                    if (autoDismissRunnable != null) {
                        mainHandler.removeCallbacks(autoDismissRunnable);
                        autoDismissRunnable = null;
                    }
                    isApprovalDialogShowing = false;
                    activeApprovalDialog = null;
                    activePendingRequest = null;
                    rejectRequest(activity, req);
                });
        activeApprovalDialog = dialogBuilder;
        dialogBuilder.show();
    }
}
