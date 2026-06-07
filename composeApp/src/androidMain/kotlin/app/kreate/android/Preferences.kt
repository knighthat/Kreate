package app.kreate.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.util.Log
import androidx.annotation.ColorRes
import androidx.annotation.MainThread
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import app.kreate.android.utils.innertube.getSystemCountryCode
import app.kreate.di.PrefType
import co.touchlab.kermit.Logger
import me.knighthat.innertube.Constants
import me.knighthat.utils.Toaster
import org.jetbrains.annotations.Blocking
import org.jetbrains.annotations.NonBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.java.KoinJavaComponent.inject

/**
 * Represents an individual setting.
 *
 * @param sharedPreferences a class that holds all entries of preferences file
 * @param key of the entry, used to extract/write data to preferences file
 * @param previousKey for backward compatibility, when key changed,
 * this will be used to extract old value to be used with new key
 * @param defaultValue if key doesn't exist in preferences, this value will be written
 * to it, and used as current value
 */
sealed class Preferences<T>(
    protected val sharedPreferences: SharedPreferences,
    val key: kotlin.String,
    val previousKey: kotlin.String,
    val defaultValue: T
): MutableState<T> {

    /**
     * These settings are set up in a way that calling an instance
     * multiple times will not result in multiple creation (initialization)
     * of the same value.
     *
     * However, the state of said setting is observable. Meaning,
     * in certain contexts, when this value is changed (either by the same
     * component or by a completely different component), previously called
     * will be noticed about the change, and will be updated accordingly.
     *
     * Furthermore, a setting entry is lazily initialized, until the setting
     * is first called, it'll remain uninitialized, no computation power, nor
     * memory will be consumed.
     */
    companion object : KoinComponent {

        private const val LOGGING_TAG = "Preferences"

        val profilePreferences: SharedPreferences by inject<SharedPreferences>(PrefType.PROFILES)
        val preferences: SharedPreferences by inject<SharedPreferences>(PrefType.DEFAULT)
        val encryptedPreferences: SharedPreferences by inject<SharedPreferences>(PrefType.CREDENTIALS)

        //<editor-fold defaultstate="collapsed" desc="Item size">
        val SONG_THUMBNAIL_SIZE by lazy {
            Int(preferences, Key.SONG_THUMBNAIL_SIZE, "", 54)
        }
        val ALBUM_THUMBNAIL_SIZE by lazy {
            Int(preferences, Key.ALBUM_THUMBNAIL_SIZE, "", 128)
        }
        val ARTIST_THUMBNAIL_SIZE by lazy {
            Int(preferences, Key.ARTIST_THUMBNAIL_SIZE, "", 128)
        }
        val PLAYLIST_THUMBNAIL_SIZE by lazy {
            Int(preferences, Key.PLAYLIST_THUMBNAIL_SIZE, "", 128)
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Player">
        val PLAYER_THUMBNAIL_VINYL_SIZE by lazy {
            Float( preferences, Key.PLAYER_THUMBNAIL_VINYL_SIZE, "VinylSize", 50F )
        }
        val PLAYER_THUMBNAIL_FADE by lazy {
            Float( preferences, Key.PLAYER_THUMBNAIL_FADE, "thumbnailFade", 5F )
        }
        val PLAYER_THUMBNAIL_FADE_EX by lazy {
            Float( preferences, Key.PLAYER_THUMBNAIL_FADE_EX, "thumbnailFadeEx", 5F )
        }
        val PLAYER_THUMBNAIL_SPACING by lazy {
            Float( preferences, Key.PLAYER_THUMBNAIL_SPACING, "thumbnailSpacing", 0F )
        }
        val PLAYER_THUMBNAIL_SPACING_LANDSCAPE by lazy {
            Float( preferences, Key.PLAYER_THUMBNAIL_SPACING_LANDSCAPE, "thumbnailSpacingL", 0F )
        }
        val PLAYER_CURRENT_VISUALIZER  by lazy {
            Int( preferences, Key.PLAYER_CURRENT_VISUALIZER , "currentVisualizerKey", 0 )
        }
        val PLAYER_BACKGROUND_BLUR_STRENGTH by lazy {
            Float( preferences, Key.PLAYER_BACKGROUND_BLUR_STRENGTH, "blurScale", 25F )
        }
        val PLAYER_BACKGROUND_BACK_DROP by lazy {
            Float( preferences, Key.PLAYER_BACKGROUND_BACK_DROP, "playerBackdrop", 0F )
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Cache">
        val IMAGE_CACHE_SIZE by lazy {
            Long(preferences, Key.IMAGE_CACHE_SIZE, "", kotlin.Long.MAX_VALUE)
        }
        val EXO_CACHE_SIZE by lazy {
            Long(preferences, Key.EXO_CACHE_SIZE, "", kotlin.Long.MAX_VALUE)
        }
        val EXO_DOWNLOAD_SIZE by lazy {
            Long(preferences, Key.EXO_DOWNLOAD_SIZE, "", kotlin.Long.MAX_VALUE)
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Lyrics">
        val LYRICS_SIZE by lazy {
            Float( preferences, Key.LYRICS_SIZE, "lyricsSize", 5F )
        }
        val LYRICS_SIZE_LANDSCAPE by lazy {
            Float( preferences, Key.LYRICS_SIZE_LANDSCAPE, "lyricsSizeL", 5F )
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Audio">
        val AUDIO_REVERB_PRESET by lazy {
            Int(preferences, Key.AUDIO_REVERB_PRESET, "audioReverbPreset", 0)
        }
        val AUDIO_VOLUME_NORMALIZATION_TARGET by lazy {
            Float( preferences, Key.AUDIO_VOLUME_NORMALIZATION_TARGET, "loudnessBaseGain", 5F )
        }
        val AUDIO_BASS_BOOST_LEVEL by lazy {
            Float( preferences, Key.AUDIO_BASS_BOOST_LEVEL, "bassboostLevel", .5F )
        }
        val AUDIO_SPEED_VALUE by lazy {
            Float( preferences, Key.AUDIO_SPEED_VALUE, "playbackSpeed", 1F )
        }
        val AUDIO_PITCH by lazy {
            Float( preferences, Key.AUDIO_PITCH, "playbackPitch", 1F )
        }
        val AUDIO_VOLUME by lazy {
            Float( preferences, Key.AUDIO_VOLUME, "playbackVolume", .5F )
        }
        val AUDIO_DEVICE_VOLUME by lazy {
            Float( preferences, Key.AUDIO_DEVICE_VOLUME, "playbackDeviceVolume", .5f )
        }
        val AUDIO_MEDLEY_DURATION by lazy {
            Float( preferences, Key.AUDIO_MEDLEY_DURATION, "playbackDuration", 0F )
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="YouTube">
        val YOUTUBE_VISITOR_DATA by lazy {
            String( encryptedPreferences, Key.YOUTUBE_VISITOR_DATA, "ytVisitorData", Constants.CHROME_WINDOWS_VISITOR_DATA )
        }
        val YOUTUBE_SYNC_ID by lazy {
            String( encryptedPreferences, Key.YOUTUBE_SYNC_ID, "ytDataSyncIdKey", "" )
        }
        val YOUTUBE_COOKIES by lazy {
            String( encryptedPreferences, Key.YOUTUBE_COOKIES, "ytCookie", "" )
        }
        val YOUTUBE_ACCOUNT_NAME by lazy {
            String( encryptedPreferences, Key.YOUTUBE_ACCOUNT_NAME, "ytAccountNameKey", "" )
        }
        val YOUTUBE_ACCOUNT_EMAIL by lazy {
            String( encryptedPreferences, Key.YOUTUBE_ACCOUNT_EMAIL, "ytAccountEmailKey", "" )
        }
        val YOUTUBE_SELF_CHANNEL_HANDLE by lazy {
            String( encryptedPreferences, Key.YOUTUBE_SELF_CHANNEL_HANDLE, "ytAccountChannelHandleKey", "" )
        }
        val YOUTUBE_ACCOUNT_AVATAR by lazy {
            String( encryptedPreferences, Key.YOUTUBE_ACCOUNT_AVATAR, "ytAccountThumbnailKey", "" )
        }
        val YOUTUBE_LAST_VIDEO_ID by lazy {
            String( preferences, Key.YOUTUBE_LAST_VIDEO_ID, "lastVideoId", "" )
        }
        val YOUTUBE_LAST_VIDEO_SECONDS by lazy {
            Float( preferences, Key.YOUTUBE_LAST_VIDEO_SECONDS, "lastVideoSeconds", 0F )
        }
        //</editor-fold>
        //<editor-fold desc="Discord">
        val DISCORD_ACCESS_TOKEN by lazy {
            String( encryptedPreferences, Key.DISCORD_ACCESS_TOKEN, "", "" )
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Proxy">
        val PROXY_HOST by lazy {
            String( preferences, Key.PROXY_HOST, "proxyHostnameKey", "" )
        }
        val PROXY_PORT  by lazy {
            Int( preferences, Key.PROXY_PORT , "proxyPort", 1080 )
        }
        //</editor-fold>
        //<editor-fold desc="Logging">
        val RUNTIME_LOG_LEVEL by lazy {
            Int(preferences, Key.RUNTIME_LOG_LEVEL, "", Log.INFO)
        }
        val RUNTIME_LOG_FILE_COUNT by lazy {
            Int(preferences, Key.RUNTIME_LOG_FILE_COUNT, "", 5)
        }
        val RUNTIME_LOG_MAX_SIZE_PER_FILE by lazy {
            Long(preferences, "DebugLogMaxSizePerFile", "", 5L * 1024 * 1024)   // 5 Mb
        }
        //</editor-fold>
        //<editor-fold desc="Thumbnail roundness">
        val SONG_THUMBNAIL_ROUNDNESS_PERCENT by lazy {
            Int(preferences, Key.SONG_THUMBNAIL_ROUNDNESS_PERCENT, "", 0)
        }
        val ALBUM_THUMBNAIL_ROUNDNESS_PERCENT by lazy {
            Int(preferences, Key.ALBUM_THUMBNAIL_ROUNDNESS_PERCENT, "", 10)
        }
        val ARTIST_THUMBNAIL_ROUNDNESS_PERCENT by lazy {
            Int(preferences, Key.ARTIST_THUMBNAIL_ROUNDNESS_PERCENT, "", 50)
        }
        val PLAYLIST_THUMBNAIL_ROUNDNESS_PERCENT by lazy {
            Int(preferences, Key.PLAYLIST_THUMBNAIL_ROUNDNESS_PERCENT, "", 10)
        }
        //</editor-fold>

        val LIVE_WALLPAPER_RESET_DURATION by lazy {
            Long(preferences, Key.LIVE_WALLPAPER_RESET_DURATION, "", -1L)
        }
        val APP_REGION by lazy {
            String( preferences, Key.APP_REGION, "", getSystemCountryCode() )
        }
        val FLOATING_ICON_X_OFFSET by lazy {
            Float( preferences, Key.FLOATING_ICON_X_OFFSET, "floatActionIconOffsetX", 0F )
        }
        val FLOATING_ICON_Y_OFFSET by lazy {
            Float( preferences, Key.FLOATING_ICON_Y_OFFSET, "floatActionIconOffsetY", 0F )
        }
        val MULTI_FLOATING_ICON_X_OFFSET by lazy {
            Float( preferences, Key.MULTI_FLOATING_ICON_X_OFFSET, "multiFloatActionIconOffsetX", 0F )
        }
        val MULTI_FLOATING_ICON_Y_OFFSET by lazy {
            Float( preferences, Key.MULTI_FLOATING_ICON_Y_OFFSET, "multiFloatActionIconOffsetY", 0F )
        }
        val SMART_REWIND by lazy {
            Float(preferences, Key.SMART_REWIND, "", 3f)
        }
        val LOCAL_SONGS_FOLDER by lazy {
            String( preferences, Key.LOCAL_SONGS_FOLDER, "defaultFolder", "/" )
        }
        val SEEN_CHANGELOGS_VERSION by lazy {
            String( preferences, Key.SEEN_CHANGELOGS_VERSION, "seenChangelogsVersionKey", "" )
        }
        val SEARCH_RESULTS_TAB_INDEX by lazy {
            Int( preferences, Key.SEARCH_RESULTS_TAB_INDEX, "searchResultScreenTabIndex", 0 )
        }
        val HOME_TAB_INDEX by lazy {
            Int( preferences, Key.HOME_TAB_INDEX, "homeScreenTabIndex", 0 )
        }
        val ARTIST_SCREEN_TAB_INDEX  by lazy {
            Int( preferences, Key.ARTIST_SCREEN_TAB_INDEX , "artistScreenTabIndex", 0 )
        }
        val ACTIVE_PROFILE by lazy {
            String(profilePreferences, Key.ACTIVE_PROFILE, "", "default")
        }

        fun isLoggedInToDiscord(): kotlin.Boolean =
            app.kreate.preferences.Preferences.DISCORD_LOGIN.value && DISCORD_ACCESS_TOKEN.value.isNotBlank()

        /**
         * Finalize all changes and write it to disk.
         *
         * This is a blocking call.
         *
         * **NOTE**: Should only be called when the app
         * is about to close to make sure all settings are saved
         */
        @SuppressLint("UseKtx", "ApplySharedPref")      // Use conventional syntax because it's easier to read
        @Blocking
        fun unload() {
            this.preferences.edit().commit()
            this.profilePreferences.edit().commit()
        }
    }

    protected abstract val delegate: MutableState<T>

    /**
     * How old and new value are processed
     */
    protected abstract val policy: SnapshotMutationPolicy<T>

    override var value: T
        @MainThread
        get() = try {
            // Try standard reading first
            delegate.value
        } catch( e: IllegalStateException ) {
            Logger.w( e, LOGGING_TAG ) { "Key: $key" }

            // Fallback: Take a fresh snapshot of the current global state
            // and read from that instead.
            Snapshot.takeSnapshot().enter( delegate::value )
        }
        @MainThread
        set(value) {
            if( Looper.myLooper() != Looper.getMainLooper() ) {
                val threadName = Looper.myLooper()?.thread?.name ?: "unknown"
                Logger.e( Throwable(), LOGGING_TAG ) {
                    "$key is being written on thread \"$threadName\""
                }

                Toaster.e( R.string.error_required_report )

                return
            }

            delegate.value = value
        }

    /**
     * Extract value from [SharedPreferences]. Return value
     * must be `null` if [key] doesn't exist inside preferences file.
     *
     * @return value of this preference, `null` if [key] doesn't exist
     */
    protected abstract fun getFromSharedPreferences(): T?

    /**
     * Write [value] into [SharedPreferences] instance.
     *
     * This is a non-blocking calls. Meaning, all writes
     * are temporary written to memory first, then sync
     * value to disk asynchronously.
     */
    @NonBlocking
    protected abstract fun write( value: T )

    /**
     * Write [defaultValue] to this setting
     */
    fun reset() { value = defaultValue }

    /**
     * Retrieves the current value via the getter logic of this property handler.
     *
     * Example:
     * ```
     * val (settingGetter, settingSetter) = ENTRY
     * println( settingGetter() )
     * ```
     */
    override fun component1(): T = value

    /**
     * Provides the setter logic to update the value of this property handler.
     *
     * Example:
     * ```
     * val (settingGetter, settingSetter) = ENTRY
     * set( "new value" )
     * ```
     */
    override fun component2(): (T) -> Unit = { value = it }

    protected inner class StructuralEqualityPolicy: SnapshotMutationPolicy<T> {
        override fun equivalent( a: T, b: T ): kotlin.Boolean {
            if( a != b ) write( b )
            return a == b
        }
    }

    protected inner class ReferentialEqualityPolicy: SnapshotMutationPolicy<T> {
        override fun equivalent( a: T, b: T ): kotlin.Boolean {
            if( a !== b ) write( b )
            return a === b
        }
    }

    protected inner class DecimalEqualityPolicy: SnapshotMutationPolicy<T> {
        override fun equivalent( a: T, b: T ): kotlin.Boolean {
            require( a is Comparable<*> && b is Comparable<*> && a::class == b::class )

            @Suppress("UNCHECKED_CAST")
            val areEqual = (a as Comparable<Any>).compareTo( b ) == 0
            if( !areEqual ) write( b )

            return areEqual
        }
    }

    class Enum<E: kotlin.Enum<E>>(
        sharedPreferences: SharedPreferences,
        key: kotlin.String,
        previousKey: kotlin.String,
        defaultValue: E
    ): Preferences<E>(sharedPreferences, key, previousKey, defaultValue) {

        override val policy = ReferentialEqualityPolicy()

        override var delegate = mutableStateOf(
            value = getFromSharedPreferences() ?: defaultValue.also( ::write ),
            policy = this.policy
        )

        /**
         * Whether one of the provided [E] matches current value
         */
        fun either( vararg others: E ): kotlin.Boolean = value in others

        /**
         * @return `true` if none of the provided values is the current value
         */
        fun neither( vararg others: E ): kotlin.Boolean = value !in others

        override fun getFromSharedPreferences(): E? {
            var fromFile: kotlin.String? = null

            /*
                 Set [fromFile] to the value of [previousKey] if it's
                 existed in the preferences file, then delete that key
                 (for migration to new key)
             */
            if( sharedPreferences.contains( previousKey ) ) {
                fromFile = sharedPreferences.getString( previousKey, null )
                sharedPreferences.edit( commit = true ) {
                    remove( previousKey )

                    // Add this value to new [key], otherwise, only old key
                    // will be removed and new key is not added until next start
                    // with default value
                    fromFile.also { putString( key, it ) }
                }
            }

            /*
                 Set [fromFile] to the value of [key] if it's
                 existed in the preferences file.

                 Reasons for 2 separate steps are:
                 - When both [key] and [previousKey] are existed
                 in side the file, [previousKey] will be deleted
                 while value of [key] is being used.
                 - Or either 1 of the key will be used if only
                 1 of them existed inside the file.
            */
            if( sharedPreferences.contains( key ) )
                fromFile = sharedPreferences.getString( key, null )

            return fromFile?.let { enumStr ->
                defaultValue.javaClass.enumConstants?.firstOrNull { it.name == enumStr }
            }
        }

        override fun write( value: E ) =
            sharedPreferences.edit {
                putString( key, value.name )
            }
    }

    class String(
        sharedPreferences: SharedPreferences,
        key: kotlin.String,
        previousKey: kotlin.String,
        defaultValue: kotlin.String
    ): Preferences<kotlin.String>(sharedPreferences, key, previousKey, defaultValue) {

        override val policy = ReferentialEqualityPolicy()

        override var delegate = mutableStateOf(
            value = getFromSharedPreferences() ?: defaultValue.also( ::write ),
            policy = this.policy
        )

        override fun getFromSharedPreferences(): kotlin.String? {
            var fromFile: kotlin.String? = null

            /*
                 Set [fromFile] to the value of [previousKey] if it's
                 existed in the preferences file, then delete that key
                 (for migration to new key)
             */
            if( sharedPreferences.contains( previousKey ) ) {
                fromFile = sharedPreferences.getString( previousKey, null )
                sharedPreferences.edit( commit = true ) {
                    remove( previousKey )

                    // Add this value to new [key], otherwise, only old key
                    // will be removed and new key is not added until next start
                    // with default value
                    fromFile?.also { putString( key, it ) }
                }
            }

            /*
                 Set [fromFile] to the value of [key] if it's
                 existed in the preferences file.

                 Reason for 2 separate steps is:
                 - When both [key] and [previousKey] are existed
                 in side the file, [previousKey] will be deleted
                 while value of [key] is being used.
                 - Or either 1 of the key will be used if only
                 1 of them existed inside the file.
            */
            if( sharedPreferences.contains( key ) )
                fromFile = sharedPreferences.getString( key, null )

            return fromFile
        }

        override fun write( value: kotlin.String) =
            sharedPreferences.edit {
                putString( key, value )
            }
    }

    class StringSet(
        sharedPreferences: SharedPreferences,
        key: kotlin.String,
        previousKey: kotlin.String,
        defaultValue: Set<kotlin.String>
    ): Preferences<Set<kotlin.String>>(sharedPreferences, key, previousKey, defaultValue) {

        override val policy = StructuralEqualityPolicy()

        override var delegate = mutableStateOf(
            value = getFromSharedPreferences() ?: defaultValue.also( ::write ),
            policy = this.policy
        )

        override fun getFromSharedPreferences(): Set<kotlin.String>? {
            var fromFile: Set<kotlin.String>? = null

            /*
                 Set [fromFile] to the value of [previousKey] if it's
                 existed in the preferences file, then delete that key
                 (for migration to new key)
             */
            if( sharedPreferences.contains( previousKey ) ) {
                fromFile = sharedPreferences.getStringSet( previousKey, null )
                sharedPreferences.edit( commit = true ) {
                    remove( previousKey )

                    // Add this value to new [key], otherwise, only old key
                    // will be removed and new key is not added until next start
                    // with default value
                    fromFile?.also { putStringSet( key, it ) }
                }
            }

            /*
                 Set [fromFile] to the value of [key] if it's
                 existed in the preferences file.

                 Reason for 2 separate steps is:
                 - When both [key] and [previousKey] are existed
                 in side the file, [previousKey] will be deleted
                 while value of [key] is being used.
                 - Or either 1 of the key will be used if only
                 1 of them existed inside the file.
            */
            if( sharedPreferences.contains( key ) )
                fromFile = sharedPreferences.getStringSet( key, null )

            return fromFile
        }

        override fun write( value: Set<kotlin.String> ) =
            sharedPreferences.edit {
                putStringSet( key, value )
            }
    }

    class Int(
        sharedPreferences: SharedPreferences,
        key: kotlin.String,
        previousKey: kotlin.String,
        defaultValue: kotlin.Int
    ): Preferences<kotlin.Int>(sharedPreferences, key, previousKey, defaultValue) {

        override val policy = DecimalEqualityPolicy()

        override var delegate = mutableStateOf(
            value = getFromSharedPreferences() ?: defaultValue.also( ::write ),
            policy = this.policy
        )

        override fun getFromSharedPreferences(): kotlin.Int? {
            var fromFile: kotlin.Int? = null

            /*
                 Set [fromFile] to the value of [previousKey] if it's
                 existed in the preferences file, then delete that key
                 (for migration to new key)
             */
            if( sharedPreferences.contains( previousKey ) ) {
                fromFile = sharedPreferences.getInt( previousKey, defaultValue )
                sharedPreferences.edit( commit = true ) {
                    remove( previousKey )

                    // Add this value to new [key], otherwise, only old key
                    // will be removed and new key is not added until next start
                    // with default value
                    fromFile.also { putInt( key, it ) }
                }
            }

            /*
                 Set [fromFile] to the value of [key] if it's
                 existed in the preferences file.

                 Reasons for 2 separate steps are:
                 - When both [key] and [previousKey] are existed
                 in side the file, [previousKey] will be deleted
                 while value of [key] is being used.
                 - Or either 1 of the key will be used if only
                 1 of them existed inside the file.
            */
            if( sharedPreferences.contains( key ) )
                fromFile = sharedPreferences.getInt( key, defaultValue )

            return fromFile
        }

        override fun write( value: kotlin.Int) =
            sharedPreferences.edit {
                putInt( key, value )
            }
    }

    class Long(
        sharedPreferences: SharedPreferences,
        key: kotlin.String,
        previousKey: kotlin.String,
        defaultValue: kotlin.Long
    ): Preferences<kotlin.Long>(sharedPreferences, key, previousKey, defaultValue) {

        override val policy = DecimalEqualityPolicy()

        override var delegate = mutableStateOf(
            value = getFromSharedPreferences() ?: defaultValue.also( ::write ),
            policy = this.policy
        )

        override fun getFromSharedPreferences(): kotlin.Long? {
            var fromFile: kotlin.Long? = null

            /*
                 Set [fromFile] to the value of [previousKey] if it's
                 existed in the preferences file, then delete that key
                 (for migration to new key)
             */
            if( sharedPreferences.contains( previousKey ) ) {
                fromFile = sharedPreferences.getLong( previousKey, defaultValue )
                sharedPreferences.edit( commit = true ) {
                    remove( previousKey )

                    // Add this value to new [key], otherwise, only old key
                    // will be removed and new key is not added until next start
                    // with default value
                    fromFile.also { putLong( key, it ) }
                }
            }

            /*
                 Set [fromFile] to the value of [key] if it's
                 existed in the preferences file.

                 Reasons for 2 separate steps are:
                 - When both [key] and [previousKey] are existed
                 in side the file, [previousKey] will be deleted
                 while value of [key] is being used.
                 - Or either 1 of the key will be used if only
                 1 of them existed inside the file.
            */
            if( sharedPreferences.contains( key ) )
                fromFile = sharedPreferences.getLong( key, defaultValue )

            return fromFile
        }

        override fun write( value: kotlin.Long) =
            sharedPreferences.edit {
                putLong( key, value )
            }
    }

    class Float(
        sharedPreferences: SharedPreferences,
        key: kotlin.String,
        previousKey: kotlin.String,
        defaultValue: kotlin.Float
    ): Preferences<kotlin.Float>(sharedPreferences, key, previousKey, defaultValue) {

        override val policy = DecimalEqualityPolicy()

        override var delegate = mutableStateOf(
            value = getFromSharedPreferences() ?: defaultValue.also( ::write ),
            policy = this.policy
        )

        override fun getFromSharedPreferences(): kotlin.Float? {
            var fromFile: kotlin.Float? = null

            /*
                 Set [fromFile] to the value of [previousKey] if it's
                 existed in the preferences file, then delete that key
                 (for migration to new key)
             */
            if( sharedPreferences.contains( previousKey ) ) {
                fromFile = sharedPreferences.getFloat( previousKey, defaultValue )
                sharedPreferences.edit( commit = true ) {
                    remove( previousKey )

                    // Add this value to new [key], otherwise, only old key
                    // will be removed and new key is not added until next start
                    // with default value
                    fromFile.also { putFloat( key, it ) }
                }
            }

            /*
                 Set [fromFile] to the value of [key] if it's
                 existed in the preferences file.

                 Reasons for 2 separate steps are:
                 - When both [key] and [previousKey] are existed
                 in side the file, [previousKey] will be deleted
                 while value of [key] is being used.
                 - Or either 1 of the key will be used if only
                 1 of them existed inside the file.
            */
            if( sharedPreferences.contains( key ) )
                fromFile = sharedPreferences.getFloat( key, defaultValue )

            return fromFile
        }

        override fun write( value: kotlin.Float) =
            sharedPreferences.edit {
                putFloat( key, value )
            }
    }

    class Boolean(
        sharedPreferences: SharedPreferences,
        key: kotlin.String,
        previousKey: kotlin.String,
        defaultValue: kotlin.Boolean
    ): Preferences<kotlin.Boolean>(sharedPreferences, key, previousKey, defaultValue) {

        override val policy = ReferentialEqualityPolicy()

        override var delegate = mutableStateOf(
            value = getFromSharedPreferences() ?: defaultValue.also( ::write ),
            policy = this.policy
        )

        /**
         * Set current value to opposite value and return new value.
         */
        fun flip(): kotlin.Boolean {
            value = !value
            return value
        }

        override fun getFromSharedPreferences(): kotlin.Boolean? {
            var fromFile: kotlin.Boolean? = null

            /*
                 Set [fromFile] to the value of [previousKey] if it's
                 existed in the preferences file, then delete that key
                 (for migration to new key)
             */
            if( sharedPreferences.contains( previousKey ) ) {
                fromFile = sharedPreferences.getBoolean( previousKey, defaultValue )
                sharedPreferences.edit( commit = true ) {
                    remove( previousKey )

                    // Add this value to new [key], otherwise, only old key
                    // will be removed and new key is not added until next start
                    // with default value
                    fromFile.also { putBoolean( key, it ) }
                }
            }

            /*
                 Set [fromFile] to the value of [key] if it's
                 existed in the preferences file.

                 Reasons for 2 separate steps are:
                 - When both [key] and [previousKey] are existed
                 in side the file, [previousKey] will be deleted
                 while value of [key] is being used.
                 - Or either 1 of the key will be used if only
                 1 of them existed inside the file.
            */
            if( sharedPreferences.contains( key ) )
                fromFile = sharedPreferences.getBoolean( key, defaultValue )

            return fromFile
        }

        override fun write( value: kotlin.Boolean) =
            sharedPreferences.edit {
                putBoolean( key, value )
            }
    }

    class Color(
        sharedPreferences: SharedPreferences,
        key: kotlin.String,
        previousKey: kotlin.String,
        defaultValue: androidx.compose.ui.graphics.Color
    ): Preferences<androidx.compose.ui.graphics.Color>(sharedPreferences, key, previousKey, defaultValue) {

        constructor(
            sharedPreferences: SharedPreferences,
            key: kotlin.String,
            previousKey: kotlin.String,
            @ColorRes defaultValue: kotlin.Int
        ): this(
            sharedPreferences,
            key,
            previousKey,
            Color(
                ContextCompat.getColor( inject<Context>(Context::class.java).value, defaultValue )
            )
        )

        override val policy = StructuralEqualityPolicy()

        override var delegate = mutableStateOf(
            value = getFromSharedPreferences() ?: defaultValue.also( ::write ),
            policy = this.policy
        )

        override fun getFromSharedPreferences(): androidx.compose.ui.graphics.Color? {
            var fromFile: kotlin.Int? = null

            /*
                 Set [fromFile] to the value of [previousKey] if it's
                 existed in the preferences file, then delete that key
                 (for migration to new key)
             */
            if( sharedPreferences.contains( previousKey ) ) {
                fromFile = sharedPreferences.getInt( previousKey, defaultValue.hashCode() )
                sharedPreferences.edit( commit = true ) {
                    remove( previousKey )

                    // Add this value to new [key], otherwise, only old key
                    // will be removed and new key is not added until next start
                    // with default value
                    fromFile.also { putInt( key, it ) }
                }
            }

            /*
                 Set [fromFile] to the value of [key] if it's
                 existed in the preferences file.

                 Reasons for 2 separate steps are:
                 - When both [key] and [previousKey] are existed
                 in side the file, [previousKey] will be deleted
                 while value of [key] is being used.
                 - Or either 1 of the key will be used if only
                 1 of them existed inside the file.
            */
            if( sharedPreferences.contains( key ) )
                fromFile = sharedPreferences.getInt( key, defaultValue.hashCode() )

            return fromFile?.let( ::Color )
        }

        override fun write( value: androidx.compose.ui.graphics.Color ) =
            sharedPreferences.edit {
                putInt( key, value.hashCode() )
            }
    }

    object Key {
        const val HOME_ARTIST_ITEM_SIZE = "HomeAristItemSize"
        const val HOME_ALBUM_ITEM_SIZE = "HomeAlbumItemSize"
        const val HOME_LIBRARY_ITEM_SIZE = "HomeLibraryItemSize"
        const val SONG_THUMBNAIL_SIZE = "SongItemSize"
        const val ALBUM_THUMBNAIL_SIZE = "AlbumItemSize"
        const val ARTIST_THUMBNAIL_SIZE = "ArtistItemSize"
        const val PLAYLIST_THUMBNAIL_SIZE = "PlaylistItemSize"
        const val HOME_SONGS_SORT_BY = "HomeSongsSortBy"
        const val HOME_ON_DEVICE_SONGS_SORT_BY = "HomeOnDeviceSongsSortBy"
        const val HOME_ARTISTS_SORT_BY = "HomeArtistsSortBy"
        const val HOME_ALBUMS_SORT_BY = "HomeAlbumsSortBy"
        const val HOME_LIBRARY_SORT_BY = "HomeLibrarySortBy"
        const val PLAYLIST_SONGS_SORT_BY = "PlaylistSongsSortBy"
        const val HOME_SONGS_SORT_ORDER = "HomeSongsSortOrder"
        const val HOME_ARTISTS_SORT_ORDER = "PlaylistSongsSortOrder"
        const val HOME_ALBUM_SORT_ORDER = "PlaylistSongsSortOrder"
        const val HOME_LIBRARY_SORT_ORDER = "HomeLibrarySortOrder"
        const val PLAYLIST_SONGS_SORT_ORDER = "PlaylistSongsSortOrder"
        const val MAX_NUMBER_OF_SMART_RECOMMENDATIONS = "MaxNumberOfSmartRecommendations"
        const val MAX_NUMBER_OF_STATISTIC_ITEMS = "MaxNumberOfStatisticItems"
        const val MAX_NUMBER_OF_TOP_PLAYED = "MaxNumberOfTopPlayed"
        const val MAX_NUMBER_OF_SONG_IN_QUEUE = "MaxNumberOfTopPlayed"
        const val MAX_NUMBER_OF_NEXT_IN_QUEUE = "MaxNumberOfNextInQueue"
        const val ENABLE_SWIPE_ACTION = "EnableSwipeAction"
        const val QUEUE_SWIPE_LEFT_ACTION = "QueueSwipeLeftAction"
        const val QUEUE_SWIPE_RIGHT_ACTION = "QueueSwipeRightAction"
        const val PLAYLIST_SWIPE_LEFT_ACTION = "PlaylistSwipeLeftAction"
        const val PLAYLIST_SWIPE_RIGHT_ACTION = "PlaylistSwipeRightAction"
        const val ALBUM_SWIPE_LEFT_ACTION = "AlbumSwipeLeftAction"
        const val ALBUM_SWIPE_RIGHT_ACTION = "AlbumSwipeRightAction"
        const val MINI_PLAYER_POSITION = "MiniPlayerPosition"
        const val MINI_PLAYER_TYPE = "MiniPlayerType"
        const val MINI_PLAYER_PROGRESS_BAR = "MiniPlayerProgressBar"
        const val MINI_DISABLE_SWIPE_DOWN_TO_DISMISS = "MiniPlayerDisableSwipeDownToDismiss"
        const val PLAYER_CONTROLS_TYPE = "PlayerControlsType"
        const val PLAYER_IS_CONTROLS_EXPANDED = "PlayerIsControlsExpanded"
        const val PLAYER_INFO_TYPE = "PlayerInfoType"
        const val PLAYER_TYPE = "PlayerType"
        const val PLAYER_TIMELINE_TYPE = "PlayerTimelineType"
        const val PLAYER_PORTRAIT_THUMBNAIL_SIZE = "PlayerThumbnailSize"
        const val PLAYER_LANDSCAPE_THUMBNAIL_SIZE = "PlayerLandscapeThumbnailSize"
        const val PLAYER_TIMELINE_SIZE = "PlayerTimelineSize"
        const val PLAYER_PLAY_BUTTON_TYPE = "PlayerPlayButtonType"
        const val PLAYER_BACKGROUND = "PlayerBackground"
        const val PLAYER_THUMBNAIL_TYPE = "PlayerThumbnailType"
        const val PLAYER_THUMBNAIL_VINYL_SIZE = "PlayerThumbnailVinylSize"
        const val PLAYER_NO_THUMBNAIL_SWIPE_ANIMATION = "PlayerNoThumbnailSwipeAnimation"
        const val PLAYER_SHOW_THUMBNAIL = "PlayerShowThumbnail"
        const val PLAYER_BOTTOM_GRADIENT = "PlayerBottomGradient"
        const val PLAYER_EXPANDED = "PlayerExpanded"
        const val PLAYER_THUMBNAIL_HORIZONTAL_SWIPE_DISABLED = "PlayerThumbnailHorizontalSwipe"
        const val PLAYER_THUMBNAIL_FADE = "PlayerThumbnailFade"
        const val PLAYER_THUMBNAIL_FADE_EX = "PlayerThumbnailFadeEx"
        const val PLAYER_THUMBNAIL_SPACING = "PlayerThumbnailSpacing"
        const val PLAYER_THUMBNAIL_SPACING_LANDSCAPE = "PlayerThumbnailSpacingLandscape"
        const val PLAYER_VISUALIZER = "PlayerVisualizer"
        const val PLAYER_CURRENT_VISUALIZER  = "PlayerCurrentVisualizer"
        const val PLAYER_TAP_THUMBNAIL_FOR_LYRICS = "PlayerTapThumbnailForLyrics"
        const val PLAYER_ACTION_ADD_TO_PLAYLIST = "PlayerActionAddToPlaylist"
        const val PLAYER_ACTION_OPEN_QUEUE_ARROW = "PlayerActionOpenQueueArrow"
        const val PLAYER_ACTION_DOWNLOAD = "PlayerActionDownload"
        const val PLAYER_ACTION_LOOP = "PlayerActionLoop"
        const val PLAYER_ACTION_SHOW_LYRICS = "PlayerActionShowLyrics"
        const val PLAYER_ACTION_TOGGLE_EXPAND = "PlayerActionToggleExpand"
        const val PLAYER_ACTION_SHUFFLE = "PlayerActionShuffle"
        const val PLAYER_ACTION_SLEEP_TIMER = "PlayerActionSleepTimer"
        const val PLAYER_ACTION_SHOW_MENU = "PlayerActionShowMenu"
        const val PLAYER_ACTION_START_RADIO = "PlayerActionStartRadio"
        const val PLAYER_ACTION_OPEN_EQUALIZER = "PlayerActionOpenEqualizer"
        const val PLAYER_ACTION_DISCOVER = "PlayerActionDiscover"
        const val PLAYER_ACTION_TOGGLE_VIDEO = "PlayerActionToggleVideo"
        const val PLAYER_ACTION_LYRICS_POPUP_MESSAGE = "PlayerActionLyricsPopupMessage"
        const val PLAYER_TRANSPARENT_ACTIONS_BAR = "PlayerTransparentActionsBar"
        const val PLAYER_ACTION_BUTTONS_SPACED_EVENLY = "PlayerActionButtonsSpacedEvenly"
        const val PLAYER_ACTIONS_BAR_TAP_TO_OPEN_QUEUE = "PlayerActionsBarTapToOpenQueue"
        const val PLAYER_ACTIONS_BAR_SWIPE_UP_TO_OPEN_QUEUE = "PlayerIsActionsBarExpanded"
        const val PLAYER_IS_ACTIONS_BAR_EXPANDED = "PlayerActionsBarSwipeUpToOpenQueue"
        const val PLAYER_SHOW_TOTAL_QUEUE_TIME = "PlayerShowTotalQueueTime"
        const val PLAYER_IS_QUEUE_DURATION_EXPANDED = "PlayerIsQueueDurationExpanded"
        const val PLAYER_SHOW_NEXT_IN_QUEUE = "PlayerShowNextInQueue"
        const val PLAYER_IS_NEXT_IN_QUEUE_EXPANDED = "PlayerIsNextInQueueExpanded"
        const val PLAYER_SHOW_NEXT_IN_QUEUE_THUMBNAIL = "PlayerShowNextInQueueThumbnail"
        const val PLAYER_SHOW_SONGS_REMAINING_TIME = "PlayerShowSongsRemainingTime"
        const val PLAYER_SHOW_SEEK_BUTTONS = "PlayerShowSeekButtons"
        const val PLAYER_SHOW_TOP_ACTIONS_BAR = "PlayerShowTopActionsBar"
        const val PLAYER_IS_CONTROL_AND_TIMELINE_SWAPPED = "PlayerIsControlAndTimelineSwapped"
        const val PLAYER_SHOW_THUMBNAIL_ON_VISUALIZER = "PlayerShowThumbnailOnVisualizer"
        const val PLAYER_SHRINK_THUMBNAIL_ON_PAUSE = "PlayerShrinkThumbnailOnPause"
        const val PLAYER_KEEP_MINIMIZED = "PlayerKeepMinimized"
        const val PLAYER_BACKGROUND_BLUR = "PlayerBackgroundBlur"
        const val PLAYER_BACKGROUND_BLUR_STRENGTH = "PlayerBackgroundBlurStrength"
        const val PLAYER_BACKGROUND_BACK_DROP = "PlayerBackgroundBackDrop"
        const val PLAYER_BACKGROUND_FADING_EDGE = "PlayerBackgroundFadingEdge"
        const val PLAYER_STATS_FOR_NERDS = "PlayerStatsForNerds"
        const val PLAYER_IS_STATS_FOR_NERDS_EXPANDED = "PlayerIsStatForNerdsExpanded"
        const val PLAYER_THUMBNAILS_CAROUSEL = "PlayerThumbnailCarousel"
        const val PLAYER_THUMBNAIL_ANIMATION = "PlayerThumbnailAnimation"
        const val PLAYER_THUMBNAIL_ROTATION = "PlayerThumbnailRotation"
        const val PLAYER_IS_TITLE_EXPANDED = "PlayerIsTitleExpanded"
        const val PLAYER_IS_TIMELINE_EXPANDED = "PlayerIsTimelineExpanded"
        const val PLAYER_SONG_INFO_ICON = "PlayerSongInfoIcon"
        const val PLAYER_TOP_PADDING = "PlayerTopPadding"
        const val PLAYER_EXTRA_SPACE = "PlayerExtraSpace"
        const val PLAYER_ROTATING_ALBUM_COVER = "PlayerRotatingAlbumCover"
        const val EXO_CACHE_LOCATION = "ExoCacheLocation"
        const val IMAGE_CACHE_SIZE = "ThumbnailCacheSizeBytes"
        const val EXO_CACHE_SIZE = "SongCacheSizeBytes"
        const val EXO_DOWNLOAD_SIZE = "SongDownloadSizeBytes"
        const val MEDIA_NOTIFICATION_FIRST_ICON = "MediaNotificationFirstIcon"
        const val MEDIA_NOTIFICATION_SECOND_ICON = "MediaNotificationSecondIcon"
        const val LYRICS_SIZE = "LyricsSize"
        const val LYRICS_SIZE_LANDSCAPE = "LyricsSizeLandscape"
        const val LYRICS_COLOR = "LyricsColor"
        const val LYRICS_OUTLINE = "HomeAlbumType"
        const val LYRICS_FONT_SIZE = "LyricsFontSize"
        const val LYRICS_ROMANIZATION_TYPE = "LyricsRomanizationType"
        const val LYRICS_BACKGROUND = "LyricsBackground"
        const val LYRICS_HIGHLIGHT = "LyricsHighlight"
        const val LYRICS_ALIGNMENT = "LyricsAlignment"
        const val LYRICS_SHOW_THUMBNAIL = "LyricsShowThumbnail"
        const val LYRICS_JUMP_ON_TAP = "LyricsJumpOnTap"
        const val LYRICS_SHOW_ACCENT_BACKGROUND = "LyricsShowAccentBackground"
        const val LYRICS_SYNCHRONIZED = "LyricsSynchronized"
        const val LYRICS_SHOW_SECOND_LINE = "LyricsShowSecondLine"
        const val LYRICS_ANIMATE_SIZE = "LyricsAnimateSize"
        const val LYRICS_LANDSCAPE_CONTROLS = "LysricsLandscapeControls"
        const val HOME_ARTIST_TYPE = "HomeArtistType"
        const val HOME_ALBUM_TYPE = "HomeAlbumType"
        const val HOME_SONGS_TYPE = "HomeSongsType"
        const val HISTORY_PAGE_TYPE = "HistoryPageType"
        const val HOME_LIBRARY_TYPE = "HomePlaylistType"
        const val AUDIO_FADE_DURATION = "AudioFadeDuration"
        const val AUDIO_QUALITY = "AudioQuality"
        const val AUDIO_REVERB_PRESET = "AudioReverbPresetValue"
        const val AUDIO_SKIP_SILENCE = "AudioSkipSilence"
        const val AUDIO_VOLUME_NORMALIZATION = "AudioVolumeNormalization"
        const val AUDIO_VOLUME_NORMALIZATION_TARGET = "AudioVolumeNormalizationTarget"
        const val AUDIO_SHAKE_TO_SKIP = "AudioShakeToSkip"
        const val AUDIO_VOLUME_BUTTONS_CHANGE_SONG = "AudioVolumeButtonsChangeSong"
        const val AUDIO_BASS_BOOSTED = "AudioBassBoosted"
        const val AUDIO_BASS_BOOST_LEVEL = "AudioBassBoostLevel"
        const val AUDIO_SMART_PAUSE_DURING_CALLS = "AudioSmartPauseDuringCalls"
        const val AUDIO_SPEED = "AudioSpeed"
        const val AUDIO_SPEED_VALUE = "AudioSpeedValue"
        const val AUDIO_PITCH = "AudioPitch"
        const val AUDIO_VOLUME = "AudioVolume"
        const val AUDIO_DEVICE_VOLUME = "AudioDeviceVolume"
        const val AUDIO_MEDLEY_DURATION = "AudioMedleyDuration"
        const val YOUTUBE_LOGIN = "YouTubeLogin"
        const val YOUTUBE_PLAYLISTS_SYNC = "YouTubePlaylistsSync"
        const val YOUTUBE_ARTISTS_SYNC = "YouTubeArtistsSync"
        const val YOUTUBE_ALBUMS_SYNC = "YouTubeAlbumsSync"
        const val YOUTUBE_VISITOR_DATA = "YouTubeVisitorData"
        const val YOUTUBE_SYNC_ID = "YouTubeSyncId"
        const val YOUTUBE_COOKIES = "YouTubeCookies"
        const val YOUTUBE_ACCOUNT_NAME = "YouTubeAccountName"
        const val YOUTUBE_ACCOUNT_EMAIL = "YouTubeAccountEmail"
        const val YOUTUBE_SELF_CHANNEL_HANDLE = "YouTubeSelfChannelHandle"
        const val YOUTUBE_ACCOUNT_AVATAR = "YouTubeAccountAvatar"
        const val YOUTUBE_LAST_VIDEO_ID = "YouTubeLastVideoId"
        const val YOUTUBE_LAST_VIDEO_SECONDS = "YouTubeLastVideoSeconds"
        const val QUICK_PICKS_TYPE = "QuickPicksType"
        const val QUICK_PICKS_MIN_DURATION = "QuickPicksMinDuration"
        const val QUICK_PICKS_SHOW_TIPS = "QuickPicksShowTips"
        const val QUICK_PICKS_SHOW_RELATED_ALBUMS = "QuickPicksShowRelatedAlbums"
        const val QUICK_PICKS_SHOW_RELATED_ARTISTS = "QuickPicksShowRelatedArtists"
        const val QUICK_PICKS_SHOW_NEW_ALBUMS_ARTISTS = "QuickPicksShowNewAlbumsArtists"
        const val QUICK_PICKS_SHOW_NEW_ALBUMS = "QuickPicksShowNewAlbums"
        const val QUICK_PICKS_SHOW_MIGHT_LIKE_PLAYLISTS = "QuickPicksShowPlaylists"
        const val QUICK_PICKS_SHOW_MOODS_AND_GENRES = "QuickPicksShowMoodsAndGenres"
        const val QUICK_PICKS_SHOW_MONTHLY_PLAYLISTS = "QuickPicksShowMonthlyPlaylists"
        const val QUICK_PICKS_SHOW_CHARTS = "QuickPicksShowCharts"
        const val QUICK_PICKS_PAGE = "QuickPicksPage"
        const val DISCORD_LOGIN = "DiscordLogin"
        const val DISCORD_ACCESS_TOKEN = "DiscordPersonalAccessToken"
        const val IS_PROXY_ENABLED = "IsProxyEnabled"
        const val PROXY_SCHEME = "ProxyScheme"
        const val PROXY_HOST = "ProxyHost"
        const val PROXY_PORT  = "ProxyPort"
        const val DOH_SERVER = "DnsOverHttpsServer"
        const val CUSTOM_LIGHT_THEME_BACKGROUND_0 = "CustomLightThemeBackground0"
        const val CUSTOM_LIGHT_THEME_BACKGROUND_1 = "CustomLightThemeBackground1"
        const val CUSTOM_LIGHT_THEME_BACKGROUND_2 = "CustomLightThemeBackground2"
        const val CUSTOM_LIGHT_THEME_BACKGROUND_3 = "CustomLightThemeBackground3"
        const val CUSTOM_LIGHT_THEME_BACKGROUND_4 = "CustomLightThemeBackground4"
        const val CUSTOM_LIGHT_TEXT = "CustomLightThemeText"
        const val CUSTOM_LIGHT_TEXT_SECONDARY = "CustomLightThemeTextSecondary"
        const val CUSTOM_LIGHT_TEXT_DISABLED = "CustomLightThemeTextDisabled"
        const val CUSTOM_LIGHT_PLAY_BUTTON = "CustomLightThemePlayButton"
        const val CUSTOM_LIGHT_ACCENT = "CustomLightThemeAccent"
        const val CUSTOM_DARK_THEME_BACKGROUND_0 = "CustomDarkThemeBackground0"
        const val CUSTOM_DARK_THEME_BACKGROUND_1 = "CustomDarkThemeBackground1"
        const val CUSTOM_DARK_THEME_BACKGROUND_2 = "CustomDarkThemeBackground2"
        const val CUSTOM_DARK_THEME_BACKGROUND_3 = "CustomDarkThemeBackground3"
        const val CUSTOM_DARK_THEME_BACKGROUND_4 = "CustomDarkThemeBackground4"
        const val CUSTOM_DARK_TEXT = "CustomDarkThemeText"
        const val CUSTOM_DARK_TEXT_SECONDARY = "CustomDarkThemeTextSecondary"
        const val CUSTOM_DARK_TEXT_DISABLED = "CustomDarkThemeTextDisabled"
        const val CUSTOM_DARK_PLAY_BUTTON = "CustomDarkThemePlayButton"
        const val CUSTOM_DARK_ACCENT = "CustomDarkThemeAccent"
        const val RUNTIME_LOG = "DebugLog"
        const val RUNTIME_LOG_SHARED = "DebugLogShared"
        const val RUNTIME_LOG_LEVEL = "DebugLogLevel"
        const val RUNTIME_LOG_SEVERITY = "DebugLogSeverity"
        const val RUNTIME_LOG_FILE_COUNT = "DebugLogFileCount"
        const val RUNTIME_LOG_MAX_SIZE_PER_FILE = "DebugLogMaxSizePerFile"
        const val ALBUMS_PLATFORM_INDICATOR = "AlbumPlatformIndicator"
        const val ARTISTS_PLATFORM_INDICATOR = "ArtistsPlatformIndicator"
        const val PLAYLISTS_PLATFORM_INDICATOR = "PlaylistPlatformIndicator"
        const val SONG_THUMBNAIL_ROUNDNESS_PERCENT = "SongThumbnailRoundnessPercent"
        const val ALBUM_THUMBNAIL_ROUNDNESS_PERCENT = "AlbumThumbnailRoundnessPercent"
        const val ARTIST_THUMBNAIL_ROUNDNESS_PERCENT = "ArtistThumbnailRoundnessPercent"
        const val PLAYLIST_THUMBNAIL_ROUNDNESS_PERCENT = "PlaylistThumbnailRoundnessPercent"
        const val HOME_SONGS_TOP_PLAYLIST_PERIOD = "HomeSongsTopPlaylistPeriod"
        const val MENU_STYLE = "MenuStyle"
        const val MAIN_THEME = "MainTheme"
        const val COLOR_PALETTE = "ColorPalette"
        const val THEME_MODE = "ThemeMode"
        const val STARTUP_SCREEN = "StartupScreen"
        const val FONT = "Font"
        const val NAVIGATION_BAR_POSITION = "NavigationBarPosition"
        const val NAVIGATION_BAR_TYPE = "NavigationBarType"
        const val PAUSE_BETWEEN_SONGS = "PauseBetweenSongs"
        const val THUMBNAIL_BORDER_RADIUS = "ThumbnailBorderRadius"
        const val TRANSITION_EFFECT = "TransitionEffect"
        const val LIMIT_SONGS_WITH_DURATION = "LimitSongsWithDuration"
        const val QUEUE_TYPE = "QueueType"
        const val QUEUE_LOOP_TYPE = "QueueLoopType"
        const val QUEUE_AUTO_APPEND = "QueueAutoAppend"
        const val CAROUSEL_SIZE = "CarouselSize"
        const val THUMBNAIL_TYPE = "ThumbnailType"
        const val LIKE_ICON = "LikeIcon"
        const val LIVE_WALLPAPER = "LiveWallpaper"
        const val LIVE_WALLPAPER_RESET_DURATION = "LiveWallpaperResetDuration"
        const val ANIMATED_GRADIENT = "AnimatedGradient"
        const val NOW_PLAYING_INDICATOR = "NowPlayingIndicator"
        const val PIP_MODULE = "PipModule"
        const val CHECK_UPDATE = "CheckUpdateState"
        const val SHOW_CHECK_UPDATE_STATUS = "ShowNoUpdateAvailableMessage"
        const val APP_LANGUAGE = "AppLanguage"
        const val OTHER_APP_LANGUAGE = "OtherAppLanguage"
        const val APP_REGION = "AppRegion"
        const val HOME_ARTIST_AND_ALBUM_FILTER = "filterBy"
        const val STATISTIC_PAGE_CATEGORY = "StatisticPageCategory"
        const val MARQUEE_TEXT_EFFECT = "MarqueeEffect"
        const val PARENTAL_CONTROL = "ParentalControl"
        const val ROTATION_EFFECT = "RotationEffect"
        const val TRANSPARENT_TIMELINE = "TransparentTimeline"
        const val BLACK_GRADIENT = "BlackGradient"
        const val TEXT_OUTLINE = "TextOutline"
        const val SHOW_FLOATING_ICON = "ShowFloatingIcon"
        const val FLOATING_ICON_X_OFFSET = "FloatingIconXOffset"
        const val FLOATING_ICON_Y_OFFSET = "FloatingIconYOffset"
        const val MULTI_FLOATING_ICON_X_OFFSET = "MultiFloatingIconXOffset"
        const val MULTI_FLOATING_ICON_Y_OFFSET = "MultiFloatingIconYOffset"
        const val ZOOM_OUT_ANIMATION = "ZoomOutAnimation"
        const val ENABLE_DISCOVER = "EnableDiscover"
        const val ENABLE_PERSISTENT_QUEUE = "EnablePersistentQueue"
        const val RESUME_PLAYBACK_ON_STARTUP = "ResumePlaybackOnStartup"
        const val RESUME_PLAYBACK_WHEN_CONNECT_TO_AUDIO_DEVICE = "ResumePlaybackWhenConnectToAudioDevice"
        const val CLOSE_APP_ON_BACK = "CloseAppOnBack"
        const val PLAYBACK_SKIP_ON_ERROR = "PlaybackSkipOnError"
        const val USE_SYSTEM_FONT = "UseSystemFont"
        const val APPLY_FONT_PADDING = "ApplyFontPadding"
        const val SHOW_SEARCH_IN_NAVIGATION_BAR = "ShowSearchInNavigationBar"
        const val SHOW_STATS_IN_NAVIGATION_BAR = "ShowStatsInNavigationBar"
        const val SHOW_LISTENING_STATS = "ShowListeningStats"
        const val HOME_SONGS_SHOW_FAVORITES_CHIP = "HomeSongsShowFavoritesChip"
        const val HOME_SONGS_SHOW_CACHED_CHIP = "HomeSongsShowCachedChip"
        const val HOME_SONGS_SHOW_DOWNLOADED_CHIP = "HomeSongsShowDownloadedChip"
        const val HOME_SONGS_SHOW_MOST_PLAYED_CHIP = "HomeSongsShowMostPlayedChip"
        const val HOME_SONGS_SHOW_ON_DEVICE_CHIP = "HomeSongsShowOnDeviceChip"
        const val HOME_SONGS_ON_DEVICE_SHOW_FOLDERS = "HomeSongsOnDeviceShowFolders"
        const val HOME_SONGS_INCLUDE_ON_DEVICE_IN_ALL = "HomeSongsIncludeOnDeviceInAll"
        const val MONTHLY_PLAYLIST_COMPILATION = "MonthlyPlaylistCompilation"
        const val SHOW_MONTHLY_PLAYLISTS = "ShowMonthlyPlaylists"
        const val SHOW_PINNED_PLAYLISTS = "ShowPinnedPlaylists"
        const val SHOW_PLAYLIST_INDICATOR = "ShowPlaylistIndicator"
        const val PAUSE_WHEN_VOLUME_SET_TO_ZERO = "PauseWhenVolumeSetToZero"
        const val PAUSE_HISTORY = "PauseHistory"
        const val IS_PIP_ENABLED = "IsPiPEnabled"
        const val IS_AUTO_PIP_ENABLED = "IsAutoPiPEnabled"
        const val AUTO_DOWNLOAD = "AutoDownload"
        const val AUTO_DOWNLOAD_ON_LIKE = "AutoDownloadOnLike"
        const val AUTO_DOWNLOAD_ON_ALBUM_BOOKMARKED = "AutoDownloadOnAlbumBookmarked"
        const val KEEP_SCREEN_ON = "KeepScreenOn"
        const val AUTO_SYNC = "AutoSync"
        const val PAUSE_SEARCH_HISTORY = "PauseSearchHistory"
        const val IS_DATA_KEY_LOADED = "IsDataKeyLoaded"
        const val LOCAL_PLAYLIST_SMART_RECOMMENDATION = "LocalPlaylistSmartRecommendation"
        const val IS_CONNECTION_METERED = "IsConnectionMetered"
        const val SMART_REWIND = "SmartRewind"
        const val LOCAL_SONGS_FOLDER = "LocalSongsFolder"
        const val SEEN_CHANGELOGS_VERSION = "SeenChangelogsVersion"
        const val CUSTOM_COLOR = "CustomColorHashCode"
        const val SEARCH_RESULTS_TAB_INDEX = "SearchResultsTabIndex"
        const val HOME_TAB_INDEX = "HomeTabIndex"
        const val ARTIST_SCREEN_TAB_INDEX  = "ArtistScreenTabIndex"
        const val SINGLE_BACK_FROM_SEARCH = "SingleBackFromSearch"
        const val SONG_EMPTY_DURATION_PLACEHOLDER = "SongEmptyDurationPlaceholder"
        const val ACTIVE_PROFILE = "ActiveProfile"
    }
}