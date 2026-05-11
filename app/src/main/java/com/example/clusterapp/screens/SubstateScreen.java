package com.example.clusterapp.screens;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import com.example.clusterapi.ClusterPlugin;
import com.example.clusterapi.PluginContext;
import com.example.clusterapi.VehicleSnapshot;

/**
 * Base for single-sub-state diagnostic screens.
 *
 * Subclasses provide a title, a fixed label list, and a values array computed from
 * the latest snapshot. The view auto-switches to a two-column layout when there
 * are more than 8 fields so everything stays visible on the small cluster canvas.
 */
abstract class SubstateScreen implements ClusterPlugin {

    private SubstateView mView;

    abstract String   title();
    abstract String[] labels();
    abstract String[] values(VehicleSnapshot s);

    @Override
    public View onCreateView(PluginContext context) {
        mView = new SubstateView(context.getAndroidContext(), title(), labels());
        return mView;
    }

    @Override
    public void onVehicleSnapshotChanged(VehicleSnapshot snapshot) {
        if (mView != null) mView.update(values(snapshot));
    }

    @Override public void onStart() {}
    @Override public void onStop()  { mView = null; }
    @Override public int getRefreshRateMs() { return 250; }

    // ── Helpers for subclasses ────────────────────────────────────────────────

    static String f1(double v)      { return String.format("%.1f", v); }
    static String on(boolean v)     { return v ? "ON" : "–"; }
    static String warn(boolean v)   { return v ? "WARN" : "ok"; }

    // ── Shared drawing view ───────────────────────────────────────────────────

    private static final class SubstateView extends View {

        private final String   mTitle;
        private final String[] mLabels;
        private       String[] mValues;

        private final Paint mTitlePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mLabelPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mValuePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mDividerPaint = new Paint();

        SubstateView(Context ctx, String title, String[] labels) {
            super(ctx);
            setBackgroundColor(0xFF000000);
            mTitle  = title;
            mLabels = labels;
            mValues = new String[labels.length];

            mTitlePaint.setColor(0xFFFFCC00);
            mTitlePaint.setTextSize(13f);

            mLabelPaint.setColor(0xFF666666);
            mLabelPaint.setTextSize(11f);

            mValuePaint.setColor(0xFFEEEEEE);
            mValuePaint.setTextSize(11f);
            mValuePaint.setTextAlign(Paint.Align.RIGHT);

            mDividerPaint.setColor(0xFF222222);
            mDividerPaint.setStrokeWidth(1f);
        }

        void update(String[] values) {
            mValues = values;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w   = getWidth();
            float h   = getHeight();
            float p   = 6f;
            float lh  = 13f;
            float ty  = 13f; // title baseline
            float fy  = ty + 13f; // first field baseline

            canvas.drawText(mTitle, p, ty, mTitlePaint);

            int     n      = mLabels.length;
            boolean twoCol = n > 8;
            int     perCol = twoCol ? (n + 1) / 2 : n;
            float   cw     = twoCol ? w / 2f : w;

            if (twoCol) {
                canvas.drawLine(cw, fy - lh, cw, h, mDividerPaint);
            }

            for (int i = 0; i < n; i++) {
                int   col    = twoCol ? i / perCol : 0;
                int   rowIdx = twoCol ? i % perCol : i;
                float y      = fy + rowIdx * lh;
                float lx     = col * cw + p;
                float rx     = (col + 1) * cw - p;
                String val = (mValues != null && i < mValues.length && mValues[i] != null)
                        ? mValues[i] : "–";
                canvas.drawText(mLabels[i], lx, y, mLabelPaint);
                canvas.drawText(val, rx, y, mValuePaint);
            }
        }
    }
}
