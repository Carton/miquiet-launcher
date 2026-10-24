package local.mitv.launcher;

/** Reduces ordered Android foreground/background events; retains only the current page. */
public final class ForegroundState {
    private long latest = -1;
    private long foreground = -1;
    private String pkg;
    private String page;

    public void accept(long timestamp, int type, String packageName, String className) {
        if (timestamp < latest || (type != 1 && type != 2)) return;
        latest = timestamp;
        if (type == 1) {
            pkg = packageName;
            page = className;
            foreground = timestamp;
        } else if (packageName != null && packageName.equals(pkg)
                && (className == null || className.equals(page))) {
            pkg = null;
            page = null;
        }
    }

    public boolean isFactoryHome() { return LauncherPolicy.redirect(pkg, page, true, false); }
    public long foregroundTimestamp() { return foreground; }
    public String packageName() { return pkg == null ? "" : pkg; }
    public void reset() { latest = -1; foreground = -1; pkg = null; page = null; }
}
