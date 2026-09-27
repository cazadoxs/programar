package com.focusguard.blocker.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "focusguard.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE_EVENTS = "block_events";
    public static final String COL_ID = "id";
    public static final String COL_TS = "ts";
    public static final String COL_DAY = "day";
    public static final String COL_PLATFORM = "platform";

    private static DbHelper instance;

    public static synchronized DbHelper get(Context context) {
        if (instance == null) {
            instance = new DbHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_EVENTS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_TS + " INTEGER NOT NULL, " +
                COL_DAY + " TEXT NOT NULL, " +
                COL_PLATFORM + " TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_events_day ON " + TABLE_EVENTS + "(" + COL_DAY + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // No schema changes yet.
    }
}
