package local.mitv.launcher;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.FileInputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocketFactory;

/** Run with app_process on API 23 using the real Android JSON, HTML and TLS implementations. */
public final class DashboardDataTest {
    private static int assertions;
    private static void check(boolean value) { assertions++; if (!value) throw new AssertionError("Data assertion " + assertions); }
    private static JSONObject row(String title, String url, long time) throws Exception {
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
        date.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return new JSONObject().put("title", title).put("url", url).put("brief", "<b>摘要</b>")
            .put("focus_date", date.format(new Date(time)));
    }
    private static String feed(JSONArray rows) throws Exception {
        return "china(" + new JSONObject().put("data", new JSONObject().put("list", rows)) + ");";
    }
    public static void main(String[] args) {
        try { run(args); }
        catch (Throwable error) { error.printStackTrace(); System.exit(1); }
    }
    private static void run(String[] args) throws Exception {
        long now = System.currentTimeMillis();
        JSONObject response = new JSONObject().put("current_units", new JSONObject().put("temperature_2m", "°C"))
            .put("current", new JSONObject().put("temperature_2m", -3.6).put("weather_code", 73).put("is_day", 0).put("time", now / 1000));
        DashboardRepository.Weather weather = DashboardRepository.parseWeather(response);
        check(weather.temperature == -3.6 && weather.code == 73 && !weather.day && Math.abs(weather.time - now) < 1000);
        response.getJSONObject("current_units").put("temperature_2m", "°F");
        try { DashboardRepository.parseWeather(response); throw new AssertionError("Fahrenheit accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
        response.getJSONObject("current_units").put("temperature_2m", "°C");
        response.getJSONObject("current").put("temperature_2m", JSONObject.NULL);
        try { DashboardRepository.parseWeather(response); throw new AssertionError("Null temperature accepted"); }
        catch (org.json.JSONException expected) { assertions++; }
        JSONArray rows = new JSONArray()
            .put(row("旧消息", "https://news.cctv.com/old.shtml", now - 49 * 60 * 60_000L))
            .put(row("较早", "https://news.cctv.com/earlier.shtml", now - 120_000))
            .put(row("未来错误时间", "https://news.cctv.com/future.shtml", now + 600_000))
            .put(row("外站链接", "https://example.com/", now))
            .put(row("脚本链接", "javascript:alert(1)", now))
            .put(row("最新 &amp; 标题", "https://news.cctv.com/latest.shtml", now - 60_000))
            .put(row("重复", "https://news.cctv.com/latest.shtml", now - 60_000))
            .put(new JSONObject().put("title", "坏数据"));
        List<DashboardRepository.Story> stories = DashboardRepository.parseStories(feed(rows), "china");
        check(stories.size() == 2);
        check(stories.get(0).title.equals("最新 & 标题") && stories.get(0).brief.equals("摘要"));
        check(stories.get(0).time > stories.get(1).time);
        check(DashboardRepository.parseStories(feed(new JSONArray()), "china").isEmpty());
        check(!DashboardRepository.validCoordinates(Double.NaN, 0));
        check(!DashboardRepository.validCoordinates(91, 0));
        check(DashboardRepository.validCoordinates(-90, 180));
        String body = "这是一条用于验证解析的内容。";
        StringBuilder longer = new StringBuilder(); for (int i = 0; i < 60; i++) longer.append(body);
        JSONObject flash = new JSONObject().put("title","财经 &amp; 快讯").put("content_text",longer.toString())
            .put("uri","http://wallstreetcn.com/livenews/123").put("display_time",now/1000-30);
        JSONObject invalidFlash = new JSONObject(flash.toString()).put("uri","https://wallstreetcn.com.attacker.test/123");
        List<DashboardRepository.Story> finance = DashboardRepository.parseFeed(new JSONObject().put("data",new JSONObject()
            .put("items",new JSONArray().put(flash).put(flash).put(invalidFlash))).toString(),2);
        check(finance.size() == 1 && finance.get(0).publisher.equals("华尔街见闻"));
        check(finance.get(0).title.equals("财经 & 快讯") && finance.get(0).url.startsWith("https://"));
        check(finance.get(0).brief.length() == 361 && finance.get(0).body.contains(body));
        SimpleDateFormat rssDate = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z",Locale.US);
        rssDate.setTimeZone(TimeZone.getTimeZone("GMT"));
        String rssItem = "<item><title>科技 &amp; 中文</title><link>https://www.ithome.com/1/2/3.htm</link><pubDate>"
            + rssDate.format(new Date(now-30_000)) + "</pubDate><description><![CDATA[<p>" + longer + "</p>]]></description></item>";
        String rss = "<rss><channel>" + rssItem + rssItem + rssItem.replace("www.ithome.com/1", "www.ithome.com.attacker.test/1") + "</channel></rss>";
        List<DashboardRepository.Story> tech = DashboardRepository.parseFeed(rss,3);
        check(tech.size() == 1 && tech.get(0).publisher.equals("IT之家"));
        check(tech.get(0).title.equals("科技 & 中文") && tech.get(0).body.contains("<p>"));
        check(DashboardRepository.parseFeed(rss.replace("科技 &amp; 中文","京东补贴优惠券"),3).isEmpty());
        check(DashboardRepository.parseFeed(rss.replace(rssDate.format(new Date(now-30_000)),rssDate.format(new Date(now-49*60*60_000L))),3).isEmpty());
        check(DashboardRepository.parseFeed(rss,4).isEmpty());
        System.out.println("PASS: " + assertions + " Android data assertions");
        if (args.length >= 2) {
            SSLSocketFactory sockets;
            try (FileInputStream isrg = new FileInputStream(args[0]); FileInputStream google = new FileInputStream(args[1])) {
                sockets = LegacyTls.factory(isrg, google);
            }
            if (args.length == 3 && args[2].equals("--news")) {
                liveNews(sockets); return;
            }
            for (String host : new String[]{"ipwho.is", "api.open-meteo.com", "geocoding-api.open-meteo.com"}) {
                HttpsURLConnection connection = (HttpsURLConnection)new URL("https://" + host + "/").openConnection();
                connection.setSSLSocketFactory(sockets); connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
                try { connection.connect(); System.out.println("TLS VALID: " + host); }
                finally { connection.disconnect(); }
            }
            for (String host : new String[]{"self-signed.badssl.com", "wrong.host.badssl.com", "expired.badssl.com"}) {
                HttpsURLConnection connection = (HttpsURLConnection)new URL("https://" + host + "/").openConnection();
                connection.setSSLSocketFactory(sockets); connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
                try { connection.connect(); throw new AssertionError("Invalid TLS accepted: " + host); }
                catch (SSLHandshakeException | SSLPeerUnverifiedException expected) { System.out.println("TLS REJECTED: " + host); }
                finally { connection.disconnect(); }
            }
        }
    }

    private static void liveNews(SSLSocketFactory sockets) throws Exception {
        for (int i = 0; i < NewsSources.IDS.length; i++) {
            HttpsURLConnection connection = (HttpsURLConnection)new URL(NewsSources.URLS[i]).openConnection();
            connection.setSSLSocketFactory(sockets); connection.setConnectTimeout(8000); connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false); connection.setRequestProperty("User-Agent","QuietTV/0.7");
            try {
                if (connection.getResponseCode() != 200) throw new AssertionError("HTTP " + connection.getResponseCode() + " " + NewsSources.IDS[i]);
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                try (java.io.InputStream input = connection.getInputStream()) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (output.size() + count > 512*1024) throw new AssertionError("Oversize feed");
                        output.write(buffer,0,count);
                    }
                }
                List<DashboardRepository.Story> stories = DashboardRepository.parseFeed(output.toString("UTF-8"),i);
                if (stories.isEmpty()) throw new AssertionError("No recent " + NewsSources.IDS[i] + " stories");
                System.out.println("NEWS LIVE: " + NewsSources.IDS[i] + " count=" + stories.size() + " newest=" + stories.get(0).time);
            } finally { connection.disconnect(); }
        }
    }
}
