package app.kreate.android.themed.common.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.entry
import app.kreate.android.themed.common.component.settings.header
import app.kreate.android.themed.common.screens.settings.ui.SwipeActionSettings
import app.kreate.android.themed.common.screens.settings.ui.themeSettingsSection
import app.kreate.android.themed.rimusic.component.Visual
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.CarouselSize
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.PlayerBackgroundColors
import it.fast4x.rimusic.enums.PlayerThumbnailSize
import it.fast4x.rimusic.enums.PlayerTimelineSize
import it.fast4x.rimusic.enums.PlayerType
import it.fast4x.rimusic.enums.QueueType
import it.fast4x.rimusic.enums.ThumbnailType
import it.fast4x.rimusic.enums.UiType
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import me.knighthat.component.dialog.InputDialogConstraints
import kotlin.math.roundToInt


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThumbnailRoundnessSlider( preference: Preferences.Int ) =
    SettingComponents.SliderEntry(
        preference = preference,
        title = stringResource( R.string.thumbnail_roundness ),
        constraints = InputDialogConstraints.THUMBNAIL_ROUNDNESS_PERCENT,
        valueRange = 0f..50f,
        steps = 11,
        onTextDisplay = {
            // Small problem with floating-point arithmetic is that
            // the number doesn't round to the correct number.
            // [Float.roundToInt()] in this case can be any number besides 5
            // By rounding to the nearest multiple, increment stays at desired number,
            // which is 5 per step.
            val intVal = (it / 5).roundToInt() * 5
            when( intVal ) {
                0       -> stringResource( R.string.shape_rectangle )
                50      -> stringResource( R.string.shape_circle )
                else    -> intVal.toString()
            }
        },
        onValueChangeFinished = { pref, value ->
            pref.value = (value / 5).roundToInt() * 5
        }
    ) {
        val color = LocalAppearance.current.colorPalette.accent
        val shape by remember {derivedStateOf {
            Visual.getShape( preference.value )
        }}

        Box(
            Modifier.size( 40.dp )
                    .border( 2.dp, color, shape )
                    .background( color.copy( .2f ), shape )
        )
    }

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
            header( R.string.user_interface )

            entry( search, R.string.interface_in_use ) {
                SettingComponents.EnumEntry(
                    Preferences.MAIN_THEME,
                    R.string.interface_in_use,
                    action = SettingComponents.Action.RESTART_APP
                ) {
                    if ( it == UiType.ViMusic ) {
                        Preferences.PLAYER_THUMBNAIL_HORIZONTAL_SWIPE_DISABLED.value = true
                        Preferences.PLAYER_TIMELINE_TYPE.reset()
                        Preferences.PLAYER_VISUALIZER.reset()
                        Preferences.PLAYER_PORTRAIT_THUMBNAIL_SIZE.value = PlayerThumbnailSize.Medium
                        Preferences.PLAYER_TAP_THUMBNAIL_FOR_LYRICS.reset()
                        Preferences.SHOW_SEARCH_IN_NAVIGATION_BAR.reset()
                        Preferences.SHOW_STATS_IN_NAVIGATION_BAR.value = true
                        Preferences.NAVIGATION_BAR_POSITION.value = NavigationBarPosition.Left
                        Preferences.PLAYER_SHOW_TOP_ACTIONS_BAR.value = false
                        Preferences.PLAYER_TYPE.value = PlayerType.Modern
                        Preferences.QUEUE_TYPE.value = QueueType.Modern
                        Preferences.PLAYER_BACKGROUND_FADING_EDGE.reset()
                        Preferences.PLAYER_THUMBNAILS_CAROUSEL.reset()
                        Preferences.CAROUSEL_SIZE.value = CarouselSize.Medium
                        Preferences.THUMBNAIL_TYPE.value = ThumbnailType.Essential
                        Preferences.PLAYER_TIMELINE_SIZE.value = PlayerTimelineSize.Medium
                        Preferences.PLAYER_SONG_INFO_ICON.reset()
                        Preferences.MINI_PLAYER_TYPE.reset()
                        Preferences.PLAYER_IS_CONTROL_AND_TIMELINE_SWAPPED.reset()
                        Preferences.PLAYER_CONTROLS_TYPE.reset()
                        Preferences.PLAYER_PLAY_BUTTON_TYPE.reset()
                        Preferences.ZOOM_OUT_ANIMATION.value = true
                        Preferences.LIKE_ICON.reset()
                        Preferences.PLAYER_BACKGROUND.value = PlayerBackgroundColors.CoverColorGradient
                        Preferences.BLACK_GRADIENT.value = true
                        Preferences.PLAYER_SHOW_TOTAL_QUEUE_TIME.value = false
                        Preferences.PLAYER_SHOW_SONGS_REMAINING_TIME.value = false
                        Preferences.PLAYER_SHOW_NEXT_IN_QUEUE.reset()
                        Preferences.MARQUEE_TEXT_EFFECT.reset()
                        Preferences.ROTATION_EFFECT.reset()
                        Preferences.LYRICS_JUMP_ON_TAP.reset()
                        Preferences.PLAYER_ACTION_LYRICS_POPUP_MESSAGE.reset()
                        Preferences.MINI_PLAYER_PROGRESS_BAR.reset()
                        Preferences.PLAYER_TRANSPARENT_ACTIONS_BAR.value = true
                        Preferences.PLAYER_ACTION_BUTTONS_SPACED_EVENLY.reset()
                        Preferences.PLAYER_ACTIONS_BAR_TAP_TO_OPEN_QUEUE.value = false
                        Preferences.PLAYER_ACTIONS_BAR_SWIPE_UP_TO_OPEN_QUEUE.reset()
                        Preferences.PLAYER_ACTION_DISCOVER.reset()
                        Preferences.PLAYER_ACTION_DOWNLOAD.value = false
                        Preferences.PLAYER_ACTION_ADD_TO_PLAYLIST.value = false
                        Preferences.PLAYER_ACTION_LOOP.value = false
                        Preferences.PLAYER_ACTION_SHUFFLE.value = false
                        Preferences.PLAYER_ACTION_SHOW_LYRICS.value = false
                        Preferences.PLAYER_ACTION_TOGGLE_EXPAND.value = false
                        Preferences.PLAYER_ACTION_SLEEP_TIMER.reset()
                        Preferences.PLAYER_ACTION_OPEN_EQUALIZER.reset()
                        Preferences.PLAYER_ACTION_OPEN_QUEUE_ARROW.value = false
                        Preferences.PLAYER_ACTION_SHOW_MENU.value = true
                        Preferences.PLAYER_SHOW_THUMBNAIL.reset()
                        Preferences.PLAYER_KEEP_MINIMIZED.reset()
                    } else {
                        Preferences.PLAYER_THUMBNAIL_HORIZONTAL_SWIPE_DISABLED.reset()
                        Preferences.NAVIGATION_BAR_POSITION.reset()
                    }
                }
            }
            themeSettingsSection( search )
            entry( search, R.string.navigation_bar_position ) {
                val subtitleId by remember { derivedStateOf {
                    // Use [Preferences] for observable state (because of [derivedStateOf])
                    if( Preferences.MAIN_THEME.value == UiType.ViMusic )
                        R.string.setting_description_only_available_in_theme
                    else
                        Preferences.NAVIGATION_BAR_POSITION.value.androidTextId
                }}

                SettingComponents.EnumEntry(
                    Preferences.NAVIGATION_BAR_POSITION,
                    R.string.navigation_bar_position,
                    isEnabled = UiType.ViMusic.isNotCurrent(),
                    subtitle = stringResource( subtitleId, UiType.RiMusic.text )
                )
            }
            entry( search, R.string.navigation_bar_type ) {
                SettingComponents.EnumEntry(
                    Preferences.NAVIGATION_BAR_TYPE,
                    R.string.navigation_bar_type
                )
            }
            entry( search, R.string.player_position ) {
                SettingComponents.EnumEntry(
                    Preferences.MINI_PLAYER_POSITION,
                    R.string.player_position
                )
            }
            entry( search, R.string.menu_style ) {
                SettingComponents.EnumEntry(
                    Preferences.MENU_STYLE,
                    R.string.menu_style
                )
            }
            entry( search, R.string.default_page ) {
                SettingComponents.EnumEntry(
                    Preferences.STARTUP_SCREEN,
                    R.string.default_page
                )
            }
            entry( search, R.string.transition_effect ) {
                SettingComponents.EnumEntry(
                    Preferences.TRANSITION_EFFECT,
                    R.string.transition_effect
                )
            }
            entry(
                search = search,
                titleId = R.string.vimusic_show_search_button_in_navigation_bar,
                additionalCheck = UiType.ViMusic.isCurrent()
            ) {
                SettingComponents.BooleanEntry(
                    Preferences.SHOW_SEARCH_IN_NAVIGATION_BAR,
                    R.string.vimusic_show_search_button_in_navigation_bar,
                    R.string.vismusic_only_in_left_right_navigation_bar
                )
            }
            entry(
                search = search,
                titleId = R.string.show_statistics_in_navigation_bar,
                additionalCheck = UiType.ViMusic.isCurrent()
            ) {
                SettingComponents.BooleanEntry(
                    Preferences.SHOW_STATS_IN_NAVIGATION_BAR,
                    R.string.show_statistics_in_navigation_bar
                )
            }
            entry( search, R.string.show_floating_icon ) {
                SettingComponents.BooleanEntry(
                    Preferences.SHOW_FLOATING_ICON,
                    R.string.show_floating_icon
                )
            }
            entry( search, R.string.settings_use_font_type ) {
                SettingComponents.EnumEntry(
                    Preferences.FONT,
                    R.string.settings_use_font_type
                )
            }
            entry( search, R.string.use_system_font ) {
                SettingComponents.BooleanEntry(
                    Preferences.USE_SYSTEM_FONT,
                    R.string.use_system_font,
                    R.string.use_font_by_the_system
                )
            }
            entry( search, R.string.apply_font_padding ) {
                SettingComponents.BooleanEntry(
                    Preferences.APPLY_FONT_PADDING,
                    R.string.apply_font_padding,
                    R.string.add_spacing_around_texts
                )
            }
            entry( search, R.string.swipe_to_action ) {
                SwipeActionSettings( search )
            }

            header( R.string.songs )
            item {
                val showFavorites = stringResource( R.string.setting_entry_show_chip, stringResource( R.string.favorites ) )
                if( search appearsIn showFavorites )
                    SettingComponents.BooleanEntry(
                        Preferences.HOME_SONGS_SHOW_FAVORITES_CHIP,
                        showFavorites
                    )
            }
            item {
                val showCached = stringResource( R.string.setting_entry_show_chip, stringResource( R.string.cached ) )
                if( search appearsIn showCached )
                    SettingComponents.BooleanEntry(
                        Preferences.HOME_SONGS_SHOW_CACHED_CHIP,
                        showCached
                    )
            }
            item {
                val showDownloaded = stringResource( R.string.setting_entry_show_chip, stringResource( R.string.downloaded ) )
                if ( search appearsIn showDownloaded )
                    SettingComponents.BooleanEntry(
                        Preferences.HOME_SONGS_SHOW_DOWNLOADED_CHIP,
                        showDownloaded
                    )
            }
            item {
                val showMostPlayed = stringResource(
                    R.string.setting_entry_show_chip,
                    stringResource( R.string.my_playlist_top, Preferences.MAX_NUMBER_OF_TOP_PLAYED.value )
                )
                if ( search appearsIn showMostPlayed )
                    SettingComponents.BooleanEntry(
                        Preferences.HOME_SONGS_SHOW_MOST_PLAYED_CHIP,
                        showMostPlayed
                    )
            }
            item {
                val showOnDevice = stringResource( R.string.setting_entry_show_chip, stringResource( R.string.on_device ) )
                if ( search appearsIn showOnDevice )
                    SettingComponents.BooleanEntry(
                        Preferences.HOME_SONGS_SHOW_ON_DEVICE_CHIP,
                        showOnDevice
                    )
            }
            entry( search, R.string.setting_entry_song_empty_duration_placeholder ) {
                SettingComponents.BooleanEntry(
                    preference = Preferences.SONG_EMPTY_DURATION_PLACEHOLDER,
                    title = stringResource( R.string.setting_entry_song_empty_duration_placeholder ),
                    subtitle = stringResource( R.string.setting_description_song_empty_duration_placeholder )
                )
            }
            entry( search, R.string.thumbnail_roundness, "songThumbnailRoundness" ) {
                ThumbnailRoundnessSlider( Preferences.SONG_THUMBNAIL_ROUNDNESS_PERCENT )
            }

            header( R.string.playlists )
            item {
                val showPinned = stringResource( R.string.setting_entry_show_chip, stringResource( R.string.pinned_playlists ) )
                if ( search appearsIn showPinned )
                    SettingComponents.BooleanEntry(
                        Preferences.SHOW_PINNED_PLAYLISTS,
                        showPinned
                    )
            }
            entry( search, R.string.setting_entry_monthly_playlist_compilation ) {
                SettingComponents.BooleanEntry(
                    preference = Preferences.MONTHLY_PLAYLIST_COMPILATION,
                    title = stringResource( R.string.setting_entry_monthly_playlist_compilation ),
                    subtitle = stringResource( R.string.setting_description_monthly_playlist_compilation )
                )
            }
            item {
                val showMonthly = stringResource( R.string.setting_entry_show_chip, stringResource( R.string.monthly_playlists ) )
                if ( search appearsIn showMonthly )
                    SettingComponents.BooleanEntry(
                        preference = Preferences.SHOW_MONTHLY_PLAYLISTS,
                        title = showMonthly
                    )
            }
            entry( search, R.string.setting_entry_platform_indicator, "playlistsPlatformIndicator" ) {
                SettingComponents.EnumEntry(
                    preference = Preferences.PLAYLISTS_PLATFORM_INDICATOR,
                    title = stringResource( R.string.setting_entry_platform_indicator )
                )
            }
            entry( search, R.string.thumbnail_roundness, "playlistThumbnailRoundness" ) {
                ThumbnailRoundnessSlider( Preferences.PLAYLIST_THUMBNAIL_ROUNDNESS_PERCENT )
            }

            header( R.string.albums )
            entry( search, R.string.setting_entry_platform_indicator, "albumsPlatformIndicator" ) {
                SettingComponents.EnumEntry(
                    preference = Preferences.ALBUMS_PLATFORM_INDICATOR,
                    title = stringResource( R.string.setting_entry_platform_indicator )
                )
            }
            entry( search, R.string.thumbnail_roundness, "albumThumbnailRoundness" ) {
                ThumbnailRoundnessSlider( Preferences.ALBUM_THUMBNAIL_ROUNDNESS_PERCENT )
            }


            header( R.string.artists )
            entry( search, R.string.setting_entry_platform_indicator, "artistsPlatformIndicator" ) {
                SettingComponents.EnumEntry(
                    preference = Preferences.ARTISTS_PLATFORM_INDICATOR,
                    title = stringResource( R.string.setting_entry_platform_indicator )
                )
            }
            entry( search, R.string.thumbnail_roundness, "artistThumbnailRoundness" ) {
                ThumbnailRoundnessSlider( Preferences.ARTIST_THUMBNAIL_ROUNDNESS_PERCENT )
            }

            header( R.string.smart_recommendations )
            entry( search, R.string.statistics_max_number_of_items ) {
                SettingComponents.EnumEntry(
                    Preferences.MAX_NUMBER_OF_SMART_RECOMMENDATIONS,
                    R.string.statistics_max_number_of_items
                )
            }

            header( R.string.statistics )
            entry(
                search = search,
                titleId = R.string.statistics_max_number_of_items,
                key = "maxNumOfItemsInStatistics"
            ) {
                SettingComponents.EnumEntry(
                    Preferences.MAX_NUMBER_OF_STATISTIC_ITEMS,
                    R.string.statistics_max_number_of_items
                )
            }
            entry( search, R.string.listening_time ) {
                SettingComponents.BooleanEntry(
                    Preferences.SHOW_LISTENING_STATS,
                    R.string.listening_time,
                    R.string.shows_the_number_of_songs_heard_and_their_listening_time
                )
            }

            header( R.string.playlist_top )
            entry(
                search = search,
                titleId = R.string.statistics_max_number_of_items,
                key = "maxNumOfItemsInTopPlaylist"
            ) {
                SettingComponents.EnumEntry(
                    Preferences.MAX_NUMBER_OF_TOP_PLAYED,
                    R.string.statistics_max_number_of_items
                )
            }

            header( android.R.string.search_go )
            entry( search, R.string.setting_entry_single_back_from_search ) {
                SettingComponents.BooleanEntry(
                    preference = Preferences.SINGLE_BACK_FROM_SEARCH,
                    title = stringResource( R.string.setting_entry_single_back_from_search ),
                    subtitle = stringResource( R.string.setting_description_single_back_from_search )
                )
            }
        }
    }
}