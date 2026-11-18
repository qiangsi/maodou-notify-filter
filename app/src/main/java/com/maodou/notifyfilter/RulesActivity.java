package com.maodou.notifyfilter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * FR-R2~R4: 规则管理——增删改、启停、排序（上移/下移）、复制、删除确认。
 * 条件：App（多选之一）+ 关键字 + 时间段（支持跨零点）；动作：消除 / 静音。
 */
public class RulesActivity extends Activity {
    ListView list;
    List<RuleEngine.Rule> rules;
    BaseAdapter adapter;
    int theme; int fg, bg, card, accent;
    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        theme = getSharedPreferences("cfg", MODE_PRIVATE).getInt("theme", 0);
        fg = theme == 0 ? Color.parseColor("#1A1A1A") : Color.parseColor("#EDEDED");
        bg = theme == 0 ? Color.parseColor("#F5F5F5") : Color.parseColor("#121212");
        card = theme == 0 ? Color.WHITE : Color.parseColor("#1E1E2E");
        accent = theme == 0 ? Color.parseColor("#2E7D32") : Color.parseColor("#66BB6A");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(dp(10), dp(8), dp(10), dp(8));

        TextView title = new TextView(this);
        title.setText("📋 过滤规则");
        title.setTextColor(fg); title.setTextSize(18);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        list = new ListView(this);
        list.setDivider(null);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button add = new Button(this);
        add.setText("＋ 新建规则");
        add.setOnClickListener(v -> editDialog(null));
        root.addView(add, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);

        rules = RuleEngine.load(this);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return rules.size(); }
            @Override public Object getItem(int i) { return rules.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup p) {
                RuleEngine.Rule r = rules.get(i);
                LinearLayout cell = new LinearLayout(RulesActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setBackgroundColor(card);
                cell.setPadding(dp(12), dp(10), dp(12), dp(10));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, 0, 0, dp(6));
                cell.setLayoutParams(lp);
                TextView t1 = new TextView(RulesActivity.this);
                t1.setText(ruleLabel(r));
                t1.setTextColor(r.enabled ? fg : Color.GRAY);
                t1.setTextSize(14);
                cell.addView(t1);
                TextView t2 = new TextView(RulesActivity.this);
                t2.setText("点击编辑 · 长按更多（启停/上移/下移/复制/删除）");
                t2.setTextColor(r.enabled ? Color.GRAY : Color.parseColor("#E53935"));
                t2.setTextSize(11);
                cell.addView(t2);
                cell.setOnClickListener(v -> editDialog(r));
                cell.setOnLongClickListener(v -> { moreMenu(r); return true; });
                return cell;
            }
        };
        list.setAdapter(adapter);
    }

    String ruleLabel(RuleEngine.Rule r) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.pkg.isEmpty() ? "全部应用" : HistoryStore.appName(this, r.pkg));
        if (!r.keyword.isEmpty()) sb.append(" · 含「").append(r.keyword).append("」");
        if (!(r.startMin == 0 && r.endMin == 1439)) {
            sb.append(" · ").append(String.format("%02d:%02d-%02d:%02d",
                r.startMin / 60, r.startMin % 60, r.endMin / 60, r.endMin % 60));
        }
        sb.append(" → ").append(r.action == RuleEngine.ACT_REMOVE ? "消除" : "静音");
        if (!r.enabled) sb.append("（已停用）");
        return sb.toString();
    }

    void persist() {
        RuleEngine.save(this, rules);
        adapter.notifyDataSetChanged();
    }

    void moreMenu(RuleEngine.Rule r) {
        final int idx = rules.indexOf(r);
        List<String> opts = new ArrayList<>();
        opts.add(r.enabled ? "停用" : "启用");
        opts.add("上移");
        opts.add("下移");
        opts.add("复制");
        opts.add("删除");
        new AlertDialog.Builder(this)
            .setTitle(ruleLabel(r))
            .setItems(opts.toArray(new String[0]), (d, w) -> {
                switch (w) {
                    case 0: r.enabled = !r.enabled; persist(); break;
                    case 1: if (idx > 0) { Collections.swap(rules, idx, idx - 1); persist(); } break;
                    case 2: if (idx < rules.size() - 1) { Collections.swap(rules, idx, idx + 1); persist(); } break;
                    case 3:
                        RuleEngine.Rule cp = new RuleEngine.Rule();
                        cp.enabled = r.enabled; cp.pkg = r.pkg; cp.keyword = r.keyword;
                        cp.startMin = r.startMin; cp.endMin = r.endMin; cp.action = r.action;
                        cp.id = System.currentTimeMillis();
                        rules.add(idx + 1, cp); persist();
                        break;
                    case 3 + 1:
                        new AlertDialog.Builder(this)
                            .setMessage("删除该规则？")
                            .setPositiveButton("删除", (d2, w2) -> { rules.remove(idx); persist(); })
                            .setNegativeButton(android.R.string.cancel, null).show();
                        break;
                }
            }).show();
    }

    void editDialog(RuleEngine.Rule existing) {
        final RuleEngine.Rule r = existing != null ? existing : new RuleEngine.Rule();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), 0);

        TextView l1 = new TextView(this); l1.setText("生效应用（点选，留空=全部）");
        l1.setTextColor(fg); l1.setTextSize(13); box.addView(l1);
        TextView pkgBtn = new TextView(this);
        pkgBtn.setText(r.pkg.isEmpty() ? "全部应用" : HistoryStore.appName(this, r.pkg));
        pkgBtn.setTextColor(accent); pkgBtn.setTextSize(14);
        pkgBtn.setPadding(0, dp(6), 0, dp(6));
        box.addView(pkgBtn);
        pkgBtn.setOnClickListener(v -> pickApp(pkgBtn, r));

        EditText kw = new EditText(this);
        kw.setHint("关键字（标题或正文包含，忽略大小写，留空=不限）");
        kw.setText(r.keyword); kw.setTextSize(13);
        box.addView(kw);

        TextView l2 = new TextView(this); l2.setText("时间段（如 18:00–23:59，可跨零点）");
        l2.setTextColor(fg); l2.setTextSize(13); box.addView(l2);
        LinearLayout times = new LinearLayout(this); times.setOrientation(LinearLayout.HORIZONTAL);
        EditText s = new EditText(this); s.setHint("开始 08:00"); s.setText(fmtMin(r.startMin)); s.setTextSize(13);
        EditText e = new EditText(this); e.setHint("结束 23:59"); e.setText(fmtMin(r.endMin)); e.setTextSize(13);
        times.addView(s, new LinearLayout.LayoutParams(0, -2, 1f));
        times.addView(e, new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(times);

        TextView l3 = new TextView(this); l3.setText("动作");
        l3.setTextColor(fg); l3.setTextSize(13); box.addView(l3);
        TextView actBtn = new TextView(this);
        actBtn.setText(r.action == RuleEngine.ACT_REMOVE ? "消除（直接从通知栏消除）" : "静音（静默展示，不响铃）");
        actBtn.setTextColor(accent); actBtn.setTextSize(14); actBtn.setPadding(0, dp(6), 0, dp(6));
        box.addView(actBtn);
        actBtn.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setItems(new String[]{"消除", "静音"}, (d, w) -> {
                r.action = w;
                actBtn.setText(w == 0 ? "消除（直接从通知栏消除）" : "静音（静默展示，不响铃）");
            }).show());

        new AlertDialog.Builder(this)
            .setTitle(existing == null ? "新建规则" : "编辑规则")
            .setView(box)
            .setPositiveButton(android.R.string.ok, (d, w) -> {
                r.keyword = kw.getText().toString().trim();
                r.startMin = parseMin(s.getText().toString(), 0);
                r.endMin = parseMin(e.getText().toString(), 1439);
                if (existing == null) { r.id = System.currentTimeMillis(); r.enabled = true; rules.add(r); }
                persist();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    void pickApp(TextView btn, RuleEngine.Rule r) {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        List<String> names = new ArrayList<>();
        List<String> pkgs = new ArrayList<>();
        names.add("全部应用"); pkgs.add("");
        for (ApplicationInfo ai : apps) {
            names.add(String.valueOf(pm.getApplicationLabel(ai)));
            pkgs.add(ai.packageName);
        }
        new AlertDialog.Builder(this)
            .setTitle("选择应用")
            .setItems(names.toArray(new String[0]), (d, w) -> {
                r.pkg = pkgs.get(w);
                btn.setText(w == 0 ? "全部应用" : names.get(w));
            }).show();
    }

    String fmtMin(int m) { return String.format("%02d:%02d", m / 60, m % 60); }

    int parseMin(String s, int def) {
        try {
            String[] p = s.trim().split(":");
            return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        } catch (Exception e) { return def; }
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
