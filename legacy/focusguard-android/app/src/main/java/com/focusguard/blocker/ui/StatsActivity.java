package com.focusguard.blocker.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.focusguard.blocker.R;
import com.focusguard.blocker.data.Platform;
import com.focusguard.blocker.data.Prefs;
import com.focusguard.blocker.data.StatsRepository;

public class StatsActivity extends android.app.Activity {

    private StatsRepository stats;
    private Prefs prefs;
    private WeekChartView chart;
    private TextView tab7;
    private TextView tab30;
    private TextView totalBlockedValue;
    private TextView totalTimeValue;
    private int selectedDays = 7;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);
        stats = new StatsRepository(this);
        prefs = new Prefs(this);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        chart = findViewById(R.id.chart);
        tab7 = findViewById(R.id.tab7);
        tab30 = findViewById(R.id.tab30);
        totalBlockedValue = findViewById(R.id.totalBlockedValue);
        totalTimeValue = findViewById(R.id.totalTimeValue);

        tab7.setOnClickListener(v -> selectRange(7));
        tab30.setOnClickListener(v -> selectRange(30));

        buildPlatformBreakdown();
        selectRange(7);
    }

    private void selectRange(int days) {
        selectedDays = days;
        tab7.setTextColor(getColor(days == 7 ? R.color.brand_primary : R.color.text_secondary_light));
        tab30.setTextColor(getColor(days == 30 ? R.color.brand_primary : R.color.text_secondary_light));
        refreshChart();
    }

    private void refreshChart() {
        int[] counts = stats.countsForLastDays(selectedDays);
        String[] labels = new String[selectedDays];
        for (int i = 0; i < selectedDays; i++) {
            int fromEnd = selectedDays - i;
            labels[i] = (selectedDays == 7) ? "" : String.valueOf(fromEnd);
        }
        chart.setData(counts, labels);

        int total = stats.countTotal();
        totalBlockedValue.setText(String.valueOf(total));
        int totalMinutes = (total * prefs.getAvgShortSeconds()) / 60;
        totalTimeValue.setText(totalMinutes < 60
                ? totalMinutes + "m"
                : (totalMinutes / 60) + "h " + (totalMinutes % 60) + "m");
    }

    private void buildPlatformBreakdown() {
        LinearLayout list = findViewById(R.id.platformBreakdown);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (StatsRepository.PlatformCount pc : stats.countsByPlatformTotal()) {
            View row = inflater.inflate(R.layout.item_platform_row, list, false);
            Platform platform = pc.platform;
            ((ImageView) row.findViewById(R.id.rowIcon)).setImageResource(platform.iconRes);
            row.findViewById(R.id.rowIcon).getBackground().mutate().setTint(getColor(platform.colorRes));
            ((TextView) row.findViewById(R.id.rowTitle)).setText(platform.labelRes);
            TextView rowDesc = row.findViewById(R.id.rowDesc);
            rowDesc.setVisibility(View.GONE);
            TextView count = row.findViewById(R.id.rowCount);
            count.setText(getString(R.string.stats_platform_total_format, pc.count));
            row.findViewById(R.id.rowSwitch).setVisibility(View.GONE);
            list.addView(row);
        }
    }
}
