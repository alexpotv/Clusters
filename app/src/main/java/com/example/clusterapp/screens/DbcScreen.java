package com.example.clusterapp.screens;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.clusterapi.ClusterPlugin;
import com.example.clusterapi.PluginContext;
import com.example.clusterapi.VehicleSnapshot;
import com.example.clusterapp.VehicleDataSource;
import com.example.clusterapp.can.CanDefinitions;
import com.example.clusterapp.can.CanMessage;
import com.example.clusterapp.can.CanSignal;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scrollable list of every CAN signal defined in CanDefinitions, showing its latest
 * parsed value as it arrives. Signals that have never been received show "—".
 *
 * Grouped by bus (FCAN / BCAN), then by message (ID + name), then by signal.
 */
public class DbcScreen implements ClusterPlugin {

    /** A single displayable signal row: which bus stats to query and the signal name/unit. */
    private static final class SignalRow {
        String  bus;    // "FCAN" or "BCAN"
        String  name;   // CanSignal.name (lookup key in stats map)
        String  unit;
        TextView valueTv;
    }

    private final List<SignalRow> mRows = new ArrayList<>();

    private final VehicleDataSource.Listener mStateListener = new VehicleDataSource.Listener() {
        @Override public void onVehicleSnapshotChanged(VehicleSnapshot s) { refresh(); }
    };

    @Override
    public View onCreateView(PluginContext context) {
        Context ctx = context.getAndroidContext();
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8));

        // ── Diag header ───────────────────────────────────────────────────────
        addSectionHeader(ctx, list, "── Diagnostics ──");
        addDiagRows(ctx, list);

        // ── Save button ───────────────────────────────────────────────────────
        Button saveBtn = new Button(ctx);
        saveBtn.setText("Save to file");
        saveBtn.setTextSize(11);
        saveBtn.setOnClickListener(v -> saveToFile(v.getContext()));
        list.addView(saveBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── FCAN signals ──────────────────────────────────────────────────────
        addSectionHeader(ctx, list, "── FCAN ──");
        for (CanMessage msg : CanDefinitions.FCAN_MESSAGES) {
            addMessageBlock(ctx, list, "FCAN", msg);
        }

        // ── BCAN signals ──────────────────────────────────────────────────────
        addSectionHeader(ctx, list, "── BCAN ──");
        for (CanMessage msg : CanDefinitions.BCAN_MESSAGES) {
            addMessageBlock(ctx, list, "BCAN", msg);
        }

        scroll.addView(list);
        scroll.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                VehicleDataSource ds = VehicleDataSource.getInstance();
                if (ds != null) ds.addListener(mStateListener);
            }
            @Override public void onViewDetachedFromWindow(View v) {
                VehicleDataSource ds = VehicleDataSource.getInstance();
                if (ds != null) ds.removeListener(mStateListener);
            }
        });
        return scroll;
    }

    // ── Diag row references ───────────────────────────────────────────────────

    private final Map<String, TextView> mDiagViews = new LinkedHashMap<>();


    private void addDiagRows(Context ctx, LinearLayout list) {
        String[][] diag = {
            {"fcan.callbacks", ""}, {"fcan.lastType",   ""}, {"fcan.decoded",    "values"},
            {"fcan.types",     ""}, {"fcan.hasRawFrame",""}, {"fcan.keys",       ""},
            {"fcan.sample",    ""},
            {"bcan.callbacks", ""}, {"bcan.lastType",   ""}, {"bcan.decoded",    "values"},
            {"bcan.types",     ""}, {"bcan.hasRawFrame",""}, {"bcan.keys",       ""},
            {"bcan.sample",    ""},
            {"diag.connected", ""}, {"diag.callbacks",  ""}, {"diag.lastKeys",   ""},
        };
        for (String[] d : diag) {
            TextView tv = addSignalRow(ctx, list, d[0], d[1]);
            mDiagViews.put(d[0], tv);
        }
    }

    // ── Message block ─────────────────────────────────────────────────────────

    private void addMessageBlock(Context ctx, LinearLayout list, String bus, CanMessage msg) {
        // Message sub-header
        TextView hdr = new TextView(ctx);
        hdr.setText(msg.id + "  " + msg.name);
        hdr.setTextColor(0xFFFFAA00);
        hdr.setTextSize(11);
        hdr.setPadding(0, dp(ctx, 8), 0, dp(ctx, 1));
        list.addView(hdr);

        for (CanSignal sig : msg.signals) {
            String unit = sig.unit != null ? sig.unit : "";
            TextView valueTv = addSignalRow(ctx, list, sig.name, unit);

            SignalRow row = new SignalRow();
            row.bus     = bus;
            row.name    = sig.name;
            row.unit    = unit;
            row.valueTv = valueTv;
            mRows.add(row);
        }
    }

    /** Adds a label + value TextView pair to the list and returns the value TextView. */
    private static TextView addSignalRow(Context ctx, LinearLayout list,
                                         String label, String unit) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView labelTv = new TextView(ctx);
        labelTv.setText(label);
        labelTv.setTextColor(0xFF888888);
        labelTv.setTextSize(10);
        labelTv.setPadding(0, dp(ctx, 1), dp(ctx, 6), dp(ctx, 1));
        row.addView(labelTv, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));

        TextView valueTv = new TextView(ctx);
        valueTv.setText("—");
        valueTv.setTextColor(0xFFCCCCCC);
        valueTv.setTextSize(10);
        valueTv.setGravity(Gravity.END);
        valueTv.setPadding(0, dp(ctx, 1), 0, dp(ctx, 1));
        row.addView(valueTv, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (!unit.isEmpty()) {
            TextView unitTv = new TextView(ctx);
            unitTv.setText(" " + unit);
            unitTv.setTextColor(0xFF555555);
            unitTv.setTextSize(10);
            unitTv.setPadding(0, dp(ctx, 1), 0, dp(ctx, 1));
            row.addView(unitTv, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        list.addView(row);
        return valueTv;
    }

    private static void addSectionHeader(Context ctx, LinearLayout list, String text) {
        TextView hdr = new TextView(ctx);
        hdr.setText(text);
        hdr.setTextColor(0xFFFFCC00);
        hdr.setTextSize(12);
        hdr.setPadding(0, dp(ctx, 12), 0, dp(ctx, 3));
        list.addView(hdr);
    }

    // ── Live update ───────────────────────────────────────────────────────────

    private void refresh() {
        VehicleDataSource ds = VehicleDataSource.getInstance();
        if (ds == null) return;

        // Diag
        setDiag("fcan.callbacks",   ds.diagFcanCallbacks);
        setDiag("fcan.lastType",    ds.diagLastFcanType < 0 ? "-1"
                : ds.diagLastFcanType + " (0x" + Integer.toHexString(ds.diagLastFcanType) + ")");
        setDiag("fcan.decoded",     ds.diagFcanDecoded);
        setDiag("fcan.types",       ds.diagFcanTypes.isEmpty()       ? "(none)" : ds.diagFcanTypes);
        setDiag("fcan.hasRawFrame", ds.diagFcanHasRawFrame);
        setDiag("fcan.keys",        ds.diagFcanBundleKeys.isEmpty()  ? "(none)" : ds.diagFcanBundleKeys);
        setDiag("fcan.sample",      ds.diagFcanSample.isEmpty()      ? "(none)" : ds.diagFcanSample);
        setDiag("bcan.callbacks",   ds.diagBcanCallbacks);
        setDiag("bcan.lastType",    ds.diagLastBcanType < 0 ? "-1"
                : ds.diagLastBcanType + " (0x" + Integer.toHexString(ds.diagLastBcanType) + ")");
        setDiag("bcan.decoded",     ds.diagBcanDecoded);
        setDiag("bcan.types",       ds.diagBcanTypes.isEmpty()       ? "(none)" : ds.diagBcanTypes);
        setDiag("bcan.hasRawFrame", ds.diagBcanHasRawFrame);
        setDiag("bcan.keys",        ds.diagBcanBundleKeys.isEmpty()  ? "(none)" : ds.diagBcanBundleKeys);
        setDiag("bcan.sample",      ds.diagBcanSample.isEmpty()      ? "(none)" : ds.diagBcanSample);
        setDiag("diag.connected",   ds.diagDiagConnected);
        setDiag("diag.callbacks",   ds.diagDiagCallbacks);
        setDiag("diag.lastKeys",    ds.diagDiagLastKeys.isEmpty()    ? "(none)" : ds.diagDiagLastKeys);

        // CAN signal rows
        Map<String, double[]> fcanStats = ds.getFcanStats();
        Map<String, double[]> bcanStats = ds.getBcanStats();

        for (SignalRow r : mRows) {
            Map<String, double[]> stats = "FCAN".equals(r.bus) ? fcanStats : bcanStats;
            double[] entry = stats.get(r.name);
            if (entry == null) {
                r.valueTv.setText("—");
            } else {
                double v = entry[2]; // latest
                r.valueTv.setTextColor(0xFF00FF88);
                r.valueTv.setText(formatValue(v));
            }
        }

    }

    /** Format a double value: integer if no fractional part, otherwise 3 decimal places. */
    private static String formatValue(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return String.valueOf(v);
        if (v == Math.floor(v) && Math.abs(v) < 1e9) return String.valueOf((long) v);
        return String.format("%.3f", v);
    }

    private void setDiag(String key, Object value) {
        TextView tv = mDiagViews.get(key);
        if (tv != null) tv.setText(String.valueOf(value));
    }

    // ── Save to file ──────────────────────────────────────────────────────────

    private static void saveToFile(Context ctx) {
        VehicleDataSource ds = VehicleDataSource.getInstance();
        if (ds == null) {
            Toast.makeText(ctx, "No data source", Toast.LENGTH_SHORT).show();
            return;
        }
        File dir = ctx.getExternalFilesDir(null);
        if (dir == null) dir = ctx.getFilesDir();
        File file = new File(dir, "can_keys.txt");
        try (FileWriter w = new FileWriter(file)) {
            w.write("=== FCAN ===\n");
            writeStats(w, ds.getFcanStats());
            w.write("\n=== BCAN ===\n");
            writeStats(w, ds.getBcanStats());
        } catch (IOException e) {
            Toast.makeText(ctx, "Write failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(ctx, "Saved: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
    }

    private static void writeStats(FileWriter w, Map<String, double[]> stats) throws IOException {
        for (Map.Entry<String, double[]> e : stats.entrySet()) {
            double[] s = e.getValue();
            w.write(String.format("%-40s latest=%-12s min=%-12s max=%s\n",
                    e.getKey(), s[2], s[0], s[1]));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int dp(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    @Override public void onStart() {}
    @Override public void onVehicleSnapshotChanged(VehicleSnapshot snapshot) {}
    @Override public void onStop() {}
}
