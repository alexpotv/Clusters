package com.example.exampleplugin;

import android.content.Context;
import android.view.View;
import com.example.clusterapi.ClusterPlugin;
import com.example.clusterapi.PluginContext;
import com.example.clusterapi.VehicleSnapshot;

public class Example1Plugin implements ClusterPlugin {
    public Example1Plugin() {}

    @Override
    public View onCreateView(PluginContext context) {
        return new View(context.getAndroidContext());
    }

    @Override
    public void onStart() {}

    @Override
    public void onVehicleSnapshotChanged(VehicleSnapshot snapshot) {}

    @Override
    public void onStop() {}
}
