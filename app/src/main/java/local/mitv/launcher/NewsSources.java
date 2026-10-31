package local.mitv.launcher;

/** Fixed publisher endpoints: no dependency on a public aggregation proxy. */
final class NewsSources {
    static final String[] IDS = {"china", "world", "finance", "tech", "game", "ai"};
    static final String[] PUBLISHERS = {"央视网", "央视网", "华尔街见闻", "科学网·科技日报", "游民星空", "AIHOT"};
    static final String[] URLS = {
        "https://news.cctv.com/2019/07/gaiban/cmsdatainterface/page/china_1.jsonp",
        "https://news.cctv.com/2019/07/gaiban/cmsdatainterface/page/world_1.jsonp",
        "https://api-one.wallstcn.com/apiv1/content/lives?channel=global-channel&limit=30",
        "https://www.sciencenet.cn/xml/news-0.aspx?di=7",
        "https://db2.gamersky.com/LabelJsonpAjax.aspx?jsondata=%7B%22type%22%3A%22updatenodelabel%22%2C%22isCache%22%3Atrue%2C%22cacheTime%22%3A60%2C%22nodeId%22%3A%22129%22%2C%22isNodeId%22%3A%22true%22%2C%22page%22%3A1%7D",
        "https://aihot.news/feed.xml"
    };
    static final String[] SCIENCE_URLS = {URLS[3], "https://www.stdaily.com/web/gjxw/node_381.html"};

    /** English UI edition: major foreign publishers, chosen over reachability; BBC/The Guardian/The Verge need a proxy on Chinese networks. */
    static final String[] EN_PUBLISHERS = {"BBC News", "The Guardian", "MarketWatch", "Phys.org · ScienceDaily", "GameSpot", "The Verge"};
    static final String[] EN_URLS = {
        "https://feeds.bbci.co.uk/news/rss.xml",
        "https://www.theguardian.com/world/rss",
        "https://feeds.content.dowjones.io/public/rss/mw_topstories",
        "",
        "https://www.gamespot.com/feeds/mashup/",
        "https://www.theverge.com/rss/ai-artificial-intelligence/index.xml"
    };
    static final String[] EN_SCIENCE_URLS = {"https://phys.org/rss-feed/", "https://www.sciencedaily.com/rss/all.xml"};

    private NewsSources() {}
    static int index(int value) { return value >= 0 && value < IDS.length ? value : 0; }
    static int index(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        throw new IllegalArgumentException("Unknown news category");
    }
    static String[] urls(boolean english) { return english ? EN_URLS : URLS; }
    static String[] scienceUrls(boolean english) { return english ? EN_SCIENCE_URLS : SCIENCE_URLS; }
    static String[] publishers(boolean english) { return english ? EN_PUBLISHERS : PUBLISHERS; }
    static boolean articleHost(int category, String host) {
        if (host == null) return false;
        switch (index(category)) {
            case 0: case 1: return host.equalsIgnoreCase("news.cctv.com") || host.equalsIgnoreCase("m.news.cctv.com");
            case 2: return host.equalsIgnoreCase("wallstreetcn.com");
            case 3: return host.equalsIgnoreCase("news.sciencenet.cn") || host.equalsIgnoreCase("www.stdaily.com");
            case 4: return host.equalsIgnoreCase("www.gamersky.com");
            case 5: return host.equalsIgnoreCase("aihot.news");
            default: return false;
        }
    }
    static boolean articleHost(int category, String host, boolean english) {
        if (!english) return articleHost(category, host);
        if (host == null) return false;
        switch (index(category)) {
            case 0: return host.equalsIgnoreCase("www.bbc.co.uk") || host.equalsIgnoreCase("www.bbc.com");
            case 1: return host.equalsIgnoreCase("www.theguardian.com");
            case 2: return host.equalsIgnoreCase("www.marketwatch.com");
            case 3: return host.equalsIgnoreCase("phys.org") || host.equalsIgnoreCase("www.sciencedaily.com");
            case 4: return host.equalsIgnoreCase("www.gamespot.com");
            case 5: return host.equalsIgnoreCase("www.theverge.com");
            default: return false;
        }
    }
    /** Hosts the built-in reader can really render: Chinese sources all have templates or feed bodies. */
    static boolean readerHost(String host, boolean english) {
        if (host == null || !english) return host != null;
        return host.equalsIgnoreCase("www.theguardian.com") || host.equalsIgnoreCase("www.sciencedaily.com")
            || host.equalsIgnoreCase("www.gamespot.com") || host.equalsIgnoreCase("www.theverge.com");
    }
}
