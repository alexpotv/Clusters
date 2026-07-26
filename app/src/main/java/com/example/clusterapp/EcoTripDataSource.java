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

/**
 * Eco / trip-computer data via {@code VehicleCoordinationService}'s
 * {@code IEcoAndTripInformationManager} (see reference-docs/can-analysis.md §7.2).
 *
 * <p>This interface is served by the same {@code VEHICLE_RW}-gated service as the climate manager,
 * so it costs no extra privilege beyond the system install this app already relies on — yet nothing
 * bound it before, leaving the whole fuel-economy / trip / eco-score domain unparsed (see
 * can-inventory.md §4b). This source binds it, polls the small getters, and registers the fuel
 * push callback.
 *
 * <p>Reliability note (identical caveat to {@link ClimateDataSource}): §7.2 documents the getter
 * transaction codes and the field <i>names</i> of each returned Parcelable, but not their exact
 * on-wire order or primitive widths. The reads below assume the AIDL convention — a presence int
 * followed by the fields as {@code int}s in declaration order (value, then unit, then any extras).
 * Every read is guarded, so a wrong guess yields no/garbage data rather than a crash, and the Debug
 * tab surfaces the raw values so the true layout can be confirmed against the car live. Fields are
 * therefore labelled "best-effort" until validated on the vehicle.
 */
public class EcoTripDataSource {

    private static final String TAG = "ECOTRIP";

    private static final String VC_PKG = "com.mitsubishielectric.ada.appservice.vehiclecoordination";
    private static final String VC_CLS = VC_PKG + ".VehicleCoordinationService";
    private static final String ET_DESCRIPTOR = VC_PKG + ".IEcoAndTripInformationManager";
    private static final String CB_FUEL_DESC   = VC_PKG + ".IFuelConsumptionCanDataCallback";

    // Getter transaction codes (§7.2).
    private static final int TX_INSTANT_FUEL_EFF   = 1;
    private static final int TX_AVERAGE_FUEL_EFF   = 2;
    private static final int TX_TRIP_A_AND_AVG     = 3;
    private static final int TX_DISTANCE_TO_EMPTY  = 4;
    private static final int TX_ECO_TOTAL_SCORE    = 7;
    private static final int TX_ECO_DETAIL_SCORE   = 8;
    private static final int TX_REG_FUEL_CALLBACK  = 15;

    private static EcoTripDataSource sInstance;
    public static EcoTripDataSource getInstance() { return sInstance; }

    private Context mContext;
    private IBinder mBinder;
    private boolean mBound = false;
    private final Handler mHandler = new Handler();
    private int mPollMs = 1000;

    // ── Observed state (written on the poll/binder threads, read on the main thread) ──
    public volatile boolean connected     = false;
    public volatile int  fuelCallbacks    = 0;
    public volatile long lastUpdateMs     = 0;

    // Instant / average fuel efficiency (value + its own unit selector byte).
    public volatile int instantFuelEff     = Integer.MIN_VALUE;
    public volatile int instantFuelEffUnit = Integer.MIN_VALUE;
    public volatile int averageFuelEff     = Integer.MIN_VALUE;
    public volatile int averageFuelEffUnit = Integer.MIN_VALUE;
    // Range remaining.
    public volatile int distanceToEmpty     = Integer.MIN_VALUE;
    public volatile int distanceToEmptyUnit = Integer.MIN_VALUE;
    // Trip A (meter + its average fuel efficiency), best-effort leading ints.
    public volatile int tripAMeter          = Integer.MIN_VALUE;
    public volatile int tripAAvgFuelEff      = Integer.MIN_VALUE;
    // Eco scores.
    public volatile int ecoScore     = Integer.MIN_VALUE;
    public volatile int ecoClass     = Integer.MIN_VALUE;
    public volatile int ecoLifeScore = Integer.MIN_VALUE;
    // Push fuel value from the callback.
    public volatile int pushFuelValue = Integer.MIN_VALUE;

    // ── Fuel push callback ─────────────────────────────────────────────────────

    private final Binder mFuelCb = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code != 1) return false; // the interface's single notify method
            try {
                data.enforceInterface(CB_FUEL_DESC);
                // Leading int is the fuel-consumption value; remaining fields are unknown → ignored.
                pushFuelValue = data.readInt();
                fuelCallbacks++;
                lastUpdateMs = System.currentTimeMillis();
            } catch (Exception e) {
                Log.e(TAG, "fuel cb parse error", e);
            }
            if (reply != null) reply.writeNoException();
            return true;
        }
    };

    private final ServiceConnection mConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            mBinder = b;
            connected = true;
            registerFuelCallback();
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

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    public void start(Context context) {
        mContext = context;
        sInstance = this;
        try {
            Intent i = new Intent(ET_DESCRIPTOR);
            i.setComponent(new ComponentName(VC_PKG, VC_CLS));
            mBound = mContext.bindService(i, mConn, Context.BIND_AUTO_CREATE);
            if (!mBound) Log.w(TAG, "VehicleCoordinationService (eco) bind returned false (missing VEHICLE_RW?)");
        } catch (Exception e) {
            Log.w(TAG, "VehicleCoordinationService (eco) bind failed: " + e.getMessage());
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

    // ── Internals ──────────────────────────────────────────────────────────────

    private void registerFuelCallback() {
        if (mBinder == null) return;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(ET_DESCRIPTOR);
            data.writeStrongBinder(mFuelCb);
            mBinder.transact(TX_REG_FUEL_CALLBACK, data, reply, 0);
            reply.readException();
            Log.d(TAG, "registered fuel callback");
        } catch (Exception e) {
            Log.w(TAG, "register fuel callback failed: " + e.getMessage());
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private void poll() {
        int[] ife = transactInts(TX_INSTANT_FUEL_EFF, 2);
        if (ife != null) { instantFuelEff = ife[0]; instantFuelEffUnit = ife[1]; touch(); }

        int[] afe = transactInts(TX_AVERAGE_FUEL_EFF, 2);
        if (afe != null) { averageFuelEff = afe[0]; averageFuelEffUnit = afe[1]; touch(); }

        int[] dte = transactInts(TX_DISTANCE_TO_EMPTY, 2);
        if (dte != null) { distanceToEmpty = dte[0]; distanceToEmptyUnit = dte[1]; touch(); }

        int[] trip = transactInts(TX_TRIP_A_AND_AVG, 2);
        if (trip != null) { tripAMeter = trip[0]; tripAAvgFuelEff = trip[1]; touch(); }

        int[] eco = transactInts(TX_ECO_TOTAL_SCORE, 3);
        if (eco != null) { ecoScore = eco[0]; ecoClass = eco[1]; ecoLifeScore = eco[2]; touch(); }
    }

    private void touch() {
        connected = true;
        lastUpdateMs = System.currentTimeMillis();
    }

    /**
     * Transact a getter that returns a Parcelable and read the first {@code count} ints of its
     * body (after the AIDL presence flag). Returns {@code null} if the service returned no object,
     * the read underflowed, or the call failed — never throws.
     */
    private int[] transactInts(int tx, int count) {
        if (mBinder == null) return null;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(ET_DESCRIPTOR);
            mBinder.transact(tx, data, reply, 0);
            reply.readException();
            if (reply.readInt() == 0) return null; // null Parcelable
            int[] out = new int[count];
            for (int i = 0; i < count; i++) out[i] = reply.readInt();
            return out;
        } catch (Exception e) {
            return null;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}
