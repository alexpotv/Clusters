package com.example.clusterapp.screens;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.clusterapi.ClusterPlugin;
import com.example.clusterapi.PluginContext;
import com.example.clusterapi.VehicleSnapshot;
import com.example.clusterapp.VehicleDataSource;
import com.example.clusterapp.can.CanDecoder;
import com.example.clusterapp.can.CanDefinitions;
import com.example.clusterapp.can.CanMessage;
import com.example.clusterapp.can.CanSignal;
import com.example.clusterapp.can.SignalCatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Scrollable, real-time inspector for every vehicle signal the head-unit middleware relays.
 *
 * <p>The data path: the companion microcontroller (layer D) forwards a fixed set of CAN frames to
 * the SoC; {@code VehicleInfoManagerApService} (layer A) pre-decodes each frame into named integer
 * Bundle values. {@link VehicleDataSource} captures every one of those integers as it arrives
 * ({@code getFcanRaw()/getBcanRaw()}), so this screen shows the maximum data reachable without
 * probing below what the MCU already forwards.
 *
 * <p>Each row shows, when available: the signal <b>name</b> (friendly name from
 * {@link SignalCatalog}, falling back to the raw Mitsubishi key), the <b>raw</b> integer the
 * middleware delivered, and the <b>value</b> + <b>unit</b> after applying the firmware/opendbc
 * scaling. Signals are grouped by frame (CAN id); the frame's raw {@code byte[8]} is shown too on
 * the rare occasion the middleware forwards it (stock firmware delivers named ints only).
 *
 * <p>Recency coloring (time since the value last changed):
 * bright = &lt;2 s, amber = &lt;6 s, dim = &lt;15 s, gray = stale.
 */
public class CanDataScreen implements ClusterPlugin {

    // ── Cached views, rebuilt lazily as new frames / signals appear ───────────

    private static final class SignalRow {
        TextView rawTv;
        TextView valueTv;
    }

    private static final class FrameSection {
        LinearLayout           container;   // holds the signal rows
        TextView               bytesTv;     // raw byte[8] line (only populated if forwarded)
        Map<String, SignalRow> rows = new LinkedHashMap<>();
    }

    // Raw frames straight from CpuComService (Tier 1): raw byte[8] + DBC-decoded signals.
    private static final class RawSection {
        TextView              metaTv;      // receiveState + hex payload
        Map<String, TextView> sigValues = new LinkedHashMap<>(); // DBC signal name → value view
    }

    private final Map<Integer, FrameSection> mFcanSections = new LinkedHashMap<>();
    private final Map<Integer, FrameSection> mBcanSections = new LinkedHashMap<>();
    private final Map<Integer, RawSection>   mCpuFcanSections = new LinkedHashMap<>();
    private final Map<Integer, RawSection>   mCpuBcanSections = new LinkedHashMap<>();

    private LinearLayout mFcanContainer;
    private LinearLayout mBcanContainer;
    private LinearLayout mCpuFcanContainer;
    private LinearLayout mCpuBcanContainer;
    private TextView     mStatusTv;
    private TextView     mCpuStatusTv;

    private final VehicleDataSource.Listener mListener = new VehicleDataSource.Listener() {
        @Override public void onVehicleSnapshotChanged(VehicleSnapshot s) { refresh(); }
    };

    // ── Construction ──────────────────────────────────────────────────────────

    @Override
    public View onCreateView(PluginContext context) {
        Context ctx = context.getAndroidContext();
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 8));

        TextView title = new TextView(ctx);
        title.setText("VEHICLE DATA — live CAN");
        title.setTextColor(0xFFEEEEEE);
        title.setTextSize(12);
        root.addView(title);

        mStatusTv = new TextView(ctx);
        mStatusTv.setTextColor(0xFF888888);
        mStatusTv.setTextSize(8);
        mStatusTv.setPadding(0, dp(ctx, 2), 0, dp(ctx, 4));
        root.addView(mStatusTv);

        addBusHeader(ctx, root, "── FCAN — powertrain / chassis ──");
        addLegend(ctx, root);
        mFcanContainer = new LinearLayout(ctx);
        mFcanContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(mFcanContainer);

        addBusHeader(ctx, root, "── BCAN — body / HMI ──");
        addLegend(ctx, root);
        mBcanContainer = new LinearLayout(ctx);
        mBcanContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(mBcanContainer);

        // ── Raw frames straight from CpuComService (Tier 1 / root) ────────────
        addBusHeader(ctx, root, "══ RAW FRAMES — CpuComService (root) ══");
        mCpuStatusTv = new TextView(ctx);
        mCpuStatusTv.setTextColor(0xFF888888);
        mCpuStatusTv.setTextSize(8);
        mCpuStatusTv.setPadding(0, 0, 0, dp(ctx, 2));
        root.addView(mCpuStatusTv);

        addBusHeader(ctx, root, "· FCAN raw (500 kbps) ·");
        mCpuFcanContainer = new LinearLayout(ctx);
        mCpuFcanContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(mCpuFcanContainer);

        addBusHeader(ctx, root, "· BCAN raw (125 kbps) ·");
        mCpuBcanContainer = new LinearLayout(ctx);
        mCpuBcanContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(mCpuBcanContainer);

        scroll.addView(root);
        scroll.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                VehicleDataSource ds = VehicleDataSource.getInstance();
                if (ds != null) ds.addListener(mListener);
                refresh();
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

        Map<String, double[]> fcanRaw = ds.getFcanRaw();
        Map<String, double[]> bcanRaw = ds.getBcanRaw();

        if (mStatusTv != null) {
            mStatusTv.setText(
                    "FCAN " + ds.diagFcanCallbacks + " cb / " + fcanRaw.size() + " sig"
                    + "   BCAN " + ds.diagBcanCallbacks + " cb / " + bcanRaw.size() + " sig"
                    + "   diag " + (ds.diagDiagConnected ? "ok" : "n/a"));
        }

        long now = System.currentTimeMillis();
        updateBus(mFcanContainer, mFcanSections, fcanRaw, ds.getFcanRawChangedAt(),
                ds.getFcanFrames(), now);
        updateBus(mBcanContainer, mBcanSections, bcanRaw, ds.getBcanRawChangedAt(),
                ds.getBcanFrames(), now);

        if (mCpuStatusTv != null) {
            mCpuStatusTv.setText(
                    (ds.cpuComConnected ? "bound" : "not bound — needs VEHICLE_RW (root)")
                    + "   FCAN " + ds.cpuFcanFrameCallbacks + " fr / " + ds.getCpuFcanFrames().size() + " id"
                    + "   BCAN " + ds.cpuBcanFrameCallbacks + " fr / " + ds.getCpuBcanFrames().size() + " id");
        }
        updateRaw(mCpuFcanContainer, mCpuFcanSections, ds.getCpuFcanFrames(),
                CanDefinitions.FCAN_MESSAGES, now);
        updateRaw(mCpuBcanContainer, mCpuBcanSections, ds.getCpuBcanFrames(),
                CanDefinitions.BCAN_MESSAGES, now);
    }

    /** Recency color for a value that last changed {@code ageMs} ago. */
    private static int colorForAge(long ageMs) {
        if      (ageMs <  2_000) return 0xFF7CFF7C;   // fresh — green
        else if (ageMs <  6_000) return 0xFFCCB050;   // recent — amber
        else if (ageMs < 15_000) return 0xFF808060;   // fading
        else                     return 0xFF585858;   // stale
    }

    private void updateBus(LinearLayout container, Map<Integer, FrameSection> sections,
                           Map<String, double[]> raw, Map<String, Long> changedAt,
                           Map<Integer, byte[]> frames, long now) {
        if (container == null) return;
        Context ctx = container.getContext();

        // Group the flat "typeCode/SIGNAL_NAME" map into { typeCode → [signal names] }, sorted.
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

            FrameSection sec = sections.get(type);
            if (sec == null) sec = addFrameSection(ctx, container, sections, type);

            // Raw byte[8], if the middleware ever forwards one (usually absent at this layer).
            byte[] frame = frames != null ? frames.get(type) : null;
            sec.bytesTv.setText(frame != null ? "bytes " + hex(frame) : "");
            sec.bytesTv.setVisibility(frame != null ? View.VISIBLE : View.GONE);

            for (String key : keys) {
                String compound = type + "/" + key;
                SignalRow row = sec.rows.get(key);
                if (row == null) {
                    row = addRow(ctx, sec.container, type, key);
                    sec.rows.put(key, row);
                }
                double[] stats = raw.get(compound);
                if (stats == null) continue;

                int rawInt = (int) stats[2];
                Long changedMs = changedAt.get(compound);
                long ageMs = (changedMs == null) ? Long.MAX_VALUE : (now - changedMs);

                row.rawTv.setTextColor(colorForAge(ageMs));
                row.rawTv.setText(String.valueOf(rawInt));

                SignalCatalog.SignalInfo info = SignalCatalog.signal(type, key);
                String value = (info != null && info.hasFormattedValue())
                        ? info.formatValue(rawInt) : "";
                row.valueTv.setTextColor(value.isEmpty() ? 0xFF585858
                        : (ageMs < 15_000 ? 0xFFAADDFF : 0xFF5A6A78));
                row.valueTv.setText(value);
            }
        }
    }

    // ── View builders ─────────────────────────────────────────────────────────

    private FrameSection addFrameSection(Context ctx, LinearLayout container,
                                         Map<Integer, FrameSection> sections, int type) {
        TextView hdr = new TextView(ctx);
        hdr.setText(SignalCatalog.frameLabel(type));
        hdr.setTextColor(0xFFFFAA00);
        hdr.setTextSize(10);
        hdr.setPadding(0, dp(ctx, 8), 0, dp(ctx, 1));
        container.addView(hdr);

        TextView bytesTv = new TextView(ctx);
        bytesTv.setTextColor(0xFF6699AA);
        bytesTv.setTextSize(8);
        bytesTv.setVisibility(View.GONE);
        container.addView(bytesTv);

        FrameSection sec = new FrameSection();
        sec.bytesTv  = bytesTv;
        sec.container = new LinearLayout(ctx);
        sec.container.setOrientation(LinearLayout.VERTICAL);
        container.addView(sec.container);
        sections.put(type, sec);
        return sec;
    }

    private SignalRow addRow(Context ctx, LinearLayout parent, int type, String key) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);

        SignalCatalog.SignalInfo info = SignalCatalog.signal(type, key);

        TextView nameTv = new TextView(ctx);
        nameTv.setText(info != null ? info.display : key);
        nameTv.setTextColor(info != null ? 0xFFBBBBBB : 0xFF7A7A7A); // dim unknown keys
        nameTv.setTextSize(9);
        nameTv.setSingleLine(true);
        nameTv.setEllipsize(TextUtils.TruncateAt.END);
        nameTv.setPadding(0, dp(ctx, 1), dp(ctx, 4), dp(ctx, 1));
        row.addView(nameTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 5f));

        TextView rawTv = new TextView(ctx);
        rawTv.setText("—");
        rawTv.setTextColor(0xFF585858);
        rawTv.setTextSize(9);
        rawTv.setGravity(Gravity.END);
        rawTv.setPadding(0, dp(ctx, 1), dp(ctx, 6), dp(ctx, 1));
        row.addView(rawTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));

        TextView valueTv = new TextView(ctx);
        valueTv.setText("");
        valueTv.setTextColor(0xFF585858);
        valueTv.setTextSize(9);
        valueTv.setGravity(Gravity.END);
        valueTv.setSingleLine(true);
        valueTv.setEllipsize(TextUtils.TruncateAt.END);
        valueTv.setPadding(0, dp(ctx, 1), 0, dp(ctx, 1));
        row.addView(valueTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 4f));

        parent.addView(row);
        SignalRow sr = new SignalRow();
        sr.rawTv   = rawTv;
        sr.valueTv = valueTv;
        return sr;
    }

    // ── Raw-frame (CpuComService) rendering ───────────────────────────────────

    private void updateRaw(LinearLayout container, Map<Integer, RawSection> sections,
                           Map<Integer, VehicleDataSource.RawFrame> frames,
                           List<CanMessage> dbc, long now) {
        if (container == null) return;
        Context ctx = container.getContext();

        List<Integer> ids = new ArrayList<>(frames.keySet());
        Collections.sort(ids);
        for (int id : ids) {
            VehicleDataSource.RawFrame rf = frames.get(id);
            if (rf == null || rf.data == null) continue;

            RawSection sec = sections.get(id);
            if (sec == null) sec = addRawSection(ctx, container, sections, id, dbc);

            long ageMs   = now - rf.changedAt;
            boolean stale = rf.receiveState == 2;   // §3.3: 2 = not currently received
            sec.metaTv.setTextColor(stale ? 0xFF9A6A5A : colorForAge(ageMs));
            sec.metaTv.setText((stale ? "STALE " : "") + "rs=" + rf.receiveState
                    + " ×" + rf.count + "   " + hex(rf.data));

            if (!sec.sigValues.isEmpty()) {
                Map<String, Double> decoded = CanDecoder.decode(id, rf.data, dbc);
                for (Map.Entry<String, TextView> e : sec.sigValues.entrySet()) {
                    Double v = decoded.get(e.getKey());
                    TextView tv = e.getValue();
                    tv.setTextColor(stale ? 0xFF585858 : (ageMs < 15_000 ? 0xFFAADDFF : 0xFF5A6A78));
                    tv.setText(v == null ? "—" : fmt(v));
                }
            }
        }
    }

    private RawSection addRawSection(Context ctx, LinearLayout container,
                                     Map<Integer, RawSection> sections, int id, List<CanMessage> dbc) {
        TextView hdr = new TextView(ctx);
        hdr.setText(SignalCatalog.frameLabel(id));
        hdr.setTextColor(0xFFFFAA00);
        hdr.setTextSize(10);
        hdr.setPadding(0, dp(ctx, 8), 0, dp(ctx, 1));
        container.addView(hdr);

        TextView metaTv = new TextView(ctx);
        metaTv.setTextColor(0xFF6699AA);
        metaTv.setTextSize(8);
        container.addView(metaTv);

        RawSection sec = new RawSection();
        sec.metaTv = metaTv;

        // If the DBC defines this id, decode every signal (name · value · unit).
        CanMessage msg = CanDecoder.messageById(id, dbc);
        if (msg != null) {
            for (CanSignal sig : msg.signals) {
                if (sec.sigValues.containsKey(sig.name)) continue; // ignore duplicate names
                sec.sigValues.put(sig.name, addDecodedRow(ctx, container, sig.name, sig.unit));
            }
        }
        sections.put(id, sec);
        return sec;
    }

    /** A "name … value unit" row for a DBC-decoded raw-frame signal. Returns the value view. */
    private static TextView addDecodedRow(Context ctx, LinearLayout parent, String name, String unit) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView nameTv = new TextView(ctx);
        nameTv.setText(name);
        nameTv.setTextColor(0xFFBBBBBB);
        nameTv.setTextSize(9);
        nameTv.setSingleLine(true);
        nameTv.setEllipsize(TextUtils.TruncateAt.END);
        nameTv.setPadding(dp(ctx, 8), dp(ctx, 1), dp(ctx, 4), dp(ctx, 1));
        row.addView(nameTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 5f));

        TextView valueTv = new TextView(ctx);
        valueTv.setText("—");
        valueTv.setTextColor(0xFF585858);
        valueTv.setTextSize(9);
        valueTv.setGravity(Gravity.END);
        valueTv.setPadding(0, dp(ctx, 1), dp(ctx, 4), dp(ctx, 1));
        row.addView(valueTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f));

        TextView unitTv = new TextView(ctx);
        unitTv.setText(unit == null ? "" : unit);
        unitTv.setTextColor(0xFF666666);
        unitTv.setTextSize(8);
        unitTv.setPadding(0, dp(ctx, 1), 0, dp(ctx, 1));
        row.addView(unitTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));

        parent.addView(row);
        return valueTv;
    }

    private static String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return String.valueOf(v);
        if (v == Math.floor(v) && Math.abs(v) < 1e9) return String.valueOf((long) v);
        return String.format(Locale.US, "%.2f", v);
    }

    private static void addBusHeader(Context ctx, LinearLayout list, String text) {
        TextView hdr = new TextView(ctx);
        hdr.setText(text);
        hdr.setTextColor(0xFFFFCC00);
        hdr.setTextSize(11);
        hdr.setPadding(0, dp(ctx, 10), 0, dp(ctx, 1));
        list.addView(hdr);
    }

    private static void addLegend(Context ctx, LinearLayout list) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView n = new TextView(ctx);
        n.setText("signal");
        n.setTextColor(0xFF666666);
        n.setTextSize(8);
        row.addView(n, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 5f));

        TextView r = new TextView(ctx);
        r.setText("raw");
        r.setTextColor(0xFF666666);
        r.setTextSize(8);
        r.setGravity(Gravity.END);
        r.setPadding(0, 0, dp(ctx, 6), 0);
        row.addView(r, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));

        TextView v = new TextView(ctx);
        v.setText("value");
        v.setTextColor(0xFF666666);
        v.setTextSize(8);
        v.setGravity(Gravity.END);
        row.addView(v, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 4f));

        list.addView(row);
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format(Locale.US, "%02X", b[i] & 0xFF));
        }
        return sb.toString();
    }

    private static int dp(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    @Override public void onStart() {}
    @Override public void onVehicleSnapshotChanged(VehicleSnapshot snapshot) {}
    @Override public void onStop() {}
}
