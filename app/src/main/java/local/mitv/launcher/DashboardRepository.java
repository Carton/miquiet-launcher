package local.mitv.launcher;

import android.content.SharedPreferences;
import android.content.Context;
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Independent, bounded background requests; never run on the Home bridge thread. */
final class DashboardRepository {
    static final long WEATHER_MAX_AGE = 6 * 60 * 60_000L;
    static final long NEWS_MAX_AGE = 48 * 60 * 60_000L;
    static final String NEWS_BASE = "https://news.cctv.com/2019/07/gaiban/cmsdatainterface/page/";
    static final String WEATHER_CREDIT = "天气：Open-Meteo · CC BY 4.0；图标：Tabler Icons · MIT";

    static final class Weather {
        final double temperature;
        final int code;
        final boolean day;
        final long time;
        Weather(double temperature, int code, boolean day, long time) {
            this.temperature = temperature; this.code = code; this.day = day; this.time = time;
        }
    }

    static final class Story {
        final String title, brief, url, publisher, body;
        final long time;
        Story(String title, String brief, String url, long time) {
            this(title, brief, url, time, "央视网", "");
        }
        Story(String title, String brief, String url, long time, String publisher, String body) {
            this.title = title; this.brief = brief; this.url = url; this.time = time;
            this.publisher = publisher; this.body = body;
        }
    }

    static final class Place {
        final String label;
        final double latitude, longitude;
        Place(String label, double latitude, double longitude) {
            this.label = label; this.latitude = latitude; this.longitude = longitude;
        }
    }

    interface PlacesListener { void result(List<Place> places, String error); }
    private final SharedPreferences prefs;
    private final Context context;
    private final Handler main = new Handler();
    private final ExecutorService worker = Executors.newFixedThreadPool(3);
    private final Runnable changed;
    private volatile boolean closed;
    private long nextWeather;
    private boolean weatherBusy;
    private final long[] nextNews = new long[NewsSources.IDS.length];
    private final boolean[] newsBusy = new boolean[NewsSources.IDS.length], newsFailed = new boolean[NewsSources.IDS.length];
    private final List<List<Story>> news = new ArrayList<>();
    private int locationGeneration;
    Weather weather;
    boolean weatherFailed;

    DashboardRepository(Context context, SharedPreferences prefs, Runnable changed) {
        this.context = context.getApplicationContext(); this.prefs = prefs; this.changed = changed;
        try { weather = parseWeather(new JSONObject(prefs.getString("weather_cache", "{}"))); }
        catch (Exception ignored) {}
        for (int i = 0; i < NewsSources.IDS.length; i++) {
            news.add(Collections.emptyList());
            final int category = i;
            worker.execute(() -> {
                try {
                    List<Story> cached = parseFeed(prefs.getString("news_" + NewsSources.IDS[category] + "_cache", ""),category);
                    deliver(() -> {
                        // A live response may already have won the race. Never replace it with cache.
                        if (news.get(category).isEmpty()) { news.set(category,cached); changed.run(); }
                    });
                } catch (Exception ignored) { /* Empty or old-format cache is harmless. */ }
            });
        }
    }

    private void deliver(Runnable action) {
        if (!closed) main.post(() -> { if (!closed) action.run(); });
    }

    void refresh(int category) { refreshWeather(false); refreshNews(category, false); }
    List<Story> stories(int category) { return news.get(NewsSources.index(category)); }
    boolean failed(int category) { return newsFailed[NewsSources.index(category)]; }
    boolean busy(int category) { return newsBusy[NewsSources.index(category)]; }
    static long newsMaxAge(int category) { return category==3 ? ScienceNews.MAX_AGE : NEWS_MAX_AGE; }
    void refreshWeather(boolean force) {
        if (!closed && !weatherBusy && (force || System.currentTimeMillis() >= nextWeather)) fetchWeather();
    }
    boolean refreshNews(int category, boolean force) {
        int index = NewsSources.index(category);
        if (closed || newsBusy[index] || (!force && System.currentTimeMillis() < nextNews[index])) return false;
        fetchNews(index); return true;
    }

    private void fetchWeather() {
        weatherBusy = true;
        nextWeather = System.currentTimeMillis() + 15 * 60_000L;
        final int generation = locationGeneration;
        final boolean manual = prefs.getBoolean("weather_manual", false);
        final double savedLat = Double.longBitsToDouble(prefs.getLong("weather_lat", 0));
        final double savedLon = Double.longBitsToDouble(prefs.getLong("weather_lon", 0));
        worker.execute(() -> {
            try {
                double latitude = savedLat, longitude = savedLon;
                if (!manual) {
                    // The TV's own outbound IP is used, not the development computer's location.
                    JSONObject ip = new JSONObject(get("https://ipwho.is/?fields=success,latitude,longitude"));
                    if (!ip.optBoolean("success")) throw new IllegalArgumentException("Location unavailable");
                    latitude = ip.getDouble("latitude"); longitude = ip.getDouble("longitude");
                }
                if (!validCoordinates(latitude, longitude)) throw new IllegalArgumentException("Invalid location");
                String address = String.format(Locale.ROOT,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.5f&longitude=%.5f&current=temperature_2m,weather_code,is_day&temperature_unit=celsius&timeformat=unixtime&forecast_days=1",
                    latitude, longitude);
                JSONObject data = new JSONObject(get(address));
                Weather fresh = parseWeather(data);
                if (!DashboardPolicy.fresh(fresh.time, System.currentTimeMillis(), WEATHER_MAX_AGE)) {
                    throw new IllegalArgumentException("Weather is outdated");
                }
                deliver(() -> {
                    weatherBusy = false;
                    if (generation != locationGeneration) { fetchWeather(); return; }
                    weather = fresh; weatherFailed = false;
                    prefs.edit().putString("weather_cache", data.toString()).apply();
                    Log.i("QuietTVData", "weather updated code=" + fresh.code + " at=" + fresh.time);
                    changed.run();
                });
            } catch (Exception error) {
                deliver(() -> {
                    weatherBusy = false;
                    if (generation != locationGeneration) { fetchWeather(); return; }
                    weatherFailed = true; nextWeather = System.currentTimeMillis() + 2 * 60_000L;
                    Log.w("QuietTVData", "weather: " + error.getClass().getSimpleName() + ": " + error.getMessage());
                    changed.run();
                });
            }
        });
    }

    private void fetchNews(int category) {
        newsBusy[category] = true;
        nextNews[category] = System.currentTimeMillis() + 10 * 60_000L;
        worker.execute(() -> {
            try {
                String response;
                boolean partial=false;
                if(category==3) {
                    String[] cached={"",""};
                    try { cached=unpackScience(prefs.getString("news_tech_cache","")); } catch(Exception ignored) {}
                    ScienceNews.Batch batch=ScienceNews.load(cached,i->get(NewsSources.SCIENCE_URLS[i]),System.currentTimeMillis());
                    response=packScience(batch.responses);partial=batch.partial;
                } else response=get(NewsSources.URLS[category]);
                List<Story> stories = parseFeed(response, category);
                if (stories.isEmpty()) throw new IllegalArgumentException("No recent headlines");
                final boolean incomplete=partial;
                deliver(() -> {
                    newsBusy[category] = false; news.set(category, stories); newsFailed[category] = incomplete;
                    if(incomplete)nextNews[category]=System.currentTimeMillis()+2*60_000L;
                    prefs.edit().putString("news_" + NewsSources.IDS[category] + "_cache", response).apply();
                    Log.i("QuietTVData", NewsSources.IDS[category] + " updated count=" + stories.size() + " partial=" + incomplete + " newest=" + stories.get(0).time);
                    changed.run();
                });
            } catch (Exception error) {
                deliver(() -> {
                    newsBusy[category] = false; newsFailed[category] = true;
                    nextNews[category] = System.currentTimeMillis() + 2 * 60_000L;
                    Log.w("QuietTVData", NewsSources.IDS[category] + ": " + error.getClass().getSimpleName() + ": " + error.getMessage());
                    changed.run();
                });
            }
        });
    }

    void setPlace(Place place) {
        locationGeneration++;
        SharedPreferences.Editor edit = prefs.edit().putBoolean("weather_manual", place != null)
            .remove("weather_cache");
        if (place != null) edit.putString("weather_place", place.label)
            .putLong("weather_lat", Double.doubleToLongBits(place.latitude))
            .putLong("weather_lon", Double.doubleToLongBits(place.longitude));
        edit.apply(); weather = null; weatherFailed = false; nextWeather = 0;
        changed.run();
        if (!weatherBusy) fetchWeather();
    }

    void searchPlaces(String query, PlacesListener listener) {
        worker.execute(() -> {
            try {
                JSONObject data = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=8&language=zh&format=json&name="
                    + URLEncoder.encode(query, "UTF-8")));
                JSONArray rows = data.optJSONArray("results");
                List<Place> places = new ArrayList<>();
                if (rows != null) for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    double lat = row.getDouble("latitude"), lon = row.getDouble("longitude");
                    if (!validCoordinates(lat, lon)) continue;
                    String label = row.getString("name") + " · " + row.optString("admin1") + " · " + row.optString("country");
                    places.add(new Place(label, lat, lon));
                }
                deliver(() -> listener.result(places, null));
            } catch (Exception error) { deliver(() -> listener.result(Collections.emptyList(), "位置搜索失败，请稍后重试")); }
        });
    }

    static boolean validCoordinates(double lat, double lon) {
        return !Double.isNaN(lat) && !Double.isNaN(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180;
    }

    static Weather parseWeather(JSONObject data) throws Exception {
        JSONObject current = data.getJSONObject("current");
        if (!"°C".equals(data.getJSONObject("current_units").getString("temperature_2m"))) {
            throw new IllegalArgumentException("Unexpected temperature unit");
        }
        double temperature = current.getDouble("temperature_2m");
        if (Double.isNaN(temperature) || temperature < -100 || temperature > 70) throw new IllegalArgumentException("Invalid temperature");
        return new Weather(temperature, current.getInt("weather_code"), current.getInt("is_day") == 1,
            current.getLong("time") * 1000L);
    }

    static List<Story> parseStories(String jsonp, String category) throws Exception {
        JSONArray rows = new JSONObject(DashboardPolicy.unwrap(jsonp, category)).getJSONObject("data").getJSONArray("list");
        List<Story> result = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        format.setLenient(false); format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        long now = System.currentTimeMillis();
        for (int i = 0; i < rows.length(); i++) {
            try {
                JSONObject row = rows.getJSONObject(i);
                String title = Html.fromHtml(row.getString("title")).toString().trim();
                String url = row.getString("url");
                URL parsed = new URL(url);
                if (!(parsed.getProtocol().equals("https") || parsed.getProtocol().equals("http"))
                    || !parsed.getHost().equals("news.cctv.com") || title.isEmpty() || !seen.add(url)) continue;
                Date date = format.parse(row.getString("focus_date"));
                if (date == null || !DashboardPolicy.fresh(date.getTime(), now, NEWS_MAX_AGE)) continue;
                result.add(new Story(title, Html.fromHtml(row.optString("brief")).toString().trim(), url, date.getTime()));
            } catch (Exception ignored) { /* Skip a malformed item without discarding the remaining feed. */ }
        }
        Collections.sort(result, (a, b) -> Long.compare(b.time, a.time));
        return Collections.unmodifiableList(new ArrayList<>(result.subList(0, Math.min(12, result.size()))));
    }

    static List<Story> parseFeed(String response, int category) throws Exception {
        if (category < 2) return parseStories(response, NewsSources.IDS[category]);
        List<Story> result = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        long now = System.currentTimeMillis();
        if(category==3) {
            String[] feeds=unpackScience(response);
            List<ScienceNews.Item> first=feeds[0].isEmpty()?Collections.emptyList():ScienceNews.parse(0,feeds[0]);
            List<ScienceNews.Item> second=feeds[1].isEmpty()?Collections.emptyList():ScienceNews.parse(1,feeds[1]);
            for(ScienceNews.Item item:ScienceNews.merge(first,second,now)) {
                String brief=plain(item.brief);
                result.add(new Story(plain(item.title),brief.length()>360?brief.substring(0,360)+"…":brief,
                    item.url,item.time,item.publisher,""));
            }
            return Collections.unmodifiableList(result);
        }
        if (category == 2) {
            JSONArray rows = new JSONObject(response).getJSONObject("data").getJSONArray("items");
            for (int i = 0; i < rows.length(); i++) {
                try {
                    JSONObject row = rows.getJSONObject(i);
                    String text = plain(row.optString("content_text"));
                    String title = plain(row.optString("title"));
                    if (title.isEmpty()) title = text;
                    addStory(result, seen, category, title, text, row.getString("uri"), row.getLong("display_time") * 1000L,
                        "<p>" + Html.escapeHtml(text).replace("\n", "</p><p>") + "</p>", now);
                } catch (Exception ignored) { /* Individual malformed flash. */ }
            }
        } else if (category == 4) {
            String value = response.trim();
            if (!value.startsWith("(") || !value.endsWith(");")) throw new IllegalArgumentException("Unexpected game response");
            JSONObject data = new JSONObject(value.substring(1,value.length()-2));
            if (!data.getString("status").equals("ok")) throw new IllegalArgumentException("Game source unavailable");
            for (GameNews.Item item : GameNews.parse(data.getString("body"))) {
                String title = plain(item.title), brief = plain(item.brief);
                if (GameNews.relevant(title,brief)) addStory(result,seen,category,title,brief,item.url,item.time,"",now);
            }
        } else {
            for (RssFeed.Item item : RssFeed.parse(response)) {
                String title = plain(item.title);
                String body = item.body();
                // RSS link footers become dead text in a remote-only reader; retain the
                // publisher credit in our own header instead of filling the summary with URLs.
                if (category == 5) body = body.replaceAll("(?is)<p>\\s*(?:🔗|via\\s+AIHOT\\b).*?</p\\s*>","");
                addStory(result, seen, category, title, plain(body), item.url, RssFeed.date(item.date), body, now);
            }
        }
        Collections.sort(result, (a, b) -> Long.compare(b.time, a.time));
        return Collections.unmodifiableList(new ArrayList<>(result.subList(0, Math.min(12, result.size()))));
    }

    static String packScience(String[] feeds) throws Exception {
        return new JSONObject().put("scienceVersion",1).put("feeds",new JSONArray().put(feeds[0]).put(feeds[1])).toString();
    }
    static String[] unpackScience(String response) throws Exception {
        JSONObject value=new JSONObject(response);
        if(value.getInt("scienceVersion")!=1)throw new IllegalArgumentException("Unsupported science cache");
        JSONArray feeds=value.getJSONArray("feeds");
        if(feeds.length()!=2)throw new IllegalArgumentException("Invalid science cache");
        return new String[]{feeds.getString(0),feeds.getString(1)};
    }

    private static String plain(String html) {
        return Html.fromHtml(html).toString().replace("\ufffc", "").replace('\u00a0', ' ').replaceAll("\\n{3,}","\n\n").trim();
    }
    private static void addStory(List<Story> result, HashSet<String> seen, int category, String title,
                                 String text, String url, long time, String body, long now) {
        String address = NewsReaderPolicy.address(url);
        try {
            if (address == null || !NewsSources.articleHost(category, new URL(address).getHost())
                || title.isEmpty() || !DashboardPolicy.fresh(time, now, NEWS_MAX_AGE) || !seen.add(address)) return;
            String brief = text.length() > 360 ? text.substring(0, 360) + "…" : text;
            // AIHOT publishes curated summaries, not full articles. Read the supplied feed text
            // even for a short item; never attempt to load an unrelated upstream social page.
            String article = !text.isEmpty() && (category == 2 || category == 5 || text.length() >= 500)
                && body.length() <= 60_000 ? body : "";
            result.add(new Story(title.length() > 160 ? title.substring(0, 160) + "…" : title,
                brief, address, time, NewsSources.PUBLISHERS[category], article));
        } catch (java.net.MalformedURLException ignored) {}
    }

    private String get(String address) throws Exception {
        return request(context,address);
    }
    static String request(Context context,String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)new URL(address).openConnection();
        connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
        connection.setRequestProperty("User-Agent", "QuietTV/0.7 (personal Android TV launcher)");
        connection.setInstanceFollowRedirects(false);
        try {
            if (connection instanceof javax.net.ssl.HttpsURLConnection) {
                LegacyTls.configure((javax.net.ssl.HttpsURLConnection)connection, context.getResources());
            }
            if (connection.getResponseCode() != 200) throw new java.io.IOException("HTTP " + connection.getResponseCode());
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
                    if (output.size() + count > 512 * 1024) throw new java.io.IOException("Response too large");
                    output.write(buffer, 0, count);
                }
                return output.toString("UTF-8");
            }
        } finally { connection.disconnect(); }
    }

    void close() { closed = true; main.removeCallbacksAndMessages(null); worker.shutdownNow(); }
}
