package app.kreate.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat


object NotificationUtil {

    const val SLEEP_TIMER_NOTIFICATION_ID = 1010
    const val SLEEP_TIMER_CHANNEL_ID = "sleep_timer"

    const val DOWNLOAD_FOREGROUND_NOTIFICATION_ID = 0xd100      // 53504

    fun canPostNotification( context: Context ): Boolean =
        IS_ANDROID_13_OR_LATER && ContextCompat.checkSelfPermission( context, Manifest.permission.POST_NOTIFICATIONS ) == PackageManager.PERMISSION_GRANTED
}