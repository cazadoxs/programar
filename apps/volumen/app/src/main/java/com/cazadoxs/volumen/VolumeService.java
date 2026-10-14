package com.cazadoxs.volumen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.DynamicsProcessing;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lowers audio below Android's minimum volume step by applying negative input gain with
 * DynamicsProcessing: on the global output mix (session 0) for the whole phone, and on each audio
 * session that a music app announces, for per-app volume.
 */
public class VolumeService extends Service {
    static final String ACTION_APPLY = "com.cazadoxs.volumen.APPLY";
    static final String ACTION_STOP = "com.cazadoxs.volumen.STOP";

    private static final String TAG = "Volumen";
    private static final String CHANNEL = "volumen";
    private static final int NOTIFICATION_ID = 1;
    private static final int PRIORITY = 1000;

    /** Change listener for the open screen. Main thread only. */
    interface Listener {
        void onChanged();
    }

    private static VolumeService running;
    private static Listener listener;

    private final Map<Integer, String> sessionPackages = new HashMap<>();
    private final Map<Integer, DynamicsProcessing> sessionEffects = new HashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private DynamicsProcessing global;
    private boolean globalFailed;
    private Store store;

    private final BroadcastReceiver sessions = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR);
            String pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
            if (session <= 0) return;
            if (AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(intent.getAction())) {
                if (pkg == null || pkg.isEmpty()) return;
                openSession(session, pkg);
            } else if (AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(intent.getAction())) {
                closeSession(session);
            }
            notifyListener();
        }
    };

    static boolean isRunning() {
        return running != null;
    }

    static void setListener(Listener l) {
        listener = l;
    }

    /** Packages with an audio session open right now. */
    static List<String> activePackages() {
        List<String> out = new ArrayList<>();
        if (running == null) return out;
        for (String pkg : running.sessionPackages.values()) {
            if (!out.contains(pkg)) out.add(pkg);
        }
        return out;
    }

    /** True when the phone-wide effect could not be attached on this device. */
    static boolean globalUnavailable() {
        return running != null && running.globalFailed;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        store = new Store(this);
        running = this;
        startInForeground();
        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        filter.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        // Exported: the broadcasts come from other apps. Before Android 13 the flag is ignored.
        registerReceiver(sessions, filter, Context.RECEIVER_EXPORTED);
        global = create(0);
        globalFailed = global == null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            store.setEnabled(false);
            stopSelf();
            return START_NOT_STICKY;
        }
        applyAll();
        notifyListener();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(sessions);
        release(global);
        global = null;
        for (DynamicsProcessing dp : sessionEffects.values()) release(dp);
        sessionEffects.clear();
        sessionPackages.clear();
        running = null;
        notifyListener();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void openSession(int session, String pkg) {
        store.rememberApp(pkg);
        sessionPackages.put(session, pkg);
        if (!sessionEffects.containsKey(session)) {
            DynamicsProcessing dp = create(session);
            if (dp != null) sessionEffects.put(session, dp);
        }
        applyAll();
    }

    private void closeSession(int session) {
        sessionPackages.remove(session);
        release(sessionEffects.remove(session));
    }

    private void applyAll() {
        setGain(global, Store.toDb(store.global()));
        for (Map.Entry<Integer, DynamicsProcessing> e : sessionEffects.entrySet()) {
            String pkg = sessionPackages.get(e.getKey());
            setGain(e.getValue(), pkg == null ? 0f : Store.toDb(store.app(pkg)));
        }
    }

    private static DynamicsProcessing create(int session) {
        try {
            DynamicsProcessing.Config config = new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
                    false, 0, false, 0, false, 0, false).build();
            DynamicsProcessing dp = new DynamicsProcessing(PRIORITY, session, config);
            dp.setEnabled(true);
            return dp;
        } catch (RuntimeException e) {
            Log.w(TAG, "No se pudo crear el efecto en la sesión " + session, e);
            return null;
        }
    }

    private static void setGain(DynamicsProcessing dp, float db) {
        if (dp == null) return;
        try {
            dp.setInputGainAllChannelsTo(db);
        } catch (RuntimeException e) {
            Log.w(TAG, "No se pudo ajustar la ganancia", e);
        }
    }

    private static void release(DynamicsProcessing dp) {
        if (dp == null) return;
        try {
            dp.release();
        } catch (RuntimeException ignored) {
            // Already released by the system.
        }
    }

    private void notifyListener() {
        main.post(() -> {
            if (listener != null) listener.onChanged();
        });
    }

    private void startInForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Volumen activo", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, VolumeService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Volumen fino activo")
                .setContentText("Toca para ajustar")
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Apagar", stop).build())
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }
}
