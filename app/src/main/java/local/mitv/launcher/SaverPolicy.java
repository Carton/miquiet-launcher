package local.mitv.launcher;

final class SaverPolicy {
    static final int DEFAULT_WAIT_MINUTES = 12;
    static final int MAX_IMAGES = 24;
    private SaverPolicy() {}
    static int waitMinutes(int value) { return value >= 1 && value <= 60 ? value : DEFAULT_WAIT_MINUTES; }
    static int slideSeconds(int value) { return value >= 10 && value <= 300 ? value : 30; }
    static boolean imageName(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
            || lower.endsWith(".webp") || lower.endsWith(".bmp");
    }
}
