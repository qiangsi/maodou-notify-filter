package com.maodou.notifyfilter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * FR-H2~H6: 历史页——搜索（App/关键字）、按日分组时间轴、一键恢复、CSV 导出、清空（FR-P3）。
 */
public class HistoryActivity extends Activity {
    ListView list;
    EditText search;
    List<HistoryStore.Item> all = new ArrayList<>();
    List<Object> rows = new ArrayList<>(); // String=日期头, HistoryStore.Item=条目
    BaseAdapter adapter;
    int theme; int fg, bg, card, accent;
    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    String dayShown = null; // 非空 = 只看这一天

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

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("🕓 通知历史");
        title.setTextColor(fg); title.setTextSize(18);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Button exp = new Button(this);
        exp.setText("导出CSV"); exp.setTextSize(12); exp.setAllCaps(false);
        exp.setOnClickListener(v -> exportCsv());
        top.addView(exp, new LinearLayout.LayoutParams(-2, -2));
        Button clr = new Button(this);
        clr.setText("清空"); clr.setTextSize(12); clr.setAllCaps(false);
        clr.setOnClickListener(v -> confirmClear());
        top.addView(clr, new LinearLayout.LayoutParams(-2, -2));
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        search = new EditText(this);
        search.setHint("搜索：App 名 / 包名 / 关键字");
        search.setTextSize(13);
        search.setBackgroundResource(android.R.drawable.edit_text);
        root.addView(search, new LinearLayout.LayoutParams(-1, -2));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            public void onTextChanged(CharSequence s, int a, int b2, int c) {}
            public void afterTextChanged(Editable s) { rebuild(); }
        });

        Button dayBtn = new Button(this);
        dayBtn.setText("查看全部日期"); dayBtn.setTextSize(12); dayBtn.setAllCaps(false);
        dayBtn.setOnClickListener(v -> showDayPicker());
        root.addView(dayBtn, new LinearLayout.LayoutParams(-1, -2));
        final Button[] dayBtnRef = {dayBtn};

        list = new ListView(this);
        list.setDivider(null);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        all = HistoryStore.load(this);
        rebuild();

        adapter = new BaseAdapter() {
            @Override public int getCount() { return rows.size(); }
            @Override public Object getItem(int i) { return rows.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup p) {
                Object o = rows.get(i);
                LinearLayout cell = new LinearLayout(HistoryActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                if (o instanceof String) { // 日期分组头
                    cell.setBackgroundColor(card);
                    cell.setPadding(dp(12), dp(8), dp(12), dp(4));
                    TextView tv = new TextView(HistoryActivity.this);
                    tv.setText((String) o);
                    tv.setTextColor(accent); tv.setTextSize(13);
                    cell.addView(tv);
                    return cell;
                }
                HistoryStore.Item it = (HistoryStore.Item) o;
                cell.setBackgroundColor(card);
                cell.setPadding(dp(12), dp(10), dp(12), dp(10));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, 0, 0, dp(6));
                cell.setLayoutParams(lp);
                TextView t1 = new TextView(HistoryActivity.this);
                String tag = it.action == HistoryStore.ACTION_KEEP ? "" :
                    it.action == HistoryStore.ACTION_FILTER ? " [已过滤]" :
                    it.action == HistoryStore.ACTION_RULE ? " [规则:" + it.rule + "]" : " [AI]";
                t1.setText("[" + it.app + "] " + it.title + tag);
                t1.setTextColor(it.action == HistoryStore.ACTION_KEEP ? fg : Color.parseColor("#E53935"));
                t1.setTextSize(14);
                cell.addView(t1);
                TextView t2 = new TextView(HistoryActivity.this);
                t2.setText(it.text);
                t2.setTextColor(fg); t2.setTextSize(12);
                cell.addView(t2);
                TextView t3 = new TextView(HistoryActivity.this);
                t3.setText(new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date(it.t))
                    + " · " + it.pkg + "  · 长按恢复/删除");
                t3.setTextColor(fg); t3.setTextSize(11);
                cell.addView(t3);
                cell.setOnLongClickListener(v -> { itemMenu(it); return true; });
                return cell;
            }
        };
        list.setAdapter(adapter);
    }

    void rebuild() {
        String q = search.getText().toString().trim().toLowerCase();
        rows.clear();
        String lastDay = null;
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        for (HistoryStore.Item it : all) {
            if (dayShown != null && !dayShown.equals(df.format(new Date(it.t)))) continue;
            if (!q.isEmpty()
                && !it.title.toLowerCase().contains(q)
                && !it.text.toLowerCase().contains(q)
                && !it.pkg.toLowerCase().contains(q)
                && !it.app.toLowerCase().contains(q)) continue;
            String day = df.format(new Date(it.t));
            if (!day.equals(lastDay)) { rows.add(day + "（" + countOfDay(day) + " 条）"); lastDay = day; }
            rows.add(it);
        }
        if (rows.isEmpty()) rows.add("（无匹配记录）");
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    int countOfDay(String day) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        int n = 0;
        for (HistoryStore.Item it : all) if (day.equals(df.format(new Date(it.t)))) n++;
        return n;
    }

    void showDayPicker() {
        java.util.TreeSet<String> days = new java.util.TreeSet<>();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        for (HistoryStore.Item it : all) days.add(df.format(new Date(it.t)));
        final String[] arr = days.descendingSet().toArray(new String[0]);
        new AlertDialog.Builder(this)
            .setTitle("跳转到日期")
            .setItems(arr, (d, w) -> {
                dayShown = arr[w].startsWith("yyyy") ? null : arr[w];
                rebuild();
            })
            .setNeutralButton("查看全部", (d, w) -> { dayShown = null; rebuild(); })
            .show();
    }

    /** FR-H4: 一键恢复——以本 App 名义重发到通知栏，标注来源应用 */
    void restore(HistoryStore.Item it) {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            String ch = "restore";
            nm.createNotificationChannel(new NotificationChannel(ch, "恢复的通知",
                NotificationManager.IMPORTANCE_DEFAULT));
            Notification n = new Notification.Builder(this, ch)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("↩ [" + it.app + "] " + it.title)
                .setContentText(it.text)
                .setAutoCancel(true)
                .build();
            nm.notify("restore", (int) it.id, n);
            toast("已恢复到通知栏");
        } catch (Exception e) { toast("恢复失败: " + e.getMessage()); }
    }

    void itemMenu(HistoryStore.Item it) {
        new AlertDialog.Builder(this)
            .setTitle(it.title)
            .setItems(new String[]{"恢复到通知栏", "删除该记录"}, (d, w) -> {
                if (w == 0) restore(it);
                else { HistoryStore.delete(this, it.id); all = HistoryStore.load(this); rebuild(); }
            }).show();
    }

    void confirmClear() {
        new AlertDialog.Builder(this)
            .setTitle("清空全部历史")
            .setMessage("将删除全部通知历史（含加密数据），不可恢复。确定？")
            .setPositiveButton("清空", (d, w) -> {
                HistoryStore.clearAll(this);
                all = HistoryStore.load(this); rebuild();
                toast("已清空");
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /** FR-H6: CSV 导出（UTF-8 BOM，Excel 中文不乱码） */
    void exportCsv() {
        try {
            File f = new File(getExternalFilesDir(null),
                "maodou_notify_history_" + System.currentTimeMillis() + ".csv");
            OutputStream os = new FileOutputStream(f);
            os.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            StringBuilder sb = new StringBuilder("时间,应用名,包名,标题,正文,处理结果\n");
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            for (HistoryStore.Item it : all) {
                String res = it.action == HistoryStore.ACTION_KEEP ? "保留" :
                    it.action == HistoryStore.ACTION_FILTER ? "已过滤" :
                    it.action == HistoryStore.ACTION_RULE ? "规则:" + it.rule : "AI";
                sb.append(df.format(new Date(it.t))).append(',')
                    .append(csv(it.app)).append(',').append(csv(it.pkg)).append(',')
                    .append(csv(it.title)).append(',').append(csv(it.text)).append(',')
                    .append(res).append('\n');
            }
            os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            os.close();
            toast("已导出: " + f.getAbsolutePath());
        } catch (Exception e) { toast("导出失败: " + e.getMessage()); }
    }

    String csv(String s) {
        if (s == null) s = "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        return s.replace("\r", "");
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
