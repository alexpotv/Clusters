package com.example.clusterapp.screens;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.clusterapi.ClusterPlugin;
import com.example.clusterapi.PluginContext;
import com.example.clusterapi.VehicleSnapshot;
import com.example.clusterapp.ClimateDataSource;
import com.example.clusterapp.VehicleDataSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HVAC / climate screen fed by {@link ClimateDataSource} (VehicleCoordinationService).
 *
 * <p>Shows the A/C panel state (on/off, mode, driver & passenger setpoints, fan), the analog
 * cabin/ambient sensors, and the connection/handle getters. Setpoints are shown as the raw encoded
 * index plus a best-effort °C conversion. Refreshes on the shared VehicleDataSource tick.
 */
public class ClimateScreen implements ClusterPlugin {

    private TextView mStatusTv;
    private final Map<String, TextView> mRows = new LinkedHashMap<>();
    private LinearLayout mSensorContainer;
    private final Map<Integer, TextView> mSensorRows = new LinkedHashMap<>();

    private final VehicleDataSource.Listener mListener = new VehicleDataSource.Listener() {
        @Override public void onVehicleSnapshotChanged(VehicleSnapshot s) { refresh(); }
    };

    @Override
    public View onCreateView(PluginContext context) {
        Context ctx = context.getAndroidContext();
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(ctx, 10), dp(ctx, 6), dp(ctx, 10), dp(ctx, 8));

        TextView title = new TextView(ctx);
        title.setText("CLIMATE — HVAC");
        title.setTextColor(0xFFEEEEEE);
        title.setTextSize(12);
        root.addView(title);

        mStatusTv = new TextView(ctx);
        mStatusTv.setTextColor(0xFF888888);
        mStatusTv.setTextSize(8);
        mStatusTv.setPadding(0, dp(ctx, 2), 0, dp(ctx, 4));
        root.addView(mStatusTv);

        addHeader(ctx, root, "── A/C panel ──");
        addRow(ctx, root, "A/C status");
        addRow(ctx, root, "Mode");
        addRow(ctx, root, "Driver setpoint");
        addRow(ctx, root, "Passenger setpoint");
        addRow(ctx, root, "Fan volume");
        addRow(ctx, root, "Panel connection");

        addHeader(ctx, root, "── Getters ──");
        addRow(ctx, root, "Connection status");
        addRow(ctx, root, "Handle position");

        addHeader(ctx, root, "── Analog sensors (id → value) ──");
        mSensorContainer = new LinearLayout(ctx);
        mSensorContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(mSensorContainer);

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

    private void refresh() {
        ClimateDataSource cs = ClimateDataSource.getInstance();
        if (cs == null) {
            if (mStatusTv != null) mStatusTv.setText("climate source not started");
            return;
        }

        mStatusTv.setText((cs.connected ? "bound" : "not bound — needs VEHICLE_RW (root)")
                + "   display " + cs.displayCallbacks
                + "  status " + cs.statusCallbacks
                + "  sensor " + cs.sensorCallbacks);

        set("A/C status",         onOff(cs.acStatus >= 0 ? cs.acStatus : cs.statusAcState));
        set("Mode",               intOrDash(cs.acMode));
        set("Driver setpoint",    setpoint(cs.drSetpointIdx));
        set("Passenger setpoint", setpoint(cs.asSetpointIdx));
        set("Fan volume",         intOrDash(cs.fanVolume));
        set("Panel connection",   intOrDash(cs.connectionStatus));
        set("Connection status",  intOrDash(cs.getterConnStatus));
        set("Handle position",    handle(cs.handlePosition));

        // Analog sensors — add rows lazily as ids appear.
        List<Integer> ids = new ArrayList<>(cs.getSensors().keySet());
        Collections.sort(ids);
        Context ctx = mSensorContainer.getContext();
        for (int id : ids) {
            TextView tv = mSensorRows.get(id);
            if (tv == null) {
                tv = addRowTo(ctx, mSensorContainer, "sensor " + id);
                mSensorRows.put(id, tv);
            }
            Integer v = cs.getSensors().get(id);
            tv.setTextColor(0xFFAADDFF);
            tv.setText(v == null ? "—" : String.valueOf(v));
        }
        if (ids.isEmpty() && mSensorRows.isEmpty()) {
            if (!mSensorRows.containsKey(-1)) {
                TextView tv = addRowTo(ctx, mSensorContainer, "(no sensors reported)");
                mSensorRows.put(-1, tv);
            }
        }
    }

    // ── Value formatting ──────────────────────────────────────────────────────

    private static String onOff(int v) {
        if (v < 0) return "—";
        return v == 0 ? "off" : "on";
    }

    private static String intOrDash(int v) { return v < 0 ? "—" : String.valueOf(v); }

    private static String setpoint(int idx) {
        if (idx < 0) return "—";
        double c = ClimateDataSource.setpointCelsius(idx);
        return Double.isNaN(c)
                ? "idx " + idx
                : String.format(Locale.US, "idx %d  (~%.1f °C)", idx, c);
    }

    private static String handle(int v) {
        if (v < 0) return "—";
        return v == 0 ? "LHD (0)" : v == 1 ? "RHD (1)" : String.valueOf(v);
    }

    // ── View helpers ──────────────────────────────────────────────────────────

    private void addRow(Context ctx, LinearLayout parent, String label) {
        mRows.put(label, addRowTo(ctx, parent, label));
    }

    private void set(String label, String value) {
        TextView tv = mRows.get(label);
        if (tv != null) tv.setText(value);
    }

    private static TextView addRowTo(Context ctx, LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView labelTv = new TextView(ctx);
        labelTv.setText(label);
        labelTv.setTextColor(0xFFBBBBBB);
        labelTv.setTextSize(10);
        labelTv.setPadding(0, dp(ctx, 1), dp(ctx, 6), dp(ctx, 1));
        row.addView(labelTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f));

        TextView valueTv = new TextView(ctx);
        valueTv.setText("—");
        valueTv.setTextColor(0xFFAADDFF);
        valueTv.setTextSize(10);
        valueTv.setGravity(Gravity.END);
        valueTv.setPadding(0, dp(ctx, 1), 0, dp(ctx, 1));
        row.addView(valueTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 4f));

        parent.addView(row);
        return valueTv;
    }

    private static void addHeader(Context ctx, LinearLayout list, String text) {
        TextView hdr = new TextView(ctx);
        hdr.setText(text);
        hdr.setTextColor(0xFFFFCC00);
        hdr.setTextSize(11);
        hdr.setPadding(0, dp(ctx, 10), 0, dp(ctx, 2));
        list.addView(hdr);
    }

    private static int dp(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    @Override public void onStart() {}
    @Override public void onVehicleSnapshotChanged(VehicleSnapshot snapshot) {}
    @Override public void onStop() {}
}
