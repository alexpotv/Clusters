package com.example.clusterapp.screens;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import com.example.clusterapi.ClusterPlugin;
import com.example.clusterapi.PluginContext;
import com.example.clusterapi.VehicleSnapshot;

/** Full vehicle data grid: speed, range, brake, lights, turn signals, audio. */
public class DashboardScreen implements ClusterPlugin {

    public DashboardScreen() {}

    private DashboardView mView;

    @Override
    public View onCreateView(PluginContext context) {
        mView = new DashboardView(context.getAndroidContext());
        return mView;
    }

    @Override public void onStart() {}

    @Override
    public void onVehicleSnapshotChanged(VehicleSnapshot snapshot) {
        if (mView != null) mView.update(snapshot);
    }

    @Override
    public void onStop() { mView = null; }

    // -------------------------------------------------------------------------

    private static class DashboardView extends View {
        private final Paint mOnPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mOffPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private VehicleSnapshot mSnapshot;

        private float mRowH;
        private float mPad;
        private float mValueSize;
        private float mLabelSize;

        DashboardView(Context ctx) {
            super(ctx);
            setBackgroundColor(0xFF000000);
            mOnPaint.setColor(0xFF00CC66);
            mOffPaint.setColor(0xFF444444);
            mLabelPaint.setColor(0xFF888888);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            mRowH       = h / 4f;
            mPad        = mRowH * 0.12f;
            mValueSize  = mRowH * 0.52f;
            mLabelSize  = mRowH * 0.28f;
            mOnPaint.setTextSize(mValueSize);
            mOffPaint.setTextSize(mValueSize);
            mLabelPaint.setTextSize(mLabelSize);
        }

        void update(VehicleSnapshot snapshot) {
            mSnapshot = snapshot;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (mSnapshot == null) return;
            float w = getWidth();

            // Row 0: Speed | Brake pedal
            float y0 = mRowH * 0;
            String speedStr = mSnapshot.motion.speedKmh >= 0
                    ? mSnapshot.motion.speedKmh + " km/h" : "--";
            drawCell(canvas, speedStr, "SPEED", mOnPaint, mPad, y0);
            drawCellRight(canvas,
                    mSnapshot.brakes.pedalPressed ? "BRAKE" : "brake", "BRAKE PEDAL",
                    mSnapshot.brakes.pedalPressed ? mOnPaint : mOffPaint, w - mPad, y0);

            // Row 1: Range | Exterior lights
            float y1 = mRowH * 1;
            String rangeStr = mSnapshot.fuel.range >= 0
                    ? mSnapshot.fuel.range + (mSnapshot.fuel.rangeInMiles ? " mi" : " km") : "--";
            drawCell(canvas, rangeStr, "RANGE", mOnPaint, mPad, y1);
            drawCellRight(canvas,
                    mSnapshot.lights.lightsOn ? "ON" : "off", "EXT LIGHTS",
                    mSnapshot.lights.lightsOn ? mOnPaint : mOffPaint, w - mPad, y1);

            // Row 2: Turn signals
            float y2 = mRowH * 2;
            drawCell(canvas, "<<", "LEFT TURN",
                    mSnapshot.lights.turnLeft ? mOnPaint : mOffPaint, mPad, y2);
            drawCellRight(canvas, ">>", "RIGHT TURN",
                    mSnapshot.lights.turnRight ? mOnPaint : mOffPaint, w - mPad, y2);

            // Row 3: Audio playing | Volume
            float y3 = mRowH * 3;
            int volPct = mSnapshot.audio.volumeMax > 0
                    ? (mSnapshot.audio.volume * 100 / mSnapshot.audio.volumeMax) : 0;
            drawCell(canvas,
                    mSnapshot.audio.musicPlaying ? "PLAYING" : "off", "AUDIO",
                    mSnapshot.audio.musicPlaying ? mOnPaint : mOffPaint, mPad, y3);
            drawCellRight(canvas,
                    mSnapshot.audio.mute ? "MUTE" : volPct + "%", "VOLUME",
                    mSnapshot.audio.mute ? mOffPaint : mOnPaint, w - mPad, y3);
        }

        private void drawCell(Canvas c, String value, String label,
                Paint valuePaint, float x, float rowTop) {
            c.drawText(value, x, rowTop + mPad + mValueSize, valuePaint);
            c.drawText(label, x, rowTop + mPad + mValueSize + mLabelSize * 1.2f, mLabelPaint);
        }

        private void drawCellRight(Canvas c, String value, String label,
                Paint valuePaint, float xRight, float rowTop) {
            c.drawText(value, xRight - valuePaint.measureText(value),
                    rowTop + mPad + mValueSize, valuePaint);
            c.drawText(label, xRight - mLabelPaint.measureText(label),
                    rowTop + mPad + mValueSize + mLabelSize * 1.2f, mLabelPaint);
        }
    }
}
