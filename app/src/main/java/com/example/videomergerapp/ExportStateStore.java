package com.example.videomergerapp;

import android.content.Context;
import android.content.SharedPreferences;

public class ExportStateStore {

    private static final String PREFS = "export_state";
    private static final String KEY_STATUS = "status";
    private static final String KEY_PROGRESS = "progress";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_BACKGROUND = "background";

    public static void save(Context context, String status, int progress, boolean active, boolean background) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_STATUS, status)
                .putInt(KEY_PROGRESS, progress)
                .putBoolean(KEY_ACTIVE, active)
                .putBoolean(KEY_BACKGROUND, background)
                .apply();
    }

    public static ExportState load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new ExportState(
                prefs.getString(KEY_STATUS, "Idle"),
                prefs.getInt(KEY_PROGRESS, 0),
                prefs.getBoolean(KEY_ACTIVE, false),
                prefs.getBoolean(KEY_BACKGROUND, false)
        );
    }

    public static class ExportState {
        public final String status;
        public final int progress;
        public final boolean active;
        public final boolean background;

        public ExportState(String status, int progress, boolean active, boolean background) {
            this.status = status;
            this.progress = progress;
            this.active = active;
            this.background = background;
        }
    }
}
