package ai.usemagpie.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Five notification channels + service notification, framework-only (no AndroidX). */
public final class NotificationHelper {
    public static final String CH_SERVICE = "magpie_service";
    public static final String CH_QUOTA = "magpie_quota";
    public static final String CH_TURN = "magpie_turn";
    public static final String CH_MASK = "magpie_mask";
    public static final String CH_UPDATE = "magpie_update";
    public static final int NOTIF_SERVICE_ID = 1001;
    private NotificationHelper() {}

    public static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            nm.createNotificationChannel(new NotificationChannel(CH_SERVICE, c.getString(R.string.ch_service), NotificationManager.IMPORTANCE_LOW));
            nm.createNotificationChannel(new NotificationChannel(CH_QUOTA, c.getString(R.string.ch_quota), NotificationManager.IMPORTANCE_DEFAULT));
            nm.createNotificationChannel(new NotificationChannel(CH_TURN, c.getString(R.string.ch_turn), NotificationManager.IMPORTANCE_DEFAULT));
            nm.createNotificationChannel(new NotificationChannel(CH_MASK, c.getString(R.string.ch_mask), NotificationManager.IMPORTANCE_DEFAULT));
            nm.createNotificationChannel(new NotificationChannel(CH_UPDATE, c.getString(R.string.ch_update), NotificationManager.IMPORTANCE_DEFAULT));
        }
    }

    public static Notification buildServiceNotification(Context c, String text) {
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(c, 0, open, flags());
        Intent restart = new Intent(c, MagpieGatewayService.class);
        restart.setAction(MagpieGatewayService.ACTION_RESTART);
        PendingIntent piRestart = PendingIntent.getService(c, 1, restart, flags());
        Intent stop = new Intent(c, MagpieGatewayService.class);
        stop.setAction(MagpieGatewayService.ACTION_STOP);
        PendingIntent piStop = PendingIntent.getService(c, 2, stop, flags());
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CH_SERVICE) : new Notification.Builder(c);
        b.setContentTitle("Magpie 运行中")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setContentIntent(piOpen)
                .addAction(0, "打开", piOpen)
                .addAction(0, "重启网关", piRestart)
                .addAction(0, "停止", piStop);
        return b.build();
    }

    public static void postView(Context c, String channel, String title, String text, String view) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (view != null) i.putExtra("magpie.view", view);
        int id = (int) (System.currentTimeMillis() % 100000);
        PendingIntent pi = PendingIntent.getActivity(c, id, i, flags());
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, channel) : new Notification.Builder(c);
        b.setContentTitle(title).setContentText(text).setSmallIcon(R.mipmap.ic_launcher).setAutoCancel(true).setContentIntent(pi);
        nm.notify(id, b.build());
    }

    private static int flags() {
        int f = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) f |= PendingIntent.FLAG_IMMUTABLE;
        return f;
    }
}
