package me.knighthat.kreate.util

import android.content.Context
import android.os.Build
import androidx.core.net.toUri
import me.knighthat.kreate.BuildConfig
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.io.PrintWriter


actual class CrashHandler : AbstractCrashHandler() {

    override fun writeReport( file: File, e: Throwable ) {
        val context: Context by inject( Context::class.java )
        context.contentResolver.openOutputStream( file.toUri() )?.use {  outStream ->
            val writer = PrintWriter(outStream)

            writer.println( "Version: ${BuildConfig.VERSION_NAME}" )
            writer.println( "Manufacturer: ${Build.MANUFACTURER}" )
            writer.println( "Model: ${Build.MODEL}" )
            writer.println( "Brand: ${Build.BRAND}" )
            writer.println( "Device: ${Build.DEVICE}" )
            writer.println( "Product: ${Build.PRODUCT}" )
            writer.println( "Hardware: ${Build.HARDWARE}" )
            writer.println( "SDK: ${Build.VERSION.SDK_INT}" )
            writer.println( "Release: ${Build.VERSION.RELEASE}" )
            writer.println( "Build Incremental: ${Build.VERSION.INCREMENTAL}" )
            writer.println()
            // Similar to [Throwable.stackTraceToString], but writing to [outStream] instead
            e.printStackTrace( writer )

            writer.flush()
        }
    }
}