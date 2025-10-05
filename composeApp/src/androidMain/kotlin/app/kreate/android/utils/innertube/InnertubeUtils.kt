package app.kreate.android.utils.innertube

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import app.kreate.android.Preferences

object InnertubeUtils {

    val isLoggedIn: Boolean by derivedStateOf {
        Preferences.YOUTUBE_LOGIN.value && Preferences.YOUTUBE_SYNC_ID.value.isNotBlank()
    }
}