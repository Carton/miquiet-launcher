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
    private TextView uploadStatus;
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
        toolbar.addView(button(SaverLibraryActivity.this.getString(R.string.reader_back),this::onBackPressed),new LinearLayout.LayoutParams(dp(76),dp(36)));
        title = Ui.text(this,SaverLibraryActivity.this.getString(R.string.saver_photos),22,Color.WHITE); title.setGravity(Gravity.CENTER_VERTICAL); title.setPadding(dp(16),0,0,0);
        toolbar.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        toolbar.addView(button(SaverLibraryActivity.this.getString(R.string.preview_saver),() -> startActivity(new Intent(this,ScreensaverActivity.class).putExtra(ScreensaverActivity.EXTRA_PREVIEW,true))),new LinearLayout.LayoutParams(dp(108),dp(36)));
        LinearLayout controls = row(); page.addView(controls,new LinearLayout.LayoutParams(-1,dp(46)));
        sourceButton = button(SaverLibraryActivity.this.getString(R.string.add_photos),() -> { if (mode == LIBRARY) sources(); else leaveSources(); });
        controls.addView(sourceButton,new LinearLayout.LayoutParams(dp(120),dp(36)));
        controls.addView(button(SaverLibraryActivity.this.getString(R.string.upload_photos),() -> discardThen(this::showUpload)),new LinearLayout.LayoutParams(dp(150),dp(36)));
        allButton = button(SaverLibraryActivity.this.getString(R.string.select_all),this::toggleAll); controls.addView(allButton,new LinearLayout.LayoutParams(dp(86),dp(36)));
        inspectButton = button(SaverLibraryActivity.this.getString(R.string.view_selection),this::inspect); controls.addView(inspectButton,new LinearLayout.LayoutParams(dp(100),dp(36)));
        actionButton = button(SaverLibraryActivity.this.getString(R.string.remove_selected),this::applySelection); controls.addView(actionButton,new LinearLayout.LayoutParams(dp(170),dp(36)));
        controls.addView(button(SaverLibraryActivity.this.getString(R.string.refresh_list),this::reload),new LinearLayout.LayoutParams(dp(110),dp(36)));
        location = Ui.text(this,"",13,Ui.MUTED); location.setSingleLine(true); location.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        page.addView(location,new LinearLayout.LayoutParams(-1,dp(28)));
        FrameLayout area = new FrameLayout(this); page.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        grid = new GridView(this); grid.setNumColumns(5); grid.setVerticalSpacing(dp(10)); grid.setHorizontalSpacing(dp(10));
        grid.setSelector(Ui.shape(this,Color.TRANSPARENT,Color.WHITE,7)); grid.setDrawSelectorOnTop(true);
        area.addView(grid,new FrameLayout.LayoutParams(-1,-1));
        empty = Ui.text(this,SaverLibraryActivity.this.getString(R.string.loading_photos),18,Ui.MUTED); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(24),0,dp(24),0);
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
        TextView view = Ui.text(this,text,UiLocale.english(this) ? 13 : 14,Color.WHITE); view.setGravity(Gravity.CENTER);
        view.setSingleLine(true); view.setEllipsize(TextUtils.TruncateAt.END);
        Ui.focusable(view,Color.TRANSPARENT,6); view.setOnClickListener(v -> { if (!importing) action.run(); }); return view;
    }
    private static String key(File file) {
        try { return file.getCanonicalPath(); } catch (java.io.IOException error) { return file.getAbsolutePath(); }
    }
    private void post(Runnable action) { if (!closed) handler.post(() -> { if (!closed && !isFinishing()) action.run(); }); }
    private void updateButtons() {
        int count = selected.size();
        title.setText(SaverLibraryActivity.this.getString(R.string.saver_photos_saved) + SaverImages.list(this).size() + "/24");
        sourceButton.setText(mode == LIBRARY ? SaverLibraryActivity.this.getString(R.string.add_photos) : SaverLibraryActivity.this.getString(R.string.saved_photos));
        actionButton.setText((mode == LIBRARY ? SaverLibraryActivity.this.getString(R.string.remove_selected) : SaverLibraryActivity.this.getString(R.string.import_selected)) + "（" + count + "）");
        actionButton.setEnabled(count > 0); actionButton.setAlpha(count > 0 ? 1f : .4f);
        inspectButton.setEnabled(count == 1); inspectButton.setAlpha(count == 1 ? 1f : .4f);
        allButton.setText(count > 0 ? SaverLibraryActivity.this.getString(R.string.clear_selection) : SaverLibraryActivity.this.getString(R.string.select_all));
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
        empty.setText(SaverLibraryActivity.this.getString(R.string.loading)); location.setText(text); hint.setText(""); updateButtons(); return token;
    }
    private void library() {
        mode = LIBRARY; selected.clear(); int token = loading(SaverLibraryActivity.this.getString(R.string.screensaver_copies_saved_on_this_tv));
        files.execute(() -> {
            List<Entry> values = new ArrayList<>(); int index = 1;
            for (File photo : SaverImages.list(this)) values.add(new Entry(photo,SaverLibraryActivity.this.getString(R.string.photo) + index++,IMAGE));
            post(() -> {
                if (token != generation) return;
                empty.setText(SaverLibraryActivity.this.getString(R.string.no_imported_photos_built_in_scenes_are));
                display(token,values,SaverLibraryActivity.this.getString(R.string.screensaver_copies_saved_on_this_tv),SaverLibraryActivity.this.getString(R.string.select_photos_view_selection_to_enlarge_removing));
            });
        });
    }
    private boolean permission() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) return true;
        requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},PERMISSION); return false;
    }
    private void sources() {
        if (!permission()) return;
        mode = SOURCES; int token = loading(SaverLibraryActivity.this.getString(R.string.choose_photo_location));
        files.execute(() -> {
            List<Entry> values = new ArrayList<>(); values.add(new Entry(null,SaverLibraryActivity.this.getString(R.string.tv_album),MEDIA));
            File internal = Environment.getExternalStorageDirectory();
            for (String name : new String[]{"Download","Pictures","DCIM"}) {
                File folder = new File(internal,name);
                if (folder.isDirectory() && folder.canRead()) values.add(new Entry(folder,name.equals("Download") ? SaverLibraryActivity.this.getString(R.string.downloads) : name.equals("Pictures") ? SaverLibraryActivity.this.getString(R.string.pictures) : SaverLibraryActivity.this.getString(R.string.camera_photos),DIRECTORY));
            }
            List<File> roots = SaverBrowser.roots(internal,new File("/storage"),new File("/mnt/usb"),new File("/mnt/usb_storage"));
            for (File device : roots) values.add(new Entry(device,key(device).equals(key(internal)) ? SaverLibraryActivity.this.getString(R.string.tv_internal_storage) : SaverLibraryActivity.this.getString(R.string.usb_drive) + device.getName(),DIRECTORY));
            post(() -> display(token,values,SaverLibraryActivity.this.getString(R.string.choose_photo_location),SaverLibraryActivity.this.getString(R.string.select_across_folders_then_import_selected_reconnect)));
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
                    empty.setText(SaverLibraryActivity.this.getString(R.string.no_supported_photos_in_this_folder_go));
                    display(token,values,current.getPath(),(children.length > SaverBrowser.LIMIT ? SaverLibraryActivity.this.getString(R.string.first_300_items_only) : "") + SaverLibraryActivity.this.getString(R.string.select_photos_imported_copies_work_without_the));
                });
            } catch (Exception error) {
                post(() -> { empty.setText(UiLabels.photoFailure(this,error)); display(token,Collections.emptyList(),current.getPath(),SaverLibraryActivity.this.getString(R.string.go_back_to_choose_another_location_reconnect)); });
            }
        });
    }
    private void album() {
        mode = ALBUM; int token = loading(SaverLibraryActivity.this.getString(R.string.tv_album_recent_photos));
        files.execute(() -> {
            List<Entry> values = new ArrayList<>(); String failure = null;
            try (Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media.DATA,MediaStore.Images.Media.DISPLAY_NAME},null,null,MediaStore.Images.Media.DATE_ADDED + " DESC")) {
                if (cursor != null) while (cursor.moveToNext() && values.size() < SaverBrowser.LIMIT) {
                    String path = cursor.getString(0); if (path == null) continue;
                    File file = new File(path);
                    if (file.isFile() && file.canRead() && SaverPolicy.imageName(file.getName())) values.add(new Entry(file,file.getName(),IMAGE));
                }
            } catch (RuntimeException error) { failure = SaverLibraryActivity.this.getString(R.string.tv_album_unavailable_use_folders_or_upload); }
            final String message = failure;
            post(() -> {
                if (token != generation) return;
                empty.setText(message == null ? SaverLibraryActivity.this.getString(R.string.the_tv_album_has_no_photos_yet) : message);
                display(token,values,SaverLibraryActivity.this.getString(R.string.tv_album_recent_photos),SaverLibraryActivity.this.getString(R.string.select_photos_then_import_selected_up_to));
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
            if (selected.size() >= limit) { toast(SaverLibraryActivity.this.getString(R.string.the_saver_holds_24_photos_remove_some)); return; }
            selected.put(key,entry.file);
        }
        adapter.notifyDataSetChanged(); updateButtons();
    }
    private void toggleAll() {
        if (!selected.isEmpty()) selected.clear();
        else {
            int limit = mode == LIBRARY ? SaverPolicy.MAX_IMAGES : SaverPolicy.MAX_IMAGES - SaverImages.list(this).size();
            for (Entry entry : entries) if (entry.kind == IMAGE && selected.size() < limit) selected.put(key(entry.file),entry.file);
            if (selected.isEmpty()) toast(mode == SOURCES ? SaverLibraryActivity.this.getString(R.string.open_a_photo_location_first) : limit == 0 ? SaverLibraryActivity.this.getString(R.string.photo_library_full_remove_some_saved_copies) : SaverLibraryActivity.this.getString(R.string.no_selectable_photos_here));
        }
        adapter.notifyDataSetChanged(); updateButtons();
    }
    private void inspect() {
        if (selected.size() == 1) { File file = selected.values().iterator().next(); preview(file,mode == LIBRARY ? SaverLibraryActivity.this.getString(R.string.screensaver_photo) : file.getName()); }
    }
    private void preview(File file,String name) {
        toast(SaverLibraryActivity.this.getString(R.string.loading_preview));
        files.execute(() -> {
            Bitmap bitmap = SaverImages.decode(file,1280,720);
            if (closed) { if (bitmap != null) bitmap.recycle(); return; }
            post(() -> {
                if (bitmap == null) { toast(SaverLibraryActivity.this.getString(R.string.this_photo_cannot_be_decoded_choose_another)); return; }
                ImageView image = new ImageView(this); image.setScaleType(ImageView.ScaleType.FIT_CENTER); image.setImageBitmap(bitmap);
                FrameLayout frame = new FrameLayout(this); frame.addView(image,new FrameLayout.LayoutParams(-1,dp(300)));
                AlertDialog dialog = new AlertDialog.Builder(this).setTitle(name).setView(frame).setPositiveButton(SaverLibraryActivity.this.getString(R.string.close),null).create();
                dialog.setOnDismissListener(d -> { image.setImageDrawable(null); bitmap.recycle(); }); dialog.show();
            });
        });
    }
    private void applySelection() {
        if (selected.isEmpty() || importing) return;
        if (mode == LIBRARY) {
            new AlertDialog.Builder(this).setTitle(SaverLibraryActivity.this.getString(R.string.remove_2) + selected.size() + SaverLibraryActivity.this.getString(R.string.screensaver_photos))
                .setMessage(SaverLibraryActivity.this.getString(R.string.only_saved_screensaver_copies_are_removed_originals))
                .setPositiveButton(SaverLibraryActivity.this.getString(R.string.remove),(d,w) -> {
                    int removed = 0;
                    for (File file : selected.values()) if (SaverBrowser.inside(SaverImages.directory(this),file) && file.delete()) removed++;
                    toast(SaverLibraryActivity.this.getString(R.string.removed) + removed + SaverLibraryActivity.this.getString(R.string.photo_count_suffix)); library();
                }).setNegativeButton(SaverLibraryActivity.this.getString(R.string.cancel),null).show(); return;
        }
        List<File> batch = new ArrayList<>(selected.values()); importing = true; cancelImport = false;
        progress = new ProgressDialog(this); progress.setTitle(SaverLibraryActivity.this.getString(R.string.importing_saver_photos)); progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(batch.size()); progress.setCancelable(false); progress.setButton(ProgressDialog.BUTTON_NEGATIVE,SaverLibraryActivity.this.getString(R.string.stop),(d,w) -> {}); progress.show();
        progress.getButton(ProgressDialog.BUTTON_NEGATIVE).setOnClickListener(v -> { cancelImport = true; progress.setMessage(SaverLibraryActivity.this.getString(R.string.stopping_imported_photos_will_be_kept)); v.setEnabled(false); });
        files.execute(() -> {
            int saved = 0, failed = 0, done = 0; String reason = "";
            for (File file : batch) {
                if (cancelImport || Thread.currentThread().isInterrupted()) break;
                try (FileInputStream input = new FileInputStream(file)) { SaverImages.add(this,input); saved++; }
                catch (Exception error) { failed++; reason = error.getMessage() == null ? SaverLibraryActivity.this.getString(R.string.unreadable_file_or_unsupported_format) : UiLabels.photoFailure(this,error); }
                final int count = ++done; post(() -> { progress.setProgress(count); progress.setMessage(file.getName()); });
            }
            final String message = SaverLibraryActivity.this.getString(R.string.imported) + saved + SaverLibraryActivity.this.getString(R.string.photo_count_suffix) + (failed > 0 ? SaverLibraryActivity.this.getString(R.string.failed) + failed + SaverLibraryActivity.this.getString(R.string.photo_failure_count_suffix) + reason : "") + (cancelImport ? SaverLibraryActivity.this.getString(R.string.stopped) : "");
            post(() -> { importing = false; progress.dismiss(); progress = null; library(); new AlertDialog.Builder(this).setTitle(SaverLibraryActivity.this.getString(R.string.import_results)).setMessage(message).setPositiveButton(SaverLibraryActivity.this.getString(R.string.done),null).show(); });
        });
    }
    private void reload() {
        if (mode == LIBRARY) library(); else if (mode == FOLDER) folder(root,directory); else if (mode == ALBUM) album(); else sources();
    }
    private void leaveSources() { discardThen(this::library); }
    private void discardThen(Runnable action) {
        if (selected.isEmpty()) { action.run(); return; }
        new AlertDialog.Builder(this).setTitle(SaverLibraryActivity.this.getString(R.string.clear_the_current_selection)).setMessage(SaverLibraryActivity.this.getString(R.string.saved_screensaver_photos_are_not_affected))
            .setPositiveButton(SaverLibraryActivity.this.getString(R.string.action_continue),(d,w) -> { selected.clear(); adapter.notifyDataSetChanged(); updateButtons(); action.run(); }).setNegativeButton(SaverLibraryActivity.this.getString(R.string.back),null).show();
    }
    private void showUpload() {
        stopUpload();
        try {
            InetAddress address = lanAddress();
            if (address == null) { toast(SaverLibraryActivity.this.getString(R.string.no_local_network_found_connect_wi_fi)); return; }
            PhotoUploadServer.Labels labels = new UploadStrings(this);
            try { upload = new PhotoUploadServer(address,8765,this::receive,labels); }
            catch (java.net.BindException occupied) { upload = new PhotoUploadServer(address,0,this::receive,labels); }
            LinearLayout content=row();content.setPadding(dp(24),dp(12),dp(24),dp(12));
            content.setGravity(Gravity.CENTER_VERTICAL);
            content.addView(new UploadQrView(this,upload.scanUrl()),new LinearLayout.LayoutParams(dp(220),dp(220)));
            LinearLayout details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams detailLayout=new LinearLayout.LayoutParams(0,-2,1);
            detailLayout.leftMargin=dp(28);content.addView(details,detailLayout);
            TextView info=Ui.text(this,SaverLibraryActivity.this.getString(R.string.scan_with_your_phone_to_choose_photos)+upload.url()
                +SaverLibraryActivity.this.getString(R.string.connection_code)+upload.code(),16,Color.WHITE);
            details.addView(info,new LinearLayout.LayoutParams(-1,-2));
            uploadStatus=Ui.text(this,SaverLibraryActivity.this.getString(R.string.photo_saved_prefix)+SaverImages.list(this).size()+SaverLibraryActivity.this.getString(R.string.photo_capacity_suffix),16,Ui.ACCENT);
            uploadStatus.setPadding(0,dp(12),0,dp(8));details.addView(uploadStatus,new LinearLayout.LayoutParams(-1,-2));
            details.addView(Ui.text(this,SaverLibraryActivity.this.getString(R.string.choose_photos_then_save_to_tv_screensaver),13,Ui.MUTED),
                new LinearLayout.LayoutParams(-1,-2));
            uploadDialog = new AlertDialog.Builder(this).setTitle(SaverLibraryActivity.this.getString(R.string.upload_from_a_browser))
                .setView(content)
                .setPositiveButton(SaverLibraryActivity.this.getString(R.string.done_uploading),null).create();
            uploadDialog.setOnDismissListener(d -> { uploadDialog = null; stopUpload(); library(); }); uploadDialog.show();
            if(uploadDialog.getWindow()!=null)uploadDialog.getWindow().setLayout(
                Math.min(dp(740),getResources().getDisplayMetrics().widthPixels-dp(32)),-2);
        } catch (Exception error) { stopUpload(); toast(SaverLibraryActivity.this.getString(R.string.could_not_start_uploads_check_the_network)); }
    }
    private void receive(java.io.InputStream input) throws Exception {
        try { SaverImages.add(this,input); }
        catch (Exception error) { throw new java.io.IOException(UiLabels.photoFailure(this,error), error); }
        post(() -> {
            updateButtons();
            if (uploadDialog != null && upload != null && uploadStatus!=null)
                uploadStatus.setText(SaverLibraryActivity.this.getString(R.string.photo_saved_prefix) + SaverImages.list(this).size() + SaverLibraryActivity.this.getString(R.string.photo_capacity_suffix));
        });
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
        uploadStatus=null;
    }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if (request == PERMISSION && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) sources();
        else if (request == PERMISSION) new AlertDialog.Builder(this).setTitle(SaverLibraryActivity.this.getString(R.string.photo_read_permission_not_granted))
            .setMessage(SaverLibraryActivity.this.getString(R.string.you_can_still_upload_photos_from_a))
            .setPositiveButton(SaverLibraryActivity.this.getString(R.string.ok),null).show();
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
            card.setContentDescription(entry.name + (entry.kind != IMAGE ? SaverLibraryActivity.this.getString(R.string.open_location) : checked ? SaverLibraryActivity.this.getString(R.string.photo_selected_suffix) : SaverLibraryActivity.this.getString(R.string.select_photo)));
            return card;
        }
    }
}
