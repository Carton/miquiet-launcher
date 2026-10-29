package local.mitv.launcher;

/** User labels are local presentation only; the physical port always remains visible. */
final class InputNames {
    static final int MAX_LENGTH = 8;
    private InputNames() {}

    static String key(int port) {
        if (port < 1 || port > 3) throw new IllegalArgumentException("Invalid HDMI port");
        return "input_name_" + port;
    }

    static String normalize(String value) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder();
        boolean space = false;
        for (int offset = 0; offset < value.length();) {
            int point = value.codePointAt(offset);
            offset += Character.charCount(point);
            if (Character.isWhitespace(point) || Character.isSpaceChar(point) || Character.isISOControl(point)) {
                space = result.length() > 0;
            } else {
                if (space) result.append(' ');
                result.appendCodePoint(point);
                space = false;
            }
        }
        return result.toString();
    }

    static boolean valid(String value) {
        String name = normalize(value);
        return name.codePointCount(0, name.length()) <= MAX_LENGTH;
    }

    static String display(int port, String value) {
        key(port);
        String name = normalize(value);
        return name.isEmpty() ? "HDMI" + port : name + " · HDMI" + port;
    }
}
