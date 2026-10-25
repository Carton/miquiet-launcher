package local.mitv.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** One remote-friendly photo workflow; never delegates to an absent system picker. */
public final class SaverLibraryActivity extends Activity {
    private static final int LIBRARY = 0, SOURCES = 1, FOLDER = 2, ALBUM = 3;
    private static final int IMAGE = 0, DIRECTORY = 1, MEDIA = 2, PERMISSION = 114;
    private final Handler handler = new Handler();
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private final ExecutorService thumbnails = new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(48),new ThreadPoolExecutor.DiscardPolicy());
    private final LruCache<String,Bitmap> cache = new LruCache<String,Bitmap>(6 * 1024 * 1024) {
        @Override protected int sizeOf(String key,Bitmap value) { return value.getByteCount(); }
    };
    private final List<Entry> entries = new ArrayList<>();
    private final LinkedHashMap<String,File> selected = new LinkedHashMap<>();
    private GridView grid;
    private TextView title, location, hint, empty, sourceButton, actionButton, allButton, inspectButton;
    private PhotoAdapter adapter;
    private File root, directory;
    private int mode = LIBRARY, generation;
    private volatile boolean closed, cancelImport;
    private boolean importing, firstResume = true;
    private PhotoUploadServer upload;
    private AlertDialog uploadDialog;
    private ProgressDialog progress;

    private static final class Entry {
        final File file; final String name; final int kind;
        Entry(File file,String name,int kind) { this.file = file; this.name = name; this.kind = kind; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20),dp(12),dp(20),dp(10)); page.setBackgroundColor(Ui.BACKGROUND); setContentView(page);
        LinearLayout toolbar = row(); page.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(44)));
        toolbar.addView(button("‹ 返回",this::onBackPressed),new LinearLayout.LayoutParams(dp(76),dp(36)));
        title = Ui.text(this,"屏保图片",22,Color.WHITE); title.setGravity(Gravity.CENTER_VERTICAL); title.setPadding(dp(16),0,0,0);
        toolbar.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        toolbar.addView(button("预览屏保",() -> startActivity(new Intent(this,ScreensaverActivity.class))),new LinearLayout.LayoutParams(dp(108),dp(36)));
        LinearLayout controls = row(); page.addView(controls,new LinearLayout.LayoutParams(-1,dp(46)));
        sourceButton = button("添加图片",() -> { if (mode == LIBRARY) sources(); else leaveSources(); });
        controls.addView(sourceButton,new LinearLayout.LayoutParams(dp(120),dp(36)));
        controls.addView(button("电脑 / 手机传图",() -> discardThen(this::showUpload)),new LinearLayout.LayoutParams(dp(150),dp(36)));
        allButton = button("全选",this::toggleAll); controls.addView(allButton,new LinearLayout.LayoutParams(dp(86),dp(36)));
        inspectButton = button("查看选中",this::inspect); controls.addView(inspectButton,new LinearLayout.LayoutParams(dp(100),dp(36)));
        actionButton = button("移除选中",this::applySelection); controls.addView(actionButton,new LinearLayout.LayoutParams(dp(170),dp(36)));
        controls.addView(button("刷新列表",this::reload),new LinearLayout.LayoutParams(dp(110),dp(36)));
        location = Ui.text(this,"",13,Ui.MUTED); location.setSingleLine(true); location.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        page.addView(location,new LinearLayout.LayoutParams(-1,dp(28)));
        FrameLayout area = new FrameLayout(this); page.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        grid = new GridView(this); grid.setNumColumns(5); grid.setVerticalSpacing(dp(10)); grid.setHorizontalSpacing(dp(10));
        grid.setSelector(Ui.shape(this,Color.TRANSPARENT,Color.WHITE,7)); grid.setDrawSelectorOnTop(true);
        area.addView(grid,new FrameLayout.LayoutParams(-1,-1));
        empty = Ui.text(this,"正在读取图片…",18,Ui.MUTED); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(24),0,dp(24),0);
        area.addView(empty,new FrameLayout.LayoutParams(-1,-1)); grid.setEmptyView(empty);
        adapter = new PhotoAdapter(); grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent,view,index,id) -> open(entries.get(index)));
        grid.setOnItemLongClickListener((parent,view,index,id) -> {
            Entry entry = entries.get(index); if (entry.kind != IMAGE) return false;
            preview(entry.file,entry.name); return true;
        });
        hint = Ui.text(this,"",13,Ui.MUTED); hint.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(hint,new LinearLayout.LayoutParams(-1,dp(42)));
        library();
    }
    private int dp(int value) { return Ui.dp(this,value); }
    private LinearLayout row() { LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); return row; }
    private TextView button(String text,Runnable action) {
        TextView view = Ui.text(this,text,14,Color.WHITE); view.setGravity(Gravity.CENTER);
        Ui.focusable(view,Color.TRANSPARENT,6); view.setOnClickListener(v -> { if (!importing) action.run(); }); return view;
    }
    private static String key(File file) {
        try { return file.getCanonicalPath(); } catch (java.io.IOException error) { return file.getAbsolutePath(); }
    }
    private void post(Runnable action) { if (!closed) handler.post(() -> { if (!closed && !isFinishing()) action.run(); }); }
    private void updateButtons() {
        int count = selected.size();
        title.setText("屏保图片 · 已保存 " + SaverImages.list(this).size() + "/24");
        sourceButton.setText(mode == LIBRARY ? "添加图片" : "返回已保存");
        actionButton.setText((mode == LIBRARY ? "移除选中" : "导入选中") + "（" + count + "）");
        actionButton.setEnabled(count > 0); actionButton.setAlpha(count > 0 ? 1f : .4f);
        inspectButton.setEnabled(count == 1); inspectButton.setAlpha(count == 1 ? 1f : .4f);
        allButton.setText(count > 0 ? "取消勾选" : "全选");
    }
    private void display(int token,List<Entry> values,String path,String message) {
        if (token != generation) return;
        entries.clear(); entries.addAll(values); location.setText(path); hint.setText(message);
        adapter.notifyDataSetChanged(); updateButtons();
        if (!values.isEmpty()) { grid.setSelection(0); grid.requestFocus(); }
        else sourceButton.requestFocus();
    }
    private int loading(String text) {
        int token = ++generation; entries.clear(); adapter.notifyDataSetChanged();
        empty.setText("正在读取…"); location.setText(text); hint.setText(""); updateButtons(); return token;
    }
    private void library() {
        mode = LIBRARY; selected.clear(); int token = loading("已保存到电视的屏保副本");
        files.execute(() -> {
            List<Entry> values = new ArrayList<>(); int index = 1;
            for (File photo : SaverImages.list(this)) values.add(new Entry(photo,"照片 " + index++,IMAGE));
            post(() -> {
                if (token != generation) return;
                empty.setText("还没有自选照片，当前使用内置风景。\n\n选择「添加图片」从电视或U盘导入，\n或选择「电脑 / 手机传图」直接上传。");
                display(token,values,"已保存到电视的屏保副本","点击照片勾选 · 查看选中可放大预览 · 移除副本不会删除原图");
            });
        });
    }
    private boolean permission() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) return true;
        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},PERMISSION); return false;
    }
    private void sources() {
        if (!permission()) return;
        mode = SOURCES; int token = loading("选择图片位置");
        files.execute(() -> {
            List<Entry> values = new ArrayList<>(); values.add(new Entry(null,"电视相册",MEDIA));
            File internal = Environment.getExternalStorageDirectory();
            for (String name : new String[]{"Download","Pictures","DCIM"}) {
                File folder = new File(internal,name);
                if (folder.isDirectory() && folder.canRead()) values.add(new Entry(folder,name.equals("Download") ? "下载" : name.equals("Pictures") ? "图片" : "相机照片",DIRECTORY));
            }
            List<File> roots = SaverBrowser.roots(internal,new File("/storage"),new File("/mnt/usb"),new File("/mnt/usb_storage"));
            for (File device : roots) values.add(new Entry(device,key(device).equals(key(internal)) ? "电视内部存储" : "U盘 · " + device.getName(),DIRECTORY));
            post(() -> display(token,values,"选择图片位置","可跨文件夹勾选，最后点击「导入选中」 · 未看到U盘时插入后刷新列表"));
        });
    }
    private void folder(File base,File current) {
        root = base; directory = current; mode = FOLDER; int token = loading(current.getPath());
        files.execute(() -> {
            try {
                File[] children = SaverBrowser.entries(base,current); List<Entry> values = new ArrayList<>();
                for (int i = 0; i < Math.min(children.length,SaverBrowser.LIMIT); i++) {
                    File child = children[i]; values.add(new Entry(child,child.getName(),child.isDirectory() ? DIRECTORY : IMAGE));
                }
                post(() -> {
                    if (token != generation) return;
                    empty.setText("这个文件夹没有可用图片。\n按返回选择其他位置，或从电脑 / 手机传图。\n支持 JPG、PNG、WebP、BMP。");
                    display(token,values,current.getPath(),(children.length > SaverBrowser.LIMIT ? "仅显示前300项 · " : "") + "点击图片勾选 · 导入后拔掉U盘仍可播放 · 返回键回上一级");
                });
            } catch (Exception error) {
                post(() -> { empty.setText(error.getMessage()); display(token,Collections.emptyList(),current.getPath(),"返回选择其他位置；U盘断开后请重新插入"); });
            }
        });
    }
    private void album() {
        mode = ALBUM; int token = loading("电视相册 · 最近图片");
        files.execute(() -> {
            List<Entry> values = new ArrayList<>(); String failure = null;
            try (Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media.DATA,MediaStore.Images.Media.DISPLAY_NAME},null,null,MediaStore.Images.Media.DATE_ADDED + " DESC")) {
                if (cursor != null) while (cursor.moveToNext() && values.size() < SaverBrowser.LIMIT) {
                    String path = cursor.getString(0); if (path == null) continue;
                    File file = new File(path);
                    if (file.isFile() && file.canRead() && SaverPolicy.imageName(file.getName())) values.add(new Entry(file,file.getName(),IMAGE));
                }
            } catch (RuntimeException error) { failure = "电视相册暂不可读取，请通过文件夹或电脑 / 手机传图"; }
            final String message = failure;
            post(() -> {
                if (token != generation) return;
                empty.setText(message == null ? "电视相册尚未收录图片。\n可返回从「下载」「图片」或U盘直接导入，\n也可从电脑 / 手机传图。" : message);
                display(token,values,"电视相册 · 最近图片","点击图片勾选，完成后点击「导入选中」 · 最多显示最近300张");
            });
        });
    }
    private void open(Entry entry) {
        if (importing) return;
        if (entry.kind == MEDIA) { album(); return; }
        if (entry.kind == DIRECTORY) { folder(mode == SOURCES ? entry.file : root,entry.file); return; }
        String key = key(entry.file);
        if (selected.containsKey(key)) selected.remove(key);
        else {
            int limit = mode == LIBRARY ? SaverPolicy.MAX_IMAGES : SaverPolicy.MAX_IMAGES - SaverImages.list(this).size();
            if (selected.size() >= limit) { toast("屏保最多24张，请先移除部分已保存照片"); return; }
            selected.put(key,entry.file);
        }
        adapter.notifyDataSetChanged(); updateButtons();
    }
    private void toggleAll() {
        if (!selected.isEmpty()) selected.clear();
        else {
            int limit = mode == LIBRARY ? SaverPolicy.MAX_IMAGES : SaverPolicy.MAX_IMAGES - SaverImages.list(this).size();
            for (Entry entry : entries) if (entry.kind == IMAGE && selected.size() < limit) selected.put(key(entry.file),entry.file);
            if (selected.isEmpty()) toast(mode == SOURCES ? "请先打开图片所在位置" : limit == 0 ? "图片已满，请先移除已保存照片" : "这里没有可选择的图片");
        }
        adapter.notifyDataSetChanged(); updateButtons();
    }
    private void inspect() {
        if (selected.size() == 1) { File file = selected.values().iterator().next(); preview(file,mode == LIBRARY ? "屏保照片" : file.getName()); }
    }
    private void preview(File file,String name) {
        toast("正在读取预览…");
        files.execute(() -> {
            Bitmap bitmap = SaverImages.decode(file,1280,720);
            if (closed) { if (bitmap != null) bitmap.recycle(); return; }
            post(() -> {
                if (bitmap == null) { toast("无法解码这张图片，请选择其他图片"); return; }
                ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.FIT_CENTER); image.setImageBitmap(bitmap);
                FrameLayout frame = new FrameLayout(this); frame.addView(image,new FrameLayout.LayoutParams(-1,dp(300)));
                AlertDialog dialog = new AlertDialog.Builder(this).setTitle(name).setView(frame).setPositiveButton("关闭",null).create();
                dialog.setOnDismissListener(d -> { image.setImageDrawable(null); bitmap.recycle(); }); dialog.show();
            });
        });
    }
    private void applySelection() {
        if (selected.isEmpty() || importing) return;
        if (mode == LIBRARY) {
            new AlertDialog.Builder(this).setTitle("移除 " + selected.size() + " 张屏保照片？")
                .setMessage("只移除电视内的屏保副本，原图保留。")
                .setPositiveButton("移除",(d,w) -> {
                    int removed = 0;
                    for (File file : selected.values()) if (SaverBrowser.inside(SaverImages.directory(this),file) && file.delete()) removed++;
                    toast("已移除 " + removed + " 张"); library();
                }).setNegativeButton("取消",null).show(); return;
        }
        List<File> batch = new ArrayList<>(selected.values()); importing = true; cancelImport = false;
        progress = new ProgressDialog(this); progress.setTitle("正在导入屏保照片"); progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(batch.size()); progress.setCancelable(false); progress.setButton(ProgressDialog.BUTTON_NEGATIVE,"停止",(d,w) -> {}); progress.show();
        progress.getButton(ProgressDialog.BUTTON_NEGATIVE).setOnClickListener(v -> { cancelImport = true; progress.setMessage("正在停止，已导入的照片会保留"); v.setEnabled(false); });
        files.execute(() -> {
            int saved = 0, failed = 0, done = 0; String reason = "";
            for (File file : batch) {
                if (cancelImport || Thread.currentThread().isInterrupted()) break;
                try (FileInputStream input = new FileInputStream(file)) { SaverImages.add(this,input); saved++; }
                catch (Exception error) { failed++; reason = error.getMessage() == null ? "文件不可读或格式无效" : error.getMessage(); }
                final int count = ++done; post(() -> { progress.setProgress(count); progress.setMessage(file.getName()); });
            }
            final String message = "已导入 " + saved + " 张" + (failed > 0 ? "，失败 " + failed + " 张：" + reason : "") + (cancelImport ? "，已停止" : "");
            post(() -> { importing = false; progress.dismiss(); progress = null; library(); new AlertDialog.Builder(this).setTitle("导入结果").setMessage(message).setPositiveButton("完成",null).show(); });
        });
    }
    private void reload() {
        if (mode == LIBRARY) library(); else if (mode == FOLDER) folder(root,directory); else if (mode == ALBUM) album(); else sources();
    }
    private void leaveSources() { discardThen(this::library); }
    private void discardThen(Runnable action) {
        if (selected.isEmpty()) { action.run(); return; }
        new AlertDialog.Builder(this).setTitle("取消当前勾选？").setMessage("已保存的屏保照片不受影响。")
            .setPositiveButton("继续",(d,w) -> { selected.clear(); adapter.notifyDataSetChanged(); updateButtons(); action.run(); }).setNegativeButton("返回",null).show();
    }
    private void showUpload() {
        stopUpload();
        try {
            InetAddress address = lanAddress();
            if (address == null) { toast("未找到局域网连接，请先连接Wi-Fi或网线"); return; }
            try { upload = new PhotoUploadServer(address,8765,this::receive); }
            catch (java.net.BindException occupied) { upload = new PhotoUploadServer(address,0,this::receive); }
            String url = "http://" + address.getHostAddress() + ":" + upload.port();
            uploadDialog = new AlertDialog.Builder(this).setTitle("从电脑 / 手机传图")
                .setMessage("在同一Wi-Fi或局域网的浏览器中打开：\n\n" + url + "\n\n连接码：" + upload.code()
                    + "\n\n选好照片后点击「保存到电视屏保」。\n当前已保存 " + SaverImages.list(this).size() + "/24 张。\n关闭此窗口或离开本页后停止接收。")
                .setPositiveButton("完成传图",null).create();
            uploadDialog.setOnDismissListener(d -> { uploadDialog = null; stopUpload(); library(); }); uploadDialog.show();
        } catch (Exception error) { stopUpload(); toast("暂时无法开启传图，请检查网络后重试"); }
    }
    private void receive(java.io.InputStream input) throws Exception {
        SaverImages.add(this,input);
        post(() -> {
            updateButtons();
            if (uploadDialog != null && upload != null) uploadDialog.setMessage("浏览器地址：http://" + uploadAddress() + "\n\n连接码：" + upload.code()
                + "\n\n已保存 " + SaverImages.list(this).size() + "/24 张。\n可继续上传，完成后关闭此窗口。\n离开本页即停止接收。");
        });
    }
    private String uploadAddress() {
        try { return lanAddress().getHostAddress() + ":" + upload.port(); } catch (Exception error) { return "网络已断开"; }
    }
    private static InetAddress lanAddress() throws java.net.SocketException {
        java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) return null;
        while (interfaces.hasMoreElements()) {
            NetworkInterface network = interfaces.nextElement();
            if (!network.isUp() || network.isLoopback() || !(network.getName().startsWith("wlan") || network.getName().startsWith("eth"))) continue;
            java.util.Enumeration<InetAddress> addresses = network.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address instanceof Inet4Address && address.isSiteLocalAddress()) return address;
            }
        }
        return null;
    }
    private void stopUpload() {
        if (upload != null) { upload.close(); upload = null; }
    }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if (request == PERMISSION && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) sources();
        else if (request == PERMISSION) new AlertDialog.Builder(this).setTitle("未获得图片读取权限")
            .setMessage("仍可通过「电脑 / 手机传图」添加屏保照片；再次进入添加图片可重新申请权限。")
            .setPositiveButton("知道了",null).show();
    }
    @Override public void onBackPressed() {
        if (importing) { cancelImport = true; return; }
        if (mode == FOLDER && !key(directory).equals(key(root))) folder(root,directory.getParentFile());
        else if (mode == FOLDER || mode == ALBUM) sources();
        else if (mode == SOURCES) leaveSources();
        else finish();
    }
    @Override protected void onResume() {
        super.onResume();
        if (firstResume) firstResume = false;
        else if (mode == LIBRARY && !importing) library();
    }
    @Override protected void onPause() {
        stopUpload();
        if (uploadDialog != null && uploadDialog.isShowing()) uploadDialog.dismiss();
        cancelImport = true; super.onPause();
    }
    @Override protected void onDestroy() {
        closed = true; cancelImport = true; stopUpload(); handler.removeCallbacksAndMessages(null);
        files.shutdownNow(); thumbnails.shutdownNow(); cache.evictAll(); super.onDestroy();
    }

    private final class PhotoAdapter extends BaseAdapter {
        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int position) { return entries.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position,View convert,ViewGroup parent) {
            Entry entry = entries.get(position);
            FrameLayout card = new FrameLayout(SaverLibraryActivity.this);
            boolean checked = entry.kind == IMAGE && selected.containsKey(key(entry.file));
            card.setBackground(Ui.shape(SaverLibraryActivity.this,checked ? 0xff264f45 : Ui.PANEL,checked ? Ui.ACCENT : 0,7));
            card.setLayoutParams(new AbsListView.LayoutParams(-1,dp(136)));
            ImageView photo = new ImageView(SaverLibraryActivity.this); photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams imageLayout = new FrameLayout.LayoutParams(-1,dp(106)); imageLayout.setMargins(dp(4),dp(4),dp(4),0); card.addView(photo,imageLayout);
            TextView name = Ui.text(SaverLibraryActivity.this,entry.name,12,Color.WHITE); name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.MIDDLE); name.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams label = new FrameLayout.LayoutParams(-1,dp(26),Gravity.BOTTOM); card.addView(name,label);
            if (entry.kind != IMAGE) {
                Ui.Icon icon = new Ui.Icon(SaverLibraryActivity.this,entry.kind == MEDIA ? "image" : "folder",Ui.ACCENT);
                FrameLayout.LayoutParams symbol = new FrameLayout.LayoutParams(dp(58),dp(58),Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                symbol.topMargin = dp(23); card.addView(icon,symbol);
            } else {
                Ui.Icon check = new Ui.Icon(SaverLibraryActivity.this,checked ? "checked" : "unchecked",checked ? Ui.ACCENT : Color.WHITE);
                FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(dp(32),dp(32),Gravity.TOP | Gravity.RIGHT); card.addView(check,badge);
                String tag = key(entry.file) + ":" + entry.file.lastModified(); photo.setTag(tag);
                Bitmap ready = cache.get(tag);
                if (ready != null) photo.setImageBitmap(ready);
                else thumbnails.execute(() -> {
                    Bitmap bitmap = SaverImages.decode(entry.file,320,180);
                    if (bitmap == null) return;
                    if (closed) { bitmap.recycle(); return; }
                    cache.put(tag,bitmap); post(() -> { if (tag.equals(photo.getTag())) photo.setImageBitmap(bitmap); });
                });
            }
            card.setContentDescription(entry.name + (entry.kind != IMAGE ? "，打开位置" : checked ? "，已勾选" : "，点击勾选"));
            return card;
        }
    }
}
