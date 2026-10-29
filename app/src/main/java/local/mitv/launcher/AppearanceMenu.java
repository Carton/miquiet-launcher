package local.mitv.launcher;

/** Rows in the full-screen editor: Back, palette, type, focus, news, weather, actions. */
final class AppearanceMenu {
    static final int COUNT = 12;
    private static final int[][] ROWS = {{0}, {1, 2, 3}, {4, 5}, {6, 7}, {8}, {9}, {10, 11}};
    private AppearanceMenu() {}

    static int move(int index, int dx, int dy) {
        if (index < 0 || index >= COUNT) return 1;
        if (Math.abs(dx) + Math.abs(dy) != 1) return index;
        for (int row = 0; row < ROWS.length; row++) {
            for (int column = 0; column < ROWS[row].length; column++) {
                if (ROWS[row][column] != index) continue;
                int nextRow = row + dy, nextColumn = column + dx;
                if (nextRow < 0 || nextRow >= ROWS.length || nextColumn < 0) return index;
                if (dy == 0 && nextColumn >= ROWS[row].length) return index;
                return ROWS[nextRow][Math.min(nextColumn, ROWS[nextRow].length - 1)];
            }
        }
        return index;
    }
}
