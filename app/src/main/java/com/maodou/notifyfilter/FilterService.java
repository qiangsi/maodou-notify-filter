package com.maodou.notifyfilter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FilterService extends NotificationListenerService {
    public static final String PREF = "cfg";
    public static final String LOG = "notify_log";
    public static final String KEY_GLOBAL_ON = "global_on";
    public static final String KEY_AI_ON = "ai_on";
    public static final String KEY_AI_URL = "ai_url";
    public static final String KEY_AI_MODEL = "ai_model";
    public static final String KEY_AI_TOKEN = "ai_token";
    static final String SILENT_CHANNEL = "filtered_silent";

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

    private void ensureSilentChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(SILENT_CHANNEL,
            getString(R.string.silent_channel), NotificationManager.IMPORTANCE_LOW);
        ch.setSound(null, null);
        ch.enableVibration(false);
        nm.createNotificationChannel(ch);
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
        try {
            ensureSilentChannel();
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
}
