package local.mitv.launcher;

/** Network-independent rules, also exercised on the host JVM. */
final class DashboardPolicy {
    private DashboardPolicy() {}

    static String description(int code) {
        if (code == 0) return "晴";
        if (code == 1) return "晴间多云";
        if (code == 2) return "多云";
        if (code == 3) return "阴";
        if (code == 45 || code == 48) return "雾";
        if (code >= 51 && code <= 57) return "毛毛雨";
        if (code == 61) return "小雨";
        if (code == 63) return "中雨";
        if (code == 65) return "大雨";
        if (code == 66 || code == 67) return "冻雨";
        if (code == 71) return "小雪";
        if (code == 73) return "中雪";
        if (code == 75) return "大雪";
        if (code == 77) return "雪粒";
        if (code >= 80 && code <= 82) return "阵雨";
        if (code == 85 || code == 86) return "阵雪";
        if (code == 95) return "雷雨";
        if (code == 96 || code == 99) return "雷雨冰雹";
        return "天气未知";
    }

    static String icon(int code, boolean day) {
        if (code == 0) return day ? "sun" : "moon";
        if (code == 1 || code == 2) return day ? "cloud_sun" : "cloud_moon";
        if (code == 3) return "cloud";
        if (code == 45 || code == 48) return "mist";
        if (code >= 51 && code <= 67 || code >= 80 && code <= 82) return "cloud_rain";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "cloud_snow";
        if (code == 95 || code == 96 || code == 99) return "cloud_storm";
        return "cloud_question";
    }

    static boolean fresh(long timestamp, long now, long maximumAge) {
        return timestamp > 0 && timestamp <= now + 5 * 60_000L && now - timestamp <= maximumAge;
    }

    static boolean weatherAvailable(long timestamp, long now, boolean failed) {
        return !failed && fresh(timestamp, now, 60 * 60_000L);
    }

    static String unwrap(String response, String callback) {
        String value = response.trim();
        String prefix = callback + "(";
        if (!value.startsWith(prefix) || !(value.endsWith(")") || value.endsWith(");"))) {
            throw new IllegalArgumentException("Unexpected news response");
        }
        String json = value.substring(prefix.length(), value.lastIndexOf(')')).trim();
        if (!json.startsWith("{") || !json.endsWith("}")) throw new IllegalArgumentException("Expected JSON object");
        return json;
    }
}
