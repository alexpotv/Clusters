package com.example.clusterapp.screens;

import com.example.clusterapi.VehicleSnapshot;
import com.example.clusterapi.vehicle.SteeringState;

public class SteeringScreen extends SubstateScreen {

    @Override String title() { return "Steering"; }

    @Override
    String[] labels() {
        return new String[]{"Angle", "Sensor OK"};
    }

    @Override
    String[] values(VehicleSnapshot s) {
        SteeringState st = s.steering;
        return new String[]{f1(st.angleDeg) + "°", on(st.sensorOk)};
    }
}
