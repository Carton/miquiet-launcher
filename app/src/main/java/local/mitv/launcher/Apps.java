package local.mitv.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

final class Apps {
    static final String PREFS = "quiet_tv";
    static final List<String> DEFAULTS = Arrays.asList(
        "com.xiaodianshi.tv.yst", "com.cibn.tv", "com.huya.nftv",
        "com.dangbeimarket", "com.gitvdemo.video");

    static final class App {
        final String pkg;
        final String label;
        final Drawable icon;
        final Intent intent;
        App(String pkg, String label, Drawable icon, Intent intent) {
            this.pkg = pkg; this.label = label; this.icon = icon; this.intent = intent;
        }
    }

    final LinkedHashMap<String, App> all = new LinkedHashMap<>();
    private final Context context;
    private final SharedPreferences prefs;

    Apps(Context context) {
        this.context = context;
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        reload();
    }

    void reload() {
        all.clear();
        PackageManager pm = context.getPackageManager();
        List<App> result = new ArrayList<>();
        for (String category : Arrays.asList(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            for (ResolveInfo info : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String pkg = info.activityInfo.packageName;
                if (all.containsKey(pkg) || pkg.equals(context.getPackageName()) || pkg.equals("com.mitv.tvhome")) continue;
                if (!info.activityInfo.exported || !info.activityInfo.enabled) continue;
                Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category)
                    .setClassName(pkg, info.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                App app = new App(pkg, info.loadLabel(pm).toString(), info.loadIcon(pm), intent);
                all.put(pkg, app);
                result.add(app);
            }
        }
        Collator collator = Collator.getInstance(UiLocale.locale(context));
        Collections.sort(result, (a, b) -> collator.compare(a.label, b.label));
        all.clear();
        for (App app : result) all.put(app.pkg, app);
    }

    List<String> favorites() {
        return LauncherPolicy.favorites(prefs.getString("favorites", null), DEFAULTS, new ArrayList<>(all.keySet()));
    }

    void save(List<String> selected) {
        prefs.edit().putString("favorites", TextUtils.join(",", selected)).apply();
    }
}
