package com.example.clusterapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.example.clusterapi.PluginContext;

public class MainActivity extends Activity {

    private static final int MODE_COUNT = 14;

    private static final String[] SCREEN_NAMES = {
        "Placeholder",
        "Motion",
        "Drivetrain",
        "Brakes",
        "Steering",
        "Lights",
        "Cabin",
        "Fuel",
        "Audio",
        "ADAS",
        "Maintenance",
        "Dashboard",
        "CAN Data",
        "Climate",
    };
    private static final int TAB_BUILTIN  = 0;
    private static final int TAB_DEBUG    = 1;
    private static final int TAB_SETTINGS = 2;

    private int mSelectedMode = 1;
    private int mCurrentTab   = TAB_BUILTIN;

    private final Button[] mTabButtons = new Button[3];

    private FrameLayout mContentFrame;
    private FrameLayout mSettingsOverlay;
    private TextView    mStatusText;
    private final Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Outer wrapper: main content + settings overlay stacked in a FrameLayout.
        FrameLayout rootWrapper = new FrameLayout(this);
        rootWrapper.setBackgroundColor(0xFF111111);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // ── Title row ─────────────────────────────────────────────────────────
        FrameLayout titleRow = new FrameLayout(this);
        titleRow.setPadding(dp(8), dp(12), dp(8), dp(8));

        TextView title = new TextView(this);
        title.setText("CLUSTER DISPLAY");
        title.setTextColor(0xFF888888);
        title.setTextSize(13);
        title.setGravity(Gravity.CENTER);
        titleRow.addView(title, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL));

        Button settingsBtn = new Button(this);
        settingsBtn.setText("⊞"); // ⊞ window icon
        settingsBtn.setTextSize(16);
        settingsBtn.setAllCaps(false);
        settingsBtn.setBackgroundColor(0x00000000);
        settingsBtn.setTextColor(0xFF555555);
        settingsBtn.setPadding(0, 0, 0, 0);
        settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openScreenSettings(); }
        });
        FrameLayout.LayoutParams settingsBtnLp = new FrameLayout.LayoutParams(
            dp(40), dp(40), Gravity.END | Gravity.CENTER_VERTICAL);
        titleRow.addView(settingsBtn, settingsBtnLp);

        root.addView(titleRow, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── Tab bar ───────────────────────────────────────────────────────────
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackgroundColor(0xFF1A1A1A);

        String[] tabLabels = {"Built-in", "Debug", "Settings"};
        for (int i = 0; i < tabLabels.length; i++) {
            final int tab = i;
            Button btn = new Button(this);
            btn.setText(tabLabels[i]);
            btn.setTextSize(13);
            btn.setPadding(0, 0, 0, 0);
            btn.setAllCaps(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { selectTab(tab); }
            });
            mTabButtons[i] = btn;
            tabBar.addView(btn);
        }

        root.addView(tabBar, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── Content area ──────────────────────────────────────────────────────
        mContentFrame = new FrameLayout(this);
        root.addView(mContentFrame, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // ── Status line ───────────────────────────────────────────────────────
        mStatusText = new TextView(this);
        mStatusText.setTextColor(0xFF444444);
        mStatusText.setTextSize(9);
        mStatusText.setGravity(Gravity.CENTER);
        mStatusText.setPadding(dp(8), dp(4), dp(8), dp(4));
        root.addView(mStatusText, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        rootWrapper.addView(root, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        // ── Settings overlay (hidden until the ⊞ button is tapped) ───────────
        mSettingsOverlay = new FrameLayout(this);
        mSettingsOverlay.setBackgroundColor(0xCC000000);
        mSettingsOverlay.setVisibility(View.GONE);
        mSettingsOverlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dismissSettingsPanel(); }
        });
        rootWrapper.addView(mSettingsOverlay, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));

        setContentView(rootWrapper);

        selectTab(TAB_BUILTIN);
        startService(new Intent(this, ClusterDisplayService.class));

        mHandler.postDelayed(new Runnable() {
            @Override public void run() { refreshStatus(); }
        }, 4000);

        maybePromptSystemInstall();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderTab(mCurrentTab);
        applyTabStyles();
        refreshStatus();
    }

    // -------------------------------------------------------------------------
    // Tab management
    // -------------------------------------------------------------------------

    private void selectTab(int tab) {
        mCurrentTab = tab;
        applyTabStyles();
        renderTab(tab);
    }

    private void applyTabStyles() {
        for (int i = 0; i < mTabButtons.length; i++) {
            boolean active = (i == mCurrentTab);
            mTabButtons[i].setBackgroundColor(active ? 0xFF222222 : 0xFF1A1A1A);
            mTabButtons[i].setTextColor(active ? 0xFFFFFFFF : 0xFF666666);
        }
    }

    private void renderTab(int tab) {
        mContentFrame.removeAllViews();
        if (tab == TAB_BUILTIN) {
            mContentFrame.addView(buildBuiltInTab());
        } else if (tab == TAB_DEBUG) {
            mContentFrame.addView(CanDebugView.create(this));
        } else if (tab == TAB_SETTINGS) {
            mContentFrame.addView(buildSettingsTab());
        }
    }

    // -------------------------------------------------------------------------
    // Tab: Built-in screens
    // -------------------------------------------------------------------------

    private View buildBuiltInTab() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), dp(12), dp(12), dp(12));

        // 2-column scrollable grid of screen buttons
        for (int i = 0; i < MODE_COUNT; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            for (int j = i; j < Math.min(i + 2, MODE_COUNT); j++) {
                final int mode = j + 1;
                boolean selected = (mode == mSelectedMode);

                Button btn = new Button(this);
                btn.setText(SCREEN_NAMES[j]);
                btn.setTextSize(14);
                btn.setAllCaps(false);
                btn.setPadding(dp(4), 0, dp(4), 0);
                btn.setBackgroundColor(selected ? 0xFFFFFFFF : 0xFF2A2A2A);
                btn.setTextColor(selected ? 0xFF000000 : 0xFFCCCCCC);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        mSelectedMode = mode;
                        ClusterDisplayService.setMode(mode);
                        renderTab(TAB_BUILTIN);
                        refreshStatus();
                    }
                });

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(72), 1f);
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                row.addView(btn, lp);
            }

            col.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        scroll.addView(col);
        return scroll;
    }

    // -------------------------------------------------------------------------
    // Tab: Settings
    // -------------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    private View buildSettingsTab() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(20), dp(24), dp(20));

        // ── Section: System (root) ────────────────────────────────────────────
        TextView sysHeader = new TextView(this);
        sysHeader.setText("SYSTEM");
        sysHeader.setTextColor(0xFF666666);
        sysHeader.setTextSize(10);
        sysHeader.setTypeface(null, Typeface.BOLD);
        sysHeader.setPadding(0, 0, 0, dp(8));
        col.addView(sysHeader);

        LinearLayout sysRow = new LinearLayout(this);
        sysRow.setOrientation(LinearLayout.HORIZONTAL);
        sysRow.setGravity(Gravity.CENTER_VERTICAL);
        sysRow.setBackgroundColor(0xFF1E1E1E);
        sysRow.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout sysText = new LinearLayout(this);
        sysText.setOrientation(LinearLayout.VERTICAL);

        boolean systemApp = RootInstaller.isSystemApp(this);
        boolean hasRw = RootInstaller.hasVehicleRw(this);

        TextView sysTitle = new TextView(this);
        sysTitle.setText("System install (root)");
        sysTitle.setTextColor(0xFFEEEEEE);
        sysTitle.setTextSize(14);
        sysText.addView(sysTitle);

        TextView sysDesc = new TextView(this);
        sysDesc.setText(systemApp
                ? ("Installed as system app ✓" + (hasRw ? "   VEHICLE_RW granted ✓" : "   VEHICLE_RW pending"))
                : "Not a system app — full CAN/HVAC data needs a one-time\nroot install to " + RootInstaller.systemAppDir() + " + reboot.");
        sysDesc.setTextColor(systemApp ? 0xFF66BB66 : 0xFF777777);
        sysDesc.setTextSize(10);
        sysText.addView(sysDesc);

        sysRow.addView(sysText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button sysBtn = new Button(this);
        sysBtn.setText(systemApp ? "Reinstall" : "Install");
        sysBtn.setAllCaps(false);
        sysBtn.setTextSize(12);
        sysBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { doSystemInstall(); }
        });
        sysRow.addView(sysBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams sysRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sysRowLp.setMargins(0, 0, 0, dp(20));
        col.addView(sysRow, sysRowLp);

        // ── Section: Camera ───────────────────────────────────────────────────
        TextView cameraHeader = new TextView(this);
        cameraHeader.setText("CAMERA");
        cameraHeader.setTextColor(0xFF666666);
        cameraHeader.setTextSize(10);
        cameraHeader.setTypeface(null, Typeface.BOLD);
        cameraHeader.setPadding(0, 0, 0, dp(8));
        col.addView(cameraHeader);

        // Lane Watch row
        LinearLayout lwRow = new LinearLayout(this);
        lwRow.setOrientation(LinearLayout.HORIZONTAL);
        lwRow.setGravity(Gravity.CENTER_VERTICAL);
        lwRow.setBackgroundColor(0xFF1E1E1E);
        lwRow.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout lwText = new LinearLayout(this);
        lwText.setOrientation(LinearLayout.VERTICAL);

        TextView lwTitle = new TextView(this);
        lwTitle.setText("Lane Watch in gauge cluster");
        lwTitle.setTextColor(0xFFEEEEEE);
        lwTitle.setTextSize(14);
        lwText.addView(lwTitle);

        TextView lwDesc = new TextView(this);
        lwDesc.setText("When the right turn signal is active, show the\nside camera on the cluster (overrides current screen).");
        lwDesc.setTextColor(0xFF777777);
        lwDesc.setTextSize(10);
        lwText.addView(lwDesc);

        lwRow.addView(lwText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final AppSettings settings = AppSettings.getInstance(this);
        Switch lwSwitch = new Switch(this);
        lwSwitch.setChecked(settings.isLaneWatchEnabled());
        lwSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton btn, boolean checked) {
                settings.setLaneWatchEnabled(checked);
            }
        });
        lwRow.addView(lwSwitch, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, dp(1));
        col.addView(lwRow, rowLp);

        // ── Camera ID override ────────────────────────────────────────────────
        LinearLayout camIdRow = new LinearLayout(this);
        camIdRow.setOrientation(LinearLayout.HORIZONTAL);
        camIdRow.setGravity(Gravity.CENTER_VERTICAL);
        camIdRow.setBackgroundColor(0xFF1E1E1E);
        camIdRow.setPadding(dp(16), dp(10), dp(16), dp(10));

        TextView camIdLabel = new TextView(this);
        camIdLabel.setText("Camera ID override");
        camIdLabel.setTextColor(0xFFAAAAAA);
        camIdLabel.setTextSize(12);
        camIdRow.addView(camIdLabel, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final EditText camIdEdit = new EditText(this);
        String savedId = settings.getLaneWatchCameraId();
        camIdEdit.setHint("auto");
        camIdEdit.setText(savedId != null ? savedId : "");
        camIdEdit.setTextColor(0xFFEEEEEE);
        camIdEdit.setHintTextColor(0xFF555555);
        camIdEdit.setTextSize(12);
        camIdEdit.setBackgroundColor(0xFF2A2A2A);
        camIdEdit.setPadding(dp(8), dp(4), dp(8), dp(4));
        camIdEdit.setSingleLine(true);
        camIdEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String v = s.toString().trim();
                settings.setLaneWatchCameraId(v.isEmpty() ? null : v);
            }
        });
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(dp(60), dp(36));
        camIdRow.addView(camIdEdit, editLp);

        LinearLayout.LayoutParams camIdRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        camIdRowLp.setMargins(0, 0, 0, dp(16));
        col.addView(camIdRow, camIdRowLp);

        TextView camIdHint = new TextView(this);
        camIdHint.setText("Leave blank for auto-detection (tries external-facing cameras first).\n"
                + "If the camera feed is black, try IDs: 2, 3, 4…");
        camIdHint.setTextColor(0xFF555555);
        camIdHint.setTextSize(10);
        col.addView(camIdHint);

        scroll.addView(col);
        return scroll;
    }

    // -------------------------------------------------------------------------
    // Screen settings panel
    // -------------------------------------------------------------------------

    private void openScreenSettings() {
        com.example.clusterapi.ClusterPlugin screen = ClusterDisplayService.getActiveScreen();
        if (screen == null) {
            Toast.makeText(this, "No active screen", Toast.LENGTH_SHORT).show();
            return;
        }

        String apkPath   = ClusterDisplayService.sActiveApkPath;
        String namespace = ClusterDisplayService.sActiveNamespace;
        if (apkPath == null) apkPath = getApplicationInfo().sourceDir;
        if (namespace == null) namespace = "unknown";

        PluginContext pc = new PluginContext(this, apkPath, namespace, 0, 0);
        View settingsView;
        try {
            settingsView = screen.onCreateSettingsView(pc);
        } catch (Throwable t) {
            Toast.makeText(this, "Settings error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        if (settingsView == null) {
            Toast.makeText(this, "This screen has no settings", Toast.LENGTH_SHORT).show();
            return;
        }

        showSettingsPanel(settingsView);
    }

    private void showSettingsPanel(View settingsView) {
        mSettingsOverlay.removeAllViews();

        // Card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFF1A1A1A);
        card.setClickable(true); // absorb touches so backdrop click doesn't fire through

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(0xFF222222);
        header.setPadding(dp(16), dp(10), dp(8), dp(10));

        TextView headerTitle = new TextView(this);
        headerTitle.setText("Screen Settings");
        headerTitle.setTextColor(0xFFCCCCCC);
        headerTitle.setTextSize(14);
        header.addView(headerTitle, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button closeBtn = new Button(this);
        closeBtn.setText("✕");
        closeBtn.setTextSize(14);
        closeBtn.setAllCaps(false);
        closeBtn.setBackgroundColor(0x00000000);
        closeBtn.setTextColor(0xFF888888);
        closeBtn.setPadding(dp(8), 0, dp(8), 0);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dismissSettingsPanel(); }
        });
        header.addView(closeBtn, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)));

        card.addView(header, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Scrollable settings content
        ScrollView scroll = new ScrollView(this);
        scroll.addView(settingsView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scrollLp.setMargins(0, 0, 0, 0);
        card.addView(scroll, scrollLp);

        int maxH = (int) (getResources().getDisplayMetrics().heightPixels * 0.75f);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
            dp(320), maxH, Gravity.CENTER);
        mSettingsOverlay.addView(card, cardLp);
        mSettingsOverlay.setVisibility(View.VISIBLE);
    }

    private void dismissSettingsPanel() {
        mSettingsOverlay.setVisibility(View.GONE);
        mSettingsOverlay.removeAllViews();
    }

    // -------------------------------------------------------------------------
    // Status
    // -------------------------------------------------------------------------

    private void refreshStatus() {
        mStatusText.setText(
            "overlay: " + ClusterDisplayService.sStatus
            + "   mux: " + ClusterDisplayService.sMuxStatus);
    }

    // -------------------------------------------------------------------------
    // System install (root) — grants VEHICLE_RW so CpuComService / HVAC bind
    // -------------------------------------------------------------------------

    private static final String PREFS = "cluster_prefs";
    private static final String KEY_SKIP_INSTALL_PROMPT = "skip_system_install_prompt";

    /** On first run, offer the one-time root self-install unless already system-installed or dismissed. */
    private void maybePromptSystemInstall() {
        if (RootInstaller.isSystemApp(this)) return; // already provisioned
        boolean skip = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_SKIP_INSTALL_PROMPT, false);
        if (skip) return;

        new AlertDialog.Builder(this)
            .setTitle("Enable full vehicle data")
            .setMessage("To read all CAN and HVAC data, this app must be installed as a system app "
                    + "(this grants the VEHICLE_RW permission). This requires root and one reboot.\n\n"
                    + "Install now? You'll be asked to grant root access.")
            .setPositiveButton("Install", new android.content.DialogInterface.OnClickListener() {
                @Override public void onClick(android.content.DialogInterface d, int w) { doSystemInstall(); }
            })
            .setNegativeButton("Not now", null)
            .setNeutralButton("Don't ask again", new android.content.DialogInterface.OnClickListener() {
                @Override public void onClick(android.content.DialogInterface d, int w) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putBoolean(KEY_SKIP_INSTALL_PROMPT, true).apply();
                }
            })
            .show();
    }

    private void doSystemInstall() {
        final ProgressDialog pd = ProgressDialog.show(this, null,
                "Installing as system app…\nGrant root if prompted.", true, false);
        new Thread(new Runnable() {
            @Override public void run() {
                final RootInstaller.Result r = RootInstaller.install(MainActivity.this);
                mHandler.post(new Runnable() {
                    @Override public void run() {
                        try { pd.dismiss(); } catch (Exception ignored) {}
                        if (r.ok) showRebootDialog(r.targetPath);
                        else showInstallFailed(r.log);
                    }
                });
            }
        }).start();
    }

    private void showRebootDialog(String targetPath) {
        new AlertDialog.Builder(this)
            .setTitle("Installed")
            .setMessage("Copied to " + targetPath + ".\n\nA reboot is required for the system to grant "
                    + "VEHICLE_RW. Reboot now?")
            .setPositiveButton("Reboot now", new android.content.DialogInterface.OnClickListener() {
                @Override public void onClick(android.content.DialogInterface d, int w) {
                    new Thread(new Runnable() {
                        @Override public void run() { RootInstaller.reboot(); }
                    }).start();
                }
            })
            .setNegativeButton("Later", null)
            .show();
    }

    private void showInstallFailed(String log) {
        String msg = log == null ? "Unknown error." : log.trim();
        if (msg.length() > 900) msg = msg.substring(msg.length() - 900);
        new AlertDialog.Builder(this)
            .setTitle("Install failed")
            .setMessage("Could not install as a system app. Is the device rooted and root granted?\n\n"
                    + msg)
            .setPositiveButton("OK", null)
            .show();
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
        stopService(new Intent(this, ClusterDisplayService.class));
    }
}
