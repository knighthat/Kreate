package me.knighthat.kreate.util

import android.content.Context
import org.koin.java.KoinJavaComponent.inject
import java.io.File


actual fun getConfigDir(): File {
    val context: Context by inject( Context::class.java )
    return context.filesDir
}

actual fun getDataDir(): File = getConfigDir()

actual fun getCacheDir(): File {
    val context: Context by inject( Context::class.java )
    return context.cacheDir
}