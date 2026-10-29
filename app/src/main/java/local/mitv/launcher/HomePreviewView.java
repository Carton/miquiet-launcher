package local.mitv.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** A local miniature of the home geometry, with sample content and existing app icons. */
final class HomePreviewView extends View {
    private HomeAppearance appearance = HomeAppearance.DEFAULT;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final List<Apps.App> favorites = new ArrayList<>();
    private final String[] inputNames = new String[4];
    private final int selectedPort;

    HomePreviewView(Context context, SharedPreferences prefs) {
        super(context);
        Apps apps = new Apps(context);
        for (String pkg : apps.favorites()) {
            if (favorites.size() == 5) break;
            Apps.App app = apps.all.get(pkg);
            if (app != null) favorites.add(app);
        }
        for (int port = 1; port <= 3; port++) inputNames[port] = InputNames.normalize(prefs.getString(InputNames.key(port), ""));
        int savedPort = prefs.getInt("selected_port", 3);
        selectedPort = savedPort >= 1 && savedPort <= 3 ? savedPort : 3;
        setContentDescription("首页示意预览，不包含实时新闻、天气或视频");
        setFocusable(false);
    }

    void setAppearance(HomeAppearance value) { appearance = value; invalidate(); }

    private void box(Canvas canvas, int color, float left, float top, float width, float height, int radius) {
        paint.setColor(color); paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(left, top, left + width, top + height), radius, radius, paint);
    }

    private void label(Canvas canvas, String value, float x, float y, float size, int color, float width) {
        text.setTextSize(size); text.setColor(color);
        canvas.drawText(TextUtils.ellipsize(value, text, width, TextUtils.TruncateAt.END).toString(), x, y, text);
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.scale(getWidth() / 960f, getHeight() / 540f);
        canvas.drawColor(appearance.backgroundColor());
        box(canvas, appearance.newsColor(), 12, 8, 110, 34, 20);
        label(canvas, "信号源", 31, 31, appearance.textSize(15), Color.WHITE, 88);
        label(canvas, "♪", appearance.networkX() - 30, 31, 20, Ui.MUTED, 30);
        label(canvas, "Wi-Fi", appearance.networkX() + 1, 30, 12, Ui.MUTED, 42);
        if (appearance.showWeather) label(canvas, "☀ 26°C 晴", 704, 31, 17, Color.WHITE, 138);
        label(canvas, "20:30", 850, 31, 19, Color.WHITE, 60);
        label(canvas, "⚙", 921, 31, 22, Ui.MUTED, 30);
        box(canvas, appearance.panelColor(), 12, 54, 936, 332, 8);
        if (appearance.showNews) {
            box(canvas, appearance.newsColor(), 12, 54, 200, 332, 8);
            label(canvas, "国内   国际   科技", 24, 84, appearance.textSize(15), Ui.ACCENT, 176);
            String[] titles = {"今日新闻标题示例", "探索自然，发现科学", "科技与生活的新发现"};
            for (int index = 0; index < titles.length; index++) {
                label(canvas, titles[index], 26, 132 + index * 83, appearance.textSize(14), Color.WHITE, 170);
                label(canvas, "新闻内容示意", 26, 156 + index * 83, 11, Ui.MUTED, 170);
            }
            label(canvas, "来源 · 内容示意", 26, 372, 10, Ui.MUTED, 160);
        }
        int previewX = 12 + appearance.previewX();
        box(canvas, 0xff4061ce, previewX + 4, 58, appearance.previewWidth() - 8, 324, 6);
        label(canvas, "信号源预览", previewX + 30, 220, appearance.textSize(20), Color.WHITE, appearance.previewWidth() - 60);
        label(canvas, InputNames.display(selectedPort, inputNames[selectedPort]), previewX + 16, 365,
            appearance.textSize(18), Color.WHITE, appearance.previewWidth() - 34);
        int[] ports = {3, 0, 1, 2, 0};
        for (int index = 0; index < ports.length; index++) {
            int port = ports[index];
            String name = port == 0 ? (index == 1 ? "文件管理" : "全部") : inputNames[port];
            boolean named = port > 0 && !name.isEmpty();
            label(canvas, port > 0 && !named ? "HDMI" + port : name, 841, 94 + index * 66,
                appearance.textSize(18), Color.WHITE, 98);
            if (named) label(canvas, "HDMI" + port, 841, 113 + index * 66, 12, Ui.MUTED, 98);
        }
        for (int index = 0; index < favorites.size() + 2; index++) {
            int x = 18 + index * 132;
            if (index == 0) {
                box(canvas, appearance.focusColor(), x, 404, 130, 122, 10);
                paint.setColor(appearance.focusStroke()); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2);
                canvas.drawRoundRect(new RectF(x, 404, x + 130, 526), 10, 10, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            box(canvas, appearance.panelColor(), x + 19, 408, 94, 88, 20);
            String title;
            if (index < favorites.size()) {
                Apps.App app = favorites.get(index);
                title = app.label;
                Drawable icon = app.icon;
                icon.setBounds(x + 24, 410, x + 108, 494); icon.draw(canvas);
            } else {
                title = index == favorites.size() ? "原厂桌面" : "我的应用";
                label(canvas, index == favorites.size() ? "⌂" : "▦", x + 47, 467, 40, Color.WHITE, 60);
            }
            label(canvas, title, x + 4, 520, appearance.textSize(15), Ui.MUTED, 122);
        }
        canvas.restore();
    }
}
