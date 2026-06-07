package app.kreate.android.utils.innertube

import android.content.Context
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
import org.koin.java.KoinJavaComponent.inject
import java.util.Locale


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