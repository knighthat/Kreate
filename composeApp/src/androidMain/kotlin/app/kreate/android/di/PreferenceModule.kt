package app.kreate.android.di

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.util.fastForEach
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.fast4x.rimusic.utils.isAtLeastAndroid7
import timber.log.Timber
import java.io.File
import javax.inject.Named
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object PreferenceModule {

    private const val PREFERENCES_FILENAME = "preferences"
    private const val ENCRYPTED_PREFERENCES_FILENAME = "secure_preferences"
    private const val PRIVATE_PREFERENCES_FILENAME = "private_preferences"

    /**
     * Creating an instance of [SharedPreferences] that all of its values
     * are encrypted using [EncryptedSharedPreferences] library.
     *
     * Only use for Android 6-, any other attempt to use it at higher API
     * will result in error being thrown.
     *
     * @throws IllegalStateException when sdk is >= 24
     */
    @Suppress("DEPRECATION")
    @Throws(IllegalStateException::class)
    private fun legacySharedPreferences( context: Context ): SharedPreferences {
        if( isAtLeastAndroid7 )
            throw IllegalStateException("Only use this with sdk < 24")

        lateinit var result: SharedPreferences
        try {
            val masterKey: MasterKey = MasterKey.Builder( context, MasterKey.DEFAULT_MASTER_KEY_ALIAS )
                                                .setKeyScheme( MasterKey.KeyScheme.AES256_GCM )
                                                .build()
            result = EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFERENCES_FILENAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            result.edit {
                // Using reflection to get unused keys would be a better
                // idea, but it'd force all keys to be initialized, which
                // is undesirable.
                listOf(
                    "pipedUsername", "pipedPassword", "pipedInstanceName", "pipedApiBaseUrl",
                    "pipedApiToken",
                ).fastForEach( this::remove )
            }
        } catch ( e: Exception ) {
            e.printStackTrace()

            runCatching {
                if( isAtLeastAndroid7 )
                    context.deleteSharedPreferences( ENCRYPTED_PREFERENCES_FILENAME )
                else
                    File(
                        context.applicationInfo.dataDir,
                        "shared_prefs/$ENCRYPTED_PREFERENCES_FILENAME.xml"
                    ).delete()
            }.onFailure {
                Timber.tag( "Preferences" ).e( it, "Error while deleting encrypted preferences" )
            }

            legacySharedPreferences( context )
        }

        return result
    }

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
        if ( isAtLeastAndroid7 )
            context.getSharedPreferences( PRIVATE_PREFERENCES_FILENAME, Context.MODE_PRIVATE )
        else
            legacySharedPreferences( context )
}