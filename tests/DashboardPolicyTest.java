package local.mitv.launcher;

public final class DashboardPolicyTest {
    private static int assertions;
    private static void check(boolean condition) {
        assertions++;
        if (!condition) throw new AssertionError("Dashboard assertion " + assertions);
    }
    public static void main(String[] args) {
        check(DashboardPolicy.description(0).equals("晴"));
        check(DashboardPolicy.description(63).equals("中雨"));
        check(DashboardPolicy.description(75).equals("大雪"));
        check(DashboardPolicy.description(45).equals("雾"));
        check(DashboardPolicy.description(99).equals("雷雨冰雹"));
        check(DashboardPolicy.description(-1).equals("天气未知"));
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
        check(DashboardPolicy.unwrap(" china({\"data\":{}}); ", "china").equals("{\"data\":{}}"));
        check(DashboardPolicy.unwrap("world({})", "world").equals("{}"));
        try { DashboardPolicy.unwrap("evil({});", "china"); throw new AssertionError("Accepted wrong callback"); }
        catch (IllegalArgumentException expected) { assertions++; }
        try { DashboardPolicy.unwrap("china({});alert(1)", "china"); throw new AssertionError("Accepted script"); }
        catch (IllegalArgumentException expected) { assertions++; }
        System.out.println("PASS: " + assertions + " dashboard policy assertions");
    }
}
