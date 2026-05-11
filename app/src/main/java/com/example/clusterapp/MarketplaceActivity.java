package com.example.clusterapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.clusterapp.plugin.PluginInfo;
import com.example.clusterapp.plugin.PluginRegistry;
import com.example.clusterapp.plugin.PluginRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Three-level hierarchical marketplace: Authors → Collections → Plugins.
 *
 * Each level uses a two-column layout: selections on the left, selected item's
 * README rendered as Markdown in a WebView on the right. Navigation is a manual
 * back-stack of integer screen IDs, keeping the code compatible with API 17.
 */
public class MarketplaceActivity extends Activity {

    private static final int SCREEN_AUTHORS    = 0;
    private static final int SCREEN_COLLECTION = 1;
    private static final int SCREEN_PLUGINS    = 2;
    private static final int SCREEN_INSTALLED  = 3;

    private PluginRegistry   mRegistry;
    private PluginRepository mRepository;

    private Map<String, Map<String, List<PluginInfo>>> mRemoteIndex;
    private List<PluginInfo> mRemoteFlat = new ArrayList<>();

    private final List<Integer> mBackStack = new ArrayList<>();
    private String     mSelectedAuthor;
    private String     mSelectedCollection;
    private PluginInfo mSelectedPlugin;

    private FrameLayout mContentFrame;
    private TextView    mBreadcrumb;
    private Button      mBackBtn;
    private TextView    mStatusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        mRegistry   = PluginRegistry.getInstance(this);
        mRepository = new PluginRepository(this);
        setContentView(buildShell());
        navigateTo(SCREEN_AUTHORS);
        fetchRemoteIndex();
    }

    @Override
    public void onBackPressed() {
        if (mBackStack.size() > 1) {
            mBackStack.remove(mBackStack.size() - 1);
            renderCurrentScreen();
        } else {
            finish();
        }
    }

    // -------------------------------------------------------------------------
    // Shell
    // -------------------------------------------------------------------------

    private View buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF111111);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setBackgroundColor(0xFF1A1A1A);
        topBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        mBackBtn = makeTextButton("← Back", 0xFFCCCCCC);
        mBackBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onBackPressed(); }
        });
        topBar.addView(mBackBtn, new LinearLayout.LayoutParams(dp(80), dp(40)));

        mBreadcrumb = new TextView(this);
        mBreadcrumb.setTextColor(0xFF888888);
        mBreadcrumb.setTextSize(12);
        mBreadcrumb.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bcLp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bcLp.gravity = Gravity.CENTER_VERTICAL;
        topBar.addView(mBreadcrumb, bcLp);

        LinearLayout rightBtns = new LinearLayout(this);
        rightBtns.setOrientation(LinearLayout.HORIZONTAL);
        rightBtns.setGravity(Gravity.CENTER_VERTICAL);

        Button homeBtn = makeTextButton("⌂ Home", 0xFF888888);
        homeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        rightBtns.addView(homeBtn, new LinearLayout.LayoutParams(dp(68), dp(40)));

        Button installedBtn = makeTextButton("Installed", 0xFF4CAF50);
        installedBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { navigateTo(SCREEN_INSTALLED); }
        });
        LinearLayout.LayoutParams instLp = new LinearLayout.LayoutParams(dp(72), dp(40));
        instLp.setMargins(dp(4), 0, 0, 0);
        rightBtns.addView(installedBtn, instLp);

        topBar.addView(rightBtns, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(topBar, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mStatusText = new TextView(this);
        mStatusText.setTextColor(0xFF666666);
        mStatusText.setTextSize(11);
        mStatusText.setPadding(dp(12), dp(4), dp(12), dp(4));
        root.addView(mStatusText, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        mContentFrame = new FrameLayout(this);
        root.addView(mContentFrame, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        return root;
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    private void navigateTo(int screen) {
        mBackStack.add(screen);
        renderCurrentScreen();
    }

    private void renderCurrentScreen() {
        int screen = mBackStack.get(mBackStack.size() - 1);
        mBackBtn.setVisibility(mBackStack.size() > 1 ? View.VISIBLE : View.INVISIBLE);
        mContentFrame.removeAllViews();
        switch (screen) {
            case SCREEN_AUTHORS:
                mBreadcrumb.setText("SCREEN MARKETPLACE");
                mContentFrame.addView(buildAuthorsScreen());
                break;
            case SCREEN_COLLECTION:
                mBreadcrumb.setText(mSelectedAuthor);
                mContentFrame.addView(buildCollectionsScreen());
                break;
            case SCREEN_PLUGINS:
                mBreadcrumb.setText(mSelectedAuthor + " / " + mSelectedCollection);
                mContentFrame.addView(buildPluginsScreen());
                break;
            case SCREEN_INSTALLED:
                mBreadcrumb.setText("INSTALLED SCREENS");
                mContentFrame.addView(buildInstalledScreen());
                break;
        }
    }

    // -------------------------------------------------------------------------
    // Screen: Authors — left: author list, right: selected author README
    // -------------------------------------------------------------------------

    private View buildAuthorsScreen() {
        if (mRemoteIndex == null) return fullWidthMessage("Loading available screens…");
        if (mRemoteIndex.isEmpty()) return fullWidthMessage("No screens available.");

        final List<Map.Entry<String, Map<String, List<PluginInfo>>>> entries =
            new ArrayList<>(mRemoteIndex.entrySet());

        int initialIdx = 0;
        if (mSelectedAuthor != null) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).getKey().equals(mSelectedAuthor)) { initialIdx = i; break; }
            }
        }
        final int[] sel = {initialIdx};
        final View[] cards = new View[entries.size()];
        final WebView readmeView = makeWebView();

        LinearLayout leftList = new LinearLayout(this);
        leftList.setOrientation(LinearLayout.VERTICAL);
        leftList.setPadding(dp(4), dp(4), dp(4), dp(4));

        for (int i = 0; i < entries.size(); i++) {
            final int idx = i;
            final Map.Entry<String, Map<String, List<PluginInfo>>> entry = entries.get(i);
            final String author = entry.getKey();

            String aUrl = null;
            outer:
            for (List<PluginInfo> col : entry.getValue().values()) {
                for (PluginInfo p : col) {
                    if (p.authorReadmeUrl != null) { aUrl = p.authorReadmeUrl; break outer; }
                }
            }
            final String readmeUrl = aUrl;

            int pluginCount = 0;
            for (List<PluginInfo> col : entry.getValue().values()) pluginCount += col.size();
            final int count = pluginCount;
            final int colCount = entry.getValue().size();

            LinearLayout card = buildSelectorCard(idx == initialIdx);

            LinearLayout nameRow = new LinearLayout(this);
            nameRow.setOrientation(LinearLayout.HORIZONTAL);
            nameRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView nameView = new TextView(this);
            nameView.setText(author);
            nameView.setTextColor(0xFFFFFFFF);
            nameView.setTextSize(14);
            nameView.setTypeface(null, Typeface.BOLD);
            nameRow.addView(nameView, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button browseBtn = makeButton("Browse →", 0xFF1565C0, 0xFFFFFFFF);
            LinearLayout.LayoutParams browseLp = new LinearLayout.LayoutParams(dp(80), dp(30));
            browseLp.gravity = Gravity.CENTER_VERTICAL;
            browseBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    mSelectedAuthor = author;
                    navigateTo(SCREEN_COLLECTION);
                }
            });
            nameRow.addView(browseBtn, browseLp);
            card.addView(nameRow);

            TextView countView = new TextView(this);
            countView.setText(count + " screen" + (count == 1 ? "" : "s")
                + "  •  " + colCount + " collection" + (colCount == 1 ? "" : "s"));
            countView.setTextColor(0xFF888888);
            countView.setTextSize(11);
            countView.setPadding(0, dp(2), 0, 0);
            card.addView(countView);

            cards[i] = card;
            card.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cards[sel[0]].setBackgroundColor(0xFF1E1E1E);
                    sel[0] = idx;
                    cards[idx].setBackgroundColor(0xFF1A2A3A);
                    mSelectedAuthor = author;
                    loadReadmeInto(readmeView, readmeUrl);
                }
            });

            leftList.addView(card);
            leftList.addView(divider());
        }

        // Pre-load initial author README
        String initUrl = null;
        outer:
        for (List<PluginInfo> col : entries.get(initialIdx).getValue().values()) {
            for (PluginInfo p : col) {
                if (p.authorReadmeUrl != null) { initUrl = p.authorReadmeUrl; break outer; }
            }
        }
        loadReadmeInto(readmeView, initUrl);

        ScrollView leftScroll = new ScrollView(this);
        leftScroll.addView(leftList);
        return makeTwoColumn(leftScroll, readmeView);
    }

    // -------------------------------------------------------------------------
    // Screen: Collections — left: collection list, right: selected collection README
    // -------------------------------------------------------------------------

    private View buildCollectionsScreen() {
        Map<String, List<PluginInfo>> collections =
            mRemoteIndex != null ? mRemoteIndex.get(mSelectedAuthor) : null;
        if (collections == null || collections.isEmpty()) {
            return fullWidthMessage("No collections found.");
        }

        final List<Map.Entry<String, List<PluginInfo>>> entries =
            new ArrayList<>(collections.entrySet());

        // Author README as fallback when a collection has none
        String aUrl = null;
        outer:
        for (List<PluginInfo> col : collections.values()) {
            for (PluginInfo p : col) {
                if (p.authorReadmeUrl != null) { aUrl = p.authorReadmeUrl; break outer; }
            }
        }
        final String authorReadmeUrl = aUrl;

        int initialIdx = 0;
        if (mSelectedCollection != null) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).getKey().equals(mSelectedCollection)) { initialIdx = i; break; }
            }
        }
        final int[] sel = {initialIdx};
        final View[] cards = new View[entries.size()];
        final WebView readmeView = makeWebView();

        LinearLayout leftList = new LinearLayout(this);
        leftList.setOrientation(LinearLayout.VERTICAL);
        leftList.setPadding(dp(4), dp(4), dp(4), dp(4));

        for (int i = 0; i < entries.size(); i++) {
            final int idx = i;
            final Map.Entry<String, List<PluginInfo>> entry = entries.get(i);
            final String collection = entry.getKey();
            final List<PluginInfo> plugins = entry.getValue();

            String cUrl = null;
            for (PluginInfo p : plugins) {
                if (p.collectionReadmeUrl != null) { cUrl = p.collectionReadmeUrl; break; }
            }
            final String colReadmeUrl = cUrl;

            LinearLayout card = buildSelectorCard(idx == initialIdx);

            LinearLayout nameRow = new LinearLayout(this);
            nameRow.setOrientation(LinearLayout.HORIZONTAL);
            nameRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView nameView = new TextView(this);
            nameView.setText(collection);
            nameView.setTextColor(0xFFFFFFFF);
            nameView.setTextSize(14);
            nameView.setTypeface(null, Typeface.BOLD);
            nameRow.addView(nameView, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button browseBtn = makeButton("Browse →", 0xFF1565C0, 0xFFFFFFFF);
            LinearLayout.LayoutParams browseLp = new LinearLayout.LayoutParams(dp(80), dp(30));
            browseLp.gravity = Gravity.CENTER_VERTICAL;
            browseBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    mSelectedCollection = collection;
                    navigateTo(SCREEN_PLUGINS);
                }
            });
            nameRow.addView(browseBtn, browseLp);
            card.addView(nameRow);

            TextView countView = new TextView(this);
            countView.setText(plugins.size() + " screen" + (plugins.size() == 1 ? "" : "s"));
            countView.setTextColor(0xFF888888);
            countView.setTextSize(11);
            countView.setPadding(0, dp(2), 0, 0);
            card.addView(countView);

            cards[i] = card;
            card.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cards[sel[0]].setBackgroundColor(0xFF1E1E1E);
                    sel[0] = idx;
                    cards[idx].setBackgroundColor(0xFF1A2A3A);
                    mSelectedCollection = collection;
                    String url = colReadmeUrl != null ? colReadmeUrl : authorReadmeUrl;
                    loadReadmeInto(readmeView, url);
                }
            });

            leftList.addView(card);
            leftList.addView(divider());
        }

        // Pre-load initial README
        String initUrl = null;
        for (PluginInfo p : entries.get(initialIdx).getValue()) {
            if (p.collectionReadmeUrl != null) { initUrl = p.collectionReadmeUrl; break; }
        }
        if (initUrl == null) initUrl = authorReadmeUrl;
        loadReadmeInto(readmeView, initUrl);

        ScrollView leftScroll = new ScrollView(this);
        leftScroll.addView(leftList);
        return makeTwoColumn(leftScroll, readmeView);
    }

    // -------------------------------------------------------------------------
    // Screen: Plugins — left: plugin list, right: selected plugin README + action bar
    // -------------------------------------------------------------------------

    private View buildPluginsScreen() {
        List<PluginInfo> plugins = null;
        if (mRemoteIndex != null && mRemoteIndex.containsKey(mSelectedAuthor)) {
            Map<String, List<PluginInfo>> cols = mRemoteIndex.get(mSelectedAuthor);
            if (cols != null) plugins = cols.get(mSelectedCollection);
        }
        if (plugins == null || plugins.isEmpty()) {
            return fullWidthMessage("No screens in this collection.");
        }

        final List<PluginInfo> list = plugins;

        int initialIdx = 0;
        if (mSelectedPlugin != null) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id.equals(mSelectedPlugin.id)) { initialIdx = i; break; }
            }
        }
        final int[] sel = {initialIdx};
        final View[] cards = new View[list.size()];

        final WebView readmeView = makeWebView();
        final LinearLayout actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setBackgroundColor(0xFF181818);
        actionBar.setPadding(dp(10), dp(8), dp(10), dp(8));
        actionBar.setGravity(Gravity.CENTER_VERTICAL);
        actionBar.setMinimumHeight(dp(52));

        LinearLayout leftList = new LinearLayout(this);
        leftList.setOrientation(LinearLayout.VERTICAL);
        leftList.setPadding(dp(4), dp(4), dp(4), dp(4));

        for (int i = 0; i < list.size(); i++) {
            final int idx = i;
            final PluginInfo info = list.get(i);
            boolean isActive = info.id.equals(ClusterDisplayService.sCurrentPluginId);
            boolean installed = mRegistry.isInstalled(info.id);

            LinearLayout card = buildSelectorCard(idx == initialIdx);

            TextView nameView = new TextView(this);
            nameView.setText((isActive ? "● " : "") + info.name);
            nameView.setTextColor(isActive ? 0xFF4CAF50 : 0xFFFFFFFF);
            nameView.setTextSize(14);
            nameView.setTypeface(null, Typeface.BOLD);
            card.addView(nameView);

            TextView metaView = new TextView(this);
            String badge = isActive ? "Active  •  " : (installed ? "Installed  •  " : "");
            metaView.setText(badge + "v" + info.version
                + (updateAvailable(info) ? "  •  Update available" : ""));
            metaView.setTextColor(isActive ? 0xFF4CAF50 : (installed ? 0xFF388E3C : 0xFF888888));
            metaView.setTextSize(11);
            metaView.setPadding(0, dp(2), 0, 0);
            card.addView(metaView);

            cards[i] = card;
            card.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    cards[sel[0]].setBackgroundColor(0xFF1E1E1E);
                    sel[0] = idx;
                    cards[idx].setBackgroundColor(0xFF1A2A3A);
                    mSelectedPlugin = info;
                    loadReadmeInto(readmeView, info.screenReadmeUrl);
                    buildActionBarContent(info, actionBar);
                }
            });

            leftList.addView(card);
            leftList.addView(divider());
        }

        // Set initial right-pane state
        PluginInfo init = list.get(initialIdx);
        mSelectedPlugin = init;
        loadReadmeInto(readmeView, init.screenReadmeUrl);
        buildActionBarContent(init, actionBar);

        ScrollView leftScroll = new ScrollView(this);
        leftScroll.addView(leftList);

        LinearLayout rightPane = new LinearLayout(this);
        rightPane.setOrientation(LinearLayout.VERTICAL);
        rightPane.addView(readmeView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        rightPane.addView(actionBar, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        return makeTwoColumn(leftScroll, rightPane);
    }

    private void buildActionBarContent(final PluginInfo info, LinearLayout bar) {
        bar.removeAllViews();
        final boolean isActive    = info.id.equals(ClusterDisplayService.sCurrentPluginId);
        final boolean installed   = mRegistry.isInstalled(info.id);
        final boolean updateAvail = updateAvailable(info);

        if (isActive) {
            TextView lbl = new TextView(this);
            lbl.setText("● Active on cluster");
            lbl.setTextColor(0xFF4CAF50);
            lbl.setTextSize(12);
            bar.addView(lbl, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        } else if (installed) {
            if (updateAvail) {
                final Button updateBtn = makeButton("Update", 0xFFE65100, 0xFFFFFFFF);
                bar.addView(updateBtn, new LinearLayout.LayoutParams(dp(88), dp(36)));
                updateBtn.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { startInstall(info, updateBtn); }
                });
                bar.addView(new View(this), new LinearLayout.LayoutParams(dp(8), 1));
            }
            Button activateBtn = makeButton("Activate", 0xFF1B5E20, 0xFFFFFFFF);
            bar.addView(activateBtn, new LinearLayout.LayoutParams(dp(88), dp(36)));
            activateBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { activatePlugin(info); }
            });

            bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

            Button removeBtn = makeButton("Remove", 0xFF7F0000, 0xFFCCCCCC);
            bar.addView(removeBtn, new LinearLayout.LayoutParams(dp(80), dp(36)));
            removeBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { removePlugin(info); }
            });
        } else {
            bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
            final Button installBtn = makeButton("Install", 0xFF1565C0, 0xFFFFFFFF);
            bar.addView(installBtn, new LinearLayout.LayoutParams(dp(88), dp(36)));
            installBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { startInstall(info, installBtn); }
            });
        }
    }

    // -------------------------------------------------------------------------
    // Screen: Installed plugins
    // -------------------------------------------------------------------------

    private View buildInstalledScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(12), dp(12), dp(12));

        Map<String, Map<String, List<PluginInfo>>> hierarchy = mRegistry.listInstalledByHierarchy();

        if (hierarchy.isEmpty()) {
            list.addView(emptyLabel("No screens installed yet.\nBrowse the marketplace to find one."));
        } else {
            for (Map.Entry<String, Map<String, List<PluginInfo>>> authorEntry : hierarchy.entrySet()) {
                TextView authorLabel = new TextView(this);
                authorLabel.setText(authorEntry.getKey().toUpperCase());
                authorLabel.setTextColor(0xFF888888);
                authorLabel.setTextSize(11);
                authorLabel.setPadding(0, dp(16), 0, dp(4));
                list.addView(authorLabel);

                for (Map.Entry<String, List<PluginInfo>> colEntry : authorEntry.getValue().entrySet()) {
                    TextView colLabel = new TextView(this);
                    colLabel.setText("  " + colEntry.getKey());
                    colLabel.setTextColor(0xFF666666);
                    colLabel.setTextSize(11);
                    colLabel.setPadding(dp(8), dp(6), 0, dp(2));
                    list.addView(colLabel);

                    for (final PluginInfo info : colEntry.getValue()) {
                        final boolean isActive = info.id.equals(ClusterDisplayService.sCurrentPluginId);
                        LinearLayout card = makeCard();

                        LinearLayout textCol = new LinearLayout(this);
                        textCol.setOrientation(LinearLayout.VERTICAL);

                        TextView nameView = new TextView(this);
                        nameView.setText(info.name);
                        nameView.setTextColor(0xFFFFFFFF);
                        nameView.setTextSize(14);
                        nameView.setTypeface(null, Typeface.BOLD);
                        textCol.addView(nameView);

                        TextView metaView = new TextView(this);
                        String badge = isActive ? "● Active  •  " : "";
                        metaView.setText(badge + "v" + info.version
                            + (updateAvailable(info) ? "  •  Update available" : ""));
                        metaView.setTextColor(isActive ? 0xFF4CAF50 : 0xFF888888);
                        metaView.setTextSize(11);
                        textCol.addView(metaView);

                        card.addView(textCol, new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                        if (!isActive) {
                            Button activateBtn = makeButton("Activate", 0xFF1B5E20, 0xFFFFFFFF);
                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(84), dp(32));
                            lp.gravity = Gravity.CENTER_VERTICAL;
                            lp.setMargins(0, 0, dp(6), 0);
                            card.addView(activateBtn, lp);
                            activateBtn.setOnClickListener(new View.OnClickListener() {
                                @Override public void onClick(View v) { activatePlugin(info); }
                            });
                        }

                        Button removeBtn = makeButton("Remove", 0xFF7F0000, 0xFFCCCCCC);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(76), dp(32));
                        lp.gravity = Gravity.CENTER_VERTICAL;
                        card.addView(removeBtn, lp);
                        removeBtn.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) { removePlugin(info); }
                        });

                        list.addView(card);
                        list.addView(divider());
                    }
                }
            }
        }

        scroll.addView(list);
        return scroll;
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private void startInstall(final PluginInfo info, final Button triggerBtn) {
        triggerBtn.setEnabled(false);
        triggerBtn.setText("…");
        mStatusText.setText("Downloading " + info.name + "…");
        mRepository.download(info, new PluginRepository.Callback<File>() {
            @Override public void onSuccess(File apkFile) {
                mStatusText.setText("");
                try {
                    mRegistry.install(apkFile, info);
                    Toast.makeText(MarketplaceActivity.this,
                        info.name + " installed.", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(MarketplaceActivity.this,
                        "Install failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
                renderCurrentScreen();
            }
            @Override public void onFailure(String error) {
                mStatusText.setText("");
                Toast.makeText(MarketplaceActivity.this,
                    "Download failed: " + error, Toast.LENGTH_LONG).show();
                renderCurrentScreen();
            }
        });
    }

    private void activatePlugin(PluginInfo info) {
        ClusterDisplayService.setPlugin(info.id);
        startService(new Intent(this, ClusterDisplayService.class));
        Toast.makeText(this, "Now showing: " + info.name, Toast.LENGTH_SHORT).show();
        renderCurrentScreen();
    }

    private void removePlugin(PluginInfo info) {
        if (info.id.equals(ClusterDisplayService.sCurrentPluginId)) {
            ClusterDisplayService.setMode(mRegistry.getActiveMode());
        }
        mRegistry.uninstall(info.id);
        Toast.makeText(this, info.name + " removed.", Toast.LENGTH_SHORT).show();
        renderCurrentScreen();
    }

    // -------------------------------------------------------------------------
    // Remote index
    // -------------------------------------------------------------------------

    private void fetchRemoteIndex() {
        mStatusText.setText("Fetching available screens…");
        mRepository.fetchIndex(new PluginRepository.Callback<List<PluginInfo>>() {
            @Override public void onSuccess(List<PluginInfo> plugins) {
                mStatusText.setText("");
                mRemoteFlat  = plugins;
                mRemoteIndex = PluginRepository.groupByHierarchy(plugins);
                int cur = mBackStack.get(mBackStack.size() - 1);
                if (cur == SCREEN_AUTHORS) renderCurrentScreen();
            }
            @Override public void onFailure(String error) {
                mStatusText.setText("Could not load index: " + error);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Two-column layout helpers
    // -------------------------------------------------------------------------

    private View makeTwoColumn(View leftPane, View rightPane) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT));
        row.addView(leftPane, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, 2f));
        View sep = new View(this);
        sep.setBackgroundColor(0xFF252525);
        row.addView(sep, new LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT));
        row.addView(rightPane, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, 3f));
        return row;
    }

    private WebView makeWebView() {
        WebView wv = new WebView(this);
        wv.setBackgroundColor(0xFF1A1A1A);
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(false);
        ws.setSupportZoom(false);
        ws.setBuiltInZoomControls(false);
        wv.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        loadHtml(wv, MarkdownRenderer.toHtml("*Select an item on the left.*"));
        return wv;
    }

    private void loadReadmeInto(final WebView wv, final String url) {
        if (url == null || url.isEmpty()) {
            loadHtml(wv, MarkdownRenderer.toHtml("*No description available.*"));
            return;
        }
        loadHtml(wv, MarkdownRenderer.toHtml("*Loading…*"));
        mRepository.fetchReadme(url, new PluginRepository.Callback<String>() {
            @Override public void onSuccess(String text) {
                loadHtml(wv, MarkdownRenderer.toHtml(text));
            }
            @Override public void onFailure(String e) {
                loadHtml(wv, MarkdownRenderer.toHtml("*README unavailable.*"));
            }
        });
    }

    private static void loadHtml(WebView wv, String html) {
        wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    /** A card used as a left-pane selection item. */
    private LinearLayout buildSelectorCard(boolean selected) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(selected ? 0xFF1A2A3A : 0xFF1E1E1E);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(1));
        card.setLayoutParams(lp);
        return card;
    }

    // -------------------------------------------------------------------------
    // Misc helpers
    // -------------------------------------------------------------------------

    private boolean updateAvailable(PluginInfo info) {
        if (!mRegistry.isInstalled(info.id)) return false;
        PluginInfo installed = mRegistry.getInfo(info.id);
        return installed != null && !installed.version.equals(info.version);
    }

    private View fullWidthMessage(String msg) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(Gravity.CENTER);
        wrapper.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        wrapper.addView(emptyLabel(msg));
        return wrapper;
    }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundColor(0xFF1E1E1E);
        card.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(1));
        card.setLayoutParams(lp);
        return card;
    }

    private TextView emptyLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF555555);
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(16), dp(32), dp(16), 0);
        return tv;
    }

    private View divider() {
        View div = new View(this);
        div.setBackgroundColor(0xFF252525);
        div.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return div;
    }

    private Button makeButton(String label, int bgColor, int textColor) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(11);
        btn.setPadding(dp(6), 0, dp(6), 0);
        btn.setAllCaps(false);
        btn.setBackgroundColor(bgColor);
        btn.setTextColor(textColor);
        return btn;
    }

    private Button makeTextButton(String label, int textColor) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(12);
        btn.setPadding(0, 0, 0, 0);
        btn.setAllCaps(false);
        btn.setBackgroundColor(0x00000000);
        btn.setTextColor(textColor);
        return btn;
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
