package com.focusguard.blocker.service;

import android.view.accessibility.AccessibilityNodeInfo;

import com.focusguard.blocker.data.Platform;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Heuristic, on-device detector for Shorts / Reels / the TikTok video feed.
 *
 * <p>Android gives third-party apps no public, stable API to ask "is this
 * screen a Shorts player". The only signal available without root is the
 * accessibility node tree, so this class walks it looking for view id names,
 * text and content-descriptions that are characteristic of each platform's
 * short-video surface, while explicitly staying quiet on screens that are
 * long-form video, stories, DMs or anything else.
 *
 * <p>The walk is bounded (node count + depth) so a run costs at most a few
 * hundred cheap field reads — this keeps CPU/battery impact negligible even
 * though it runs on every content-changed event from the target apps.
 */
final class ShortsDetector {

    private static final int MAX_NODES = 500;

    // --- YouTube -------------------------------------------------------

    private static final String[] YT_ID_MARKERS = {
            "reel_player", "reel_watch", "reel_recycler", "shorts_container",
            "shorts_player", "reels_player_navigation", "shorts_shelf"
    };
    private static final String[] YT_CLASS_MARKERS = {
            "ShortsPlayerActivity", "ReelWatchActivity", "ShortsPlayback", "ShortsFragment"
    };
    private static final String[] YT_TAB_SELECTED_MARKERS = {"shorts"};

    // --- Instagram -------------------------------------------------------

    private static final String[] IG_ID_MARKERS = {
            "clips_viewer", "clips_tab", "reel_viewer", "reels_tray", "clips_swipe_refresh",
            "reel_feed_timeline", "clips_grid_item"
    };
    private static final String[] IG_CLASS_MARKERS = {
            "ClipsViewerActivity", "ReelViewerFragment", "ClipsFragment"
    };
    private static final String[] IG_TAB_SELECTED_MARKERS = {"reels"};

    // --- TikTok -------------------------------------------------------

    private static final String[] TT_ID_MARKERS = {
            "vv_video_container", "feed_recycler_view", "tux_tab_feed", "video_feed_container",
            "feed_view_pager", "video_view_wrapper"
    };
    private static final String[] TT_HOME_TAB_MARKERS = {"home"};
    private static final String[] TT_SAFE_TAB_MARKERS = {"inbox", "friends", "profile", "search", "message"};

    private ShortsDetector() {
    }

    static boolean isBlockedContent(Platform platform, AccessibilityNodeInfo root, CharSequence windowClassName) {
        if (root == null) return false;
        String className = windowClassName == null ? "" : windowClassName.toString();
        if (platform == Platform.YOUTUBE) return isYoutubeShorts(root, className);
        if (platform == Platform.INSTAGRAM) return isInstagramReels(root, className);
        if (platform == Platform.TIKTOK) return isTikTokFeed(root, className);
        return false;
    }

    private static boolean isYoutubeShorts(AccessibilityNodeInfo root, String className) {
        if (containsAny(className, YT_CLASS_MARKERS)) return true;
        Walker w = new Walker(root);
        return w.findAny(YT_ID_MARKERS, null) || w.findSelectedTab(YT_TAB_SELECTED_MARKERS);
    }

    private static boolean isInstagramReels(AccessibilityNodeInfo root, String className) {
        if (containsAny(className, IG_CLASS_MARKERS)) return true;
        Walker w = new Walker(root);
        return w.findAny(IG_ID_MARKERS, null) || w.findSelectedTab(IG_TAB_SELECTED_MARKERS);
    }

    private static boolean isTikTokFeed(AccessibilityNodeInfo root, String className) {
        Walker w = new Walker(root);
        // If a "safe" tab (inbox, profile, search...) is the selected one, never block —
        // this is what keeps DMs and the profile usable.
        if (w.findSelectedTab(TT_SAFE_TAB_MARKERS)) return false;
        if (w.findAny(TT_ID_MARKERS, null)) return true;
        return w.findSelectedTab(TT_HOME_TAB_MARKERS);
    }

    private static boolean containsAny(String haystack, String[] needles) {
        if (haystack == null) return false;
        String lower = haystack.toLowerCase(Locale.US);
        for (String n : needles) {
            if (lower.contains(n)) return true;
        }
        return false;
    }

    /** Bounded breadth-first walk of the accessibility tree. */
    private static class Walker {
        private final AccessibilityNodeInfo root;

        Walker(AccessibilityNodeInfo root) {
            this.root = root;
        }

        boolean findAny(String[] idMarkers, String[] textMarkers) {
            ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
            queue.add(root);
            int visited = 0;
            while (!queue.isEmpty() && visited < MAX_NODES) {
                AccessibilityNodeInfo node = queue.poll();
                if (node == null) continue;
                visited++;

                String id = node.getViewIdResourceName();
                if (id != null && containsAny(id, idMarkers)) {
                    return true;
                }
                if (textMarkers != null) {
                    CharSequence text = node.getText();
                    CharSequence desc = node.getContentDescription();
                    if (containsAny(text == null ? null : text.toString(), textMarkers)) return true;
                    if (containsAny(desc == null ? null : desc.toString(), textMarkers)) return true;
                }

                if (visited < MAX_NODES) {
                    int childCount = Math.min(node.getChildCount(), 12);
                    for (int i = 0; i < childCount; i++) {
                        AccessibilityNodeInfo child = node.getChild(i);
                        if (child != null) queue.add(child);
                    }
                }
            }
            return false;
        }

        /** Looks for a selected/checked tab-like node whose text or content-desc matches. */
        boolean findSelectedTab(String[] markers) {
            ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
            queue.add(root);
            int visited = 0;
            while (!queue.isEmpty() && visited < MAX_NODES) {
                AccessibilityNodeInfo node = queue.poll();
                if (node == null) continue;
                visited++;

                if (node.isSelected() || node.isChecked()) {
                    CharSequence text = node.getText();
                    CharSequence desc = node.getContentDescription();
                    if (containsAny(text == null ? null : text.toString(), markers)) return true;
                    if (containsAny(desc == null ? null : desc.toString(), markers)) return true;
                }

                int childCount = Math.min(node.getChildCount(), 12);
                for (int i = 0; i < childCount; i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) queue.add(child);
                }
            }
            return false;
        }
    }
}
