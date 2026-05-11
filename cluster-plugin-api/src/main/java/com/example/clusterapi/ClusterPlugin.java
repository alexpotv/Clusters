package com.example.clusterapi;

import android.view.View;

/**
 * Contract that every cluster screen plugin must implement.
 *
 * Lifecycle: onCreateView → onStart → onVehicleSnapshotChanged (repeated) → onStop.
 * All methods are called on the main thread.
 *
 * The view returned by {@link #onCreateView} will be sized to exactly
 * {@link PluginContext#getCanvasWidth()} × {@link PluginContext#getCanvasHeight()} pixels.
 * Draw within those bounds; content outside is clipped by the host.
 */
public interface ClusterPlugin {

    /** Create and return the View that represents this screen. */
    View onCreateView(PluginContext context);

    /** The view is now attached. Start animations, timers, or any ongoing work. */
    void onStart();

    /**
     * Fresh vehicle data is available. Update the view accordingly.
     * Called on the main thread at {@link #getRefreshRateMs()} intervals.
     * Must return quickly — do not block.
     */
    void onVehicleSnapshotChanged(VehicleSnapshot snapshot);

    /** The screen is being replaced or the host is shutting down. Release all resources. */
    void onStop();

    /** Desired poll interval in milliseconds. Override to reduce CPU use on slow-changing data. */
    default int getRefreshRateMs() { return 250; }

    /**
     * Return a View that lets the user configure this screen's settings.
     *
     * The host shows this view inside a settings panel on the head-unit display
     * when the user taps the settings button.  The panel provides its own title
     * and dismiss button, so the returned view should contain only the settings
     * controls themselves.
     *
     * Return {@code null} (the default) if this screen has no configurable options.
     * Use {@link PluginContext#getSettings()} to persist values.
     */
    default View onCreateSettingsView(PluginContext context) { return null; }
}
