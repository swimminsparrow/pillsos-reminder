package swimminsparrow.ricordamelo.core.data.models;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SOSMessage {
    private long id;
    private String topic;
    private String message;
    private String direction; // "SENT" or "RECEIVED"
    private long timestamp;
    private boolean ackDelivered;

    public SOSMessage(long id, String topic, String message, String direction, long timestamp, boolean ackDelivered) {
        this.id = id;
        this.topic = topic;
        this.message = message;
        this.direction = direction;
        this.timestamp = timestamp;
        this.ackDelivered = ackDelivered;
    }

    public long getId() { return id; }
    public String getTopic() { return topic; }
    public String getMessage() { return message; }
    public String getDirection() { return direction; }
    public long getTimestamp() { return timestamp; }
    public boolean isAckDelivered() { return ackDelivered; }

    public boolean isSent() {
        return "SENT".equalsIgnoreCase(direction);
    }

    public String getFormattedTimestamp() {
        if (timestamp <= 0) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
