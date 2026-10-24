package local.mitv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

final class DeviceActions {
    private final Activity activity;
    DeviceActions(Activity activity) { this.activity = activity; }

    boolean start(Intent intent) {
        try {
            activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException e) {
            Log.w("QuietTV", "Cannot launch " + intent, e);
            Toast.makeText(activity, "暂时无法打开该入口，请使用电视设置", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    void settings() {
        start(new Intent().setClassName("com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.entry.MainActivity"));
    }

    void panel(String action) {
        try {
            activity.sendBroadcast(new Intent("com.xiaomi.mitv.settings." + action)
                .setComponent(new ComponentName("com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.SettingsBroadcastReceiver")));
        } catch (RuntimeException e) {
            Log.w("QuietTV", "Cannot open settings panel " + action, e);
            settings();
        }
    }

    void sources() { panel("INPUTSOURCE_POPUP"); }

    void media() {
        Intent intent = activity.getPackageManager().getLaunchIntentForPackage("com.xiaomi.mitv.mediaexplorer");
        if (intent != null) start(intent);
        else Toast.makeText(activity, "文件管理未安装", Toast.LENGTH_SHORT).show();
    }

    void accessibility() {
        Intent system = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        if (system.resolveActivity(activity.getPackageManager()) != null) start(system);
        else new AlertDialog.Builder(activity).setTitle("首次接管设置")
            .setMessage("这台电视未提供无障碍设置页。首次接管需要在电脑端完成；之后可在此暂停、恢复或返回原厂桌面。")
            .setPositiveButton("知道了", null).show();
    }

    boolean factory() {
        return start(new Intent(Intent.ACTION_MAIN)
            .setClassName("com.mitv.tvhome", "com.mitv.tvhome.MainActivity"));
    }

    void fullscreen(int port) {
        if (port < 1 || port > 3) { sources(); return; }
        start(new Intent("com.xiaomi.mitv.tvplayer.EXTSRC_PLAY")
            .setClassName("com.xiaomi.mitv.tvplayer", "com.xiaomi.mitv.tvplayer.ExternalSourceActivity")
            .putExtra("input", 22 + port).putExtra("no_launch_panel", true));
    }
}
