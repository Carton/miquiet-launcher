package local.mitv.launcher;

import java.io.StringReader;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/** Small RSS 2 / Atom parser using the XML implementation bundled with Android 6. */
final class RssFeed {
    static final class Item {
        String title = "", url = "", description = "", content = "", date = "";
        String body() { return content.trim().isEmpty() ? description : content; }
    }
    private RssFeed() {}

    static List<Item> parse(String xml) throws Exception {
        // Some publishers append cache comments (and stray markup) after the root close tag;
        // Android 6's expat rejects that trailing junk even though the items are complete.
        for (String closeTag : new String[]{"</rss>", "</feed>"}) {
            int close = xml.lastIndexOf(closeTag);
            if (close >= 0) { xml = xml.substring(0, close + closeTag.length()); break; }
        }
        // Do not allow DTDs even on a platform that lacks the optional SAX features.
        String upper = xml.toUpperCase(Locale.ROOT);
        if (upper.contains("<!DOCTYPE") || upper.contains("<!ENTITY")) throw new SAXException("DTD is not allowed");
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XMLReader reader = factory.newSAXParser().getXMLReader();
        for (String feature : new String[]{"http://xml.org/sax/features/external-general-entities",
                "http://xml.org/sax/features/external-parameter-entities"}) {
            try { reader.setFeature(feature, false); } catch (SAXException ignored) { /* DTDs already rejected. */ }
        }
        reader.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        reader.setErrorHandler(new DefaultHandler() {
            @Override public void error(org.xml.sax.SAXParseException error) throws SAXException { throw error; }
            @Override public void fatalError(org.xml.sax.SAXParseException error) throws SAXException { throw error; }
        });
        List<Item> items = new ArrayList<>();
        reader.setContentHandler(new DefaultHandler() {
            private Item item;
            private int depth, fieldDepth;
            private String field;
            private final StringBuilder text = new StringBuilder();
            @Override public void startElement(String uri, String name, String qName, Attributes attributes) throws SAXException {
                depth++;
                if (name.equals("item") || name.equals("entry")) {
                    if (items.size() >= 200) throw new SAXException("Too many feed items");
                    item = new Item(); fieldDepth = depth + 1;
                } else if (item != null && depth == fieldDepth) {
                    field = name; text.setLength(0);
                    if (name.equals("link")) {
                        // Atom links live in href attributes; rel=alternate is the article page.
                        String rel = attributes.getValue("", "rel"), href = attributes.getValue("", "href");
                        if (href != null && !href.isEmpty() && (rel == null || rel.equals("alternate"))
                            && item.url.isEmpty()) item.url = href;
                    }
                }
            }
            @Override public void characters(char[] value, int offset, int length) throws SAXException {
                if (item != null && field != null) {
                    if (text.length() + length > 256 * 1024) throw new SAXException("Feed item is too large");
                    text.append(value, offset, length);
                }
            }
            @Override public void endElement(String uri, String name, String qName) {
                if (item != null && depth == fieldDepth && field != null) {
                    String value = text.toString().trim();
                    if (field.equals("title")) item.title = value;
                    else if (field.equals("link")) { if (!value.isEmpty()) item.url = value; }
                    else if (field.equals("description") || field.equals("summary")) item.description = value;
                    else if (field.equals("encoded") || field.equals("content")) item.content = value;
                    else if (field.equals("pubDate") || field.equals("published")) item.date = value;
                    else if (field.equals("updated") && item.date.isEmpty()) item.date = value;
                    field = null;
                }
                if ((name.equals("item") || name.equals("entry")) && item != null) { items.add(item); item = null; }
                depth--;
            }
        });
        reader.parse(new InputSource(new StringReader(xml)));
        return items;
    }

    static long date(String value) {
        if (value == null) return 0;
        // Android 6 lacks the ISO 8601 'X' pattern letter: normalize ±HH:MM and trailing Z
        // to an RFC 822 offset before parsing.
        java.util.regex.Matcher iso = java.util.regex.Pattern.compile("(.*)([+-]\\d\\d):(\\d\\d)$").matcher(value);
        if (iso.matches()) value = iso.group(1) + iso.group(2) + iso.group(3);
        else if (value.endsWith("Z")) value = value.substring(0, value.length() - 1) + "+0000";
        // RSS feeds use RFC 822 (some with named zones like EDT); Atom uses ISO 8601.
        for (String pattern : new String[]{"EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm Z",
                "EEE, dd MMM yyyy HH:mm:ss z", "yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd'T'HH:mmZ"}) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            java.util.Date date = format.parse(value, position);
            if (date != null && position.getIndex() == value.length()) return date.getTime();
        }
        return 0;
    }
}
