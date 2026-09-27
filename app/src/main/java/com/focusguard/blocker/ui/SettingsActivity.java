package com.focusguard.blocker.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.focusguard.blocker.R;
import com.focusguard.blocker.data.Platform;
import com.focusguard.blocker.data.Prefs;
import com.focusguard.blocker.data.StatsRepository;
import com.focusguard.blocker.util.PermissionUtils;

public class SettingsActivity extends android.app.Activity {

    private static final int REQ_PIN_SETUP = 81;

    private Prefs prefs;
    private View rowOverlay;
    private View rowPin;
    private View rowPermAccessibility;
    private View rowPermNotif;
    private Switch pinSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = new Prefs(this);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        buildPlatformRows();

        rowOverlay = findViewById(R.id.rowOverlay);
        bindSwitchRow(rowOverlay, R.string.settings_overlay_toggle, R.string.settings_overlay_toggle_desc,
                prefs.isOverlayEnabled(), (btn, checked) -> prefs.setOverlayEnabled(checked));

        rowPin = findViewById(R.id.rowPin);
        pinSwitch = bindSwitchRow(rowPin, R.string.settings_pin_toggle, R.string.settings_pin_toggle_desc,
                prefs.isPinEnabled(), null);
        pinSwitch.setOnCheckedChangeListener((btn, checked) -> onPinSwitchChanged(checked));

        rowPermAccessibility = findViewById(R.id.rowPermAccessibility);
        rowPermNotif = findViewById(R.id.rowPermNotif);

        findViewById(R.id.resetStatsButton).setOnClickListener(v -> confirmResetStats());
    }

    private void buildPlatformRows() {
        LinearLayout list = findViewById(R.id.platformList);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Platform platform : Platform.values()) {
            View row = inflater.inflate(R.layout.item_platform_row, list, false);
            ((ImageView) row.findViewById(R.id.rowIcon)).setImageResource(platform.iconRes);
            row.findViewById(R.id.rowIcon).getBackground().mutate().setTint(getColor(platform.colorRes));
            ((TextView) row.findViewById(R.id.rowTitle)).setText(platform.labelRes);
            ((TextView) row.findViewById(R.id.rowDesc)).setText(platform.descRes);
            row.findViewById(R.id.rowCount).setVisibility(View.GONE);

            Switch rowSwitch = row.findViewById(R.id.rowSwitch);
            rowSwitch.setChecked(prefs.isPlatformEnabled(platform));
            rowSwitch.setOnCheckedChangeListener((btn, checked) -> prefs.setPlatformEnabled(platform, checked));

            list.addView(row);
        }
    }

    private interface OnToggle {
        void onChanged(Switch button, boolean checked);
    }

    private Switch bindSwitchRow(View row, int titleRes, int descRes, boolean checked, OnToggle listener) {
        ((TextView) row.findViewById(R.id.switchRowTitle)).setText(titleRes);
        ((TextView) row.findViewById(R.id.switchRowDesc)).setText(descRes);
        Switch sw = row.findViewById(R.id.switchRowSwitch);
        sw.setChecked(checked);
        if (listener != null) {
            sw.setOnCheckedChangeListener((btn, isChecked) -> listener.onChanged(sw, isChecked));
        }
        return sw;
    }

    private void onPinSwitchChanged(boolean checked) {
        if (checked) {
            Intent intent = new Intent(this, PinActivity.class);
            intent.putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_SETUP);
            startActivityForResult(intent, REQ_PIN_SETUP);
        } else {
            prefs.clearPin();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PIN_SETUP && resultCode != RESULT_OK) {
            pinSwitch.setChecked(false);
        }
    }

    private void confirmResetStats() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.settings_reset_stats_confirm)
                .setPositiveButton(R.string.settings_reset_stats, (d, w) -> {
                    new StatsRepository(this).resetAll();
                    d.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionRow(rowPermAccessibility, R.string.settings_permission_accessibility,
                PermissionUtils.isAccessibilityServiceEnabled(this),
                v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        updatePermissionRow(rowPermNotif, R.string.settings_permission_notifications,
                PermissionUtils.areNotificationsEnabled(this),
                v -> {
                    Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    startActivity(intent);
                });
    }

    private void updatePermissionRow(View row, int titleRes, boolean granted, View.OnClickListener grantAction) {
        ((TextView) row.findViewById(R.id.permRowTitle)).setText(titleRes);
        TextView status = row.findViewById(R.id.permRowStatus);
        status.setText(granted ? R.string.permission_granted : R.string.permission_grant);
        status.setBackgroundResource(granted ? R.drawable.bg_pill_success : R.drawable.bg_pill_danger);
        status.setOnClickListener(granted ? null : grantAction);
    }
}
