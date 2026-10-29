package local.mitv.launcher;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/** Exercises the real preferences adapter on the JVM; Android interfaces need no device. */
public final class AppearanceStoreTest {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Appearance store assertion " + checks);
    }

    private static final class Memory {
        final Map<String, Object> values = new HashMap<>();
        boolean failCommit;
        int commits;
        final SharedPreferences prefs = (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
            new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getAll": return new HashMap<>(values);
                    case "getInt": case "getBoolean": return values.containsKey(args[0]) ? values.get(args[0]) : args[1];
                    case "edit": return editor();
                    default: throw new AssertionError("Unexpected read: " + method.getName());
                }
            });

        Object editor() {
            Map<String, Object> pending = new HashMap<>();
            return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(), new Class<?>[]{SharedPreferences.Editor.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "putInt": case "putBoolean": pending.put((String) args[0], args[1]); return proxy;
                        case "remove": pending.put((String) args[0], null); return proxy;
                        case "commit": case "apply":
                            pending.forEach((key, value) -> { if (value == null) values.remove(key); else values.put(key, value); });
                            if (method.getName().equals("apply")) return null;
                            commits++;
                            return !failCommit;
                        default: throw new AssertionError("Unexpected write: " + method.getName());
                    }
                });
        }
    }

    public static void main(String[] args) {
        Memory memory = new Memory();
        memory.values.put("favorites", "a,b"); memory.values.put("input_name_1", "游戏机");
        memory.values.put("saver_enabled", false); memory.values.put("weather_manual", true);
        Map<String, Object> existing = new HashMap<>(memory.values);
        check(AppearanceStore.read(memory.prefs).equals(HomeAppearance.DEFAULT));
        HomeAppearance candidate = new HomeAppearance(2, true, 1, false, false);
        check(memory.commits == 0 && memory.values.equals(existing)); // Merely creating a preview writes nothing.
        check(AppearanceStore.save(memory.prefs, candidate));
        check(memory.commits == 1 && AppearanceStore.read(memory.prefs).equals(candidate)); // Reopening retains all choices.
        existing.forEach((key, value) -> check(value.equals(memory.values.get(key))));
        check(AppearanceStore.save(memory.prefs, HomeAppearance.DEFAULT));
        check(AppearanceStore.read(memory.prefs).showNews && AppearanceStore.read(memory.prefs).showWeather);
        memory.values.put("home_background", 99); memory.values.put("home_focus", -1);
        check(AppearanceStore.read(memory.prefs).equals(HomeAppearance.DEFAULT));
        Map<String, Object> beforeFailure = new HashMap<>(memory.values);
        memory.failCommit = true;
        check(!AppearanceStore.save(memory.prefs, candidate));
        check(memory.values.equals(beforeFailure)); // A failed commit must not apply the candidate in memory.
        Memory firstRun = new Memory(); firstRun.failCommit = true;
        check(!AppearanceStore.save(firstRun.prefs, candidate));
        check(firstRun.values.isEmpty() && AppearanceStore.read(firstRun.prefs).equals(HomeAppearance.DEFAULT));
        System.out.println("PASS: " + checks + " appearance preferences assertions");
    }
}
