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
        check(finance.get(0).brief.length() == 361 && android.text.Html.fromHtml(finance.get(0).body).toString().contains(body));
        SimpleDateFormat rssDate = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z",Locale.US);
        rssDate.setTimeZone(TimeZone.getTimeZone("GMT"));
        String rssItem = "<item><title>科技 &amp; 中文</title><link>https://www.ithome.com/1/2/3.htm</link><pubDate>"
            + rssDate.format(new Date(now-30_000)) + "</pubDate><description><![CDATA[<img src='https://example.test/unused.png'><p>" + longer + "</p>]]></description></item>";
        String rss = "<rss><channel>" + rssItem + rssItem + rssItem.replace("www.ithome.com/1", "www.ithome.com.attacker.test/1") + "</channel></rss>";
        String science=rss.replace("https://www.ithome.com/1/2/3.htm","http://news.sciencenet.cn/htmlnews/test.shtm");
        SimpleDateFormat scienceDate=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.ROOT);
        scienceDate.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        String daily="<div class='content_right_top_item h70'><div class='content_right_top_item_title'>"
            +"<a href='https://www.stdaily.com/web/gjxw/test.html'>新材料与能源研究</a></div>"
            +"<div class='content_right_top_item_date'>"+scienceDate.format(new Date(now-4*24*60*60_000L))+"</div></div>";
        String combined=DashboardRepository.packScience(new String[]{science,daily});
        List<DashboardRepository.Story> tech = DashboardRepository.parseFeed(combined,3);
        check(tech.size() == 2 && tech.get(0).publisher.equals("科学网") && tech.get(1).publisher.equals("科技日报"));
        check(tech.get(0).title.equals("科技 & 中文") && tech.get(0).body.isEmpty());
        check(tech.get(0).brief.indexOf('\ufffc') == -1 && tech.get(0).url.startsWith("https://"));
        check(DashboardRepository.parseFeed(DashboardRepository.packScience(new String[]{"",daily}),3).size()==1);
        check(DashboardRepository.parseFeed(DashboardRepository.packScience(new String[]{science,
            daily.replace("www.stdaily.com/","www.stdaily.com.attacker.test/")}),3).size()==1);
        try {DashboardRepository.parseFeed(rss,3);throw new AssertionError("Old IT feed cache accepted");}
        catch(org.json.JSONException expected){assertions++;}
        check(DashboardRepository.newsMaxAge(3)==7*24*60*60_000L && DashboardRepository.newsMaxAge(5)==48*60*60_000L);
        String aiRss = rss.replace("www.ithome.com/1/2/3.htm", "aihot.news/items/example")
            .replace(longer.toString(), "这是AI聚合摘要，短内容也应在电视直接可读。")
            .replace("</p>]]>","</p><p>🔗 <a href='https://example.test/'>阅读原文</a></p><p>via AIHOT · https://aihot.news/items/example</p>]]>");
        List<DashboardRepository.Story> ai = DashboardRepository.parseFeed(aiRss,5);
        check(ai.size() == 1 && ai.get(0).publisher.equals("AIHOT") && !ai.get(0).body.isEmpty());
        check(!ai.get(0).brief.contains("阅读原文") && !ai.get(0).body.contains("via AIHOT"));
        check(DashboardRepository.parseFeed(aiRss.replace("aihot.news/items/example", "www.qbitai.com/old"),5).isEmpty());
        SimpleDateFormat gameDate = new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT);
        gameDate.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        String gameRow = "<li><a class='tt' href='https://www.gamersky.com/news/202610/test.shtml'>Steam &amp; 新作</a>"
            + "<div class='txt'>PC新游摘要</div><div class='time'>" + gameDate.format(new Date(now-120_000)) + "</div></li>";
        String gameResponse = "(" + new JSONObject().put("status","ok").put("body",gameRow + gameRow
            + gameRow.replace("www.gamersky.com/", "www.gamersky.com.attacker.test/")
            + gameRow.replace("test.shtml", "console.shtml").replace("Steam &amp; 新作","PS5固件更新").replace("PC新游摘要","主机独占")
            + gameRow.replace("test.shtml", "old.shtml").replace(gameDate.format(new Date(now-120_000)), gameDate.format(new Date(now-49*60*60_000L)))) + ");";
        List<DashboardRepository.Story> games = DashboardRepository.parseFeed(gameResponse,4);
        check(games.size() == 1);
        check(games.get(0).publisher.equals("游民星空") && games.get(0).title.equals("Steam & 新作") && games.get(0).body.isEmpty());
        try { DashboardRepository.parseFeed(rss,4); throw new AssertionError("Old RSS cache accepted as game JSONP"); }
        catch (IllegalArgumentException expected) { assertions++; }
        System.out.println("PASS: " + assertions + " Android data assertions");
        if (args.length >= 2) {
            SSLSocketFactory sockets;
            try (FileInputStream isrg = new FileInputStream(args[0]); FileInputStream google = new FileInputStream(args[1])) {
                sockets = LegacyTls.factory(isrg, google);
            }
            if (args.length == 3 && args[2].equals("--news")) {
                liveNews(sockets); return;
            }
            if (args.length == 3 && args[2].equals("--science")) {
                liveScience(sockets); return;
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
            if(i==3){liveScience(sockets);continue;}
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

    private static String read(SSLSocketFactory sockets,String address)throws Exception {
        HttpsURLConnection connection=(HttpsURLConnection)new URL(address).openConnection();
        connection.setSSLSocketFactory(sockets);connection.setConnectTimeout(8000);connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);connection.setRequestProperty("User-Agent","QuietTV/0.7 (personal Android TV launcher)");
        try {
            if(connection.getResponseCode()!=200)throw new java.io.IOException("HTTP "+connection.getResponseCode());
            java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
            try(java.io.InputStream input=connection.getInputStream()) {
                byte[] buffer=new byte[8192];int count;
                while((count=input.read(buffer))!=-1){if(output.size()+count>512*1024)throw new java.io.IOException("Oversize response");output.write(buffer,0,count);}
            }
            return output.toString("UTF-8");
        } finally {connection.disconnect();}
    }

    private static void liveScience(SSLSocketFactory sockets)throws Exception {
        ScienceNews.Batch batch=ScienceNews.load(new String[]{"",""},new ScienceNews.Loader(){
            @Override public String get(int source)throws Exception{return read(sockets,NewsSources.SCIENCE_URLS[source]);}
        },System.currentTimeMillis());
        check(!batch.partial);
        List<DashboardRepository.Story> stories=DashboardRepository.parseFeed(DashboardRepository.packScience(batch.responses),3);
        check(stories.size()>=2 && stories.get(0).publisher.equals("科学网") && stories.get(1).publisher.equals("科技日报"));
        for(int i=0;i<2;i++) {
            System.out.println("SCIENCE LIVE: "+ScienceNews.PUBLISHERS[i]+" count="+ScienceNews.parse(i,batch.responses[i]).size());
            DashboardRepository.Story story=stories.get(i);
            String body=ArticleHtml.extract(read(sockets,story.url),new URL(story.url).getHost());
            check(body!=null && body.length()>100);
            System.out.println("SCIENCE ARTICLE: "+story.publisher+" chars="+body.length()+" url="+story.url);
        }
        System.out.println("SCIENCE COMBINED: count="+stories.size());
        System.out.println("NEWS LIVE: tech count="+stories.size()+" newest="+stories.get(0).time);
    }
}
