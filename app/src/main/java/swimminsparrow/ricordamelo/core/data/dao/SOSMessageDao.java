package swimminsparrow.ricordamelo.core.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import swimminsparrow.ricordamelo.core.data.AvvertimiDBHelper;
import swimminsparrow.ricordamelo.core.data.models.SOSMessage;

/**
 * Data Access Object for SOS message history (sent and received messages).
 * Follows the singleton pattern aligned with FarmacoDao.
 */
public class SOSMessageDao {
    private static final String TAG = "SOSMessageDao";

    public static final String TABLE_SENT = "sos_sent_history";
    public static final String TABLE_RECEIVED = "sos_received_history";

    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_TOPIC = "contact_topic";
    public static final String COLUMN_MESSAGE = "message";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_ACK_DELIVERED = "ack_delivered";

    public static final String SQL_CREATE_SENT =
            "CREATE TABLE IF NOT EXISTS " + TABLE_SENT + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_TOPIC + " TEXT NOT NULL, " +
                    COLUMN_MESSAGE + " TEXT NOT NULL, " +
                    COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
                    COLUMN_ACK_DELIVERED + " INTEGER DEFAULT 0" +
                    ");";

    public static final String SQL_CREATE_RECEIVED =
            "CREATE TABLE IF NOT EXISTS " + TABLE_RECEIVED + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_TOPIC + " TEXT NOT NULL, " +
                    COLUMN_MESSAGE + " TEXT NOT NULL, " +
                    COLUMN_TIMESTAMP + " INTEGER NOT NULL" +
                    ");";

    // 1 Month = 30 Days in milliseconds
    private static final long RETENTION_PERIOD_MS = 30L * 24 * 60 * 60 * 1000L;
    private static final int MAX_MESSAGES_PER_TOPIC = 100;

    private final Context context;
    private final AvvertimiDBHelper dbHelper;
    private static volatile SOSMessageDao sInstance;

    private SOSMessageDao(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = new AvvertimiDBHelper(this.context);
    }

    /**
     * Returns the singleton instance, creating it on first call.
     * Uses application context internally to prevent memory leaks.
     */
    public static SOSMessageDao getInstance(Context context) {
        if (sInstance == null) {
            synchronized (SOSMessageDao.class) {
                if (sInstance == null) {
                    sInstance = new SOSMessageDao(context);
                }
            }
        }
        return sInstance;
    }

    public synchronized void addMessage(String topic, String message, String direction, long timestamp) {
        if ("SENT".equalsIgnoreCase(direction)) {
            addSentMessage(topic, message, timestamp);
        } else {
            addReceivedMessage(topic, message, timestamp);
        }
    }

    public synchronized void addSentMessage(String topic, String message, long timestamp) {
        insertIntoTable(TABLE_SENT, topic, message, timestamp);
    }

    public synchronized void addReceivedMessage(String topic, String message, long timestamp) {
        insertIntoTable(TABLE_RECEIVED, topic, message, timestamp);
    }

    private synchronized void insertIntoTable(String tableName, String topic, String message, long timestamp) {
        if (topic == null || message == null || message.trim().isEmpty()) return;

        SQLiteDatabase db = null;
        try {
            db = dbHelper.getWritableDatabase();

            // 1. Cleanup messages older than 30 days
            long cutoffTs = System.currentTimeMillis() - RETENTION_PERIOD_MS;
            db.delete(tableName, COLUMN_TIMESTAMP + " < ?", new String[]{String.valueOf(cutoffTs)});

            // 2. Insert new message
            ContentValues cv = new ContentValues();
            cv.put(COLUMN_TOPIC, topic);
            cv.put(COLUMN_MESSAGE, message);
            cv.put(COLUMN_TIMESTAMP, timestamp > 0 ? timestamp : System.currentTimeMillis());
            if (TABLE_SENT.equals(tableName)) {
                cv.put(COLUMN_ACK_DELIVERED, 0);
            }
            db.insert(tableName, null, cv);

            // 3. Enforce maximum 100 messages for this topic in this table
            String pruneSql = "DELETE FROM " + tableName +
                    " WHERE " + COLUMN_TOPIC + " = ? AND " + COLUMN_ID + " NOT IN (" +
                    "SELECT " + COLUMN_ID + " FROM " + tableName +
                    " WHERE " + COLUMN_TOPIC + " = ? ORDER BY " + COLUMN_TIMESTAMP + " DESC LIMIT " + MAX_MESSAGES_PER_TOPIC + ")";
            db.execSQL(pruneSql, new String[]{topic, topic});

        } catch (Exception e) {
            Log.e(TAG, "Error inserting message into " + tableName, e);
        }
    }

    public synchronized void markMessageDelivered(String topic) {
        if (topic == null) return;
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(COLUMN_ACK_DELIVERED, 1);
            int count = db.update(TABLE_SENT, cv, COLUMN_TOPIC + " = ?", new String[]{topic});
            if (count == 0) {
                count = db.update(TABLE_SENT, cv, COLUMN_ACK_DELIVERED + " = 0", null);
            }
            Log.d(TAG, "markMessageDelivered executed for topic " + topic + ", count: " + count);
        } catch (Exception e) {
            Log.e(TAG, "Error marking message delivered for topic: " + topic, e);
        }
    }

    public synchronized void clearHistory(String topic) {
        if (topic == null) return;
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.delete(TABLE_SENT, COLUMN_TOPIC + " = ?", new String[]{topic});
            db.delete(TABLE_RECEIVED, COLUMN_TOPIC + " = ?", new String[]{topic});
        } catch (Exception e) {
            Log.e(TAG, "Error clearing message history for topic: " + topic, e);
        }
    }

    public synchronized List<SOSMessage> getMessageHistory(String topic) {
        List<SOSMessage> list = new ArrayList<>();
        if (topic == null) return list;

        Cursor cursor = null;
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            // Cleanup expired messages (> 30 days) from both tables
            long cutoffTs = System.currentTimeMillis() - RETENTION_PERIOD_MS;
            db.delete(TABLE_SENT, COLUMN_TIMESTAMP + " < ?", new String[]{String.valueOf(cutoffTs)});
            db.delete(TABLE_RECEIVED, COLUMN_TIMESTAMP + " < ?", new String[]{String.valueOf(cutoffTs)});

            // Combined timeline query using UNION ALL
            String unionQuery = "SELECT " + COLUMN_ID + ", " + COLUMN_TOPIC + ", " + COLUMN_MESSAGE + ", 'SENT' AS direction, " + COLUMN_TIMESTAMP + ", " + COLUMN_ACK_DELIVERED + " AS ack_delivered FROM " + TABLE_SENT + " WHERE " + COLUMN_TOPIC + " = ?" +
                    " UNION ALL " +
                    "SELECT " + COLUMN_ID + ", " + COLUMN_TOPIC + ", " + COLUMN_MESSAGE + ", 'RECEIVED' AS direction, " + COLUMN_TIMESTAMP + ", 0 AS ack_delivered FROM " + TABLE_RECEIVED + " WHERE " + COLUMN_TOPIC + " = ?" +
                    " ORDER BY " + COLUMN_TIMESTAMP + " DESC LIMIT " + MAX_MESSAGES_PER_TOPIC;

            cursor = db.rawQuery(unionQuery, new String[]{topic, topic});

            if (cursor != null && cursor.moveToFirst()) {
                int idIdx = cursor.getColumnIndex(COLUMN_ID);
                int topicIdx = cursor.getColumnIndex(COLUMN_TOPIC);
                int msgIdx = cursor.getColumnIndex(COLUMN_MESSAGE);
                int dirIdx = cursor.getColumnIndex("direction");
                int tsIdx = cursor.getColumnIndex(COLUMN_TIMESTAMP);
                int ackIdx = cursor.getColumnIndex("ack_delivered");

                do {
                    long id = idIdx >= 0 ? cursor.getLong(idIdx) : -1;
                    String top = topicIdx >= 0 ? cursor.getString(topicIdx) : topic;
                    String msg = msgIdx >= 0 ? cursor.getString(msgIdx) : "";
                    String dir = dirIdx >= 0 ? cursor.getString(dirIdx) : "RECEIVED";
                    long ts = tsIdx >= 0 ? cursor.getLong(tsIdx) : 0;
                    boolean ack = ackIdx >= 0 && cursor.getInt(ackIdx) == 1;

                    list.add(new SOSMessage(id, top, msg, dir, ts, ack));
                } while (cursor.moveToNext());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error fetching SOS message history", e);
        } finally {
            if (cursor != null) cursor.close();
        }

        return list;
    }
}
