package com.renzo.idscanner;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(7, 8, 10);
    private static final int SURFACE = Color.rgb(19, 21, 25);
    private static final int FG = Color.rgb(239, 241, 244);
    private static final int MUTED = Color.rgb(139, 145, 154);
    private static final int GOLD = Color.rgb(226, 177, 83);
    private static final int GREEN = Color.rgb(83, 194, 125);
    private static final int BLUE = Color.rgb(105, 183, 222);

    private static final class Item {
        String label, pkg, category, sourceDir;
        String[] splitSourceDirs;
        Drawable icon;
        List<String> detectedProviders = Collections.emptyList();
        List<String> rootProviders = Collections.emptyList();
    }

    private static final class MetricCard {
        final LinearLayout root;
        final TextView value;
        MetricCard(LinearLayout root, TextView value) {
            this.root = root;
            this.value = value;
        }
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<Item> items = new ArrayList<>();
    private RecordStore store;
    private BaseAdapter adapter;
    private TextView rootStatus;
    private TextView rootButton;
    private TextView selectedAppText;
    private TextView gaidRefresh;
    private MetricCard gaidCard;
    private MetricCard appsFlyerCard;
    private MetricCard fullExportCard;
    private volatile String gaidValue;
    private Item selectedItem;
    private String selectedAppsFlyerId;
    private String selectedFullExport;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new RecordStore(this);
        float d = getResources().getDisplayMetrics().density;

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(8), dp(3), dp(8), 0);
        page.setBackgroundColor(BG);

        // Compact RENZO wordmark. Equal side spaces keep the title visually centered.
        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.setPadding(dp(2), dp(2), dp(2), dp(3));
        TextView leftBalance = new TextView(this);
        brandRow.addView(leftBalance, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setGravity(Gravity.CENTER);
        TextView brandTitle = new TextView(this);
        brandTitle.setText("RENZO");
        brandTitle.setTextColor(GOLD);
        brandTitle.setTextSize(23);
        brandTitle.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        brandTitle.setLetterSpacing(0.13f);
        brandTitle.setGravity(Gravity.CENTER);
        brand.addView(brandTitle);
        TextView brandSub = new TextView(this);
        brandSub.setText("ID SCANNER");
        brandSub.setTextColor(MUTED);
        brandSub.setTextSize(7);
        brandSub.setLetterSpacing(0.28f);
        brandSub.setGravity(Gravity.CENTER);
        brand.addView(brandSub);
        brandRow.addView(brand, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        gaidRefresh = new TextView(this);
        gaidRefresh.setText("↻");
        gaidRefresh.setTextColor(GOLD);
        gaidRefresh.setTextSize(22);
        gaidRefresh.setGravity(Gravity.CENTER);
        gaidRefresh.setBackground(rounded(SURFACE, Color.rgb(70, 57, 34), dp(9)));
        gaidRefresh.setOnClickListener(v -> refreshGaid());
        brandRow.addView(gaidRefresh, new LinearLayout.LayoutParams(dp(34), dp(34)));
        page.addView(brandRow);

        // Three compact copy targets: device GAID, selected game's AppsFlyer ID, full export.
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setGravity(Gravity.CENTER_VERTICAL);
        cards.setPadding(0, dp(4), 0, dp(2));

        gaidCard = createMetricCard("01  GAID", "READING DEVICE ID", GOLD,
                v -> copyGaid());
        appsFlyerCard = createMetricCard("02  APPSFLYER ID", "SELECT A GAME", BLUE,
                v -> copySelectedAppsFlyerId());
        fullExportCard = createMetricCard("03  FULL EXPORT", "GAID | ID | AID | UA", GOLD,
                v -> copyFullExport());
        addCard(cards, gaidCard, 0);
        addCard(cards, appsFlyerCard, 1);
        addCard(cards, fullExportCard, 2);
        page.addView(cards);

        selectedAppText = new TextView(this);
        selectedAppText.setText("SELECT A GAME TO LOAD ITS IDENTIFIERS");
        selectedAppText.setTextColor(MUTED);
        selectedAppText.setTextSize(8);
        selectedAppText.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        selectedAppText.setGravity(Gravity.CENTER_VERTICAL);
        selectedAppText.setSingleLine(true);
        selectedAppText.setEllipsize(TextUtils.TruncateAt.END);
        selectedAppText.setPadding(dp(3), dp(3), dp(3), dp(1));
        page.addView(selectedAppText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(19)));

        LinearLayout rootBar = new LinearLayout(this);
        rootBar.setGravity(Gravity.CENTER_VERTICAL);
        rootBar.setPadding(dp(3), dp(2), dp(1), dp(4));
        rootStatus = new TextView(this);
        rootStatus.setText("ROOT NOT CHECKED");
        rootStatus.setTextColor(MUTED);
        rootStatus.setTextSize(9);
        rootStatus.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        rootBar.addView(rootStatus, new LinearLayout.LayoutParams(0, dp(34), 1f));

        rootButton = new TextView(this);
        rootButton.setText("SCAN ROOT  ›");
        rootButton.setTextColor(GOLD);
        rootButton.setTextSize(9);
        rootButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        rootButton.setGravity(Gravity.CENTER);
        rootButton.setPadding(dp(11), 0, dp(11), 0);
        rootButton.setBackground(rounded(SURFACE, Color.rgb(78, 62, 36), dp(9)));
        rootButton.setOnClickListener(v -> scanWithRoot());
        rootBar.addView(rootButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        page.addView(rootBar);

        GridView grid = new GridView(this);
        grid.setBackgroundColor(BG);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(dp(7));
        grid.setHorizontalSpacing(dp(2));
        grid.setPadding(dp(2), dp(3), dp(2), dp(8));
        grid.setClipToPadding(false);
        grid.setSelector(android.R.color.transparent);
        adapter = new Adapter(d);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> onAppTap(items.get(pos)));
        page.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(page);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        refreshGaid();
    }

    private MetricCard createMetricCard(String title, String initial, int accent,
                                        View.OnClickListener listener) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(7), dp(6), dp(6), dp(5));
        box.setBackground(ripple(SURFACE, accent, dp(10)));
        box.setElevation(dp(1));
        box.setClickable(true);
        box.setFocusable(true);
        box.setOnClickListener(listener);

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(accent);
        heading.setTextSize(7.5f);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView value = new TextView(this);
        value.setText(initial);
        value.setTextColor(FG);
        value.setTextSize(9);
        value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        value.setMaxLines(2);
        value.setEllipsize(TextUtils.TruncateAt.END);
        value.setPadding(0, dp(4), 0, 0);
        box.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new MetricCard(box, value);
    }

    private void addCard(LinearLayout row, MetricCard card, int index) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(67), 1f);
        if (index > 0) lp.leftMargin = dp(5);
        row.addView(card.root, lp);
    }

    /** Reads this device/profile's GAID through Google Play services off the UI thread. */
    private void refreshGaid() {
        if (gaidCard == null || gaidRefresh == null) return;
        gaidCard.value.setText("READING DEVICE ID...");
        gaidCard.value.setTextColor(MUTED);
        gaidRefresh.setEnabled(false);
        io.execute(() -> {
            String id = null;
            try {
                com.google.android.gms.ads.identifier.AdvertisingIdClient.Info info =
                        com.google.android.gms.ads.identifier.AdvertisingIdClient
                                .getAdvertisingIdInfo(getApplicationContext());
                String fetched = info == null ? null : info.getId();
                if (!TextUtils.isEmpty(fetched)
                        && !"00000000-0000-0000-0000-000000000000".equals(fetched)) {
                    id = fetched;
                }
            } catch (Exception ignored) {
                // Play services may be absent or the advertising ID may be unavailable.
            }
            final String finalId = id;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || gaidCard == null) return;
                gaidValue = finalId;
                gaidCard.value.setText(finalId == null ? "NOT AVAILABLE" : finalId);
                gaidCard.value.setTextColor(finalId == null ? MUTED : GREEN);
                gaidRefresh.setEnabled(true);
                renderSelection();
            });
        });
    }

    private void onAppTap(Item item) {
        selectedItem = item;
        renderSelection();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    /** Refreshes the three top cards whenever the selected app or device GAID changes. */
    private void renderSelection() {
        if (appsFlyerCard == null || fullExportCard == null || selectedAppText == null) return;
        selectedAppsFlyerId = null;
        selectedFullExport = null;

        if (selectedItem == null) {
            appsFlyerCard.value.setText("SELECT A GAME");
            appsFlyerCard.value.setTextColor(MUTED);
            fullExportCard.value.setText("GAID | ID | AID | UA");
            fullExportCard.value.setTextColor(MUTED);
            selectedAppText.setText("SELECT A GAME TO LOAD ITS IDENTIFIERS");
            selectedAppText.setTextColor(MUTED);
            return;
        }

        String raw = store.get(selectedItem.pkg, Provider.APPSFLYER);
        selectedAppsFlyerId = SelectionExport.appsFlyerId(raw);
        selectedFullExport = SelectionExport.build(gaidValue, raw);

        if (selectedAppsFlyerId == null) {
            appsFlyerCard.value.setText("NO APPSFLYER ID");
            appsFlyerCard.value.setTextColor(MUTED);
            fullExportCard.value.setText(raw == null
                    ? "NO APPSFLYER RECORD" : "RECORD INCOMPLETE");
            fullExportCard.value.setTextColor(MUTED);
            String detail = selectedItem.rootProviders.contains(Provider.APPSFLYER)
                    ? "APPSFLYER FILE FOUND • NO ID VALUE"
                    : selectedItem.detectedProviders.contains(Provider.APPSFLYER)
                    ? "APPSFLYER SDK FOUND • RUN ROOT SCAN"
                    : "NO APPSFLYER DATA FOUND";
            selectedAppText.setText(selectedItem.label + "  •  " + detail);
            selectedAppText.setTextColor(MUTED);
        } else {
            appsFlyerCard.value.setText(selectedAppsFlyerId);
            appsFlyerCard.value.setTextColor(FG);
            if (selectedFullExport != null) {
                fullExportCard.value.setText(compact(selectedFullExport, 34));
                fullExportCard.value.setTextColor(GOLD);
            } else {
                fullExportCard.value.setText("WAITING FOR GAID / AID / UA");
                fullExportCard.value.setTextColor(MUTED);
            }
            selectedAppText.setText(selectedItem.label + "  •  APPSFLYER DATA READY");
            selectedAppText.setTextColor(GREEN);
        }
    }

    private String compact(String value, int limit) {
        if (value == null || value.length() <= limit) return value;
        return value.substring(0, limit - 1) + "…";
    }

    private void copyGaid() {
        copyValue("GAID", gaidValue, "GAID is not available on this device");
    }

    private void copySelectedAppsFlyerId() {
        copyValue("AppsFlyer ID", selectedAppsFlyerId,
                "Select a game with an AppsFlyer ID first");
    }

    private void copyFullExport() {
        copyValue("GAID|ID|AID|UA", selectedFullExport,
                "Complete export unavailable: check GAID and selected game data");
    }

    private void copyValue(String label, String value, String missingMessage) {
        if (TextUtils.isEmpty(value)) {
            Toast.makeText(this, missingMessage, Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(q, 0);
        Map<String, Item> unique = new LinkedHashMap<>();
        for (ResolveInfo ri : ris) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || unique.containsKey(pkg)) continue;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                Item it = new Item();
                it.pkg = pkg;
                it.label = String.valueOf(ri.loadLabel(pm));
                it.icon = ri.loadIcon(pm);
                it.category = getCategory(ai);
                it.rootProviders = store.getRootProviders(pkg);
                it.sourceDir = ai.sourceDir;
                it.splitSourceDirs = ai.splitSourceDirs;
                unique.put(pkg, it);
            } catch (PackageManager.NameNotFoundException ignored) {
                // App was uninstalled while the launcher list was being read.
            }
        }
        List<Item> out = new ArrayList<>(unique.values());
        Collections.sort(out, (a, z) -> a.label.compareToIgnoreCase(z.label));
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            items.clear();
            items.addAll(out);
            if (selectedItem != null) {
                for (Item candidate : out) {
                    if (candidate.pkg.equals(selectedItem.pkg)) {
                        selectedItem = candidate;
                        break;
                    }
                }
            }
            adapter.notifyDataSetChanged();
            renderSelection();
        });

        // Identify attribution SDK signatures in the public APK code, separately from
        // the local Root-based preference-file scan initiated by the user.
        for (Item item : out) {
            item.detectedProviders = SdkScanner.scan(item.sourceDir, item.splitSourceDirs);
        }
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed() && adapter != null) {
                adapter.notifyDataSetChanged();
                renderSelection();
            }
        });
    }

    private String getCategory(ApplicationInfo ai) {
        if (Build.VERSION.SDK_INT < 26) return "UNKNOWN";
        if (ai.category == ApplicationInfo.CATEGORY_GAME) return "GAME";
        return "APP";
    }

    private void scanWithRoot() {
        if (items.isEmpty()) {
            Toast.makeText(this, "App list is still loading", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> packages = new ArrayList<>();
        Map<String, Item> byPackageSnapshot = new LinkedHashMap<>();
        for (Item item : items) {
            packages.add(item.pkg);
            byPackageSnapshot.put(item.pkg, item);
        }

        rootStatus.setText("ROOT SCAN: WAITING FOR APPROVAL");
        rootStatus.setTextColor(GOLD);
        rootButton.setText("SCANNING…");
        rootButton.setEnabled(false);

        io.execute(() -> {
            RootAttributionScanner.Report report;
            try {
                report = RootAttributionScanner.scan(packages);
            } catch (IOException e) {
                runOnUiThread(() -> finishRootScan("ROOT NOT AVAILABLE", MUTED));
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                runOnUiThread(() -> finishRootScan("ROOT SCAN INTERRUPTED", MUTED));
                return;
            }

            if (!report.rootAvailable) {
                runOnUiThread(() -> finishRootScan("ROOT ACCESS DENIED", Color.rgb(230, 95, 85)));
                return;
            }

            int savedValues = 0;
            int providerFiles = 0;
            for (String pkg : packages) store.clearRootProviders(pkg);
            for (Map.Entry<String, java.util.Set<String>> entry
                    : report.providersByPackage.entrySet()) {
                Item item = byPackageSnapshot.get(entry.getKey());
                if (item != null) item.rootProviders = new ArrayList<>(entry.getValue());
                for (String provider : entry.getValue()) {
                    store.markRootProvider(entry.getKey(), provider);
                }
                providerFiles += entry.getValue().size();
            }
            for (Map.Entry<String, Map<String, String>> appEntry
                    : report.recordsByPackage.entrySet()) {
                for (Map.Entry<String, String> record : appEntry.getValue().entrySet()) {
                    if (store.save(appEntry.getKey(), record.getKey(), record.getValue())) {
                        savedValues++;
                    }
                }
            }

            int finalSavedValues = savedValues;
            int finalProviderFiles = providerFiles;
            String status = "ROOT AVAILABLE  •  " + report.scannedPackages
                    + " APPS  •  " + finalSavedValues + " IDS";
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                rootStatus.setText(status);
                rootStatus.setTextColor(GREEN);
                rootButton.setText("SCAN AGAIN  ›");
                rootButton.setEnabled(true);
                if (adapter != null) adapter.notifyDataSetChanged();
                renderSelection();
                Toast.makeText(MainActivity.this,
                        finalSavedValues + " values saved • " + finalProviderFiles
                                + " provider files found locally",
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private void finishRootScan(String status, int color) {
        if (isFinishing() || isDestroyed()) return;
        rootStatus.setText(status);
        rootStatus.setTextColor(color);
        rootButton.setText("SCAN ROOT  ›");
        rootButton.setEnabled(true);
    }

    @Override protected void onResume() {
        super.onResume();
        if (adapter != null) io.execute(this::loadApps);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private Drawable rounded(int color, int strokeColor, float radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        if (strokeColor != Color.TRANSPARENT) drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private Drawable ripple(int color, int strokeColor, float radiusPx) {
        GradientDrawable content = (GradientDrawable) rounded(color, strokeColor, radiusPx);
        return new RippleDrawable(ColorStateList.valueOf(Color.argb(48, 236, 190, 104)),
                content, null);
    }

    private final class Adapter extends BaseAdapter {
        private final float d;
        Adapter(float d) { this.d = d; }
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            LinearLayout cell;
            if (convertView instanceof LinearLayout) {
                cell = (LinearLayout) convertView;
                cell.removeAllViews();
            } else {
                cell = new LinearLayout(MainActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding((int) (2 * d), (int) (3 * d), (int) (2 * d), (int) (3 * d));
            }

            Item item = items.get(pos);
            boolean selected = selectedItem != null && selectedItem.pkg.equals(item.pkg);
            cell.setBackground(selected
                    ? rounded(Color.rgb(28, 25, 19), Color.rgb(120, 91, 43), dp(9))
                    : rounded(Color.TRANSPARENT, Color.TRANSPARENT, dp(9)));

            ImageView img = new ImageView(MainActivity.this);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setImageDrawable(item.icon);
            img.setContentDescription(item.label);
            int iconSize = (int) (50 * d);
            cell.addView(img, new LinearLayout.LayoutParams(iconSize, iconSize));

            TextView category = new TextView(MainActivity.this);
            category.setText(item.category);
            category.setTextColor(selected ? GOLD : Color.rgb(210, 161, 65));
            category.setTextSize(9);
            category.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            category.setGravity(Gravity.CENTER);
            category.setSingleLine(true);
            cell.addView(category, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            Map<String, String> records = store.getForPackage(item.pkg);
            List<String> providersToShow = new ArrayList<>();
            for (String provider : Provider.ORDERED) {
                if (item.detectedProviders.contains(provider) || item.rootProviders.contains(provider)
                        || records.containsKey(provider)) {
                    providersToShow.add(provider);
                }
            }
            if (records.containsKey(Provider.LEGACY)) providersToShow.add(Provider.LEGACY);

            img.setAlpha(providersToShow.isEmpty() ? 0.62f : 1f);
            for (String provider : providersToShow) {
                TextView tag = new TextView(MainActivity.this);
                tag.setText(Provider.displayName(provider));
                tag.setTextColor(records.containsKey(provider)
                        ? FG : BLUE);
                tag.setTextSize(8);
                tag.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                tag.setGravity(Gravity.CENTER);
                tag.setSingleLine(true);
                tag.setEllipsize(TextUtils.TruncateAt.END);
                tag.setPadding((int) (2 * d), 0, (int) (2 * d), 0);
                tag.setOnClickListener(v -> onAppTap(item));
                tag.setContentDescription(Provider.displayName(provider)
                        + " detected; tap to select app");
                cell.addView(tag, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, (int) (16 * d)));
            }
            return cell;
        }
    }
}
