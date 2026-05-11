package com.example.clusterapp.screens;

import android.view.View;

import com.example.clusterapi.ClusterPlugin;
import com.example.clusterapi.PluginContext;
import com.example.clusterapi.VehicleSnapshot;

/**
 * Beach/ocean themed screen that shows the available driving range in the centre.
 * Animated waves scroll across the bottom; starfish, shells, and a turtle sit on the sand.
 */
public class BeachScreen implements ClusterPlugin {

    public BeachScreen() {}

    private BeachView mView;

    @Override
    public View onCreateView(PluginContext context) {
        mView = new BeachView(context.getAndroidContext());
        return mView;
    }

    @Override
    public void onStart() {
        mView.startWaves();
    }

    @Override
    public void onVehicleSnapshotChanged(VehicleSnapshot snapshot) {
        mView.setRange(snapshot.fuel.range, snapshot.fuel.rangeInMiles);
    }

    @Override
    public void onStop() {
        mView.stopWaves();
        mView = null;
    }

    @Override
    public int getRefreshRateMs() {
        return 500;
    }
}
