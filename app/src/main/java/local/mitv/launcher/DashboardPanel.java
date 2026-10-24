package local.mitv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Remote-friendly weather control and a left column that can gain more tabs later. */
final class DashboardPanel {
    private final Activity activity;
    private final SharedPreferences prefs;
    private final Runnable originalWeather;
    private final DashboardRepository data;
    final FrameLayout weather, news;
    private final ImageView weatherIcon;
    private final TextView temperature, description, newsMeta;
    private final TextView[] tabs = new TextView[2], headlines = new TextView[3];
    private int category;
    private int page;
    private final TextView next;
    private List<DashboardRepository.Story> shown = new ArrayList<>();

    DashboardPanel(Activity activity, SharedPreferences prefs, Runnable originalWeather) {
        this.activity = activity; this.prefs = prefs; this.originalWeather = originalWeather;
        data = new DashboardRepository(activity, prefs, this::render);
        category = prefs.getInt("news_tab", 0) == 1 ? 1 : 0;
        weather = new FrameLayout(activity);
        Ui.focusable(weather, Color.TRANSPARENT, 7);
        weather.setOnClickListener(v -> weatherSettings());
        weatherIcon = new ImageView(activity);
        Ui.place(weather, weatherIcon, 30, 30, 0, 2);
        temperature = Ui.text(activity, "--°C", 19, Color.WHITE);
        temperature.setGravity(Gravity.CENTER);
        Ui.place(weather, temperature, 62, 34, 30, 0);
        description = Ui.text(activity, "更新中", 12, Ui.MUTED);
        description.setGravity(Gravity.CENTER);
        Ui.place(weather, description, 54, 34, 92, 0);

        news = new FrameLayout(activity);
        news.setBackground(Ui.shape(activity, 0xff282b30, 0, 8));
        for (int i = 0; i < tabs.length; i++) {
            final int tab = i;
            tabs[i] = Ui.text(activity, i == 0 ? "国内" : "国际", 16, Color.WHITE);
            tabs[i].setGravity(Gravity.CENTER);
            Ui.focusable(tabs[i], Color.TRANSPARENT, 6);
            tabs[i].setOnClickListener(v -> {
                category = tab; page = 0; prefs.edit().putInt("news_tab", category).apply(); render();
            });
            Ui.place(news, tabs[i], 78, 32, 12 + i * 88, 10);
        }
        for (int i = 0; i < headlines.length; i++) {
            final int index = i;
            headlines[i] = Ui.text(activity, "", 14, 0xffe2e4e8);
            headlines[i].setGravity(Gravity.CENTER_VERTICAL);
            headlines[i].setPadding(Ui.dp(activity, 6), 0, Ui.dp(activity, 6), 0);
            headlines[i].setMaxLines(3);
            headlines[i].setEllipsize(TextUtils.TruncateAt.END);
            headlines[i].setLineSpacing(Ui.dp(activity, 2), 1);
            Ui.focusable(headlines[i], Color.TRANSPARENT, 5);
            headlines[i].setOnClickListener(v -> { if (index < shown.size()) showStory(shown.get(index)); });
            Ui.place(news, headlines[i], 174, 62, 8, 48 + i * 65);
        }
        newsMeta = Ui.text(activity, "央视网 · 正在更新", 10, Ui.MUTED);
        newsMeta.setGravity(Gravity.CENTER_VERTICAL);
        Ui.place(news, newsMeta, 128, 25, 14, 250);
        next = Ui.text(activity, "更多 ›", 11, Ui.MUTED);
        next.setGravity(Gravity.CENTER);
        Ui.focusable(next, Color.TRANSPARENT, 4);
        next.setOnClickListener(v -> { page++; render(); });
        next.setOnLongClickListener(v -> { data.refresh(true); Toast.makeText(activity, "正在刷新天气和新闻", Toast.LENGTH_SHORT).show(); return true; });
        Ui.place(news, next, 48, 25, 139, 250);
        render();
    }

    void tick() { data.refresh(false); render(); }
    void close() { data.close(); }

    private void render() {
        long now = System.currentTimeMillis();
        DashboardRepository.Weather current = data.weather;
        boolean valid = current != null && DashboardPolicy.fresh(current.time, now, DashboardRepository.WEATHER_MAX_AGE);
        if (valid) {
            temperature.setText(Math.round(current.temperature) + "°C");
            boolean cached = data.weatherFailed || now - current.time > 60 * 60_000L;
            description.setText(DashboardPolicy.description(current.code) + (cached ? "\n缓存" : ""));
            String icon = DashboardPolicy.icon(current.code, current.day);
            weatherIcon.setImageResource(activity.getResources().getIdentifier("weather_" + icon, "drawable", activity.getPackageName()));
            weather.setContentDescription(temperature.getText() + " " + description.getText() + "，确定打开天气设置");
        } else {
            temperature.setText("--°C");
            description.setText(data.weatherFailed || current != null ? "暂不可用" : "更新中");
            weatherIcon.setImageResource(R.drawable.weather_cloud_question);
            weather.setContentDescription("天气暂不可用，确定打开设置选择位置或重试");
        }
        List<DashboardRepository.Story> source = category == 0 ? data.china : data.world;
        boolean failed = category == 0 ? data.chinaFailed : data.worldFailed;
        List<DashboardRepository.Story> recent = new ArrayList<>();
        for (DashboardRepository.Story story : source) {
            if (DashboardPolicy.fresh(story.time, now, DashboardRepository.NEWS_MAX_AGE)) recent.add(story);
        }
        int pages = Math.max(1, (recent.size() + 2) / 3);
        page %= pages;
        shown = recent.subList(page * 3, Math.min(page * 3 + 3, recent.size()));
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setTextColor(i == category ? Ui.ACCENT : Ui.MUTED);
            tabs[i].setText((i == 0 ? "国内" : "国际") + (i == category ? " ·" : ""));
        }
        for (int i = 0; i < headlines.length; i++) {
            TextView view = headlines[i];
            if (i < shown.size()) {
                view.setText(shown.get(i).title); view.setEnabled(true);
                view.setContentDescription(shown.get(i).title + "，确定查看摘要");
            } else {
                view.setText(i == 0 ? (failed ? "新闻更新失败\n稍后将自动重试" : source.isEmpty() ? "正在获取最新头条…" : "暂无近期新闻") : "");
                view.setEnabled(false);
            }
        }
        String time = recent.isEmpty() ? "" : format(recent.get(0).time, "MM-dd HH:mm");
        newsMeta.setText("央视网" + (failed ? " · 缓存" : "") + (time.isEmpty() ? "" : " · " + time));
        newsMeta.setTextSize(failed ? 9 : 10);
        next.setText(pages > 1 ? (page + 1) + "/" + pages + " ›" : "刷新");
        next.setContentDescription(pages > 1 ? "下一页新闻，长按刷新" : "刷新新闻");
        next.setOnClickListener(v -> { if (pages > 1) { page++; render(); } else data.refresh(true); });
    }

    private String format(long time, String pattern) {
        return new SimpleDateFormat(pattern, Locale.CHINA).format(new Date(time));
    }

    private void showStory(DashboardRepository.Story story) {
        new AlertDialog.Builder(activity).setTitle(story.title)
            .setMessage("央视网 · " + format(story.time, "MM月dd日 HH:mm") + "\n\n" + (story.brief.isEmpty() ? "此条新闻暂无摘要。" : story.brief))
            .setPositiveButton("关闭", null)
            .setNeutralButton("打开原文", (d, w) -> {
                try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(story.url))); }
                catch (android.content.ActivityNotFoundException error) {
                    Toast.makeText(activity, "电视未安装可打开网页的浏览器", Toast.LENGTH_LONG).show();
                }
            }).show();
    }

    void weatherSettings() {
        String location = prefs.getBoolean("weather_manual", false) ? prefs.getString("weather_place", "手动位置") : "网络自动定位（约略位置）";
        String updated = data.weather == null ? "尚无可用数据" : "数据时间 " + format(data.weather.time, "MM-dd HH:mm");
        new AlertDialog.Builder(activity).setTitle("天气设置")
            .setItems(new String[]{"位置：" + location, "选择天气位置（首页不显示城市）", "使用网络自动定位", "立即刷新 · " + updated, "打开原厂天气", "数据与开源说明"}, (d, which) -> {
                if (which == 0 || which == 1) choosePlace();
                else if (which == 2) data.setPlace(null);
                else if (which == 3) { data.refresh(true); Toast.makeText(activity, "正在刷新", Toast.LENGTH_SHORT).show(); }
                else if (which == 4) originalWeather.run();
                else if (which == 5) showCredits();
            }).setNegativeButton("关闭", null).show();
    }

    private void choosePlace() {
        EditText input = new EditText(activity);
        input.setSingleLine(true); input.setTextSize(20); input.setHint("输入城市或区县，如：上海");
        new AlertDialog.Builder(activity).setTitle("天气位置").setView(input)
            .setPositiveButton("搜索", (d, w) -> {
                String query = input.getText().toString().trim();
                if (query.isEmpty()) return;
                Toast.makeText(activity, "正在搜索位置…", Toast.LENGTH_SHORT).show();
                data.searchPlaces(query, (places, error) -> {
                    if (activity.isFinishing()) return;
                    if (error != null || places.isEmpty()) {
                        Toast.makeText(activity, error != null ? error : "没有找到位置，请尝试城市名或拼音", Toast.LENGTH_LONG).show(); return;
                    }
                    String[] labels = new String[places.size()];
                    for (int i = 0; i < labels.length; i++) labels[i] = places.get(i).label;
                    new AlertDialog.Builder(activity).setTitle("选择天气位置").setItems(labels, (dialog, index) -> data.setPlace(places.get(index)))
                        .setNegativeButton("取消", null).show();
                });
            }).setNegativeButton("取消", null).show();
    }

    private void showCredits() {
        new AlertDialog.Builder(activity).setTitle("数据与开源说明")
            .setMessage(DashboardRepository.WEATHER_CREDIT
                + "\n\nOpen-Meteo：https://open-meteo.com/\n位置搜索：Open-Meteo / GeoNames（https://www.geonames.org/）\n数据许可：https://creativecommons.org/licenses/by/4.0/\n图标：https://tabler.io/icons\n网络位置：IPWhois（IP 约略定位，可手动更改）\n新闻标题与摘要：央视网，版权归原权利人，非开源内容。\n\n天气约每 15 分钟、新闻约每 10 分钟更新；失败时保留近期缓存并标记。超过 6 小时的天气、48 小时的新闻停止显示。\n\n" + licenseText())
            .setPositiveButton("关闭", null).show();
    }

    private String licenseText() {
        try (java.io.InputStream input = activity.getResources().openRawResource(R.raw.tabler_license);
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] bytes = new byte[2048]; int count;
            while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
            return output.toString("UTF-8");
        } catch (java.io.IOException error) { return "Tabler Icons · MIT · Copyright (c) 2020-2024 Paweł Kuna"; }
    }
}
