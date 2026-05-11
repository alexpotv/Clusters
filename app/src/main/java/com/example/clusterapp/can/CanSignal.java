package com.example.clusterapp.can;

/** One DBC SG_ record: a single CAN signal definition. */
public final class CanSignal {
    public final String name;
    public final int startBit;
    public final int length;
    /** true = Motorola / big-endian (@0), false = Intel / little-endian (@1). */
    public final boolean isBigEndian;
    /** true = signed two's-complement raw value (@x-). */
    public final boolean isSigned;
    public final double factor;
    public final double offset;
    public final double min;
    public final double max;
    public final String unit;
    public final String receiver;

    public CanSignal(String name, int startBit, int length,
                     boolean isBigEndian, boolean isSigned,
                     double factor, double offset,
                     double min, double max,
                     String unit, String receiver) {
        this.name        = name;
        this.startBit    = startBit;
        this.length      = length;
        this.isBigEndian = isBigEndian;
        this.isSigned    = isSigned;
        this.factor      = factor;
        this.offset      = offset;
        this.min         = min;
        this.max         = max;
        this.unit        = unit;
        this.receiver    = receiver;
    }
}
