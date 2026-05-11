package com.example.clusterapp.screens;

import com.example.clusterapi.VehicleSnapshot;
import com.example.clusterapi.vehicle.AudioState;

public class AudioScreen extends SubstateScreen {

    @Override String title() { return "Audio"; }

    @Override
    String[] labels() {
        return new String[]{"Source", "Volume", "Volume Max", "Mute", "Music Playing"};
    }

    @Override
    String[] values(VehicleSnapshot s) {
        AudioState a = s.audio;
        return new String[]{
            String.valueOf(a.source),
            String.valueOf(a.volume),
            String.valueOf(a.volumeMax),
            on(a.mute),
            on(a.musicPlaying),
        };
    }
}
