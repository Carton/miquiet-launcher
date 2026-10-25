package local.mitv.launcher;

import java.net.URI;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Publisher-specific scientific reporting, balanced without subject-keyword guessing. */
final class ScienceNews {
    static final long MAX_AGE = 7 * 24 * 60 * 60_000L;
    static final String[] PUBLISHERS = {"科学网", "科技日报"};
    private static final Pattern DAILY = Pattern.compile(
        "<div\\s+class=['\"]content_right_top_item(?:\\s+[^'\"]*)?['\"]\\s*>\\s*"
        + "<div\\s+class=['\"]content_right_top_item_title['\"]\\s*>\\s*"
        + "<a\\b[^>]*href=['\"]([^'\"]+)['\"][^>]*>(.*?)</a>\\s*</div>\\s*"
        + "<div\\s+class=['\"]content_right_top_item_date['\"]\\s*>(.*?)</div>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    static final class Item {
        final String title, brief, url, publisher;
        final long time;
        Item(String title, String brief, String url, long time, String publisher) {
            this.title=title; this.brief=brief; this.url=url; this.time=time; this.publisher=publisher;
        }
    }
    private ScienceNews() {}

    interface Loader { String get(int source) throws Exception; }
    static final class Batch {
        final String[] responses;
        final boolean partial;
        Batch(String[] responses,boolean partial) {this.responses=responses;this.partial=partial;}
    }
    static Batch load(String[] cached,Loader loader,long now) throws Exception {
        String[] responses={"",""};boolean partial=false;
        for(int i=0;i<2;i++) {
            try {
                String value=loader.get(i);
                if(merge(parse(i,value),Collections.emptyList(),now).isEmpty())throw new IllegalArgumentException("No recent science stories");
                responses[i]=value;
            } catch(Exception error) {
                partial=true;
                try {
                    if(!merge(parse(i,cached[i]),Collections.emptyList(),now).isEmpty())responses[i]=cached[i];
                } catch(Exception ignored) { /* Keep the other publisher working independently. */ }
            }
        }
        if(responses[0].isEmpty() && responses[1].isEmpty())throw new java.io.IOException("Both science publishers unavailable");
        return new Batch(responses,partial);
    }

    static List<Item> parse(int source, String response) throws Exception {
        List<Item> result = new ArrayList<>();
        if (source == 0) {
            for (RssFeed.Item row : RssFeed.parse(response)) {
                add(result,source,row.title,row.body(),row.url,date(row.date));
            }
        } else if (source == 1) {
            // Only dated international-science / innovation cards. Ignore navigation,
            // policy commentary, promotional blocks and undated carousel headlines.
            String html=response.replaceAll("(?is)<!--.*?-->","");
            Matcher cards=DAILY.matcher(html);
            while(cards.find()) add(result,source,cards.group(2),"",cards.group(1),date(cards.group(3).trim()));
        } else throw new IllegalArgumentException("Unknown science publisher");
        Collections.sort(result,(a,b)->Long.compare(b.time,a.time));
        return result;
    }

    private static void add(List<Item> result,int source,String title,String brief,String url,long time) {
        String address=NewsReaderPolicy.address(url);
        if(address==null || time==0)return;
        try {
            URI uri=new URI(address);
            if (!(source==0 ? uri.getHost().equals("news.sciencenet.cn") && uri.getPath().startsWith("/htmlnews/")
                : uri.getHost().equals("www.stdaily.com") && uri.getPath().startsWith("/web/gjxw/")))return;
            title=text(title);
            if(!title.isEmpty())result.add(new Item(title,brief,address,time,PUBLISHERS[source]));
        } catch(java.net.URISyntaxException ignored) {}
    }

    static long date(String value) {
        for(String pattern:new String[]{"yyyy-MM-dd HH:mm:ss","yyyy-MM-dd HH:mm"}) {
            SimpleDateFormat format=new SimpleDateFormat(pattern,Locale.ROOT);
            format.setLenient(false);format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            ParsePosition position=new ParsePosition(0);java.util.Date date=format.parse(value,position);
            if(date!=null && position.getIndex()==value.length())return date.getTime();
        }
        return RssFeed.date(value);
    }

    static List<Item> merge(List<Item> first,List<Item> second,long now) {
        List<Item> result=new ArrayList<>();HashSet<String> urls=new HashSet<>(),titles=new HashSet<>();
        int[] positions={0,0};
        List<List<Item>> sources=new ArrayList<>();sources.add(first);sources.add(second);
        while(result.size()<12) {
            int before=result.size();
            for(int source=0;source<2 && result.size()<12;source++) {
                List<Item> rows=sources.get(source);
                while(positions[source]<rows.size()) {
                    Item item=rows.get(positions[source]++);
                    String title=text(item.title).toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]","");
                    if(title.isEmpty() || !DashboardPolicy.fresh(item.time,now,MAX_AGE)
                        || urls.contains(item.url) || titles.contains(title))continue;
                    urls.add(item.url);titles.add(title);result.add(item);break;
                }
            }
            if(result.size()==before)break;
        }
        return result;
    }

    private static String text(String value) {
        return value.replaceAll("<[^>]*>","").replace("&nbsp;"," ").replace("&amp;","&")
            .replace("&quot;","\"").replace("&#39;","'").trim();
    }
}
