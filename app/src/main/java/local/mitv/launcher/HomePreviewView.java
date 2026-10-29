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
    private final int[] orderedPorts;
    private final Ui.Icon sound, network, settings, factory, allApps;
    private final Drawable weatherIcon;

    HomePreviewView(Context context, SharedPreferences prefs) {
        super(context);
        sound = previewIcon(context, "sound", Ui.MUTED);
        network = previewIcon(context, "wifi", Ui.MUTED);
        network.setNetwork(true, true, 3);
        settings = previewIcon(context, "settings", Ui.MUTED);
        factory = previewIcon(context, "factory", Color.WHITE);
        allApps = previewIcon(context, "apps", Color.WHITE);
        weatherIcon = context.getDrawable(R.drawable.weather_sun).mutate();
        Apps apps = new Apps(context);
        for (String pkg : apps.favorites()) {
            if (favorites.size() == 5) break;
            Apps.App app = apps.all.get(pkg);
            if (app != null) favorites.add(app);
        }
        for (int port = 1; port <= 3; port++) inputNames[port] = InputNames.normalize(prefs.getString(InputNames.key(port), ""));
        int savedPort = prefs.getInt("selected_port", 3);
        selectedPort = savedPort >= 1 && savedPort <= 3 ? savedPort : 3;
        orderedPorts = SourceMenu.order(prefs.getString(SourceMenu.RECENT, ""));
        setContentDescription(getContext().getString(R.string.home_mock_preview_without_live_news_weather));
        setFocusable(false);
    }

    void setAppearance(HomeAppearance value) { appearance = value; invalidate(); }

    private static Ui.Icon previewIcon(Context context, String kind, int color) {
        Ui.Icon icon = new Ui.Icon(context, kind, color);
        icon.layout(0, 0, 32, 32);
        return icon;
    }

    private void icon(Canvas canvas, Ui.Icon icon, float x, float y, float size) {
        canvas.save(); canvas.translate(x, y); canvas.scale(size / 32f, size / 32f);
        icon.draw(canvas); canvas.restore();
    }

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
        label(canvas, getContext().getString(R.string.inputs), 31, 31, appearance.textSize(15), Color.WHITE, 88);
        icon(canvas, sound, appearance.networkX() - 38, 14, 25);
        icon(canvas, network, appearance.networkX() + 6, 14, 25);
        if (appearance.showWeather) {
            weatherIcon.setBounds(696, 10, 726, 40); weatherIcon.draw(canvas);
            label(canvas, getContext().getString(R.string.preview_weather_sample), 734, 31, 17, Color.WHITE, 108);
        }
        label(canvas, "20:30", 850, 31, 19, Color.WHITE, 60);
        icon(canvas, settings, 916, 14, 25);
        box(canvas, appearance.panelColor(), appearance.heroX(), 54, appearance.heroWidth(), 332, 8);
        if (appearance.showNews) {
            box(canvas, appearance.newsColor(), 12, 54, 200, 332, 8);
            label(canvas, getContext().getString(R.string.china_world_science), 24, 84, appearance.textSize(15), Ui.ACCENT, 176);
            String[] titles = {getContext().getString(R.string.today_s_sample_headline), getContext().getString(R.string.exploring_nature_and_science), getContext().getString(R.string.new_discoveries_in_everyday_life)};
            for (int index = 0; index < titles.length; index++) {
                label(canvas, titles[index], 26, 132 + index * 83, appearance.textSize(14), Color.WHITE, 170);
                label(canvas, getContext().getString(R.string.sample_news_content), 26, 156 + index * 83, 11, Ui.MUTED, 170);
            }
            label(canvas, getContext().getString(R.string.source_sample_content), 26, 372, 10, Ui.MUTED, 160);
        }
        int previewX = appearance.heroX() + appearance.previewX();
        box(canvas, 0xff4061ce, previewX + 4, 58, appearance.previewWidth() - 8, 324, 6);
        label(canvas, getContext().getString(R.string.input_preview), previewX + 30, 220, appearance.textSize(20), Color.WHITE, appearance.previewWidth() - 60);
        label(canvas, InputNames.display(selectedPort, inputNames[selectedPort]), previewX + 16, 365,
            appearance.textSize(18), Color.WHITE, appearance.previewWidth() - 34);
        int[] ports = orderedPorts;
        for (int index = 0; index < ports.length; index++) {
            int port = ports[index];
            String name = port == 0 ? getContext().getString(index == SourceMenu.FILES ? R.string.files : R.string.all_inputs) : inputNames[port];
            boolean named = port > 0 && !name.isEmpty();
            int captionX = appearance.heroX() + appearance.inputsX() + 45;
            label(canvas, port > 0 && !named ? "HDMI" + port : name, captionX, 94 + index * 66,
                appearance.textSize(18), Color.WHITE, 98);
            if (named) label(canvas, "HDMI" + port, captionX, 113 + index * 66, 12, Ui.MUTED, 98);
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
                title = index == favorites.size() ? getContext().getString(R.string.factory_home) : getContext().getString(R.string.my_apps);
                icon(canvas, index == favorites.size() ? factory : allApps, x + 42, 426, 49);
            }
            label(canvas, title, x + 4, 520, appearance.textSize(15), Ui.MUTED, 122);
        }
        canvas.restore();
    }
}
