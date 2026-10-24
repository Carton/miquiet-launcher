package local.mitv.launcher;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

/** A shell-only recovery entry; this app can disable its own component without root. */
public final class RestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        boolean enable = "local.mitv.launcher.ENABLE_HOME".equals(intent.getAction());
        if (!enable && !"local.mitv.launcher.RESTORE_FACTORY".equals(intent.getAction())) return;
        context.getSharedPreferences(Apps.PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("bridge_enabled", enable).putBoolean("bridge_paused", !enable)
            .putBoolean("boot_pending", false).commit();
        context.getPackageManager().setComponentEnabledSetting(
            new ComponentName(context, HomeBridgeService.class),
            enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP);
        if (enable) { setResultCode(1); return; }
        context.startActivity(new Intent(Intent.ACTION_MAIN)
            .setClassName("com.mitv.tvhome", "com.mitv.tvhome.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Log.i("QuietTV", "Factory launcher restored; own bridge disabled");
        setResultCode(1);
    }
}
