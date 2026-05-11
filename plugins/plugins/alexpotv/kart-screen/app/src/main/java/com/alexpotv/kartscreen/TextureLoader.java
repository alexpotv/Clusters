package com.alexpotv.kartscreen;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.io.IOException;
import java.io.InputStream;

/** Decodes a PNG/JPG from an InputStream and uploads it to a GLES2 texture. */
class TextureLoader {

    /**
     * @return a valid GL texture ID, or 0 on failure.
     *         Must be called on the GL thread.
     */
    static int load(InputStream is) throws IOException {
        Bitmap bmp;
        try {
            bmp = BitmapFactory.decodeStream(is);
        } finally {
            is.close();
        }
        if (bmp == null) return 0;

        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        int id = ids[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
        bmp.recycle();

        return id;
    }

    /** 1×1 opaque white texture used as a fallback when a mesh has no texture. */
    static int createWhite() {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        java.nio.ByteBuffer pixel = java.nio.ByteBuffer.wrap(
                new byte[]{ (byte)255, (byte)255, (byte)255, (byte)255 });
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixel);
        return ids[0];
    }
}
