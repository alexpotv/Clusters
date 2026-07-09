package com.example.clusterapp;

import android.content.Context;
import android.content.SharedPreferences;

public class AppSettings {

    private static final String PREFS = "com.example.clusterapp.settings";
    private static final String KEY_LANE_WATCH_ENABLED   = "lane_watch_enabled";
    private static final String KEY_LANE_WATCH_CAMERA_ID = "lane_watch_camera_id";
    private static final String KEY_ACTIVE_MODE          = "active_mode";

    private static AppSettings sInstance;

    private final SharedPreferences mPrefs;

    private AppSettings(Context context) {
        mPrefs = context.getApplicationContext()
                        .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static AppSettings getInstance(Context context) {
        if (sInstance == null) sInstance = new AppSettings(context);
        return sInstance;
    }

    public boolean isLaneWatchEnabled() {
        return mPrefs.getBoolean(KEY_LANE_WATCH_ENABLED, false);
    }

    public void setLaneWatchEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_LANE_WATCH_ENABLED, enabled).apply();
    }

    /** Returns the user-configured camera ID, or null to use auto-detection. */
    public String getLaneWatchCameraId() {
        String id = mPrefs.getString(KEY_LANE_WATCH_CAMERA_ID, "").trim();
        return id.isEmpty() ? null : id;
    }

    public void setLaneWatchCameraId(String id) {
        mPrefs.edit().putString(KEY_LANE_WATCH_CAMERA_ID, id == null ? "" : id.trim()).apply();
    }

    // -------------------------------------------------------------------------
    // Active built-in screen mode
    // -------------------------------------------------------------------------

    public int getActiveMode() {
        return mPrefs.getInt(KEY_ACTIVE_MODE, 1);
    }

    public void setActiveMode(int mode) {
        mPrefs.edit().putInt(KEY_ACTIVE_MODE, mode).apply();
    }
}
