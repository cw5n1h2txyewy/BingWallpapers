package io.github.cw5n1h2txyewy.bingwallpapers

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/** Persistent app-only locale and night mode, independent of the Bing market. */
object AppSettings {
    /** Apply saved options before Activity and service resources are inflated. */
    @JvmStatic
    fun wrap(base: Context): Context {
        val preferences = base.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val configuration = Configuration(base.resources.configuration)
        val language = preferences.getString("language", "zh") ?: "zh"
        if (language != "system") configuration.setLocales(LocaleList(Locale.forLanguageTag(language)))
        val theme = preferences.getString("theme", "system") ?: "system"
        if (theme != "system") {
            configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (theme == "dark") Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return base.createConfigurationContext(configuration)
    }

    /** Resolve a string using the latest locale, including in a running download service. */
    @JvmStatic
    fun text(context: Context, resource: Int, vararg arguments: Any): String =
        wrap(context.applicationContext).getString(resource, *arguments)

    /** Keep a translated status only while it matches the current interface locale. */
    @JvmStatic
    fun lastStatus(context: Context): String {
        val preferences = context.getSharedPreferences("bing", Context.MODE_PRIVATE)
        val tag = context.resources.configuration.locales[0].toLanguageTag()
        val fallback = context.getString(R.string.save_hint)
        return if (tag == preferences.getString("status-language", "")) {
            preferences.getString("status", fallback) ?: fallback
        } else fallback
    }
}
