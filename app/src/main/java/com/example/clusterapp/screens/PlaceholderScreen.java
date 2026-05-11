package com.example.clusterapp.screens;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import com.example.clusterapi.ClusterPlugin;
import com.example.clusterapi.PluginContext;
import com.example.clusterapi.VehicleSnapshot;

/** Displays a large mode number. Used for modes without a real implementation yet. */
public class PlaceholderScreen implements ClusterPlugin {

    public PlaceholderScreen() { this(-1); }
    public PlaceholderScreen(int number) { mNumber = number; }

    private final int mNumber;

    @Override
    public View onCreateView(PluginContext context) {
        return new NumberView(context.getAndroidContext(), mNumber);
    }

    @Override public void onStart() {}
    @Override public void onVehicleSnapshotChanged(VehicleSnapshot snapshot) {}
    @Override public void onStop() {}
    @Override public int getRefreshRateMs() { return 1000; }

    private static class NumberView extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String mText;

        NumberView(Context ctx, int number) {
            super(ctx);
            mText = String.valueOf(number);
            setBackgroundColor(0xFF000000);
            mPaint.setColor(0xFFFFFFFF);
            mPaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            mPaint.setTextSize(h * 0.5f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float x = getWidth() / 2f;
            float y = getHeight() / 2f - (mPaint.descent() + mPaint.ascent()) / 2f;
            canvas.drawText(mText, x, y, mPaint);
        }
    }
}
