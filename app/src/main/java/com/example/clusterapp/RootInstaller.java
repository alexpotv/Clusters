package com.example.clusterapp;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

/**
 * First-run root self-installer.
 *
 * <p>The {@code VEHICLE_RW} permission this app needs (CpuComService, VehicleCoordinationService) is
 * {@code signatureOrSystem}: PackageManager only grants it to apps installed under a system path. On
 * this platform we can't platform-sign, so instead — on a rooted device — we copy our own APK into
 * the system app directory and trigger a reboot. After the reboot PackageManager rescans, marks the
 * app {@code FLAG_SYSTEM}, and grants {@code VEHICLE_RW}; the existing in-process binder code in
 * {@link VehicleDataSource} / {@link ClimateDataSource} then works unchanged.
 *
 * <p><b>Platform note.</b> {@code /system/priv-app} is not scanned by PackageManager until Android
 * 4.4 (API 19). This head unit is Android 4.2.2 (API 17), where the privileged-apps location is
 * {@code /system/app}. The installer targets the correct directory for the running OS and cleans up
 * any stale {@code /system/priv-app} copy from an earlier manual attempt (which would never have
 * been registered on 4.2.2 — the cause of the "activity not found" launch failure).
 *
 * <p>All root work runs in a child {@code su} process, so the only privileged UID involved is the
 * one the device's su manager (Magisk/SuperSU) grants when it shows its prompt on first use.
 */
public final class RootInstaller {

    private static final String TAG = "RootInstaller";

    /** signatureOrSystem permission gating CpuComService + VehicleCoordinationService. */
    public static final String VEHICLE_RW = "com.mitsubishielectric.ada.permission.VEHICLE_RW";

    /** Filename we install as, inside the chosen system dir. */
    private static final String APK_NAME = "Clusters.apk";

    private RootInstaller() {}

    /** Outcome of an {@link #install} attempt. */
    public static final class Result {
        public final boolean ok;
        public final String targetPath;
        public final String log;
        Result(boolean ok, String targetPath, String log) {
            this.ok = ok; this.targetPath = targetPath; this.log = log;
        }
    }

    // ── Status queries ─────────────────────────────────────────────────────────

    /** True once the app is installed under a system path (survives a reboot after {@link #install}). */
    public static boolean isSystemApp(Context ctx) {
        ApplicationInfo ai = ctx.getApplicationInfo();
        return (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }

    /** True if PackageManager has actually granted VEHICLE_RW to this package. */
    public static boolean hasVehicleRw(Context ctx) {
        try {
            return ctx.getPackageManager().checkPermission(VEHICLE_RW, ctx.getPackageName())
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    /** Everything already in place — nothing to do. */
    public static boolean isFullyProvisioned(Context ctx) {
        return isSystemApp(ctx) && hasVehicleRw(ctx);
    }

    /** Best-effort check for working root (triggers the su prompt the first time). */
    public static boolean hasRoot() {
        return runAsRoot("id -u").ok;
    }

    /** Directory PackageManager scans for privileged/system apps on the running OS. */
    public static String systemAppDir() {
        // /system/priv-app is only scanned from API 19 (Android 4.4) onward.
        if (Build.VERSION.SDK_INT >= 19) {
            return "/system/priv-app";
        }
        return "/system/app";
    }

    // ── Install ─────────────────────────────────────────────────────────────────

    /**
     * Copy this APK into the system app directory as root. Blocking — call off the main thread.
     * Requires a reboot afterwards for PackageManager to rescan and grant VEHICLE_RW.
     */
    public static Result install(Context ctx) {
        String src = ctx.getApplicationInfo().sourceDir;
        String targetDir = systemAppDir();
        String target = targetDir + "/" + APK_NAME;

        // Built so it degrades gracefully across toolbox/busybox variants and SELinux states.
        // A final CLUSTERS_OK / CLUSTERS_FAIL marker lets us verify without trusting exit codes.
        String script =
                "set -x\n"
              + "mount -o rw,remount /system 2>/dev/null || "
              +   "mount -o rw,remount / 2>/dev/null || "
              +   "{ DEV=$(grep ' /system ' /proc/mounts | head -n1 | cut -d' ' -f1); "
              +     "mount -o rw,remount \"$DEV\" /system 2>/dev/null; }\n"
              // Remove the stale priv-app copy from any earlier manual attempt (never scanned on 4.2.2).
              + "rm -rf /system/priv-app/Clusters 2>/dev/null\n"
              + "rm -f /system/priv-app/Clusters.apk 2>/dev/null\n"
              + "mkdir -p \"" + targetDir + "\"\n"
              + "cp -f \"" + src + "\" \"" + target + "\"\n"
              + "chmod 644 \"" + target + "\"\n"
              + "chown 0:0 \"" + target + "\" 2>/dev/null || chown 0.0 \"" + target + "\" 2>/dev/null\n"
              + "restorecon \"" + target + "\" 2>/dev/null\n"
              + "sync\n"
              + "if [ -f \"" + target + "\" ]; then echo CLUSTERS_OK; else echo CLUSTERS_FAIL; fi\n";

        Exec e = runAsRoot(script);
        boolean ok = e.ok && e.output.contains("CLUSTERS_OK");
        Log.d(TAG, "install ok=" + ok + " target=" + target + "\n" + e.output);
        return new Result(ok, target, e.output);
    }

    /** Reboot the device as root. Blocking. */
    public static boolean reboot() {
        // `reboot` may not return cleanly (the process dies with the system) — treat launch as success.
        runAsRoot("sync; setprop sys.powerctl reboot 2>/dev/null; reboot");
        return true;
    }

    // ── su exec plumbing ─────────────────────────────────────────────────────────

    private static final class Exec {
        final boolean ok;
        final String output;
        Exec(boolean ok, String output) { this.ok = ok; this.output = output; }
    }

    /** Feed {@code script} to a root shell over stdin; capture stdout+stderr; return exit status. */
    private static Exec runAsRoot(String script) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");
            final Process fp = p;

            // Drain stderr concurrently so the child can't block on a full pipe.
            final StringBuilder err = new StringBuilder();
            Thread errThread = new Thread(new Runnable() {
                @Override public void run() {
                    try (BufferedReader r =
                                 new BufferedReader(new InputStreamReader(fp.getErrorStream()))) {
                        String line;
                        while ((line = r.readLine()) != null) err.append(line).append('\n');
                    } catch (Exception ignored) {}
                }
            });
            errThread.start();

            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(script);
            os.writeBytes("\nexit\n");
            os.flush();

            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line).append('\n');
            }

            int code = p.waitFor();
            errThread.join(1000);
            if (err.length() > 0) out.append(err);
            return new Exec(code == 0, out.toString());
        } catch (Exception e) {
            // Most commonly: no su binary / root denied by the su manager.
            return new Exec(false, "root unavailable: " + e.getMessage());
        } finally {
            if (p != null) p.destroy();
        }
    }
}
