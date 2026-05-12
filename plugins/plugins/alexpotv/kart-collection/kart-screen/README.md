# Kart Screen

Displays a go-kart and driver 3D model in the cluster, animated with live vehicle data.

## Preparing your 3D assets

The plugin loads **Wavefront OBJ** files — you need to re-export your `.dae` files to `.obj` before building. This is a one-step operation in any 3D tool.

### Blender export steps

1. Open your Blender scene with the kart and character.
2. Select the kart mesh (or all kart objects).
3. **File → Export → Wavefront (.obj)**
4. In the export dialog, enable:
   - ✅ Include Normals
   - ✅ Include UVs
   - ✅ Write Materials
   - ✅ Triangulate Faces  ← important, avoids polygon-fan edge cases
   - Forward Axis: **-Z** (default)
   - Up Axis: **Y** (default)
5. Name the file `kart.obj` and export.
6. Repeat for the character mesh → `character.obj`.

> Both models must be exported from the **same world coordinate system** so the
> character sits correctly inside the kart. Export from the same scene without
> moving either object between exports.

> Centre both models around the world origin (0, 0, 0) in Blender so they appear
> centred in the cluster view.

### Placing files

Copy the following into `app/src/main/assets/`:

```
assets/
  plugin.json        (already present)
  kart.obj
  kart.mtl
  character.obj
  character.mtl
  kart_texture.png   (or whatever your MTL references)
  character_texture.png
  …
```

Texture filenames are taken directly from the MTL file. The loader strips any
directory prefix, so only the bare filename matters.

## Animation mapping

| Vehicle signal | Visual effect |
|---|---|
| `steering.angleDeg` | Yaw the kart ±30° (mapped from ±270° full lock) |
| `motion.speedKmh` | Nose-down pitch up to −5° at 120 km/h |
| `brakes.pedalPressed` | Red ambient tint (brake-light back-scatter) |
| Parked + centred | Slow 18°/s auto-showcase rotation |

## Building

Open `kart-screen/` as a Gradle project in Android Studio, or build from the repo
root:

```bash
cd plugins/plugins/alexpotv/kart-screen
./gradlew assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`.
