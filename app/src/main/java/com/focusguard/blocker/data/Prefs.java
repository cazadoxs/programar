package com.focusguard.blocker.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Thin wrapper around SharedPreferences. Everything FocusGuard ever persists
 * lives here or in {@link StatsRepository} — both are purely local, there is
 * no network code anywhere in the app.
 */
public class Prefs {

    private static final String FILE = "focusguard_prefs";

    private static final String KEY_ONBOARDED = "onboarded";
    private static final String KEY_OVERLAY_ENABLED = "overlay_enabled";
    private static final String KEY_NOTIF_ENABLED = "notif_enabled";
    private static final String KEY_PIN_ENABLED = "pin_enabled";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_DISABLE_REQUEST_AT = "disable_request_at";
    private static final String KEY_AVG_SECONDS = "avg_short_seconds";
    private static final String KEY_LAST_ACTIVE_DAY = "last_active_day";
    private static final String KEY_STREAK = "streak_days";

    private static final String PREFIX_APP_ENABLED = "app_enabled_";

    public static final long TAMPER_DELAY_MS = 15 * 60 * 1000L;
    public static final int DEFAULT_AVG_SECONDS = 34;

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean isOnboarded() {
        return sp.getBoolean(KEY_ONBOARDED, false);
    }

    public void setOnboarded(boolean value) {
        sp.edit().putBoolean(KEY_ONBOARDED, value).apply();
    }

    public boolean isPlatformEnabled(Platform platform) {
        return sp.getBoolean(PREFIX_APP_ENABLED + platform.storageKey, true);
    }

    public void setPlatformEnabled(Platform platform, boolean enabled) {
        sp.edit().putBoolean(PREFIX_APP_ENABLED + platform.storageKey, enabled).apply();
    }

    public boolean isOverlayEnabled() {
        return sp.getBoolean(KEY_OVERLAY_ENABLED, true);
    }

    public void setOverlayEnabled(boolean enabled) {
        sp.edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply();
    }

    public boolean isNotifEnabled() {
        return sp.getBoolean(KEY_NOTIF_ENABLED, true);
    }

    public void setNotifEnabled(boolean enabled) {
        sp.edit().putBoolean(KEY_NOTIF_ENABLED, enabled).apply();
    }

    public boolean isPinEnabled() {
        return sp.getBoolean(KEY_PIN_ENABLED, false);
    }

    public void setPinEnabled(boolean enabled) {
        sp.edit().putBoolean(KEY_PIN_ENABLED, enabled).apply();
    }

    public boolean hasPin() {
        return sp.contains(KEY_PIN_HASH);
    }

    public void setPin(String rawPin) {
        sp.edit().putString(KEY_PIN_HASH, hash(rawPin)).apply();
    }

    public boolean checkPin(String rawPin) {
        String stored = sp.getString(KEY_PIN_HASH, null);
        return stored != null && stored.equals(hash(rawPin));
    }

    public void clearPin() {
        sp.edit().remove(KEY_PIN_HASH).putBoolean(KEY_PIN_ENABLED, false).apply();
    }

    /** Starts (or reads) the 15-minute cool-down window before protection can be turned off. */
    public long getOrStartDisableRequest() {
        long at = sp.getLong(KEY_DISABLE_REQUEST_AT, 0);
        if (at == 0) {
            at = System.currentTimeMillis();
            sp.edit().putLong(KEY_DISABLE_REQUEST_AT, at).apply();
        }
        return at;
    }

    public void clearDisableRequest() {
        sp.edit().remove(KEY_DISABLE_REQUEST_AT).apply();
    }

    public int getAvgShortSeconds() {
        return sp.getInt(KEY_AVG_SECONDS, DEFAULT_AVG_SECONDS);
    }

    /** Call once per day the user opens the app with protection on; used for the streak counter. */
    public int touchStreakForToday() {
        String today = todayKey();
        String last = sp.getString(KEY_LAST_ACTIVE_DAY, null);
        int streak = sp.getInt(KEY_STREAK, 0);
        if (today.equals(last)) {
            return streak;
        }
        if (last != null && isYesterday(last, today)) {
            streak = streak + 1;
        } else {
            streak = 1;
        }
        sp.edit().putString(KEY_LAST_ACTIVE_DAY, today).putInt(KEY_STREAK, streak).apply();
        return streak;
    }

    public int getStreak() {
        return sp.getInt(KEY_STREAK, 0);
    }

    private static String todayKey() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
    }

    private static boolean isYesterday(String lastDay, String todayDay) {
        try {
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
            long last = fmt.parse(lastDay).getTime();
            long today = fmt.parse(todayDay).getTime();
            long diffDays = Math.round((today - last) / 86400000.0);
            return diffDays == 1;
        } catch (Exception e) {
            return false;
        }
    }

    private static String hash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return value;
        }
    }
}
