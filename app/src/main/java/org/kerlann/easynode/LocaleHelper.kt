package org.kerlann.easynode

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/** Langue choisie dans l'app : "system" (langue du téléphone), "fr" ou "en". */
object LocaleHelper {
    private const val PREFS = "easynode"
    private const val KEY = "language"
    const val SYSTEM = "system"

    fun get(ctx: Context): String =
        SYSTEM

    fun set(ctx: Context, lang: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, lang).commit()
    }

    /** À appeler dans attachBaseContext() de l'activité et du service. */
    fun wrap(base: Context): Context {
        val lang = get(base)
        if (lang == SYSTEM) return base
        val locale = Locale(lang)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}
