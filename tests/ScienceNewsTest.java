package local.mitv.launcher;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ScienceNewsTest {
    private static int assertions;
    private static void check(boolean value) {assertions++;if(!value)throw new AssertionError("Science assertion "+assertions);}
    private static String rss(String title,String url,String date) {
        return "<rss><channel><item><title>"+title+"</title><link>"+url+"</link><pubDate>"+date
            +"</pubDate><description>研究摘要</description></item></channel></rss>";
    }
    private static String daily(String title,String url,String date) {
        return "<div class='content_right_top_item h70'><div class='content_right_top_item_title'><a href='"+url+"'>"+title
            +"</a></div><div class='content_right_top_item_date'>"+date+"</div></div>";
    }
    public static void main(String[] args) throws Exception {
        long now=ScienceNews.date("2026-10-25 16:00:00");
        String science=rss("反物质研究","http://news.sciencenet.cn/htmlnews/test.shtm","2026-10-25 10:27");
        String daily=daily("新材料 &amp; 能源","https://www.stdaily.com/web/gjxw/test.html","2026-10-21 01:35:00");
        List<ScienceNews.Item> first=ScienceNews.parse(0,science),second=ScienceNews.parse(1,daily);
        check(first.size()==1 && first.get(0).publisher.equals("科学网"));
        check(first.get(0).url.startsWith("https://") && first.get(0).brief.equals("研究摘要"));
        check(second.size()==1 && second.get(0).publisher.equals("科技日报") && second.get(0).title.equals("新材料 & 能源"));
        check(ScienceNews.date("2026-10-25 10:27")==ScienceNews.date("Sun, 25 Oct 2026 02:27:00 +0000"));
        check(ScienceNews.date("2026-10-32 01:00")==0 && ScienceNews.date("2026-10-25 10:27 trailing")==0);
        check(ScienceNews.parse(0,science.replace("news.sciencenet.cn/","news.sciencenet.cn.attacker.test/")).isEmpty());
        check(ScienceNews.parse(0,science.replace("http://news.sciencenet.cn/","http://news.sciencenet.cnhttps://news.sciencenet.cn/")).isEmpty());
        check(ScienceNews.parse(1,"<!--"+daily+"-->").isEmpty());
        check(ScienceNews.parse(1,daily.replace("www.stdaily.com/","www.stdaily.com.attacker.test/")).isEmpty());
        check(ScienceNews.parse(1,daily.replace("content_right_top_item h70","commentary")).isEmpty());
        check(ScienceNews.parse(1,daily.replace("2026-10-21 01:35:00","")+daily).size()==1);
        List<ScienceNews.Item> merged=ScienceNews.merge(first,second,now);
        check(merged.size()==2 && merged.get(0).publisher.equals("科学网") && merged.get(1).publisher.equals("科技日报"));
        check(ScienceNews.merge(first,ScienceNews.parse(1,daily.replace("新材料 &amp; 能源","反物质：研究！")),now).size()==1);
        check(ScienceNews.merge(first,first,now).size()==1);
        check(ScienceNews.merge(first,second,now+8*24*60*60_000L).isEmpty());
        check(ScienceNews.merge(first,Collections.emptyList(),now-60*60_000L*24).isEmpty());
        ScienceNews.Batch good=ScienceNews.load(new String[]{"",""},i->i==0?science:daily,now);
        check(!good.partial);
        ScienceNews.Batch fallback=ScienceNews.load(new String[]{"",daily},i->{if(i==1)throw new java.io.IOException();return science;},now);
        check(fallback.partial && fallback.responses[1].equals(daily));
        ScienceNews.Batch single=ScienceNews.load(new String[]{"",""},i->i==0?science:"<html>error</html>",now);
        check(single.partial && single.responses[1].isEmpty());
        ScienceNews.Batch stale=ScienceNews.load(new String[]{"",daily.replace("2026-10-21","2026-10-01")},i->i==0?science:"error",now);
        check(stale.responses[1].isEmpty());
        try {ScienceNews.load(new String[]{"",""},i->{throw new java.io.IOException();},now);throw new AssertionError("Both failures accepted");}
        catch(java.io.IOException expected){assertions++;}
        ScienceNews.Item a=first.get(0),b=second.get(0);
        java.util.ArrayList<ScienceNews.Item> many=new java.util.ArrayList<>();
        for(int i=0;i<20;i++)many.add(new ScienceNews.Item("标题"+i,"",a.url+i,a.time,a.publisher));
        List<ScienceNews.Item> balanced=ScienceNews.merge(many,Arrays.asList(b),now);
        check(balanced.size()==12 && balanced.get(1).publisher.equals("科技日报"));
        if(args.length==2) {
            List<ScienceNews.Item> liveFirst=ScienceNews.parse(0,read(args[0])),liveSecond=ScienceNews.parse(1,read(args[1]));
            List<ScienceNews.Item> live=ScienceNews.merge(liveFirst,liveSecond,System.currentTimeMillis());
            check(!liveFirst.isEmpty() && !liveSecond.isEmpty());
            check(live.size()>=2 && live.get(0).publisher.equals("科学网") && live.get(1).publisher.equals("科技日报"));
            System.out.println("SCIENCE FIXTURES: first="+liveFirst.size()+" second="+liveSecond.size()+" merged="+live.size());
        }
        System.out.println("PASS: "+assertions+" science parsing, balancing and failure assertions");
    }
    private static String read(String path)throws Exception{return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)),java.nio.charset.StandardCharsets.UTF_8);}
}
