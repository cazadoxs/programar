package com.focusguard.blocker.ui;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.focusguard.blocker.R;
import com.focusguard.blocker.data.Prefs;
import com.focusguard.blocker.util.PermissionUtils;

public class OnboardingActivity extends android.app.Activity {

    private static final int STEP_WELCOME = 0;
    private static final int STEP_PRIVACY = 1;
    private static final int STEP_ACCESS = 2;
    private static final int STEP_NOTIF = 3;
    private static final int STEP_COUNT = 4;
    private static final int REQ_NOTIF = 55;

    private ViewFlipper flipper;
    private final View[] dots = new View[STEP_COUNT];
    private final TextView[] statusViews = new TextView[STEP_COUNT];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        flipper = findViewById(R.id.flipper);
        dots[0] = findViewById(R.id.dot0);
        dots[1] = findViewById(R.id.dot1);
        dots[2] = findViewById(R.id.dot2);
        dots[3] = findViewById(R.id.dot3);

        LayoutInflater inflater = LayoutInflater.from(this);
        buildStep(inflater, STEP_WELCOME, android.R.drawable.ic_dialog_info,
                getString(R.string.onb_welcome_title), getString(R.string.onb_welcome_body),
                getString(R.string.onb_continue), false);
        buildStep(inflater, STEP_PRIVACY, android.R.drawable.ic_lock_idle_lock,
                getString(R.string.onb_privacy_title), getString(R.string.onb_privacy_body),
                getString(R.string.onb_continue), false);
        buildStep(inflater, STEP_ACCESS, android.R.drawable.ic_dialog_alert,
                getString(R.string.onb_access_title), getString(R.string.onb_access_body),
                getString(R.string.onb_access_cta), true);
        buildStep(inflater, STEP_NOTIF, android.R.drawable.ic_dialog_email,
                getString(R.string.onb_notif_title), getString(R.string.onb_notif_body),
                getString(R.string.onb_finish), true);

        updateDots(0);
    }

    private void buildStep(LayoutInflater inflater, int index, int iconRes, String title, String body,
                            String actionLabel, boolean showStatus) {
        View page = inflater.inflate(R.layout.item_onboarding_step, flipper, false);
        ((ImageView) page.findViewById(R.id.stepIcon)).setImageResource(iconRes);
        ((TextView) page.findViewById(R.id.stepTitle)).setText(title);
        ((TextView) page.findViewById(R.id.stepBody)).setText(body);
        Button action = page.findViewById(R.id.stepAction);
        action.setText(actionLabel);
        action.setOnClickListener(v -> onAction(index));

        TextView status = page.findViewById(R.id.stepStatus);
        statusViews[index] = status;
        status.setVisibility(showStatus ? View.VISIBLE : View.GONE);

        TextView skip = page.findViewById(R.id.stepSkip);
        if (index == STEP_COUNT - 1) {
            skip.setVisibility(View.GONE);
        } else {
            skip.setOnClickListener(v -> goTo(index + 1));
        }

        flipper.addView(page);
    }

    private void onAction(int index) {
        switch (index) {
            case STEP_WELCOME:
            case STEP_PRIVACY:
                goTo(index + 1);
                break;
            case STEP_ACCESS:
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                break;
            case STEP_NOTIF:
                requestNotificationsThenFinish();
                break;
        }
    }

    private void requestNotificationsThenFinish() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        } else {
            finishOnboarding();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF) finishOnboarding();
    }

    private void finishOnboarding() {
        new Prefs(this).setOnboarded(true);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void goTo(int index) {
        flipper.setDisplayedChild(index);
        updateDots(index);
    }

    private void updateDots(int active) {
        for (int i = 0; i < dots.length; i++) {
            dots[i].setAlpha(i == active ? 1f : 0.3f);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus(STEP_ACCESS, PermissionUtils.isAccessibilityServiceEnabled(this));
        refreshStatus(STEP_NOTIF, PermissionUtils.areNotificationsEnabled(this));
    }

    private void refreshStatus(int step, boolean granted) {
        TextView view = statusViews[step];
        if (view == null) return;
        view.setText(granted ? R.string.onb_status_granted : R.string.onb_status_pending);
        view.setBackgroundResource(granted ? R.drawable.bg_pill_success : R.drawable.bg_pill_danger);
    }
}
