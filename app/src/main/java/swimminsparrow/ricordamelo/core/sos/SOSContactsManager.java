package swimminsparrow.ricordamelo.core.sos;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import swimminsparrow.ricordamelo.core.data.SettingsDB;
import swimminsparrow.ricordamelo.core.data.dao.SOSMessageDao;

/**
 * Unified manager for SOS Emergency Contacts.
 * Contacts are bidirectional (mutual) peers with zero dual-list discrepancy.
 */
public class SOSContactsManager {

    private static final String TAG = "SOSContactsManager";
    private static final String PREF_NAME = "sos_contacts_prefs";
    private static final String KEY_SOS_CONTACTS = "sos_contacts";
    private static final String KEY_HANDLED_REQUESTS = "handled_connection_requests";



    public static final String ACTION_SOS_CONTACTS_UPDATED = "swimminsparrow.ricordamelo.ACTION_SOS_CONTACTS_UPDATED";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void notifyContactsChanged(Context context) {
        if (context == null) return;
        try {
            android.content.Intent intent = new android.content.Intent(ACTION_SOS_CONTACTS_UPDATED);
            intent.setPackage(context.getPackageName());
            context.sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error sending contacts update broadcast", e);
        }
    }

    // ─── Request Deduplication ───────────────────────────────────────────────

    public static boolean isRequestHandled(Context context, String topic, long ts) {
        if (context == null || topic == null) return true;
        try {
            SharedPreferences prefs = getPrefs(context);
            String handledJson = prefs.getString(KEY_HANDLED_REQUESTS, "{}");
            JSONObject obj = new JSONObject(handledJson);
            long handledTs = obj.optLong(topic, 0);
            if (handledTs > 0 && (ts <= 0 || handledTs >= ts)) {
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking handled request", e);
        }
        return false;
    }

    public static void markRequestHandled(Context context, String topic, long ts) {
        if (context == null || topic == null) return;
        long recordTs = ts > 0 ? ts : System.currentTimeMillis();
        try {
            SharedPreferences prefs = getPrefs(context);
            String handledJson = prefs.getString(KEY_HANDLED_REQUESTS, "{}");
            JSONObject obj = new JSONObject(handledJson);
            obj.put(topic, recordTs);
            prefs.edit().putString(KEY_HANDLED_REQUESTS, obj.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error marking request handled", e);
        }
    }

    // ─── Core Contacts Storage & CRUD ────────────────────────────────────────

    public static synchronized List<SOSContact> getContacts(Context context) {
        if (context == null) return new ArrayList<>();
        return loadContacts(context);
    }

    public static synchronized void saveContacts(Context context, List<SOSContact> list) {
        saveContactsInternal(context, list);
        notifyContactsChanged(context);
    }

    public static synchronized void addContact(Context context, SOSContact contact) {
        if (context == null || contact == null || contact.getTopic() == null) return;
        List<SOSContact> list = getContacts(context);
        removeByTopic(list, contact.getTopic());
        list.add(0, contact);
        saveContacts(context, list);
    }

    public static synchronized boolean removeContact(Context context, String topic) {
        if (context == null || topic == null) return false;
        List<SOSContact> list = getContacts(context);
        boolean removed = removeByTopic(list, topic);
        if (removed) {
            saveContacts(context, list);
        }
        return removed;
    }

    public static SOSContact getContactByTopic(Context context, String topic) {
        if (context == null || topic == null) return null;
        for (SOSContact c : getContacts(context)) {
            if (topic.equals(c.getTopic())) return c;
        }
        return null;
    }

    public static boolean isContactPaired(Context context, String topic) {
        return getContactByTopic(context, topic) != null;
    }

    public static boolean hasConfiguredContacts(Context context) {
        if (context == null) return false;
        String json = getPrefs(context).getString(KEY_SOS_CONTACTS, "[]");
        return json != null && json.length() > 4;
    }

    public static void addMutualContact(Context context, String topic, String name, String key, String avatar) {
        if (context == null || topic == null || topic.trim().isEmpty()) return;
        SOSContact contact = new SOSContact(
                topic, name, topic, key, "MUTUAL", "APPROVED",
                System.currentTimeMillis(), System.currentTimeMillis(),
                "", 0, avatar != null ? avatar : "");
        addContact(context, contact);
    }

    public static synchronized void updatePingTimestamp(Context context, String topic, long ts) {
        if (context == null || topic == null) return;
        List<SOSContact> contacts = getContacts(context);
        boolean changed = false;
        for (SOSContact c : contacts) {
            if (topic.equals(c.getTopic())) {
                c.setLastPingTimestamp(ts);
                changed = true;
            }
        }
        if (changed) {
            saveContacts(context, contacts);
        }
    }

    public static synchronized void updateContactKey(Context context, String topic, String newKey) {
        if (context == null || topic == null || newKey == null || newKey.trim().isEmpty()) return;
        List<SOSContact> contacts = getContacts(context);
        boolean changed = false;
        for (SOSContact c : contacts) {
            if (topic.equals(c.getTopic())) {
                if (!newKey.equals(c.getKey())) {
                    c.setKey(newKey);
                    changed = true;
                }
            }
        }
        if (changed) {
            saveContacts(context, contacts);
        }
    }

    public static String getSenderNameForTopic(Context context, String topic) {
        if (context == null || topic == null) return null;
        SOSContact contact = getContactByTopic(context, topic);
        if (contact != null && contact.getName() != null && !contact.getName().trim().isEmpty()) {
            return contact.getName().trim();
        }
        return null;
    }


    // ─── Disconnection Synchronization ───────────────────────────────────────

    public static void disconnectContact(Context context, String topic) {
        if (context == null || topic == null) return;
        String myUuid = SettingsDB.getInstance(context).getUserUUID();

        // 1. Find master key of target contact
        SOSContact targetContact = getContactByTopic(context, topic);
        String masterKey = targetContact != null ? targetContact.getKey() : null;

        // 2. Publish disconnection signal to remote user with scope "ALL"
        swimminsparrow.ricordamelo.core.ntfy.NtfyManager.getInstance(context, topic).sendDisconnectionSignal(topic, masterKey, myUuid, "ALL");
        if (myUuid != null && !myUuid.equals(topic)) {
            swimminsparrow.ricordamelo.core.ntfy.NtfyManager.getInstance(context, myUuid).sendDisconnectionSignal(myUuid, masterKey, myUuid, "ALL");
        }

        // 3. Complete local removal
        removeContact(context, topic);
        swimminsparrow.ricordamelo.core.ntfy.NtfyManager.getInstance(context, topic).stopReceiving();

        // 4. Sync protection service
        swimminsparrow.ricordamelo.services.ProtectionService.sync(context);

        // 5. Broadcast UI update
        notifyContactsChanged(context);
    }



    public static void handleRemoteDisconnection(Context context, String streamTopic, String senderTopic, String scope, long msgTimeSec) {
        if (context == null) return;
        Log.d(TAG, "handleRemoteDisconnection streamTopic=" + streamTopic + " senderTopic=" + senderTopic + " scope=" + scope + " msgTimeSec=" + msgTimeSec);

        String myUuid = SettingsDB.getInstance(context).getUserUUID();
        String targetTopic = (senderTopic != null && !senderTopic.isEmpty() && !senderTopic.equals(myUuid)) ? senderTopic
                : ((streamTopic != null && !streamTopic.isEmpty() && !streamTopic.equals(myUuid)) ? streamTopic : null);

        if (targetTopic == null) {
            Log.d(TAG, "Ignoring handleRemoteDisconnection: no valid remote topic found");
            return;
        }

        SOSContact c = getContactByTopic(context, targetTopic);
        if (c == null) {
            Log.d(TAG, "Ignoring DISCONNECT_SOS: contact " + targetTopic + " not found locally");
            return;
        }

        // 1. If msgTimeSec > 0, ensure contact was not added AFTER this disconnection signal was dispatched
        if (msgTimeSec > 0) {
            long addedTs = c.getAddedTimestamp();
            if (addedTs > 0 && (addedTs / 1000L) >= msgTimeSec) {
                Log.d(TAG, "Ignoring stale DISCONNECT_SOS signal: contact was added at " + addedTs + " ms after disconnection msgTime " + msgTimeSec + " s");
                return;
            }
        }

        // 2. 30-second grace immunity guard for newly added contacts
        if ((System.currentTimeMillis() - c.getAddedTimestamp()) < 30000) {
            Log.d(TAG, "Ignoring DISCONNECT_SOS for " + targetTopic + ": contact was just added " + (System.currentTimeMillis() - c.getAddedTimestamp()) + "ms ago");
            return;
        }

        boolean removed = removeContact(context, targetTopic);

        if (removed) {
            swimminsparrow.ricordamelo.core.ntfy.NtfyManager.getInstance(context, targetTopic).stopReceiving();
        }

        swimminsparrow.ricordamelo.services.ProtectionService.sync(context);
        notifyContactsChanged(context);

        if (removed) {
            new android.os.Handler(context.getMainLooper()).post(() -> {
                android.widget.Toast.makeText(context, context.getString(swimminsparrow.ricordamelo.R.string.sos_contact_disconnected_toast), android.widget.Toast.LENGTH_SHORT).show();
                showDisconnectionNotification(context);
            });
        }
    }

    public static void handleNameUpdate(Context context, String senderUuid, String streamTopic, String newName) {
        if (context == null || newName == null || newName.trim().isEmpty()) return;
        Log.d(TAG, "handleNameUpdate senderUuid=" + senderUuid + " streamTopic=" + streamTopic + " newName=" + newName);

        String trimmed = newName.trim();
        List<SOSContact> contacts = getContacts(context);
        boolean updated = false;
        for (SOSContact c : contacts) {
            if ((senderUuid != null && senderUuid.equals(c.getTopic())) ||
                (streamTopic != null && streamTopic.equals(c.getTopic()))) {
                c.setName(trimmed);
                updated = true;
            }
        }
        if (updated) {
            saveContacts(context, contacts);
            Log.d(TAG, "Contact name updated to '" + trimmed + "' for uuid=" + senderUuid);
        }
    }

    // ─── Single-Pass Last Message & History Persistence ──────────────────────

    public static synchronized void updateLastMessage(Context context, String topic, String lastMessage, String direction) {
        if (context == null || topic == null || lastMessage == null) return;
        long now = System.currentTimeMillis();
        String myUuid = SettingsDB.getInstance(context).getUserUUID();

        List<SOSContact> contacts = getContacts(context);
        boolean updated = false;

        for (SOSContact c : contacts) {
            // When sending (topic == myUuid), record for all connected contacts
            // When receiving (topic == c.getTopic()), record for that specific sender
            if (topic.equals(c.getTopic()) || (myUuid != null && myUuid.equals(topic))) {
                c.setLastMessage(lastMessage);
                c.setLastMessageTimestamp(now);
                updated = true;
                SOSMessageDao.getInstance(context).addMessage(c.getTopic(), lastMessage, direction, now);
            }
        }

        if (updated) {
            saveContacts(context, contacts);
        } else {
            // Fallback: If no contact match was found yet, still record message history for topic
            SOSMessageDao.getInstance(context).addMessage(topic, lastMessage, direction, now);
        }
    }

    public static void showDisconnectionNotification(Context context) {
        if (context == null) return;
        try {
            int pendingFlags = android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE;

            android.content.Intent tapIntent = new android.content.Intent(context, swimminsparrow.ricordamelo.ui.alarms.MainActivity.class);
            tapIntent.putExtra("open_sos", true);
            tapIntent.putExtra("skip_splash", true);
            tapIntent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
            android.app.PendingIntent tapPendingIntent = android.app.PendingIntent.getActivity(context, 0, tapIntent, pendingFlags);

            String title = context.getString(swimminsparrow.ricordamelo.R.string.sos_disconnection_title);
            String body = context.getString(swimminsparrow.ricordamelo.R.string.sos_disconnection_body);

            boolean isSilent = SettingsDB.getInstance(context).getSilentMode();
            android.net.Uri soundUri = isSilent ? null : SettingsDB.getInstance(context).getSoundUri();

            boolean isVibro = SettingsDB.getInstance(context).getVibrationMode();
            long[] vibratePattern = isVibro ? new long[]{0, 300, 200, 300} : null;

            androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(context, swimminsparrow.ricordamelo.config.Constants.SOS_CHANNEL_ID)
                    .setSmallIcon(swimminsparrow.ricordamelo.R.drawable.icon)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                    .setSound(soundUri)
                    .setVibrate(vibratePattern)
                    .setContentIntent(tapPendingIntent)
                    .setAutoCancel(true);

            if (androidx.core.app.ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.NotificationManagerCompat.from(context).notify(1004, builder.build());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing disconnection notification", e);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static List<SOSContact> loadContacts(Context context) {
        List<SOSContact> result = new ArrayList<>();
        String jsonStr = getPrefs(context).getString(KEY_SOS_CONTACTS, "[]");
        try {
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                result.add(SOSContact.fromJsonObject(array.getJSONObject(i)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading contacts", e);
        }
        return result;
    }

    private static void saveContactsInternal(Context context, List<SOSContact> list) {
        try {
            JSONArray array = new JSONArray();
            for (SOSContact contact : list) {
                array.put(contact.toJsonObject());
            }
            getPrefs(context).edit().putString(KEY_SOS_CONTACTS, array.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving contacts", e);
        }
    }

    private static boolean removeByTopic(List<SOSContact> list, String topic) {
        if (topic == null) return false;
        boolean removed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (topic.equals(list.get(i).getTopic())) {
                list.remove(i);
                removed = true;
            }
        }
        return removed;
    }
}
