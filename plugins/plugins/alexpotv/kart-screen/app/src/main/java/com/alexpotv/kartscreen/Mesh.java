package com.alexpotv.kartscreen;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Holds one draw-call worth of geometry: interleaved vertex data on the CPU side
 * and (once uploaded) a GL texture ID.
 *
 * Vertex layout per element: [x, y, z,  nx, ny, nz,  u, v]  (8 floats = 32 bytes)
 */
class Mesh {

    static final int FLOATS_PER_VERTEX = 8;
    static final int STRIDE            = FLOATS_PER_VERTEX * 4; // bytes

    // Byte offsets within one vertex
    static final int OFFSET_POS  = 0;
    static final int OFFSET_NORM = 3;
    static final int OFFSET_UV   = 6;

    final String      name;
    final FloatBuffer buffer;
    final int         vertexCount;

    String textureName; // asset filename, resolved from MTL; null = no texture
    int    glTexture;   // 0 until TextureLoader.upload() fills it in

    Mesh(String name, float[] data) {
        this.name        = name;
        this.vertexCount = data.length / FLOATS_PER_VERTEX;

        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
        bb.order(ByteOrder.nativeOrder());
        buffer = bb.asFloatBuffer();
        buffer.put(data);
        buffer.position(0);
    }
}
