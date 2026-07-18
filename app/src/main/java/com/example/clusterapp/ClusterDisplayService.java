package com.example.clusterapp;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.IBinder;
import android.os.Parcel;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.example.clusterapp.screens.AdasScreen;
import com.example.clusterapp.screens.AudioScreen;
import com.example.clusterapp.screens.BrakeScreen;
import com.example.clusterapp.screens.CabinScreen;
import com.example.clusterapp.screens.CanDataScreen;
import com.example.clusterapp.screens.ClimateScreen;
import com.example.clusterapp.screens.DashboardScreen;
import com.example.clusterapp.screens.DrivetrainScreen;
import com.example.clusterapp.screens.FuelScreen;
import com.example.clusterapp.screens.LaneWatchScreen;
import com.example.clusterapp.screens.LightsScreen;
import com.example.clusterapp.screens.MaintenanceScreen;
import com.example.clusterapp.screens.MotionScreen;
import com.example.clusterapp.screens.PlaceholderScreen;
import com.example.clusterapp.screens.SteeringScreen;
import com.example.clusterapi.ClusterPlugin;
import com.example.clusterapi.PluginContext;
import com.example.clusterapi.VehicleSnapshot;

public class ClusterDisplayService extends Service {

    static volatile String sStatus    = "not started";
    static volatile String sMuxStatus = "not attempted";

    /** APK path of the currently-active screen (always the host APK now that plugins are gone). */
    static volatile String sActiveApkPath = null;
    /** Settings namespace for the currently-active screen ("builtin.N"). */
    static volatile String sActiveNamespace = null;

    // Pixel offsets within the 800×480 HDMI output that map to the visible cluster area.
    static final int CANVAS_X1 = 50;
    static final int CANVAS_Y1 = 25;
    static final int CANVAS_X2 = 535;
    static final int CANVAS_Y2 = 215;

    private static final String EXT_PKG        = "com.mitsubishielectric.ada.appservice.externaldisplay";
    private static final String EXT_CLS        = EXT_PKG + ".ExternalDisplayApService";
    private static final String EXT_DESCRIPTOR = EXT_PKG + ".IExternalDisplayApService";
    private static final int    TX_LVDS        = 9;
    private static final int    CONTENT_AUDIO  = 64;

    private static final String VID_DESCRIPTOR = "com.mitsubishielectric.ada.appservice.videomanager.IVideoManagerApService";
    private static final int    TX_SHOW_AUTO   = 1;
    private static final int    TX_HIDE_AUTO   = 2;
    private static final int    TX_OPEN_PATH   = 6;
    private static final int    TX_CLOSE_PATH  = 7;
    private static final int    VIDEO_TYPE_LWC = 1;

    static ClusterDisplayService sInstance;

    private TextView mDebugOverlay;

    private FrameLayout      mOverlay;
    private FrameLayout      mContentContainer;
    private Context          mClusterContext;
    private WindowManager    mWindowManager;
    private IBinder          mExtBinder;
    private IBinder          mVidBinder;
    private ClusterPlugin    mActiveScreen;
    private VehicleDataSource mDataSource;
    private ClimateDataSource mClimateSource;
    private int              mCanvasW;
    private int              mCanvasH;

    // Lane Watch override state
    private boolean mLaneWatchActive  = false;
    private int     mPreLaneWatchMode = 1;

    private final VehicleDataSource.Listener mDataListener = new VehicleDataSource.Listener() {
        @Override public void onVehicleSnapshotChanged(VehicleSnapshot snapshot) {
            handleLaneWatchOverride(snapshot);
            updateDebugOverlay(snapshot);
            if (mActiveScreen != null) mActiveScreen.onVehicleSnapshotChanged(snapshot);
        }
    };

    private final ServiceConnection mExtConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            mExtBinder = b;
            sMuxStatus = "bound — calling notifyLvdsInterrupt(" + CONTENT_AUDIO + ")";
            callNotifyLvdsInterrupt(CONTENT_AUDIO);
        }
        @Override public void onServiceDisconnected(ComponentName n) {
            mExtBinder = null;
            sMuxStatus = "ext service disconnected";
        }
    };

    private final ServiceConnection mVidConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            mVidBinder = b;
            sMuxStatus += " | vid:bound";
        }
        @Override public void onServiceDisconnected(ComponentName n) { mVidBinder = null; }
    };

    // -------------------------------------------------------------------------
    // Service lifecycle
    // -------------------------------------------------------------------------

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mOverlay != null) return START_STICKY;

        DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display[] displays = dm.getDisplays();

        if (displays.length < 2) {
            sStatus = "FAIL: only " + displays.length + " display(s) found";
            stopSelf();
            return START_NOT_STICKY;
        }

        Display clusterDisplay = displays[displays.length - 1];
        Point clusterSize = new Point();
        clusterDisplay.getSize(clusterSize);

        mClusterContext = createDisplayContext(clusterDisplay);
        mWindowManager = (WindowManager) mClusterContext.getSystemService(Context.WINDOW_SERVICE);

        mCanvasW = CANVAS_X2 - CANVAS_X1;
        mCanvasH = CANVAS_Y2 - CANVAS_Y1;

        mOverlay = new FrameLayout(mClusterContext);
        mOverlay.setBackgroundColor(0xFF000000);

        mContentContainer = new FrameLayout(mClusterContext);
        FrameLayout.LayoutParams containerLp = new FrameLayout.LayoutParams(mCanvasW, mCanvasH);
        containerLp.gravity    = Gravity.TOP | Gravity.LEFT;
        containerLp.leftMargin = CANVAS_X1;
        containerLp.topMargin  = CANVAS_Y1;
        mOverlay.addView(mContentContainer, containerLp);

        FrameLayout.LayoutParams markerLp = new FrameLayout.LayoutParams(mCanvasW, mCanvasH);
        markerLp.gravity    = Gravity.TOP | Gravity.LEFT;
        markerLp.leftMargin = CANVAS_X1;
        markerLp.topMargin  = CANVAS_Y1;
        mOverlay.addView(new CornerMarkersView(mClusterContext), markerLp);

        mDebugOverlay = new TextView(mClusterContext);
        mDebugOverlay.setTextColor(0xFFFF0000);
        mDebugOverlay.setTextSize(9);
        mDebugOverlay.setBackgroundColor(0xAA000000);
        mDebugOverlay.setPadding(dp(4), dp(4), dp(4), dp(4));
        FrameLayout.LayoutParams debugLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.LEFT);
        debugLp.leftMargin = CANVAS_X1 + dp(4);
        debugLp.topMargin = CANVAS_Y1 + dp(4);
        mOverlay.addView(mDebugOverlay, debugLp);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;

        try {
            mWindowManager.addView(mOverlay, params);
            sStatus = "OK — display " + clusterDisplay.getDisplayId()
                    + " (" + clusterDisplay.getName() + ")"
                    + " " + clusterSize.x + "x" + clusterSize.y
                    + "  canvas=(" + CANVAS_X1 + "," + CANVAS_Y1
                    + ")-(" + CANVAS_X2 + "," + CANVAS_Y2 + ")";
        } catch (Exception e) {
            sStatus = "FAIL addView: " + e;
            mOverlay = null;
            stopSelf();
            return START_NOT_STICKY;
        }

        sInstance = this;

        mDataSource = new VehicleDataSource();
        mDataSource.addListener(mDataListener);
        mDataSource.start(this);

        mClimateSource = new ClimateDataSource();
        mClimateSource.start(this);

        sMuxStatus = "binding…";
        Intent extIntent = new Intent();
        extIntent.setComponent(new ComponentName(EXT_PKG, EXT_CLS));
        if (!bindService(extIntent, mExtConn, Context.BIND_AUTO_CREATE)) {
            sMuxStatus = "FAIL: bindService returned false";
        }

        Intent vidIntent = new Intent(VID_DESCRIPTOR);
        if (!bindService(vidIntent, mVidConn, Context.BIND_AUTO_CREATE)) {
            sMuxStatus += " | vid bind FAIL";
        }

        // Restore the last active built-in screen.
        applyMode(AppSettings.getInstance(this).getActiveMode());
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mActiveScreen != null) { mActiveScreen.onStop(); mActiveScreen = null; }
        if (mDataSource    != null) { mDataSource.stop();    mDataSource    = null; }
        if (mClimateSource != null) { mClimateSource.stop(); mClimateSource = null; }
        sInstance = null;
        if (mExtBinder != null) {
            try { unbindService(mExtConn); } catch (Exception ignored) {}
            mExtBinder = null;
        }
        if (mVidBinder != null) {
            try { unbindService(mVidConn); } catch (Exception ignored) {}
            mVidBinder = null;
        }
        if (mOverlay != null && mWindowManager != null) {
            mWindowManager.removeView(mOverlay);
            mOverlay = null;
        }
        sStatus    = "stopped";
        sMuxStatus = "stopped";
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    // -------------------------------------------------------------------------
    // Screen management — public entry points
    // -------------------------------------------------------------------------

    /** Switch to a built-in screen by mode number. */
    static void setMode(int mode) {
        if (sInstance != null) sInstance.applyMode(mode);
    }

    /** Returns the currently-active screen, or null if the service is not running. */
    static ClusterPlugin getActiveScreen() {
        return sInstance != null ? sInstance.mActiveScreen : null;
    }

    // -------------------------------------------------------------------------
    // Screen management — internal
    // -------------------------------------------------------------------------

    private void handleLaneWatchOverride(VehicleSnapshot snapshot) {
        boolean enabled   = AppSettings.getInstance(this).isLaneWatchEnabled();
        boolean blinkerOn = snapshot.lights.rightBlinker || snapshot.lights.turnRight;

        if (enabled && blinkerOn && !mLaneWatchActive) {
            mPreLaneWatchMode = AppSettings.getInstance(this).getActiveMode();
            mLaneWatchActive = true;
            callOpenVideoPath();
            try {
                swapScreen(new LaneWatchScreen(), hostApkPath(), "builtin.lanewatch");
            } catch (Throwable t) {
                android.util.Log.e("ClusterDisplayService", "LaneWatch swapScreen failed", t);
                mLaneWatchActive = false;
                callCloseVideoPath();
                sStatus = "LaneWatch error: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            }
        } else if (mLaneWatchActive && (!blinkerOn || !enabled)) {
            mLaneWatchActive = false;
            restorePreLaneWatch();
            callCloseVideoPath();
        }
    }

    private void updateDebugOverlay(VehicleSnapshot snapshot) {
        if (mDebugOverlay == null) return;
        String camInfo = LaneWatchScreen.debugInfo != null ? LaneWatchScreen.debugInfo : "none";
        final String text =
                "LW:" + (mLaneWatchActive ? "ON" : "off")
                + " blink=" + (snapshot.lights.rightBlinker ? "R" : "-")
                + " turnR=" + (snapshot.lights.turnRight ? "R" : "-")
                + " cam=" + camInfo;
        mDebugOverlay.post(() -> mDebugOverlay.setText(text));
    }

    private void restorePreLaneWatch() {
        applyMode(mPreLaneWatchMode);
    }

    private void applyMode(int mode) {
        mLaneWatchActive = false;
        sActiveApkPath   = hostApkPath();
        sActiveNamespace = "builtin." + mode;
        AppSettings.getInstance(this).setActiveMode(mode);
        swapScreen(buildScreen(mode), sActiveApkPath, sActiveNamespace);
    }

    /**
     * Replace the active screen.
     *
     * @param plugin    The screen to show.
     * @param apkPath   Absolute path of the APK whose {@code assets/} the screen may read.
     *                  Pass {@link #hostApkPath()} for locally-compiled screens.
     * @param namespace Settings namespace for this screen ("builtin.N").
     */
    private void swapScreen(ClusterPlugin plugin, String apkPath, String namespace) {
        if (mActiveScreen != null) mActiveScreen.onStop();
        mActiveScreen = plugin;
        mDataSource.setRefreshRateMs(mActiveScreen.getRefreshRateMs());

        mContentContainer.removeAllViews();
        PluginContext pc = new PluginContext(mClusterContext, apkPath, namespace, mCanvasW, mCanvasH);
        mContentContainer.addView(
                mActiveScreen.onCreateView(pc),
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
        mActiveScreen.onStart();
    }

    /**
     * Maps mode numbers to built-in screen implementations.
     * Add a new {@code case} here to publish a new screen.
     */
    private ClusterPlugin buildScreen(int mode) {
        switch (mode) {
            // ── Sub-state diagnostic screens (one per VehicleSnapshot group) ──
            case 2:  return new MotionScreen();
            case 3:  return new DrivetrainScreen();
            case 4:  return new BrakeScreen();
            case 5:  return new SteeringScreen();
            case 6:  return new LightsScreen();
            case 7:  return new CabinScreen();
            case 8:  return new FuelScreen();
            case 9:  return new AudioScreen();
            case 10: return new AdasScreen();
            case 11: return new MaintenanceScreen();
            // ── Main display ──────────────────────────────────────────────────
            case 12: return new DashboardScreen();
            // ── Full live-CAN inspector ───────────────────────────────────────
            case 13: return new CanDataScreen();
            // ── HVAC / climate (VehicleCoordinationService) ───────────────────
            case 14: return new ClimateScreen();
            default: return new PlaceholderScreen(mode);
        }
    }

    /** Absolute path of the host APK — used as the asset source for locally-compiled screens. */
    private String hostApkPath() {
        return getApplicationInfo().sourceDir;
    }

    // -------------------------------------------------------------------------
    // ExternalDisplay mux
    // -------------------------------------------------------------------------

    private void callOpenVideoPath() {
        if (mVidBinder == null) { android.util.Log.w("ClusterDisplayService", "openVideoPath: no binder"); return; }
        callVideoTx(TX_SHOW_AUTO, "showVideoAuto");
        callVideoTx(TX_OPEN_PATH, "openVideoPath");
    }

    private void callCloseVideoPath() {
        if (mVidBinder == null) return;
        callVideoTx(TX_CLOSE_PATH, "closeVideoPath");
        callVideoTx(TX_HIDE_AUTO,  "hideVideoAuto");
    }

    private void callVideoTx(int code, String name) {
        if (mVidBinder == null) return;
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(VID_DESCRIPTOR);
            data.writeInt(VIDEO_TYPE_LWC);
            mVidBinder.transact(code, data, reply, 0);
            reply.readException();
        } catch (Exception e) {
            android.util.Log.w("ClusterDisplayService", name + " failed: " + e);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private void callNotifyLvdsInterrupt(int contentId) {
        if (mExtBinder == null) { sMuxStatus = "FAIL: binder is null"; return; }
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(EXT_DESCRIPTOR);
            data.writeInt(contentId);
            mExtBinder.transact(TX_LVDS, data, reply, 0);
            reply.readException();
            sMuxStatus = "notifyLvdsInterrupt(" + contentId + ") = " + (reply.readInt() != 0);
        } catch (Exception e) {
            sMuxStatus = "FAIL: " + e;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private int dp(int dp) {
        return Math.round(dp * mClusterContext.getResources().getDisplayMetrics().density);
    }

    // -------------------------------------------------------------------------
    // Calibration aid — cyan corner brackets drawn over the content area
    // -------------------------------------------------------------------------

    private static class CornerMarkersView extends View {
        private static final int ARM_PX    = 24;
        private static final int STROKE_PX = 3;
        private final Paint mPaint = new Paint();

        CornerMarkersView(Context ctx) {
            super(ctx);
            mPaint.setColor(0xFF00FFFF);
            mPaint.setStrokeWidth(STROKE_PX);
            mPaint.setAntiAlias(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth()  - 1;
            int h = getHeight() - 1;
            int a = ARM_PX;
            canvas.drawLine(0,     0,     a,     0,     mPaint);
            canvas.drawLine(0,     0,     0,     a,     mPaint);
            canvas.drawLine(w - a, 0,     w,     0,     mPaint);
            canvas.drawLine(w,     0,     w,     a,     mPaint);
            canvas.drawLine(0,     h - a, 0,     h,     mPaint);
            canvas.drawLine(0,     h,     a,     h,     mPaint);
            canvas.drawLine(w,     h - a, w,     h,     mPaint);
            canvas.drawLine(w - a, h,     w,     h,     mPaint);
        }
    }
}
