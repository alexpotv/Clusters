package com.example.clusterapp.plugin;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Metadata for one plugin, sourced either from the remote index or the APK bundle. */
public final class PluginInfo {

    public final String id;
    public final String name;
    public final String description;
    public final String author;
    public final String collection;
    public final String version;
    public final int    apiVersion;
    public final String entryClass;

    /** Null when not fetched from the remote index. */
    public final String apkUrl;

    /** SHA-256 hex of the APK. Null when not from remote index. */
    public final String sha256;

    /** URLs to README.md at each hierarchy level. Null when unavailable. */
    public final String authorReadmeUrl;
    public final String collectionReadmeUrl;
    public final String screenReadmeUrl;

    private PluginInfo(String id, String name, String description, String author,
                       String collection, String version, int apiVersion, String entryClass,
                       String apkUrl, String sha256,
                       String authorReadmeUrl, String collectionReadmeUrl, String screenReadmeUrl) {
        this.id                 = id;
        this.name               = name;
        this.description        = description;
        this.author             = author;
        this.collection         = collection;
        this.version            = version;
        this.apiVersion         = apiVersion;
        this.entryClass         = entryClass;
        this.apkUrl             = apkUrl;
        this.sha256             = sha256;
        this.authorReadmeUrl    = authorReadmeUrl;
        this.collectionReadmeUrl = collectionReadmeUrl;
        this.screenReadmeUrl    = screenReadmeUrl;
    }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    /** Parse from the remote plugin index JSON object. */
    public static PluginInfo fromIndexJson(JSONObject obj) throws JSONException {
        return new PluginInfo(
            obj.getString("id"),
            obj.getString("name"),
            obj.optString("description", ""),
            obj.optString("author", ""),
            obj.optString("collection", ""),
            obj.getString("version"),
            obj.optInt("apiVersion", 1),
            obj.optString("entryClass", ""),
            obj.optString("apkUrl", null),
            obj.optString("sha256", null),
            obj.optString("authorReadmeUrl", null),
            obj.optString("collectionReadmeUrl", null),
            obj.optString("screenReadmeUrl", null)
        );
    }

    /**
     * Read {@code assets/plugin.json} directly from an APK (ZIP) file without loading any DEX.
     * Used to verify the bundle matches the index entry.
     */
    public static PluginInfo readFromApk(File apkFile) throws IOException, JSONException {
        ZipFile zip = new ZipFile(apkFile);
        try {
            ZipEntry entry = zip.getEntry("assets/plugin.json");
            if (entry == null) throw new IOException("assets/plugin.json not found in " + apkFile.getName());
            InputStream is = zip.getInputStream(entry);
            byte[] bytes = readBytes(is);
            is.close();
            JSONObject obj = new JSONObject(new String(bytes, "UTF-8"));
            return new PluginInfo(
                obj.getString("id"),
                obj.getString("name"),
                obj.optString("description", ""),
                obj.optString("author", ""),
                obj.optString("collection", ""),
                obj.getString("version"),
                obj.optInt("apiVersion", 1),
                obj.optString("entryClass", ""),
                null, null, null, null, null
            );
        } finally {
            zip.close();
        }
    }

    // -------------------------------------------------------------------------
    // Persistence (SharedPreferences)
    // -------------------------------------------------------------------------

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id",          id);
        obj.put("name",        name);
        obj.put("description", description);
        obj.put("author",      author);
        obj.put("collection",  collection);
        obj.put("version",     version);
        obj.put("apiVersion",  apiVersion);
        obj.put("entryClass",  entryClass);
        if (apkUrl              != null) obj.put("apkUrl",              apkUrl);
        if (sha256              != null) obj.put("sha256",              sha256);
        if (authorReadmeUrl     != null) obj.put("authorReadmeUrl",     authorReadmeUrl);
        if (collectionReadmeUrl != null) obj.put("collectionReadmeUrl", collectionReadmeUrl);
        if (screenReadmeUrl     != null) obj.put("screenReadmeUrl",     screenReadmeUrl);
        return obj;
    }

    public static PluginInfo fromStorageJson(String jsonStr) throws JSONException {
        return fromIndexJson(new JSONObject(jsonStr));
    }

    // -------------------------------------------------------------------------

    private static byte[] readBytes(InputStream is) throws IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while ((n = is.read(tmp)) != -1) buf.write(tmp, 0, n);
        return buf.toByteArray();
    }
}
