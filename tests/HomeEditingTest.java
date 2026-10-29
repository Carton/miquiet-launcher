package local.mitv.launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class HomeEditingTest {
    private static int checks;
    private static void equal(Object actual, Object expected) {
        checks++;
        if (!actual.equals(expected)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        List<String> saved = new ArrayList<>(Arrays.asList("a", "b", "c", "d"));
        FavoriteMove move = new FavoriteMove(saved, "b");
        equal(move.move(1), true);
        equal(move.move(1), true);
        equal(move.current(), Arrays.asList("a", "c", "d", "b"));
        equal(move.move(1), false); // Stop at the edge; never wrap into fixed shortcuts.
        equal(move.move(-1), true);
        equal(saved, Arrays.asList("a", "b", "c", "d")); // No save while moving.
        equal(move.finish(false), saved); // Back / leaving the desktop cancels the whole draft.
        equal(move.move(-1), false);
        equal(move.finish(true), saved); // A late confirm cannot revive a cancelled edit.

        FavoriteMove committed = new FavoriteMove(saved, "c");
        equal(committed.move(-1), true);
        equal(committed.move(-1), true);
        equal(committed.move(-1), false);
        equal(committed.move(0), false);
        equal(committed.move(2), false);
        List<String> result = committed.finish(true);
        equal(result, Arrays.asList("c", "a", "b", "d"));
        result.clear();
        equal(committed.current(), Arrays.asList("c", "a", "b", "d"));
        equal(saved, Arrays.asList("a", "b", "c", "d"));
        List<String> snapshot = committed.current();
        snapshot.clear();
        equal(committed.finish(false), Arrays.asList("c", "a", "b", "d"));
        FavoriteMove single = new FavoriteMove(Collections.singletonList("a"), "a");
        equal(single.move(1), false);
        equal(single.move(-1), false);
        equal(single.finish(true), Collections.singletonList("a"));
        try {
            new FavoriteMove(saved, "missing");
            throw new AssertionError("Missing favorite accepted");
        } catch (IllegalArgumentException expected) { checks++; }
        FavoriteMove isolated = new FavoriteMove(saved, "b");
        saved.clear();
        equal(isolated.finish(false), Arrays.asList("a", "b", "c", "d"));

        equal(InputNames.display(1, null), "HDMI1");
        equal(InputNames.display(3, "   "), "HDMI3");
        equal(InputNames.display(2, " 游戏机 "), "游戏机 · HDMI2");
        equal(InputNames.normalize(" \tApple\n TV\u0000 "), "Apple TV");
        equal(InputNames.normalize("\u3000机顶盒\u3000"), "机顶盒");
        equal(InputNames.valid(""), true); // Clearing restores the physical port name.
        equal(InputNames.valid("12345678"), true);
        equal(InputNames.valid("123456789"), false);
        equal(InputNames.valid("😀😀😀😀😀😀😀😀"), true);
        equal(InputNames.valid("😀😀😀😀😀😀😀😀😀"), false);
        equal(InputNames.key(1).equals(InputNames.key(2)), false);
        for (int invalid : new int[]{0, 4, -1}) {
            try {
                InputNames.display(invalid, "游戏机");
                throw new AssertionError("Invalid port accepted");
            } catch (IllegalArgumentException expected) { checks++; }
        }
        System.out.println("PASS: " + checks + " home editing assertions");
    }
}
