package com.maodou.notifyfilter;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

public class AiFilter {
    public interface Callback { void onResult(boolean block); }

    public static void evaluateAsync(Context ctx, String title, String text, String cat, Callback cb) {
        final Context c = ctx.getApplicationContext();
        new Thread(() -> {
            boolean block = evaluate(c, title, text, cat);
            new Handler(Looper.getMainLooper()).post(() -> cb.onResult(block));
        }).start();
    }

    private static boolean evaluate(Context c, String title, String text, String cat) {
        try {
            String urlStr = c.getSharedPreferences(FilterService.PREF, Context.MODE_PRIVATE)
                    .getString(FilterService.KEY_AI_URL, "");
            String model = c.getSharedPreferences(FilterService.PREF, Context.MODE_PRIVATE)
                    .getString(FilterService.KEY_AI_MODEL, "gpt-4o-mini");
            String token = c.getSharedPreferences(FilterService.PREF, Context.MODE_PRIVATE)
                    .getString(FilterService.KEY_AI_TOKEN, "");
            if (urlStr.isEmpty()) return false;
            JSONObject sys = new JSONObject().put("role", "system").put("content",
                "你是通知过滤助手。判断下面的安卓通知是否是垃圾/广告/营销/骚扰通知。" +
                "只回答 JSON: {\"block\": true} 或 {\"block\": false}");
            JSONObject user = new JSONObject().put("role", "user").put("content",
                "类别:" + cat + "\n标题:" + title + "\n内容:" + text);
            JSONObject body = new JSONObject()
                .put("model", model)
                .put("messages", new JSONArray().put(sys).put(user))
                .put("max_tokens", 20);
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            if (!token.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            java.io.InputStream is = code < 400 ? conn.getInputStream() : conn.getErrorStream();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int r;
            while ((r = is.read(buf)) > 0) bos.write(buf, 0, r);
            String resp = bos.toString("UTF-8");
            JSONObject root = new JSONObject(resp);
            String content = root.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content");
            JSONObject j = new JSONObject(content.trim()
                    .replace("```json", "").replace("```", "").trim());
            return j.optBoolean("block", false);
        } catch (Exception e) {
            return false;
        }
    }
}
