package com.example.clusterapp;

import com.example.clusterapi.VehicleSnapshot;

/**
 * Host-internal alias for {@link VehicleSnapshot}.
 * All screens and services should reference VehicleSnapshot directly.
 */
public final class VehicleState extends VehicleSnapshot {

    public VehicleState(
            com.example.clusterapi.vehicle.MotionState      motion,
            com.example.clusterapi.vehicle.DrivetrainState  drivetrain,
            com.example.clusterapi.vehicle.BrakeState       brakes,
            com.example.clusterapi.vehicle.SteeringState    steering,
            com.example.clusterapi.vehicle.LightsState      lights,
            com.example.clusterapi.vehicle.CabinState       cabin,
            com.example.clusterapi.vehicle.FuelState        fuel,
            com.example.clusterapi.vehicle.AudioState       audio,
            com.example.clusterapi.vehicle.AdasState        adas,
            com.example.clusterapi.vehicle.MaintenanceState maintenance) {
        super(motion, drivetrain, brakes, steering, lights, cabin, fuel, audio, adas, maintenance);
    }
}
