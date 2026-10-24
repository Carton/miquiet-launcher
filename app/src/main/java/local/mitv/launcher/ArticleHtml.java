package local.mitv.launcher;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Narrow static-page extractors. Unknown templates fail back to the feed summary. */
final class ArticleHtml {
    private static final Pattern CONTAINERS = Pattern.compile("<(/?)(div|article|section)\\b([^>]*)>",Pattern.CASE_INSENSITIVE);
    private ArticleHtml() {}
    static String clean(String html) {
        return html.replaceAll("(?is)<!--.*?-->","")
            .replaceAll("(?is)<(script|style|iframe|object|form)\\b[^>]*>.*?</\\1\\s*>","")
            .replaceAll("(?is)<(?:meta|link|base|input|embed)\\b[^>]*>","");
    }
    static String extract(String html,String host) {
        String source = clean(html); Matcher tags = CONTAINERS.matcher(source);
        while (tags.find()) {
            if (!tags.group(1).isEmpty()) continue;
            String attributes = tags.group(3);
            boolean start = host.equals("www.qbitai.com") ? attribute(attributes,"class","article")
                : host.equals("www.ithome.com") ? attribute(attributes,"id","paragraph")
                : host.equals("www.gcores.com") && tags.group(2).equalsIgnoreCase("article");
            if (!start) continue;
            int begin = tags.end(), depth = 1; String kind = tags.group(2);
            Matcher nested = CONTAINERS.matcher(source); nested.region(begin,source.length());
            while (nested.find()) {
                if (!nested.group(2).equalsIgnoreCase(kind)) continue;
                depth += nested.group(1).isEmpty() ? 1 : -1;
                if (depth == 0) {
                    String body = source.substring(begin,nested.start()).replaceAll("(?is)<h1\\b[^>]*>.*?</h1\\s*>","");
                    if (body.replaceAll("<[^>]*>","").trim().length() >= 80) return body;
                    return null;
                }
            }
            return null;
        }
        return null;
    }
    private static boolean attribute(String attributes,String name,String value) {
        Matcher field = Pattern.compile("\\b" + name + "\\s*=\\s*(['\"])(.*?)\\1",Pattern.CASE_INSENSITIVE).matcher(attributes);
        if (!field.find()) return false;
        for (String word : field.group(2).toLowerCase(Locale.ROOT).split("\\s+")) if (word.equals(value)) return true;
        return false;
    }
}
