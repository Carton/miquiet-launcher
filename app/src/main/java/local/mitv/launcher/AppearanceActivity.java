package local.mitv.launcher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Full-screen candidate preview. Only the explicit Apply button writes preferences. */
public final class AppearanceActivity extends Activity {
    private SharedPreferences prefs;
    private HomeAppearance original, draft;
    private HomePreviewView preview;
    private TextView feedback;
    private final Button[] controls = new Button[AppearanceMenu.COUNT];

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        prefs = getSharedPreferences(Apps.PREFS, MODE_PRIVATE);
        original = AppearanceStore.read(prefs);
        draft = state == null ? original : new HomeAppearance(state.getInt("background"),
            state.getBoolean("large"), state.getInt("focus"), state.getBoolean("news", true), state.getBoolean("weather", true));
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BACKGROUND);
        setContentView(root);
        FrameLayout page = new FrameLayout(this);
        root.addView(page, new FrameLayout.LayoutParams(Ui.dp(this, 960), Ui.dp(this, 540), Gravity.CENTER));
        Ui.place(page, Ui.text(this, "桌面设置  /  首页配置  /  外观与内容", 12, Ui.MUTED), 760, 22, 24, 12);
        Ui.place(page, Ui.text(this, "外观与内容", 26, Color.WHITE), 760, 38, 24, 34);
        button(page, 0, "返回", 838, 22, 98, 38, this::finish);

        label(page, "背景色", 24, 84);
        for (int index = 0; index < HomeAppearance.BACKGROUNDS.length; index++) {
            final int selected = index;
            button(page, index + 1, "", 24 + index * 88, 112, 80, 36,
                () -> change(selected, draft.largeText, draft.focus, draft.showNews, draft.showWeather));
        }
        label(page, "首页文字", 24, 164);
        button(page, 4, "", 24, 192, 124, 36, () -> change(draft.background, false, draft.focus, draft.showNews, draft.showWeather));
        button(page, 5, "", 156, 192, 124, 36, () -> change(draft.background, true, draft.focus, draft.showNews, draft.showWeather));
        label(page, "焦点样式", 24, 244);
        button(page, 6, "", 24, 272, 124, 36, () -> change(draft.background, draft.largeText, 0, draft.showNews, draft.showWeather));
        button(page, 7, "", 156, 272, 124, 36, () -> change(draft.background, draft.largeText, 1, draft.showNews, draft.showWeather));
        label(page, "首页内容", 24, 324);
        button(page, 8, "", 24, 352, 256, 36,
            () -> change(draft.background, draft.largeText, draft.focus, !draft.showNews, draft.showWeather));
        button(page, 9, "", 24, 400, 256, 36,
            () -> change(draft.background, draft.largeText, draft.focus, draft.showNews, !draft.showWeather));

        Ui.place(page, Ui.text(this, "首页预览", 15, Color.WHITE), 200, 24, 312, 84);
        TextView sample = Ui.text(this, "示意内容 · 不联网、不播放视频", 12, Ui.MUTED);
        sample.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        Ui.place(page, sample, 424, 24, 512, 84);
        preview = new HomePreviewView(this, prefs);
        Ui.place(page, preview, 624, 351, 312, 112);
        feedback = Ui.text(this, "", 13, Ui.MUTED);
        feedback.setGravity(Gravity.CENTER_VERTICAL);
        Ui.place(page, feedback, 580, 44, 24, 478);
        button(page, 10, "取消", 660, 478, 98, 44, this::finish);
        button(page, 11, "应用到首页", 774, 478, 162, 44, () -> {
            if (!AppearanceStore.save(prefs, draft)) {
                Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_LONG).show();
                return;
            }
            finish();
        });
        render();
        controls[draft.background + 1].requestFocus();
    }

    private void label(FrameLayout page, String value, int x, int y) {
        Ui.place(page, Ui.text(this, value, 16, Color.WHITE), 256, 24, x, y);
    }

    private void button(FrameLayout page, int index, String text, int x, int y, int width, int height, Runnable action) {
        Button button = new Button(this);
        button.setText(text); button.setTextSize(15); button.setTextColor(Color.WHITE);
        button.setAllCaps(false); button.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        button.setMinWidth(0); button.setMinHeight(0);
        controls[index] = button;
        Ui.focusable(button, 0xff30363d, 8);
        button.setOnClickListener(v -> action.run());
        button.setOnKeyListener((v, key, event) -> {
            int dx = 0, dy = 0;
            if (key == KeyEvent.KEYCODE_DPAD_LEFT) dx = -1;
            else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) dx = 1;
            else if (key == KeyEvent.KEYCODE_DPAD_UP) dy = -1;
            else if (key == KeyEvent.KEYCODE_DPAD_DOWN) dy = 1;
            else return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) controls[AppearanceMenu.move(index, dx, dy)].requestFocus();
            return true;
        });
        Ui.place(page, button, width, height, x, y);
    }

    private void change(int background, boolean large, int focus, boolean news, boolean weather) {
        draft = new HomeAppearance(background, large, focus, news, weather);
        render();
    }

    private void choice(int index, String label, boolean selected, String group) {
        Button button = controls[index];
        button.setText(label);
        button.setCompoundDrawables(selected ? Ui.checkmark(this) : null, null, null, null);
        button.setCompoundDrawablePadding(Ui.dp(this, 4));
        button.setSelected(selected);
        button.setContentDescription(group + "，" + label + (selected ? "，已选择" : ""));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, Ui.shape(this, 0xff31564e, Color.WHITE, 8));
        states.addState(new int[]{android.R.attr.state_pressed}, Ui.shape(this, 0xff31564e, Ui.ACCENT, 8));
        states.addState(new int[]{}, Ui.shape(this, selected ? 0xff303e3e : 0xff30363d, selected ? Ui.ACCENT : 0, 8));
        button.setBackground(states);
    }

    private void render() {
        for (int index = 0; index < HomeAppearance.BACKGROUNDS.length; index++) {
            choice(index + 1, HomeAppearance.BACKGROUNDS[index], draft.background == index, "背景色");
        }
        choice(4, "标准", !draft.largeText, "首页文字");
        choice(5, "大字", draft.largeText, "首页文字");
        choice(6, "白色描边", draft.focus == 0, "焦点样式");
        choice(7, "柔和底色", draft.focus == 1, "焦点样式");
        choice(8, "显示新闻：" + (draft.showNews ? "开启" : "关闭"), draft.showNews, "首页内容");
        choice(9, "显示天气：" + (draft.showWeather ? "开启" : "关闭"), draft.showWeather, "首页内容");
        preview.setAppearance(draft);
        feedback.setText(draft.equals(original) ? "方向键选择，确定修改；返回取消" : "正在预览 · 尚未保存；返回取消");
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("background", draft.background); state.putBoolean("large", draft.largeText);
        state.putInt("focus", draft.focus); state.putBoolean("news", draft.showNews); state.putBoolean("weather", draft.showWeather);
    }

    @Override public boolean onKeyDown(int key, KeyEvent event) {
        if (key == KeyEvent.KEYCODE_MENU) { finish(); return true; }
        return super.onKeyDown(key, event);
    }
}
