package com.example.clusterapi.vehicle;

/** Fuel level and driving range. */
public final class FuelState {
    /** Driving range remaining from BCAN TRICOM C_TRICOM_RANGE. -1 = not received. */
    public final int     range;
    /** True when range is in miles (C_TRICOM_RANGE_UNIT = 1), false for km. */
    public final boolean rangeInMiles;
    /** Fuel level from FCAN 806 METER_FUEL_LEVEL, raw 0–255. -1 = not received. */
    public final int     level;

    private FuelState(Builder b) {
        range        = b.range;
        rangeInMiles = b.rangeInMiles;
        level        = b.level;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        int     range        = -1;
        boolean rangeInMiles = false;
        int     level        = -1;

        public Builder range(int v)           { range = v;        return this; }
        public Builder rangeInMiles(boolean v){ rangeInMiles = v; return this; }
        public Builder level(int v)           { level = v;        return this; }
        public FuelState build()              { return new FuelState(this); }
    }
}
