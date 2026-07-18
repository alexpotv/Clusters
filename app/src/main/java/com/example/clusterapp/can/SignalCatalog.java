package com.example.clusterapp.can;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Formatting metadata for the pre-decoded signals the head-unit middleware relays.
 *
 * <p>Vehicle data reaches an ordinary app already parsed: the companion microcontroller
 * (layer D) forwards a fixed set of frames to the SoC, and {@code VehicleInfoManagerApService}
 * (layer A) decodes each raw {@code byte[8]} into named integer Bundle values keyed by
 * Mitsubishi signal names. {@link com.example.clusterapp.VehicleDataSource} captures every one
 * of those integers wholesale (see {@code getFcanRaw()/getBcanRaw()}), keyed as
 * {@code "typeCode/SIGNAL_NAME"}.
 *
 * <p>This catalog is the last formatting step: it turns each raw integer into a human-readable
 * row — a friendly name, the physical value (raw × factor + offset), and a unit — reproducing
 * the scaling the stock firmware / opendbc apply (see {@code reference-docs/can-analysis.md}
 * §4–§5). Signals not listed here are still shown by their raw name and integer value; the
 * catalog only enriches what the reference layers already gave meaning to.
 *
 * <p>Note on signedness: the middleware sign-extends the few signed fields (steering angle,
 * BCAN engine speed) before putting them in the Bundle, so the raw integer captured upstream is
 * already signed. The catalog therefore only applies factor/offset — never re-interprets bits.
 */
public final class SignalCatalog {

    /** Formatting descriptor for one middleware signal. */
    public static final class SignalInfo {
        public final String display;      // friendly name
        public final double factor;
        public final double offset;
        public final String unit;
        public final String[] enumLabels; // value→label, indexed by raw int; null if none

        SignalInfo(String display, double factor, double offset, String unit, String[] enumLabels) {
            this.display    = display;
            this.factor     = factor;
            this.offset     = offset;
            this.unit       = unit;
            this.enumLabels = enumLabels;
        }

        /** True when this signal carries a scaled/enumerated value worth showing beside the raw int. */
        public boolean hasFormattedValue() {
            return enumLabels != null || factor != 1.0 || offset != 0.0 || !unit.isEmpty();
        }

        /** Render the value column for a raw integer, or "" if there is nothing to add beyond the raw. */
        public String formatValue(int raw) {
            if (enumLabels != null) {
                if (raw >= 0 && raw < enumLabels.length) return enumLabels[raw];
                return String.valueOf(raw);
            }
            if (factor == 1.0 && offset == 0.0) {
                return unit.isEmpty() ? "" : raw + " " + unit;
            }
            double v = raw * factor + offset;
            String num = (v == Math.floor(v) && Math.abs(v) < 1e9)
                    ? String.valueOf((long) v)
                    : String.format(Locale.US, "%.2f", v);
            return unit.isEmpty() ? num : num + " " + unit;
        }
    }

    // ── Frame names, keyed by the middleware type code (FCAN 11-bit id / BCAN 32-bit id) ──
    private static final Map<Integer, String> FRAMES = new HashMap<>();

    // ── Signal descriptors, keyed by "typeCode/SIGNAL_NAME" (matches VehicleDataSource raw maps) ──
    private static final Map<String, SignalInfo> SIGNALS = new HashMap<>();

    // Reusable enum ladders.
    private static final String[] SPEED_UNIT = {"km/h", "mph"};
    private static final String[] DIST_UNIT  = {"km", "mi"};
    private static final String[] TRANSMISSION = {"MT", "AMT", "AT", "CVT", "DCT"};

    public static String frameName(int type)        { return FRAMES.get(type); }
    public static SignalInfo signal(int type, String key) { return SIGNALS.get(type + "/" + key); }

    /** e.g. "ENGINE_DATA · 0x158" for a known frame, else just "0x158". */
    public static String frameLabel(int type) {
        String name = FRAMES.get(type);
        String hex  = "0x" + Integer.toHexString(type).toUpperCase(Locale.US);
        return name != null ? name + " · " + hex : hex;
    }

    // ── Registration helpers ─────────────────────────────────────────────────
    private static void frame(int type, String name) { FRAMES.put(type, name); }

    /** Boolean / counter / raw-count signal: raw integer shown as-is, no scaled value. */
    private static void raw(int type, String key, String display) {
        SIGNALS.put(type + "/" + key, new SignalInfo(display, 1.0, 0.0, "", null));
    }
    /** Scaled physical value: display = raw × factor + offset, with a unit. */
    private static void scaled(int type, String key, String display,
                               double factor, double offset, String unit) {
        SIGNALS.put(type + "/" + key, new SignalInfo(display, factor, offset, unit, null));
    }
    /** Unit-only signal (factor 1, but carries a physical unit, e.g. rpm / km/h). */
    private static void unit(int type, String key, String display, String unit) {
        SIGNALS.put(type + "/" + key, new SignalInfo(display, 1.0, 0.0, unit, null));
    }
    /** Enumerated signal: raw integer indexes into {@code labels}. */
    private static void en(int type, String key, String display, String[] labels) {
        SIGNALS.put(type + "/" + key, new SignalInfo(display, 1.0, 0.0, "", labels));
    }

    static {
        // =====================================================================
        // FCAN — powertrain / chassis / meter (type code == real Honda 11-bit id)
        // =====================================================================

        // 344 / 0x158 — transmission / vehicle speed
        frame(344, "ENGINE_DATA");
        scaled(344, "EAT_TRANS_SPEED_VNC", "Transmission speed (VNC)", 0.01, 0.0, "km/h");
        scaled(344, "EAT_TRANS_SPEED",     "Transmission speed",       0.01, 0.0, "km/h");
        raw(344, "EAT_WARN_LAMP_STATUS",   "AT warning lamp");
        raw(344, "EAT_ALIVE_COUNTER_158",  "Alive counter");
        raw(344, "EAT_CHECKSUM_158",       "Checksum");

        // 380 / 0x17C — powertrain: rpm, pedal, brake switches
        frame(380, "POWERTRAIN_DATA");
        unit(380, "ENG_ENG_SPEED",              "Engine speed", "rpm");
        raw(380, "ENG_DRIVER_ACPEDAL_POSITION", "Accelerator pedal (0-255)");
        raw(380, "ENG_ENG_FAILCODE_MIL_INT",    "Check-engine (MIL)");
        raw(380, "ENG_SW_STATUS_BRAKE_NC",      "Brake switch (N/C, pressed)");
        raw(380, "ENG_SW_STATUS_BRAKE_NO",      "Brake switch (N/O)");
        raw(380, "ENG_IS_PROGRESS",             "Idle-stop in progress");
        raw(380, "ENG_IS_PRE_PROGRESS",         "Idle-stop pre-progress");
        raw(380, "ENG_PGMFI_LAMP_METER_INT",    "PGM-FI lamp");
        raw(380, "ENG_ALIVE_COUNTER_17C",       "Alive counter");
        raw(380, "ENG_CHECKSUM_17C",            "Checksum");

        // 401 / 0x191 — CVT gearbox / shift position
        frame(401, "GEARBOX_CVT");
        raw(401, "CVT_GEAR_POSITION_IND_CVT", "Gear position indicator");
        raw(401, "CVT_RATIO_INFO_191",        "CVT ratio");
        raw(401, "CVT_TRANS_MIL",             "Transmission MIL");
        raw(401, "CVT_SHFT_IN_PARKING",       "Shift gate: P");
        raw(401, "CVT_SHFT_IN_REVERSE",       "Shift gate: R");
        raw(401, "CVT_SHFT_IN_NEUTRAL",       "Shift gate: N");
        raw(401, "CVT_SHFT_IN_D",             "Shift gate: D");
        raw(401, "CVT_SHFT_IN_L",             "Shift gate: L");
        raw(401, "CVT_SHFT_IN_S",             "Shift gate: S");
        raw(401, "CVT_ALIVE_COUNTER_191",     "Alive counter");
        raw(401, "CVT_CHECKSUM_191",          "Checksum");

        // 420 / 0x1A4 — VSA: brake pressure, ABS/VSA/TCS flags
        frame(420, "VSA_STATUS");
        scaled(420, "VSA_MASTER_CYLINDER_PRESSURE", "Master-cylinder pressure",
                0.015625, -1.609375, "bar");
        raw(420, "VSA_ABS_EBD_ACT",          "ABS/EBD active");
        raw(420, "VSA_VSA_TCS_ACT",          "VSA/TCS active (computer braking)");
        raw(420, "VSA_WARN_STATUS_ABS",      "ABS warning");
        raw(420, "VSA_WARN_STATUS_BRAKE",    "Brake warning");
        raw(420, "VSA_WARN_STATUS_VSA",      "VSA warning (ESP off)");
        raw(420, "VSA_WARN_STATUS_DWS",      "Deflation warning (DWS)");
        raw(420, "VSA_WARN_STATUS_PUNCTURE", "Puncture warning");
        raw(420, "VSA_MID_REQUEST_VSA",      "VSA MID request");
        raw(420, "VSA_ALIVE_COUNTER_1A4",    "Alive counter");
        raw(420, "VSA_CHECKSUM_1A4",         "Checksum");

        // 427 / 0x1AB — EPS MID request
        frame(427, "EPS_STATUS");
        raw(427, "EPS_MID_REQ",            "EPS MID request");
        raw(427, "EPS_ALIVE_COUNTER_1AB",  "Alive counter");
        raw(427, "EPS_CHECKSUM_1AB",       "Checksum");

        // 460 / 0x1CC — ADS warn lamp (not equipped on this car → -1)
        frame(460, "ADS_STATUS");
        raw(460, "ADS_WARN_LAMP_STATUS",   "ADS warning lamp");
        raw(460, "ADS_ALIVE_COUNTER_1CC",  "Alive counter");
        raw(460, "ADS_CHECKSUM_1CC",       "Checksum");

        // 777 / 0x309 — meter display speed
        frame(777, "METER_DISPLAY_SPEED");
        scaled(777, "METER_DISPLAY_SPEED", "Speedometer display", 0.01, 0.0, "km/h");

        // 806 / 0x326 — meter: fuel, turn signals, park brake, brake fluid
        frame(806, "METER_FUEL_TURN");
        raw(806, "METER_FUEL_LEVEL",          "Fuel level (segments)");
        raw(806, "METER_FUEL_LEVEL_RAW",      "Fuel level (raw)");
        raw(806, "METER_SW_STATUS_BRAKE_FLUID","Brake fluid low");
        raw(806, "METER_SW_STATUS_PARK_BRAKE", "Parking brake");
        raw(806, "METER_TURN_SIGNAL_L_TURN",  "Left turn signal");
        raw(806, "METER_TURN_SIGNAL_R_TURN",  "Right turn signal");
        raw(806, "METER_ALIVE_COUNTER_326",   "Alive counter");
        raw(806, "METER_CHECKSUM_326",        "Checksum");

        // 829 / 0x33D — LKAS unit status
        frame(829, "LKAS_STATUS");
        raw(829, "LKAS_STATUS_ACTIVE_33D",   "LKAS active");
        raw(829, "LKAS_STATUS_FAILED_33D",   "LKAS failed");
        raw(829, "LKAS_STATUS_ADJ_MODE_33D", "LKAS adjust mode");
        raw(829, "LKAS_ALIVE_COUNTER_33D",   "Alive counter");
        raw(829, "LKAS_CHECKSUM_33D",        "Checksum");

        // 884 / 0x374 — meter MID: seatbelt, park brake, wipers
        frame(884, "METER_MID");
        raw(884, "METER_MID_INFO",           "MID info");
        raw(884, "METER_SEAT_BELT_WRN_RMD",  "Seatbelt warning (1=unlatched)");
        raw(884, "METER_PARK_BRAKE_WRN_RMD", "Park-brake warning");
        raw(884, "METER_WIPER_STATUS",       "Wiper status");
        raw(884, "METER_AUTOWIP_STATUS",     "Auto-wiper status");
        raw(884, "METER_ALIVE_COUNTER_374",  "Alive counter");
        raw(884, "METER_CHECKSUM_374",       "Checksum");

        // 1036 / 0x40C — engine freeze-frame diagnostic
        frame(1036, "ENGINE_FREEZE");
        raw(1036, "ENG_FREEZE_LEVEL",           "Freeze-frame level");
        raw(1036, "ENG_FREEZE_SYNCHRO_COUNTER", "Freeze sync counter");
        raw(1036, "ENG_DATA_COUNTER",           "Data counter");
        raw(1036, "ENG_DATA_BYTE1",             "Freeze data byte 1");
        raw(1036, "ENG_DATA_BYTE2",             "Freeze data byte 2");
        raw(1036, "ENG_DATA_BYTE3",             "Freeze data byte 3");
        raw(1036, "ENG_DATA_BYTE4",             "Freeze data byte 4");
        raw(1036, "ENG_DATA_BYTE5",             "Freeze data byte 5");
        raw(1036, "ENG_DATA_BYTE6",             "Freeze data byte 6");
        raw(1036, "ENG_UN_REGISTERING",         "Unregistering");
        raw(1036, "ENG_EEPROM_FAIL",            "EEPROM fail");
        raw(1036, "ENG_ALIVE_COUNTER_40C",      "Alive counter");
        raw(1036, "ENG_CHECKSUM_40C",           "Checksum");

        // =====================================================================
        // BCAN — body / HMI (type code == 32-bit Honda/Mitsubishi body-CAN id)
        // =====================================================================

        // Headlight-switch (turn signals) — ICU / BCM aliases
        frame(184053776, "HLSW_ICU");
        frame(184053784, "HLSW_BCM");
        raw(184053776, "C_TURNL", "Left turn (headlight sw)");
        raw(184053776, "C_TURNR", "Right turn (headlight sw)");
        raw(184053784, "C_TURNL", "Left turn (headlight sw)");
        raw(184053784, "C_TURNR", "Right turn (headlight sw)");

        // MICU / BCM — ignition, reverse light, one-touch turn — ICU / BCM aliases
        frame(318246928, "MICU_ICU");
        frame(318246936, "MICU_BCM");
        for (int id : new int[]{318246928, 318246936}) {
            raw(id, "C_IG1",             "Ignition IG1");
            raw(id, "C_BACKLTSW",        "Reverse-light switch");
            raw(id, "C_NGST_CONNECTED",  "NGST connected");
            raw(id, "C_TURN_L_ONETOUCH", "One-touch left turn");
            raw(id, "C_TURN_R_ONETOUCH", "One-touch right turn");
        }

        // VSPNE — speed / engine speed
        frame(318263376, "VSPNE");
        unit(318263376, "C_VSP", "Vehicle speed", "km/h");
        unit(318263376, "C_NE",  "Engine speed",  "rpm");
        raw(318263376, "C_ACGL", "A/C compressor signal");

        // AT — transmission / gear position / parking brake
        frame(318263632, "AT");
        raw(318263632, "C_ATP", "Shift position P");
        raw(318263632, "C_ATR", "Shift position R");
        raw(318263632, "C_ATN", "Shift position N");
        raw(318263632, "C_ATD", "Shift position D");
        raw(318263632, "C_AT1", "Gear 1");
        raw(318263632, "C_AT2", "Gear 2");
        raw(318263632, "C_AT3", "Gear 3");
        raw(318263632, "C_ATMPRESS",  "ATF pressure");
        raw(318263632, "C_IG1METER",  "Ignition IG1 (meter)");
        raw(318263632, "C_PBRAKE",    "Parking brake");
        en(318263632, "C_TRANSMISSION", "Transmission type", TRANSMISSION);

        // ILLUMI — illumination / headlight state
        frame(318264400, "ILLUMI");
        raw(318264400, "C_ILSTEP",                "Illumination step");
        raw(318264400, "C_ILSTEPMAX",             "Illumination step max");
        raw(318264400, "C_ILSTEP_DAYTIME_MEMORY", "Daytime illum memory");
        raw(318264400, "C_METER_ILL_STATUS",      "Meter illum (headlights on)");
        raw(318264400, "C_METER_ILL_STEP",        "Meter illum step");
        raw(318264400, "C_METER_ILL_CANCEL",      "Meter illum cancel");
        raw(318264400, "C_METER_WELCOMESTATE",    "Meter welcome state");
        raw(318264400, "C_ILCANCEL",              "Illumination cancel");
        raw(318264400, "C_ACILL",                 "A/C panel illum");
        raw(318264400, "C_THEATER",               "Theater dimming");
        raw(318264400, "C_DAYTIME_ILCON",         "Daytime illum control");
        raw(318264400, "C_WELCOMESTATUS",         "Welcome status");

        // PARKSENS — 6-zone park distances (not equipped → -1)
        frame(318285722, "PARKSENS");
        raw(318285722, "C_DIST_FRONT_LEFT",   "Front-left distance");
        raw(318285722, "C_DIST_FRONT_CENTER", "Front-center distance");
        raw(318285722, "C_DIST_FRONT_RIGHT",  "Front-right distance");
        raw(318285722, "C_DIST_REAR_LEFT",    "Rear-left distance");
        raw(318285722, "C_DIST_REAR_CENTER",  "Rear-center distance");
        raw(318285722, "C_DIST_REAR_RIGHT",   "Rear-right distance");
        raw(318285722, "C_SOUND_FRONT",       "Front park sound");
        raw(318285722, "C_SOUND_REAR",        "Rear park sound");
        raw(318285722, "C_SYSTEM_STATUS",     "Park system status");
        raw(318285722, "C_APPOBJ_DISP_ID",    "Object display id");

        // PARKSENS_TWO — 8-zone park distances (not equipped → -1)
        frame(318309018, "PARKSENS_TWO");
        raw(318309018, "C_DIST_FRONT_LEFT",    "Front-left distance");
        raw(318309018, "C_DIST_FRONT_C_LEFT",  "Front center-left distance");
        raw(318309018, "C_DIST_FRONT_C_RIGHT", "Front center-right distance");
        raw(318309018, "C_DIST_FRONT_RIGHT",   "Front-right distance");
        raw(318309018, "C_DIST_REAR_LEFT",     "Rear-left distance");
        raw(318309018, "C_DIST_REAR_C_LEFT",   "Rear center-left distance");
        raw(318309018, "C_DIST_REAR_C_RIGHT",  "Rear center-right distance");
        raw(318309018, "C_DIST_REAR_RIGHT",    "Rear-right distance");
        raw(318309018, "C_SYSTEM_STATUS",      "Park system status");

        // MAINTENANCE — oil life / service interval
        frame(318334800, "MAINTENANCE");
        raw(318334800, "C_MAINT_OIL_LIFE",  "Oil life (0-15)");
        raw(318334800, "C_MAINT_DATA",      "Distance to service");
        en(318334800, "C_MAINT_UNIT",       "Service distance unit", DIST_UNIT);
        raw(318334800, "C_MAINT_INFO",      "Maintenance info");
        raw(318334800, "C_MAINT_RESULT",    "Maintenance result");
        raw(318334800, "C_MAINT_DATE_UNIT", "Date unit");
        raw(318334800, "C_EW_MAINT",        "Extended-warranty maint");
        raw(318334800, "C_I_CAR_CONDITION_MAINT",     "Car condition");
        raw(318334800, "C_I_DISPLAY_CONDITION_MAINT", "Display condition");
        for (String item : new String[]{"0","1","2","3","4","5","6","7","8","9","A","B"}) {
            raw(318334800, "C_MAINT_ITEM_" + item + "_APPLIED", "Maint item " + item + " applied");
            raw(318334800, "C_MAINT_SUBMAINT_" + item,          "Sub-maint " + item);
        }

        // MET_CUSTOM — driver-assist HMI flags
        frame(318336336, "MET_CUSTOM");
        raw(318336336, "C_MET_CUSTOM_ACC_BUZZER_STATUS", "A/C buzzer status");
        en(318336336, "C_MET_CUSTOM_ACC_SPEED_UNIT",     "Speed unit (A/C)", SPEED_UNIT);
        en(318336336, "C_MET_CUSTOM_CC_ASL_SPEED_UNIT",  "Speed unit (ASL)", SPEED_UNIT);
        raw(318336336, "C_MET_CUSTOM_CMBS_DISTANCE",     "CMBS distance");
        raw(318336336, "C_MET_CUSTOM_FCW_DISTANCE",      "FCW distance");
        raw(318336336, "C_MET_CUSTOM_LKAS_BUZZER_STATUS","LKAS buzzer status");
        raw(318336336, "C_MET_CUSTOM_RDM_STATUS",        "Road-departure mitigation");
        raw(318336336, "C_MET_CUSTOM_REV_MATCH_STATUS",  "Rev-match status");
        raw(318336336, "C_MET_CUSTOM_IACC_SETUP_STATUS", "i-ACC setup status");
        raw(318336336, "C_MET_CUSTOM_DWS_INIT_REQUEST",  "DWS init request");
        raw(318336336, "C_MET_CUSTOM_EPT1_INFO_STATUS",  "EPT1 info status");
        raw(318336336, "C_MET_CUSTOM_SIF_STATUS",        "SIF status");
        raw(318336336, "C_MET_CUSTOM_SS30_OFF",          "Stop&Start 30 off");
        raw(318336336, "C_MET_CUSTOM_AFP",               "AFP");
        raw(318336336, "C_MET_CUSTOM_FCANDAAS_STATUS",   "FCAN DAAS status");

        // STEERING — steering angle (signed, ×0.1 deg)
        frame(318337360, "STEERING");
        scaled(318337360, "C_STR_ANGLE", "Steering angle", 0.1, 0.0, "deg");
        raw(318337360, "C_STR_STATE_OK",         "Sensor OK");
        raw(318337360, "C_FAIL_STEERING_SENSOR", "Sensor fault");
        raw(318337360, "C_STR_COMP_CAL",         "Compensation calibrated");
        raw(318337360, "C_STR_COMP_TRIM",        "Compensation trimmed");
        raw(318337360, "C_VSA_INHBIT_STR_SENSOR","VSA inhibit sensor");

        // VINNO — VIN bytes (multi-frame ASCII assembly)
        frame(385376848, "VINNO");
        raw(385376848, "C_VINDATACOUNT", "VIN data count");
        raw(385376848, "C_VINBYTE2", "VIN byte 2 (ASCII)");
        raw(385376848, "C_VINBYTE3", "VIN byte 3 (ASCII)");
        raw(385376848, "C_VINBYTE4", "VIN byte 4 (ASCII)");
        raw(385376848, "C_VINBYTE5", "VIN byte 5 (ASCII)");
        raw(385376848, "C_VINBYTE6", "VIN byte 6 (ASCII)");
        raw(385376848, "C_VINBYTE7", "VIN byte 7 (ASCII)");
        raw(385376848, "C_UNREGISTERING", "Unregistering");
        raw(385376848, "C_FIEEPROMFAIL",  "EEPROM fail");

        // FOB (delivered by default; low nibble = source ECU)
        frame(318256152, "FOB_ID_BCM");
        raw(318256152, "C_FOBID", "Key-fob id");
    }

    private SignalCatalog() {}
}
