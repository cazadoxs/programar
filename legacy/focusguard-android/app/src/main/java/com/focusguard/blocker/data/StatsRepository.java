package com.focusguard.blocker.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * All block-event persistence. Rows never leave the device: there is no sync,
 * no analytics SDK and no network permission in this app.
 */
public class StatsRepository {

    private static final SimpleDateFormat DAY_FMT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private final DbHelper dbHelper;

    public StatsRepository(Context context) {
        this.dbHelper = DbHelper.get(context);
    }

    public void recordBlock(Platform platform) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        long now = System.currentTimeMillis();
        cv.put(DbHelper.COL_TS, now);
        cv.put(DbHelper.COL_DAY, dayKey(now));
        cv.put(DbHelper.COL_PLATFORM, platform.storageKey);
        db.insert(DbHelper.TABLE_EVENTS, null, cv);
    }

    public int countForDay(String day) {
        return countWhere(DbHelper.COL_DAY + "=?", new String[]{day});
    }

    public int countToday() {
        return countForDay(dayKey(System.currentTimeMillis()));
    }

    public int countTotal() {
        return countWhere(null, null);
    }

    public int countForPlatformToday(Platform platform) {
        return countWhere(DbHelper.COL_DAY + "=? AND " + DbHelper.COL_PLATFORM + "=?",
                new String[]{dayKey(System.currentTimeMillis()), platform.storageKey});
    }

    public int countForPlatformTotal(Platform platform) {
        return countWhere(DbHelper.COL_PLATFORM + "=?", new String[]{platform.storageKey});
    }

    /** Oldest-first counts for the last {@code days} days, including days with zero events. */
    public int[] countsForLastDays(int days) {
        int[] result = new int[days];
        long now = System.currentTimeMillis();
        for (int i = 0; i < days; i++) {
            long ts = now - (long) (days - 1 - i) * 86400000L;
            result[i] = countForDay(dayKey(ts));
        }
        return result;
    }

    public List<PlatformCount> countsByPlatformTotal() {
        List<PlatformCount> out = new ArrayList<>();
        for (Platform p : Platform.values()) {
            out.add(new PlatformCount(p, countForPlatformTotal(p)));
        }
        return out;
    }

    public void resetAll() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(DbHelper.TABLE_EVENTS, null, null);
    }

    private int countWhere(String selection, String[] args) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DbHelper.TABLE_EVENTS, new String[]{"COUNT(*)"}, selection, args,
                null, null, null);
        try {
            if (c.moveToFirst()) return c.getInt(0);
            return 0;
        } finally {
            c.close();
        }
    }

    public static String dayKey(long timestampMs) {
        return DAY_FMT.format(new Date(timestampMs));
    }

    public static class PlatformCount {
        public final Platform platform;
        public final int count;

        public PlatformCount(Platform platform, int count) {
            this.platform = platform;
            this.count = count;
        }
    }
}
