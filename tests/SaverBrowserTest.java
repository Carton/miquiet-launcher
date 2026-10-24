package local.mitv.launcher;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SaverBrowserTest {
    private static int assertions;
    private static void check(boolean value) { assertions++; if (!value) throw new AssertionError("Browser assertion " + assertions); }
    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("quiet-saver-browser-").toRealPath();
        try {
            File root = Files.createDirectory(base.resolve("photos")).toFile();
            File sibling = Files.createDirectory(base.resolve("photos-other")).toFile();
            File folder = Files.createDirectory(root.toPath().resolve("Folder")).toFile();
            Files.createFile(root.toPath().resolve("z.jpg")); Files.createFile(root.toPath().resolve("a.PNG"));
            Files.createFile(root.toPath().resolve(".hidden.jpg")); Files.createFile(root.toPath().resolve("notes.txt"));
            File[] entries = SaverBrowser.entries(root,root);
            check(entries.length == 3); check(entries[0].isDirectory());
            check(entries[1].getName().equals("a.PNG") && entries[2].getName().equals("z.jpg"));
            check(SaverBrowser.inside(root,folder)); check(!SaverBrowser.inside(root,sibling));
            check(!SaverBrowser.inside(root,new File(root,"../photos-other")));
            check(SaverBrowser.inside(root,new File(folder,"..")));
            try { SaverBrowser.entries(root,sibling); throw new AssertionError("Outside directory accepted"); }
            catch (java.io.IOException expected) { assertions++; }
            check(SaverBrowser.entries(root,folder).length == 0);
            List<File> roots = SaverBrowser.roots(root,base.toFile());
            check(roots.size() == 2);
            SaverBrowser.addRoot(roots,folder); check(roots.size() == 2);
            SaverBrowser.addRoot(roots,new File(root,".")); check(roots.size() == 2);
            System.out.println("PASS: " + assertions + " local photo browser assertions");
        } finally {
            // Only remove this exact newly-created temporary tree; never follow symbolic links.
            Path temp = new File(System.getProperty("java.io.tmpdir")).getCanonicalFile().toPath();
            if (!base.getParent().equals(temp) || !base.getFileName().toString().startsWith("quiet-saver-browser-")) throw new AssertionError("Unexpected cleanup path");
            Files.walkFileTree(base,new java.nio.file.SimpleFileVisitor<Path>() {
                @Override public java.nio.file.FileVisitResult visitFile(Path path,java.nio.file.attribute.BasicFileAttributes attrs) throws java.io.IOException {
                    Files.delete(path); return java.nio.file.FileVisitResult.CONTINUE;
                }
                @Override public java.nio.file.FileVisitResult postVisitDirectory(Path path,java.io.IOException error) throws java.io.IOException {
                    if (error != null) throw error; Files.delete(path); return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
