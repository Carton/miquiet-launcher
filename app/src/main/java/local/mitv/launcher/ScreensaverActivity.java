package local.mitv.launcher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local-only slideshow. It has no network path and opens only from the visible desktop. */
public final class ScreensaverActivity extends Activity {
    static final String EXTRA_PREVIEW = "preview";
    private final Handler handler = new Handler();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private FrameLayout root;
    private ImageView photo;
    private Ambient ambient;
    private TextView clock, hint;
    private List<File> images;
    private boolean active, preview;
    private int slide, generation, clockStep;
    private java.util.concurrent.Future<?> pending;
    private final Runnable next = () -> { showSlide(slide+1); scheduleNext(); };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preview=getIntent().getBooleanExtra(EXTRA_PREVIEW,false);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        root = new FrameLayout(this); root.setBackgroundColor(Color.BLACK); setContentView(root);
        ambient = new Ambient(this); root.addView(ambient, new FrameLayout.LayoutParams(-1, -1));
        photo = new ImageView(this); photo.setScaleType(ImageView.ScaleType.FIT_CENTER); photo.setAlpha(.72f);
        root.addView(photo, new FrameLayout.LayoutParams(-1, -1));
        clock = Ui.text(this, "", 38, 0xaaffffff); clock.setGravity(Gravity.CENTER); clock.setShadowLayer(3, 0, 1, Color.BLACK);
        root.addView(clock);
        hint = Ui.text(this, preview ? ScreensaverActivity.this.getString(R.string.left_right_change_photo_back_exit_preview) : ScreensaverActivity.this.getString(R.string.press_any_key_to_return_home), 16, 0x88ffffff);
        hint.setGravity(Gravity.CENTER); hint.setShadowLayer(3,0,1,Color.BLACK);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(-1, Ui.dp(this, 45), Gravity.BOTTOM);
        root.addView(hint, hintParams);
        if(!preview)handler.postDelayed(() -> hint.setVisibility(View.GONE), 5000);
        images = SaverImages.list(this);
        android.util.Log.i("QuietTVSaver", "started images=" + images.size()+" preview="+preview);
    }

    @Override protected void onResume() { super.onResume(); active = true; showSlide(slide); scheduleNext(); }
    @Override protected void onPause() {
        active = false; generation++; handler.removeCallbacksAndMessages(null);
        super.onPause(); if (!isFinishing()) finish();
    }
    @Override protected void onDestroy() { active = false; handler.removeCallbacksAndMessages(null); worker.shutdownNow(); photo.setImageDrawable(null); super.onDestroy(); }

    private void scheduleNext() {
        handler.postDelayed(next,SaverPolicy.slideSeconds(
            getSharedPreferences(Apps.PREFS,MODE_PRIVATE).getInt("saver_slide_seconds",30))*1000L);
    }
    private void showSlide(int requested) {
        int count=images.isEmpty()?3:images.size();
        slide=(requested%count+count)%count;
        final int index=slide;
        final int token = ++generation;
        if(pending!=null)pending.cancel(false);
        if(preview)hint.setText(ScreensaverActivity.this.getString(R.string.left_right_change_photo)+(index+1)+"/"+count+ScreensaverActivity.this.getString(R.string.back_exit_preview));
        ambient.scene = index % 3; ambient.invalidate();
        android.text.SpannableString time = new android.text.SpannableString(new SimpleDateFormat(ScreensaverActivity.this.getString(R.string.screensaver_clock_format), UiLocale.locale(this)).format(new Date()));
        time.setSpan(new android.text.style.RelativeSizeSpan(.45f),6,time.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        clock.setText(time);
        // Move the entire clock every slide rather than keeping bright text fixed on the panel.
        int clockPosition=clockStep++%6;
        FrameLayout.LayoutParams clockParams = new FrameLayout.LayoutParams(Ui.dp(this, 270), Ui.dp(this, 110));
        clockParams.leftMargin = Ui.dp(this, 55 + clockPosition % 3 * 260);
        clockParams.topMargin = Ui.dp(this, clockPosition % 2 == 0 ? 60 : 355);
        clock.setLayoutParams(clockParams);
        if (images.isEmpty()) { photo.setImageDrawable(null); return; }
        File selected = images.get(index % images.size());
        pending=worker.submit(() -> {
            Bitmap bitmap = null;
            try { bitmap = SaverImages.decode(selected, getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels); }
            catch (RuntimeException | OutOfMemoryError error) { android.util.Log.w("QuietTVSaver", "Cannot decode local image"); }
            final Bitmap decoded = bitmap;
            handler.post(() -> {
                if (!active || generation != token) { if (decoded != null) decoded.recycle(); return; }
                photo.setImageBitmap(decoded);
                android.util.Log.i("QuietTVSaver", "slide=" + index + " local=" + (decoded != null));
            });
        });
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_POWER || event.getKeyCode() == KeyEvent.KEYCODE_SLEEP) return super.dispatchKeyEvent(event);
        if(preview) {
            if(event.getAction()==KeyEvent.ACTION_DOWN) {
                int key=event.getKeyCode();
                if(key==KeyEvent.KEYCODE_BACK)finish();
                else if(key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT) {
                    handler.removeCallbacks(next);
                    showSlide(slide+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1));scheduleNext();
                }
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) finish();
        return true;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event) { if (!preview && event.getAction() == MotionEvent.ACTION_DOWN) finish(); return true; }

    /** Three original geometric landscapes, drawn locally when no photos are selected. */
    private static final class Ambient extends View {
        int scene;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Ambient(android.content.Context context) { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            int[][] colors = {{0xff121e36,0xff315363,0xff1c3548},{0xff291e31,0xff74534c,0xff352b40},{0xff142b2d,0xff47716a,0xff203e44}};
            int[] palette = colors[scene];
            paint.setAlpha(255);
            paint.setShader(new LinearGradient(0,0,w,h,palette[0],palette[1],Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,w,h,paint); paint.setShader(null);
            paint.setColor(0x55e8d6aa); canvas.drawCircle(w * .72f, h * .30f, h * .09f, paint);
            for (int i = 0; i < 4; i++) {
                paint.setColor(i % 2 == 0 ? palette[2] : palette[0]); paint.setAlpha(140 + i * 25);
                Path ridge = new Path(); ridge.moveTo(0,h);
                ridge.lineTo(0,h * (.57f + i * .08f));
                ridge.cubicTo(w*.22f,h*(.30f+i*.12f), w*.40f,h*(.96f-i*.04f),w,h*(.43f+i*.13f));
                ridge.lineTo(w,h);ridge.close();canvas.drawPath(ridge,paint);
            }
            paint.setAlpha(255); paint.setColor(0x55000000); canvas.drawRect(0,0,w,h,paint);
        }
    }
}
