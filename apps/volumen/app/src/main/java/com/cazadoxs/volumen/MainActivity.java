package com.cazadoxs.volumen;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity implements VolumeService.Listener {
    private static final int BG = Color.parseColor("#14161C");
    private static final int CARD = Color.parseColor("#1E212A");
    private static final int ACCENT = Color.parseColor("#FFB547");
    private static final int TEXT = Color.parseColor("#F2F2F5");
    private static final int MUTED = Color.parseColor("#9AA0AE");

    private Store store;
    private AudioManager audio;
    private Switch power;
    private TextView globalLabel;
    private TextView stepLabel;
    private TextView warning;
    private LinearLayout appsBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        audio = getSystemService(AudioManager.class);
        setContentView(buildUi());
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        if (store.enabled() && !VolumeService.isRunning()) sendToService(VolumeService.ACTION_APPLY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        VolumeService.setListener(this);
        onChanged();
    }

    @Override
    protected void onPause() {
        VolumeService.setListener(null);
        super.onPause();
    }

    @Override
    public void onChanged() {
        power.setOnCheckedChangeListener(null);
        power.setChecked(VolumeService.isRunning());
        power.setOnCheckedChangeListener((b, on) -> toggle(on));
        warning.setVisibility(VolumeService.globalUnavailable() ? View.VISIBLE : View.GONE);
        refreshStep();
        refreshApps();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, dp(28), pad, pad);

        TextView title = text("Volumen", 30, TEXT);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        root.addView(title);
        root.addView(text("Baja más de lo que Android deja, sin que se corten los cascos.", 15, MUTED));

        // Power
        LinearLayout powerCard = card(root);
        power = new Switch(this);
        power.setText("Activar volumen fino");
        power.setTextColor(TEXT);
        power.setTextSize(17);
        powerCard.addView(power);
        warning = text("Este móvil no deja aplicar el ajuste a todo el sonido. "
                + "El control por app sigue funcionando en las apps que lo permiten.", 13, ACCENT);
        warning.setVisibility(View.GONE);
        powerCard.addView(warning);

        // Fine master volume
        LinearLayout globalCard = card(root);
        globalCard.addView(heading("Volumen fino de todo el móvil"));
        globalLabel = text("", 15, ACCENT);
        globalCard.addView(globalLabel);
        globalCard.addView(slider(store.global(), p -> {
            store.setGlobal(p);
            showGlobal(p);
        }));
        showGlobal(store.global());
        globalCard.addView(text("Se aplica encima del volumen del sistema. 100 % no cambia nada.", 13, MUTED));

        // System step
        LinearLayout stepCard = card(root);
        stepCard.addView(heading("Volumen del sistema"));
        stepCard.addView(text("Déjalo en un paso donde los cascos no se desconecten "
                + "y baja desde aquí con el volumen fino.", 13, MUTED));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Button minus = button("−");
        Button plus = button("+");
        stepLabel = text("", 17, TEXT);
        stepLabel.setGravity(Gravity.CENTER);
        row.addView(minus);
        row.addView(stepLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(plus);
        minus.setOnClickListener(v -> step(AudioManager.ADJUST_LOWER));
        plus.setOnClickListener(v -> step(AudioManager.ADJUST_RAISE));
        stepCard.addView(row);

        // Per app
        LinearLayout appsCard = card(root);
        appsCard.addView(heading("Volumen por app"));
        appsCard.addView(text("Aparecen aquí las apps de música que avisan a Android cuando suenan "
                + "(Spotify, YouTube Music, Poweramp, VLC y muchas más). Pon algo a sonar con el "
                + "volumen fino activado. Las que no avisan, como YouTube o el navegador, "
                + "no se pueden ajustar por separado sin root.", 13, MUTED));
        appsBox = new LinearLayout(this);
        appsBox.setOrientation(LinearLayout.VERTICAL);
        appsCard.addView(appsBox);

        // Settings tip
        LinearLayout tipCard = card(root);
        tipCard.addView(heading("Si se desconectan los cascos Bluetooth"));
        tipCard.addView(text("Activa Opciones de desarrollador (Ajustes > Información del teléfono > "
                + "pulsa 7 veces en Número de compilación) y marca \"Inhabilitar volumen absoluto\". "
                + "Reinicia el Bluetooth. Así el móvil baja el volumen él mismo en vez de mandar "
                + "el nivel mínimo a los cascos.", 13, MUTED));
        Button dev = button("Abrir opciones de desarrollador");
        dev.setOnClickListener(v -> openDeveloperOptions());
        tipCard.addView(dev);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.addView(root);
        return scroll;
    }

    private void toggle(boolean on) {
        store.setEnabled(on);
        sendToService(on ? VolumeService.ACTION_APPLY : VolumeService.ACTION_STOP);
    }

    private void sendToService(String action) {
        Intent i = new Intent(this, VolumeService.class).setAction(action);
        if (VolumeService.ACTION_STOP.equals(action)) {
            if (VolumeService.isRunning()) startService(i);
        } else {
            startForegroundService(i);
        }
    }

    private void apply() {
        if (VolumeService.isRunning()) sendToService(VolumeService.ACTION_APPLY);
    }

    private void showGlobal(int p) {
        globalLabel.setText(String.format(Locale.getDefault(), "%d %%   (%s)", p, db(p)));
        apply();
    }

    private void step(int direction) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
        refreshStep();
    }

    private void refreshStep() {
        stepLabel.setText(String.format(Locale.getDefault(), "Paso %d de %d",
                audio.getStreamVolume(AudioManager.STREAM_MUSIC),
                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)));
    }

    private void refreshApps() {
        appsBox.removeAllViews();
        Set<String> known = store.knownApps();
        List<String> active = VolumeService.activePackages();
        known.addAll(active);
        if (known.isEmpty()) {
            appsBox.addView(text("Todavía no ha sonado ninguna app compatible.", 14, TEXT));
            return;
        }
        for (String pkg : known) {
            String name = label(pkg) + (active.contains(pkg) ? "  ● sonando" : "");
            TextView title = text(name, 15, TEXT);
            title.setPadding(0, dp(12), 0, 0);
            TextView value = text("", 13, ACCENT);
            appsBox.addView(title);
            appsBox.addView(value);
            value.setText(String.format(Locale.getDefault(), "%d %%   (%s)", store.app(pkg), db(store.app(pkg))));
            appsBox.addView(slider(store.app(pkg), p -> {
                store.setApp(pkg, p);
                value.setText(String.format(Locale.getDefault(), "%d %%   (%s)", p, db(p)));
                apply();
            }));
        }
    }

    private String label(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private void openDeveloperOptions() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
        }
    }

    private static String db(int p) {
        if (p >= 100) return "0 dB";
        if (p <= 0) return "silencio";
        return String.format(Locale.getDefault(), "%.0f dB", Store.toDb(p));
    }

    private interface OnPercent {
        void set(int percent);
    }

    private SeekBar slider(int value, OnPercent onPercent) {
        SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        bar.setProgress(value);
        bar.setPadding(dp(8), dp(14), dp(8), dp(14));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser) onPercent.set(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        return bar;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18);
        c.setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(20));
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16);
        parent.addView(c, lp);
        return c;
    }

    private TextView heading(String s) {
        TextView t = text(s, 17, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, dp(6));
        return t;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(BG);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ACCENT);
        bg.setCornerRadius(dp(14));
        b.setBackground(bg);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
