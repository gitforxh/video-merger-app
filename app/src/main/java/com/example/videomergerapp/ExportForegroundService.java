package com.example.videomergerapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ExportForegroundService extends Service {

    public static final String ACTION_START_EXPORT = "com.example.videomergerapp.action.START_EXPORT";
    public static final String EXTRA_VIDEO_URIS = "extra_video_uris";
    private static final String CHANNEL_ID = "export_channel";
    private static final int NOTIFICATION_ID = 1001;

    private Transformer transformer;

    public static Intent createStartIntent(Context context, ArrayList<String> uris) {
        Intent intent = new Intent(context, ExportForegroundService.class);
        intent.setAction(ACTION_START_EXPORT);
        intent.putStringArrayListExtra(EXTRA_VIDEO_URIS, uris);
        return intent;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_START_EXPORT.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        ArrayList<String> uriStrings = intent.getStringArrayListExtra(EXTRA_VIDEO_URIS);
        if (uriStrings == null || uriStrings.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification("Preparing export...", true));
        startExport(uriStrings);
        return START_NOT_STICKY;
    }

    private void startExport(ArrayList<String> uriStrings) {
        try {
            Uri outputUri = createOutputMediaStoreUri();
            if (outputUri == null) {
                showFinishedNotification("Export failed: could not create output file");
                stopSelf();
                return;
            }

            File exportFile = prepareTempFile();
            ArrayList<EditedMediaItem> editedItems = new ArrayList<>();
            for (String uriString : uriStrings) {
                MediaItem mediaItem = MediaItem.fromUri(Uri.parse(uriString));
                editedItems.add(new EditedMediaItem.Builder(mediaItem).build());
            }

            Composition composition = new Composition.Builder(new EditedMediaItemSequence(editedItems)).build();
            transformer = new Transformer.Builder(this)
                    .addListener(new Transformer.Listener() {
                        @Override
                        public void onCompleted(Composition composition, ExportResult exportResult) {
                            try {
                                copyToGallery(exportFile, outputUri);
                                showFinishedNotification("Merge complete. Saved to gallery.");
                            } catch (Exception e) {
                                showFinishedNotification("Merged, but saving failed: " + e.getMessage());
                            }
                            stopSelf();
                        }

                        @Override
                        public void onError(Composition composition, ExportResult exportResult, ExportException exportException) {
                            showFinishedNotification("Export failed: " + exportException.getMessage());
                            stopSelf();
                        }
                    })
                    .build();

            updateNotification("Merging videos...");
            transformer.start(composition, exportFile.getAbsolutePath());
        } catch (Exception e) {
            showFinishedNotification("Export failed: " + e.getMessage());
            stopSelf();
        }
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

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, buildNotification(text, true));
    }

    private void showFinishedNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, buildNotification(text, false));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Export notifications", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Video export progress and completion");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Uri createOutputMediaStoreUri() {
        ContentValues values = new ContentValues();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        values.put(MediaStore.Video.Media.DISPLAY_NAME, "merged_" + timeStamp + ".mp4");
        values.put(MediaStore.Video.Media.MIME_TYPE, MimeTypes.VIDEO_MP4);
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VideoMergerApp");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
        }
        return getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
    }

    private File prepareTempFile() throws Exception {
        File tempFile = new File(getCacheDir(), "pending_merge_output.mp4");
        if (tempFile.exists() && !tempFile.delete()) {
            throw new IllegalStateException("Could not reset temp file");
        }
        return tempFile;
    }

    private void copyToGallery(File exportFile, Uri outputUri) throws Exception {
        ContentResolver resolver = getContentResolver();
        try (InputStream inputStream = java.nio.file.Files.newInputStream(exportFile.toPath());
             OutputStream outputStream = resolver.openOutputStream(outputUri, "w")) {
            if (outputStream == null) {
                throw new IllegalStateException("Could not open output stream");
            }
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.IS_PENDING, 0);
            resolver.update(outputUri, values, null, null);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (transformer != null) {
            transformer.cancel();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
