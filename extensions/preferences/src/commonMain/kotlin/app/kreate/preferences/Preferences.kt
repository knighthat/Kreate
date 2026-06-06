package app.kreate.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import app.kreate.di.PrefType
import app.kreate.di.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import androidx.datastore.preferences.core.Preferences.Key as DatastoreKey


@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
sealed class Preferences<K, V>(
    protected val storage: Storage,
    protected val key: DatastoreKey<K>,
    val defaultValue: V
) : MutableStateFlow<V> {

    companion object : KoinComponent {

        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private val preferences: Storage by inject(PrefType.DEFAULT)

        //<editor-fold desc="Swipe action">
        val ENABLE_SWIPE_ACTION by lazy {
            BooleanPref(preferences, Key.ENABLE_SWIPE_ACTION, true)
        }
        //</editor-fold>
        //<editor-fold desc="Mini player">
        val MINI_DISABLE_SWIPE_DOWN_TO_DISMISS by lazy {
            BooleanPref(preferences, Key.MINI_DISABLE_SWIPE_DOWN_TO_DISMISS, false)
        }
        //</editor-fold>
        //<editor-fold desc="Player">
        val PLAYER_IS_CONTROLS_EXPANDED by lazy {
            BooleanPref(preferences, Key.PLAYER_IS_CONTROLS_EXPANDED, true)
        }
        val PLAYER_SHOW_THUMBNAIL by lazy {
            BooleanPref(preferences, Key.PLAYER_SHOW_THUMBNAIL, true)
        }
        val PLAYER_BOTTOM_GRADIENT by lazy {
            BooleanPref(preferences, Key.PLAYER_BOTTOM_GRADIENT, false)
        }
        val PLAYER_EXPANDED by lazy {
            BooleanPref(preferences, Key.PLAYER_EXPANDED, false)
        }
        val PLAYER_THUMBNAIL_HORIZONTAL_SWIPE_DISABLED by lazy {
            BooleanPref(preferences, Key.PLAYER_THUMBNAIL_HORIZONTAL_SWIPE_DISABLED, false)
        }
        val PLAYER_VISUALIZER by lazy {
            BooleanPref(preferences, Key.PLAYER_VISUALIZER, false)
        }
        val PLAYER_TAP_THUMBNAIL_FOR_LYRICS by lazy {
            BooleanPref(preferences, Key.PLAYER_TAP_THUMBNAIL_FOR_LYRICS, true)
        }
        val PLAYER_ACTION_ADD_TO_PLAYLIST by lazy {
            BooleanPref(preferences, Key.PLAYER_ACTION_ADD_TO_PLAYLIST, true)
        }
        val PLAYER_ACTION_OPEN_QUEUE_ARROW by lazy {
            BooleanPref(preferences, Key.PLAYER_ACTION_OPEN_QUEUE_ARROW, true)
        }
        val PLAYER_ACTION_DOWNLOAD by lazy {
            BooleanPref(preferences, Key.PLAYER_ACTION_DOWNLOAD, true)
        }
        val PLAYER_ACTION_LOOP by lazy {
            BooleanPref(preferences, Key.PLAYER_ACTION_LOOP, true)
        }
        val PLAYER_ACTION_SHOW_LYRICS by lazy {
            BooleanPref(preferences, Key.PLAYER_ACTION_SHOW_LYRICS, true)
        }
        val PLAYER_ACTION_TOGGLE_EXPAND by lazy {
            BooleanPref(preferences, Key.PLAYER_ACTION_TOGGLE_EXPAND, true)
        }
        val PLAYER_ACTION_SHUFFLE by lazy {
            BooleanPref(preferences, Key.PLAYER_ACTION_SHUFFLE, true)
        }
        val PLAYER_ACTION_SLEEP_TIMER by lazy {
            BooleanPref(preferences, Key.PLAYER_ACTION_SLEEP_TIMER, false)
        }
        val PLAYER_ACTION_SHOW_MENU by lazy {
            BooleanPref(preferences, Key.PLAYER_ACTION_SHOW_MENU, false)
        }
        val PLAYER_ACTION_START_RADIO by lazy {
            BooleanPref(preferences, Key.PLAYER_ACTION_START_RADIO, false)
        }
        val PLAYER_ACTION_OPEN_EQUALIZER by lazy {
            BooleanPref(preferences, Key.PLAYER_ACTION_OPEN_EQUALIZER, false)
        }
        val PLAYER_ACTION_DISCOVER by lazy {
            BooleanPref(preferences, Key.PLAYER_ACTION_DISCOVER, false)
        }
        val PLAYER_ACTION_TOGGLE_VIDEO by lazy {
            BooleanPref(preferences, Key.PLAYER_ACTION_TOGGLE_VIDEO, false)
        }
        val PLAYER_ACTION_LYRICS_POPUP_MESSAGE by lazy {
            BooleanPref(preferences, Key.PLAYER_ACTION_LYRICS_POPUP_MESSAGE, true)
        }
        val PLAYER_TRANSPARENT_ACTIONS_BAR by lazy {
            BooleanPref(preferences, Key.PLAYER_TRANSPARENT_ACTIONS_BAR, false)
        }
        val PLAYER_ACTION_BUTTONS_SPACED_EVENLY by lazy {
            BooleanPref(preferences, Key.PLAYER_ACTION_BUTTONS_SPACED_EVENLY, false)
        }
        val PLAYER_ACTIONS_BAR_TAP_TO_OPEN_QUEUE by lazy {
            BooleanPref(preferences, Key.PLAYER_ACTIONS_BAR_TAP_TO_OPEN_QUEUE, true)
        }
        val PLAYER_ACTIONS_BAR_SWIPE_UP_TO_OPEN_QUEUE by lazy {
            BooleanPref(preferences, Key.PLAYER_ACTIONS_BAR_SWIPE_UP_TO_OPEN_QUEUE, true)
        }
        val PLAYER_IS_ACTIONS_BAR_EXPANDED by lazy {
            BooleanPref(preferences, Key.PLAYER_IS_ACTIONS_BAR_EXPANDED, true)
        }
        val PLAYER_SHOW_TOTAL_QUEUE_TIME by lazy {
            BooleanPref(preferences, Key.PLAYER_SHOW_TOTAL_QUEUE_TIME, true)
        }
        val PLAYER_IS_QUEUE_DURATION_EXPANDED by lazy {
            BooleanPref(preferences, Key.PLAYER_IS_QUEUE_DURATION_EXPANDED, true)
        }
        val PLAYER_SHOW_NEXT_IN_QUEUE by lazy {
            BooleanPref(preferences, Key.PLAYER_SHOW_NEXT_IN_QUEUE, false)
        }
        val PLAYER_IS_NEXT_IN_QUEUE_EXPANDED by lazy {
            BooleanPref(preferences, Key.PLAYER_IS_NEXT_IN_QUEUE_EXPANDED, true)
        }
        val PLAYER_SHOW_NEXT_IN_QUEUE_THUMBNAIL by lazy {
            BooleanPref(preferences, Key.PLAYER_SHOW_NEXT_IN_QUEUE_THUMBNAIL, true)
        }
        val PLAYER_SHOW_SONGS_REMAINING_TIME by lazy {
            BooleanPref(preferences, Key.PLAYER_SHOW_SONGS_REMAINING_TIME, true)
        }
        val PLAYER_SHOW_SEEK_BUTTONS by lazy {
            BooleanPref(preferences, Key.PLAYER_SHOW_SEEK_BUTTONS, true)
        }
        val PLAYER_SHOW_TOP_ACTIONS_BAR by lazy {
            BooleanPref(preferences, Key.PLAYER_SHOW_TOP_ACTIONS_BAR, true)
        }
        val PLAYER_IS_CONTROL_AND_TIMELINE_SWAPPED by lazy {
            BooleanPref(preferences, Key.PLAYER_IS_CONTROL_AND_TIMELINE_SWAPPED, false)
        }
        val PLAYER_SHOW_THUMBNAIL_ON_VISUALIZER by lazy {
            BooleanPref(preferences, Key.PLAYER_SHOW_THUMBNAIL_ON_VISUALIZER, false)
        }
        val PLAYER_SHRINK_THUMBNAIL_ON_PAUSE by lazy {
            BooleanPref(preferences, Key.PLAYER_SHRINK_THUMBNAIL_ON_PAUSE, false)
        }
        val PLAYER_KEEP_MINIMIZED by lazy {
            BooleanPref(preferences, Key.PLAYER_KEEP_MINIMIZED, false)
        }
        val PLAYER_BACKGROUND_BLUR by lazy {
            BooleanPref(preferences, Key.PLAYER_BACKGROUND_BLUR, true)
        }
        val PLAYER_BACKGROUND_FADING_EDGE by lazy {
            BooleanPref(preferences, Key.PLAYER_BACKGROUND_FADING_EDGE, false)
        }
        val PLAYER_STATS_FOR_NERDS by lazy {
            BooleanPref(preferences, Key.PLAYER_STATS_FOR_NERDS, false)
        }
        val PLAYER_IS_STATS_FOR_NERDS_EXPANDED by lazy {
            BooleanPref(preferences, Key.PLAYER_IS_STATS_FOR_NERDS_EXPANDED, true)
        }
        val PLAYER_THUMBNAILS_CAROUSEL by lazy {
            BooleanPref(preferences, Key.PLAYER_THUMBNAILS_CAROUSEL, true)
        }
        val PLAYER_THUMBNAIL_ANIMATION by lazy {
            BooleanPref(preferences, Key.PLAYER_THUMBNAIL_ANIMATION, false)
        }
        val PLAYER_THUMBNAIL_ROTATION by lazy {
            BooleanPref(preferences, Key.PLAYER_THUMBNAIL_ROTATION, false)
        }
        val PLAYER_IS_TITLE_EXPANDED by lazy {
            BooleanPref(preferences, Key.PLAYER_IS_TITLE_EXPANDED, true)
        }
        val PLAYER_IS_TIMELINE_EXPANDED by lazy {
            BooleanPref(preferences, Key.PLAYER_IS_TIMELINE_EXPANDED, true)
        }
        val PLAYER_SONG_INFO_ICON by lazy {
            BooleanPref(preferences, Key.PLAYER_SONG_INFO_ICON, true)
        }
        val PLAYER_TOP_PADDING by lazy {
            BooleanPref(preferences, Key.PLAYER_TOP_PADDING, true)
        }
        val PLAYER_EXTRA_SPACE by lazy {
            BooleanPref(preferences, Key.PLAYER_EXTRA_SPACE, false)
        }
        val PLAYER_ROTATING_ALBUM_COVER by lazy {
            BooleanPref(preferences, Key.PLAYER_ROTATING_ALBUM_COVER, false)
        }
        //</editor-fold>
        //<editor-fold desc="Lyrics">
        val LYRICS_SHOW_THUMBNAIL by lazy {
            BooleanPref(preferences, Key.LYRICS_SHOW_THUMBNAIL, false)
        }
        val LYRICS_JUMP_ON_TAP by lazy {
            BooleanPref(preferences, Key.LYRICS_JUMP_ON_TAP, true)
        }
        val LYRICS_SHOW_ACCENT_BACKGROUND by lazy {
            BooleanPref(preferences, Key.LYRICS_SHOW_ACCENT_BACKGROUND, false)
        }
        val LYRICS_SYNCHRONIZED by lazy {
            BooleanPref(preferences, Key.LYRICS_SYNCHRONIZED, false)
        }
        val LYRICS_SHOW_SECOND_LINE by lazy {
            BooleanPref(preferences, Key.LYRICS_SHOW_SECOND_LINE, false)
        }
        val LYRICS_ANIMATE_SIZE by lazy {
            BooleanPref(preferences, Key.LYRICS_ANIMATE_SIZE, false)
        }
        val LYRICS_LANDSCAPE_CONTROLS by lazy {
            BooleanPref(preferences, Key.LYRICS_LANDSCAPE_CONTROLS, true)
        }
        //</editor-fold>
        //<editor-fold desc="Audio">
        val AUDIO_SKIP_SILENCE by lazy {
            BooleanPref(preferences, Key.AUDIO_SKIP_SILENCE, false)
        }
        val AUDIO_VOLUME_NORMALIZATION by lazy {
            BooleanPref(preferences, Key.AUDIO_VOLUME_NORMALIZATION, false)
        }
        val AUDIO_SHAKE_TO_SKIP by lazy {
            BooleanPref(preferences, Key.AUDIO_SHAKE_TO_SKIP, false)
        }
        val AUDIO_VOLUME_BUTTONS_CHANGE_SONG by lazy {
            BooleanPref(preferences, Key.AUDIO_VOLUME_BUTTONS_CHANGE_SONG, false)
        }
        val AUDIO_BASS_BOOSTED by lazy {
            BooleanPref(preferences, Key.AUDIO_BASS_BOOSTED, false)
        }
        val AUDIO_SMART_PAUSE_DURING_CALLS by lazy {
            BooleanPref(preferences, Key.AUDIO_SMART_PAUSE_DURING_CALLS, true)
        }
        val AUDIO_SPEED by lazy {
            BooleanPref(preferences, Key.AUDIO_SPEED, false)
        }
        //</editor-fold>
        //<editor-fold desc="YouTube">
        val YOUTUBE_LOGIN by lazy {
            BooleanPref(preferences, Key.YOUTUBE_LOGIN, false)
        }
        val YOUTUBE_PLAYLISTS_SYNC by lazy {
            BooleanPref(preferences, Key.YOUTUBE_PLAYLISTS_SYNC, false)
        }
        val YOUTUBE_ARTISTS_SYNC by lazy {
            BooleanPref(preferences, Key.YOUTUBE_ARTISTS_SYNC, false)
        }
        val YOUTUBE_ALBUMS_SYNC by lazy {
            BooleanPref(preferences, Key.YOUTUBE_ALBUMS_SYNC, false)
        }
        //</editor-fold>
        //<editor-fold desc="Quick picks">
        val QUICK_PICKS_SHOW_TIPS by lazy {
            BooleanPref(preferences, Key.QUICK_PICKS_SHOW_TIPS, true)
        }
        val QUICK_PICKS_SHOW_RELATED_ALBUMS by lazy {
            BooleanPref(preferences, Key.QUICK_PICKS_SHOW_RELATED_ALBUMS, true)
        }
        val QUICK_PICKS_SHOW_RELATED_ARTISTS by lazy {
            BooleanPref(preferences, Key.QUICK_PICKS_SHOW_RELATED_ARTISTS, true)
        }
        val QUICK_PICKS_SHOW_NEW_ALBUMS_ARTISTS by lazy {
            BooleanPref(preferences, Key.QUICK_PICKS_SHOW_NEW_ALBUMS_ARTISTS, true)
        }
        val QUICK_PICKS_SHOW_NEW_ALBUMS by lazy {
            BooleanPref(preferences, Key.QUICK_PICKS_SHOW_NEW_ALBUMS, true)
        }
        val QUICK_PICKS_SHOW_MIGHT_LIKE_PLAYLISTS by lazy {
            BooleanPref(preferences, Key.QUICK_PICKS_SHOW_MIGHT_LIKE_PLAYLISTS, true)
        }
        val QUICK_PICKS_SHOW_MOODS_AND_GENRES by lazy {
            BooleanPref(preferences, Key.QUICK_PICKS_SHOW_MOODS_AND_GENRES, true)
        }
        val QUICK_PICKS_SHOW_MONTHLY_PLAYLISTS by lazy {
            BooleanPref(preferences, Key.QUICK_PICKS_SHOW_MONTHLY_PLAYLISTS, true)
        }
        val QUICK_PICKS_SHOW_CHARTS by lazy {
            BooleanPref(preferences, Key.QUICK_PICKS_SHOW_CHARTS, true)
        }
        val QUICK_PICKS_PAGE by lazy {
            BooleanPref(preferences, Key.QUICK_PICKS_PAGE, true)
        }
        //</editor-fold>
        //<editor-fold desc="Discord">
        val DISCORD_LOGIN by lazy {
            BooleanPref(preferences, Key.DISCORD_LOGIN, false)
        }
        //</editor-fold>
        //<editor-fold desc="Proxy">
        val IS_PROXY_ENABLED by lazy {
            BooleanPref(preferences, Key.IS_PROXY_ENABLED, false)
        }
        //</editor-fold>
        //<editor-fold desc="Logging">
        val RUNTIME_LOG by lazy {
            BooleanPref(preferences, Key.RUNTIME_LOG, false)
        }
        val RUNTIME_LOG_SHARED by lazy {
            BooleanPref(preferences, Key.RUNTIME_LOG_SHARED, true)
        }
        //</editor-fold>

        val QUEUE_AUTO_APPEND by lazy {
            BooleanPref(preferences, Key.QUEUE_AUTO_APPEND, true)
        }
        val SHOW_CHECK_UPDATE_STATUS by lazy {
            BooleanPref(preferences, Key.SHOW_CHECK_UPDATE_STATUS, true)
        }
        val MARQUEE_TEXT_EFFECT by lazy {
            BooleanPref(preferences, Key.MARQUEE_TEXT_EFFECT, true)
        }
        val PARENTAL_CONTROL by lazy {
            BooleanPref(preferences, Key.PARENTAL_CONTROL, false)
        }
        val ROTATION_EFFECT by lazy {
            BooleanPref(preferences, Key.ROTATION_EFFECT, true)
        }
        val TRANSPARENT_TIMELINE by lazy {
            BooleanPref(preferences, Key.TRANSPARENT_TIMELINE, true)
        }
        val BLACK_GRADIENT by lazy {
            BooleanPref(preferences, Key.BLACK_GRADIENT, false)
        }
        val TEXT_OUTLINE by lazy {
            BooleanPref(preferences, Key.TEXT_OUTLINE, false)
        }
        val SHOW_FLOATING_ICON by lazy {
            BooleanPref(preferences, Key.SHOW_FLOATING_ICON, false)
        }
        val ZOOM_OUT_ANIMATION by lazy {
            BooleanPref(preferences, Key.ZOOM_OUT_ANIMATION, false)
        }
        val ENABLE_DISCOVER by lazy {
            BooleanPref(preferences, Key.ENABLE_DISCOVER, false)
        }
        val ENABLE_PERSISTENT_QUEUE by lazy {
            BooleanPref(preferences, Key.ENABLE_PERSISTENT_QUEUE, false)
        }
        val RESUME_PLAYBACK_ON_STARTUP by lazy {
            BooleanPref(preferences, Key.RESUME_PLAYBACK_ON_STARTUP, false)
        }
        val RESUME_PLAYBACK_WHEN_CONNECT_TO_AUDIO_DEVICE by lazy {
            BooleanPref(preferences, Key.RESUME_PLAYBACK_WHEN_CONNECT_TO_AUDIO_DEVICE, false)
        }
        val CLOSE_APP_ON_BACK by lazy {
            BooleanPref(preferences, Key.CLOSE_APP_ON_BACK, true)
        }
        val PLAYBACK_SKIP_ON_ERROR by lazy {
            BooleanPref(preferences, Key.PLAYBACK_SKIP_ON_ERROR, false)
        }
        val USE_SYSTEM_FONT by lazy {
            BooleanPref(preferences, Key.USE_SYSTEM_FONT, false)
        }
        val APPLY_FONT_PADDING by lazy {
            BooleanPref(preferences, Key.APPLY_FONT_PADDING, false)
        }
        val SHOW_SEARCH_IN_NAVIGATION_BAR by lazy {
            BooleanPref(preferences, Key.SHOW_SEARCH_IN_NAVIGATION_BAR, false)
        }
        val SHOW_STATS_IN_NAVIGATION_BAR by lazy {
            BooleanPref(preferences, Key.SHOW_STATS_IN_NAVIGATION_BAR, false)
        }
        val SHOW_LISTENING_STATS by lazy {
            BooleanPref(preferences, Key.SHOW_LISTENING_STATS, true)
        }
        val HOME_SONGS_SHOW_FAVORITES_CHIP by lazy {
            BooleanPref(preferences, Key.HOME_SONGS_SHOW_FAVORITES_CHIP, true)
        }
        val HOME_SONGS_SHOW_CACHED_CHIP by lazy {
            BooleanPref(preferences, Key.HOME_SONGS_SHOW_CACHED_CHIP, true)
        }
        val HOME_SONGS_SHOW_DOWNLOADED_CHIP by lazy {
            BooleanPref(preferences, Key.HOME_SONGS_SHOW_DOWNLOADED_CHIP, true)
        }
        val HOME_SONGS_SHOW_MOST_PLAYED_CHIP by lazy {
            BooleanPref(preferences, Key.HOME_SONGS_SHOW_MOST_PLAYED_CHIP, true)
        }
        val HOME_SONGS_SHOW_ON_DEVICE_CHIP by lazy {
            BooleanPref(preferences, Key.HOME_SONGS_SHOW_ON_DEVICE_CHIP, true)
        }
        val HOME_SONGS_ON_DEVICE_SHOW_FOLDERS by lazy {
            BooleanPref(preferences, Key.HOME_SONGS_ON_DEVICE_SHOW_FOLDERS, true)
        }
        val HOME_SONGS_INCLUDE_ON_DEVICE_IN_ALL by lazy {
            BooleanPref(preferences, Key.HOME_SONGS_INCLUDE_ON_DEVICE_IN_ALL, false)
        }
        val MONTHLY_PLAYLIST_COMPILATION by lazy {
            BooleanPref(preferences, Key.MONTHLY_PLAYLIST_COMPILATION, true)
        }
        val SHOW_MONTHLY_PLAYLISTS by lazy {
            BooleanPref(preferences, Key.SHOW_MONTHLY_PLAYLISTS, true)
        }
        val SHOW_PINNED_PLAYLISTS by lazy {
            BooleanPref(preferences, Key.SHOW_PINNED_PLAYLISTS, true)
        }
        val SHOW_PLAYLIST_INDICATOR by lazy {
            BooleanPref(preferences, Key.SHOW_PLAYLIST_INDICATOR, false)
        }
        val PAUSE_WHEN_VOLUME_SET_TO_ZERO by lazy {
            BooleanPref(preferences, Key.PAUSE_WHEN_VOLUME_SET_TO_ZERO, false)
        }
        val PAUSE_HISTORY by lazy {
            BooleanPref(preferences, Key.PAUSE_HISTORY, false)
        }
        val IS_PIP_ENABLED by lazy {
            BooleanPref(preferences, Key.IS_PIP_ENABLED, false)
        }
        val IS_AUTO_PIP_ENABLED by lazy {
            BooleanPref(preferences, Key.IS_AUTO_PIP_ENABLED, false)
        }
        val AUTO_DOWNLOAD by lazy {
            BooleanPref(preferences, Key.AUTO_DOWNLOAD, false)
        }
        val AUTO_DOWNLOAD_ON_LIKE by lazy {
            BooleanPref(preferences, Key.AUTO_DOWNLOAD_ON_LIKE, false)
        }
        val AUTO_DOWNLOAD_ON_ALBUM_BOOKMARKED by lazy {
            BooleanPref(preferences, Key.AUTO_DOWNLOAD_ON_ALBUM_BOOKMARKED, false)
        }
        val KEEP_SCREEN_ON by lazy {
            BooleanPref(preferences, Key.KEEP_SCREEN_ON, false)
        }
        val AUTO_SYNC by lazy {
            BooleanPref(preferences, Key.AUTO_SYNC, false)
        }
        val PAUSE_SEARCH_HISTORY by lazy {
            BooleanPref(preferences, Key.PAUSE_SEARCH_HISTORY, false)
        }
        val IS_DATA_KEY_LOADED by lazy {
            BooleanPref(preferences, Key.IS_DATA_KEY_LOADED, false)
        }
        val LOCAL_PLAYLIST_SMART_RECOMMENDATION by lazy {
            BooleanPref(preferences, Key.LOCAL_PLAYLIST_SMART_RECOMMENDATION, false)
        }
        val IS_CONNECTION_METERED by lazy {
            BooleanPref(preferences, Key.IS_CONNECTION_METERED, true)
        }
        val SINGLE_BACK_FROM_SEARCH by lazy {
            BooleanPref(preferences, Key.SINGLE_BACK_FROM_SEARCH, true)
        }
        val SONG_EMPTY_DURATION_PLACEHOLDER by lazy {
            BooleanPref(preferences, Key.SONG_EMPTY_DURATION_PLACEHOLDER, false)
        }
    }

    private val _internalState = MutableStateFlow(defaultValue)

    override val replayCache: List<V> get() = _internalState.replayCache
    override val subscriptionCount: StateFlow<Int> get() = _internalState.subscriptionCount

    override var value: V
        get() = _internalState.value
        set(value) {
            _internalState.value = value
            scope.launch { writeToDisk(value) }
        }

    init {
        // Sync from Disk to the internal StateFlow
        scope.launch {
            storage.data
                   .mapNotNull {
                       it[key]?.let( ::deserialize ) ?: defaultValue
                   }
                   .distinctUntilChanged()
                   .collect { _internalState.value = it }
        }
    }

    protected abstract fun deserialize( key: K ): V?

    protected abstract fun serialize( value: V ): K

    protected open suspend fun writeToDisk( value: V ) {
        storage.edit { it[key] = serialize(value) }
    }

    fun reset() = update { defaultValue }

    override suspend fun emit( value: V ) {
        _internalState.emit( value )
        writeToDisk( value )
    }

    override fun tryEmit( value: V ): Boolean {
        val success = _internalState.tryEmit( value )
        if (success) {
            scope.launch { writeToDisk(value) }
        }
        return success
    }

    override fun compareAndSet( expect: V, update: V ): Boolean {
        val success = _internalState.compareAndSet( expect, update )
        if (success) {
            scope.launch { writeToDisk(update) }
        }
        return success
    }

    override suspend fun collect( collector: FlowCollector<V> ): Nothing =
        _internalState.collect( collector )

    @ExperimentalCoroutinesApi
    override fun resetReplayCache() = _internalState.resetReplayCache()

    class BooleanPref(
        storage: Storage,
        key: Preferences.Key,
        defaultValue: Boolean
    ) : Preferences<Boolean, Boolean>(storage, booleanPreferencesKey(key.value), defaultValue) {

        fun flip() = update { !it }

        override fun deserialize( key: Boolean ): Boolean = key

        override fun serialize( value: Boolean ): Boolean = value
    }

    class Key private constructor(val value: String) {
        companion object {
            //<editor-fold desc="Swipe action">
            val ENABLE_SWIPE_ACTION = Key("enable_swipe_action")
            //</editor-fold>
            //<editor-fold desc="Mini player">
            val MINI_DISABLE_SWIPE_DOWN_TO_DISMISS = Key("mini_disable_swipe_down_to_dismiss")
            //</editor-fold>
            //<editor-fold desc="Player">
            val PLAYER_IS_CONTROLS_EXPANDED = Key("player_is_controls_expanded")
            val PLAYER_SHOW_THUMBNAIL = Key("player_show_thumbnail")
            val PLAYER_BOTTOM_GRADIENT = Key("player_bottom_gradient")
            val PLAYER_EXPANDED = Key("player_expanded")
            val PLAYER_THUMBNAIL_HORIZONTAL_SWIPE_DISABLED = Key("player_thumbnail_horizontal_swipe_disabled")
            val PLAYER_VISUALIZER = Key("player_visualizer")
            val PLAYER_TAP_THUMBNAIL_FOR_LYRICS = Key("player_tap_thumbnail_for_lyrics")
            val PLAYER_ACTION_ADD_TO_PLAYLIST = Key("player_action_add_to_playlist")
            val PLAYER_ACTION_OPEN_QUEUE_ARROW = Key("player_action_open_queue_arrow")
            val PLAYER_ACTION_DOWNLOAD = Key("player_action_download")
            val PLAYER_ACTION_LOOP = Key("player_action_loop")
            val PLAYER_ACTION_SHOW_LYRICS = Key("player_action_show_lyrics")
            val PLAYER_ACTION_TOGGLE_EXPAND = Key("player_action_toggle_expand")
            val PLAYER_ACTION_SHUFFLE = Key("player_action_shuffle")
            val PLAYER_ACTION_SLEEP_TIMER = Key("player_action_sleep_timer")
            val PLAYER_ACTION_SHOW_MENU = Key("player_action_show_menu")
            val PLAYER_ACTION_START_RADIO = Key("player_action_start_radio")
            val PLAYER_ACTION_OPEN_EQUALIZER = Key("player_action_open_equalizer")
            val PLAYER_ACTION_DISCOVER = Key("player_action_discover")
            val PLAYER_ACTION_TOGGLE_VIDEO = Key("player_action_toggle_video")
            val PLAYER_ACTION_LYRICS_POPUP_MESSAGE = Key("player_action_lyrics_popup_message")
            val PLAYER_TRANSPARENT_ACTIONS_BAR = Key("player_transparent_actions_bar")
            val PLAYER_ACTION_BUTTONS_SPACED_EVENLY = Key("player_action_buttons_spaced_evenly")
            val PLAYER_ACTIONS_BAR_TAP_TO_OPEN_QUEUE = Key("player_actions_bar_tap_to_open_queue")
            val PLAYER_ACTIONS_BAR_SWIPE_UP_TO_OPEN_QUEUE = Key("player_actions_bar_swipe_up_to_open_queue")
            val PLAYER_IS_ACTIONS_BAR_EXPANDED = Key("player_is_actions_bar_expanded")
            val PLAYER_SHOW_TOTAL_QUEUE_TIME = Key("player_show_total_queue_time")
            val PLAYER_IS_QUEUE_DURATION_EXPANDED = Key("player_is_queue_duration_expanded")
            val PLAYER_SHOW_NEXT_IN_QUEUE = Key("player_show_next_in_queue")
            val PLAYER_IS_NEXT_IN_QUEUE_EXPANDED = Key("player_is_next_in_queue_expanded")
            val PLAYER_SHOW_NEXT_IN_QUEUE_THUMBNAIL = Key("player_show_next_in_queue_thumbnail")
            val PLAYER_SHOW_SONGS_REMAINING_TIME = Key("player_show_songs_remaining_time")
            val PLAYER_SHOW_SEEK_BUTTONS = Key("player_show_seek_buttons")
            val PLAYER_SHOW_TOP_ACTIONS_BAR = Key("player_show_top_actions_bar")
            val PLAYER_IS_CONTROL_AND_TIMELINE_SWAPPED = Key("player_is_control_and_timeline_swapped")
            val PLAYER_SHOW_THUMBNAIL_ON_VISUALIZER = Key("player_show_thumbnail_on_visualizer")
            val PLAYER_SHRINK_THUMBNAIL_ON_PAUSE = Key("player_shrink_thumbnail_on_pause")
            val PLAYER_KEEP_MINIMIZED = Key("player_keep_minimized")
            val PLAYER_BACKGROUND_BLUR = Key("player_background_blur")
            val PLAYER_BACKGROUND_FADING_EDGE = Key("player_background_fading_edge")
            val PLAYER_STATS_FOR_NERDS = Key("player_stats_for_nerds")
            val PLAYER_IS_STATS_FOR_NERDS_EXPANDED = Key("player_is_stats_for_nerds_expanded")
            val PLAYER_THUMBNAILS_CAROUSEL = Key("player_thumbnails_carousel")
            val PLAYER_THUMBNAIL_ANIMATION = Key("player_thumbnail_animation")
            val PLAYER_THUMBNAIL_ROTATION = Key("player_thumbnail_rotation")
            val PLAYER_IS_TITLE_EXPANDED = Key("player_is_title_expanded")
            val PLAYER_IS_TIMELINE_EXPANDED = Key("player_is_timeline_expanded")
            val PLAYER_SONG_INFO_ICON = Key("player_song_info_icon")
            val PLAYER_TOP_PADDING = Key("player_top_padding")
            val PLAYER_EXTRA_SPACE = Key("player_extra_space")
            val PLAYER_ROTATING_ALBUM_COVER = Key("player_rotating_album_cover")
            //</editor-fold>
            //<editor-fold desc="Lyrics">
            val LYRICS_SHOW_THUMBNAIL = Key("lyrics_show_thumbnail")
            val LYRICS_JUMP_ON_TAP = Key("lyrics_jump_on_tap")
            val LYRICS_SHOW_ACCENT_BACKGROUND = Key("lyrics_show_accent_background")
            val LYRICS_SYNCHRONIZED = Key("lyrics_synchronized")
            val LYRICS_SHOW_SECOND_LINE = Key("lyrics_show_second_line")
            val LYRICS_ANIMATE_SIZE = Key("lyrics_animate_size")
            val LYRICS_LANDSCAPE_CONTROLS = Key("lyrics_landscape_controls")
            //</editor-fold>
            //<editor-fold desc="Audio">
            val AUDIO_SKIP_SILENCE = Key("audio_skip_silence")
            val AUDIO_VOLUME_NORMALIZATION = Key("audio_volume_normalization")
            val AUDIO_SHAKE_TO_SKIP = Key("audio_shake_to_skip")
            val AUDIO_VOLUME_BUTTONS_CHANGE_SONG = Key("audio_volume_buttons_change_song")
            val AUDIO_BASS_BOOSTED = Key("audio_bass_boosted")
            val AUDIO_SMART_PAUSE_DURING_CALLS = Key("audio_smart_pause_during_calls")
            val AUDIO_SPEED = Key("audio_speed")
            //</editor-fold>
            //<editor-fold desc="YouTube">
            val YOUTUBE_LOGIN = Key("youtube_login")
            val YOUTUBE_PLAYLISTS_SYNC = Key("youtube_playlists_sync")
            val YOUTUBE_ARTISTS_SYNC = Key("youtube_artists_sync")
            val YOUTUBE_ALBUMS_SYNC = Key("youtube_albums_sync")
            //</editor-fold>
            //<editor-fold desc="Quick picks">
            val QUICK_PICKS_SHOW_TIPS = Key("quick_picks_show_tips")
            val QUICK_PICKS_SHOW_RELATED_ALBUMS = Key("quick_picks_show_related_albums")
            val QUICK_PICKS_SHOW_RELATED_ARTISTS = Key("quick_picks_show_related_artists")
            val QUICK_PICKS_SHOW_NEW_ALBUMS_ARTISTS = Key("quick_picks_show_new_albums_artists")
            val QUICK_PICKS_SHOW_NEW_ALBUMS = Key("quick_picks_show_new_albums")
            val QUICK_PICKS_SHOW_MIGHT_LIKE_PLAYLISTS = Key("quick_picks_show_might_like_playlists")
            val QUICK_PICKS_SHOW_MOODS_AND_GENRES = Key("quick_picks_show_moods_and_genres")
            val QUICK_PICKS_SHOW_MONTHLY_PLAYLISTS = Key("quick_picks_show_monthly_playlists")
            val QUICK_PICKS_SHOW_CHARTS = Key("quick_picks_show_charts")
            val QUICK_PICKS_PAGE = Key("quick_picks_page")
            //</editor-fold>
            //<editor-fold desc="Discord">
            val DISCORD_LOGIN = Key("discord_login")
            //</editor-fold>
            //<editor-fold desc="Proxy">
            val IS_PROXY_ENABLED = Key("is_proxy_enabled")
            //</editor-fold>
            //<editor-fold desc="Logging">
            val RUNTIME_LOG = Key("runtime_log")
            val RUNTIME_LOG_SHARED = Key("runtime_log_shared")
            //</editor-fold>

            val QUEUE_AUTO_APPEND = Key("queue_auto_append")
            val SHOW_CHECK_UPDATE_STATUS = Key("show_check_update_status")
            val MARQUEE_TEXT_EFFECT = Key("marquee_text_effect")
            val PARENTAL_CONTROL = Key("parental_control")
            val ROTATION_EFFECT = Key("rotation_effect")
            val TRANSPARENT_TIMELINE = Key("transparent_timeline")
            val BLACK_GRADIENT = Key("black_gradient")
            val TEXT_OUTLINE = Key("text_outline")
            val SHOW_FLOATING_ICON = Key("show_floating_icon")
            val ZOOM_OUT_ANIMATION = Key("zoom_out_animation")
            val ENABLE_DISCOVER = Key("enable_discover")
            val ENABLE_PERSISTENT_QUEUE = Key("enable_persistent_queue")
            val RESUME_PLAYBACK_ON_STARTUP = Key("resume_playback_on_startup")
            val RESUME_PLAYBACK_WHEN_CONNECT_TO_AUDIO_DEVICE = Key("resume_playback_when_connect_to_audio_device")
            val CLOSE_APP_ON_BACK = Key("close_app_on_back")
            val PLAYBACK_SKIP_ON_ERROR = Key("playback_skip_on_error")
            val USE_SYSTEM_FONT = Key("use_system_font")
            val APPLY_FONT_PADDING = Key("apply_font_padding")
            val SHOW_SEARCH_IN_NAVIGATION_BAR = Key("show_search_in_navigation_bar")
            val SHOW_STATS_IN_NAVIGATION_BAR = Key("show_stats_in_navigation_bar")
            val SHOW_LISTENING_STATS = Key("show_listening_stats")
            val HOME_SONGS_SHOW_FAVORITES_CHIP = Key("home_songs_show_favorites_chip")
            val HOME_SONGS_SHOW_CACHED_CHIP = Key("home_songs_show_cached_chip")
            val HOME_SONGS_SHOW_DOWNLOADED_CHIP = Key("home_songs_show_downloaded_chip")
            val HOME_SONGS_SHOW_MOST_PLAYED_CHIP = Key("home_songs_show_most_played_chip")
            val HOME_SONGS_SHOW_ON_DEVICE_CHIP = Key("home_songs_show_on_device_chip")
            val HOME_SONGS_ON_DEVICE_SHOW_FOLDERS = Key("home_songs_on_device_show_folders")
            val HOME_SONGS_INCLUDE_ON_DEVICE_IN_ALL = Key("home_songs_include_on_device_in_all")
            val MONTHLY_PLAYLIST_COMPILATION = Key("monthly_playlist_compilation")
            val SHOW_MONTHLY_PLAYLISTS = Key("show_monthly_playlists")
            val SHOW_PINNED_PLAYLISTS = Key("show_pinned_playlists")
            val SHOW_PLAYLIST_INDICATOR = Key("show_playlist_indicator")
            val PAUSE_WHEN_VOLUME_SET_TO_ZERO = Key("pause_when_volume_set_to_zero")
            val PAUSE_HISTORY = Key("pause_history")
            val IS_PIP_ENABLED = Key("is_pip_enabled")
            val IS_AUTO_PIP_ENABLED = Key("is_auto_pip_enabled")
            val AUTO_DOWNLOAD = Key("auto_download")
            val AUTO_DOWNLOAD_ON_LIKE = Key("auto_download_on_like")
            val AUTO_DOWNLOAD_ON_ALBUM_BOOKMARKED = Key("auto_download_on_album_bookmarked")
            val KEEP_SCREEN_ON = Key("keep_screen_on")
            val AUTO_SYNC = Key("auto_sync")
            val PAUSE_SEARCH_HISTORY = Key("pause_search_history")
            val IS_DATA_KEY_LOADED = Key("is_data_key_loaded")
            val LOCAL_PLAYLIST_SMART_RECOMMENDATION = Key("local_playlist_smart_recommendation")
            val IS_CONNECTION_METERED = Key("is_connection_metered")
            val SINGLE_BACK_FROM_SEARCH = Key("single_back_from_search")
            val SONG_EMPTY_DURATION_PLACEHOLDER = Key("song_empty_duration_placeholder")
        }
    }
}