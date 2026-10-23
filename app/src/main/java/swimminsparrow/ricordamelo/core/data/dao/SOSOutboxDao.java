package swimminsparrow.ricordamelo.core.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import swimminsparrow.ricordamelo.core.data.AvvertimiDBHelper;
import swimminsparrow.ricordamelo.core.data.models.SOSOutboxMessage;

/**
 * Data Access Object for outgoing SOS messages queued offline.
 */
public class SOSOutboxDao {
    private static final String TAG = "SOSOutboxDao";

    public static final String TABLE_OUTBOX = "sos_outbox";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_TOPIC = "topic";
    public static final String COLUMN_MESSAGE = "message";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_ATTEMPTS = "attempts";
    public static final String COLUMN_ENCRYPTION_KEY = "encryption_key";
    public static final String COLUMN_TTL = "ttl";

    public static final String SQL_CREATE_OUTBOX =
            "CREATE TABLE IF NOT EXISTS " + TABLE_OUTBOX + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_TOPIC + " TEXT NOT NULL, " +
                    COLUMN_MESSAGE + " TEXT NOT NULL, " +
                    COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
                    COLUMN_ATTEMPTS + " INTEGER DEFAULT 0, " +
                    COLUMN_ENCRYPTION_KEY + " TEXT, " +
                    COLUMN_TTL + " TEXT" +
                    ");";

    private final Context context;
    private final AvvertimiDBHelper dbHelper;
    private static volatile SOSOutboxDao sInstance;

    private SOSOutboxDao(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = new AvvertimiDBHelper(this.context);
    }

    public static SOSOutboxDao getInstance(Context context) {
        if (sInstance == null) {
            synchronized (SOSOutboxDao.class) {
                if (sInstance == null) {
                    sInstance = new SOSOutboxDao(context);
                }
            }
        }
        return sInstance;
    }

    public synchronized long addPendingMessage(String topic, String message, long timestamp) {
        return addPendingMessage(topic, message, timestamp, null, null);
    }

    public synchronized long addPendingMessage(String topic, String message, long timestamp, String encryptionKey, String ttl) {
        if (topic == null || message == null || message.trim().isEmpty()) return -1;
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(COLUMN_TOPIC, topic);
            cv.put(COLUMN_MESSAGE, message);
            cv.put(COLUMN_TIMESTAMP, timestamp > 0 ? timestamp : System.currentTimeMillis());
            cv.put(COLUMN_ATTEMPTS, 0);
            if (encryptionKey != null) {
                cv.put(COLUMN_ENCRYPTION_KEY, encryptionKey);
            }
            if (ttl != null) {
                cv.put(COLUMN_TTL, ttl);
            }
            long id = db.insert(TABLE_OUTBOX, null, cv);
            Log.d(TAG, "Enqueued pending message ID: " + id + " for topic: " + topic);
            return id;
        } catch (Exception e) {
            Log.e(TAG, "Error inserting message into outbox", e);
            return -1;
        }
    }

    public synchronized List<SOSOutboxMessage> getPendingMessages() {
        List<SOSOutboxMessage> list = new ArrayList<>();
        Cursor cursor = null;
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            cursor = db.query(TABLE_OUTBOX, null, null, null, null, null, COLUMN_TIMESTAMP + " ASC");
            if (cursor != null && cursor.moveToFirst()) {
                int idIdx = cursor.getColumnIndex(COLUMN_ID);
                int topIdx = cursor.getColumnIndex(COLUMN_TOPIC);
                int msgIdx = cursor.getColumnIndex(COLUMN_MESSAGE);
                int tsIdx = cursor.getColumnIndex(COLUMN_TIMESTAMP);
                int attIdx = cursor.getColumnIndex(COLUMN_ATTEMPTS);
                int keyIdx = cursor.getColumnIndex(COLUMN_ENCRYPTION_KEY);
                int ttlIdx = cursor.getColumnIndex(COLUMN_TTL);

                do {
                    long id = idIdx >= 0 ? cursor.getLong(idIdx) : -1;
                    String top = topIdx >= 0 ? cursor.getString(topIdx) : "";
                    String msg = msgIdx >= 0 ? cursor.getString(msgIdx) : "";
                    long ts = tsIdx >= 0 ? cursor.getLong(tsIdx) : 0;
                    int attempts = attIdx >= 0 ? cursor.getInt(attIdx) : 0;
                    String encKey = keyIdx >= 0 ? cursor.getString(keyIdx) : null;
                    String ttlVal = ttlIdx >= 0 ? cursor.getString(ttlIdx) : null;
                    list.add(new SOSOutboxMessage(id, top, msg, ts, attempts, encKey, ttlVal));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying pending outbox messages", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return list;
    }

    public synchronized void removeMessage(long id) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.delete(TABLE_OUTBOX, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
            Log.d(TAG, "Removed outbox message ID: " + id);
        } catch (Exception e) {
            Log.e(TAG, "Error removing outbox message: " + id, e);
        }
    }

    public synchronized void incrementAttempts(long id) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.execSQL("UPDATE " + TABLE_OUTBOX + " SET " + COLUMN_ATTEMPTS + " = " + COLUMN_ATTEMPTS + " + 1 WHERE " + COLUMN_ID + " = ?", new Object[]{id});
        } catch (Exception e) {
            Log.e(TAG, "Error incrementing attempts for ID: " + id, e);
        }
    }

    public synchronized boolean hasPendingMessages() {
        Cursor cursor = null;
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            cursor = db.rawQuery("SELECT 1 FROM " + TABLE_OUTBOX + " LIMIT 1", null);
            return cursor != null && cursor.moveToFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error checking pending outbox messages", e);
            return false;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    public synchronized void clearExpiredMessages(long maxAgeMillis) {
        try {
            long cutoff = System.currentTimeMillis() - maxAgeMillis;
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            int deleted = db.delete(TABLE_OUTBOX, COLUMN_TIMESTAMP + " < ?", new String[]{String.valueOf(cutoff)});
            if (deleted > 0) {
                Log.d(TAG, "Pruned " + deleted + " expired outbox messages");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error pruning expired outbox messages", e);
        }
    }
}
