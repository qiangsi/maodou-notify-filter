package com.maodou.notifyfilter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SimpleTimeZone;

public class MainActivity extends Activity {
    static final String PREF = "cfg";
    static final String LOG = "notify_log";
    static final String[] CATS = {"media","msg","conversation","sms","ongoing","call"};
    static final String[] CAT_LABELS_ZH = {"媒体通知","消息通知","对话通知","短信通知","常驻通知","来电通知"};
    LinearLayout root; TextView title; LinearLayout body;
    ListView list; LinearLayout bottomBar; Button btnPass, btnFilter, btnAi;
    int theme = 0; // 0 day 1 night
    String lang = "zh";
    android.content.res.Resources locRes;

    String t(int id) {
        if (locRes != null && !"zh".equals(lang)) return locRes.getString(id);
        return getString(id);
    }

    void applyLang() {
        if ("zh".equals(lang)) { locRes = null; return; }
        java.util.Locale loc = new java.util.Locale("zh-rTW".equals(lang) ? "zh" : "en",
            "zh-rTW".equals(lang) ? "TW" : "");
        android.content.res.Configuration cfg = new android.content.res.Configuration();
        cfg.setLocale(loc);
        locRes = createConfigurationContext(cfg).getResources();
    }



    int fg() { return theme == 0 ? Color.parseColor("#1A1A1A") : Color.parseColor("#EDEDED"); }
    int bg() { return theme == 0 ? Color.parseColor("#F5F5F5") : Color.parseColor("#121212"); }
    int card() { return theme == 0 ? Color.WHITE : Color.parseColor("#1E1E2E"); }
    int accent() { return theme == 0 ? Color.parseColor("#2E7D32") : Color.parseColor("#66BB6A"); }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        theme = sp.getInt("theme", 0);
        lang = sp.getString("lang", "zh");
        applyLang();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg());
        title = new TextView(this);
        title.setTextSize(20); title.setPadding(dp(16), dp(14), dp(16), dp(10));
        title.setTextColor(fg());
        root.addView(title);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(4), dp(12), dp(8));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        list = new ListView(this);
        list.setDivider(null);
        list.setBackgroundColor(bg());
        body.addView(list, new LinearLayout.LayoutParams(-1, -1));
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setPadding(dp(8), dp(6), dp(8), dp(10));
        btnPass = barBtn(); btnFilter = barBtn(); btnAi = barBtn();
        bottomBar.addView(btnPass, w()); bottomBar.addView(btnFilter, w()); bottomBar.addView(btnAi, w());
        root.addView(bottomBar);
        setContentView(root);
        String page = getIntent() == null ? "smart" : getIntent().getStringExtra("page");
        show(page == null ? "smart" : page);
    }

    LinearLayout.LayoutParams w() { return new LinearLayout.LayoutParams(0, -2, 1f); }

    Button barBtn() {
        Button b = new Button(this);
        b.setTextSize(14);
        b.setTextColor(fg());
        b.setBackgroundColor(card());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        return b;
    }

    void show(String page) {
        switch (page) {
            case "analysis": showAnalysis(); break;
            case "settings": showSettings(); break;
            default: showSmart(); break;
        }
    }

    void showSmart() {
        title.setText("🌱 " + t(R.string.tab_smart));
        btnPass.setText(t(R.string.pass_rule));
        btnFilter.setText(t(R.string.filter_rule));
        btnAi.setText("AI");
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        List<Map<String, Object>> items = new ArrayList<>();
        // global switch
        Map<String, Object> g = new HashMap<>();
        g.put("type", "switch"); g.put("key", KEY_GLOBAL_ON());
        g.put("label", t(R.string.global_on));
        g.put("val", sp.getBoolean(KEY_GLOBAL_ON(), true));
        items.add(g);
        String[] labels = catLabels();
        for (int i = 0; i < CATS.length; i++) {
            Map<String, Object> m = new HashMap<>();
            m.put("type", "switch"); m.put("key", "cat_" + CATS[i]);
            m.put("label", labels[i]);
            m.put("val", sp.getBoolean("cat_" + CATS[i], true));
            items.add(m);
        }
        Map<String, Object> ai = new HashMap<>();
        ai.put("type", "switch"); ai.put("key", KEY_AI_ON());
        ai.put("label", t(R.string.ai_filter));
        ai.put("val", sp.getBoolean(KEY_AI_ON(), false));
        items.add(ai);
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("type", "ai");
        cfg.put("label", t(R.string.ai_config));
        items.add(cfg);
        list.setAdapter(makeAdapter(items));
        list.setOnItemClickListener((p, v, pos, id) -> {
            Map<String, Object> m = items.get(pos);
            if ("ai".equals(m.get("type"))) { aiConfigDialog(); }
        });
    }

    static String KEY_GLOBAL_ON() { return "global_on"; }
    static String KEY_AI_ON() { return "ai_on"; }

    void aiConfigDialog() {
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(0));
        EditText url = field(box, t(R.string.api_url),
                sp.getString("ai_url", ""));
        EditText model = field(box, t(R.string.api_model),
                sp.getString("ai_model", "gpt-4o-mini"));
        EditText token = field(box, t(R.string.api_token),
                sp.getString("ai_token", ""));
        new AlertDialog.Builder(this)
            .setTitle(t(R.string.ai_config))
            .setView(box)
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                sp.edit()
                    .putString("ai_url", url.getText().toString().trim())
                    .putString("ai_model", model.getText().toString().trim())
                    .putString("ai_token", token.getText().toString().trim())
                    .putBoolean("ai_on", !url.getText().toString().trim().isEmpty())
                    .apply();
                showSmart();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    EditText field(LinearLayout box, String label, String val) {
        TextView tv = new TextView(this); tv.setText(label); tv.setTextColor(fg()); tv.setTextSize(13);
        box.addView(tv);
        EditText et = new EditText(this); et.setText(val); et.setTextSize(13);
        box.addView(et);
        return et;
    }

    void showAnalysis() {
        title.setText("📊 " + t(R.string.tab_analysis));
        btnPass.setText(t(R.string.day));
        btnFilter.setText(t(R.string.week));
        btnAi.setText(t(R.string.month));
        String mode = "day"; Object[] holder = new Object[]{"day"};
        renderChart("day");
        btnPass.setOnClickListener(v -> renderChart("day"));
        btnFilter.setOnClickListener(v -> renderChart("week"));
        btnAi.setOnClickListener(v -> renderChart("month"));
    }

    void renderChart(String mode) {
        SimpleDateFormat df;
        if ("day".equals(mode)) df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        else if ("week".equals(mode)) df = new SimpleDateFormat("yyyy-'W'ww", Locale.US);
        else df = new SimpleDateFormat("yyyy-MM", Locale.US);
        df.setTimeZone(SimpleTimeZone.getDefault());
        Map<String, int[]> agg = new HashMap<>();
        SharedPreferences sp = getSharedPreferences(LOG, MODE_PRIVATE);
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            String[] p = e.getKey().split("\\|");
            if (p.length != 3) continue;
            String bucket = bucketOf(p[0], mode, df);
            int[] a = agg.computeIfAbsent(bucket, k -> new int[2]);
            if ("filter".equals(p[2]) || "ai".equals(p[2])) a[1] += (Integer) e.getValue();
            else a[0] += (Integer) e.getValue();
        }
        List<String> keys = new ArrayList<>(agg.keySet());
        java.util.Collections.sort(keys);
        if (keys.size() > 7) keys = keys.subList(keys.size() - 7, keys.size());
        body.removeAllViews();
        TextView head = new TextView(this);
        head.setText(t(R.string.notify_stat));
        head.setTextColor(fg()); head.setTextSize(15); head.setPadding(dp(4), dp(8), dp(4), dp(4));
        body.addView(head);
        LinearLayout chart = new LinearLayout(this);
        chart.setOrientation(LinearLayout.VERTICAL);
        chart.setBackgroundColor(card());
        chart.setPadding(dp(10), dp(10), dp(10), dp(10));
        int max = 1;
        for (String k : keys) { int[] a = agg.get(k); max = Math.max(max, a[0] + a[1]); }
        if (keys.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(t(R.string.no_data));
            empty.setTextColor(fg()); empty.setPadding(0, dp(20), 0, dp(20));
            chart.addView(empty);
        }
        for (String k : keys) {
            int[] a = agg.get(k);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            TextView lbl = new TextView(this);
            lbl.setText(k + "  " + (a[0] + a[1]));
            lbl.setTextColor(fg()); lbl.setTextSize(12);
            row.addView(lbl);
            LinearLayout bars = new LinearLayout(this);
            bars.setOrientation(LinearLayout.HORIZONTAL);
            View b1 = new View(this); b1.setBackgroundColor(accent());
            View b2 = new View(this); b2.setBackgroundColor(Color.parseColor("#E53935"));
            int totalW = list.getWidth() > 0 ? list.getWidth() - dp(20) : dp(280);
            bars.addView(b1, new LinearLayout.LayoutParams(Math.max(dp(2), (int)(totalW * a[0] / max)), dp(10)));
            bars.addView(b2, new LinearLayout.LayoutParams(Math.max(dp(2), (int)(totalW * a[1] / max)), dp(10)));
            row.addView(bars);
            chart.addView(row, new LinearLayout.LayoutParams(-1, -2));
            ((LinearLayout.LayoutParams) row.getLayoutParams()).topMargin = dp(6);
        }
        body.addView(chart);
        TextView legend = new TextView(this);
        legend.setText("■ " + t(R.string.passed)
            + "  ■ " + t(R.string.blocked));
        legend.setTextColor(fg()); legend.setTextSize(12); legend.setPadding(dp(4), dp(8), dp(4), 0);
        body.addView(legend);
    }

    String bucketOf(String day, String mode, SimpleDateFormat df) {
        if ("day".equals(mode)) return day;
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day);
            Calendar c = Calendar.getInstance();
            c.setTime(d);
            df.setTimeZone(SimpleTimeZone.getDefault());
            return df.format(c.getTime());
        } catch (Exception e) { return day; }
    }

    void showSettings() {
        title.setText("⚙️ " + t(R.string.tab_settings));
        btnPass.setText(t(R.string.export_cfg));
        btnFilter.setText(t(R.string.import_cfg));
        btnAi.setText(t(R.string.version));
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        List<Map<String, Object>> items = new ArrayList<>();
        add(items, "pick", t(R.string.theme_color), themeLabel());
        add(items, "pick", t(R.string.language), langLabel());
        add(items, "pick", t(R.string.app_lock),
            sp.getBoolean("lock", false) ? "ON" : "OFF");
        add(items, "info", t(R.string.faq), "");
        add(items, "info", t(R.string.feedback), "");
        add(items, "info", t(R.string.check_update), "");
        list.setAdapter(makeAdapter(items));
        list.setOnItemClickListener((p, v, pos, id) -> {
            Map<String, Object> m = items.get(pos);
            String label = (String) m.get("label");
            if (m.get("type").equals("pick")) {
                if (label.equals(t(R.string.theme_color))) themeDialog();
                else if (label.equals(t(R.string.language))) langDialog();
                else lockDialog();
            } else if (m.get("type").equals("info")) {
                if (label.equals(t(R.string.faq))) faqDialog();
                else if (label.equals(t(R.string.feedback))) {
                    try { startActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:siqiangcn@gmail.com"))); } catch (Exception ignored) {}
                } else {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pgyer.com/mdtzglzs"))); } catch (Exception ignored) {}
                }
            }
        });
    }

    void add(List<Map<String, Object>> items, String type, String label, String sub) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type); m.put("label", label); m.put("sub", sub);
        items.add(m);
    }

    String themeLabel() {
        return theme == 0 ? t(R.string.day_mode)
                          : t(R.string.night_mode);
    }
    String langLabel() {
        return "en".equals(lang) ? "English" : "zh-rTW".equals(lang) ? "繁體中文" : "简体中文";
    }
    String[] catLabels() {
        return new String[]{
            t(R.string.cat_media),
            t(R.string.cat_msg),
            t(R.string.cat_conversation),
            t(R.string.cat_sms),
            t(R.string.cat_ongoing),
            t(R.string.cat_call)};
    }

    void themeDialog() {
        String[] opts = {t(R.string.day_mode),
                         t(R.string.night_mode)};
        new AlertDialog.Builder(this).setTitle(t(R.string.theme_color))
            .setItems(opts, (d, w) -> {
                getSharedPreferences(PREF, MODE_PRIVATE).edit().putInt("theme", w).apply();
                recreate();
            }).show();
    }

    void langDialog() {
        String[] opts = {"简体中文", "繁體中文", "English"};
        new AlertDialog.Builder(this).setTitle(t(R.string.language))
            .setItems(opts, (d, w) -> {
                String v = w == 0 ? "zh" : w == 1 ? "zh-rTW" : "en";
                getSharedPreferences(PREF, MODE_PRIVATE).edit().putString("lang", v).apply();
                recreate();
            }).show();
    }

    void lockDialog() {
        String[] opts = {t(R.string.lock_on),
                         t(R.string.lock_off)};
        new AlertDialog.Builder(this).setTitle(t(R.string.app_lock))
            .setItems(opts, (d, w) -> {
                getSharedPreferences(PREF, MODE_PRIVATE).edit().putBoolean("lock", w == 0).apply();
                showSettings();
            }).show();
    }

    void faqDialog() {
        new AlertDialog.Builder(this).setTitle(t(R.string.faq))
            .setMessage(t(R.string.faq_text))
            .setPositiveButton(android.R.string.ok, null).show();
    }

    String exportFile;
    static final int REQ_EXPORT = 11;
    static final int REQ_IMPORT = 12;

    void doExport() {
        try {
            SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
            JSONObject o = new JSONObject();
            for (Map.Entry<String, ?> e : sp.getAll().entrySet()) o.put(e.getKey(), String.valueOf(e.getValue()));
            exportFile = new java.io.File(getExternalFilesDir(null), "notify_filter_config.json").getAbsolutePath();
            try (OutputStream os = new java.io.FileOutputStream(exportFile)) {
                os.write(o.toString().getBytes(StandardCharsets.UTF_8));
            }
            toast(t(R.string.export_ok) + exportFile);
        } catch (Exception e) {
            toast("Export failed: " + e.getMessage());
        }
    }

    void doImport() {
        try {
            java.io.File f = new java.io.File(getExternalFilesDir(null), "notify_filter_config.json");
            if (!f.exists()) { toast(t(R.string.no_file)); return; }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            try (InputStream is = new java.io.FileInputStream(f)) {
                byte[] buf = new byte[4096]; int r;
                while ((r = is.read(buf)) > 0) bos.write(buf, 0, r);
            }
            JSONObject o = new JSONObject(bos.toString("UTF-8"));
            SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
            SharedPreferences.Editor ed = sp.edit();
            java.util.Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                String v = o.getString(k);
                if ("true".equals(v) || "false".equals(v)) ed.putBoolean(k, Boolean.parseBoolean(v));
                else try { ed.putInt(k, Integer.parseInt(v)); } catch (Exception n) { ed.putString(k, v); }
            }
            ed.apply();
            recreate();
            toast(t(R.string.import_ok));
        } catch (Exception e) {
            toast("Import failed: " + e.getMessage());
        }
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    BaseAdapter makeAdapter(List<Map<String, Object>> items) {
        return new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int i) { return items.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup p) {
                LinearLayout cell = new LinearLayout(MainActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setBackgroundColor(card());
                cell.setPadding(dp(14), dp(12), dp(14), dp(12));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, 0, 0, dp(8));
                cell.setLayoutParams(lp);
                Map<String, Object> m = items.get(i);
                String label = (String) m.get("label");
                LinearLayout line = new LinearLayout(MainActivity.this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setGravity(Gravity.CENTER_VERTICAL);
                TextView tv = new TextView(MainActivity.this);
                tv.setText(label); tv.setTextColor(fg()); tv.setTextSize(15);
                line.addView(tv, new LinearLayout.LayoutParams(0, -2, 1f));
                if ("switch".equals(m.get("type"))) {
                    android.widget.Switch sw = new android.widget.Switch(MainActivity.this);
                    sw.setChecked((Boolean) m.get("val"));
                    sw.setOnCheckedChangeListener((b, v) -> {
                        getSharedPreferences(PREF, MODE_PRIVATE).edit().putBoolean((String) m.get("key"), v).apply();
                    });
                    line.addView(sw);
                } else {
                    String sub = (String) m.get("sub");
                    if (sub != null && !sub.isEmpty()) {
                        TextView st = new TextView(MainActivity.this);
                        st.setText(sub); st.setTextColor(fg()); st.setTextSize(13);
                        line.addView(st);
                    }
                }
                cell.addView(line);
                return cell;
            }
        };
    }
}
