package local.mitv.launcher;

/** Immutable, network-independent home choices. Invalid future values fall back safely. */
final class HomeAppearance {
    static final String SHOW_NEWS = "home_show_news", SHOW_WEATHER = "home_show_weather";
    static final String[] BACKGROUNDS = {"炭灰", "深海蓝", "墨绿"};
    private static final int[] BACKGROUND_COLORS = {0xff1a1c20, 0xff141f2c, 0xff16241f};
    private static final int[] PANEL_COLORS = {0xff303237, 0xff25374a, 0xff2b3b33};
    private static final int[] NEWS_COLORS = {0xff282b30, 0xff1c2b3b, 0xff203129};
    static final HomeAppearance DEFAULT = new HomeAppearance(0, false, 0, true, true);
    final int background, focus;
    final boolean largeText, showNews, showWeather;

    HomeAppearance(int background, boolean largeText, int focus, boolean showNews, boolean showWeather) {
        this.background = background >= 0 && background < BACKGROUNDS.length ? background : 0;
        this.largeText = largeText;
        this.focus = focus == 1 ? 1 : 0;
        this.showNews = showNews;
        this.showWeather = showWeather;
    }

    int backgroundColor() { return BACKGROUND_COLORS[background]; }
    int panelColor() { return PANEL_COLORS[background]; }
    int newsColor() { return NEWS_COLORS[background]; }
    int focusColor() { return focus == 1 ? 0xff31564e : 0xff464a50; }
    int focusStroke() { return focus == 1 ? 0xff63debd : 0xffffffff; }
    float textSize(float base) { return largeText ? base + 2 : base; }
    int previewX() { return showNews ? 208 : 0; }
    int previewWidth() { return 792 - previewX(); }
    int networkX() { return showWeather ? 654 : 800; }

    @Override public boolean equals(Object other) {
        if (!(other instanceof HomeAppearance)) return false;
        HomeAppearance value = (HomeAppearance) other;
        return background == value.background && largeText == value.largeText && focus == value.focus
            && showNews == value.showNews && showWeather == value.showWeather;
    }

    @Override public int hashCode() {
        return (((background * 31 + (largeText ? 1 : 0)) * 31 + focus) * 31 + (showNews ? 1 : 0)) * 31 + (showWeather ? 1 : 0);
    }
}
