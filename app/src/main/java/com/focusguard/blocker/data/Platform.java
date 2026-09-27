package com.focusguard.blocker.data;

import com.focusguard.blocker.R;

/**
 * The three platforms FocusGuard knows how to police. Package names cover the
 * regional/alias APKs seen in the wild (TikTok ships under two package ids).
 *
 * <p>This is a plain, singleton-based "typesafe enum" rather than a real
 * {@code enum}: the app's build pipeline dexes with an older toolchain that
 * chokes on the synthetic {@code values()/valueOf()} methods javac emits for
 * real enums, so we avoid them entirely.
 */
public final class Platform {

    public static final Platform YOUTUBE = new Platform(
            new String[]{"com.google.android.youtube"},
            R.string.app_youtube, R.string.app_youtube_desc,
            R.drawable.ic_play, R.color.yt_color, "yt");

    public static final Platform INSTAGRAM = new Platform(
            new String[]{"com.instagram.android"},
            R.string.app_instagram, R.string.app_instagram_desc,
            R.drawable.ic_camera, R.color.ig_color, "ig");

    public static final Platform TIKTOK = new Platform(
            new String[]{
                    "com.zhiliaoapp.musically",
                    "com.zhiliaoapp.musically.go",
                    "com.ss.android.ugc.trill",
                    "com.ss.android.ugc.aweme"
            },
            R.string.app_tiktok, R.string.app_tiktok_desc,
            R.drawable.ic_music, R.color.tt_color, "tt");

    private static final Platform[] VALUES = {YOUTUBE, INSTAGRAM, TIKTOK};

    public final String primaryPackage;
    public final int labelRes;
    public final int descRes;
    public final int iconRes;
    public final int colorRes;
    public final String storageKey;
    private final String[] packages;

    private Platform(String[] packages, int labelRes, int descRes, int iconRes, int colorRes, String storageKey) {
        this.packages = packages;
        this.primaryPackage = packages[0];
        this.labelRes = labelRes;
        this.descRes = descRes;
        this.iconRes = iconRes;
        this.colorRes = colorRes;
        this.storageKey = storageKey;
    }

    public static Platform[] values() {
        return VALUES.clone();
    }

    /** All package identifiers that should be treated as this platform. */
    public String[] allPackages() {
        return packages;
    }

    public static Platform forPackage(String packageName) {
        if (packageName == null) return null;
        for (Platform p : VALUES) {
            for (String pkg : p.packages) {
                if (pkg.equals(packageName)) return p;
            }
        }
        return null;
    }

    public static String[] allMonitoredPackages() {
        java.util.List<String> all = new java.util.ArrayList<>();
        for (Platform p : VALUES) {
            for (String pkg : p.packages) all.add(pkg);
        }
        return all.toArray(new String[0]);
    }
}
