package com.focusguard.blocker.receiver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.focusguard.blocker.R;
import com.focusguard.blocker.data.Prefs;
import com.focusguard.blocker.data.StatsRepository;
import com.focusguard.blocker.ui.MainActivity;

public class DailySummaryReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "daily_summary";
    private static final int NOTIF_ID = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        Prefs prefs = new Prefs(context);
        if (!prefs.isNotifEnabled()) return;

        int blockedToday = new StatsRepository(context).countToday();
        if (blockedToday <= 0) return;

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    context.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(channel);
        }

        Intent openApp = new Intent(context, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String body = context.getString(R.string.notif_daily_body, blockedToday);

        android.app.Notification notification = new android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_small)
                .setContentTitle(context.getString(R.string.notif_daily_title))
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build();

        nm.notify(NOTIF_ID, notification);
    }
}
