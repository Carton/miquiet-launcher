package local.mitv.launcher;

/** Recent HDMI use changes only the first three rows; file/source actions stay at the bottom. */
final class SourceMenu {
    static final String RECENT = "hdmi_recent_order";
    static final int FILES = 3;
    static final String[] ICONS = {"hdmi", "hdmi", "hdmi", "folder", "arrow"};
    static final int[] PORTS = {1, 2, 3, 0, 0};
    private SourceMenu() {}

    static int[] order(String saved) {
        int[] ports = new int[5];
        boolean[] seen = new boolean[4];
        int count = 0;
        String value = (saved == null ? "" : saved) + "123";
        for (int i = 0; i < value.length() && count < 3; i++) {
            int port = value.charAt(i) - '0';
            if (port < 1 || port > 3 || seen[port]) continue;
            seen[port] = true; ports[count++] = port;
        }
        return ports;
    }

    static String used(String saved, int port) {
        int[] previous = order(saved);
        if (port < 1 || port > 3) return "" + previous[0] + previous[1] + previous[2];
        StringBuilder next = new StringBuilder().append(port);
        for (int value : previous) if (value > 0 && value != port) next.append(value);
        return next.toString();
    }
}
