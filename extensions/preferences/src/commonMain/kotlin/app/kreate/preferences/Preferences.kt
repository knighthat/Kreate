package app.kreate.preferences

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
import app.kreate.di.InternalPrefKey
import app.kreate.di.InternalPreferences
import app.kreate.di.PrefType
import app.kreate.di.Storage
import app.kreate.util.getSystemCountryCode
import app.kreate.util.priomap.Priority
import app.kreate.util.priomap.PriorityKey
import app.kreate.util.priomap.createPrioritySortedMap
import co.touchlab.kermit.Logger
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
import it.fast4x.rimusic.enums.ExoPlayerCacheLocation
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
import it.fast4x.rimusic.enums.MenuStyle
import it.fast4x.rimusic.enums.MusicAnimationType
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.NavigationBarType
import it.fast4x.rimusic.enums.NotificationButtons
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
import it.fast4x.rimusic.enums.Romanization
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
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.net.Proxy
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.enums.EnumEntries
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import androidx.datastore.preferences.core.Preferences.Key as DatastoreKey


@OptIn(ExperimentalForInheritanceCoroutinesApi::class, ExperimentalAtomicApi::class)
sealed class Preferences<K, V>(
    private val storage: PrefHelper,
    protected val key: DatastoreKey<K>,
    val defaultValue: V
) : StateFlow<V> {

    companion object : KoinComponent {

        internal val preferences = PrefHelper(get(PrefType.DEFAULT))
        internal val credentials = PrefHelper(get(PrefType.CREDENTIALS))
        //<editor-fold desc="Priority-sorted map">
        internal val listeners = createPrioritySortedMap<Listener>()
        internal val sequenceCounter = AtomicLong(0)
        internal val mutex = Mutex()
        //</editor-fold>

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
        val SONG_THUMBNAIL_SIZE by lazy {
            IntPref(preferences, Key.SONG_THUMBNAIL_SIZE, 54)
        }
        val ALBUM_THUMBNAIL_SIZE by lazy {
            IntPref(preferences, Key.ALBUM_THUMBNAIL_SIZE, 128)
        }
        val ARTIST_THUMBNAIL_SIZE by lazy {
            IntPref(preferences, Key.ARTIST_THUMBNAIL_SIZE, 128)
        }
        val PLAYLIST_THUMBNAIL_SIZE by lazy {
            IntPref(preferences, Key.PLAYLIST_THUMBNAIL_SIZE, 128)
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
            IntPref(preferences, Key.MAX_NUMBER_OF_SMART_RECOMMENDATIONS, 5, IntRange(5, 20))
        }
        val MAX_NUMBER_OF_STATISTIC_ITEMS by lazy {
            IntPref(preferences, Key.MAX_NUMBER_OF_STATISTIC_ITEMS, 10, IntRange(10, 50))
        }
        val MAX_NUMBER_OF_TOP_PLAYED by lazy {
            IntPref(preferences, Key.MAX_NUMBER_OF_TOP_PLAYED, 10, IntRange(10, 200))
        }
        val MAX_NUMBER_OF_SONG_IN_QUEUE by lazy {
            IntPref(preferences, Key.MAX_NUMBER_OF_SONG_IN_QUEUE, 3000, IntRange(50, 3000))
        }
        val MAX_NUMBER_OF_NEXT_IN_QUEUE by lazy {
            IntPref(preferences, Key.MAX_NUMBER_OF_NEXT_IN_QUEUE, 2, IntRange(0, 9))
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
        val PLAYER_THUMBNAIL_VINYL_SIZE by lazy {
            FloatPref(preferences, Key.PLAYER_THUMBNAIL_VINYL_SIZE, 50F)
        }
        val PLAYER_THUMBNAIL_FADE by lazy {
            FloatPref(preferences, Key.PLAYER_THUMBNAIL_FADE, 5F)
        }
        val PLAYER_THUMBNAIL_FADE_EX by lazy {
            FloatPref(preferences, Key.PLAYER_THUMBNAIL_FADE_EX, 5F)
        }
        val PLAYER_THUMBNAIL_SPACING by lazy {
            FloatPref(preferences, Key.PLAYER_THUMBNAIL_SPACING, 0F)
        }
        val PLAYER_THUMBNAIL_SPACING_LANDSCAPE by lazy {
            FloatPref(preferences, Key.PLAYER_THUMBNAIL_SPACING_LANDSCAPE, 0F)
        }
        val PLAYER_BACKGROUND_BLUR_STRENGTH by lazy {
            FloatPref(preferences, Key.PLAYER_BACKGROUND_BLUR_STRENGTH, 25F)
        }
        val PLAYER_BACKGROUND_BACK_DROP by lazy {
            FloatPref(preferences, Key.PLAYER_BACKGROUND_BACK_DROP, 0F)
        }
        val PLAYER_CURRENT_VISUALIZER  by lazy {
            IntPref(preferences, Key.PLAYER_CURRENT_VISUALIZER , 0)
        }
        //</editor-fold>
        //<editor-fold desc="Cache">
        val EXO_CACHE_LOCATION by lazy {
            EnumPref(preferences, Key.EXO_CACHE_LOCATION, ExoPlayerCacheLocation.SPLIT, ExoPlayerCacheLocation::entries)
        }
        val IMAGE_CACHE_SIZE by lazy {
            LongPref(preferences, Key.IMAGE_CACHE_SIZE, Long.MAX_VALUE)
        }
        val EXO_CACHE_SIZE by lazy {
            LongPref(preferences, Key.EXO_CACHE_SIZE, Long.MAX_VALUE)
        }
        val EXO_DOWNLOAD_SIZE by lazy {
            LongPref(preferences, Key.EXO_DOWNLOAD_SIZE, Long.MAX_VALUE)
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
        val LYRICS_SIZE by lazy {
            FloatPref(preferences, Key.LYRICS_SIZE, 5F)
        }
        val LYRICS_SIZE_LANDSCAPE by lazy {
            FloatPref(preferences, Key.LYRICS_SIZE_LANDSCAPE, 5F)
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
        val AUDIO_QUALITY by lazy {
            EnumPref(preferences, Key.AUDIO_QUALITY, AudioQualityFormat.Auto, AudioQualityFormat::entries)
        }
        val AUDIO_VOLUME_NORMALIZATION_TARGET by lazy {
            FloatPref(preferences, Key.AUDIO_VOLUME_NORMALIZATION_TARGET, 5F)
        }
        val AUDIO_BASS_BOOST_LEVEL by lazy {
            FloatPref(preferences, Key.AUDIO_BASS_BOOST_LEVEL, .5F)
        }
        val AUDIO_SPEED_VALUE by lazy {
            FloatPref(preferences, Key.AUDIO_SPEED_VALUE, 1F)
        }
        val AUDIO_PITCH by lazy {
            FloatPref(preferences, Key.AUDIO_PITCH, 1F)
        }
        val AUDIO_VOLUME by lazy {
            FloatPref(preferences, Key.AUDIO_VOLUME, .5F)
        }
        val AUDIO_DEVICE_VOLUME by lazy {
            FloatPref(preferences, Key.AUDIO_DEVICE_VOLUME, .5f)
        }
        val AUDIO_MEDLEY_DURATION by lazy {
            FloatPref(preferences, Key.AUDIO_MEDLEY_DURATION, 0F)
        }
        val AUDIO_REVERB_PRESET by lazy {
            IntPref(preferences, Key.AUDIO_REVERB_PRESET, 0)
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
        val YOUTUBE_LAST_VIDEO_SECONDS by lazy {
            FloatPref(preferences, Key.YOUTUBE_LAST_VIDEO_SECONDS, 0F)
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
            IntPref(preferences, Key.QUICK_PICKS_MIN_DURATION, 20, IntRange(0, 60))
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
        val PROXY_PORT  by lazy {
            IntPref(preferences, Key.PROXY_PORT , 1080)
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
         val RUNTIME_LOG_LEVEL by lazy {
            IntPref(preferences, Key.RUNTIME_LOG_LEVEL, 4)
        }
        val RUNTIME_LOG_FILE_COUNT by lazy {
            IntPref(preferences, Key.RUNTIME_LOG_FILE_COUNT, 5, IntRange(1, 10))
        }
        val RUNTIME_LOG_MAX_SIZE_PER_FILE by lazy {
            LongPref(preferences, Key.RUNTIME_LOG_MAX_SIZE_PER_FILE, 5L * 1024 * 1024)   // 5 Mb
        }
        //</editor-fold>
        //<editor-fold desc="Thumbnail roundness">
        val SONG_THUMBNAIL_ROUNDNESS_PERCENT by lazy {
            IntPref(preferences, Key.SONG_THUMBNAIL_ROUNDNESS_PERCENT, 0)
        }
        val ALBUM_THUMBNAIL_ROUNDNESS_PERCENT by lazy {
            IntPref(preferences, Key.ALBUM_THUMBNAIL_ROUNDNESS_PERCENT, 10)
        }
        val ARTIST_THUMBNAIL_ROUNDNESS_PERCENT by lazy {
            IntPref(preferences, Key.ARTIST_THUMBNAIL_ROUNDNESS_PERCENT, 50)
        }
        val PLAYLIST_THUMBNAIL_ROUNDNESS_PERCENT by lazy {
            IntPref(preferences, Key.PLAYLIST_THUMBNAIL_ROUNDNESS_PERCENT, 10)
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
        val PAUSE_SEARCH_HISTORY by lazy {
            BooleanPref(preferences, Key.PAUSE_SEARCH_HISTORY, false)
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
        val THUMBNAIL_BORDER_RADIUS by lazy {
            EnumPref(preferences, Key.THUMBNAIL_BORDER_RADIUS, ThumbnailRoundness.Heavy, ThumbnailRoundness::entries)
        }
        val TRANSITION_EFFECT by lazy {
            EnumPref(preferences, Key.TRANSITION_EFFECT, TransitionEffect.Scale, TransitionEffect::entries)
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
        val APP_REGION by lazy {
            StringPref(preferences, Key.APP_REGION,  getSystemCountryCode())
        }
        val FLOATING_ICON_X_OFFSET by lazy {
            FloatPref(preferences, Key.FLOATING_ICON_X_OFFSET, 0F)
        }
        val FLOATING_ICON_Y_OFFSET by lazy {
            FloatPref(preferences, Key.FLOATING_ICON_Y_OFFSET, 0F)
        }
        val MULTI_FLOATING_ICON_X_OFFSET by lazy {
            FloatPref(preferences, Key.MULTI_FLOATING_ICON_X_OFFSET, 0F)
        }
        val MULTI_FLOATING_ICON_Y_OFFSET by lazy {
            FloatPref(preferences, Key.MULTI_FLOATING_ICON_Y_OFFSET, 0F)
        }
        val SMART_REWIND by lazy {
            IntPref(preferences, Key.SMART_REWIND, 3, IntRange(0, 60))
        }
        val SEARCH_RESULTS_TAB_INDEX by lazy {
            IntPref(preferences, Key.SEARCH_RESULTS_TAB_INDEX, 0)
        }
        val HOME_TAB_INDEX by lazy {
            IntPref(preferences, Key.HOME_TAB_INDEX, 0)
        }
        val ARTIST_SCREEN_TAB_INDEX  by lazy {
            IntPref(preferences, Key.ARTIST_SCREEN_TAB_INDEX , 0)
        }
        val LIVE_WALLPAPER_RESET_DURATION by lazy {
            LongPref(preferences, Key.LIVE_WALLPAPER_RESET_DURATION, -1L)
        }
        val BLACKLISTED_FOLDERS by lazy {
            StringSetPref(preferences, Key.BLACKLISTED_FOLDERS, emptySet())
        }
        val PAUSE_BETWEEN_SONGS by lazy {
            DurationPref(preferences, Key.PAUSE_BETWEEN_SONGS, Duration.ZERO)
        }
        val LIMIT_SONGS_WITH_DURATION by lazy {
            DurationPref(preferences, Key.LIMIT_SONGS_WITH_DURATION, Duration.ZERO)
        }
        val AUDIO_FADE_DURATION by lazy {
            DurationPref(preferences, Key.AUDIO_FADE_DURATION, Duration.ZERO, LongRange(0, 5000))
        }

        /**
         * Inserts a value into the map tied to a specific priority in a thread-safe manner.
         *
         * This function secures a mutual exclusion lock before mutating the collection, ensuring
         * that concurrent calls from multiple coroutines or background threads do not cause race
         * conditions or structural corruption.
         *
         * **Order Preservation:** The item is guaranteed to be appended as the last element of its matching [Priority] group.
         * **Concurrency:** Safe to call while other threads or coroutines are actively reading or writing.
         *
         * @param listener The actual data payload to store.
         * @param priority The importance level determining which section the value belongs to.
         */
        suspend fun addListener( listener: Listener, priority: Priority = Priority.NORMAL ) =
            mutex.withLock {
                val sequence = sequenceCounter.incrementAndFetch()
                val key = PriorityKey(priority, sequence)
                listeners[key] = listener
            }

        /**
         * Removes first entry from the map that match the specified value in a thread-safe manner.
         *
         * This function secures a mutual exclusion lock before mutating the collection.
         * It performs a linear scan over the entry set to find and purge matching values.
         *
         * **Concurrency:** Safe to call while other threads or coroutines are actively reading or writing.
         *
         * @param listener The value payload to search for and delete.
         */
        suspend fun removeListener( listener: Listener ) =
            mutex.withLock {
                // Apply reverse lookup to get the key (if exists) and delete entry from the map
                listeners.entries.firstOrNull { it.value == listener }?.key?.also( listeners::remove )
            }
    }

    private val _internalState: StateFlow<V> = storage.state( key, defaultValue, ::deserialize )

    override val value: V get() = storage.get( key, serialize(defaultValue) ).let( ::deserialize )
    override val replayCache: List<V> get() = _internalState.replayCache

    protected abstract fun deserialize( key: K ): V

    protected abstract fun serialize( value: V ): K

    fun reset() = update( defaultValue )

    open fun update( newValue: V ) = storage.asyncWrite( key, serialize(newValue) )

    override suspend fun collect( collector: FlowCollector<V> ): Nothing =
        _internalState.collect( collector )

    class BooleanPref internal constructor(
        storage: PrefHelper,
        key: DatastoreKey<Boolean>,
        defaultValue: Boolean
    ) : Preferences<Boolean, Boolean>(storage, key, defaultValue) {

        fun flip() = update( !value )

        override fun deserialize( key: Boolean ): Boolean = key

        override fun serialize( value: Boolean ): Boolean = value
    }

    class EnumPref<E: Enum<E>> internal constructor(
        storage: PrefHelper,
        key: DatastoreKey<String>,
        defaultValue: E,
        val entries: () -> EnumEntries<E>
    ) : Preferences<String, E>(storage, key, defaultValue) {

        override fun deserialize( key: String ): E = entries().firstOrNull { it.name == key } ?: defaultValue

        override fun serialize( value: E ): String = value.name
    }

    // [Color] is just value class of ULong. Similarly to Enum, we can store
    // this value as its primitive value - Long, and use simple reverse lookup
    // to get the value back flawlessly
    class ColorPref internal constructor(
        storage: PrefHelper,
        key: DatastoreKey<Long>,
        defaultValue: Color
    ) : Preferences<Long, Color>(storage, key, defaultValue) {

        override fun deserialize( key: Long ): Color = Color(key.toULong())

        override fun serialize( value: Color ): Long = value.value.toLong()
    }

    class StringPref internal constructor(
        storage: PrefHelper,
        key: DatastoreKey<String>,
        defaultValue: String
    ) : Preferences<String, String>(storage, key, defaultValue) {

        override fun deserialize( key: String ): String = key

        override fun serialize( value: String ): String = value
    }

    class FloatPref internal constructor(
        storage: PrefHelper,
        key: DatastoreKey<Float>,
        defaultValue: Float
    ) : Preferences<Float, Float>(storage, key, defaultValue) {

        override fun deserialize( key: Float ): Float = key

        override fun serialize( value: Float ): Float = value
    }

    class IntPref internal constructor(
        storage: PrefHelper,
        key: DatastoreKey<Int>,
        defaultValue: Int,
        val range: IntRange = IntRange(Int.MIN_VALUE, Int.MAX_VALUE)
    ) : Preferences<Int, Int>(storage, key, defaultValue) {

        override fun deserialize( key: Int ): Int = key

        override fun serialize( value: Int ): Int = value

        override fun update( newValue: Int ) {
            val newValue = newValue.coerceIn( range )
            super.update( newValue )
        }
    }

    class LongPref internal constructor(
        storage: PrefHelper,
        key: DatastoreKey<Long>,
        defaultValue: Long
    ) : Preferences<Long, Long>(storage, key, defaultValue) {

        override fun deserialize( key: Long ): Long = key

        override fun serialize( value: Long ): Long = value
    }

    class StringSetPref internal constructor(
        storage: PrefHelper,
        key: DatastoreKey<Set<String>>,
        defaultValue: Set<String>
    ) : Preferences<Set<String>, Set<String>>(storage, key, defaultValue) {

        override fun deserialize( key: Set<String> ): Set<String> = key

        override fun serialize( value: Set<String> ): Set<String> = value
    }

    class DurationPref internal constructor(
        storage: PrefHelper,
        key: DatastoreKey<Long>,
        defaultValue: Duration,
        val range: LongRange = LongRange(0, Long.MAX_VALUE)
    ) : Preferences<Long, Duration>(storage, key, defaultValue) {

        override fun deserialize( key: Long ): Duration = key.milliseconds

        override fun serialize( value: Duration ): Long = value.inWholeMilliseconds
    }

    internal class PrefHelper(
        private val storage: Storage
    ) : KoinComponent {

        private val scope: CoroutineScope by inject()
        private val data: StateFlow<InternalPreferences> =
            storage.data.stateIn( scope, SharingStarted.Eagerly, emptyPreferences() )

        fun <T> get( key: InternalPrefKey<T>, default: T ): T = data.value[key] ?: default

        suspend fun <T> write( key: InternalPrefKey<T>, value: T ): Boolean =
            try {
                storage.edit { it[key] = value }
                // Trigger event to all listeners
                listeners.values.forEach { it.onChange(storage, key) }

                true
            } catch( err: IOException ) {
                Logger.e( "Failed to write ${key.name} to disk", err, "PrefHelper" )

                false
            }

        fun <T> asyncWrite( key: InternalPrefKey<T>, value: T ) {
            scope.launch {
                write( key, value )
            }
        }

        fun <K, V> state( key: InternalPrefKey<K>, default: V, transform: (K) -> V ): StateFlow<V> =
            storage.data
                .map { it[key]?.let( transform ) ?: default }
                .stateIn(
                    scope = scope,
                    // Stop emitting changes if no one is subscribing to this
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = default
                )
    }

    fun interface Listener {

        suspend fun onChange( storage: Storage, key: InternalPrefKey<*> )
    }

    object Key {
        //<editor-fold desc="Item size">
        val HOME_ARTIST_ITEM_SIZE = stringPreferencesKey("home_artist_item_size")
        val HOME_ALBUM_ITEM_SIZE = stringPreferencesKey("home_album_item_size")
        val HOME_LIBRARY_ITEM_SIZE = stringPreferencesKey("home_library_item_size")
        val SONG_THUMBNAIL_SIZE = intPreferencesKey("song_thumbnail_size")
        val ALBUM_THUMBNAIL_SIZE = intPreferencesKey("album_thumbnail_size")
        val ARTIST_THUMBNAIL_SIZE = intPreferencesKey("artist_thumbnail_size")
        val PLAYLIST_THUMBNAIL_SIZE = intPreferencesKey("playlist_thumbnail_size")
        //</editor-fold>
        //<editor-fold desc="Sort by">
        val HOME_SONGS_SORT_BY = stringPreferencesKey("home_songs_sort_by")
        val HOME_ON_DEVICE_SONGS_SORT_BY = stringPreferencesKey("home_on_device_songs_sort_by")
        val HOME_ARTISTS_SORT_BY = stringPreferencesKey("home_artists_sort_by")
        val HOME_ALBUMS_SORT_BY = stringPreferencesKey("home_albums_sort_by")
        val HOME_LIBRARY_SORT_BY = stringPreferencesKey("home_library_sort_by")
        val PLAYLIST_SONGS_SORT_BY = stringPreferencesKey("playlist_songs_sort_by")
        //</editor-fold>
        //<editor-fold desc="Sort order">
        val HOME_SONGS_SORT_ORDER = stringPreferencesKey("home_songs_sort_order")
        val HOME_ARTISTS_SORT_ORDER = stringPreferencesKey("home_artists_sort_order")
        val HOME_ALBUM_SORT_ORDER = stringPreferencesKey("home_album_sort_order")
        val HOME_LIBRARY_SORT_ORDER = stringPreferencesKey("home_library_sort_order")
        val PLAYLIST_SONGS_SORT_ORDER = stringPreferencesKey("playlist_songs_sort_order")
        //</editor-fold>
        //<editor-fold desc="Max # of ...">
        val MAX_NUMBER_OF_SMART_RECOMMENDATIONS = intPreferencesKey("max_number_of_smart_recommendations")
        val MAX_NUMBER_OF_STATISTIC_ITEMS = intPreferencesKey("max_number_of_statistic_items")
        val MAX_NUMBER_OF_TOP_PLAYED = intPreferencesKey("max_number_of_top_played")
        val MAX_NUMBER_OF_SONG_IN_QUEUE = intPreferencesKey("max_number_of_song_in_queue")
        val MAX_NUMBER_OF_NEXT_IN_QUEUE = intPreferencesKey("max_number_of_next_in_queue")
        //</editor-fold>
        //<editor-fold desc="Swipe action">
        val ENABLE_SWIPE_ACTION = booleanPreferencesKey("enable_swipe_action")
        val QUEUE_SWIPE_LEFT_ACTION = stringPreferencesKey("queue_swipe_left_action")
        val QUEUE_SWIPE_RIGHT_ACTION = stringPreferencesKey("queue_swipe_right_action")
        val PLAYLIST_SWIPE_LEFT_ACTION = stringPreferencesKey("playlist_swipe_left_action")
        val PLAYLIST_SWIPE_RIGHT_ACTION = stringPreferencesKey("playlist_swipe_right_action")
        val ALBUM_SWIPE_LEFT_ACTION = stringPreferencesKey("album_swipe_left_action")
        val ALBUM_SWIPE_RIGHT_ACTION = stringPreferencesKey("album_swipe_right_action")
        //</editor-fold>
        //<editor-fold desc="Mini player">
        val MINI_DISABLE_SWIPE_DOWN_TO_DISMISS = booleanPreferencesKey("mini_disable_swipe_down_to_dismiss")
        val MINI_PLAYER_POSITION = stringPreferencesKey("mini_player_position")
        val MINI_PLAYER_TYPE = stringPreferencesKey("mini_player_type")
        val MINI_PLAYER_PROGRESS_BAR = stringPreferencesKey("mini_player_progress_bar")
        //</editor-fold>
        //<editor-fold desc="Player">
        val PLAYER_IS_CONTROLS_EXPANDED = booleanPreferencesKey("player_is_controls_expanded")
        val PLAYER_SHOW_THUMBNAIL = booleanPreferencesKey("player_show_thumbnail")
        val PLAYER_BOTTOM_GRADIENT = booleanPreferencesKey("player_bottom_gradient")
        val PLAYER_EXPANDED = booleanPreferencesKey("player_expanded")
        val PLAYER_THUMBNAIL_HORIZONTAL_SWIPE_DISABLED = booleanPreferencesKey("player_thumbnail_horizontal_swipe_disabled")
        val PLAYER_VISUALIZER = booleanPreferencesKey("player_visualizer")
        val PLAYER_TAP_THUMBNAIL_FOR_LYRICS = booleanPreferencesKey("player_tap_thumbnail_for_lyrics")
        val PLAYER_ACTION_ADD_TO_PLAYLIST = booleanPreferencesKey("player_action_add_to_playlist")
        val PLAYER_ACTION_OPEN_QUEUE_ARROW = booleanPreferencesKey("player_action_open_queue_arrow")
        val PLAYER_ACTION_DOWNLOAD = booleanPreferencesKey("player_action_download")
        val PLAYER_ACTION_LOOP = booleanPreferencesKey("player_action_loop")
        val PLAYER_ACTION_SHOW_LYRICS = booleanPreferencesKey("player_action_show_lyrics")
        val PLAYER_ACTION_TOGGLE_EXPAND = booleanPreferencesKey("player_action_toggle_expand")
        val PLAYER_ACTION_SHUFFLE = booleanPreferencesKey("player_action_shuffle")
        val PLAYER_ACTION_SLEEP_TIMER = booleanPreferencesKey("player_action_sleep_timer")
        val PLAYER_ACTION_SHOW_MENU = booleanPreferencesKey("player_action_show_menu")
        val PLAYER_ACTION_START_RADIO = booleanPreferencesKey("player_action_start_radio")
        val PLAYER_ACTION_OPEN_EQUALIZER = booleanPreferencesKey("player_action_open_equalizer")
        val PLAYER_ACTION_DISCOVER = booleanPreferencesKey("player_action_discover")
        val PLAYER_ACTION_TOGGLE_VIDEO = booleanPreferencesKey("player_action_toggle_video")
        val PLAYER_ACTION_LYRICS_POPUP_MESSAGE = booleanPreferencesKey("player_action_lyrics_popup_message")
        val PLAYER_TRANSPARENT_ACTIONS_BAR = booleanPreferencesKey("player_transparent_actions_bar")
        val PLAYER_ACTION_BUTTONS_SPACED_EVENLY = booleanPreferencesKey("player_action_buttons_spaced_evenly")
        val PLAYER_ACTIONS_BAR_TAP_TO_OPEN_QUEUE = booleanPreferencesKey("player_actions_bar_tap_to_open_queue")
        val PLAYER_ACTIONS_BAR_SWIPE_UP_TO_OPEN_QUEUE = booleanPreferencesKey("player_actions_bar_swipe_up_to_open_queue")
        val PLAYER_IS_ACTIONS_BAR_EXPANDED = booleanPreferencesKey("player_is_actions_bar_expanded")
        val PLAYER_SHOW_TOTAL_QUEUE_TIME = booleanPreferencesKey("player_show_total_queue_time")
        val PLAYER_IS_QUEUE_DURATION_EXPANDED = booleanPreferencesKey("player_is_queue_duration_expanded")
        val PLAYER_IS_NEXT_IN_QUEUE_EXPANDED = booleanPreferencesKey("player_is_next_in_queue_expanded")
        val PLAYER_SHOW_NEXT_IN_QUEUE_THUMBNAIL = booleanPreferencesKey("player_show_next_in_queue_thumbnail")
        val PLAYER_SHOW_SONGS_REMAINING_TIME = booleanPreferencesKey("player_show_songs_remaining_time")
        val PLAYER_SHOW_SEEK_BUTTONS = booleanPreferencesKey("player_show_seek_buttons")
        val PLAYER_SHOW_TOP_ACTIONS_BAR = booleanPreferencesKey("player_show_top_actions_bar")
        val PLAYER_IS_CONTROL_AND_TIMELINE_SWAPPED = booleanPreferencesKey("player_is_control_and_timeline_swapped")
        val PLAYER_SHOW_THUMBNAIL_ON_VISUALIZER = booleanPreferencesKey("player_show_thumbnail_on_visualizer")
        val PLAYER_SHRINK_THUMBNAIL_ON_PAUSE = booleanPreferencesKey("player_shrink_thumbnail_on_pause")
        val PLAYER_KEEP_MINIMIZED = booleanPreferencesKey("player_keep_minimized")
        val PLAYER_BACKGROUND_BLUR = booleanPreferencesKey("player_background_blur")
        val PLAYER_BACKGROUND_FADING_EDGE = booleanPreferencesKey("player_background_fading_edge")
        val PLAYER_STATS_FOR_NERDS = booleanPreferencesKey("player_stats_for_nerds")
        val PLAYER_IS_STATS_FOR_NERDS_EXPANDED = booleanPreferencesKey("player_is_stats_for_nerds_expanded")
        val PLAYER_THUMBNAILS_CAROUSEL = booleanPreferencesKey("player_thumbnails_carousel")
        val PLAYER_THUMBNAIL_ANIMATION = booleanPreferencesKey("player_thumbnail_animation")
        val PLAYER_THUMBNAIL_ROTATION = booleanPreferencesKey("player_thumbnail_rotation")
        val PLAYER_IS_TITLE_EXPANDED = booleanPreferencesKey("player_is_title_expanded")
        val PLAYER_IS_TIMELINE_EXPANDED = booleanPreferencesKey("player_is_timeline_expanded")
        val PLAYER_SONG_INFO_ICON = booleanPreferencesKey("player_song_info_icon")
        val PLAYER_TOP_PADDING = booleanPreferencesKey("player_top_padding")
        val PLAYER_EXTRA_SPACE = booleanPreferencesKey("player_extra_space")
        val PLAYER_ROTATING_ALBUM_COVER = booleanPreferencesKey("player_rotating_album_cover")
        val PLAYER_CONTROLS_TYPE = stringPreferencesKey("player_controls_type")
        val PLAYER_INFO_TYPE = stringPreferencesKey("player_info_type")
        val PLAYER_TYPE = stringPreferencesKey("player_type")
        val PLAYER_TIMELINE_TYPE = stringPreferencesKey("player_timeline_type")
        val PLAYER_PORTRAIT_THUMBNAIL_SIZE = stringPreferencesKey("player_portrait_thumbnail_size")
        val PLAYER_LANDSCAPE_THUMBNAIL_SIZE = stringPreferencesKey("player_landscape_thumbnail_size")
        val PLAYER_TIMELINE_SIZE = stringPreferencesKey("player_timeline_size")
        val PLAYER_PLAY_BUTTON_TYPE = stringPreferencesKey("player_play_button_type")
        val PLAYER_BACKGROUND = stringPreferencesKey("player_background")
        val PLAYER_THUMBNAIL_TYPE = stringPreferencesKey("player_thumbnail_type")
        val PLAYER_NO_THUMBNAIL_SWIPE_ANIMATION = stringPreferencesKey("player_no_thumbnail_swipe_animation")
        val PLAYER_THUMBNAIL_VINYL_SIZE = floatPreferencesKey("player_thumbnail_vinyl_size")
        val PLAYER_THUMBNAIL_FADE = floatPreferencesKey("player_thumbnail_fade")
        val PLAYER_THUMBNAIL_FADE_EX = floatPreferencesKey("player_thumbnail_fade_ex")
        val PLAYER_THUMBNAIL_SPACING = floatPreferencesKey("player_thumbnail_spacing")
        val PLAYER_THUMBNAIL_SPACING_LANDSCAPE = floatPreferencesKey("player_thumbnail_spacing_landscape")
        val PLAYER_BACKGROUND_BLUR_STRENGTH = floatPreferencesKey("player_background_blur_strength")
        val PLAYER_BACKGROUND_BACK_DROP = floatPreferencesKey("player_background_back_drop")
        val PLAYER_CURRENT_VISUALIZER = intPreferencesKey("player_current_visualizer")
        //</editor-fold>
        //<editor-fold desc="Cache">
        val EXO_CACHE_LOCATION = stringPreferencesKey("exo_cache_location")
        val IMAGE_CACHE_SIZE = longPreferencesKey("image_cache_size")
        val EXO_CACHE_SIZE = longPreferencesKey("exo_cache_size")
        val EXO_DOWNLOAD_SIZE = longPreferencesKey("exo_download_size")
        //</editor-fold>
        //<editor-fold desc="Notification">
        val MEDIA_NOTIFICATION_FIRST_ICON = stringPreferencesKey("media_notification_first_icon")
        val MEDIA_NOTIFICATION_SECOND_ICON = stringPreferencesKey("media_notification_second_icon")
        //</editor-fold>
        //<editor-fold desc="Lyrics">
        val LYRICS_SHOW_THUMBNAIL = booleanPreferencesKey("lyrics_show_thumbnail")
        val LYRICS_JUMP_ON_TAP = booleanPreferencesKey("lyrics_jump_on_tap")
        val LYRICS_SHOW_ACCENT_BACKGROUND = booleanPreferencesKey("lyrics_show_accent_background")
        val LYRICS_SYNCHRONIZED = booleanPreferencesKey("lyrics_synchronized")
        val LYRICS_SHOW_SECOND_LINE = booleanPreferencesKey("lyrics_show_second_line")
        val LYRICS_ANIMATE_SIZE = booleanPreferencesKey("lyrics_animate_size")
        val LYRICS_LANDSCAPE_CONTROLS = booleanPreferencesKey("lyrics_landscape_controls")
        val LYRICS_COLOR = stringPreferencesKey("lyrics_color")
        val LYRICS_OUTLINE = stringPreferencesKey("lyrics_outline")
        val LYRICS_FONT_SIZE = stringPreferencesKey("lyrics_font_size")
        val LYRICS_ROMANIZATION_TYPE = stringPreferencesKey("lyrics_romanization_type")
        val LYRICS_BACKGROUND = stringPreferencesKey("lyrics_background")
        val LYRICS_HIGHLIGHT = stringPreferencesKey("lyrics_highlight")
        val LYRICS_ALIGNMENT = stringPreferencesKey("lyrics_alignment")
        val LYRICS_SIZE = floatPreferencesKey("lyrics_size")
        val LYRICS_SIZE_LANDSCAPE = floatPreferencesKey("lyrics_size_landscape")
        //</editor-fold>
        //<editor-fold desc="Page type">
        val HOME_ARTIST_TYPE = stringPreferencesKey("home_artist_type")
        val HOME_ALBUM_TYPE = stringPreferencesKey("home_album_type")
        val HOME_SONGS_TYPE = stringPreferencesKey("home_songs_type")
        val HISTORY_PAGE_TYPE = stringPreferencesKey("history_page_type")
        val HOME_LIBRARY_TYPE = stringPreferencesKey("home_library_type")
        //</editor-fold>
        //<editor-fold desc="Audio">
        val AUDIO_SKIP_SILENCE = booleanPreferencesKey("audio_skip_silence")
        val AUDIO_VOLUME_NORMALIZATION = booleanPreferencesKey("audio_volume_normalization")
        val AUDIO_SHAKE_TO_SKIP = booleanPreferencesKey("audio_shake_to_skip")
        val AUDIO_VOLUME_BUTTONS_CHANGE_SONG = booleanPreferencesKey("audio_volume_buttons_change_song")
        val AUDIO_BASS_BOOSTED = booleanPreferencesKey("audio_bass_boosted")
        val AUDIO_SMART_PAUSE_DURING_CALLS = booleanPreferencesKey("audio_smart_pause_during_calls")
        val AUDIO_SPEED = booleanPreferencesKey("audio_speed")
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val AUDIO_VOLUME_NORMALIZATION_TARGET = floatPreferencesKey("audio_volume_normalization_target")
        val AUDIO_BASS_BOOST_LEVEL = floatPreferencesKey("audio_bass_boost_level")
        val AUDIO_SPEED_VALUE = floatPreferencesKey("audio_speed_value")
        val AUDIO_PITCH = floatPreferencesKey("audio_pitch")
        val AUDIO_VOLUME = floatPreferencesKey("audio_volume")
        val AUDIO_DEVICE_VOLUME = floatPreferencesKey("audio_device_volume")
        val AUDIO_MEDLEY_DURATION = floatPreferencesKey("audio_medley_duration")
        val AUDIO_REVERB_PRESET = intPreferencesKey("audio_reverb_preset")
        //</editor-fold>
        //<editor-fold desc="YouTube">
        val YOUTUBE_LOGIN = booleanPreferencesKey("youtube_login")
        val YOUTUBE_PLAYLISTS_SYNC = booleanPreferencesKey("youtube_playlists_sync")
        val YOUTUBE_ARTISTS_SYNC = booleanPreferencesKey("youtube_artists_sync")
        val YOUTUBE_ALBUMS_SYNC = booleanPreferencesKey("youtube_albums_sync")
        val YOUTUBE_VISITOR_DATA = stringPreferencesKey("youtube_visitor_data")
        val YOUTUBE_SYNC_ID = stringPreferencesKey("youtube_sync_id")
        val YOUTUBE_COOKIES = stringPreferencesKey("youtube_cookies")
        val YOUTUBE_ACCOUNT_NAME = stringPreferencesKey("youtube_account_name")
        val YOUTUBE_ACCOUNT_EMAIL = stringPreferencesKey("youtube_account_email")
        val YOUTUBE_SELF_CHANNEL_HANDLE = stringPreferencesKey("youtube_self_channel_handle")
        val YOUTUBE_ACCOUNT_AVATAR = stringPreferencesKey("youtube_account_avatar")
        val YOUTUBE_LAST_VIDEO_ID = stringPreferencesKey("youtube_last_video_id")
        val YOUTUBE_LAST_VIDEO_SECONDS = floatPreferencesKey("youtube_last_video_seconds")
        //</editor-fold>
        //<editor-fold desc="Quick picks">
        val QUICK_PICKS_SHOW_TIPS = booleanPreferencesKey("quick_picks_show_tips")
        val QUICK_PICKS_SHOW_RELATED_ALBUMS = booleanPreferencesKey("quick_picks_show_related_albums")
        val QUICK_PICKS_SHOW_RELATED_ARTISTS = booleanPreferencesKey("quick_picks_show_related_artists")
        val QUICK_PICKS_SHOW_NEW_ALBUMS_ARTISTS = booleanPreferencesKey("quick_picks_show_new_albums_artists")
        val QUICK_PICKS_SHOW_NEW_ALBUMS = booleanPreferencesKey("quick_picks_show_new_albums")
        val QUICK_PICKS_SHOW_MIGHT_LIKE_PLAYLISTS = booleanPreferencesKey("quick_picks_show_might_like_playlists")
        val QUICK_PICKS_SHOW_MOODS_AND_GENRES = booleanPreferencesKey("quick_picks_show_moods_and_genres")
        val QUICK_PICKS_SHOW_MONTHLY_PLAYLISTS = booleanPreferencesKey("quick_picks_show_monthly_playlists")
        val QUICK_PICKS_SHOW_CHARTS = booleanPreferencesKey("quick_picks_show_charts")
        val QUICK_PICKS_PAGE = booleanPreferencesKey("quick_picks_page")
        val QUICK_PICKS_TYPE = stringPreferencesKey("quick_picks_type")
        val QUICK_PICKS_MIN_DURATION = intPreferencesKey("quick_picks_min_duration")
        //</editor-fold>
        //<editor-fold desc="Discord">
        val DISCORD_LOGIN = booleanPreferencesKey("discord_login")
        val DISCORD_ACCESS_TOKEN = stringPreferencesKey("discord_access_token")
        //</editor-fold>
        //<editor-fold desc="Proxy">
        val IS_PROXY_ENABLED = booleanPreferencesKey("is_proxy_enabled")
        val PROXY_SCHEME = stringPreferencesKey("proxy_scheme")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = intPreferencesKey("proxy_port")
        //</editor-fold>
        //<editor-fold desc="Custom light colors">
        val CUSTOM_LIGHT_THEME_BACKGROUND_0 = longPreferencesKey("custom_light_theme_background_0")
        val CUSTOM_LIGHT_THEME_BACKGROUND_1 = longPreferencesKey("custom_light_theme_background_1")
        val CUSTOM_LIGHT_THEME_BACKGROUND_2 = longPreferencesKey("custom_light_theme_background_2")
        val CUSTOM_LIGHT_THEME_BACKGROUND_3 = longPreferencesKey("custom_light_theme_background_3")
        val CUSTOM_LIGHT_THEME_BACKGROUND_4 = longPreferencesKey("custom_light_theme_background_4")
        val CUSTOM_LIGHT_TEXT = longPreferencesKey("custom_light_text")
        val CUSTOM_LIGHT_TEXT_SECONDARY = longPreferencesKey("custom_light_text_secondary")
        val CUSTOM_LIGHT_TEXT_DISABLED = longPreferencesKey("custom_light_text_disabled")
        val CUSTOM_LIGHT_PLAY_BUTTON = longPreferencesKey("custom_light_play_button")
        val CUSTOM_LIGHT_ACCENT = longPreferencesKey("custom_light_accent")
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Custom dark theme">
        val CUSTOM_DARK_THEME_BACKGROUND_0 = longPreferencesKey("custom_dark_theme_background_0")
        val CUSTOM_DARK_THEME_BACKGROUND_1 = longPreferencesKey("custom_dark_theme_background_1")
        val CUSTOM_DARK_THEME_BACKGROUND_2 = longPreferencesKey("custom_dark_theme_background_2")
        val CUSTOM_DARK_THEME_BACKGROUND_3 = longPreferencesKey("custom_dark_theme_background_3")
        val CUSTOM_DARK_THEME_BACKGROUND_4 = longPreferencesKey("custom_dark_theme_background_4")
        val CUSTOM_DARK_TEXT = longPreferencesKey("custom_dark_text")
        val CUSTOM_DARK_TEXT_SECONDARY = longPreferencesKey("custom_dark_text_secondary")
        val CUSTOM_DARK_TEXT_DISABLED = longPreferencesKey("custom_dark_text_disabled")
        val CUSTOM_DARK_PLAY_BUTTON = longPreferencesKey("custom_dark_play_button")
        val CUSTOM_DARK_ACCENT = longPreferencesKey("custom_dark_accent")
        //</editor-fold>
        //<editor-fold desc="Logging">
        val RUNTIME_LOG = booleanPreferencesKey("runtime_log")
        val RUNTIME_LOG_SHARED = booleanPreferencesKey("runtime_log_shared")
        val RUNTIME_LOG_SEVERITY = stringPreferencesKey("runtime_log_severity")
        val RUNTIME_LOG_LEVEL = intPreferencesKey("runtime_log_level")
        val RUNTIME_LOG_FILE_COUNT = intPreferencesKey("runtime_log_file_count")
        val RUNTIME_LOG_MAX_SIZE_PER_FILE = longPreferencesKey("runtime_log_max_size_per_file")
        //</editor-fold>
        //<editor-fold desc="Thumbnail roundness">
        val SONG_THUMBNAIL_ROUNDNESS_PERCENT = intPreferencesKey("song_thumbnail_roundness_percent")
        val ALBUM_THUMBNAIL_ROUNDNESS_PERCENT = intPreferencesKey("album_thumbnail_roundness_percent")
        val ARTIST_THUMBNAIL_ROUNDNESS_PERCENT = intPreferencesKey("artist_thumbnail_roundness_percent")
        val PLAYLIST_THUMBNAIL_ROUNDNESS_PERCENT = intPreferencesKey("playlist_thumbnail_roundness_percent")
        //</editor-fold>
        //<editor-fold desc="Platform indicator">
        val ALBUMS_PLATFORM_INDICATOR = stringPreferencesKey("albums_platform_indicator")
        val ARTISTS_PLATFORM_INDICATOR = stringPreferencesKey("artists_platform_indicator")
        val PLAYLISTS_PLATFORM_INDICATOR = stringPreferencesKey("playlists_platform_indicator")
        //</editor-fold>

        val QUEUE_AUTO_APPEND = booleanPreferencesKey("queue_auto_append")
        val SHOW_CHECK_UPDATE_STATUS = booleanPreferencesKey("show_check_update_status")
        val MARQUEE_TEXT_EFFECT = booleanPreferencesKey("marquee_text_effect")
        val PARENTAL_CONTROL = booleanPreferencesKey("parental_control")
        val ROTATION_EFFECT = booleanPreferencesKey("rotation_effect")
        val TRANSPARENT_TIMELINE = booleanPreferencesKey("transparent_timeline")
        val BLACK_GRADIENT = booleanPreferencesKey("black_gradient")
        val TEXT_OUTLINE = booleanPreferencesKey("text_outline")
        val SHOW_FLOATING_ICON = booleanPreferencesKey("show_floating_icon")
        val ZOOM_OUT_ANIMATION = booleanPreferencesKey("zoom_out_animation")
        val ENABLE_DISCOVER = booleanPreferencesKey("enable_discover")
        val ENABLE_PERSISTENT_QUEUE = booleanPreferencesKey("enable_persistent_queue")
        val RESUME_PLAYBACK_ON_STARTUP = booleanPreferencesKey("resume_playback_on_startup")
        val RESUME_PLAYBACK_WHEN_CONNECT_TO_AUDIO_DEVICE = booleanPreferencesKey("resume_playback_when_connect_to_audio_device")
        val CLOSE_APP_ON_BACK = booleanPreferencesKey("close_app_on_back")
        val PLAYBACK_SKIP_ON_ERROR = booleanPreferencesKey("playback_skip_on_error")
        val USE_SYSTEM_FONT = booleanPreferencesKey("use_system_font")
        val APPLY_FONT_PADDING = booleanPreferencesKey("apply_font_padding")
        val SHOW_SEARCH_IN_NAVIGATION_BAR = booleanPreferencesKey("show_search_in_navigation_bar")
        val SHOW_STATS_IN_NAVIGATION_BAR = booleanPreferencesKey("show_stats_in_navigation_bar")
        val SHOW_LISTENING_STATS = booleanPreferencesKey("show_listening_stats")
        val HOME_SONGS_SHOW_FAVORITES_CHIP = booleanPreferencesKey("home_songs_show_favorites_chip")
        val HOME_SONGS_SHOW_CACHED_CHIP = booleanPreferencesKey("home_songs_show_cached_chip")
        val HOME_SONGS_SHOW_DOWNLOADED_CHIP = booleanPreferencesKey("home_songs_show_downloaded_chip")
        val HOME_SONGS_SHOW_MOST_PLAYED_CHIP = booleanPreferencesKey("home_songs_show_most_played_chip")
        val HOME_SONGS_SHOW_ON_DEVICE_CHIP = booleanPreferencesKey("home_songs_show_on_device_chip")
        val HOME_SONGS_ON_DEVICE_SHOW_FOLDERS = booleanPreferencesKey("home_songs_on_device_show_folders")
        val HOME_SONGS_INCLUDE_ON_DEVICE_IN_ALL = booleanPreferencesKey("home_songs_include_on_device_in_all")
        val MONTHLY_PLAYLIST_COMPILATION = booleanPreferencesKey("monthly_playlist_compilation")
        val SHOW_MONTHLY_PLAYLISTS = booleanPreferencesKey("show_monthly_playlists")
        val SHOW_PINNED_PLAYLISTS = booleanPreferencesKey("show_pinned_playlists")
        val SHOW_PLAYLIST_INDICATOR = booleanPreferencesKey("show_playlist_indicator")
        val PAUSE_WHEN_VOLUME_SET_TO_ZERO = booleanPreferencesKey("pause_when_volume_set_to_zero")
        val PAUSE_HISTORY = booleanPreferencesKey("pause_history")
        val IS_PIP_ENABLED = booleanPreferencesKey("is_pip_enabled")
        val IS_AUTO_PIP_ENABLED = booleanPreferencesKey("is_auto_pip_enabled")
        val AUTO_DOWNLOAD = booleanPreferencesKey("auto_download")
        val AUTO_DOWNLOAD_ON_LIKE = booleanPreferencesKey("auto_download_on_like")
        val AUTO_DOWNLOAD_ON_ALBUM_BOOKMARKED = booleanPreferencesKey("auto_download_on_album_bookmarked")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val PAUSE_SEARCH_HISTORY = booleanPreferencesKey("pause_search_history")
        val LOCAL_PLAYLIST_SMART_RECOMMENDATION = booleanPreferencesKey("local_playlist_smart_recommendation")
        val IS_CONNECTION_METERED = booleanPreferencesKey("is_connection_metered")
        val SINGLE_BACK_FROM_SEARCH = booleanPreferencesKey("single_back_from_search")
        val SONG_EMPTY_DURATION_PLACEHOLDER = booleanPreferencesKey("song_empty_duration_placeholder")
        val DOH_SERVER = stringPreferencesKey("doh_server")
        val HOME_SONGS_TOP_PLAYLIST_PERIOD = stringPreferencesKey("home_songs_top_playlist_period")
        val MENU_STYLE = stringPreferencesKey("menu_style")
        val MAIN_THEME = stringPreferencesKey("main_theme")
        val COLOR_PALETTE = stringPreferencesKey("color_palette")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val STARTUP_SCREEN = stringPreferencesKey("startup_screen")
        val FONT = stringPreferencesKey("font")
        val NAVIGATION_BAR_POSITION = stringPreferencesKey("navigation_bar_position")
        val NAVIGATION_BAR_TYPE = stringPreferencesKey("navigation_bar_type")
        val THUMBNAIL_BORDER_RADIUS = stringPreferencesKey("thumbnail_border_radius")
        val TRANSITION_EFFECT = stringPreferencesKey("transition_effect")
        val QUEUE_TYPE = stringPreferencesKey("queue_type")
        val QUEUE_LOOP_TYPE = stringPreferencesKey("queue_loop_type")
        val CAROUSEL_SIZE = stringPreferencesKey("carousel_size")
        val THUMBNAIL_TYPE = stringPreferencesKey("thumbnail_type")
        val LIKE_ICON = stringPreferencesKey("like_icon")
        val LIVE_WALLPAPER = stringPreferencesKey("live_wallpaper")
        val ANIMATED_GRADIENT = stringPreferencesKey("animated_gradient")
        val NOW_PLAYING_INDICATOR = stringPreferencesKey("now_playing_indicator")
        val PIP_MODULE = stringPreferencesKey("pip_module")
        val CHECK_UPDATE = stringPreferencesKey("check_update")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val OTHER_APP_LANGUAGE = stringPreferencesKey("other_app_language")
        val HOME_ARTIST_AND_ALBUM_FILTER = stringPreferencesKey("home_artist_and_album_filter")
        val STATISTIC_PAGE_CATEGORY = stringPreferencesKey("statistic_page_category")
        val APP_REGION = stringPreferencesKey("app_region")
        val LOCAL_SONGS_FOLDER = stringPreferencesKey("local_songs_folder")
        val SEEN_CHANGELOGS_VERSION = stringPreferencesKey("seen_changelogs_version")
        val FLOATING_ICON_X_OFFSET = floatPreferencesKey("floating_icon_x_offset")
        val FLOATING_ICON_Y_OFFSET = floatPreferencesKey("floating_icon_y_offset")
        val MULTI_FLOATING_ICON_X_OFFSET = floatPreferencesKey("multi_floating_icon_x_offset")
        val MULTI_FLOATING_ICON_Y_OFFSET = floatPreferencesKey("multi_floating_icon_y_offset")
        val SMART_REWIND = intPreferencesKey("smart_rewind")
        val SEARCH_RESULTS_TAB_INDEX = intPreferencesKey("search_results_tab_index")
        val HOME_TAB_INDEX = intPreferencesKey("home_tab_index")
        val ARTIST_SCREEN_TAB_INDEX = intPreferencesKey("artist_screen_tab_index")
        val LIVE_WALLPAPER_RESET_DURATION = longPreferencesKey("live_wallpaper_reset_duration")
        val BLACKLISTED_FOLDERS = stringSetPreferencesKey("blacklisted_folders")
        val CUSTOM_COLOR = longPreferencesKey("custom_color")
        val PAUSE_BETWEEN_SONGS = longPreferencesKey("pause_duration_between_songs")
        val LIMIT_SONGS_WITH_DURATION = longPreferencesKey("limit_songs_with_duration")
        val AUDIO_FADE_DURATION = longPreferencesKey("audio_fade_duration")
    }
}