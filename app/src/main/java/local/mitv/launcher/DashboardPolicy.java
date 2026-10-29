package local.mitv.launcher;

/** Network-independent rules, also exercised on the host JVM. */
final class DashboardPolicy {
    private DashboardPolicy() {}

    enum Weather { CLEAR, FAIR, PARTLY_CLOUDY, OVERCAST, FOG, DRIZZLE, LIGHT_RAIN, RAIN, HEAVY_RAIN, FREEZING_RAIN, LIGHT_SNOW, SNOW, HEAVY_SNOW, SNOW_GRAINS, SHOWERS, SNOW_SHOWERS, THUNDERSTORM, HAIL, UNKNOWN }

    static Weather condition(int code) {
        if (code == 0) return Weather.CLEAR;
        if (code == 1) return Weather.FAIR;
        if (code == 2) return Weather.PARTLY_CLOUDY;
        if (code == 3) return Weather.OVERCAST;
        if (code == 45 || code == 48) return Weather.FOG;
        if (code >= 51 && code <= 57) return Weather.DRIZZLE;
        if (code == 61) return Weather.LIGHT_RAIN;
        if (code == 63) return Weather.RAIN;
        if (code == 65) return Weather.HEAVY_RAIN;
        if (code == 66 || code == 67) return Weather.FREEZING_RAIN;
        if (code == 71) return Weather.LIGHT_SNOW;
        if (code == 73) return Weather.SNOW;
        if (code == 75) return Weather.HEAVY_SNOW;
        if (code == 77) return Weather.SNOW_GRAINS;
        if (code >= 80 && code <= 82) return Weather.SHOWERS;
        if (code == 85 || code == 86) return Weather.SNOW_SHOWERS;
        if (code == 95) return Weather.THUNDERSTORM;
        if (code == 96 || code == 99) return Weather.HAIL;
        return Weather.UNKNOWN;
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
