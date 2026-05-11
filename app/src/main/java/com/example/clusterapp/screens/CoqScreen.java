package com.example.clusterapp.screens;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import com.example.clusterapi.ClusterPlugin;
import com.example.clusterapi.PluginContext;
import com.example.clusterapi.VehicleSnapshot;

/**
 * Displays the coq image and current speed.
 * Coq sits on the left half when speed < 50 km/h, right half at 50 km/h or above.
 *
 * The coq image is loaded from {@code assets/coq.jpg} via {@link PluginContext#decodeBitmap}.
 * To customise this screen as a marketplace plugin, replace {@code coq.jpg} in your
 * plugin APK's {@code assets/} folder.
 */
public class CoqScreen implements ClusterPlugin {

    public CoqScreen() {}

    private static final int SPEED_THRESHOLD = 50;

    private CoqView mView;

    @Override
    public View onCreateView(PluginContext context) {
        Bitmap bitmap = context.decodeBitmap("coq.jpg");
        mView = new CoqView(context.getAndroidContext(), bitmap);
        return mView;
    }

    @Override public void onStart() {}

    @Override
    public void onVehicleSnapshotChanged(VehicleSnapshot snapshot) {
        if (mView != null) mView.update(snapshot.motion.speedKmh);
    }

    @Override
    public void onStop() {
        if (mView != null) {
            mView.recycle();
            mView = null;
        }
    }

    // -------------------------------------------------------------------------

    private static class CoqView extends View {
        private Bitmap mBitmap;
        private int mSpeed = -1;

        private final Paint mBitmapPaint =
                new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint mSpeedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CoqView(Context ctx, Bitmap bitmap) {
            super(ctx);
            setBackgroundColor(0xFF000000);
            mBitmap = bitmap;
            mSpeedPaint.setColor(0xFFFFFFFF);
            mSpeedPaint.setTextAlign(Paint.Align.CENTER);
        }

        void update(int speed) {
            mSpeed = speed;
            invalidate();
        }

        void recycle() {
            if (mBitmap != null) {
                mBitmap.recycle();
                mBitmap = null;
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            mSpeedPaint.setTextSize(h * 0.35f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (mBitmap == null) return;

            int w = getWidth();
            int h = getHeight();
            float halfW = w / 2f;
            boolean coqOnRight = mSpeed >= SPEED_THRESHOLD;

            float scale = Math.min(
                    halfW / mBitmap.getWidth(),
                    (float) h / mBitmap.getHeight());
            float bw = mBitmap.getWidth()  * scale;
            float bh = mBitmap.getHeight() * scale;

            float bitmapHalfOrigin = coqOnRight ? halfW : 0f;
            float bitmapLeft = bitmapHalfOrigin + (halfW - bw) / 2f;
            float bitmapTop  = (h - bh) / 2f;

            canvas.drawBitmap(mBitmap, null,
                    new RectF(bitmapLeft, bitmapTop, bitmapLeft + bw, bitmapTop + bh),
                    mBitmapPaint);

            String speedStr = mSpeed >= 0 ? mSpeed + " km/h" : "--";
            float textHalfOrigin = coqOnRight ? 0f : halfW;
            float textX = textHalfOrigin + halfW / 2f;
            float textY = h / 2f - (mSpeedPaint.descent() + mSpeedPaint.ascent()) / 2f;
            canvas.drawText(speedStr, textX, textY, mSpeedPaint);
        }
    }
}
