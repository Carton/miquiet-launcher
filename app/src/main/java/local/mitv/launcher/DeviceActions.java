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
            Toast.makeText(activity, activity.getString(R.string.this_shortcut_is_unavailable_please_use_tv), Toast.LENGTH_SHORT).show();
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
        // This firmware declares BROWSE_DEVICE_LIST but never handles it. WITH_PATH selects tab 0.
        start(new Intent("duokan.mediaexplorer.intent.action.BROWSE_WITH_PATH")
            .setClassName("com.xiaomi.mitv.mediaexplorer", "com.xiaomi.mitv.mediaexplorer.NewScraperMainEntryActivity")
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    void accessibility() {
        Intent system = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        if (system.resolveActivity(activity.getPackageManager()) != null) start(system);
        else new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.first_time_takeover_setup))
            .setMessage(activity.getString(R.string.this_tv_has_no_accessibility_settings_page))
            .setPositiveButton(activity.getString(R.string.ok), null).show();
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
