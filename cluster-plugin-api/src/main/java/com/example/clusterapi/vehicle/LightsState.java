package com.example.clusterapi.vehicle;

/** Exterior lights, turn signals, wipers, and ADAS main switch. */
public final class LightsState {
    /** Exterior lights from VehicleInfoManager VEHICLE_INFO_ILLUMINATION. */
    public final boolean lightsOn;
    /** Left turn from VehicleInfoManager TX_GET_TURN_SIGNALS. */
    public final boolean turnLeft;
    /** Right turn from VehicleInfoManager TX_GET_TURN_SIGNALS. */
    public final boolean turnRight;
    /** Left blinker from FCAN 806 METER_TURN_SIGNAL_L_TURN or BCAN HLSW C_TURNL. */
    public final boolean leftBlinker;
    /** Right blinker from FCAN 806 METER_TURN_SIGNAL_R_TURN or BCAN HLSW C_TURNR. */
    public final boolean rightBlinker;
    /** Headlights from BCAN ILLUMI C_METER_ILL_STATUS. */
    public final boolean headlightsOn;
    /** High beams. Not forwarded by middleware — always false. */
    public final boolean highBeam;
    /** Wiper speed from FCAN 884 METER_WIPER_STATUS. 0=off, 2=low, 4=high. -1 = not received. */
    public final int     wipers;
    /** LKAS/ACC main switch from BCAN MICU C_IG1. */
    public final boolean mainOn;

    private LightsState(Builder b) {
        lightsOn     = b.lightsOn;
        turnLeft     = b.turnLeft;
        turnRight    = b.turnRight;
        leftBlinker  = b.leftBlinker;
        rightBlinker = b.rightBlinker;
        headlightsOn = b.headlightsOn;
        highBeam     = b.highBeam;
        wipers       = b.wipers;
        mainOn       = b.mainOn;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        boolean lightsOn     = false;
        boolean turnLeft     = false;
        boolean turnRight    = false;
        boolean leftBlinker  = false;
        boolean rightBlinker = false;
        boolean headlightsOn = false;
        boolean highBeam     = false;
        int     wipers       = -1;
        boolean mainOn       = false;

        public Builder lightsOn(boolean v)     { lightsOn = v;     return this; }
        public Builder turnLeft(boolean v)     { turnLeft = v;     return this; }
        public Builder turnRight(boolean v)    { turnRight = v;    return this; }
        public Builder leftBlinker(boolean v)  { leftBlinker = v;  return this; }
        public Builder rightBlinker(boolean v) { rightBlinker = v; return this; }
        public Builder headlightsOn(boolean v) { headlightsOn = v; return this; }
        public Builder highBeam(boolean v)     { highBeam = v;     return this; }
        public Builder wipers(int v)           { wipers = v;       return this; }
        public Builder mainOn(boolean v)       { mainOn = v;       return this; }
        public LightsState build()             { return new LightsState(this); }
    }
}
