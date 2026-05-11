package com.alexpotv.kartscreen;

import com.example.clusterapi.PluginContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal Wavefront OBJ loader.
 *
 * Reads positions, normals, and UV coordinates, triangulates polygons (fan method),
 * and resolves diffuse textures via the accompanying MTL file.
 * Produces one {@link Mesh} per material group.
 *
 * Export checklist from Blender (File → Export → Wavefront .obj):
 *   ✓ Include Normals
 *   ✓ Include UVs
 *   ✓ Write Materials
 *   ✓ Triangulate Faces
 *   Forward: -Z  |  Up: Y  (defaults)
 */
class ObjLoader {

    static List<Mesh> load(PluginContext ctx, String objAsset) throws IOException {
        List<float[]> positions = new ArrayList<>();
        List<float[]> normals   = new ArrayList<>();
        List<float[]> uvs       = new ArrayList<>();

        Map<String, String> materials = new HashMap<>(); // materialName → textureAssetName
        String   currentMat   = null;
        String   currentGroup = "default";
        List<Float> verts     = new ArrayList<>();
        List<Mesh>  meshes    = new ArrayList<>();

        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(ctx.openAsset(objAsset)))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') continue;

                if (line.startsWith("v ")) {
                    positions.add(parseXyz(line));
                } else if (line.startsWith("vn ")) {
                    normals.add(parseXyz(line));
                } else if (line.startsWith("vt ")) {
                    float[] f = parseXy(line);
                    f[1] = 1f - f[1]; // flip V for OpenGL convention
                    uvs.add(f);
                } else if (line.startsWith("mtllib ")) {
                    materials.putAll(parseMtl(ctx, line.substring(7).trim()));
                } else if (line.startsWith("usemtl ")) {
                    flush(meshes, verts, currentMat, materials, currentGroup);
                    currentMat = line.substring(7).trim();
                } else if (line.startsWith("o ") || line.startsWith("g ")) {
                    flush(meshes, verts, currentMat, materials, currentGroup);
                    currentGroup = line.substring(2).trim();
                } else if (line.startsWith("f ")) {
                    addFace(verts, line, positions, normals, uvs);
                }
            }
        }

        flush(meshes, verts, currentMat, materials, currentGroup);
        return meshes.isEmpty() ? Collections.<Mesh>emptyList() : meshes;
    }

    // -------------------------------------------------------------------------

    private static float[] parseXyz(String line) {
        String[] p = line.split("\\s+");
        return new float[]{ Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3]) };
    }

    private static float[] parseXy(String line) {
        String[] p = line.split("\\s+");
        return new float[]{ Float.parseFloat(p[1]), Float.parseFloat(p[2]) };
    }

    /** Fan-triangulate a polygon face and emit interleaved vertices. */
    private static void addFace(List<Float> out, String line,
                                 List<float[]> pos, List<float[]> nor, List<float[]> uvs) {
        String[] parts = line.split("\\s+");
        // Fan from vertex 0: triangles (0,1,2), (0,2,3), (0,3,4) …
        for (int i = 2; i < parts.length - 1; i++) {
            emit(out, parts[1],   pos, nor, uvs);
            emit(out, parts[i],   pos, nor, uvs);
            emit(out, parts[i+1], pos, nor, uvs);
        }
    }

    /** Decode one face-vertex spec ("v/t/n" or "v//n" or "v") and append to the list. */
    private static void emit(List<Float> out, String spec,
                              List<float[]> pos, List<float[]> nor, List<float[]> uvs) {
        String[] tok = spec.split("/");
        int vi = resolve(tok, 0, pos.size());
        int ti = resolve(tok, 1, uvs.size());
        int ni = resolve(tok, 2, nor.size());

        float[] p = pos.get(vi);
        out.add(p[0]); out.add(p[1]); out.add(p[2]);

        if (ni >= 0 && ni < nor.size()) {
            float[] n = nor.get(ni);
            out.add(n[0]); out.add(n[1]); out.add(n[2]);
        } else {
            out.add(0f); out.add(1f); out.add(0f); // up normal fallback
        }

        if (ti >= 0 && ti < uvs.size()) {
            float[] t = uvs.get(ti);
            out.add(t[0]); out.add(t[1]);
        } else {
            out.add(0f); out.add(0f);
        }
    }

    /** Convert a 1-based (or negative-relative) OBJ index token to a 0-based list index. */
    private static int resolve(String[] tok, int slot, int listSize) {
        if (slot >= tok.length || tok[slot].isEmpty()) return -1;
        int v = Integer.parseInt(tok[slot]);
        return v < 0 ? listSize + v : v - 1;
    }

    private static void flush(List<Mesh> meshes, List<Float> verts,
                               String mat, Map<String, String> materials, String name) {
        if (verts.isEmpty()) return;
        float[] arr = new float[verts.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = verts.get(i);
        Mesh m = new Mesh(name, arr);
        m.textureName = materials.get(mat);
        meshes.add(m);
        verts.clear();
    }

    // -------------------------------------------------------------------------

    private static Map<String, String> parseMtl(PluginContext ctx, String mtlAsset) {
        Map<String, String> result = new HashMap<>();
        String current = null;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(ctx.openAsset(mtlAsset)))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("newmtl ")) {
                    current = line.substring(7).trim();
                } else if (line.startsWith("map_Kd ") && current != null) {
                    String filename = line.substring(7).trim();
                    // Strip any directory prefix the exporter may have inserted
                    int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
                    if (slash >= 0) filename = filename.substring(slash + 1);
                    result.put(current, filename);
                }
            }
        } catch (IOException ignored) {}
        return result;
    }
}
