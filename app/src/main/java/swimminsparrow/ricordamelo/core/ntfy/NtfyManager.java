package swimminsparrow.ricordamelo.core.ntfy;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.config.Constants;
import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.core.notification.AppNotificationManager;
import swimminsparrow.ricordamelo.core.utils.AppExecutors;
import swimminsparrow.ricordamelo.core.utils.CryptoUtils;
import swimminsparrow.ricordamelo.core.utils.NetworkUtils;
import swimminsparrow.ricordamelo.services.NotificationDismissReceiver;
import swimminsparrow.ricordamelo.ui.alarms.MainActivity;
import swimminsparrow.ricordamelo.ui.common.Toaster;

public class NtfyManager {

    private static final String TAG = "NtfyManager";
    private static final java.util.concurrent.ConcurrentHashMap<String, NtfyManager> instances = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Boolean> processedMsgIds = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<String, Boolean>(100, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Boolean> eldest) {
                    return size() > 300;
                }
            }
    );

    private final String topic;
    private Context context;
    private Thread receiveThread;
    private volatile boolean isReceivingActive = false;
    private volatile HttpURLConnection currentReceiveConn = null;

    private NtfyManager(Context context, String topic) {
        this.topic = topic;
        this.context = context.getApplicationContext();
    }

    public static synchronized NtfyManager getInstance(Context context, String topic) {
        if (topic == null || topic.isEmpty()) return null;
        if (!instances.containsKey(topic)) {
            instances.put(topic, new NtfyManager(context, topic));
        }
        return instances.get(topic);
    }

    private String getLastMessageId(String topic) {
        if (context == null || topic == null) return null;
        return context.getSharedPreferences("ntfy_prefs", Context.MODE_PRIVATE)
                .getString("last_msg_id_" + topic, null);
    }

    private void saveLastMessageId(String topic, String msgId) {
        if (context == null || topic == null || msgId == null) return;
        context.getSharedPreferences("ntfy_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("last_msg_id_" + topic, msgId)
                .apply();
    }

    public void startReceiving() {
        if (topic == null || topic.trim().isEmpty()) return;
        if (isReceivingActive) return;
        isReceivingActive = true;

        receiveThread = new Thread(() -> {
            while (isReceivingActive) {
                HttpURLConnection conn = null;
                try {
                    String lastId = getLastMessageId(topic);
                    String endpoint = (lastId != null && !lastId.trim().isEmpty())
                            ? topic + "/json?since=" + lastId
                            : topic + "/json?since=24h";

                    URL url = new URL(swimminsparrow.ricordamelo.config.Constants.getNtfyTopicUrl(endpoint));
                    conn = (HttpURLConnection) url.openConnection();
                    swimminsparrow.ricordamelo.core.utils.ForceIPv4SSLSocketFactory.applyTo(conn);
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept", "text/event-stream");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(75000); // 75s read timeout to detect dead sockets (ntfy sends keepalive every ~45s)
                    currentReceiveConn = conn;

                    int responseCode = conn.getResponseCode();
                    if (responseCode == 429) {
                        Log.w(TAG, "Ntfy rate limited (429) for endpoint " + endpoint + ", backing off 20s.");
                        if (conn != null) conn.disconnect();
                        try { Thread.sleep(20000); } catch (InterruptedException ignored) {}
                        continue;
                    }
                    if (responseCode != 200) {
                        Log.w(TAG, "Ntfy response code " + responseCode + " for endpoint " + endpoint);
                        if (conn != null) conn.disconnect();
                        try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                        continue;
                    }

                    try (InputStream in = conn.getInputStream();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
                        String line;

                        Log.d(TAG, "Connected to Ntfy stream for topic: " + topic + " (endpoint: " + endpoint + ")");

                        while (isReceivingActive && (line = reader.readLine()) != null) {
                            if (line.trim().isEmpty()) continue;
                            try {
                                JSONObject json = new JSONObject(line);
                                String event = json.optString("event");
                                if ("message".equals(event)) {
                                    String msgId = json.optString("id");
                                    long msgTime = json.optLong("time", 0);
                                    if (msgId != null && !msgId.trim().isEmpty()) {
                                        if (processedMsgIds.containsKey(msgId)) {
                                            continue;
                                        }
                                        processedMsgIds.put(msgId, Boolean.TRUE);
                                        saveLastMessageId(topic, msgId);
                                    }
                                    String encryptedPayload = json.optString("message");
                                    handleIncomingMessage(encryptedPayload, msgTime);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing JSON stream line", e);
                            }
                        }
                    }

                } catch (Exception e) {
                    if (!isReceivingActive) {
                        Log.d(TAG, "Ntfy stream closed cleanly for topic: " + topic);
                    } else if (e instanceof java.net.SocketException || e instanceof java.io.IOException) {
                        Log.w(TAG, "Ntfy stream disconnected (" + e.getMessage() + ") for topic: " + topic + ", reconnecting in 5s");
                    } else {
                        Log.e(TAG, "Ntfy Receive Error for topic: " + topic, e);
                    }
                } finally {
                    currentReceiveConn = null;
                    if (conn != null) {
                        try { conn.disconnect(); } catch (Exception ignored) {}
                    }
                }

                // If still active but stream closed, reconnect after a delay
                if (isReceivingActive) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignored) { }
                }
            }
        });
        receiveThread.start();
    }

    private void handleIncomingMessage(String encryptedPayload, long msgTime) {
        String key = null;
        swimminsparrow.ricordamelo.core.sos.SOSContact matchedContact =
                swimminsparrow.ricordamelo.core.sos.SOSContactsManager.getContactByTopic(context, this.topic);
        if (matchedContact != null) {
            key = matchedContact.getKey();
        }
        if (key == null || key.trim().isEmpty()) {
            key = SettingsDB.getInstance(context).getSecurePairingKey();
        }

        String decryptedMessage = key != null ? CryptoUtils.decrypt(encryptedPayload, key) : null;
        if (decryptedMessage == null) {
            String altKey = SettingsDB.getInstance(context).getSecurePairingKey();
            if (altKey != null && !altKey.equals(key)) {
                decryptedMessage = CryptoUtils.decrypt(encryptedPayload, altKey);
            }
        }

        // Fallback: check contact specific master keys
        if (decryptedMessage == null) {
            java.util.List<swimminsparrow.ricordamelo.core.sos.SOSContact> allContacts =
                    swimminsparrow.ricordamelo.core.sos.SOSContactsManager.getContacts(context);
            for (swimminsparrow.ricordamelo.core.sos.SOSContact c : allContacts) {
                if (c.getKey() != null) {
                    decryptedMessage = CryptoUtils.decrypt(encryptedPayload, c.getKey());
                    if (decryptedMessage != null) break;
                }
            }
        }

        if (decryptedMessage == null) {
            Log.e(TAG, "Failed to decrypt incoming message.");
            return;
        }

        NtfyMessageType messageType = NtfyMessageType.resolve(decryptedMessage);
        if (messageType == null) {
            Log.w(TAG, "Rejected message: outside of managed Ntfy message types.");
            return;
        }

        Log.d(TAG, "Received managed message [" + messageType + "]");

        if (messageType == NtfyMessageType.ACK_SOS) {
            Log.d(TAG, "Received delivery ACK for topic: " + topic);
            String ackSenderTopic = null;
            if (decryptedMessage.contains(":")) {
                ackSenderTopic = decryptedMessage.substring(decryptedMessage.indexOf(":") + 1).trim();
            }
            String myUuid = SettingsDB.getInstance(context).getUserUUID();
            if (myUuid != null && myUuid.equals(ackSenderTopic)) {
                Log.d(TAG, "Ignoring self-echoed ACK_SOS from myUuid: " + myUuid);
                return;
            }
            String targetTopicForAck = (ackSenderTopic != null && !ackSenderTopic.isEmpty()) ? ackSenderTopic : topic;
            swimminsparrow.ricordamelo.core.data.dao.SOSMessageDao.getInstance(context).markMessageDelivered(targetTopicForAck);

            new android.os.Handler(context.getMainLooper()).post(() -> {
                swimminsparrow.ricordamelo.core.sos.SOSContactsManager.notifyContactsChanged(context);
            });
            return;
        }

        long nowSec = System.currentTimeMillis() / 1000L;
        // User content messages (SOS_ALARM, IM_OK) remain valid for 24 hours so they are delivered after device reboots or being offline.
        // Ephemeral signals (PING, PONG, DISCONNECT) expire after 10 minutes (600s).
        long maxAgeSec = (messageType == NtfyMessageType.SOS_ALARM || messageType == NtfyMessageType.IM_OK) ? 86400L : 600L;
        if (msgTime > 0 && (nowSec - msgTime) > maxAgeSec) {
            Log.d(TAG, "Ignoring stale incoming message [" + messageType + "] from " + (nowSec - msgTime) + "s ago");
            return;
        }

        if (messageType == NtfyMessageType.DISCONNECT_SOS) {
            Log.d(TAG, "Received DISCONNECT_SOS signal on topic: " + topic + " msgTime: " + msgTime);
            String senderTopic = null;
            String scope = null;
            if (decryptedMessage.contains(":")) {
                String sub = decryptedMessage.substring(decryptedMessage.indexOf(":") + 1).trim();
                if (sub.contains(":")) {
                    String[] parts = sub.split(":", 2);
                    senderTopic = parts[0].trim();
                    scope = parts[1].trim();
                } else {
                    senderTopic = sub;
                }
            }
            String myUuid = SettingsDB.getInstance(context).getUserUUID();
            if (myUuid != null && myUuid.equals(senderTopic)) {
                Log.d(TAG, "Ignoring self-echoed DISCONNECT_SOS signal (myUuid=" + myUuid + ")");
                return;
            }
            swimminsparrow.ricordamelo.core.sos.SOSContactsManager.handleRemoteDisconnection(context, topic, senderTopic, scope, msgTime);
            return;
        }

        // NAME_UPDATE_SOS:<newName>:<senderUuid>
        if (messageType == NtfyMessageType.NAME_UPDATE_SOS) {
            if (msgTime > 0 && (nowSec - msgTime) > 300) {
                Log.d(TAG, "Ignoring stale NAME_UPDATE_SOS from " + (nowSec - msgTime) + "s ago");
                return;
            }
            // Format: NAME_UPDATE_SOS:<newName>:<senderUuid>
            String payload = decryptedMessage.substring(NtfyMessageType.NAME_UPDATE_SOS.getIdentifier().length());
            int lastColon = payload.lastIndexOf(":");
            String newName = lastColon > 0 ? payload.substring(0, lastColon) : payload;
            String senderUuid = lastColon > 0 ? payload.substring(lastColon + 1) : topic;
            Log.d(TAG, "Received NAME_UPDATE_SOS: name='" + newName + "' uuid='" + senderUuid + "'");
            swimminsparrow.ricordamelo.core.sos.SOSContactsManager.handleNameUpdate(context, senderUuid, topic, newName);
            return;
        }

        if (messageType == NtfyMessageType.PING_SOS) {
            String sub = decryptedMessage.substring(NtfyMessageType.PING_SOS.getIdentifier().length()).trim();
            String senderUuid = sub;
            String senderKey = null;
            if (sub.contains(":")) {
                String[] parts = sub.split(":", 2);
                senderUuid = parts[0].trim();
                senderKey = parts[1].trim();
            }
            String myUuid = SettingsDB.getInstance(context).getUserUUID();
            String myKey = SettingsDB.getInstance(context).getSecurePairingKey();
            if (myUuid != null && myUuid.equals(senderUuid)) {
                return;
            }
            Log.d(TAG, "Received PING_SOS from " + senderUuid);
            boolean isPaired = swimminsparrow.ricordamelo.core.sos.SOSContactsManager.isContactPaired(context, senderUuid);
            if (isPaired) {
                swimminsparrow.ricordamelo.core.sos.SOSContactsManager.updatePingTimestamp(context, senderUuid, System.currentTimeMillis());
                if (senderKey != null && !senderKey.isEmpty()) {
                    swimminsparrow.ricordamelo.core.sos.SOSContactsManager.updateContactKey(context, senderUuid, senderKey);
                }
                sendDirectSignal(senderUuid, "PONG_SOS:" + myUuid + ":" + myKey, null);
            }
            return;
        }

        if (messageType == NtfyMessageType.PONG_SOS) {
            String sub = decryptedMessage.substring(NtfyMessageType.PONG_SOS.getIdentifier().length()).trim();
            String senderUuid = sub;
            String senderKey = null;
            if (sub.contains(":")) {
                String[] parts = sub.split(":", 2);
                senderUuid = parts[0].trim();
                senderKey = parts[1].trim();
            }
            Log.d(TAG, "Received PONG_SOS from " + senderUuid + " -> verified active!");
            swimminsparrow.ricordamelo.core.sos.SOSContactsManager.updatePingTimestamp(context, senderUuid, System.currentTimeMillis());
            if (senderKey != null && !senderKey.isEmpty()) {
                swimminsparrow.ricordamelo.core.sos.SOSContactsManager.updateContactKey(context, senderUuid, senderKey);
            }
            return;
        }

        if (messageType == NtfyMessageType.NOT_PAIRED_SOS) {
            String senderUuid = decryptedMessage.substring(NtfyMessageType.NOT_PAIRED_SOS.getIdentifier().length()).trim();
            Log.w(TAG, "Ignoring obsolete NOT_PAIRED_SOS from " + senderUuid);
            return;
        }

        // Ignore self-echoed regular SOS / status messages on our own sender topic
        String myUuid = SettingsDB.getInstance(context).getUserUUID();
        if (myUuid != null && myUuid.equals(topic)) {
            Log.d(TAG, "Ignoring self-echoed SOS message on own topic: " + topic);
            return;
        }

        final String cleanContent = NtfyMessageType.extractContent(decryptedMessage);
        swimminsparrow.ricordamelo.core.sos.SOSContactsManager.updateLastMessage(context, topic, cleanContent, "RECEIVED");

        sendAckResponse(topic);

        String senderName = swimminsparrow.ricordamelo.core.sos.SOSContactsManager.getSenderNameForTopic(context, topic);
        final String resolvedSender = (senderName != null && !senderName.trim().isEmpty()) ? senderName.trim() : "Contact";
        final String finalMsg = cleanContent;
        final NtfyMessageType finalType = messageType;
        new android.os.Handler(context.getMainLooper()).post(() -> {
            swimminsparrow.ricordamelo.core.sos.SOSContactsManager.notifyContactsChanged(context);
            if (finalType == NtfyMessageType.IM_OK) {
                showImOkNotification(context, resolvedSender, finalMsg);
            } else {
                Toaster.errorToast(context, context.getString(R.string.sos_received_toast));
                showSOSNotification(context, resolvedSender, finalMsg);
            }
        });
    }

    /**
     * Centralized helper to encrypt a payload and POST it to a topic on the ntfy server using AppExecutors.diskIO.
     * If the device is offline or the request fails and it is a DISCONNECT_SOS signal, it is reliably queued to the Outbox.
     */
    private static void postEncryptedPayload(Context context, String targetTopic, String key, String rawPayload, String ttl, String logTag) {
        if (targetTopic == null || key == null || rawPayload == null) return;
        AppExecutors.getInstance().diskIO().execute(() -> {
            HttpURLConnection conn = null;
            try {
                if (context != null && !NetworkUtils.isNetworkAvailable(context)) {
                    throw new java.io.IOException("Network unavailable (offline or airplane mode)");
                }

                String encryptedMsg = CryptoUtils.encrypt(rawPayload, key);
                if (encryptedMsg == null) return;

                URL url = new URL(swimminsparrow.ricordamelo.config.Constants.getNtfyTopicUrl(targetTopic));
                conn = (HttpURLConnection) url.openConnection();
                swimminsparrow.ricordamelo.core.utils.ForceIPv4SSLSocketFactory.applyTo(conn);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                if (ttl != null) {
                    conn.setRequestProperty("TTL", ttl);
                }
                conn.setDoOutput(true);

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = encryptedMsg.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                    os.flush();
                }
                int code = conn.getResponseCode();
                Log.d(TAG, "Sent " + logTag + " to topic " + targetTopic + ", status: " + code);
                if (code == 429) {
                    Log.w(TAG, "Rate limited (429) sending " + logTag + " to " + targetTopic + ", backing off.");
                    return;
                }
                if (code < 200 || code >= 300) {
                    throw new java.io.IOException("HTTP error response code: " + code);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to send " + logTag + " to " + targetTopic + ": " + e.getMessage());
                if (context != null && rawPayload.startsWith("DISCONNECT_SOS")) {
                    Log.d(TAG, "Queuing DISCONNECT_SOS to outbox for retry");
                    swimminsparrow.ricordamelo.core.data.dao.SOSOutboxDao.getInstance(context)
                            .addPendingMessage(targetTopic, rawPayload, System.currentTimeMillis(), key, ttl);
                    swimminsparrow.ricordamelo.core.sos.SOSOutboxWorker.scheduleRetry(context);
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void sendAckResponse(String targetTopic) {
        if (context == null || targetTopic == null) return;
        String key = null;
        swimminsparrow.ricordamelo.core.sos.SOSContact contact =
                swimminsparrow.ricordamelo.core.sos.SOSContactsManager.getContactByTopic(context, targetTopic);
        if (contact != null) {
            key = contact.getKey();
        }
        if (key == null) key = SettingsDB.getInstance(context).getSecurePairingKey();
        if (key == null) return;

        String senderUuid = SettingsDB.getInstance(context).getUserUUID();
        String rawAck = "ACK_SOS" + (senderUuid != null && !senderUuid.isEmpty() ? ":" + senderUuid : "");
        postEncryptedPayload(context, targetTopic, key, rawAck, "24h", "ACK response");
    }

    /**
     * Broadcasts the user's new display name to all connected contacts.
     *
     * Topology:
     *  - connectedReceivers (people who receive MY SOS) subscribe to MY UUID topic.
     *    → publish name update to MY OWN UUID topic (same channel as regular SOS).
     *  - monitoredSenders (people whose SOS I receive) own their UUID topics.
     *    → publish name update directly to each of THEIR topics.
     */
    public static void sendNameUpdateToAllContacts(Context context, String newName) {
        if (context == null || newName == null || newName.trim().isEmpty()) return;
        String myUuid = SettingsDB.getInstance(context).getUserUUID();
        if (myUuid == null || myUuid.trim().isEmpty()) return;

        String rawPayload = "NAME_UPDATE_SOS:" + newName.trim() + ":" + myUuid;

        java.util.List<swimminsparrow.ricordamelo.core.sos.SOSContact> contacts =
                swimminsparrow.ricordamelo.core.sos.SOSContactsManager.getContacts(context);

        if (contacts.isEmpty()) {
            Log.d(TAG, "sendNameUpdateToAllContacts: no contacts to notify");
            return;
        }

        // 1. Post to OWN topic once
        String senderKey = SettingsDB.getInstance(context).getSecurePairingKey();
        if (senderKey != null && !senderKey.isEmpty()) {
            postNameUpdateSignal(context, myUuid, senderKey, rawPayload);
        }

        // 2. Post directly to each contact's topic
        for (swimminsparrow.ricordamelo.core.sos.SOSContact contact : contacts) {
            String targetTopic = contact.getTopic();
            String key = contact.getKey();
            if (targetTopic == null || key == null) continue;
            postNameUpdateSignal(context, targetTopic, key, rawPayload);
        }
    }

    /** Encrypts rawPayload with key and POSTs it to targetTopic on the ntfy server. */
    private static void postNameUpdateSignal(Context context, String targetTopic, String key, String rawPayload) {
        postEncryptedPayload(context, targetTopic, key, rawPayload, "5m", "NAME_UPDATE_SOS");
    }



    public void sendDisconnectionSignal(String targetTopic, String customKey, String senderUuid, String scope) {
        if (context == null || targetTopic == null) return;
        String key = customKey;
        if (key == null) {
            swimminsparrow.ricordamelo.core.sos.SOSContact c =
                    swimminsparrow.ricordamelo.core.sos.SOSContactsManager.getContactByTopic(context, targetTopic);
            if (c != null) key = c.getKey();
        }
        if (key == null) key = SettingsDB.getInstance(context).getSecurePairingKey();
        if (key == null) return;

        String rawPayload = "DISCONNECT_SOS" + (senderUuid != null ? ":" + senderUuid : "") + (scope != null ? ":" + scope : "");
        postEncryptedPayload(context, targetTopic, key, rawPayload, "12h", "DISCONNECT_SOS signal");
    }

    public void stopReceiving() {
        isReceivingActive = false;

        HttpURLConnection c = currentReceiveConn;
        currentReceiveConn = null;
        if (c != null) {
            new Thread(() -> {
                try {
                    c.disconnect();
                } catch (Exception ignored) {}
            }).start();
        }

        if (receiveThread != null) {
            receiveThread.interrupt();
            receiveThread = null;
        }
        instances.remove(topic);
        Log.d(TAG, "Receiving stopped for topic: " + topic);
    }

    public void publishMessage(String message, boolean notifyUser) {
        if (!NtfyMessageType.isManagedMessage(message)) {
            Log.w(TAG, "Aborting publish: message is outside of managed Ntfy types: " + message);
            return;
        }

        if (!swimminsparrow.ricordamelo.core.sos.SOSContactsManager.hasConfiguredContacts(context)) {
            Log.w(TAG, "No connected contacts found. Aborting publish.");
            return;
        }

        // Ensure receiver stream is active to listen for incoming delivery ACKs
        startReceiving();

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String key = SettingsDB.getInstance(context).getSecurePairingKey();
                String encryptedMessage = CryptoUtils.encrypt(message, key);
                if (encryptedMessage == null) throw new Exception("Encryption failed");

                URL url = new URL(swimminsparrow.ricordamelo.config.Constants.getNtfyTopicUrl(topic));
                conn = (HttpURLConnection) url.openConnection();
                swimminsparrow.ricordamelo.core.utils.ForceIPv4SSLSocketFactory.applyTo(conn);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(encryptedMessage.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    os.flush();
                }

                int responseCode = conn.getResponseCode();
                boolean success = responseCode >= 200 && responseCode < 300;
                
                if (success) {
                    swimminsparrow.ricordamelo.core.sos.SOSContactsManager.updateLastMessage(context, topic, NtfyMessageType.extractContent(message), "SENT");
                    if (notifyUser) {
                        showPublishResultNotification(context, true, NtfyMessageType.extractContent(message));
                    }
                } else {
                    Log.w(TAG, "Publish returned error code " + responseCode + ", queuing to outbox.");
                    swimminsparrow.ricordamelo.core.data.dao.SOSOutboxDao.getInstance(context).addPendingMessage(topic, message, System.currentTimeMillis());
                    swimminsparrow.ricordamelo.core.sos.SOSOutboxWorker.scheduleRetry(context);
                    if (notifyUser) {
                        showQueuedOfflineNotification(context, NtfyMessageType.extractContent(message));
                    }
                }

                Log.d(TAG, "Published to " + topic + " with response code: " + responseCode);

            } catch (Exception e) {
                Log.e(TAG, "Publish failed, queuing in offline outbox", e);
                swimminsparrow.ricordamelo.core.data.dao.SOSOutboxDao.getInstance(context).addPendingMessage(topic, message, System.currentTimeMillis());
                swimminsparrow.ricordamelo.core.sos.SOSOutboxWorker.scheduleRetry(context);
                if (notifyUser) {
                    showQueuedOfflineNotification(context, NtfyMessageType.extractContent(message));
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    public void sendDirectSignal(String targetTopic, String rawMessage, String customKey) {
        if (targetTopic == null || rawMessage == null) return;
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String key = customKey;
                if (key == null) {
                    swimminsparrow.ricordamelo.core.sos.SOSContact c =
                            swimminsparrow.ricordamelo.core.sos.SOSContactsManager.getContactByTopic(context, targetTopic);
                    if (c != null) key = c.getKey();
                }
                if (key == null) key = SettingsDB.getInstance(context).getSecurePairingKey();
                String encryptedMessage = CryptoUtils.encrypt(rawMessage, key);
                if (encryptedMessage == null) {
                    Log.e(TAG, "Encryption failed for direct signal, aborting transmission.");
                    return;
                }
                byte[] bytesToSend = encryptedMessage.getBytes(java.nio.charset.StandardCharsets.UTF_8);

                URL url = new URL(swimminsparrow.ricordamelo.config.Constants.getNtfyTopicUrl(targetTopic));
                conn = (HttpURLConnection) url.openConnection();
                swimminsparrow.ricordamelo.core.utils.ForceIPv4SSLSocketFactory.applyTo(conn);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("TTL", "1m");
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytesToSend);
                }
                conn.getResponseCode();
            } catch (Exception e) {
                Log.e(TAG, "Failed to send direct signal: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    public void pingContact(String targetTopic) {
        String myUuid = SettingsDB.getInstance(context).getUserUUID();
        String myKey = SettingsDB.getInstance(context).getSecurePairingKey();
        if (myUuid == null || targetTopic == null || targetTopic.equals(myUuid)) return;
        sendDirectSignal(targetTopic, "PING_SOS:" + myUuid + (myKey != null ? ":" + myKey : ""), null);
    }

    private void showSOSNotification(Context context, String username, String message) {
        AppNotificationManager.getInstance().showSosAlert(context, username, this.topic, message);
    }

    private void showImOkNotification(Context context, String username, String message) {
        AppNotificationManager.getInstance().showImOk(context, username, this.topic, message);
    }

    private void showPublishResultNotification(Context context, boolean success, String message) {
        AppNotificationManager.getInstance().showPublishResult(context, success, message);
    }

    public void showQueuedOfflineNotification(Context context, String message) {
        AppNotificationManager.getInstance().showQueuedOffline(context, message);
    }

}
