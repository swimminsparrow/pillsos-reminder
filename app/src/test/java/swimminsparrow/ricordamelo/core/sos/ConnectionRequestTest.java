package swimminsparrow.ricordamelo.core.sos;

import android.content.Intent;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class ConnectionRequestTest {

    @Test
    public void testSerializationToAndFromIntent() {
        long now = System.currentTimeMillis();
        ConnectionRequest original = new ConnectionRequest(
                "req-test-uuid", "Alice", "alice_uuid_123", "secret_key_456", "avatar_base64", now);

        Intent intent = new Intent();
        original.toIntent(intent);

        ConnectionRequest restored = ConnectionRequest.fromIntent(intent);
        assertNotNull(restored);
        assertEquals("req-test-uuid", restored.reqId);
        assertEquals("Alice", restored.name);
        assertEquals("alice_uuid_123", restored.topic);
        assertEquals("secret_key_456", restored.key);
        assertEquals("avatar_base64", restored.avatar);
        assertEquals(now, restored.timestamp);
    }

    @Test
    public void testFromJson_validPayload() throws Exception {
        long ts = System.currentTimeMillis();
        JSONObject json = new JSONObject();
        json.put("req_id", "req-abc");
        json.put("name", "Bob");
        json.put("topic", "bob_topic");
        json.put("avatar", "bob_avatar");
        json.put("ts", ts);

        ConnectionRequest req = ConnectionRequest.fromJson(json, "fallback_key");
        assertNotNull(req);
        assertEquals("req-abc", req.reqId);
        assertEquals("Bob", req.name);
        assertEquals("bob_topic", req.topic);
        assertEquals("fallback_key", req.key);
        assertEquals("bob_avatar", req.avatar);
        assertEquals(ts, req.timestamp);
        assertFalse(req.isExpired());
    }

    @Test
    public void testFromJson_missingTopicReturnsNull() throws Exception {
        JSONObject json = new JSONObject();
        json.put("name", "NoTopic");
        assertNull(ConnectionRequest.fromJson(json, "fallback_key"));
        assertNull(ConnectionRequest.fromJson(null, "fallback_key"));
    }

    @Test
    public void testExpirationLogic() {
        long now = System.currentTimeMillis();
        ConnectionRequest fresh = new ConnectionRequest("1", "A", "t1", "k1", "", now);
        assertFalse(fresh.isExpired());

        ConnectionRequest expired = new ConnectionRequest("2", "B", "t2", "k2", "", now - 125000);
        assertTrue(expired.isExpired());
    }
}
