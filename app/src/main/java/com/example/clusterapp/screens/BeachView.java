package com.example.clusterapp.screens;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/**
 * Draws the beach/ocean scene for {@link BeachScreen}.
 *
 * Canvas size = 485 × 190 px (the mContentContainer dimensions in ClusterDisplayService).
 *
 * Layer order (bottom → top):
 *   sky gradient → sea gradient → 3 animated wave bands →
 *   sand strip → surf foam → beach decorations → sun + clouds → range text
 *
 * The three wave bands fill downward from their crest line; the sand rectangle
 * then covers the wave fill below SAND_Y, so only the crests protrude into view.
 * The range display is entirely above the highest possible wave crest (~y 117),
 * so the constraint "waves never overlay the range" is structural, not conditional.
 */
public class BeachView extends View {

    // -----------------------------------------------------------------------
    // Layout constants — all in canvas pixels (canvas = 485 × 190)
    // -----------------------------------------------------------------------

    private static final float CX = 242f;          // horizontal centre of canvas

    // Scene zone boundaries
    private static final float HORIZON_Y = 105f;   // sky-to-sea gradient boundary
    private static final float WAVE1_Y   = 126f;   // back wave base (fills downward)
    private static final float WAVE2_Y   = 133f;   // mid wave base
    private static final float WAVE3_Y   = 140f;   // front wave base
    private static final float SAND_Y    = 153f;   // sand top; covers wave bodies below here

    // Range text — all three items sit above y ≈ 107, below which the highest
    // wave crest can theoretically reach (WAVE1_Y − amp − harmonic ≈ 117 px).
    private static final float LABEL_Y = 28f;      // "RANGE" caption baseline
    private static final float NUM_Y   = 80f;      // large range number baseline
    private static final float UNITS_Y = 98f;      // km / mi unit label baseline

    // Sun (top-right sky area)
    private static final float SUN_X = 440f;
    private static final float SUN_Y = 22f;
    private static final float SUN_R = 13f;

    // -----------------------------------------------------------------------
    // Fixed decoration data  [cx, cy, size, rotation-or-extra]
    // -----------------------------------------------------------------------

    private static final float[][] STARFISH = {
        { 32f, 163f,  8f, 0.30f},
        {118f, 168f,  7f, 1.10f},
        {375f, 160f,  9f, 0.70f},
        {447f, 166f,  7f, 2.00f},
    };
    private static final int[] STARFISH_COLORS = {
        0xFFFF7043, 0xFFFF8A65, 0xFFF4511E, 0xFFE64A19,
    };

    private static final float[][] SHELLS = {
        { 72f, 172f, 7f, -20f},
        {186f, 163f, 6f,  10f},
        {315f, 170f, 8f, -30f},
        {460f, 165f, 6f,  15f},
    };

    private static final float[] TURTLE = {270f, 155f, 10f};

    // -----------------------------------------------------------------------
    // Animation
    // -----------------------------------------------------------------------

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mRunning = false;
    private float   mWavePhase = 0f;       // advances ~0.06 rad per frame @ 30 fps

    // -----------------------------------------------------------------------
    // Vehicle state
    // -----------------------------------------------------------------------

    private int     mRange        = -1;
    private boolean mRangeInMiles = false;

    // -----------------------------------------------------------------------
    // Paints (allocated once in constructor)
    // -----------------------------------------------------------------------

    private final Paint mSkyPaint   = new Paint();
    private final Paint mSeaPaint   = new Paint();
    private final Paint mSandPaint  = new Paint();
    private final Paint mWave1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mWave2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mWave3Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFoamPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSunPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRayPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCloudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mNumPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mUnitPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDecPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Reusable geometry objects — safe to reuse because onDraw is single-threaded
    private final Path  mWavePath = new Path();
    private final Path  mDecPath  = new Path();
    private final RectF mRect     = new RectF();

    // -----------------------------------------------------------------------

    public BeachView(Context context) {
        super(context);

        mWave1Paint.setColor(0xDD1565C0);   // deep blue, back layer
        mWave1Paint.setStyle(Paint.Style.FILL);

        mWave2Paint.setColor(0xDD1976D2);   // mid blue
        mWave2Paint.setStyle(Paint.Style.FILL);

        mWave3Paint.setColor(0xCC42A5F5);   // light blue, front layer
        mWave3Paint.setStyle(Paint.Style.FILL);

        mFoamPaint.setColor(0x99B3E5FC);
        mFoamPaint.setStyle(Paint.Style.FILL);

        mSunPaint.setColor(0xFFFFE066);
        mSunPaint.setStyle(Paint.Style.FILL);

        mRayPaint.setColor(0xAAFFD54F);
        mRayPaint.setStyle(Paint.Style.STROKE);
        mRayPaint.setStrokeWidth(2f);
        mRayPaint.setStrokeCap(Paint.Cap.ROUND);

        mCloudPaint.setColor(0xCCEEF7FF);
        mCloudPaint.setStyle(Paint.Style.FILL);

        mLabelPaint.setColor(0xAAFFFFFF);
        mLabelPaint.setTextSize(13f);
        mLabelPaint.setTextAlign(Paint.Align.CENTER);

        mNumPaint.setColor(0xFFFFFFFF);
        mNumPaint.setTextSize(62f);
        mNumPaint.setFakeBoldText(true);
        mNumPaint.setTextAlign(Paint.Align.CENTER);
        mNumPaint.setShadowLayer(6f, 2f, 3f, 0xCC001A33);

        mUnitPaint.setColor(0xDDFFFFFF);
        mUnitPaint.setTextSize(21f);
        mUnitPaint.setTextAlign(Paint.Align.CENTER);
        mUnitPaint.setShadowLayer(4f, 1f, 2f, 0xCC001A33);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public void setRange(int range, boolean inMiles) {
        if (mRange != range || mRangeInMiles != inMiles) {
            mRange        = range;
            mRangeInMiles = inMiles;
            invalidate();
        }
    }

    public void startWaves() {
        mRunning = true;
        scheduleTick();
    }

    public void stopWaves() {
        mRunning = false;
        mHandler.removeCallbacksAndMessages(null);
    }

    // -----------------------------------------------------------------------
    // Animation tick (~30 fps)
    // -----------------------------------------------------------------------

    private void scheduleTick() {
        mHandler.postDelayed(mTickRunnable, 33L);
    }

    private final Runnable mTickRunnable = new Runnable() {
        @Override public void run() {
            if (!mRunning) return;
            mWavePhase += 0.06f;
            invalidate();
            scheduleTick();
        }
    };

    // -----------------------------------------------------------------------
    // Gradient setup (deferred until we know the view dimensions)
    // -----------------------------------------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mSkyPaint.setShader(new LinearGradient(
            0, 0, 0, HORIZON_Y + 20,
            new int[]{0xFF87CEEB, 0xFF4A90D9, 0xFF1A6FB5},
            new float[]{0f, 0.55f, 1f},
            Shader.TileMode.CLAMP));
        mSkyPaint.setStyle(Paint.Style.FILL);

        mSeaPaint.setShader(new LinearGradient(
            0, HORIZON_Y - 10, 0, h,
            new int[]{0xFF1A6FB5, 0xFF0D47A1, 0xFF082A6A},
            null,
            Shader.TileMode.CLAMP));
        mSeaPaint.setStyle(Paint.Style.FILL);

        mSandPaint.setShader(new LinearGradient(
            0, SAND_Y, 0, h,
            new int[]{0xFFF5DEB3, 0xFFDEB887},
            null,
            Shader.TileMode.CLAMP));
        mSandPaint.setStyle(Paint.Style.FILL);
    }

    // -----------------------------------------------------------------------
    // Drawing
    // -----------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();

        // 1. Sky
        canvas.drawRect(0, 0, w, HORIZON_Y + 20, mSkyPaint);

        // 2. Sea
        canvas.drawRect(0, HORIZON_Y - 10, w, h, mSeaPaint);

        // 3. Three wave bands (each fills from its crest line to the canvas bottom)
        drawWaveLayer(canvas, w, h, WAVE1_Y, 7f, 130f, mWavePhase * 0.65f, mWave1Paint);
        drawWaveLayer(canvas, w, h, WAVE2_Y, 5f, 100f, mWavePhase * 1.00f, mWave2Paint);
        drawWaveLayer(canvas, w, h, WAVE3_Y, 4f,  75f, mWavePhase * 1.50f, mWave3Paint);

        // 4. Sand strip (masks the wave fill below SAND_Y; only crests remain visible)
        canvas.drawRect(0, SAND_Y, w, h, mSandPaint);

        // 5. Surf foam at the sand edge
        drawFoam(canvas, w);

        // 6. Beach decorations sitting on the sand
        drawDecorations(canvas);

        // 7. Sun and clouds in the sky (drawn after decorations to stay on top)
        drawSun(canvas);
        drawClouds(canvas);

        // 8. Range readout — always the topmost layer
        drawRange(canvas);
    }

    // -----------------------------------------------------------------------
    // Waves
    // -----------------------------------------------------------------------

    private void drawWaveLayer(Canvas canvas, int w, int h,
                               float baseY, float amp, float wl,
                               float phase, Paint paint) {
        mWavePath.reset();
        mWavePath.moveTo(0, waveY(0, baseY, amp, wl, phase));
        for (float x = 4f; x <= w; x += 4f) {
            mWavePath.lineTo(x, waveY(x, baseY, amp, wl, phase));
        }
        mWavePath.lineTo(w, h);
        mWavePath.lineTo(0, h);
        mWavePath.close();
        canvas.drawPath(mWavePath, paint);
    }

    private float waveY(float x, float baseY, float amp, float wl, float phase) {
        // Primary wave + a higher-frequency harmonic for a natural look
        double t = x / wl * (Math.PI * 2) + phase;
        return baseY
            + (float)(amp       * Math.sin(t))
            + (float)(amp * 0.3 * Math.sin(t * 2.1 + 0.5));
    }

    private void drawFoam(Canvas canvas, int w) {
        for (int i = 0; i < 11; i++) {
            float x = (i * 47f + (float)(Math.sin(mWavePhase + i * 0.9) * 5f) + w) % w;
            float y = SAND_Y - 2f + (float)(Math.sin(mWavePhase * 0.6f + i) * 2.5f);
            mRect.set(x - 5f, y - 2.5f, x + 5f, y + 2.5f);
            canvas.drawOval(mRect, mFoamPaint);
        }
    }

    // -----------------------------------------------------------------------
    // Beach decorations
    // -----------------------------------------------------------------------

    private void drawDecorations(Canvas canvas) {
        for (int i = 0; i < STARFISH.length; i++) {
            float[] d = STARFISH[i];
            drawStarfish(canvas, d[0], d[1], d[2], d[3], STARFISH_COLORS[i]);
        }
        for (float[] d : SHELLS) {
            drawShell(canvas, d[0], d[1], d[2], d[3]);
        }
        drawTurtle(canvas, TURTLE[0], TURTLE[1], TURTLE[2]);
    }

    private void drawStarfish(Canvas canvas, float cx, float cy, float r, float rot, int color) {
        float inner = r * 0.38f;
        mDecPath.reset();
        for (int i = 0; i < 10; i++) {
            float radius = (i % 2 == 0) ? r : inner;
            float angle  = (float)(Math.PI * i / 5.0 + rot - Math.PI / 2);
            float x = cx + radius * (float) Math.cos(angle);
            float y = cy + radius * (float) Math.sin(angle);
            if (i == 0) mDecPath.moveTo(x, y); else mDecPath.lineTo(x, y);
        }
        mDecPath.close();

        mDecPaint.setStyle(Paint.Style.FILL);
        mDecPaint.setColor(color);
        canvas.drawPath(mDecPath, mDecPaint);

        mDecPaint.setStyle(Paint.Style.STROKE);
        mDecPaint.setStrokeWidth(0.8f);
        mDecPaint.setColor(0x88400000);
        canvas.drawPath(mDecPath, mDecPaint);
    }

    /** Fan-shell: pie-wedge filled with pink, ribs radiating from the apex. */
    private void drawShell(Canvas canvas, float cx, float cy, float size, float rotDeg) {
        mDecPath.reset();
        mDecPath.moveTo(cx, cy);
        mRect.set(cx - size, cy - size, cx + size, cy + size);
        mDecPath.arcTo(mRect, rotDeg, 150f);
        mDecPath.close();

        mDecPaint.setStyle(Paint.Style.FILL);
        mDecPaint.setColor(0xFFF48FB1);
        canvas.drawPath(mDecPath, mDecPaint);

        mDecPaint.setStyle(Paint.Style.STROKE);
        mDecPaint.setStrokeWidth(1f);
        mDecPaint.setColor(0xFFC2185B);
        for (int i = 0; i <= 4; i++) {
            double a = Math.toRadians(rotDeg + i * 37.5);
            canvas.drawLine(
                cx, cy,
                cx + size * (float) Math.cos(a),
                cy + size * (float) Math.sin(a),
                mDecPaint);
        }
    }

    private void drawTurtle(Canvas canvas, float cx, float cy, float size) {
        // Flippers drawn first so the shell body overlaps them at the edges
        mDecPaint.setStyle(Paint.Style.FILL);
        mDecPaint.setColor(0xFF7CB342);
        mRect.set(cx - size * 1.55f, cy - size * 0.70f, cx - size * 0.80f, cy - size * 0.15f);
        canvas.drawOval(mRect, mDecPaint);
        mRect.set(cx + size * 0.80f,  cy - size * 0.70f, cx + size * 1.55f, cy - size * 0.15f);
        canvas.drawOval(mRect, mDecPaint);
        mRect.set(cx - size * 1.45f, cy + size * 0.10f, cx - size * 0.75f, cy + size * 0.65f);
        canvas.drawOval(mRect, mDecPaint);
        mRect.set(cx + size * 0.75f, cy + size * 0.10f, cx + size * 1.45f, cy + size * 0.65f);
        canvas.drawOval(mRect, mDecPaint);

        // Shell body
        mDecPaint.setColor(0xFF558B2F);
        mRect.set(cx - size, cy - size * 0.65f, cx + size, cy + size * 0.65f);
        canvas.drawOval(mRect, mDecPaint);

        // Shell scute pattern (cross + two diagonals)
        mDecPaint.setStyle(Paint.Style.STROKE);
        mDecPaint.setStrokeWidth(1f);
        mDecPaint.setColor(0xFF33691E);
        canvas.drawLine(cx, cy - size * 0.55f, cx, cy + size * 0.55f, mDecPaint);
        canvas.drawLine(cx - size * 0.55f, cy, cx + size * 0.55f, cy, mDecPaint);
        canvas.drawLine(cx - size * 0.45f, cy - size * 0.30f, cx + size * 0.45f, cy + size * 0.30f, mDecPaint);
        canvas.drawLine(cx + size * 0.45f, cy - size * 0.30f, cx - size * 0.45f, cy + size * 0.30f, mDecPaint);

        // Head
        mDecPaint.setStyle(Paint.Style.FILL);
        mDecPaint.setColor(0xFF7CB342);
        canvas.drawCircle(cx + size * 1.28f, cy, size * 0.26f, mDecPaint);
    }

    // -----------------------------------------------------------------------
    // Sky elements
    // -----------------------------------------------------------------------

    private void drawSun(Canvas canvas) {
        float inner = SUN_R + 3f;
        float outer = SUN_R + 9f;
        // Very slow ray rotation tied to wave phase
        double base = mWavePhase * 0.015;
        for (int i = 0; i < 8; i++) {
            double a = base + Math.PI * 2 * i / 8;
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            canvas.drawLine(
                SUN_X + inner * cos, SUN_Y + inner * sin,
                SUN_X + outer * cos, SUN_Y + outer * sin,
                mRayPaint);
        }
        canvas.drawCircle(SUN_X, SUN_Y, SUN_R, mSunPaint);
    }

    private void drawClouds(Canvas canvas) {
        drawCloud(canvas,  70f, 17f, 20f);
        drawCloud(canvas, 172f, 11f, 16f);
    }

    /** Puffy cloud: three overlapping circles forming a rounded mass. */
    private void drawCloud(Canvas canvas, float cx, float cy, float r) {
        canvas.drawCircle(cx,             cy,             r * 0.60f,  mCloudPaint);
        canvas.drawCircle(cx - r * 0.52f, cy + r * 0.22f, r * 0.48f, mCloudPaint);
        canvas.drawCircle(cx + r * 0.52f, cy + r * 0.22f, r * 0.48f, mCloudPaint);
        canvas.drawCircle(cx,             cy + r * 0.30f, r * 0.42f, mCloudPaint);
    }

    // -----------------------------------------------------------------------
    // Range readout
    // -----------------------------------------------------------------------

    private void drawRange(Canvas canvas) {
        String numStr  = mRange >= 0 ? String.valueOf(mRange) : "---";
        String unitStr = mRangeInMiles ? "mi" : "km";
        canvas.drawText("RANGE", CX, LABEL_Y, mLabelPaint);
        canvas.drawText(numStr,  CX, NUM_Y,   mNumPaint);
        canvas.drawText(unitStr, CX, UNITS_Y, mUnitPaint);
    }
}
