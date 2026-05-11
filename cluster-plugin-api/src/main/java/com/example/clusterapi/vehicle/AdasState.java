package com.example.clusterapi.vehicle;

/** LKAS, CMBS, RDM, and cruise-control state. */
public final class AdasState {
    /** LKAS lane-keep assist active from FCAN 829 LKAS_STATUS_ACTIVE_33D. */
    public final boolean lkasActive;
    /** LKAS system fault from FCAN 829 LKAS_STATUS_FAILED_33D. */
    public final boolean lkasFailed;
    /** LKAS adjustment mode from FCAN 829 LKAS_STATUS_ADJ_MODE_33D. */
    public final boolean lkasAdjMode;
    /**
     * CMBS (collision mitigation) following distance setting from BCAN MET_CUSTOM
     * C_MET_CUSTOM_CMBS_DISTANCE. -1 = not received.
     */
    public final int     cmbsDistance;
    /**
     * LKAS lane-departure buzzer status from BCAN MET_CUSTOM
     * C_MET_CUSTOM_LKAS_BUZZER_STATUS. -1 = not received.
     */
    public final int     lkasBuzzerStatus;
    /**
     * Road departure mitigation status from BCAN MET_CUSTOM C_MET_CUSTOM_RDM_STATUS.
     * -1 = not received.
     */
    public final int     rdmStatus;
    /** Cruise control set speed, kph. 255 = inactive, -1 = not received. */
    public final int     cruiseSpeedKph;

    private AdasState(Builder b) {
        lkasActive       = b.lkasActive;
        lkasFailed       = b.lkasFailed;
        lkasAdjMode      = b.lkasAdjMode;
        cmbsDistance     = b.cmbsDistance;
        lkasBuzzerStatus = b.lkasBuzzerStatus;
        rdmStatus        = b.rdmStatus;
        cruiseSpeedKph   = b.cruiseSpeedKph;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        boolean lkasActive       = false;
        boolean lkasFailed       = false;
        boolean lkasAdjMode      = false;
        int     cmbsDistance     = -1;
        int     lkasBuzzerStatus = -1;
        int     rdmStatus        = -1;
        int     cruiseSpeedKph   = -1;

        public Builder lkasActive(boolean v)      { lkasActive = v;       return this; }
        public Builder lkasFailed(boolean v)      { lkasFailed = v;       return this; }
        public Builder lkasAdjMode(boolean v)     { lkasAdjMode = v;      return this; }
        public Builder cmbsDistance(int v)        { cmbsDistance = v;     return this; }
        public Builder lkasBuzzerStatus(int v)    { lkasBuzzerStatus = v; return this; }
        public Builder rdmStatus(int v)           { rdmStatus = v;        return this; }
        public Builder cruiseSpeedKph(int v)      { cruiseSpeedKph = v;   return this; }
        public AdasState build()                  { return new AdasState(this); }
    }
}
