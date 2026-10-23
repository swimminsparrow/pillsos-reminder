package swimminsparrow.ricordamelo.core.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import swimminsparrow.ricordamelo.core.data.models.Farmaco;
import swimminsparrow.ricordamelo.core.data.dao.SOSMessageDao;
import swimminsparrow.ricordamelo.core.data.dao.SOSOutboxDao;

/**
 * SQLite open-helper for the app database.
 *
 * VERSION HISTORY
 *  1 – original schema (AvvertimiTable only)
 *  2 – added AppSettingsTable; migrated previousAlarms from SharedPreferences
 *  3 – added SOS messaging and outbox tables (sos_sent_history, sos_received_history, sos_outbox)
 */
public class AvvertimiDBHelper extends SQLiteOpenHelper {

    public static final int    DATABASE_VERSION = 3;
    public static final String DATABASE_NAME    = "AvvertimiDatabase";

    public AvvertimiDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(Farmaco.FarmacoTable.SQL_CREATE);
        db.execSQL(SOSMessageDao.SQL_CREATE_SENT);
        db.execSQL(SOSMessageDao.SQL_CREATE_RECEIVED);
        db.execSQL(SOSOutboxDao.SQL_CREATE_OUTBOX);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            db.execSQL(SOSMessageDao.SQL_CREATE_SENT);
            db.execSQL(SOSMessageDao.SQL_CREATE_RECEIVED);
            db.execSQL(SOSOutboxDao.SQL_CREATE_OUTBOX);
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Keep current schema when downgrading during development.
    }
}