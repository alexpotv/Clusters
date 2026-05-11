package com.example.clusterapp.vehicle;

/** Steering wheel angle and sensor health. */
public final class SteeringState {
    /** Steering angle from BCAN STEERING C_STR_ANGLE × 0.1, degrees. NaN = not received. */
    public final double  angleDeg;
    /** Steering sensor status from BCAN STEERING C_STR_STATE_OK. false when not received. */
    public final boolean sensorOk;

    private SteeringState(Builder b) {
        angleDeg = b.angleDeg;
        sensorOk = b.sensorOk;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        double  angleDeg = Double.NaN;
        boolean sensorOk = false;

        public Builder angleDeg(double v)  { angleDeg = v; return this; }
        public Builder sensorOk(boolean v) { sensorOk = v; return this; }
        public SteeringState build()       { return new SteeringState(this); }
    }
}
