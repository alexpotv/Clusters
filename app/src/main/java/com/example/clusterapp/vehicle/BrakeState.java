package com.example.clusterapp.vehicle;

/** Brake pedal, parking brake, brake pressure, and stability-control flags. */
public final class BrakeState {
    /** Foot-brake pedal from VehicleInfoManager TX_GET_FOOT_BRAKE. */
    public final boolean pedalPressed;
    /** Brake switch from FCAN 380 ENG_SW_STATUS_BRAKE_NC. */
    public final boolean fcanBrakePressed;
    /** Parking brake from VehicleInfoManager VEHICLE_INFO_PARKING_BRAKE. */
    public final boolean parkingBrake;
    /** Parking brake from FCAN 806 METER_SW_STATUS_PARK_BRAKE or BCAN AT C_PBRAKE. */
    public final boolean parkingBrakeOn;
    /** Master cylinder pressure from FCAN 420 VSA_MASTER_CYLINDER_PRESSURE, bar. NaN = not received. */
    public final double  userBrakeBar;
    /** ABS/TCS active from FCAN 420 VSA_VSA_TCS_ACT. */
    public final boolean computerBraking;
    /** VSA/ESP disabled from FCAN 420 VSA_WARN_STATUS_VSA. */
    public final boolean espDisabled;
    /** Brake fluid level warning from FCAN 806 METER_SW_STATUS_BRAKE_FLUID. */
    public final boolean brakeFluidWarning;
    /** ABS active warning from FCAN 420 VSA_WARN_STATUS_ABS. */
    public final boolean absWarning;
    /** Brake system warning from FCAN 420 VSA_WARN_STATUS_BRAKE. */
    public final boolean brakeWarning;
    /** Tyre puncture warning from FCAN 420 VSA_WARN_STATUS_PUNCTURE. */
    public final boolean punctureWarning;

    private BrakeState(Builder b) {
        pedalPressed    = b.pedalPressed;
        fcanBrakePressed = b.fcanBrakePressed;
        parkingBrake    = b.parkingBrake;
        parkingBrakeOn  = b.parkingBrakeOn;
        userBrakeBar    = b.userBrakeBar;
        computerBraking = b.computerBraking;
        espDisabled     = b.espDisabled;
        brakeFluidWarning = b.brakeFluidWarning;
        absWarning      = b.absWarning;
        brakeWarning    = b.brakeWarning;
        punctureWarning = b.punctureWarning;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        boolean pedalPressed     = false;
        boolean fcanBrakePressed = false;
        boolean parkingBrake     = false;
        boolean parkingBrakeOn   = false;
        double  userBrakeBar     = Double.NaN;
        boolean computerBraking  = false;
        boolean espDisabled      = false;
        boolean brakeFluidWarning = false;
        boolean absWarning       = false;
        boolean brakeWarning     = false;
        boolean punctureWarning  = false;

        public Builder pedalPressed(boolean v)      { pedalPressed = v;     return this; }
        public Builder fcanBrakePressed(boolean v)  { fcanBrakePressed = v; return this; }
        public Builder parkingBrake(boolean v)      { parkingBrake = v;     return this; }
        public Builder parkingBrakeOn(boolean v)    { parkingBrakeOn = v;   return this; }
        public Builder userBrakeBar(double v)       { userBrakeBar = v;     return this; }
        public Builder computerBraking(boolean v)   { computerBraking = v;  return this; }
        public Builder espDisabled(boolean v)       { espDisabled = v;      return this; }
        public Builder brakeFluidWarning(boolean v) { brakeFluidWarning = v;return this; }
        public Builder absWarning(boolean v)        { absWarning = v;       return this; }
        public Builder brakeWarning(boolean v)      { brakeWarning = v;     return this; }
        public Builder punctureWarning(boolean v)   { punctureWarning = v;  return this; }
        public BrakeState build()                   { return new BrakeState(this); }
    }
}
