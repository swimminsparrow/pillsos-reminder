package swimminsparrow.ricordamelo.core.data.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;

import swimminsparrow.ricordamelo.core.data.AvvertimiDBHelper;
import swimminsparrow.ricordamelo.core.data.models.Farmaco;
import swimminsparrow.ricordamelo.core.utils.AppExecutors;

public class FarmacoDao {

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private final Context context;
    private final AvvertimiDBHelper dbHelper;
    private static volatile FarmacoDao sInstance;

    private FarmacoDao(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = new AvvertimiDBHelper(context);
    }

    private void notifyAlarmsChanged() {
        if (context != null) {
            AppExecutors.getInstance().diskIO().execute(() -> {
                swimminsparrow.ricordamelo.services.ReminderScheduler.refreshAlarms(context);
            });
        }
    }

    /**
     * Returns the singleton, creating it on the first call.
     * Uses {@code context.getApplicationContext()} internally to avoid leaks.
     * Call this at least once from {@code Application.onCreate()}.
     */
    public static FarmacoDao getInstance(Context context) {
        if (sInstance == null) {
            synchronized (FarmacoDao.class) {
                if (sInstance == null) {
                    sInstance = new FarmacoDao(context);
                }
            }
        }
        return sInstance;
    }

    /**
     * Returns the already-created singleton.
     *
     * @throws IllegalStateException if {@link #getInstance(Context)} has not been called yet.
     */
    public static FarmacoDao getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException(
                    "FarmacoDao not initialised – call getInstance(Context) first.");
        }
        return sInstance;
    }


    // The one place cursor→Farmaco mapping lives
    private Farmaco fromCursor(Cursor c) {
        int id             = c.getInt(c.getColumnIndexOrThrow(Farmaco.FarmacoTable.ID));
        String nome        = c.getString(c.getColumnIndexOrThrow(Farmaco.FarmacoTable.NOME));
        ArrayList<String> ore    = splitList(c, Farmaco.FarmacoTable.ORE);
        ArrayList<String> minuti = splitList(c, Farmaco.FarmacoTable.MINUTI);
        ArrayList<String> ds     = splitList(c, Farmaco.FarmacoTable.DAYS);
        ArrayList<Integer> days  = new ArrayList<>();
        for (String s : ds) days.add(Integer.parseInt(s));
        String imagePath   = c.getString(c.getColumnIndexOrThrow(Farmaco.FarmacoTable.IMAGEPATH));
        long whenToFinish  = c.getLong(c.getColumnIndexOrThrow(Farmaco.FarmacoTable.WHENTOFINISH));
        int forever        = c.getInt(c.getColumnIndexOrThrow(Farmaco.FarmacoTable.FOREVER));
        return new Farmaco(id, nome, ore, minuti, imagePath, days, whenToFinish, forever);
    }

    private ArrayList<String> splitList(Cursor c, String col) {
        return new ArrayList<>(Arrays.asList(
                TextUtils.split(c.getString(c.getColumnIndexOrThrow(col)), ",")));
    }

    private ContentValues toValues(Farmaco f, boolean includeId) {
        ContentValues cv = new ContentValues();
        if (includeId && f.getId() > 0) cv.put(Farmaco.FarmacoTable.ID, f.getId());
        cv.put(Farmaco.FarmacoTable.NOME,         f.getNome());
        cv.put(Farmaco.FarmacoTable.ORE,          TextUtils.join(",", f.getOre()));
        cv.put(Farmaco.FarmacoTable.MINUTI,       TextUtils.join(",", f.getMinuti()));
        cv.put(Farmaco.FarmacoTable.DAYS,         TextUtils.join(",", f.getDays()));
        cv.put(Farmaco.FarmacoTable.IMAGEPATH,    f.getImagePath());
        cv.put(Farmaco.FarmacoTable.WHENTOFINISH, f.getWhenToFinish());
        cv.put(Farmaco.FarmacoTable.FOREVER,      f.getForever());
        return cv;
    }

    public ArrayList<Farmaco> getAll() {
        ArrayList<Farmaco> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(Farmaco.FarmacoTable.TABLE_NAME, null, null, null,
                null, null, Farmaco.FarmacoTable.ID + " ASC");
        try {
            while (c.moveToNext()) list.add(fromCursor(c));
        } finally {
            if (c != null) c.close();
        }
        return list;
    }

    public Farmaco getById(int id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(Farmaco.FarmacoTable.TABLE_NAME, null,
                Farmaco.FarmacoTable.ID + "=?", new String[]{String.valueOf(id)},
                null, null, null);
        try {
            return (c != null && c.moveToFirst()) ? fromCursor(c) : null;
        } finally {
            if (c != null) c.close();
        }
    }

    public long insert(Farmaco f) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        boolean includeId = f.getId() > 0;
        long newId = db.insert(Farmaco.FarmacoTable.TABLE_NAME, null, toValues(f, includeId));
        if (newId != -1 && f.getId() <= 0) {
            f.setId((int) newId);
        }
        notifyAlarmsChanged();
        return newId;
    }

    public void update(int id, Farmaco f) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.update(Farmaco.FarmacoTable.TABLE_NAME, toValues(f, false),
                Farmaco.FarmacoTable.ID + "=?", new String[]{String.valueOf(id)});
        notifyAlarmsChanged();
    }

    public void delete(int id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(Farmaco.FarmacoTable.TABLE_NAME,
                Farmaco.FarmacoTable.ID + "=?", new String[]{String.valueOf(id)});
        notifyAlarmsChanged();
    }

    /** Returns {@code true} if a farmaco with the given name already exists. */
    public boolean existsByName(String nome) {
        if (nome == null) return false;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(Farmaco.FarmacoTable.TABLE_NAME, new String[]{Farmaco.FarmacoTable.ID},
                Farmaco.FarmacoTable.NOME + "=?", new String[]{nome},
                null, null, null, "1");
        try {
            return c != null && c.moveToFirst();
        } finally {
            if (c != null) c.close();
        }
    }
}