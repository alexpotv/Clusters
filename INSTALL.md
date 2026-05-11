# Cluster Display App — Build and Install Guide

## How the app works

- **`MainActivity`** fills the main touchscreen with the image (fullscreen, black background),
  then starts `ClusterDisplayService`.
- **`ClusterDisplayService`** calls `DisplayManager.getDisplays()`, takes the last display
  (Display 1, the cluster panel), creates a `WindowManager` context for it, and adds a
  `TYPE_SYSTEM_OVERLAY` window containing the same image. This window sits above everything
  `ExternalDisplayOutService` rendered, so the cluster shows your image instead of the normal
  audio/navigation content.
- When the activity is destroyed (back button or `force-stop`), `onDestroy()` calls
  `stopService()`, which triggers `onDestroy()` in the service, which removes the overlay
  window — restoring the cluster to its normal state.

## Step 1 — Install Android Studio

Download from https://developer.android.com/studio and install (drag to Applications). First
launch installs the Android SDK automatically — accept all defaults. This takes a few minutes.

## Step 2 — Open the project

1. In Android Studio, choose **Open** (not "New Project")
2. Navigate to `ic1101/clusterApp` and click **Open**
3. Android Studio detects the Gradle project and starts syncing. On the first open it downloads
   ~1 GB of SDK components — let it finish. You'll see a progress bar at the bottom.

If it prompts **"Gradle JDK not found"**, go to
**Android Studio → Settings → Build, Execution, Deployment → Build Tools → Gradle**
and point it at your existing Java 21 installation.

## Step 3 — Build the APK

Once the sync bar disappears with no errors:

**Build → Build Bundle(s) / APK(s) → Build APK(s)**

After ~30 seconds a **"Build successful"** notification appears with a **"locate"** link.
Click it. The APK is at:

```
clusterApp/app/build/outputs/apk/debug/app-debug.apk
```

## Step 4 — Put the headunit in USB Device mode

Follow the steps in `docs/adb.md`:

1. Hold **Brightness + Phone + Volume/Power** on the headunit
2. Tap **"Detail Information and Settings"**
3. Hold the **Phone** key, then hold the **Home** key (three beeps then one)
4. In the USB settings menu, change **Role → Device**
5. Connect a **USB-A to USB-A** cable from the headunit to your Mac

## Step 5 — Install and launch

```bash
# Verify the headunit is visible
adb devices

# Install
adb install clusterApp/app/build/outputs/apk/debug/app-debug.apk

# Launch (shows image on main screen + starts cluster overlay)
adb shell am start -n com.example.clusterapp/.MainActivity
```

No permission grant step is needed. On Android 4.2.2 all manifest permissions
(including `SYSTEM_ALERT_WINDOW`) are granted automatically at install time.

## Step 6 — Stop the app

```bash
adb shell am force-stop com.example.clusterapp
```

This kills the activity and the service together, removing the overlay from the cluster
and restoring the normal cluster display.
