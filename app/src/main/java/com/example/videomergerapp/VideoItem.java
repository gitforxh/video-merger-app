package com.example.videomergerapp;

import android.net.Uri;

public class VideoItem {
    public final Uri uri;
    public final String displayName;

    public VideoItem(Uri uri, String displayName) {
        this.uri = uri;
        this.displayName = displayName;
    }
}
