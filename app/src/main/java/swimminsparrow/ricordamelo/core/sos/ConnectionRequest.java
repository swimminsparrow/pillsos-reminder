package swimminsparrow.ricordamelo.core.sos;

import android.content.Intent;
import org.json.JSONObject;

/**
 * Encapsulates an incoming or outgoing connection request between peers.
 */
public class ConnectionRequest {

    public static final String EXTRA_REQUEST_ID = "req_id";
    public static final String EXTRA_REQUESTER_NAME = "requester_name";
    public static final String EXTRA_REQUESTER_TOPIC = "requester_topic";
    public static final String EXTRA_MASTER_KEY = "master_key";
    public static final String EXTRA_TIMESTAMP = "timestamp";
    public static final String EXTRA_REQUESTER_AVATAR = "requester_avatar";

    public final String reqId;
    public final String name;
    public final String topic;
    public final String key;
    public final String avatar;
    public final long timestamp;

    public ConnectionRequest(String reqId, String name, String topic, String key, String avatar, long timestamp) {
        this.reqId = reqId != null ? reqId : "";
        this.name = (name != null && !name.trim().isEmpty()) ? name : "Contatto SOS";
        this.topic = topic != null ? topic : "";
        this.key = key != null ? key : "";
        this.avatar = avatar != null ? avatar : "";
        this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
    }

    public boolean isExpired() {
        return ConnectionRequestManager.isRequestExpired(timestamp);
    }

    public void toIntent(Intent intent) {
        if (intent == null) return;
        intent.putExtra(EXTRA_REQUEST_ID, reqId);
        intent.putExtra(EXTRA_REQUESTER_NAME, name);
        intent.putExtra(EXTRA_REQUESTER_TOPIC, topic);
        intent.putExtra(EXTRA_MASTER_KEY, key);
        intent.putExtra(EXTRA_REQUESTER_AVATAR, avatar);
        intent.putExtra(EXTRA_TIMESTAMP, timestamp);
    }

    public static ConnectionRequest fromIntent(Intent intent) {
        if (intent == null) return null;
        String reqId = intent.getStringExtra(EXTRA_REQUEST_ID);
        String name = intent.getStringExtra(EXTRA_REQUESTER_NAME);
        String topic = intent.getStringExtra(EXTRA_REQUESTER_TOPIC);
        String key = intent.getStringExtra(EXTRA_MASTER_KEY);
        String avatar = intent.getStringExtra(EXTRA_REQUESTER_AVATAR);
        long ts = intent.getLongExtra(EXTRA_TIMESTAMP, 0);

        if (topic == null || topic.isEmpty()) return null;
        return new ConnectionRequest(reqId, name, topic, key, avatar, ts);
    }

    public static ConnectionRequest fromJson(JSONObject json, String fallbackKey) {
        if (json == null) return null;
        String reqId = json.optString("req_id", "");
        String name = json.optString("name", "Nuovo Utente");
        String topic = json.optString("topic", "");
        String key = json.optString("key", fallbackKey);
        String avatar = json.optString("avatar", "");
        long ts = json.optLong("ts", System.currentTimeMillis());

        if (topic.isEmpty()) return null;
        return new ConnectionRequest(reqId, name, topic, key, avatar, ts);
    }
}
