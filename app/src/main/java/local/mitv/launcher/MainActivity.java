package local.mitv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.tv.TvView;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private SharedPreferences prefs;
    private Apps apps;
    private DeviceActions actions;
    private PreviewController preview;
    private FrameLayout root;
    private TextView clock;
    private TextView connection;
    private TextView previewTitle;
    private LinearLayout dock;
    private FrameLayout previewBox;
    private int selectedPort;
    private String lastFocusKey;
    private boolean appsDirty;
    private final BroadcastReceiver packageChanges = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { appsDirty = true; }
    };
    private final Handler handler = new Handler();
    private final Runnable statusTick = new Runnable() {
        @Override public void run() { updateStatus(); handler.postDelayed(this, 15000); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        prefs = getSharedPreferences(Apps.PREFS, MODE_PRIVATE);
        prefs.edit().putBoolean("bridge_paused", false).putBoolean("boot_pending", false).commit();
        selectedPort = prefs.getInt("selected_port", 3);
        apps = new Apps(this);
        IntentFilter changes = new IntentFilter();
        changes.addAction(Intent.ACTION_PACKAGE_ADDED);
        changes.addAction(Intent.ACTION_PACKAGE_REMOVED);
        changes.addAction(Intent.ACTION_PACKAGE_CHANGED);
        changes.addAction(Intent.ACTION_PACKAGE_REPLACED);
        changes.addDataScheme("package");
        registerReceiver(packageChanges, changes);
        actions = new DeviceActions(this);
        buildLayout();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        prefs.edit().putBoolean("bridge_paused", false).putBoolean("boot_pending", false).commit();
    }

    private int dp(float value) { return Ui.dp(this, value); }

    private void buildLayout() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BACKGROUND);
        setContentView(root);
        // The TV reports a 960 x 540 dp landscape display; center the design on other sizes.
        FrameLayout page = new FrameLayout(this);
        FrameLayout.LayoutParams pageParams = new FrameLayout.LayoutParams(dp(960), dp(540), Gravity.CENTER);
        root.addView(page, pageParams);
        topButton(page, "信号源", "hdmi", 60, 110, () -> actions.sources());
        topButton(page, "搜索", "search", 178, 90, this::searchApps);

        FrameLayout sound = iconButton("声音设置", "sound", () -> actions.panel("sound_settings"));
        Ui.place(page, sound, 38, 34, 657, 17);
        FrameLayout network = iconButton("网络设置", "wifi", () -> actions.panel("connect_settings"));
        Ui.place(page, network, 38, 34, 701, 17);
        connection = Ui.text(this, "天气", 14, Ui.MUTED);
        connection.setGravity(Gravity.CENTER);
        connection.setContentDescription("打开原厂天气");
        Ui.focusable(connection, Color.TRANSPARENT, 7);
        connection.setOnClickListener(v -> launchPackage("com.xiaomi.tweather"));
        Ui.place(page, connection, 55, 34, 744, 17);
        clock = Ui.text(this, "", 19, Color.WHITE);
        clock.setGravity(Gravity.CENTER);
        Ui.place(page, clock, 64, 34, 800, 17);
        Ui.place(page, iconButton("桌面设置", "settings", this::showSettings), 38, 34, 868, 17);

        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(Ui.shape(this, Ui.PANEL, 0, 8));
        Ui.place(page, hero, 840, 282, 60, 82);

        previewBox = new FrameLayout(this);
        Ui.focusable(previewBox, 0xff222b3c, 8);
        previewBox.setContentDescription("信号源预览，确定进入全屏");
        Ui.place(hero, previewBox, 610, 282, 0, 0);
        TvView tv = new TvView(this);
        tv.setFocusable(false);
        tv.setClickable(false);
        Ui.place(previewBox, tv, 602, 274, 4, 4);
        ImageView fallback = new ImageView(this);
        fallback.setScaleType(ImageView.ScaleType.FIT_XY);
        fallback.setBackgroundColor(0xff4061ce);
        try {
            android.content.res.Resources original = getPackageManager().getResourcesForApplication("com.mitv.tvhome");
            int id = original.getIdentifier("ic_input_source_default", "drawable", "com.mitv.tvhome");
            if (id != 0) fallback.setImageDrawable(original.getDrawable(id, null));
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            // The solid blue background remains usable if the original launcher is unavailable.
        }
        Ui.place(previewBox, fallback, 602, 274, 4, 4);
        TextView hint = Ui.text(this, "正在连接信号源…", 16, 0xffd6dce7);
        hint.setGravity(Gravity.CENTER);
        Ui.place(previewBox, hint, 590, 220, 10, 10);
        previewTitle = Ui.text(this, "HDMI" + selectedPort, 18, Color.WHITE);
        previewTitle.setShadowLayer(4, 0, 1, Color.BLACK);
        Ui.place(previewBox, previewTitle, 480, 30, 16, 240);
        preview = new PreviewController(this, tv, hint, fallback);
        previewBox.setOnClickListener(v -> actions.fullscreen(selectedPort));

        String[] labels = {"HDMI3", "小米路由", "HDMI1", "HDMI2", "全部"};
        String[] icons = {"hdmi", "router", "hdmi", "hdmi", "arrow"};
        int[] ports = {3, 0, 1, 2, 0};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            FrameLayout row = new FrameLayout(this);
            Ui.focusable(row, Ui.PANEL, 5);
            row.setContentDescription(labels[i]);
            Ui.place(row, new Ui.Icon(this, icons[i], Color.WHITE), 27, 27, 20, 15);
            TextView label = Ui.text(this, labels[i], 18, Color.WHITE);
            label.setGravity(Gravity.CENTER_VERTICAL);
            Ui.place(row, label, 142, 56, 67, 0);
            Ui.place(hero, row, 226, 56, 612, i * 56);
            row.setOnFocusChangeListener((v, focused) -> {
                if (focused && ports[index] > 0) selectPort(ports[index]);
            });
            row.setOnClickListener(v -> {
                if (ports[index] > 0) { selectPort(ports[index]); actions.fullscreen(ports[index]); }
                else if (index == 1) actions.media();
                else actions.sources();
            });
        }

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setClipChildren(false);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFocusable(false);
        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setPadding(dp(6), dp(6), dp(6), 0);
        scroll.addView(dock, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Ui.place(page, scroll, 864, 132, 48, 388);
        renderDock();
        previewBox.requestFocus();
    }

    private void topButton(FrameLayout page, String label, String icon, int x, int width, Runnable action) {
        FrameLayout view = new FrameLayout(this);
        Ui.focusable(view, 0xff27292d, 20);
        view.setContentDescription(label);
        Ui.place(view, new Ui.Icon(this, icon, Ui.MUTED), 20, 20, 12, 7);
        TextView text = Ui.text(this, label, 15, 0xffd1d2d5);
        text.setGravity(Gravity.CENTER_VERTICAL);
        Ui.place(view, text, width - 42, 34, 38, 0);
        view.setOnClickListener(v -> action.run());
        Ui.place(page, view, width, 34, x, 17);
    }

    private FrameLayout iconButton(String description, String kind, Runnable action) {
        FrameLayout box = new FrameLayout(this);
        box.setContentDescription(description);
        Ui.focusable(box, Color.TRANSPARENT, 7);
        Ui.place(box, new Ui.Icon(this, kind, Ui.MUTED), 25, 25, 6, 4);
        box.setOnClickListener(v -> action.run());
        return box;
    }

    private void selectPort(int port) {
        selectedPort = port;
        previewTitle.setText("HDMI" + port);
        prefs.edit().putInt("selected_port", port).apply();
        preview.select(port);
    }

    private void renderDock() {
        dock.removeAllViews();
        for (String pkg : apps.favorites()) {
            Apps.App app = apps.all.get(pkg);
            if (app == null) continue;
            FrameLayout tile = appTile(app.label, app, false);
            tile.setTag(app.pkg);
            tile.setOnFocusChangeListener((v, focused) -> { if (focused) lastFocusKey = app.pkg; });
            tile.setOnClickListener(v -> actions.start(new Intent(app.intent)));
            tile.setOnLongClickListener(v -> { editFavorite(app); return true; });
            dock.addView(tile, new LinearLayout.LayoutParams(dp(140), dp(124)));
            if (app.pkg.equals(lastFocusKey)) tile.requestFocus();
        }
        FrameLayout all = appTile("我的应用", null, true);
        all.setTag("all");
        all.setOnFocusChangeListener((v, focused) -> { if (focused) lastFocusKey = "all"; });
        all.setOnClickListener(v -> showApps(new ArrayList<>(apps.all.values()), "我的应用"));
        all.setOnLongClickListener(v -> { configureFavorites(); return true; });
        dock.addView(all, new LinearLayout.LayoutParams(dp(140), dp(124)));
        if ("all".equals(lastFocusKey)) all.requestFocus();
    }

    private FrameLayout appTile(String label, Apps.App app, boolean allApps) {
        FrameLayout tile = new FrameLayout(this);
        Ui.focusable(tile, Color.TRANSPARENT, 10);
        tile.setPadding(dp(3), dp(3), dp(3), dp(3));
        tile.setContentDescription(label);
        if (allApps) {
            FrameLayout icon = new FrameLayout(this);
            icon.setBackground(Ui.shape(this, 0xff3b3e43, 0, 22));
            Ui.place(icon, new Ui.Icon(this, "apps", Color.WHITE), 49, 49, 22, 22);
            Ui.place(tile, icon, 94, 94, 23, 2);
        } else {
            ImageView image = new ImageView(this);
            image.setImageDrawable(app.icon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackground(Ui.shape(this, Ui.PANEL, 0, 20));
            image.setClipToOutline(true);
            Ui.place(tile, image, 94, 94, 23, 2);
        }
        TextView title = Ui.text(this, label, 15, Ui.MUTED);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        Ui.place(tile, title, 136, 25, 2, 98);
        return tile;
    }

    private void updateStatus() {
        clock.setText(new SimpleDateFormat("HH:mm", Locale.CHINA).format(new java.util.Date()));
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = manager.getActiveNetworkInfo();
        connection.setContentDescription("打开原厂天气；网络" + (info != null && info.isConnected() ? "已连接" : "未连接"));
    }

    private void showSettings() {
        String[] choices = {"配置首页应用", "调整应用顺序", "电视设置", "网络与连接", "蓝牙", "图像", "声音", "文件管理 / 小米路由", "无线投屏", "桌面接管与恢复", "返回原厂桌面"};
        new AlertDialog.Builder(this).setTitle("桌面设置").setItems(choices, (dialog, which) -> {
            switch (which) {
                case 0: configureFavorites(); break;
                case 1: orderFavorites(); break;
                case 2: actions.settings(); break;
                case 3: actions.panel("connect_settings"); break;
                case 4: actions.panel("bluetooth_settings"); break;
                case 5: actions.panel("display_settings"); break;
                case 6: actions.panel("sound_settings"); break;
                case 7: actions.media(); break;
                case 8: launchPackage("com.xiaomi.mitv.smartshare"); break;
                case 9: bridgeSettings(); break;
                case 10: returnFactory(); break;
                default: break;
            }
        }).setNegativeButton("关闭", null).show();
    }

    private void launchPackage(String pkg) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent == null) intent = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (intent != null) actions.start(intent);
        else Toast.makeText(this, "此应用没有独立入口，请从电视设置打开", Toast.LENGTH_LONG).show();
    }

    private void configureFavorites() {
        List<Apps.App> available = new ArrayList<>(apps.all.values());
        List<String> previous = apps.favorites();
        String[] names = new String[available.size()];
        boolean[] checked = new boolean[available.size()];
        for (int i = 0; i < available.size(); i++) {
            names[i] = available.get(i).label;
            checked[i] = previous.contains(available.get(i).pkg);
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("选择首页应用")
            .setMultiChoiceItems(names, checked, (d, index, selected) -> checked[index] = selected)
            .setPositiveButton("保存", (d, which) -> {
                List<String> selected = new ArrayList<>();
                for (String pkg : previous) {
                    for (int i = 0; i < available.size(); i++) if (checked[i] && available.get(i).pkg.equals(pkg)) selected.add(pkg);
                }
                for (int i = 0; i < available.size(); i++) if (checked[i] && !selected.contains(available.get(i).pkg)) selected.add(available.get(i).pkg);
                apps.save(selected); renderDock();
                previewBox.requestFocus();
            }).setNegativeButton("取消", null).create();
        dialog.show();
    }

    private void orderFavorites() {
        List<String> favorites = apps.favorites();
        if (favorites.isEmpty()) { configureFavorites(); return; }
        String[] names = new String[favorites.size()];
        for (int i = 0; i < names.length; i++) names[i] = apps.all.get(favorites.get(i)).label;
        new AlertDialog.Builder(this).setTitle("选择要调整的应用").setItems(names, (d, which) -> editFavorite(apps.all.get(favorites.get(which))))
            .setNegativeButton("关闭", null).show();
    }

    private void editFavorite(Apps.App app) {
        new AlertDialog.Builder(this).setTitle(app.label).setItems(new String[]{"向左移动", "向右移动", "从首页移除", "配置首页应用"}, (dialog, which) -> {
            List<String> favorites = apps.favorites();
            if (which == 0 || which == 1) apps.save(LauncherPolicy.move(favorites, app.pkg, which == 0 ? -1 : 1));
            else if (which == 2) { favorites.remove(app.pkg); apps.save(favorites); }
            else { configureFavorites(); return; }
            renderDock();
            previewBox.requestFocus();
        }).setNegativeButton("取消", null).show();
    }

    private void searchApps() {
        EditText query = new EditText(this);
        query.setSingleLine(true);
        query.setTextSize(20);
        query.setHint("输入应用名称");
        new AlertDialog.Builder(this).setTitle("搜索本机应用").setView(query).setPositiveButton("搜索", (dialog, which) -> {
            String keyword = query.getText().toString().trim().toLowerCase(Locale.ROOT);
            List<Apps.App> result = new ArrayList<>();
            for (Apps.App app : apps.all.values()) if (app.label.toLowerCase(Locale.ROOT).contains(keyword) || app.pkg.contains(keyword)) result.add(app);
            showApps(result, keyword.isEmpty() ? "我的应用" : "搜索结果");
        }).setNegativeButton("取消", null).show();
    }

    private void showApps(List<Apps.App> entries, String title) {
        if (entries.isEmpty()) { Toast.makeText(this, "没有找到应用", Toast.LENGTH_SHORT).show(); return; }
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(16), dp(18), dp(16));
        content.setBackgroundColor(Ui.BACKGROUND);
        TextView heading = Ui.text(this, title + "    ·    长按应用配置首页", 20, Color.WHITE);
        content.addView(heading, new LinearLayout.LayoutParams(-1, dp(42)));
        GridView grid = new GridView(this);
        grid.setNumColumns(5);
        grid.setVerticalSpacing(dp(12));
        grid.setHorizontalSpacing(dp(8));
        grid.setSelector(android.R.color.transparent);
        grid.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return entries.size(); }
            @Override public Object getItem(int position) { return entries.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convert, ViewGroup parent) {
                FrameLayout tile = appTile(entries.get(position).label, entries.get(position), false);
                tile.setFocusable(false); tile.setClickable(false);
                tile.setBackgroundColor(Color.TRANSPARENT);
                tile.setLayoutParams(new android.widget.AbsListView.LayoutParams(dp(140), dp(126)));
                return tile;
            }
        });
        grid.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                for (int i = 0; i < grid.getChildCount(); i++) grid.getChildAt(i).setBackgroundColor(Color.TRANSPARENT);
                if (view != null) view.setBackground(Ui.shape(MainActivity.this, 0xff464a50, Color.WHITE, 10));
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        grid.setOnItemClickListener((parent, view, position, id) -> { dialog.dismiss(); actions.start(new Intent(entries.get(position).intent)); });
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            Apps.App app = entries.get(position);
            List<String> favorites = apps.favorites();
            if (favorites.contains(app.pkg)) favorites.remove(app.pkg); else favorites.add(app.pkg);
            apps.save(favorites); renderDock();
            Toast.makeText(this, favorites.contains(app.pkg) ? "已添加到首页" : "已从首页移除", Toast.LENGTH_SHORT).show();
            return true;
        });
        content.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setContentView(content);
        Window window = dialog.getWindow();
        if (window != null) window.setLayout(dp(820), dp(430));
        dialog.show();
        if (window != null) window.setLayout(dp(820), dp(430));
        grid.requestFocus();
    }

    private boolean bridgeBound() {
        AccessibilityManager manager = (AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getId().startsWith(getPackageName() + "/")) return true;
        }
        return false;
    }

    private void bridgeSettings() {
        boolean enabled = prefs.getBoolean("bridge_enabled", false);
        String state = bridgeBound() ? (enabled ? "Home 接管已开启" : "无障碍服务已授权，接管已暂停") : "尚未授权无障碍服务";
        new AlertDialog.Builder(this).setTitle("桌面接管与恢复")
            .setItems(new String[]{state, "启用 Home 接管", "暂停接管", "打开系统无障碍设置", "返回原厂桌面"}, (dialog, which) -> {
                if (which == 1) {
                    getPackageManager().setComponentEnabledSetting(
                        new android.content.ComponentName(this, HomeBridgeService.class),
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        android.content.pm.PackageManager.DONT_KILL_APP);
                    prefs.edit().putBoolean("bridge_enabled", true).putBoolean("bridge_paused", false).commit();
                    if (!bridgeBound()) {
                        actions.accessibility();
                    }
                } else if (which == 2) {
                    prefs.edit().putBoolean("bridge_enabled", false).commit();
                    Toast.makeText(this, "已暂停 Home 接管", Toast.LENGTH_SHORT).show();
                } else if (which == 3) actions.accessibility();
                else if (which == 4) returnFactory();
            }).setNegativeButton("关闭", null).show();
    }

    private void returnFactory() {
        prefs.edit().putBoolean("bridge_paused", true).commit();
        if (!actions.factory()) prefs.edit().putBoolean("bridge_paused", false).commit();
    }

    @Override protected void onResume() {
        super.onResume();
        android.util.Log.i("QuietTV", "Desktop resumed at=" + android.os.SystemClock.elapsedRealtime());
        if (preview != null) {
            if (appsDirty) {
                apps.reload(); renderDock(); appsDirty = false;
            }
            preview.select(selectedPort); preview.resume();
            handler.post(statusTick);
        }
    }

    @Override protected void onPause() {
        handler.removeCallbacks(statusTick);
        if (preview != null) preview.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        unregisterReceiver(packageChanges);
        super.onDestroy();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) { showSettings(); return true; }
        if (keyCode == KeyEvent.KEYCODE_BACK) { previewBox.requestFocus(); return true; }
        return super.onKeyDown(keyCode, event);
    }
}
