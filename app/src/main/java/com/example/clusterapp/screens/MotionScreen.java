package com.example.clusterapp.screens;

import com.example.clusterapi.VehicleSnapshot;
import com.example.clusterapi.vehicle.MotionState;

public class MotionScreen extends SubstateScreen {

    @Override String title() { return "Motion"; }

    @Override
    String[] labels() {
        return new String[]{
            "Speed", "FCAN Speed", "Display Speed", "RPM",
            "Gas Pedal", "Gas Pressed",
            "Wheel FL", "Wheel FR", "Wheel RL", "Wheel RR",
            "Lat Accel", "Long Accel",
        };
    }

    @Override
    String[] values(VehicleSnapshot s) {
        MotionState m = s.motion;
        return new String[]{
            m.speedKmh + " km/h",
            f1(m.fcanSpeedKph) + " km/h",
            m.displaySpeedKph + " km/h",
            String.valueOf(m.rpm),
            m.pedalGas + "%",
            on(m.gasPressed),
            f1(m.wheelSpeedFl), f1(m.wheelSpeedFr),
            f1(m.wheelSpeedRl), f1(m.wheelSpeedRr),
            f1(m.latAccelMs2  / 9.81) + " g",
            f1(m.longAccelMs2 / 9.81) + " g",
        };
    }
}
