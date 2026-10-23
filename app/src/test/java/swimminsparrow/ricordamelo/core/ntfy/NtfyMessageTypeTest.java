package swimminsparrow.ricordamelo.core.ntfy;

import org.junit.Test;
import static org.junit.Assert.*;

public class NtfyMessageTypeTest {

    @Test
    public void testResolveAllKnownTypes() {
        assertEquals(NtfyMessageType.ACK_SOS, NtfyMessageType.resolve("ACK_SOS"));
        assertEquals(NtfyMessageType.ACK_SOS, NtfyMessageType.resolve("ACK_SOS:uuid123"));
        assertEquals(NtfyMessageType.DISCONNECT_SOS, NtfyMessageType.resolve("DISCONNECT_SOS"));
        assertEquals(NtfyMessageType.DISCONNECT_SOS, NtfyMessageType.resolve("DISCONNECT_SOS:topic456"));
        assertEquals(NtfyMessageType.NAME_UPDATE_SOS, NtfyMessageType.resolve("NAME_UPDATE_SOS:Mario:uuid789"));
        assertEquals(NtfyMessageType.IM_OK, NtfyMessageType.resolve("IM_OK_SOS:Mario sta bene"));
        assertEquals(NtfyMessageType.SOS_ALARM, NtfyMessageType.resolve("ALARM_SOS:Allarme caduta"));
    }

    @Test
    public void testResolveNullOrEmptyOrUnknown() {
        assertNull(NtfyMessageType.resolve(null));
        assertNull(NtfyMessageType.resolve(""));
        assertNull(NtfyMessageType.resolve("   "));
        assertNull(NtfyMessageType.resolve("RANDOM_TEXT_123"));
        assertNull(NtfyMessageType.resolve("hello world"));
    }

    @Test
    public void testExtractContent() {
        assertEquals("Mario sta bene", NtfyMessageType.extractContent("IM_OK_SOS:Mario sta bene"));
        assertEquals("Allarme farmaco!", NtfyMessageType.extractContent("ALARM_SOS:Allarme farmaco!"));
        assertEquals("raw text", NtfyMessageType.extractContent("raw text"));
        assertEquals("", NtfyMessageType.extractContent(null));
    }

    @Test
    public void testIsManagedMessage() {
        assertTrue(NtfyMessageType.isManagedMessage("IM_OK_SOS:ok"));
        assertTrue(NtfyMessageType.isManagedMessage("ALARM_SOS:help"));
        assertTrue(NtfyMessageType.isManagedMessage("ACK_SOS"));
        assertTrue(NtfyMessageType.isManagedMessage("DISCONNECT_SOS"));
        assertFalse(NtfyMessageType.isManagedMessage("UNKNOWN_PREFIX"));
        assertFalse(NtfyMessageType.isManagedMessage(null));
    }
}
