package com.focusguard.blocker.ui;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import com.focusguard.blocker.R;
import com.focusguard.blocker.data.Platform;
import com.focusguard.blocker.data.Prefs;
import com.focusguard.blocker.data.StatsRepository;

import java.util.Locale;

public class MainActivity extends android.app.Activity {

    private static final int REQ_PIN_DISABLE = 71;

    private Prefs prefs;
    private StatsRepository stats;

    private ImageView protectionIcon;
    private TextView protectionTitle;
    private TextView protectionSubtitle;
    private Switch protectionSwitch;
    private TextView statBlockedValue;
    private TextView statTimeValue;
    private TextView statStreakValue;
    private WeekChartView weekChart;
    private final Switch[] platformSwitches = new Switch[Platform.values().length];
    private final TextView[] platformCounts = new TextView[Platform.values().length];

    private boolean suppressProtectionListener = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new Prefs(this);
        stats = new StatsRepository(this);

        protectionIcon = findViewById(R.id.protectionIcon);
        protectionTitle = findViewById(R.id.protectionTitle);
        protectionSubtitle = findViewById(R.id.protectionSubtitle);
        protectionSwitch = findViewById(R.id.protectionSwitch);
        statBlockedValue = findViewById(R.id.statBlockedValue);
        statTimeValue = findViewById(R.id.statTimeValue);
        statStreakValue = findViewById(R.id.statStreakValue);
        weekChart = findViewById(R.id.weekChart);

        protectionSwitch.setOnCheckedChangeListener(this::onProtectionSwitchChanged);

        buildPlatformRows();

        findViewById(R.id.navStats).setOnClickListener(v -> startActivity(new Intent(this, StatsActivity.class)));
        findViewById(R.id.navSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.navAbout).setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
    }

    private void buildPlatformRows() {
        android.widget.LinearLayout list = findViewById(R.id.platformList);
        LayoutInflater inflater = LayoutInflater.from(this);
        Platform[] all = Platform.values();
        for (int i = 0; i < all.length; i++) {
            Platform platform = all[i];
            View row = inflater.inflate(R.layout.item_platform_row, list, false);
            ((ImageView) row.findViewById(R.id.rowIcon)).setImageResource(platform.iconRes);
            row.findViewById(R.id.rowIcon).getBackground().mutate()
                    .setTint(getColor(platform.colorRes));
            ((TextView) row.findViewById(R.id.rowTitle)).setText(platform.labelRes);
            ((TextView) row.findViewById(R.id.rowDesc)).setText(platform.descRes);

            Switch rowSwitch = row.findViewById(R.id.rowSwitch);
            rowSwitch.setChecked(prefs.isPlatformEnabled(platform));
            rowSwitch.setOnCheckedChangeListener((btn, checked) -> prefs.setPlatformEnabled(platform, checked));
            platformSwitches[i] = rowSwitch;
            platformCounts[i] = row.findViewById(R.id.rowCount);

            list.addView(row);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshProtectionCard();
        refreshStats();
        if (com.focusguard.blocker.service.BlockerAccessibilityService.isRunning()) {
            prefs.touchStreakForToday();
        }
        for (int i = 0; i < platformCounts.length; i++) {
            platformCounts[i].setText(String.format(Locale.getDefault(), "%d",
                    stats.countForPlatformToday(Platform.values()[i])));
        }
    }

    private void refreshProtectionCard() {
        boolean enabled = com.focusguard.blocker.util.PermissionUtils.isAccessibilityServiceEnabled(this);
        suppressProtectionListener = true;
        protectionSwitch.setChecked(enabled);
        suppressProtectionListener = false;

        protectionIcon.setImageResource(enabled ? R.drawable.ic_check : R.drawable.ic_close);
        protectionIcon.getBackground().mutate()
                .setTint(getColor(enabled ? R.color.brand_success : R.color.brand_danger));
        protectionTitle.setText(enabled ? R.string.dash_protection_on : R.string.dash_protection_off);
        protectionSubtitle.setText(enabled ? R.string.dash_protection_on_sub : R.string.dash_protection_off_sub);
    }

    private void refreshStats() {
        int today = stats.countToday();
        statBlockedValue.setText(String.valueOf(today));

        int totalSeconds = today * prefs.getAvgShortSeconds();
        statTimeValue.setText(formatMinutes(totalSeconds));

        statStreakValue.setText(prefs.getStreak() + getString(R.string.dash_days_suffix));

        int[] counts = stats.countsForLastDays(7);
        String[] labels = lastDayLabels(7);
        weekChart.setData(counts, labels);
    }

    private String formatMinutes(int totalSeconds) {
        int minutes = totalSeconds / 60;
        if (minutes < 60) return minutes + "m";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private String[] lastDayLabels(int days) {
        String[] names = {"D", "L", "M", "X", "J", "V", "S"};
        String[] out = new String[days];
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_YEAR, -(days - 1));
        for (int i = 0; i < days; i++) {
            int dow = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1; // 0=Sunday
            out[i] = names[dow];
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
        }
        return out;
    }

    private void onProtectionSwitchChanged(CompoundButton button, boolean checked) {
        if (suppressProtectionListener) return;
        boolean currentlyEnabled = com.focusguard.blocker.util.PermissionUtils.isAccessibilityServiceEnabled(this);

        // Revert the visual toggle immediately: only the system Accessibility
        // settings screen can actually flip the service, we just route there.
        suppressProtectionListener = true;
        button.setChecked(currentlyEnabled);
        suppressProtectionListener = false;

        if (!currentlyEnabled) {
            openAccessibilitySettings();
            return;
        }

        if (prefs.isPinEnabled()) {
            Intent intent = new Intent(this, PinActivity.class);
            intent.putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_DISABLE);
            startActivityForResult(intent, REQ_PIN_DISABLE);
        } else {
            openAccessibilitySettings();
        }
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PIN_DISABLE && resultCode == RESULT_OK) {
            openAccessibilitySettings();
        }
    }
}
