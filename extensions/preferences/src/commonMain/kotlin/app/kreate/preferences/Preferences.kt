package app.kreate.preferences

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.kreate.android.enums.DohServer
import app.kreate.android.enums.PlatformIndicatorType
import app.kreate.constant.AlbumSortBy
import app.kreate.constant.ArtistSortBy
import app.kreate.constant.Language
import app.kreate.constant.PlaylistSongSortBy
import app.kreate.constant.PlaylistSortBy
import app.kreate.constant.SongSortBy
import app.kreate.constant.SortOrder
import app.kreate.constant.Type
import app.kreate.di.PrefType
import app.kreate.di.Storage
import co.touchlab.kermit.Severity
import it.fast4x.rimusic.enums.AlbumSwipeAction
import it.fast4x.rimusic.enums.AlbumsType
import it.fast4x.rimusic.enums.AnimatedGradient
import it.fast4x.rimusic.enums.ArtistsType
import it.fast4x.rimusic.enums.AudioQualityFormat
import it.fast4x.rimusic.enums.BackgroundProgress
import it.fast4x.rimusic.enums.BuiltInPlaylist
import it.fast4x.rimusic.enums.CarouselSize
import it.fast4x.rimusic.enums.CheckUpdateState
import it.fast4x.rimusic.enums.ColorPaletteMode
import it.fast4x.rimusic.enums.ColorPaletteName
import it.fast4x.rimusic.enums.DurationInMilliseconds
import it.fast4x.rimusic.enums.DurationInMinutes
import it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import it.fast4x.rimusic.enums.ExoPlayerMinTimeForEvent
import it.fast4x.rimusic.enums.FilterBy
import it.fast4x.rimusic.enums.FontType
import it.fast4x.rimusic.enums.HistoryType
import it.fast4x.rimusic.enums.HomeItemSize
import it.fast4x.rimusic.enums.HomeScreenTabs
import it.fast4x.rimusic.enums.IconLikeType
import it.fast4x.rimusic.enums.LyricsAlignment
import it.fast4x.rimusic.enums.LyricsBackground
import it.fast4x.rimusic.enums.LyricsColor
import it.fast4x.rimusic.enums.LyricsFontSize
import it.fast4x.rimusic.enums.LyricsHighlight
import it.fast4x.rimusic.enums.LyricsOutline
import it.fast4x.rimusic.enums.MaxSongs
import it.fast4x.rimusic.enums.MaxStatisticsItems
import it.fast4x.rimusic.enums.MaxTopPlaylistItems
import it.fast4x.rimusic.enums.MenuStyle
import it.fast4x.rimusic.enums.MusicAnimationType
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.NavigationBarType
import it.fast4x.rimusic.enums.NotificationButtons
import it.fast4x.rimusic.enums.PauseBetweenSongs
import it.fast4x.rimusic.enums.PipModule
import it.fast4x.rimusic.enums.PlayEventsType
import it.fast4x.rimusic.enums.PlayerBackgroundColors
import it.fast4x.rimusic.enums.PlayerPlayButtonType
import it.fast4x.rimusic.enums.PlayerPosition
import it.fast4x.rimusic.enums.PlayerThumbnailSize
import it.fast4x.rimusic.enums.PlayerTimelineSize
import it.fast4x.rimusic.enums.PlayerTimelineType
import it.fast4x.rimusic.enums.PlaylistSwipeAction
import it.fast4x.rimusic.enums.PlaylistsType
import it.fast4x.rimusic.enums.QueueSwipeAction
import it.fast4x.rimusic.enums.RecommendationsNumber
import it.fast4x.rimusic.enums.Romanization
import it.fast4x.rimusic.enums.SongsNumber
import it.fast4x.rimusic.enums.StatisticsCategory
import it.fast4x.rimusic.enums.StatisticsType
import it.fast4x.rimusic.enums.SwipeAnimationNoThumbnail
import it.fast4x.rimusic.enums.ThumbnailCoverType
import it.fast4x.rimusic.enums.ThumbnailRoundness
import it.fast4x.rimusic.enums.TransitionEffect
import it.fast4x.rimusic.enums.UiType
import it.fast4x.rimusic.enums.WallpaperType
import it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import it.fast4x.rimusic.ui.styling.DefaultLightColorPalette
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
import java.net.Proxy
import kotlin.enums.EnumEntries
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
        private val credentials: Storage by inject(PrefType.CREDENTIALS)

        //<editor-fold desc="Item size">
        val HOME_ARTIST_ITEM_SIZE by lazy {
            EnumPref(preferences, Key.HOME_ARTIST_ITEM_SIZE, HomeItemSize.SMALL, HomeItemSize::entries)
        }
        val HOME_ALBUM_ITEM_SIZE by lazy {
            EnumPref(preferences, Key.HOME_ALBUM_ITEM_SIZE, HomeItemSize.SMALL, HomeItemSize::entries)
        }
        val HOME_LIBRARY_ITEM_SIZE by lazy {
            EnumPref(preferences, Key.HOME_LIBRARY_ITEM_SIZE, HomeItemSize.SMALL, HomeItemSize::entries)
        }
        //</editor-fold>
        //<editor-fold desc="Sort by">
        val HOME_SONGS_SORT_BY by lazy {
            EnumPref(preferences, Key.HOME_SONGS_SORT_BY, SongSortBy.TITLE, SongSortBy::entries)
        }
        val HOME_ARTISTS_SORT_BY by lazy {
            EnumPref(preferences, Key.HOME_ARTISTS_SORT_BY, ArtistSortBy.TITLE, ArtistSortBy::entries)
        }
        val HOME_ALBUMS_SORT_BY by lazy {
            EnumPref(preferences, Key.HOME_ALBUMS_SORT_BY, AlbumSortBy.TITLE, AlbumSortBy::entries)
        }
        val HOME_LIBRARY_SORT_BY by lazy {
            EnumPref(preferences, Key.HOME_LIBRARY_SORT_BY, PlaylistSortBy.SONG_COUNT, PlaylistSortBy::entries)
        }
        val PLAYLIST_SONGS_SORT_BY by lazy {
            EnumPref(preferences, Key.PLAYLIST_SONGS_SORT_BY, PlaylistSongSortBy.TITLE, PlaylistSongSortBy::entries)
        }
        //</editor-fold>
        //<editor-fold desc="Sort order">
        val HOME_SONGS_SORT_ORDER by lazy {
            EnumPref(preferences, Key.HOME_SONGS_SORT_ORDER, SortOrder.ASCENDING, SortOrder::entries)
        }
        val HOME_ARTISTS_SORT_ORDER by lazy {
            EnumPref(preferences, Key.HOME_ARTISTS_SORT_ORDER, SortOrder.ASCENDING, SortOrder::entries)
        }
        val HOME_ALBUM_SORT_ORDER by lazy {
            EnumPref(preferences, Key.HOME_ALBUM_SORT_ORDER, SortOrder.ASCENDING, SortOrder::entries)
        }
        val HOME_LIBRARY_SORT_ORDER by lazy {
            EnumPref(preferences, Key.HOME_LIBRARY_SORT_ORDER, SortOrder.ASCENDING, SortOrder::entries)
        }
        val PLAYLIST_SONGS_SORT_ORDER by lazy {
            EnumPref(preferences, Key.PLAYLIST_SONGS_SORT_ORDER, SortOrder.ASCENDING, SortOrder::entries)
        }
        //</editor-fold>
        //<editor-fold desc="Max # of ...">
        val MAX_NUMBER_OF_SMART_RECOMMENDATIONS by lazy {
            EnumPref(preferences, Key.MAX_NUMBER_OF_SMART_RECOMMENDATIONS, RecommendationsNumber.`5`, RecommendationsNumber::entries)
        }
        val MAX_NUMBER_OF_STATISTIC_ITEMS by lazy {
            EnumPref(preferences, Key.MAX_NUMBER_OF_STATISTIC_ITEMS, MaxStatisticsItems.`10`, MaxStatisticsItems::entries)
        }
        val MAX_NUMBER_OF_TOP_PLAYED by lazy {
            EnumPref(preferences, Key.MAX_NUMBER_OF_TOP_PLAYED, MaxTopPlaylistItems.`10`, MaxTopPlaylistItems::entries)
        }
        val MAX_NUMBER_OF_SONG_IN_QUEUE by lazy {
            EnumPref(preferences, Key.MAX_NUMBER_OF_SONG_IN_QUEUE, MaxSongs.Unlimited, MaxSongs::entries)
        }
        val MAX_NUMBER_OF_NEXT_IN_QUEUE by lazy {
            EnumPref(preferences, Key.MAX_NUMBER_OF_NEXT_IN_QUEUE, SongsNumber.`2`, SongsNumber::entries)
        }
        //</editor-fold>
        //<editor-fold desc="Swipe action">
        val ENABLE_SWIPE_ACTION by lazy {
            BooleanPref(preferences, Key.ENABLE_SWIPE_ACTION, true)
        }
        val QUEUE_SWIPE_LEFT_ACTION by lazy {
            EnumPref(preferences, Key.QUEUE_SWIPE_LEFT_ACTION, QueueSwipeAction.RemoveFromQueue, QueueSwipeAction::entries)
        }
        val QUEUE_SWIPE_RIGHT_ACTION by lazy {
            EnumPref(preferences, Key.QUEUE_SWIPE_RIGHT_ACTION, QueueSwipeAction.PlayNext, QueueSwipeAction::entries)
        }
        val PLAYLIST_SWIPE_LEFT_ACTION by lazy {
            EnumPref(preferences, Key.PLAYLIST_SWIPE_LEFT_ACTION, PlaylistSwipeAction.Favourite, PlaylistSwipeAction::entries)
        }
        val PLAYLIST_SWIPE_RIGHT_ACTION by lazy {
            EnumPref(preferences, Key.PLAYLIST_SWIPE_RIGHT_ACTION, PlaylistSwipeAction.PlayNext, PlaylistSwipeAction::entries)
        }
        val ALBUM_SWIPE_LEFT_ACTION by lazy {
            EnumPref(preferences, Key.ALBUM_SWIPE_LEFT_ACTION, AlbumSwipeAction.PlayNext, AlbumSwipeAction::entries)
        }
        val ALBUM_SWIPE_RIGHT_ACTION by lazy {
            EnumPref(preferences, Key.ALBUM_SWIPE_RIGHT_ACTION, AlbumSwipeAction.Bookmark, AlbumSwipeAction::entries)
        }
        //</editor-fold>
        //<editor-fold desc="Mini player">
        val MINI_DISABLE_SWIPE_DOWN_TO_DISMISS by lazy {
            BooleanPref(preferences, Key.MINI_DISABLE_SWIPE_DOWN_TO_DISMISS, false)
        }
        val MINI_PLAYER_POSITION by lazy {
            EnumPref(preferences, Key.MINI_PLAYER_POSITION, PlayerPosition.Bottom, PlayerPosition::entries)
        }
        val MINI_PLAYER_TYPE by lazy {
            EnumPref(preferences, Key.MINI_PLAYER_TYPE, Type.MODERN, Type::entries)
        }
        val MINI_PLAYER_PROGRESS_BAR by lazy {
            EnumPref(preferences, Key.MINI_PLAYER_PROGRESS_BAR, BackgroundProgress.MiniPlayer, BackgroundProgress::entries)
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
        val PLAYER_CONTROLS_TYPE by lazy {
            EnumPref(preferences, Key.PLAYER_CONTROLS_TYPE, Type.LEGACY, Type::entries)
        }
        val PLAYER_INFO_TYPE by lazy {
            EnumPref(preferences, Key.PLAYER_INFO_TYPE, Type.LEGACY, Type::entries)
        }
        val PLAYER_TYPE by lazy {
            EnumPref(preferences, Key.PLAYER_TYPE, Type.LEGACY, Type::entries)
        }
        val PLAYER_TIMELINE_TYPE by lazy {
            EnumPref(preferences, Key.PLAYER_TIMELINE_TYPE, PlayerTimelineType.FakeAudioBar, PlayerTimelineType::entries)
        }
        val PLAYER_PORTRAIT_THUMBNAIL_SIZE by lazy {
            EnumPref(preferences, Key.PLAYER_PORTRAIT_THUMBNAIL_SIZE, PlayerThumbnailSize.Biggest, PlayerThumbnailSize::entries)
        }
        val PLAYER_LANDSCAPE_THUMBNAIL_SIZE by lazy {
            EnumPref(preferences, Key.PLAYER_LANDSCAPE_THUMBNAIL_SIZE, PlayerThumbnailSize.Biggest, PlayerThumbnailSize::entries)
        }
        val PLAYER_TIMELINE_SIZE by lazy {
            EnumPref(preferences, Key.PLAYER_TIMELINE_SIZE, PlayerTimelineSize.Biggest, PlayerTimelineSize::entries)
        }
        val PLAYER_PLAY_BUTTON_TYPE by lazy {
            EnumPref(preferences, Key.PLAYER_PLAY_BUTTON_TYPE, PlayerPlayButtonType.Disabled, PlayerPlayButtonType::entries)
        }
        val PLAYER_BACKGROUND by lazy {
            EnumPref(preferences, Key.PLAYER_BACKGROUND, PlayerBackgroundColors.BlurredCoverColor, PlayerBackgroundColors::entries)
        }
        val PLAYER_THUMBNAIL_TYPE by lazy {
            EnumPref(preferences, Key.PLAYER_THUMBNAIL_TYPE, ThumbnailCoverType.Vinyl, ThumbnailCoverType::entries)
        }
        val PLAYER_NO_THUMBNAIL_SWIPE_ANIMATION by lazy {
            EnumPref(preferences, Key.PLAYER_NO_THUMBNAIL_SWIPE_ANIMATION, SwipeAnimationNoThumbnail.Sliding, SwipeAnimationNoThumbnail::entries)
        }
        //</editor-fold>
        //<editor-fold desc="Cache">
        val EXO_CACHE_LOCATION by lazy {
            EnumPref(preferences, Key.EXO_CACHE_LOCATION, ExoPlayerCacheLocation.SPLIT, ExoPlayerCacheLocation::entries)
        }
        //</editor-fold>
        //<editor-fold desc="Notification">
        val MEDIA_NOTIFICATION_FIRST_ICON by lazy {
            EnumPref(preferences, Key.MEDIA_NOTIFICATION_FIRST_ICON, NotificationButtons.Download, NotificationButtons::entries)
        }
        val MEDIA_NOTIFICATION_SECOND_ICON by lazy {
            EnumPref(preferences, Key.MEDIA_NOTIFICATION_SECOND_ICON, NotificationButtons.Favorites, NotificationButtons::entries)
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
        val LYRICS_COLOR by lazy {
            EnumPref(preferences, Key.LYRICS_COLOR, LyricsColor.Thememode, LyricsColor::entries)
        }
        val LYRICS_OUTLINE by lazy {
            EnumPref(preferences, Key.LYRICS_OUTLINE, LyricsOutline.None, LyricsOutline::entries)
        }
        val LYRICS_FONT_SIZE by lazy {
            EnumPref(preferences, Key.LYRICS_FONT_SIZE, LyricsFontSize.Medium, LyricsFontSize::entries)
        }
        val LYRICS_ROMANIZATION_TYPE by lazy {
            EnumPref(preferences, Key.LYRICS_ROMANIZATION_TYPE, Romanization.Off, Romanization::entries)
        }
        val LYRICS_BACKGROUND by lazy {
            EnumPref(preferences, Key.LYRICS_BACKGROUND, LyricsBackground.Black, LyricsBackground::entries)
        }
        val LYRICS_HIGHLIGHT by lazy {
            EnumPref(preferences, Key.LYRICS_HIGHLIGHT, LyricsHighlight.None, LyricsHighlight::entries)
        }
        val LYRICS_ALIGNMENT by lazy {
            EnumPref(preferences, Key.LYRICS_ALIGNMENT, LyricsAlignment.Center, LyricsAlignment::entries)
        }
        //</editor-fold>
        //<editor-fold desc="Page type">
        val HOME_ARTIST_TYPE by lazy {
            EnumPref(preferences, Key.HOME_ARTIST_TYPE, ArtistsType.Favorites, ArtistsType::entries)
        }
        val HOME_ALBUM_TYPE by lazy {
            EnumPref(preferences, Key.HOME_ALBUM_TYPE, AlbumsType.Favorites, AlbumsType::entries)
        }
        val HOME_SONGS_TYPE by lazy {
            EnumPref(preferences, Key.HOME_SONGS_TYPE, BuiltInPlaylist.Favorites, BuiltInPlaylist::entries)
        }
        val HISTORY_PAGE_TYPE by lazy {
            EnumPref(preferences, Key.HISTORY_PAGE_TYPE, HistoryType.History, HistoryType::entries)
        }
        val HOME_LIBRARY_TYPE by lazy {
            EnumPref(preferences, Key.HOME_LIBRARY_TYPE, PlaylistsType.Playlist, PlaylistsType::entries)
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
        val AUDIO_FADE_DURATION by lazy {
            EnumPref(preferences, Key.AUDIO_FADE_DURATION, DurationInMilliseconds.Disabled, DurationInMilliseconds::entries)
        }
        val AUDIO_QUALITY by lazy {
            EnumPref(preferences, Key.AUDIO_QUALITY, AudioQualityFormat.Auto, AudioQualityFormat::entries)
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
        val YOUTUBE_VISITOR_DATA by lazy {
            StringPref(credentials, Key.YOUTUBE_VISITOR_DATA, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36")
        }
        val YOUTUBE_SYNC_ID by lazy {
            StringPref(credentials, Key.YOUTUBE_SYNC_ID, "")
        }
        val YOUTUBE_COOKIES by lazy {
            StringPref(credentials, Key.YOUTUBE_COOKIES, "")
        }
        val YOUTUBE_ACCOUNT_NAME by lazy {
            StringPref(credentials, Key.YOUTUBE_ACCOUNT_NAME, "")
        }
        val YOUTUBE_ACCOUNT_EMAIL by lazy {
            StringPref(credentials, Key.YOUTUBE_ACCOUNT_EMAIL, "")
        }
        val YOUTUBE_SELF_CHANNEL_HANDLE by lazy {
            StringPref(credentials, Key.YOUTUBE_SELF_CHANNEL_HANDLE, "")
        }
        val YOUTUBE_ACCOUNT_AVATAR by lazy {
            StringPref(credentials, Key.YOUTUBE_ACCOUNT_AVATAR, "")
        }
        val YOUTUBE_LAST_VIDEO_ID by lazy {
            StringPref(preferences, Key.YOUTUBE_LAST_VIDEO_ID, "")
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
        val QUICK_PICKS_TYPE by lazy {
            EnumPref(preferences, Key.QUICK_PICKS_TYPE, PlayEventsType.MostPlayed, PlayEventsType::entries)
        }
        val QUICK_PICKS_MIN_DURATION by lazy {
            EnumPref(preferences, Key.QUICK_PICKS_MIN_DURATION, ExoPlayerMinTimeForEvent.`20s`, ExoPlayerMinTimeForEvent::entries)
        }
        //</editor-fold>
        //<editor-fold desc="Discord">
        val DISCORD_LOGIN by lazy {
            BooleanPref(preferences, Key.DISCORD_LOGIN, false)
        }
        val DISCORD_ACCESS_TOKEN by lazy {
            StringPref(credentials, Key.DISCORD_ACCESS_TOKEN, "")
        }
        //</editor-fold>
        //<editor-fold desc="Proxy">
        val IS_PROXY_ENABLED by lazy {
            BooleanPref(preferences, Key.IS_PROXY_ENABLED, false)
        }
        val PROXY_SCHEME by lazy {
            EnumPref(preferences, Key.PROXY_SCHEME, Proxy.Type.HTTP, Proxy.Type::entries)
        }
        val PROXY_HOST by lazy {
            StringPref(preferences, Key.PROXY_HOST, "")
        }
        //</editor-fold>
        //<editor-fold desc="Custom light colors">
        val CUSTOM_LIGHT_THEME_BACKGROUND_0 by lazy {
            ColorPref(preferences, Key.CUSTOM_LIGHT_THEME_BACKGROUND_0, DefaultLightColorPalette.background0)
        }
        val CUSTOM_LIGHT_THEME_BACKGROUND_1 by lazy {
            ColorPref(preferences, Key.CUSTOM_LIGHT_THEME_BACKGROUND_1, DefaultLightColorPalette.background1)
        }
        val CUSTOM_LIGHT_THEME_BACKGROUND_2 by lazy {
            ColorPref(preferences, Key.CUSTOM_LIGHT_THEME_BACKGROUND_2, DefaultLightColorPalette.background2)
        }
        val CUSTOM_LIGHT_THEME_BACKGROUND_3 by lazy {
            ColorPref(preferences, Key.CUSTOM_LIGHT_THEME_BACKGROUND_3, DefaultLightColorPalette.background3)
        }
        val CUSTOM_LIGHT_THEME_BACKGROUND_4 by lazy {
            ColorPref(preferences, Key.CUSTOM_LIGHT_THEME_BACKGROUND_4, DefaultLightColorPalette.background4)
        }
        val CUSTOM_LIGHT_TEXT by lazy {
            ColorPref(preferences, Key.CUSTOM_LIGHT_TEXT, DefaultLightColorPalette.text)
        }
        val CUSTOM_LIGHT_TEXT_SECONDARY by lazy {
            ColorPref(preferences, Key.CUSTOM_LIGHT_TEXT_SECONDARY, DefaultLightColorPalette.textSecondary)
        }
        val CUSTOM_LIGHT_TEXT_DISABLED by lazy {
            ColorPref(preferences, Key.CUSTOM_LIGHT_TEXT_DISABLED, DefaultLightColorPalette.textDisabled)
        }
        val CUSTOM_LIGHT_PLAY_BUTTON by lazy {
            ColorPref(preferences, Key.CUSTOM_LIGHT_PLAY_BUTTON, DefaultLightColorPalette.iconButtonPlayer)
        }
        val CUSTOM_LIGHT_ACCENT by lazy {
            ColorPref(preferences, Key.CUSTOM_LIGHT_ACCENT, DefaultLightColorPalette.accent)
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Custom dark theme">
        val CUSTOM_DARK_THEME_BACKGROUND_0 by lazy {
            ColorPref(preferences, Key.CUSTOM_DARK_THEME_BACKGROUND_0, DefaultDarkColorPalette.background0)
        }
        val CUSTOM_DARK_THEME_BACKGROUND_1 by lazy {
            ColorPref(preferences, Key.CUSTOM_DARK_THEME_BACKGROUND_1, DefaultDarkColorPalette.background1)
        }
        val CUSTOM_DARK_THEME_BACKGROUND_2 by lazy {
            ColorPref(preferences, Key.CUSTOM_DARK_THEME_BACKGROUND_2, DefaultDarkColorPalette.background2)
        }
        val CUSTOM_DARK_THEME_BACKGROUND_3 by lazy {
            ColorPref(preferences, Key.CUSTOM_DARK_THEME_BACKGROUND_3, DefaultDarkColorPalette.background3)
        }
        val CUSTOM_DARK_THEME_BACKGROUND_4 by lazy {
            ColorPref(preferences, Key.CUSTOM_DARK_THEME_BACKGROUND_4, DefaultDarkColorPalette.background4)
        }
        val CUSTOM_DARK_TEXT by lazy {
            ColorPref(preferences, Key.CUSTOM_DARK_TEXT, DefaultDarkColorPalette.text)
        }
        val CUSTOM_DARK_TEXT_SECONDARY by lazy {
            ColorPref(preferences, Key.CUSTOM_DARK_TEXT_SECONDARY, DefaultDarkColorPalette.textSecondary)
        }
        val CUSTOM_DARK_TEXT_DISABLED by lazy {
            ColorPref(preferences, Key.CUSTOM_DARK_TEXT_DISABLED, DefaultDarkColorPalette.textDisabled)
        }
        val CUSTOM_DARK_PLAY_BUTTON by lazy {
            ColorPref(preferences, Key.CUSTOM_DARK_PLAY_BUTTON, DefaultDarkColorPalette.iconButtonPlayer)
        }
        val CUSTOM_DARK_ACCENT by lazy {
            ColorPref(preferences, Key.CUSTOM_DARK_ACCENT, DefaultDarkColorPalette.accent)
        }
        //</editor-fold>
        //<editor-fold desc="Logging">
        val RUNTIME_LOG by lazy {
            BooleanPref(preferences, Key.RUNTIME_LOG, false)
        }
        val RUNTIME_LOG_SHARED by lazy {
            BooleanPref(preferences, Key.RUNTIME_LOG_SHARED, true)
        }
        val RUNTIME_LOG_SEVERITY by lazy {
            EnumPref(preferences, Key.RUNTIME_LOG_SEVERITY, Severity.Info, Severity::entries)
        }
        //</editor-fold>
        //<editor-fold desc="Platform indicator">
        val ALBUMS_PLATFORM_INDICATOR by lazy {
            EnumPref(preferences, Key.ALBUMS_PLATFORM_INDICATOR, PlatformIndicatorType.ICON, PlatformIndicatorType::entries)
        }
        val ARTISTS_PLATFORM_INDICATOR by lazy {
            EnumPref(preferences, Key.ARTISTS_PLATFORM_INDICATOR, PlatformIndicatorType.ICON, PlatformIndicatorType::entries)
        }
        val PLAYLISTS_PLATFORM_INDICATOR by lazy {
            EnumPref(preferences, Key.PLAYLISTS_PLATFORM_INDICATOR, PlatformIndicatorType.ICON, PlatformIndicatorType::entries)
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
        val DOH_SERVER by lazy {
            EnumPref(preferences, Key.DOH_SERVER, DohServer.NONE, DohServer::entries)
        }
        val HOME_SONGS_TOP_PLAYLIST_PERIOD by lazy {
            EnumPref(preferences, Key.HOME_SONGS_TOP_PLAYLIST_PERIOD, StatisticsType.All, StatisticsType::entries)
        }
        val MENU_STYLE by lazy {
            EnumPref(preferences, Key.MENU_STYLE, MenuStyle.List, MenuStyle::entries)
        }
        val MAIN_THEME by lazy {
            EnumPref(preferences, Key.MAIN_THEME, UiType.RiMusic, UiType::entries)
        }
        val COLOR_PALETTE by lazy {
            EnumPref(preferences, Key.COLOR_PALETTE, ColorPaletteName.Dynamic, ColorPaletteName::entries)
        }
        val THEME_MODE by lazy {
            EnumPref(preferences, Key.THEME_MODE, ColorPaletteMode.Dark, ColorPaletteMode::entries)
        }
        val STARTUP_SCREEN by lazy {
            EnumPref(preferences, Key.STARTUP_SCREEN, HomeScreenTabs.Songs, HomeScreenTabs::entries)
        }
        val FONT by lazy {
            EnumPref(preferences, Key.FONT, FontType.Rubik, FontType::entries)
        }
        val NAVIGATION_BAR_POSITION by lazy {
            EnumPref(preferences, Key.NAVIGATION_BAR_POSITION, NavigationBarPosition.Bottom, NavigationBarPosition::entries)
        }
        val NAVIGATION_BAR_TYPE by lazy {
            EnumPref(preferences, Key.NAVIGATION_BAR_TYPE, NavigationBarType.IconAndText, NavigationBarType::entries)
        }
        val PAUSE_BETWEEN_SONGS by lazy {
            EnumPref(preferences, Key.PAUSE_BETWEEN_SONGS, PauseBetweenSongs.`0`, PauseBetweenSongs::entries)
        }
        val THUMBNAIL_BORDER_RADIUS by lazy {
            EnumPref(preferences, Key.THUMBNAIL_BORDER_RADIUS, ThumbnailRoundness.Heavy, ThumbnailRoundness::entries)
        }
        val TRANSITION_EFFECT by lazy {
            EnumPref(preferences, Key.TRANSITION_EFFECT, TransitionEffect.Scale, TransitionEffect::entries)
        }
        val LIMIT_SONGS_WITH_DURATION by lazy {
            EnumPref(preferences, Key.LIMIT_SONGS_WITH_DURATION, DurationInMinutes.Disabled, DurationInMinutes::entries)
        }
        val QUEUE_TYPE by lazy {
            EnumPref(preferences, Key.QUEUE_TYPE, Type.LEGACY, Type::entries)
        }
        val CAROUSEL_SIZE by lazy {
            EnumPref(preferences, Key.CAROUSEL_SIZE, CarouselSize.Biggest, CarouselSize::entries)
        }
        val THUMBNAIL_TYPE by lazy {
            EnumPref(preferences, Key.THUMBNAIL_TYPE, Type.MODERN, Type::entries)
        }
        val LIKE_ICON by lazy {
            EnumPref(preferences, Key.LIKE_ICON, IconLikeType.Essential, IconLikeType::entries)
        }
        val LIVE_WALLPAPER by lazy {
            EnumPref(preferences, Key.LIVE_WALLPAPER, WallpaperType.DISABLED, WallpaperType::entries)
        }
        val ANIMATED_GRADIENT by lazy {
            EnumPref(preferences, Key.ANIMATED_GRADIENT, AnimatedGradient.Linear, AnimatedGradient::entries)
        }
        val NOW_PLAYING_INDICATOR by lazy {
            EnumPref(preferences, Key.NOW_PLAYING_INDICATOR, MusicAnimationType.Bubbles, MusicAnimationType::entries)
        }
        val PIP_MODULE by lazy {
            EnumPref(preferences, Key.PIP_MODULE, PipModule.Cover, PipModule::entries)
        }
        val CHECK_UPDATE by lazy {
            EnumPref(preferences, Key.CHECK_UPDATE, CheckUpdateState.DISABLED, CheckUpdateState::entries)
        }
        val APP_LANGUAGE by lazy {
            EnumPref(preferences, Key.APP_LANGUAGE, Language.SYSTEM, Language::entries)
        }
        val OTHER_APP_LANGUAGE by lazy {
            EnumPref(preferences, Key.OTHER_APP_LANGUAGE, Language.SYSTEM, Language::entries)
        }
        val HOME_ARTIST_AND_ALBUM_FILTER by lazy {
            EnumPref(preferences, Key.HOME_ARTIST_AND_ALBUM_FILTER, FilterBy.All, FilterBy::entries)
        }
        val STATISTIC_PAGE_CATEGORY by lazy {
            EnumPref(preferences, Key.STATISTIC_PAGE_CATEGORY, StatisticsCategory.Songs, StatisticsCategory::entries)
        }
        val CUSTOM_COLOR by lazy {
            ColorPref(preferences, Key.CUSTOM_COLOR, Color.Green)
        }
        val LOCAL_SONGS_FOLDER by lazy {
            StringPref(preferences, Key.LOCAL_SONGS_FOLDER, "/")
        }
        val SEEN_CHANGELOGS_VERSION by lazy {
            StringPref(preferences, Key.SEEN_CHANGELOGS_VERSION, "")
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

    class EnumPref<E: Enum<E>>(
        storage: Storage,
        key: Preferences.Key,
        defaultValue: E,
        val entries: () -> EnumEntries<E>
    ) : Preferences<String, E>(storage, stringPreferencesKey(key.value), defaultValue) {

        override fun deserialize( key: String ): E? = entries().firstOrNull { it.name == key }

        override fun serialize( value: E ): String = value.name
    }

    // [Color] is just value class of ULong. Similarly to Enum, we can store
    // this value as its primitive value - Long, and use simple reverse lookup
    // to get the value back flawlessly
    class ColorPref(
        storage: Storage,
        key: Preferences.Key,
        defaultValue: Color
    ) : Preferences<Long, Color>(storage, longPreferencesKey(key.value), defaultValue) {

        override fun deserialize( key: Long ): Color = Color(key.toULong())

        override fun serialize( value: Color ): Long = value.value.toLong()
    }

    class StringPref(
        storage: Storage,
        key: Preferences.Key,
        defaultValue: String
    ) : Preferences<String, String>(storage, stringPreferencesKey(key.value), defaultValue) {

        override fun deserialize( key: String ): String = key

        override fun serialize( value: String ): String = value
    }

    class Key private constructor(val value: String) {
        companion object {
            //<editor-fold desc="Item size">
            val HOME_ARTIST_ITEM_SIZE = Key("home_artist_item_size")
            val HOME_ALBUM_ITEM_SIZE = Key("home_album_item_size")
            val HOME_LIBRARY_ITEM_SIZE = Key("home_library_item_size")
            //</editor-fold>
            //<editor-fold desc="Sort by">
            val HOME_SONGS_SORT_BY = Key("home_songs_sort_by")
            val HOME_ON_DEVICE_SONGS_SORT_BY = Key("home_on_device_songs_sort_by")
            val HOME_ARTISTS_SORT_BY = Key("home_artists_sort_by")
            val HOME_ALBUMS_SORT_BY = Key("home_albums_sort_by")
            val HOME_LIBRARY_SORT_BY = Key("home_library_sort_by")
            val PLAYLIST_SONGS_SORT_BY = Key("playlist_songs_sort_by")
            //</editor-fold>
            //<editor-fold desc="Sort order">
            val HOME_SONGS_SORT_ORDER = Key("home_songs_sort_order")
            val HOME_ARTISTS_SORT_ORDER = Key("home_artists_sort_order")
            val HOME_ALBUM_SORT_ORDER = Key("home_album_sort_order")
            val HOME_LIBRARY_SORT_ORDER = Key("home_library_sort_order")
            val PLAYLIST_SONGS_SORT_ORDER = Key("playlist_songs_sort_order")
            //</editor-fold>
            //<editor-fold desc="Max # of ...">
            val MAX_NUMBER_OF_SMART_RECOMMENDATIONS = Key("max_number_of_smart_recommendations")
            val MAX_NUMBER_OF_STATISTIC_ITEMS = Key("max_number_of_statistic_items")
            val MAX_NUMBER_OF_TOP_PLAYED = Key("max_number_of_top_played")
            val MAX_NUMBER_OF_SONG_IN_QUEUE = Key("max_number_of_song_in_queue")
            val MAX_NUMBER_OF_NEXT_IN_QUEUE = Key("max_number_of_next_in_queue")
            //</editor-fold>
            //<editor-fold desc="Swipe action">
            val ENABLE_SWIPE_ACTION = Key("enable_swipe_action")
            val QUEUE_SWIPE_LEFT_ACTION = Key("queue_swipe_left_action")
            val QUEUE_SWIPE_RIGHT_ACTION = Key("queue_swipe_right_action")
            val PLAYLIST_SWIPE_LEFT_ACTION = Key("playlist_swipe_left_action")
            val PLAYLIST_SWIPE_RIGHT_ACTION = Key("playlist_swipe_right_action")
            val ALBUM_SWIPE_LEFT_ACTION = Key("album_swipe_left_action")
            val ALBUM_SWIPE_RIGHT_ACTION = Key("album_swipe_right_action")
            //</editor-fold>
            //<editor-fold desc="Mini player">
            val MINI_DISABLE_SWIPE_DOWN_TO_DISMISS = Key("mini_disable_swipe_down_to_dismiss")
            val MINI_PLAYER_POSITION = Key("mini_player_position")
            val MINI_PLAYER_TYPE = Key("mini_player_type")
            val MINI_PLAYER_PROGRESS_BAR = Key("mini_player_progress_bar")
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
            val PLAYER_CONTROLS_TYPE = Key("player_controls_type")
            val PLAYER_INFO_TYPE = Key("player_info_type")
            val PLAYER_TYPE = Key("player_type")
            val PLAYER_TIMELINE_TYPE = Key("player_timeline_type")
            val PLAYER_PORTRAIT_THUMBNAIL_SIZE = Key("player_portrait_thumbnail_size")
            val PLAYER_LANDSCAPE_THUMBNAIL_SIZE = Key("player_landscape_thumbnail_size")
            val PLAYER_TIMELINE_SIZE = Key("player_timeline_size")
            val PLAYER_PLAY_BUTTON_TYPE = Key("player_play_button_type")
            val PLAYER_BACKGROUND = Key("player_background")
            val PLAYER_THUMBNAIL_TYPE = Key("player_thumbnail_type")
            val PLAYER_NO_THUMBNAIL_SWIPE_ANIMATION = Key("player_no_thumbnail_swipe_animation")
            //</editor-fold>
            //<editor-fold desc="Cache">
            val EXO_CACHE_LOCATION = Key("exo_cache_location")
            //</editor-fold>
            //<editor-fold desc="Notification">
            val MEDIA_NOTIFICATION_FIRST_ICON = Key("media_notification_first_icon")
            val MEDIA_NOTIFICATION_SECOND_ICON = Key("media_notification_second_icon")
            //</editor-fold>
            //<editor-fold desc="Lyrics">
            val LYRICS_SHOW_THUMBNAIL = Key("lyrics_show_thumbnail")
            val LYRICS_JUMP_ON_TAP = Key("lyrics_jump_on_tap")
            val LYRICS_SHOW_ACCENT_BACKGROUND = Key("lyrics_show_accent_background")
            val LYRICS_SYNCHRONIZED = Key("lyrics_synchronized")
            val LYRICS_SHOW_SECOND_LINE = Key("lyrics_show_second_line")
            val LYRICS_ANIMATE_SIZE = Key("lyrics_animate_size")
            val LYRICS_LANDSCAPE_CONTROLS = Key("lyrics_landscape_controls")
            val LYRICS_COLOR = Key("lyrics_color")
            val LYRICS_OUTLINE = Key("lyrics_outline")
            val LYRICS_FONT_SIZE = Key("lyrics_font_size")
            val LYRICS_ROMANIZATION_TYPE = Key("lyrics_romanization_type")
            val LYRICS_BACKGROUND = Key("lyrics_background")
            val LYRICS_HIGHLIGHT = Key("lyrics_highlight")
            val LYRICS_ALIGNMENT = Key("lyrics_alignment")
            //</editor-fold>
            //<editor-fold desc="Page type">
            val HOME_ARTIST_TYPE = Key("home_artist_type")
            val HOME_ALBUM_TYPE = Key("home_album_type")
            val HOME_SONGS_TYPE = Key("home_songs_type")
            val HISTORY_PAGE_TYPE = Key("history_page_type")
            val HOME_LIBRARY_TYPE = Key("home_library_type")
            //</editor-fold>
            //<editor-fold desc="Audio">
            val AUDIO_SKIP_SILENCE = Key("audio_skip_silence")
            val AUDIO_VOLUME_NORMALIZATION = Key("audio_volume_normalization")
            val AUDIO_SHAKE_TO_SKIP = Key("audio_shake_to_skip")
            val AUDIO_VOLUME_BUTTONS_CHANGE_SONG = Key("audio_volume_buttons_change_song")
            val AUDIO_BASS_BOOSTED = Key("audio_bass_boosted")
            val AUDIO_SMART_PAUSE_DURING_CALLS = Key("audio_smart_pause_during_calls")
            val AUDIO_SPEED = Key("audio_speed")
            val AUDIO_FADE_DURATION = Key("audio_fade_duration")
            val AUDIO_QUALITY = Key("audio_quality")
            //</editor-fold>
            //<editor-fold desc="YouTube">
            val YOUTUBE_LOGIN = Key("youtube_login")
            val YOUTUBE_PLAYLISTS_SYNC = Key("youtube_playlists_sync")
            val YOUTUBE_ARTISTS_SYNC = Key("youtube_artists_sync")
            val YOUTUBE_ALBUMS_SYNC = Key("youtube_albums_sync")
            val YOUTUBE_VISITOR_DATA = Key("youtube_visitor_data")
            val YOUTUBE_SYNC_ID = Key("youtube_sync_id")
            val YOUTUBE_COOKIES = Key("youtube_cookies")
            val YOUTUBE_ACCOUNT_NAME = Key("youtube_account_name")
            val YOUTUBE_ACCOUNT_EMAIL = Key("youtube_account_email")
            val YOUTUBE_SELF_CHANNEL_HANDLE = Key("youtube_self_channel_handle")
            val YOUTUBE_ACCOUNT_AVATAR = Key("youtube_account_avatar")
            val YOUTUBE_LAST_VIDEO_ID = Key("youtube_last_video_id")
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
            val QUICK_PICKS_TYPE = Key("quick_picks_type")
            val QUICK_PICKS_MIN_DURATION = Key("quick_picks_min_duration")
            //</editor-fold>
            //<editor-fold desc="Discord">
            val DISCORD_LOGIN = Key("discord_login")
            val DISCORD_ACCESS_TOKEN = Key("discord_access_token")
            //</editor-fold>
            //<editor-fold desc="Proxy">
            val IS_PROXY_ENABLED = Key("is_proxy_enabled")
            val PROXY_SCHEME = Key("proxy_scheme")
            val PROXY_HOST = Key("proxy_host")
            //</editor-fold>
            //<editor-fold desc="Custom light colors">
            val CUSTOM_LIGHT_THEME_BACKGROUND_0 = Key("custom_light_theme_background_0")
            val CUSTOM_LIGHT_THEME_BACKGROUND_1 = Key("custom_light_theme_background_1")
            val CUSTOM_LIGHT_THEME_BACKGROUND_2 = Key("custom_light_theme_background_2")
            val CUSTOM_LIGHT_THEME_BACKGROUND_3 = Key("custom_light_theme_background_3")
            val CUSTOM_LIGHT_THEME_BACKGROUND_4 = Key("custom_light_theme_background_4")
            val CUSTOM_LIGHT_TEXT = Key("custom_light_text")
            val CUSTOM_LIGHT_TEXT_SECONDARY = Key("custom_light_text_secondary")
            val CUSTOM_LIGHT_TEXT_DISABLED = Key("custom_light_text_disabled")
            val CUSTOM_LIGHT_PLAY_BUTTON = Key("custom_light_play_button")
            val CUSTOM_LIGHT_ACCENT = Key("custom_light_accent")
            //</editor-fold>
            //<editor-fold defaultstate="collapsed" desc="Custom dark theme">
            val CUSTOM_DARK_THEME_BACKGROUND_0 = Key("custom_dark_theme_background_0")
            val CUSTOM_DARK_THEME_BACKGROUND_1 = Key("custom_dark_theme_background_1")
            val CUSTOM_DARK_THEME_BACKGROUND_2 = Key("custom_dark_theme_background_2")
            val CUSTOM_DARK_THEME_BACKGROUND_3 = Key("custom_dark_theme_background_3")
            val CUSTOM_DARK_THEME_BACKGROUND_4 = Key("custom_dark_theme_background_4")
            val CUSTOM_DARK_TEXT = Key("custom_dark_text")
            val CUSTOM_DARK_TEXT_SECONDARY = Key("custom_dark_text_secondary")
            val CUSTOM_DARK_TEXT_DISABLED = Key("custom_dark_text_disabled")
            val CUSTOM_DARK_PLAY_BUTTON = Key("custom_dark_play_button")
            val CUSTOM_DARK_ACCENT = Key("custom_dark_accent")
            //</editor-fold>
            //<editor-fold desc="Logging">
            val RUNTIME_LOG = Key("runtime_log")
            val RUNTIME_LOG_SHARED = Key("runtime_log_shared")
            val RUNTIME_LOG_SEVERITY = Key("runtime_log_severity")
            //</editor-fold>
            //<editor-fold desc="Platform indicator">
            val ALBUMS_PLATFORM_INDICATOR = Key("albums_platform_indicator")
            val ARTISTS_PLATFORM_INDICATOR = Key("artists_platform_indicator")
            val PLAYLISTS_PLATFORM_INDICATOR = Key("playlists_platform_indicator")
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
            val DOH_SERVER = Key("doh_server")
            val HOME_SONGS_TOP_PLAYLIST_PERIOD = Key("home_songs_top_playlist_period")
            val MENU_STYLE = Key("menu_style")
            val MAIN_THEME = Key("main_theme")
            val COLOR_PALETTE = Key("color_palette")
            val THEME_MODE = Key("theme_mode")
            val STARTUP_SCREEN = Key("startup_screen")
            val FONT = Key("font")
            val NAVIGATION_BAR_POSITION = Key("navigation_bar_position")
            val NAVIGATION_BAR_TYPE = Key("navigation_bar_type")
            val PAUSE_BETWEEN_SONGS = Key("pause_between_songs")
            val THUMBNAIL_BORDER_RADIUS = Key("thumbnail_border_radius")
            val TRANSITION_EFFECT = Key("transition_effect")
            val LIMIT_SONGS_WITH_DURATION = Key("limit_songs_with_duration")
            val QUEUE_TYPE = Key("queue_type")
            val QUEUE_LOOP_TYPE = Key("queue_loop_type")
            val CAROUSEL_SIZE = Key("carousel_size")
            val THUMBNAIL_TYPE = Key("thumbnail_type")
            val LIKE_ICON = Key("like_icon")
            val LIVE_WALLPAPER = Key("live_wallpaper")
            val ANIMATED_GRADIENT = Key("animated_gradient")
            val NOW_PLAYING_INDICATOR = Key("now_playing_indicator")
            val PIP_MODULE = Key("pip_module")
            val CHECK_UPDATE = Key("check_update")
            val APP_LANGUAGE = Key("app_language")
            val OTHER_APP_LANGUAGE = Key("other_app_language")
            val HOME_ARTIST_AND_ALBUM_FILTER = Key("home_artist_and_album_filter")
            val STATISTIC_PAGE_CATEGORY = Key("statistic_page_category")
            val CUSTOM_COLOR = Key("custom_color")
            val APP_REGION = Key("app_region")
            val LOCAL_SONGS_FOLDER = Key("local_songs_folder")
            val SEEN_CHANGELOGS_VERSION = Key("seen_changelogs_version")
        }
    }
}