package com.example.videomergerapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class ExportForegroundService extends Service implements ExportCoordinator.Listener {

    public static final String ACTION_START_FOREGROUND = "com.example.videomergerapp.action.START_FOREGROUND";
    public static final String ACTION_STOP_FOREGROUND = "com.example.videomergerapp.action.STOP_FOREGROUND";
    private static final String CHANNEL_ID = "export_channel";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        ExportCoordinator.getInstance().addListener(this, this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP_FOREGROUND.equals(action)) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification("Export running in background...", true));
        ExportCoordinator.getInstance().setBackgroundMode(this, true);
        return START_STICKY;
    }

    private Notification buildNotification(String text, boolean ongoing) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Video Merger")
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Export notifications", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Video export progress and completion");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onStateChanged(String status, int progress, boolean active, boolean background) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (!active) {
            manager.notify(NOTIFICATION_ID, buildNotification(status, false));
            stopForeground(STOP_FOREGROUND_DETACH);
            stopSelf();
            return;
        }
        if (background) {
            manager.notify(NOTIFICATION_ID, buildNotification(status + " " + progress + "%", true));
        }
    }

    @Override
    public void onDestroy() {
        ExportCoordinator.getInstance().removeListener(this);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
