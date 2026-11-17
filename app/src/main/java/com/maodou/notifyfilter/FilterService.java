package com.maodou.notifyfilter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FilterService extends NotificationListenerService {
    public static final String PREF = "cfg";
    public static final String LOG = "notify_log";
    public static final String HIST = "notify_hist";
    public static final String KEY_GLOBAL_ON = "global_on";
    public static final String KEY_AI_ON = "ai_on";
    public static final String KEY_AI_URL = "ai_url";
    public static final String KEY_AI_MODEL = "ai_model";
    public static final String KEY_AI_TOKEN = "ai_token";
    static final String SILENT_CHANNEL = "filtered_silent";
    static final String RESIDENT_CHANNEL = "resident";

    static final String[] CAT_KEYS = {"media","msg","conversation","sms","ongoing","call"};

    private static String catOf(Notification n) {
        String c = n.category;
        if (c != null && c.startsWith("cat:")) return c.substring(4);
        if ("cat:msg".equals(c)) return "msg";
        if ("cat:conversation".equals(c)) return "conversation";
        if ("cat:sms".equals(c)) return "sms";
        if ("cat:media".equals(c)) return "media";
        if ("cat:call".equals(c)) return "call";
        boolean ongoing = n != null && (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        if (ongoing) return "ongoing";
        return c != null ? c : "msg";
    }

    private void ensureChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel silent = new NotificationChannel(SILENT_CHANNEL,
            getString(R.string.silent_channel), NotificationManager.IMPORTANCE_LOW);
        silent.setSound(null, null);
        silent.enableVibration(false);
        nm.createNotificationChannel(silent);
        NotificationChannel res = new NotificationChannel(RESIDENT_CHANNEL,
            getString(R.string.resident_channel), NotificationManager.IMPORTANCE_MIN);
        res.setSound(null, null);
        res.setShowBadge(false);
        nm.createNotificationChannel(res);
    }

    @Override
    public void onListenerConnected() {
        postResident();
    }

    @Override
    public int onStartCommand(android.content.Intent i, int flags, int startId) {
        postResident();
        return START_STICKY;
    }

    private void postResident() {
        ensureChannels();
        Notification n = new Notification.Builder(this, RESIDENT_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.resident_title))
            .setContentText(getString(R.string.resident_text))
            .setOngoing(true)
            .setContentIntent(android.app.PendingIntent.getActivity(this, 0,
                new android.content.Intent(this, MainActivity.class),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE))
            .build();
        getSystemService(NotificationManager.class).notify(RESIDENT_CHANNEL, 1, n);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        sp.edit().putLong("last_seen", System.currentTimeMillis()).apply();
        if (!sp.getBoolean(KEY_GLOBAL_ON, true)) return;
        String cat = catOf(n);
        if (!sp.getBoolean("cat_" + cat, true)) {
            filterSilently(sbn, n, cat);
            return;
        }
        if (sp.getBoolean(KEY_AI_ON, false)) {
            String title = n.extras.getString(Notification.EXTRA_TITLE, "");
            String text = n.extras.getString(Notification.EXTRA_TEXT, "");
            AiFilter.evaluateAsync(this, title, text, cat, block -> {
                if (block) log(cat, "ai");
            });
        } else {
            log(cat, "pass");
        }
    }

    /**
     * 过滤 = 静默重发：撤回原通知（消除响铃/震动/横幅），
     * 用 IMPORTANCE_LOW 渠道重发等价通知，仍留在通知栏、可点击查看。
     */
    private void filterSilently(StatusBarNotification sbn, Notification n, String cat) {
        String title = n.extras.getString(Notification.EXTRA_TITLE, "");
        String text = n.extras.getString(Notification.EXTRA_TEXT, "");
        String pkg = sbn.getPackageName();
        try { cancelNotification(sbn.getKey()); } catch (Exception ignored) {}
        log(cat, "filter");
        addHistory(pkg, cat, title, text);
        try {
            ensureChannels();
            Notification quiet = new Notification.Builder(this, SILENT_CHANNEL)
                .setSmallIcon(n.getSmallIcon() != null
                    ? n.getSmallIcon().getResId() : android.R.drawable.ic_dialog_info)
                .setContentTitle(title.isEmpty() ? pkg : title)
                .setContentText(text)
                .setCategory(n.category)
                .setAutoCancel(true)
                .build();
            int nid = (pkg + "|" + sbn.getId()).hashCode();
            getSystemService(NotificationManager.class).notify("filtered_" + pkg, nid, quiet);
        } catch (Exception ignored) {}
    }

    private void log(String cat, String action) {
        SharedPreferences sp = getSharedPreferences(LOG, MODE_PRIVATE);
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String key = day + "|" + cat + "|" + action;
        sp.edit().putInt(key, sp.getInt(key, 0) + 1).apply();
    }

    /** 通知历史：最多保留 200 条被过滤的通知 */
    private void addHistory(String pkg, String cat, String title, String text) {
        try {
            SharedPreferences sp = getSharedPreferences(HIST, MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString("items", "[]"));
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis());
            o.put("pkg", pkg);
            o.put("cat", cat);
            o.put("title", title);
            o.put("text", text == null ? "" : text);
            arr.put(o);
            while (arr.length() > 200) arr.remove(0);
            sp.edit().putString("items", arr.toString()).apply();
        } catch (Exception ignored) {}
    }
}
