package com.alexpotv.kartscreen;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;

import com.example.clusterapi.PluginContext;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * GLES2 renderer for the kart + character scene.
 *
 * Animation (all driven from the main thread via volatile fields):
 *   steering  → Y-axis yaw (±30° visual, mapped from Honda Civic ±270° full lock)
 *   speed     → slight nose-down pitch at speed
 *   braking   → reddish ambient tint simulating brake-light back-scatter
 *
 * When stationary and steering is centred the model slowly auto-rotates to
 * showcase the model from all angles.
 */
class KartRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = "KartRenderer";

    // ---- GLSL shaders -------------------------------------------------------

    private static final String VERT_SRC =
        "attribute vec3 aPos;\n"  +
        "attribute vec3 aNorm;\n" +
        "attribute vec2 aUv;\n"   +
        "uniform mat4 uMVP;\n"    +
        "uniform mat4 uModel;\n"  +
        "varying vec2  vUv;\n"    +
        "varying float vDiff;\n"  +
        "varying vec3  vModelPos;\n" +
        "void main() {\n"         +
        // Pass model-space position for brake-light distance calc in fragment shader.
        "  vModelPos = aPos;\n" +
        // Transform normal to world space (model has no non-uniform scale, so
        // the upper-left 3×3 of uModel is the correct normal matrix).
        "  vec3 wn = normalize(vec3(uModel * vec4(aNorm, 0.0)));\n" +
        // Fixed overhead-left key light in world space.
        "  vec3 l = normalize(vec3(0.5, 1.0, 0.7));\n" +
        "  vDiff = max(dot(wn, l), 0.0);\n" +
        "  vUv   = aUv;\n" +
        "  gl_Position = uMVP * vec4(aPos, 1.0);\n" +
        "}";

    // spot(p, c) — radial falloff: 1/(1 + k·d²), k=30 → half-brightness at ~0.18 model units.
    private static final String FRAG_SRC =
        "precision mediump float;\n"     +
        "uniform sampler2D uTex;\n"      +
        "uniform float uBrake;\n"        +
        "uniform vec3  uBrakePos0;\n"    +
        "uniform vec3  uBrakePos1;\n"    +
        "uniform float uHead;\n"         +
        "uniform vec3  uHeadPos0;\n"     +
        "uniform vec3  uHeadPos1;\n"     +
        "uniform float uBlinkerL;\n"     +
        "uniform vec3  uBlinkerLPos0;\n" +
        "uniform vec3  uBlinkerLPos1;\n" +
        "uniform float uBlinkerR;\n"     +
        "uniform vec3  uBlinkerRPos0;\n" +
        "uniform vec3  uBlinkerRPos1;\n" +
        "varying vec2  vUv;\n"           +
        "varying float vDiff;\n"         +
        "varying vec3  vModelPos;\n"     +
        "float spot(vec3 p, vec3 c) { float d = length(p - c); return 1.0 / (1.0 + d*d*30.0); }\n" +
        "void main() {\n"                +
        "  vec4  col  = texture2D(uTex, vUv);\n"  +
        "  float lit  = 0.30 + vDiff * 0.70;\n"   +
        "  float brake   = uBrake    * (spot(vModelPos, uBrakePos0)    + spot(vModelPos, uBrakePos1));\n"    +
        "  float head    = uHead     * (spot(vModelPos, uHeadPos0)     + spot(vModelPos, uHeadPos1));\n"     +
        "  float blinker = uBlinkerL * (spot(vModelPos, uBlinkerLPos0) + spot(vModelPos, uBlinkerLPos1))\n"  +
        "                + uBlinkerR * (spot(vModelPos, uBlinkerRPos0) + spot(vModelPos, uBlinkerRPos1));\n"  +
        "  vec3 rgb = col.rgb * lit\n"                                         +
        "           + vec3(brake   * 0.70, 0.0,             0.0          )\n"  +
        "           + vec3(head    * 0.80, head    * 0.80,  head   * 0.80)\n"  +
        "           + vec3(blinker * 0.80, blinker * 0.40,  0.0          );\n" +
        "  gl_FragColor = vec4(rgb, col.a);\n" +
        "}";

    // ---- Rainbow road shaders -----------------------------------------------

    private static final String ROAD_VERT_SRC =
        "attribute vec3 aPos;\n"     +
        "attribute vec2 aUv;\n"      +
        "uniform mat4 uMVP;\n"       +
        "uniform float uOffset;\n"   +
        "varying vec2 vUv;\n"        +
        "void main() {\n"            +
        "  vUv = vec2(aUv.x, aUv.y - uOffset);\n" +
        "  gl_Position = uMVP * vec4(aPos, 1.0);\n" +
        "}";

    private static final String ROAD_FRAG_SRC =
        "precision mediump float;\n" +
        "uniform sampler2D uTex;\n"  +
        "varying vec2 vUv;\n"        +
        "void main() {\n"            +
        "  gl_FragColor = texture2D(uTex, vec2(vUv.y, 0.5));\n" +
        "}";

    // ---- GL handles ---------------------------------------------------------

    // Placeholder light positions in model space — tune X/Y/Z once kart geometry is confirmed.
    private static final float[] BRAKE_POS_L    = { -0.25f,  0.15f, -0.5f };
    private static final float[] BRAKE_POS_R    = {  0.25f,  0.15f, -0.5f };
    private static final float[] HEAD_POS_L     = { -0.25f,  0.15f,  0.5f };
    private static final float[] HEAD_POS_R     = {  0.25f,  0.15f,  0.5f };
    private static final float[] BLINKER_L_POS0 = { -0.35f,  0.15f,  0.4f };
    private static final float[] BLINKER_L_POS1 = { -0.35f,  0.15f, -0.4f };
    private static final float[] BLINKER_R_POS0 = {  0.35f,  0.15f,  0.4f };
    private static final float[] BLINKER_R_POS1 = {  0.35f,  0.15f, -0.4f };

    private int mProgram;
    private int mAttr_aPos, mAttr_aNorm, mAttr_aUv;
    private int mUni_uMVP, mUni_uModel, mUni_uTex;
    private int mUni_uBrake,    mUni_uBrakePos0,    mUni_uBrakePos1;
    private int mUni_uHead,     mUni_uHeadPos0,     mUni_uHeadPos1;
    private int mUni_uBlinkerL, mUni_uBlinkerLPos0, mUni_uBlinkerLPos1;
    private int mUni_uBlinkerR, mUni_uBlinkerRPos0, mUni_uBlinkerRPos1;
    private int mWhiteTex;

    // ---- Rainbow road GL handles --------------------------------------------

    private int mRoadProgram;
    private int mRoadAttr_aPos, mRoadAttr_aUv;
    private int mRoadUni_uMVP, mRoadUni_uOffset, mRoadUni_uTex;
    private int mRoadTex;
    private java.nio.FloatBuffer mRoadVbo;

    // ---- Scene data ---------------------------------------------------------

    private List<Mesh> mKartMeshes      = Collections.emptyList();
    private List<Mesh> mCharacterMeshes = Collections.emptyList();

    // ---- Animation state (written on main thread, read on GL thread) --------

    volatile float   mSteeringDeg  = 0f;
    volatile int     mSpeedKmh     = 0;
    volatile boolean mBraking      = false;
    // DrivetrainState.shiftPosition values: 0=P, 1=R, 2=N, 3=other, -1=unknown
    volatile int     mShiftPosition = -1;
    volatile boolean mHeadlights   = false;
    volatile boolean mBlinkerL     = false;
    volatile boolean mBlinkerR     = false;
    volatile boolean mLkasActive   = false;

    // Idle showcase spin state (GL-thread only)
    private float mIdleAngle  = 0f;
    private float mRoadOffset = 0f;
    private long  mPrevTimeMs = 0L;

    // ---- Matrices -----------------------------------------------------------

    private final float[] mProjection = new float[16];
    private final float[] mView       = new float[16];
    private final float[] mModel      = new float[16];
    private final float[] mMVP        = new float[16];
    private final float[] mMV         = new float[16]; // scratch only
    private final float[] mRoadMVP   = new float[16];

    private final PluginContext mCtx;

    KartRenderer(PluginContext ctx) {
        mCtx = ctx;
    }

    // ---- Setters called from main thread ------------------------------------

    void setSteeringDeg(float deg)   { mSteeringDeg  = deg; }
    void setSpeedKmh(int kmh)        { mSpeedKmh     = kmh; }
    void setBraking(boolean b)       { mBraking      = b;   }
    void setShiftPosition(int pos)   { mShiftPosition = pos; }
    void setHeadlights(boolean b)    { mHeadlights   = b;   }
    void setBlinkerL(boolean b)      { mBlinkerL     = b;   }
    void setBlinkerR(boolean b)      { mBlinkerR     = b;   }
    void setLkasActive(boolean b)    { mLkasActive   = b;   }

    // ---- GLSurfaceView.Renderer ---------------------------------------------

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_CULL_FACE);

        mProgram    = buildProgram(VERT_SRC, FRAG_SRC);
        mAttr_aPos  = GLES20.glGetAttribLocation (mProgram, "aPos");
        mAttr_aNorm = GLES20.glGetAttribLocation (mProgram, "aNorm");
        mAttr_aUv   = GLES20.glGetAttribLocation (mProgram, "aUv");
        mUni_uMVP          = GLES20.glGetUniformLocation(mProgram, "uMVP");
        mUni_uModel        = GLES20.glGetUniformLocation(mProgram, "uModel");
        mUni_uTex          = GLES20.glGetUniformLocation(mProgram, "uTex");
        mUni_uBrake        = GLES20.glGetUniformLocation(mProgram, "uBrake");
        mUni_uBrakePos0    = GLES20.glGetUniformLocation(mProgram, "uBrakePos0");
        mUni_uBrakePos1    = GLES20.glGetUniformLocation(mProgram, "uBrakePos1");
        mUni_uHead         = GLES20.glGetUniformLocation(mProgram, "uHead");
        mUni_uHeadPos0     = GLES20.glGetUniformLocation(mProgram, "uHeadPos0");
        mUni_uHeadPos1     = GLES20.glGetUniformLocation(mProgram, "uHeadPos1");
        mUni_uBlinkerL     = GLES20.glGetUniformLocation(mProgram, "uBlinkerL");
        mUni_uBlinkerLPos0 = GLES20.glGetUniformLocation(mProgram, "uBlinkerLPos0");
        mUni_uBlinkerLPos1 = GLES20.glGetUniformLocation(mProgram, "uBlinkerLPos1");
        mUni_uBlinkerR     = GLES20.glGetUniformLocation(mProgram, "uBlinkerR");
        mUni_uBlinkerRPos0 = GLES20.glGetUniformLocation(mProgram, "uBlinkerRPos0");
        mUni_uBlinkerRPos1 = GLES20.glGetUniformLocation(mProgram, "uBlinkerRPos1");

        mWhiteTex = TextureLoader.createWhite();

        // Camera: rear-left quarter view, slightly elevated.
        Matrix.setLookAtM(mView, 0,
                /* eye    */ 2.0f, 1.8f, 3.5f,
                /* target */ 0f,   0.2f, 0f,
                /* up     */ 0f,   1f,   0f);

        loadModels();

        mRoadProgram     = buildProgram(ROAD_VERT_SRC, ROAD_FRAG_SRC);
        mRoadAttr_aPos   = GLES20.glGetAttribLocation (mRoadProgram, "aPos");
        mRoadAttr_aUv    = GLES20.glGetAttribLocation (mRoadProgram, "aUv");
        mRoadUni_uMVP    = GLES20.glGetUniformLocation(mRoadProgram, "uMVP");
        mRoadUni_uOffset = GLES20.glGetUniformLocation(mRoadProgram, "uOffset");
        mRoadUni_uTex    = GLES20.glGetUniformLocation(mRoadProgram, "uTex");
        mRoadTex         = createRainbowTexture();
        mRoadVbo         = buildRoadMesh();

        mPrevTimeMs = 0L;
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int w, int h) {
        GLES20.glViewport(0, 0, w, h);
        // Wide cluster display (≈ 485 × 190 px → aspect ≈ 2.55).
        Matrix.perspectiveM(mProjection, 0, 45f, (float) w / Math.max(h, 1), 0.1f, 100f);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // ---- Time delta ----
        long now = SystemClock.elapsedRealtime();
        float dt = mPrevTimeMs == 0L ? 0f : (now - mPrevTimeMs) / 1000f;
        // Clamp dt so a long GL-thread pause doesn't produce a huge jump.
        if (dt > 0.25f) dt = 0.25f;
        mPrevTimeMs = now;

        // ---- Showcase spin vs. steering yaw ----
        // Positions 1 (R) and 2 (N): auto-rotate for display showcase.
        // All other positions: steer-mapped yaw clamped to ±30°.
        boolean showcase = mShiftPosition == 1 || mShiftPosition == 2;
        if (showcase) {
            mIdleAngle = (mIdleAngle + dt * 18f) % 360f; // 18 °/s
        } else {
            mIdleAngle *= Math.max(0f, 1f - dt * 4f);
        }

        // ---- Build model matrix ----
        Matrix.setIdentityM(mModel, 0);

        float yaw;
        if (showcase) {
            yaw = mIdleAngle;
        } else {
            // Honda Civic full lock ≈ ±270°  →  visual ±30°, hard-clamped.
            float steer = Float.isNaN(mSteeringDeg) ? 0f : mSteeringDeg;
            yaw = Math.max(-60f, Math.min(60f, -4*steer * (60f / 270f)));
        }
        Matrix.rotateM(mModel, 0, yaw, 0f, 1f, 0f);
        Matrix.rotateM(mModel, 0, -150f, 0f, 1f, 0f); // model rest-pose offset

        // Speed pitch: nose down at speed (aerodynamic press simulation).
        float speedNorm = Math.max(0f, Math.min(mSpeedKmh / 120f, 1f));
        Matrix.rotateM(mModel, 0, -speedNorm * 5f, 1f, 0f, 0f);

        // ---- Compute MVP ----
        Matrix.multiplyMM(mMV, 0, mView, 0, mModel, 0);
        Matrix.multiplyMM(mMVP, 0, mProjection, 0, mMV, 0);

        // ---- Draw ----
        GLES20.glUseProgram(mProgram);
        GLES20.glUniformMatrix4fv(mUni_uMVP,   1, false, mMVP,   0);
        GLES20.glUniformMatrix4fv(mUni_uModel, 1, false, mModel, 0);
        GLES20.glUniform1i(mUni_uTex, 0);
        GLES20.glUniform1f(mUni_uBrake,    mBraking    ? 1f : 0f);
        GLES20.glUniform3fv(mUni_uBrakePos0,    1, BRAKE_POS_L,    0);
        GLES20.glUniform3fv(mUni_uBrakePos1,    1, BRAKE_POS_R,    0);
        GLES20.glUniform1f(mUni_uHead,     mHeadlights ? 1f : 0f);
        GLES20.glUniform3fv(mUni_uHeadPos0,     1, HEAD_POS_L,     0);
        GLES20.glUniform3fv(mUni_uHeadPos1,     1, HEAD_POS_R,     0);
        GLES20.glUniform1f(mUni_uBlinkerL,  mBlinkerL   ? 1f : 0f);
        GLES20.glUniform3fv(mUni_uBlinkerLPos0, 1, BLINKER_L_POS0, 0);
        GLES20.glUniform3fv(mUni_uBlinkerLPos1, 1, BLINKER_L_POS1, 0);
        GLES20.glUniform1f(mUni_uBlinkerR,  mBlinkerR   ? 1f : 0f);
        GLES20.glUniform3fv(mUni_uBlinkerRPos0, 1, BLINKER_R_POS0, 0);
        GLES20.glUniform3fv(mUni_uBlinkerRPos1, 1, BLINKER_R_POS1, 0);

        drawList(mKartMeshes);
        drawList(mCharacterMeshes);

        // ---- Rainbow road (LKAS active) ----
        mRoadOffset = (mRoadOffset + dt * 0.15f) % 1.0f;
        if (mLkasActive) {
            Matrix.setIdentityM(mModel, 0);
            Matrix.rotateM(mModel, 0, 30f, 0f, 1f, 0f);
            Matrix.multiplyMM(mMV, 0, mView, 0, mModel, 0);
            Matrix.multiplyMM(mRoadMVP, 0, mProjection, 0, mMV, 0);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glDepthMask(false);
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            GLES20.glUseProgram(mRoadProgram);
            GLES20.glUniformMatrix4fv(mRoadUni_uMVP, 1, false, mRoadMVP, 0);
            GLES20.glUniform1f(mRoadUni_uOffset, mRoadOffset);
            GLES20.glUniform1i(mRoadUni_uTex, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mRoadTex);
            int roadStride = 5 * 4;
            mRoadVbo.position(0);
            GLES20.glVertexAttribPointer(mRoadAttr_aPos, 3, GLES20.GL_FLOAT, false, roadStride, mRoadVbo);
            GLES20.glEnableVertexAttribArray(mRoadAttr_aPos);
            mRoadVbo.position(3);
            GLES20.glVertexAttribPointer(mRoadAttr_aUv,  2, GLES20.GL_FLOAT, false, roadStride, mRoadVbo);
            GLES20.glEnableVertexAttribArray(mRoadAttr_aUv);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
            GLES20.glDepthMask(true);
            GLES20.glDisable(GLES20.GL_BLEND);
            GLES20.glEnable(GLES20.GL_CULL_FACE);
        }
    }

    // ---- Internal -----------------------------------------------------------

    private void loadModels() {
        try {
            mKartMeshes = ObjLoader.load(mCtx, "kart.obj");
            uploadTextures(mKartMeshes);
        } catch (IOException e) {
            Log.e(TAG, "Could not load kart.obj — place it in assets/", e);
        }
        try {
            mCharacterMeshes = ObjLoader.load(mCtx, "character.obj");
            uploadTextures(mCharacterMeshes);
        } catch (IOException e) {
            Log.e(TAG, "Could not load character.obj — place it in assets/", e);
        }
    }

    private void uploadTextures(List<Mesh> meshes) {
        for (Mesh m : meshes) {
            if (m.textureName == null) continue;
            try {
                m.glTexture = TextureLoader.load(mCtx.openAsset(m.textureName));
            } catch (IOException e) {
                Log.w(TAG, "Texture not found: " + m.textureName);
            }
        }
    }

    private void drawList(List<Mesh> meshes) {
        for (Mesh m : meshes) {
            int tex = m.glTexture != 0 ? m.glTexture : mWhiteTex;
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);

            m.buffer.position(Mesh.OFFSET_POS);
            GLES20.glVertexAttribPointer(mAttr_aPos,  3, GLES20.GL_FLOAT, false, Mesh.STRIDE, m.buffer);
            GLES20.glEnableVertexAttribArray(mAttr_aPos);

            m.buffer.position(Mesh.OFFSET_NORM);
            GLES20.glVertexAttribPointer(mAttr_aNorm, 3, GLES20.GL_FLOAT, false, Mesh.STRIDE, m.buffer);
            GLES20.glEnableVertexAttribArray(mAttr_aNorm);

            m.buffer.position(Mesh.OFFSET_UV);
            GLES20.glVertexAttribPointer(mAttr_aUv,   2, GLES20.GL_FLOAT, false, Mesh.STRIDE, m.buffer);
            GLES20.glEnableVertexAttribArray(mAttr_aUv);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, m.vertexCount);
        }
    }

    // ---- Rainbow road helpers -----------------------------------------------

    private static int createRainbowTexture() {
        int w = 256;
        int[] pixels = new int[w];
        for (int x = 0; x < w; x++) {
            pixels[x] = hsvToArgb(x * 360f / w, 1f, 1f, 0.78f);
        }
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                pixels, w, 1, android.graphics.Bitmap.Config.ARGB_8888);
        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0]);
        android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        bmp.recycle();
        return tex[0];
    }

    private static int hsvToArgb(float hue, float s, float v, float a) {
        float h = hue / 60f;
        int   i = (int) h % 6;
        float f = h - (int) h;
        float p = v * (1 - s);
        float q = v * (1 - s * f);
        float t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i) {
            case 0:  r = v; g = t; b = p; break;
            case 1:  r = q; g = v; b = p; break;
            case 2:  r = p; g = v; b = t; break;
            case 3:  r = p; g = q; b = v; break;
            case 4:  r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return ((int)(a*255) << 24) | ((int)(r*255) << 16) | ((int)(g*255) << 8) | (int)(b*255);
    }

    private static java.nio.FloatBuffer buildRoadMesh() {
        // Flat quad at y=-0.15, width 1.6 (x), length 6.0 (z: -3..+3).
        // CCW winding from +Y so the face is visible from the camera above.
        // UV: u across width (0..1), v along length (0..3, tiles 3×).
        float[] v = {
            // triangle 1
            -1.5f, -0.15f, -3.0f,  0f, 0f,
             1.5f, -0.15f,  3.0f,  1f, 3f,
             1.5f, -0.15f, -3.0f,  1f, 0f,
            // triangle 2
            -1.5f, -0.15f, -3.0f,  0f, 0f,
            -1.5f, -0.15f,  3.0f,  0f, 3f,
             1.5f, -0.15f,  3.0f,  1f, 3f,
        };
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocateDirect(v.length * 4);
        bb.order(java.nio.ByteOrder.nativeOrder());
        java.nio.FloatBuffer fb = bb.asFloatBuffer();
        fb.put(v);
        fb.position(0);
        return fb;
    }

    // ---- Shader helpers -----------------------------------------------------

    private static int buildProgram(String vertSrc, String fragSrc) {
        int vs   = compile(GLES20.GL_VERTEX_SHADER,   vertSrc);
        int fs   = compile(GLES20.GL_FRAGMENT_SHADER, fragSrc);
        int prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, vs);
        GLES20.glAttachShader(prog, fs);
        GLES20.glLinkProgram(prog);
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        return prog;
    }

    private static int compile(int type, String src) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, src);
        GLES20.glCompileShader(shader);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "shader log: " + GLES20.glGetShaderInfoLog(shader));
        }
        return shader;
    }
}
