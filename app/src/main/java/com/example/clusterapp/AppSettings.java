package com.example.clusterapp;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.clusterapp.plugin.RepoInfo;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

public class AppSettings {

    private static final String PREFS = "com.example.clusterapp.settings";
    private static final String KEY_LANE_WATCH_ENABLED   = "lane_watch_enabled";
    private static final String KEY_LANE_WATCH_CAMERA_ID = "lane_watch_camera_id";
    private static final String KEY_CUSTOM_REPOS         = "custom_repos";
    private static final String KEY_ACTIVE_REPO_URL      = "active_repo_url";
    private static final String KEY_DEVELOPER_MODE       = "developer_mode";

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
    // Custom repositories
    // -------------------------------------------------------------------------

    public List<RepoInfo> getCustomRepos() {
        String json = mPrefs.getString(KEY_CUSTOM_REPOS, "[]");
        List<RepoInfo> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(RepoInfo.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException ignored) {}
        return list;
    }

    public void addCustomRepo(RepoInfo repo) {
        List<RepoInfo> repos = getCustomRepos();
        repos.add(repo);
        saveCustomRepos(repos);
    }

    public void removeCustomRepo(String url) {
        List<RepoInfo> repos = getCustomRepos();
        for (int i = repos.size() - 1; i >= 0; i--) {
            if (repos.get(i).url.equals(url)) repos.remove(i);
        }
        saveCustomRepos(repos);
    }

    private void saveCustomRepos(List<RepoInfo> repos) {
        JSONArray arr = new JSONArray();
        for (RepoInfo r : repos) {
            try { arr.put(r.toJson()); } catch (JSONException ignored) {}
        }
        mPrefs.edit().putString(KEY_CUSTOM_REPOS, arr.toString()).apply();
    }

    // -------------------------------------------------------------------------
    // Active repository (empty string = official)
    // -------------------------------------------------------------------------

    /** Returns the URL of the active repository, or empty string for the official repo. */
    public String getActiveRepoUrl() {
        return mPrefs.getString(KEY_ACTIVE_REPO_URL, "");
    }

    public void setActiveRepoUrl(String url) {
        mPrefs.edit().putString(KEY_ACTIVE_REPO_URL, url == null ? "" : url).apply();
    }

    // -------------------------------------------------------------------------
    // Developer mode
    // -------------------------------------------------------------------------

    public boolean isDeveloperModeEnabled() {
        return mPrefs.getBoolean(KEY_DEVELOPER_MODE, false);
    }

    public void setDeveloperModeEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_DEVELOPER_MODE, enabled).apply();
    }
}
