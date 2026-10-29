package local.mitv.launcher;

/** Shared menu order and non-wrapping remote movement, independent of Android focus search. */
final class SettingsMenu {
    static final int COLUMNS = 4;
    static final int COUNT = 12;
    private SettingsMenu() {}

    static int move(int index, int dx, int dy) {
        if (index < 0 || index >= COUNT) return 0;
        if (Math.abs(dx) + Math.abs(dy) != 1) return index;
        int row = index / COLUMNS + dy, column = index % COLUMNS + dx;
        int next = row * COLUMNS + column;
        return row >= 0 && column >= 0 && column < COLUMNS && next < COUNT ? next : index;
    }
}
