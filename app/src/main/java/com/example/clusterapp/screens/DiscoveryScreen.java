package com.example.clusterapp.screens;

import android.content.Context;
import android.text.TextUtils;
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Shows every integer-valued key seen in FCAN and BCAN callbacks, grouped by message type code.
 * This reveals all signals the middleware delivers — including ones beyond what the hardcoded
 * switch cases currently extract.
 *
 * Color coding by time since last value change:
 *   Bright yellow  — changed < 2 s ago
 *   Amber          — changed < 6 s ago
 *   Dim yellow     — changed < 15 s ago
 *   Dark gray      — stale / never changed
 *
 * How to use for signal mapping:
 *   1. Perform a physical action (turn wheel, press brake, open door, etc.)
 *   2. Watch which rows turn yellow — those are the signals that responded.
 *   3. The signal name is the Mitsubishi middleware key (e.g. "ENG_ENG_SPEED").
 *   4. Tap "Save snapshot" after each action for an offline record.
 */
public class DiscoveryScreen implements ClusterPlugin {

    // ── Per-signal display row ────────────────────────────────────────────────

    private static final class SignalRow {
        TextView valueTv;
        TextView rangeTv;
    }

    // ── Per-type-code section ─────────────────────────────────────────────────

    private static final class TypeSection {
        LinearLayout           container;
        Map<String, SignalRow> rows = new LinkedHashMap<>();
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final Map<Integer, TypeSection> mFcanSections = new LinkedHashMap<>();
    private final Map<Integer, TypeSection> mBcanSections = new LinkedHashMap<>();
    private LinearLayout mFcanContainer;
    private LinearLayout mBcanContainer;
    private TextView     mStatusTv;

    private final VehicleDataSource.Listener mListener = new VehicleDataSource.Listener() {
        @Override public void onVehicleSnapshotChanged(VehicleSnapshot s) { refresh(); }
    };

    // ── View construction ─────────────────────────────────────────────────────

    @Override
    public View onCreateView(PluginContext context) {
        Context ctx = context.getAndroidContext();
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8));

        mStatusTv = new TextView(ctx);
        mStatusTv.setTextColor(0xFF888888);
        mStatusTv.setTextSize(10);
        mStatusTv.setPadding(0, 0, 0, dp(ctx, 6));
        root.addView(mStatusTv);

        Button saveBtn = new Button(ctx);
        saveBtn.setText("Save snapshot");
        saveBtn.setTextSize(11);
        saveBtn.setOnClickListener(v -> saveToFile(v.getContext()));
        root.addView(saveBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        addHeader(ctx, root, "── FCAN ──");
        mFcanContainer = new LinearLayout(ctx);
        mFcanContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(mFcanContainer);

        addHeader(ctx, root, "── BCAN ──");
        mBcanContainer = new LinearLayout(ctx);
        mBcanContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(mBcanContainer);

        scroll.addView(root);
        scroll.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                VehicleDataSource ds = VehicleDataSource.getInstance();
                if (ds != null) ds.addListener(mListener);
            }
            @Override public void onViewDetachedFromWindow(View v) {
                VehicleDataSource ds = VehicleDataSource.getInstance();
                if (ds != null) ds.removeListener(mListener);
            }
        });
        return scroll;
    }

    // ── Live refresh ──────────────────────────────────────────────────────────

    private void refresh() {
        VehicleDataSource ds = VehicleDataSource.getInstance();
        if (ds == null) return;

        Map<String, double[]> fcanRaw  = ds.getFcanRaw();
        Map<String, double[]> bcanRaw  = ds.getBcanRaw();
        Map<String, Long>     fcanAt   = ds.getFcanRawChangedAt();
        Map<String, Long>     bcanAt   = ds.getBcanRawChangedAt();

        if (mStatusTv != null) {
            mStatusTv.setText(
                    "fcan callbacks=" + ds.diagFcanCallbacks
                    + "  bcan callbacks=" + ds.diagBcanCallbacks
                    + "  fcan keys=" + fcanRaw.size()
                    + "  bcan keys=" + bcanRaw.size()
                    + "  diag=" + (ds.diagDiagConnected ? "ok" : "n/a"));
        }

        long now = System.currentTimeMillis();
        updateBus(mFcanContainer, mFcanSections, fcanRaw, fcanAt, now);
        updateBus(mBcanContainer, mBcanSections, bcanRaw, bcanAt, now);
    }

    private void updateBus(LinearLayout container, Map<Integer, TypeSection> sections,
                            Map<String, double[]> raw, Map<String, Long> changedAt, long now) {
        if (container == null) return;
        Context ctx = container.getContext();

        // Collect type codes present in the raw map, sorted numerically.
        Map<Integer, List<String>> byType = new TreeMap<>();
        for (String compound : raw.keySet()) {
            int slash = compound.indexOf('/');
            if (slash < 0) continue;
            int type;
            try { type = Integer.parseInt(compound.substring(0, slash)); }
            catch (NumberFormatException e) { continue; }
            List<String> list = byType.get(type);
            if (list == null) { list = new ArrayList<>(); byType.put(type, list); }
            list.add(compound.substring(slash + 1));
        }

        for (Map.Entry<Integer, List<String>> e : byType.entrySet()) {
            int type = e.getKey();
            List<String> keys = e.getValue();
            Collections.sort(keys);

            TypeSection sec = sections.get(type);
            if (sec == null) {
                sec = new TypeSection();
                sec.container = new LinearLayout(ctx);
                sec.container.setOrientation(LinearLayout.VERTICAL);

                TextView typeHdr = new TextView(ctx);
                typeHdr.setText(type + "  (0x" + Integer.toHexString(type) + ")");
                typeHdr.setTextColor(0xFFFFAA00);
                typeHdr.setTextSize(10);
                typeHdr.setPadding(0, dp(ctx, 8), 0, dp(ctx, 2));
                container.addView(typeHdr);
                container.addView(sec.container);
                sections.put(type, sec);
            }

            for (String sigName : keys) {
                String compound = type + "/" + sigName;
                if (!sec.rows.containsKey(sigName)) {
                    sec.rows.put(sigName, addRow(ctx, sec.container, sigName));
                }
                SignalRow row = sec.rows.get(sigName);
                double[] s = raw.get(compound);
                if (s == null || row == null) continue;

                Long changedMs = changedAt.get(compound);
                long ageMs = (changedMs == null) ? Long.MAX_VALUE : (now - changedMs);

                int valueColor;
                if      (ageMs <  2_000) valueColor = 0xFFFFFF00;
                else if (ageMs <  6_000) valueColor = 0xFFCCA000;
                else if (ageMs < 15_000) valueColor = 0xFF888800;
                else                     valueColor = 0xFF505050;

                row.valueTv.setTextColor(valueColor);
                row.valueTv.setText(fmt(s[2]));
                row.rangeTv.setTextColor(ageMs < 15_000 ? 0xFF443300 : 0xFF2A2A2A);
                row.rangeTv.setText(s[0] != s[1] ? fmt(s[0]) + ".." + fmt(s[1]) : "");
            }
        }
    }

    // ── Row helpers ───────────────────────────────────────────────────────────

    private static SignalRow addRow(Context ctx, LinearLayout parent, String name) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView nameTv = new TextView(ctx);
        nameTv.setText(name);
        nameTv.setTextColor(0xFF555555);
        nameTv.setTextSize(9);
        nameTv.setSingleLine(true);
        nameTv.setEllipsize(TextUtils.TruncateAt.END);
        nameTv.setPadding(0, dp(ctx, 1), dp(ctx, 4), dp(ctx, 1));
        row.addView(nameTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 4f));

        TextView valueTv = new TextView(ctx);
        valueTv.setText("—");
        valueTv.setTextColor(0xFF505050);
        valueTv.setTextSize(9);
        valueTv.setGravity(Gravity.END);
        valueTv.setPadding(0, dp(ctx, 1), dp(ctx, 6), dp(ctx, 1));
        row.addView(valueTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));

        TextView rangeTv = new TextView(ctx);
        rangeTv.setText("");
        rangeTv.setTextColor(0xFF2A2A2A);
        rangeTv.setTextSize(9);
        rangeTv.setGravity(Gravity.END);
        rangeTv.setPadding(0, dp(ctx, 1), 0, dp(ctx, 1));
        row.addView(rangeTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f));

        parent.addView(row);
        SignalRow sr = new SignalRow();
        sr.valueTv = valueTv;
        sr.rangeTv = rangeTv;
        return sr;
    }

    private static void addHeader(Context ctx, LinearLayout list, String text) {
        TextView hdr = new TextView(ctx);
        hdr.setText(text);
        hdr.setTextColor(0xFFFFCC00);
        hdr.setTextSize(11);
        hdr.setPadding(0, dp(ctx, 10), 0, dp(ctx, 3));
        list.addView(hdr);
    }

    // ── Save snapshot ─────────────────────────────────────────────────────────

    private static void saveToFile(Context ctx) {
        VehicleDataSource ds = VehicleDataSource.getInstance();
        if (ds == null) {
            Toast.makeText(ctx, "No data source", Toast.LENGTH_SHORT).show();
            return;
        }
        File dir  = ctx.getExternalFilesDir(null);
        if (dir == null) dir = ctx.getFilesDir();
        String ts   = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File   file = new File(dir, "raw_signals_" + ts + ".txt");
        try (FileWriter w = new FileWriter(file)) {
            w.write("Raw Signal Snapshot  " + ts + "\n");
            w.write("fcan_callbacks=" + ds.diagFcanCallbacks
                    + "  bcan_callbacks=" + ds.diagBcanCallbacks + "\n\n");
            writeBus(w, "FCAN", ds.getFcanRaw());
            writeBus(w, "BCAN", ds.getBcanRaw());
        } catch (IOException e) {
            Toast.makeText(ctx, "Write failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(ctx, "Saved: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
    }

    private static void writeBus(FileWriter w, String bus,
                                  Map<String, double[]> raw) throws IOException {
        // Group by type code.
        Map<Integer, List<String>> byType = new TreeMap<>();
        for (String compound : raw.keySet()) {
            int slash = compound.indexOf('/');
            if (slash < 0) continue;
            try {
                int type = Integer.parseInt(compound.substring(0, slash));
                List<String> list = byType.get(type);
                if (list == null) { list = new ArrayList<>(); byType.put(type, list); }
                list.add(compound.substring(slash + 1));
            } catch (NumberFormatException ignored) {}
        }
        w.write("=== " + bus + " ===\n");
        for (Map.Entry<Integer, List<String>> e : byType.entrySet()) {
            int type = e.getKey();
            w.write("  " + type + " (0x" + Integer.toHexString(type) + ")\n");
            List<String> keys = e.getValue();
            Collections.sort(keys);
            for (String sig : keys) {
                double[] s = raw.get(type + "/" + sig);
                if (s == null) continue;
                w.write(String.format(Locale.US, "    %-45s  latest=%-10s  min=%-10s  max=%s\n",
                        sig, fmt(s[2]), fmt(s[0]), fmt(s[1])));
            }
        }
        w.write("\n");
    }

    private static String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return String.valueOf(v);
        if (v == Math.floor(v) && Math.abs(v) < 1e9)  return String.valueOf((long) v);
        return String.format(Locale.US, "%.2f", v);
    }

    private static int dp(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    @Override public void onStart() {}
    @Override public void onVehicleSnapshotChanged(VehicleSnapshot snapshot) {}
    @Override public void onStop() {}
}
