package com.example.myplugin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.example.clusterapi.ClusterPlugin;
import com.example.clusterapi.PluginContext;
import com.example.clusterapi.VehicleSnapshot;

/**
 * Minimal ClusterPlugin template.
 *
 * This screen displays the driving range in the centre of the cluster canvas.
 * Replace the contents of MyView.onDraw() with whatever you want to show.
 *
 * Rename this file and update:
 *   - The package declaration above
 *   - app/build.gradle  →  namespace / applicationId
 *   - assets/plugin.json  →  id / name / author / entryClass
 */
public class MyScreen implements ClusterPlugin {

    private MyView mView;

    // Required: public no-arg constructor.
    public MyScreen() {}

    @Override
    public View onCreateView(PluginContext context) {
        mView = new MyView(context.getAndroidContext());
        return mView;
    }

    @Override
    public void onStart() {
        // Start any animations or repeating work here.
    }

    @Override
    public void onVehicleSnapshotChanged(VehicleSnapshot snapshot) {
        if (mView != null) {
            mView.update(snapshot.range, snapshot.rangeInMiles);
        }
    }

    @Override
    public void onStop() {
        // Cancel handlers, recycle bitmaps — release everything.
        mView = null;
    }

    @Override
    public int getRefreshRateMs() {
        return 500; // Range does not need 250 ms updates.
    }

    // -------------------------------------------------------------------------

    private static class MyView extends View {

        private int     mRange  = -1;
        private boolean mMiles  = false;

        private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        MyView(Context context) {
            super(context);
            setBackgroundColor(Color.BLACK);

            mTextPaint.setColor(Color.WHITE);
            mTextPaint.setTextAlign(Paint.Align.CENTER);
            mTextPaint.setTextSize(52f);
            mTextPaint.setFakeBoldText(true);
        }

        void update(int range, boolean miles) {
            mRange = range;
            mMiles = miles;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            String text = mRange >= 0
                ? mRange + (mMiles ? " mi" : " km")
                : "---";

            float cx = getWidth()  / 2f;
            float cy = getHeight() / 2f
                - (mTextPaint.descent() + mTextPaint.ascent()) / 2f;

            canvas.drawText(text, cx, cy, mTextPaint);
        }
    }
}
