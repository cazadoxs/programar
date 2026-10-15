package com.focusguard.blocker.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;

import com.focusguard.blocker.R;
import com.focusguard.blocker.data.Platform;
import com.focusguard.blocker.data.Prefs;
import com.focusguard.blocker.data.StatsRepository;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The whole product lives in this class: an event-driven accessibility
 * service that watches only the three target apps (enforced both by the
 * {@code android:packageNames} filter in accessibility_service_config.xml
 * and again here), detects Shorts/Reels/TikTok-feed screens and backs the
 * user out of them.
 *
 * <p>Design choices that keep this battery-cheap:
 * <ul>
 *   <li>No polling, no foreground service, no wake locks — the service is
 *   pure reaction to {@link AccessibilityEvent}s the system already decided
 *   to deliver.</li>
 *   <li>Bursts of content-changed events (very common while scrolling) are
 *   coalesced with a short debounce before the (bounded) node-tree walk
 *   runs.</li>
 *   <li>A per-platform cool-down avoids re-triggering the back/home action
 *   or the stats counter dozens of times for what is really one "the user
 *   opened a Short" moment.</li>
 * </ul>
 */
public class BlockerAccessibilityService extends AccessibilityService {

    private static final long DEBOUNCE_MS = 220L;
    private static final long BLOCK_COOLDOWN_MS = 3500L;
    private static final long ESCALATE_AFTER_MS = 2500L;
    private static final int ESCALATE_ATTEMPT_THRESHOLD = 3;

    private static final long WHOLE_APP_COOLDOWN_MS = 1500L;

    private static volatile boolean running = false;

    // Held in a field: SharedPreferences keeps only a weak reference to listeners.
    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingCheck;

    private final Map<Platform, Long> lastBlockAt = new HashMap<>();
    private final Map<Platform, Integer> attemptStreak = new HashMap<>();
    private final Map<Platform, Long> streakStartedAt = new HashMap<>();

    private Prefs prefs;
    private StatsRepository stats;

    public static boolean isRunning() {
        return running;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = true;
        prefs = new Prefs(this);
        stats = new StatsRepository(this);
        refreshServiceInfo();
        prefsListener = (sp, key) -> refreshServiceInfo();
        prefs.registerListener(prefsListener);
    }

    /**
     * Narrows event delivery to the built-in platforms plus whatever apps the user chose, so the
     * service stays idle (and battery-cheap) for every other app even though any app can be guarded.
     */
    private void refreshServiceInfo() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info == null || prefs == null) return;
        Set<String> packages = new LinkedHashSet<>(Arrays.asList(Platform.allMonitoredPackages()));
        packages.addAll(prefs.getGuardedPackages());
        info.packageNames = packages.toArray(new String[0]);
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;

        String pkgName = pkg.toString();
        if (pkgName.equals(getPackageName())) return;
        if (prefs == null) prefs = new Prefs(this);

        Platform platform = Platform.forPackage(pkgName);
        if (platform != null) {
            if (!prefs.isPlatformEnabled(platform)) return;
        } else {
            if (!prefs.isGuardEnabled(pkgName)) return;
            int mode = prefs.getGuardMode(pkgName);
            if (mode == 0) return;
            platform = Platform.custom(pkgName);
            if (mode == Prefs.MODE_WHOLE) {
                handleWholeApp(platform);
                return;
            }
        }

        // Coalesce bursts: cancel any pending check and schedule a fresh one.
        if (pendingCheck != null) handler.removeCallbacks(pendingCheck);
        final Platform target = platform;
        final String targetPkg = pkgName;
        pendingCheck = () -> runCheck(target, targetPkg);
        handler.postDelayed(pendingCheck, DEBOUNCE_MS);
    }

    /** "Block the whole app" mode: the moment the app shows up, go home. */
    private void handleWholeApp(Platform platform) {
        long now = System.currentTimeMillis();
        Long last = lastBlockAt.get(platform);
        if (last != null && (now - last) < WHOLE_APP_COOLDOWN_MS) return;
        lastBlockAt.put(platform, now);
        if (stats == null) stats = new StatsRepository(this);
        stats.recordBlock(platform);
        if (prefs.isOverlayEnabled()) showFeedback();
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    /** Root of the guarded app's window, or null if the user is not looking at that app. */
    private AccessibilityNodeInfo findRootFor(String pkg) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            if (pkg.equals(String.valueOf(root.getPackageName()))) return root;
            root.recycle();
            return null;
        }
        // Some apps (TikTok among them) intermittently report no active-window root; fall back to
        // the interactive windows list.
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) return null;
        for (AccessibilityWindowInfo w : windows) {
            if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            AccessibilityNodeInfo r = w.getRoot();
            if (r == null) continue;
            if (pkg.equals(String.valueOf(r.getPackageName()))) return r;
            r.recycle();
        }
        return null;
    }

    private void runCheck(Platform platform, String pkg) {
        AccessibilityNodeInfo root = findRootFor(pkg);
        if (root == null) return;

        CharSequence className = null; // Window class name is not reliably available post-hoc;
        // the detector falls back to the node-tree walk in that case, which is the
        // common path anyway.
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        boolean blocked;
        try {
            blocked = ShortsDetector.isBlockedContent(platform, root, className, dm.widthPixels, dm.heightPixels);
        } finally {
            // recycle() is a no-op on modern API levels but still correct on older ones.
            root.recycle();
        }

        if (blocked) {
            handleBlocked(platform);
        } else {
            attemptStreak.put(platform, 0);
        }
    }

    private void handleBlocked(Platform platform) {
        long now = System.currentTimeMillis();
        Long last = lastBlockAt.get(platform);
        boolean isNewSession = last == null || (now - last) > BLOCK_COOLDOWN_MS;

        if (isNewSession) {
            lastBlockAt.put(platform, now);
            attemptStreak.put(platform, 0);
            streakStartedAt.put(platform, now);
            if (stats == null) stats = new StatsRepository(this);
            stats.recordBlock(platform);
            if (prefs == null) prefs = new Prefs(this);
            if (prefs.isOverlayEnabled()) {
                showFeedback();
            }
        }

        int attempts = attemptStreak.getOrDefault(platform, 0) + 1;
        attemptStreak.put(platform, attempts);

        Long streakStart = streakStartedAt.get(platform);
        boolean stuckTooLong = streakStart != null && (now - streakStart) > ESCALATE_AFTER_MS;

        if (attempts >= ESCALATE_ATTEMPT_THRESHOLD && stuckTooLong) {
            // Safety valve: repeated back presses are not clearing the screen
            // (e.g. the user re-opened a Short right after we backed out).
            // Go all the way home instead of looping forever.
            performGlobalAction(GLOBAL_ACTION_HOME);
            attemptStreak.put(platform, 0);
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK);
        }
    }

    private void showFeedback() {
        handler.post(() -> Toast.makeText(getApplicationContext(),
                R.string.overlay_message_default, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onInterrupt() {
        // Nothing to release; we hold no long-lived resources.
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        running = false;
        if (prefs != null && prefsListener != null) prefs.unregisterListener(prefsListener);
        if (pendingCheck != null) handler.removeCallbacks(pendingCheck);
        return super.onUnbind(intent);
    }
}
