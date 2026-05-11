package com.example.clusterapp.screens;

import com.example.clusterapi.VehicleSnapshot;
import com.example.clusterapi.vehicle.AdasState;

public class AdasScreen extends SubstateScreen {

    @Override String title() { return "ADAS"; }

    @Override
    String[] labels() {
        return new String[]{
            "LKAS Active", "LKAS Failed", "LKAS Adj Mode",
            "CMBS Distance", "LKAS Buzzer", "RDM Status",
            "Cruise Speed",
        };
    }

    @Override
    String[] values(VehicleSnapshot s) {
        AdasState a = s.adas;
        return new String[]{
            on(a.lkasActive),  on(a.lkasFailed),  on(a.lkasAdjMode),
            String.valueOf(a.cmbsDistance),
            String.valueOf(a.lkasBuzzerStatus),
            String.valueOf(a.rdmStatus),
            a.cruiseSpeedKph + " km/h",
        };
    }
}
