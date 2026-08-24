package com.ehab.sprintsync.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * In-app language selection, so the app is not limited to whatever the device is set to.
 *
 * No SharedPreferences here on purpose: [AppCompatDelegate.setApplicationLocales] persists
 * the choice itself - through the framework on API 33+, and through AppCompat's own store
 * below that, which is why the manifest declares AppLocalesMetadataHolderService with
 * autoStoreLocales. Adding a second copy of the state would only let the two disagree.
 */
object LocaleManager {

    /**
     * "iw" rather than "he" is deliberate and must stay: it is the qualifier Android uses
     * for the Hebrew resource folder (values-iw), and the tag here has to match it.
     */
    enum class AppLocale(val languageTag: String?) {
        SYSTEM(null),
        ENGLISH("en"),
        HEBREW("iw")
    }

    fun apply(locale: AppLocale) {
        AppCompatDelegate.setApplicationLocales(
            locale.languageTag
                ?.let(LocaleListCompat::forLanguageTags)
                ?: LocaleListCompat.getEmptyLocaleList()
        )
    }

    /** An empty application locale list means "no override", i.e. follow the system. */
    fun current(): AppLocale {
        val tag = AppCompatDelegate.getApplicationLocales()
            .takeUnless(LocaleListCompat::isEmpty)
            ?.get(0)
            ?.language
            ?: return AppLocale.SYSTEM
        return AppLocale.entries.firstOrNull { it.languageTag == tag } ?: AppLocale.SYSTEM
    }
}
