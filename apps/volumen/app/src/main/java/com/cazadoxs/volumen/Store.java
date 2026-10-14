package com.cazadoxs.volumen;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/** Saved levels: one for the whole phone and one per app, as a percentage (100 = untouched). */
final class Store {
    private static final String FILE = "volumen";
    private static final String GLOBAL = "global";
    private static final String APP_PREFIX = "app:";
    private static final String KNOWN = "known_apps";
    private static final String ENABLED = "enabled";

    private final SharedPreferences prefs;

    Store(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    int global() {
        return prefs.getInt(GLOBAL, 100);
    }

    void setGlobal(int percent) {
        prefs.edit().putInt(GLOBAL, clamp(percent)).apply();
    }

    int app(String pkg) {
        return prefs.getInt(APP_PREFIX + pkg, 100);
    }

    void setApp(String pkg, int percent) {
        prefs.edit().putInt(APP_PREFIX + pkg, clamp(percent)).apply();
    }

    boolean enabled() {
        return prefs.getBoolean(ENABLED, false);
    }

    void setEnabled(boolean on) {
        prefs.edit().putBoolean(ENABLED, on).apply();
    }

    Set<String> knownApps() {
        return new TreeSet<>(prefs.getStringSet(KNOWN, new HashSet<>()));
    }

    void rememberApp(String pkg) {
        Set<String> known = knownApps();
        if (known.add(pkg)) prefs.edit().putStringSet(KNOWN, known).apply();
    }

    /**
     * Percent to decibels on a perceptual curve (amplitude = p²): 50 % is -12 dB, 10 % is -40 dB,
     * 1 % is -80 dB. That leaves plenty of fine control right at the quiet end.
     */
    static float toDb(int percent) {
        if (percent >= 100) return 0f;
        if (percent <= 0) return -96f;
        return (float) (40.0 * Math.log10(percent / 100.0));
    }

    private static int clamp(int p) {
        return Math.max(0, Math.min(100, p));
    }
}
