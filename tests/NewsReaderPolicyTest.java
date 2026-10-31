package local.mitv.launcher;

public final class NewsReaderPolicyTest {
    private static int assertions;
    private static void check(boolean value) { assertions++; if (!value) throw new AssertionError("Reader assertion " + assertions); }
    public static void main(String[] args) {
        check(NewsReaderPolicy.address("https://news.cctv.com/2026/10/article.shtml").equals("https://news.cctv.com/2026/10/article.shtml"));
        check(NewsReaderPolicy.address("http://news.cctv.com/a.shtml").equals("https://news.cctv.com/a.shtml"));
        check(NewsReaderPolicy.address("https://m.news.cctv.com/a.shtml") != null);
        check(NewsReaderPolicy.address("https://news.cctv.com.evil.example/a") == null);
        check(NewsReaderPolicy.address("https://example.com/?news.cctv.com") == null);
        check(NewsReaderPolicy.address("https://user:password@news.cctv.com/a") == null);
        check(NewsReaderPolicy.address("https://news.cctv.com:8080/a") == null);
        check(NewsReaderPolicy.address("file:///sdcard/a.html") == null);
        check(NewsReaderPolicy.address("javascript:alert(1)") == null);
        check(NewsReaderPolicy.address("intent://news.cctv.com/a") == null);
        check(NewsReaderPolicy.address(null) == null);
        check(NewsReaderPolicy.address("bad url") == null);
        check(NewsReaderPolicy.address("https://www.ithome.com/1/2/3.htm") == null);
        check(NewsReaderPolicy.address("http://news.sciencenet.cn/htmlnews/2026/10/123.shtm").startsWith("https://"));
        check(NewsReaderPolicy.address("https://www.stdaily.com/web/gjxw/2026-10/23/content_123.html") != null);
        check(NewsReaderPolicy.address("https://www.stdaily.com.attacker.test/") == null);
        check(NewsReaderPolicy.address("http://aihot.news/items/example").startsWith("https://"));
        check(NewsReaderPolicy.address("https://www.gamersky.com/news/202610/123.shtml") != null);
        check(NewsReaderPolicy.address("https://wallstreetcn.com/livenews/123") != null);
        check(NewsReaderPolicy.address("https://aihot.news.attacker.test/") == null);
        check(NewsReaderPolicy.address("https://www.gamersky.com:443/news/123") == null);
        check(NewsReaderPolicy.address("https://www.gcores.com/articles/123") == null);
        check(NewsReaderPolicy.address("https://www.qbitai.com/2026/10/story.html") == null);
        // English edition whitelist and reader capability gate.
        check(NewsReaderPolicy.address("https://www.bbc.co.uk/news/articles/cv2d62e34rw5o", true) != null);
        check(NewsReaderPolicy.address("https://www.bbc.com/news/articles/cv2d62e34rw5o", true) != null);
        check(NewsReaderPolicy.address("https://www.theguardian.com/world/2026/oct/30/story", true) != null);
        check(NewsReaderPolicy.address("http://www.gamespot.com/articles/x/1100-654321/", true).startsWith("https://"));
        check(NewsReaderPolicy.address("https://www.theverge.com/ai-artificial-intelligence/1023188/pope-leo-disarm-ai", true) != null);
        check(NewsReaderPolicy.address("https://www.marketwatch.com/story/earnings-24babdfc", true) != null);
        check(NewsReaderPolicy.address("https://phys.org/news/2026-10-china.html", true) != null);
        check(NewsReaderPolicy.address("https://www.sciencedaily.com/releases/2026/10/261030002931.htm", true) != null);
        check(NewsReaderPolicy.address("https://www.bbc.co.uk.evil.example/a", true) == null);
        check(NewsReaderPolicy.address("https://news.cctv.com/a.shtml", true) == null);
        check(NewsReaderPolicy.address("https://www.theverge.com:443/a", true) == null);
        check(NewsReaderPolicy.address("https://www.theguardian.com/world/a", true) != null
            && !NewsSources.articleHost(1, "www.theguardian.com", false));
        check(NewsSources.readerHost("www.theguardian.com", true) && NewsSources.readerHost("www.sciencedaily.com", true)
            && NewsSources.readerHost("www.gamespot.com", true) && NewsSources.readerHost("www.theverge.com", true));
        check(!NewsSources.readerHost("www.bbc.co.uk", true) && !NewsSources.readerHost("www.marketwatch.com", true)
            && !NewsSources.readerHost("phys.org", true) && !NewsSources.readerHost("www.bbc.co.uk.attacker.test", true));
        check(NewsSources.readerHost("news.cctv.com", false) && NewsSources.readerHost("aihot.news", false));
        System.out.println("PASS: " + assertions + " news reader URL assertions");
    }
}
