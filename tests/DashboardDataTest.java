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
        System.out.println("PASS: " + assertions + " Android data assertions");
        if (args.length == 2) {
            SSLSocketFactory sockets;
            try (FileInputStream isrg = new FileInputStream(args[0]); FileInputStream google = new FileInputStream(args[1])) {
                sockets = LegacyTls.factory(isrg, google);
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
}
