package me.knighthat.kreate.preference

import androidx.annotation.StringDef


@Retention(AnnotationRetention.SOURCE)
@StringDef(
    Preferences.Key.RUNTIME_LOG_NUM_OF_FILES,
    Preferences.Key.RUNTIME_LOG_FILE_SIZE,
    Preferences.Key.RUNTIME_LOG_SEVERITY,
)
annotation class PrefKey()
