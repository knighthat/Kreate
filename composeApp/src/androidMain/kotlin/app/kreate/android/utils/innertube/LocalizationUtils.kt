package app.kreate.android.utils.innertube

import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
import app.kreate.android.Preferences
import app.kreate.constant.Language
import it.fast4x.rimusic.appContext
import me.knighthat.innertube.request.Localization
import java.util.Locale


val CURRENT_LOCALE: Localization
    get() = Localization(HOST_LANGUAGE, GEO_LOCATION)

// hl
val HOST_LANGUAGE: String
    get() = when ( Preferences.APP_LANGUAGE.value ) {
        Language.SYSTEM ->
            try {
                enumValueOf<Language>(Locale.getDefault().language).code
            } catch (_: IllegalArgumentException) {
                "en"
            }

        else -> Preferences.APP_LANGUAGE.value.code
    }

// gl
val GEO_LOCATION: String
    get() = Preferences.APP_REGION.value.let {
        if( it.isBlank() || it !in Locale.getISOCountries() )
            getSystemCountryCode()
        else
            it
    }

fun getSystemCountryCode(): String {
    var countryCode = Locale.getDefault().country
    if( countryCode !in Locale.getISOCountries() )
        countryCode = appContext().getSystemService<TelephonyManager>()
                                  ?.networkCountryIso
                                  ?.uppercase()
                                  .orEmpty()

    return countryCode.ifBlank { "US" }
}