package app.kreate.util

import java.util.Locale


actual fun getSystemCountryCode(): String = Locale.getDefault().country