package app.kreate.di

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.util.fastForEach
import androidx.core.content.edit
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.QualifierValue
import org.koin.dsl.module


private const val PROFILE_PREFERENCES_FILENAME = "profiles"
private const val ACTIVE_PROFILE_KEY = "ActiveProfile"
private const val PREFERENCES_BASE_FILENAME = "preferences"
private const val PRIVATE_PREFERENCES_BASE_FILENAME = "private_preferences"

val preferencesModule = module {
    single( PrefType.PROFILES, true ) {
        val context: Context = get()
        context.getSharedPreferences( PROFILE_PREFERENCES_FILENAME, Context.MODE_PRIVATE )
    }

    single( PrefType.DEFAULT, true ) {
        val context: Context = get()
        val profile: SharedPreferences = get(PrefType.PROFILES )
        val profileName = profile.getString( ACTIVE_PROFILE_KEY, "default" )!!
        val filename = if (profileName == "default") {
            PREFERENCES_BASE_FILENAME
        } else {
            PREFERENCES_BASE_FILENAME + "_$profileName"
        }

        val result = context.getSharedPreferences( filename, Context.MODE_PRIVATE )
        result.edit {
            // Using reflection to get unused keys would be a better
            // idea, but it'd force all keys to be initialized, which
            // is undesirable.
            listOf(
                "EnablePiped", "isPipedEnabled", "IsPipedCustom", "isPipedCustomEnabled",
                "YouTubeVisitorData", "ytVisitorData", "YouTubeSyncId", "ytDataSyncIdKey",
                "YouTubeCookies", "ytCookie", "YouTubeAccountName", "ytAccountNameKey",
                "YouTubeAccountEmail", "ytAccountEmailKey", "YouTubeSelfChannelHandle",
                "ytAccountChannelHandleKey", "YouTubeAccountAvatar", "ytAccountThumbnailKey",
                "JumpPrevious", "jumpPrevious", "ScrollingText", "disableScrollingText",
                "ThumbnailCacheSize", "coilDiskCacheMaxSize", "ThumbnailCacheCustomSize",
                "exoPlayerCustomCache", "SongCacheCustomSize", "SongCacheSize",
                "exoPlayerDiskCacheMaxSize"
            ).fastForEach( this::remove )
        }

        return@single result
    }
    single( PrefType.CREDENTIALS, true ) {
        val context: Context = get()
        val profile: SharedPreferences = get( PrefType.PROFILES )
        val profileName = profile.getString( ACTIVE_PROFILE_KEY, "default" )
        val filename = if (profileName == "default") {
            PRIVATE_PREFERENCES_BASE_FILENAME
        } else {
            PRIVATE_PREFERENCES_BASE_FILENAME + "_$profileName"
        }

        return@single context.getSharedPreferences( filename, Context.MODE_PRIVATE )
    }
}

enum class PrefType : Qualifier {
    DEFAULT, CREDENTIALS, PROFILES;

    override val value: QualifierValue = toString().lowercase()
}