package com.example.clusterapp.screens;

import com.example.clusterapi.VehicleSnapshot;
import com.example.clusterapi.vehicle.DrivetrainState;

public class DrivetrainScreen extends SubstateScreen {

    @Override String title() { return "Drivetrain"; }

    @Override
    String[] labels() {
        return new String[]{"Shift Position", "Gear", "Gear Shifter", "Reverse Light"};
    }

    @Override
    String[] values(VehicleSnapshot s) {
        DrivetrainState d = s.drivetrain;
        return new String[]{
            String.valueOf(d.shiftPosition),
            String.valueOf(d.gear),
            String.valueOf(d.gearShifter),
            on(d.reverseLight),
        };
    }
}
