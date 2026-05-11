package com.example.clusterapp.screens;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.example.clusterapp.AppSettings;
import com.example.clusterapi.ClusterPlugin;
import com.example.clusterapi.PluginContext;
import com.example.clusterapi.VehicleSnapshot;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;


@SuppressWarnings("deprecation")
public class LaneWatchScreen implements ClusterPlugin {

    private static final String TAG = "LaneWatchScreen";
    public static volatile String debugInfo = "";

    private Context mContext;
    private TextureView mTextureView;
    private TextView mStatusText;

    private HandlerThread mCameraThread;
    private Handler mCameraHandler;
    private Camera mCamera;
    private volatile boolean mStopping = false;

    @Override
    public View onCreateView(PluginContext context) {
        mContext = context.getAndroidContext();

        FrameLayout container = new FrameLayout(mContext);
        container.setBackgroundColor(0xFF000000);

        mTextureView = new TextureView(mContext);
        container.addView(mTextureView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        mStatusText = new TextView(mContext);
        mStatusText.setTextColor(0xFFFFFFFF);
        mStatusText.setTextSize(9);
        mStatusText.setBackgroundColor(0xAA000000);
        mStatusText.setPadding(6, 2, 6, 2);
        mStatusText.setGravity(Gravity.CENTER);
        mStatusText.setVisibility(View.GONE);
        container.addView(mStatusText, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        return container;
    }

    @Override
    public void onStart() {
        mStopping = false;
        mCameraThread = new HandlerThread("LaneWatchCam");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());

        mCameraHandler.post(new Runnable() {
            @Override public void run() { openCamera(); }
        });
    }

    @Override public void onVehicleSnapshotChanged(VehicleSnapshot snapshot) {}

    @Override
    public void onStop() {
        mStopping = true;
        if (mCameraThread != null) {
            mCameraThread.quit();
            try { mCameraThread.join(2000); } catch (InterruptedException ignored) {}
            mCameraThread = null;
        }
        mCameraHandler = null;
        // Camera thread has stopped; release camera on this thread.
        releaseCamera();
    }

    @Override
    public int getRefreshRateMs() { return 1000; }

    // -------------------------------------------------------------------------

    private void openCamera() {
        // Try up to 5 times with 200 ms between attempts, matching VideoView behaviour.
        Camera cam = null;
        for (int attempt = 0; attempt < 5 && cam == null && !mStopping; attempt++) {
            if (attempt > 0) {
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
            cam = acquireCamera();
        }

        if (cam == null || mStopping) {
            if (cam != null) { try { cam.release(); } catch (Exception ignored) {} }
            if (!mStopping) { showStatus("Camera open failed after retries"); debugInfo = "open_failed"; }
            return;
        }

        mCamera = cam;

        if (mTextureView.isAvailable()) {
            startPreview(mTextureView.getSurfaceTexture());
        } else {
            mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                    startPreview(st);
                }
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {}
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { return true; }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {}
            });
        }
    }

    /**
     * Opens the vehicle side camera.
     *
     * Camera.open(int) and getCameraInfo() are disabled on this head unit and throw
     * RuntimeException("Unsupported"). The vendor-specific Camera.openVehicle() method
     * (which calls new Camera(0)) is the only working path and is reached via reflection.
     *
     * If a camera ID is manually configured in Settings, the protected Camera(int)
     * constructor is invoked directly via reflection to try that specific ID.
     */
    private Camera acquireCamera() {
        String configured = AppSettings.getInstance(mContext).getLaneWatchCameraId();

        // User-configured ID: access the protected Camera(int) constructor directly.
        if (configured != null) {
            try {
                int id = Integer.parseInt(configured.trim());
                Constructor<Camera> ctor = Camera.class.getDeclaredConstructor(int.class);
                ctor.setAccessible(true);
                Camera cam = ctor.newInstance(id);
                debugInfo = "ctor:" + id;
                Log.d(TAG, "Opened camera via constructor id=" + id);
                return cam;
            } catch (NumberFormatException e) {
                Log.w(TAG, "Configured camera id not a number: " + configured);
            } catch (Exception e) {
                Log.w(TAG, "Camera(id) constructor failed: " + e);
                debugInfo = "ctor_err:" + configured;
            }
        }

        // Default: Camera.openVehicle() — vendor method that opens the side camera (id=0).
        try {
            Method m = Camera.class.getDeclaredMethod("openVehicle");
            Camera cam = (Camera) m.invoke(null);
            if (cam != null) {
                debugInfo = "openVehicle:ok";
                Log.d(TAG, "Opened camera via openVehicle()");
                return cam;
            }
        } catch (Exception e) {
            Log.w(TAG, "Camera.openVehicle() failed: " + e);
            debugInfo = "openVehicle_err";
        }

        return null;
    }

    private void startPreview(final SurfaceTexture surfaceTexture) {
        if (mCamera == null) return;
        try {
            mCamera.setPreviewTexture(surfaceTexture);
            mCamera.startPreview();
            showStatus(null);
            debugInfo += " preview:ok";
            Log.d(TAG, "Preview started");
        } catch (Exception e) {
            String msg = "Preview failed:\n" + e.getMessage();
            showStatus(msg);
            debugInfo += " preview_err";
            Log.e(TAG, "startPreview failed", e);
        }
    }

    private void releaseCamera() {
        if (mCamera != null) {
            try { mCamera.stopPreview(); } catch (RuntimeException ignored) {}
            mCamera.release();
            mCamera = null;
            Log.d(TAG, "Camera released");
        }
    }

    private void showStatus(final String msg) {
        if (mStatusText == null) return;
        mStatusText.post(new Runnable() {
            @Override public void run() {
                if (msg == null || msg.isEmpty()) {
                    mStatusText.setVisibility(View.GONE);
                } else {
                    mStatusText.setText(msg);
                    mStatusText.setVisibility(View.VISIBLE);
                    Log.w(TAG, "Status: " + msg);
                }
            }
        });
    }
}
