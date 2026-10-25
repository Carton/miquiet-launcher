package local.mitv.launcher;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One-screen, remote-first settings. The two explanatory captions are deliberately omitted. */
final class SettingsDialog {
    private static final int[] ICONS = {R.drawable.menu_monitor_cog, R.drawable.menu_layout_grid,
        R.drawable.menu_wifi, R.drawable.menu_bluetooth, R.drawable.menu_monitor, R.drawable.menu_volume_2,
        R.drawable.menu_folder_open, R.drawable.menu_cast, R.drawable.menu_images, R.drawable.menu_shield_check,
        R.drawable.menu_info, R.drawable.menu_x};
    private SettingsDialog() {}

    static Dialog show(Activity activity, Runnable[] actions, Runnable closed) {
        if (actions.length != SettingsMenu.LABELS.length) throw new IllegalArgumentException("Menu action count");
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(activity,24),Ui.dp(activity,20),Ui.dp(activity,24),Ui.dp(activity,18));
        content.setBackground(Ui.shape(activity,0xff24282d,0xff424a55,16));
        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        ImageView gear = new ImageView(activity); gear.setImageResource(R.drawable.menu_settings);
        gear.setImageTintList(ColorStateList.valueOf(Ui.ACCENT));
        heading.addView(gear,new LinearLayout.LayoutParams(Ui.dp(activity,28),Ui.dp(activity,28)));
        TextView title = Ui.text(activity,"桌面设置",26,Color.WHITE);
        LinearLayout.LayoutParams titleLayout = new LinearLayout.LayoutParams(-2,-2);
        titleLayout.leftMargin = Ui.dp(activity,12); heading.addView(title,titleLayout);
        content.addView(heading,new LinearLayout.LayoutParams(-1,Ui.dp(activity,40)));

        LinearLayout grid = new LinearLayout(activity); grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridLayout = new LinearLayout.LayoutParams(-1,0,1);
        gridLayout.topMargin = Ui.dp(activity,14); content.addView(grid,gridLayout);
        TextView detail = Ui.text(activity,SettingsMenu.DETAILS[0],12,0xffc7e5da);
        detail.setSingleLine(true); detail.setEllipsize(TextUtils.TruncateAt.END);
        detail.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams detailLayout = new LinearLayout.LayoutParams(-1,Ui.dp(activity,20));
        detailLayout.topMargin = Ui.dp(activity,12); content.addView(detail,detailLayout);
        View[] tiles = new View[actions.length];
        ColorStateList iconColors = new ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{}},
            new int[]{0xffa2f1d9,0xffbdc7d1});
        for (int row=0; row<3; row++) {
            LinearLayout line = new LinearLayout(activity);
            LinearLayout.LayoutParams rowLayout = new LinearLayout.LayoutParams(-1,0,1);
            if (row<2) rowLayout.bottomMargin=Ui.dp(activity,10);
            grid.addView(line,rowLayout);
            for (int column=0; column<SettingsMenu.COLUMNS; column++) {
                final int index = row*SettingsMenu.COLUMNS+column;
                LinearLayout tile = new LinearLayout(activity); tiles[index]=tile;
                tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER);
                tile.setPadding(Ui.dp(activity,6),Ui.dp(activity,5),Ui.dp(activity,6),Ui.dp(activity,5));
                tile.setContentDescription(SettingsMenu.LABELS[index]);
                StateListDrawable states = new StateListDrawable();
                states.addState(new int[]{android.R.attr.state_focused},Ui.shape(activity,0xff31564e,Color.WHITE,10));
                states.addState(new int[]{android.R.attr.state_pressed},Ui.shape(activity,0xff31564e,Ui.ACCENT,10));
                states.addState(new int[]{},Ui.shape(activity,0xff30363d,0,10));
                tile.setBackground(states); tile.setFocusable(true); tile.setFocusableInTouchMode(true); tile.setClickable(true);
                ImageView icon = new ImageView(activity); icon.setImageResource(ICONS[index]);
                icon.setImageTintList(iconColors); icon.setDuplicateParentStateEnabled(true);
                icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                tile.addView(icon,new LinearLayout.LayoutParams(Ui.dp(activity,32),Ui.dp(activity,32)));
                TextView label = Ui.text(activity,SettingsMenu.LABELS[index],17,Color.WHITE);
                label.setSingleLine(true); label.setEllipsize(TextUtils.TruncateAt.END); label.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams labelLayout = new LinearLayout.LayoutParams(-1,-2);
                labelLayout.topMargin=Ui.dp(activity,8); tile.addView(label,labelLayout);
                tile.setOnFocusChangeListener((v,focused)->{if(focused){
                    detail.setText(SettingsMenu.DETAILS[index]);
                    android.util.Log.i("QuietTVSettings","Focus tile="+index);
                }});
                tile.setOnClickListener(v->{android.util.Log.i("QuietTVSettings","Open tile="+index);dialog.dismiss();actions[index].run();});
                tile.setOnKeyListener((v,key,event)->{
                    int dx=0,dy=0;
                    if(key==KeyEvent.KEYCODE_DPAD_LEFT)dx=-1;
                    else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)dx=1;
                    else if(key==KeyEvent.KEYCODE_DPAD_UP)dy=-1;
                    else if(key==KeyEvent.KEYCODE_DPAD_DOWN)dy=1;
                    else return false;
                    if(event.getAction()==KeyEvent.ACTION_DOWN) tiles[SettingsMenu.move(index,dx,dy)].requestFocus();
                    return true;
                });
                LinearLayout.LayoutParams tileLayout = new LinearLayout.LayoutParams(0,-1,1);
                if(column<SettingsMenu.COLUMNS-1) tileLayout.rightMargin=Ui.dp(activity,10);
                line.addView(tile,tileLayout);
            }
        }
        dialog.setContentView(content);
        dialog.setOnDismissListener(d->closed.run());
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnKeyListener((d,key,event)->{
            if(key!=KeyEvent.KEYCODE_MENU)return false;
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0)dialog.dismiss();return true;
        });
        Window window=dialog.getWindow();
        if(window!=null){
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.72f); window.setGravity(Gravity.CENTER);
        }
        dialog.show();
        if(window!=null){
            DisplayMetrics size=activity.getResources().getDisplayMetrics();
            window.setLayout(Math.min(Ui.dp(activity,780),size.widthPixels-Ui.dp(activity,32)),
                Math.min(Ui.dp(activity,430),size.heightPixels-Ui.dp(activity,32)));
        }
        tiles[0].requestFocus();
        return dialog;
    }
}
