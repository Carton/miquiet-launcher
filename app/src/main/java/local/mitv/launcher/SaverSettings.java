package local.mitv.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class SaverSettings {
    static final int READ_IMAGES = 112, PICK_IMAGES = 113;
    private final Activity activity;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean closed;

    SaverSettings(Activity activity, SharedPreferences prefs) { this.activity = activity; this.prefs = prefs; }
    void show() {
        boolean enabled = prefs.getBoolean("saver_enabled", true);
        int count = SaverImages.list(activity).size();
        String[] choices = {"预览本地屏保", "等待时间：" + SaverPolicy.waitMinutes(prefs.getInt("saver_wait_minutes", 12)) + " 分钟",
            "图片间隔：" + SaverPolicy.slideSeconds(prefs.getInt("saver_slide_seconds", 30)) + " 秒",
            "从电视 / U盘添加图片", "使用图片选择器添加", "管理图片（" + count + "/24）",
            enabled ? "关闭本地屏保" : "开启本地屏保"};
        new AlertDialog.Builder(activity).setTitle("本地屏保 · " + (enabled ? "已开启" : "已关闭"))
            .setItems(choices, (d, which) -> {
                if (which == 0) activity.startActivity(new Intent(activity, ScreensaverActivity.class));
                else if (which == 1) chooseTime(true);
                else if (which == 2) chooseTime(false);
                else if (which == 3) browse();
                else if (which == 4) pick();
                else if (which == 5) manage();
                else prefs.edit().putBoolean("saver_enabled", !enabled).apply();
            }).setNegativeButton("关闭", null)
            .setNeutralButton("说明", (d,w) -> new AlertDialog.Builder(activity).setTitle("本地图片屏保")
                .setMessage("仅在新桌面前台静置时启动，按任意键返回。不会打断视频、HDMI 或设置。\n\n可从电视或U盘导入最多24张图片，图片会复制到电视内，拔掉U盘后仍可使用；移除屏保图片不删除原图。\n\n没有自选图片时，显示三幅内置风景和移动时钟。屏保自身不联网，也不展示广告。关闭本地屏保后保留桌面画面。")
                .setPositiveButton("关闭",null).show()).show();
    }

    private void chooseTime(boolean wait) {
        int[] values = wait ? new int[]{1,2,5,10,12,15,30,60} : new int[]{10,15,30,60,120,300};
        String[] labels = new String[values.length];
        int selected = 0, current = prefs.getInt(wait ? "saver_wait_minutes" : "saver_slide_seconds", wait ? 12 : 30);
        for (int i = 0; i < values.length; i++) { labels[i] = values[i] + (wait ? " 分钟" : " 秒"); if (values[i] == current) selected = i; }
        new AlertDialog.Builder(activity).setTitle(wait ? "静置多久进入屏保" : "图片轮播间隔")
            .setSingleChoiceItems(labels, selected, (d,index) -> {
                prefs.edit().putInt(wait ? "saver_wait_minutes" : "saver_slide_seconds", values[index]).apply(); d.dismiss();
            }).setNegativeButton("取消",null).show();
    }

    private void browse() {
        if (activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, READ_IMAGES); return;
        }
        chooseRoot();
    }

    void permissionResult(int[] results) {
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) chooseRoot();
        else Toast.makeText(activity,"未获得图片读取权限，可尝试图片选择器",Toast.LENGTH_LONG).show();
    }

    private void chooseRoot() {
        List<File> roots = new ArrayList<>();
        roots.add(Environment.getExternalStorageDirectory());
        File[] devices = new File("/storage").listFiles();
        if (devices != null) for (File device : devices) {
            if (!device.getName().equals("emulated") && !device.getName().equals("self") && device.isDirectory() && device.canRead()) {
                try {
                    boolean duplicate = false;
                    for (File root : roots) if (root.getCanonicalPath().equals(device.getCanonicalPath())) duplicate = true;
                    if (!duplicate) roots.add(device);
                } catch (java.io.IOException ignored) {}
            }
        }
        String[] names = new String[roots.size()];
        for (int i = 0; i < names.length; i++) names[i] = i == 0 ? "电视内部存储" : "U盘 / 存储设备 · " + roots.get(i).getName();
        new AlertDialog.Builder(activity).setTitle("选择图片所在位置").setItems(names, (d,index) -> folder(roots.get(index),roots.get(index)))
            .setNegativeButton("取消",null).show();
    }

    private void folder(File root, File directory) {
        File[] entries = directory.listFiles(file -> !file.getName().startsWith(".") && file.canRead()
            && (file.isDirectory() || SaverPolicy.imageName(file.getName())));
        if (entries == null) { Toast.makeText(activity,"无法读取这个目录",Toast.LENGTH_SHORT).show(); return; }
        Arrays.sort(entries, (a,b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        List<File> files = new ArrayList<>(Arrays.asList(entries).subList(0, Math.min(entries.length, 300)));
        List<String> labels = new ArrayList<>();
        boolean parent = !directory.equals(root);
        if (parent) labels.add("‹ 上一级");
        for (File file : files) labels.add((file.isDirectory() ? "[文件夹] " : "") + file.getName());
        if (labels.isEmpty()) labels.add("这个文件夹没有图片");
        new AlertDialog.Builder(activity).setTitle(directory.equals(root) ? "选择图片" : directory.getName())
            .setItems(labels.toArray(new String[0]), (d,index) -> {
                if (parent && index == 0) { folder(root,directory.getParentFile()); return; }
                int selected = index - (parent ? 1 : 0);
                if (selected >= files.size()) return;
                File file = files.get(selected);
                if (file.isDirectory()) folder(root,file);
                else {
                    List<Uri> uri = new ArrayList<>(); uri.add(Uri.fromFile(file)); importImages(uri);
                }
            }).setNegativeButton("取消",null).show();
    }

    private void pick() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        if (pick.resolveActivity(activity.getPackageManager()) == null) pick.setAction(Intent.ACTION_GET_CONTENT);
        try { activity.startActivityForResult(pick,PICK_IMAGES); }
        catch (android.content.ActivityNotFoundException error) { Toast.makeText(activity,"没有图片选择器，请使用电视 / U盘入口",Toast.LENGTH_LONG).show(); }
    }

    void pickerResult(Intent result) {
        if (result == null) return;
        List<Uri> uris = new ArrayList<>();
        if (result.getClipData() != null) {
            for (int i = 0; i < Math.min(SaverPolicy.MAX_IMAGES,result.getClipData().getItemCount()); i++) uris.add(result.getClipData().getItemAt(i).getUri());
        } else if (result.getData() != null) uris.add(result.getData());
        importImages(uris);
    }

    private void importImages(List<Uri> uris) {
        if (uris.isEmpty()) return;
        Toast.makeText(activity,"正在保存图片…",Toast.LENGTH_SHORT).show();
        worker.execute(() -> {
            int saved = 0; String failure = "";
            for (Uri uri : uris) {
                if (Thread.currentThread().isInterrupted()) break;
                try (InputStream input = "file".equals(uri.getScheme()) ? new FileInputStream(new File(uri.getPath())) : activity.getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new java.io.IOException("无法读取图片");
                    SaverImages.add(activity,input); saved++;
                } catch (Exception error) { failure = error.getMessage(); }
            }
            final String message = saved > 0 ? "已添加 " + saved + " 张图片" : "添加失败：" + failure;
            handler.post(() -> { if (!closed && !activity.isFinishing()) Toast.makeText(activity,message,Toast.LENGTH_LONG).show(); });
        });
    }

    private void manage() {
        List<File> images = SaverImages.list(activity);
        if (images.isEmpty()) { Toast.makeText(activity,"尚未添加图片，当前使用内置风景",Toast.LENGTH_LONG).show(); return; }
        String[] names = new String[images.size()];
        for (int i = 0; i < names.length; i++) names[i] = "图片 " + (i+1);
        new AlertDialog.Builder(activity).setTitle("选择要移除的图片").setItems(names, (d,index) -> {
            android.widget.ImageView image = new android.widget.ImageView(activity);
            image.setAdjustViewBounds(true); image.setMaxHeight(Ui.dp(activity,260));
            android.graphics.Bitmap bitmap = SaverImages.decode(images.get(index),640,360);
            image.setImageBitmap(bitmap);
            new AlertDialog.Builder(activity).setTitle(names[index]).setView(image)
                .setPositiveButton("从屏保移除",(dialog,which) -> {
                    boolean removed = images.get(index).delete();
                    Toast.makeText(activity,removed ? "已移除屏保副本，原图保留" : "移除失败",Toast.LENGTH_SHORT).show();
                }).setNegativeButton("保留",null).show();
        }).setNegativeButton("关闭",null).show();
    }

    void close() { closed = true; handler.removeCallbacksAndMessages(null); worker.shutdownNow(); }
}
