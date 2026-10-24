package local.mitv.launcher;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Shared filesystem rules for a bounded local-photo browser. */
final class SaverBrowser {
    static final int LIMIT = 300;
    private SaverBrowser() {}
    static boolean inside(File root, File file) {
        try {
            String base = root.getCanonicalPath(), path = file.getCanonicalPath();
            return path.equals(base) || path.startsWith(base + File.separator);
        } catch (IOException error) { return false; }
    }
    static File[] entries(File root, File directory) throws IOException {
        if (!inside(root,directory)) throw new IOException("这个目录不在所选存储设备内");
        File[] entries = directory.listFiles(file -> !file.getName().startsWith(".") && file.canRead()
            && inside(root,file) && (file.isDirectory() || SaverPolicy.imageName(file.getName())));
        if (entries == null) throw new IOException("无法读取此文件夹，请重新连接存储设备或选择其他位置");
        Arrays.sort(entries,(a,b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        return entries;
    }
    static List<File> roots(File internal, File... parents) {
        List<File> roots = new ArrayList<>();
        addRoot(roots,internal);
        for (File parent : parents) {
            File[] entries = parent.listFiles();
            if (entries == null) continue;
            for (File device : entries) {
                if (device.getName().equals("emulated") || device.getName().equals("self")) continue;
                addRoot(roots,device);
            }
        }
        return roots;
    }
    static void addRoot(List<File> roots,File device) {
        if (!device.isDirectory() || !device.canRead()) return;
        for (File root : roots) if (inside(root,device) || inside(device,root)) return;
        roots.add(device);
    }
}
