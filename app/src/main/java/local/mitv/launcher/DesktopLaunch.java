package local.mitv.launcher;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

final class DesktopLaunch {
    private DesktopLaunch() {}

    static void show(Context context) {
        Intent home = new Intent(context, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            // Android 6 delays direct background app switches after Home. Its system-mediated
            // PendingIntent path can resume this same, fixed activity without that five-second gate.
            PendingIntent.getActivity(context, 0, home,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE).send();
        } catch (PendingIntent.CanceledException e) {
            Log.w("QuietTV", "Desktop pending intent canceled; using direct launch", e);
            context.startActivity(home);
        }
    }
}
