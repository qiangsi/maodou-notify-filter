package com.maodou.notifyfilter;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.Cursor;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import android.util.Base64;

/**
 * FR-H1: 全量通知历史，SQLite 本地存储，标题/正文 AES/GCM 加密（密钥在 AndroidKeyStore）。
 * 数据不出设备（FR-P1）。保留窗口默认 30 天（待拍板项 #1 的默认值），滚动清理。
 */
public class HistoryStore {
    public static final int ACTION_KEEP = 0;      // 保留
    public static final int ACTION_FILTER = 1;    // 分类过滤
    public static final int ACTION_RULE = 2;      // 命中规则
    public static final int ACTION_AI = 3;        // AI 过滤

    static final String KS_ALIAS = "mdn_hist_key";
    static final int RETAIN_DAYS = 30;
    static final int MAX_ROWS = 50000;

    static class Item {
        long id; long t; String pkg; String app; String title; String text;
        int action; String rule;
    }

    static class Db extends SQLiteOpenHelper {
        Db(Context c) { super(c, "history.db", null, 1); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE hist(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "t INTEGER, pkg TEXT, app TEXT, title_ct TEXT, text_ct TEXT,"
                + "action INTEGER DEFAULT 0, rule TEXT)");
            db.execSQL("CREATE INDEX idx_t ON hist(t)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int a, int b) {}
    }

    static Db db(Context c) { return new Db(c); }

    // ---- AES/GCM 加密，密钥存 AndroidKeyStore ----
    static synchronized SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KS_ALIAS)) return (SecretKey) ks.getKey(KS_ALIAS, null);
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(KS_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build());
        return kg.generateKey();
    }

    static String enc(String plain) {
        if (plain == null) plain = "";
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV();
            byte[] ct = c.doFinal(plain.getBytes("UTF-8"));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception e) {
            return Base64.encodeToString(plain.getBytes(), Base64.NO_WRAP); // 降级
        }
    }

    static String dec(String blob) {
        try {
            byte[] all = Base64.decode(blob, Base64.NO_WRAP);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), "UTF-8");
        } catch (Exception e) {
            try { return new String(Base64.decode(blob, Base64.NO_WRAP), "UTF-8"); }
            catch (Exception e2) { return ""; }
        }
    }

    static String appName(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) { return pkg; }
    }

    /** 写入一条，返回 id */
    public static long add(Context c, String pkg, String title, String text, int action, String rule) {
        try {
            SQLiteDatabase d = db(c).getWritableDatabase();
            android.content.ContentValues cv = new android.content.ContentValues();
            long t = System.currentTimeMillis();
            cv.put("t", t);
            cv.put("pkg", pkg);
            cv.put("app", appName(c, pkg));
            cv.put("title_ct", enc(title));
            cv.put("text_ct", enc(text));
            cv.put("action", action);
            cv.put("rule", rule == null ? "" : rule);
            long id = d.insert("hist", null, cv);
            d.close();
            prune(c);
            return id;
        } catch (Exception e) { return -1; }
    }

    public static void markAction(Context c, long id, int action, String rule) {
        if (id < 0) return;
        try {
            SQLiteDatabase d = db(c).getWritableDatabase();
            d.execSQL("UPDATE hist SET action=?, rule=? WHERE id=?",
                new Object[]{action, rule == null ? "" : rule, id});
            d.close();
        } catch (Exception ignored) {}
    }

    /** 滚动清理：超过 30 天或 5 万条的按最旧优先删除 */
    static void prune(Context c) {
        try {
            SQLiteDatabase d = db(c).getWritableDatabase();
            long cutoff = System.currentTimeMillis() - RETAIN_DAYS * 86400000L;
            d.execSQL("DELETE FROM hist WHERE t < ?", new Object[]{cutoff});
            d.execSQL("DELETE FROM hist WHERE id <= (SELECT MAX(id) FROM hist) - ?", new Object[]{MAX_ROWS});
            d.close();
        } catch (Exception ignored) {}
    }

    /** 读全部（时间倒序），解密后返回 */
    public static List<Item> load(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            SQLiteDatabase d = db(c).getReadableDatabase();
            Cursor cur = d.rawQuery("SELECT id,t,pkg,app,title_ct,text_ct,action,rule FROM hist ORDER BY t DESC", null);
            while (cur.moveToNext()) {
                Item it = new Item();
                it.id = cur.getLong(0); it.t = cur.getLong(1);
                it.pkg = cur.getString(2); it.app = cur.getString(3);
                it.title = dec(cur.getString(4)); it.text = dec(cur.getString(5));
                it.action = cur.getInt(6); it.rule = cur.getString(7);
                out.add(it);
            }
            cur.close(); d.close();
        } catch (Exception ignored) {}
        return out;
    }

    public static void delete(Context c, long id) {
        try {
            SQLiteDatabase d = db(c).getWritableDatabase();
            d.execSQL("DELETE FROM hist WHERE id=?", new Object[]{id});
            d.close();
        } catch (Exception ignored) {}
    }

    public static void clearAll(Context c) {
        try {
            SQLiteDatabase d = db(c).getWritableDatabase();
            d.execSQL("DELETE FROM hist");
            d.close();
        } catch (Exception ignored) {}
    }
}
