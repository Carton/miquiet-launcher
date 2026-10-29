package local.mitv.launcher;

import android.content.SharedPreferences;
import java.util.Map;

final class AppearanceStore {
    private AppearanceStore() {}

    static HomeAppearance read(SharedPreferences prefs) {
        return new HomeAppearance(prefs.getInt("home_background", 0), prefs.getBoolean("home_large_text", false),
            prefs.getInt("home_focus", 0), prefs.getBoolean(HomeAppearance.SHOW_NEWS, true),
            prefs.getBoolean(HomeAppearance.SHOW_WEATHER, true));
    }

    static boolean save(SharedPreferences prefs, HomeAppearance value) {
        Map<String, ?> previous = prefs.getAll();
        boolean saved = prefs.edit().putInt("home_background", value.background).putBoolean("home_large_text", value.largeText)
            .putInt("home_focus", value.focus).putBoolean(HomeAppearance.SHOW_NEWS, value.showNews)
            .putBoolean(HomeAppearance.SHOW_WEATHER, value.showWeather).commit();
        if (!saved) {
            // Android updates its in-memory map even if commit fails. Back must still cancel.
            SharedPreferences.Editor restore = prefs.edit();
            for (String key : new String[]{"home_background", "home_large_text", "home_focus", HomeAppearance.SHOW_NEWS, HomeAppearance.SHOW_WEATHER}) {
                Object old = previous.get(key);
                if (old instanceof Integer) restore.putInt(key, (Integer) old);
                else if (old instanceof Boolean) restore.putBoolean(key, (Boolean) old);
                else restore.remove(key);
            }
            restore.apply();
        }
        return saved;
    }
}
