package local.mitv.launcher;

public final class DashboardPolicyTest {
    private static int assertions;
    private static void check(boolean condition) {
        assertions++;
        if (!condition) throw new AssertionError("Dashboard assertion " + assertions);
    }
    public static void main(String[] args) {
        check(DashboardPolicy.condition(0) == DashboardPolicy.Weather.CLEAR);
        check(DashboardPolicy.condition(63) == DashboardPolicy.Weather.RAIN);
        check(DashboardPolicy.condition(75) == DashboardPolicy.Weather.HEAVY_SNOW);
        check(DashboardPolicy.condition(45) == DashboardPolicy.Weather.FOG);
        check(DashboardPolicy.condition(99) == DashboardPolicy.Weather.HAIL);
        check(DashboardPolicy.condition(-1) == DashboardPolicy.Weather.UNKNOWN);
        check(DashboardPolicy.icon(0, false).equals("moon"));
        check(DashboardPolicy.icon(0, true).equals("sun"));
        check(DashboardPolicy.icon(2, false).equals("cloud_moon"));
        check(DashboardPolicy.icon(2, true).equals("cloud_sun"));
        check(DashboardPolicy.icon(66, true).equals("cloud_rain"));
        check(DashboardPolicy.icon(85, false).equals("cloud_snow"));
        check(DashboardPolicy.icon(95, true).equals("cloud_storm"));
        check(DashboardPolicy.icon(999, true).equals("cloud_question"));
        long now = 1_000_000L;
        check(DashboardPolicy.fresh(now - 100, now, 100));
        check(!DashboardPolicy.fresh(now - 101, now, 100));
        check(!DashboardPolicy.fresh(0, now, 100));
        check(!DashboardPolicy.fresh(now + 300_001L, now, 100));
        long weatherNow = 10_000_000L;
        check(DashboardPolicy.weatherAvailable(weatherNow, weatherNow, false));
        check(!DashboardPolicy.weatherAvailable(weatherNow, weatherNow, true));
        check(DashboardPolicy.weatherAvailable(weatherNow - 3_600_000L, weatherNow, false));
        check(!DashboardPolicy.weatherAvailable(weatherNow - 3_600_001L, weatherNow, false));
        check(!DashboardPolicy.weatherAvailable(0, weatherNow, false));
        check(!DashboardPolicy.weatherAvailable(weatherNow + 300_001L, weatherNow, false));
        // A successful retry makes recent data visible again.
        check(!DashboardPolicy.weatherAvailable(weatherNow - 60_000L, weatherNow, true));
        check(DashboardPolicy.weatherAvailable(weatherNow - 60_000L, weatherNow, false));
        check(DashboardPolicy.unwrap(" china({\"data\":{}}); ", "china").equals("{\"data\":{}}"));
        check(DashboardPolicy.unwrap("world({})", "world").equals("{}"));
        try { DashboardPolicy.unwrap("evil({});", "china"); throw new AssertionError("Accepted wrong callback"); }
        catch (IllegalArgumentException expected) { assertions++; }
        try { DashboardPolicy.unwrap("china({});alert(1)", "china"); throw new AssertionError("Accepted script"); }
        catch (IllegalArgumentException expected) { assertions++; }
        System.out.println("PASS: " + assertions + " dashboard policy assertions");
    }
}
