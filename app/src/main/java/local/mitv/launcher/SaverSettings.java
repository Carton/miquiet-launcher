package local.mitv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;

final class SaverSettings {
    private final Activity activity;
    private final SharedPreferences prefs;
    SaverSettings(Activity activity, SharedPreferences prefs) { this.activity = activity; this.prefs = prefs; }
    void show() {
        boolean enabled = prefs.getBoolean("saver_enabled", true);
        int count = SaverImages.list(activity).size();
        String[] choices = {"图片管理与导入 · " + count + "/24 张", "预览屏保",
            "等待时间：" + SaverPolicy.waitMinutes(prefs.getInt("saver_wait_minutes", 12)) + " 分钟",
            "图片间隔：" + SaverPolicy.slideSeconds(prefs.getInt("saver_slide_seconds", 30)) + " 秒",
            enabled ? "关闭本地屏保" : "开启本地屏保"};
        new AlertDialog.Builder(activity).setTitle("本地屏保 · " + (enabled ? "已开启" : "已关闭"))
            .setItems(choices, (d, which) -> {
                if (which == 0) activity.startActivity(new Intent(activity,SaverLibraryActivity.class));
                else if (which == 1) activity.startActivity(new Intent(activity,ScreensaverActivity.class));
                else if (which == 2) chooseTime(true);
                else if (which == 3) chooseTime(false);
                else { prefs.edit().putBoolean("saver_enabled", !enabled).apply(); show(); }
            }).setNegativeButton("关闭", null)
            .setNeutralButton("说明", (d,w) -> new AlertDialog.Builder(activity).setTitle("本地图片屏保")
                .setMessage("仅在新桌面前台静置时启动，按任意键返回。\n\n在图片管理页中，可预览、批量导入或移除照片；也可从电脑或手机的浏览器向电视传图。\n\n照片会复制到电视内，拔掉U盘后仍可播放；移除屏保副本不删除原图。最多24张。\n\n未添加照片时使用三幅内置风景和移动时钟。屏保自身不联网，也不展示广告。")
                .setPositiveButton("关闭",null).show()).show();
    }
    private void chooseTime(boolean wait) {
        int[] values = wait ? new int[]{1,2,5,10,12,15,30,60} : new int[]{10,15,30,60,120,300};
        String[] labels = new String[values.length];
        int selected = 0, current = prefs.getInt(wait ? "saver_wait_minutes" : "saver_slide_seconds", wait ? 12 : 30);
        for (int i = 0; i < values.length; i++) { labels[i] = values[i] + (wait ? " 分钟" : " 秒"); if (values[i] == current) selected = i; }
        new AlertDialog.Builder(activity).setTitle(wait ? "静置多久进入屏保" : "图片轮播间隔")
            .setSingleChoiceItems(labels, selected, (d,index) -> {
                prefs.edit().putInt(wait ? "saver_wait_minutes" : "saver_slide_seconds", values[index]).apply(); d.dismiss(); show();
            }).setNegativeButton("取消",(d,w) -> show()).show();
    }
}
