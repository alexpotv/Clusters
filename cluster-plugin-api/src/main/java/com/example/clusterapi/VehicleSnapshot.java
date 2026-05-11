package com.example.clusterapi;

import com.example.clusterapi.vehicle.AdasState;
import com.example.clusterapi.vehicle.AudioState;
import com.example.clusterapi.vehicle.BrakeState;
import com.example.clusterapi.vehicle.CabinState;
import com.example.clusterapi.vehicle.DrivetrainState;
import com.example.clusterapi.vehicle.FuelState;
import com.example.clusterapi.vehicle.LightsState;
import com.example.clusterapi.vehicle.MaintenanceState;
import com.example.clusterapi.vehicle.MotionState;
import com.example.clusterapi.vehicle.SteeringState;

/**
 * Immutable snapshot of vehicle state delivered to plugins each poll tick.
 *
 * Data is grouped into logical sub-objects so screens only import what they need.
 * All sub-objects are non-null; individual signals use -1 or NaN for "not received."
 */
public class VehicleSnapshot {

    /** Speed, RPM, pedals, wheel speeds, and accelerations. */
    public final MotionState      motion;
    /** Gear selector, CVT position, and reverse light. */
    public final DrivetrainState  drivetrain;
    /** Brake pedal, parking brake, pressure, and stability-control flags. */
    public final BrakeState       brakes;
    /** Steering wheel angle and sensor health. */
    public final SteeringState    steering;
    /** Exterior lights, turn signals, wipers, and ADAS main switch. */
    public final LightsState      lights;
    /** Doors, trunk, and seatbelt. */
    public final CabinState       cabin;
    /** Fuel level and driving range. */
    public final FuelState        fuel;
    /** Audio source, volume, and mute. */
    public final AudioState       audio;
    /** LKAS, CMBS, RDM, and cruise control. */
    public final AdasState        adas;
    /** Oil life and service interval. */
    public final MaintenanceState maintenance;

    public VehicleSnapshot(
            MotionState      motion,
            DrivetrainState  drivetrain,
            BrakeState       brakes,
            SteeringState    steering,
            LightsState      lights,
            CabinState       cabin,
            FuelState        fuel,
            AudioState       audio,
            AdasState        adas,
            MaintenanceState maintenance) {
        this.motion      = motion;
        this.drivetrain  = drivetrain;
        this.brakes      = brakes;
        this.steering    = steering;
        this.lights      = lights;
        this.cabin       = cabin;
        this.fuel        = fuel;
        this.audio       = audio;
        this.adas        = adas;
        this.maintenance = maintenance;
    }
}
