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
        // English edition: BBC/Guardian/MarketWatch/GameSpot RSS, The Verge Atom; science pair unchanged.
        StringBuilder enText = new StringBuilder(); for (int i = 0; i < 60; i++) enText.append("English dashboard story body for the TV reader. ");
        SimpleDateFormat isoDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ",Locale.US);
        isoDate.setTimeZone(TimeZone.getTimeZone("GMT"));
        String enRss = "<rss><channel>"
            + "<item><title>BBC &amp; markets</title><link>https://www.bbc.co.uk/news/articles/c1v2d3e4f5g6</link>"
            + "<pubDate>" + rssDate.format(new Date(now-60_000)) + "</pubDate><description>BBC summary</description></item>"
            + "<item><title>Guardian world report</title><link>https://www.theguardian.com/world/2026/oct/30-report</link>"
            + "<pubDate>" + rssDate.format(new Date(now-120_000)) + "</pubDate><description><![CDATA[<p>" + enText + "</p>]]></description></item>"
            + "<item><title>MarketWatch earnings</title><link>https://www.marketwatch.com/story/earnings-24babdfc</link>"
            + "<pubDate>" + rssDate.format(new Date(now-180_000)) + "</pubDate><description>MarketWatch summary</description></item>"
            + "<item><title>GameSpot guide</title><link>https://www.gamespot.com/articles/guide/1100-654321/</link>"
            + "<pubDate>" + rssDate.format(new Date(now-240_000)) + "</pubDate><description><![CDATA[<p>" + enText + "</p>]]></description></item>"
            + "<item><title>Attacker link</title><link>https://www.bbc.co.uk.attacker.test/2026/a.html</link>"
            + "<pubDate>" + rssDate.format(new Date(now-30_000)) + "</pubDate><description>bad</description></item>"
            + "<item><title>Old item</title><link>https://www.bbc.co.uk/news/2026/10/old.html</link>"
            + "<pubDate>" + rssDate.format(new Date(now-49*60*60_000L)) + "</pubDate><description>old</description></item></channel></rss>";
        String enAtom = "<feed xmlns='http://www.w3.org/2005/Atom'>"
            + "<entry><title type='html'>Verge AI &amp; chips</title>"
            + "<link rel='alternate' href='https://www.theverge.com/ai-artificial-intelligence/1023188/verge-story'/>"
            + "<published>" + isoDate.format(new Date(now-300_000)) + "</published>"
            + "<content type='html'>&lt;p&gt;" + enText + "&lt;/p&gt;</content></entry>"
            + "<entry><title>Attacker atom</title><link rel='alternate' href='https://www.theverge.com.attacker.test/1/x'/>"
            + "<published>" + isoDate.format(new Date(now-30_000)) + "</published><content>bad</content></entry></feed>";
        List<DashboardRepository.Story> bbcEn = DashboardRepository.parseFeed(enRss,0,true);
        check(bbcEn.size()==1 && bbcEn.get(0).publisher.equals("BBC News") && bbcEn.get(0).title.equals("BBC & markets"));
        check(bbcEn.get(0).brief.equals("BBC summary") && bbcEn.get(0).body.isEmpty());
        check(!NewsSources.readerHost(new URL(bbcEn.get(0).url).getHost(),true));
        List<DashboardRepository.Story> worldEn = DashboardRepository.parseFeed(enRss,1,true);
        check(worldEn.size()==1 && worldEn.get(0).publisher.equals("The Guardian") && !worldEn.get(0).body.isEmpty());
        check(NewsSources.readerHost(new URL(worldEn.get(0).url).getHost(),true));
        List<DashboardRepository.Story> financeEn = DashboardRepository.parseFeed(enRss,2,true);
        check(financeEn.size()==1 && financeEn.get(0).publisher.equals("MarketWatch"));
        List<DashboardRepository.Story> gameEn = DashboardRepository.parseFeed(enRss,4,true);
        check(gameEn.size()==1 && gameEn.get(0).publisher.equals("GameSpot") && !gameEn.get(0).body.isEmpty()
            && gameEn.get(0).brief.length()==361);
        List<DashboardRepository.Story> aiEn = DashboardRepository.parseFeed(enAtom,5,true);
        check(aiEn.size()==1 && aiEn.get(0).publisher.equals("The Verge") && aiEn.get(0).title.equals("Verge AI & chips"));
        check(!aiEn.get(0).body.isEmpty() && NewsSources.readerHost(new URL(aiEn.get(0).url).getHost(),true));
        String physEn = enRss.replace("https://www.bbc.co.uk/news/articles/c1v2d3e4f5g6","https://phys.org/news/2026-10-antimatter.html")
            .replace("BBC &amp; markets","Phys antimatter").replace("BBC summary","Phys summary");
        String dailyEn = enRss.replace("https://www.theguardian.com/world/2026/oct/30-report","https://www.sciencedaily.com/releases/2026/10/251030111.htm")
            .replace("Guardian world report","Daily materials").replace("<p>" + enText + "</p>","Daily summary")
            .replace(rssDate.format(new Date(now-120_000)),"Fri, 30 Oct 2026 09:35:00 EDT");
        List<DashboardRepository.Story> scienceEn = DashboardRepository.parseFeed(DashboardRepository.packScience(new String[]{physEn,dailyEn}),3,true);
        check(scienceEn.size()==2 && scienceEn.get(0).publisher.equals("Phys.org") && scienceEn.get(1).publisher.equals("ScienceDaily"));
        check(DashboardRepository.parseFeed(DashboardRepository.packScience(new String[]{physEn.replace("phys.org/news/","phys.org/press/"),dailyEn}),3,true).size()==1);
        try { DashboardRepository.parseFeed(feed(new JSONArray()),0,true); throw new AssertionError("Chinese JSONP accepted as English feed"); }
        catch (org.xml.sax.SAXException expected) { assertions++; }
        check(DashboardRepository.parseFeed(enRss.replace("https://www.bbc.co.uk/","https://news.cctv.com/"),0,true).isEmpty());
        System.out.println("PASS: " + assertions + " Android data assertions");
        if (args.length >= 2) {
            SSLSocketFactory sockets;
            String mode = "", proxy = null;
            java.util.List<java.io.InputStream> roots = new java.util.ArrayList<>();
            try (FileInputStream isrg = new FileInputStream(args[0]); FileInputStream google = new FileInputStream(args[1])) {
                roots.add(isrg); roots.add(google);
                for (int i = 2; i < args.length; i++) {
                    if (args[i].startsWith("--proxy=")) proxy = args[i].substring(8);
                    else if (args[i].startsWith("--")) mode = args[i];
                    else {
                        try { roots.add(new FileInputStream(args[i])); } catch (java.io.IOException ignored) { /* Optional root. */ }
                    }
                }
                sockets = LegacyTls.factory(roots.toArray(new java.io.InputStream[0]));
            } finally { for (int i = 2; i < roots.size(); i++) { try { roots.get(i).close(); } catch (java.io.IOException ignored) {} } }
            if (proxy != null) {
                // app_process has no ActivityThread, so the framework never installs its
                // ProxySelector; route these test connections explicitly like a proxied TV app.
                String[] hostPort = proxy.split(":");
                System.setProperty("http.proxyHost", hostPort[0]);
                System.setProperty("http.proxyPort", hostPort.length > 1 ? hostPort[1] : "80");
                System.setProperty("https.proxyHost", hostPort[0]);
                System.setProperty("https.proxyPort", hostPort.length > 1 ? hostPort[1] : "80");
            }
            if (mode.equals("--news")) { liveNews(sockets,false); return; }
            if (mode.equals("--news-en")) { liveNews(sockets,true); return; }
            if (mode.equals("--science")) { liveScience(sockets,false); return; }
            if (mode.equals("--science-en")) { liveScience(sockets,true); return; }
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

    private static void liveNews(SSLSocketFactory sockets, boolean english) throws Exception {
        String[] urls = NewsSources.urls(english);
        for (int i = 0; i < NewsSources.IDS.length; i++) {
            if(i==3){liveScience(sockets,english);continue;}
            HttpsURLConnection connection = (HttpsURLConnection)new URL(urls[i]).openConnection();
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
                List<DashboardRepository.Story> stories = DashboardRepository.parseFeed(output.toString("UTF-8"),i,english);
                if (stories.isEmpty()) throw new AssertionError("No recent " + NewsSources.IDS[i] + " stories");
                System.out.println("NEWS LIVE: " + NewsSources.IDS[i] + " count=" + stories.size() + " newest=" + stories.get(0).time);
                if (english && i == 1) {
                    // The Guardian's short standfirsts fall back to a page extraction template;
                    // items that already carry feed text never fetch the page in production.
                    int pages = 0; String body = null; DashboardRepository.Story proven = null;
                    for (DashboardRepository.Story candidate : stories) {
                        if (!candidate.body.isEmpty() || body != null) continue;
                        if (pages >= 4) break;
                        pages++;
                        try {
                            String extracted = ArticleHtml.extract(read(sockets,candidate.url),new URL(candidate.url).getHost());
                            if (extracted != null && extracted.length() > 100) { body = extracted; proven = candidate; }
                        } catch (java.io.IOException error) { /* Oversize or slow live pages are skipped. */ }
                    }
                    check(pages == 0 || body != null);
                    System.out.println(body != null
                        ? "READER LIVE: " + proven.publisher + " chars=" + body.length() + " url=" + proven.url
                        : "READER LIVE: The Guardian items carry feed text; page template not exercised");
                }
                if (english && i == 5) {
                    // The Verge publishes full articles inside its Atom feed; the reader shows that text.
                    DashboardRepository.Story story = stories.get(0);
                    check(story.body.length() > 100);
                    System.out.println("READER LIVE: " + story.publisher + " feed chars=" + story.body.length() + " url=" + story.url);
                }
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

    private static void liveScience(SSLSocketFactory sockets,boolean english)throws Exception {
        int edition=english?ScienceNews.EN:ScienceNews.ZH;
        final String[] science=NewsSources.scienceUrls(english);
        ScienceNews.Batch batch=ScienceNews.load(edition,new String[]{"",""},new ScienceNews.Loader(){
            @Override public String get(int source)throws Exception{return read(sockets,science[source]);}
        },System.currentTimeMillis());
        if (batch.partial) System.out.println("SCIENCE PARTIAL: source0=" + (batch.responses[0].isEmpty() ? "EMPTY" : "ok")
            + " source1=" + (batch.responses[1].isEmpty() ? "EMPTY" : "ok"));
        check(!batch.partial);
        List<DashboardRepository.Story> stories=DashboardRepository.parseFeed(DashboardRepository.packScience(batch.responses),3,english);
        check(stories.size()>=2 && stories.get(0).publisher.equals(english?"Phys.org":"科学网")
            && stories.get(1).publisher.equals(english?"ScienceDaily":"科技日报"));
        for(int i=0;i<2;i++) {
            System.out.println("SCIENCE LIVE: "+stories.get(i).publisher+" count="+ScienceNews.parse(edition,i,batch.responses[i]).size());
            if(english && i==0) continue; // Phys.org blocks the app user agent: feed summaries only, no reader.
            DashboardRepository.Story story=stories.get(i);
            String body=ArticleHtml.extract(read(sockets,story.url),new URL(story.url).getHost());
            check(body!=null && body.length()>100);
            System.out.println("SCIENCE ARTICLE: "+story.publisher+" chars="+body.length()+" url="+story.url);
        }
        System.out.println("SCIENCE COMBINED: count="+stories.size());
        System.out.println("NEWS LIVE: tech count="+stories.size()+" newest="+stories.get(0).time);
    }
}
