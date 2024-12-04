package it.fast4x.rimusic.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import it.fast4x.rimusic.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.*
import it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import it.fast4x.rimusic.ui.components.themed.Search
import it.fast4x.rimusic.ui.components.themed.SmartMessage
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.utils.*

@Composable
fun DefaultAppearanceSettings() {
    var isShowingThumbnailInLockscreen by rememberPreference(
        isShowingThumbnailInLockscreenKey,
        true
    )
    isShowingThumbnailInLockscreen = true
    var showthumbnail by rememberPreference(showthumbnailKey, true)
    showthumbnail = true
    var transparentbar by rememberPreference(transparentbarKey, true)
    transparentbar = true
    var blackgradient by rememberPreference(blackgradientKey, false)
    blackgradient = false
    var showlyricsthumbnail by rememberPreference(showlyricsthumbnailKey, false)
    showlyricsthumbnail = false
    var playerPlayButtonType by rememberPreference(
        playerPlayButtonTypeKey,
        PlayerPlayButtonType.Disabled
    )
    playerPlayButtonType = PlayerPlayButtonType.Disabled
    var bottomgradient by rememberPreference(bottomgradientKey, false)
    bottomgradient = false
    var textoutline by rememberPreference(textoutlineKey, false)
    textoutline = false
    var lastPlayerPlayButtonType by rememberPreference(
        lastPlayerPlayButtonTypeKey,
        PlayerPlayButtonType.Rectangular
    )
    lastPlayerPlayButtonType = PlayerPlayButtonType.Rectangular
    var disablePlayerHorizontalSwipe by rememberPreference(disablePlayerHorizontalSwipeKey, false)
    disablePlayerHorizontalSwipe = false
    var disableScrollingText by rememberPreference(disableScrollingTextKey, false)
    disableScrollingText = false
    var showLikeButtonBackgroundPlayer by rememberPreference(
        showLikeButtonBackgroundPlayerKey,
        true
    )
    showLikeButtonBackgroundPlayer = true
    var showDownloadButtonBackgroundPlayer by rememberPreference(
        showDownloadButtonBackgroundPlayerKey,
        true
    )
    showDownloadButtonBackgroundPlayer = true
    var visualizerEnabled by rememberPreference(visualizerEnabledKey, false)
    visualizerEnabled = false
    var playerTimelineType by rememberPreference(playerTimelineTypeKey, PlayerTimelineType.FakeAudioBar)
    playerTimelineType = PlayerTimelineType.FakeAudioBar
    var playerThumbnailSize by rememberPreference(
        playerThumbnailSizeKey,
        PlayerThumbnailSize.Biggest
    )
    playerThumbnailSize = PlayerThumbnailSize.Biggest
    var playerTimelineSize by rememberPreference(
        playerTimelineSizeKey,
        PlayerTimelineSize.Biggest
    )
    playerTimelineSize = PlayerTimelineSize.Biggest
    var effectRotationEnabled by rememberPreference(effectRotationKey, true)
    effectRotationEnabled = true
    var thumbnailTapEnabled by rememberPreference(thumbnailTapEnabledKey, true)
    thumbnailTapEnabled = true
    var showButtonPlayerAddToPlaylist by rememberPreference(showButtonPlayerAddToPlaylistKey, true)
    showButtonPlayerAddToPlaylist = true
    var showButtonPlayerArrow by rememberPreference(showButtonPlayerArrowKey, true)
    showButtonPlayerArrow = false
    var showButtonPlayerDownload by rememberPreference(showButtonPlayerDownloadKey, true)
    showButtonPlayerDownload = true
    var showButtonPlayerLoop by rememberPreference(showButtonPlayerLoopKey, true)
    showButtonPlayerLoop = true
    var showButtonPlayerLyrics by rememberPreference(showButtonPlayerLyricsKey, true)
    showButtonPlayerLyrics = true
    var expandedplayertoggle by rememberPreference(expandedplayertoggleKey, true)
    expandedplayertoggle = true
    var showButtonPlayerShuffle by rememberPreference(showButtonPlayerShuffleKey, true)
    showButtonPlayerShuffle = true
    var showButtonPlayerSleepTimer by rememberPreference(showButtonPlayerSleepTimerKey, false)
    showButtonPlayerSleepTimer = false
    var showButtonPlayerMenu by rememberPreference(showButtonPlayerMenuKey, false)
    showButtonPlayerMenu = false
    var showButtonPlayerSystemEqualizer by rememberPreference(
        showButtonPlayerSystemEqualizerKey,
        false
    )
    showButtonPlayerSystemEqualizer = false
    var showButtonPlayerDiscover by rememberPreference(showButtonPlayerDiscoverKey, false)
    showButtonPlayerDiscover = false
    var showButtonPlayerVideo by rememberPreference(showButtonPlayerVideoKey, false)
    showButtonPlayerVideo = false
    var navigationBarPosition by rememberPreference(
        navigationBarPositionKey,
        NavigationBarPosition.Bottom
    )
    navigationBarPosition = NavigationBarPosition.Bottom
    var showTotalTimeQueue by rememberPreference(showTotalTimeQueueKey, true)
    showTotalTimeQueue = true
    var backgroundProgress by rememberPreference(
        backgroundProgressKey,
        BackgroundProgress.MiniPlayer
    )
    backgroundProgress = BackgroundProgress.MiniPlayer
    var showNextSongsInPlayer by rememberPreference(showNextSongsInPlayerKey, false)
    showNextSongsInPlayer = false
    var showRemainingSongTime by rememberPreference(showRemainingSongTimeKey, true)
    showRemainingSongTime = true
    var clickLyricsText by rememberPreference(clickOnLyricsTextKey, true)
    clickLyricsText = true
    var showBackgroundLyrics by rememberPreference(showBackgroundLyricsKey, false)
    showBackgroundLyrics = false
    var thumbnailRoundness by rememberPreference(
        thumbnailRoundnessKey,
        ThumbnailRoundness.Heavy
    )
    thumbnailRoundness = ThumbnailRoundness.Heavy
    var miniPlayerType by rememberPreference(
        miniPlayerTypeKey,
        MiniPlayerType.Modern
    )
    miniPlayerType = MiniPlayerType.Modern
    var playerBackgroundColors by rememberPreference(
        playerBackgroundColorsKey,
        PlayerBackgroundColors.BlurredCoverColor
    )
    playerBackgroundColors = PlayerBackgroundColors.BlurredCoverColor
    var showTopActionsBar by rememberPreference(showTopActionsBarKey, true)
    showTopActionsBar = true
    var playerControlsType by rememberPreference(playerControlsTypeKey, PlayerControlsType.Essential)
    playerControlsType = PlayerControlsType.Modern
    var playerInfoType by rememberPreference(playerInfoTypeKey, PlayerInfoType.Essential)
    playerInfoType = PlayerInfoType.Modern
    var transparentBackgroundActionBarPlayer by rememberPreference(
        transparentBackgroundPlayerActionBarKey,
        false
    )
    transparentBackgroundActionBarPlayer = false
    var iconLikeType by rememberPreference(iconLikeTypeKey, IconLikeType.Essential)
    iconLikeType = IconLikeType.Essential
    var playerSwapControlsWithTimeline by rememberPreference(
        playerSwapControlsWithTimelineKey,
        false
    )
    playerSwapControlsWithTimeline = false
    var playerEnableLyricsPopupMessage by rememberPreference(
        playerEnableLyricsPopupMessageKey,
        true
    )
    playerEnableLyricsPopupMessage = true
    var actionspacedevenly by rememberPreference(actionspacedevenlyKey, false)
    actionspacedevenly = false
    var thumbnailType by rememberPreference(thumbnailTypeKey, ThumbnailType.Modern)
    thumbnailType = ThumbnailType.Modern
    var showvisthumbnail by rememberPreference(showvisthumbnailKey, false)
    showvisthumbnail = false
    var buttonzoomout by rememberPreference(buttonzoomoutKey, false)
    buttonzoomout = false
    var thumbnailpause by rememberPreference(thumbnailpauseKey, false)
    thumbnailpause = false
    var showsongs by rememberPreference(showsongsKey, SongsNumber.`2`)
    showsongs = SongsNumber.`2`
    var showalbumcover by rememberPreference(showalbumcoverKey, true)
    showalbumcover = true
    var prevNextSongs by rememberPreference(prevNextSongsKey, PrevNextSongs.twosongs)
    prevNextSongs = PrevNextSongs.twosongs
    var tapqueue by rememberPreference(tapqueueKey, true)
    tapqueue = true
    var swipeUpQueue by rememberPreference(swipeUpQueueKey, true)
    swipeUpQueue = true
    var statsfornerds by rememberPreference(statsfornerdsKey, false)
    statsfornerds = false
    var playerType by rememberPreference(playerTypeKey, PlayerType.Essential)
    playerType = PlayerType.Essential
    var queueType by rememberPreference(queueTypeKey, QueueType.Essential)
    queueType = QueueType.Essential
    var noblur by rememberPreference(noblurKey, true)
    noblur = true
    var fadingedge by rememberPreference(fadingedgeKey, false)
    fadingedge = false
    var carousel by rememberPreference(carouselKey, true)
    carousel = true
    var carouselSize by rememberPreference(carouselSizeKey, CarouselSize.Biggest)
    carouselSize = CarouselSize.Biggest
    var keepPlayerMinimized by rememberPreference(keepPlayerMinimizedKey,false)
    keepPlayerMinimized = false
    var playerInfoShowIcons by rememberPreference(playerInfoShowIconsKey, true)
    playerInfoShowIcons = true
}

@ExperimentalAnimationApi
@UnstableApi
@Composable
fun AppearanceSettings(
    navController: NavController,
) {

    var isShowingThumbnailInLockscreen by rememberPreference(
        isShowingThumbnailInLockscreenKey,
        true
    )

    var showthumbnail by rememberPreference(showthumbnailKey, true)
    var transparentbar by rememberPreference(transparentbarKey, true)
    var blackgradient by rememberPreference(blackgradientKey, false)
    var showlyricsthumbnail by rememberPreference(showlyricsthumbnailKey, false)
    var expandedplayer by rememberPreference(expandedplayerKey, false)
    var playerPlayButtonType by rememberPreference(
        playerPlayButtonTypeKey,
        PlayerPlayButtonType.Disabled
    )
    var bottomgradient by rememberPreference(bottomgradientKey, false)
    var textoutline by rememberPreference(textoutlineKey, false)

    var lastPlayerPlayButtonType by rememberPreference(
        lastPlayerPlayButtonTypeKey,
        PlayerPlayButtonType.Rectangular
    )
    var disablePlayerHorizontalSwipe by rememberPreference(disablePlayerHorizontalSwipeKey, false)

    var disableScrollingText by rememberPreference(disableScrollingTextKey, false)
    var showLikeButtonBackgroundPlayer by rememberPreference(
        showLikeButtonBackgroundPlayerKey,
        true
    )
    var showDownloadButtonBackgroundPlayer by rememberPreference(
        showDownloadButtonBackgroundPlayerKey,
        true
    )
    var visualizerEnabled by rememberPreference(visualizerEnabledKey, false)
    /*
    var playerVisualizerType by rememberPreference(
        playerVisualizerTypeKey,
        PlayerVisualizerType.Disabled
    )
    */
    var playerTimelineType by rememberPreference(playerTimelineTypeKey, PlayerTimelineType.FakeAudioBar)
    var playerThumbnailSize by rememberPreference(
        playerThumbnailSizeKey,
        PlayerThumbnailSize.Biggest
    )
    var playerThumbnailSizeL by rememberPreference(
        playerThumbnailSizeLKey,
        PlayerThumbnailSize.Biggest
    )
    var playerTimelineSize by rememberPreference(
        playerTimelineSizeKey,
        PlayerTimelineSize.Biggest
    )
    //

    var effectRotationEnabled by rememberPreference(effectRotationKey, true)

    var thumbnailTapEnabled by rememberPreference(thumbnailTapEnabledKey, true)


    var showButtonPlayerAddToPlaylist by rememberPreference(showButtonPlayerAddToPlaylistKey, true)
    var showButtonPlayerArrow by rememberPreference(showButtonPlayerArrowKey, true)
    var showButtonPlayerDownload by rememberPreference(showButtonPlayerDownloadKey, true)
    var showButtonPlayerLoop by rememberPreference(showButtonPlayerLoopKey, true)
    var showButtonPlayerLyrics by rememberPreference(showButtonPlayerLyricsKey, true)
    var expandedplayertoggle by rememberPreference(expandedplayertoggleKey, true)
    var showButtonPlayerShuffle by rememberPreference(showButtonPlayerShuffleKey, true)
    var showButtonPlayerSleepTimer by rememberPreference(showButtonPlayerSleepTimerKey, false)
    var showButtonPlayerMenu by rememberPreference(showButtonPlayerMenuKey, false)
    var showButtonPlayerStartradio by rememberPreference(showButtonPlayerStartRadioKey, false)
    var showButtonPlayerSystemEqualizer by rememberPreference(
        showButtonPlayerSystemEqualizerKey,
        false
    )
    var showButtonPlayerDiscover by rememberPreference(showButtonPlayerDiscoverKey, false)
    var showButtonPlayerVideo by rememberPreference(showButtonPlayerVideoKey, false)

    val navigationBarPosition by rememberPreference(
        navigationBarPositionKey,
        NavigationBarPosition.Bottom
    )

    //var isGradientBackgroundEnabled by rememberPreference(isGradientBackgroundEnabledKey, false)
    var showTotalTimeQueue by rememberPreference(showTotalTimeQueueKey, true)
    var backgroundProgress by rememberPreference(
        backgroundProgressKey,
        BackgroundProgress.MiniPlayer
    )
    var showNextSongsInPlayer by rememberPreference(showNextSongsInPlayerKey, false)
    var showRemainingSongTime by rememberPreference(showRemainingSongTimeKey, true)
    var clickLyricsText by rememberPreference(clickOnLyricsTextKey, true)
    var showBackgroundLyrics by rememberPreference(showBackgroundLyricsKey, false)

    val search = Search.init()

    var thumbnailRoundness by rememberPreference(
        thumbnailRoundnessKey,
        ThumbnailRoundness.Heavy
    )

    var miniPlayerType by rememberPreference(
        miniPlayerTypeKey,
        MiniPlayerType.Modern
    )
    var playerBackgroundColors by rememberPreference(
        playerBackgroundColorsKey,
        PlayerBackgroundColors.BlurredCoverColor
    )

    var showTopActionsBar by rememberPreference(showTopActionsBarKey, true)
    var playerControlsType by rememberPreference(playerControlsTypeKey, PlayerControlsType.Essential)
    var playerInfoType by rememberPreference(playerInfoTypeKey, PlayerInfoType.Essential)
    var transparentBackgroundActionBarPlayer by rememberPreference(
        transparentBackgroundPlayerActionBarKey,
        false
    )
    var iconLikeType by rememberPreference(iconLikeTypeKey, IconLikeType.Essential)
    var playerSwapControlsWithTimeline by rememberPreference(
        playerSwapControlsWithTimelineKey,
        false
    )
    var playerEnableLyricsPopupMessage by rememberPreference(
        playerEnableLyricsPopupMessageKey,
        true
    )
    var actionspacedevenly by rememberPreference(actionspacedevenlyKey, false)
    var thumbnailType by rememberPreference(thumbnailTypeKey, ThumbnailType.Modern)
    var showvisthumbnail by rememberPreference(showvisthumbnailKey, false)
    var buttonzoomout by rememberPreference(buttonzoomoutKey, false)
    var thumbnailpause by rememberPreference(thumbnailpauseKey, false)
    var showsongs by rememberPreference(showsongsKey, SongsNumber.`2`)
    var showalbumcover by rememberPreference(showalbumcoverKey, true)
    var prevNextSongs by rememberPreference(prevNextSongsKey, PrevNextSongs.twosongs)
    var tapqueue by rememberPreference(tapqueueKey, true)
    var swipeUpQueue by rememberPreference(swipeUpQueueKey, true)
    var statsfornerds by rememberPreference(statsfornerdsKey, false)

    var playerType by rememberPreference(playerTypeKey, PlayerType.Essential)
    var queueType by rememberPreference(queueTypeKey, QueueType.Essential)
    var noblur by rememberPreference(noblurKey, true)
    var fadingedge by rememberPreference(fadingedgeKey, false)
    var carousel by rememberPreference(carouselKey, true)
    var carouselSize by rememberPreference(carouselSizeKey, CarouselSize.Biggest)
    var keepPlayerMinimized by rememberPreference(keepPlayerMinimizedKey,false)
    var playerInfoShowIcons by rememberPreference(playerInfoShowIconsKey, true)
    var queueDurationExpanded by rememberPreference(queueDurationExpandedKey, true)
    var titleExpanded by rememberPreference(titleExpandedKey, true)
    var timelineExpanded by rememberPreference(timelineExpandedKey, true)
    var controlsExpanded by rememberPreference(controlsExpandedKey, true)
    var miniQueueExpanded by rememberPreference(miniQueueExpandedKey, true)
    var statsExpanded by rememberPreference(statsExpandedKey, true)
    var actionExpanded by rememberPreference(actionExpandedKey, true)
    var restartService by rememberSaveable { mutableStateOf(false) }
    var showCoverThumbnailAnimation by rememberPreference(showCoverThumbnailAnimationKey, false)
    var coverThumbnailAnimation by rememberPreference(coverThumbnailAnimationKey, ThumbnailCoverType.Vinyl)

    var notificationPlayerFirstIcon by rememberPreference(notificationPlayerFirstIconKey, NotificationButtons.Download)
    var notificationPlayerSecondIcon by rememberPreference(notificationPlayerSecondIconKey, NotificationButtons.Favorites)
    var enableWallpaper by rememberPreference(enableWallpaperKey, false)
    var wallpaperType by rememberPreference(wallpaperTypeKey, WallpaperType.Lockscreen)
    var topPadding by rememberPreference(topPaddingKey, true)
    var animatedGradient by rememberPreference(
        animatedGradientKey,
        AnimatedGradient.Linear
    )

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            //.fillMaxSize()
            .fillMaxHeight()
            .fillMaxWidth(
                if (navigationBarPosition == NavigationBarPosition.Left ||
                    navigationBarPosition == NavigationBarPosition.Top ||
                    navigationBarPosition == NavigationBarPosition.Bottom
                ) 1f
                else Dimensions.contentWidthRightBar
            )
            .verticalScroll(rememberScrollState())
        /*
        .padding(
            LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                .asPaddingValues()
        )

         */
    ) {
        HeaderWithIcon(
            title = stringResource(R.string.player_appearance),
            iconId = R.drawable.color_palette,
            enabled = false,
            showIcon = true,
            modifier = Modifier,
            onClick = {}
        )

        search.ToolBarButton()
        search.SearchBar( this )

        //SettingsEntryGroupText(stringResource(R.string.user_interface))

        //SettingsGroupSpacer()
        SettingsEntryGroupText(title = stringResource(R.string.player))

        if (playerBackgroundColors != PlayerBackgroundColors.BlurredCoverColor)
            showthumbnail = true
        if (!visualizerEnabled) showvisthumbnail = false
        if (!showthumbnail) {showlyricsthumbnail = false; showvisthumbnail = false}
        if (playerType == PlayerType.Modern) {
            showlyricsthumbnail = false
            showvisthumbnail = false
            thumbnailpause = false
            //keepPlayerMinimized = false
        }

        if (!isLandscape) {
            if (search.input.isBlank() || stringResource(R.string.show_player_top_actions_bar).contains(
                    search.input,
                    true
                )
            )
                SwitchSettingEntry(
                    title = stringResource(R.string.show_player_top_actions_bar),
                    text = "",
                    isChecked = showTopActionsBar,
                    onCheckedChange = { showTopActionsBar = it }
                )

            if (!showTopActionsBar) {
                if (search.input.isBlank() || stringResource(R.string.blankspace).contains(
                        search.input,
                        true
                    )
                )
                    SwitchSettingEntry(
                        title = stringResource(R.string.blankspace),
                        text = "",
                        isChecked = topPadding,
                        onCheckedChange = { topPadding = it }
                    )
            }
        }
        if (search.input.isBlank() || stringResource(R.string.playertype).contains(
                search.input,
                true
            )
        )
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.playertype),
                selectedValue = playerType,
                onValueSelected = {
                    playerType = it
                },
                valueText = { it.text },
            )

        if (search.input.isBlank() || stringResource(R.string.queuetype).contains(
                search.input,
                true
            )
        )
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.queuetype),
                selectedValue = queueType,
                onValueSelected = {
                    queueType = it
                },
                valueText = { it.text },
            )

        if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) {
            if (search.input.isBlank() || stringResource(R.string.show_thumbnail).contains(
                    search.input,
                    true
                )
            )
                SwitchSettingEntry(
                    title = stringResource(R.string.show_thumbnail),
                    text = "",
                    isChecked = showthumbnail,
                    onCheckedChange = {showthumbnail = it},
                )
        }
        AnimatedVisibility(visible = showthumbnail) {
            Column {
                if (playerType == PlayerType.Modern) {
                    if (search.input.isBlank() || stringResource(R.string.fadingedge).contains(
                            search.input,
                            true
                        )
                    )
                        SwitchSettingEntry(
                            title = stringResource(R.string.fadingedge),
                            text = "",
                            isChecked = fadingedge,
                            onCheckedChange = { fadingedge = it },
                            modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                        )
                }

                if (playerType == PlayerType.Modern && !isLandscape && (expandedplayertoggle || expandedplayer)) {
                    if (search.input.isBlank() || stringResource(R.string.carousel).contains(
                            search.input,
                            true
                        )
                    )
                        SwitchSettingEntry(
                            title = stringResource(R.string.carousel),
                            text = "",
                            isChecked = carousel,
                            onCheckedChange = { carousel = it },
                            modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                        )

                    if (search.input.isBlank() || stringResource(R.string.carouselsize).contains(
                            search.input,
                            true
                        )
                    )
                        EnumValueSelectorSettingsEntry(
                            title = stringResource(R.string.carouselsize),
                            selectedValue = carouselSize,
                            onValueSelected = { carouselSize = it },
                            valueText = { it.text },
                            modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                        )
                }
                if (playerType == PlayerType.Essential) {

                    if (search.input.isBlank() || stringResource(R.string.thumbnailpause).contains(
                            search.input,
                            true
                        )
                    )
                        SwitchSettingEntry(
                            title = stringResource(R.string.thumbnailpause),
                            text = "",
                            isChecked = thumbnailpause,
                            onCheckedChange = { thumbnailpause = it },
                            modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                        )

                    if (search.input.isBlank() || stringResource(R.string.show_lyrics_thumbnail).contains(
                            search.input,
                            true
                        )
                    )
                        SwitchSettingEntry(
                            title = stringResource(R.string.show_lyrics_thumbnail),
                            text = "",
                            isChecked = showlyricsthumbnail,
                            onCheckedChange = { showlyricsthumbnail = it },
                            modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                        )
                    if (visualizerEnabled) {
                        if (search.input.isBlank() || stringResource(R.string.showvisthumbnail).contains(
                                search.input,
                                true
                            )
                        )
                            SwitchSettingEntry(
                                title = stringResource(R.string.showvisthumbnail),
                                text = "",
                                isChecked = showvisthumbnail,
                                onCheckedChange = { showvisthumbnail = it },
                                modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                            )
                    }
                }

                if (search.input.isBlank() || stringResource(R.string.show_cover_thumbnail_animation).contains(
                        search.input,
                        true
                    )
                ) {
                    SwitchSettingEntry(
                        title = stringResource(R.string.show_cover_thumbnail_animation),
                        text = "",
                        isChecked = showCoverThumbnailAnimation,
                        onCheckedChange = { showCoverThumbnailAnimation = it },
                        modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                    )
                    AnimatedVisibility(visible = showCoverThumbnailAnimation) {
                        Column {
                            EnumValueSelectorSettingsEntry(
                                title = stringResource(R.string.cover_thumbnail_animation_type),
                                selectedValue = coverThumbnailAnimation,
                                onValueSelected = { coverThumbnailAnimation = it },
                                valueText = { it.text },
                                modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 50.dp else 25.dp)
                            )
                        }
                    }
                }

                if (isLandscape) {
                    if (search.input.isBlank() || stringResource(R.string.player_thumbnail_size).contains(
                            search.input,
                            true
                        )
                    )
                        EnumValueSelectorSettingsEntry(
                            title = stringResource(R.string.player_thumbnail_size),
                            selectedValue = playerThumbnailSizeL,
                            onValueSelected = { playerThumbnailSizeL = it },
                            valueText = { it.text },
                            modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                        )
                } else {
                    if (search.input.isBlank() || stringResource(R.string.player_thumbnail_size).contains(
                            search.input,
                            true
                        )
                    )
                        EnumValueSelectorSettingsEntry(
                            title = stringResource(R.string.player_thumbnail_size),
                            selectedValue = playerThumbnailSize,
                            onValueSelected = { playerThumbnailSize = it },
                            valueText = { it.text },
                            modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                        )
                }
                if (search.input.isBlank() || stringResource(R.string.thumbnailtype).contains(
                        search.input,
                        true
                    )
                )
                    EnumValueSelectorSettingsEntry(
                        title = stringResource(R.string.thumbnailtype),
                        selectedValue = thumbnailType,
                        onValueSelected = {
                            thumbnailType = it
                        },
                        valueText = { it.text },
                        modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                    )

                if (search.input.isBlank() || stringResource(R.string.thumbnail_roundness).contains(
                        search.input,
                        true
                    )
                )
                    EnumValueSelectorSettingsEntry(
                        title = stringResource(R.string.thumbnail_roundness),
                        selectedValue = thumbnailRoundness,
                        onValueSelected = { thumbnailRoundness = it },
                        trailingContent = {
                            Spacer(
                                modifier = Modifier
                                    .border(
                                        width = 1.dp,
                                        color = colorPalette().accent,
                                        shape = thumbnailRoundness.shape()
                                    )
                                    .background(
                                        color = colorPalette().background1,
                                        shape = thumbnailRoundness.shape()
                                    )
                                    .size(36.dp)
                            )
                        },
                        valueText = { it.text },
                        modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor) 25.dp else 0.dp)
                    )
            }
        }

        if (!showthumbnail) {
            if (search.input.isBlank() || stringResource(R.string.noblur).contains(
                    search.input,
                    true
                )
            )
                SwitchSettingEntry(
                    title = stringResource(R.string.noblur),
                    text = "",
                    isChecked = noblur,
                    onCheckedChange = { noblur = it }
                )


        }

        if (!(showthumbnail && playerType == PlayerType.Essential)){
            if (search.input.isBlank() || stringResource(R.string.statsfornerdsplayer).contains(
                    search.input,
                    true
                )
            )
                SwitchSettingEntry(
                    title = stringResource(R.string.statsfornerdsplayer),
                    text = "",
                    isChecked = statsfornerds,
                    onCheckedChange = { statsfornerds = it }
                )
        }

        if (search.input.isBlank() || stringResource(R.string.timelinesize).contains(
                search.input,
                true
            )
        )
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.timelinesize),
                selectedValue = playerTimelineSize,
                onValueSelected = { playerTimelineSize = it },
                valueText = { it.text }
            )

        if (search.input.isBlank() || stringResource(R.string.pinfo_type).contains(
                search.input,
                true
            )
        ) {
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.pinfo_type),
                selectedValue = playerInfoType,
                onValueSelected = {
                    playerInfoType = it
                },
                valueText = { it.text },
            )
            SettingsDescription(text = stringResource(R.string.pinfo_album_and_artist_name))

            AnimatedVisibility( visible = playerInfoType == PlayerInfoType.Modern) {
                Column {
                    if (search.input.isBlank() || stringResource(R.string.pinfo_show_icons).contains(
                            search.input,
                            true
                        )
                    )
                        SwitchSettingEntry(
                            title = stringResource(R.string.pinfo_show_icons),
                            text = "",
                            isChecked = playerInfoShowIcons,
                            onCheckedChange = { playerInfoShowIcons = it },
                            modifier = Modifier
                                .padding(start = 25.dp)
                        )
                }
            }

        }



        if (search.input.isBlank() || stringResource(R.string.miniplayertype).contains(
                search.input,
                true
            )
        )
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.miniplayertype),
                selectedValue = miniPlayerType,
                onValueSelected = {
                    miniPlayerType = it
                },
                valueText = { it.text },
            )

        if (search.input.isBlank() || stringResource(R.string.player_swap_controls_with_timeline).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.player_swap_controls_with_timeline),
                text = "",
                isChecked = playerSwapControlsWithTimeline,
                onCheckedChange = { playerSwapControlsWithTimeline = it }
            )

        if (search.input.isBlank() || stringResource(R.string.timeline).contains(
                search.input,
                true
            )
        )
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.timeline),
                selectedValue = playerTimelineType,
                onValueSelected = { playerTimelineType = it },
                valueText = { it.text }
            )

        if (search.input.isBlank() || stringResource(R.string.transparentbar).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.transparentbar),
                text = "",
                isChecked = transparentbar,
                onCheckedChange = { transparentbar = it }
            )

        if (search.input.isBlank() || stringResource(R.string.pcontrols_type).contains(
                search.input,
                true
            )
        )
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.pcontrols_type),
                selectedValue = playerControlsType,
                onValueSelected = {
                    playerControlsType = it
                },
                valueText = { it.text }
            )


        if (search.input.isBlank() || stringResource(R.string.play_button).contains(
                search.input,
                true
            )
        )
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.play_button),
                selectedValue = playerPlayButtonType,
                onValueSelected = {
                    playerPlayButtonType = it
                    lastPlayerPlayButtonType = it
                },
                valueText = { it.text }
            )

        if (search.input.isBlank() || stringResource(R.string.buttonzoomout).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.buttonzoomout),
                text = "",
                isChecked = buttonzoomout,
                onCheckedChange = { buttonzoomout = it }
            )


        if (search.input.isBlank() || stringResource(R.string.play_button).contains(
                search.input,
                true
            )
        )
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.icon_like_button),
                selectedValue = iconLikeType,
                onValueSelected = {
                    iconLikeType = it
                },
                valueText = { it.text },
            )

        /*

        if (filter.isNullOrBlank() || stringResource(R.string.use_gradient_background).contains(filterCharSequence,true))
            SwitchSettingEntry(
                title = stringResource(R.string.use_gradient_background),
                text = "",
                isChecked = isGradientBackgroundEnabled,
                onCheckedChange = { isGradientBackgroundEnabled = it }
            )
         */

        if (search.input.isBlank() || stringResource(R.string.background_colors).contains(
                search.input,
                true
            )
        )
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.background_colors),
                selectedValue = playerBackgroundColors,
                onValueSelected = {
                    playerBackgroundColors = it
                },
                valueText = { it.text }
            )

        AnimatedVisibility(visible = playerBackgroundColors == PlayerBackgroundColors.AnimatedGradient) {
            if (search.input.isBlank() || stringResource(R.string.gradienttype).contains(
                    search.input,
                    true
                )
            )
                EnumValueSelectorSettingsEntry(
                    title = stringResource(R.string.gradienttype),
                    selectedValue = animatedGradient,
                    onValueSelected = {
                        animatedGradient = it
                    },
                    valueText = { it.text },
                    modifier = Modifier.padding(start = if (playerBackgroundColors == PlayerBackgroundColors.AnimatedGradient) 25.dp else 0.dp)
                )
        }

        if ((playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient) || (playerBackgroundColors == PlayerBackgroundColors.ThemeColorGradient))
            if (search.input.isBlank() || stringResource(R.string.blackgradient).contains(
                    search.input,
                    true
                )
            )
                SwitchSettingEntry(
                    title = stringResource(R.string.blackgradient),
                    text = "",
                    isChecked = blackgradient,
                    onCheckedChange = { blackgradient = it }
                )
        if (playerBackgroundColors == PlayerBackgroundColors.BlurredCoverColor)
            if (search.input.isBlank() || stringResource(R.string.bottomgradient).contains(
                    search.input,
                    true
                )
            )
                SwitchSettingEntry(
                    title = stringResource(R.string.bottomgradient),
                    text = "",
                    isChecked = bottomgradient,
                    onCheckedChange = { bottomgradient = it }
                )
        if (search.input.isBlank() || stringResource(R.string.textoutline).contains(
              search.input,
              true
              )
         )
             SwitchSettingEntry(
                 title = stringResource(R.string.textoutline),
                 text = "",
                 isChecked = textoutline,
                 onCheckedChange = { textoutline = it }
             )

       if (search.input.isBlank() || stringResource(R.string.show_total_time_of_queue).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.show_total_time_of_queue),
                text = "",
                isChecked = showTotalTimeQueue,
                onCheckedChange = { showTotalTimeQueue = it }
            )

        if (search.input.isBlank() || stringResource(R.string.show_remaining_song_time).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.show_remaining_song_time),
                text = "",
                isChecked = showRemainingSongTime,
                onCheckedChange = { showRemainingSongTime = it }
            )

        if (search.input.isBlank() || stringResource(R.string.show_next_songs_in_player).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.show_next_songs_in_player),
                text = "",
                isChecked = showNextSongsInPlayer,
                onCheckedChange = { showNextSongsInPlayer = it }
            )
        AnimatedVisibility( visible = showNextSongsInPlayer) {
          Column {
              if (search.input.isBlank() || stringResource(R.string.showtwosongs).contains(search.input,true))
                  EnumValueSelectorSettingsEntry(
                      title = stringResource(R.string.songs_number_to_show),
                      selectedValue = showsongs,
                      onValueSelected = {
                          showsongs = it
                      },
                      valueText = { it.name },
                      modifier = Modifier
                          .padding(start = 25.dp)
                  )


            if (search.input.isBlank() || stringResource(R.string.showalbumcover).contains(
                    search.input,
                    true
                )
            )
                SwitchSettingEntry(
                    title = stringResource(R.string.showalbumcover),
                    text = "",
                    isChecked = showalbumcover,
                    onCheckedChange = { showalbumcover = it },
                      modifier = Modifier.padding(start = 25.dp)
                  )
          }
        }

        if (search.input.isBlank() || stringResource(R.string.disable_scrolling_text).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.disable_scrolling_text),
                text = stringResource(R.string.scrolling_text_is_used_for_long_texts),
                isChecked = disableScrollingText,
                onCheckedChange = { disableScrollingText = it }
            )
        if (playerType == PlayerType.Essential) {
            if (search.input.isBlank() || stringResource(R.string.disable_horizontal_swipe).contains(
                    search.input,
                    true
                )
            )
                SwitchSettingEntry(
                    title = stringResource(R.string.disable_horizontal_swipe),
                    text = stringResource(R.string.disable_song_switching_via_swipe),
                    isChecked = disablePlayerHorizontalSwipe,
                    onCheckedChange = { disablePlayerHorizontalSwipe = it }
                )
        }

        if (search.input.isBlank() || stringResource(R.string.player_rotating_buttons).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.player_rotating_buttons),
                text = stringResource(R.string.player_enable_rotation_buttons),
                isChecked = effectRotationEnabled,
                onCheckedChange = { effectRotationEnabled = it }
            )

        if (search.input.isBlank() || stringResource(R.string.toggle_lyrics).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.toggle_lyrics),
                text = stringResource(R.string.by_tapping_on_the_thumbnail),
                isChecked = thumbnailTapEnabled,
                onCheckedChange = { thumbnailTapEnabled = it }
            )

        if (search.input.isBlank() || stringResource(R.string.click_lyrics_text).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.click_lyrics_text),
                text = "",
                isChecked = clickLyricsText,
                onCheckedChange = { clickLyricsText = it }
            )
        if (showlyricsthumbnail)
            if (search.input.isBlank() || stringResource(R.string.show_background_in_lyrics).contains(
                    search.input,
                    true
                )
            )
                SwitchSettingEntry(
                    title = stringResource(R.string.show_background_in_lyrics),
                    text = "",
                    isChecked = showBackgroundLyrics,
                    onCheckedChange = { showBackgroundLyrics = it }
                )

        if (search.input.isBlank() || stringResource(R.string.player_enable_lyrics_popup_message).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.player_enable_lyrics_popup_message),
                text = "",
                isChecked = playerEnableLyricsPopupMessage,
                onCheckedChange = { playerEnableLyricsPopupMessage = it }
            )

        if (search.input.isBlank() || stringResource(R.string.background_progress_bar).contains(
                search.input,
                true
            )
        )
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.background_progress_bar),
                selectedValue = backgroundProgress,
                onValueSelected = {
                    backgroundProgress = it
                },
                valueText = { it.text },
            )


        if (search.input.isBlank() || stringResource(R.string.visualizer).contains(
                search.input,
                true
            )
        ) {
            SwitchSettingEntry(
                title = stringResource(R.string.visualizer),
                text = "",
                isChecked = visualizerEnabled,
                onCheckedChange = { visualizerEnabled = it }
            )
            /*
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.visualizer),
                selectedValue = playerVisualizerType,
                onValueSelected = { playerVisualizerType = it },
                valueText = {
                    when (it) {
                        PlayerVisualizerType.Fancy -> stringResource(R.string.vt_fancy)
                        PlayerVisualizerType.Circular -> stringResource(R.string.vt_circular)
                        PlayerVisualizerType.Disabled -> stringResource(R.string.vt_disabled)
                        PlayerVisualizerType.Stacked -> stringResource(R.string.vt_stacked)
                        PlayerVisualizerType.Oneside -> stringResource(R.string.vt_one_side)
                        PlayerVisualizerType.Doubleside -> stringResource(R.string.vt_double_side)
                        PlayerVisualizerType.DoublesideCircular -> stringResource(R.string.vt_double_side_circular)
                        PlayerVisualizerType.Full -> stringResource(R.string.vt_full)
                    }
                }
            )
            */
            ImportantSettingsDescription(text = stringResource(R.string.visualizer_require_mic_permission))
        }

        SettingsGroupSpacer()
        SettingsEntryGroupText(title = stringResource(R.string.player_action_bar))

        if (search.input.isBlank() || stringResource(R.string.action_bar_transparent_background).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.action_bar_transparent_background),
                text = "",
                isChecked = transparentBackgroundActionBarPlayer,
                onCheckedChange = { transparentBackgroundActionBarPlayer = it }
            )

        if (search.input.isBlank() || stringResource(R.string.actionspacedevenly).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.actionspacedevenly),
                text = "",
                isChecked = actionspacedevenly,
                onCheckedChange = { actionspacedevenly = it }
            )

        if (search.input.isBlank() || stringResource(R.string.tapqueue).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.tapqueue),
                text = "",
                isChecked = tapqueue,
                onCheckedChange = { tapqueue = it }
            )

        if (search.input.isBlank() || stringResource(R.string.swipe_up_to_open_the_queue).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.swipe_up_to_open_the_queue),
                text = "",
                isChecked = swipeUpQueue,
                onCheckedChange = { swipeUpQueue = it }
            )

        if (search.input.isBlank() || stringResource(R.string.action_bar_show_video_button).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.action_bar_show_video_button),
                text = "",
                isChecked = showButtonPlayerVideo,
                onCheckedChange = { showButtonPlayerVideo = it }
            )

        if (search.input.isBlank() || stringResource(R.string.action_bar_show_discover_button).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.action_bar_show_discover_button),
                text = "",
                isChecked = showButtonPlayerDiscover,
                onCheckedChange = { showButtonPlayerDiscover = it }
            )

        if (search.input.isBlank() || stringResource(R.string.action_bar_show_download_button).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.action_bar_show_download_button),
                text = "",
                isChecked = showButtonPlayerDownload,
                onCheckedChange = { showButtonPlayerDownload = it }
            )

        if (search.input.isBlank() || stringResource(R.string.action_bar_show_add_to_playlist_button).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.action_bar_show_add_to_playlist_button),
                text = "",
                isChecked = showButtonPlayerAddToPlaylist,
                onCheckedChange = { showButtonPlayerAddToPlaylist = it }
            )

        if (search.input.isBlank() || stringResource(R.string.action_bar_show_loop_button).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.action_bar_show_loop_button),
                text = "",
                isChecked = showButtonPlayerLoop,
                onCheckedChange = { showButtonPlayerLoop = it }
            )

        if (search.input.isBlank() || stringResource(R.string.action_bar_show_shuffle_button).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.action_bar_show_shuffle_button),
                text = "",
                isChecked = showButtonPlayerShuffle,
                onCheckedChange = { showButtonPlayerShuffle = it }
            )

        if (search.input.isBlank() || stringResource(R.string.action_bar_show_lyrics_button).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.action_bar_show_lyrics_button),
                text = "",
                isChecked = showButtonPlayerLyrics,
                onCheckedChange = { showButtonPlayerLyrics = it }
            )
        if (!isLandscape || !showthumbnail) {
            if (!showlyricsthumbnail) {
                if (search.input.isBlank() || stringResource(R.string.expandedplayer).contains(
                        search.input,
                        true
                    )
                )
                    SwitchSettingEntry(
                        title = stringResource(R.string.expandedplayer),
                        text = "",
                        isChecked = expandedplayertoggle,
                        onCheckedChange = { expandedplayertoggle = it }
                    )
            }
        }

        if (search.input.isBlank() || stringResource(R.string.action_bar_show_sleep_timer_button).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.action_bar_show_sleep_timer_button),
                text = "",
                isChecked = showButtonPlayerSleepTimer,
                onCheckedChange = { showButtonPlayerSleepTimer = it }
            )

        if (search.input.isBlank() || stringResource(R.string.show_equalizer).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.show_equalizer),
                text = "",
                isChecked = showButtonPlayerSystemEqualizer,
                onCheckedChange = { showButtonPlayerSystemEqualizer = it }
            )

        if (search.input.isBlank() || stringResource(R.string.action_bar_show_arrow_button_to_open_queue).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.action_bar_show_arrow_button_to_open_queue),
                text = "",
                isChecked = showButtonPlayerArrow,
                onCheckedChange = { showButtonPlayerArrow = it }
            )

        if (search.input.isBlank() || stringResource(R.string.action_bar_show_start_radio_button).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.action_bar_show_start_radio_button),
                text = "",
                isChecked = showButtonPlayerStartradio,
                onCheckedChange = { showButtonPlayerStartradio = it }
            )

        if (search.input.isBlank() || stringResource(R.string.action_bar_show_menu_button).contains(
                search.input,
                true
            )
        )
            SwitchSettingEntry(
                title = stringResource(R.string.action_bar_show_menu_button),
                text = "",
                isChecked = showButtonPlayerMenu,
                onCheckedChange = { showButtonPlayerMenu = it }
            )

        if (!showlyricsthumbnail) {
            SettingsGroupSpacer()
            SettingsEntryGroupText(title = stringResource(R.string.full_screen_lyrics_components))

            if (showTotalTimeQueue) {
                if (search.input.isBlank() || stringResource(R.string.show_total_time_of_queue).contains(
                        search.input,
                        true
                    )
                )
                    SwitchSettingEntry(
                        title = stringResource(R.string.show_total_time_of_queue),
                        text = "",
                        isChecked = queueDurationExpanded,
                        onCheckedChange = { queueDurationExpanded = it }
                    )
            }

            if (search.input.isBlank() || stringResource(R.string.titleartist).contains(
                    search.input,
                    true
                )
            )
                SwitchSettingEntry(
                    title = stringResource(R.string.titleartist),
                    text = "",
                    isChecked = titleExpanded,
                    onCheckedChange = { titleExpanded = it }
                )

            if (search.input.isBlank() || stringResource(R.string.timeline).contains(
                    search.input,
                    true
                )
            )
                SwitchSettingEntry(
                    title = stringResource(R.string.timeline),
                    text = "",
                    isChecked = timelineExpanded,
                    onCheckedChange = { timelineExpanded = it }
                )

            if (search.input.isBlank() || stringResource(R.string.controls).contains(
                    search.input,
                    true
                )
            )
                SwitchSettingEntry(
                    title = stringResource(R.string.controls),
                    text = "",
                    isChecked = controlsExpanded,
                    onCheckedChange = { controlsExpanded = it }
                )

            if (statsfornerds){
                if (search.input.isBlank() || stringResource(R.string.statsfornerds).contains(
                        search.input,
                        true
                    )
                )
                    SwitchSettingEntry(
                        title = stringResource(R.string.statsfornerds),
                        text = "",
                        isChecked = statsExpanded,
                        onCheckedChange = { statsExpanded = it }
                    )
            }

            if (
                showButtonPlayerDownload ||
                showButtonPlayerAddToPlaylist ||
                showButtonPlayerLoop ||
                showButtonPlayerShuffle ||
                showButtonPlayerLyrics ||
                showButtonPlayerSleepTimer ||
                showButtonPlayerSystemEqualizer ||
                showButtonPlayerArrow ||
                showButtonPlayerMenu ||
                expandedplayertoggle ||
                showButtonPlayerDiscover ||
                showButtonPlayerVideo
            ){
                if (search.input.isBlank() || stringResource(R.string.actionbar).contains(
                        search.input,
                        true
                    )
                )
                    SwitchSettingEntry(
                        title = stringResource(R.string.actionbar),
                        text = "",
                        isChecked = actionExpanded,
                        onCheckedChange = {
                            actionExpanded = it
                        }
                    )
            }
            if (showNextSongsInPlayer && actionExpanded) {
                if (search.input.isBlank() || stringResource(R.string.miniqueue).contains(
                        search.input,
                        true
                    )
                )
                    SwitchSettingEntry(
                        title = stringResource(R.string.miniqueue),
                        text = "",
                        isChecked = miniQueueExpanded,
                        onCheckedChange = { miniQueueExpanded = it }
                    )
            }

        }


        SettingsGroupSpacer()
        SettingsEntryGroupText(title = stringResource(R.string.notification_player))

        if (search.input.isBlank() || stringResource(R.string.notification_player).contains(
                search.input,
                true
            )
        ) {
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.notificationPlayerFirstIcon),
                selectedValue = notificationPlayerFirstIcon,
                onValueSelected = {
                    notificationPlayerFirstIcon = it
                    restartService = true
                },
                valueText = { it.text },
            )
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.notificationPlayerSecondIcon),
                selectedValue = notificationPlayerSecondIcon,
                onValueSelected = {
                    notificationPlayerSecondIcon = it
                    restartService = true
                },
                valueText = { it.text },
            )
            RestartPlayerService(restartService, onRestart = { restartService = false })
        }


//        if (search.input.isBlank() || stringResource(R.string.show_song_cover).contains(
//                search.input,
//                true
//            )
//        )
//            if (!isAtLeastAndroid13) {
//                SettingsGroupSpacer()
//
//                SettingsEntryGroupText(title = stringResource(R.string.lockscreen))
//
//                SwitchSettingEntry(
//                    title = stringResource(R.string.show_song_cover),
//                    text = stringResource(R.string.use_song_cover_on_lockscreen),
//                    isChecked = isShowingThumbnailInLockscreen,
//                    onCheckedChange = { isShowingThumbnailInLockscreen = it }
//                )
//            }

        if (isAtLeastAndroid7) {
            SettingsGroupSpacer()
            SettingsEntryGroupText(title = stringResource(R.string.wallpaper))
            SwitchSettingEntry(
                title = stringResource(R.string.enable_wallpaper),
                text = "",
                isChecked = enableWallpaper,
                onCheckedChange = { enableWallpaper = it }
            )
            AnimatedVisibility(visible = enableWallpaper) {
                Column {
                    EnumValueSelectorSettingsEntry(
                        title = stringResource(R.string.set_cover_thumbnail_as_wallpaper),
                        selectedValue = wallpaperType,
                        onValueSelected = {
                            wallpaperType = it
                            restartService = true
                        },
                        valueText = {
                            it.displayName
                        },
                        modifier = Modifier.padding(start = 25.dp)
                    )
                    RestartPlayerService(restartService, onRestart = { restartService = false })
                }
            }
        }

        SettingsGroupSpacer()
        var resetToDefault by remember { mutableStateOf(false) }
        val context = LocalContext.current
        ButtonBarSettingEntry(
            title = stringResource(R.string.settings_reset),
            text = stringResource(R.string.settings_restore_default_settings),
            icon = R.drawable.refresh,
            iconColor = colorPalette().text,
            onClick = { resetToDefault = true },
        )
        if (resetToDefault) {
            DefaultAppearanceSettings()
            resetToDefault = false
            navController.popBackStack()
            SmartMessage(stringResource(R.string.done), context = context)
        }

        SettingsGroupSpacer(
            modifier = Modifier.height(Dimensions.bottomSpacer)
        )
    }
}
