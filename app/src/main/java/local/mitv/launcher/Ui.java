package local.mitv.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

final class Ui {
    static final int BACKGROUND = 0xff1a1c20;
    static final int PANEL = 0xff303237;
    static final int MUTED = 0xffa2a5aa;
    static final int ACCENT = 0xff63debd;
    private Ui() {}

    static int dp(Context context, float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    static GradientDrawable shape(Context c, int color, int stroke, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color); bg.setCornerRadius(dp(c, radius));
        if (stroke != 0) bg.setStroke(dp(c, 2), stroke);
        return bg;
    }

    static void focusable(View view, int normal, int radius) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, shape(view.getContext(), 0xff464a50, Color.WHITE, radius));
        states.addState(new int[]{android.R.attr.state_pressed}, shape(view.getContext(), 0xff464a50, ACCENT, radius));
        states.addState(new int[]{}, shape(view.getContext(), normal, 0, radius));
        view.setBackground(states); view.setFocusable(true); view.setFocusableInTouchMode(true); view.setClickable(true);
    }

    static TextView text(Context context, String value, float size, int color) {
        TextView text = new TextView(context);
        text.setText(value); text.setTextColor(color); text.setTextSize(size);
        text.setFontFeatureSettings("tnum");
        text.setIncludeFontPadding(false);
        return text;
    }

    static void place(FrameLayout parent, View view, int width, int height, int x, int y) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(parent.getContext(), width), dp(parent.getContext(), height));
        params.leftMargin = dp(parent.getContext(), x); params.topMargin = dp(parent.getContext(), y);
        parent.addView(view, params);
    }

    static final class Icon extends View {
        private final String kind;
        private final Paint paint = new Paint(3);
        private final int color;
        Icon(Context context, String kind, int color) { super(context); this.kind = kind; this.color = color; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            canvas.save();
            canvas.scale(getWidth() / 32f, getHeight() / 32f);
            paint.setColor(color); paint.setStrokeWidth(2.2f); paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND); paint.setStrokeCap(Paint.Cap.ROUND);
            if (kind.equals("hdmi")) {
                Path path = new Path(); path.moveTo(4, 11); path.lineTo(28, 11); path.lineTo(28, 17); path.lineTo(24, 21); path.lineTo(8, 21); path.lineTo(4, 17); path.close();
                canvas.drawPath(path, paint); canvas.drawLine(9, 15, 23, 15, paint);
            } else if (kind.equals("search")) {
                canvas.drawCircle(13, 13, 8, paint); canvas.drawLine(19, 19, 27, 27, paint);
            } else if (kind.equals("wifi")) {
                canvas.drawArc(new RectF(1, 5, 31, 31), 225, 90, false, paint);
                canvas.drawArc(new RectF(7, 11, 25, 29), 225, 90, false, paint);
                canvas.drawCircle(16, 24, 1, paint);
            } else if (kind.equals("router")) {
                canvas.drawRoundRect(new RectF(4, 16, 28, 27), 2, 2, paint);
                canvas.drawLine(8, 16, 6, 3, paint); canvas.drawLine(24, 16, 26, 3, paint);
                for (int i = 10; i <= 22; i += 6) canvas.drawPoint(i, 22, paint);
            } else if (kind.equals("sound")) {
                Path path = new Path(); path.moveTo(4, 12); path.lineTo(10, 12); path.lineTo(17, 6); path.lineTo(17, 26); path.lineTo(10, 20); path.lineTo(4, 20); path.close(); canvas.drawPath(path, paint);
                canvas.drawArc(new RectF(14, 9, 28, 23), -55, 110, false, paint);
            } else if (kind.equals("settings")) {
                canvas.drawCircle(16, 16, 9, paint); canvas.drawCircle(16, 16, 3, paint);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    canvas.drawLine(16 + (float)Math.cos(a) * 10, 16 + (float)Math.sin(a) * 10, 16 + (float)Math.cos(a) * 13, 16 + (float)Math.sin(a) * 13, paint);
                }
            } else if (kind.equals("apps")) {
                for (int x : new int[]{5, 18}) for (int y : new int[]{5, 18}) canvas.drawRoundRect(new RectF(x, y, x + 9, y + 9), 1, 1, paint);
            } else {
                canvas.drawLine(5, 16, 25, 16, paint); canvas.drawLine(18, 9, 25, 16, paint); canvas.drawLine(18, 23, 25, 16, paint);
            }
            canvas.restore();
        }
    }
}
