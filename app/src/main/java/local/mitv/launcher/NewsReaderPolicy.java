package local.mitv.launcher;

import java.net.URI;

final class NewsReaderPolicy {
    private NewsReaderPolicy() {}
    static String address(String value) { return address(value, false); }
    static String address(String value, boolean english) {
        if (value == null) return null;
        try {
            URI uri = new URI(value);
            String host = uri.getHost(), scheme = uri.getScheme();
            boolean allowed = false;
            for (int i = 0; i < NewsSources.IDS.length; i++) allowed |= NewsSources.articleHost(i, host, english);
            if (!allowed
                || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                || uri.getRawUserInfo() != null || uri.getPort() != -1) return null;
            return "http".equalsIgnoreCase(scheme) ? "https" + value.substring(value.indexOf(':')) : value;
        } catch (java.net.URISyntaxException error) { return null; }
    }
}
