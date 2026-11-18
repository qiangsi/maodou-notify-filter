package com.maodou.notifyfilter;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.List;

/**
 * FR-R1~R4: 规则引擎。规则 = 条件（App/关键字/时间段，AND）→ 动作（消除/静音）。
 * 待拍板默认值：首条命中即执行；关键字忽略大小写子串匹配；时间段支持跨零点。
 */
public class RuleEngine {
    static final String PREF = "rules";

    /** 静音：撤回原通知后用静音渠道重发（复用 FilterService 的 SILENT_CHANNEL） */
    public static final int ACT_REMOVE = 0;
    public static final int ACT_MUTE = 1;

    static class Rule {
        long id; boolean enabled;
        String pkg;      // "" = 全部应用
        String keyword;  // 忽略大小写子串，"" = 不限
        int startMin;    // 0..1439
        int endMin;      // 跨零点支持：start > end 视为跨天
        int action;
    }

    public static List<Rule> load(Context c) {
        java.util.List<Rule> out = new java.util.ArrayList<>();
        try {
            SharedPreferences sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString("items", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Rule r = new Rule();
                r.id = o.optLong("id", System.currentTimeMillis());
                r.enabled = o.optBoolean("enabled", true);
                r.pkg = o.optString("pkg", "");
                r.keyword = o.optString("kw", "");
                r.startMin = o.optInt("s", 0);
                r.endMin = o.optInt("e", 1439);
                r.action = o.optInt("act", ACT_REMOVE);
                out.add(r);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void save(Context c, List<Rule> rules) {
        try {
            JSONArray arr = new JSONArray();
            for (Rule r : rules) {
                JSONObject o = new JSONObject();
                o.put("id", r.id);
                o.put("enabled", r.enabled);
                o.put("pkg", r.pkg);
                o.put("kw", r.keyword);
                o.put("s", r.startMin);
                o.put("e", r.endMin);
                o.put("act", r.action);
                arr.put(o);
            }
            c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString("items", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    static boolean inWindow(int nowMin, int s, int e) {
        if (s == e) return true; // 全天
        if (s < e) return nowMin >= s && nowMin < e;
        return nowMin >= s || nowMin < e; // 跨零点
    }

    /**
     * 匹配引擎：按规则顺序，首条命中的启用规则即执行（FR-R1 待拍板默认）。
     * @return 命中的规则，null = 未命中（正常保留）
     */
    public static Rule match(Context c, String pkg, String title, String text) {
        int nowMin;
        Calendar cal = Calendar.getInstance();
        nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        String kw = (title + "\n" + text).toLowerCase();
        for (Rule r : load(c)) {
            if (!r.enabled) continue;
            if (!r.pkg.isEmpty() && !r.pkg.equals(pkg)) continue;
            if (!r.keyword.isEmpty() && !kw.contains(r.keyword.toLowerCase())) continue;
            if (!inWindow(nowMin, r.startMin, r.endMin)) continue;
            return r;
        }
        return null;
    }
}
