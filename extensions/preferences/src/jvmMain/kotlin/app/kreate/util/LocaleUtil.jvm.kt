package app.kreate.util

import java.util.Locale


actual fun getSystemCountryCode(): String = Locale.getDefault().country

actual fun getSystemLanguageCode(): String = Locale.getDefault().language