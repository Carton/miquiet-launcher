package local.mitv.launcher;

public final class GameNewsTest {
    private static int assertions;
    private static void check(boolean value) { assertions++; if (!value) throw new AssertionError("Game assertion " + assertions); }
    public static void main(String[] args) {
        check(GameNews.relevant("Steam史低折扣", "限时特卖"));
        check(GameNews.relevant("新3A发售预告", "支持PC"));
        check(GameNews.relevant("新作评测：值得一玩吗", ""));
        check(GameNews.relevant("RPCS3模拟器更新", ""));
        check(GameNews.relevant("PS5模拟器在PC运行", ""));
        check(GameNews.relevant("PS5运行Steam游戏", ""));
        check(GameNews.relevant("《新作》补丁与MOD", ""));
        check(!GameNews.relevant("PS5固件升级", "主机更新"));
        check(!GameNews.relevant("NS2新作独占", ""));
        check(!GameNews.relevant("Switch2商店促销史低", ""));
        check(!GameNews.relevant("主播直播美女影游", "Steam"));
        check(!GameNews.relevant("某手游新活动", ""));
        check(!GameNews.relevant("电竞决赛", ""));
        String row = "<li><a target='_blank' class='tt' href='https://www.gamersky.com/news/a.shtml'>Steam &amp; PC</a>"
            + "<div class='txt'>摘要</div><div class='time'>2026-10-25 09:12</div></li>";
        java.util.List<GameNews.Item> items = GameNews.parse("<nav>outside</nav>" + row);
        check(items.size() == 1 && items.get(0).title.equals("Steam &amp; PC"));
        check(items.get(0).url.endsWith("a.shtml") && items.get(0).brief.equals("摘要"));
        check(items.get(0).time == 1792890720000L);
        check(GameNews.parse(row.replace("2026-10-25", "2026-10-32")).isEmpty());
        check(GameNews.parse(row.replace("09:12", "09:12 trailing")).isEmpty());
        check(GameNews.parse(row.replace("class='tt'", "class='ad'")).isEmpty());
        check(GameNews.parse("<li>no title</li>").isEmpty());
        System.out.println("PASS: " + assertions + " PC game source assertions");
    }
}
