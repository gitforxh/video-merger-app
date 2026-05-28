package com.example.videomergerapp;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class DragManageAdapter extends ItemTouchHelper.SimpleCallback {

    private final VideoAdapter adapter;

    public DragManageAdapter(VideoAdapter adapter) {
        super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        this.adapter = adapter;
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        adapter.moveItem(viewHolder.getBindingAdapterPosition(), target.getBindingAdapterPosition());
        return true;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        // no-op
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return false;
    }
}
