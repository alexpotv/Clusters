package com.example.clusterapp.plugin;

import android.content.Context;

import com.example.clusterapi.ClusterPlugin;

import dalvik.system.DexClassLoader;

import java.io.File;

/**
 * Loads a {@link ClusterPlugin} from an installed plugin APK using
 * {@link DexClassLoader}.
 *
 * The host app's classloader is used as parent, so the plugin's DEX can
 * resolve {@code ClusterPlugin}, {@code VehicleSnapshot}, and
 * {@code PluginContext} without bundling them — as long as the plugin APK was
 * compiled with the matching {@code cluster-plugin-api} as {@code compileOnly}.
 */
public final class PluginLoader {

    /** Plugins declaring a higher apiVersion than this are rejected. */
    public static final int HOST_API_VERSION = 1;

    private final File mOdexDir;

    public PluginLoader(Context context) {
        mOdexDir = context.getDir("plugin-odex", Context.MODE_PRIVATE);
    }

    /**
     * Load and instantiate the plugin's entry class.
     *
     * @throws PluginLoadException if the apiVersion is incompatible, the entry
     *                             class cannot be found, or it does not implement
     *                             {@link ClusterPlugin}.
     */
    public ClusterPlugin load(PluginInfo info, File apkFile) throws PluginLoadException {
        if (info.apiVersion > HOST_API_VERSION) {
            throw new PluginLoadException(
                "Plugin requires API " + info.apiVersion
                + " but host only supports " + HOST_API_VERSION);
        }
        if (!apkFile.isFile()) {
            throw new PluginLoadException("APK not found: " + apkFile.getAbsolutePath());
        }

        DexClassLoader loader = new DexClassLoader(
            apkFile.getAbsolutePath(),
            mOdexDir.getAbsolutePath(),
            null,                                        // no native lib dir
            ClusterPlugin.class.getClassLoader());       // host classloader as parent

        try {
            Class<?> cls      = loader.loadClass(info.entryClass);
            Object   instance = cls.newInstance();
            if (!(instance instanceof ClusterPlugin)) {
                throw new PluginLoadException(
                    info.entryClass + " does not implement ClusterPlugin");
            }
            return (ClusterPlugin) instance;
        } catch (ClassNotFoundException e) {
            throw new PluginLoadException("Entry class not found: " + info.entryClass, e);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new PluginLoadException("Cannot instantiate " + info.entryClass
                + " (needs public no-arg constructor)", e);
        }
    }
}
