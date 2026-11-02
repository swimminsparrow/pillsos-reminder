package swimminsparrow.ricordamelo.core.notification;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import android.view.View;
import android.widget.RemoteViews;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.config.Constants;
import swimminsparrow.ricordamelo.core.data.dao.FarmacoDao;
import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.core.data.models.Farmaco;
import swimminsparrow.ricordamelo.core.sos.ConnectionRequest;
import swimminsparrow.ricordamelo.core.sos.ConnectionRequestActionReceiver;
import swimminsparrow.ricordamelo.core.sos.PinPairingManager;
import swimminsparrow.ricordamelo.core.utils.AlarmRinger;
import swimminsparrow.ricordamelo.core.utils.PendingIntentHelper;
import swimminsparrow.ricordamelo.core.utils.SpockLogger;
import swimminsparrow.ricordamelo.core.utils.VolumeUtils;
import swimminsparrow.ricordamelo.services.FallAlertReceiver;
import swimminsparrow.ricordamelo.services.MissedAlarmReceiver;
import swimminsparrow.ricordamelo.services.NotificationDismissReceiver;
import swimminsparrow.ricordamelo.services.ReminderReceiver;
import swimminsparrow.ricordamelo.services.SnoozeReceiver;
import swimminsparrow.ricordamelo.ui.alarms.FullAlarmActivity;
import swimminsparrow.ricordamelo.ui.alarms.MainActivity;
import swimminsparrow.ricordamelo.ui.sos.FallAlertActivity;

/**
 * Centralized manager for application notifications.
 * Encapsulates notification construction, channel selection, ID allocation,
 * and runtime permission checks.
 */
public class AppNotificationManager {

    private static final String TAG = "AppNotificationManager";

    // Unique Notification IDs to prevent collisions
    public static final int ID_SOS_ALERT = 1001;
    public static final int ID_IM_OK = 1002;
    public static final int ID_PUBLISH_RESULT = 1005;
    public static final int ID_OUTBOX_DELIVERED = 1006;

    // Schema: Attesa 0ms -> Vibra 800ms -> Pausa 4000ms (sincronizzato con
    // AlarmRinger per ritmo lento e costante)
    public static final long[] VIBRATION_PATTERN = new long[] { 0, 1000, 4000 };

    private static volatile AppNotificationManager instance;

    public static AppNotificationManager getInstance() {
        if (instance == null) {
            synchronized (AppNotificationManager.class) {
                if (instance == null) {
                    instance = new AppNotificationManager();
                }
            }
        }
        return instance;
    }

    private AppNotificationManager() {
    }

    /**
     * Creates and registers all notification channels required by the application.
     */
    public void createNotificationChannels(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null)
                return;

            // Clean up legacy channels so old settings are not retained.
            try {
                manager.deleteNotificationChannel("pillsos_service_channel");
                manager.deleteNotificationChannel("pillsos_alarms_channel");
                manager.deleteNotificationChannel("pillsos_alarms_channel_v2");
            } catch (Exception ignored) {
            }

            // 1. Silent - for persistent foreground services (no badge on launcher icon)
            NotificationChannel serviceChannel = new NotificationChannel(
                    Constants.SERVICE_CHANNEL_ID,
                    context.getString(R.string.notification_channel_service),
                    NotificationManager.IMPORTANCE_LOW);
            serviceChannel.setShowBadge(false);
            serviceChannel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);

            // 2. Max priority - for medication alarms (visual pop-up; sound & vibration
            // handled exclusively by AlarmRinger)
            NotificationChannel alarmChannel = new NotificationChannel(
                    Constants.ALARM_CHANNEL_ID,
                    context.getString(R.string.notification_channel_reminders),
                    NotificationManager.IMPORTANCE_MAX);
            alarmChannel.setShowBadge(true);
            alarmChannel.enableVibration(false); // Vibration is fully handled by AlarmRinger to maintain consistent
                                                 // slow loop
            alarmChannel.setVibrationPattern(null);
            alarmChannel.setSound(null, null); // Sound handled by AlarmRinger to prevent double sound and system
                                               // insistent vibration
            alarmChannel.setBypassDnd(true);
            alarmChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            // 3. Max priority - for incoming emergency SOS alerts (siren handled by
            // AlarmRinger MediaPlayer)
            NotificationChannel sosEmergencyChannel = new NotificationChannel(
                    Constants.SOS_EMERGENCY_CHANNEL_ID,
                    context.getString(R.string.notification_channel_sos_emergency),
                    NotificationManager.IMPORTANCE_MAX);
            sosEmergencyChannel.setShowBadge(true);
            sosEmergencyChannel.enableVibration(false); // Vibration is fully handled by AlarmRinger to maintain
                                                        // consistent slow loop
            sosEmergencyChannel.setVibrationPattern(null);
            sosEmergencyChannel.setSound(null, null); // Audio handled by AlarmRinger MediaPlayer to avoid double sound
            sosEmergencyChannel.setBypassDnd(true);
            sosEmergencyChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            // 4. SOS & Connection Requests - High priority with sound, vibration, and
            // heads-up banner
            NotificationChannel sosChannel = new NotificationChannel(
                    Constants.SOS_CHANNEL_ID,
                    context.getString(R.string.sos_channel_name),
                    NotificationManager.IMPORTANCE_HIGH);
            sosChannel.setShowBadge(true);
            sosChannel.enableVibration(true);
            sosChannel.setVibrationPattern(VIBRATION_PATTERN);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            sosChannel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttributes);
            sosChannel.setBypassDnd(true);
            sosChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            // 5. Fall Detection Pre-Alarm - Emergency full-screen channel
            NotificationChannel fallChannel = new NotificationChannel(
                    Constants.FALL_CHANNEL_ID,
                    context.getString(R.string.fall_alert_title),
                    NotificationManager.IMPORTANCE_MAX);
            fallChannel.setShowBadge(true);
            fallChannel.enableVibration(false); // Vibration handled by FallAlertController
            fallChannel.setSound(null, null); // Audio handled by FallAlertController MediaPlayer
            fallChannel.setBypassDnd(true);
            fallChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            manager.createNotificationChannel(serviceChannel);
            manager.createNotificationChannel(alarmChannel);
            manager.createNotificationChannel(sosEmergencyChannel);
            manager.createNotificationChannel(sosChannel);
            manager.createNotificationChannel(fallChannel);
        }
    }

    /**
     * Checks if notification permission is granted.
     */
    public boolean hasNotificationPermission(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /**
     * Safely dispatches a notification checking for permissions and handling
     * exceptions.
     */
    public void safeNotify(@NonNull Context context, int notificationId, @NonNull Notification notification) {
        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "Notification permission not granted, skipping notification id: " + notificationId);
            return;
        }
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification);
        } catch (SecurityException se) {
            Log.e(TAG, "SecurityException while notifying id: " + notificationId, se);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error notifying id: " + notificationId, e);
        }
    }

    /**
     * Cancels an active notification by ID.
     */
    public void cancel(@NonNull Context context, int notificationId) {
        if (notificationId == ID_SOS_ALERT) {
            clearActiveSos();
        }
        try {
            NotificationManagerCompat.from(context).cancel(notificationId);
        } catch (Exception e) {
            Log.e(TAG, "Error canceling notification id: " + notificationId, e);
        }
    }

    /**
     * Builds the standardized custom RemoteViews banner used for both Medication
     * and Received SOS alarms.
     * Displays title, message/time, divider line, dismiss ("Spegni") button, and
     * optionally snooze ("Posponi") button.
     */
    public RemoteViews buildAlarmRemoteViews(@NonNull Context context,
            @NonNull String title,
            @NonNull String messageOrTime,
            @NonNull PendingIntent dismissPendingIntent,
            @Nullable PendingIntent snoozePendingIntent) {
        RemoteViews customView = new RemoteViews(context.getPackageName(), R.layout.notification_alarm_popup);
        customView.setTextViewText(R.id.notification_alarm_title, title);
        customView.setTextViewText(R.id.notification_alarm_time, messageOrTime);
        customView.setTextViewText(R.id.btn_notification_dismiss, context.getString(R.string.alarm_dismiss));

        customView.setOnClickPendingIntent(R.id.btn_notification_dismiss_container, dismissPendingIntent);
        customView.setOnClickPendingIntent(R.id.btn_notification_dismiss, dismissPendingIntent);

        if (snoozePendingIntent != null) {
            customView.setTextViewText(R.id.btn_notification_snooze, context.getString(R.string.snooze));
            customView.setViewVisibility(R.id.btn_notification_snooze_container, View.VISIBLE);
            customView.setViewVisibility(R.id.btn_notification_snooze, View.VISIBLE);
            customView.setOnClickPendingIntent(R.id.btn_notification_snooze_container, snoozePendingIntent);
            customView.setOnClickPendingIntent(R.id.btn_notification_snooze, snoozePendingIntent);
        } else {
            customView.setViewVisibility(R.id.btn_notification_snooze_container, View.GONE);
            customView.setViewVisibility(R.id.btn_notification_snooze, View.GONE);
        }
        return customView;
    }

    private volatile String activeSosUsername = null;
    private volatile String activeSosTopic = null;
    private volatile String activeSosMessage = null;

    public void clearActiveSos() {
        this.activeSosUsername = null;
        this.activeSosTopic = null;
        this.activeSosMessage = null;
    }

    public String getActiveSosUsername() {
        return activeSosUsername;
    }

    public String getActiveSosTopic() {
        return activeSosTopic;
    }

    public String getActiveSosMessage() {
        return activeSosMessage;
    }

    /**
     * High-priority SOS alert notification with full-screen intent and dismiss
     * action.
     */
    public void showSosAlert(@NonNull Context context, @Nullable String username, @Nullable String topic,
            @NonNull String message) {
        showSosAlert(context, username, topic, message, false);
    }

    public void showSosAlert(@NonNull Context context, @Nullable String username, @Nullable String topic,
            @Nullable String message, boolean headsUpOnly) {
        if (username != null && !username.trim().isEmpty()) {
            this.activeSosUsername = username.trim();
        }
        if (topic != null && !topic.trim().isEmpty()) {
            this.activeSosTopic = topic.trim();
        }
        if (message != null && !message.trim().isEmpty()) {
            this.activeSosMessage = message.trim();
        }

        String effectiveUsername = this.activeSosUsername != null ? this.activeSosUsername : username;
        String effectiveTopic = this.activeSosTopic != null ? this.activeSosTopic : topic;
        String effectiveMessage = (this.activeSosMessage != null && !this.activeSosMessage.isEmpty())
                ? this.activeSosMessage
                : ((message != null && !message.trim().isEmpty()) ? message.trim()
                        : context.getString(R.string.sos_detected_title));

        String channelId = Constants.SOS_EMERGENCY_CHANNEL_ID;

        String title = (effectiveUsername != null && !effectiveUsername.isEmpty())
                ? effectiveUsername
                : context.getString(R.string.sos_detected_title);

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT |
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);

        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("username", effectiveUsername);
        if (effectiveTopic != null) {
            intent.putExtra("topic", effectiveTopic);
        }
        intent.putExtra("message", effectiveMessage);
        intent.putExtra("open_sos", true);
        intent.putExtra("is_sos_alert", true);
        intent.putExtra("skip_splash", true);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        android.os.Bundle optionsBundle = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            android.app.ActivityOptions options = android.app.ActivityOptions.makeBasic();
            options.setPendingIntentCreatorBackgroundActivityStartMode(
                    android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            optionsBundle = options.toBundle();
        }

        PendingIntent tapPendingIntent = PendingIntent.getActivity(
                context, ID_SOS_ALERT, intent, pendingFlags, optionsBundle);

        // Dismiss action ("Spegni")
        Intent dismissIntent = new Intent(context, NotificationDismissReceiver.class);
        dismissIntent.putExtra("ID", ID_SOS_ALERT);
        PendingIntent dismissPendingIntent = PendingIntent.getBroadcast(
                context, ID_SOS_ALERT * 10 + 1, dismissIntent, pendingFlags);

        // Intercept swipe dismiss to ensure SOS notification remains active if user
        // attempts to swipe it away
        Intent swipeIntent = new Intent(context, ReminderReceiver.class);
        swipeIntent.setAction(ReminderReceiver.ACTION_SOS_NOTIFICATION_SWIPED);
        swipeIntent.putExtra("username", effectiveUsername);
        swipeIntent.putExtra("topic", effectiveTopic);
        swipeIntent.putExtra("message", effectiveMessage);
        PendingIntent swipePendingIntent = PendingIntent.getBroadcast(
                context, ID_SOS_ALERT * 10 + 4, swipeIntent, pendingFlags);

        // Build Custom RemoteViews for Alarm Pop-up Banner & Drawer (matching pill
        // alarm style without snooze)
        RemoteViews customView = buildAlarmRemoteViews(context, title, effectiveMessage, dismissPendingIntent, null);

        // Start repeating alarm sound, vibration, volume control, and duration
        // auto-dismiss timing
        AlarmRinger.getInstance().start(context, ID_SOS_ALERT);

        showMaxPriorityNotification(context, ID_SOS_ALERT, channelId, title, effectiveMessage, customView,
                tapPendingIntent, tapPendingIntent, swipePendingIntent);

        if (!headsUpOnly) {
            try {
                android.app.Activity currentActivity = swimminsparrow.ricordamelo.PillSOS.getCurrentActivity();
                if (currentActivity instanceof MainActivity && !currentActivity.isFinishing()) {
                    ((MainActivity) currentActivity).showSosAlarmDialog(effectiveUsername);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public void refreshSosHeadsUpNotification(@NonNull Context context, @Nullable String username,
            @Nullable String topic, @Nullable String message) {
        if (!swimminsparrow.ricordamelo.core.utils.AlarmRinger.getInstance().isRinging())
            return;
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        try {
            nm.cancel(ID_SOS_ALERT);
        } catch (Exception ignored) {
        }
        showSosAlert(context, username, topic, message, true);
    }

    /**
     * Shows a standard informational notification (PRIORITY_DEFAULT,
     * CATEGORY_STATUS, auto-cancel) with optional action buttons.
     */
    public void showStandardNotification(@NonNull Context context, int notificationId, @NonNull String channelId,
            @NonNull String title, @NonNull String message, @Nullable PendingIntent contentIntent,
            NotificationCompat.Action... actions) {
        PendingIntent tapIntent = contentIntent != null ? contentIntent : createDefaultMainTapIntent(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(tapIntent)
                .setAutoCancel(true);

        if (actions != null) {
            for (NotificationCompat.Action action : actions) {
                if (action != null) {
                    builder.addAction(action);
                }
            }
        }

        safeNotify(context, notificationId, builder.build());
    }

    /**
     * Shows a high-priority notification (PRIORITY_HIGH, VISIBILITY_PUBLIC,
     * CATEGORY_STATUS, auto-cancel) with optional action buttons.
     */
    public void showHighPriorityNotification(@NonNull Context context, int notificationId, @NonNull String channelId,
            @NonNull String title, @NonNull String message, @Nullable PendingIntent contentIntent,
            NotificationCompat.Action... actions) {
        PendingIntent tapIntent = contentIntent != null ? contentIntent : createDefaultMainTapIntent(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(tapIntent)
                .setAutoCancel(true);

        if (actions != null) {
            for (NotificationCompat.Action action : actions) {
                if (action != null) {
                    builder.addAction(action);
                }
            }
        }

        safeNotify(context, notificationId, builder.build());
    }

    /**
     * Shows a maximum priority alert notification (PRIORITY_MAX, CATEGORY_CALL,
     * VISIBILITY_PUBLIC, ongoing, custom RemoteViews, full-screen intent).
     */
    public void showMaxPriorityNotification(@NonNull Context context, int notificationId, @NonNull String channelId,
            @NonNull String title, @NonNull String message, @NonNull RemoteViews customView,
            @NonNull PendingIntent contentIntent, @Nullable PendingIntent fullScreenIntent,
            @Nullable PendingIntent deleteIntent) {
        createNotificationChannels(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(title)
                .setContentText(message)
                .setCustomContentView(customView)
                .setCustomBigContentView(customView)
                .setCustomHeadsUpContentView(customView)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        if (fullScreenIntent != null) {
            builder.setFullScreenIntent(fullScreenIntent, true);
        }
        if (deleteIntent != null) {
            builder.setDeleteIntent(deleteIntent);
        }

        Notification notification = builder.build();
        safeNotify(context, notificationId, notification);

        // Ensure AlarmRinger's steady, slow vibration loop is active
        AlarmRinger.getInstance().ensureVibrating(context);
    }

    private PendingIntent createDefaultMainTapIntent(@NonNull Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * "I'm OK" status message notification.
     */
    public void showImOk(@NonNull Context context, @Nullable String username, @Nullable String topic,
            @NonNull String message) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("username", username);
        if (topic != null) {
            intent.putExtra("topic", topic);
        }
        intent.putExtra("open_sos", true);
        intent.putExtra("skip_splash", true);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = (username != null && !username.trim().isEmpty())
                ? username.trim()
                : context.getString(R.string.im_ok);

        showHighPriorityNotification(context, ID_IM_OK, Constants.SOS_CHANNEL_ID, title, message, pendingIntent);
    }

    /**
     * Feedback notification for SOS message delivery status.
     */
    public void showPublishResult(@NonNull Context context, boolean success, @NonNull String message) {
        String title = success ? context.getString(R.string.sos_sent_title)
                : context.getString(R.string.sos_send_error_title);
        String body = (success ? context.getString(R.string.sos_send_success_body)
                : context.getString(R.string.sos_send_error_body)) + "\n\"" + message + "\"";

        if (success) {
            showStandardNotification(context, ID_PUBLISH_RESULT, Constants.SOS_CHANNEL_ID, title, body, null);
        } else {
            showHighPriorityNotification(context, ID_PUBLISH_RESULT, Constants.SOS_CHANNEL_ID, title, body, null);
        }
    }

    /**
     * Notification indicating a message could not be sent immediately and was
     * queued offline.
     */
    public void showQueuedOffline(@NonNull Context context, @Nullable String message) {
        String title = context.getString(R.string.sos_queued_offline_title);
        String body = context.getString(R.string.sos_queued_offline_body) +
                (message != null && !message.isEmpty() ? "\n\"" + message + "\"" : "");
        showHighPriorityNotification(context, ID_PUBLISH_RESULT, Constants.SOS_CHANNEL_ID, title, body, null);
    }

    /**
     * Notification when outbox offline messages are successfully flushed and
     * delivered.
     */
    public void showOutboxDelivered(@NonNull Context context) {
        showStandardNotification(context, ID_OUTBOX_DELIVERED, Constants.SOS_CHANNEL_ID,
                context.getString(R.string.sos_sent_from_outbox_title),
                context.getString(R.string.sos_sent_from_outbox_body), null);
    }

    /**
     * Connection request notification with direct inline Approve and Reject
     * actions.
     */
    public void showConnectionRequestNotification(@NonNull Context context, @NonNull ConnectionRequest request) {
        try {
            // Persist pending request in SettingsDB so dialog can be presented even if
            // notification is swiped away
            SettingsDB.getInstance(context).savePendingConnectionRequest(request);

            String title = context.getString(R.string.sos_connection_request_title);
            String message = context.getString(R.string.sos_connection_request_desc, request.name);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

            Intent contentIntent = new Intent(context, MainActivity.class);
            contentIntent.putExtra("open_sos", true);
            contentIntent.putExtra("skip_splash", true);
            request.toIntent(contentIntent);
            contentIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(context, Constants.NOTIFICATION_ID_CONNECTION_REQ,
                    contentIntent, flags);

            Intent approveIntent = new Intent(context, ConnectionRequestActionReceiver.class);
            approveIntent.setAction(ConnectionRequestActionReceiver.ACTION_APPROVE);
            request.toIntent(approveIntent);
            PendingIntent approvePendingIntent = PendingIntent.getBroadcast(context,
                    Constants.NOTIFICATION_ID_CONNECTION_REQ + 1, approveIntent, flags);

            Intent rejectIntent = new Intent(context, ConnectionRequestActionReceiver.class);
            rejectIntent.setAction(ConnectionRequestActionReceiver.ACTION_REJECT);
            request.toIntent(rejectIntent);
            PendingIntent rejectPendingIntent = PendingIntent.getBroadcast(context,
                    Constants.NOTIFICATION_ID_CONNECTION_REQ + 2, rejectIntent, flags);

            NotificationCompat.Action approveAction = new NotificationCompat.Action.Builder(
                    R.drawable.ic_check_circle_24, context.getString(R.string.btn_approve), approvePendingIntent)
                    .build();
            NotificationCompat.Action rejectAction = new NotificationCompat.Action.Builder(
                    R.drawable.ic_close_white_24dp, context.getString(R.string.btn_reject), rejectPendingIntent)
                    .build();

            showHighPriorityNotification(context, Constants.NOTIFICATION_ID_CONNECTION_REQ, Constants.SOS_CHANNEL_ID,
                    title, message, pendingIntent, approveAction, rejectAction);
        } catch (Exception e) {
            Log.e(TAG, "Error posting connection request notification", e);
        }
    }

    public void cancelConnectionRequestNotification(@NonNull Context context) {
        cancel(context, Constants.NOTIFICATION_ID_CONNECTION_REQ);
    }

    /**
     * Notification while waiting for the remote contact to approve the pairing
     * request.
     */
    public void showWaitingForApprovalNotification(@NonNull Context context, @Nullable String targetName) {
        try {
            String channelId = Constants.SERVICE_CHANNEL_ID;
            String title = context.getString(R.string.sos_connecting_notif_title);
            String message = context.getString(R.string.sos_waiting_for_approval_notif,
                    targetName != null ? targetName : "");

            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

            Intent contentIntent = new Intent(context, MainActivity.class);
            contentIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent contentPendingIntent = PendingIntent.getActivity(context,
                    Constants.NOTIFICATION_ID_PAIRING_WAIT, contentIntent, flags);

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

            safeNotify(context, Constants.NOTIFICATION_ID_PAIRING_WAIT, builder.build());
        } catch (Exception e) {
            Log.e(TAG, "Error posting waiting notification", e);
        }
    }

    public void cancelWaitingNotification(@NonNull Context context) {
        cancel(context, Constants.NOTIFICATION_ID_PAIRING_WAIT);
        PinPairingManager.stopApprovalListening();
    }

    /**
     * Notification informing the user of the result of a pairing request (approved
     * / rejected).
     */
    public void showApprovalResultNotification(@NonNull Context context, @NonNull String title,
            @NonNull String message) {
        try {
            Intent contentIntent = new Intent(context, MainActivity.class);
            contentIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
            PendingIntent contentPendingIntent = PendingIntent.getActivity(context,
                    Constants.NOTIFICATION_ID_PAIRING_WAIT + 2, contentIntent, flags);

            showHighPriorityNotification(context, Constants.NOTIFICATION_ID_PAIRING_WAIT, Constants.SOS_CHANNEL_ID,
                    title, message, contentPendingIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error posting result notification", e);
        }
    }

    /**
     * Notification informing that a paired contact has disconnected.
     */
    public void showDisconnectionNotification(@NonNull Context context) {
        try {
            Intent tapIntent = new Intent(context, MainActivity.class);
            tapIntent.putExtra("open_sos", true);
            tapIntent.putExtra("skip_splash", true);
            tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent tapPendingIntent = PendingIntent.getActivity(context, 0, tapIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            showHighPriorityNotification(context, Constants.NOTIFICATION_ID_DISCONNECTION, Constants.SOS_CHANNEL_ID,
                    context.getString(R.string.sos_disconnection_title),
                    context.getString(R.string.sos_disconnection_body), tapPendingIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error showing disconnection notification", e);
        }
    }

    private final Handler fallFallbackHandler = new Handler(Looper.getMainLooper());
    private Runnable fallFallbackRunnable = null;

    public synchronized void cancelFallNotificationFallback() {
        if (fallFallbackRunnable != null) {
            fallFallbackHandler.removeCallbacks(fallFallbackRunnable);
            fallFallbackRunnable = null;
        }
    }

    private synchronized void scheduleFallNotificationFallback(Context context, int countdownSeconds) {
        cancelFallNotificationFallback();
        fallFallbackRunnable = () -> {
            if (!FallAlertActivity.isActivityVisible()
                    && !swimminsparrow.ricordamelo.core.protection.FallAlertController.getInstance().isActivityInForeground()
                    && swimminsparrow.ricordamelo.core.protection.FallAlertController.getInstance().isAlerting()) {
                SpockLogger.info(TAG, "FallAlertActivity not visible after timeout, showing fallback notification");
                postFallPreAlarmNotification(context, countdownSeconds);
            }
        };
        fallFallbackHandler.postDelayed(fallFallbackRunnable, 1200);
    }

    private void postFallPreAlarmNotification(@NonNull Context context, int countdownSeconds) {
        if (FallAlertActivity.isActivityVisible()
                || swimminsparrow.ricordamelo.core.protection.FallAlertController.getInstance().isActivityInForeground()) {
            return;
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT |
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        Intent fullScreenIntent = new Intent(context, FallAlertActivity.class);
        fullScreenIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent fullScreenPending = PendingIntent.getActivity(
                context, 20021, fullScreenIntent, flags);

        // Cancel Intent (Sto bene)
        Intent cancelIntent = new Intent(context, FallAlertReceiver.class);
        cancelIntent.setAction(Constants.ACTION_CANCEL_FALL_ALERT);
        PendingIntent cancelPending = PendingIntent.getBroadcast(context, 20022, cancelIntent, flags);

        // Send SOS Now Intent
        Intent sendNowIntent = new Intent(context, FallAlertReceiver.class);
        sendNowIntent.setAction(Constants.ACTION_TRIGGER_SOS_NOW);
        PendingIntent sendNowPending = PendingIntent.getBroadcast(context, 20023, sendNowIntent, flags);

        boolean hasContacts = swimminsparrow.ricordamelo.core.sos.SOSContactsManager.hasConfiguredContacts(context);
        String subtitle = hasContacts ? context.getString(R.string.fall_alert_subtitle)
                : context.getString(R.string.fall_alert_subtitle_no_contacts);
        String actionBtnText = hasContacts ? context.getString(R.string.fall_alert_send_now_btn)
                : context.getString(R.string.fall_alert_call_emergency_btn);
        int actionIcon = hasContacts ? R.drawable.baseline_sos_24 : R.drawable.ic_call_24;

        NotificationCompat.Action cancelAction = new NotificationCompat.Action.Builder(
                R.drawable.baseline_done_24, context.getString(R.string.fall_alert_cancel_btn), cancelPending).build();
        NotificationCompat.Action sendNowAction = new NotificationCompat.Action.Builder(
                actionIcon, actionBtnText, sendNowPending).build();

        showHighPriorityNotification(context, Constants.FALL_ALERT_NOTIFICATION_ID, Constants.FALL_CHANNEL_ID,
                context.getString(R.string.fall_alert_title), subtitle, fullScreenPending,
                cancelAction, sendNowAction);
    }

    /**
     * Shows the emergency Fall Pre-Alarm notification with live countdown and
     * action buttons,
     * and returns the full-screen PendingIntent for immediate screen wake up.
     */
    @Nullable
    public PendingIntent showFallPreAlarmNotification(@NonNull Context context, int countdownSeconds) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT |
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        Intent fullScreenIntent = new Intent(context, FallAlertActivity.class);
        fullScreenIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK);

        android.os.Bundle optionsBundle = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            android.app.ActivityOptions options = android.app.ActivityOptions.makeBasic();
            options.setPendingIntentCreatorBackgroundActivityStartMode(
                    android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            optionsBundle = options.toBundle();
        }

        PendingIntent fullScreenPending = PendingIntent.getActivity(
                context, 20021, fullScreenIntent, flags, optionsBundle);

        if (FallAlertActivity.isActivityVisible()
                || swimminsparrow.ricordamelo.core.protection.FallAlertController.getInstance().isActivityInForeground()) {
            return fullScreenPending;
        }

        scheduleFallNotificationFallback(context, countdownSeconds);
        return fullScreenPending;
    }

    public void refreshFallPreAlarmHeadsUpNotification(@NonNull Context context, int countdownSeconds) {
        if (!swimminsparrow.ricordamelo.core.protection.FallAlertController.getInstance().isAlerting())
            return;
        if (FallAlertActivity.isActivityVisible()
                || swimminsparrow.ricordamelo.core.protection.FallAlertController.getInstance().isActivityInForeground())
            return;
        postFallPreAlarmNotification(context, countdownSeconds);
    }

    /**
     * Shows confirmation notification after emergency SOS has been confirmed and
     * dispatched.
     */
    public void showFallAlertConfirmedNotification(@NonNull Context context) {
        showHighPriorityNotification(context, Constants.FALL_ALERT_NOTIFICATION_ID, Constants.SOS_CHANNEL_ID,
                context.getString(R.string.fall_detected_notification_title),
                context.getString(R.string.fall_detected_notification_body), null);
    }

    /**
     * Shows notification after fall alert confirmed when no SOS contacts are
     * configured,
     * inviting the user to call emergency services if needed.
     */
    public void showFallAlertNoContactsNotification(@NonNull Context context) {
        Intent dialIntent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"));
        dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT |
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent dialPending = PendingIntent.getActivity(context, 20025, dialIntent, pendingFlags);

        NotificationCompat.Action callAction = new NotificationCompat.Action.Builder(
                R.drawable.ic_call_24, context.getString(R.string.fall_alert_call_emergency_btn), dialPending).build();

        showHighPriorityNotification(context, Constants.FALL_ALERT_NOTIFICATION_ID, Constants.SOS_CHANNEL_ID,
                context.getString(R.string.fall_detected_no_contacts_title),
                context.getString(R.string.fall_detected_no_contacts_body),
                dialPending, callAction);
    }

    /**
     * Dismisses the active Fall Pre-Alarm notification.
     */
    public void cancelFallAlertNotification(@NonNull Context context) {
        cancelFallNotificationFallback();
        cancel(context, Constants.FALL_ALERT_NOTIFICATION_ID);
    }

    /**
     * Pill reminder alarm notification with custom RemoteViews (Spegni / Posponi),
     * full-screen intent for locked/sleeping states, and continuous AlarmRinger playback.
     */
    public void showAlarmNotification(@NonNull Context context, int id, @Nullable String orario) {
        cancelNotificationOnly(context, id);
        createNotificationChannels(context);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT |
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0);

        Farmaco farmaco = FarmacoDao.getInstance(context).getById(id);
        String pillName = (farmaco != null) ? farmaco.getNome() : "";

        if (SettingsDB.getInstance(context).isMaxVolumeMode()) {
            VolumeUtils.overrideVolumeToMax(context);
        }

        Intent fullScreenIntent = new Intent(context, FullAlarmActivity.class);
        fullScreenIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        fullScreenIntent.putExtra("ID", id);
        fullScreenIntent.putExtra("Orario", orario);
        android.os.Bundle optionsBundle = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            android.app.ActivityOptions options = android.app.ActivityOptions.makeBasic();
            options.setPendingIntentCreatorBackgroundActivityStartMode(
                    android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            optionsBundle = options.toBundle();
        }

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(context, id * 10 + 3, fullScreenIntent,
                pendingFlags, optionsBundle);

        Intent dismissIntent = new Intent(context, NotificationDismissReceiver.class);
        dismissIntent.setAction("swimminsparrow.ricordamelo.ACTION_DISMISS_ALARM");
        dismissIntent.setData(Uri.parse("pillsos://alarm/dismiss/" + id));
        dismissIntent.putExtra("ID", id);
        PendingIntent dismissPendingIntent = PendingIntentHelper.getBroadcast(context, id * 10 + 1, dismissIntent,
                false);

        int defaultSnoozeMinutes = 15;
        Intent snoozeIntent = new Intent(context, SnoozeReceiver.class);
        snoozeIntent.setAction("swimminsparrow.ricordamelo.ACTION_SNOOZE_ALARM");
        snoozeIntent.setData(Uri.parse("pillsos://alarm/snooze/" + id));
        snoozeIntent.putExtra("ID", id);
        snoozeIntent.putExtra("Orario", orario);
        snoozeIntent.putExtra("snooze_minutes", defaultSnoozeMinutes);
        PendingIntent snoozePendingIntent = PendingIntentHelper.getBroadcast(context, id * 10 + 2, snoozeIntent,
                false);

        String displayTime = orario;
        if (displayTime == null || displayTime.isEmpty() || "TEST".equalsIgnoreCase(displayTime)) {
            displayTime = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(new java.util.Date());
        }

        String alarmName = pillName;
        if (alarmName == null || alarmName.isEmpty()) {
            if (id == Constants.TEST_ALARM_NOTIFICATION_ID || "TEST".equalsIgnoreCase(orario)) {
                alarmName = context.getString(R.string.test_alarm_title);
            } else {
                alarmName = context.getString(R.string.app_name);
            }
        }

        RemoteViews customView = buildAlarmRemoteViews(context, alarmName, displayTime, dismissPendingIntent,
                snoozePendingIntent);

        AlarmRinger.getInstance().start(context, id);

        // 1. Attempt to launch FullAlarmActivity directly without creating any notification
        boolean started = false;
        android.app.Activity currentActivity = swimminsparrow.ricordamelo.PillSOS.getCurrentActivity();
        if (currentActivity != null && !currentActivity.isFinishing()) {
            try {
                currentActivity.startActivity(fullScreenIntent);
                started = true;
                SpockLogger.info(TAG, "Started FullAlarmActivity directly from foreground currentActivity");
            } catch (Exception e) {
                SpockLogger.info(TAG, "Failed to start FullAlarmActivity from currentActivity: " + e.getMessage());
            }
        }
        if (!started) {
            try {
                fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fullScreenIntent, optionsBundle);
                started = true;
                SpockLogger.info(TAG, "Started FullAlarmActivity with NEW_TASK");
            } catch (Exception e) {
                SpockLogger.info(TAG, "Failed to start FullAlarmActivity with NEW_TASK: " + e.getMessage());
            }
        }

        // 2. Schedule fallback notification: ONLY created if FullAlarmActivity does not appear within 1200ms
        scheduleAlarmNotificationFallback(context, id, alarmName, displayTime,
                customView, fullScreenPendingIntent, snoozePendingIntent);
    }

    private final Handler alarmFallbackHandler = new Handler(Looper.getMainLooper());
    private Runnable alarmFallbackRunnable = null;

    public synchronized void cancelNotificationFallback() {
        if (alarmFallbackRunnable != null) {
            alarmFallbackHandler.removeCallbacks(alarmFallbackRunnable);
            alarmFallbackRunnable = null;
        }
    }

    private synchronized void scheduleAlarmNotificationFallback(Context context, int id, String alarmName,
            String displayTime, RemoteViews customView, PendingIntent fullScreenPendingIntent,
            PendingIntent snoozePendingIntent) {
        cancelNotificationFallback();
        alarmFallbackRunnable = () -> {
            if (!FullAlarmActivity.isAlarmVisible(id) && AlarmRinger.getInstance().isRinging()) {
                SpockLogger.info(TAG, "FullAlarmActivity not visible after timeout, showing fallback notification for " + id);
                showMaxPriorityNotification(context, id, Constants.ALARM_CHANNEL_ID, alarmName, displayTime,
                        customView, fullScreenPendingIntent, fullScreenPendingIntent, snoozePendingIntent);
            }
        };
        alarmFallbackHandler.postDelayed(alarmFallbackRunnable, 1200);
    }

    /**
     * Shows a quiet ongoing notification for a snoozed medication alarm with a live countdown timer.
     * Appears in the notification drawer and lock screen with an immediate "Spegni" action.
     */
    public void showSnoozedNotification(@NonNull Context context, int id, @Nullable String orario, int snoozeMinutes) {
        createNotificationChannels(context);

        Farmaco farmaco = FarmacoDao.getInstance(context).getById(id);
        String pillName = (farmaco != null && farmaco.getNome() != null && !farmaco.getNome().isEmpty())
                ? farmaco.getNome()
                : (id == Constants.TEST_ALARM_NOTIFICATION_ID ? context.getString(R.string.test_alarm_title) : context.getString(R.string.app_name));

        long triggerTime = System.currentTimeMillis() + ((long) snoozeMinutes * 60 * 1000);
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        String resumeTimeStr = sdf.format(new java.util.Date(triggerTime));

        String title = context.getString(R.string.alarm_snoozed_title, pillName);
        String content = context.getString(R.string.alarm_snoozed_body, resumeTimeStr, snoozeMinutes);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPending = PendingIntent.getActivity(context, id, openIntent, flags);

        Intent dismissIntent = new Intent(context, NotificationDismissReceiver.class);
        dismissIntent.setAction("swimminsparrow.ricordamelo.ACTION_DISMISS_ALARM");
        dismissIntent.setData(Uri.parse("pillsos://alarm/dismiss/" + id));
        dismissIntent.putExtra("ID", id);
        PendingIntent dismissPending = PendingIntentHelper.getBroadcast(context, id * 10 + 5, dismissIntent, false);

        NotificationCompat.Action dismissAction = new NotificationCompat.Action.Builder(
                R.drawable.baseline_done_24, context.getString(R.string.alarm_dismiss), dismissPending).build();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Constants.ALARM_CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(contentPending)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(triggerTime)
                .setShowWhen(true)
                .setAutoCancel(true)
                .addAction(dismissAction);

        Notification notification = builder.build();
        safeNotify(context, id, notification);
    }

    /**
     * Cancels any active alarm notifications from the drawer and status bar.
     */
    public void cancelNotificationOnly(@NonNull Context context, int id) {
        if (id == ID_SOS_ALERT) {
            clearActiveSos();
        }
        int activeId = AlarmRinger.getInstance().getActiveAlarmId();
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                if (id != -1) nm.cancel(id);
                if (activeId > 0 && activeId != id) nm.cancel(activeId);
                nm.cancel(Constants.TEST_ALARM_NOTIFICATION_ID);
                nm.cancel(ID_SOS_ALERT);

                android.service.notification.StatusBarNotification[] active = nm.getActiveNotifications();
                if (active != null) {
                    for (android.service.notification.StatusBarNotification sbn : active) {
                        boolean isService = sbn.getId() == Constants.PROTECTION_NOTIFICATION_ID
                                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                                        && sbn.getNotification() != null
                                        && Constants.SERVICE_CHANNEL_ID.equals(sbn.getNotification().getChannelId()))
                                || (sbn.getNotification() != null
                                        && (sbn.getNotification().flags & Notification.FLAG_FOREGROUND_SERVICE) != 0);
                        if (!isService) {
                            if (id == -1 || sbn.getId() == id || sbn.getId() == activeId
                                    || sbn.getId() == Constants.TEST_ALARM_NOTIFICATION_ID || sbn.getId() == ID_SOS_ALERT) {
                                nm.cancel(sbn.getTag(), sbn.getId());
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        try {
            NotificationManagerCompat nmc = NotificationManagerCompat.from(context);
            if (id != -1) nmc.cancel(id);
            if (activeId > 0 && activeId != id) nmc.cancel(activeId);
            nmc.cancel(Constants.TEST_ALARM_NOTIFICATION_ID);
            nmc.cancel(ID_SOS_ALERT);
        } catch (Exception ignored) {}

        if (id == -1) {
            try {
                java.util.List<Farmaco> list = FarmacoDao.getInstance(context).getAll();
                if (list != null) {
                    NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    NotificationManagerCompat nmc = NotificationManagerCompat.from(context);
                    for (Farmaco f : list) {
                        if (nm != null) nm.cancel(f.getId());
                        if (nmc != null) nmc.cancel(f.getId());
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Unified single-source-of-truth method to dismiss an alarm completely.
     * Stops ringing, audio, vibration, duration timeout, restores volume,
     * cancels notifications, and cancels scheduled fallback alarms.
     */
    public void dismissAlarm(@NonNull Context context, int id) {
        cancelNotificationFallback();
        int ringingId = AlarmRinger.getInstance().getActiveAlarmId();

        // 1. Stop audio, vibration, wakelock, timeout, and TTS
        AlarmRinger.getInstance().stop(context);

        // 2. Clear all related notifications
        cancelNotificationOnly(context, id);
        if (ringingId > 0 && ringingId != id) {
            cancelNotificationOnly(context, ringingId);
        }

        // 3. Restore volumes
        VolumeUtils.restoreVolume(context);

        // 4. Cancel scheduled MissedAlarm and Snooze in AlarmManager
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                Intent missedIntent = new Intent(context, MissedAlarmReceiver.class);
                PendingIntent missedPending = PendingIntentHelper.getBroadcast(context, id, missedIntent, true);
                am.cancel(missedPending);

                Intent snoozeIntent = new Intent(context, ReminderReceiver.class);
                PendingIntent snoozePending = PendingIntentHelper.getBroadcast(context, id, snoozeIntent, true);
                am.cancel(snoozePending);

                if (id == -1) {
                    java.util.List<Farmaco> list = FarmacoDao.getInstance(context).getAll();
                    if (list != null) {
                        for (Farmaco f : list) {
                            PendingIntent sp = PendingIntentHelper.getBroadcast(context, f.getId(), snoozeIntent, true);
                            am.cancel(sp);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Unified single-source-of-truth method to snooze an alarm.
     * Dismisses the current ringing alarm, schedules the future snooze wakeup,
     * and displays the snoozed countdown notification.
     */
    public void snoozeAlarm(@NonNull Context context, int id, @Nullable String orario, int snoozeMinutes) {
        dismissAlarm(context, id);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            Intent snoozeIntent = new Intent(context, ReminderReceiver.class);
            snoozeIntent.putExtra("ID", id);
            if (orario != null) {
                snoozeIntent.putExtra("Orario", orario);
            }
            PendingIntent snoozePending = PendingIntentHelper.getBroadcast(context, id, snoozeIntent, true);
            long triggerTime = System.currentTimeMillis() + ((long) snoozeMinutes * 60 * 1000);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, snoozePending);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerTime, snoozePending);
            }
        }

        showSnoozedNotification(context, id, orario, snoozeMinutes);

        try {
            Toast.makeText(context, context.getString(R.string.snooze_format, snoozeMinutes), Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {}
    }

    /**
     * Unified handler called when user leaves an activity (Home button or Back press).
     * Ensures heads-up notification is presented if FallAlert countdown is actively counting down.
     */
    public void handleUserLeaveHint(@NonNull Context context) {
        if (swimminsparrow.ricordamelo.core.protection.FallAlertController.getInstance().isAlerting()) {
            refreshFallPreAlarmHeadsUpNotification(context.getApplicationContext(),
                    swimminsparrow.ricordamelo.core.protection.FallAlertController.getInstance().getSecondsRemaining());
        }
    }
}
