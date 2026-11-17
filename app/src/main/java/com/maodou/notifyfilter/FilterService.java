package com.maodou.notifyfilter;

import android.app.Notification;
import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.content.Context;
import android.app.NotificationManager;
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

    static final String[] CAT_KEYS = {"media","msg","conversation","sms","ongoing","call"};

    private static String catOf(StatusBarNotification sbn, Notification n) {
        String c = n == null ? null : n.category;
        if (c == null && sbn != null) c = sbn.getNotification().category;
        if ("cat:msg".equals(c)) return "msg";
        if ("cat:conversation".equals(c)) return "conversation";
        if ("cat:sms".equals(c)) return "sms";
        if ("cat:media".equals(c)) return "media";
        if ("cat:call".equals(c)) return "call";
        if (c != null && c.startsWith("cat:")) return c.substring(4);
        boolean ongoing = n != null && (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        if (ongoing) return "ongoing";
        return "msg";
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        String cat = catOf(sbn, n);
        getSharedPreferences(PREF, MODE_PRIVATE).edit().putLong("last_seen", System.currentTimeMillis()).apply();
        if (!getSharedPreferences(PREF, MODE_PRIVATE).getBoolean(KEY_GLOBAL_ON, true)) return;
        if (!getSharedPreferences(PREF, MODE_PRIVATE).getBoolean("cat_" + cat, true)) {
            log(sbn, cat, "filter");
            n.priority = Notification.PRIORITY_MIN;
            n.visibility = Notification.VISIBILITY_SECRET;
            sbn.getNotification().extras.putString("android.title", "");
            sbn.getNotification().extras.putString("android.text", "");
            return;
        }
        if (getSharedPreferences(PREF, MODE_PRIVATE).getBoolean(KEY_AI_ON, false)) {
            String title = n.extras.getString(Notification.EXTRA_TITLE, "");
            String text = n.extras.getString(Notification.EXTRA_TEXT, "");
            AiFilter.evaluateAsync(this, title, text, cat, new AiFilter.Callback() {
                @Override public void onResult(boolean block) {
                    if (block) log(sbn, cat, "ai");
                }
            });
        } else {
            log(sbn, cat, "pass");
        }
    }

    private void log(StatusBarNotification sbn, String cat, String action) {
        SharedPreferences sp = getSharedPreferences(LOG, MODE_PRIVATE);
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        String key = day + "|" + cat + "|" + action;
        sp.edit().putInt(key, sp.getInt(key, 0) + 1).apply();
    }
}
