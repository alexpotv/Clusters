package com.example.clusterapp.screens;

import com.example.clusterapi.VehicleSnapshot;
import com.example.clusterapi.vehicle.MaintenanceState;

public class MaintenanceScreen extends SubstateScreen {

    @Override String title() { return "Maintenance"; }

    @Override
    String[] labels() {
        return new String[]{"Oil Life", "Maint Data", "Maint Unit"};
    }

    @Override
    String[] values(VehicleSnapshot s) {
        MaintenanceState m = s.maintenance;
        return new String[]{
            m.oilLife + "%",
            String.valueOf(m.maintData),
            String.valueOf(m.maintUnit),
        };
    }
}
