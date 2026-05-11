package com.example.clusterapp.screens;

import com.example.clusterapi.VehicleSnapshot;
import com.example.clusterapi.vehicle.BrakeState;

public class BrakeScreen extends SubstateScreen {

    @Override String title() { return "Brakes"; }

    @Override
    String[] labels() {
        return new String[]{
            "Pedal", "FCAN Pedal",
            "Parking Brake", "Parking On",
            "Brake Bar", "Computer Braking",
            "ESP Disabled",
            "Fluid Warning", "ABS Warning",
            "Brake Warning", "Puncture Warning",
        };
    }

    @Override
    String[] values(VehicleSnapshot s) {
        BrakeState b = s.brakes;
        return new String[]{
            on(b.pedalPressed),   on(b.fcanBrakePressed),
            on(b.parkingBrake),   on(b.parkingBrakeOn),
            f1(b.userBrakeBar),   on(b.computerBraking),
            on(b.espDisabled),
            warn(b.brakeFluidWarning), warn(b.absWarning),
            warn(b.brakeWarning),      warn(b.punctureWarning),
        };
    }
}
