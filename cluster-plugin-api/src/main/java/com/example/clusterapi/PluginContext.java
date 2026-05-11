package com.example.clusterapi;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

/**
 * Context object passed to {@link ClusterPlugin#onCreateView}.
 *
 * Provides access to the plugin's own bundled assets and exposes the canvas
 * dimensions that the host allocates for each screen.
 *
 * Use {@link #getAndroidContext()} when constructing Views.  Use
 * {@link #openAsset(String)} / {@link #decodeBitmap(String)} to read files
 * from your plugin APK's {@code assets/} directory.
 */
public final class PluginContext {

    private final Context      mDisplayContext;
    private final AssetManager mPluginAssets;
    private final String       mSettingsNamespace;
    private final int          mCanvasWidth;
    private final int          mCanvasHeight;

    private PluginSettings mSettings;

    /**
     * Constructed by the host; plugin code receives an instance via onCreateView.
     *
     * @param displayContext    The cluster display's Android Context — use for View construction.
     * @param pluginApkPath     Absolute path of the installed plugin APK on disk.
     * @param settingsNamespace Unique key used to isolate this plugin's saved settings.
     *                          Use the plugin ID for marketplace plugins; any stable string for built-ins.
     * @param canvasWidth       Width of the drawing area in pixels.
     * @param canvasHeight      Height of the drawing area in pixels.
     */
    public PluginContext(Context displayContext, String pluginApkPath,
                        String settingsNamespace,
                        int canvasWidth, int canvasHeight) {
        mDisplayContext    = displayContext;
        mSettingsNamespace = settingsNamespace != null ? settingsNamespace : "";
        mCanvasWidth       = canvasWidth;
        mCanvasHeight      = canvasHeight;
        mPluginAssets      = createAssetManager(pluginApkPath);
    }

    /** The cluster display's Android Context. Use this when constructing Views. */
    public Context getAndroidContext() { return mDisplayContext; }

    /** Width of the cluster canvas in pixels (the View will be sized to exactly this). */
    public int getCanvasWidth()  { return mCanvasWidth; }

    /** Height of the cluster canvas in pixels (the View will be sized to exactly this). */
    public int getCanvasHeight() { return mCanvasHeight; }

    /**
     * Persistent settings store for this plugin.
     *
     * Values written here survive app restarts and are isolated to this plugin's namespace.
     * Backed by {@link android.content.SharedPreferences}.
     */
    public synchronized PluginSettings getSettings() {
        if (mSettings == null) {
            mSettings = new PluginSettings(mDisplayContext, mSettingsNamespace);
        }
        return mSettings;
    }

    /**
     * Open a file from your plugin APK's {@code assets/} directory.
     * The caller is responsible for closing the returned stream.
     */
    public InputStream openAsset(String name) throws IOException {
        if (mPluginAssets == null) throw new IOException("Plugin asset manager unavailable");
        return mPluginAssets.open(name);
    }

    /**
     * Decode an image file from your plugin APK's {@code assets/} directory.
     * Returns null if the asset does not exist or cannot be decoded.
     */
    public Bitmap decodeBitmap(String assetName) {
        try {
            InputStream is = openAsset(assetName);
            try {
                return BitmapFactory.decodeStream(is);
            } finally {
                is.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Internal: create an AssetManager pointed at the plugin APK via reflection.
    // addAssetPath() is a hidden but stable API present since API 1; unrestricted
    // on API < 28 (the target device runs 4.2 / API 17).
    // -------------------------------------------------------------------------

    private static AssetManager createAssetManager(String apkPath) {
        try {
            AssetManager am = AssetManager.class.newInstance();
            Method add = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            add.setAccessible(true);
            add.invoke(am, apkPath);
            return am;
        } catch (Exception e) {
            return null;
        }
    }
}
