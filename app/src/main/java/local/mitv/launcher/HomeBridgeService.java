package local.mitv.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public final class HomeBridgeService extends AccessibilityService {
    private ForegroundMonitor foreground;
    @Override protected void onServiceConnected() {
        SharedPreferences prefs = getSharedPreferences(Apps.PREFS, MODE_PRIVATE);
        Log.i("QuietTV", "Home bridge bound at=" + SystemClock.elapsedRealtime()
            + " enabled=" + prefs.getBoolean("bridge_enabled", false)
            + " paused=" + prefs.getBoolean("bridge_paused", false)
            + " bootPending=" + prefs.getBoolean("boot_pending", false));
        android.accessibilityservice.AccessibilityServiceInfo info = getServiceInfo();
        Log.i("QuietTV", "Home service config flags=" + (info == null ? "null" : info.flags)
            + " packages=" + (info == null ? "null" : java.util.Arrays.toString(info.packageNames)));
        if (foreground != null) foreground.stop();
        foreground = new ForegroundMonitor(this);
        foreground.start();
        if (prefs.getBoolean("boot_pending", false)
                && prefs.getBoolean("bridge_enabled", false)
                && !prefs.getBoolean("bridge_paused", false)) {
            DesktopLaunch.show(this);
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        SharedPreferences prefs = getSharedPreferences(Apps.PREFS, MODE_PRIVATE);
        String pkg = event.getPackageName() == null ? null : event.getPackageName().toString();
        String window = event.getClassName() == null ? null : event.getClassName().toString();
        boolean enabled = prefs.getBoolean("bridge_enabled", false);
        boolean paused = prefs.getBoolean("bridge_paused", false);
        boolean redirect = LauncherPolicy.redirect(pkg, window, enabled, paused);
        Log.i("QuietTV", "Home event at=" + SystemClock.elapsedRealtime()
            + " package=" + pkg + " class=" + window + " enabled=" + enabled
            + " paused=" + paused + " redirect=" + redirect);
        if (!redirect) return;
        Log.i("QuietTV", "Home bridge redirect: " + window);
        DesktopLaunch.show(this);
    }

    @Override public void onInterrupt() { Log.i("QuietTV", "Home bridge interrupted"); }

    @Override public boolean onUnbind(Intent intent) {
        if (foreground != null) foreground.stop();
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        if (foreground != null) foreground.stop();
        super.onDestroy();
    }
}
