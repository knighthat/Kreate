package app.kreate.android.themed.common.screens.settings.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.android.R
import app.kreate.android.themed.common.component.settings.BooleanEntry
import app.kreate.android.themed.common.component.settings.EnumEntry
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.animatedEntry
import app.kreate.android.themed.common.component.settings.entry
import app.kreate.android.themed.common.component.settings.header
import app.kreate.constant.Type
import app.kreate.preferences.Preferences
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.AnimatedGradient
import it.fast4x.rimusic.enums.CarouselSize
import it.fast4x.rimusic.enums.ColorPaletteMode
import it.fast4x.rimusic.enums.ColorPaletteName
import it.fast4x.rimusic.enums.PlayerBackgroundColors
import it.fast4x.rimusic.enums.PlayerPlayButtonType
import it.fast4x.rimusic.enums.PlayerThumbnailSize
import it.fast4x.rimusic.enums.PlayerTimelineSize
import it.fast4x.rimusic.enums.PlayerTimelineType
import it.fast4x.rimusic.enums.ThumbnailRoundness
import it.fast4x.rimusic.ui.components.themed.AppearancePresetDialog

fun LazyListScope.playerAppearanceSection(
    search: SettingEntrySearch,
    isLandscape: Boolean,
    showthumbnail: Boolean,
    playerBackgroundColors: PlayerBackgroundColors,
    playerInfoType: Type,
    playerType: Type
) {
    header( R.string.player )

    if( !isLandscape ) {
        entry( search, R.string.appearancepresets ) {

            var appearanceChooser by remember{ mutableStateOf(false)}
            if (appearanceChooser) {
                AppearancePresetDialog(
                    onDismiss = {appearanceChooser = false},
                    onClick0 = {
                        Preferences.PLAYER_SHOW_TOP_ACTIONS_BAR.update( true )
                        Preferences.PLAYER_SHOW_THUMBNAIL.update( true )
                        Preferences.PLAYER_BACKGROUND.update( PlayerBackgroundColors.BlurredCoverColor )
                        Preferences.PLAYER_BACKGROUND_BLUR_STRENGTH.update( 50f )
                        Preferences.THUMBNAIL_BORDER_RADIUS.update( ThumbnailRoundness.None )
                        Preferences.PLAYER_INFO_TYPE.update( Type.LEGACY )
                        Preferences.PLAYER_TIMELINE_TYPE.update( PlayerTimelineType.ThinBar )
                        Preferences.PLAYER_TIMELINE_SIZE.update( PlayerTimelineSize.Biggest )
                        Preferences.PLAYER_CONTROLS_TYPE.update( Type.LEGACY )
                        Preferences.PLAYER_PLAY_BUTTON_TYPE.update( PlayerPlayButtonType.Disabled )
                        Preferences.TRANSPARENT_TIMELINE.update( true )
                        Preferences.LYRICS_SHOW_THUMBNAIL.update( false )
                        Preferences.PLAYER_EXPANDED.update( true )
                        Preferences.THUMBNAIL_TYPE.update( Type.MODERN )
                        Preferences.PLAYER_PORTRAIT_THUMBNAIL_SIZE.update( PlayerThumbnailSize.Big )
                        Preferences.PLAYER_SHOW_TOTAL_QUEUE_TIME.update( false )
                        Preferences.PLAYER_BOTTOM_GRADIENT.update( true )
                        Preferences.PLAYER_SHOW_SONGS_REMAINING_TIME.update( true )
                        Preferences.PLAYER_SHOW_NEXT_IN_QUEUE.update( false )
                        Preferences.COLOR_PALETTE.update( ColorPaletteName.Dynamic )
                        Preferences.THEME_MODE.update( ColorPaletteMode.System )
                        ///////ACTION BAR BUTTONS////////////////
                        Preferences.PLAYER_TRANSPARENT_ACTIONS_BAR.update( true )
                        Preferences.PLAYER_ACTION_BUTTONS_SPACED_EVENLY.update( true )
                        Preferences.PLAYER_ACTION_TOGGLE_VIDEO.update( false )
                        Preferences.PLAYER_ACTION_DISCOVER.update( false )
                        Preferences.PLAYER_ACTION_DOWNLOAD.update( false )
                        Preferences.PLAYER_ACTION_ADD_TO_PLAYLIST.update( true )
                        Preferences.PLAYER_ACTION_LOOP.update( false )
                        Preferences.PLAYER_ACTION_SHUFFLE.update( true )
                        Preferences.PLAYER_ACTION_SHOW_LYRICS.update( false )
                        Preferences.PLAYER_ACTION_TOGGLE_EXPAND.update( false )
                        Preferences.PLAYER_ACTION_SLEEP_TIMER.update( false )
                        Preferences.PLAYER_VISUALIZER.update( false )
                        appearanceChooser = false
                        Preferences.PLAYER_ACTION_OPEN_QUEUE_ARROW.update(  false )
                        Preferences.PLAYER_ACTION_START_RADIO.update( false )
                        Preferences.PLAYER_ACTION_SHOW_MENU.update( true )
                        ///////////////////////////
                        appearanceChooser = false
                    },
                    onClick1 = {
                        Preferences.PLAYER_SHOW_TOP_ACTIONS_BAR.update( true )
                        Preferences.PLAYER_SHOW_THUMBNAIL.update( true )
                        Preferences.PLAYER_BACKGROUND.update( PlayerBackgroundColors.BlurredCoverColor )
                        Preferences.PLAYER_BACKGROUND_BLUR_STRENGTH.update( 50f )
                        Preferences.PLAYER_INFO_TYPE.update( Type.LEGACY )
                        Preferences.PLAYER_PLAY_BUTTON_TYPE.update( PlayerPlayButtonType.Disabled )
                        Preferences.PLAYER_TIMELINE_TYPE.update( PlayerTimelineType.ThinBar )
                        Preferences.PLAYER_CONTROLS_TYPE.update( Type.LEGACY )
                        Preferences.TRANSPARENT_TIMELINE.update( true )
                        Preferences.PLAYER_EXPANDED.update( true )
                        Preferences.PLAYER_BACKGROUND_FADING_EDGE.update( true )
                        Preferences.PLAYER_THUMBNAIL_FADE_EX.update( 4f )
                        Preferences.PLAYER_THUMBNAIL_SPACING.update( -32f )
                        Preferences.THUMBNAIL_TYPE.update( Type.LEGACY )
                        Preferences.CAROUSEL_SIZE.update( CarouselSize.Big )
                        Preferences.PLAYER_PORTRAIT_THUMBNAIL_SIZE.update( PlayerThumbnailSize.Biggest )
                        Preferences.PLAYER_SHOW_TOTAL_QUEUE_TIME.update( false )
                        Preferences.PLAYER_SHOW_SONGS_REMAINING_TIME.update( true )
                        Preferences.PLAYER_BOTTOM_GRADIENT.update( true )
                        Preferences.LYRICS_SHOW_THUMBNAIL.update( false )
                        Preferences.THUMBNAIL_BORDER_RADIUS.update( ThumbnailRoundness.Medium )
                        Preferences.PLAYER_SHOW_NEXT_IN_QUEUE.update( true )
                        Preferences.COLOR_PALETTE.update( ColorPaletteName.Dynamic )
                        Preferences.THEME_MODE.update( ColorPaletteMode.System )
                        ///////ACTION BAR BUTTONS////////////////
                        Preferences.PLAYER_TRANSPARENT_ACTIONS_BAR.update( true )
                        Preferences.PLAYER_ACTION_BUTTONS_SPACED_EVENLY.update( true )
                        Preferences.PLAYER_ACTION_TOGGLE_VIDEO.update( false )
                        Preferences.PLAYER_ACTION_DISCOVER.update( false )
                        Preferences.PLAYER_ACTION_DOWNLOAD.update( false )
                        Preferences.PLAYER_ACTION_ADD_TO_PLAYLIST.update( true )
                        Preferences.PLAYER_ACTION_LOOP.update( false )
                        Preferences.PLAYER_ACTION_SHUFFLE.update( false )
                        Preferences.PLAYER_ACTION_SHOW_LYRICS.update( false )
                        Preferences.PLAYER_ACTION_TOGGLE_EXPAND.update( true )
                        Preferences.PLAYER_ACTION_SLEEP_TIMER.update( false )
                        Preferences.PLAYER_VISUALIZER.update( false )
                        appearanceChooser = false
                        Preferences.PLAYER_ACTION_OPEN_QUEUE_ARROW.update(  false )
                        Preferences.PLAYER_ACTION_START_RADIO.update( false )
                        Preferences.PLAYER_ACTION_SHOW_MENU.update( true )
                        ///////////////////////////
                        appearanceChooser = false
                    },
                    onClick2 = {
                        Preferences.PLAYER_SHOW_TOP_ACTIONS_BAR.update( false )
                        Preferences.PLAYER_SHOW_THUMBNAIL.update( false )
                        Preferences.PLAYER_BACKGROUND_BLUR.update( true )
                        Preferences.PLAYER_TOP_PADDING.update( false )
                        Preferences.PLAYER_BACKGROUND.update( PlayerBackgroundColors.BlurredCoverColor )
                        Preferences.PLAYER_BACKGROUND_BLUR_STRENGTH.update( 50f )
                        Preferences.PLAYER_PLAY_BUTTON_TYPE.update( PlayerPlayButtonType.Disabled )
                        Preferences.PLAYER_INFO_TYPE.update( Type.MODERN )
                        Preferences.PLAYER_SONG_INFO_ICON.update( false )
                        Preferences.PLAYER_TIMELINE_TYPE.update( PlayerTimelineType.ThinBar )
                        Preferences.PLAYER_CONTROLS_TYPE.update( Type.LEGACY )
                        Preferences.TRANSPARENT_TIMELINE.update( true )
                        Preferences.PLAYER_EXPANDED.update( true )
                        Preferences.PLAYER_SHOW_TOTAL_QUEUE_TIME.update( false )
                        Preferences.PLAYER_SHOW_SONGS_REMAINING_TIME.update( true )
                        Preferences.PLAYER_BOTTOM_GRADIENT.update( true )
                        Preferences.LYRICS_SHOW_THUMBNAIL.update( false )
                        Preferences.PLAYER_SHOW_NEXT_IN_QUEUE.update( false )
                        Preferences.COLOR_PALETTE.update( ColorPaletteName.Dynamic )
                        Preferences.THEME_MODE.update( ColorPaletteMode.System )
                        ///////ACTION BAR BUTTONS////////////////
                        Preferences.PLAYER_TRANSPARENT_ACTIONS_BAR.update( true )
                        Preferences.PLAYER_ACTION_BUTTONS_SPACED_EVENLY.update( true )
                        Preferences.PLAYER_ACTION_TOGGLE_VIDEO.update( false )
                        Preferences.PLAYER_ACTION_DISCOVER.update( false )
                        Preferences.PLAYER_ACTION_DOWNLOAD.update( false )
                        Preferences.PLAYER_ACTION_ADD_TO_PLAYLIST.update( false )
                        Preferences.PLAYER_ACTION_LOOP.update( false )
                        Preferences.PLAYER_ACTION_SHUFFLE.update( false )
                        Preferences.PLAYER_ACTION_SHOW_LYRICS.update( false )
                        Preferences.PLAYER_ACTION_TOGGLE_EXPAND.update( false )
                        Preferences.PLAYER_ACTION_SLEEP_TIMER.update( false )
                        Preferences.PLAYER_VISUALIZER.update( false )
                        appearanceChooser = false
                        Preferences.PLAYER_ACTION_OPEN_QUEUE_ARROW.update(  false )
                        Preferences.PLAYER_ACTION_START_RADIO.update( false )
                        Preferences.PLAYER_ACTION_SHOW_MENU.update( true )
                        ///////////////////////////
                        appearanceChooser = false
                    },
                    onClick3 = {
                        Preferences.PLAYER_SHOW_TOP_ACTIONS_BAR.update( false )
                        Preferences.PLAYER_TOP_PADDING.update( false )
                        Preferences.PLAYER_SHOW_THUMBNAIL.update( true )
                        Preferences.PLAYER_BACKGROUND.update( PlayerBackgroundColors.BlurredCoverColor )
                        Preferences.PLAYER_BACKGROUND_BLUR_STRENGTH.update( 50f )
                        Preferences.PLAYER_INFO_TYPE.update( Type.LEGACY )
                        Preferences.PLAYER_TIMELINE_TYPE.update( PlayerTimelineType.FakeAudioBar )
                        Preferences.PLAYER_TIMELINE_SIZE.update( PlayerTimelineSize.Biggest )
                        Preferences.PLAYER_CONTROLS_TYPE.update( Type.MODERN )
                        Preferences.PLAYER_PLAY_BUTTON_TYPE.update( PlayerPlayButtonType.Disabled )
                        Preferences.COLOR_PALETTE.update( ColorPaletteName.PureBlack )
                        Preferences.TRANSPARENT_TIMELINE.update( false )
                        Preferences.PLAYER_EXPANDED.update( false )
                        Preferences.PLAYER_PORTRAIT_THUMBNAIL_SIZE.update( PlayerThumbnailSize.Expanded )
                        Preferences.PLAYER_SHOW_TOTAL_QUEUE_TIME.update( false )
                        Preferences.PLAYER_SHOW_SONGS_REMAINING_TIME.update( true )
                        Preferences.PLAYER_BOTTOM_GRADIENT.update( true )
                        Preferences.LYRICS_SHOW_THUMBNAIL.update( false )
                        Preferences.THUMBNAIL_TYPE.update( Type.LEGACY )
                        Preferences.THUMBNAIL_BORDER_RADIUS.update( ThumbnailRoundness.Light )
                        Preferences.PLAYER_TYPE.update( Type.MODERN )
                        Preferences.PLAYER_BACKGROUND_FADING_EDGE.update( true )
                        Preferences.PLAYER_THUMBNAIL_FADE.update( 5f )
                        Preferences.PLAYER_SHOW_NEXT_IN_QUEUE.update( false )
                        ///////ACTION BAR BUTTONS////////////////
                        Preferences.PLAYER_TRANSPARENT_ACTIONS_BAR.update( true )
                        Preferences.PLAYER_ACTION_BUTTONS_SPACED_EVENLY.update( true )
                        Preferences.PLAYER_ACTION_TOGGLE_VIDEO.update( false )
                        Preferences.PLAYER_ACTION_DISCOVER.update( false )
                        Preferences.PLAYER_ACTION_DOWNLOAD.update( false )
                        Preferences.PLAYER_ACTION_ADD_TO_PLAYLIST.update( false )
                        Preferences.PLAYER_ACTION_LOOP.update( true )
                        Preferences.PLAYER_ACTION_SHUFFLE.update( true )
                        Preferences.PLAYER_ACTION_SHOW_LYRICS.update( false )
                        Preferences.PLAYER_ACTION_TOGGLE_EXPAND.update( false )
                        Preferences.PLAYER_ACTION_SLEEP_TIMER.update( false )
                        Preferences.PLAYER_VISUALIZER.update( false )
                        appearanceChooser = false
                        Preferences.PLAYER_ACTION_OPEN_QUEUE_ARROW.update(  true )
                        Preferences.PLAYER_ACTION_START_RADIO.update( false )
                        Preferences.PLAYER_ACTION_SHOW_MENU.update( true )
                        ///////////////////////////
                        appearanceChooser = false
                    },
                    onClick4 = {
                        Preferences.PLAYER_SHOW_TOP_ACTIONS_BAR.update( false )
                        Preferences.PLAYER_TOP_PADDING.update( true )
                        Preferences.PLAYER_SHOW_THUMBNAIL.update( true )
                        Preferences.PLAYER_BACKGROUND.update( PlayerBackgroundColors.AnimatedGradient )
                        Preferences.ANIMATED_GRADIENT.update( AnimatedGradient.Linear )
                        Preferences.PLAYER_INFO_TYPE.update( Type.LEGACY )
                        Preferences.PLAYER_TIMELINE_TYPE.update( PlayerTimelineType.PinBar )
                        Preferences.PLAYER_TIMELINE_SIZE.update( PlayerTimelineSize.Biggest )
                        Preferences.PLAYER_CONTROLS_TYPE.update( Type.LEGACY )
                        Preferences.PLAYER_PLAY_BUTTON_TYPE.update( PlayerPlayButtonType.Square )
                        Preferences.COLOR_PALETTE.update( ColorPaletteName.Dynamic )
                        Preferences.THEME_MODE.update( ColorPaletteMode.PitchBlack )
                        Preferences.TRANSPARENT_TIMELINE.update( false )
                        Preferences.PLAYER_TYPE.update( Type.MODERN )
                        Preferences.PLAYER_EXPANDED.update( false )
                        Preferences.PLAYER_PORTRAIT_THUMBNAIL_SIZE.update( PlayerThumbnailSize.Biggest )
                        Preferences.PLAYER_SHOW_TOTAL_QUEUE_TIME.update( false )
                        Preferences.PLAYER_SHOW_SONGS_REMAINING_TIME.update( true )
                        Preferences.LYRICS_SHOW_THUMBNAIL.update( false )
                        Preferences.THUMBNAIL_TYPE.update( Type.MODERN )
                        Preferences.THUMBNAIL_BORDER_RADIUS.update( ThumbnailRoundness.Heavy )
                        Preferences.PLAYER_BACKGROUND_FADING_EDGE.update( true )
                        Preferences.PLAYER_THUMBNAIL_FADE.update( 0f )
                        Preferences.PLAYER_THUMBNAIL_FADE_EX.update( 5f )
                        Preferences.PLAYER_THUMBNAIL_SPACING.update( -32f )
                        Preferences.PLAYER_SHOW_NEXT_IN_QUEUE.update( false )
                        ///////ACTION BAR BUTTONS////////////////
                        Preferences.PLAYER_TRANSPARENT_ACTIONS_BAR.update( true )
                        Preferences.PLAYER_ACTION_BUTTONS_SPACED_EVENLY.update( true )
                        Preferences.PLAYER_ACTION_TOGGLE_VIDEO.update( false )
                        Preferences.PLAYER_ACTION_DISCOVER.update( false )
                        Preferences.PLAYER_ACTION_DOWNLOAD.update( true )
                        Preferences.PLAYER_ACTION_ADD_TO_PLAYLIST.update( false )
                        Preferences.PLAYER_ACTION_LOOP.update( false )
                        Preferences.PLAYER_ACTION_SHUFFLE.update( false )
                        Preferences.PLAYER_ACTION_SHOW_LYRICS.update( false )
                        Preferences.PLAYER_ACTION_TOGGLE_EXPAND.update( true )
                        Preferences.PLAYER_ACTION_SLEEP_TIMER.update( false )
                        Preferences.PLAYER_VISUALIZER.update( false )
                        appearanceChooser = false
                        Preferences.PLAYER_ACTION_OPEN_QUEUE_ARROW.update( false )
                        Preferences.PLAYER_ACTION_START_RADIO.update( false )
                        Preferences.PLAYER_ACTION_SHOW_MENU.update( true )
                        ///////////////////////////
                        appearanceChooser = false
                    },
                    onClick5 = {
                        Preferences.PLAYER_SHOW_TOP_ACTIONS_BAR.update( true )
                        Preferences.PLAYER_SHOW_THUMBNAIL.update( true )
                        Preferences.PLAYER_BACKGROUND.update( PlayerBackgroundColors.CoverColorGradient )
                        Preferences.PLAYER_INFO_TYPE.update( Type.LEGACY )
                        Preferences.PLAYER_TIMELINE_TYPE.update( PlayerTimelineType.Wavy )
                        Preferences.PLAYER_TIMELINE_SIZE.update( PlayerTimelineSize.Biggest )
                        Preferences.PLAYER_CONTROLS_TYPE.update( Type.LEGACY )
                        Preferences.PLAYER_PLAY_BUTTON_TYPE.update( PlayerPlayButtonType.CircularRibbed )
                        Preferences.COLOR_PALETTE.update( ColorPaletteName.Dynamic )
                        Preferences.THEME_MODE.update( ColorPaletteMode.System )
                        Preferences.TRANSPARENT_TIMELINE.update( false )
                        Preferences.PLAYER_TYPE.update( Type.LEGACY )
                        Preferences.PLAYER_EXPANDED.update( true )
                        Preferences.PLAYER_PORTRAIT_THUMBNAIL_SIZE.update( PlayerThumbnailSize.Big )
                        Preferences.PLAYER_SHOW_TOTAL_QUEUE_TIME.update( false )
                        Preferences.PLAYER_SHOW_SONGS_REMAINING_TIME.update( true )
                        Preferences.LYRICS_SHOW_THUMBNAIL.update( false )
                        Preferences.THUMBNAIL_TYPE.update( Type.MODERN )
                        Preferences.THUMBNAIL_BORDER_RADIUS.update( ThumbnailRoundness.Heavy )
                        Preferences.PLAYER_SHOW_NEXT_IN_QUEUE.update( false )
                        ///////ACTION BAR BUTTONS////////////////
                        Preferences.PLAYER_TRANSPARENT_ACTIONS_BAR.update( true )
                        Preferences.PLAYER_ACTION_BUTTONS_SPACED_EVENLY.update( true )
                        Preferences.PLAYER_ACTION_TOGGLE_VIDEO.update( false )
                        Preferences.PLAYER_ACTION_DISCOVER.update( false )
                        Preferences.PLAYER_ACTION_DOWNLOAD.update( false )
                        Preferences.PLAYER_ACTION_ADD_TO_PLAYLIST.update( false )
                        Preferences.PLAYER_ACTION_LOOP.update( false )
                        Preferences.PLAYER_ACTION_SHUFFLE.update( true )
                        Preferences.PLAYER_ACTION_SHOW_LYRICS.update( true )
                        Preferences.PLAYER_ACTION_TOGGLE_EXPAND.update( false )
                        Preferences.PLAYER_ACTION_SLEEP_TIMER.update( false )
                        Preferences.PLAYER_VISUALIZER.update( false )
                        appearanceChooser = false
                        Preferences.PLAYER_ACTION_OPEN_QUEUE_ARROW.update( false )
                        Preferences.PLAYER_ACTION_START_RADIO.update( false )
                        Preferences.PLAYER_ACTION_SHOW_MENU.update( true )
                        ///////////////////////////
                        appearanceChooser = false
                    }
                )
            }

            SettingComponents.Text(
                    stringResource( R.string.appearancepresets ),
                    { appearanceChooser = true },
                    subtitle = stringResource( R.string.appearancepresetssecondary )
                )
        }
        entry( search, R.string.show_player_top_actions_bar ) {
            SettingComponents.BooleanEntry(
                    preference = Preferences.PLAYER_SHOW_TOP_ACTIONS_BAR,
                    title = stringResource( R.string.show_player_top_actions_bar )
                )
        }
        entry( search, R.string.blankspace ) {
            SettingComponents.BooleanEntry(
                    preference = Preferences.PLAYER_TOP_PADDING,
                    title = stringResource( R.string.blankspace )
                )
        }
    }
    entry( search, R.string.playertype ) {
        SettingComponents.EnumEntry(
            preference = Preferences.PLAYER_TYPE,
            title = stringResource( R.string.playertype )
        )
    }
    entry( search, R.string.queuetype ) {
        SettingComponents.EnumEntry(
            preference = Preferences.QUEUE_TYPE,
            title = stringResource( R.string.queuetype )
        )
    }
    entry(
        search = search,
        titleId = R.string.show_thumbnail,
        additionalCheck = playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor
    ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.PLAYER_SHOW_THUMBNAIL,
            title = stringResource( R.string.show_thumbnail )
        )
    }
    item( "showThumbnailFalseChildren" ) {
        val playerType by Preferences.PLAYER_TYPE.collectAsStateWithLifecycle()
        val playerBackgroundColors by Preferences.PLAYER_BACKGROUND.collectAsStateWithLifecycle()
        val modifier = Modifier.padding(
            start = if ( playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor )
                25.dp
            else
                0.dp
        )

        AnimatedVisibility(
            visible = !showthumbnail && playerType === Type.MODERN && !isLandscape,
            modifier = modifier
        ) {
            if ( search appearsIn R.string.swipe_Animation_No_Thumbnail )
                SettingComponents.EnumEntry(
                    preference = Preferences.PLAYER_NO_THUMBNAIL_SWIPE_ANIMATION,
                    title = stringResource( R.string.swipe_Animation_No_Thumbnail )
                )
        }
    }
    animatedEntry(
        key = "showThumbnailTrueChildren",
        visibleState = Preferences.PLAYER_SHOW_THUMBNAIL,
        modifier = Modifier.padding(
            start = if ( playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor )
                25.dp
            else
                0.dp
        )
    ) {
        Column {
            if ( playerType == Type.MODERN && search appearsIn R.string.fadingedge )
                SettingComponents.BooleanEntry(
                    preference = Preferences.PLAYER_BACKGROUND_FADING_EDGE,
                    title = stringResource( R.string.fadingedge )
                )

            val expandedplayer by Preferences.PLAYER_EXPANDED.collectAsStateWithLifecycle()
            val expandedplayertoggle by Preferences.PLAYER_ACTION_TOGGLE_EXPAND.collectAsStateWithLifecycle()
            if ( playerType == Type.MODERN && !isLandscape && (expandedplayertoggle || expandedplayer) ) {
                if ( search appearsIn R.string.carousel )
                    SettingComponents.BooleanEntry(
                        preference = Preferences.PLAYER_THUMBNAILS_CAROUSEL,
                        title = stringResource( R.string.carousel )
                    )

                if ( search appearsIn R.string.carouselsize )
                    SettingComponents.EnumEntry(
                        preference = Preferences.CAROUSEL_SIZE,
                        title = stringResource( R.string.carouselsize )
                    )
            }
            if ( playerType == Type.LEGACY ) {
                if ( search appearsIn R.string.thumbnailpause )
                    SettingComponents.BooleanEntry(
                        preference = Preferences.PLAYER_SHRINK_THUMBNAIL_ON_PAUSE,
                        title = stringResource( R.string.thumbnailpause )
                    )
                if ( search appearsIn R.string.show_lyrics_thumbnail )
                    SettingComponents.BooleanEntry(
                        preference = Preferences.LYRICS_SHOW_THUMBNAIL,
                        title = stringResource( R.string.show_lyrics_thumbnail )
                    )
                val visualizerEnabled by Preferences.PLAYER_VISUALIZER.collectAsStateWithLifecycle()
                if ( visualizerEnabled && search appearsIn R.string.showvisthumbnail )
                    SettingComponents.BooleanEntry(
                        preference = Preferences.PLAYER_SHOW_THUMBNAIL_ON_VISUALIZER,
                        title = stringResource( R.string.showvisthumbnail )
                    )
            }

            if ( search appearsIn R.string.show_cover_thumbnail_animation ) {
                SettingComponents.BooleanEntry(
                    preference = Preferences.PLAYER_THUMBNAIL_ANIMATION,
                    title = stringResource( R.string.show_cover_thumbnail_animation )
                )
                val showCoverThumbnailAnimation by Preferences.PLAYER_THUMBNAIL_ANIMATION.collectAsStateWithLifecycle()
                AnimatedVisibility( showCoverThumbnailAnimation ) {
                    if( search appearsIn R.string.cover_thumbnail_animation_type )
                        SettingComponents.EnumEntry(
                            preference = Preferences.PLAYER_THUMBNAIL_TYPE,
                            title = stringResource( R.string.cover_thumbnail_animation_type ),
                            modifier = Modifier.padding(
                                start = if ( playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor )
                                    25.dp
                                else
                                    0.dp
                            )
                        )
                }
            }

            if (isLandscape) {
                if ( search appearsIn R.string.player_thumbnail_size )
                    SettingComponents.EnumEntry(
                        preference = Preferences.PLAYER_LANDSCAPE_THUMBNAIL_SIZE,
                        title = stringResource( R.string.player_thumbnail_size )
                    )
            } else {
                if ( search appearsIn R.string.player_thumbnail_size )
                    SettingComponents.EnumEntry(
                        preference = Preferences.PLAYER_PORTRAIT_THUMBNAIL_SIZE,
                        title = stringResource( R.string.player_thumbnail_size )
                    )
            }
            if ( search appearsIn R.string.thumbnailtype )
                SettingComponents.EnumEntry(
                    preference = Preferences.THUMBNAIL_TYPE,
                    title = stringResource( R.string.thumbnailtype )
                )

            if ( search appearsIn R.string.thumbnail_roundness )
                SettingComponents.EnumEntry(
                    preference = Preferences.THUMBNAIL_BORDER_RADIUS,
                    title = stringResource( R.string.thumbnail_roundness ),
                    trailingContent = {
                        val thumbnailRoundness by Preferences.THUMBNAIL_BORDER_RADIUS.collectAsStateWithLifecycle()

                        Spacer(
                            Modifier.border(
                                        width = 1.dp,
                                        color = colorPalette().accent,
                                        shape = thumbnailRoundness.shape
                                    )
                                    .background(
                                        color = colorPalette().background1,
                                        shape = thumbnailRoundness.shape
                                    )
                                    .size( 36.dp )
                        )
                    }
                )
        }
    }
    entry(
        search = search,
        titleId = R.string.noblur,
        additionalCheck = !showthumbnail
    ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.PLAYER_BACKGROUND_BLUR,
            title = stringResource( R.string.noblur )
        )
    }
    entry(
        search = search,
        titleId = R.string.statsfornerdsplayer,
        additionalCheck = !(showthumbnail && playerType == Type.LEGACY)
    ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.PLAYER_STATS_FOR_NERDS,
            title = stringResource( R.string.statsfornerdsplayer )
        )
    }
    entry(
        search = search,
        titleId = R.string.timelinesize,
        additionalCheck = !(showthumbnail && playerType == Type.LEGACY)
    ) {
        SettingComponents.EnumEntry(
            preference = Preferences.PLAYER_TIMELINE_SIZE,
            title = stringResource( R.string.timelinesize )
        )
    }
    entry( search, R.string.pinfo_type ) {
        SettingComponents.EnumEntry(
            preference = Preferences.PLAYER_INFO_TYPE,
            title = stringResource( R.string.pinfo_type ),
            subtitleId = R.string.pinfo_album_and_artist_name
        )
    }
    animatedEntry(
        key = "playerInfoTypeChildren",
        visible = playerInfoType == Type.MODERN,
        modifier = Modifier.padding( start = 25.dp )
    ) {
        if( search appearsIn R.string.pinfo_show_icons )
            SettingComponents.BooleanEntry(
                preference = Preferences.PLAYER_SONG_INFO_ICON,
                title = stringResource( R.string.pinfo_show_icons )
            )
    }
    entry( search, R.string.miniplayertype ) {
        SettingComponents.EnumEntry(
            preference = Preferences.MINI_PLAYER_TYPE,
            title = stringResource( R.string.miniplayertype )
        )
    }
    entry( search, R.string.player_swap_controls_with_timeline ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.PLAYER_IS_CONTROL_AND_TIMELINE_SWAPPED,
            title = stringResource( R.string.player_swap_controls_with_timeline )
        )
    }
    entry( search, R.string.timeline ) {
        SettingComponents.EnumEntry(
            preference = Preferences.PLAYER_TIMELINE_TYPE,
            title = stringResource( R.string.timeline )
        )
    }
    entry( search, R.string.transparentbar ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.TRANSPARENT_TIMELINE,
            title = stringResource( R.string.transparentbar )
        )
    }
    entry( search, R.string.pcontrols_type ) {
        SettingComponents.EnumEntry(
            preference = Preferences.PLAYER_CONTROLS_TYPE,
            title = stringResource( R.string.pcontrols_type )
        )
    }
    entry( search, R.string.play_button ) {
        SettingComponents.EnumEntry(
            preference = Preferences.PLAYER_PLAY_BUTTON_TYPE,
            title = stringResource( R.string.play_button )
        )
    }
    entry( search, R.string.buttonzoomout ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.ZOOM_OUT_ANIMATION,
            title = stringResource ( R.string.buttonzoomout )
        )
    }
    entry( search, R.string.icon_like_button ) {
        SettingComponents.EnumEntry(
            preference = Preferences.LIKE_ICON,
            title = stringResource( R.string.icon_like_button )
        )
    }
    entry( search, R.string.background_colors ) {
        SettingComponents.EnumEntry(
            preference = Preferences.PLAYER_BACKGROUND,
            title = stringResource( R.string.background_colors )
        )
    }
    animatedEntry(
        key = "playerBackgroundColorsIsAnimatedGradient",
        visible = playerBackgroundColors == PlayerBackgroundColors.AnimatedGradient,
        modifier = Modifier.padding( start = 25.dp )
    ) {
        if ( search appearsIn R.string.gradienttype )
            SettingComponents.EnumEntry(
                preference = Preferences.ANIMATED_GRADIENT,
                title = stringResource( R.string.gradienttype )
            )
    }
    animatedEntry(
        key = "playerBackgroundColorsIsBlurredCoverColor",
        visible = playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor,
        modifier = Modifier.padding( start = 25.dp )
    ) {
        Column {
            if( search appearsIn R.string.rotating_cover_title )
                SettingComponents.BooleanEntry(
                    preference = Preferences.PLAYER_ROTATING_ALBUM_COVER,
                    title = stringResource( R.string.rotating_cover_title )
                )

            if ( search appearsIn R.string.bottomgradient )
                SettingComponents.BooleanEntry(
                    preference = Preferences.PLAYER_BOTTOM_GRADIENT,
                    title = stringResource( R.string.bottomgradient )
                )

            if ( playerType == Type.MODERN && search appearsIn R.string.albumCoverRotation )
                SettingComponents.BooleanEntry(
                    preference = Preferences.PLAYER_THUMBNAIL_ROTATION,
                    title = stringResource( R.string.albumCoverRotation )
                )
        }
    }
    item(
        key = "playerBackgroundColorsIsEitherCoverColorGradientOrThemeColorGradient"
    ) {
        val playerBackground by Preferences.PLAYER_BACKGROUND.collectAsStateWithLifecycle()

        AnimatedVisibility(
            visible = playerBackground === PlayerBackgroundColors.CoverColorGradient || playerBackground === PlayerBackgroundColors.ThemeColorGradient,
            modifier = Modifier.padding( start = 25.dp )
        ) {
            if( search appearsIn R.string.blackgradient )
                SettingComponents.BooleanEntry(
                    preference = Preferences.BLACK_GRADIENT,
                    title = stringResource( R.string.blackgradient ),
                )
        }

    }
    entry( search, R.string.textoutline ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.TEXT_OUTLINE,
            title = stringResource( R.string.textoutline )
        )
    }
    entry( search, R.string.show_total_time_of_queue ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.PLAYER_SHOW_TOTAL_QUEUE_TIME,
            title = stringResource( R.string.show_total_time_of_queue )
        )
    }
    entry( search, R.string.show_remaining_song_time ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.PLAYER_SHOW_SONGS_REMAINING_TIME,
            title = stringResource( R.string.show_remaining_song_time )
        )
    }
    entry( search, R.string.setting_entry_show_seek_forward_backward ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.PLAYER_SHOW_SEEK_BUTTONS,
            title = stringResource( R.string.setting_entry_show_seek_forward_backward )
        )
    }
    entry( search, R.string.show_next_songs_in_player ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.PLAYER_SHOW_NEXT_IN_QUEUE,
            title =   stringResource( R.string.show_next_songs_in_player )
        )
    }
    animatedEntry(
        key = "showNextSongsInPlayerChildren",
        visibleState = Preferences.PLAYER_SHOW_NEXT_IN_QUEUE,
        modifier = Modifier.padding( start = 25.dp )
    ) {
        Column {
            if( search appearsIn R.string.showtwosongs )
                SettingComponents.EnumEntry(
                    preference = Preferences.MAX_NUMBER_OF_NEXT_IN_QUEUE,
                    title = stringResource( R.string.songs_number_to_show )
                )

            if ( search appearsIn R.string.showalbumcover )
                SettingComponents.BooleanEntry(
                    preference = Preferences.PLAYER_SHOW_NEXT_IN_QUEUE_THUMBNAIL,
                    title = stringResource( R.string.showalbumcover )
                )
        }
    }
    entry( search, R.string.setting_entry_marquee_effect ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.MARQUEE_TEXT_EFFECT,
            title = stringResource( R.string.setting_entry_marquee_effect ),
            subtitle = stringResource( R.string.setting_description_marquee_effect )
        )
    }
    val (titleId, subtitleId) = if( playerType == Type.MODERN && !isLandscape )
        R.string.disable_vertical_swipe to R.string.disable_vertical_swipe_secondary
    else
        R.string.disable_horizontal_swipe to R.string.disable_song_switching_via_swipe
    entry( search, titleId ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.PLAYER_THUMBNAIL_HORIZONTAL_SWIPE_DISABLED,
            title = stringResource( titleId ),
            subtitleId
        )
    }
    entry( search, R.string.player_rotating_buttons ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.ROTATION_EFFECT,
            title = stringResource( R.string.player_rotating_buttons) ,
            R.string.player_enable_rotation_buttons
        )
    }
    entry( search, R.string.toggle_lyrics ) {
         SettingComponents.BooleanEntry(
            preference = Preferences.PLAYER_TAP_THUMBNAIL_FOR_LYRICS,
            title = stringResource( R.string.toggle_lyrics) ,
            R.string.by_tapping_on_the_thumbnail
        )
    }
    entry( search, R.string.click_lyrics_text ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.LYRICS_JUMP_ON_TAP,
            title = stringResource( R.string.click_lyrics_text )
        )
    }
    entry( search, R.string.show_background_in_lyrics ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.LYRICS_SHOW_ACCENT_BACKGROUND,
            title = stringResource( R.string.show_background_in_lyrics )
        )
    }
    entry( search, R.string.player_enable_lyrics_popup_message ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.PLAYER_ACTION_LYRICS_POPUP_MESSAGE,
            title = stringResource( R.string.player_enable_lyrics_popup_message )
        )
    }
    entry( search, R.string.background_progress_bar ) {
        SettingComponents.EnumEntry(
            preference = Preferences.MINI_PLAYER_PROGRESS_BAR,
            title = stringResource( R.string.background_progress_bar )
        )
    }
    entry( search, R.string.visualizer ) {
        SettingComponents.BooleanEntry(
            preference = Preferences.PLAYER_VISUALIZER,
            title = stringResource( R.string.visualizer ),
            subtitleId = R.string.visualizer_require_mic_permission
        )
    }
}