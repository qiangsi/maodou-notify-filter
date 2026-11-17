package com.maodou.notifyfilter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class LockActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences sp = getSharedPreferences(MainActivity.PREF, MODE_PRIVATE);
        String code = sp.getString("lock_code", "");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(60), dp(24), 0);
        TextView tv = new TextView(this);
        tv.setText(code.isEmpty()
            ? getString(R.string.set_lock_code)
            : getString(R.string.enter_lock_code));
        tv.setTextSize(16);
        root.addView(tv);
        EditText et = new EditText(this);
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
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
