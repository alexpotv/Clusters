package com.example.clusterapi.vehicle;

/** Doors, trunk, and seatbelt status. */
public final class CabinState {
    /** Front-left door open. Not forwarded by middleware — always false. */
    public final boolean doorFl;
    /** Front-right door open. Not forwarded by middleware — always false. */
    public final boolean doorFr;
    /** Rear-left door open. Not forwarded by middleware — always false. */
    public final boolean doorRl;
    /** Rear-right door open. Not forwarded by middleware — always false. */
    public final boolean doorRr;
    /** Trunk open. Not forwarded by middleware — always false. */
    public final boolean trunkOpen;
    /**
     * Driver seatbelt latched. Derived from FCAN 884 METER_SEAT_BELT_WRN_RMD:
     * warning=1 means NOT latched; true here means latched (no warning).
     */
    public final boolean seatbeltDriver;

    private CabinState(Builder b) {
        doorFl         = b.doorFl;
        doorFr         = b.doorFr;
        doorRl         = b.doorRl;
        doorRr         = b.doorRr;
        trunkOpen      = b.trunkOpen;
        seatbeltDriver = b.seatbeltDriver;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        boolean doorFl         = false;
        boolean doorFr         = false;
        boolean doorRl         = false;
        boolean doorRr         = false;
        boolean trunkOpen      = false;
        boolean seatbeltDriver = false;

        public Builder doorFl(boolean v)         { doorFl = v;         return this; }
        public Builder doorFr(boolean v)         { doorFr = v;         return this; }
        public Builder doorRl(boolean v)         { doorRl = v;         return this; }
        public Builder doorRr(boolean v)         { doorRr = v;         return this; }
        public Builder trunkOpen(boolean v)      { trunkOpen = v;      return this; }
        public Builder seatbeltDriver(boolean v) { seatbeltDriver = v; return this; }
        public CabinState build()                { return new CabinState(this); }
    }
}
