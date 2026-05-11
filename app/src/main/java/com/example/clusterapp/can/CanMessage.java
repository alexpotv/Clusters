package com.example.clusterapp.can;

import java.util.List;

/** One DBC BO_ record: a CAN message with its ID, name, and signal list. */
public final class CanMessage {
    public final int id;
    public final String name;
    public final List<CanSignal> signals;

    public CanMessage(int id, String name, List<CanSignal> signals) {
        this.id = id;
        this.name = name;
        this.signals = signals;
    }
}
