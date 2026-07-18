package com.example.clusterapp;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.clusterapp.can.CanDecoder;
import com.example.clusterapp.can.CanDefinitions;
import com.example.clusterapp.can.CanMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Live CAN debug view for the main touchscreen "Debug" tab.
 *
 * <p>The bus carries far more messages than an underpowered head unit can lay out at once, so the
 * view is split into logical <b>groups</b> (Powertrain, Brakes, Steering, Lights, ADAS, HVAC, …).
 * A group selector sits at the top; only the <b>selected</b> group's messages are built and
 * refreshed each tick, keeping per-frame work to a handful of rows.
 *
 * <p>For each CAN id it merges the two sources {@link VehicleDataSource} exposes — the raw
 * CpuComService {@code byte[8]} stream (Tier 1, needs VEHICLE_RW; shown as a hex dump) and the
 * middleware pre-decoded named signals ({@code getFcanRaw()/getBcanRaw()}, keyed
 * "&lt;canId&gt;/SIGNAL"). FCAN ids are additionally DBC-decoded via {@link CanDecoder}. The HVAC
 * group instead renders {@link ClimateDataSource}, which decodes climate off a separate service
 * (can-analysis.md §7) and would otherwise be invisible here, plus the raw A/C BCAN frames. The
 * "Other" group is a catch-all for any id not claimed by a named group, so nothing is hidden.
 *
 * <p>Rows are colored by recency (bright = changed within 2 s, fading to gray when stale), so a
 * physical action in the car makes the responding signals light up.
 */
public final class CanDebugView {

    private static final int REFRESH_MS = 500;

    // ── Logical group model ───────────────────────────────────────────────────

    private static final class Group {
        final String label;
        final Set<Integer> fcanIds;
        final Set<Integer> bcanIds;
        final boolean climate;   // render ClimateDataSource (HVAC) before the raw frames
        final boolean catchAll;  // show every id not claimed by a named group

        Group(String label, int[] fcan, int[] bcan, boolean climate, boolean catchAll) {
            this.label    = label;
            this.fcanIds  = toSet(fcan);
            this.bcanIds  = toSet(bcan);
            this.climate  = climate;
            this.catchAll = catchAll;
        }
        private static Set<Integer> toSet(int[] a) {
            Set<Integer> s = new LinkedHashSet<>();
            if (a != null) for (int v : a) s.add(v);
            return s;
        }
    }

    // BCAN 32-bit body-CAN ids (can-analysis.md §2.2 / §7.3).
    private static final int B_HLSW_BCM = 184053784, B_HLSW_ICU = 184053776;
    private static final int B_MICU_BCM = 318246936, B_MICU_ICU = 318246928;
    private static final int B_VSPNE = 318263376, B_AT = 318263632, B_ILLUMI = 318264400;
    private static final int B_PARKSENS = 318285722, B_PARKSENS2 = 318309018;
    private static final int B_MAINTENANCE = 318334800, B_MET_CUSTOM = 318336336;
    private static final int B_STEERING = 318337360, B_VINNO = 385376848;
    private static final int B_FOB = 318256152, B_TRICOM = 318333520;
    private static final int B_AC = 318264145, B_ACSTATE = 251231057, B_ACINFO = 251220561;
    private static final int B_ACFB = 251234129, B_ACSW = 318329173, B_ACSET = 318339669;
    private static final int B_ACCOM = 318328917, B_ACDIAG = 385438545;

    private static final int[] HVAC_BCAN = {
            B_AC, B_ACSTATE, B_ACINFO, B_ACFB, B_ACSW, B_ACSET, B_ACCOM, B_ACDIAG };

    private static final List<Group> GROUPS = Arrays.asList(
            new Group("Powertrain", new int[]{344, 380, 401, 777, 1036},
                    new int[]{B_VSPNE, B_AT}, false, false),
            new Group("Brakes", new int[]{420}, new int[]{}, false, false),
            new Group("Steering", new int[]{427}, new int[]{B_STEERING}, false, false),
            new Group("Lights", new int[]{806, 884},
                    new int[]{B_HLSW_BCM, B_HLSW_ICU, B_MICU_BCM, B_MICU_ICU, B_ILLUMI}, false, false),
            new Group("ADAS", new int[]{829, 460}, new int[]{B_MET_CUSTOM}, false, false),
            new Group("Maint/Fuel", new int[]{}, new int[]{B_MAINTENANCE, B_TRICOM}, false, false),
            new Group("Identity", new int[]{},
                    new int[]{B_VINNO, B_FOB, B_PARKSENS, B_PARKSENS2}, false, false),
            new Group("HVAC", new int[]{}, HVAC_BCAN, true, false),
            new Group("Other", new int[]{}, new int[]{}, false, true)
    );

    // Ids claimed by any named (non-catch-all) group — excluded from "Other".
    private static final Set<Integer> CLAIMED_FCAN = new HashSet<>();
    private static final Set<Integer> CLAIMED_BCAN = new HashSet<>();
    static {
        for (Group g : GROUPS) {
            if (g.catchAll) continue;
            CLAIMED_FCAN.addAll(g.fcanIds);
            CLAIMED_BCAN.addAll(g.bcanIds);
        }
    }

    // 32-bit Mitsubishi body-CAN id → symbolic name.
    private static final Map<Integer, String> BCAN_NAMES = new LinkedHashMap<>();
    static {
        BCAN_NAMES.put(B_HLSW_BCM, "HLSW_BCM");   BCAN_NAMES.put(B_HLSW_ICU, "HLSW_ICU");
        BCAN_NAMES.put(B_MICU_BCM, "MICU_BCM");   BCAN_NAMES.put(B_MICU_ICU, "MICU_ICU");
        BCAN_NAMES.put(B_VSPNE, "VSPNE");         BCAN_NAMES.put(B_AT, "AT");
        BCAN_NAMES.put(B_ILLUMI, "ILLUMI");       BCAN_NAMES.put(B_PARKSENS, "PARKSENS");
        BCAN_NAMES.put(B_PARKSENS2, "PARKSENS_TWO");
        BCAN_NAMES.put(B_MAINTENANCE, "MAINTENANCE"); BCAN_NAMES.put(B_MET_CUSTOM, "MET_CUSTOM");
        BCAN_NAMES.put(B_STEERING, "STEERING");   BCAN_NAMES.put(B_VINNO, "VINNO");
        BCAN_NAMES.put(B_FOB, "FOB_ID_BCM");      BCAN_NAMES.put(B_TRICOM, "TRICOM (range)");
        BCAN_NAMES.put(B_AC, "AC");               BCAN_NAMES.put(B_ACSTATE, "ACSTATE");
        BCAN_NAMES.put(B_ACINFO, "ACINFO");       BCAN_NAMES.put(B_ACFB, "ACFB");
        BCAN_NAMES.put(B_ACSW, "ACSW");           BCAN_NAMES.put(B_ACSET, "ACSET");
        BCAN_NAMES.put(B_ACCOM, "ACCOM");         BCAN_NAMES.put(B_ACDIAG, "ACDIAG");
    }

    // Preferred display names for the 11 stock FCAN ids (can-analysis.md §2.1).
    private static final Map<Integer, String> FCAN_NAMES = new LinkedHashMap<>();
    static {
        FCAN_NAMES.put(344, "ENGINE_DATA");    FCAN_NAMES.put(380, "POWERTRAIN_DATA");
        FCAN_NAMES.put(401, "CVT_INFO");       FCAN_NAMES.put(420, "VSA_STATUS");
        FCAN_NAMES.put(427, "EPS_MID_REQ");    FCAN_NAMES.put(460, "ADS_WARN_LAMP");
        FCAN_NAMES.put(777, "METER_DISPLAY_SPEED"); FCAN_NAMES.put(806, "METER_FUEL_326");
        FCAN_NAMES.put(829, "LKAS_STATUS_33D"); FCAN_NAMES.put(884, "METER_MID_374");
        FCAN_NAMES.put(1036, "ENG_FREEZE_40C");
    }

    // ── Instance state ────────────────────────────────────────────────────────

    private int mSelected = 0;
    private LinearLayout mContent;
    private final Button[] mGroupButtons = new Button[GROUPS.size()];

    /** Build the self-refreshing, group-selectable debug view. */
    public static View create(Context ctx) {
        return new CanDebugView().build(ctx);
    }

    private View build(Context ctx) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);

        root.addView(buildSelector(ctx), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(ctx);
        mContent = new LinearLayout(ctx);
        mContent.setOrientation(LinearLayout.VERTICAL);
        mContent.setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 16));
        scroll.addView(mContent);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        final android.os.Handler handler = new android.os.Handler();
        final Runnable[] tick = new Runnable[1];
        tick[0] = new Runnable() {
            @Override public void run() {
                rebuildActive();
                handler.postDelayed(this, REFRESH_MS);
            }
        };
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { handler.post(tick[0]); }
            @Override public void onViewDetachedFromWindow(View v) { handler.removeCallbacks(tick[0]); }
        });
        return root;
    }

    // ── Group selector (3-column grid of buttons) ─────────────────────────────

    private View buildSelector(Context ctx) {
        LinearLayout grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setBackgroundColor(0xFF161616);
        grid.setPadding(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6));

        final int cols = 3;
        for (int i = 0; i < GROUPS.size(); i += cols) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < Math.min(i + cols, GROUPS.size()); j++) {
                final int idx = j;
                Button b = new Button(ctx);
                b.setText(GROUPS.get(j).label);
                b.setTextSize(11);
                b.setAllCaps(false);
                b.setPadding(dp(ctx, 2), 0, dp(ctx, 2), 0);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        mSelected = idx;
                        applyButtonStyles();
                        rebuildActive();
                    }
                });
                mGroupButtons[j] = b;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(ctx, 40), 1f);
                lp.setMargins(dp(ctx, 3), dp(ctx, 3), dp(ctx, 3), dp(ctx, 3));
                row.addView(b, lp);
            }
            // Pad the final short row so buttons keep a consistent width.
            for (int k = Math.min(i + cols, GROUPS.size()); k < i + cols; k++) {
                View spacer = new View(ctx);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(ctx, 40), 1f);
                lp.setMargins(dp(ctx, 3), dp(ctx, 3), dp(ctx, 3), dp(ctx, 3));
                row.addView(spacer, lp);
            }
            grid.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        applyButtonStyles();
        return grid;
    }

    private void applyButtonStyles() {
        for (int i = 0; i < mGroupButtons.length; i++) {
            if (mGroupButtons[i] == null) continue;
            boolean active = (i == mSelected);
            mGroupButtons[i].setBackgroundColor(active ? 0xFFFFFFFF : 0xFF2A2A2A);
            mGroupButtons[i].setTextColor(active ? 0xFF000000 : 0xFFCCCCCC);
        }
    }

    // ── Active-group render (only this runs each tick) ─────────────────────────

    private void rebuildActive() {
        Context ctx = mContent.getContext();
        mContent.removeAllViews();

        VehicleDataSource ds = VehicleDataSource.getInstance();
        if (ds == null) {
            addNote(mContent, "Vehicle data source is starting…");
            return;
        }
        Group g = GROUPS.get(mSelected);

        TextView status = new TextView(ctx);
        status.setTextColor(0xFF888888);
        status.setTextSize(10);
        status.setText(g.label + "   •   CpuCom(raw): "
                + (ds.cpuComConnected ? "connected" : "not connected (needs VEHICLE_RW)"));
        status.setPadding(0, 0, 0, dp(ctx, 4));
        mContent.addView(status);

        long now = System.currentTimeMillis();

        if (g.climate) {
            renderClimate(mContent, now);
        }

        if (g.catchAll) {
            renderCatchAll(ctx, ds, now);
        } else {
            if (!g.fcanIds.isEmpty()) {
                addBusHeader(ctx, "FCAN");
                for (int id : g.fcanIds) renderId(ctx, ds, true, id, now);
            }
            if (!g.bcanIds.isEmpty()) {
                addBusHeader(ctx, g.climate ? "Raw A/C BCAN frames" : "BCAN");
                for (int id : g.bcanIds) renderId(ctx, ds, false, id, now);
            }
        }
    }

    private void renderCatchAll(Context ctx, VehicleDataSource ds, long now) {
        renderCatchAllBus(ctx, ds, true, now);
        renderCatchAllBus(ctx, ds, false, now);
    }

    private void renderCatchAllBus(Context ctx, VehicleDataSource ds, boolean fcan, long now) {
        Set<Integer> claimed = fcan ? CLAIMED_FCAN : CLAIMED_BCAN;
        TreeSet<Integer> ids = new TreeSet<>();
        for (int id : (fcan ? ds.getCpuFcanFrames() : ds.getCpuBcanFrames()).keySet()) {
            if (!claimed.contains(id)) ids.add(id);
        }
        for (String key : (fcan ? ds.getFcanRaw() : ds.getBcanRaw()).keySet()) {
            int slash = key.indexOf('/');
            if (slash < 0) continue;
            try {
                int id = Integer.parseInt(key.substring(0, slash));
                if (!claimed.contains(id)) ids.add(id);
            } catch (NumberFormatException ignored) {}
        }
        addBusHeader(ctx, fcan ? "FCAN (unclaimed)" : "BCAN (unclaimed)");
        if (ids.isEmpty()) { addNote(mContent, "  none"); return; }
        for (int id : ids) renderId(ctx, ds, fcan, id, now);
    }

    /** Render one CAN id: header + raw hex + decoded signals. */
    private void renderId(Context ctx, VehicleDataSource ds, boolean fcan, int id, long now) {
        Map<Integer, VehicleDataSource.RawFrame> frames =
                fcan ? ds.getCpuFcanFrames() : ds.getCpuBcanFrames();
        Map<String, double[]> mw   = fcan ? ds.getFcanRaw()          : ds.getBcanRaw();
        Map<String, Long>     mwAt = fcan ? ds.getFcanRawChangedAt() : ds.getBcanRawChangedAt();

        VehicleDataSource.RawFrame rf = frames.get(id);
        String name = fcan ? fcanName(id) : bcanName(id);

        // Decoded signals: name → {value, min, max, ageMs}.
        LinkedHashMap<String, double[]> sigs = new LinkedHashMap<>();
        String prefix = id + "/";
        for (Map.Entry<String, double[]> e : mw.entrySet()) {
            if (!e.getKey().startsWith(prefix)) continue;
            String sigName = e.getKey().substring(prefix.length());
            double[] s = e.getValue();
            Long ch = mwAt.get(e.getKey());
            long age = (ch == null) ? Long.MAX_VALUE : now - ch;
            sigs.put(sigName, new double[]{s[2], s[0], s[1], age});
        }
        if (fcan && rf != null && rf.data != null) {
            long rawAge = now - rf.changedAt;
            Map<String, Double> decoded = CanDecoder.decode(id, rf.data, CanDefinitions.FCAN_MESSAGES);
            for (Map.Entry<String, Double> e : decoded.entrySet()) {
                if (sigs.containsKey(e.getKey())) continue;
                double v = e.getValue();
                sigs.put(e.getKey(), new double[]{v, v, v, rawAge});
            }
        }

        long idAge = (rf != null) ? now - rf.changedAt : Long.MAX_VALUE;
        for (double[] s : sigs.values()) idAge = Math.min(idAge, (long) s[3]);

        mContent.addView(idHeader(ctx, id, name, rf, idAge));
        if (rf != null && rf.data != null) mContent.addView(rawRow(ctx, rf, idAge));

        List<String> sigNames = new ArrayList<>(sigs.keySet());
        Collections.sort(sigNames);
        for (String sn : sigNames) mContent.addView(signalRow(ctx, sn, sigs.get(sn)));

        if (rf == null && sigs.isEmpty()) addNote(mContent, "  (subscribed, no data)");
    }

    // ── HVAC / Climate (separate service; see ClimateDataSource) ───────────────

    private void renderClimate(LinearLayout content, long now) {
        Context ctx = content.getContext();
        addBusHeader(ctx, "Climate (VehicleCoordinationService)");

        ClimateDataSource cs = ClimateDataSource.getInstance();
        if (cs == null) { addNote(content, "  climate source not started"); return; }

        TextView st = new TextView(ctx);
        st.setTextColor(0xFF888888);
        st.setTextSize(9);
        st.setText("  " + (cs.connected ? "connected" : "not connected (needs VEHICLE_RW)")
                + "   cb: display=" + cs.displayCallbacks
                + " status=" + cs.statusCallbacks + " sensor=" + cs.sensorCallbacks);
        content.addView(st);

        long age = cs.lastUpdateMs > 0 ? now - cs.lastUpdateMs : Long.MAX_VALUE;
        content.addView(kvRow(ctx, "A/C status",
                onOff(cs.acStatus >= 0 ? cs.acStatus : cs.statusAcState), age));
        content.addView(kvRow(ctx, "A/C mode", intOrDash(cs.acMode), age));
        content.addView(kvRow(ctx, "Driver setpoint", setpoint(cs.drSetpointIdx), age));
        content.addView(kvRow(ctx, "Passenger setpoint", setpoint(cs.asSetpointIdx), age));
        content.addView(kvRow(ctx, "Fan volume", intOrDash(cs.fanVolume), age));
        content.addView(kvRow(ctx, "Connection status", intOrDash(cs.connectionStatus), age));
        content.addView(kvRow(ctx, "Handle position", handle(cs.handlePosition), age));

        List<Integer> ids = new ArrayList<>(cs.getSensors().keySet());
        Collections.sort(ids);
        if (ids.isEmpty()) {
            addNote(content, "  (no analog sensors reported)");
        } else {
            for (int id : ids) {
                Integer v = cs.getSensors().get(id);
                content.addView(kvRow(ctx, "sensor " + id, v == null ? "—" : String.valueOf(v), age));
            }
        }
    }

    // ── Row builders ──────────────────────────────────────────────────────────

    private void addBusHeader(Context ctx, String text) {
        TextView hdr = new TextView(ctx);
        hdr.setText("── " + text + " ──");
        hdr.setTextColor(0xFFFFCC00);
        hdr.setTextSize(12);
        hdr.setTypeface(null, Typeface.BOLD);
        hdr.setPadding(0, dp(ctx, 12), 0, dp(ctx, 4));
        mContent.addView(hdr);
    }

    private static View idHeader(Context ctx, int id, String name,
                                 VehicleDataSource.RawFrame rf, long ageMs) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(ctx, 7), 0, dp(ctx, 1));

        TextView idTv = new TextView(ctx);
        idTv.setText(id + "  (0x" + Integer.toHexString(id).toUpperCase(Locale.US) + ")"
                + (name.isEmpty() ? "" : "  " + name));
        idTv.setTextColor(ageMs < 15_000 ? 0xFFFFAA00 : 0xFF7A6A30);
        idTv.setTextSize(11);
        idTv.setTypeface(null, Typeface.BOLD);
        idTv.setSingleLine(true);
        idTv.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(idTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView metaTv = new TextView(ctx);
        metaTv.setText(rf != null ? ("×" + rf.count + (rf.receiveState == 2 ? "  stale" : "")) : "");
        metaTv.setTextColor(0xFF666666);
        metaTv.setTextSize(9);
        metaTv.setGravity(Gravity.END);
        row.addView(metaTv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private static View rawRow(Context ctx, VehicleDataSource.RawFrame rf, long ageMs) {
        TextView tv = new TextView(ctx);
        tv.setText("  " + hex(rf.data));
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextColor(ageColor(ageMs));
        tv.setTextSize(11);
        tv.setPadding(0, 0, 0, dp(ctx, 1));
        return tv;
    }

    private static View signalRow(Context ctx, String name, double[] s) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView nameTv = new TextView(ctx);
        nameTv.setText("    " + name);
        nameTv.setTextColor(0xFF777777);
        nameTv.setTextSize(9);
        nameTv.setSingleLine(true);
        nameTv.setEllipsize(TextUtils.TruncateAt.END);
        nameTv.setPadding(0, dp(ctx, 1), dp(ctx, 4), dp(ctx, 1));
        row.addView(nameTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 5f));

        TextView valueTv = new TextView(ctx);
        valueTv.setText(fmt(s[0]));
        valueTv.setTextColor(ageColor((long) s[3]));
        valueTv.setTextSize(9);
        valueTv.setGravity(Gravity.END);
        valueTv.setPadding(0, dp(ctx, 1), dp(ctx, 6), dp(ctx, 1));
        row.addView(valueTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));

        TextView rangeTv = new TextView(ctx);
        rangeTv.setText(s[1] != s[2] ? fmt(s[1]) + ".." + fmt(s[2]) : "");
        rangeTv.setTextColor((long) s[3] < 15_000 ? 0xFF443300 : 0xFF2A2A2A);
        rangeTv.setTextSize(9);
        rangeTv.setGravity(Gravity.END);
        rangeTv.setPadding(0, dp(ctx, 1), 0, dp(ctx, 1));
        row.addView(rangeTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f));
        return row;
    }

    /** Simple label = value row for HVAC/climate, colored by a shared freshness age. */
    private static View kvRow(Context ctx, String label, String value, long ageMs) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView labelTv = new TextView(ctx);
        labelTv.setText("  " + label);
        labelTv.setTextColor(0xFF999999);
        labelTv.setTextSize(10);
        labelTv.setPadding(0, dp(ctx, 1), dp(ctx, 6), dp(ctx, 1));
        row.addView(labelTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f));

        TextView valueTv = new TextView(ctx);
        valueTv.setText(value);
        valueTv.setTextColor(ageColor(ageMs));
        valueTv.setTextSize(10);
        valueTv.setGravity(Gravity.END);
        valueTv.setPadding(0, dp(ctx, 1), 0, dp(ctx, 1));
        row.addView(valueTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));
        return row;
    }

    private void addNote(LinearLayout content, String text) {
        Context ctx = content.getContext();
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(0xFF666666);
        tv.setTextSize(10);
        tv.setPadding(0, dp(ctx, 2), 0, dp(ctx, 2));
        content.addView(tv);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static String fcanName(int id) {
        String n = FCAN_NAMES.get(id);
        if (n != null) return n;
        CanMessage m = CanDecoder.messageById(id, CanDefinitions.FCAN_MESSAGES);
        return m != null ? m.name : "";
    }

    private static String bcanName(int id) {
        String n = BCAN_NAMES.get(id);
        return n != null ? n : "";
    }

    private static String onOff(int v) { return v < 0 ? "—" : (v == 0 ? "off" : "on"); }

    private static String intOrDash(int v) { return v < 0 ? "—" : String.valueOf(v); }

    private static String setpoint(int idx) {
        if (idx < 0) return "—";
        double c = ClimateDataSource.setpointCelsius(idx);
        return Double.isNaN(c) ? ("idx " + idx) : (fmt(c) + " °C");
    }

    private static String handle(int v) {
        if (v < 0) return "—";
        return v == 0 ? "LHD" : v == 1 ? "RHD" : String.valueOf(v);
    }

    private static int ageColor(long ageMs) {
        if      (ageMs <  2_000) return 0xFFFFFF00;
        else if (ageMs <  6_000) return 0xFFCCA000;
        else if (ageMs < 15_000) return 0xFF888800;
        else                     return 0xFF585858;
    }

    private static String hex(byte[] b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format(Locale.US, "%02X", b[i] & 0xFF));
        }
        return sb.toString();
    }

    private static String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return String.valueOf(v);
        if (v == Math.floor(v) && Math.abs(v) < 1e9)  return String.valueOf((long) v);
        return String.format(Locale.US, "%.2f", v);
    }

    private static int dp(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }
}
