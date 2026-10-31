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
            .replaceAll("(?is)\\[!--begin:htmlVideoCode--\\].*?\\[!--end:htmlVideoCode--\\]","")
            .replaceAll("(?is)<(script|style|iframe|object|form)\\b[^>]*>.*?</\\1\\s*>","")
            .replaceAll("(?is)<(?:meta|link|base|input|embed)\\b[^>]*>","")
            // Inline links otherwise steal remote focus and can jump the initial viewport.
            .replaceAll("(?is)<a\\b[^>]*>","<span>").replaceAll("(?is)</a\\s*>","</span>");
    }
    static String extract(String html,String host) {
        boolean cctv = host.equals("news.cctv.com") || host.equals("m.news.cctv.com");
        if (cctv) {
            String content = literal(html);
            if (content != null && content.trim().length() >= 20) return clean(content);
        }
        String source = clean(html); Matcher tags = CONTAINERS.matcher(source);
        while (tags.find()) {
            if (!tags.group(1).isEmpty()) continue;
            String attributes = tags.group(3);
            boolean start = cctv ? attribute(attributes,"id","content_area")
                : host.equals("news.sciencenet.cn") ? attribute(attributes,"id","content1")
                : host.equals("www.stdaily.com") ? attribute(attributes,"class","content")
                : host.equals("www.theguardian.com") ? attribute(attributes,"id","maincontent")
                : host.equals("www.sciencedaily.com") ? attribute(attributes,"id","story_text")
                : host.equals("www.gamersky.com") && (attribute(attributes,"class","Mid2L_con") || attribute(attributes,"class","MidLcon"));
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

    /** Decode only a JS string literal, never evaluate the page's script. */
    private static String literal(String html) {
        Matcher start = Pattern.compile("\\bcontentdate\\s*=\\s*(['\"])").matcher(html);
        if (!start.find()) return null;
        char quote = start.group(1).charAt(0); StringBuilder result = new StringBuilder();
        for (int i = start.end(); i < html.length(); i++) {
            char value = html.charAt(i);
            if (value == quote) return result.toString();
            if (value != '\\') { result.append(value); continue; }
            if (++i == html.length()) return null;
            value = html.charAt(i);
            if (value == 'u' || value == 'x') {
                int digits = value == 'u' ? 4 : 2;
                if (i + digits >= html.length()) return null;
                try { result.append((char)Integer.parseInt(html.substring(i+1,i+1+digits),16)); }
                catch (NumberFormatException error) { return null; }
                i += digits;
            } else if (value == 'n') result.append('\n');
            else if (value == 'r') result.append('\r');
            else if (value == 't') result.append('\t');
            else if (value == 'b') result.append('\b');
            else if (value == 'f') result.append('\f');
            else if (value == '\r') { if (i+1 < html.length() && html.charAt(i+1) == '\n') i++; }
            else if (value != '\n') result.append(value);
        }
        return null;
    }
    private static boolean attribute(String attributes,String name,String value) {
        Matcher field = Pattern.compile("\\b" + name + "\\s*=\\s*(['\"])(.*?)\\1",Pattern.CASE_INSENSITIVE).matcher(attributes);
        if (!field.find()) return false;
        for (String word : field.group(2).toLowerCase(Locale.ROOT).split("\\s+")) if (word.equalsIgnoreCase(value)) return true;
        return false;
    }
}
