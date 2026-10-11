package com.focusguard.blocker.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.focusguard.blocker.R;
import com.focusguard.blocker.data.Prefs;

public class SplashActivity extends android.app.Activity {

    private static final long DELAY_MS = 700L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Prefs prefs = new Prefs(this);
            Class<?> next = prefs.isOnboarded() ? MainActivity.class : OnboardingActivity.class;
            startActivity(new Intent(this, next));
            finish();
        }, DELAY_MS);
    }
}
