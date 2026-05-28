package com.example.videomergerapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    public interface DragStartListener {
        void onStartDrag(@NonNull RecyclerView.ViewHolder viewHolder);
    }

    private final Context context;
    private final List<VideoItem> items;
    private final DragStartListener dragStartListener;
    private int selectedIndex = -1;

    public VideoAdapter(Context context, List<VideoItem> items, DragStartListener dragStartListener) {
        this.context = context;
        this.items = items;
        this.dragStartListener = dragStartListener;
    }

    public void setSelectedIndex(int selectedIndex) {
        this.selectedIndex = selectedIndex;
        notifyDataSetChanged();
    }

    public List<VideoItem> getItems() {
        return items;
    }

    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition < 0 || toPosition < 0 || fromPosition >= items.size() || toPosition >= items.size()) {
            return;
        }
        if (fromPosition == toPosition) {
            return;
        }

        VideoItem moved = items.remove(fromPosition);
        items.add(toPosition, moved);

        if (selectedIndex == fromPosition) {
            selectedIndex = toPosition;
        } else if (fromPosition < toPosition && selectedIndex > fromPosition && selectedIndex <= toPosition) {
            selectedIndex--;
        } else if (fromPosition > toPosition && selectedIndex >= toPosition && selectedIndex < fromPosition) {
            selectedIndex++;
        }

        notifyItemMoved(fromPosition, toPosition);
        notifyItemRangeChanged(Math.min(fromPosition, toPosition), Math.abs(fromPosition - toPosition) + 1);
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.video_item, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        VideoItem item = items.get(position);
        holder.titleText.setText(item.displayName);
        holder.subtitleText.setText("Position " + (position + 1));

        Bitmap thumbnail = loadVideoThumbnail(item.uri);
        if (thumbnail != null) {
            holder.thumbnailView.setImageBitmap(thumbnail);
        } else {
            holder.thumbnailView.setImageResource(R.drawable.ic_video_placeholder);
        }

        boolean isSelected = position == selectedIndex;
        if (isSelected) {
            holder.itemView.setAlpha(1.0f);
            holder.cardView.setStrokeWidth(dpToPx(3));
            holder.cardView.setStrokeColor(ContextCompat.getColor(context, com.google.android.material.R.color.m3_ref_palette_primary40));
            holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, com.google.android.material.R.color.m3_sys_color_dark_primary_container));
            holder.titleText.setTextColor(ContextCompat.getColor(context, com.google.android.material.R.color.m3_sys_color_dark_on_primary_container));
            holder.subtitleText.setTextColor(ContextCompat.getColor(context, com.google.android.material.R.color.m3_sys_color_dark_on_primary_container));
            holder.dragHandle.setTextColor(ContextCompat.getColor(context, com.google.android.material.R.color.m3_sys_color_dark_on_primary_container));
        } else {
            holder.itemView.setAlpha(0.96f);
            holder.cardView.setStrokeWidth(0);
            holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, com.google.android.material.R.color.m3_ref_palette_neutral20));
            holder.titleText.setTextColor(ContextCompat.getColor(context, android.R.color.white));
            holder.subtitleText.setTextColor(ContextCompat.getColor(context, com.google.android.material.R.color.m3_ref_palette_neutral80));
            holder.dragHandle.setTextColor(ContextCompat.getColor(context, android.R.color.white));
        }

        holder.itemView.setOnClickListener(v -> {
            int oldIndex = selectedIndex;
            selectedIndex = holder.getBindingAdapterPosition();
            if (oldIndex >= 0) {
                notifyItemChanged(oldIndex);
            }
            notifyItemChanged(selectedIndex);
        });

        holder.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                dragStartListener.onStartDrag(holder);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private Bitmap loadVideoThumbnail(android.net.Uri uri) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return context.getContentResolver().loadThumbnail(uri, new Size(320, 180), null);
            }
        } catch (Exception ignored) {
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
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
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    static class VideoViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView cardView;
        final ImageView thumbnailView;
        final TextView titleText;
        final TextView subtitleText;
        final TextView dragHandle;

        VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardView);
            thumbnailView = itemView.findViewById(R.id.thumbnailView);
            titleText = itemView.findViewById(R.id.titleText);
            subtitleText = itemView.findViewById(R.id.subtitleText);
            dragHandle = itemView.findViewById(R.id.dragHandle);
        }
    }
}
