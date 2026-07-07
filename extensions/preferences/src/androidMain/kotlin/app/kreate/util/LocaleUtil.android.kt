package app.kreate.util

import android.content.Context
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
import org.koin.java.KoinJavaComponent.get
import java.util.Locale


actual fun getSystemCountryCode(): String =
    get<Context>( Context::class.java )
        .getSystemService<TelephonyManager>()
        ?.networkCountryIso
        ?.uppercase()
        // Fallback to JVM's country code
        ?: Locale.getDefault().country