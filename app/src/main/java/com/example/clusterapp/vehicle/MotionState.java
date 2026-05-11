package com.example.clusterapp.vehicle;

/** Speed, RPM, acceleration, pedal inputs, and wheel speeds. */
public final class MotionState {
    /** Speed from VehicleInfoManager API, km/h. -1 = unknown. */
    public final int    speedKmh;
    /** Speed from FCAN 344 XMISSION_SPEED × 0.01, kph. NaN = not received. */
    public final double fcanSpeedKph;
    /** Speedometer display value from FCAN 777 METER_DISPLAY_SPEED, kph. -1 = not received. */
    public final int    displaySpeedKph;
    /** Engine RPM from FCAN 380 ENG_ENG_SPEED. -1 = not received. */
    public final int    rpm;
    /** Gas pedal position from FCAN 380 PEDAL_GAS, raw 0–255. -1 = not received. */
    public final int    pedalGas;
    /** True when pedalGas > 0. */
    public final boolean gasPressed;
    /** Individual wheel speeds from FCAN 464, kph × 0.01. NaN = blocked (needs CpuComService). */
    public final double wheelSpeedFl;
    public final double wheelSpeedFr;
    public final double wheelSpeedRl;
    public final double wheelSpeedRr;
    /** Lateral acceleration from FCAN 148/490, m/s². NaN = blocked. */
    public final double latAccelMs2;
    /** Longitudinal acceleration from FCAN 148/490, m/s². NaN = blocked. */
    public final double longAccelMs2;

    private MotionState(Builder b) {
        speedKmh       = b.speedKmh;
        fcanSpeedKph   = b.fcanSpeedKph;
        displaySpeedKph = b.displaySpeedKph;
        rpm            = b.rpm;
        pedalGas       = b.pedalGas;
        gasPressed     = b.gasPressed;
        wheelSpeedFl   = b.wheelSpeedFl;
        wheelSpeedFr   = b.wheelSpeedFr;
        wheelSpeedRl   = b.wheelSpeedRl;
        wheelSpeedRr   = b.wheelSpeedRr;
        latAccelMs2    = b.latAccelMs2;
        longAccelMs2   = b.longAccelMs2;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        int    speedKmh        = -1;
        double fcanSpeedKph    = Double.NaN;
        int    displaySpeedKph = -1;
        int    rpm             = -1;
        int    pedalGas        = -1;
        boolean gasPressed     = false;
        double wheelSpeedFl    = Double.NaN;
        double wheelSpeedFr    = Double.NaN;
        double wheelSpeedRl    = Double.NaN;
        double wheelSpeedRr    = Double.NaN;
        double latAccelMs2     = Double.NaN;
        double longAccelMs2    = Double.NaN;

        public Builder speedKmh(int v)          { speedKmh = v;        return this; }
        public Builder fcanSpeedKph(double v)   { fcanSpeedKph = v;    return this; }
        public Builder displaySpeedKph(int v)   { displaySpeedKph = v; return this; }
        public Builder rpm(int v)               { rpm = v;             return this; }
        public Builder pedalGas(int v)          { pedalGas = v;        return this; }
        public Builder gasPressed(boolean v)    { gasPressed = v;      return this; }
        public Builder wheelSpeedFl(double v)   { wheelSpeedFl = v;    return this; }
        public Builder wheelSpeedFr(double v)   { wheelSpeedFr = v;    return this; }
        public Builder wheelSpeedRl(double v)   { wheelSpeedRl = v;    return this; }
        public Builder wheelSpeedRr(double v)   { wheelSpeedRr = v;    return this; }
        public Builder latAccelMs2(double v)    { latAccelMs2 = v;     return this; }
        public Builder longAccelMs2(double v)   { longAccelMs2 = v;    return this; }
        public MotionState build()              { return new MotionState(this); }
    }
}
