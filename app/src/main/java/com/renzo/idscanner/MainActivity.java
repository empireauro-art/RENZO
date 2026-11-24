package com.renzo.idscanner;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private TextView gaidTv, statusTv;
    private ArrayList<String> listDisplay = new ArrayList<>(), listIds = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private String rawGaid = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(30, 30, 30, 30);
        root.setBackgroundColor(Color.parseColor("#121212"));

        gaidTv = new TextView(this);
        gaidTv.setTextColor(Color.parseColor("#00E676"));
        gaidTv.setBackgroundColor(Color.parseColor("#1E1E1E"));
        gaidTv.setPadding(24, 24, 24, 24);
        gaidTv.setOnClickListener(v -> { if (!rawGaid.isEmpty()) copy("GAID", rawGaid); });
        root.addView(gaidTv);

        statusTv = new TextView(this);
        statusTv.setTextColor(Color.parseColor("#FFB74D"));
        statusTv.setPadding(8, 12, 8, 8);
        root.addView(statusTv);

        Button btn = new Button(this);
        btn.setText("🔄 تحديث وسحب معرفات AppsFlyer");
        btn.setBackgroundColor(Color.parseColor("#2979FF"));
        btn.setTextColor(Color.WHITE);
        btn.setOnClickListener(v -> scan());
        root.addView(btn);

        ListView lv = new ListView(this);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listDisplay) {
            @Override
            public View getView(int pos, View cv, ViewGroup p) {
                TextView tv = (TextView) super.getView(pos, cv, p);
                tv.setTextColor(Color.WHITE);
                tv.setBackgroundColor(Color.parseColor("#1E1E1E"));
                tv.setPadding(24, 24, 24, 24);
                return tv;
            }
        };
        lv.setAdapter(adapter);
        lv.setDividerHeight(16);
        lv.setOnItemClickListener((p, v, pos, id) -> {
            String val = listIds.get(pos);
            if (val != null && !val.startsWith("⚠️")) copy("AppsFlyer ID", val);
            else Toast.makeText(this, "لا يوجد معرف متاح للنسخ", Toast.LENGTH_SHORT).show();
        });
        root.addView(lv);
        setContentView(root);
        scan();
    }

    private void scan() {
        new Thread(() -> {
            rawGaid = IdExtractor.getGaid(this);
            boolean rooted = IdExtractor.hasRoot();
            runOnUiThread(() -> {
                gaidTv.setText("📱 GAID (اضغط للنسخ):\n" + rawGaid);
                statusTv.setText(rooted ? "✅ Root مفعل (جاهز للسحب)" : "⚠️ بدون Root (اكتشاف النوع فقط)");
            });

            PackageManager pm = getPackageManager();
            ArrayList<String> tDisp = new ArrayList<>(), tIds = new ArrayList<>();
            for (ApplicationInfo app : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                String pkg = app.packageName;
                if (pkg.equals(getPackageName())) continue;

                boolean hasAf = false;
                try { if (SdkScanner.detectProvider(pm, pkg) == Provider.APPSFLYER) hasAf = true; } catch (Exception ignored) {}
                String afId = rooted ? IdExtractor.extractAppsFlyerId(pkg) : null;

                if (afId != null) {
                    tDisp.add("🎮 " + pm.getApplicationLabel(app) + "\n📦 " + pkg + "\n🏷️ النوع: AppsFlyer\n🔑 المعرف (اضغط للنسخ):\n" + afId);
                    tIds.add(afId);
                } else if (hasAf) {
                    String m = rooted ? "⚠️ افتح اللعبة مرة ثم اضغط تحديث" : "⚠️ يتطلب بيئة Root لسحب المعرف";
                    tDisp.add("🎮 " + pm.getApplicationLabel(app) + "\n📦 " + pkg + "\n🏷️ النوع: AppsFlyer\n" + m);
                    tIds.add(m);
                }
            }
            runOnUiThread(() -> {
                listDisplay.clear(); listIds.clear();
                if (tDisp.isEmpty()) { listDisplay.add("لا توجد ألعاب AppsFlyer حالياً"); listIds.add("⚠️"); }
                else { listDisplay.addAll(tDisp); listIds.addAll(tIds); }
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void copy(String label, String txt) {
        ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(label, txt));
        Toast.makeText(this, "✅ تم نسخ " + label + ":\n" + txt, Toast.LENGTH_LONG).show();
    }
}
