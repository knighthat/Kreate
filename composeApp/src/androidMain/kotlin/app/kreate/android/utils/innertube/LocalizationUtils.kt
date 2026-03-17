package app.kreate.android.utils.innertube

import android.content.Context
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
import app.kreate.android.Preferences
import app.kreate.constant.Language
import me.knighthat.innertube.request.Localization
import org.koin.java.KoinJavaComponent.inject
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
    val context: Context by inject(Context::class.java)
    var countryCode = Locale.getDefault().country
    if( countryCode !in Locale.getISOCountries() )
        countryCode = context.getSystemService<TelephonyManager>()
                             ?.networkCountryIso
                             ?.uppercase()
                             .orEmpty()

    return countryCode.ifBlank { "US" }
}