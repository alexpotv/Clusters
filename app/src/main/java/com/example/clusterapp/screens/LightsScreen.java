package com.example.clusterapp.screens;

import com.example.clusterapi.VehicleSnapshot;
import com.example.clusterapi.vehicle.LightsState;

public class LightsScreen extends SubstateScreen {

    @Override String title() { return "Lights"; }

    @Override
    String[] labels() {
        return new String[]{
            "Lights On", "Main On", "Headlights",
            "High Beam", "Wipers",
            "Turn Left", "Turn Right",
            "Blinker Left", "Blinker Right",
        };
    }

    @Override
    String[] values(VehicleSnapshot s) {
        LightsState l = s.lights;
        return new String[]{
            on(l.lightsOn),    on(l.mainOn),    on(l.headlightsOn),
            on(l.highBeam),    String.valueOf(l.wipers),
            on(l.turnLeft),    on(l.turnRight),
            on(l.leftBlinker), on(l.rightBlinker),
        };
    }
}
