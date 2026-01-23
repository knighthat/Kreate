package app.kreate.android

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.ColorRes
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import app.kreate.android.enums.DohServer
import app.kreate.android.enums.PlatformIndicatorType
import app.kreate.android.utils.innertube.getSystemCountryCode
import app.kreate.constant.Language
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.enums.AlbumSortBy
import it.fast4x.rimusic.enums.AlbumSwipeAction
import it.fast4x.rimusic.enums.AlbumsType
import it.fast4x.rimusic.enums.AnimatedGradient
import it.fast4x.rimusic.enums.ArtistSortBy
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
import it.fast4x.rimusic.enums.MiniPlayerType
import it.fast4x.rimusic.enums.MusicAnimationType
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.NavigationBarType
import it.fast4x.rimusic.enums.NotificationButtons
import it.fast4x.rimusic.enums.OnDeviceSongSortBy
import it.fast4x.rimusic.enums.PauseBetweenSongs
import it.fast4x.rimusic.enums.PipModule
import it.fast4x.rimusic.enums.PlayEventsType
import it.fast4x.rimusic.enums.PlayerBackgroundColors
import it.fast4x.rimusic.enums.PlayerControlsType
import it.fast4x.rimusic.enums.PlayerInfoType
import it.fast4x.rimusic.enums.PlayerPlayButtonType
import it.fast4x.rimusic.enums.PlayerPosition
import it.fast4x.rimusic.enums.PlayerThumbnailSize
import it.fast4x.rimusic.enums.PlayerTimelineSize
import it.fast4x.rimusic.enums.PlayerTimelineType
import it.fast4x.rimusic.enums.PlayerType
import it.fast4x.rimusic.enums.PlaylistSongSortBy
import it.fast4x.rimusic.enums.PlaylistSortBy
import it.fast4x.rimusic.enums.PlaylistSwipeAction
import it.fast4x.rimusic.enums.PlaylistsType
import it.fast4x.rimusic.enums.PresetsReverb
import it.fast4x.rimusic.enums.QueueLoopType
import it.fast4x.rimusic.enums.QueueSwipeAction
import it.fast4x.rimusic.enums.QueueType
import it.fast4x.rimusic.enums.RecommendationsNumber
import it.fast4x.rimusic.enums.Romanization
import it.fast4x.rimusic.enums.SongSortBy
import it.fast4x.rimusic.enums.SongsNumber
import it.fast4x.rimusic.enums.SortOrder
import it.fast4x.rimusic.enums.StatisticsCategory
import it.fast4x.rimusic.enums.StatisticsType
import it.fast4x.rimusic.enums.SwipeAnimationNoThumbnail
import it.fast4x.rimusic.enums.ThumbnailCoverType
import it.fast4x.rimusic.enums.ThumbnailRoundness
import it.fast4x.rimusic.enums.ThumbnailType
import it.fast4x.rimusic.enums.TransitionEffect
import it.fast4x.rimusic.enums.UiType
import it.fast4x.rimusic.enums.WallpaperType
import it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import it.fast4x.rimusic.ui.styling.DefaultLightColorPalette
import it.fast4x.rimusic.utils.getDeviceVolume
import me.knighthat.innertube.Constants
import org.jetbrains.annotations.Blocking
import org.jetbrains.annotations.NonBlocking
import java.net.Proxy

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
    companion object {

        lateinit var profilePreferences: SharedPreferences
            private set
        lateinit var preferences: SharedPreferences
            private set
        lateinit var encryptedPreferences: SharedPreferences
            private set

        //<editor-fold defaultstate="collapsed" desc="Item size">
        val HOME_ARTIST_ITEM_SIZE by lazy {
            Enum( preferences, "HomeAristItemSize", "AristItemSizeEnum", HomeItemSize.SMALL )
        }
        val HOME_ALBUM_ITEM_SIZE by lazy {
            Enum( preferences, "HomeAlbumItemSize", "AlbumItemSizeEnum", HomeItemSize.SMALL )
        }
        val HOME_LIBRARY_ITEM_SIZE by lazy {
            Enum( preferences, "HomeLibraryItemSize", "LibraryItemSizeEnum", HomeItemSize.SMALL )
        }
        val SONG_THUMBNAIL_SIZE by lazy {
            Int(preferences, "SongItemSize", "", 54)
        }
        val ALBUM_THUMBNAIL_SIZE by lazy {
            Int(preferences, "AlbumItemSize", "", 128)
        }
        val ARTIST_THUMBNAIL_SIZE by lazy {
            Int(preferences, "ArtistItemSize", "", 128)
        }
        val PLAYLIST_THUMBNAIL_SIZE by lazy {
            Int(preferences, "PlaylistItemSize", "", 128)
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Sort by">
        val HOME_SONGS_SORT_BY by lazy {
            Enum( preferences, "HomeSongsSortBy", "", SongSortBy.Title )
        }
        val HOME_ON_DEVICE_SONGS_SORT_BY by lazy {
            Enum( preferences, "HomeOnDeviceSongsSortBy", "", OnDeviceSongSortBy.Title )
        }
        val HOME_ARTISTS_SORT_BY by lazy {
            Enum( preferences, "HomeArtistsSortBy", "", ArtistSortBy.Name )
        }
        val HOME_ALBUMS_SORT_BY by lazy {
            Enum( preferences, "HomeAlbumsSortBy", "", AlbumSortBy.Title )
        }
        val HOME_LIBRARY_SORT_BY by lazy {
            Enum( preferences, "HomeLibrarySortBy", "", PlaylistSortBy.SongCount )
        }
        val PLAYLIST_SONGS_SORT_BY by lazy {
            Enum( preferences, "PlaylistSongsSortBy", "", PlaylistSongSortBy.Title )
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Sort order">
        val HOME_SONGS_SORT_ORDER by lazy {
            Enum( preferences, "HomeSongsSortOrder", "", SortOrder.Ascending )
        }
        val HOME_ARTISTS_SORT_ORDER by lazy {
            Enum( preferences, "PlaylistSongsSortOrder", "", SortOrder.Ascending )
        }
        val HOME_ALBUM_SORT_ORDER by lazy {
            Enum( preferences, "PlaylistSongsSortOrder", "", SortOrder.Ascending )
        }
        val HOME_LIBRARY_SORT_ORDER by lazy {
            Enum( preferences, "HomeLibrarySortOrder", "", SortOrder.Ascending )
        }
        val PLAYLIST_SONGS_SORT_ORDER by lazy {
            Enum( preferences, "PlaylistSongsSortOrder", "", SortOrder.Ascending )
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Max # of ...">
        val MAX_NUMBER_OF_SMART_RECOMMENDATIONS by lazy {
            Enum( preferences, "MaxNumberOfSmartRecommendations", "recommendationsNumber", RecommendationsNumber.`5` )
        }
        val MAX_NUMBER_OF_STATISTIC_ITEMS by lazy {
            Enum( preferences, "MaxNumberOfStatisticItems", "maxStatisticsItems", MaxStatisticsItems.`10` )
        }
        val MAX_NUMBER_OF_TOP_PLAYED by lazy {
            Enum( preferences, "MaxNumberOfTopPlayed", "MaxTopPlaylistItems", MaxTopPlaylistItems.`10` )
        }
        val MAX_NUMBER_OF_SONG_IN_QUEUE by lazy {
            Enum( preferences, "MaxNumberOfTopPlayed", "MaxTopPlaylistItems", MaxSongs.Unlimited )
        }
        val MAX_NUMBER_OF_NEXT_IN_QUEUE by lazy {
            Enum( preferences, "MaxNumberOfNextInQueue", "showsongs", SongsNumber.`2` )
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Swipe action">
        val ENABLE_SWIPE_ACTION by lazy {
            Boolean( preferences, "EnableSwipeAction", "isSwipeToActionEnabled", true )
        }
        val QUEUE_SWIPE_LEFT_ACTION by lazy {
            Enum( preferences, "QueueSwipeLeftAction", "queueSwipeLeftAction", QueueSwipeAction.RemoveFromQueue )
        }
        val QUEUE_SWIPE_RIGHT_ACTION by lazy {
            Enum( preferences, "QueueSwipeRightAction", "queueSwipeRightAction", QueueSwipeAction.PlayNext )
        }
        val PLAYLIST_SWIPE_LEFT_ACTION by lazy {
            Enum( preferences, "PlaylistSwipeLeftAction", "playlistSwipeLeftAction", PlaylistSwipeAction.Favourite )
        }
        val PLAYLIST_SWIPE_RIGHT_ACTION by lazy {
            Enum( preferences, "PlaylistSwipeRightAction", "playlistSwipeRightAction", PlaylistSwipeAction.PlayNext )
        }
        val ALBUM_SWIPE_LEFT_ACTION by lazy {
            Enum( preferences, "AlbumSwipeLeftAction", "albumSwipeLeftAction", AlbumSwipeAction.PlayNext )
        }
        val ALBUM_SWIPE_RIGHT_ACTION by lazy {
            Enum( preferences, "AlbumSwipeRightAction", "albumSwipeRightAction", AlbumSwipeAction.Bookmark )
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Mini player">
        val MINI_PLAYER_POSITION by lazy {
            Enum( preferences, "MiniPlayerPosition", "playerPosition", PlayerPosition.Bottom )
        }
        val MINI_PLAYER_TYPE by lazy {
            Enum( preferences, "MiniPlayerType", "miniPlayerType", MiniPlayerType.Modern )
        }
        val MINI_PLAYER_PROGRESS_BAR by lazy {
            Enum( preferences, "MiniPlayerProgressBar", "backgroundProgress", BackgroundProgress.MiniPlayer )
        }
        val MINI_DISABLE_SWIPE_DOWN_TO_DISMISS by lazy {
            Boolean( preferences, "MiniPlayerDisableSwipeDownToDismiss", "disableClosingPlayerSwipingDown", false )
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Player">
        val PLAYER_CONTROLS_TYPE by lazy {
            Enum( preferences, "PlayerControlsType", "playerControlsType", PlayerControlsType.Essential )
        }
        val PLAYER_IS_CONTROLS_EXPANDED by lazy {
            Boolean( preferences, "PlayerIsControlsExpanded", "controlsExpanded", true )
        }
        val PLAYER_INFO_TYPE by lazy {
            Enum( preferences, "PlayerInfoType", "playerInfoType", PlayerInfoType.Essential )
        }
        val PLAYER_TYPE by lazy {
            Enum( preferences, "PlayerType", "playerType", PlayerType.Essential )
        }
        val PLAYER_TIMELINE_TYPE by lazy {
            Enum( preferences, "PlayerTimelineType", "playerTimelineType", PlayerTimelineType.FakeAudioBar )
        }
        val PLAYER_PORTRAIT_THUMBNAIL_SIZE by lazy {
            Enum( preferences, "PlayerThumbnailSize", "playerThumbnailSize", PlayerThumbnailSize.Biggest )
        }
        val PLAYER_LANDSCAPE_THUMBNAIL_SIZE by lazy {
            Enum( preferences, "PlayerLandscapeThumbnailSize", "playerThumbnailSizeL", PlayerThumbnailSize.Biggest )
        }
        val PLAYER_TIMELINE_SIZE by lazy {
            Enum( preferences, "PlayerTimelineSize", "playerTimelineSize", PlayerTimelineSize.Biggest )
        }
        val PLAYER_PLAY_BUTTON_TYPE by lazy {
            Enum( preferences, "PlayerPlayButtonType", "playerPlayButtonType", PlayerPlayButtonType.Disabled )
        }
        val PLAYER_BACKGROUND by lazy {
            Enum( preferences, "PlayerBackground", "playerBackgroundColors", PlayerBackgroundColors.BlurredCoverColor )
        }
        val PLAYER_THUMBNAIL_TYPE by lazy {
            Enum( preferences, "PlayerThumbnailType", "coverThumbnailAnimation", ThumbnailCoverType.Vinyl )
        }
        val PLAYER_THUMBNAIL_VINYL_SIZE by lazy {
            Float( preferences, "PlayerThumbnailVinylSize", "VinylSize", 50F )
        }
        val PLAYER_NO_THUMBNAIL_SWIPE_ANIMATION by lazy {
            Enum( preferences, "PlayerNoThumbnailSwipeAnimation", "swipeAnimationsNoThumbnail", SwipeAnimationNoThumbnail.Sliding )
        }
        val PLAYER_SHOW_THUMBNAIL by lazy {
            Boolean( preferences, "PlayerShowThumbnail", "showthumbnail", true )
        }
        val PLAYER_BOTTOM_GRADIENT by lazy {
            Boolean( preferences, "PlayerBottomGradient", "bottomgradient", false )
        }
        val PLAYER_EXPANDED by lazy {
            Boolean( preferences, "PlayerExpanded", "expandedplayer", false )
        }
        val PLAYER_THUMBNAIL_HORIZONTAL_SWIPE_DISABLED by lazy {
            Boolean( preferences, "PlayerThumbnailHorizontalSwipe", "disablePlayerHorizontalSwipe", false )
        }
        val PLAYER_THUMBNAIL_FADE by lazy {
            Float( preferences, "PlayerThumbnailFade", "thumbnailFade", 5F )
        }
        val PLAYER_THUMBNAIL_FADE_EX by lazy {
            Float( preferences, "PlayerThumbnailFadeEx", "thumbnailFadeEx", 5F )
        }
        val PLAYER_THUMBNAIL_SPACING by lazy {
            Float( preferences, "PlayerThumbnailSpacing", "thumbnailSpacing", 0F )
        }
        val PLAYER_THUMBNAIL_SPACING_LANDSCAPE by lazy {
            Float( preferences, "PlayerThumbnailSpacingLandscape", "thumbnailSpacingL", 0F )
        }
        val PLAYER_VISUALIZER by lazy {
            Boolean( preferences, "PlayerVisualizer", "visualizerEnabled", false )
        }
        val PLAYER_CURRENT_VISUALIZER  by lazy {
            Int( preferences, "PlayerCurrentVisualizer", "currentVisualizerKey", 0 )
        }
        val PLAYER_TAP_THUMBNAIL_FOR_LYRICS by lazy {
            Boolean( preferences, "PlayerTapThumbnailForLyrics", "thumbnailTapEnabled", true )
        }
        val PLAYER_ACTION_ADD_TO_PLAYLIST by lazy {
            Boolean( preferences, "PlayerActionAddToPlaylist", "showButtonPlayerAddToPlaylist", true )
        }
        val PLAYER_ACTION_OPEN_QUEUE_ARROW by lazy {
            Boolean( preferences, "PlayerActionOpenQueueArrow", "showButtonPlayerArrow", true )
        }
        val PLAYER_ACTION_DOWNLOAD by lazy {
            Boolean( preferences, "PlayerActionDownload", "showButtonPlayerDownload", true )
        }
        val PLAYER_ACTION_LOOP by lazy {
            Boolean( preferences, "PlayerActionLoop", "showButtonPlayerLoop", true )
        }
        val PLAYER_ACTION_SHOW_LYRICS by lazy {
            Boolean( preferences, "PlayerActionShowLyrics", "showButtonPlayerLyrics", true )
        }
        val PLAYER_ACTION_TOGGLE_EXPAND by lazy {
            Boolean( preferences, "PlayerActionToggleExpand", "expandedplayertoggle", true )
        }
        val PLAYER_ACTION_SHUFFLE by lazy {
            Boolean( preferences, "PlayerActionShuffle", "showButtonPlayerShuffle", true )
        }
        val PLAYER_ACTION_SLEEP_TIMER by lazy {
            Boolean( preferences, "PlayerActionSleepTimer", "showButtonPlayerSleepTimer", false )
        }
        val PLAYER_ACTION_SHOW_MENU by lazy {
            Boolean( preferences, "PlayerActionShowMenu", "showButtonPlayerMenu", false )
        }
        val PLAYER_ACTION_START_RADIO by lazy {
            Boolean( preferences, "PlayerActionStartRadio", "showButtonPlayerStartRadio", false )
        }
        val PLAYER_ACTION_OPEN_EQUALIZER by lazy {
            Boolean( preferences, "PlayerActionOpenEqualizer", "showButtonPlayerSystemEqualizer", false )
        }
        val PLAYER_ACTION_DISCOVER by lazy {
            Boolean( preferences, "PlayerActionDiscover", "showButtonPlayerDiscover", false )
        }
        val PLAYER_ACTION_TOGGLE_VIDEO by lazy {
            Boolean( preferences, "PlayerActionToggleVideo", "showButtonPlayerVideo", false )
        }
        val PLAYER_ACTION_LYRICS_POPUP_MESSAGE by lazy {
            Boolean( preferences, "PlayerActionLyricsPopupMessage", "playerEnableLyricsPopupMessage", true )
        }
        val PLAYER_TRANSPARENT_ACTIONS_BAR by lazy {
            Boolean( preferences, "PlayerTransparentActionsBar", "transparentBackgroundPlayerActionBar", false )
        }
        val PLAYER_ACTION_BUTTONS_SPACED_EVENLY by lazy {
            Boolean( preferences, "PlayerActionButtonsSpacedEvenly", "actionspacedevenly", false )
        }
        val PLAYER_ACTIONS_BAR_TAP_TO_OPEN_QUEUE by lazy {
            Boolean( preferences, "PlayerActionsBarTapToOpenQueue", "tapqueue", true )
        }
        val PLAYER_ACTIONS_BAR_SWIPE_UP_TO_OPEN_QUEUE by lazy {
            Boolean( preferences, "PlayerIsActionsBarExpanded", "actionExpanded", true )
        }
        val PLAYER_IS_ACTIONS_BAR_EXPANDED by lazy {
            Boolean( preferences, "PlayerActionsBarSwipeUpToOpenQueue", "swipeUpQueue", true )
        }
        val PLAYER_SHOW_TOTAL_QUEUE_TIME by lazy {
            Boolean( preferences, "PlayerShowTotalQueueTime", "showTotalTimeQueue", true )
        }
        val PLAYER_IS_QUEUE_DURATION_EXPANDED by lazy {
            Boolean( preferences, "PlayerIsQueueDurationExpanded", "queueDurationExpanded", true )
        }
        val PLAYER_SHOW_NEXT_IN_QUEUE by lazy {
            Boolean( preferences, "PlayerShowNextInQueue", "showNextSongsInPlayer", false )
        }
        val PLAYER_IS_NEXT_IN_QUEUE_EXPANDED by lazy {
            Boolean( preferences, "PlayerIsNextInQueueExpanded", "miniQueueExpanded", true )
        }
        val PLAYER_SHOW_NEXT_IN_QUEUE_THUMBNAIL by lazy {
            Boolean( preferences, "PlayerShowNextInQueueThumbnail", "showalbumcover", true )
        }
        val PLAYER_SHOW_SONGS_REMAINING_TIME by lazy {
            Boolean( preferences, "PlayerShowSongsRemainingTime", "showRemainingSongTime", true )
        }
        val PLAYER_SHOW_SEEK_BUTTONS by lazy {
            Boolean(preferences, "PlayerShowSeekButtons", "", true)
        }
        val PLAYER_SHOW_TOP_ACTIONS_BAR by lazy {
            Boolean( preferences, "PlayerShowTopActionsBar", "showTopActionsBar", true )
        }
        val PLAYER_IS_CONTROL_AND_TIMELINE_SWAPPED by lazy {
            Boolean( preferences, "PlayerIsControlAndTimelineSwapped", "playerSwapControlsWithTimeline", false )
        }
        val PLAYER_SHOW_THUMBNAIL_ON_VISUALIZER by lazy {
            Boolean( preferences, "PlayerShowThumbnailOnVisualizer", "showvisthumbnail", false )
        }
        val PLAYER_SHRINK_THUMBNAIL_ON_PAUSE by lazy {
            Boolean( preferences, "PlayerShrinkThumbnailOnPause", "thumbnailpause", false )
        }
        val PLAYER_KEEP_MINIMIZED by lazy {
            Boolean( preferences, "PlayerKeepMinimized", "keepPlayerMinimized", false )
        }
        val PLAYER_BACKGROUND_BLUR by lazy {
            Boolean( preferences, "PlayerBackgroundBlur", "noblur", true )
        }
        val PLAYER_BACKGROUND_BLUR_STRENGTH by lazy {
            Float( preferences, "PlayerBackgroundBlurStrength", "blurScale", 25F )
        }
        val PLAYER_BACKGROUND_BACK_DROP by lazy {
            Float( preferences, "PlayerBackgroundBackDrop", "playerBackdrop", 0F )
        }
        val PLAYER_BACKGROUND_FADING_EDGE by lazy {
            Boolean( preferences, "PlayerBackgroundFadingEdge", "fadingedge", false )
        }
        val PLAYER_STATS_FOR_NERDS by lazy {
            Boolean( preferences, "PlayerStatsForNerds", "statsfornerds", false )
        }
        val PLAYER_IS_STATS_FOR_NERDS_EXPANDED by lazy {
            Boolean( preferences, "PlayerIsStatForNerdsExpanded", "statsExpanded", true )
        }
        val PLAYER_THUMBNAILS_CAROUSEL by lazy {
            Boolean( preferences, "PlayerThumbnailCarousel", "carousel", true )
        }
        val PLAYER_THUMBNAIL_ANIMATION by lazy {
            Boolean( preferences, "PlayerThumbnailAnimation", "showCoverThumbnailAnimation", false )
        }
        val PLAYER_THUMBNAIL_ROTATION by lazy {
            Boolean( preferences, "PlayerThumbnailRotation", "albumCoverRotation", false )
        }
        val PLAYER_IS_TITLE_EXPANDED by lazy {
            Boolean( preferences, "PlayerIsTitleExpanded", "titleExpanded", true )
        }
        val PLAYER_IS_TIMELINE_EXPANDED by lazy {
            Boolean( preferences, "PlayerIsTimelineExpanded", "timelineExpanded", true )
        }
        val PLAYER_SONG_INFO_ICON by lazy {
            Boolean( preferences, "PlayerSongInfoIcon", "playerInfoShowIcons", true )
        }
        val PLAYER_TOP_PADDING by lazy {
            Boolean( preferences, "PlayerTopPadding", "topPadding", true )
        }
        val PLAYER_EXTRA_SPACE by lazy {
            Boolean( preferences, "PlayerExtraSpace", "extraspace", false )
        }
        val PLAYER_ROTATING_ALBUM_COVER by lazy {
            Boolean( preferences, "PlayerRotatingAlbumCover", "rotatingAlbumCover", false )
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Cache">
        val EXO_CACHE_LOCATION by lazy {
            Enum( preferences, "ExoCacheLocation", "exoPlayerCacheLocationKey", ExoPlayerCacheLocation.System )
        }
        val IMAGE_CACHE_SIZE by lazy {
            Long(preferences, "ThumbnailCacheSizeBytes", "", kotlin.Long.MAX_VALUE)
        }
        val EXO_CACHE_SIZE by lazy {
            Long(preferences, "SongCacheSizeBytes", "", kotlin.Long.MAX_VALUE)
        }
        val EXO_DOWNLOAD_SIZE by lazy {
            Long(preferences, "SongDownloadSizeBytes", "", kotlin.Long.MAX_VALUE)
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Notification">
        val MEDIA_NOTIFICATION_FIRST_ICON by lazy {
            Enum( preferences, "MediaNotificationFirstIcon", "notificationPlayerFirstIcon", NotificationButtons.Download )
        }
        val MEDIA_NOTIFICATION_SECOND_ICON by lazy {
            Enum( preferences, "MediaNotificationSecondIcon", "notificationPlayerSecondIcon", NotificationButtons.Favorites )
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Lyrics">
        val LYRICS_SIZE by lazy {
            Float( preferences, "LyricsSize", "lyricsSize", 5F )
        }
        val LYRICS_SIZE_LANDSCAPE by lazy {
            Float( preferences, "LyricsSizeLandscape", "lyricsSizeL", 5F )
        }
        val LYRICS_COLOR by lazy {
            Enum( preferences, "LyricsColor", "lyricsColor", LyricsColor.Thememode )
        }
        val LYRICS_OUTLINE by lazy {
            Enum( preferences, "HomeAlbumType", "albumType", LyricsOutline.None )
        }
        val LYRICS_FONT_SIZE by lazy {
            Enum( preferences, "LyricsFontSize", "lyricsFontSize", LyricsFontSize.Medium )
        }
        val LYRICS_ROMANIZATION_TYPE by lazy {
            Enum( preferences, "LyricsRomanizationType", "romanization", Romanization.Off )
        }
        val LYRICS_BACKGROUND by lazy {
            Enum( preferences, "LyricsBackground", "lyricsBackground", LyricsBackground.Black )
        }
        val LYRICS_HIGHLIGHT by lazy {
            Enum( preferences, "LyricsHighlight", "lyricsHighlight", LyricsHighlight.None )
        }
        val LYRICS_ALIGNMENT by lazy {
            Enum( preferences, "LyricsAlignment", "lyricsAlignment", LyricsAlignment.Center )
        }
        val LYRICS_SHOW_THUMBNAIL by lazy {
            Boolean( preferences, "LyricsShowThumbnail", "showlyricsthumbnail", false )
        }
        val LYRICS_JUMP_ON_TAP by lazy {
            Boolean( preferences, "LyricsJumpOnTap", "clickOnLyricsText", true )
        }
        val LYRICS_SHOW_ACCENT_BACKGROUND by lazy {
            Boolean( preferences, "LyricsShowAccentBackground", "showBackgroundLyrics", false )
        }
        val LYRICS_SYNCHRONIZED by lazy {
            Boolean( preferences, "LyricsSynchronized", "isShowingSynchronizedLyrics", false )
        }
        val LYRICS_SHOW_SECOND_LINE by lazy {
            Boolean( preferences, "LyricsShowSecondLine", "showSecondLine", false )
        }
        val LYRICS_ANIMATE_SIZE by lazy {
            Boolean( preferences, "LyricsAnimateSize", "lyricsSizeAnimate", false )
        }
        val LYRICS_LANDSCAPE_CONTROLS by lazy {
            Boolean( preferences, "LysricsLandscapeControls", "landscapeControls", true )
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Page type">
        val HOME_ARTIST_TYPE by lazy {
            Enum( preferences, "HomeArtistType", "artistType", ArtistsType.Favorites )
        }
        val HOME_ALBUM_TYPE by lazy {
            Enum( preferences, "HomeAlbumType", "albumType", AlbumsType.Favorites )
        }
        val HOME_SONGS_TYPE by lazy {
            Enum( preferences, "HomeSongsType", "builtInPlaylist", BuiltInPlaylist.Favorites )
        }
        val HISTORY_PAGE_TYPE by lazy {
            Enum( preferences, "HistoryPageType", "historyType", HistoryType.History )
        }
        val HOME_LIBRARY_TYPE by lazy {
            Enum( preferences, "HomePlaylistType", "playlistType", PlaylistsType.Playlist )
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Audio">
        val AUDIO_FADE_DURATION by lazy {
            Enum( preferences, "AudioFadeDuration", "playbackFadeAudioDuration", DurationInMilliseconds.Disabled )
        }
        val AUDIO_QUALITY by lazy {
            Enum( preferences, "AudioQuality", "audioQualityFormat", AudioQualityFormat.Auto )
        }
        val AUDIO_REVERB_PRESET by lazy {
            Enum( preferences, "AudioReverbPreset", "audioReverbPreset", PresetsReverb.NONE )
        }
        val AUDIO_SKIP_SILENCE by lazy {
            Boolean( preferences, "AudioSkipSilence", "skipSilence", false )
        }
        val AUDIO_SKIP_SILENCE_LENGTH by lazy {
            Long( preferences, "AudioSkipSilenceLength", "minimumSilenceDuration", 0L )
        }
        val AUDIO_VOLUME_NORMALIZATION by lazy {
            Boolean( preferences, "AudioVolumeNormalization", "volumeNormalization", false )
        }
        val AUDIO_VOLUME_NORMALIZATION_TARGET by lazy {
            Float( preferences, "AudioVolumeNormalizationTarget", "loudnessBaseGain", 5F )
        }
        val AUDIO_SHAKE_TO_SKIP by lazy {
            Boolean( preferences, "AudioShakeToSkip", "shakeEventEnabled", false )
        }
        val AUDIO_VOLUME_BUTTONS_CHANGE_SONG by lazy {
            Boolean( preferences, "AudioVolumeButtonsChangeSong", "useVolumeKeysToChangeSong", false )
        }
        val AUDIO_BASS_BOOSTED by lazy {
            Boolean( preferences, "AudioBassBoosted", "bassboostEnabled", false )
        }
        val AUDIO_BASS_BOOST_LEVEL by lazy {
            Float( preferences, "AudioBassBoostLevel", "bassboostLevel", .5F )
        }
        val AUDIO_SMART_PAUSE_DURING_CALLS by lazy {
            Boolean( preferences, "AudioSmartPauseDuringCalls", "handleAudioFocusEnabled", true )
        }
        val AUDIO_SPEED by lazy {
            Boolean( preferences, "AudioSpeed", "showPlaybackSpeedButton", false )
        }
        val AUDIO_SPEED_VALUE by lazy {
            Float( preferences, "AudioSpeedValue", "playbackSpeed", 1F )
        }
        val AUDIO_PITCH by lazy {
            Float( preferences, "AudioPitch", "playbackPitch", 1F )
        }
        val AUDIO_VOLUME by lazy {
            Float( preferences, "AudioVolume", "playbackVolume", .5F )
        }
        val AUDIO_DEVICE_VOLUME by lazy {
            Float( preferences, "AudioDeviceVolume", "playbackDeviceVolume", getDeviceVolume( appContext() ) )
        }
        val AUDIO_MEDLEY_DURATION by lazy {
            Float( preferences, "AudioMedleyDuration", "playbackDuration", 0F )
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="YouTube">
        val YOUTUBE_LOGIN by lazy {
            Boolean( preferences, "YouTubeLogin", "enableYoutubeLogin", false )
        }
        val YOUTUBE_PLAYLISTS_SYNC by lazy {
            Boolean( preferences, "YouTubePlaylistsSync", "enableYoutubeSync", false )
        }
        val YOUTUBE_ARTISTS_SYNC by lazy {
            Boolean(preferences, "YouTubeArtistsSync", "", false)
        }
        val YOUTUBE_ALBUMS_SYNC by lazy {
            Boolean(preferences, "YouTubeAlbumsSync", "", false)
        }
        val YOUTUBE_VISITOR_DATA by lazy {
            String( encryptedPreferences, "YouTubeVisitorData", "ytVisitorData", Constants.CHROME_WINDOWS_VISITOR_DATA )
        }
        val YOUTUBE_SYNC_ID by lazy {
            String( encryptedPreferences, "YouTubeSyncId", "ytDataSyncIdKey", "" )
        }
        val YOUTUBE_COOKIES by lazy {
            String( encryptedPreferences, "YouTubeCookies", "ytCookie", "" )
        }
        val YOUTUBE_ACCOUNT_NAME by lazy {
            String( encryptedPreferences, "YouTubeAccountName", "ytAccountNameKey", "" )
        }
        val YOUTUBE_ACCOUNT_EMAIL by lazy {
            String( encryptedPreferences, "YouTubeAccountEmail", "ytAccountEmailKey", "" )
        }
        val YOUTUBE_SELF_CHANNEL_HANDLE by lazy {
            String( encryptedPreferences, "YouTubeSelfChannelHandle", "ytAccountChannelHandleKey", "" )
        }
        val YOUTUBE_ACCOUNT_AVATAR by lazy {
            String( encryptedPreferences, "YouTubeAccountAvatar", "ytAccountThumbnailKey", "" )
        }
        val YOUTUBE_LAST_VIDEO_ID by lazy {
            String( preferences, "YouTubeLastVideoId", "lastVideoId", "" )
        }
        val YOUTUBE_LAST_VIDEO_SECONDS by lazy {
            Float( preferences, "YouTubeLastVideoSeconds", "lastVideoSeconds", 0F )
        }
        //</editor-fold>
        //<editor-fold desc="Quick picks">
        val QUICK_PICKS_TYPE by lazy {
            Enum( preferences, "QuickPicksType", "playEventsType", PlayEventsType.MostPlayed )
        }
        val QUICK_PICKS_MIN_DURATION by lazy {
            Enum( preferences, "QuickPicksMinDuration", "exoPlayerMinTimeForEvent", ExoPlayerMinTimeForEvent.`20s` )
        }
        val QUICK_PICKS_SHOW_TIPS by lazy {
            Boolean( preferences, "QuickPicksShowTips", "showTips", true )
        }
        val QUICK_PICKS_SHOW_RELATED_ALBUMS by lazy {
            Boolean( preferences, "QuickPicksShowRelatedAlbums", "showRelatedAlbums", true )
        }
        val QUICK_PICKS_SHOW_RELATED_ARTISTS by lazy {
            Boolean( preferences, "QuickPicksShowRelatedArtists", "showSimilarArtists", true )
        }
        val QUICK_PICKS_SHOW_NEW_ALBUMS_ARTISTS by lazy {
            Boolean( preferences, "QuickPicksShowNewAlbumsArtists", "showNewAlbumsArtists", true )
        }
        val QUICK_PICKS_SHOW_NEW_ALBUMS by lazy {
            Boolean( preferences, "QuickPicksShowNewAlbums", "showNewAlbums", true )
        }
        val QUICK_PICKS_SHOW_MIGHT_LIKE_PLAYLISTS by lazy {
            Boolean( preferences, "QuickPicksShowPlaylists", "showPlaylistMightLike", true )
        }
        val QUICK_PICKS_SHOW_MOODS_AND_GENRES by lazy {
            Boolean( preferences, "QuickPicksShowMoodsAndGenres", "showMoodsAndGenres", true )
        }
        val QUICK_PICKS_SHOW_MONTHLY_PLAYLISTS by lazy {
            Boolean( preferences, "QuickPicksShowMonthlyPlaylists", "showMonthlyPlaylistInQuickPicks", true )
        }
        val QUICK_PICKS_SHOW_CHARTS by lazy {
            Boolean( preferences, "QuickPicksShowCharts", "showCharts", true )
        }
        val QUICK_PICKS_PAGE by lazy {
            Boolean( preferences, "QuickPicksPage", "enableQuickPicksPage", true )
        }
        //</editor-fold>
        //<editor-fold desc="Discord">
        val DISCORD_LOGIN by lazy {
            Boolean( preferences, "DiscordLogin", "isDiscordPresenceEnabled", false )
        }
        val DISCORD_ACCESS_TOKEN by lazy {
            String( encryptedPreferences, "DiscordPersonalAccessToken", "", "" )
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Proxy">
        val IS_PROXY_ENABLED by lazy {
            Boolean( preferences, "IsProxyEnabled", "isProxyEnabled", false )
        }
        val PROXY_SCHEME by lazy {
            Enum( preferences, "ProxyScheme", "ProxyMode", Proxy.Type.HTTP )
        }
        val PROXY_HOST by lazy {
            String( preferences, "ProxyHost", "proxyHostnameKey", "" )
        }
        val PROXY_PORT  by lazy {
            Int( preferences, "ProxyPort", "proxyPort", 1080 )
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="DoH">
        val DOH_SERVER by lazy {
            Enum(preferences, "DnsOverHttpsServer", "", DohServer.NONE)
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Custom light colors">
        val CUSTOM_LIGHT_THEME_BACKGROUND_0 by lazy {
            Color(preferences, "CustomLightThemeBackground0", "customThemeLight_Background0", DefaultLightColorPalette.background0)
        }
        val CUSTOM_LIGHT_THEME_BACKGROUND_1 by lazy {
            Color(preferences, "CustomLightThemeBackground1", "customThemeLight_Background1", DefaultLightColorPalette.background1)
        }
        val CUSTOM_LIGHT_THEME_BACKGROUND_2 by lazy {
            Color(preferences, "CustomLightThemeBackground2", "customThemeLight_Background2", DefaultLightColorPalette.background2)
        }
        val CUSTOM_LIGHT_THEME_BACKGROUND_3 by lazy {
            Color(preferences, "CustomLightThemeBackground3", "customThemeLight_Background3", DefaultLightColorPalette.background3)
        }
        val CUSTOM_LIGHT_THEME_BACKGROUND_4 by lazy {
            Color(preferences, "CustomLightThemeBackground4", "customThemeLight_Background4", DefaultLightColorPalette.background4)
        }
        val CUSTOM_LIGHT_TEXT by lazy {
            Color(preferences, "CustomLightThemeText", "customThemeLight_Text", DefaultLightColorPalette.text)
        }
        val CUSTOM_LIGHT_TEXT_SECONDARY by lazy {
            Color(preferences, "CustomLightThemeTextSecondary", "customThemeLight_textSecondary", DefaultLightColorPalette.textSecondary)
        }
        val CUSTOM_LIGHT_TEXT_DISABLED by lazy {
            Color(preferences, "CustomLightThemeTextDisabled", "customThemeLight_textDisabled", DefaultLightColorPalette.textDisabled)
        }
        val CUSTOM_LIGHT_PLAY_BUTTON by lazy {
            Color(preferences, "CustomLightThemePlayButton", "customThemeLight_iconButtonPlayer", DefaultLightColorPalette.iconButtonPlayer)
        }
        val CUSTOM_LIGHT_ACCENT by lazy {
            Color(preferences, "CustomLightThemeAccent", "customThemeLight_accent", DefaultLightColorPalette.accent)
        }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Custom dark theme">
        val CUSTOM_DARK_THEME_BACKGROUND_0 by lazy {
            Color(preferences, "CustomDarkThemeBackground0", "customThemeDark_Background0", DefaultDarkColorPalette.background0)
        }
        val CUSTOM_DARK_THEME_BACKGROUND_1 by lazy {
            Color(preferences, "CustomDarkThemeBackground1", "customThemeDark_Background1", DefaultDarkColorPalette.background1)
        }
        val CUSTOM_DARK_THEME_BACKGROUND_2 by lazy {
            Color(preferences, "CustomDarkThemeBackground2", "customThemeDark_Background2", DefaultDarkColorPalette.background2)
        }
        val CUSTOM_DARK_THEME_BACKGROUND_3 by lazy {
            Color(preferences, "CustomDarkThemeBackground3", "customThemeDark_Background3", DefaultDarkColorPalette.background3)
        }
        val CUSTOM_DARK_THEME_BACKGROUND_4 by lazy {
            Color(preferences, "CustomDarkThemeBackground4", "customThemeDark_Background4", DefaultDarkColorPalette.background4)
        }
        val CUSTOM_DARK_TEXT by lazy {
            Color(preferences, "CustomDarkThemeText", "customThemeDark_Text", DefaultDarkColorPalette.text)
        }
        val CUSTOM_DARK_TEXT_SECONDARY by lazy {
            Color(preferences, "CustomDarkThemeTextSecondary", "customThemeDark_textSecondary", DefaultDarkColorPalette.textSecondary)
        }
        val CUSTOM_DARK_TEXT_DISABLED by lazy {
            Color(preferences, "CustomDarkThemeTextDisabled", "customThemeDark_textDisabled", DefaultDarkColorPalette.textDisabled)
        }
        val CUSTOM_DARK_PLAY_BUTTON by lazy {
            Color(preferences, "CustomDarkThemePlayButton", "customThemeDark_iconButtonPlayer", DefaultDarkColorPalette.iconButtonPlayer)
        }
        val CUSTOM_DARK_ACCENT by lazy {
            Color(preferences, "CustomDarkThemeAccent", "customThemeDark_accent", DefaultDarkColorPalette.accent)
        }
        //</editor-fold>
        //<editor-fold desc="Logging">
        val RUNTIME_LOG by lazy {
            Boolean(preferences, "DebugLog", "logDebugEnabled", false)
        }
        val RUNTIME_LOG_SHARED by lazy {
            Boolean(preferences, "DebugLogShared", "", true)
        }
        val RUNTIME_LOG_LEVEL by lazy {
            Int(preferences, "DebugLogLevel", "", Log.INFO)
        }
        val RUNTIME_LOG_FILE_COUNT by lazy {
            Int(preferences, "DebugLogFileCount", "", 5)
        }
        val RUNTIME_LOG_MAX_SIZE_PER_FILE by lazy {
            Long(preferences, "DebugLogMaxSizePerFile", "", 5L * 1024 * 1024)   // 5 Mb
        }
        //</editor-fold>
        //<editor-fold desc="Platform indicator">
        val ALBUMS_PLATFORM_INDICATOR by lazy {
            Enum(preferences, "AlbumPlatformIndicator", "", PlatformIndicatorType.ICON)
        }
        val ARTISTS_PLATFORM_INDICATOR by lazy {
            Enum(preferences, "ArtistsPlatformIndicator", "", PlatformIndicatorType.ICON)
        }
        val PLAYLISTS_PLATFORM_INDICATOR by lazy {
            Enum(preferences, "PlaylistPlatformIndicator", "", PlatformIndicatorType.ICON)
        }
        //</editor-fold>
        //<editor-fold desc="Thumbnail roundness">
        val SONG_THUMBNAIL_ROUNDNESS_PERCENT by lazy {
            Int(preferences, "SongThumbnailRoundnessPercent", "", 0)
        }
        val ALBUM_THUMBNAIL_ROUNDNESS_PERCENT by lazy {
            Int(preferences, "AlbumThumbnailRoundnessPercent", "", 10)
        }
        val ARTIST_THUMBNAIL_ROUNDNESS_PERCENT by lazy {
            Int(preferences, "ArtistThumbnailRoundnessPercent", "", 50)
        }
        val PLAYLIST_THUMBNAIL_ROUNDNESS_PERCENT by lazy {
            Int(preferences, "PlaylistThumbnailRoundnessPercent", "", 10)
        }
        //</editor-fold>

        val HOME_SONGS_TOP_PLAYLIST_PERIOD by lazy {
            Enum( preferences, "HomeSongsTopPlaylistPeriod", "", StatisticsType.All )
        }
        val MENU_STYLE by lazy {
            Enum( preferences, "MenuStyle", "menuStyle", MenuStyle.List )
        }
        val MAIN_THEME by lazy {
            Enum( preferences, "MainTheme", "UiType", UiType.RiMusic )
        }
        val COLOR_PALETTE by lazy {
            Enum( preferences, "ColorPalette", "colorPaletteName", ColorPaletteName.Dynamic )
        }
        val THEME_MODE by lazy {
            Enum( preferences, "ThemeMode", "colorPaletteMode", ColorPaletteMode.Dark )
        }
        val STARTUP_SCREEN by lazy {
            Enum( preferences, "StartupScreen", "indexNavigationTab", HomeScreenTabs.Songs )
        }
        val FONT by lazy {
            Enum( preferences, "Font", "fontType", FontType.Rubik )
        }
        val NAVIGATION_BAR_POSITION by lazy {
            Enum( preferences, "NavigationBarPosition", "navigationBarPosition", NavigationBarPosition.Bottom )
        }
        val NAVIGATION_BAR_TYPE by lazy {
            Enum( preferences, "NavigationBarType", "navigationBarType", NavigationBarType.IconAndText )
        }
        val PAUSE_BETWEEN_SONGS by lazy {
            Enum( preferences, "PauseBetweenSongs", "pauseBetweenSongs", PauseBetweenSongs.`0` )
        }
        val THUMBNAIL_BORDER_RADIUS by lazy {
            Enum( preferences, "ThumbnailBorderRadius", "thumbnailRoundness", ThumbnailRoundness.Heavy )
        }
        val TRANSITION_EFFECT by lazy {
            Enum( preferences, "TransitionEffect", "transitionEffect", TransitionEffect.Scale )
        }
        val LIMIT_SONGS_WITH_DURATION by lazy {
            Enum( preferences, "LimitSongsWithDuration", "excludeSongsWithDurationLimit", DurationInMinutes.Disabled )
        }
        val QUEUE_TYPE by lazy {
            Enum( preferences, "QueueType", "queueType", QueueType.Essential )
        }
        val QUEUE_LOOP_TYPE by lazy {
            Enum( preferences, "QueueLoopType", "queueLoopType", QueueLoopType.Default )
        }
        val QUEUE_AUTO_APPEND by lazy {
            Boolean( preferences, "QueueAutoAppend", "autoLoadSongsInQueue", true )
        }
        val CAROUSEL_SIZE by lazy {
            Enum( preferences, "CarouselSize", "carouselSize", CarouselSize.Biggest )
        }
        val THUMBNAIL_TYPE by lazy {
            Enum( preferences, "ThumbnailType", "thumbnailType", ThumbnailType.Modern )
        }
        val LIKE_ICON by lazy {
            Enum( preferences, "LikeIcon", "iconLikeType", IconLikeType.Essential )
        }
        val LIVE_WALLPAPER by lazy {
            Enum(preferences, "LiveWallpaper", "", WallpaperType.DISABLED)
        }
        val LIVE_WALLPAPER_RESET_DURATION by lazy {
            Long(preferences, "LiveWallpaperResetDuration", "", -1L)
        }
        val ANIMATED_GRADIENT by lazy {
            Enum( preferences, "AnimatedGradient", "animatedGradient", AnimatedGradient.Linear )
        }
        val NOW_PLAYING_INDICATOR by lazy {
            Enum( preferences, "NowPlayingIndicator", "nowPlayingIndicator", MusicAnimationType.Bubbles )
        }
        val PIP_MODULE by lazy {
            Enum( preferences, "PipModule", "pipModule", PipModule.Cover )
        }
        val CHECK_UPDATE by lazy {
            Enum( preferences, "CheckUpdateState", "checkUpdateState", CheckUpdateState.DISABLED )
        }
        val SHOW_CHECK_UPDATE_STATUS by lazy {
            Boolean( preferences, "ShowNoUpdateAvailableMessage", "", true )
        }
        val APP_LANGUAGE by lazy {
            Enum( preferences, "AppLanguage", "languageApp", Language.SYSTEM )
        }
        val OTHER_APP_LANGUAGE by lazy {
            Enum( preferences, "OtherAppLanguage", "otherLanguageApp", Language.SYSTEM )
        }
        val APP_REGION by lazy {
            String( preferences, "AppRegion", "", getSystemCountryCode() )
        }
        val HOME_ARTIST_AND_ALBUM_FILTER by lazy {
            Enum( preferences, "filterBy", "", FilterBy.All )
        }
        val STATISTIC_PAGE_CATEGORY by lazy {
            Enum( preferences, "StatisticPageCategory", "statisticsCategory", StatisticsCategory.Songs )
        }
        val MARQUEE_TEXT_EFFECT by lazy {
            Boolean(preferences, "MarqueeEffect", "", true )
        }
        val PARENTAL_CONTROL by lazy {
            Boolean( preferences, "ParentalControl", "parentalControlEnabled", false )
        }
        val ROTATION_EFFECT by lazy {
            Boolean( preferences, "RotationEffect", "effectRotation", true )
        }
        val TRANSPARENT_TIMELINE by lazy {
            Boolean( preferences, "TransparentTimeline", "transparentbar", true )
        }
        val BLACK_GRADIENT by lazy {
            Boolean( preferences, "BlackGradient", "blackgradient", false )
        }
        val TEXT_OUTLINE by lazy {
            Boolean( preferences, "TextOutline", "textoutline", false )
        }
        val SHOW_FLOATING_ICON by lazy {
            Boolean( preferences, "ShowFloatingIcon", "showFloatingIcon", false )
        }
        val FLOATING_ICON_X_OFFSET by lazy {
            Float( preferences, "FloatingIconXOffset", "floatActionIconOffsetX", 0F )
        }
        val FLOATING_ICON_Y_OFFSET by lazy {
            Float( preferences, "FloatingIconYOffset", "floatActionIconOffsetY", 0F )
        }
        val MULTI_FLOATING_ICON_X_OFFSET by lazy {
            Float( preferences, "MultiFloatingIconXOffset", "multiFloatActionIconOffsetX", 0F )
        }
        val MULTI_FLOATING_ICON_Y_OFFSET by lazy {
            Float( preferences, "MultiFloatingIconYOffset", "multiFloatActionIconOffsetY", 0F )
        }
        val ZOOM_OUT_ANIMATION by lazy {
            Boolean( preferences, "ZoomOutAnimation", "buttonzoomout", false )
        }
        val ENABLE_DISCOVER by lazy {
            Boolean( preferences, "EnableDiscover", "discover", false )
        }
        val ENABLE_PERSISTENT_QUEUE by lazy {
            Boolean( preferences, "EnablePersistentQueue", "persistentQueue", false )
        }
        val RESUME_PLAYBACK_ON_STARTUP by lazy {
            Boolean( preferences, "ResumePlaybackOnStartup", "resumePlaybackOnStart", false )
        }
        val RESUME_PLAYBACK_WHEN_CONNECT_TO_AUDIO_DEVICE by lazy {
            Boolean( preferences, "ResumePlaybackWhenConnectToAudioDevice", "resumePlaybackWhenDeviceConnected", false )
        }
        val CLOSE_APP_ON_BACK by lazy {
            Boolean( preferences, "CloseAppOnBack", "closeWithBackButton", true )
        }
        val PLAYBACK_SKIP_ON_ERROR by lazy {
            Boolean( preferences, "PlaybackSkipOnError", "skipMediaOnError", false )
        }
        val USE_SYSTEM_FONT by lazy {
            Boolean( preferences, "UseSystemFont", "useSystemFont", false )
        }
        val APPLY_FONT_PADDING by lazy {
            Boolean( preferences, "ApplyFontPadding", "applyFontPadding", false )
        }
        val SHOW_SEARCH_IN_NAVIGATION_BAR by lazy {
            Boolean( preferences, "ShowSearchInNavigationBar", "showSearchTab", false )
        }
        val SHOW_STATS_IN_NAVIGATION_BAR by lazy {
            Boolean( preferences, "ShowStatsInNavigationBar", "showStatsInNavbar", false )
        }
        val SHOW_LISTENING_STATS by lazy {
            Boolean( preferences, "ShowListeningStats", "showStatsListeningTime", true )
        }
        val HOME_SONGS_SHOW_FAVORITES_CHIP by lazy {
            Boolean( preferences, "HomeSongsShowFavoritesChip", "showFavoritesPlaylist", true )
        }
        val HOME_SONGS_SHOW_CACHED_CHIP by lazy {
            Boolean( preferences, "HomeSongsShowCachedChip", "showCachedPlaylist", true )
        }
        val HOME_SONGS_SHOW_DOWNLOADED_CHIP by lazy {
            Boolean( preferences, "HomeSongsShowDownloadedChip", "showDownloadedPlaylist", true )
        }
        val HOME_SONGS_SHOW_MOST_PLAYED_CHIP by lazy {
            Boolean( preferences, "HomeSongsShowMostPlayedChip", "showMyTopPlaylist", true )
        }
        val HOME_SONGS_SHOW_ON_DEVICE_CHIP by lazy {
            Boolean( preferences, "HomeSongsShowOnDeviceChip", "showOnDevicePlaylist", true )
        }
        val HOME_SONGS_ON_DEVICE_SHOW_FOLDERS by lazy {
            Boolean( preferences, "HomeSongsOnDeviceShowFolders", "showFoldersOnDevice", true )
        }
        val HOME_SONGS_INCLUDE_ON_DEVICE_IN_ALL by lazy {
            Boolean( preferences, "HomeSongsIncludeOnDeviceInAll", "includeLocalSongs", false )
        }
        val MONTHLY_PLAYLIST_COMPILATION by lazy {
            Boolean( preferences, "MonthlyPlaylistCompilation", "enableCreateMonthlyPlaylists", true )
        }
        val SHOW_MONTHLY_PLAYLISTS by lazy {
            Boolean( preferences, "ShowMonthlyPlaylists", "showMonthlyPlaylists", true )
        }
        val SHOW_PINNED_PLAYLISTS by lazy {
            Boolean( preferences, "ShowPinnedPlaylists", "showPinnedPlaylists", true )
        }
        val SHOW_PLAYLIST_INDICATOR by lazy {
            Boolean( preferences, "ShowPlaylistIndicator", "playlistindicator", false )
        }
        val PAUSE_WHEN_VOLUME_SET_TO_ZERO by lazy {
            Boolean( preferences, "PauseWhenVolumeSetToZero", "isPauseOnVolumeZeroEnabled", false )
        }
        val PAUSE_HISTORY by lazy {
            Boolean( preferences, "PauseHistory", "pauseListenHistory", false )
        }
        val IS_PIP_ENABLED by lazy {
            Boolean( preferences, "IsPiPEnabled", "enablePicturInPicture", false )
        }
        val IS_AUTO_PIP_ENABLED by lazy {
            Boolean( preferences, "IsAutoPiPEnabled", "enablePicturInPictureAuto", false )
        }
        val AUTO_DOWNLOAD by lazy {
            Boolean( preferences, "AutoDownload", "autoDownloadSong", false )
        }
        val AUTO_DOWNLOAD_ON_LIKE by lazy {
            Boolean( preferences, "AutoDownloadOnLike", "autoDownloadSongWhenLiked", false )
        }
        val AUTO_DOWNLOAD_ON_ALBUM_BOOKMARKED by lazy {
            Boolean( preferences, "AutoDownloadOnAlbumBookmarked", "autoDownloadSongWhenAlbumBookmarked", false )
        }
        val KEEP_SCREEN_ON by lazy {
            Boolean( preferences, "KeepScreenOn", "isKeepScreenOnEnabled", false )
        }
        val AUTO_SYNC by lazy {
            Boolean( preferences, "AutoSync", "autosync", false )
        }
        val PAUSE_SEARCH_HISTORY by lazy {
            Boolean( preferences, "PauseSearchHistory", "pauseSearchHistory", false )
        }
        val IS_DATA_KEY_LOADED by lazy {
            Boolean( preferences, "IsDataKeyLoaded", "loadedData", false )
        }
        val LOCAL_PLAYLIST_SMART_RECOMMENDATION by lazy {
            Boolean( preferences, "LocalPlaylistSmartRecommendation", "isRecommendationEnabled", false )
        }
        val IS_CONNECTION_METERED by lazy {
            Boolean( preferences, "IsConnectionMetered", "isConnectionMeteredEnabled", true )
        }
        val SMART_REWIND by lazy {
            Float(preferences, "SmartRewind", "", 3f)
        }
        val LOCAL_SONGS_FOLDER by lazy {
            String( preferences, "LocalSongsFolder", "defaultFolder", "/" )
        }
        val SEEN_CHANGELOGS_VERSION by lazy {
            String( preferences, "SeenChangelogsVersion", "seenChangelogsVersionKey", "" )
        }
        val CUSTOM_COLOR by lazy {
            Color(preferences, "CustomColorHashCode", "customColor", androidx.compose.ui.graphics.Color.Green)
        }
        val SEARCH_RESULTS_TAB_INDEX by lazy {
            Int( preferences, "SearchResultsTabIndex", "searchResultScreenTabIndex", 0 )
        }
        val HOME_TAB_INDEX by lazy {
            Int( preferences, "HomeTabIndex", "homeScreenTabIndex", 0 )
        }
        val ARTIST_SCREEN_TAB_INDEX  by lazy {
            Int( preferences, "ArtistScreenTabIndex", "artistScreenTabIndex", 0 )
        }
        val SINGLE_BACK_FROM_SEARCH by lazy {
            Boolean(preferences, "SingleBackFromSearch", "", true)
        }
        val SONG_EMPTY_DURATION_PLACEHOLDER by lazy {
            Boolean(preferences, "SongEmptyDurationPlaceholder", "", false)
        }
        val ACTIVE_PROFILE by lazy {
            String(profilePreferences, "ActiveProfile", "", "default")
        }

        fun isLoggedInToDiscord(): kotlin.Boolean =
            DISCORD_LOGIN.value && DISCORD_ACCESS_TOKEN.value.isNotBlank()

        /**
         * Initialize needed properties for settings to use.
         *
         * **ATTENTION**: Must be call as early as possible to prevent
         * because all preference require [preferences] to be initialized
         * to work.
         */
        fun load(profilePreferences: SharedPreferences, preferences: SharedPreferences, encryptedPreferences: SharedPreferences ) {
            // Only set once to prevent unwanted injection
            if ( !::profilePreferences.isInitialized )
                this.profilePreferences = profilePreferences

            // Only set once to prevent unwanted injection
            if( !::preferences.isInitialized )
                this.preferences = preferences

            // Only set once to prevent unwanted injection
            if( !::encryptedPreferences.isInitialized )
                this.encryptedPreferences = encryptedPreferences
        }

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

    /**
     * How old and new value are processed
     */
    protected abstract val policy: SnapshotMutationPolicy<T>

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

        override var value: E by mutableStateOf(
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

        override var value: kotlin.String by mutableStateOf(
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

        override var value: Set<kotlin.String> by mutableStateOf(
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

        override var value: kotlin.Int by mutableStateOf(
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

        override var value: kotlin.Long by mutableStateOf(
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

        override var value: kotlin.Float by mutableStateOf(
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

        override var value: kotlin.Boolean by mutableStateOf(
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
        ): this(sharedPreferences, key, previousKey, Color(ContextCompat.getColor( appContext(), defaultValue )))

        override val policy = StructuralEqualityPolicy()

        override var value: androidx.compose.ui.graphics.Color by mutableStateOf(
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
}