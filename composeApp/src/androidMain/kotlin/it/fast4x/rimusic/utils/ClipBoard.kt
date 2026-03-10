package it.fast4x.rimusic.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.kreate.android.R
import co.touchlab.kermit.Logger
import me.knighthat.utils.Toaster


fun textCopyToClipboard(textCopied:String, context: Context) {
    val clipboardManager = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    runCatching {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("", textCopied))
    }.onFailure {
        Logger.e( "", it, "CopyToClipboard" )
        Toaster.e( "Failed to copy text to clipboard, try again" )
    }
    // Only show a toast for Android 12 and lower.
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
        Toaster.s( R.string.value_copied )
}

@Composable
fun textCopyFromClipboard(context: Context): String {
    var textCopied by remember { mutableStateOf("") }
    val clipboardManager = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    runCatching {
        textCopied = clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(context).toString()
    }.onFailure {
        Logger.e( "", it, "CopyToClipboard" )
        Toaster.e( "Failed to copy text to clipboard, try again" )
    }
    // Only show a toast for Android 12 and lower.
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
        Toaster.i( R.string.value_copied )

    return textCopied
}