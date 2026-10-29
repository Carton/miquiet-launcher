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
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.widget.BaseAdapter;
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
    private Ui.Icon networkIcon;
    private DashboardPanel dashboard;
    private SaverSettings saverSettings;
    private HomeAppearance appearance;
    private boolean desktopResumed;
    private final Runnable idleSaver = () -> {
        android.os.PowerManager power = (android.os.PowerManager)getSystemService(POWER_SERVICE);
        if (desktopResumed && hasWindowFocus() && power.isInteractive() && prefs.getBoolean("saver_enabled",true)) {
            startActivity(new Intent(this,ScreensaverActivity.class));
        }
    };
    private TextView previewTitle;
    private final TextView[] inputLabels = new TextView[4];
    private final TextView[] inputPorts = new TextView[4];
    private final View[] inputRows = new View[4];
    private TextView moveHint;
    private FavoriteMove favoriteMove;
    private int moveKeyToRelease = KeyEvent.KEYCODE_UNKNOWN;
    private LinearLayout dock;
    private FrameLayout previewBox;
    private View settingsButton;
    private Dialog settingsDialog;
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
        int savedPort = prefs.getInt("selected_port", 3);
        selectedPort = savedPort >= 1 && savedPort <= 3 ? savedPort : 3;
        apps = new Apps(this);
        IntentFilter changes = new IntentFilter();
        changes.addAction(Intent.ACTION_PACKAGE_ADDED);
        changes.addAction(Intent.ACTION_PACKAGE_REMOVED);
        changes.addAction(Intent.ACTION_PACKAGE_CHANGED);
        changes.addAction(Intent.ACTION_PACKAGE_REPLACED);
        changes.addDataScheme("package");
        registerReceiver(packageChanges, changes);
        actions = new DeviceActions(this);
        appearance = AppearanceStore.read(prefs);
        dashboard = new DashboardPanel(this, prefs, () -> launchPackage("com.xiaomi.tweather"), appearance);
        saverSettings = new SaverSettings(this,prefs);
        buildLayout();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        prefs.edit().putBoolean("bridge_paused", false).putBoolean("boot_pending", false).commit();
    }

    private int dp(float value) { return Ui.dp(this, value); }

    private void buildLayout() {
        root = new FrameLayout(this);
        root.setBackgroundColor(appearance.backgroundColor());
        setContentView(root);
        // The TV reports a 960 x 540 dp landscape display; center the design on other sizes.
        FrameLayout page = new FrameLayout(this);
        FrameLayout.LayoutParams pageParams = new FrameLayout.LayoutParams(dp(960), dp(540), Gravity.CENTER);
        root.addView(page, pageParams);
        topButton(page, "信号源", "hdmi", 12, 110, () -> actions.sources());
        moveHint = Ui.text(this, "移动位置  ·  ← → 调整  ·  确定保存  ·  返回取消", 14, Ui.ACCENT);
        moveHint.setGravity(Gravity.CENTER_VERTICAL);
        moveHint.setVisibility(View.GONE);
        Ui.place(page, moveHint, 470, 34, 130, 8);

        FrameLayout sound = iconButton("声音设置", "sound", () -> actions.panel("sound_settings"));
        Ui.place(page, sound, 38, 34, appearance.networkX() - 44, 8);
        FrameLayout network = iconButton("网络设置", "wifi", () -> actions.panel("connect_settings"));
        networkIcon = (Ui.Icon)network.getChildAt(0);
        Ui.place(page, network, 38, 34, appearance.networkX(), 8);
        if (appearance.showWeather) Ui.place(page, dashboard.weather, 146, 34, 696, 8);
        clock = Ui.text(this, "", 19, Color.WHITE);
        clock.setGravity(Gravity.CENTER);
        Ui.place(page, clock, 64, 34, 846, 8);
        settingsButton = iconButton("桌面设置", "settings", this::showSettings);
        Ui.place(page, settingsButton, 38, 34, 910, 8);

        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(Ui.shape(this, appearance.panelColor(), 0, 8));
        Ui.place(page, hero, 936, 332, 12, 54);
        if (appearance.showNews) Ui.place(hero, dashboard.news, 200, 332, 0, 0);

        previewBox = new FrameLayout(this);
        Ui.homeFocusable(previewBox, 0xff222b3c, 8, appearance);
        previewBox.setContentDescription("信号源预览，确定进入全屏");
        Ui.place(hero, previewBox, appearance.previewWidth(), 332, appearance.previewX(), 0);
        FrameLayout videoViewport = new FrameLayout(this);
        videoViewport.setClipChildren(true);
        Ui.place(previewBox, videoViewport, appearance.previewWidth() - 8, 324, 4, 4);
        TvView tv = new TvView(this);
        tv.setFocusable(false);
        tv.setClickable(false);
        videoViewport.addView(tv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        ImageView fallback = new ImageView(this);
        fallback.setScaleType(ImageView.ScaleType.FIT_CENTER);
        fallback.setBackgroundColor(0xff4061ce);
        try {
            android.content.res.Resources original = getPackageManager().getResourcesForApplication("com.mitv.tvhome");
            int id = original.getIdentifier("ic_input_source_default", "drawable", "com.mitv.tvhome");
            if (id != 0) fallback.setImageDrawable(original.getDrawable(id, null));
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            // The solid blue background remains usable if the original launcher is unavailable.
        }
        Ui.place(previewBox, fallback, appearance.previewWidth() - 8, 324, 4, 4);
        TextView hint = Ui.text(this, "正在连接信号源…", appearance.textSize(16), 0xffd6dce7);
        hint.setGravity(Gravity.CENTER);
        Ui.place(previewBox, hint, appearance.previewWidth() - 28, 260, 14, 12);
        previewTitle = Ui.text(this, inputDisplay(selectedPort), appearance.textSize(18), Color.WHITE);
        previewTitle.setShadowLayer(4, 0, 1, Color.BLACK);
        Ui.place(previewBox, previewTitle, appearance.previewWidth() - 34, 30, 16, 288);
        preview = new PreviewController(this, tv, hint, fallback);
        previewBox.setOnClickListener(v -> actions.fullscreen(selectedPort));

        String[] labels = {"HDMI3", "文件管理", "HDMI1", "HDMI2", "全部"};
        String[] icons = {"hdmi", "folder", "hdmi", "hdmi", "arrow"};
        int[] ports = {3, 0, 1, 2, 0};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            FrameLayout row = new FrameLayout(this);
            Ui.homeFocusable(row, appearance.panelColor(), 5, appearance);
            row.setContentDescription(labels[i]);
            Ui.place(row, new Ui.Icon(this, icons[i], Color.WHITE), 25, 25, 10, 20);
            TextView label = Ui.text(this, labels[i], appearance.textSize(18), Color.WHITE);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            if (ports[i] > 0) {
                LinearLayout caption = new LinearLayout(this);
                caption.setOrientation(LinearLayout.VERTICAL);
                caption.setGravity(Gravity.CENTER_VERTICAL);
                caption.addView(label, new LinearLayout.LayoutParams(-1, dp(26)));
                TextView port = Ui.text(this, labels[i], 12, Ui.MUTED);
                caption.addView(port, new LinearLayout.LayoutParams(-1, dp(20)));
                inputLabels[ports[i]] = label;
                inputPorts[ports[i]] = port;
                inputRows[ports[i]] = row;
                row.setOnLongClickListener(v -> {
                    InputSettings.edit(this, prefs, ports[index], this::refreshInputNames);
                    return true;
                });
                Ui.place(row, caption, 94, 66, 45, 0);
            } else {
                label.setGravity(Gravity.CENTER_VERTICAL);
                Ui.place(row, label, 94, 66, 45, 0);
            }
            Ui.place(hero, row, 144, 66, 792, i * 66 + 1);
            row.setOnFocusChangeListener((v, focused) -> {
                if (focused && ports[index] > 0) selectPort(ports[index]);
            });
            row.setOnClickListener(v -> {
                if (ports[index] > 0) { selectPort(ports[index]); actions.fullscreen(ports[index]); }
                else if (index == 1) actions.media();
                else actions.sources();
            });
        }
        refreshInputNames();

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setClipChildren(false);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFocusable(false);
        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setPadding(dp(6), dp(6), dp(6), 0);
        scroll.addView(dock, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Ui.place(page, scroll, 936, 130, 12, 398);
        renderDock();
        previewBox.requestFocus();
    }

    private void topButton(FrameLayout page, String label, String icon, int x, int width, Runnable action) {
        FrameLayout view = new FrameLayout(this);
        Ui.homeFocusable(view, appearance.newsColor(), 20, appearance);
        view.setContentDescription(label);
        Ui.place(view, new Ui.Icon(this, icon, Ui.MUTED), 20, 20, 12, 7);
        TextView text = Ui.text(this, label, appearance.textSize(15), 0xffd1d2d5);
        text.setGravity(Gravity.CENTER_VERTICAL);
        Ui.place(view, text, width - 42, 34, 38, 0);
        view.setOnClickListener(v -> action.run());
        Ui.place(page, view, width, 34, x, 8);
    }

    private FrameLayout iconButton(String description, String kind, Runnable action) {
        FrameLayout box = new FrameLayout(this);
        box.setContentDescription(description);
        Ui.homeFocusable(box, Color.TRANSPARENT, 7, appearance);
        Ui.place(box, new Ui.Icon(this, kind, Ui.MUTED), 25, 25, 6, 4);
        box.setOnClickListener(v -> action.run());
        return box;
    }

    private void selectPort(int port) {
        selectedPort = port;
        previewTitle.setText(inputDisplay(port));
        previewBox.setContentDescription(inputDisplay(port) + " 预览，确定进入全屏");
        prefs.edit().putInt("selected_port", port).apply();
        preview.select(port);
    }

    private String inputDisplay(int port) {
        return InputNames.display(port, prefs.getString(InputNames.key(port), ""));
    }

    private void refreshInputNames() {
        for (int port = 1; port <= 3; port++) {
            String name = InputNames.normalize(prefs.getString(InputNames.key(port), ""));
            inputLabels[port].setText(name.isEmpty() ? "HDMI" + port : name);
            inputPorts[port].setVisibility(name.isEmpty() ? View.GONE : View.VISIBLE);
            inputRows[port].setContentDescription(inputDisplay(port) + "，长按修改名称");
        }
        previewTitle.setText(inputDisplay(selectedPort));
        previewBox.setContentDescription(inputDisplay(selectedPort) + " 预览，确定进入全屏");
    }

    private void renderDock() {
        String focusKey = favoriteMove == null ? lastFocusKey : favoriteMove.item;
        dock.removeAllViews();
        for (String pkg : favoriteMove == null ? apps.favorites() : favoriteMove.current()) {
            Apps.App app = apps.all.get(pkg);
            if (app == null) continue;
            FrameLayout tile = appTile(app.label, app, false);
            tile.setTag(app.pkg);
            tile.setOnFocusChangeListener((v, focused) -> { if (focused) lastFocusKey = app.pkg; });
            tile.setOnClickListener(v -> {
                if (favoriteMove == null) actions.start(new Intent(app.intent));
                else if (favoriteMove.item.equals(app.pkg)) finishFavoriteMove(true);
            });
            tile.setOnLongClickListener(v -> { beginFavoriteMove(app); return true; });
            if (favoriteMove != null && favoriteMove.item.equals(pkg)) {
                tile.setBackground(Ui.shape(this, 0xff31564e, Ui.ACCENT, 10));
                tile.setContentDescription(app.label + "，正在移动，左右调整，确定保存，返回取消");
            }
            dock.addView(tile, new LinearLayout.LayoutParams(dp(132), dp(124)));
        }
        FrameLayout factory = appTile("原厂桌面", null, true, "factory");
        factory.setTag("factory");
        factory.setOnFocusChangeListener((v, focused) -> { if (focused) lastFocusKey = "factory"; });
        factory.setOnClickListener(v -> { if (favoriteMove == null) returnFactory(); });
        dock.addView(factory, new LinearLayout.LayoutParams(dp(132), dp(124)));
        FrameLayout all = appTile("我的应用", null, true);
        all.setTag("all");
        all.setOnFocusChangeListener((v, focused) -> { if (focused) lastFocusKey = "all"; });
        all.setOnClickListener(v -> { if (favoriteMove == null) showApps(new ArrayList<>(apps.all.values()), "我的应用"); });
        all.setOnLongClickListener(v -> { if (favoriteMove == null) configureFavorites(); return true; });
        dock.addView(all, new LinearLayout.LayoutParams(dp(132), dp(124)));
        if (focusKey != null) {
            View previous = dock.findViewWithTag(focusKey);
            if (previous != null) previous.requestFocus();
        }
    }

    private FrameLayout appTile(String label, Apps.App app, boolean allApps) {
        return appTile(label, app, allApps, "apps");
    }

    private FrameLayout appTile(String label, Apps.App app, boolean allApps, String iconKind) {
        FrameLayout tile = new FrameLayout(this);
        Ui.homeFocusable(tile, Color.TRANSPARENT, 10, appearance);
        tile.setPadding(dp(3), dp(3), dp(3), dp(3));
        tile.setContentDescription(label);
        if (allApps) {
            FrameLayout icon = new FrameLayout(this);
            icon.setBackground(Ui.shape(this, 0xff3b3e43, 0, 22));
            Ui.place(icon, new Ui.Icon(this, iconKind, Color.WHITE), 49, 49, 22, 22);
            Ui.place(tile, icon, 94, 94, 19, 2);
        } else {
            ImageView image = new ImageView(this);
            image.setImageDrawable(app.icon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackground(Ui.shape(this, appearance.panelColor(), 0, 20));
            image.setClipToOutline(true);
            Ui.place(tile, image, 94, 94, 19, 2);
        }
        TextView title = Ui.text(this, label, appearance.textSize(15), Ui.MUTED);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        Ui.place(tile, title, 128, 25, 2, 98);
        return tile;
    }

    private void updateStatus() {
        clock.setText(new SimpleDateFormat("HH:mm", Locale.CHINA).format(new java.util.Date()));
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = manager.getActiveNetworkInfo();
        boolean connected = info != null && info.isConnected();
        boolean wifi = connected && info.getType() == ConnectivityManager.TYPE_WIFI;
        int level = -1;
        if (wifi) {
            WifiManager wifiManager = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            WifiInfo signal = wifiManager.getConnectionInfo();
            if (signal != null && signal.getRssi() > -127 && signal.getRssi() < 0) {
                level = WifiManager.calculateSignalLevel(signal.getRssi(), 4);
            }
        }
        networkIcon.setNetwork(wifi, connected, level);
        ((View)networkIcon.getParent()).setContentDescription(!connected ? "网络未连接，打开网络设置" :
            wifi ? "Wi-Fi 已连接，" + (level < 0 ? "信号强度未知" : "信号 " + (level + 1) + "/4") + "，打开网络设置" : "有线网络已连接，打开网络设置");
        dashboard.tick();
    }

    private void showSettings() {
        if (settingsDialog != null && settingsDialog.isShowing()) return;
        settingsDialog = SettingsDialog.show(this,new Runnable[]{
            actions::settings, this::homeSettings, ()->actions.panel("connect_settings"),
            ()->actions.panel("bluetooth_settings"), ()->actions.panel("display_settings"),
            ()->actions.panel("sound_settings"), actions::media, ()->launchPackage("com.xiaomi.mitv.smartshare"),
            saverSettings::show, this::bridgeSettings, dashboard::showCredits, this::showAbout
        },()->{
            settingsDialog=null;
            if (!isFinishing() && settingsButton!=null) settingsButton.requestFocus();
        });
    }

    private void showAbout() {
        String version = "未知";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
        new AlertDialog.Builder(this).setTitle("关于")
            .setMessage("简净桌面 · MiTV Quiet Launcher\n版本 " + version
                + "\n\n源码与发布：\nhttps://github.com/Carton/mitv-quiet-launcher")
            .setPositiveButton("关闭", null).show();
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

    private void homeSettings() {
        new AlertDialog.Builder(this).setTitle("首页配置")
            .setItems(new String[]{"选择首页应用", "信号源名称", "外观与内容"}, (dialog, which) -> {
                if (which == 0) configureFavorites();
                else if (which == 1) InputSettings.show(this, prefs, this::refreshInputNames);
                else startActivity(new Intent(this, AppearanceActivity.class));
            }).setNegativeButton("关闭", null).show();
    }

    private void beginFavoriteMove(Apps.App app) {
        if (favoriteMove != null) return;
        favoriteMove = new FavoriteMove(apps.favorites(), app.pkg);
        lastFocusKey = app.pkg;
        moveHint.setVisibility(View.VISIBLE);
        handler.removeCallbacks(idleSaver);
        renderDock();
    }

    private void finishFavoriteMove(boolean save) {
        if (favoriteMove == null) return;
        List<String> order = favoriteMove.finish(save);
        lastFocusKey = favoriteMove.item;
        favoriteMove = null;
        if (save) apps.save(order);
        moveHint.setVisibility(View.GONE);
        renderDock();
        resetSaverTimer();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        // The release belonging to Save/Cancel must never click the newly rendered tile.
        if (key == moveKeyToRelease) {
            if (event.getAction() == KeyEvent.ACTION_UP) moveKeyToRelease = KeyEvent.KEYCODE_UNKNOWN;
            return true;
        }
        if (favoriteMove != null) {
            if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    resetSaverTimer();
                    if (favoriteMove.move(key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)) renderDock();
                }
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                    || key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_MENU) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    // A settings dialog owns the Menu release once it opens.
                    moveKeyToRelease = key == KeyEvent.KEYCODE_MENU ? KeyEvent.KEYCODE_UNKNOWN : key;
                    finishFavoriteMove(key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER);
                    if (key == KeyEvent.KEYCODE_MENU) showSettings();
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void showApps(List<Apps.App> entries, String title) {
        if (entries.isEmpty()) { Toast.makeText(this, "没有找到应用", Toast.LENGTH_SHORT).show(); return; }
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(16), dp(18), dp(16));
        content.setBackgroundColor(appearance.backgroundColor());
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
                if (view != null) view.setBackground(Ui.shape(MainActivity.this, appearance.focusColor(), appearance.focusStroke(), 10));
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
        moveKeyToRelease = KeyEvent.KEYCODE_UNKNOWN;
        HomeAppearance saved = AppearanceStore.read(prefs);
        if (!saved.equals(appearance)) {
            dashboard.close();
            appearance = saved;
            dashboard = new DashboardPanel(this, prefs, () -> launchPackage("com.xiaomi.tweather"), appearance);
            buildLayout();
        }
        desktopResumed = true; resetSaverTimer();
        android.util.Log.i("QuietTV", "Desktop resumed at=" + android.os.SystemClock.elapsedRealtime());
        if (preview != null) {
            if (appsDirty) {
                apps.reload(); renderDock(); appsDirty = false;
            }
            preview.select(selectedPort); preview.resume();
            handler.removeCallbacks(statusTick);
            handler.post(statusTick);
        }
    }

    @Override protected void onPause() {
        desktopResumed = false;
        finishFavoriteMove(false);
        handler.removeCallbacks(idleSaver);
        handler.removeCallbacks(statusTick);
        if (preview != null) preview.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (settingsDialog!=null) settingsDialog.dismiss();
        unregisterReceiver(packageChanges);
        handler.removeCallbacks(statusTick);
        dashboard.close();
        handler.removeCallbacks(idleSaver);
        super.onDestroy();
    }

    private void resetSaverTimer() {
        handler.removeCallbacks(idleSaver);
        if (desktopResumed && favoriteMove == null && prefs != null && hasWindowFocus() && prefs.getBoolean("saver_enabled",true)) {
            handler.postDelayed(idleSaver,SaverPolicy.waitMinutes(prefs.getInt("saver_wait_minutes",12)) * 60_000L);
        }
    }

    @Override public void onUserInteraction() { super.onUserInteraction(); resetSaverTimer(); }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); resetSaverTimer(); }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) { showSettings(); return true; }
        if (keyCode == KeyEvent.KEYCODE_BACK) { previewBox.requestFocus(); return true; }
        return super.onKeyDown(keyCode, event);
    }
}
