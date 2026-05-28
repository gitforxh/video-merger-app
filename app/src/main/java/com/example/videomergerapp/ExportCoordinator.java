package com.example.videomergerapp;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
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
import java.util.List;
import java.util.Locale;

public class ExportCoordinator {

    private static Context appContext;

    public interface Listener {
        void onStateChanged(String status, int progress, boolean active, boolean background);
    }

    private static ExportCoordinator instance;

    public static synchronized ExportCoordinator getInstance() {
        if (instance == null) {
            instance = new ExportCoordinator();
        }
        return instance;
    }

    public static synchronized void initialize(@NonNull Context context) {
        appContext = context.getApplicationContext();
        if (instance == null) {
            instance = new ExportCoordinator();
        }
        ExportStateStore.ExportState state = ExportStateStore.load(appContext);
        instance.status = state.status;
        instance.progress = state.progress;
        instance.active = state.active;
        instance.background = state.background;
    }

    private final List<Listener> listeners = new ArrayList<>();
    private Transformer transformer;
    private boolean active;
    private boolean background;
    private String status = "Idle";
    private int progress;

    public synchronized void addListener(@NonNull Listener listener, @NonNull Context context) {
        listeners.add(listener);
        listener.onStateChanged(status, progress, active, background);
        ExportStateStore.save(context, status, progress, active, background);
    }

    public synchronized void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized void setBackgroundMode(Context context, boolean background) {
        this.background = background;
        notifyState(context);
    }

    public synchronized void startExport(Context context, List<VideoItem> items) {
        initialize(context);
        if (active) {
            return;
        }
        active = true;
        background = false;
        status = "Preparing export...";
        progress = 5;
        notifyState(context);

        try {
            Uri outputUri = createOutputMediaStoreUri(context);
            if (outputUri == null) {
                fail(context, "Export failed: could not create output file");
                return;
            }

            File exportFile = prepareTempFile(context);
            ArrayList<EditedMediaItem> editedItems = new ArrayList<>();
            for (VideoItem item : items) {
                editedItems.add(new EditedMediaItem.Builder(MediaItem.fromUri(item.uri)).build());
            }

            Composition composition = new Composition.Builder(new EditedMediaItemSequence(editedItems)).build();
            transformer = new Transformer.Builder(context)
                    .addListener(new Transformer.Listener() {
                        @Override
                        public void onCompleted(Composition composition, ExportResult exportResult) {
                            try {
                                copyToGallery(context, exportFile, outputUri);
                                complete(context, "Merge complete. Saved to gallery.");
                            } catch (Exception e) {
                                fail(context, "Merged, but saving failed: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onError(Composition composition, ExportResult exportResult, ExportException exportException) {
                            fail(context, "Export failed: " + exportException.getMessage());
                        }
                    })
                    .build();

            update(context, "Merging videos...", 25);
            transformer.start(composition, exportFile.getAbsolutePath());
        } catch (Exception e) {
            fail(context, "Export failed: " + e.getMessage());
        }
    }

    public synchronized void cancel() {
        if (transformer != null) {
            transformer.cancel();
        }
        active = false;
        background = false;
        status = "Idle";
        progress = 0;
        if (appContext != null) {
            ExportStateStore.save(appContext, status, progress, active, background);
        }
    }

    private synchronized void update(Context context, String status, int progress) {
        this.status = status;
        this.progress = progress;
        notifyState(context);
    }

    private synchronized void complete(Context context, String status) {
        active = false;
        background = false;
        this.status = status;
        this.progress = 100;
        notifyState(context);
    }

    private synchronized void fail(Context context, String status) {
        active = false;
        background = false;
        this.status = status;
        this.progress = 0;
        notifyState(context);
    }

    private synchronized void notifyState(Context context) {
        ExportStateStore.save(context, status, progress, active, background);
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onStateChanged(status, progress, active, background);
        }
    }

    private Uri createOutputMediaStoreUri(Context context) {
        ContentValues values = new ContentValues();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        values.put(MediaStore.Video.Media.DISPLAY_NAME, "merged_" + timeStamp + ".mp4");
        values.put(MediaStore.Video.Media.MIME_TYPE, MimeTypes.VIDEO_MP4);
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VideoMergerApp");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
        }
        return context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
    }

    private File prepareTempFile(Context context) throws Exception {
        File tempFile = new File(context.getCacheDir(), "pending_merge_output.mp4");
        if (tempFile.exists() && !tempFile.delete()) {
            throw new IllegalStateException("Could not reset temp file");
        }
        return tempFile;
    }

    private void copyToGallery(Context context, File exportFile, Uri outputUri) throws Exception {
        ContentResolver resolver = context.getContentResolver();
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
}
