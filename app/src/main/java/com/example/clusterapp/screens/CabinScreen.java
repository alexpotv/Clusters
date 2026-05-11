package com.example.clusterapp.screens;

import com.example.clusterapi.VehicleSnapshot;
import com.example.clusterapi.vehicle.CabinState;

public class CabinScreen extends SubstateScreen {

    @Override String title() { return "Cabin"; }

    @Override
    String[] labels() {
        return new String[]{
            "Door FL", "Door FR", "Door RL", "Door RR",
            "Trunk", "Seatbelt Driver",
        };
    }

    @Override
    String[] values(VehicleSnapshot s) {
        CabinState c = s.cabin;
        return new String[]{
            on(c.doorFl), on(c.doorFr), on(c.doorRl), on(c.doorRr),
            on(c.trunkOpen), on(c.seatbeltDriver),
        };
    }
}
