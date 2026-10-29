package local.mitv.launcher;

import java.util.ArrayList;
import java.util.List;

/** A local reorder draft. Only the caller's explicit save may persist its result. */
final class FavoriteMove {
    final String item;
    private final List<String> original;
    private List<String> draft;
    private List<String> result;

    FavoriteMove(List<String> current, String item) {
        if (!current.contains(item)) throw new IllegalArgumentException("Missing favorite");
        this.item = item;
        original = new ArrayList<>(current);
        draft = new ArrayList<>(current);
    }

    List<String> current() { return new ArrayList<>(result == null ? draft : result); }

    boolean move(int direction) {
        if (result != null || (direction != -1 && direction != 1)) return false;
        List<String> next = LauncherPolicy.move(draft, item, direction);
        if (next.equals(draft)) return false;
        draft = next;
        return true;
    }

    List<String> finish(boolean save) {
        if (result == null) result = new ArrayList<>(save ? draft : original);
        return current();
    }
}
