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

        binding.mergeButton.setOnClickListener(v -> mergeVideosInBackground());
        updateSelectionUi();
    }

    private void setupRecyclerView() {
        videoAdapter = new VideoAdapter(this, selectedVideos, this);
        binding.videoRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.videoRecyclerView.setAdapter(videoAdapter);
        itemTouchHelper = new ItemTouchHelper(new DragManageAdapter(videoAdapter));
        itemTouchHelper.attachToRecyclerView(binding.videoRecyclerView);
    }

    private void updateSelectionUi() {
        boolean hasItems = !selectedVideos.isEmpty();
        binding.mergeButton.setEnabled(hasItems);

        if (!hasItems) {
            binding.selectionHintText.setText("Select videos first, then drag cards to reorder them.");
            binding.statusText.setText("Idle");
            binding.progressBar.setProgress(0);
            return;
        }

        if (selectedIndex < 0 || selectedIndex >= selectedVideos.size()) {
            selectedIndex = 0;
            videoAdapter.setSelectedIndex(selectedIndex);
        }

        binding.selectionHintText.setText("Drag the ≡ handle to reorder. Selected item: " + (selectedIndex + 1) + " of " + selectedVideos.size());
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

    private void mergeVideosInBackground() {
        if (selectedVideos.isEmpty()) {
            Toast.makeText(this, "Pick at least one video first.", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<String> uriStrings = new ArrayList<>();
        for (VideoItem item : selectedVideos) {
            uriStrings.add(item.uri.toString());
        }

        Intent serviceIntent = ExportForegroundService.createStartIntent(this, uriStrings);
        ContextCompat.startForegroundService(this, serviceIntent);
        binding.statusText.setText("Export started in background. Check notifications.");
        binding.progressBar.setIndeterminate(true);
        Toast.makeText(this, "Background export started", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onStartDrag(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder) {
        itemTouchHelper.startDrag(viewHolder);
    }
}
