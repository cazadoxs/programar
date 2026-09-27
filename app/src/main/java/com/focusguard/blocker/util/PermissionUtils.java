package com.focusguard.blocker.util;

import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import com.focusguard.blocker.service.BlockerAccessibilityService;

public class PermissionUtils {

    public static boolean isAccessibilityServiceEnabled(Context context) {
        String expected = context.getPackageName() + "/" + BlockerAccessibilityService.class.getName();
        String enabledServices = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabledServices)) return false;
        for (String service : enabledServices.split(":")) {
            if (service.equalsIgnoreCase(expected)) return true;
        }
        return false;
    }

    public static boolean areNotificationsEnabled(Context context) {
        android.app.NotificationManager nm =
                (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }
}
