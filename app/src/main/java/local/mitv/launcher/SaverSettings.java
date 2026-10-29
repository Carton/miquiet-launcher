package local.mitv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;

final class SaverSettings {
    private final Activity activity;
    private final SharedPreferences prefs;
    SaverSettings(Activity activity, SharedPreferences prefs) { this.activity = activity; this.prefs = prefs; }
    void show() {
        boolean enabled = prefs.getBoolean("saver_enabled", true);
        int count = SaverImages.list(activity).size();
        String[] choices = {activity.getString(R.string.photo_library) + count + activity.getString(R.string.photo_capacity_suffix), activity.getString(R.string.preview_saver),
            activity.getString(R.string.idle_time) + SaverPolicy.waitMinutes(prefs.getInt("saver_wait_minutes", 12)) + activity.getString(R.string.min),
            activity.getString(R.string.slide_interval) + SaverPolicy.slideSeconds(prefs.getInt("saver_slide_seconds", 30)) + activity.getString(R.string.sec),
            enabled ? activity.getString(R.string.turn_off_local_screensaver) : activity.getString(R.string.turn_on_local_screensaver)};
        new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.screensaver_title_prefix) + (enabled ? activity.getString(R.string.state_enabled) : activity.getString(R.string.state_disabled)))
            .setItems(choices, (d, which) -> {
                if (which == 0) activity.startActivity(new Intent(activity,SaverLibraryActivity.class));
                else if (which == 1) activity.startActivity(new Intent(activity,ScreensaverActivity.class).putExtra(ScreensaverActivity.EXTRA_PREVIEW,true));
                else if (which == 2) chooseTime(true);
                else if (which == 3) chooseTime(false);
                else { prefs.edit().putBoolean("saver_enabled", !enabled).apply(); show(); }
            }).setNegativeButton(activity.getString(R.string.close), null)
            .setNeutralButton(activity.getString(R.string.help), (d,w) -> new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.local_screensaver))
                .setMessage(activity.getString(R.string.the_automatic_saver_starts_only_while_this))
                .setPositiveButton(activity.getString(R.string.close),null).show()).show();
    }
    private void chooseTime(boolean wait) {
        int[] values = wait ? new int[]{1,2,5,10,12,15,30,60} : new int[]{10,15,30,60,120,300};
        String[] labels = new String[values.length];
        int selected = 0, current = prefs.getInt(wait ? "saver_wait_minutes" : "saver_slide_seconds", wait ? 12 : 30);
        for (int i = 0; i < values.length; i++) { labels[i] = values[i] + (wait ? activity.getString(R.string.min) : activity.getString(R.string.sec)); if (values[i] == current) selected = i; }
        new AlertDialog.Builder(activity).setTitle(wait ? activity.getString(R.string.screensaver_idle_time) : activity.getString(R.string.slideshow_interval))
            .setSingleChoiceItems(labels, selected, (d,index) -> {
                prefs.edit().putInt(wait ? "saver_wait_minutes" : "saver_slide_seconds", values[index]).apply(); d.dismiss(); show();
            }).setNegativeButton(activity.getString(R.string.cancel),(d,w) -> show()).show();
    }
}
