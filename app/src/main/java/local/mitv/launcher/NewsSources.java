package local.mitv.launcher;

/** Fixed publisher endpoints: no dependency on a public aggregation proxy. */
final class NewsSources {
    static final String[] IDS = {"china", "world", "finance", "tech", "game", "ai"};
    static final String[] LABELS = {"国内", "国际", "财经", "科技", "游戏", "AI"};
    static final String[] PUBLISHERS = {"央视网", "央视网", "华尔街见闻", "IT之家", "机核", "量子位"};
    static final String[] URLS = {
        "https://news.cctv.com/2019/07/gaiban/cmsdatainterface/page/china_1.jsonp",
        "https://news.cctv.com/2019/07/gaiban/cmsdatainterface/page/world_1.jsonp",
        "https://api-one.wallstcn.com/apiv1/content/lives?channel=global-channel&limit=30",
        "https://www.ithome.com/rss/", "https://www.gcores.com/rss", "https://www.qbitai.com/feed"
    };
    private NewsSources() {}
    static int index(int value) { return value >= 0 && value < IDS.length ? value : 0; }
    static int index(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        throw new IllegalArgumentException("Unknown news category");
    }
    static boolean articleHost(int category, String host) {
        if (host == null) return false;
        switch (index(category)) {
            case 0: case 1: return host.equalsIgnoreCase("news.cctv.com") || host.equalsIgnoreCase("m.news.cctv.com");
            case 2: return host.equalsIgnoreCase("wallstreetcn.com");
            case 3: return host.equalsIgnoreCase("www.ithome.com");
            case 4: return host.equalsIgnoreCase("www.gcores.com");
            case 5: return host.equalsIgnoreCase("www.qbitai.com");
            default: return false;
        }
    }
}
