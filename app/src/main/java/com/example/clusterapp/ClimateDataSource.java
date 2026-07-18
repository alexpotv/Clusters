package com.example.clusterapp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HVAC / climate data via {@code VehicleCoordinationService}'s {@code IAirConditionerManager}.
 *
 * <p>Climate is <b>not</b> on the vehicle-info CAN path — it is served by a separate bound service
 * gated by the same {@code VEHICLE_RW} permission as {@code CpuComService}
 * (see reference-docs/can-analysis.md §7). This source binds that service, registers the push
 * callbacks (full A/C panel, on/off status, analog sensors), and polls the simple getters.
 *
 * <p>Reliability note: unlike the CpuComService transport, §7 documents the register-transaction
 * codes and field <i>names</i> but not every callback descriptor / method code / Parcel layout.
 * The low-level details below (callback interface names, single-method code = 1, the standard AIDL
 * Parcelable presence int, and {@code AirConditionerDisplayData} field order) are inferred from the
 * documented shapes and the usual AIDL conventions. Every read is guarded, so a wrong guess yields
 * no data rather than a crash — and the raw A/C BCAN frames still surface in {@link
 * com.example.clusterapp.screens.CanDataScreen}'s CpuComService section as ground truth.
 */
public class ClimateDataSource {

    private static final String TAG = "CLIMATE";

    private static final String VC_PKG = "com.mitsubishielectric.ada.appservice.vehiclecoordination";
    private static final String VC_CLS = VC_PKG + ".VehicleCoordinationService";
    private static final String AC_DESCRIPTOR = VC_PKG + ".IAirConditionerManager";

    // Callback interface descriptors (inferred from the interface names in §7.1).
    private static final String CB_DISPLAY_DESC = VC_PKG + ".IAirConditionerDisplayDataCallback";
    private static final String CB_STATUS_DESC  = VC_PKG + ".IAirConditionerStatusCallback";
    private static final String CB_SENSOR_DESC  = VC_PKG + ".IAirConditionerSensorDataCallback";

    // Register-callback transaction codes on IAirConditionerManager (§7.1).
    private static final int TX_REG_DISPLAY = 21;
    private static final int TX_REG_STATUS  = 23;
    private static final int TX_REG_SENSOR  = 27;
    // Polled getters (§7.1).
    private static final int TX_GET_CONNECTION_STATUS = 3;
    private static final int TX_GET_HANDLE_POSITION   = 12;

    private static ClimateDataSource sInstance;
    public static ClimateDataSource getInstance() { return sInstance; }

    private Context mContext;
    private IBinder mBinder;
    private boolean mBound = false;
    private final Handler mHandler = new Handler();
    private int mPollMs = 500;

    // ── Observed state (written from binder threads, read on the main thread) ──
    public volatile boolean connected        = false;
    public volatile int  displayCallbacks    = 0;
    public volatile int  statusCallbacks     = 0;
    public volatile int  sensorCallbacks     = 0;

    // From the full-panel callback (AirConditionerDisplayData).
    public volatile int  acMode          = -1;
    public volatile int  acStatus        = -1;   // A/C on/off
    public volatile int  drSetpointIdx   = -1;   // encoded driver setpoint
    public volatile int  asSetpointIdx   = -1;   // encoded passenger setpoint
    public volatile int  fanVolume       = -1;
    public volatile int  connectionStatus = -1;  // BCAN receive-state for the source frame

    // From the on/off status callback and the getters.
    public volatile int  statusAcState   = -1;
    public volatile int  handlePosition  = -1;   // LHD/RHD steering side
    public volatile int  getterConnStatus = -1;
    public volatile long lastUpdateMs    = 0;

    // Analog sensors keyed by their reported id (cabin / ambient temperatures, etc.).
    private final ConcurrentHashMap<Integer, Integer> mSensors = new ConcurrentHashMap<>();
    public Map<Integer, Integer> getSensors() { return mSensors; }

    // ── Callback stubs ────────────────────────────────────────────────────────

    private final Binder mDisplayCb = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code != 1) return false; // notifyAcDisplayData — the interface's single method
            try {
                data.enforceInterface(CB_DISPLAY_DESC);
                if (data.readInt() != 0) {           // AIDL Parcelable presence flag
                    // AirConditionerDisplayData field order (§7.1).
                    acMode           = data.readInt();
                    acStatus         = data.readInt();
                    drSetpointIdx    = data.readInt();
                    asSetpointIdx    = data.readInt();
                    fanVolume        = data.readInt();
                    connectionStatus = data.readInt();
                    lastUpdateMs     = System.currentTimeMillis();
                }
                displayCallbacks++;
            } catch (Exception e) {
                Log.e(TAG, "display cb parse error", e);
            }
            if (reply != null) reply.writeNoException();
            return true;
        }
    };

    private final Binder mStatusCb = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code != 1) return false; // notifyAcStatus(int acState, boolean bcanStatus)
            try {
                data.enforceInterface(CB_STATUS_DESC);
                statusAcState = data.readInt();
                data.readInt();                       // bcanStatus (bool)
                statusCallbacks++;
                lastUpdateMs = System.currentTimeMillis();
            } catch (Exception e) {
                Log.e(TAG, "status cb parse error", e);
            }
            if (reply != null) reply.writeNoException();
            return true;
        }
    };

    private final Binder mSensorCb = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code != 1) return false; // notifySensorValue(int id, int value, boolean bcanStatus)
            try {
                data.enforceInterface(CB_SENSOR_DESC);
                int id    = data.readInt();
                int value = data.readInt();
                data.readInt();                       // bcanStatus (bool)
                mSensors.put(id, value);
                sensorCallbacks++;
                lastUpdateMs = System.currentTimeMillis();
            } catch (Exception e) {
                Log.e(TAG, "sensor cb parse error", e);
            }
            if (reply != null) reply.writeNoException();
            return true;
        }
    };

    private final ServiceConnection mConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            mBinder = b;
            connected = true;
            registerCallback(TX_REG_DISPLAY, mDisplayCb);
            registerCallback(TX_REG_STATUS,  mStatusCb);
            registerCallback(TX_REG_SENSOR,  mSensorCb);
        }
        @Override public void onServiceDisconnected(ComponentName n) {
            mBinder = null;
            connected = false;
        }
    };

    private final Runnable mPoll = new Runnable() {
        @Override public void run() {
            poll();
            mHandler.postDelayed(this, mPollMs);
        }
    };

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start(Context context) {
        mContext = context;
        sInstance = this;
        try {
            Intent i = new Intent(AC_DESCRIPTOR);
            i.setComponent(new ComponentName(VC_PKG, VC_CLS));
            mBound = mContext.bindService(i, mConn, Context.BIND_AUTO_CREATE);
            if (!mBound) Log.w(TAG, "VehicleCoordinationService bind returned false (missing VEHICLE_RW?)");
        } catch (Exception e) {
            Log.w(TAG, "VehicleCoordinationService bind failed: " + e.getMessage());
        }
        mHandler.post(mPoll);
    }

    public void stop() {
        mHandler.removeCallbacks(mPoll);
        if (mBound) {
            try { mContext.unbindService(mConn); } catch (Exception ignored) {}
            mBound = false;
            mBinder = null;
            connected = false;
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void registerCallback(int tx, Binder cb) {
        if (mBinder == null) return;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(AC_DESCRIPTOR);
            data.writeStrongBinder(cb);
            mBinder.transact(tx, data, reply, 0);
            reply.readException();
            Log.d(TAG, "registered AC callback tx=" + tx);
        } catch (Exception e) {
            Log.w(TAG, "register AC callback tx=" + tx + " failed: " + e.getMessage());
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private void poll() {
        int conn = transactInt(TX_GET_CONNECTION_STATUS);
        if (conn != Integer.MIN_VALUE) getterConnStatus = conn;
        int handle = transactInt(TX_GET_HANDLE_POSITION);
        if (handle != Integer.MIN_VALUE) handlePosition = handle;
    }

    private int transactInt(int tx) {
        if (mBinder == null) return Integer.MIN_VALUE;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(AC_DESCRIPTOR);
            mBinder.transact(tx, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    /**
     * Best-effort decode of an encoded setpoint index to °C, per §7.1 (15.0–32.0 in 0.5° steps).
     * Returns {@code Double.NaN} when the index falls outside that ladder (encoding unknown).
     */
    public static double setpointCelsius(int idx) {
        if (idx < 0) return Double.NaN;
        double t = 15.0 + 0.5 * idx;
        return (t >= 15.0 && t <= 32.0) ? t : Double.NaN;
    }
}
