package io.github.cw5n1h2txyewy.bingwallpapers;

import android.content.*;
import android.content.res.Configuration;

/** Check real resource selection for saved languages and light/dark/system settings. */
public final class AppearanceChecks {
    private AppearanceChecks() {}

    /** Exercise all six explicit combinations and restore the original user options. */
    public static void run(Context context) {
        Context base = context.getApplicationContext();
        SharedPreferences preferences = base.getSharedPreferences("appearance", Context.MODE_PRIVATE);
        String oldLanguage = preferences.getString("language", "zh");
        String oldTheme = preferences.getString("theme", "system");
        String market = base.getSharedPreferences("MainActivity", Context.MODE_PRIVATE).getString("market", "zh-CN");
        String[] languages = {"zh", "en", "ja"};
        String[] recent = {"最近八天", "Last 8 days", "最近8日間"};
        try {
            for (int index = 0; index < languages.length; index++) {
                for (String theme : new String[]{"light", "dark"}) {
                    preferences.edit().putString("language", languages[index]).putString("theme", theme).commit();
                    Context configured = AppSettings.wrap(base);
                    require(configured.getString(R.string.recent).equals(recent[index]), "Wrong tab language");
                    require(!configured.getString(R.string.download_result, 1, 2, 0).contains("%"), "Download placeholders not formatted");
                    require(!Resolution.UHD.label(configured).isEmpty(), "Missing size translation");
                    int night = configured.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                    require(night == (theme.equals("dark") ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO), "Wrong night mode");
                    int background = configured.getColor(R.color.background);
                    require(background == (theme.equals("dark") ? 0xff121a16 : 0xfff5f7f3), "Wrong palette");
                    require(AppSettings.text(base, R.string.recent).equals(recent[index]), "Service string uses stale locale");
                }
            }
            preferences.edit().putString("language", "system").putString("theme", "system").commit();
            Configuration selected = AppSettings.wrap(base).getResources().getConfiguration();
            Configuration original = base.getResources().getConfiguration();
            require(selected.getLocales().equals(original.getLocales()), "System locale not followed");
            require((selected.uiMode & Configuration.UI_MODE_NIGHT_MASK) == (original.uiMode & Configuration.UI_MODE_NIGHT_MASK), "System theme not followed");
            require(base.getSharedPreferences("MainActivity", Context.MODE_PRIVATE).getString("market", "zh-CN").equals(market), "Interface settings changed Bing market");
        } finally {
            preferences.edit().putString("language", oldLanguage).putString("theme", oldTheme).commit();
        }
    }

    /** Fail with the resource or persistence contract that was violated. */
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
