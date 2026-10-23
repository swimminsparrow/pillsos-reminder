package swimminsparrow.ricordamelo.core.data.models;

/**
 * Model representing an outgoing SOS or status message queued in the local outbox
 * waiting to be sent when network connectivity becomes available.
 */
public class SOSOutboxMessage {
    private final long id;
    private final String topic;
    private final String message;
    private final long timestamp;
    private final int attempts;
    private final String encryptionKey;
    private final String ttl;

    public SOSOutboxMessage(long id, String topic, String message, long timestamp, int attempts, String encryptionKey, String ttl) {
        this.id = id;
        this.topic = topic;
        this.message = message;
        this.timestamp = timestamp;
        this.attempts = attempts;
        this.encryptionKey = encryptionKey;
        this.ttl = ttl;
    }

    public long getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public String getTtl() {
        return ttl;
    }
}
