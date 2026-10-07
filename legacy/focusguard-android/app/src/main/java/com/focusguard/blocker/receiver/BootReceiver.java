package com.focusguard.blocker.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Android re-enables an accessibility service automatically after reboot if
 * the user had it turned on — nothing to do here for the blocking engine
 * itself. This receiver exists as the hook point for the optional daily
 * summary notification, which is (re)scheduled on boot.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        com.focusguard.blocker.util.DailySummaryScheduler.schedule(context);
    }
}
