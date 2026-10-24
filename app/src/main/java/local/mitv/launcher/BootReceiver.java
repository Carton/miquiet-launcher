package local.mitv.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/** Restore startup independently of accessibility window-event ordering. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        SharedPreferences prefs = context.getSharedPreferences(Apps.PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean("bridge_enabled", false) || prefs.getBoolean("bridge_paused", false)) return;
        prefs.edit().putBoolean("boot_pending", true).commit();
        Log.i("QuietTV", "Restoring launcher after boot");
        DesktopLaunch.show(context);
    }
}
