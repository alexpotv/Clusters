package com.example.clusterapp.plugin;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages installed plugins: persists metadata in SharedPreferences and stores
 * APK files in the app's private files directory.
 *
 * Thread-safe; may be called from both UI and service threads.
 */
public final class PluginRegistry {

    private static final String PREFS          = "com.example.clusterapp.plugin_registry";
    private static final String KEY_IDS        = "installed_ids";
    private static final String KEY_JSON       = "plugin_json_";
    private static final String KEY_ACTIVE_ID  = "active_plugin_id";
    private static final String KEY_ACTIVE_MODE = "active_mode";

    private static volatile PluginRegistry sInstance;

    public static PluginRegistry getInstance(Context context) {
        if (sInstance == null) {
            synchronized (PluginRegistry.class) {
                if (sInstance == null) {
                    sInstance = new PluginRegistry(context.getApplicationContext());
                }
            }
        }
        return sInstance;
    }

    // -------------------------------------------------------------------------

    private final Context          mContext;
    private final File             mPluginsDir;
    private final SharedPreferences mPrefs;

    private PluginRegistry(Context appContext) {
        mContext    = appContext;
        mPrefs      = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        mPluginsDir = new File(appContext.getFilesDir(), "plugins");
        mPluginsDir.mkdirs();
    }

    // -------------------------------------------------------------------------
    // Install / uninstall
    // -------------------------------------------------------------------------

    /** Copy {@code apkFile} into private storage and persist {@code info}. */
    public synchronized void install(File apkFile, PluginInfo info) throws IOException {
        File dest = apkFileFor(info.id);
        copyFile(apkFile, dest);

        try {
            Set<String> ids = new HashSet<>(getInstalledIds());
            ids.add(info.id);
            mPrefs.edit()
                .putStringSet(KEY_IDS, ids)
                .putString(KEY_JSON + info.id, info.toJson().toString())
                .apply();
        } catch (JSONException e) {
            dest.delete();
            throw new IOException("Failed to serialize plugin metadata", e);
        }
    }

    /** Remove the APK and all stored metadata for the given plugin. */
    public synchronized void uninstall(String pluginId) {
        apkFileFor(pluginId).delete();

        Set<String> ids = new HashSet<>(getInstalledIds());
        ids.remove(pluginId);
        SharedPreferences.Editor ed = mPrefs.edit()
            .putStringSet(KEY_IDS, ids)
            .remove(KEY_JSON + pluginId);

        if (pluginId.equals(getActivePluginId())) {
            ed.remove(KEY_ACTIVE_ID);
        }
        ed.apply();
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public synchronized List<PluginInfo> listInstalled() {
        Set<String> ids = getInstalledIds();
        List<PluginInfo> result = new ArrayList<>();
        for (String id : ids) {
            PluginInfo info = getInfo(id);
            if (info != null) result.add(info);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns installed plugins grouped as author → collection → list of plugins.
     * The maps preserve insertion order.
     */
    public synchronized Map<String, Map<String, List<PluginInfo>>> listInstalledByHierarchy() {
        Map<String, Map<String, List<PluginInfo>>> result = new LinkedHashMap<>();
        for (PluginInfo info : listInstalled()) {
            String author     = info.author     != null && !info.author.isEmpty()     ? info.author     : "unknown";
            String collection = info.collection != null && !info.collection.isEmpty() ? info.collection : "other";
            if (!result.containsKey(author)) result.put(author, new LinkedHashMap<String, List<PluginInfo>>());
            Map<String, List<PluginInfo>> collections = result.get(author);
            if (!collections.containsKey(collection)) collections.put(collection, new ArrayList<PluginInfo>());
            collections.get(collection).add(info);
        }
        return result;
    }

    public synchronized PluginInfo getInfo(String pluginId) {
        String json = mPrefs.getString(KEY_JSON + pluginId, null);
        if (json == null) return null;
        try {
            return PluginInfo.fromStorageJson(json);
        } catch (JSONException e) {
            return null;
        }
    }

    public File getApkFile(String pluginId) {
        return apkFileFor(pluginId);
    }

    public synchronized boolean isInstalled(String pluginId) {
        return getInstalledIds().contains(pluginId);
    }

    // -------------------------------------------------------------------------
    // Active plugin / mode persistence
    // -------------------------------------------------------------------------

    /** Persist the active plugin ID. Pass null to clear (i.e. a built-in is active). */
    public synchronized void setActivePluginId(String pluginId) {
        SharedPreferences.Editor ed = mPrefs.edit();
        if (pluginId == null) {
            ed.remove(KEY_ACTIVE_ID);
        } else {
            ed.putString(KEY_ACTIVE_ID, pluginId);
            ed.remove(KEY_ACTIVE_MODE);
        }
        ed.apply();
    }

    public synchronized String getActivePluginId() {
        return mPrefs.getString(KEY_ACTIVE_ID, null);
    }

    /** Persist the active built-in mode number. */
    public synchronized void setActiveMode(int mode) {
        mPrefs.edit()
            .putInt(KEY_ACTIVE_MODE, mode)
            .remove(KEY_ACTIVE_ID)
            .apply();
    }

    public synchronized int getActiveMode() {
        return mPrefs.getInt(KEY_ACTIVE_MODE, 1);
    }

    // -------------------------------------------------------------------------

    private Set<String> getInstalledIds() {
        Set<String> stored = mPrefs.getStringSet(KEY_IDS, null);
        return stored != null ? new HashSet<>(stored) : new HashSet<String>();
    }

    private File apkFileFor(String pluginId) {
        return new File(mPluginsDir, pluginId.replaceAll("[^a-zA-Z0-9._\\-]", "_") + ".apk");
    }

    private static void copyFile(File src, File dst) throws IOException {
        FileInputStream  in  = new FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst);
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        } finally {
            in.close();
            out.close();
        }
    }
}
