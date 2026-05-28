package com.example.videomergerapp;

import android.content.Context;
import android.content.SharedPreferences;

public class ExportStateStore {

    private static final String PREFS = "export_state";
    private static final String KEY_STATUS = "status";
    private static final String KEY_PROGRESS = "progress";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_BACKGROUND = "background";
    private static final String KEY_SESSION_ID = "session_id";

    public static void save(Context context, String status, int progress, boolean active, boolean background) {
        save(context, status, progress, active, background, null);
    }

    public static void save(Context context, String status, int progress, boolean active, boolean background, String sessionId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_STATUS, status)
                .putInt(KEY_PROGRESS, progress)
                .putBoolean(KEY_ACTIVE, active)
                .putBoolean(KEY_BACKGROUND, background)
                .putString(KEY_SESSION_ID, sessionId)
                .apply();
    }

    public static ExportState load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new ExportState(
                prefs.getString(KEY_STATUS, "Idle"),
                prefs.getInt(KEY_PROGRESS, 0),
                prefs.getBoolean(KEY_ACTIVE, false),
                prefs.getBoolean(KEY_BACKGROUND, false),
                prefs.getString(KEY_SESSION_ID, null)
        );
    }

    public static class ExportState {
        public final String status;
        public final int progress;
        public final boolean active;
        public final boolean background;
        public final String sessionId;

        public ExportState(String status, int progress, boolean active, boolean background, String sessionId) {
            this.status = status;
            this.progress = progress;
            this.active = active;
            this.background = background;
            this.sessionId = sessionId;
        }
    }
}
