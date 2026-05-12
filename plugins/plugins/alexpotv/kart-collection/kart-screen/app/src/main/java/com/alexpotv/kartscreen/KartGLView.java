package com.alexpotv.kartscreen;

import android.content.Context;
import android.opengl.GLSurfaceView;

/**
 * GLSurfaceView configured for GLES 2.0 continuous rendering.
 * This is the View returned from {@link KartScreen#onCreateView}.
 */
class KartGLView extends GLSurfaceView {

    KartGLView(Context ctx, KartRenderer renderer) {
        super(ctx);
        setEGLContextClientVersion(2);
        // Preserve the GL context across pause/resume so textures survive
        // screen switches without requiring a full reload.
        setPreserveEGLContextOnPause(true);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }
}
