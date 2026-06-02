package app.kreate.util

import android.content.Context
import okio.Path
import okio.Path.Companion.toOkioPath
import org.koin.java.KoinJavaComponent.inject


actual fun getConfigDir(): Path {
    val context: Context by inject( Context::class.java )
    return context.filesDir.toOkioPath()
}

actual fun getDataDir(): Path = getConfigDir()

actual fun getCacheDir(): Path {
    val context: Context by inject( Context::class.java )
    return context.cacheDir.toOkioPath()
}

actual fun getExternalCacheDir(): Path {
    val context: Context by inject( Context::class.java )
    return (context.externalCacheDir ?: context.cacheDir).toOkioPath()
}