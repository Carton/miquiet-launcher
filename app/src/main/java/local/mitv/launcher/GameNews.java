package local.mitv.launcher;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parse the publisher's single-player list, with a conservative PC-oriented topic filter. */
final class GameNews {
    static final class Item {
        final String title, url, brief;
        final long time;
        Item(String title, String url, String brief, long time) {
            this.title = title; this.url = url; this.brief = brief; this.time = time;
        }
    }
    private static final Pattern ITEMS = Pattern.compile("(?is)<li\\b[^>]*>(.*?)</li\\s*>");
    private static final Pattern TITLE = Pattern.compile("(?is)<a\\b(?=[^>]*\\bclass=['\"]tt['\"])[^>]*\\bhref=['\"]([^'\"]+)['\"][^>]*>(.*?)</a\\s*>");
    private static final Pattern PC = Pattern.compile("(?i)(?<![a-z0-9])(?:pc|steam|epic|gog|rpcs3|shadps4|cemu|xenia|dolphin|proton|dxvk|mod)(?![a-z0-9])|模拟器|电脑");
    private static final Pattern CONSOLE = Pattern.compile("(?i)(?<![a-z0-9])(?:ps[456]|ns2?|xbox)(?![a-z0-9])|switch|任天堂|索尼|主机独占|ps商店");
    private static final Pattern NOISE = Pattern.compile("手游|手机游戏|电竞|王者荣耀|和平精英|直播|主播|网红|写真|女主脸|身材|颜值|(?i)cosplay");
    private GameNews() {}

    static boolean relevant(String title, String brief) {
        if (NOISE.matcher(title).find()) return false;
        // An emulator/Steam story may mention consoles; keep these explicit PC connections.
        if (PC.matcher(title + " " + brief).find()) return true;
        return !CONSOLE.matcher(title).find();
    }

    static List<Item> parse(String html) {
        List<Item> result = new ArrayList<>(); Matcher items = ITEMS.matcher(html);
        while (items.find() && result.size() < 100) {
            String row = items.group(1); Matcher title = TITLE.matcher(row);
            if (!title.find()) continue;
            String time = field(row,"time");
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT);
            format.setLenient(false); format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
            ParsePosition position = new ParsePosition(0); java.util.Date date = format.parse(time,position);
            if (date == null || position.getIndex() != time.length()) continue;
            result.add(new Item(title.group(2), title.group(1), field(row,"txt"),date.getTime()));
        }
        return result;
    }

    private static String field(String html, String name) {
        Matcher match = Pattern.compile("(?is)<div\\b[^>]*\\bclass=['\"]" + name + "['\"][^>]*>(.*?)</div\\s*>").matcher(html);
        return match.find() ? match.group(1).trim() : "";
    }
}
