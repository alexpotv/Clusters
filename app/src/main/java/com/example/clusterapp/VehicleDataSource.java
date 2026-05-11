package com.example.clusterapp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;

import android.util.Log;

import com.example.clusterapp.can.CanDecoder;
import com.example.clusterapp.can.CanDefinitions;
import com.example.clusterapp.can.CanMessage;
import com.example.clusterapp.can.CanRepository;
import com.example.clusterapi.VehicleSnapshot;
import com.example.clusterapi.vehicle.AdasState;
import com.example.clusterapi.vehicle.AudioState;
import com.example.clusterapi.vehicle.BrakeState;
import com.example.clusterapi.vehicle.CabinState;
import com.example.clusterapi.vehicle.DrivetrainState;
import com.example.clusterapi.vehicle.FuelState;
import com.example.clusterapi.vehicle.LightsState;
import com.example.clusterapi.vehicle.MaintenanceState;
import com.example.clusterapi.vehicle.MotionState;
import com.example.clusterapi.vehicle.SteeringState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * Owns all system-service bindings (VehicleInfoManager, IAvApService) and the poll loop.
 * Builds a fresh {@link VehicleState} each tick and pushes it to registered listeners.
 * All listener calls happen on the main thread.
 */
public class VehicleDataSource {

    private final CanRepository canRepo = new CanRepository();
    {
        canRepo.addBus("FCAN", CanDefinitions.FCAN_MESSAGES);
        canRepo.addBus("BCAN", CanDefinitions.BCAN_MESSAGES);
    }

    private final List<Listener> mListeners = new ArrayList<>();

    public interface Listener {
        void onVehicleSnapshotChanged(VehicleSnapshot snapshot);
    }

    // -------------------------------------------------------------------------
    // VehicleInfoManagerApService
    // -------------------------------------------------------------------------
    private static final String VEHICLE_PKG =
            "com.mitsubishielectric.ada.appservice.vehicleinfomanager";
    private static final String VEHICLE_CLS        = VEHICLE_PKG + ".VehicleInfoManagerApService";
    private static final String VEHICLE_DESCRIPTOR = VEHICLE_PKG + ".IVehicleInfoManagerApService";
    private static final int TX_GET_VEHICLE_INFO        = 12;
    private static final int TX_GET_FOOT_BRAKE          = 102;
    private static final int TX_GET_TURN_SIGNALS        = 103;
    private static final int TX_REGISTER_BCAN_LISTENER  = 25;
    private static final int TX_REGISTER_FCAN_LISTENER  = 35;
    private static final int TX_NOTIFY_BCAN_RECEIVE     = 88;
    private static final int VEHICLE_INFO_ILLUMINATION  = 0;
    private static final int VEHICLE_INFO_SHIFT         = 1;
    private static final int VEHICLE_INFO_PARKING_BRAKE = 6;
    private static final int VEHICLE_INFO_SPEED         = 8;
    private static final int VEHICLE_INFO_GEAR          = 9;

    // -------------------------------------------------------------------------
    // CAN listener descriptors
    // -------------------------------------------------------------------------
    private static final String BCAN_DESCRIPTOR = VEHICLE_PKG + ".IVehicleBcanInformationListener";
    private static final String FCAN_DESCRIPTOR = VEHICLE_PKG + ".IVehicleFcanInformationListener";

    // Known BCAN type code for the fuel-range TRICOM frame (Mitsubishi internal ID).
    private static final int BCAN_ID_TRICOM      = 318333520;
    private static final String BCAN_KEY_RANGE      = "C_TRICOM_RANGE";
    private static final String BCAN_KEY_RANGE_UNIT = "C_TRICOM_RANGE_UNIT";

    // ── Mitsubishi proprietary BCAN IDs (from ConstExt) ──────────────────────
    // These are NOT standard CAN IDs; the middleware maps wire-level frames to these.
    private static final int BCAN_ID_VSPNE       = 318263376; // vehicle speed / NE (RPM)
    private static final int BCAN_ID_AT          = 318263632; // transmission / gear position
    private static final int BCAN_ID_ILLUMI      = 318264400; // illumination (headlights)
    private static final int BCAN_ID_STEERING    = 318337360; // steering angle
    private static final int BCAN_ID_HLSW_BCM   = 184053784; // headlight-switch BCM (turn signals)
    private static final int BCAN_ID_HLSW_ICU   = 184053776; // headlight-switch ICU
    private static final int BCAN_ID_MICU_BCM   = 318246936; // MICU/BCM (backup light, one-touch)
    private static final int BCAN_ID_MICU_ICU   = 318246928;
    private static final int BCAN_ID_MAINTENANCE = 318334800; // oil life, service interval
    private static final int BCAN_ID_MET_CUSTOM  = 318336336; // ADAS display settings (CMBS, LKAS, RDM)

    // -------------------------------------------------------------------------
    // IDiagService — CAN signal polling (FCAN + BCAN)
    // -------------------------------------------------------------------------
    private static final String DIAG_PKG             = "com.mitsubishielectric.ada.appservice.diag";
    private static final String DIAG_CLS             = DIAG_PKG + ".DiagService";
    private static final String DIAG_DESCRIPTOR      = DIAG_PKG + ".IDiagService";
    private static final String DIAG_LST_DESCRIPTOR  = DIAG_PKG + ".IDiagServiceListner";
    private static final int DIAG_TX_INITIALIZE      = 1;
    private static final int DIAG_TX_REQUEST         = 3;
    private static final int DIAG_CANDATA_GET_FCAN   = 3998210;
    private static final int DIAG_CANDATA_GET_BCAN   = 3998209;

    // -------------------------------------------------------------------------
    // IAvApService — audio source, volume, mute
    // -------------------------------------------------------------------------
    private static final String AV_PKG        = "com.mitsubishielectric.ada.appservice.avapservice";
    private static final String AV_CLS        = AV_PKG + ".AvApService";
    private static final String AV_DESCRIPTOR = AV_PKG + ".IAvApService";
    private static final int TX_AV_GET_VOLUME     = 22;
    private static final int TX_AV_GET_VOLUME_MAX = 160;
    private static final int TX_AV_GET_MUTE       = 24;
    private static final int TX_AV_GET_SOURCE     = 72;
    private static final int AV_VOLUME_TYPE_MAIN  = 0;

    // -------------------------------------------------------------------------

    private Context mContext;
    private IBinder mVehicleBinder;
    private IBinder mAvBinder;
    private IBinder mDiagBinder;
    private boolean mDiagBound  = false;
    private int mRefreshRateMs = 250;
    private int mDiagPollTick  = 0;

    private volatile int mBcanRange = -1;
    private volatile int mBcanRangeUnit = 0;

    // Decoded CAN signal values, keyed by signal name.
    // Updated from binder callbacks (any thread); read in poll() (main thread).
    private final ConcurrentHashMap<String, Double> mFcanValues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> mBcanValues = new ConcurrentHashMap<>();

    // Per-key observed stats: double[]{min, max, latest}.
    // Accumulates across all callbacks so range reveals signal type (boolean vs enum vs continuous).
    private final ConcurrentHashMap<String, double[]> mFcanStats = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, double[]> mBcanStats = new ConcurrentHashMap<>();

    // IDiagService signal stats, separated by bus. double[]{min, max, latest}.
    private final ConcurrentHashMap<String, double[]> mDiagFcanStats  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, double[]> mDiagBcanStats  = new ConcurrentHashMap<>();
    // Tracks the poll tick at which each signal last changed value (for recency highlighting).
    private final ConcurrentHashMap<String, Integer>  mDiagChangeTick = new ConcurrentHashMap<>();
    // Most recent requestIds returned by transactDiagRequest, used to route responses to correct bus.
    private volatile int mDiagFcanReqId = -1;
    private volatile int mDiagBcanReqId = -1;
    // Increments every diagPoll() call (~1 s); exposed so DiscoveryScreen can compute signal age.
    public  volatile int mDiagTick = 0;

    // All integer-valued bundle keys ever seen in FCAN/BCAN callbacks.
    // Key format: "typeDecimal/SIGNAL_NAME"  e.g. "380/ENG_ENG_SPEED"
    // Captures signals beyond what the hardcoded switch cases extract.
    private final ConcurrentHashMap<String, double[]> mFcanRaw = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, double[]> mBcanRaw = new ConcurrentHashMap<>();
    // Wall-clock ms of last value change, for recency highlighting in DiscoveryScreen.
    private final ConcurrentHashMap<String, Long> mFcanRawChangedAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> mBcanRawChangedAt = new ConcurrentHashMap<>();

    public Map<String, double[]> getFcanStats()         { return mFcanStats; }
    public Map<String, double[]> getBcanStats()          { return mBcanStats; }
    public Map<String, double[]> getDiagFcanStats()      { return mDiagFcanStats; }
    public Map<String, double[]> getDiagBcanStats()      { return mDiagBcanStats; }
    public Map<String, Integer>  getDiagChangeTick()     { return mDiagChangeTick; }
    public Map<String, double[]> getFcanRaw()            { return mFcanRaw; }
    public Map<String, double[]> getBcanRaw()             { return mBcanRaw; }
    public Map<String, Long>     getFcanRawChangedAt()   { return mFcanRawChangedAt; }
    public Map<String, Long>     getBcanRawChangedAt()   { return mBcanRawChangedAt; }

    public volatile boolean diagDiagConnected = false;
    public volatile int     diagDiagCallbacks = 0;
    public volatile String  diagDiagLastKeys  = "";

    // ── Diagnostics (written from binder thread, read from any thread) ────────
    private static final String TAG = "CAN";

    public volatile int    diagFcanCallbacks   = 0;
    public volatile int    diagBcanCallbacks   = 0;
    public volatile int    diagLastFcanType    = -1;
    public volatile int    diagLastBcanType    = -1;
    /** Number of numeric values extracted from the most recent FCAN/BCAN Bundle. */
    public volatile int    diagFcanDecoded     = 0;
    public volatile int    diagBcanDecoded     = 0;
    /** All distinct Bundle keys ever seen on each bus, comma-separated. */
    public volatile String diagFcanBundleKeys  = "";
    public volatile String diagBcanBundleKeys  = "";
    /** All distinct type codes ever received, as "decimal (0xHEX)" entries. */
    public volatile String diagFcanTypes       = "";
    public volatile String diagBcanTypes       = "";
    /** Whether the last raw-frame decode attempt found a byte[] in the Bundle. */
    public volatile boolean diagFcanHasRawFrame  = false;
    public volatile boolean diagBcanHasRawFrame  = false;
    /** Sample of current key=value pairs in the map (first 5 entries). */
    public volatile String diagFcanSample      = "";
    public volatile String diagBcanSample      = "";

    private final Set<String> mSeenFcanKeys =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Set<String> mSeenBcanKeys =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Set<Integer> mSeenFcanTypes =
            Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());
    private final Set<Integer> mSeenBcanTypes =
            Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());

    public static VehicleDataSource sInstance;

    public static VehicleDataSource getInstance() { return sInstance; }

    public CanRepository getCanRepository() { return canRepo; }

    private final Handler mHandler = new Handler();

    private final Runnable mPollRunnable = new Runnable() {
        @Override public void run() {
            poll();
            mHandler.postDelayed(this, mRefreshRateMs);
        }
    };

    // ── BCAN listener ─────────────────────────────────────────────────────────

    private final Binder mBcanListener = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code != 1) return false;
            try {
                data.enforceInterface(BCAN_DESCRIPTOR);
                int type = data.readInt();
                Bundle info = null;
                if (data.readInt() != 0) info = Bundle.CREATOR.createFromParcel(data);

                diagBcanCallbacks++;
                diagLastBcanType = type;
                Log.d(TAG, "BCAN cb #" + diagBcanCallbacks
                        + "  type=" + type + " (0x" + Integer.toHexString(type) + ")"
                        + "  bundle=" + describeBundle(info));

                if (info != null) {
                    trackBundleKeys(info, mSeenBcanKeys);
                    diagBcanBundleKeys = joinKeys(mSeenBcanKeys);
                    mSeenBcanTypes.add(type);
                    diagBcanTypes = joinTypes(mSeenBcanTypes);
                    int count = mapBcanBundle(type, info);
                    diagBcanDecoded = count;
                    diagBcanSample = sampleValues(mBcanValues, 5);
                    if (count > 0) Log.d(TAG, "BCAN mapped " + count + " signals");
                }
            } catch (Exception e) {
                Log.e(TAG, "BCAN onTransact error", e);
            }
            return true;
        }
    };

    // ── FCAN listener ─────────────────────────────────────────────────────────

    private final Binder mFcanListener = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code != 1) return false;
            try {
                data.enforceInterface(FCAN_DESCRIPTOR);
                int type = data.readInt();
                Bundle info = null;
                if (data.readInt() != 0) info = Bundle.CREATOR.createFromParcel(data);

                diagFcanCallbacks++;
                diagLastFcanType = type;
                Log.d(TAG, "FCAN cb #" + diagFcanCallbacks
                        + "  type=" + type + " (0x" + Integer.toHexString(type) + ")"
                        + "  bundle=" + describeBundle(info));

                if (info != null) {
                    trackBundleKeys(info, mSeenFcanKeys);
                    diagFcanBundleKeys = joinKeys(mSeenFcanKeys);
                    mSeenFcanTypes.add(type);
                    diagFcanTypes = joinTypes(mSeenFcanTypes);
                    int count = mapFcanBundle(type, info);
                    diagFcanDecoded = count;
                    diagFcanSample = sampleValues(mFcanValues, 5);
                    if (count > 0) Log.d(TAG, "FCAN mapped " + count + " signals");
                }
            } catch (Exception e) {
                Log.e(TAG, "FCAN onTransact error", e);
            }
            return true;
        }
    };

    // ── IDiagService listener ─────────────────────────────────────────────────

    private final Binder mDiagListener = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code != 1) return false;
            try {
                data.enforceInterface(DIAG_LST_DESCRIPTOR);
                int requestId = data.readInt();
                Bundle bundle = (data.readInt() != 0) ? Bundle.CREATOR.createFromParcel(data) : null;
                if (bundle != null) processDiagBundle(requestId, bundle);
            } catch (Exception e) {
                Log.e(TAG, "DiagListener error", e);
            }
            return true;
        }
    };

    // ── Service connections ───────────────────────────────────────────────────

    private final ServiceConnection mVehicleConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            mVehicleBinder = b;
            registerBcanListener();
            registerFcanListener();
            requestExtraBcanIds();
        }
        @Override public void onServiceDisconnected(ComponentName n) { mVehicleBinder = null; }
    };

    private final ServiceConnection mAvConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) { mAvBinder = b; }
        @Override public void onServiceDisconnected(ComponentName n) { mAvBinder = null; }
    };

    private final ServiceConnection mDiagConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            mDiagBinder = b;
            diagDiagConnected = true;
            diagInitialize();
        }
        @Override public void onServiceDisconnected(ComponentName n) {
            mDiagBinder = null;
            diagDiagConnected = false;
        }
    };

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void start(Context context) {
        mContext = context;
        sInstance = this;

        Intent vehicleIntent = new Intent(VEHICLE_DESCRIPTOR);
        vehicleIntent.setComponent(new ComponentName(VEHICLE_PKG, VEHICLE_CLS));
        mContext.bindService(vehicleIntent, mVehicleConn, Context.BIND_AUTO_CREATE);

        Intent avIntent = new Intent(AV_DESCRIPTOR);
        avIntent.setComponent(new ComponentName(AV_PKG, AV_CLS));
        mContext.bindService(avIntent, mAvConn, Context.BIND_AUTO_CREATE);

        try {
            Intent diagIntent = new Intent(DIAG_DESCRIPTOR);
            diagIntent.setComponent(new ComponentName(DIAG_PKG, DIAG_CLS));
            mDiagBound = mContext.bindService(diagIntent, mDiagConn, Context.BIND_AUTO_CREATE);
            if (!mDiagBound) Log.w(TAG, "DiagService bindService returned false");
        } catch (Exception e) {
            Log.w(TAG, "DiagService bind failed: " + e.getMessage());
        }

        mHandler.post(mPollRunnable);
    }

    public void stop() {
        mHandler.removeCallbacks(mPollRunnable);
        if (mVehicleBinder != null) {
            try { mContext.unbindService(mVehicleConn); } catch (Exception ignored) {}
            mVehicleBinder = null;
        }
        if (mAvBinder != null) {
            try { mContext.unbindService(mAvConn); } catch (Exception ignored) {}
            mAvBinder = null;
        }
        if (mDiagBound) {
            try { mContext.unbindService(mDiagConn); } catch (Exception ignored) {}
            mDiagBound = false;
            mDiagBinder = null;
        }
    }

    public void setRefreshRateMs(int ms) { mRefreshRateMs = Math.max(ms, 50); }

    public void addListener(Listener l)    { mListeners.add(l); }
    public void removeListener(Listener l) { mListeners.remove(l); }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private void registerBcanListener() {
        transactRegisterListener(TX_REGISTER_BCAN_LISTENER, VEHICLE_DESCRIPTOR, mBcanListener);
    }

    private void registerFcanListener() {
        transactRegisterListener(TX_REGISTER_FCAN_LISTENER, VEHICLE_DESCRIPTOR, mFcanListener);
    }

    private void requestExtraBcanIds() {
        if (mVehicleBinder == null) return;
        Set<Integer> ids = new java.util.HashSet<>();
        for (CanMessage msg : CanDefinitions.BCAN_MESSAGES) ids.add(msg.id);
        for (int canId : ids) {
            Parcel data = Parcel.obtain(), reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(VEHICLE_DESCRIPTOR);
                data.writeInt(canId);
                data.writeInt(1); // enable = true
                mVehicleBinder.transact(TX_NOTIFY_BCAN_RECEIVE, data, reply, 0);
                reply.readException();
                Log.d(TAG, "notifyBcanReceiveSetting canId=" + canId + " result=" + reply.readInt());
            } catch (Exception e) {
                Log.w(TAG, "notifyBcanReceiveSetting canId=" + canId + " failed: " + e.getMessage());
            } finally {
                data.recycle();
                reply.recycle();
            }
        }
    }

    private void transactRegisterListener(int tx, String descriptor, Binder listener) {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(descriptor);
            data.writeStrongBinder(listener);
            mVehicleBinder.transact(tx, data, reply, 0);
            reply.readException();
        } catch (Exception ignored) {
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private void poll() {
        // ── VehicleInfoManager API ────────────────────────────────────────────
        int speed  = vehicleInfo(VEHICLE_INFO_SPEED);
        int brake  = transactInt(mVehicleBinder, VEHICLE_DESCRIPTOR, TX_GET_FOOT_BRAKE);
        int pkBrk  = vehicleInfo(VEHICLE_INFO_PARKING_BRAKE);
        int lights = vehicleInfo(VEHICLE_INFO_ILLUMINATION);
        int shift  = vehicleInfo(VEHICLE_INFO_SHIFT);
        int gear   = vehicleInfo(VEHICLE_INFO_GEAR);
        int[] turns = transactIntArray(mVehicleBinder, VEHICLE_DESCRIPTOR, TX_GET_TURN_SIGNALS);

        // ── Audio (IAvApService, with AudioManager fallback) ──────────────────
        int source    = transactInt(mAvBinder, AV_DESCRIPTOR, TX_AV_GET_SOURCE);
        int volume    = transactIntParam(mAvBinder, AV_DESCRIPTOR, TX_AV_GET_VOLUME, AV_VOLUME_TYPE_MAIN);
        int volumeMax = transactIntParam(mAvBinder, AV_DESCRIPTOR, TX_AV_GET_VOLUME_MAX, AV_VOLUME_TYPE_MAIN);
        int muteInt   = transactInt(mAvBinder, AV_DESCRIPTOR, TX_AV_GET_MUTE);

        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        boolean musicPlaying = am != null && am.isMusicActive();
        if (volume < 0 && am != null)     volume    = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (volumeMax <= 0 && am != null) volumeMax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        // ── FCAN decoded values ───────────────────────────────────────────────
        Double fSpeed       = mFcanValues.get("XMISSION_SPEED");
        Double fRpm         = mFcanValues.get("ENGINE_RPM");
        Double fPedalGas    = mFcanValues.get("PEDAL_GAS");
        Double fBrake       = mFcanValues.get("BRAKE_PRESSED");
        Double fUserBrake   = mFcanValues.get("USER_BRAKE");
        Double fEspDisabled = mFcanValues.get("ESP_DISABLED");
        Double fComputerBraking = mFcanValues.get("COMPUTER_BRAKING");
        Double fAbsWarning  = mFcanValues.get("VSA_WARN_ABS");
        Double fBrakeWarn   = mFcanValues.get("VSA_WARN_BRAKE");
        Double fPunctureWarn = mFcanValues.get("VSA_WARN_PUNCTURE");
        Double fGear        = mFcanValues.get("GEAR_SHIFTER");
        Double fDisplaySpeed = mFcanValues.get("DISPLAY_SPEED");
        Double fFuel        = mFcanValues.get("FUEL_LEVEL");
        Double fBrakeFluid  = mFcanValues.get("BRAKE_FLUID_WARN");
        Double fWipers      = mFcanValues.get("WIPERS");
        Double fSeatWarn    = mFcanValues.get("SEATBELT_WARN");
        // FCAN 829 LKAS status
        Double fLkasActive  = mFcanValues.get("LKAS_ACTIVE");
        Double fLkasFailed  = mFcanValues.get("LKAS_FAILED");
        Double fLkasAdjMode = mFcanValues.get("LKAS_ADJ_MODE");

        // ── BCAN decoded values ───────────────────────────────────────────────
        Double fSteer    = mBcanValues.get("STEER_ANGLE");
        Double bSteerOk  = mBcanValues.get("STEER_SENSOR_OK");
        // Turn signals: FCAN meter 806 (fallback: BCAN HLSW)
        Double bLBlink   = mFcanValues.get("LEFT_BLINKER");
        if (bLBlink == null) bLBlink = mBcanValues.get("LEFT_BLINKER");
        Double bRBlink   = mFcanValues.get("RIGHT_BLINKER");
        if (bRBlink == null) bRBlink = mBcanValues.get("RIGHT_BLINKER");
        // Parking brake: FCAN meter 806 (fallback: BCAN AT)
        Double bPark     = mFcanValues.get("PARKING_BRAKE_ON");
        if (bPark == null) bPark = mBcanValues.get("PARKING_BRAKE_ON");
        Double bHeadlt   = mBcanValues.get("HEADLIGHTS_ON");
        Double bMainOn   = mBcanValues.get("MAIN_ON");
        Double bReverse  = mBcanValues.get("REVERSE_LIGHT");
        // ADAS display settings (BCAN_ID_MET_CUSTOM)
        Double bCmbsDist  = mBcanValues.get("CMBS_DISTANCE");
        Double bLkasBuzz  = mBcanValues.get("LKAS_BUZZER");
        Double bRdmStatus = mBcanValues.get("RDM_STATUS");
        // Maintenance (BCAN_ID_MAINTENANCE)
        Double bOilLife   = mBcanValues.get("OIL_LIFE");
        Double bMaintData = mBcanValues.get("MAINT_DATA");
        Double bMaintUnit = mBcanValues.get("MAINT_UNIT");

        // ── Build sub-states ──────────────────────────────────────────────────

        MotionState motion = MotionState.builder()
                .speedKmh(speed)
                .fcanSpeedKph(fSpeed != null ? fSpeed : Double.NaN)
                .displaySpeedKph(fDisplaySpeed != null ? (int) Math.round(fDisplaySpeed) : -1)
                .rpm(fRpm != null ? (int) Math.round(fRpm) : -1)
                .pedalGas(fPedalGas != null ? (int) Math.round(fPedalGas) : -1)
                .gasPressed(fPedalGas != null && fPedalGas > 0)
                .build();

        DrivetrainState drivetrain = DrivetrainState.builder()
                .shiftPosition(shift)
                .gear(gear)
                .gearShifter(fGear != null ? (int) Math.round(fGear) : -1)
                .reverseLight(bReverse != null && bReverse == 1.0)
                .build();

        BrakeState brakes = BrakeState.builder()
                .pedalPressed(brake == 1)
                .fcanBrakePressed(fBrake != null && fBrake == 1.0)
                .parkingBrake(pkBrk == 1)
                .parkingBrakeOn(bPark != null && bPark == 1.0)
                .userBrakeBar(fUserBrake != null ? fUserBrake : Double.NaN)
                .computerBraking(fComputerBraking != null && fComputerBraking == 1.0)
                .espDisabled(fEspDisabled != null && fEspDisabled == 1.0)
                .brakeFluidWarning(fBrakeFluid != null && fBrakeFluid == 1.0)
                .absWarning(fAbsWarning != null && fAbsWarning == 1.0)
                .brakeWarning(fBrakeWarn != null && fBrakeWarn == 1.0)
                .punctureWarning(fPunctureWarn != null && fPunctureWarn == 1.0)
                .build();

        SteeringState steering = SteeringState.builder()
                .angleDeg(fSteer != null ? fSteer : Double.NaN)
                .sensorOk(bSteerOk != null && bSteerOk == 1.0)
                .build();

        LightsState lightsState = LightsState.builder()
                .lightsOn(lights == 1)
                .turnLeft(turns != null && turns.length > 1 && turns[1] == 1)
                .turnRight(turns != null && turns.length > 0 && turns[0] == 1)
                .leftBlinker(bLBlink != null && bLBlink == 1.0)
                .rightBlinker(bRBlink != null && bRBlink == 1.0)
                .headlightsOn(bHeadlt != null && bHeadlt == 1.0)
                .wipers(fWipers != null ? (int) Math.round(fWipers) : -1)
                .mainOn(bMainOn != null && bMainOn == 1.0)
                .build();

        CabinState cabin = CabinState.builder()
                // seatbelt warning=1 means NOT latched; invert to get latched state
                .seatbeltDriver(fSeatWarn != null && fSeatWarn == 0.0)
                .build();

        FuelState fuel = FuelState.builder()
                .range(mBcanRange)
                .rangeInMiles(mBcanRangeUnit == 1)
                .level(fFuel != null ? (int) Math.round(fFuel) : -1)
                .build();

        AudioState audio = AudioState.builder()
                .source(source)
                .volume(Math.max(volume, 0))
                .volumeMax(Math.max(volumeMax, 1))
                .mute(muteInt == 1)
                .musicPlaying(musicPlaying)
                .build();

        AdasState adas = AdasState.builder()
                .lkasActive(fLkasActive != null && fLkasActive == 1.0)
                .lkasFailed(fLkasFailed != null && fLkasFailed == 1.0)
                .lkasAdjMode(fLkasAdjMode != null && fLkasAdjMode == 1.0)
                .cmbsDistance(bCmbsDist != null ? (int) Math.round(bCmbsDist) : -1)
                .lkasBuzzerStatus(bLkasBuzz != null ? (int) Math.round(bLkasBuzz) : -1)
                .rdmStatus(bRdmStatus != null ? (int) Math.round(bRdmStatus) : -1)
                .build();

        MaintenanceState maintenance = MaintenanceState.builder()
                .oilLife(bOilLife != null ? (int) Math.round(bOilLife) : -1)
                .maintData(bMaintData != null ? (int) Math.round(bMaintData) : -1)
                .maintUnit(bMaintUnit != null ? (int) Math.round(bMaintUnit) : -1)
                .build();

        VehicleSnapshot snapshot = new VehicleSnapshot(motion, drivetrain, brakes, steering,
                lightsState, cabin, fuel, audio, adas, maintenance);

        for (Listener l : mListeners) {
            l.onVehicleSnapshotChanged(snapshot);
        }

        // Poll IDiagService every ~1 s (4 × 250 ms ticks)
        if (++mDiagPollTick % 4 == 0) diagPoll();
    }

    // ── IDiagService helpers ──────────────────────────────────────────────────

    private void diagInitialize() {
        if (mDiagBinder == null) return;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DIAG_DESCRIPTOR);
            data.writeStrongBinder(mDiagListener);
            data.writeInt(0); // mode = 0
            mDiagBinder.transact(DIAG_TX_INITIALIZE, data, reply, 0);
            reply.readException();
            boolean ok = reply.readInt() != 0;
            Log.d(TAG, "DiagService init=" + ok);
        } catch (Exception e) {
            Log.e(TAG, "DiagService init error", e);
        } finally {
            data.recycle(); reply.recycle();
        }
    }

    private void diagPoll() {
        mDiagTick++;
        mDiagFcanReqId = transactDiagRequest(DIAG_CANDATA_GET_FCAN);
        mDiagBcanReqId = transactDiagRequest(DIAG_CANDATA_GET_BCAN);
    }

    private int transactDiagRequest(int requestKind) {
        if (mDiagBinder == null) return -1;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DIAG_DESCRIPTOR);
            data.writeInt(requestKind);
            data.writeInt(0);
            mDiagBinder.transact(DIAG_TX_REQUEST, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } catch (Exception e) {
            Log.e(TAG, "DiagService request error kind=" + requestKind, e);
            return -1;
        } finally {
            data.recycle(); reply.recycle();
        }
    }

    /**
     * Routes the notifyResponse bundle to the correct bus stats map.
     * requestId is matched against the most recent FCAN/BCAN request to determine bus.
     * Each key in the bundle is a Mitsubishi signal name; its value is a nested Bundle with
     * "value" (int, raw CAN signal integer) and "message_id" (int, actual CAN message ID).
     */
    private void processDiagBundle(int requestId, Bundle bundle) {
        diagDiagCallbacks++;
        boolean isFcan = (requestId == mDiagFcanReqId && requestId != -1);
        boolean isBcan = (requestId == mDiagBcanReqId && requestId != -1);
        ConcurrentHashMap<String, double[]> target =
                isFcan ? mDiagFcanStats : isBcan ? mDiagBcanStats : null;

        StringBuilder keys = new StringBuilder();
        for (String key : bundle.keySet()) {
            Object v = bundle.get(key);
            if (!(v instanceof Bundle)) continue;
            Bundle nested = (Bundle) v;
            int value = nested.getInt("value", Integer.MIN_VALUE);
            if (value == Integer.MIN_VALUE) continue;

            if (target != null) {
                double[] existing = target.get(key);
                boolean changed = (existing == null || (int) existing[2] != value);
                recordStats(key, value, target);
                if (changed) mDiagChangeTick.put(key, mDiagTick);
            }

            if (keys.length() > 0) keys.append(',');
            keys.append(key);
        }
        if (keys.length() > 0) {
            diagDiagLastKeys = keys.toString();
            Log.d(TAG, "DiagService reqId=" + requestId
                    + (isFcan ? " FCAN" : isBcan ? " BCAN" : " ?") + ": " + keys);
        }
    }

    // ── FCAN/BCAN typed bundle mapping ───────────────────────────────────────
    //
    // The middleware (VehicleInfoManagerApService) pre-decodes raw CAN frames
    // and delivers pre-decoded integer values in Bundles using Mitsubishi-internal
    // key names.  These methods translate those keys to DBC signal names, applying
    // DBC factor/offset where the raw integer is not already in physical units.
    //
    // FCAN messages forwarded by middleware (CAN ID → Bundle key → signal name):
    //   344 (ENGINE_DATA)     EAT_TRANS_SPEED_VNC ×0.01 → XMISSION_SPEED (kph)
    //   380 (POWERTRAIN_DATA) ENG_ENG_SPEED             → ENGINE_RPM (rpm)
    //                         ENG_DRIVER_ACPEDAL_POSITION → PEDAL_GAS (0-255)
    //                         ENG_SW_STATUS_BRAKE_NC    → BRAKE_PRESSED (0/1)
    //   420 (VSA_STATUS)      VSA_MASTER_CYLINDER_PRESSURE ×0.015625-1.609375 → USER_BRAKE
    //                         VSA_VSA_TCS_ACT           → COMPUTER_BRAKING (0/1)
    //                         VSA_WARN_STATUS_VSA       → ESP_DISABLED (0/1)
    //                         VSA_ABS_EBD_ACT           → ABS_EBD_ACT (0/1)
    //                         VSA_WARN_STATUS_ABS       → VSA_WARN_ABS (0/1)
    //                         VSA_WARN_STATUS_BRAKE     → VSA_WARN_BRAKE (0/1)
    //                         VSA_WARN_STATUS_PUNCTURE  → VSA_WARN_PUNCTURE (0/1)
    //   401 (Honda CVT)       CVT_SHFT_IN_P/R/N/D/L/S  → GEAR_SHIFTER (1/2/3/4/7/10)
    //   806 (Honda FCAN meter) METER_TURN_SIGNAL_R/L_TURN → RIGHT/LEFT_BLINKER (0/1)
    //                          METER_SW_STATUS_PARK_BRAKE → PARKING_BRAKE_ON (0/1)
    //                          METER_FUEL_LEVEL           → FUEL_LEVEL (0-255)
    //   829 (0x33D LKAS)      LKAS_STATUS_ACTIVE_33D    → LKAS_ACTIVE (0/1)
    //                         LKAS_STATUS_FAILED_33D    → LKAS_FAILED (0/1)
    //                         LKAS_STATUS_ADJ_MODE_33D  → LKAS_ADJ_MODE (0/1)
    //   884 (Honda FCAN meter) METER_WIPER_STATUS        → WIPERS (0/2/4)
    //                          METER_SEAT_BELT_WRN_RMD  → SEATBELT_WARN (1=not latched)
    //   777 (Honda FCAN meter) METER_DISPLAY_SPEED       → DISPLAY_SPEED (kph)
    //
    // BCAN messages forwarded by middleware (proprietary BCAN_ID → Bundle key → signal name):
    //   BCAN_ID_STEERING   C_STR_ANGLE ×0.1             → STEER_ANGLE (deg)
    //   BCAN_ID_AT         C_TRANSMISSION               → GEAR_SHIFTER
    //                      C_PBRAKE                     → PARKING_BRAKE_ON
    //   BCAN_ID_HLSW_BCM   C_TURNL/R                   → LEFT/RIGHT_BLINKER
    //   BCAN_ID_MICU_BCM   C_BACKLTSW                  → REVERSE_LIGHT
    //                      C_IG1                        → MAIN_ON
    //   BCAN_ID_ILLUMI     C_METER_ILL_STATUS          → HEADLIGHTS_ON
    //   BCAN_ID_TRICOM     C_TRICOM_RANGE              → fuel range (handled separately)
    //   BCAN_ID_VSPNE      C_VSP                       → CAR_SPEED_BCAN
    //                      C_NE                        → BCAN_RPM
    //   BCAN_ID_MAINTENANCE C_MAINT_OIL_LIFE           → OIL_LIFE
    //                        C_MAINT_DATA              → MAINT_DATA
    //                        C_MAINT_UNIT              → MAINT_UNIT
    //   BCAN_ID_MET_CUSTOM C_MET_CUSTOM_CMBS_DISTANCE  → CMBS_DISTANCE
    //                      C_MET_CUSTOM_LKAS_BUZZER_STATUS → LKAS_BUZZER
    //                      C_MET_CUSTOM_RDM_STATUS     → RDM_STATUS

    private int mapFcanBundle(int type, Bundle info) {
        int count = 0;
        switch (type) {
            case 344: // ENGINE_DATA: transmission speed from EAT
                count += putFcan("XMISSION_SPEED",  info, "EAT_TRANS_SPEED_VNC", 0.01, 0.0);
                count += putFcan("XMISSION_SPEED2", info, "EAT_TRANS_SPEED",      0.01, 0.0);
                break;
            case 380: // POWERTRAIN_DATA: engine speed, pedal, brake switches
                count += putFcanRaw("ENGINE_RPM",    info, "ENG_ENG_SPEED");
                count += putFcanRaw("PEDAL_GAS",     info, "ENG_DRIVER_ACPEDAL_POSITION");
                count += putFcanRaw("BRAKE_SWITCH",  info, "ENG_SW_STATUS_BRAKE_NO");
                count += putFcanRaw("BRAKE_PRESSED", info, "ENG_SW_STATUS_BRAKE_NC");
                break;
            case 420: // VSA_STATUS: brake pressure, stability control flags
                count += putFcan("USER_BRAKE",          info, "VSA_MASTER_CYLINDER_PRESSURE",
                                 0.015625, -1.609375);
                count += putFcanRaw("COMPUTER_BRAKING",  info, "VSA_VSA_TCS_ACT");
                count += putFcanRaw("ESP_DISABLED",      info, "VSA_WARN_STATUS_VSA");
                count += putFcanRaw("ABS_EBD_ACT",       info, "VSA_ABS_EBD_ACT");
                count += putFcanRaw("VSA_WARN_ABS",      info, "VSA_WARN_STATUS_ABS");
                count += putFcanRaw("VSA_WARN_BRAKE",    info, "VSA_WARN_STATUS_BRAKE");
                count += putFcanRaw("VSA_WARN_PUNCTURE", info, "VSA_WARN_STATUS_PUNCTURE");
                break;
            case 401: { // Honda CVT: individual shift-gate bits → GEAR_SHIFTER enum
                int gear = decodeCvtGear(info);
                if (gear >= 0) {
                    mFcanValues.put("GEAR_SHIFTER", (double) gear);
                    recordStats("GEAR_SHIFTER", gear, mFcanStats);
                    count++;
                }
                count += putFcanRaw("GEAR_POSITION_IND", info, "CVT_GEAR_POSITION_IND_CVT");
                break;
            }
            case 806: // Honda FCAN meter (0x326): fuel level, turn signals, parking brake
                count += putFcanRaw("RIGHT_BLINKER",    info, "METER_TURN_SIGNAL_R_TURN");
                count += putFcanRaw("LEFT_BLINKER",     info, "METER_TURN_SIGNAL_L_TURN");
                count += putFcanRaw("PARKING_BRAKE_ON", info, "METER_SW_STATUS_PARK_BRAKE");
                count += putFcanRaw("FUEL_LEVEL",       info, "METER_FUEL_LEVEL");
                count += putFcanRaw("BRAKE_FLUID_WARN", info, "METER_SW_STATUS_BRAKE_FLUID");
                break;
            case 829: // LKAS status (0x33D)
                count += putFcanRaw("LKAS_ACTIVE",   info, "LKAS_STATUS_ACTIVE_33D");
                count += putFcanRaw("LKAS_FAILED",   info, "LKAS_STATUS_FAILED_33D");
                count += putFcanRaw("LKAS_ADJ_MODE", info, "LKAS_STATUS_ADJ_MODE_33D");
                break;
            case 884: // Honda FCAN meter (0x374): wiper, seatbelt warning
                count += putFcanRaw("WIPERS",       info, "METER_WIPER_STATUS");
                count += putFcanRaw("SEATBELT_WARN",info, "METER_SEAT_BELT_WRN_RMD");
                count += putFcanRaw("PARK_BRAKE_WARN", info, "METER_PARK_BRAKE_WRN_RMD");
                break;
            case 777: // Honda FCAN meter (0x309): speedometer display value
                count += putFcanRaw("DISPLAY_SPEED", info, "METER_DISPLAY_SPEED");
                break;
        }

        // Record every integer-valued key for raw signal discovery.
        recordAllBundleInts(info, type, mFcanRaw, mFcanRawChangedAt);

        // Generic fallback: raw CAN frame byte[] → CanDecoder.
        byte[] frame = extractFrame(info);
        diagFcanHasRawFrame = (frame != null);
        if (frame != null) {
            Map<String, Double> decoded = CanDecoder.decode(type, frame, CanDefinitions.FCAN_MESSAGES);
            for (Map.Entry<String, Double> e : decoded.entrySet()) {
                mFcanValues.put(e.getKey(), e.getValue());
                recordStats(e.getKey(), e.getValue(), mFcanStats);
                count++;
            }
            if (!decoded.isEmpty())
                Log.d(TAG, "FCAN raw-decoded type=" + type + " → " + decoded.size() + " signals");
        }
        return count;
    }

    private int mapBcanBundle(int type, Bundle info) {
        int count = 0;
        if (type == BCAN_ID_TRICOM) {
            int r = info.getInt(BCAN_KEY_RANGE, -1);
            if (r >= 0) {
                mBcanRange = r;
                mBcanRangeUnit = info.getInt(BCAN_KEY_RANGE_UNIT, 0);
                count++;
            }
        } else if (type == BCAN_ID_STEERING) {
            int raw = info.getInt("C_STR_ANGLE", Integer.MIN_VALUE);
            if (raw != Integer.MIN_VALUE) {
                double deg = raw * 0.1;
                mBcanValues.put("STEER_ANGLE", deg);
                recordStats("STEER_ANGLE", deg, mBcanStats);
                count++;
            }
            count += putBcanRaw("STEER_SENSOR_OK", info, "C_STR_STATE_OK");
        } else if (type == BCAN_ID_AT) {
            count += putBcanRaw("GEAR_SHIFTER",     info, "C_TRANSMISSION");
            count += putBcanRaw("PARKING_BRAKE_ON", info, "C_PBRAKE");
        } else if (type == BCAN_ID_HLSW_BCM || type == BCAN_ID_HLSW_ICU) {
            count += putBcanRaw("LEFT_BLINKER",  info, "C_TURNL");
            count += putBcanRaw("RIGHT_BLINKER", info, "C_TURNR");
        } else if (type == BCAN_ID_MICU_BCM || type == BCAN_ID_MICU_ICU) {
            count += putBcanRaw("REVERSE_LIGHT",   info, "C_BACKLTSW");
            count += putBcanRaw("TURN_L_ONETOUCH", info, "C_TURN_L_ONETOUCH");
            count += putBcanRaw("TURN_R_ONETOUCH", info, "C_TURN_R_ONETOUCH");
            count += putBcanRaw("MAIN_ON",         info, "C_IG1");
        } else if (type == BCAN_ID_ILLUMI) {
            count += putBcanRaw("HEADLIGHTS_ON", info, "C_METER_ILL_STATUS");
            count += putBcanRaw("ILL_STEP",      info, "C_ILSTEP");
        } else if (type == BCAN_ID_VSPNE) {
            count += putBcanRaw("CAR_SPEED_BCAN", info, "C_VSP");
            count += putBcanRaw("BCAN_RPM",        info, "C_NE");
        } else if (type == BCAN_ID_MAINTENANCE) {
            count += putBcanRaw("OIL_LIFE",   info, "C_MAINT_OIL_LIFE");
            count += putBcanRaw("MAINT_DATA", info, "C_MAINT_DATA");
            count += putBcanRaw("MAINT_UNIT", info, "C_MAINT_UNIT");
        } else if (type == BCAN_ID_MET_CUSTOM) {
            count += putBcanRaw("CMBS_DISTANCE", info, "C_MET_CUSTOM_CMBS_DISTANCE");
            count += putBcanRaw("LKAS_BUZZER",   info, "C_MET_CUSTOM_LKAS_BUZZER_STATUS");
            count += putBcanRaw("RDM_STATUS",    info, "C_MET_CUSTOM_RDM_STATUS");
        }

        // Record every integer-valued key for raw signal discovery.
        recordAllBundleInts(info, type, mBcanRaw, mBcanRawChangedAt);

        // Generic fallback: raw CAN frame byte[] → CanDecoder for any matching BCAN message.
        byte[] frame = extractFrame(info);
        diagBcanHasRawFrame = (frame != null);
        if (frame != null) {
            Map<String, Double> decoded = CanDecoder.decode(type, frame, CanDefinitions.BCAN_MESSAGES);
            for (Map.Entry<String, Double> e : decoded.entrySet()) {
                mBcanValues.put(e.getKey(), e.getValue());
                recordStats(e.getKey(), e.getValue(), mBcanStats);
                count++;
            }
            if (!decoded.isEmpty())
                Log.d(TAG, "BCAN raw-decoded type=" + type + " → " + decoded.size() + " signals");
        }
        return count;
    }

    /**
     * Records every Integer-valued entry in {@code info} into {@code raw} and {@code changedAt}.
     * Key format: "typeDecimal/SIGNAL_NAME"  e.g. "380/ENG_ENG_SPEED".
     * Only updates {@code changedAt} when the integer value actually changes.
     */
    private static void recordAllBundleInts(Bundle info, int type,
                                             ConcurrentHashMap<String, double[]> raw,
                                             ConcurrentHashMap<String, Long> changedAt) {
        long now = System.currentTimeMillis();
        String prefix = type + "/";
        for (String bk : info.keySet()) {
            Object bv = info.get(bk);
            if (!(bv instanceof Integer)) continue;
            String ck = prefix + bk;
            int iv = (Integer) bv;
            double[] ex = raw.get(ck);
            boolean changed = (ex == null || (int) ex[2] != iv);
            recordStats(ck, iv, raw);
            if (changed) changedAt.put(ck, now);
        }
    }

    /**
     * Try common Bundle key names for a raw CAN frame byte[].
     * Returns null if none found or the array is too short to be useful.
     */
    private static byte[] extractFrame(Bundle info) {
        for (String key : new String[]{"data", "payload", "frame", "msg", "raw"}) {
            byte[] b = info.getByteArray(key);
            if (b != null && b.length >= 1) return b;
        }
        return null;
    }

    private static String joinTypes(Set<Integer> types) {
        StringBuilder sb = new StringBuilder();
        for (int t : types) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(t).append("(0x").append(Integer.toHexString(t)).append(')');
        }
        return sb.toString();
    }

    /** Decode Honda CVT shift-gate individual bits to DBC GEAR_SHIFTER enum values. */
    private static int decodeCvtGear(Bundle info) {
        if (info.getInt("CVT_SHFT_IN_PARKING", 0) == 1) return 1;  // P
        if (info.getInt("CVT_SHFT_IN_REVERSE", 0) == 1) return 2;  // R
        if (info.getInt("CVT_SHFT_IN_NEUTRAL", 0) == 1) return 3;  // N
        if (info.getInt("CVT_SHFT_IN_D",       0) == 1) return 4;  // D
        if (info.getInt("CVT_SHFT_IN_L",       0) == 1) return 7;  // L
        if (info.getInt("CVT_SHFT_IN_S",       0) == 1) return 10; // S
        return -1;
    }

    /** Read an int from Bundle, apply DBC factor/offset, and write to mFcanValues. */
    private int putFcan(String signal, Bundle info, String key, double factor, double offset) {
        int raw = info.getInt(key, Integer.MIN_VALUE);
        if (raw == Integer.MIN_VALUE) return 0;
        double val = raw * factor + offset;
        mFcanValues.put(signal, val);
        recordStats(signal, val, mFcanStats);
        return 1;
    }

    private int putFcanRaw(String signal, Bundle info, String key) {
        return putFcan(signal, info, key, 1.0, 0.0);
    }

    /** Read an int from Bundle, apply DBC factor/offset, and write to mBcanValues. */
    private int putBcan(String signal, Bundle info, String key, double factor, double offset) {
        int raw = info.getInt(key, Integer.MIN_VALUE);
        if (raw == Integer.MIN_VALUE) return 0;
        double val = raw * factor + offset;
        mBcanValues.put(signal, val);
        recordStats(signal, val, mBcanStats);
        return 1;
    }

    private int putBcanRaw(String signal, Bundle info, String key) {
        return putBcan(signal, info, key, 1.0, 0.0);
    }

    /** Accumulate all keys seen in this Bundle into the shared set. */
    private static void trackBundleKeys(Bundle info, Set<String> seen) {
        seen.addAll(info.keySet());
    }

    private static String joinKeys(Set<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(k);
        }
        return sb.toString();
    }

    /**
     * Log every key and its value/type in the Bundle so the full payload is visible in logcat.
     * Example output: [RPM=Int(3200), data=byte[8], FOO=String(bar)]
     */
    private static String describeBundle(Bundle info) {
        if (info == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        for (String key : info.keySet()) {
            Object val = info.get(key);
            if (sb.length() > 1) sb.append(", ");
            sb.append(key).append("=");
            if (val instanceof byte[]) {
                byte[] b = (byte[]) val;
                sb.append("byte[").append(b.length).append("]{");
                for (int i = 0; i < Math.min(b.length, 8); i++) {
                    if (i > 0) sb.append(' ');
                    sb.append(String.format("%02X", b[i] & 0xFF));
                }
                sb.append('}');
            } else if (val != null) {
                sb.append(val.getClass().getSimpleName()).append('(').append(val).append(')');
            } else {
                sb.append("null");
            }
        }
        return sb.append(']').toString();
    }

    private static void recordStats(String key, double value,
                                    ConcurrentHashMap<String, double[]> stats) {
        double[] s = stats.get(key);
        if (s == null) {
            stats.put(key, new double[]{value, value, value});
        } else {
            if (value < s[0]) s[0] = value;
            if (value > s[1]) s[1] = value;
            s[2] = value;
        }
    }

    /** Returns a comma-separated "key=value" string for the first {@code max} entries. */
    private static String sampleValues(Map<String, Double> values, int max) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, Double> e : values.entrySet()) {
            if (i++ >= max) break;
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    private int vehicleInfo(int type) {
        return transactIntParam(mVehicleBinder, VEHICLE_DESCRIPTOR, TX_GET_VEHICLE_INFO, type);
    }

    private int transactInt(IBinder binder, String descriptor, int tx) {
        if (binder == null) return -1;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(descriptor);
            binder.transact(tx, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } catch (Exception e) {
            return -1;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private int transactIntParam(IBinder binder, String descriptor, int tx, int param) {
        if (binder == null) return -1;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(descriptor);
            data.writeInt(param);
            binder.transact(tx, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } catch (Exception e) {
            return -1;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private int[] transactIntArray(IBinder binder, String descriptor, int tx) {
        if (binder == null) return null;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(descriptor);
            binder.transact(tx, data, reply, 0);
            reply.readException();
            return reply.createIntArray();
        } catch (Exception e) {
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}
