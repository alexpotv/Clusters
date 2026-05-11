package com.example.clusterapi;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persistent key-value store for a cluster screen plugin.
 *
 * Each plugin gets an isolated namespace so settings from different plugins
 * never collide.  Retrieve an instance via {@link PluginContext#getSettings()}.
 *
 * All writes are committed asynchronously via {@code apply()}.
 */
public final class PluginSettings {

    private static final String PREFS_PREFIX = "com.example.clusterapi.plugin_settings.";

    private final SharedPreferences mPrefs;

    PluginSettings(Context context, String namespace) {
        mPrefs = context.getApplicationContext()
                        .getSharedPreferences(PREFS_PREFIX + namespace, Context.MODE_PRIVATE);
    }

    // ── String ────────────────────────────────────────────────────────────────

    public String getString(String key, String defaultValue) {
        return mPrefs.getString(key, defaultValue);
    }

    public void putString(String key, String value) {
        mPrefs.edit().putString(key, value).apply();
    }

    // ── int ───────────────────────────────────────────────────────────────────

    public int getInt(String key, int defaultValue) {
        return mPrefs.getInt(key, defaultValue);
    }

    public void putInt(String key, int value) {
        mPrefs.edit().putInt(key, value).apply();
    }

    // ── boolean ───────────────────────────────────────────────────────────────

    public boolean getBoolean(String key, boolean defaultValue) {
        return mPrefs.getBoolean(key, defaultValue);
    }

    public void putBoolean(String key, boolean value) {
        mPrefs.edit().putBoolean(key, value).apply();
    }

    // ── float ─────────────────────────────────────────────────────────────────

    public float getFloat(String key, float defaultValue) {
        return mPrefs.getFloat(key, defaultValue);
    }

    public void putFloat(String key, float value) {
        mPrefs.edit().putFloat(key, value).apply();
    }
}
