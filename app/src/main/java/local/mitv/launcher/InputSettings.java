package local.mitv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.LinearLayout;

final class InputSettings {
    private InputSettings() {}

    static void show(Activity activity, SharedPreferences prefs, Runnable changed) {
        String[] labels = new String[3];
        for (int port = 1; port <= 3; port++) {
            String name = InputNames.normalize(prefs.getString(InputNames.key(port), ""));
            labels[port - 1] = "HDMI" + port + "    " + (name.isEmpty() ? "未命名" : name);
        }
        new AlertDialog.Builder(activity).setTitle("信号源名称")
            .setItems(labels, (dialog, index) -> edit(activity, prefs, index + 1, changed))
            .setNegativeButton("关闭", null).show();
    }

    static void edit(Activity activity, SharedPreferences prefs, int port, Runnable changed) {
        EditText name = new EditText(activity);
        name.setSingleLine(true);
        name.setTextSize(20);
        name.setHint("例如：游戏机、机顶盒");
        name.setText(prefs.getString(InputNames.key(port), ""));
        name.setSelection(name.length());
        LinearLayout content = new LinearLayout(activity);
        content.setPadding(Ui.dp(activity, 24), Ui.dp(activity, 8), Ui.dp(activity, 24), 0);
        content.addView(name, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("HDMI" + port + " 名称")
            .setMessage("最多 8 个字符；留空使用端口名。仅修改本桌面的显示名称。")
            .setView(content).setPositiveButton("保存", null).setNegativeButton("取消", null)
            .setNeutralButton("恢复默认", (d, which) -> {
                prefs.edit().remove(InputNames.key(port)).apply();
                changed.run();
            }).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = InputNames.normalize(name.getText().toString());
            if (!InputNames.valid(value)) {
                name.setError("最多输入 8 个字符");
                name.requestFocus();
                return;
            }
            prefs.edit().putString(InputNames.key(port), value).apply();
            changed.run();
            dialog.dismiss();
        });
    }
}
