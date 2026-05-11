package com.example.clusterapp.can;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Compile-time CAN signal definitions for the Honda Civic (2016-2017).
 *
 * Sources (all merged — bus assignment comments indicate which physical bus):
 *   honda_civic_touring_2016_can.dbc      — Touring (Nidec EPS, CVT)
 *   honda_civic_hatchback_ex_2017_can.dbc — Hatchback (Bosch EPS/radar, CVT)
 *   _honda_common.dbc                     — shared powertrain/chassis
 *   _gearbox_common.dbc                   — CVT and automatic gearbox
 *   _steering_sensors_a.dbc               — EPS steering column sensor
 *   _steering_control_a.dbc               — Nidec EPS steering control command
 *   _bosch_2018.dbc                       — Bosch EPS/VSA additions (Hatchback)
 *   _bosch_radar_acc.dbc                  — Bosch radar ACC (Hatchback)
 *   _nidec_common.dbc                     — Nidec shared (standstill, brake, radar HUD)
 *   _nidec_scm_group_b.dbc                — Nidec SCM buttons/feedback
 *   _lkas_hud_5byte.dbc                   — LKAS HUD 5-byte variant (Touring)
 *
 * DBC signal format: name : startBit|length@byteOrder sign (factor,offset) [min|max] "unit"
 *   byteOrder: 0=Motorola/big-endian (startBit=MSB), 1=Intel/little-endian (startBit=LSB)
 *   sign: +=unsigned, -=signed
 * Physical value = raw_value * factor + offset
 *
 * Where two DBC files define the same message ID for different hardware variants
 * (Nidec vs Bosch), both are included with a suffix to distinguish them.
 *
 * Note: the Mitsubishi middleware pre-decodes raw frames and only delivers integer Bundle
 * values to app-level listeners — raw bytes are NOT forwarded.  The CanDecoder can decode
 * these definitions if raw frames become available (e.g. via a future direct CAN socket).
 * Currently only a subset of these messages is accessible via the middleware listeners;
 * see VehicleDataSource.mapFcanBundle / mapBcanBundle for the live signal mapping.
 */
public final class CanDefinitions {

    // =========================================================================
    // FCAN — powertrain and chassis (500 kbps)
    // =========================================================================

    public static final List<CanMessage> FCAN_MESSAGES = Arrays.asList(

        // BO_ 145 KINEMATICS_ALT: 8 XXX  (_honda_common.dbc)
        new CanMessage(145, "KINEMATICS_ALT", Arrays.asList(
            new CanSignal("LAT_ACCEL",  7, 10, true, false,  0.02, -512.0, -20.0, 20.0, "m/s2", "EON"),
            new CanSignal("COUNTER",   61,  2, true, false,  1.0,    0.0,   0.0,  3.0, "",     "EON"),
            new CanSignal("CHECKSUM",  59,  4, true, false,  1.0,    0.0,   0.0, 15.0, "",     "EON")
        )),

        // BO_ 148 KINEMATICS: 8 XXX  (_honda_common.dbc)
        new CanMessage(148, "KINEMATICS", Arrays.asList(
            new CanSignal("LAT_ACCEL",   7, 10, true, false,  0.02, -512.0, -20.0,  20.0, "m/s2", "EON"),
            new CanSignal("LONG_ACCEL", 24,  9, true,  true, -0.02,    0.0, -20.0,  20.0, "m/s2", "EON"),
            new CanSignal("COUNTER",    61,  2, true, false,  1.0,     0.0,   0.0,   3.0, "",     "EON"),
            new CanSignal("CHECKSUM",   59,  4, true, false,  1.0,     0.0,   0.0,   3.0, "",     "EON")
        )),

        // BO_ 228 STEERING_CONTROL: 5 ADAS  (_steering_control_a.dbc — Nidec EPS, Touring 2016)
        new CanMessage(228, "STEERING_CONTROL", Arrays.asList(
            new CanSignal("STEER_TORQUE",         7, 16, true,  true,  1.0, 0.0, -3840.0, 3840.0, "", "EPS"),
            new CanSignal("STEER_TORQUE_REQUEST", 23,  1, true, false,  1.0, 0.0,    0.0,    1.0, "", "EPS"),
            new CanSignal("SET_ME_X00",           22,  7, true, false,  1.0, 0.0,    0.0,  127.0, "", "EPS"),
            new CanSignal("SET_ME_X00_2",         31,  8, true, false,  1.0, 0.0,    0.0,    0.0, "", "EPS"),
            new CanSignal("COUNTER",              37,  2, true, false,  1.0, 0.0,    0.0,    3.0, "", "EPS"),
            new CanSignal("CHECKSUM",             35,  4, true, false,  1.0, 0.0,    0.0,   15.0, "", "EPS")
        )),

        // BO_ 228 STEERING_CONTROL_BOSCH: 5 EON  (_bosch_2018.dbc — Bosch EPS, Hatchback 2017)
        new CanMessage(228, "STEERING_CONTROL_BOSCH", Arrays.asList(
            new CanSignal("STEER_TORQUE",          7, 16, true,  true,  1.0, 0.0, -4096.0, 4096.0, "", "EPS"),
            new CanSignal("DRIVER_OVERRIDE",      17,  1, true, false,  1.0, 0.0,    0.0,    1.0, "", "XXX"),
            new CanSignal("STEER_TORQUE_REQUEST", 23,  1, true, false,  1.0, 0.0,    0.0,    1.0, "", "EPS"),
            new CanSignal("CONTROL_STATE",        26,  3, true, false,  1.0, 0.0,    0.0,    7.0, "", "XXX"),
            new CanSignal("CHECKSUM",             35,  4, true, false,  1.0, 0.0,    0.0,   15.0, "", "EPS"),
            new CanSignal("COUNTER",              37,  2, true, false,  1.0, 0.0,    0.0,    3.0, "", "EPS"),
            new CanSignal("STEER_DOWN_TO_ZERO",   38,  1, true, false,  1.0, 0.0,    0.0,    1.0, "", "EPS"),
            new CanSignal("HAPTIC_WARNING",       39,  1, true, false,  1.0, 0.0,    0.0,    1.0, "", "XXX")
        )),

        // BO_ 229 BOSCH_SUPPLEMENTAL_1: 8 XXX  (_bosch_2018.dbc)
        new CanMessage(229, "BOSCH_SUPPLEMENTAL_1", Arrays.asList(
            new CanSignal("SET_ME_X04",  0,  8, false, false, 1.0, 0.0,   0.0, 255.0, "", "XXX"),
            new CanSignal("SET_ME_X00",  8,  8, false, false, 1.0, 0.0,   0.0, 255.0, "", "XXX"),
            new CanSignal("SET_ME_X80", 16,  8, false, false, 1.0, 0.0,   0.0, 255.0, "", "XXX"),
            new CanSignal("SET_ME_X10", 24,  8, false, false, 1.0, 0.0,   0.0, 255.0, "", "XXX"),
            new CanSignal("COUNTER",    61,  2, true,  false, 1.0, 0.0,   0.0,   3.0, "", "XXX"),
            new CanSignal("CHECKSUM",   59,  4, true,  false, 1.0, 0.0,   0.0,  15.0, "", "XXX")
        )),

        // BO_ 232 BRAKE_HOLD: 7 XXX  (_bosch_2018.dbc)
        new CanMessage(232, "BRAKE_HOLD", Arrays.asList(
            new CanSignal("XMISSION_SPEED",          7, 14, true,  true,  1.0, 0.0, 0.0, 0.0, "", "XXX"),
            new CanSignal("COMPUTER_BRAKE",         39, 16, true, false,  1.0, 0.0, 0.0, 0.0, "", "XXX"),
            new CanSignal("COMPUTER_BRAKE_REQUEST", 29,  1, true, false,  1.0, 0.0, 0.0, 0.0, "", "XXX"),
            new CanSignal("COUNTER",                53,  2, true, false,  1.0, 0.0, 0.0, 3.0, "", "XXX"),
            new CanSignal("CHECKSUM",               51,  4, true, false,  1.0, 0.0, 0.0,15.0, "", "XXX")
        )),

        // BO_ 304 GAS_PEDAL_2: 8 PCM  (_honda_common.dbc — torque estimates + pedal)
        new CanMessage(304, "GAS_PEDAL_2", Arrays.asList(
            new CanSignal("ENGINE_TORQUE_ESTIMATE",  7, 16, true,  true, 1.0, 0.0, -1000.0, 1000.0, "Nm", "EON"),
            new CanSignal("ENGINE_TORQUE_REQUEST",  23, 16, true,  true, 1.0, 0.0, -1000.0, 1000.0, "Nm", "EON"),
            new CanSignal("CAR_GAS",                39,  8, true, false, 1.0, 0.0,     0.0,  255.0, "",   "EON"),
            new CanSignal("COUNTER",                61,  2, true, false, 1.0, 0.0,     0.0,    3.0, "",   "EON"),
            new CanSignal("CHECKSUM",               59,  4, true, false, 1.0, 0.0,     0.0,   15.0, "",   "EON")
        )),

        // BO_ 316 GAS_PEDAL: 8 PCM  (_honda_common.dbc — Nidec variant)
        new CanMessage(316, "GAS_PEDAL", Arrays.asList(
            new CanSignal("CAR_GAS",  39, 8, true, false, 1.0, 0.0, 0.0, 255.0, "", "EON"),
            new CanSignal("COUNTER",  61, 2, true, false, 1.0, 0.0, 0.0,   3.0, "", "EON"),
            new CanSignal("CHECKSUM", 59, 4, true, false, 1.0, 0.0, 0.0,  15.0, "", "EON")
        )),

        // BO_ 330 STEERING_SENSORS: 8 EPS  (_steering_sensors_a.dbc)
        new CanMessage(330, "STEERING_SENSORS", Arrays.asList(
            new CanSignal("STEER_ANGLE",              7, 16, true, true,  -0.1, 0.0, -500.0,  500.0, "deg",   "EON"),
            new CanSignal("STEER_ANGLE_RATE",        23, 16, true, true,  -1.0, 0.0,-3000.0, 3000.0, "deg/s", "EON"),
            new CanSignal("STEER_SENSOR_STATUS_1",   34,  1, true, false,  1.0, 0.0,    0.0,    1.0, "",      "EON"),
            new CanSignal("STEER_SENSOR_STATUS_2",   33,  1, true, false,  1.0, 0.0,    0.0,    1.0, "",      "EON"),
            new CanSignal("STEER_SENSOR_STATUS_3",   32,  1, true, false,  1.0, 0.0,    0.0,    1.0, "",      "EON"),
            new CanSignal("STEER_WHEEL_ANGLE",       47, 16, true, true,  -0.1, 0.0, -500.0,  500.0, "deg",   "EON"),
            new CanSignal("COUNTER",                 61,  2, true, false,  1.0, 0.0,    0.0,    3.0, "",      "EON"),
            new CanSignal("CHECKSUM",                59,  4, true, false,  1.0, 0.0,    0.0,   15.0, "",      "EON")
        )),

        // BO_ 344 ENGINE_DATA: 8 PCM  (_honda_common.dbc)
        new CanMessage(344, "ENGINE_DATA", Arrays.asList(
            new CanSignal("XMISSION_SPEED",   7, 16, true, false, 0.01, 0.0,    0.0, 250.0,  "kph", "EON"),
            new CanSignal("ENGINE_RPM",       23, 16, true, false, 1.0,  0.0,    0.0,15000.0, "rpm", "EON"),
            new CanSignal("XMISSION_SPEED2",  39, 16, true, false, 0.01, 0.0,    0.0, 250.0,  "kph", "EON"),
            new CanSignal("ODOMETER",         55,  8, true, false,10.0,  0.0,    0.0,2550.0,  "m",   "XXX"),
            new CanSignal("COUNTER",          61,  2, true, false, 1.0,  0.0,    0.0,   3.0,  "",    "EON"),
            new CanSignal("CHECKSUM",         59,  4, true, false, 1.0,  0.0,    0.0,  15.0,  "",    "EON")
        )),

        // BO_ 380 POWERTRAIN_DATA: 8 PCM  (_honda_common.dbc)
        new CanMessage(380, "POWERTRAIN_DATA", Arrays.asList(
            new CanSignal("PEDAL_GAS",    7,  8, true, false, 1.0, 0.0, 0.0, 255.0, "",    "EON"),
            new CanSignal("ENGINE_RPM",  23, 16, true, false, 1.0, 0.0, 0.0,15000.0,"rpm", "EON"),
            new CanSignal("GAS_PRESSED", 39,  1, true, false, 1.0, 0.0, 0.0,   1.0, "",    "EON"),
            new CanSignal("ACC_STATUS",  38,  1, true, false, 1.0, 0.0, 0.0,   1.0, "",    "EON"),
            new CanSignal("BOH_17C",     37,  5, true, false, 1.0, 0.0, 0.0,   1.0, "",    "EON"),
            new CanSignal("BRAKE_SWITCH",32,  1, true, false, 1.0, 0.0, 0.0,   1.0, "",    "EON"),
            new CanSignal("BOH2_17C",    47, 10, true, false, 1.0, 0.0, 0.0,   1.0, "",    "EON"),
            new CanSignal("BRAKE_PRESSED",53, 1, true, false, 1.0, 0.0, 0.0,   1.0, "",    "EON"),
            new CanSignal("BOH3_17C",    52,  5, true, false, 1.0, 0.0, 0.0,   1.0, "",    "EON"),
            new CanSignal("COUNTER",     61,  2, true, false, 1.0, 0.0, 0.0,   3.0, "",    "EON"),
            new CanSignal("CHECKSUM",    59,  4, true, false, 1.0, 0.0, 0.0,  15.0, "",    "EON")
        )),

        // BO_ 388 HYBRID_BRAKE_ERROR: 8 XXX  (_honda_common.dbc)
        new CanMessage(388, "HYBRID_BRAKE_ERROR", Arrays.asList(
            new CanSignal("BRAKE_ERROR_1", 32, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "EON"),
            new CanSignal("BRAKE_ERROR_2", 34, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "EON"),
            new CanSignal("COUNTER",       61, 2, true, false, 1.0, 0.0, 0.0, 3.0, "", "EON"),
            new CanSignal("CHECKSUM",      59, 4, true, false, 1.0, 0.0, 0.0, 3.0, "", "EON")
        )),

        // BO_ 399 STEER_STATUS: 7 EPS  (_bosch_2018.dbc / _steering_control_a.dbc — Hatchback)
        new CanMessage(399, "STEER_STATUS", Arrays.asList(
            new CanSignal("STEER_TORQUE_SENSOR",   7, 16, true, true,  -1.0, 0.0,-31000.0,31000.0,"tbd","EON"),
            new CanSignal("STEER_ANGLE_RATE",      23, 16, true, true,  -0.1, 0.0,-31000.0,31000.0,"deg/s","EON"),
            new CanSignal("STEER_STATUS",          39,  4, true, false,  1.0, 0.0,    0.0,   15.0, "",   "EON"),
            new CanSignal("STEER_CONTROL_ACTIVE",  35,  1, true, false,  1.0, 0.0,    0.0,    1.0, "",   "EON"),
            new CanSignal("STEER_CONFIG_INDEX",    43,  4, true, false,  1.0, 0.0,    0.0,   15.0, "",   "EON"),
            new CanSignal("COUNTER",               53,  2, true, false,  1.0, 0.0,    0.0,    3.0, "",   "EON"),
            new CanSignal("CHECKSUM",              51,  4, true, false,  1.0, 0.0,    0.0,   15.0, "",   "EON")
        )),

        // BO_ 401 GEARBOX_CVT: 8 PCM  (_gearbox_common.dbc)
        new CanMessage(401, "GEARBOX_CVT", Arrays.asList(
            new CanSignal("SELECTED_P",             0,  1, true, false, 1.0, 0.0, 0.0,  1.0, "", "XXX"),
            new CanSignal("SELECTED_R",             1,  1, true, false, 1.0, 0.0, 0.0,  1.0, "", "XXX"),
            new CanSignal("SELECTED_N",             2,  1, true, false, 1.0, 0.0, 0.0,  1.0, "", "XXX"),
            new CanSignal("SELECTED_D",             3,  1, true, false, 1.0, 0.0, 0.0,  1.0, "", "XXX"),
            new CanSignal("FORWARD_DRIVING_MODE",  23,  1, true, false, 1.0, 0.0, 0.0,  1.0, "", "XXX"),
            new CanSignal("CVT_UNKNOWN_1",         31,  8, true, false, 1.0, 0.0, 0.0,255.0, "", "XXX"),
            new CanSignal("CVT_UNKNOWN_2",         39,  8, true, false, 1.0, 0.0, 0.0,255.0, "", "XXX"),
            new CanSignal("GEAR_SHIFTER",          44,  5, true, false, 1.0, 0.0, 0.0, 31.0, "", "XXX"),
            new CanSignal("SHIFTER_POSITION_VALID",45,  1, true, false, 1.0, 0.0, 0.0,  1.0, "", "XXX"),
            new CanSignal("NOT_FORWARD_GEAR",      48,  1, true, false, 1.0, 0.0, 0.0,  1.0, "", "XXX"),
            new CanSignal("CVT_UNKNOWN_3",         53,  2, true, false, 1.0, 0.0, 0.0,  3.0, "", "XXX"),
            new CanSignal("CHECKSUM",              59,  4, true, false, 1.0, 0.0, 0.0, 15.0, "", "EON"),
            new CanSignal("COUNTER",               61,  2, true, false, 1.0, 0.0, 0.0,  3.0, "", "EON")
        )),

        // BO_ 419 GEARBOX_AUTO: 8 PCM  (_gearbox_common.dbc — hybrid / 10-speed AT)
        new CanMessage(419, "GEARBOX_AUTO", Arrays.asList(
            new CanSignal("TRANS_SHIFT_ACTIVITY",  7,  8, true, false, 1.0, 0.0, 0.0, 256.0, "", "EON"),
            new CanSignal("TRANS_TARGET_GEAR",    11,  4, true, false, 1.0, 0.0, 0.0,  15.0, "", "XXX"),
            new CanSignal("REGEN_STAGE_SELECTION",14,  3, true, false, 1.0, 0.0, 0.0,   7.0, "Stage", "XXX"),
            new CanSignal("REGEN_MEMORY",         16,  1, true, false, 1.0, 0.0, 0.0,   1.0, "", "XXX"),
            new CanSignal("FORWARD_DRIVING_MODE", 20,  1, true, false, 1.0, 0.0, 0.0,   1.0, "", "XXX"),
            new CanSignal("AUTO_UNKNOWN_1",       27,  2, true, false, 1.0, 0.0, 0.0,   3.0, "", "XXX"),
            new CanSignal("AUTO_UNKNOWN_2",       29,  1, true, false, 1.0, 0.0, 0.0,   1.0, "", "XXX"),
            new CanSignal("GEAR_SHIFTER",         35,  4, true, false, 1.0, 0.0, 0.0,  15.0, "", "EON"),
            new CanSignal("REGEN_UNKNOWN",        50,  2, true, false, 1.0, 0.0, 0.0,   3.0, "", "XXX"),
            new CanSignal("COUNTER",              61,  2, true, false, 1.0, 0.0, 0.0,   3.0, "", "EON"),
            new CanSignal("CHECKSUM",             59,  4, true, false, 1.0, 0.0, 0.0,   3.0, "", "EON"),
            new CanSignal("AUTO_UNKNOWN_3",       62,  1, true, false, 1.0, 0.0, 0.0,   1.0, "", "XXX")
        )),

        // BO_ 420 VSA_STATUS: 8 VSA  (_honda_common.dbc)
        new CanMessage(420, "VSA_STATUS", Arrays.asList(
            new CanSignal("USER_BRAKE",           7, 16, true, false, 0.015625, -1.609375, 0.0, 1000.0, "", "EON"),
            new CanSignal("COMPUTER_BRAKING",    23,  1, true, false, 1.0, 0.0, 0.0,    1.0, "", "EON"),
            new CanSignal("ESP_DISABLED",        28,  1, true, false, 1.0, 0.0, 0.0,    1.0, "", "EON"),
            new CanSignal("BRAKE_HOLD_RELATED",  52,  1, true, false, 1.0, 0.0, 0.0,    1.0, "", "XXX"),
            new CanSignal("BRAKE_HOLD_ACTIVE",   46,  1, true, false, 1.0, 0.0, 0.0,    1.0, "", "EON"),
            new CanSignal("BRAKE_HOLD_ENABLED",  45,  1, true, false, 1.0, 0.0, 0.0,    1.0, "", "EON"),
            new CanSignal("COUNTER",             61,  2, true, false, 1.0, 0.0, 0.0,    3.0, "", "EON"),
            new CanSignal("CHECKSUM",            59,  4, true, false, 1.0, 0.0, 0.0,   15.0, "", "EON")
        )),

        // BO_ 427 STEER_MOTOR_TORQUE: 3 EPS  (_honda_common.dbc)
        new CanMessage(427, "STEER_MOTOR_TORQUE", Arrays.asList(
            new CanSignal("UNKNOWN_TORQUE_STATE_BIT", 3, 1, true, false, 1.0, 0.0,   0.0,   1.0, "",  "XXX"),
            new CanSignal("CONFIG_VALID",      7, 1, true, false, 1.0, 0.0,   0.0,   1.0, "",  "EON"),
            new CanSignal("MOTOR_TORQUE",      1,10, true, false, 1.0, 0.0,   0.0, 256.0, "",  "EON"),
            new CanSignal("OUTPUT_DISABLED",  22, 1, true, false, 1.0, 0.0,   0.0,   1.0, "",  "EON"),
            new CanSignal("COUNTER",          21, 2, true, false, 1.0, 0.0,   0.0,   3.0, "",  "EON"),
            new CanSignal("CHECKSUM",         19, 4, true, false, 1.0, 0.0,   0.0,  15.0, "",  "EON")
        )),

        // BO_ 432 STANDSTILL: 7 VSA  (_nidec_common.dbc)
        new CanMessage(432, "STANDSTILL", Arrays.asList(
            new CanSignal("CONTROLLED_STANDSTILL", 0, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "EON"),
            new CanSignal("WHEELS_MOVING",        12, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "EON"),
            new CanSignal("BRAKE_ERROR_1",        11, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "EON"),
            new CanSignal("BRAKE_ERROR_2",         9, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "EON"),
            new CanSignal("COUNTER",              53, 2, true, false, 1.0, 0.0, 0.0, 3.0, "", "EON"),
            new CanSignal("CHECKSUM",             51, 4, true, false, 1.0, 0.0, 0.0, 3.0, "", "EON")
        )),

        // BO_ 446 BRAKE_MODULE: 3 VSA  (_bosch_2018.dbc — Hatchback)
        new CanMessage(446, "BRAKE_MODULE", Arrays.asList(
            new CanSignal("BRAKE_PRESSED", 4, 1, true, false, 1.0, 0.0, 0.0,  1.0, "", "XXX"),
            new CanSignal("CRUISE_FAULT", 22, 1, true, false, 1.0, 0.0, 0.0,  1.0, "", "XXX"),
            new CanSignal("COUNTER",      21, 2, true, false, 1.0, 0.0, 0.0,  3.0, "", "XXX"),
            new CanSignal("CHECKSUM",     19, 4, true, false, 1.0, 0.0, 0.0, 15.0, "", "XXX")
        )),

        // BO_ 450 EPB_STATUS: 8 XXX  (_honda_common.dbc)
        new CanMessage(450, "EPB_STATUS", Arrays.asList(
            new CanSignal("EPB_BRAKE_AND_PULL", 6, 1, true, false, 1.0, 0.0, 0.0,  1.0, "", "XXX"),
            new CanSignal("EPB_ACTIVE",          3, 1, true, false, 1.0, 0.0, 0.0,  1.0, "", "XXX"),
            new CanSignal("EPB_STATE",          29, 2, true, false, 1.0, 0.0, 0.0,  1.0, "", "XXX"),
            new CanSignal("CHECKSUM",           59, 4, true, false, 1.0, 0.0, 0.0, 15.0, "", "XXX"),
            new CanSignal("COUNTER",            61, 2, true, false, 1.0, 0.0, 0.0,  3.0, "", "XXX")
        )),

        // BO_ 464 WHEEL_SPEEDS: 8 VSA  (_honda_common.dbc)
        new CanMessage(464, "WHEEL_SPEEDS", Arrays.asList(
            new CanSignal("WHEEL_SPEED_FL",  7, 15, true, false, 0.01, 0.0, 0.0, 250.0, "kph", "EON"),
            new CanSignal("WHEEL_SPEED_FR",  8, 15, true, false, 0.01, 0.0, 0.0, 250.0, "kph", "EON"),
            new CanSignal("WHEEL_SPEED_RL", 25, 15, true, false, 0.01, 0.0, 0.0, 250.0, "kph", "EON"),
            new CanSignal("WHEEL_SPEED_RR", 42, 15, true, false, 0.01, 0.0, 0.0, 250.0, "kph", "EON"),
            new CanSignal("CHECKSUM",       59,  4, true, false, 1.0,  0.0, 0.0,   3.0, "",    "EON")
        )),

        // BO_ 479 ACC_CONTROL: 8 XXX  (_bosch_radar_acc.dbc context)
        new CanMessage(479, "ACC_CONTROL", Arrays.asList(
            new CanSignal("GAS_COMMAND",              7, 16, true,  true, 1.0,  0.0,  0.0,  0.0, "",     "XXX"),
            new CanSignal("ACC_ENABLED",             16,  1, true, false, 1.0,  0.0,  0.0,  1.0, "",     "XXX"),
            new CanSignal("ACC_FAULTED",             20,  1, true, false, 1.0,  0.0,  0.0,  1.0, "",     "XXX"),
            new CanSignal("CONTROL_ON",              23,  3, true, false, 1.0,  0.0,  0.0,  5.0, "",     "XXX"),
            new CanSignal("ACCEL_COMMAND",           31, 11, true,  true, 0.01, 0.0,  0.0,  0.0, "m/s2", "XXX"),
            new CanSignal("AEB_STATUS",              33,  1, true, false, 1.0,  0.0,  0.0,  1.0, "",     "XXX"),
            new CanSignal("BRAKE_REQUEST",           34,  1, true, false, 1.0,  0.0,  0.0,  1.0, "",     "XXX"),
            new CanSignal("STANDSTILL",              35,  1, true, false, 1.0,  0.0,  0.0,  1.0, "",     "XXX"),
            new CanSignal("STANDSTILL_RELEASE",      36,  1, true, false, 1.0,  0.0,  0.0,  1.0, "",     "XXX"),
            new CanSignal("MAYBE_DISENGAGE_COMMAND", 41,  2, true, false, 1.0,  0.0,  0.0,  3.0, "",     "XXX"),
            new CanSignal("AEB_PREPARE",             43,  1, true, false, 1.0,  0.0,  0.0,  1.0, "",     "XXX"),
            new CanSignal("AEB_BRAKING",             47,  1, true, false, 1.0,  0.0,  0.0,  1.0, "",     "XXX"),
            new CanSignal("MAYBE_DISENGAGE_ALERT",   54,  1, true, false, 1.0,  0.0,  0.0,  1.0, "",     "XXX"),
            new CanSignal("CHECKSUM",                59,  4, true, false, 1.0,  0.0,  0.0, 15.0, "",     "XXX"),
            new CanSignal("COUNTER",                 61,  2, true, false, 1.0,  0.0,  0.0,  3.0, "",     "XXX"),
            new CanSignal("BRAKE_LIGHTS",            62,  1, true, false, 1.0,  0.0,  0.0,  1.0, "",     "XXX")
        )),

        // BO_ 487 BRAKE_PRESSURE: 4 VSA  (_nidec_common.dbc)
        new CanMessage(487, "BRAKE_PRESSURE", Arrays.asList(
            new CanSignal("BRAKE_PRESSURE1",  7, 10, true, false, 0.015625, -103.0, 0.0, 1000.0, "", "EON"),
            new CanSignal("BRAKE_PRESSURE2",  9, 10, true, false, 0.015625, -103.0, 0.0, 1000.0, "", "EON"),
            new CanSignal("CHECKSUM",        27,  4, true, false, 1.0,         0.0, 0.0,   15.0, "", "EON"),
            new CanSignal("COUNTER",         29,  2, true, false, 1.0,         0.0, 0.0,    3.0, "", "EON")
        )),

        // BO_ 490 VEHICLE_DYNAMICS: 8 VSA  (_honda_common.dbc)
        new CanMessage(490, "VEHICLE_DYNAMICS", Arrays.asList(
            new CanSignal("LAT_ACCEL",   7, 16, true, true,  0.0015, 0.0, -20.0, 20.0, "m/s2", "EON"),
            new CanSignal("LONG_ACCEL", 23, 16, true, true,  0.0015, 0.0, -20.0, 20.0, "m/s2", "EON"),
            new CanSignal("COUNTER",    61,  2, true, false, 1.0,    0.0,   0.0,  3.0, "",     "EON"),
            new CanSignal("CHECKSUM",   59,  4, true, false, 1.0,    0.0,   0.0,  3.0, "",     "EON")
        )),

        // BO_ 493 HUD_SETTING: 5 XXX  (honda_civic_touring_2016_can.dbc)
        new CanMessage(493, "HUD_SETTING", Arrays.asList(
            new CanSignal("IMPERIAL_UNIT", 5, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "EON")
        )),

        // BO_ 495 ACC_CONTROL_ON: 8 XXX  (_bosch_radar_acc.dbc — Hatchback Bosch radar)
        new CanMessage(495, "ACC_CONTROL_ON", Arrays.asList(
            new CanSignal("CONTROL_ON",  7,  1, true, false, 1.0, 0.0,    0.0,    1.0, "", "XXX"),
            new CanSignal("SET_TO_3",    6,  7, true, false, 1.0, 0.0,    0.0, 4095.0, "", "XXX"),
            new CanSignal("SET_TO_FF",  15,  8, true, false, 1.0, 0.0,    0.0,  255.0, "", "XXX"),
            new CanSignal("ZEROS_BOH",  23,  8, true, false, 1.0, 0.0,    0.0,  255.0, "", "XXX"),
            new CanSignal("SET_TO_75",  31,  8, true, false, 1.0, 0.0,    0.0,  255.0, "", "XXX"),
            new CanSignal("SET_TO_30",  39,  8, true, false, 1.0, 0.0,    0.0,  255.0, "", "XXX"),
            new CanSignal("ZEROS_BOH2", 47, 16, true, false, 1.0, 0.0,    0.0,  255.0, "", "XXX"),
            new CanSignal("CHECKSUM",   59,  4, true, false, 1.0, 0.0,    0.0,   15.0, "", "XXX"),
            new CanSignal("COUNTER",    61,  2, true, false, 1.0, 0.0,    0.0,    3.0, "", "XXX")
        )),

        // BO_ 506 BRAKE_COMMAND: 8 ADAS  (_nidec_common.dbc — Nidec variant)
        new CanMessage(506, "BRAKE_COMMAND", Arrays.asList(
            new CanSignal("COMPUTER_BRAKE",           7, 10, true, false, 1.0, 0.0, 0.0,   1.0, "", "EBCM"),
            new CanSignal("BRAKE_PUMP_REQUEST",       8,  1, true, false, 1.0, 0.0, 0.0,   1.0, "", "EBCM"),
            new CanSignal("BRAKE_PUMP_REQUEST_HYBRID",11,  1, true, false, 1.0, 0.0, 0.0,   1.0, "", "EBCM"),
            new CanSignal("COMPUTER_BRAKE_REQUEST",  16,  1, true, false, 1.0, 0.0, 0.0,   1.0, "", "EBCM"),
            new CanSignal("CRUISE_CANCEL_CMD",       17,  1, true, false, 1.0, 0.0, 0.0,   1.0, "", "EBCM"),
            new CanSignal("CRUISE_FAULT_CMD",        18,  1, true, false, 1.0, 0.0, 0.0,   1.0, "", "EBCM"),
            new CanSignal("SET_ME_X00_2",            19,  1, true, false, 1.0, 0.0, 0.0,   1.0, "", "EBCM"),
            new CanSignal("CRUISE_OVERRIDE",         20,  1, true, false, 1.0, 0.0, 0.0,   1.0, "", "EBCM"),
            new CanSignal("SET_ME_X00",              23,  3, true, false, 1.0, 0.0, 0.0,   1.0, "", "EBCM"),
            new CanSignal("AEB_REQ_2",               26,  3, true, false, 1.0, 0.0, 0.0,   7.0, "", "XXX"),
            new CanSignal("AEB_REQ_1",               29,  1, true, false, 1.0, 0.0, 0.0,   1.0, "", "XXX"),
            new CanSignal("SET_ME_1",                31,  1, true, false, 1.0, 0.0, 0.0,   1.0, "", "EBCM"),
            new CanSignal("CRUISE_STATES",           38,  7, true, false, 1.0, 0.0, 0.0,   1.0, "", "EBCM"),
            new CanSignal("BRAKE_LIGHTS",            39,  1, true, false, 1.0, 0.0, 0.0,   1.0, "", "EBCM"),
            new CanSignal("AEB_STATUS",              41,  2, true, false, 1.0, 0.0, 0.0,   3.0, "", "XXX"),
            new CanSignal("FCW",                     43,  2, true, false, 1.0, 0.0, 0.0,   3.0, "", "EBCM"),
            new CanSignal("SET_ME_X00_3",            44,  1, true, false, 1.0, 0.0, 0.0,   1.0, "", "EBCM"),
            new CanSignal("CHIME",                   47,  3, true, false, 1.0, 0.0, 0.0,   7.0, "", "EBCM"),
            new CanSignal("COMPUTER_BRAKE_HYBRID",   55, 10, true, false, 1.0, 0.0, 0.0,   0.0, "", "EBCM"),
            new CanSignal("COUNTER",                 61,  2, true, false, 1.0, 0.0, 0.0,   3.0, "", "EBCM"),
            new CanSignal("CHECKSUM",                59,  4, true, false, 1.0, 0.0, 0.0,  15.0, "", "EBCM")
        )),

        // BO_ 506 LEGACY_BRAKE_COMMAND: 8 ADAS  (_bosch_2018.dbc — Bosch variant)
        new CanMessage(506, "LEGACY_BRAKE_COMMAND", Arrays.asList(
            new CanSignal("CHIME",    40, 8, false, false, 1.0, 0.0, 0.0, 255.0, "", "XXX"),
            new CanSignal("CHECKSUM", 59, 4, true,  false, 1.0, 0.0, 0.0,  15.0, "", "XXX"),
            new CanSignal("COUNTER",  61, 2, true,  false, 1.0, 0.0, 0.0,   3.0, "", "XXX")
        )),

        // BO_ 547 BRAKE_HOLD_HYBRID_ALT: 6 XXX  (_honda_common.dbc)
        new CanMessage(547, "BRAKE_HOLD_HYBRID_ALT", Arrays.asList(
            new CanSignal("BRAKE_HOLD_FAULT_BIT", 33, 1, true, false, 1.0, 0.0, 0.0,  1.0, "", "XXX"),
            new CanSignal("BRAKE_HOLD_ENABLED",   37, 2, true, false, 1.0, 0.0, 0.0,  7.0, "", "XXX"),
            new CanSignal("BRAKE_HOLD_ACTIVE",    38, 1, true, false, 1.0, 0.0, 0.0,  1.0, "", "XXX"),
            new CanSignal("CHECKSUM",             43, 4, true, false, 1.0, 0.0, 0.0, 15.0, "", "XXX"),
            new CanSignal("COUNTER",              45, 2, true, false, 1.0, 0.0, 0.0,  3.0, "", "XXX")
        )),

        // BO_ 576 LEFT_LANE_LINE_1: 8 CAM  (_bosch_2018.dbc)
        new CanMessage(576, "LEFT_LANE_LINE_1", Arrays.asList(
            new CanSignal("LINE_ANGLE",             7, 12, true,  false, 0.0005,  -1.024, 0.0, 1.0, "",      "XXX"),
            new CanSignal("LINE_OFFSET",           23, 12, true,  false, 0.004,   -8.192, 0.0, 1.0, "Meters","XXX"),
            new CanSignal("LINE_DISTANCE_VISIBLE", 39,  9, true,  false, 1.0,      0.0,   0.0, 1.0, "",      "XXX"),
            new CanSignal("LINE_PROBABILITY",      46,  6, true,  false, 0.015625, 0.0,   0.0, 1.0, "",      "XXX"),
            new CanSignal("FRAME_INDEX",            8,  4, false, false, 1.0,      0.0,   0.0,15.0, "",      "XXX"),
            new CanSignal("COUNTER",               61,  2, true,  false, 1.0,      0.0,   0.0, 1.0, "",      "XXX"),
            new CanSignal("CHECKSUM",              59,  4, true,  false, 1.0,      0.0,   0.0, 1.0, "",      "XXX")
        )),

        // BO_ 577 LEFT_LANE_LINE_2: 8 CAM  (_bosch_2018.dbc)
        new CanMessage(577, "LEFT_LANE_LINE_2", Arrays.asList(
            new CanSignal("FRAME_INDEX",           7,  4, true,  false, 1.0,       0.0,     0.0, 15.0, "",   "XXX"),
            new CanSignal("LINE_SOLID",           13,  1, true,  false, 1.0,       0.0,     0.0,  1.0, "",   "XXX"),
            new CanSignal("LINE_DASHED",          14,  1, true,  false, 1.0,       0.0,     0.0,  1.0, "",   "XXX"),
            new CanSignal("LINE_CURVATURE",       23, 12, true,  false, 0.00001, -0.02048,  0.0,  1.0, "",   "XXX"),
            new CanSignal("LINE_PARAMETER",       39, 12, true,  false, 1.0,       0.0,     0.0,  1.0, "",   "XXX"),
            new CanSignal("LINE_FAR_EDGE_POSITION",55, 8, true,  false, 1.0,    -128.0,     0.0,  1.0, "",   "XXX"),
            new CanSignal("COUNTER",              61,  2, true,  false, 1.0,       0.0,     0.0,  1.0, "",   "XXX"),
            new CanSignal("CHECKSUM",             59,  4, true,  false, 1.0,       0.0,     0.0,  1.0, "",   "XXX")
        )),

        // BO_ 579 RIGHT_LANE_LINE_1: 8 CAM  (_bosch_2018.dbc)
        new CanMessage(579, "RIGHT_LANE_LINE_1", Arrays.asList(
            new CanSignal("LINE_ANGLE",             7, 12, true,  false, 0.0005,  -1.024, 0.0, 1.0, "",      "XXX"),
            new CanSignal("LINE_OFFSET",           23, 12, true,  false, 0.004,   -8.192, 0.0, 1.0, "Meters","XXX"),
            new CanSignal("LINE_DISTANCE_VISIBLE", 39,  9, true,  false, 1.0,      0.0,   0.0, 1.0, "",      "XXX"),
            new CanSignal("LINE_PROBABILITY",      46,  6, true,  false, 0.015625, 0.0,   0.0, 1.0, "",      "XXX"),
            new CanSignal("FRAME_INDEX",            8,  4, false, false, 1.0,      0.0,   0.0,15.0, "",      "XXX"),
            new CanSignal("COUNTER",               61,  2, true,  false, 1.0,      0.0,   0.0, 1.0, "",      "XXX"),
            new CanSignal("CHECKSUM",              59,  4, true,  false, 1.0,      0.0,   0.0, 1.0, "",      "XXX")
        )),

        // BO_ 580 RIGHT_LANE_LINE_2: 8 CAM  (_bosch_2018.dbc)
        new CanMessage(580, "RIGHT_LANE_LINE_2", Arrays.asList(
            new CanSignal("FRAME_INDEX",           7,  4, true,  false, 1.0,       0.0,     0.0, 15.0, "",   "XXX"),
            new CanSignal("LINE_SOLID",           13,  1, true,  false, 1.0,       0.0,     0.0,  1.0, "",   "XXX"),
            new CanSignal("LINE_DASHED",          14,  1, true,  false, 1.0,       0.0,     0.0,  1.0, "",   "XXX"),
            new CanSignal("LINE_CURVATURE",       23, 12, true,  false, 0.00001, -0.02048,  0.0,  1.0, "",   "XXX"),
            new CanSignal("LINE_PARAMETER",       39, 12, true,  false, 1.0,       0.0,     0.0,  1.0, "",   "XXX"),
            new CanSignal("LINE_FAR_EDGE_POSITION",55, 8, true,  false, 1.0,    -128.0,     0.0,  1.0, "",   "XXX"),
            new CanSignal("COUNTER",              61,  2, true,  false, 1.0,       0.0,     0.0,  1.0, "",   "XXX"),
            new CanSignal("CHECKSUM",             59,  4, true,  false, 1.0,       0.0,     0.0,  1.0, "",   "XXX")
        )),

        // BO_ 582 ADJACENT_LEFT_LANE_LINE_1: 8 CAM  (_bosch_2018.dbc)
        new CanMessage(582, "ADJACENT_LEFT_LANE_LINE_1", Arrays.asList(
            new CanSignal("LINE_ANGLE",             7, 12, true,  false, 0.0005,  -1.024, 0.0, 1.0, "",      "XXX"),
            new CanSignal("LINE_OFFSET",           23, 12, true,  false, 0.004,   -8.192, 0.0, 1.0, "Meters","XXX"),
            new CanSignal("LINE_DISTANCE_VISIBLE", 39,  9, true,  false, 1.0,      0.0,   0.0, 1.0, "",      "XXX"),
            new CanSignal("LINE_PROBABILITY",      46,  6, true,  false, 0.015625, 0.0,   0.0, 1.0, "",      "XXX"),
            new CanSignal("FRAME_INDEX",            8,  4, false, false, 1.0,      0.0,   0.0,15.0, "",      "XXX"),
            new CanSignal("COUNTER",               61,  2, true,  false, 1.0,      0.0,   0.0, 1.0, "",      "XXX"),
            new CanSignal("CHECKSUM",              59,  4, true,  false, 1.0,      0.0,   0.0, 1.0, "",      "XXX")
        )),

        // BO_ 583 ADJACENT_LEFT_LANE_LINE_2: 8 CAM  (_bosch_2018.dbc)
        new CanMessage(583, "ADJACENT_LEFT_LANE_LINE_2", Arrays.asList(
            new CanSignal("FRAME_INDEX",           7,  4, true,  false, 1.0,       0.0,     0.0, 15.0, "",   "XXX"),
            new CanSignal("LINE_SOLID",           13,  1, true,  false, 1.0,       0.0,     0.0,  1.0, "",   "XXX"),
            new CanSignal("LINE_DASHED",          14,  1, true,  false, 1.0,       0.0,     0.0,  1.0, "",   "XXX"),
            new CanSignal("LINE_CURVATURE",       23, 12, true,  false, 0.00001, -0.02048,  0.0,  1.0, "",   "XXX"),
            new CanSignal("LINE_PARAMETER",       39, 12, true,  false, 1.0,       0.0,     0.0,  1.0, "",   "XXX"),
            new CanSignal("LINE_FAR_EDGE_POSITION",55, 8, true,  false, 1.0,    -128.0,     0.0,  1.0, "",   "XXX"),
            new CanSignal("COUNTER",              61,  2, true,  false, 1.0,       0.0,     0.0,  1.0, "",   "XXX"),
            new CanSignal("CHECKSUM",             59,  4, true,  false, 1.0,       0.0,     0.0,  1.0, "",   "XXX")
        )),

        // BO_ 585 ADJACENT_RIGHT_LANE_LINE_1: 8 CAM  (_bosch_2018.dbc)
        new CanMessage(585, "ADJACENT_RIGHT_LANE_LINE_1", Arrays.asList(
            new CanSignal("LINE_ANGLE",             7, 12, true,  false, 0.0005,  -1.024, 0.0, 1.0, "",      "XXX"),
            new CanSignal("LINE_OFFSET",           23, 12, true,  false, 0.004,   -8.192, 0.0, 1.0, "Meters","XXX"),
            new CanSignal("LINE_DISTANCE_VISIBLE", 39,  9, true,  false, 1.0,      0.0,   0.0, 1.0, "",      "XXX"),
            new CanSignal("LINE_PROBABILITY",      46,  6, true,  false, 0.015625, 0.0,   0.0, 1.0, "",      "XXX"),
            new CanSignal("FRAME_INDEX",            8,  4, false, false, 1.0,      0.0,   0.0,15.0, "",      "XXX"),
            new CanSignal("COUNTER",               61,  2, true,  false, 1.0,      0.0,   0.0, 1.0, "",      "XXX"),
            new CanSignal("CHECKSUM",              59,  4, true,  false, 1.0,      0.0,   0.0, 1.0, "",      "XXX")
        )),

        // BO_ 586 ADJACENT_RIGHT_LANE_LINE_2: 8 CAM  (_bosch_2018.dbc)
        new CanMessage(586, "ADJACENT_RIGHT_LANE_LINE_2", Arrays.asList(
            new CanSignal("FRAME_INDEX",           7,  4, true,  false, 1.0,       0.0,     0.0, 15.0, "",   "XXX"),
            new CanSignal("LINE_SOLID",           13,  1, true,  false, 1.0,       0.0,     0.0,  1.0, "",   "XXX"),
            new CanSignal("LINE_DASHED",          14,  1, true,  false, 1.0,       0.0,     0.0,  1.0, "",   "XXX"),
            new CanSignal("LINE_CURVATURE",       23, 12, true,  false, 0.00001, -0.02048,  0.0,  1.0, "",   "XXX"),
            new CanSignal("LINE_PARAMETER",       39, 12, true,  false, 1.0,       0.0,     0.0,  1.0, "",   "XXX"),
            new CanSignal("LINE_FAR_EDGE_POSITION",55, 8, true,  false, 1.0,    -128.0,     0.0,  1.0, "",   "XXX"),
            new CanSignal("COUNTER",              61,  2, true,  false, 1.0,       0.0,     0.0,  1.0, "",   "XXX"),
            new CanSignal("CHECKSUM",             59,  4, true,  false, 1.0,       0.0,     0.0,  1.0, "",   "XXX")
        )),

        // BO_ 597 ROUGH_WHEEL_SPEED: 8 VSA  (_honda_common.dbc)
        new CanMessage(597, "ROUGH_WHEEL_SPEED", Arrays.asList(
            new CanSignal("WHEEL_SPEED_FL",  7, 8, true, false, 1.0, 0.0, 0.0, 255.0, "kph", "EON"),
            new CanSignal("WHEEL_SPEED_FR", 15, 8, true, false, 1.0, 0.0, 0.0, 255.0, "kph", "EON"),
            new CanSignal("WHEEL_SPEED_RL", 23, 8, true, false, 1.0, 0.0, 0.0, 255.0, "kph", "EON"),
            new CanSignal("WHEEL_SPEED_RR", 31, 8, true, false, 1.0, 0.0, 0.0, 255.0, "kph", "EON"),
            new CanSignal("SET_TO_X55",     39, 8, true, false, 1.0, 0.0, 0.0, 255.0, "",    "EON"),
            new CanSignal("SET_TO_X55_2",   47, 8, true, false, 1.0, 0.0, 0.0, 255.0, "",    "EON"),
            new CanSignal("LONG_COUNTER",   55, 8, true, false, 1.0, 0.0, 0.0, 255.0, "",    "EON"),
            new CanSignal("CHECKSUM",       59, 4, true, false, 1.0, 0.0, 0.0,  15.0, "",    "EON"),
            new CanSignal("COUNTER",        61, 2, true, false, 1.0, 0.0, 0.0,   3.0, "",    "EON")
        ))
    );

    // =========================================================================
    // BCAN — body and HMI (125 kbps)
    // =========================================================================

    public static final List<CanMessage> BCAN_MESSAGES = Arrays.asList(

        // BO_ 545 ECON_STATUS: 6 SCM  (_bosch_2018.dbc)
        new CanMessage(545, "ECON_STATUS", Arrays.asList(
            new CanSignal("ECON_ON",    23, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "XXX"),
            new CanSignal("DRIVE_MODE", 37, 2, true, false, 1.0, 0.0, 0.0, 3.0, "", "XXX"),
            new CanSignal("COUNTER",    45, 2, true, false, 1.0, 0.0, 0.0, 3.0, "", "BDY"),
            new CanSignal("CHECKSUM",   43, 4, true, false, 1.0, 0.0, 0.0,15.0, "", "BDY")
        )),

        // BO_ 662 SCM_BUTTONS: 4 SCM  (_bosch_2018.dbc / _nidec_scm_group_b.dbc)
        new CanMessage(662, "SCM_BUTTONS", Arrays.asList(
            new CanSignal("CRUISE_BUTTONS", 7, 3, true, false, 1.0, 0.0, 0.0, 7.0, "", "EON"),
            new CanSignal("CRUISE_SETTING", 3, 2, true, false, 1.0, 0.0, 0.0, 3.0, "", "EON"),
            new CanSignal("COUNTER",       29, 2, true, false, 1.0, 0.0, 0.0, 3.0, "", "EON"),
            new CanSignal("CHECKSUM",      27, 4, true, false, 1.0, 0.0, 0.0,15.0, "", "EON")
        )),

        // BO_ 773 SEATBELT_STATUS: 7 BDY  (_honda_common.dbc)
        new CanMessage(773, "SEATBELT_STATUS", Arrays.asList(
            new CanSignal("SEATBELT_DRIVER_LAMP",      7, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "EON"),
            new CanSignal("SEATBELT_PASS_UNLATCHED",  10, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "EON"),
            new CanSignal("SEATBELT_PASS_LATCHED",    11, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "EON"),
            new CanSignal("SEATBELT_DRIVER_UNLATCHED",12, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "EON"),
            new CanSignal("SEATBELT_DRIVER_LATCHED",  13, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "EON"),
            new CanSignal("PASS_AIRBAG_OFF",          14, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "EON"),
            new CanSignal("PASS_AIRBAG_ON",           15, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "EON"),
            new CanSignal("COUNTER",                  53, 2, true, false, 1.0, 0.0, 0.0, 3.0, "", "EON"),
            new CanSignal("CHECKSUM",                 51, 4, true, false, 1.0, 0.0, 0.0, 3.0, "", "EON")
        )),

        // BO_ 777 CAR_SPEED: 8 PCM  (_honda_common.dbc)
        new CanMessage(777, "CAR_SPEED", Arrays.asList(
            new CanSignal("CAR_SPEED",          7, 16, true, false, 0.01, 0.0, 0.0, 65535.0, "kph", "XXX"),
            new CanSignal("ROUGH_CAR_SPEED",   23,  8, true, false, 1.0,  0.0, 0.0,  255.0,  "mph", "XXX"),
            new CanSignal("ROUGH_CAR_SPEED_2", 31,  8, true, false, 1.0,  0.0, 0.0,  255.0,  "mph", "XXX"),
            new CanSignal("ROUGH_CAR_SPEED_3", 39, 16, true, false, 0.01, 0.0, 0.0,65535.0,  "kph", "XXX"),
            new CanSignal("LOCK_STATUS",       55,  2, true, false, 1.0,  0.0, 0.0,  255.0,  "",    "XXX"),
            new CanSignal("IMPERIAL_UNIT",     63,  1, true, false, 1.0,  0.0, 0.0,    1.0,  "",    "XXX"),
            new CanSignal("COUNTER",           61,  2, true, false, 1.0,  0.0, 0.0,    3.0,  "",    "XXX"),
            new CanSignal("CHECKSUM",          59,  4, true, false, 1.0,  0.0, 0.0,   15.0,  "",    "XXX")
        )),

        // BO_ 780 ACC_HUD: 8 ADAS  (_honda_common.dbc)
        new CanMessage(780, "ACC_HUD", Arrays.asList(
            new CanSignal("PCM_SPEED",              7, 16, true, false, 0.01, 0.0, 0.0, 250.0, "kph", "BDY"),
            new CanSignal("PCM_GAS",               23,  8, true, false, 1.0,  0.0, 0.0, 127.0, "",    "BDY"),
            new CanSignal("CRUISE_SPEED",          31,  8, true, false, 1.0,  0.0, 0.0, 255.0, "kph", "BDY"),
            new CanSignal("DTC_MODE",              39,  1, true, false, 1.0,  0.0, 0.0,   1.0, "",    "BDY"),
            new CanSignal("BRAKE_SYSTEM_ICON",     38,  1, true, false, 1.0,  0.0, 0.0,   1.0, "",    "BDY"),
            new CanSignal("ACC_PROBLEM",           37,  1, true, false, 1.0,  0.0, 0.0,   1.0, "",    "BDY"),
            new CanSignal("FCM_OFF_2",             36,  1, true, false, 1.0,  0.0, 0.0,   1.0, "",    "BDY"),
            new CanSignal("FCM_OFF",               35,  1, true, false, 1.0,  0.0, 0.0,   1.0, "",    "BDY"),
            new CanSignal("FCM_PROBLEM",           34,  1, true, false, 1.0,  0.0, 0.0,   1.0, "",    "BDY"),
            new CanSignal("RADAR_OBSTRUCTED",      33,  1, true, false, 1.0,  0.0, 0.0,   1.0, "",    "BDY"),
            new CanSignal("ENABLE_MINI_CAR",       32,  1, true, false, 1.0,  0.0, 0.0,   1.0, "",    "BDY"),
            new CanSignal("HUD_DISTANCE",          47,  2, true, false, 1.0,  0.0, 0.0,   3.0, "",    "BDY"),
            new CanSignal("HUD_LEAD",              45,  2, true, false, 1.0,  0.0, 0.0,   3.0, "",    "BDY"),
            new CanSignal("BOH_3",                 43,  1, true, false, 1.0,  0.0, 0.0,   3.0, "",    "BDY"),
            new CanSignal("BOH_4",                 42,  1, true, false, 1.0,  0.0, 0.0,   3.0, "",    "BDY"),
            new CanSignal("BOH_5",                 41,  1, true, false, 1.0,  0.0, 0.0,   3.0, "",    "BDY"),
            new CanSignal("CRUISE_CONTROL_LABEL",  40,  1, true, false, 1.0,  0.0, 0.0,   3.0, "",    "BDY"),
            new CanSignal("SET_ME_X01",            48,  1, true, false, 1.0,  0.0, 0.0,   1.0, "",    "BDY"),
            new CanSignal("CHIME",                 51,  3, true, false, 1.0,  0.0, 0.0,   1.0, "",    "BDY"),
            new CanSignal("ACC_ON",                52,  1, true, false, 1.0,  0.0, 0.0,   1.0, "",    "BDY"),
            new CanSignal("IMPERIAL_UNIT",         54,  1, true, false, 1.0,  0.0, 0.0,   1.0, "",    "BDY"),
            new CanSignal("SET_ME_X01_2",          55,  1, true, false, 1.0,  0.0, 0.0,   1.0, "",    "BDY"),
            new CanSignal("ICONS",                 63,  2, true, false, 1.0,  0.0, 0.0,   1.0, "",    "BDY"),
            new CanSignal("COUNTER",               61,  2, true, false, 1.0,  0.0, 0.0,   3.0, "",    "BDY"),
            new CanSignal("CHECKSUM",              59,  4, true, false, 1.0,  0.0, 0.0,   3.0, "",    "BDY")
        )),

        // BO_ 804 CRUISE: 8 PCM  (_honda_common.dbc)
        new CanMessage(804, "CRUISE", Arrays.asList(
            new CanSignal("HUD_SPEED_KPH",    7,  8, true, false, 1.0, 0.0, 0.0, 255.0, "kph", "EON"),
            new CanSignal("HUD_SPEED_MPH",   15,  8, true, false, 1.0, 0.0, 0.0, 255.0, "mph", "EON"),
            new CanSignal("TRIP_FUEL_CONSUMED",23,16, true, false, 1.0, 0.0, 0.0, 255.0, "",    "EON"),
            new CanSignal("CRUISE_SPEED_PCM", 39,  8, true, false, 1.0, 0.0, 0.0, 255.0, "",    "EON"),
            new CanSignal("BOH2",             47,  8, true,  true, 1.0, 0.0, 0.0, 255.0, "",    "EON"),
            new CanSignal("BOH3",             55,  8, true, false, 1.0, 0.0, 0.0, 255.0, "",    "EON"),
            new CanSignal("COUNTER",          61,  2, true, false, 1.0, 0.0, 0.0,   3.0, "",    "EON"),
            new CanSignal("CHECKSUM",         59,  4, true, false, 1.0, 0.0, 0.0,  15.0, "",    "EON")
        )),

        // BO_ 806 SCM_FEEDBACK: 8 SCM  (_bosch_2018.dbc / _nidec_scm_group_b.dbc)
        new CanMessage(806, "SCM_FEEDBACK", Arrays.asList(
            new CanSignal("DRIVERS_DOOR_OPEN",17, 1, true, false, 1.0, 0.0, 0.0,  1.0, "", "XXX"),
            new CanSignal("REVERSE_LIGHT",    18, 1, true, false, 1.0, 0.0, 0.0,  1.0, "", "EON"),
            new CanSignal("CMBS_STATES",      22, 2, true, false, 1.0, 0.0, 0.0,  3.0, "", "EON"),
            new CanSignal("LEFT_BLINKER",     26, 1, true, false, 1.0, 0.0, 0.0,  1.0, "", "EON"),
            new CanSignal("RIGHT_BLINKER",    27, 1, true, false, 1.0, 0.0, 0.0,  1.0, "", "EON"),
            new CanSignal("MAIN_ON",          28, 1, true, false, 1.0, 0.0, 0.0,  1.0, "", "EON"),
            new CanSignal("PARKING_BRAKE_ON", 29, 1, true, false, 1.0, 0.0, 0.0,  1.0, "", "XXX"),
            new CanSignal("COUNTER",          61, 2, true, false, 1.0, 0.0, 0.0,  3.0, "", "XXX"),
            new CanSignal("CHECKSUM",         59, 4, true, false, 1.0, 0.0, 0.0, 15.0, "", "XXX")
        )),

        // BO_ 829 LKAS_HUD: 5 ADAS  (_lkas_hud_5byte.dbc — Touring 2016)
        new CanMessage(829, "LKAS_HUD", Arrays.asList(
            new CanSignal("LKAS_READY",           0, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "BDY"),
            new CanSignal("LKAS_STATE_CHANGE",    6, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "XXX"),
            new CanSignal("CAM_TEMP_HIGH",         7, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "BDY"),
            new CanSignal("STEERING_REQUIRED",     8, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "BDY"),
            new CanSignal("RDM_HUD",               9, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "BDY"),
            new CanSignal("SOLID_LANES",          10, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "BDY"),
            new CanSignal("LKAS_OFF",             11, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "BDY"),
            new CanSignal("LKAS_PROBLEM",         12, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "BDY"),
            new CanSignal("DTC",                  13, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "BDY"),
            new CanSignal("DASHED_LANES",         14, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "BDY"),
            new CanSignal("BEEP",                 17, 2, true, false, 1.0, 0.0, 0.0, 5.0, "", "BDY"),
            new CanSignal("RDM_ON",               20, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "XXX"),
            new CanSignal("RDM_PROBLEM",          21, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "BDY"),
            new CanSignal("RDM_HUD_2",            23, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "XXX"),
            new CanSignal("CLEAN_WINDSHIELD",     26, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "BDY"),
            new CanSignal("RDM_OFF",              27, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "BDY"),
            new CanSignal("RDM_ON_2",             28, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "BDY"),
            new CanSignal("LANE_ASSIST_BEEP_OFF", 30, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "XXX"),
            new CanSignal("CHECKSUM",             35, 4, true, false, 1.0, 0.0, 0.0,15.0, "", "BDY"),
            new CanSignal("COUNTER",              37, 2, true, false, 1.0, 0.0, 0.0, 3.0, "", "BDY")
        )),

        // BO_ 862 CAMERA_MESSAGES: 8 CAM  (_honda_common.dbc)
        new CanMessage(862, "CAMERA_MESSAGES", Arrays.asList(
            new CanSignal("ZEROS_BOH",             7, 16, true, false, 1.0, 0.0, 0.0, 127.0, "", "BDY"),
            new CanSignal("SPEED_LIMIT_SIGN",     23,  8, true, false, 1.0, 0.0, 0.0, 255.0, "", "XXX"),
            new CanSignal("ROAD_SIGN",            31,  8, true, false, 1.0, 0.0, 0.0, 255.0, "", "XXX"),
            new CanSignal("ZEROS_BOH_2",          51,  4, true, false, 1.0, 0.0, 0.0,  15.0, "", "XXX"),
            new CanSignal("HIGHBEAMS_ON",         52,  1, true, false, 1.0, 0.0, 0.0,   1.0, "", "XXX"),
            new CanSignal("AUTO_HIGHBEAMS_ACTIVE",53,  1, true, false, 1.0, 0.0, 0.0,   1.0, "", "XXX"),
            new CanSignal("CHECKSUM",             59,  4, true, false, 1.0, 0.0, 0.0,  15.0, "", "XXX"),
            new CanSignal("COUNTER",              61,  2, true, false, 1.0, 0.0, 0.0,   3.0, "", "XXX")
        )),

        // BO_ 884 STALK_STATUS: 8 XXX  (_honda_common.dbc)
        new CanMessage(884, "STALK_STATUS", Arrays.asList(
            new CanSignal("DASHBOARD_ALERT", 39, 8, true, false, 1.0, 0.0, 0.0, 255.0, "", "EON"),
            new CanSignal("AUTO_HEADLIGHTS",46, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "EON"),
            new CanSignal("HIGH_BEAM_HOLD", 47, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "EON"),
            new CanSignal("HIGH_BEAM_FLASH",45, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "EON"),
            new CanSignal("HEADLIGHTS_ON",  54, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "EON"),
            new CanSignal("WIPER_SWITCH",   53, 2, true, false, 1.0, 0.0, 0.0,   3.0, "", "XXX"),
            new CanSignal("COUNTER",        61, 2, true, false, 1.0, 0.0, 0.0,   3.0, "", "EON"),
            new CanSignal("CHECKSUM",       59, 4, true, false, 1.0, 0.0, 0.0,  15.0, "", "EON")
        )),

        // BO_ 891 STALK_STATUS_2: 8 XXX  (_honda_common.dbc)
        new CanMessage(891, "STALK_STATUS_2", Arrays.asList(
            new CanSignal("WIPERS",     17, 2, true, false, 1.0, 0.0, 0.0, 4.0, "", "EON"),
            new CanSignal("LOW_BEAMS",  35, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "XXX"),
            new CanSignal("HIGH_BEAMS", 34, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "XXX"),
            new CanSignal("PARK_LIGHTS",36, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "XXX"),
            new CanSignal("COUNTER",    61, 2, true, false, 1.0, 0.0, 0.0, 3.0, "", "EON"),
            new CanSignal("CHECKSUM",   59, 4, true, false, 1.0, 0.0, 0.0,15.0, "", "EON")
        )),

        // BO_ 892 CRUISE_PARAMS: 8 PCM  (_nidec_common.dbc)
        new CanMessage(892, "CRUISE_PARAMS", Arrays.asList(
            new CanSignal("CRUISE_SPEED_OFFSET", 31, 8, true,  true, 0.1, 0.0, -128.0, 127.0, "kph", "EON"),
            new CanSignal("CHECKSUM",            59, 4, true, false, 1.0, 0.0,    0.0,   3.0, "",    "EON"),
            new CanSignal("COUNTER",             61, 2, true, false, 1.0, 0.0,    0.0,   3.0, "",    "EON")
        )),

        // BO_ 927 RADAR_HUD: 8 ADAS  (_nidec_common.dbc — Nidec variant)
        new CanMessage(927, "RADAR_HUD_NIDEC", Arrays.asList(
            new CanSignal("ZEROS_BOH",             7, 16, true, false, 1.0, 0.0,  0.0, 127.0, "", "BDY"),
            new CanSignal("ACC_ALERTS",           20,  5, true, false, 1.0, 0.0,  0.0,  15.0, "", "BDY"),
            new CanSignal("RESUME_INSTRUCTION",   21,  1, true, false, 1.0, 0.0,  0.0,  15.0, "", "BDY"),
            new CanSignal("APPLY_BRAKES_FOR_CANC",23,  1, true, false, 1.0, 0.0,  0.0,  15.0, "", "BDY"),
            new CanSignal("ZEROS_BOH2",           31,  8, true, false, 1.0, 0.0,  0.0, 127.0, "", "BDY"),
            new CanSignal("LEAD_SPEED",           39,  9, true, false, 1.0, 0.0,  0.0, 127.0, "", "BDY"),
            new CanSignal("LEAD_DISTANCE",        46,  8, true, false, 1.0, 0.0,  0.0,  31.0, "", "BDY"),
            new CanSignal("ZEROS_BOH3",           54,  7, true, false, 1.0, 0.0,  0.0, 127.0, "", "BDY"),
            new CanSignal("COUNTER",              61,  2, true, false, 1.0, 0.0,  0.0,   3.0, "", "BDY"),
            new CanSignal("CHECKSUM",             59,  4, true, false, 1.0, 0.0,  0.0,  15.0, "", "BDY")
        )),

        // BO_ 927 RADAR_HUD: 8 RADAR  (_bosch_2018.dbc — Bosch variant)
        new CanMessage(927, "RADAR_HUD_BOSCH", Arrays.asList(
            new CanSignal("ZEROS_BOH",             7, 10, true, false, 1.0, 0.0,  0.0, 127.0, "", "BDY"),
            new CanSignal("ZEROS_BOH2",           11,  4, true, false, 1.0, 0.0,  0.0,   1.0, "", "XXX"),
            new CanSignal("CMBS_OFF",             12,  1, true, false, 1.0, 0.0,  0.0,   1.0, "", "BDY"),
            new CanSignal("SET_TO_1",             13,  1, true, false, 1.0, 0.0,  0.0,   1.0, "", "BDY"),
            new CanSignal("ACC_ALERTS",           20,  5, true, false, 1.0, 0.0,  0.0,   1.0, "", "BDY"),
            new CanSignal("RESUME_INSTRUCTION",   21,  1, true, false, 1.0, 0.0,  0.0,   1.0, "", "XXX"),
            new CanSignal("SET_TO_0",             22,  1, true, false, 1.0, 0.0,  0.0,   1.0, "", "XXX"),
            new CanSignal("APPLY_BRAKES_FOR_CANC",23,  1, true, false, 1.0, 0.0,  0.0,   1.0, "", "XXX"),
            new CanSignal("SET_TO_64",            31,  8, true, false, 1.0, 0.0,  0.0, 255.0, "", "XXX"),
            new CanSignal("LEAD_DISTANCE",        39,  8, true, false, 1.0, 0.0,  0.0, 255.0, "", "XXX"),
            new CanSignal("HUD_LEAD",             40,  1, true, false, 1.0, 0.0,  0.0,   1.0, "", "XXX"),
            new CanSignal("ZEROS_BOH3",           47,  7, true, false, 1.0, 0.0,  0.0, 127.0, "", "XXX"),
            new CanSignal("ZEROS_BOH4",           55,  8, true, false, 1.0, 0.0,  0.0, 255.0, "", "XXX"),
            new CanSignal("COUNTER",              61,  2, true, false, 1.0, 0.0,  0.0,   3.0, "", "XXX"),
            new CanSignal("CHECKSUM",             59,  4, true, false, 1.0, 0.0,  0.0,  15.0, "", "XXX")
        )),

        // BO_ 1029 DOORS_STATUS: 8 BDY  (_honda_common.dbc)
        new CanMessage(1029, "DOORS_STATUS", Arrays.asList(
            new CanSignal("DOOR_OPEN_FL",37, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "EON"),
            new CanSignal("DOOR_OPEN_FR",38, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "EON"),
            new CanSignal("DOOR_OPEN_RL",39, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "EON"),
            new CanSignal("DOOR_OPEN_RR",40, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "EON"),
            new CanSignal("TRUNK_OPEN",  41, 1, true, false, 1.0, 0.0, 0.0, 1.0, "", "EON"),
            new CanSignal("COUNTER",     61, 2, true, false, 1.0, 0.0, 0.0, 3.0, "", "EON"),
            new CanSignal("CHECKSUM",    59, 4, true, false, 1.0, 0.0, 0.0,15.0, "", "EON")
        )),

        // BO_ 1302 ODOMETER: 8 XXX  (_honda_common.dbc)
        new CanMessage(1302, "ODOMETER", Arrays.asList(
            new CanSignal("ODOMETER", 7, 24, true, false, 1.0, 0.0, 0.0, 16777215.0, "km", "EON"),
            new CanSignal("COUNTER",  61, 2, true, false, 1.0, 0.0, 0.0,        3.0, "",   "EON"),
            new CanSignal("CHECKSUM", 59, 4, true, false, 1.0, 0.0, 0.0,        3.0, "",   "EON")
        )),

        // BO_ 13274 LKAS_HUD_A: 5 ADAS  (_bosch_2018.dbc — Bosch Hatchback 5-byte variant)
        new CanMessage(13274, "LKAS_HUD_A", Arrays.asList(
            new CanSignal("LKAS_READY",           0, 1, true, false, 1.0, 0.0, 0.0, 127.0, "", "BDY"),
            new CanSignal("LKAS_STATE_CHANGE",    6, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "XXX"),
            new CanSignal("CAM_TEMP_HIGH",         7, 1, true, false, 1.0, 0.0, 0.0, 255.0, "", "BDY"),
            new CanSignal("STEERING_REQUIRED",     8, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("RDM_HUD",               9, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("SOLID_LANES",          10, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("LKAS_OFF",             11, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("LKAS_PROBLEM",         12, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("DTC",                  13, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("DASHED_LANES",         14, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("BEEP",                 17, 2, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("RDM_ON",               20, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "XXX"),
            new CanSignal("RDM_PROBLEM",          21, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("RDM_HUD_2",            23, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "XXX"),
            new CanSignal("CLEAN_WINDSHIELD",     26, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("RDM_OFF",              27, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("RDM_ON_2",             28, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("LANE_ASSIST_BEEP_OFF", 30, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "XXX"),
            new CanSignal("CHECKSUM",             35, 4, true, false, 1.0, 0.0, 0.0,  15.0, "", "BDY"),
            new CanSignal("COUNTER",              37, 2, true, false, 1.0, 0.0, 0.0,   3.0, "", "BDY")
        )),

        // BO_ 13275 LKAS_HUD_B: 8 ADAS  (_bosch_2018.dbc — Bosch Hatchback 8-byte variant)
        new CanMessage(13275, "LKAS_HUD_B", Arrays.asList(
            new CanSignal("LKAS_READY",           0, 1, true, false, 1.0, 0.0, 0.0, 127.0, "", "BDY"),
            new CanSignal("LKAS_STATE_CHANGE",    6, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "XXX"),
            new CanSignal("CAM_TEMP_HIGH",         7, 1, true, false, 1.0, 0.0, 0.0, 255.0, "", "BDY"),
            new CanSignal("STEERING_REQUIRED",     8, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("RDM_HUD",               9, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("SOLID_LANES",          10, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("LKAS_OFF",             11, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("LKAS_PROBLEM",         12, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("DTC",                  13, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("DASHED_LANES",         14, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("BEEP",                 17, 2, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("RDM_ON",               20, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "XXX"),
            new CanSignal("RDM_PROBLEM",          21, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("RDM_HUD_2",            23, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "XXX"),
            new CanSignal("CLEAN_WINDSHIELD",     26, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("RDM_OFF",              27, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("RDM_ON_2",             28, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "BDY"),
            new CanSignal("LANE_ASSIST_BEEP_OFF", 30, 1, true, false, 1.0, 0.0, 0.0,   1.0, "", "XXX"),
            new CanSignal("LANE_LINES",           36, 2, true, false, 1.0, 0.0, 0.0,   3.0, "", "XXX"),
            new CanSignal("CHECKSUM",             59, 4, true, false, 1.0, 0.0, 0.0,  15.0, "", "BDY"),
            new CanSignal("COUNTER",              61, 2, true, false, 1.0, 0.0, 0.0,   3.0, "", "BDY")
        ))
    );

    // -------------------------------------------------------------------------
    // Flat signal lists (kept for CanRepository backward compat)
    // -------------------------------------------------------------------------

    public static final List<CanSignal> FCAN_SIGNALS = flatSignals(FCAN_MESSAGES);
    public static final List<CanSignal> BCAN_SIGNALS = flatSignals(BCAN_MESSAGES);

    private static List<CanSignal> flatSignals(List<CanMessage> messages) {
        List<CanSignal> out = new ArrayList<>();
        for (CanMessage m : messages) out.addAll(m.signals);
        return out;
    }

    private CanDefinitions() {}
}
