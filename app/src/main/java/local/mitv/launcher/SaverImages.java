package local.mitv.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

final class SaverImages {
    private SaverImages() {}
    static File directory(Context context) { return new File(context.getFilesDir(), "screensaver"); }
    static List<File> list(Context context) {
        File[] entries = directory(context).listFiles((dir, name) -> name.endsWith(".img"));
        if (entries == null) return new ArrayList<>();
        Arrays.sort(entries, (a,b) -> a.getName().compareTo(b.getName()));
        return new ArrayList<>(Arrays.asList(entries));
    }

    /** Copy into app-private storage so removing a USB drive cannot break a saved slideshow. */
    static synchronized void add(Context context, InputStream source) throws Exception {
        if (list(context).size() >= SaverPolicy.MAX_IMAGES) throw new PhotoFailure(PhotoFailure.Reason.LIMIT);
        File dir = directory(context);
        if (!dir.isDirectory() && !dir.mkdirs()) throw new PhotoFailure(PhotoFailure.Reason.DIRECTORY);
        File target = new File(dir, System.currentTimeMillis() + "-" + UUID.randomUUID() + ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[16384]; int count, total = 0;
                while ((count = source.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
                    total += count;
                    if (total > 32 * 1024 * 1024) throw new PhotoFailure(PhotoFailure.Reason.TOO_LARGE);
                    output.write(buffer, 0, count);
                }
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(target.getPath(), bounds);
            if (bounds.outWidth < 1 || bounds.outHeight < 1 || bounds.outWidth > 50000 || bounds.outHeight > 50000) {
                throw new PhotoFailure(PhotoFailure.Reason.INVALID_IMAGE);
            }
            // A header alone is insufficient: reject images that cannot produce pixels.
            Bitmap probe = decode(target,256,256);
            if (probe == null) throw new PhotoFailure(PhotoFailure.Reason.UNSUPPORTED_IMAGE);
            probe.recycle();
            File ready = new File(dir, target.getName().replace(".tmp", ".img"));
            if (!target.renameTo(ready)) throw new PhotoFailure(PhotoFailure.Reason.SAVE_FAILED);
        } finally { if (target.exists()) target.delete(); }
    }

    static Bitmap decode(File file, int width, int height) {
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), options);
        int target = Math.max(1, Math.min(2048, Math.max(width, height)));
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > target) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getPath(), options);
        if (bitmap == null) return null;
        try {
            int orientation = new ExifInterface(file.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case 2: matrix.setScale(-1, 1); break;
                case 3: matrix.setRotate(180); break;
                case 4: matrix.setScale(1, -1); break;
                case 5: matrix.setRotate(90); matrix.postScale(-1, 1); break;
                case 6: matrix.setRotate(90); break;
                case 7: matrix.setRotate(270); matrix.postScale(-1, 1); break;
                case 8: matrix.setRotate(270); break;
                default: return bitmap;
            }
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) bitmap.recycle();
            return rotated;
        } catch (java.io.IOException ignored) { return bitmap; }
    }
}
