package com.example.clusterapp.screens;

import com.example.clusterapi.VehicleSnapshot;
import com.example.clusterapi.vehicle.FuelState;

public class FuelScreen extends SubstateScreen {

    @Override String title() { return "Fuel"; }

    @Override
    String[] labels() {
        return new String[]{"Range", "Unit", "Level"};
    }

    @Override
    String[] values(VehicleSnapshot s) {
        FuelState f = s.fuel;
        return new String[]{
            String.valueOf(f.range),
            f.rangeInMiles ? "miles" : "km",
            String.valueOf(f.level),
        };
    }
}
