package local.mitv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
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
    private final TextView[] tabs = new TextView[NewsSources.IDS.length], headlines = new TextView[3];
    private final HorizontalScrollView tabScroll;
    private int category;
    private int page;
    private final TextView next;
    private List<DashboardRepository.Story> shown = new ArrayList<>();

    DashboardPanel(Activity activity, SharedPreferences prefs, Runnable originalWeather) {
        this.activity = activity; this.prefs = prefs; this.originalWeather = originalWeather;
        data = new DashboardRepository(activity, prefs, this::render);
        category = NewsSources.index(prefs.getInt("news_tab", 0));
        weather = new FrameLayout(activity);
        Ui.focusable(weather, Color.TRANSPARENT, 7);
        weather.setOnClickListener(v -> originalWeather.run());
        weather.setOnLongClickListener(v -> { weatherSettings(); return true; });
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
        tabScroll = new HorizontalScrollView(activity);
        tabScroll.setFocusable(false); tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setFadingEdgeLength(Ui.dp(activity, 6)); tabScroll.setHorizontalFadingEdgeEnabled(true);
        LinearLayout strip = new LinearLayout(activity); strip.setOrientation(LinearLayout.HORIZONTAL);
        tabScroll.addView(strip);
        Ui.place(news, tabScroll, 184, 36, 8, 8);
        TextView moreTabs = Ui.text(activity, "›", 15, Ui.MUTED); moreTabs.setGravity(Gravity.CENTER);
        Ui.place(news, moreTabs, 8, 36, 192, 8);
        tabScroll.setOnScrollChangeListener((v,x,y,oldX,oldY) -> moreTabs.setText(x < Ui.dp(activity, 176) ? "›" : ""));
        tabScroll.setOnGenericMotionListener((v,event) -> {
            if (event.getAction() != android.view.MotionEvent.ACTION_SCROLL) return false;
            float amount = event.getAxisValue(android.view.MotionEvent.AXIS_HSCROLL);
            if (amount == 0) amount = -event.getAxisValue(android.view.MotionEvent.AXIS_VSCROLL);
            if (amount == 0) return false;
            tabScroll.smoothScrollBy(Math.round(amount * Ui.dp(activity, 60)),0); return true;
        });
        for (int i = 0; i < tabs.length; i++) {
            final int tab = i;
            tabs[i] = Ui.text(activity, NewsSources.LABELS[i], 15, Color.WHITE);
            tabs[i].setId(View.generateViewId()); tabs[i].setGravity(Gravity.CENTER);
            Ui.focusable(tabs[i], Color.TRANSPARENT, 5);
            tabs[i].setOnClickListener(v -> {
                category = tab; page = 0; prefs.edit().putInt("news_tab", category).apply(); refreshNews();
            });
            tabs[i].setOnFocusChangeListener((v,focused) -> {
                if (focused) v.requestRectangleOnScreen(new android.graphics.Rect(0,0,v.getWidth(),v.getHeight()));
            });
            strip.addView(tabs[i],new LinearLayout.LayoutParams(Ui.dp(activity,60),Ui.dp(activity,36)));
        }
        for (int i = 0; i < tabs.length; i++) {
            if (i > 0) tabs[i].setNextFocusLeftId(tabs[i-1].getId());
            if (i + 1 < tabs.length) tabs[i].setNextFocusRightId(tabs[i+1].getId());
        }
        tabScroll.post(() -> tabScroll.scrollTo(Ui.dp(activity,Math.max(0,category-2)*60),0));
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
            Ui.place(news, headlines[i], 184, 80, 8, 48 + i * 83);
        }
        newsMeta = Ui.text(activity, "央视网 · 正在更新", 10, Ui.MUTED);
        newsMeta.setSingleLine(true); newsMeta.setEllipsize(TextUtils.TruncateAt.END);
        newsMeta.setGravity(Gravity.CENTER_VERTICAL);
        Ui.place(news, newsMeta, 128, 25, 14, 300);
        next = Ui.text(activity, "更多 ›", 11, Ui.MUTED);
        next.setGravity(Gravity.CENTER);
        Ui.focusable(next, Color.TRANSPARENT, 4);
        next.setOnClickListener(v -> { page++; render(); });
        next.setOnLongClickListener(v -> { refreshNews(); return true; });
        Ui.place(news, next, 48, 25, 146, 300);
        render();
    }

    void tick() { data.refresh(category); render(); }
    private void refreshNews() {
        boolean requested = data.refreshNews(category,true); render();
        Toast.makeText(activity, NewsSources.LABELS[category] + (requested ? "新闻正在刷新" : "新闻正在更新，请稍候"), Toast.LENGTH_SHORT).show();
    }
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
            weather.setContentDescription(temperature.getText() + " " + description.getText() + "，确定打开小米天气，长按设置位置");
        } else {
            temperature.setText("--°C");
            description.setText(data.weatherFailed || current != null ? "暂不可用" : "更新中");
            weatherIcon.setImageResource(R.drawable.weather_cloud_question);
            weather.setContentDescription("天气暂不可用，确定打开小米天气，长按选择位置或重试");
        }
        List<DashboardRepository.Story> source = data.stories(category);
        boolean failed = data.failed(category);
        List<DashboardRepository.Story> recent = new ArrayList<>();
        for (DashboardRepository.Story story : source) {
            if (DashboardPolicy.fresh(story.time, now, DashboardRepository.NEWS_MAX_AGE)) recent.add(story);
        }
        int pages = Math.max(1, (recent.size() + 2) / 3);
        page %= pages;
        shown = recent.subList(page * 3, Math.min(page * 3 + 3, recent.size()));
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setTextColor(i == category ? Ui.ACCENT : Ui.MUTED);
            tabs[i].setText(NewsSources.LABELS[i]);
            tabs[i].setContentDescription(NewsSources.LABELS[i] + (i == category ? "，已选择" : "") + "，确定切换并刷新，左右查看其他分类");
            Ui.focusable(tabs[i], i == category ? 0xff303e3e : Color.TRANSPARENT, 5);
        }
        for (int i = 0; i < headlines.length; i++) {
            TextView view = headlines[i];
            if (i < shown.size()) {
                view.setText(shown.get(i).title); view.setEnabled(true);
                view.setContentDescription(shown.get(i).title + "，确定查看摘要");
            } else {
                view.setText(i == 0 ? (failed ? "新闻更新失败\n稍后将自动重试" : data.busy(category) ? "正在获取最新头条…" : "暂无近期新闻，点击分类重试") : "");
                view.setEnabled(false);
            }
        }
        String time = recent.isEmpty() ? "" : format(recent.get(0).time, "MM-dd HH:mm");
        newsMeta.setText(NewsSources.PUBLISHERS[category] + (data.busy(category) ? " · 更新中" : failed ? " · 缓存" : "") + (time.isEmpty() || data.busy(category) ? "" : " · " + time));
        newsMeta.setTextSize(failed || NewsSources.PUBLISHERS[category].length() > 3 ? 9 : 10);
        next.setText(pages > 1 ? (page + 1) + "/" + pages + " ›" : "刷新");
        next.setContentDescription(pages > 1 ? "下一页新闻，长按刷新" : "刷新新闻");
        next.setOnClickListener(v -> { if (pages > 1) { page++; render(); } else refreshNews(); });
    }

    private String format(long time, String pattern) {
        return new SimpleDateFormat(pattern, Locale.CHINA).format(new Date(time));
    }

    private void showStory(DashboardRepository.Story story) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity).setTitle(story.title)
            .setMessage(story.publisher + " · " + format(story.time, "MM月dd日 HH:mm") + "\n\n" + (story.brief.isEmpty() ? "此条新闻暂无摘要。" : story.brief))
            .setPositiveButton("关闭", null);
        if (NewsReaderActivity.canRead(activity,story.url)) dialog.setNeutralButton(story.body.isEmpty() ? "阅读原文" : "阅读订阅内容", (d,w) ->
            activity.startActivity(new Intent(activity, NewsReaderActivity.class).putExtra("url",story.url).putExtra("title",story.title).putExtra("publisher",story.publisher).putExtra("body",story.body)));
        dialog.show();
    }

    void weatherSettings() {
        String updated = data.weather == null ? "尚无可用数据" : "数据时间 " + format(data.weather.time, "MM-dd HH:mm");
        new AlertDialog.Builder(activity).setTitle("天气设置")
            .setItems(new String[]{"选择天气位置", "使用网络自动定位", "立即刷新 · " + updated}, (d, which) -> {
                if (which == 0) choosePlace();
                else if (which == 1) data.setPlace(null);
                else { data.refreshWeather(true); Toast.makeText(activity, "正在刷新天气", Toast.LENGTH_SHORT).show(); }
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

    void showCredits() {
        new AlertDialog.Builder(activity).setTitle("数据与开源说明")
            .setMessage(DashboardRepository.WEATHER_CREDIT
                + "\n\nOpen-Meteo：https://open-meteo.com/\n位置搜索：Open-Meteo / GeoNames（https://www.geonames.org/）\n数据许可：https://creativecommons.org/licenses/by/4.0/\n图标：https://tabler.io/icons\n网络位置：IPWhois（IP 约略定位，可手动更改）\n新闻：央视网、华尔街见闻、IT之家、机核、量子位；版权归原权利人，非开源内容。分类与订阅交互参考 NewsNow（MIT）和 Read You（GPL-3.0），未打包其应用代码。\n\n天气约每 15 分钟、新闻约每 10 分钟更新；失败时保留近期缓存并标记。超过 6 小时的天气、48 小时的新闻停止显示。\n\n" + licenseText())
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
