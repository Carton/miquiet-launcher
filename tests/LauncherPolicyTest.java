package local.mitv.launcher;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public final class LauncherPolicyTest {
    private static int checks;

    private static void equal(Object actual, Object expected) {
        checks++;
        if (!actual.equals(expected)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        List<String> installed = Arrays.asList("a", "b", "c");
        equal(LauncherPolicy.favorites(null, Arrays.asList("b", "a"), installed), Arrays.asList("b", "a"));
        equal(LauncherPolicy.favorites("", Arrays.asList("b", "a"), installed), Collections.emptyList());
        equal(LauncherPolicy.favorites("c,a,c,missing,b", Collections.emptyList(), installed), Arrays.asList("c", "a", "b"));
        equal(LauncherPolicy.favorites("missing", Collections.emptyList(), installed), Collections.emptyList());
        equal(LauncherPolicy.move(Arrays.asList("a", "b", "c"), "b", -1), Arrays.asList("b", "a", "c"));
        equal(LauncherPolicy.move(Arrays.asList("a", "b", "c"), "a", -1), Arrays.asList("a", "b", "c"));
        equal(LauncherPolicy.move(Arrays.asList("a", "b", "c"), "b", 1), Arrays.asList("a", "c", "b"));
        equal(LauncherPolicy.redirect("com.mitv.tvhome", "com.mitv.tvhome.MainActivityUserMode", true, false), true);
        equal(LauncherPolicy.redirect("com.mitv.tvhome", "com.mitv.tvhome.MainActivity", true, false), true);
        equal(LauncherPolicy.redirect("com.mitv.tvhome", "com.mitv.tvhome.SearchActivity", true, false), false);
        equal(LauncherPolicy.redirect("com.mitv.tvhome", "android.app.Dialog", true, false), false);
        equal(LauncherPolicy.redirect("com.xiaomi.mitv.tvplayer", "com.mitv.tvhome.MainActivityUserMode", true, false), false);
        equal(LauncherPolicy.redirect("com.mitv.tvhome", "com.mitv.tvhome.MainActivityUserMode", false, false), false);
        equal(LauncherPolicy.redirect("com.mitv.tvhome", "com.mitv.tvhome.MainActivityUserMode", true, true), false);
        equal(LauncherPolicy.redirect(null, null, true, false), false);
        equal(new HashSet<>(installed).size(), 3);
        ForegroundState foreground = new ForegroundState();
        foreground.accept(100, 1, "com.mitv.tvhome", "com.mitv.tvhome.MainActivityUserMode");
        equal(foreground.isFactoryHome(), true);
        equal(foreground.foregroundTimestamp(), 100L);
        foreground.accept(110, 1, "video.app", "video.app.Player");
        foreground.accept(100, 1, "com.mitv.tvhome", "com.mitv.tvhome.MainActivityUserMode");
        equal(foreground.isFactoryHome(), false);
        foreground.accept(111, 2, "com.mitv.tvhome", "com.mitv.tvhome.MainActivityUserMode");
        equal(foreground.packageName(), "video.app");
        foreground.accept(120, 1, "com.mitv.tvhome", "com.mitv.tvhome.SearchActivity");
        equal(foreground.isFactoryHome(), false);
        foreground.accept(130, 1, "com.mitv.tvhome", "com.mitv.tvhome.MainActivityUserMode");
        foreground.accept(131, 2, "com.mitv.tvhome", "com.mitv.tvhome.MainActivityUserMode");
        equal(foreground.isFactoryHome(), false);
        foreground.accept(140, 1, "com.mitv.tvhome", "com.mitv.tvhome.MainActivity");
        equal(foreground.isFactoryHome(), true);
        foreground.accept(150, 1, "com.xiaomi.mitv.settings", "Settings");
        equal(foreground.isFactoryHome(), false);
        foreground.accept(200, 1, "com.mitv.tvhome", "com.mitv.tvhome.MainActivityUserMode");
        foreground.accept(201, 2, "com.mitv.tvhome", "com.mitv.tvhome.SearchActivity");
        equal(foreground.isFactoryHome(), true);
        foreground.reset();
        equal(foreground.isFactoryHome(), false);
        System.out.println("PASS: " + checks + " launcher policy assertions");
    }
}
