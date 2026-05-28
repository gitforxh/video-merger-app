package com.example.videomergerapp;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.videomergerapp.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements VideoAdapter.DragStartListener {

    private ActivityMainBinding binding;
    private final List<VideoItem> selectedVideos = new ArrayList<>();
    private ActivityResultLauncher<PickVisualMediaRequest> pickMultipleVideosLauncher;
    private VideoAdapter videoAdapter;
    private ItemTouchHelper itemTouchHelper;
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

        binding.mergeButton.setOnClickListener(v -> startBackgroundExport());
        restoreSavedExportState();
        updateSelectionUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        restoreSavedExportState();
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
        ExportStateStore.ExportState state = ExportStateStore.load(this);
        binding.mergeButton.setEnabled(hasItems && !state.active);

        if (!hasItems) {
            binding.selectionHintText.setText("Select videos first, then drag cards to reorder them.");
            if (!state.active) {
                binding.statusText.setText("Idle");
                binding.progressBar.setIndeterminate(false);
                binding.progressBar.setProgress(0);
            }
            return;
        }

        if (selectedIndex < 0 || selectedIndex >= selectedVideos.size()) {
            selectedIndex = 0;
            videoAdapter.setSelectedIndex(selectedIndex);
        }

        binding.selectionHintText.setText("Drag the ≡ handle to reorder. Selected item: " + (selectedIndex + 1) + " of " + selectedVideos.size());
    }

    private void restoreSavedExportState() {
        ExportStateStore.ExportState state = ExportStateStore.load(this);
        binding.statusText.setText(state.background ? state.status + " (running in background)" : state.status);
        binding.progressBar.setIndeterminate(state.active && state.progress <= 0);
        binding.progressBar.setProgress(state.progress);
        binding.mergeButton.setEnabled(!state.active && !selectedVideos.isEmpty());
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

    private void startBackgroundExport() {
        if (selectedVideos.isEmpty()) {
            Toast.makeText(this, "Pick at least one video first.", Toast.LENGTH_SHORT).show();
            return;
        }

        ExportStateStore.ExportState state = ExportStateStore.load(this);
        if (state.active) {
            Toast.makeText(this, "An export is already running.", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<String> uriStrings = new ArrayList<>();
        for (VideoItem item : selectedVideos) {
            uriStrings.add(item.uri.toString());
        }

        Intent serviceIntent = new Intent(this, ExportForegroundService.class);
        serviceIntent.setAction(ExportForegroundService.ACTION_START_EXPORT);
        serviceIntent.putStringArrayListExtra(ExportForegroundService.EXTRA_VIDEO_URIS, uriStrings);
        ContextCompat.startForegroundService(this, serviceIntent);

        binding.statusText.setText("Preparing export...");
        binding.progressBar.setIndeterminate(false);
        binding.progressBar.setProgress(5);
        binding.mergeButton.setEnabled(false);
        Toast.makeText(this, "Export started", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onStartDrag(@NonNull RecyclerView.ViewHolder viewHolder) {
        itemTouchHelper.startDrag(viewHolder);
    }
}
