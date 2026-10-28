package io.github.bingwallpapers;

import android.content.*;
import android.content.res.Configuration;
import android.os.LocaleList;
import java.util.Locale;

/** Persistent app-only locale and night mode, independent of the Bing market. */
public final class AppSettings {
    private AppSettings() {}

    /** Apply saved options before Activity and service resources are inflated. */
    public static Context wrap(Context base) {
        SharedPreferences preferences = base.getSharedPreferences("appearance", Context.MODE_PRIVATE);
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        String language = preferences.getString("language", "zh");
        if (!language.equals("system")) configuration.setLocales(new LocaleList(Locale.forLanguageTag(language)));
        String theme = preferences.getString("theme", "system");
        if (!theme.equals("system")) configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (theme.equals("dark") ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return base.createConfigurationContext(configuration);
    }

    /** Resolve a string using the latest locale, including in a running download service. */
    public static String text(Context context, int resource, Object... arguments) {
        return wrap(context.getApplicationContext()).getString(resource, arguments);
    }

    /** Keep a translated status only while it matches the current interface locale. */
    public static String lastStatus(Context context) {
        SharedPreferences preferences = context.getSharedPreferences("bing", Context.MODE_PRIVATE);
        String tag = context.getResources().getConfiguration().getLocales().get(0).toLanguageTag();
        return tag.equals(preferences.getString("status-language", ""))
                ? preferences.getString("status", context.getString(R.string.save_hint)) : context.getString(R.string.save_hint);
    }
}
