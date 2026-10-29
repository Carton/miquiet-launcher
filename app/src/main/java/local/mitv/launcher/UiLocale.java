package local.mitv.launcher;

import android.content.Context;
import java.util.Locale;

/** Locale helpers for date formatting and the few language-specific layout sizes. */
final class UiLocale {
    private UiLocale() {}
    static Locale locale(Context context) {
        Locale value = context.getResources().getConfiguration().locale;
        return value == null ? Locale.getDefault() : value;
    }
    static boolean english(Context context) { return "en".equals(locale(context).getLanguage()); }
}
