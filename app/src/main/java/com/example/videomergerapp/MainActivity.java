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
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;

import com.example.videomergerapp.databinding.ActivityMainBinding;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private final List<Uri> selectedUris = new ArrayList<>();
    private ActivityResultLauncher<PickVisualMediaRequest> pickMultipleVideosLauncher;
    private Transformer currentTransformer;
    private int selectedIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        pickMultipleVideosLauncher = registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(20),
                uris -> {
                    selectedUris.clear();
                    selectedIndex = -1;
                    if (uris != null) {
                        selectedUris.addAll(uris);
                        if (!selectedUris.isEmpty()) {
                            selectedIndex = 0;
                        }
                    }
                    updateSelectionUi();
                }
        );

        binding.selectVideosButton.setOnClickListener(v -> pickMultipleVideosLauncher.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.VideoOnly.INSTANCE)
                        .build()
        ));

        binding.previousButton.setOnClickListener(v -> selectPrevious());
        binding.nextButton.setOnClickListener(v -> selectNext());
        binding.moveUpButton.setOnClickListener(v -> moveSelectedItemUp());
        binding.moveDownButton.setOnClickListener(v -> moveSelectedItemDown());
        binding.mergeButton.setOnClickListener(v -> mergeVideos());

        updateSelectionUi();
    }

    private void updateSelectionUi() {
        boolean hasItems = !selectedUris.isEmpty();
        binding.mergeButton.setEnabled(hasItems);
        binding.previousButton.setEnabled(hasItems && selectedIndex > 0);
        binding.nextButton.setEnabled(hasItems && selectedIndex >= 0 && selectedIndex < selectedUris.size() - 1);
        binding.moveUpButton.setEnabled(hasItems && selectedIndex > 0);
        binding.moveDownButton.setEnabled(hasItems && selectedIndex >= 0 && selectedIndex < selectedUris.size() - 1);

        if (!hasItems) {
            binding.selectionHintText.setText("Select videos first, then reorder them before merging.");
            binding.videoListContainer.removeAllViews();
            return;
        }

        if (selectedIndex < 0 || selectedIndex >= selectedUris.size()) {
            selectedIndex = 0;
        }

        binding.selectionHintText.setText("Selected item: " + (selectedIndex + 1) + " of " + selectedUris.size());
        renderVideoList();
    }

    private void renderVideoList() {
        binding.videoListContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < selectedUris.size(); i++) {
            Uri uri = selectedUris.get(i);
            View itemView = inflater.inflate(R.layout.video_item, binding.videoListContainer, false);
            MaterialCardView cardView = itemView.findViewById(R.id.cardView);
            ImageView thumbnailView = itemView.findViewById(R.id.thumbnailView);
            TextView titleText = itemView.findViewById(R.id.titleText);
            TextView subtitleText = itemView.findViewById(R.id.subtitleText);

            titleText.setText(getDisplayName(uri));
            subtitleText.setText("Position " + (i + 1));

            Bitmap thumbnail = loadVideoThumbnail(uri);
            if (thumbnail != null) {
                thumbnailView.setImageBitmap(thumbnail);
            } else {
                thumbnailView.setImageResource(R.drawable.ic_video_placeholder);
            }

            if (i == selectedIndex) {
                itemView.setAlpha(1.0f);
                cardView.setStrokeWidth(dpToPx(3));
                cardView.setStrokeColor(ContextCompat.getColor(this, com.google.android.material.R.color.m3_ref_palette_primary40));
                cardView.setCardBackgroundColor(ContextCompat.getColor(this, com.google.android.material.R.color.m3_sys_color_dark_primary_container));
                titleText.setTextColor(ContextCompat.getColor(this, com.google.android.material.R.color.m3_sys_color_dark_on_primary_container));
                subtitleText.setTextColor(ContextCompat.getColor(this, com.google.android.material.R.color.m3_sys_color_dark_on_primary_container));
            } else {
                itemView.setAlpha(0.96f);
                cardView.setStrokeWidth(0);
                cardView.setCardBackgroundColor(ContextCompat.getColor(this, com.google.android.material.R.color.m3_ref_palette_neutral20));
                titleText.setTextColor(ContextCompat.getColor(this, android.R.color.white));
                subtitleText.setTextColor(ContextCompat.getColor(this, com.google.android.material.R.color.m3_ref_palette_neutral80));
            }

            final int index = i;
            itemView.setOnClickListener(v -> {
                selectedIndex = index;
                updateSelectionUi();
            });

            binding.videoListContainer.addView(itemView);
        }
    }

    private void selectPrevious() {
        if (selectedIndex > 0) {
            selectedIndex--;
            updateSelectionUi();
        }
    }

    private void selectNext() {
        if (selectedIndex >= 0 && selectedIndex < selectedUris.size() - 1) {
            selectedIndex++;
            updateSelectionUi();
        }
    }

    private void moveSelectedItemUp() {
        if (selectedIndex > 0) {
            Collections.swap(selectedUris, selectedIndex, selectedIndex - 1);
            selectedIndex--;
            updateSelectionUi();
        }
    }

    private void moveSelectedItemDown() {
        if (selectedIndex >= 0 && selectedIndex < selectedUris.size() - 1) {
            Collections.swap(selectedUris, selectedIndex, selectedIndex + 1);
            selectedIndex++;
            updateSelectionUi();
        }
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

    private Bitmap loadVideoThumbnail(Uri uri) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return getContentResolver().loadThumbnail(uri, new android.util.Size(320, 180), null);
            }
        } catch (Exception ignored) {
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            return retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void mergeVideos() {
        if (selectedUris.isEmpty()) {
            Toast.makeText(this, "Pick at least one video first.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri outputUri = createOutputMediaStoreUri();
            if (outputUri == null) {
                Toast.makeText(this, "Could not create output file.", Toast.LENGTH_SHORT).show();
                return;
            }

            File exportFile = copyUriToTempFile(outputUri);
            startExport(exportFile, outputUri);
        } catch (Exception e) {
            binding.statusText.setText("Error: " + e.getMessage());
            Toast.makeText(this, "Merge failed to start.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startExport(@NonNull File exportFile, @NonNull Uri outputUri) {
        List<EditedMediaItem> editedItems = new ArrayList<>();
        for (Uri uri : selectedUris) {
            MediaItem mediaItem = MediaItem.fromUri(uri);
            EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem)
                    .build();
            editedItems.add(editedMediaItem);
        }

        Composition composition = new Composition.Builder(
                new EditedMediaItemSequence(editedItems)
        ).build();

        currentTransformer = new Transformer.Builder(this)
                .addListener(new Transformer.Listener() {
                    @Override
                    public void onCompleted(Composition composition, ExportResult exportResult) {
                        runOnUiThread(() -> {
                            binding.statusText.setText("Done. Saved to gallery as merged video.");
                            binding.progressBar.setProgress(100);
                            makeOutputVisible(exportFile, outputUri);
                            Toast.makeText(MainActivity.this, "Merge complete", Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onError(Composition composition, ExportResult exportResult, ExportException exportException) {
                        runOnUiThread(() -> {
                            binding.statusText.setText("Export failed: " + exportException.getMessage());
                            Toast.makeText(MainActivity.this, "Export failed", Toast.LENGTH_LONG).show();
                        });
                    }
                })
                .build();

        binding.statusText.setText("Merging videos...");
        binding.progressBar.setProgress(15);
        currentTransformer.start(composition, exportFile.getAbsolutePath());
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

    private File copyUriToTempFile(Uri targetUri) throws Exception {
        File tempFile = new File(getCacheDir(), "pending_merge_output.mp4");
        if (tempFile.exists() && !tempFile.delete()) {
            throw new IllegalStateException("Could not reset temp file");
        }
        ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(targetUri, "w");
        if (pfd != null) {
            pfd.close();
        }
        return tempFile;
    }

    private void makeOutputVisible(File exportFile, Uri outputUri) {
        try {
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
        } catch (Exception e) {
            binding.statusText.setText("Saved export, but gallery finalization failed: " + e.getMessage());
        }
    }
}
