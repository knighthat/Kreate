package app.kreate.util

import android.content.Context
import android.content.res.Resources
import android.telephony.TelephonyManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.getSystemService
import androidx.core.os.ConfigurationCompat
import co.touchlab.kermit.Logger
import org.koin.java.KoinJavaComponent.get
import java.util.Locale


actual fun getSystemCountryCode(): String =
    get<Context>( Context::class.java )
        .getSystemService<TelephonyManager>()
        ?.networkCountryIso
        ?.uppercase()
        // Fallback to JVM's country code
        ?: Locale.getDefault().country

actual fun getSystemLanguageCode(): String =
    try {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val currentLocale = if (!appLocales.isEmpty) {
            appLocales[0]!!
        } else {
            val configuration = Resources.getSystem().configuration
            ConfigurationCompat.getLocales( configuration )[0]!!
        }

        currentLocale.language
    } catch( err: Exception ) {
        Logger.e("Failed to get language", err, "Locale")

        Locale.getDefault().language
    }