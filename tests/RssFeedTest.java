package local.mitv.launcher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public final class RssFeedTest {
    private static int assertions;
    private static void check(boolean value) { assertions++; if (!value) throw new AssertionError("RSS assertion " + assertions); }
    public static void main(String[] args) throws Exception {
        String xml = "<?xml version='1.0'?><rss xmlns:content='http://purl.org/rss/1.0/modules/content/'><channel>"
            + "<title>频道标题不应当成新闻标题</title><item><title>测试 &amp; 中文</title><link>https://www.ithome.com/a.htm</link>"
            + "<description><![CDATA[<p>摘要</p>]]></description><content:encoded><![CDATA[<p>完整内容</p>]]></content:encoded>"
            + "<pubDate>Sat, 24 Oct 2026 11:56:34 GMT</pubDate></item><item><title>第二条</title>"
            + "<description>转义的 &lt;p&gt;内容&lt;/p&gt;</description></item></channel></rss>";
        List<RssFeed.Item> items = RssFeed.parse(xml);
        check(items.size() == 2);
        check(items.get(0).title.equals("测试 & 中文"));
        check(items.get(0).url.equals("https://www.ithome.com/a.htm"));
        check(items.get(0).description.equals("<p>摘要</p>"));
        check(items.get(0).body().equals("<p>完整内容</p>"));
        check(items.get(1).body().equals("转义的 <p>内容</p>"));
        check(RssFeed.date(items.get(0).date) == RssFeed.date("Sat, 24 Oct 2026 19:56:34 +0800"));
        check(RssFeed.date(items.get(0).date) > 0);
        check(RssFeed.date("Sat, 24 Oct 2026 11:56 GMT") > 0);
        check(RssFeed.date("Sat, 32 Oct 2026 11:56:34 GMT") == 0);
        check(RssFeed.date("Sat, 24 Oct 2026 11:56:34 GMT trailing") == 0);
        check(RssFeed.date("") == 0);
        check(RssFeed.parse("<rss><channel/></rss>").isEmpty());
        try {
            RssFeed.parse("<!DOCTYPE rss [<!ENTITY x SYSTEM 'http://127.0.0.1:1/'>]><rss><channel><item><title>&x;</title></item></channel></rss>");
            throw new AssertionError("External entity accepted");
        } catch (org.xml.sax.SAXException expected) { assertions++; }
        try { RssFeed.parse("<rss><item></rss>"); throw new AssertionError("Malformed XML accepted"); }
        catch (org.xml.sax.SAXException expected) { assertions++; }
        check(NewsSources.IDS.length == 6 && NewsSources.LABELS.length == 6);
        check(NewsSources.index(-1) == 0 && NewsSources.index(8) == 0);
        check(NewsSources.index("ai") == 5 && NewsSources.index("game") == 4);
        check(NewsSources.articleHost(3,"news.sciencenet.cn") && NewsSources.articleHost(3,"www.stdaily.com")
            && !NewsSources.articleHost(3,"www.ithome.com") && !NewsSources.articleHost(3,"www.stdaily.com.attacker.test"));
        check(!NewsSources.articleHost(4,"aihot.news"));
        check(NewsSources.articleHost(5,"aihot.news") && !NewsSources.articleHost(5,"www.qbitai.com"));
        System.out.println("PASS: " + assertions + " RSS and source assertions");
        for (String file : args) {
            List<RssFeed.Item> live = RssFeed.parse(new String(Files.readAllBytes(Paths.get(file)),StandardCharsets.UTF_8));
            check(!live.isEmpty()); check(RssFeed.date(live.get(0).date) > 0);
            System.out.println("FIXTURE: " + file + " count=" + live.size() + " newest=" + live.get(0).date);
        }
    }
}
