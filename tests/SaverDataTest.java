package local.mitv.launcher;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

/** Uses only a newly-created shell-owned directory; never reads the user's imported photos. */
public final class SaverDataTest {
    private static int assertions;
    private static void check(boolean value) { assertions++; if (!value) throw new AssertionError("Saver data assertion " + assertions); }
    public static void main(String[] args) {
        try { run(); }
        catch (Throwable error) { error.printStackTrace(); System.exit(1); }
    }
    private static void run() throws Exception {
        final File dir = new File("/data/local/tmp/quiet-saver-test-" + System.nanoTime());
        if (!dir.mkdir()) throw new java.io.IOException("Cannot create isolated test directory");
        Context context = new ContextWrapper(null) { @Override public File getFilesDir() { return dir; } };
        try {
            check(SaverImages.list(context).isEmpty());
            try { SaverImages.add(context,new ByteArrayInputStream("invalid image".getBytes("UTF-8"))); throw new AssertionError("Accepted invalid image"); }
            catch (java.io.IOException expected) { assertions++; }
            check(SaverImages.list(context).isEmpty());
            check(SaverImages.directory(context).list().length == 0);
            Bitmap fixture = Bitmap.createBitmap(64,32,Bitmap.Config.ARGB_8888); fixture.eraseColor(Color.GREEN);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); fixture.compress(Bitmap.CompressFormat.PNG,100,bytes); fixture.recycle();
            byte[] png = bytes.toByteArray();
            SaverImages.add(context,new ByteArrayInputStream(png));
            check(SaverImages.list(context).size() == 1);
            File photo = SaverImages.list(context).get(0);
            Bitmap decoded = SaverImages.decode(photo,16,16);
            check(decoded != null && decoded.getWidth() <= 16 && decoded.getHeight() <= 16);
            check(decoded.getPixel(0,0) == Color.GREEN); decoded.recycle();
            for (int i = 1; i < SaverPolicy.MAX_IMAGES; i++) SaverImages.add(context,new ByteArrayInputStream(png));
            check(SaverImages.list(context).size() == 24);
            try { SaverImages.add(context,new ByteArrayInputStream(png)); throw new AssertionError("Exceeded image limit"); }
            catch (java.io.IOException expected) { assertions++; }
            check(SaverImages.list(context).size() == 24);
            System.out.println("PASS: " + assertions + " Android screensaver image assertions");
        } finally {
            File[] ownFiles = SaverImages.directory(context).listFiles();
            if (ownFiles != null) for (File file : ownFiles) file.delete();
            SaverImages.directory(context).delete(); dir.delete();
        }
    }
}
