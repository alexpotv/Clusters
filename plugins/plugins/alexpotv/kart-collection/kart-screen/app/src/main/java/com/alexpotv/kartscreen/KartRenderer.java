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
        "void main() {\n"         +
        // Transform normal to world space (model has no non-uniform scale, so
        // the upper-left 3×3 of uModel is the correct normal matrix).
        "  vec3 wn = normalize(vec3(uModel * vec4(aNorm, 0.0)));\n" +
        // Fixed overhead-left key light in world space.
        "  vec3 l = normalize(vec3(0.5, 1.0, 0.7));\n" +
        "  vDiff = max(dot(wn, l), 0.0);\n" +
        "  vUv   = aUv;\n" +
        "  gl_Position = uMVP * vec4(aPos, 1.0);\n" +
        "}";

    private static final String FRAG_SRC =
        "precision mediump float;\n" +
        "uniform sampler2D uTex;\n"  +
        "uniform float uBrake;\n"    +
        "varying vec2  vUv;\n"       +
        "varying float vDiff;\n"     +
        "void main() {\n"            +
        "  vec4 c    = texture2D(uTex, vUv);\n" +
        "  float lit = 0.30 + vDiff * 0.70;\n"  +
        // Brake tint: add a subtle red glow from behind the model.
        "  vec3 rgb  = c.rgb * lit + vec3(uBrake * 0.35, 0.0, 0.0);\n" +
        "  gl_FragColor = vec4(rgb, c.a);\n" +
        "}";

    // ---- GL handles ---------------------------------------------------------

    private int mProgram;
    private int mAttr_aPos, mAttr_aNorm, mAttr_aUv;
    private int mUni_uMVP, mUni_uModel, mUni_uTex, mUni_uBrake;
    private int mWhiteTex;

    // ---- Scene data ---------------------------------------------------------

    private List<Mesh> mKartMeshes      = Collections.emptyList();
    private List<Mesh> mCharacterMeshes = Collections.emptyList();

    // ---- Animation state (written on main thread, read on GL thread) --------

    volatile float   mSteeringDeg = 0f;
    volatile int     mSpeedKmh    = 0;
    volatile boolean mBraking     = false;

    // Idle showcase spin state (GL-thread only)
    private float mIdleAngle  = 0f;
    private long  mPrevTimeMs = 0L;

    // ---- Matrices -----------------------------------------------------------

    private final float[] mProjection = new float[16];
    private final float[] mView       = new float[16];
    private final float[] mModel      = new float[16];
    private final float[] mMVP        = new float[16];
    private final float[] mMV         = new float[16]; // scratch only

    private final PluginContext mCtx;

    KartRenderer(PluginContext ctx) {
        mCtx = ctx;
    }

    // ---- Setters called from main thread ------------------------------------

    void setSteeringDeg(float deg)  { mSteeringDeg = deg;  }
    void setSpeedKmh(int kmh)       { mSpeedKmh    = kmh;  }
    void setBraking(boolean b)      { mBraking     = b;    }

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
        mUni_uMVP   = GLES20.glGetUniformLocation(mProgram, "uMVP");
        mUni_uModel = GLES20.glGetUniformLocation(mProgram, "uModel");
        mUni_uTex   = GLES20.glGetUniformLocation(mProgram, "uTex");
        mUni_uBrake = GLES20.glGetUniformLocation(mProgram, "uBrake");

        mWhiteTex = TextureLoader.createWhite();

        // Camera: rear-left quarter view, slightly elevated.
        Matrix.setLookAtM(mView, 0,
                /* eye    */ 1.5f, 1.2f, 2.5f,
                /* target */ 0f,   0.2f, 0f,
                /* up     */ 0f,   1f,   0f);

        loadModels();
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

        // ---- Idle showcase spin ----
        // Engage when the car is parked and steering is centred.
        boolean idle = mSpeedKmh < 3 && Math.abs(mSteeringDeg) < 5f;
        if (idle) {
            mIdleAngle = (mIdleAngle + dt * 18f) % 360f; // 18 °/s
        } else {
            // Fade idle rotation out so there's no abrupt snap when driving starts.
            mIdleAngle *= Math.max(0f, 1f - dt * 4f);
        }

        // ---- Build model matrix ----
        Matrix.setIdentityM(mModel, 0);

        // Steering yaw: Honda Civic full lock ≈ ±270 °  →  ±30 ° visual.
        float steer = Float.isNaN(mSteeringDeg) ? 0f : mSteeringDeg;
        float yaw   = steer * (30f / 270f) + mIdleAngle;
        yaw = Math.max(-35f, Math.min(35f + mIdleAngle, yaw)); // hard clamp when not idling
        Matrix.rotateM(mModel, 0, yaw, 0f, 1f, 0f);

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
        GLES20.glUniform1i(mUni_uTex,   0);
        GLES20.glUniform1f(mUni_uBrake, mBraking ? 1f : 0f);

        drawList(mKartMeshes);
        drawList(mCharacterMeshes);
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
