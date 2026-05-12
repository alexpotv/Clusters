package com.example.clusterapp.plugin;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/**
 * Fetches the remote plugin index and downloads plugin APKs and README files.
 * All public methods are non-blocking; results are delivered on the main thread.
 */
public final class PluginRepository {

    public static final String INDEX_URL =
        "https://alexpotv.github.io/Clusters/index.json";

    public interface Callback<T> {
        void onSuccess(T result);
        void onFailure(String error);
    }

    // -------------------------------------------------------------------------

    private final Context mContext;
    private final Handler mMain = new Handler(Looper.getMainLooper());

    public PluginRepository(Context context) {
        mContext = context.getApplicationContext();
    }

    // -------------------------------------------------------------------------
    // Index fetch — flat list
    // -------------------------------------------------------------------------

    public void fetchIndex(final Callback<List<PluginInfo>> callback) {
        fetchIndex(INDEX_URL, callback);
    }

    public void fetchIndex(final String url, final Callback<List<PluginInfo>> callback) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final List<PluginInfo> result = fetchIndexSync(url);
                    mMain.post(new Runnable() {
                        @Override public void run() { callback.onSuccess(result); }
                    });
                } catch (final Exception e) {
                    final String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                    mMain.post(new Runnable() {
                        @Override public void run() { callback.onFailure(msg); }
                    });
                }
            }
        }, "PluginRepository-fetch").start();
    }

    // -------------------------------------------------------------------------
    // Index fetch — grouped by author → collection
    // -------------------------------------------------------------------------

    public void fetchIndexGrouped(
            final Callback<Map<String, Map<String, List<PluginInfo>>>> callback) {
        fetchIndex(new Callback<List<PluginInfo>>() {
            @Override public void onSuccess(List<PluginInfo> plugins) {
                callback.onSuccess(groupByHierarchy(plugins));
            }
            @Override public void onFailure(String error) {
                callback.onFailure(error);
            }
        });
    }

    /** Group a flat plugin list into author → collection → plugins. */
    public static Map<String, Map<String, List<PluginInfo>>> groupByHierarchy(
            List<PluginInfo> plugins) {
        Map<String, Map<String, List<PluginInfo>>> result = new LinkedHashMap<>();
        for (PluginInfo info : plugins) {
            String author     = info.author     != null && !info.author.isEmpty()     ? info.author     : "unknown";
            String collection = info.collection != null && !info.collection.isEmpty() ? info.collection : "other";
            if (!result.containsKey(author)) result.put(author, new LinkedHashMap<String, List<PluginInfo>>());
            Map<String, List<PluginInfo>> cols = result.get(author);
            if (!cols.containsKey(collection)) cols.put(collection, new ArrayList<PluginInfo>());
            cols.get(collection).add(info);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // README fetch
    // -------------------------------------------------------------------------

    /** Fetch the raw text of a README.md from the given URL. */
    public void fetchReadme(final String readmeUrl, final Callback<String> callback) {
        if (readmeUrl == null || readmeUrl.isEmpty()) {
            callback.onFailure("No README URL");
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final String text = httpGet(readmeUrl);
                    mMain.post(new Runnable() {
                        @Override public void run() { callback.onSuccess(text); }
                    });
                } catch (final Exception e) {
                    final String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                    mMain.post(new Runnable() {
                        @Override public void run() { callback.onFailure(msg); }
                    });
                }
            }
        }, "PluginRepository-readme").start();
    }

    // -------------------------------------------------------------------------
    // Download
    // -------------------------------------------------------------------------

    public void download(final PluginInfo info, final Callback<File> callback) {
        if (info.apkUrl == null) {
            callback.onFailure("No download URL for plugin: " + info.id);
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final File result = downloadSync(info);
                    mMain.post(new Runnable() {
                        @Override public void run() { callback.onSuccess(result); }
                    });
                } catch (final Exception e) {
                    final String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                    mMain.post(new Runnable() {
                        @Override public void run() { callback.onFailure(msg); }
                    });
                }
            }
        }, "PluginRepository-download-" + info.id).start();
    }

    private File downloadSync(PluginInfo info) throws IOException {
        File tmp = new File(mContext.getCacheDir(),
            info.id.replaceAll("[^a-zA-Z0-9._\\-]", "_") + "-" + info.version + ".apk");

        URL url = new URL(info.apkUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        enableTls(conn);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(60_000);
        try {
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + conn.getResponseCode() + " from " + info.apkUrl);
            }
            saveStream(conn.getInputStream(), tmp);
        } finally {
            conn.disconnect();
        }

        if (info.sha256 != null && !info.sha256.isEmpty()) {
            String actual = sha256Hex(tmp);
            if (!actual.equalsIgnoreCase(info.sha256)) {
                tmp.delete();
                throw new IOException(
                    "SHA-256 mismatch for " + info.id
                    + "\n  expected: " + info.sha256
                    + "\n  got:      " + actual);
            }
        }

        return tmp;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<PluginInfo> fetchIndexSync(String url) throws IOException, JSONException {
        String body = httpGet(url);
        JSONArray arr = new JSONArray(body);
        List<PluginInfo> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            list.add(PluginInfo.fromIndexJson(arr.getJSONObject(i)));
        }
        return list;
    }

    private static String httpGet(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        enableTls(conn);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        try {
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + conn.getResponseCode() + " from " + urlStr);
            }
            return readString(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }

    private static final SSLSocketFactory TLS_FACTORY = TlsSocketFactory.create();

    /**
     * Apply the custom TLS factory to the connection.
     * Enables TLS 1.1/1.2 (disabled by default on API 17) and works around
     * missing intermediate CAs in the Android 4.x system trust store.
     */
    private static void enableTls(HttpURLConnection conn) {
        if (conn instanceof HttpsURLConnection) {
            ((HttpsURLConnection) conn).setSSLSocketFactory(TLS_FACTORY);
        }
    }

    private static String readString(InputStream is) throws IOException {
        return new String(readBytes(is), "UTF-8");
    }

    private static void saveStream(InputStream is, File dest) throws IOException {
        FileOutputStream out = new FileOutputStream(dest);
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        } finally {
            out.close();
        }
    }

    private static byte[] readBytes(InputStream is) throws IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = is.read(tmp)) != -1) buf.write(tmp, 0, n);
        return buf.toByteArray();
    }

    private static String sha256Hex(File file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            InputStream fis = new java.io.FileInputStream(file);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = fis.read(buf)) != -1) md.update(buf, 0, n);
            } finally {
                fis.close();
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
    }
}
