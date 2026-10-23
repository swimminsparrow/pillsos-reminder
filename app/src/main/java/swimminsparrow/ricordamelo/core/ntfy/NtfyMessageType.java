package swimminsparrow.ricordamelo.core.ntfy;

/**
 * Formal specification of all supported message types transmitted over the Ntfy transport layer.
 * Any incoming message outside of these managed protocol types will be rejected and discarded.
 */
public enum NtfyMessageType {

    /**
     * Delivery acknowledgement for an SOS or check-in message.
     * Payload format: "ACK_SOS" or "ACK_SOS:<senderUuid>"
     */
    ACK_SOS("ACK_SOS"),

    /**
     * Remote contact disconnection notification.
     * Payload format: "DISCONNECT_SOS" or "DISCONNECT_SOS:<senderTopic>"
     */
    DISCONNECT_SOS("DISCONNECT_SOS"),

    /**
     * Contact display name update broadcast.
     * Payload format: "NAME_UPDATE_SOS:<newName>:<senderUuid>"
     */
    NAME_UPDATE_SOS("NAME_UPDATE_SOS:"),

    /**
     * "I'm OK" / "Sto Bene" safe status check-in event.
     * Payload format: "IM_OK_SOS:<message>"
     */
    IM_OK("IM_OK_SOS:"),

    /**
     * Emergency SOS alarm event (missed medication, fall detection, manual test, etc.).
     * Payload format: "ALARM_SOS:<message>"
     */
    SOS_ALARM("ALARM_SOS:"),

    /**
     * Heartbeat / status check ping.
     * Payload format: "PING_SOS:<senderUuid>"
     */
    PING_SOS("PING_SOS:"),

    /**
     * Heartbeat / status check pong response.
     * Payload format: "PONG_SOS:<senderUuid>"
     */
    PONG_SOS("PONG_SOS:"),

    /**
     * Signal indicating peer is no longer paired (e.g. data cleared or contact deleted).
     * Payload format: "NOT_PAIRED_SOS:<senderUuid>"
     */
    NOT_PAIRED_SOS("NOT_PAIRED_SOS:");

    private final String prefix;

    NtfyMessageType(String prefix) {
        this.prefix = prefix;
    }

    public String getIdentifier() {
        return prefix;
    }

    /**
     * Resolves the message type from a decrypted payload based strictly on protocol prefixes.
     *
     * @param payload The decrypted payload string.
     * @return The matched NtfyMessageType, or null if the payload is not a managed message type.
     */
    public static NtfyMessageType resolve(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return null;
        }
        String trimmed = payload.trim();

        if (trimmed.startsWith(ACK_SOS.prefix)) {
            return ACK_SOS;
        }
        if (trimmed.startsWith(DISCONNECT_SOS.prefix)) {
            return DISCONNECT_SOS;
        }
        if (trimmed.startsWith(NAME_UPDATE_SOS.prefix)) {
            return NAME_UPDATE_SOS;
        }
        if (trimmed.startsWith(IM_OK.prefix)) {
            return IM_OK;
        }
        if (trimmed.startsWith(SOS_ALARM.prefix)) {
            return SOS_ALARM;
        }
        if (trimmed.startsWith(PING_SOS.prefix)) {
            return PING_SOS;
        }
        if (trimmed.startsWith(PONG_SOS.prefix)) {
            return PONG_SOS;
        }
        if (trimmed.startsWith(NOT_PAIRED_SOS.prefix)) {
            return NOT_PAIRED_SOS;
        }

        // Unrecognized / unmanaged message
        return null;
    }

    /**
     * Extracts the user-facing text content, stripping protocol event prefixes.
     */
    public static String extractContent(String payload) {
        if (payload == null) return "";
        String trimmed = payload.trim();
        if (trimmed.startsWith(IM_OK.prefix)) {
            return trimmed.substring(IM_OK.prefix.length()).trim();
        }
        if (trimmed.startsWith(SOS_ALARM.prefix)) {
            return trimmed.substring(SOS_ALARM.prefix.length()).trim();
        }
        return trimmed;
    }

    /**
     * Checks if a decrypted payload is among the supported/managed message types.
     */
    public static boolean isManagedMessage(String payload) {
        return resolve(payload) != null;
    }
}
