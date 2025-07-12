package app.kreate.android.themed.common.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.section
import app.kreate.android.themed.common.screens.settings.ui.SwipeActionSettings
import app.kreate.android.themed.common.screens.settings.ui.ThemeSettings
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.BackgroundProgress
import it.fast4x.rimusic.enums.CarouselSize
import it.fast4x.rimusic.enums.ColorPaletteName
import it.fast4x.rimusic.enums.IconLikeType
import it.fast4x.rimusic.enums.MiniPlayerType
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.PlayerBackgroundColors
import it.fast4x.rimusic.enums.PlayerControlsType
import it.fast4x.rimusic.enums.PlayerPlayButtonType
import it.fast4x.rimusic.enums.PlayerThumbnailSize
import it.fast4x.rimusic.enums.PlayerTimelineSize
import it.fast4x.rimusic.enums.PlayerTimelineType
import it.fast4x.rimusic.enums.PlayerType
import it.fast4x.rimusic.enums.QueueType
import it.fast4x.rimusic.enums.ThumbnailType
import it.fast4x.rimusic.enums.UiType
import it.fast4x.rimusic.ui.styling.Dimensions

@Composable
fun UiSettings( paddingValues: PaddingValues ) {
    val scrollState = rememberLazyListState()

    val search = remember {
        SettingEntrySearch( scrollState, R.string.user_interface, R.drawable.ui )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.background( colorPalette().background0 )
                           .padding( paddingValues )
                           .fillMaxHeight()
                           .fillMaxWidth(
                               if ( NavigationBarPosition.Right.isCurrent() )
                                   Dimensions.contentWidthRightBar
                               else
                                   1f
                           )
    ) {
        search.ToolBarButton()

        LazyColumn(
            state = scrollState,
            contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer)
        ) {

            section( R.string.user_interface ) {
                var disablePlayerHorizontalSwipe by Preferences.PLAYER_THUMBNAIL_HORIZONTAL_SWIPE_DISABLED
                var playerTimelineType by Preferences.PLAYER_TIMELINE_TYPE
                var visualizerEnabled by Preferences.PLAYER_VISUALIZER
                var playerThumbnailSize by Preferences.PLAYER_PORTRAIT_THUMBNAIL_SIZE
                var thumbnailTapEnabled by Preferences.PLAYER_TAP_THUMBNAIL_FOR_LYRICS
                var showSearchTab by Preferences.SHOW_SEARCH_IN_NAVIGATION_BAR
                var showStatsInNavbar by Preferences.SHOW_STATS_IN_NAVIGATION_BAR
                var navigationBarPosition by Preferences.NAVIGATION_BAR_POSITION
                var showTopActionsBar by Preferences.PLAYER_SHOW_TOP_ACTIONS_BAR
                var playerType by Preferences.PLAYER_TYPE
                var queueType by Preferences.QUEUE_TYPE
                var fadingedge by Preferences.PLAYER_BACKGROUND_FADING_EDGE
                var carousel by Preferences.PLAYER_THUMBNAILS_CAROUSEL
                var carouselSize by Preferences.CAROUSEL_SIZE
                var thumbnailType by Preferences.THUMBNAIL_TYPE
                var playerTimelineSize by Preferences.PLAYER_TIMELINE_SIZE
                var playerInfoShowIcons by Preferences.PLAYER_SONG_INFO_ICON
                var miniPlayerType by Preferences.MINI_PLAYER_TYPE
                var playerSwapControlsWithTimeline by Preferences.PLAYER_IS_CONTROL_AND_TIMELINE_SWAPPED
                var transparentBackgroundActionBarPlayer by Preferences.PLAYER_TRANSPARENT_ACTIONS_BAR
                var playerControlsType by Preferences.PLAYER_CONTROLS_TYPE
                var playerPlayButtonType by Preferences.PLAYER_PLAY_BUTTON_TYPE
                var buttonzoomout by Preferences.ZOOM_OUT_ANIMATION
                var iconLikeType by Preferences.LIKE_ICON
                var playerBackgroundColors by Preferences.PLAYER_BACKGROUND
                var blackgradient by Preferences.BLACK_GRADIENT
                var showTotalTimeQueue by Preferences.PLAYER_SHOW_TOTAL_QUEUE_TIME
                var showRemainingSongTime by Preferences.PLAYER_SHOW_SONGS_REMAINING_TIME
                var showNextSongsInPlayer by Preferences.PLAYER_SHOW_NEXT_IN_QUEUE
                var disableScrollingText by Preferences.SCROLLING_TEXT_DISABLED
                var effectRotationEnabled by Preferences.ROTATION_EFFECT
                var clickLyricsText by Preferences.LYRICS_JUMP_ON_TAP
                var playerEnableLyricsPopupMessage by Preferences.PLAYER_ACTION_LYRICS_POPUP_MESSAGE
                var backgroundProgress by Preferences.MINI_PLAYER_PROGRESS_BAR
                var actionspacedevenly by Preferences.PLAYER_ACTION_BUTTONS_SPACED_EVENLY
                var tapqueue by Preferences.PLAYER_ACTIONS_BAR_TAP_TO_OPEN_QUEUE
                var swipeUpQueue by Preferences.PLAYER_ACTIONS_BAR_SWIPE_UP_TO_OPEN_QUEUE
                var showButtonPlayerDiscover by Preferences.PLAYER_ACTION_DISCOVER
                var showButtonPlayerDownload by Preferences.PLAYER_ACTION_DOWNLOAD
                var showButtonPlayerAddToPlaylist by Preferences.PLAYER_ACTION_ADD_TO_PLAYLIST
                var showButtonPlayerLoop by Preferences.PLAYER_ACTION_LOOP
                var showButtonPlayerShuffle by Preferences.PLAYER_ACTION_SHUFFLE
                var showButtonPlayerLyrics by Preferences.PLAYER_ACTION_SHOW_LYRICS
                var expandedplayertoggle by Preferences.PLAYER_ACTION_TOGGLE_EXPAND
                var showButtonPlayerSleepTimer by Preferences.PLAYER_ACTION_SLEEP_TIMER
                var showButtonPlayerSystemEqualizer by Preferences.PLAYER_ACTION_OPEN_EQUALIZER
                var showButtonPlayerArrow by Preferences.PLAYER_ACTION_OPEN_QUEUE_ARROW
                var showButtonPlayerMenu by Preferences.PLAYER_ACTION_SHOW_MENU
                var showthumbnail by Preferences.PLAYER_SHOW_THUMBNAIL
                var keepPlayerMinimized by Preferences.PLAYER_KEEP_MINIMIZED

                if( search appearsIn R.string.interface_in_use )
                    SettingComponents.EnumEntry(
                        Preferences.MAIN_THEME,
                        R.string.interface_in_use,
                        action = SettingComponents.Action.RESTART_APP
                    ) {
                        if ( it == UiType.ViMusic ) {
                            disablePlayerHorizontalSwipe = true
                            playerTimelineType = PlayerTimelineType.FakeAudioBar
                            visualizerEnabled = false
                            playerThumbnailSize = PlayerThumbnailSize.Medium
                            thumbnailTapEnabled = true
                            showSearchTab = true
                            showStatsInNavbar = true
                            navigationBarPosition = NavigationBarPosition.Left
                            showTopActionsBar = false
                            playerType = PlayerType.Modern
                            queueType = QueueType.Modern
                            fadingedge = false
                            carousel = true
                            carouselSize = CarouselSize.Medium
                            thumbnailType = ThumbnailType.Essential
                            playerTimelineSize = PlayerTimelineSize.Medium
                            playerInfoShowIcons = true
                            miniPlayerType = MiniPlayerType.Modern
                            playerSwapControlsWithTimeline = false
                            transparentBackgroundActionBarPlayer = false
                            playerControlsType = PlayerControlsType.Essential
                            playerPlayButtonType = PlayerPlayButtonType.Disabled
                            buttonzoomout = true
                            iconLikeType = IconLikeType.Essential
                            playerBackgroundColors = PlayerBackgroundColors.CoverColorGradient
                            blackgradient = true
                            showTotalTimeQueue = false
                            showRemainingSongTime = false
                            showNextSongsInPlayer = false
                            disableScrollingText = false
                            effectRotationEnabled = true
                            clickLyricsText = true
                            playerEnableLyricsPopupMessage = true
                            backgroundProgress = BackgroundProgress.MiniPlayer
                            transparentBackgroundActionBarPlayer = true
                            actionspacedevenly = false
                            tapqueue = false
                            swipeUpQueue = true
                            showButtonPlayerDiscover = false
                            showButtonPlayerDownload = false
                            showButtonPlayerAddToPlaylist = false
                            showButtonPlayerLoop = false
                            showButtonPlayerShuffle = false
                            showButtonPlayerLyrics = false
                            expandedplayertoggle = false
                            showButtonPlayerSleepTimer = false
                            showButtonPlayerSystemEqualizer = false
                            showButtonPlayerArrow = false
                            showButtonPlayerShuffle = false
                            showButtonPlayerMenu = true
                            showthumbnail = true
                            keepPlayerMinimized = false
                        } else {
                            disablePlayerHorizontalSwipe = false
                            navigationBarPosition = NavigationBarPosition.Bottom
                        }
                    }

                if( search appearsIn R.string.theme ) {
                    ThemeSettings( search )
                }
                androidx.compose.animation.AnimatedVisibility(
                    Preferences.COLOR_PALETTE.neither( ColorPaletteName.PureBlack, ColorPaletteName.ModernBlack )
                ) {
                    if( search appearsIn R.string.theme_mode )
                        SettingComponents.EnumEntry(
                            titleId = R.string.theme_mode,
                            preference = Preferences.COLOR_PALETTE
                        )
                }
                if( search appearsIn R.string.navigation_bar_position ) {
                    val subtitleId by remember { derivedStateOf {
                        // Use [Preferences] for observable state (because of [derivedStateOf])
                        if( Preferences.MAIN_THEME.value == UiType.ViMusic )
                            R.string.setting_description_only_available_in_theme
                        else
                            Preferences.NAVIGATION_BAR_POSITION.value.textId
                    }}

                    SettingComponents.EnumEntry(
                        Preferences.NAVIGATION_BAR_POSITION,
                        R.string.navigation_bar_position,
                        isEnabled = UiType.ViMusic.isNotCurrent(),
                        subtitle = stringResource( subtitleId, UiType.RiMusic.text )
                    )
                }
                if( search appearsIn R.string.navigation_bar_type )
                    SettingComponents.EnumEntry(
                        Preferences.NAVIGATION_BAR_TYPE,
                        R.string.navigation_bar_type
                    )
                if( search appearsIn R.string.player_position )
                    SettingComponents.EnumEntry(
                        Preferences.MINI_PLAYER_POSITION,
                        R.string.player_position
                    )
                if( search appearsIn R.string.menu_style )
                    SettingComponents.EnumEntry(
                        Preferences.MENU_STYLE,
                        R.string.menu_style
                    )
                if( search appearsIn R.string.default_page )
                    SettingComponents.EnumEntry(
                        Preferences.STARTUP_SCREEN,
                        R.string.default_page
                    )
                if( search appearsIn R.string.transition_effect )
                    SettingComponents.EnumEntry(
                        Preferences.TRANSITION_EFFECT,
                        R.string.transition_effect
                    )
                if( UiType.ViMusic.isCurrent() && search appearsIn R.string.vimusic_show_search_button_in_navigation_bar )
                    SettingComponents.BooleanEntry(
                        Preferences.SHOW_SEARCH_IN_NAVIGATION_BAR,
                        R.string.vimusic_show_search_button_in_navigation_bar,
                        R.string.vismusic_only_in_left_right_navigation_bar
                    )
                if( UiType.ViMusic.isCurrent() && search appearsIn R.string.show_statistics_in_navigation_bar )
                    SettingComponents.BooleanEntry(
                        Preferences.SHOW_STATS_IN_NAVIGATION_BAR,
                        R.string.show_statistics_in_navigation_bar
                    )
                if( search appearsIn R.string.show_floating_icon )
                    SettingComponents.BooleanEntry(
                        Preferences.SHOW_FLOATING_ICON,
                        R.string.show_floating_icon
                    )
                if( search appearsIn R.string.settings_use_font_type )
                    SettingComponents.EnumEntry(
                        Preferences.FONT,
                        R.string.settings_use_font_type
                    )
                if( search appearsIn R.string.use_system_font )
                    SettingComponents.BooleanEntry(
                        Preferences.USE_SYSTEM_FONT,
                        R.string.use_system_font,
                        R.string.use_font_by_the_system
                    )
                if( search appearsIn R.string.apply_font_padding ) {
                    SettingComponents.BooleanEntry(
                        Preferences.APPLY_FONT_PADDING,
                        R.string.apply_font_padding,
                        R.string.add_spacing_around_texts
                    )
                }
                if( search appearsIn R.string.swipe_to_action ) {
                    SwipeActionSettings( search )
                }
            }
            section( R.string.songs ) {
                val showFavorites = stringResource( R.string.setting_entry_show_chip, stringResource( R.string.favorites ) )
                if( search appearsIn showFavorites )
                    SettingComponents.BooleanEntry(
                        Preferences.HOME_SONGS_SHOW_FAVORITES_CHIP,
                        showFavorites
                    )

                val showCached = stringResource( R.string.setting_entry_show_chip, stringResource( R.string.cached ) )
                if( search appearsIn showCached )
                    SettingComponents.BooleanEntry(
                        Preferences.HOME_SONGS_SHOW_CACHED_CHIP,
                        showCached
                    )

                val showDownloaded = stringResource( R.string.setting_entry_show_chip, stringResource( R.string.downloaded ) )
                if ( search appearsIn showDownloaded )
                    SettingComponents.BooleanEntry(
                        Preferences.HOME_SONGS_SHOW_DOWNLOADED_CHIP,
                        showDownloaded
                    )

                val topNumber = stringResource( R.string.my_playlist_top, Preferences.MAX_NUMBER_OF_TOP_PLAYED.value )
                val showMostPlayed = stringResource( R.string.setting_entry_show_chip, topNumber )
                if ( search appearsIn showMostPlayed )
                    SettingComponents.BooleanEntry(
                        Preferences.HOME_SONGS_SHOW_MOST_PLAYED_CHIP,
                        showMostPlayed
                    )

                val showOnDevice = stringResource( R.string.setting_entry_show_chip, stringResource( R.string.on_device ) )
                if ( search appearsIn showOnDevice )
                    SettingComponents.BooleanEntry(
                        Preferences.HOME_SONGS_SHOW_ON_DEVICE_CHIP,
                        showOnDevice
                    )
            }
            section( R.string.playlists ) {
                val showPinned = stringResource( R.string.setting_entry_show_chip, stringResource( R.string.pinned_playlists ) )
                if ( search appearsIn showPinned )
                    SettingComponents.BooleanEntry(
                        Preferences.SHOW_PINNED_PLAYLISTS,
                        showPinned
                    )

                val showMonthly = stringResource( R.string.setting_entry_show_chip, stringResource( R.string.monthly_playlists ) )
                if ( search appearsIn showMonthly )
                    SettingComponents.BooleanEntry(
                        Preferences.SHOW_MONTHLY_PLAYLISTS,
                        showMonthly
                    )
            }
            section( R.string.smart_recommendations ) {
                if( search appearsIn R.string.statistics_max_number_of_items )
                    SettingComponents.EnumEntry(
                        Preferences.MAX_NUMBER_OF_SMART_RECOMMENDATIONS,
                        R.string.statistics_max_number_of_items
                    )
            }
            section( R.string.statistics ) {
                if( search appearsIn R.string.statistics_max_number_of_items )
                    SettingComponents.EnumEntry(
                        Preferences.MAX_NUMBER_OF_STATISTIC_ITEMS,
                        R.string.statistics_max_number_of_items
                    )
                if( search appearsIn R.string.listening_time )
                    SettingComponents.BooleanEntry(
                        Preferences.SHOW_LISTENING_STATS,
                        R.string.listening_time,
                        R.string.shows_the_number_of_songs_heard_and_their_listening_time
                    )
            }
            section( R.string.playlist_top ) {
                if( search appearsIn R.string.statistics_max_number_of_items )
                    SettingComponents.EnumEntry(
                        Preferences.MAX_NUMBER_OF_TOP_PLAYED,
                        R.string.statistics_max_number_of_items
                    )
            }
        }
    }
}