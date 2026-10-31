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
    private final HomeAppearance appearance;
    final FrameLayout weather, news;
    private final ImageView weatherIcon;
    private final TextView temperature, description, newsMeta;
    private final TextView[] tabs = new TextView[NewsSources.IDS.length], headlines = new TextView[3];
    private final HorizontalScrollView tabScroll;
    private int category;
    private int page;
    private final TextView next;
    private List<DashboardRepository.Story> shown = new ArrayList<>();

    DashboardPanel(Activity activity, SharedPreferences prefs, Runnable originalWeather, HomeAppearance appearance) {
        this.activity = activity; this.prefs = prefs; this.originalWeather = originalWeather;
        this.appearance = appearance;
        data = new DashboardRepository(activity, prefs, this::render);
        category = NewsSources.index(prefs.getInt("news_tab", 0));
        weather = new FrameLayout(activity);
        Ui.homeFocusable(weather, Color.TRANSPARENT, 7, appearance);
        weather.setOnClickListener(v -> {
            data.refreshWeather(true);
            originalWeather.run();
        });
        weather.setOnLongClickListener(v -> { weatherSettings(); return true; });
        weatherIcon = new ImageView(activity);
        Ui.place(weather, weatherIcon, 30, 30, 0, 2);
        temperature = Ui.text(activity, "--°C", 19, Color.WHITE);
        temperature.setGravity(Gravity.CENTER);
        Ui.place(weather, temperature, 62, 34, 30, 0);
        description = Ui.text(activity, "--", UiLocale.english(activity) ? 10 : 12, Ui.MUTED);
        description.setSingleLine(true); description.setEllipsize(TextUtils.TruncateAt.END);
        description.setGravity(Gravity.CENTER);
        Ui.place(weather, description, 54, 34, 92, 0);

        news = new FrameLayout(activity);
        news.setBackground(Ui.shape(activity, appearance.newsColor(), 0, 8));
        tabScroll = new HorizontalScrollView(activity);
        tabScroll.setFocusable(false); tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setFadingEdgeLength(Ui.dp(activity, 6)); tabScroll.setHorizontalFadingEdgeEnabled(true);
        LinearLayout strip = new LinearLayout(activity); strip.setOrientation(LinearLayout.HORIZONTAL);
        tabScroll.addView(strip);
        Ui.place(news, tabScroll, 184, 36, 8, 8);
        TextView moreTabs = Ui.text(activity, "›", 15, Ui.MUTED); moreTabs.setGravity(Gravity.CENTER);
        Ui.place(news, moreTabs, 8, 36, 192, 8);
        final int tabWidth = UiLocale.english(activity) ? 76 : 60;
        tabScroll.setOnScrollChangeListener((v,x,y,oldX,oldY) -> moreTabs.setText(x < Ui.dp(activity, tabWidth * tabs.length - 184) ? "›" : ""));
        tabScroll.setOnGenericMotionListener((v,event) -> {
            if (event.getAction() != android.view.MotionEvent.ACTION_SCROLL) return false;
            float amount = event.getAxisValue(android.view.MotionEvent.AXIS_HSCROLL);
            if (amount == 0) amount = -event.getAxisValue(android.view.MotionEvent.AXIS_VSCROLL);
            if (amount == 0) return false;
            tabScroll.smoothScrollBy(Math.round(amount * Ui.dp(activity, 60)),0); return true;
        });
        for (int i = 0; i < tabs.length; i++) {
            final int tab = i;
            tabs[i] = Ui.text(activity, activity.getString(UiLabels.NEWS[i]), appearance.textSize(15), Color.WHITE);
            tabs[i].setId(View.generateViewId()); tabs[i].setGravity(Gravity.CENTER);
            Ui.homeFocusable(tabs[i], Color.TRANSPARENT, 5, appearance);
            tabs[i].setOnClickListener(v -> {
                category = tab; page = 0; prefs.edit().putInt("news_tab", category).apply(); refreshNews();
            });
            tabs[i].setOnFocusChangeListener((v,focused) -> {
                if (focused) v.requestRectangleOnScreen(new android.graphics.Rect(0,0,v.getWidth(),v.getHeight()));
            });
            strip.addView(tabs[i],new LinearLayout.LayoutParams(Ui.dp(activity,tabWidth),Ui.dp(activity,36)));
        }
        for (int i = 0; i < tabs.length; i++) {
            if (i > 0) tabs[i].setNextFocusLeftId(tabs[i-1].getId());
            if (i + 1 < tabs.length) tabs[i].setNextFocusRightId(tabs[i+1].getId());
        }
        tabScroll.post(() -> tabScroll.scrollTo(Ui.dp(activity,
            Math.max(0, category * tabWidth - (184 - tabWidth) / 2)),0));
        for (int i = 0; i < headlines.length; i++) {
            final int index = i;
            headlines[i] = Ui.text(activity, "", appearance.textSize(14), 0xffe2e4e8);
            headlines[i].setGravity(Gravity.CENTER_VERTICAL);
            headlines[i].setPadding(Ui.dp(activity, 6), 0, Ui.dp(activity, 6), 0);
            headlines[i].setMaxLines(3);
            headlines[i].setEllipsize(TextUtils.TruncateAt.END);
            headlines[i].setLineSpacing(Ui.dp(activity, 2), 1);
            Ui.homeFocusable(headlines[i], Color.TRANSPARENT, 5, appearance);
            headlines[i].setOnClickListener(v -> { if (index < shown.size()) showStory(shown.get(index)); });
            Ui.place(news, headlines[i], 184, 80, 8, 48 + i * 83);
        }
        newsMeta = Ui.text(activity, activity.getString(R.string.cctv_updating), 10, Ui.MUTED);
        newsMeta.setSingleLine(true); newsMeta.setEllipsize(TextUtils.TruncateAt.END);
        newsMeta.setGravity(Gravity.CENTER_VERTICAL);
        Ui.place(news, newsMeta, 128, 25, 14, 300);
        next = Ui.text(activity, activity.getString(R.string.more), 11, Ui.MUTED);
        next.setGravity(Gravity.CENTER);
        Ui.homeFocusable(next, Color.TRANSPARENT, 4, appearance);
        next.setOnClickListener(v -> { page++; render(); });
        next.setOnLongClickListener(v -> { refreshNews(); return true; });
        Ui.place(news, next, 48, 25, 146, 300);
        render();
    }

    void tick() {
        if (!appearance.showNews && !appearance.showWeather) return;
        if (appearance.showWeather) data.refreshWeather(false);
        if (appearance.showNews) data.refreshNews(category, false);
        render();
    }
    private void refreshNews() {
        boolean requested = data.refreshNews(category,true); render();
        Toast.makeText(activity, activity.getString(UiLabels.NEWS[category]) + (requested ? activity.getString(R.string.news_is_refreshing) : activity.getString(R.string.news_is_updating_please_wait)), Toast.LENGTH_SHORT).show();
    }
    void close() { data.close(); }

    private void render() {
        long now = System.currentTimeMillis();
        DashboardRepository.Weather current = data.weather;
        boolean valid = current != null && DashboardPolicy.weatherAvailable(current.time, now, data.weatherFailed);
        if (valid) {
            temperature.setText(Math.round(current.temperature) + "°C");
            description.setText(activity.getString(UiLabels.weather(DashboardPolicy.condition(current.code))));
            String icon = DashboardPolicy.icon(current.code, current.day);
            weatherIcon.setImageResource(activity.getResources().getIdentifier("weather_" + icon, "drawable", activity.getPackageName()));
            weather.setContentDescription(temperature.getText() + " " + description.getText() + activity.getString(R.string.ok_to_refresh_and_open_xiaomi_weather));
        } else {
            temperature.setText("--°C");
            description.setText("--");
            weatherIcon.setImageResource(R.drawable.weather_cloud_question);
            weather.setContentDescription(activity.getString(R.string.weather_unavailable_ok_to_refresh_and_open));
        }
        List<DashboardRepository.Story> source = data.stories(category);
        boolean failed = data.failed(category);
        List<DashboardRepository.Story> recent = new ArrayList<>();
        for (DashboardRepository.Story story : source) {
            if (DashboardPolicy.fresh(story.time, now, DashboardRepository.newsMaxAge(category))) recent.add(story);
        }
        int pages = Math.max(1, (recent.size() + 2) / 3);
        page %= pages;
        shown = recent.subList(page * 3, Math.min(page * 3 + 3, recent.size()));
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setTextColor(i == category ? Ui.ACCENT : Ui.MUTED);
            tabs[i].setText(activity.getString(UiLabels.NEWS[i]));
            tabs[i].setContentDescription(activity.getString(UiLabels.NEWS[i]) + (i == category ? activity.getString(R.string.choice_selected_suffix) : "") + activity.getString(R.string.ok_to_switch_and_refresh_left_right));
            Ui.homeFocusable(tabs[i], i == category ? 0xff303e3e : Color.TRANSPARENT, 5, appearance);
        }
        for (int i = 0; i < headlines.length; i++) {
            TextView view = headlines[i];
            if (i < shown.size()) {
                view.setText(shown.get(i).title); view.setEnabled(true);
                view.setContentDescription(shown.get(i).title + activity.getString(R.string.ok_to_read_the_summary));
            } else {
                view.setText(i == 0 ? (failed ? activity.getString(R.string.news_update_failed_will_retry_automatically) : data.busy(category) ? activity.getString(R.string.loading_the_latest_headlines) : activity.getString(R.string.no_recent_news_select_a_category_to)) : "");
                view.setEnabled(false);
            }
        }
        String time = shown.isEmpty() ? "" : format(shown.get(0).time, "MM-dd HH:mm");
        String publisher = NewsSources.publishers(UiLocale.english(activity))[category];
        newsMeta.setText(publisher + (data.busy(category) ? activity.getString(R.string.updating) : failed ? activity.getString(R.string.cached) : "") + (time.isEmpty() || data.busy(category) ? "" : " · " + time));
        newsMeta.setTextSize(failed || publisher.length() > 3 ? 9 : 10);
        next.setText(pages > 1 ? (page + 1) + "/" + pages + " ›" : activity.getString(R.string.refresh));
        next.setContentDescription(pages > 1 ? activity.getString(R.string.next_news_page_hold_ok_to_refresh) : activity.getString(R.string.refresh_news));
        next.setOnClickListener(v -> { if (pages > 1) { page++; render(); } else refreshNews(); });
    }

    private String format(long time, String pattern) {
        return new SimpleDateFormat(pattern, UiLocale.locale(activity)).format(new Date(time));
    }

    private void showStory(DashboardRepository.Story story) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity).setTitle(story.title)
            .setMessage(story.publisher + " · " + format(story.time, activity.getString(R.string.mmm_d_hh_mm)) + "\n\n" + (story.brief.isEmpty() ? activity.getString(R.string.no_summary_is_available_for_this_story) : story.brief))
            .setPositiveButton(activity.getString(R.string.close), null);
        if (NewsReaderActivity.canRead(activity,story.url)) dialog.setNeutralButton(story.body.isEmpty() ? activity.getString(R.string.read_article) : activity.getString(R.string.read_feed_content), (d,w) ->
            activity.startActivity(new Intent(activity, NewsReaderActivity.class).putExtra("url",story.url).putExtra("title",story.title).putExtra("publisher",story.publisher).putExtra("body",story.body)));
        dialog.show();
    }

    void weatherSettings() {
        String updated = data.weather == null ? activity.getString(R.string.no_data_yet) : activity.getString(R.string.updated) + format(data.weather.time, "MM-dd HH:mm");
        new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.weather_settings))
            .setItems(new String[]{activity.getString(R.string.choose_weather_location), activity.getString(R.string.use_automatic_network_location), activity.getString(R.string.refresh_now) + updated}, (d, which) -> {
                if (which == 0) choosePlace();
                else if (which == 1) data.setPlace(null);
                else { data.refreshWeather(true); Toast.makeText(activity, activity.getString(R.string.refreshing_weather), Toast.LENGTH_SHORT).show(); }
            }).setNegativeButton(activity.getString(R.string.close), null).show();
    }

    private void choosePlace() {
        EditText input = new EditText(activity);
        input.setSingleLine(true); input.setTextSize(20); input.setHint(activity.getString(R.string.enter_a_city_or_district_e_g));
        new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.weather_location)).setView(input)
            .setPositiveButton(activity.getString(R.string.search), (d, w) -> {
                String query = input.getText().toString().trim();
                if (query.isEmpty()) return;
                Toast.makeText(activity, activity.getString(R.string.searching_for_locations), Toast.LENGTH_SHORT).show();
                data.searchPlaces(query, (places, error) -> {
                    if (activity.isFinishing()) return;
                    if (error != null || places.isEmpty()) {
                        Toast.makeText(activity, error != null ? error : activity.getString(R.string.no_location_found_try_a_city_name), Toast.LENGTH_LONG).show(); return;
                    }
                    String[] labels = new String[places.size()];
                    for (int i = 0; i < labels.length; i++) labels[i] = places.get(i).label;
                    new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.choose_weather_location)).setItems(labels, (dialog, index) -> data.setPlace(places.get(index)))
                        .setNegativeButton(activity.getString(R.string.cancel), null).show();
                });
            }).setNegativeButton(activity.getString(R.string.cancel), null).show();
    }

    void showCredits() {
        new AlertDialog.Builder(activity).setTitle(activity.getString(R.string.sources_licenses))
            .setMessage(activity.getString(R.string.weather_open_meteo_cc_by_4_0)
                + activity.getString(R.string.open_meteo_https_open_meteo_com_location) + licenseText(R.raw.tabler_license) + "\n\n" + licenseText(R.raw.lucide_license) + "\n\n" + licenseText(R.raw.qrcodegen_license))
            .setPositiveButton(activity.getString(R.string.close), null).show();
    }

    private String licenseText(int resource) {
        try (java.io.InputStream input = activity.getResources().openRawResource(resource);
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] bytes = new byte[2048]; int count;
            while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
            return output.toString("UTF-8");
        } catch (java.io.IOException error) { return activity.getString(R.string.tabler_icons_mit_lucide_isc_feather_mit); }
    }
}
