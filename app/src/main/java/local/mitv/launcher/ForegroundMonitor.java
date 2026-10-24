package local.mitv.launcher;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

/** A narrow fallback for the factory launcher, which sometimes emits no window events. */
final class ForegroundMonitor implements Runnable {
    private final Context context;
    private final SharedPreferences prefs;
    private final HandlerThread worker = new HandlerThread("QuietTVHomeMonitor");
    private Handler handler;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ForegroundState state = new ForegroundState();
    private final UsageStatsManager usage;
    private final AppOpsManager appOps;
    private volatile boolean active;
    private long handledTimestamp = -1;
    private long previousPoll;
    private boolean warned;

    ForegroundMonitor(Context context) {
        this.context = context;
        prefs = context.getSharedPreferences(Apps.PREFS, Context.MODE_PRIVATE);
        usage = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
    }

    void start() {
        active = true;
        worker.start();
        handler = new Handler(worker.getLooper());
        handler.post(this);
    }
    void stop() {
        active = false;
        if (handler != null) handler.removeCallbacks(this);
        main.removeCallbacksAndMessages(null);
        worker.quitSafely();
    }

    @Override public void run() {
        if (!active) return;
        try {
            if (!prefs.getBoolean("bridge_enabled", false) || prefs.getBoolean("bridge_paused", false)) {
                state.reset(); handledTimestamp = -1;
                return;
            }
            if (usage == null || appOps == null || appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName()) != AppOpsManager.MODE_ALLOWED) {
                if (!warned) Log.i("QuietTV", "Foreground fallback awaits usage access");
                warned = true;
                return;
            }
            warned = false;
            long now = System.currentTimeMillis();
            if (now < previousPoll - 1000) { state.reset(); handledTimestamp = -1; }
            previousPoll = now;
            // Overlap tolerates delayed delivery without accumulating application history.
            UsageEvents events = usage.queryEvents(now - 3000, now + 1);
            if (events == null) return;
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                state.accept(event.getTimeStamp(), event.getEventType(), event.getPackageName(), event.getClassName());
            }
            if (state.isFactoryHome() && state.foregroundTimestamp() != handledTimestamp) {
                handledTimestamp = state.foregroundTimestamp();
                final long eventTime = handledTimestamp;
                // Serialize the final decision with the UI's pause/restore actions.
                main.post(() -> {
                    if (!active || !prefs.getBoolean("bridge_enabled", false)
                            || prefs.getBoolean("bridge_paused", false)) return;
                    Log.i("QuietTV", "Foreground Home fallback at=" + System.currentTimeMillis() + " event=" + eventTime);
                    DesktopLaunch.show(context);
                });
            }
        } catch (RuntimeException e) {
            if (!warned) Log.w("QuietTV", "Foreground fallback unavailable", e);
            warned = true;
        } finally {
            if (active) handler.postDelayed(this, 400);
        }
    }
}
