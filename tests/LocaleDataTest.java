package local.mitv.launcher;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import java.util.Locale;

/** Verifies packaged Android resource selection without changing the device's language or preferences. */
public final class LocaleDataTest {
    private static int assertions;
    private static void check(boolean value) {
        assertions++;
        if (!value) throw new AssertionError("Locale assertion " + assertions);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected APK path");
        AssetManager assets = AssetManager.class.newInstance();
        int cookie = (Integer) AssetManager.class.getMethod("addAssetPath", String.class).invoke(assets, args[0]);
        check(cookie != 0);
        DisplayMetrics metrics = new DisplayMetrics(); metrics.setToDefaults();
        for (String language : new String[]{"zh", "en", "fr"}) {
            Configuration configuration = new Configuration(); configuration.setToDefaults();
            configuration.setLocale(new Locale(language));
            final Resources resources = new Resources(assets, metrics, configuration);
            Context localized = new ContextWrapper(null) {
                @Override public Resources getResources() { return resources; }
            };
            boolean english = language.equals("en");
            check(localized.getString(R.string.app_name).equals(english ? "MiTV Quiet Launcher" : "简净桌面"));
            check(localized.getString(R.string.appearance_content).equals(english ? "Appearance & content" : "外观与内容"));
            check(localized.getString(UiLabels.weather(DashboardPolicy.condition(0))).equals(english ? "Clear" : "晴"));
            check(localized.getString(R.string.switch_off).equals(english ? "Off" : "关闭"));
            check(localized.getString(R.string.opening_publisher, "Source")
                .equals(english ? "Opening Source…" : "正在打开Source…"));
            check(localized.getString(R.string.screensaver_clock_format).contains(english ? "MMM" : "月"));
            check(new java.text.SimpleDateFormat(localized.getString(R.string.screensaver_clock_format),
                UiLocale.locale(localized)).format(new java.util.Date()).length() > 5);
            check(UiLabels.photoFailure(localized, new PhotoFailure(PhotoFailure.Reason.LIMIT))
                .equals(english ? "Up to 24 photos can be saved" : "最多保存24张图片"));
            UploadStrings upload = new UploadStrings(localized);
            for (PhotoUploadServer.Message message : PhotoUploadServer.Message.values()) check(upload.text(message) != null);
            String page = upload.text(PhotoUploadServer.Message.PAGE);
            check(page.contains(english ? "lang='en'" : "lang='zh-CN'"));
            check(page.contains("hashchange") && page.contains("connectFromScan();"));
            check(page.contains("if(!/^\\d{6}$/.test(c)||!f.length)"));
            check(page.contains("'X-QuietTV-Code':c"));
        }
        System.out.println("PASS: " + assertions + " packaged Android locale assertions");
    }
}
