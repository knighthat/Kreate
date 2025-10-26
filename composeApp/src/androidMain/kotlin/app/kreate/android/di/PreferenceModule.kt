package app.kreate.android.di

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.util.fastForEach
import androidx.core.content.edit
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object PreferenceModule {

    private const val PREFERENCES_FILENAME = "preferences"
    private const val PRIVATE_PREFERENCES_FILENAME = "private_preferences"

    @Named("plain")
    @Provides
    @Singleton
    fun providePlainPreferences( @ApplicationContext context: Context ): SharedPreferences {
        val result = context.getSharedPreferences( PREFERENCES_FILENAME, Context.MODE_PRIVATE )
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
        return result
    }

    @Named("private")
    @Provides
    @Singleton
    fun providesPrivatePreferences( @ApplicationContext context: Context ): SharedPreferences =
        context.getSharedPreferences( PRIVATE_PREFERENCES_FILENAME, Context.MODE_PRIVATE )
}