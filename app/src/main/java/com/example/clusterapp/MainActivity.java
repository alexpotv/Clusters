package com.example.clusterapp;

import android.app.Activity;
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
import com.example.clusterapp.plugin.PluginInfo;
import com.example.clusterapp.plugin.PluginRegistry;

import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int MODE_COUNT = 15;

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
        "Coq",
        "Dashboard",
        "Beach",
        "Dashboard 2",
    };
    private static final int TAB_BUILTIN     = 0;
    private static final int TAB_PLUGINS     = 1;
    private static final int TAB_MARKETPLACE = 2;
    private static final int TAB_SETTINGS    = 3;

    private int mSelectedMode = 1;
    private int mCurrentTab   = TAB_BUILTIN;

    private final Button[] mTabButtons = new Button[4];

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

        String[] tabLabels = {"Built-in", "Plugins", "Marketplace", "Settings"};
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-render the current tab so plugin activation from the Marketplace is reflected.
        renderTab(mCurrentTab);
        applyTabStyles();
        refreshStatus();
    }

    // -------------------------------------------------------------------------
    // Tab management
    // -------------------------------------------------------------------------

    private void selectTab(int tab) {
        if (tab == TAB_MARKETPLACE) {
            // Marketplace is a separate Activity; don't switch the content pane.
            startActivity(new Intent(this, MarketplaceActivity.class));
            return;
        }
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
        // Always show Marketplace tab in its own colour since it navigates away.
        mTabButtons[TAB_MARKETPLACE].setTextColor(0xFF1565C0);
    }

    private void renderTab(int tab) {
        mContentFrame.removeAllViews();
        if (tab == TAB_BUILTIN) {
            mContentFrame.addView(buildBuiltInTab());
        } else if (tab == TAB_PLUGINS) {
            mContentFrame.addView(buildPluginsTab());
        } else if (tab == TAB_SETTINGS) {
            mContentFrame.addView(buildSettingsTab());
        }
    }

    // -------------------------------------------------------------------------
    // Tab: Built-in screens
    // -------------------------------------------------------------------------

    private View buildBuiltInTab() {
        boolean pluginActive = ClusterDisplayService.sCurrentPluginId != null;

        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), dp(12), dp(12), dp(12));

        // Active plugin notice
        if (pluginActive) {
            PluginRegistry registry = PluginRegistry.getInstance(this);
            PluginInfo info = registry.getInfo(ClusterDisplayService.sCurrentPluginId);
            if (info != null) {
                TextView notice = new TextView(this);
                notice.setText("● Plugin active: " + info.name
                    + "  (" + info.author + " / " + info.collection + ")");
                notice.setTextColor(0xFF4CAF50);
                notice.setTextSize(11);
                notice.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams noticeLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                noticeLp.setMargins(0, 0, 0, dp(12));
                col.addView(notice, noticeLp);
            }
        }

        // 2-column scrollable grid of screen buttons
        for (int i = 0; i < MODE_COUNT; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            for (int j = i; j < Math.min(i + 2, MODE_COUNT); j++) {
                final int mode = j + 1;
                boolean selected = !pluginActive && (mode == mSelectedMode);

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
    // Tab: Installed plugins
    // -------------------------------------------------------------------------

    private View buildPluginsTab() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(12), dp(12), dp(12));

        PluginRegistry registry = PluginRegistry.getInstance(this);
        Map<String, Map<String, List<PluginInfo>>> hierarchy = registry.listInstalledByHierarchy();

        if (hierarchy.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No plugins installed.\nOpen the Marketplace tab to find some.");
            empty.setTextColor(0xFF555555);
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(32), 0, 0);
            list.addView(empty);
        } else {
            for (Map.Entry<String, Map<String, List<PluginInfo>>> authorEntry : hierarchy.entrySet()) {
                // Author label
                TextView authorLabel = new TextView(this);
                authorLabel.setText(authorEntry.getKey().toUpperCase());
                authorLabel.setTextColor(0xFF666666);
                authorLabel.setTextSize(10);
                authorLabel.setTypeface(null, Typeface.BOLD);
                authorLabel.setPadding(dp(4), dp(12), 0, dp(2));
                list.addView(authorLabel);

                for (Map.Entry<String, List<PluginInfo>> colEntry : authorEntry.getValue().entrySet()) {
                    // Collection label
                    TextView colLabel = new TextView(this);
                    colLabel.setText("  " + colEntry.getKey());
                    colLabel.setTextColor(0xFF444444);
                    colLabel.setTextSize(10);
                    colLabel.setPadding(dp(12), dp(4), 0, dp(2));
                    list.addView(colLabel);

                    for (final PluginInfo info : colEntry.getValue()) {
                        final boolean isActive =
                            info.id.equals(ClusterDisplayService.sCurrentPluginId);

                        LinearLayout card = new LinearLayout(this);
                        card.setOrientation(LinearLayout.HORIZONTAL);
                        card.setBackgroundColor(isActive ? 0xFF1B2E1B : 0xFF1E1E1E);
                        card.setPadding(dp(14), dp(10), dp(14), dp(10));
                        card.setGravity(Gravity.CENTER_VERTICAL);
                        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        cardLp.setMargins(0, dp(2), 0, dp(2));
                        card.setLayoutParams(cardLp);

                        // Text column
                        LinearLayout textCol = new LinearLayout(this);
                        textCol.setOrientation(LinearLayout.VERTICAL);

                        TextView nameView = new TextView(this);
                        nameView.setText((isActive ? "● " : "") + info.name);
                        nameView.setTextColor(isActive ? 0xFF4CAF50 : 0xFFEEEEEE);
                        nameView.setTextSize(14);
                        nameView.setTypeface(null, Typeface.BOLD);
                        textCol.addView(nameView);

                        TextView metaView = new TextView(this);
                        metaView.setText("v" + info.version + (isActive ? "  •  Active" : ""));
                        metaView.setTextColor(isActive ? 0xFF388E3C : 0xFF888888);
                        metaView.setTextSize(11);
                        textCol.addView(metaView);

                        card.addView(textCol, new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                        // Activate button (hidden when already active)
                        if (!isActive) {
                            Button activateBtn = new Button(this);
                            activateBtn.setText("Activate");
                            activateBtn.setTextSize(12);
                            activateBtn.setBackgroundColor(0xFF1565C0);
                            activateBtn.setTextColor(0xFFFFFFFF);
                            activateBtn.setPadding(dp(8), 0, dp(8), 0);
                            LinearLayout.LayoutParams btnLp =
                                new LinearLayout.LayoutParams(dp(90), dp(36));
                            btnLp.gravity = Gravity.CENTER_VERTICAL;
                            card.addView(activateBtn, btnLp);
                            activateBtn.setOnClickListener(new View.OnClickListener() {
                                @Override public void onClick(View v) {
                                    ClusterDisplayService.setPlugin(info.id);
                                    startService(new Intent(MainActivity.this,
                                        ClusterDisplayService.class));
                                    Toast.makeText(MainActivity.this,
                                        "Now showing: " + info.name, Toast.LENGTH_SHORT).show();
                                    renderTab(TAB_PLUGINS);
                                    refreshStatus();
                                }
                            });
                        }

                        list.addView(card);
                    }
                }
            }
        }

        scroll.addView(list);
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
