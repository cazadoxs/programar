package com.focusguard.blocker.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Small, dependency-free bar chart: no chart library, just Canvas. Values
 * are counts for the last N days (oldest first). Kept intentionally simple —
 * this is a habit-tracking glance, not an analytics dashboard.
 */
public class WeekChartView extends View {

    private int[] values = new int[0];
    private String[] labels = new String[0];

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public WeekChartView(Context context) {
        super(context);
        init();
    }

    public WeekChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        barPaint.setColor(0xFF4F46E5);
        trackPaint.setColor(0x14808080);
        labelPaint.setColor(0xFF9CA3AF);
        labelPaint.setTextSize(dp(11));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setColor(0xFF4F46E5);
        valuePaint.setTextSize(dp(11));
        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setFakeBoldText(true);
    }

    public void setData(int[] values, String[] labels) {
        this.values = values == null ? new int[0] : values;
        this.labels = labels == null ? new String[0] : labels;
        invalidate();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = values.length;
        if (n == 0) return;

        int max = 1;
        for (int v : values) max = Math.max(max, v);

        float width = getWidth();
        float height = getHeight();
        float bottomPad = dp(18);
        float topPad = dp(16);
        float chartHeight = height - bottomPad - topPad;

        float slot = width / n;
        float barWidth = Math.min(slot * 0.42f, dp(28));

        for (int i = 0; i < n; i++) {
            float cx = slot * i + slot / 2f;
            float trackTop = topPad;
            float trackBottom = height - bottomPad;

            RectF track = new RectF(cx - barWidth / 2f, trackTop, cx + barWidth / 2f, trackBottom);
            canvas.drawRoundRect(track, barWidth / 2f, barWidth / 2f, trackPaint);

            float ratio = values[i] / (float) max;
            float barHeight = Math.max(chartHeight * ratio, values[i] > 0 ? dp(6) : 0);
            RectF bar = new RectF(cx - barWidth / 2f, trackBottom - barHeight, cx + barWidth / 2f, trackBottom);
            if (barHeight > 0) {
                canvas.drawRoundRect(bar, barWidth / 2f, barWidth / 2f, barPaint);
            }

            if (values[i] > 0) {
                canvas.drawText(String.valueOf(values[i]), cx, trackTop - dp(4), valuePaint);
            }
            if (i < labels.length) {
                canvas.drawText(labels[i], cx, height - dp(2), labelPaint);
            }
        }
    }
}
