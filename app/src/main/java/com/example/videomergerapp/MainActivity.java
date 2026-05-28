package com.example.videomergerapp;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Size;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.videomergerapp.databinding.ActivityMainBinding;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements VideoAdapter.DragStartListener {

    private ActivityMainBinding binding;
    private final List<VideoItem> selectedVideos = new ArrayList<>();
    private ActivityResultLauncher<PickVisualMediaRequest> pickMultipleVideosLauncher;
    private VideoAdapter videoAdapter;
    private ItemTouchHelper itemTouchHelper;
    private Transformer transformer;
    private boolean exportRunning;
    private int selectedIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupRecyclerView();

        pickMultipleVideosLauncher = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(20),
                uris -> {
                    selectedVideos.clear();
                    selectedIndex = -1;
                    if (uris != null) {
                        for (Uri uri : uris) {
                            selectedVideos.add(new VideoItem(uri, getDisplayName(uri)));
                        }
                        if (!selectedVideos.isEmpty()) {
                            selectedIndex = 0;
                        }
                    }
                    videoAdapter.setSelectedIndex(selectedIndex);
                    videoAdapter.notifyDataSetChanged();
                    updateSelectionUi();
                }
        );

        binding.selectVideosButton.setOnClickListener(v -> pickMultipleVideosLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.VideoOnly.INSTANCE)
                        .build()
        ));

        binding.mergeButton.setOnClickListener(v -> startExport());
        setIdleUi();
        updateSelectionUi();
    }

    private void setupRecyclerView() {
        videoAdapter = new VideoAdapter(this, selectedVideos, this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.videoRecyclerView.setLayoutManager(layoutManager);
        binding.videoRecyclerView.setAdapter(videoAdapter);
        binding.videoRecyclerView.setItemAnimator(null);
        itemTouchHelper = new ItemTouchHelper(new DragManageAdapter(videoAdapter));
        itemTouchHelper.attachToRecyclerView(binding.videoRecyclerView);
    }

    private void updateSelectionUi() {
        boolean hasItems = !selectedVideos.isEmpty();
        binding.mergeButton.setEnabled(hasItems && !exportRunning);

        if (!hasItems) {
            binding.selectionHintText.setText("Select videos first, then drag cards to reorder them.");
            if (!exportRunning) {
                setIdleUi();
            }
            return;
        }

        if (selectedIndex < 0 || selectedIndex >= selectedVideos.size()) {
            selectedIndex = 0;
            videoAdapter.setSelectedIndex(selectedIndex);
        }

        binding.selectionHintText.setText("Drag the ≡ handle to reorder. Selected item: " + (selectedIndex + 1) + " of " + selectedVideos.size());
    }

    private void setIdleUi() {
        binding.statusText.setText("Idle");
        binding.progressBar.setIndeterminate(false);
        binding.progressBar.setProgress(0);
    }

    private String getDisplayName(Uri uri) {
        String[] projection = {MediaStore.MediaColumns.DISPLAY_NAME};
        try (Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.isBlank()) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        String lastSegment = uri.getLastPathSegment();
        if (lastSegment == null || lastSegment.isBlank()) {
            return uri.toString();
        }
        return lastSegment;
    }

    private void startExport() {
        if (selectedVideos.isEmpty()) {
            Toast.makeText(this, "Pick at least one video first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (exportRunning) {
            Toast.makeText(this, "An export is already running.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri outputUri = createOutputMediaStoreUri();
            if (outputUri == null) {
                Toast.makeText(this, "Could not create output file.", Toast.LENGTH_SHORT).show();
                return;
            }

            File exportFile = prepareTempFile();
            ArrayList<EditedMediaItem> editedItems = new ArrayList<>();
            for (VideoItem item : selectedVideos) {
                editedItems.add(new EditedMediaItem.Builder(MediaItem.fromUri(item.uri)).build());
            }

            Composition composition = new Composition.Builder(new EditedMediaItemSequence(editedItems)).build();
            transformer = new Transformer.Builder(this)
                    .addListener(new Transformer.Listener() {
                        @Override
                        public void onCompleted(Composition composition, ExportResult exportResult) {
                            runOnUiThread(() -> {
                                try {
                                    copyToGallery(exportFile, outputUri);
                                    exportRunning = false;
                                    binding.statusText.setText("Merge complete. Saved to gallery.");
                                    binding.progressBar.setIndeterminate(false);
                                    binding.progressBar.setProgress(100);
                                    binding.mergeButton.setEnabled(!selectedVideos.isEmpty());
                                    Toast.makeText(MainActivity.this, "Merge complete", Toast.LENGTH_LONG).show();
                                } catch (Exception e) {
                                    exportRunning = false;
                                    binding.statusText.setText("Merged, but saving failed: " + e.getMessage());
                                    binding.progressBar.setIndeterminate(false);
                                    binding.progressBar.setProgress(0);
                                    binding.mergeButton.setEnabled(!selectedVideos.isEmpty());
                                }
                            });
                        }

                        @Override
                        public void onError(Composition composition, ExportResult exportResult, ExportException exportException) {
                            runOnUiThread(() -> {
                                exportRunning = false;
                                binding.statusText.setText("Export failed: " + exportException.getMessage());
                                binding.progressBar.setIndeterminate(false);
                                binding.progressBar.setProgress(0);
                                binding.mergeButton.setEnabled(!selectedVideos.isEmpty());
                                Toast.makeText(MainActivity.this, "Export failed", Toast.LENGTH_LONG).show();
                            });
                        }
                    })
                    .build();

            exportRunning = true;
            binding.statusText.setText("Merging videos...");
            binding.progressBar.setIndeterminate(true);
            binding.mergeButton.setEnabled(false);
            transformer.start(composition, exportFile.getAbsolutePath());
        } catch (Exception e) {
            exportRunning = false;
            binding.statusText.setText("Export failed: " + e.getMessage());
            binding.progressBar.setIndeterminate(false);
            binding.progressBar.setProgress(0);
            binding.mergeButton.setEnabled(!selectedVideos.isEmpty());
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
    public void onStartDrag(@NonNull RecyclerView.ViewHolder viewHolder) {
        itemTouchHelper.startDrag(viewHolder);
    }

    @Override
    protected void onDestroy() {
        if (isFinishing() && transformer != null) {
            transformer.cancel();
        }
        super.onDestroy();
    }
}
