package com.example.clusterapi.vehicle;

/** Oil life and service interval data from BCAN maintenance frame (318334800). */
public final class MaintenanceState {
    /**
     * Oil life percentage bucket from C_MAINT_OIL_LIFE, scale 0–15
     * (15 = new oil, 0 = change required). -1 = not received.
     */
    public final int oilLife;
    /**
     * Distance to next service from C_MAINT_DATA (km or miles depending on maintUnit).
     * 9999 typically means "not yet due / full interval remaining". -1 = not received.
     */
    public final int maintData;
    /** Service interval unit from C_MAINT_UNIT. 0 = km, 1 = miles. -1 = not received. */
    public final int maintUnit;

    private MaintenanceState(Builder b) {
        oilLife   = b.oilLife;
        maintData = b.maintData;
        maintUnit = b.maintUnit;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        int oilLife   = -1;
        int maintData = -1;
        int maintUnit = -1;

        public Builder oilLife(int v)   { oilLife = v;   return this; }
        public Builder maintData(int v) { maintData = v; return this; }
        public Builder maintUnit(int v) { maintUnit = v; return this; }
        public MaintenanceState build() { return new MaintenanceState(this); }
    }
}
