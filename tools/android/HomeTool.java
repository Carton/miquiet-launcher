package local.mitv.tools;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;

/** A narrowly scoped shell helper for this TV's native Home configuration. */
public final class HomeTool {
    public static void main(String[] args) throws Exception {
        if (!"MiTV4-ANSM0".equals(Build.MODEL)) throw new IllegalStateException("Unexpected TV model: " + Build.MODEL);
        if (args.length != 1 || !"inspect".equals(args[0])) throw new IllegalArgumentException("Expected inspect");
        Looper.prepareMainLooper();
        Class<?> threadClass = Class.forName("android.app.ActivityThread");
        Object thread = threadClass.getMethod("systemMain").invoke(null);
        Context context = (Context) threadClass.getMethod("getSystemContext").invoke(thread);
        PackageManager pm = context.getPackageManager();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo candidate : pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)) {
            ComponentName component = new ComponentName(candidate.activityInfo.packageName, candidate.activityInfo.name);
            System.out.println("CANDIDATE " + component.flattenToString() + " priority=" + candidate.priority
                + " match=" + candidate.match + " permission=" + candidate.activityInfo.permission);
        }
        ResolveInfo resolved = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        System.out.println("RESOLVED " + (resolved == null ? "null" : resolved.activityInfo.packageName + "/" + resolved.activityInfo.name));
        List<ResolveInfo> homes = new ArrayList<>();
        Object preferred = pm.getClass().getMethod("getHomeActivities", List.class).invoke(pm, homes);
        System.out.println("PREFERRED " + preferred);
        System.out.println("HOME_CANDIDATES " + homes.size());
    }
}
