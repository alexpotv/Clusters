package com.alexpotv.kartscreen;

import android.view.View;

import com.example.clusterapi.ClusterPlugin;
import com.example.clusterapi.PluginContext;
import com.example.clusterapi.VehicleSnapshot;

/**
 * Cluster screen that renders a 3D go-kart + driver model and animates it with
 * live vehicle data:
 *
 *   Shift position 1 (R) or 2 (N) → auto-rotating showcase spin
 *   All other shift positions       → yaw driven by steering angle (±30° max)
 *   Speed                           → slight nose-down pitch (aerodynamic press)
 *   Brakes pressed                  → red ambient tint simulating brake-light back-scatter
 *
 * Required assets in the plugin APK's assets/ folder:
 *   kart.obj / kart.mtl        — kart body, exported from Blender with Y-up
 *   character.obj / character.mtl — driver figure, same coordinate space
 *   *.png                      — all texture images referenced by the MTL files
 *
 * See README.md in this plugin's root for Blender export instructions.
 */
public final class KartScreen implements ClusterPlugin {

    private KartGLView   mView;
    private KartRenderer mRenderer;

    public KartScreen() {}

    @Override
    public View onCreateView(PluginContext ctx) {
        mRenderer = new KartRenderer(ctx);
        mView     = new KartGLView(ctx.getAndroidContext(), mRenderer);
        return mView;
    }

    @Override
    public void onStart() {
        if (mView != null) mView.onResume();
    }

    @Override
    public void onVehicleSnapshotChanged(VehicleSnapshot s) {
        if (mRenderer == null) return;
        // angleDeg is NaN when the sensor hasn't reported yet — treat as 0.
        float steer = Double.isNaN(s.steering.angleDeg) ? 0f : (float) s.steering.angleDeg;
        mRenderer.setSteeringDeg(steer);
        mRenderer.setSpeedKmh(Math.max(0, s.motion.speedKmh));
        mRenderer.setBraking(s.brakes.pedalPressed || s.brakes.fcanBrakePressed);
        mRenderer.setShiftPosition(s.drivetrain.shiftPosition);
        mRenderer.setHeadlights(s.lights.headlightsOn);
        mRenderer.setBlinkerL(s.lights.leftBlinker);
        mRenderer.setBlinkerR(s.lights.rightBlinker);
        mRenderer.setLkasActive(s.adas.lkasActive);
    }

    @Override
    public void onStop() {
        if (mView != null) {
            mView.onPause();
            mView = null;
        }
        mRenderer = null;
    }

    @Override
    public int getRefreshRateMs() {
        return 100; // 10 Hz is ample; the GL thread drives visual smoothness independently
    }
}
