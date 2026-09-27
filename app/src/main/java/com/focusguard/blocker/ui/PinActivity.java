package com.focusguard.blocker.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.focusguard.blocker.R;
import com.focusguard.blocker.data.Prefs;

/**
 * One small activity that plays three roles depending on {@link #EXTRA_MODE}:
 * creating a PIN, verifying it, and — for the "disable protection" flow —
 * enforcing the 15 minute cool-down that exists specifically so switching
 * FocusGuard off is never a single impulsive tap.
 */
public class PinActivity extends android.app.Activity {

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_SETUP = "setup";
    public static final String MODE_DISABLE = "disable";

    private static final int STATE_SETUP_ENTER = 0;
    private static final int STATE_SETUP_CONFIRM = 1;
    private static final int STATE_VERIFY = 2;
    private static final int STATE_WAIT = 3;

    private int state;
    private String mode;
    private String firstPin;

    private TextView title;
    private TextView body;
    private EditText input;
    private TextView countdown;
    private TextView error;
    private Button action;

    private Prefs prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::onTick;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);
        prefs = new Prefs(this);

        title = findViewById(R.id.pinTitle);
        body = findViewById(R.id.pinBody);
        input = findViewById(R.id.pinInput);
        countdown = findViewById(R.id.pinCountdown);
        error = findViewById(R.id.pinError);
        action = findViewById(R.id.pinAction);
        findViewById(R.id.pinCancel).setOnClickListener(v -> cancel());

        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (MODE_SETUP.equals(mode)) {
            enterState(STATE_SETUP_ENTER);
        } else {
            enterState(STATE_VERIFY);
        }
    }

    private void enterState(int newState) {
        state = newState;
        error.setVisibility(View.GONE);
        input.setText("");
        switch (state) {
            case STATE_SETUP_ENTER:
                title.setText(R.string.pin_setup_title);
                body.setText(R.string.pin_setup_body);
                showInput(true);
                action.setText(R.string.onb_continue);
                action.setOnClickListener(v -> onSetupEnter());
                break;
            case STATE_SETUP_CONFIRM:
                title.setText(R.string.pin_confirm_title);
                body.setText(R.string.pin_setup_body);
                showInput(true);
                action.setText(R.string.onb_continue);
                action.setOnClickListener(v -> onSetupConfirm());
                break;
            case STATE_VERIFY:
                title.setText(R.string.pin_enter_title);
                body.setText("");
                showInput(true);
                action.setText(R.string.onb_continue);
                action.setOnClickListener(v -> onVerify());
                break;
            case STATE_WAIT:
                title.setText(R.string.pin_wait_title);
                body.setText(R.string.pin_wait_body);
                showInput(false);
                action.setText(R.string.pin_wait_cta);
                action.setEnabled(false);
                action.setOnClickListener(v -> onWaitDone());
                startCountdown();
                break;
        }
    }

    private void showInput(boolean show) {
        input.setVisibility(show ? View.VISIBLE : View.GONE);
        countdown.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void onSetupEnter() {
        String pin = input.getText().toString();
        if (pin.length() < 4) {
            showError(getString(R.string.pin_error_mismatch));
            return;
        }
        firstPin = pin;
        enterState(STATE_SETUP_CONFIRM);
    }

    private void onSetupConfirm() {
        String pin = input.getText().toString();
        if (!pin.equals(firstPin)) {
            showError(getString(R.string.pin_error_mismatch));
            enterState(STATE_SETUP_ENTER);
            return;
        }
        prefs.setPin(pin);
        prefs.setPinEnabled(true);
        setResult(RESULT_OK);
        finish();
    }

    private void onVerify() {
        String pin = input.getText().toString();
        if (!prefs.checkPin(pin)) {
            showError(getString(R.string.pin_error_wrong));
            return;
        }
        if (MODE_DISABLE.equals(mode)) {
            enterState(STATE_WAIT);
        } else {
            setResult(RESULT_OK);
            finish();
        }
    }

    private void startCountdown() {
        prefs.getOrStartDisableRequest();
        handler.post(tick);
    }

    private void onTick() {
        long startedAt = prefs.getOrStartDisableRequest();
        long remaining = Prefs.TAMPER_DELAY_MS - (System.currentTimeMillis() - startedAt);
        if (remaining <= 0) {
            countdown.setText("00:00");
            action.setEnabled(true);
            return;
        }
        long totalSeconds = remaining / 1000;
        countdown.setText(String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60));
        handler.postDelayed(tick, 500);
    }

    private void onWaitDone() {
        prefs.clearDisableRequest();
        setResult(RESULT_OK);
        finish();
    }

    private void cancel() {
        if (state == STATE_WAIT) {
            prefs.clearDisableRequest();
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    private void showError(String message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(tick);
    }
}
