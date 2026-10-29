package local.mitv.launcher;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class HomeAppearanceTest {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Appearance assertion " + checks);
    }

    public static void main(String[] args) {
        HomeAppearance defaults = HomeAppearance.DEFAULT;
        check(defaults.showNews && defaults.showWeather && !defaults.largeText);
        check(new HomeAppearance(-1, false, 20, true, true).equals(defaults));
        check(new HomeAppearance(10, false, -1, true, true).equals(defaults));
        check(defaults.hashCode() == new HomeAppearance(0, false, 0, true, true).hashCode());
        check(!defaults.equals(null) && !defaults.equals("appearance"));
        for (int background = 0; background < 3; background++) {
            for (int mask = 0; mask < 16; mask++) {
                boolean news = (mask & 1) != 0, weather = (mask & 2) != 0, large = (mask & 4) != 0;
                HomeAppearance choice = new HomeAppearance(background, large, (mask & 8) != 0 ? 1 : 0, news, weather);
                check(choice.previewX() + choice.previewWidth() == choice.inputsX());
                check((choice.previewWidth() - 8) * 9 == 324 * 16); // Keep the 16:9 viewport in both layouts.
                check(choice.heroX() * 2 + choice.heroWidth() == 960); // Center the complete source group.
                check(choice.inputsX() + 144 == choice.heroWidth());
                check(news ? choice.previewX() >= 200 : choice.previewX() == 0); // No invisible news gap.
                check(choice.networkX() + 38 <= (weather ? 696 : 846)); // No weather/clock overlap.
                check(weather || choice.networkX() + 38 >= 838); // Close the weather gap.
                check(choice.textSize(14) > 0 && choice.textSize(14) <= 16);
                check(large ? choice.textSize(14) > defaults.textSize(14) : choice.textSize(14) == defaults.textSize(14));
                check(choice.showNews == news && choice.showWeather == weather);
                check((choice.backgroundColor() >>> 24) == 255 && (choice.panelColor() >>> 24) == 255);
            }
        }
        check(new HomeAppearance(0, false, 1, true, true).focusStroke() != defaults.focusStroke());
        check(new HomeAppearance(1, false, 0, true, true).backgroundColor() != defaults.backgroundColor());
        check(new HomeAppearance(2, false, 0, true, true).backgroundColor() != defaults.backgroundColor());
        check(defaults.showNews && defaults.showWeather && defaults.background == 0); // Candidates never mutate saved/default choices.
        check(SourceMenu.PORTS[0] == 1 && SourceMenu.PORTS[1] == 2 && SourceMenu.PORTS[2] == 3);
        check(SourceMenu.PORTS[SourceMenu.FILES] == 0 && SourceMenu.ICONS[SourceMenu.FILES].equals("folder"));
        check(java.util.Arrays.equals(SourceMenu.order(null), new int[]{1,2,3,0,0}));
        check(java.util.Arrays.equals(SourceMenu.order("333x-21"), new int[]{3,2,1,0,0}));
        check(SourceMenu.used("", 2).equals("213"));
        check(SourceMenu.used("213", 3).equals("321"));
        check(SourceMenu.used("321", 3).equals("321"));
        check(SourceMenu.used("bad", 0).equals("123"));
        check(SourceMenu.used("321", 1).equals("132"));

        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int start = 0; start < AppearanceMenu.COUNT; start++) {
            Set<Integer> visited = new HashSet<>();
            ArrayDeque<Integer> pending = new ArrayDeque<>(); pending.add(start);
            while (!pending.isEmpty()) {
                int current = pending.remove();
                if (!visited.add(current)) continue;
                for (int[] direction : directions) {
                    int next = AppearanceMenu.move(current, direction[0], direction[1]);
                    check(next >= 0 && next < AppearanceMenu.COUNT);
                    if (!visited.contains(next)) pending.add(next);
                }
            }
            check(visited.size() == AppearanceMenu.COUNT); // Every option, Apply and Cancel stay reachable.
        }
        check(AppearanceMenu.move(1, -1, 0) == 1 && AppearanceMenu.move(3, 1, 0) == 3);
        check(AppearanceMenu.move(0, 0, -1) == 0 && AppearanceMenu.move(11, 0, 1) == 11);
        check(AppearanceMenu.move(8, 0, 1) == 9 && AppearanceMenu.move(9, 0, 1) == 10);
        check(AppearanceMenu.move(10, 1, 0) == 11 && AppearanceMenu.move(11, -1, 0) == 10);
        check(AppearanceMenu.move(-1, 1, 0) == 1 && AppearanceMenu.move(100, 0, 1) == 1);
        check(AppearanceMenu.move(1, 1, 1) == 1 && AppearanceMenu.move(1, 0, 0) == 1);
        System.out.println("PASS: " + checks + " appearance, visibility, layout and navigation assertions");
    }
}
