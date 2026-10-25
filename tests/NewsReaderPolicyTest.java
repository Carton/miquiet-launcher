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
        System.out.println("PASS: " + assertions + " news reader URL assertions");
    }
}
