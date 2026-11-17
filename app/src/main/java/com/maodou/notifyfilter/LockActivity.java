package com.maodou.notifyfilter;

import android.app.Activity;
import android.content.SharedPreferences;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class LockActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        final SharedPreferences sp = getSharedPreferences(MainActivity.PREF, MODE_PRIVATE);
        final String code = sp.getString("lock_code", "");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(60), dp(24), 0);
        final TextView tv = new TextView(this);
        tv.setText(code.isEmpty()
            ? getString(R.string.set_lock_code)
            : getString(R.string.enter_lock_code));
        tv.setTextSize(16);
        root.addView(tv);
        final EditText et = new EditText(this);
        root.addView(et);
        Button ok = new Button(this);
        ok.setText(android.R.string.ok);
        ok.setOnClickListener(v -> {
            String input = et.getText().toString();
            if (code.isEmpty()) {
                if (input.length() >= 4) {
                    sp.edit().putString("lock_code", input).putBoolean("lock", true).apply();
                    finish();
                } else {
                    tv.setText(getString(R.string.lock_code_too_short));
                }
            } else if (input.equals(code)) {
                finish();
            } else {
                tv.setText(getString(R.string.lock_code_wrong));
                et.setText("");
            }
        });
        root.addView(ok);
        setContentView(root);

        // 已设密码 + 开了生物识别 + API 28+ + 有指纹硬件：先弹指纹，失败/取消回退密码输入
        if (!code.isEmpty() && sp.getBoolean("lock_biometric", false)
                && Build.VERSION.SDK_INT >= 28
                && getPackageManager().hasSystemFeature("android.hardware.fingerprint")) {
            BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                .setTitle(getString(R.string.app_name))
                .setDescription(getString(R.string.biometric_unlock))
                .build();
            prompt.authenticate(new CancellationSignal(),
                getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                        finish();
                    }
                });
        }
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
