package com.example.clusterapi.vehicle;

/** Gear position, shift selector, and reverse light. */
public final class DrivetrainState {
    /** Coarse shift from VehicleInfoManager API. 0=P, 1=R, 2=N, 3=other. -1 = unknown. */
    public final int shiftPosition;
    /** Gear from VehicleInfoManager API. 1=P, 2=R, 4=N, 8=D. -1 = unknown/fault. */
    public final int gear;
    /**
     * Gear from FCAN 401 CVT shift bits or BCAN AT C_TRANSMISSION.
     * DBC values: 1=P, 2=R, 3=N, 4=D, 7=L, 10=S. -1 = not received.
     */
    public final int gearShifter;
    /** Reverse/backup light from BCAN MICU C_BACKLTSW. */
    public final boolean reverseLight;

    private DrivetrainState(Builder b) {
        shiftPosition = b.shiftPosition;
        gear          = b.gear;
        gearShifter   = b.gearShifter;
        reverseLight  = b.reverseLight;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        int     shiftPosition = -1;
        int     gear          = -1;
        int     gearShifter   = -1;
        boolean reverseLight  = false;

        public Builder shiftPosition(int v)   { shiftPosition = v; return this; }
        public Builder gear(int v)            { gear = v;          return this; }
        public Builder gearShifter(int v)     { gearShifter = v;   return this; }
        public Builder reverseLight(boolean v){ reverseLight = v;  return this; }
        public DrivetrainState build()        { return new DrivetrainState(this); }
    }
}
