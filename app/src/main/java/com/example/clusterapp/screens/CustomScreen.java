package com.example.clusterapp.screens;

import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import com.example.clusterapi.ClusterPlugin;
import com.example.clusterapi.PluginContext;
import com.example.clusterapi.VehicleSnapshot;

/** Placeholder for future custom screens. */
public class CustomScreen implements ClusterPlugin {

    public CustomScreen() {}

    @Override
    public View onCreateView(PluginContext context) {
        TextView tv = new TextView(context.getAndroidContext());
        tv.setText("Custom screen – coming soon");
        tv.setTextSize(16);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    @Override public void onStart() {}
    @Override public void onVehicleSnapshotChanged(VehicleSnapshot snapshot) {}
    @Override public void onStop() {}
    @Override public int getRefreshRateMs() { return 1000; }
}
