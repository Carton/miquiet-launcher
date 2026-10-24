package local.mitv.launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

public final class LauncherPolicy {
    private LauncherPolicy() {}

    public static List<String> favorites(String saved, List<String> defaults, List<String> installed) {
        List<String> requested = saved == null ? defaults : Arrays.asList(saved.split(","));
        HashSet<String> available = new HashSet<>(installed);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String item : requested) if (available.contains(item)) result.add(item);
        return new ArrayList<>(result);
    }

    public static List<String> move(List<String> current, String item, int direction) {
        ArrayList<String> result = new ArrayList<>(current);
        int index = result.indexOf(item);
        int next = index + direction;
        if (index >= 0 && next >= 0 && next < result.size()) {
            result.remove(index);
            result.add(next, item);
        }
        return result;
    }

    public static boolean redirect(String pkg, String window, boolean enabled, boolean paused) {
        return enabled && !paused && "com.mitv.tvhome".equals(pkg)
            && ("com.mitv.tvhome.MainActivityUserMode".equals(window)
                || "com.mitv.tvhome.MainActivity".equals(window));
    }
}
