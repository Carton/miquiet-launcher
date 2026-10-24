package local.mitv.launcher;

import java.net.URI;

final class NewsReaderPolicy {
    private NewsReaderPolicy() {}
    static String address(String value) {
        if (value == null) return null;
        try {
            URI uri = new URI(value);
            String host = uri.getHost(), scheme = uri.getScheme();
            if (!("news.cctv.com".equalsIgnoreCase(host) || "m.news.cctv.com".equalsIgnoreCase(host))
                || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                || uri.getRawUserInfo() != null || uri.getPort() != -1) return null;
            return "http".equalsIgnoreCase(scheme) ? "https" + value.substring(value.indexOf(':')) : value;
        } catch (java.net.URISyntaxException error) { return null; }
    }
}
