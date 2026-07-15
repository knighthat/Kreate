package app.kreate.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import androidx.palette.graphics.Palette
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.service.player.StatefulPlayer
import app.kreate.android.themed.common.component.BottomMenu
import app.kreate.database.models.PersistentQueue
import app.kreate.gateway.innertube.YouTube
import app.kreate.preferences.Preferences
import app.kreate.util.thumbnail
import app.kreate.widgets.WidgetBroadcastReceiver
import app.kreate.widgets.state.WidgetColorState
import co.touchlab.kermit.Logger
import coil3.imageLoader
import coil3.request.allowHardware
import coil3.toBitmap
import com.kieronquinn.monetcompat.core.MonetCompat
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.song
import it.fast4x.innertube.utils.LocalePreferenceItem
import it.fast4x.innertube.utils.LocalePreferences
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.AnimatedGradient
import it.fast4x.rimusic.enums.ColorPaletteMode
import it.fast4x.rimusic.enums.ColorPaletteName
import it.fast4x.rimusic.enums.HomeScreenTabs
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.PipModule
import it.fast4x.rimusic.enums.PlayerBackgroundColors
import it.fast4x.rimusic.extensions.pip.PipEventContainer
import it.fast4x.rimusic.extensions.pip.PipModuleContainer
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.thumbnailShape
import it.fast4x.rimusic.ui.components.CustomModalBottomSheet
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.themed.CrossfadeContainer
import it.fast4x.rimusic.ui.screens.AppNavigation
import it.fast4x.rimusic.ui.screens.player.MiniPlayer
import it.fast4x.rimusic.ui.screens.player.Player
import it.fast4x.rimusic.ui.screens.player.components.YoutubePlayer
import it.fast4x.rimusic.ui.screens.player.rememberPlayerSheetState
import it.fast4x.rimusic.ui.styling.Appearance
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.ui.styling.applyPitchBlack
import it.fast4x.rimusic.ui.styling.colorPaletteOf
import it.fast4x.rimusic.ui.styling.customColorPalette
import it.fast4x.rimusic.ui.styling.dynamicColorPaletteOf
import it.fast4x.rimusic.ui.styling.typographyOf
import it.fast4x.rimusic.utils.LocalMonetCompat
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.forcePlay
import it.fast4x.rimusic.utils.isAtLeastAndroid6
import it.fast4x.rimusic.utils.isAtLeastAndroid8
import it.fast4x.rimusic.utils.isVideo
import it.fast4x.rimusic.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.utils.Toaster
import org.koin.compose.koinInject
import org.koin.java.KoinJavaComponent
import java.util.Locale


const val action_search = "it.fast4x.rimusic.action.search"
const val action_songs = "it.fast4x.rimusic.action.songs"
const val action_albums = "it.fast4x.rimusic.action.albums"
const val actions_artists = "it.fast4x.rimusic.action.artists"
const val action_library = "it.fast4x.rimusic.action.library"

private fun AppCompatActivity.setSystemBarAppearance(isDark: Boolean) {
    with(WindowCompat.getInsetsController(window, window.decorView.rootView)) {
        isAppearanceLightStatusBars = !isDark
        isAppearanceLightNavigationBars = !isDark
    }

    if (!isAtLeastAndroid6) {
        window.statusBarColor =
            (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
    }

    if (!isAtLeastAndroid8) {
        window.navigationBarColor =
            (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
    }
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalAnimationApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalTextApi::class
)
@Composable
fun AppCompatActivity.AppContent(
    monet: MonetCompat,
    pipState: MutableState<Boolean>,
    launchedFromNotification: Boolean,
    intentUriData: Uri?
) {
    val colorPaletteMode by Preferences.THEME_MODE.collectAsStateWithLifecycle()
    val isPicthBlack = colorPaletteMode == ColorPaletteMode.PitchBlack

    val coroutineScope = rememberCoroutineScope()
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val navController = rememberNavController()
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var switchToAudioPlayer by rememberSaveable { mutableStateOf(false) }
    val animatedGradient by Preferences.ANIMATED_GRADIENT.collectAsStateWithLifecycle()
    val customColor by Preferences.CUSTOM_COLOR.collectAsStateWithLifecycle()
    val lightTheme = colorPaletteMode == ColorPaletteMode.Light || (colorPaletteMode == ColorPaletteMode.System && (!isSystemInDarkTheme()))


    LocalePreferences.preference =
        LocalePreferenceItem(
            hl = Locale.getDefault().language,
            gl = Locale.getDefault().country
        )

    var appearance by rememberSaveable(
        !lightTheme,
        stateSaver = Appearance.Companion
    ) {
        val colorPaletteName = Preferences.COLOR_PALETTE.value
        val colorPaletteMode = Preferences.THEME_MODE.value
        val thumbnailRoundness = Preferences.THUMBNAIL_BORDER_RADIUS.value
        val useSystemFont = Preferences.USE_SYSTEM_FONT.value
        val applyFontPadding = Preferences.APPLY_FONT_PADDING.value
        val fontType = Preferences.FONT.value
        var colorPalette = colorPaletteOf(colorPaletteName, colorPaletteMode, !lightTheme)

        if (colorPaletteName == ColorPaletteName.MaterialYou) {
            colorPalette = dynamicColorPaletteOf(
                Color(monet.getAccentColor(this@AppContent)),
                !lightTheme
            )
        }
        if (colorPaletteName == ColorPaletteName.CustomColor) {
            colorPalette = dynamicColorPaletteOf(
                customColor,
                !lightTheme
            )
        }

        setSystemBarAppearance(colorPalette.isDark)

        mutableStateOf(
            Appearance(
                colorPalette = colorPalette,
                typography = typographyOf(
                    colorPalette.text,
                    useSystemFont,
                    applyFontPadding,
                    fontType
                ),
                thumbnailShape = thumbnailRoundness.shape
            )
        )
    }

    // Update widgets' color when appearance changes
    LaunchedEffect( appearance ) {
        val color = with( appearance.colorPalette ) {
            WidgetColorState(background0.value.toLong(), accent.value.toLong(), onAccent.value.toLong())
        }

        Intent(this@AppContent, WidgetBroadcastReceiver::class.java)
            .setAction( WidgetBroadcastReceiver.ACTION_SYNC )
            .putExtra( WidgetBroadcastReceiver.EXTRA_COLOR_STATE, color.toBundle() )
            .also( ::sendBroadcast )
    }

    fun setDynamicPalette(url: String) {
        val playerBackgroundColors = Preferences.PLAYER_BACKGROUND.value
        val colorPaletteName = Preferences.COLOR_PALETTE.value
        val isDynamicPalette = colorPaletteName == ColorPaletteName.Dynamic
        val isCoverColor =
            playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient ||
                    playerBackgroundColors == PlayerBackgroundColors.CoverColor ||
                    animatedGradient == AnimatedGradient.FluidCoverColorGradient

        if (!isDynamicPalette) return

        val colorPaletteMode = Preferences.THEME_MODE.value
        coroutineScope.launch(Dispatchers.Main) {
            val result = ImageFactory.requestBuilder( url ) {
                allowHardware( false )
            }.let { imageLoader.execute( it ) }
            val isPicthBlack = colorPaletteMode == ColorPaletteMode.PitchBlack
            val isDark =
                colorPaletteMode == ColorPaletteMode.Dark || isPicthBlack || (colorPaletteMode == ColorPaletteMode.System && isSystemInDarkTheme)

            val bitmap = result.image?.toBitmap()
            if (bitmap != null) {
                val palette = Palette
                    .from(bitmap)
                    .maximumColorCount(8)
                    .addFilter(if (isDark) ({ _, hsl -> hsl[0] !in 36f..100f }) else null)
                    .generate()
                println("Mainactivity onmediaItemTransition palette dominantSwatch: ${palette.dominantSwatch}")

                dynamicColorPaletteOf(bitmap, isDark)?.let {
                    withContext(Dispatchers.Main) {
                        setSystemBarAppearance(it.isDark)
                    }
                    appearance = appearance.copy(
                        colorPalette = if (!isPicthBlack) it else it.copy(
                            background0 = Color.Black,
                            background1 = Color.Black,
                            background2 = Color.Black,
                            background3 = Color.Black,
                            background4 = Color.Black,
                            // text = Color.White
                        ),
                        typography = appearance.typography.copy(it.text)
                    )
                    println("Mainactivity onmediaItemTransition appearance inside: ${appearance.colorPalette}")
                }

            }
        }
        println("Mainactivity onmediaItemTransition appearance outside: ${appearance.colorPalette}")
    }


    val player: StatefulPlayer = koinInject()
    DisposableEffect(player, !lightTheme) {
        val listener = Preferences.Listener { _, key ->
            withContext( Dispatchers.Main ) {
                when (key) {
                    Preferences.Key.MAIN_THEME,
                    Preferences.Key.NAVIGATION_BAR_POSITION,
                    Preferences.Key.NAVIGATION_BAR_TYPE,
                    Preferences.Key.MINI_PLAYER_TYPE -> this@AppContent.recreate()

                    Preferences.Key.COLOR_PALETTE,
                    Preferences.Key.THEME_MODE,
                    Preferences.Key.CUSTOM_LIGHT_THEME_BACKGROUND_0,
                    Preferences.Key.CUSTOM_LIGHT_THEME_BACKGROUND_1,
                    Preferences.Key.CUSTOM_LIGHT_THEME_BACKGROUND_2,
                    Preferences.Key.CUSTOM_LIGHT_THEME_BACKGROUND_3,
                    Preferences.Key.CUSTOM_LIGHT_THEME_BACKGROUND_4,
                    Preferences.Key.CUSTOM_LIGHT_TEXT,
                    Preferences.Key.CUSTOM_LIGHT_TEXT_SECONDARY,
                    Preferences.Key.CUSTOM_LIGHT_TEXT_DISABLED,
                    Preferences.Key.CUSTOM_LIGHT_PLAY_BUTTON,
                    Preferences.Key.CUSTOM_LIGHT_ACCENT,
                    Preferences.Key.CUSTOM_DARK_THEME_BACKGROUND_0,
                    Preferences.Key.CUSTOM_DARK_THEME_BACKGROUND_1,
                    Preferences.Key.CUSTOM_DARK_THEME_BACKGROUND_2,
                    Preferences.Key.CUSTOM_DARK_THEME_BACKGROUND_3,
                    Preferences.Key.CUSTOM_DARK_THEME_BACKGROUND_4,
                    Preferences.Key.CUSTOM_DARK_TEXT,
                    Preferences.Key.CUSTOM_DARK_TEXT_SECONDARY,
                    Preferences.Key.CUSTOM_DARK_TEXT_DISABLED,
                    Preferences.Key.CUSTOM_DARK_PLAY_BUTTON,
                    Preferences.Key.CUSTOM_DARK_ACCENT -> {
                        val colorPaletteName = Preferences.COLOR_PALETTE.value
                        val colorPaletteMode = Preferences.THEME_MODE.value

                        var colorPalette = colorPaletteOf(
                            colorPaletteName,
                            colorPaletteMode,
                            !lightTheme
                        )

                        if (colorPaletteName == ColorPaletteName.Dynamic) {
                            val artworkUri = player.currentMediaItem?.mediaMetadata?.artworkUri?.thumbnail(1200)?.toString().orEmpty()
                            artworkUri.let {
                                if (it.isNotEmpty())
                                    setDynamicPalette(it)
                                else {

                                    setSystemBarAppearance(colorPalette.isDark)
                                    appearance = appearance.copy(
                                        colorPalette = if (!isPicthBlack) colorPalette else colorPalette.copy(
                                            background0 = Color.Black,
                                            background1 = Color.Black,
                                            background2 = Color.Black,
                                            background3 = Color.Black,
                                            background4 = Color.Black,
                                            // text = Color.White
                                        ),
                                        typography = appearance.typography.copy(
                                            colorPalette.text
                                        ),
                                    )
                                }

                            }

                        } else {

                            if (colorPaletteName == ColorPaletteName.MaterialYou) {
                                colorPalette = dynamicColorPaletteOf(
                                    Color(monet.getAccentColor(this@AppContent)),
                                    !lightTheme
                                )
                            }

                            if (colorPaletteName == ColorPaletteName.Customized) {
                                colorPalette = customColorPalette(
                                    colorPalette,
                                    this@AppContent,
                                    isSystemInDarkTheme
                                )
                            }
                            if (colorPaletteName == ColorPaletteName.CustomColor) {
                                colorPalette = dynamicColorPaletteOf(
                                    customColor,
                                    !lightTheme
                                )
                            }

                            setSystemBarAppearance(colorPalette.isDark)

                            appearance = appearance.copy(
                                colorPalette = if (!isPicthBlack) colorPalette else colorPalette.copy(
                                    background0 = Color.Black,
                                    background1 = Color.Black,
                                    background2 = Color.Black,
                                    background3 = Color.Black,
                                    background4 = Color.Black,
                                    text = Color.White
                                ),
                                typography = appearance.typography.copy(if (!isPicthBlack) colorPalette.text else Color.White),
                            )
                        }
                    }

                    Preferences.Key.THUMBNAIL_BORDER_RADIUS -> {
                        appearance = appearance.copy(
                            thumbnailShape = Preferences.THUMBNAIL_BORDER_RADIUS.value.shape
                        )
                    }

                    Preferences.Key.USE_SYSTEM_FONT,
                    Preferences.Key.APPLY_FONT_PADDING,
                    Preferences.Key.FONT -> {
                        val useSystemFont = Preferences.USE_SYSTEM_FONT.value
                        val applyFontPadding = Preferences.APPLY_FONT_PADDING.value
                        val fontType = Preferences.FONT.value

                        appearance = appearance.copy(
                            typography = typographyOf(
                                appearance.colorPalette.text,
                                useSystemFont,
                                applyFontPadding,
                                fontType
                            ),
                        )
                    }
                }

            }
        }

        val colorPaletteName = Preferences.COLOR_PALETTE.value
        if (colorPaletteName == ColorPaletteName.Dynamic) {
            setDynamicPalette(
                (player.currentMediaItem?.mediaMetadata?.artworkUri.thumbnail(1200)
                    ?: "").toString()
            )
        }

        coroutineScope.launch {
            Preferences.addListener( listener )
        }
        onDispose {
            coroutineScope.launch {
                Preferences.removeListener( listener )
            }
        }
    }

    val rippleConfiguration =
        remember(appearance.colorPalette.text, appearance.colorPalette.isDark) {
            RippleConfiguration(color = appearance.colorPalette.text)
        }

    LaunchedEffect(Unit) {
        val colorPaletteName = Preferences.COLOR_PALETTE.value
        if (colorPaletteName == ColorPaletteName.Customized) {
            appearance = appearance.copy(
                colorPalette = customColorPalette(
                    appearance.colorPalette,
                    this@AppContent,
                    isSystemInDarkTheme
                )
            )
        }
    }


    if (colorPaletteMode == ColorPaletteMode.PitchBlack)
        appearance = appearance.copy(
            colorPalette = appearance.colorPalette.applyPitchBlack,
            typography = appearance.typography.copy(appearance.colorPalette.text)
        )




    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(appearance.colorPalette.background0)
    ) {


        val density = LocalDensity.current
        val windowsInsets = WindowInsets.systemBars
        val bottomDp = with(density) { windowsInsets.getBottom(density).toDp() }

        val playerSheetState = rememberPlayerSheetState(
            dismissedBound = 0.dp,
            collapsedBound = Dimensions.collapsedPlayer + bottomDp,
            expandedBound = maxHeight,
        )

        val playerState =
            rememberModalBottomSheetState(skipPartiallyExpanded = true)

        val playerAwareWindowInsets by remember(
            bottomDp,
            playerSheetState.value
        ) {
            derivedStateOf {
                val bottom = playerSheetState.value.coerceIn(
                    bottomDp,
                    playerSheetState.collapsedBound
                )

                windowsInsets
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                    .add(WindowInsets(bottom = bottom))
            }
        }

        CrossfadeContainer(state = pipState.value) { isCurrentInPip ->
            println("MainActivity pipState ${pipState.value} CrossfadeContainer isCurrentInPip $isCurrentInPip ")
            val pipModule by Preferences.PIP_MODULE.collectAsStateWithLifecycle()
            if (isCurrentInPip) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                ) {
                    when (pipModule) {
                        PipModule.Cover -> {
                            PipModuleContainer {
                                ImageFactory.AsyncImage(
                                    thumbnailUrl = player
                                        ?.currentMediaItem
                                        ?.mediaMetadata
                                        ?.artworkUri
                                        .toString()
                                        .resize( 1200, 1200 ),
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                    }

                }

            } else {
                val bottomMenu = remember { BottomMenu() }

                // FIXME: Why is this block getting called twice on start?
                CompositionLocalProvider(
                    LocalAppearance provides appearance,
                    LocalIndication provides ripple(bounded = true),
                    LocalRippleConfiguration provides rippleConfiguration,
                    LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                    LocalLayoutDirection provides LayoutDirection.Ltr,
                    LocalDownloadHelper provides MyDownloadHelper,
                    LocalPlayerSheetState provides playerState,
                    LocalMonetCompat provides monet,
                    LocalBottomMenu provides bottomMenu,
                ) {
                    // This block gets called twice on startup, first run resets
                    // [intent.action] to empty string, second run sets
                    // [Settings.HOME_TAB_INDEX] to default page, resulting
                    // in default page shows regardless of shortcut
                    val startPage = remember {
                        // This step picks index from shortcut (if applicable)
                        var tab = when( intent.action ) {
                            action_songs    -> HomeScreenTabs.Songs
                            action_albums   -> HomeScreenTabs.Albums
                            actions_artists -> HomeScreenTabs.Artists
                            action_library  -> HomeScreenTabs.Playlists
                            action_search   -> HomeScreenTabs.Search
                            // If not opened from shortcuts, then use default page (from settings)
                            else            -> Preferences.STARTUP_SCREEN.value
                        }

                        // In case [tabIndex] results to 0 and quick page
                        // isn't enabled change it to Songs page.
                        if( !Preferences.QUICK_PICKS_PAGE.value && tab == HomeScreenTabs.QuickPics )
                            tab = HomeScreenTabs.Songs

                        // Always set to empty to prevent unwanted outcome
                        intent.action = ""

                        return@remember tab
                    }

                    AppNavigation(
                        navController = navController,
                        startPage = startPage,
                        miniPlayer = {
                            MiniPlayer(
                                showPlayer = { showPlayer = true },
                                hidePlayer = { showPlayer = false },
                                navController = navController
                            )
                        }
                    )


                    val thumbnailRoundness by Preferences.THUMBNAIL_BORDER_RADIUS.collectAsStateWithLifecycle()

                    val isVideo = player.currentMediaItem?.isVideo ?: false
                    val isVideoEnabled by Preferences.PLAYER_ACTION_TOGGLE_VIDEO.collectAsStateWithLifecycle()

                    val youtubePlayer: @Composable () -> Unit = {
                        player.currentMediaItem?.mediaId?.let {
                            YoutubePlayer(
                                ytVideoId = it,
                                lifecycleOwner = LocalLifecycleOwner.current,
                                onCurrentSecond = {},
                                showPlayer = showPlayer,
                                onSwitchToAudioPlayer = {
                                    showPlayer = false
                                    switchToAudioPlayer = true
                                }
                            )
                        }
                    }

                    PipEventContainer(
                        enable = true,
                        onPipOutAction = {
                            showPlayer = false
                            switchToAudioPlayer = false
                        }
                    ) {
                        CustomModalBottomSheet(
                            showSheet = switchToAudioPlayer || showPlayer,
                            onDismissRequest = {
                                showPlayer = false
                                switchToAudioPlayer = false
                            },
                            containerColor = colorPalette().background0,
                            contentColor = colorPalette().background0,
                            modifier = Modifier.fillMaxWidth(),
                            sheetState = playerState,
                            dragHandle = {
                                Surface(
                                    modifier = Modifier.padding(vertical = 0.dp),
                                    color = colorPalette().background0,
                                    shape = thumbnailShape()
                                ) {}
                            },
                            shape = thumbnailRoundness.shape
                        ) {
                            Player( navController ) { showPlayer = false }
                        }
                    }

                    CustomModalBottomSheet(
                        showSheet = isVideo && isVideoEnabled && showPlayer,
                        onDismissRequest = { showPlayer = false },
                        containerColor = colorPalette().background0,
                        contentColor = colorPalette().background0,
                        modifier = Modifier.fillMaxWidth(),
                        sheetState = playerState,
                        dragHandle = {
                            Surface(
                                modifier = Modifier.padding(vertical = 0.dp),
                                color = colorPalette().background0,
                                shape = thumbnailShape()
                            ) {}
                        },
                        shape = thumbnailRoundness.shape
                    ) {
                        youtubePlayer()
                    }

                    val menuState = LocalMenuState.current
                    CustomModalBottomSheet(
                        showSheet = menuState.isDisplayed,
                        onDismissRequest = menuState::hide,
                        containerColor = Color.Transparent,
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        dragHandle = {
                            Surface(
                                modifier = Modifier.padding(vertical = 0.dp),
                                color = Color.Transparent,
                                //shape = thumbnailShape
                            ) {}
                        },
                        shape = thumbnailRoundness.shape
                    ) {
                        menuState.content()
                    }

                    if( bottomMenu.isVisible )
                        bottomMenu.BottomSheet( navController )
                }
            }
        }
        DisposableEffect(player) {
            val player = player ?: return@DisposableEffect onDispose { }

            if (player.currentMediaItem == null) {
                if (playerState.isVisible) {
                    showPlayer = false
                }
            } else {
                if (launchedFromNotification) {
                    intent.replaceExtras(Bundle())
                    showPlayer = !Preferences.PLAYER_KEEP_MINIMIZED.value
                } else {
                    showPlayer = false
                }
            }

            val listener = object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && mediaItem != null) {
                        if ( mediaItem.localConfiguration?.tag !== PersistentQueue.Tag )
                            showPlayer = !Preferences.PLAYER_KEEP_MINIMIZED.value
                    }

                    setDynamicPalette(mediaItem?.mediaMetadata?.artworkUri.thumbnail(1200).toString())
                }


            }

            player.addListener(listener)

            onDispose { player.removeListener(listener) }
        }

    }

    LaunchedEffect(intentUriData) {
        val uri = intentUriData ?: return@LaunchedEffect

        Toaster.n(
            "${BuildConfig.APP_NAME} ${this@AppContent.resources.getString( R.string.opening_url )}",
            duration = Toast.LENGTH_LONG
        )

        lifecycleScope.launch(Dispatchers.Main) {
            when (val path = uri.pathSegments.firstOrNull()) {
                "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                    val browseId = "VL$playlistId"

                    if (playlistId.startsWith("OLAK5uy_")) {
                        val browseId = KoinJavaComponent.get<YouTube>(YouTube::class.java)
                            .reverseAlbumIdFrom( playlistId )
                            .onFailure { err ->
                                Logger.e( "Failed to fetch playlist for albumId", err, "GoToLink" )
                            }
                            .getOrNull()
                        if( browseId == null ) {
                            Toaster.e( R.string.error_failed_to_get_album )
                            return@launch
                        }
                    } else {
                        NavRoutes.YT_PLAYLIST.navigateHere( navController, browseId )
                    }
                }

                "channel", "c" -> uri.lastPathSegment?.let { channelId ->
                    NavRoutes.YT_ARTIST.navigateHere( navController, channelId )
                }

                "search" -> uri.getQueryParameter("q")?.let { query ->
                    NavRoutes.searchResults.navigateHere( navController, query )
                }

                else -> when {
                    path == "watch" -> uri.getQueryParameter("v")
                    uri.host == "youtu.be" -> path
                    else -> null
                }?.let { videoId ->
                    Innertube.song(videoId)?.getOrNull()?.let { song ->
                        withContext(Dispatchers.Main) {
                            if ( !song.explicit && !Preferences.PARENTAL_CONTROL.value )
                                player.forcePlay(song.asMediaItem)
                            else
                                Toaster.w( "Parental control is enabled" )
                        }
                    }
                }
            }
        }
    }
}