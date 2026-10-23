package swimminsparrow.ricordamelo.core.sos;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class SOSContactTest {

    @Test
    public void testJsonSerializationRoundTrip() throws Exception {
        SOSContact original = new SOSContact(
                "contact_1",
                "Mamma",
                "topic_mamma_123",
                "secret_key_abc",
                "MUTUAL",
                "APPROVED",
                1700000000000L,
                1700000005000L,
                "Sto bene!",
                1700000010000L,
                "base64_avatar_data"
        );

        JSONObject json = original.toJsonObject();
        assertNotNull(json);

        SOSContact restored = SOSContact.fromJsonObject(json);
        assertNotNull(restored);
        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getName(), restored.getName());
        assertEquals(original.getTopic(), restored.getTopic());
        assertEquals(original.getKey(), restored.getKey());
        assertEquals(original.getType(), restored.getType());
        assertEquals(original.getStatus(), restored.getStatus());
        assertEquals(original.getAddedTimestamp(), restored.getAddedTimestamp());
        assertEquals(original.getLastPingTimestamp(), restored.getLastPingTimestamp());
        assertEquals(original.getLastMessage(), restored.getLastMessage());
        assertEquals(original.getLastMessageTimestamp(), restored.getLastMessageTimestamp());
        assertEquals(original.getAvatarBase64(), restored.getAvatarBase64());
    }

    @Test
    public void testFormattedTimestamp() {
        long now = System.currentTimeMillis();
        SOSContact contact = new SOSContact("id", "Mario", "topic", "key", "MUTUAL", now);
        contact.setLastMessage("SOS: Caduta rilevata!");
        contact.setLastMessageTimestamp(now);

        String formatted = contact.getFormattedTimestamp();
        assertNotNull(formatted);
        assertFalse(formatted.isEmpty());
        // For today's message, it should be HH:mm format
        assertTrue("Formatted timestamp should contain colon for today: " + formatted, formatted.contains(":"));
    }
}
