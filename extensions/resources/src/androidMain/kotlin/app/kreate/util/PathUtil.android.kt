package app.kreate.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import okio.Path
import okio.Path.Companion.toOkioPath
import org.koin.java.KoinJavaComponent.inject


val Uri.isDocumentTree: Boolean
    get() = if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ) {
        // API 24 and above: Safe to use the built-in Android framework method
        DocumentsContract.isTreeUri( this )
    } else {
        // API 23 and below fallback: Manually inspect the URI structure safely
        pathSegments.size >= 2 && "tree" == pathSegments[0]
    }

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