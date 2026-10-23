package swimminsparrow.ricordamelo.core.sos;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SOSContact {
    private String id;
    private String name;
    private String topic;
    private String key;
    private String type; // "RECEIVER" or "SENDER"
    private String status; // "APPROVED", "PENDING"
    private long addedTimestamp;
    private long lastPingTimestamp;
    private String lastMessage;
    private long lastMessageTimestamp;
    private String avatarBase64;

    public SOSContact(String id, String name, String topic, String key, String type, String status, long addedTimestamp, long lastPingTimestamp, String lastMessage, long lastMessageTimestamp, String avatarBase64) {
        this.id = id;
        this.name = name;
        this.topic = topic;
        this.key = key;
        this.type = type;
        this.status = status != null ? status : "APPROVED";
        this.addedTimestamp = addedTimestamp;
        this.lastPingTimestamp = lastPingTimestamp > 0 ? lastPingTimestamp : addedTimestamp;
        this.lastMessage = lastMessage != null ? lastMessage : "";
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.avatarBase64 = avatarBase64 != null ? avatarBase64 : "";
    }

    public SOSContact(String id, String name, String topic, String key, String type, long addedTimestamp) {
        this(id, name, topic, key, type, "APPROVED", addedTimestamp, addedTimestamp, "", 0, "");
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getTopic() { return topic; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public void setName(String name) { this.name = name; }
    public long getAddedTimestamp() { return addedTimestamp; }
    public long getLastPingTimestamp() { return lastPingTimestamp; }
    public void setLastPingTimestamp(long ts) { this.lastPingTimestamp = ts; }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public long getLastMessageTimestamp() { return lastMessageTimestamp; }
    public void setLastMessageTimestamp(long ts) { this.lastMessageTimestamp = ts; }
    public String getAvatarBase64() { return avatarBase64; }
    public void setAvatarBase64(String avatarBase64) { this.avatarBase64 = avatarBase64; }



    public String getFormattedTimestamp() {
        long ts = lastMessageTimestamp > 0 ? lastMessageTimestamp : addedTimestamp;
        if (ts <= 0) return "";

        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar msgTime = java.util.Calendar.getInstance();
        msgTime.setTimeInMillis(ts);

        boolean sameDay = now.get(java.util.Calendar.YEAR) == msgTime.get(java.util.Calendar.YEAR) &&
                now.get(java.util.Calendar.DAY_OF_YEAR) == msgTime.get(java.util.Calendar.DAY_OF_YEAR);

        if (sameDay) {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ts));
        }

        now.add(java.util.Calendar.DAY_OF_YEAR, -1);
        boolean yesterday = now.get(java.util.Calendar.YEAR) == msgTime.get(java.util.Calendar.YEAR) &&
                now.get(java.util.Calendar.DAY_OF_YEAR) == msgTime.get(java.util.Calendar.DAY_OF_YEAR);

        if (yesterday) {
            return "Ieri";
        }

        return new SimpleDateFormat("dd/MM", Locale.getDefault()).format(new Date(ts));
    }

    public JSONObject toJsonObject() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("topic", topic);
        json.put("key", key);
        json.put("type", type);
        json.put("status", status);
        json.put("ts", addedTimestamp);
        json.put("ping", lastPingTimestamp);
        json.put("lastMsg", lastMessage != null ? lastMessage : "");
        json.put("lastMsgTs", lastMessageTimestamp);
        json.put("avatar", avatarBase64 != null ? avatarBase64 : "");
        return json;
    }

    public static SOSContact fromJsonObject(JSONObject json) throws JSONException {
        return new SOSContact(
                json.optString("id", json.optString("topic")),
                json.optString("name", "Contatto"),
                json.optString("topic"),
                json.optString("key", ""),
                json.optString("type", "RECEIVER"),
                json.optString("status", "APPROVED"),
                json.optLong("ts", System.currentTimeMillis()),
                json.optLong("ping", System.currentTimeMillis()),
                json.optString("lastMsg", ""),
                json.optLong("lastMsgTs", 0),
                json.optString("avatar", "")
        );
    }
}

