package it.fast4x.rimusic

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import androidx.palette.graphics.Palette
import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.service.updater.UpdatePlugins
import coil3.request.allowHardware
import coil3.toBitmap
import com.kieronquinn.monetcompat.core.MonetActivityAccessException
import com.kieronquinn.monetcompat.core.MonetCompat
import com.kieronquinn.monetcompat.interfaces.MonetColorsChangedListener
import dagger.hilt.android.AndroidEntryPoint
import dev.kdrag0n.monet.theme.ColorScheme
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.BrowseBody
import it.fast4x.innertube.requests.playlistPage
import it.fast4x.innertube.requests.song
import it.fast4x.innertube.utils.LocalePreferenceItem
import it.fast4x.innertube.utils.LocalePreferences
import it.fast4x.rimusic.enums.AnimatedGradient
import it.fast4x.rimusic.enums.ColorPaletteMode
import it.fast4x.rimusic.enums.ColorPaletteName
import it.fast4x.rimusic.enums.HomeScreenTabs
import it.fast4x.rimusic.enums.LogType
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.PipModule
import it.fast4x.rimusic.enums.PlayerBackgroundColors
import it.fast4x.rimusic.enums.ThumbnailRoundness
import it.fast4x.rimusic.extensions.pip.PipEventContainer
import it.fast4x.rimusic.extensions.pip.PipModuleContainer
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.service.modern.PlayerServiceModern
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
import it.fast4x.rimusic.utils.forcePlay
import it.fast4x.rimusic.utils.getEnum
import it.fast4x.rimusic.utils.intent
import it.fast4x.rimusic.utils.invokeOnReady
import it.fast4x.rimusic.utils.isAtLeastAndroid6
import it.fast4x.rimusic.utils.isAtLeastAndroid8
import it.fast4x.rimusic.utils.isVideo
import it.fast4x.rimusic.utils.loadAppLog
import it.fast4x.rimusic.utils.playNext
import it.fast4x.rimusic.utils.preferences
import it.fast4x.rimusic.utils.resize
import it.fast4x.rimusic.utils.setDefaultPalette
import it.fast4x.rimusic.utils.textCopyToClipboard
import it.fast4x.rimusic.utils.thumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.invidious.Invidious
import me.knighthat.piped.Piped
import me.knighthat.utils.Toaster
import timber.log.Timber
import java.util.Locale
import java.util.Objects
import kotlin.math.sqrt
import kotlin.system.exitProcess

@AndroidEntryPoint
@UnstableApi
class
MainActivity :
//MonetCompatActivity(),
    AppCompatActivity(),
    MonetColorsChangedListener
//,PersistMapOwner
{
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is PlayerServiceModern.Binder) {
                this@MainActivity.binder = service
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
        }

    }

    private var binder by mutableStateOf<PlayerServiceModern.Binder?>(null)
    private var intentUriData by mutableStateOf<Uri?>(null)

    private var sensorManager: SensorManager? = null
    private var acceleration = 0f
    private var currentAcceleration = 0f
    private var lastAcceleration = 0f
    private var shakeCounter = 0

    private var _monet: MonetCompat? by mutableStateOf(null)
    private val monet get() = _monet ?: throw MonetActivityAccessException()

    private val pipState: MutableState<Boolean> = mutableStateOf(false)

    override fun onStart() {
        super.onStart()

        runCatching {
            bindService(intent<PlayerServiceModern>(), serviceConnection, Context.BIND_AUTO_CREATE)
        }.onFailure {
            Timber.e("MainActivity.onStart bindService ${it.stackTraceToString()}")
        }
    }

    @ExperimentalTextApi
    @UnstableApi
    @ExperimentalComposeUiApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        UpdatePlugins.execute( this )

        MonetCompat.enablePaletteCompat()

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                scrim = Color.Transparent.toArgb(),
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = Color.Transparent.toArgb(),
                darkScrim = Color.Transparent.toArgb()
            )
        )

        WindowCompat.setDecorFitsSystemWindows(window, false)

        MonetCompat.setup(this)
        _monet = MonetCompat.getInstance()
        monet.setDefaultPalette()
        monet.addMonetColorsChangedListener(
            listener = this,
            notifySelf = false
        )
        monet.updateMonetColors()

        monet.invokeOnReady {
            startApp()
        }

        if ( Preferences.AUDIO_SHAKE_TO_SKIP.value ) {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            Objects.requireNonNull(sensorManager)
                ?.registerListener(
                    sensorListener,
                    sensorManager!!
                        .getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_NORMAL
                )
        }

        checkIfAppIsRunningInBackground()

        // Fetch Piped & Invidious instances
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Piped.fetchInstances()
                Invidious.fetchInstances()
            } catch (e: Exception) {
                Timber.e(e, "MainActivity Error fetching Piped & Invidious instances")
            }
        }
    }

    private fun checkIfAppIsRunningInBackground() {
        val runningAppProcessInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(runningAppProcessInfo)
        appRunningInBackground =
            runningAppProcessInfo.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        pipState.value = isInPictureInPictureMode
        println("MainActivity.onPictureInPictureModeChanged isInPictureInPictureMode: $isInPictureInPictureMode")
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

    }


    @Composable
    fun ThemeApp(
        isDark: Boolean = false,
        content: @Composable () -> Unit
    ) {
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                (view.context as Activity).window.let { window ->
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                        !isDark
                    WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars =
                        !isDark
                }
            }

        }
        content()
    }

    @SuppressLint("UnusedBoxWithConstraintsScope")
    @OptIn(
        ExperimentalTextApi::class,
        ExperimentalFoundationApi::class, ExperimentalAnimationApi::class,
        ExperimentalMaterial3Api::class
    )
    fun startApp() {
        // Used in QuickPics for load data from remote instead of last saved in SharedPreferences
        Preferences.IS_DATA_KEY_LOADED.value = false

        if ( !Preferences.CLOSE_APP_ON_BACK.value )
            if (Build.VERSION.SDK_INT >= 33) {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT
                ) {
                    //Log.d("onBackPress", "yeah")
                }
            }

        /*
            Instead of checking getBoolean() individually, we can use .let() to express condition.
            Or, the whole thing is 'false' if null appears in the process.
         */
        val launchedFromNotification: Boolean =
            intent?.extras?.let {
                it.getBoolean("expandPlayerBottomSheet") || it.getBoolean("fromWidget")
            } ?: false

        println("MainActivity.onCreate launchedFromNotification: $launchedFromNotification intent $intent.action")

        intentUriData = intent.data ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri()

        if ( Preferences.KEEP_SCREEN_ON.value )
            window.addFlags( WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON )

        setContent {
            val colorPaletteMode by Preferences.THEME_MODE
            val isPicthBlack = colorPaletteMode == ColorPaletteMode.PitchBlack

            // Valid to get log when app crash
            if (intent.action == action_copy_crash_log) {
                Preferences.RUNTIME_LOG.value = true
                loadAppLog(this@MainActivity, type = LogType.Crash).let {
                    if (it != null) textCopyToClipboard(it, this@MainActivity)
                }
                LaunchedEffect(Unit) {
                    delay(5000)
                    exitProcess(0)
                }
            }

            val coroutineScope = rememberCoroutineScope()
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val navController = rememberNavController()
            var showPlayer by rememberSaveable { mutableStateOf(false) }
            var switchToAudioPlayer by rememberSaveable { mutableStateOf(false) }
            var animatedGradient by Preferences.ANIMATED_GRADIENT
            var customColor by Preferences.CUSTOM_COLOR
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
                with(preferences) {
                    val colorPaletteName by Preferences.COLOR_PALETTE
                    val colorPaletteMode by Preferences.THEME_MODE
                    val thumbnailRoundness by Preferences.THUMBNAIL_BORDER_RADIUS
                    val useSystemFont by Preferences.USE_SYSTEM_FONT
                    val applyFontPadding by Preferences.APPLY_FONT_PADDING

                    var colorPalette =
                        colorPaletteOf(colorPaletteName, colorPaletteMode, !lightTheme)

                    val fontType by Preferences.FONT

                    if (colorPaletteName == ColorPaletteName.MaterialYou) {
                        colorPalette = dynamicColorPaletteOf(
                            Color(monet.getAccentColor(this@MainActivity)),
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


            }

            fun setDynamicPalette(url: String) {
                val playerBackgroundColors by Preferences.PLAYER_BACKGROUND
                val colorPaletteName by Preferences.COLOR_PALETTE
                val isDynamicPalette = colorPaletteName == ColorPaletteName.Dynamic
                val isCoverColor =
                    playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient ||
                            playerBackgroundColors == PlayerBackgroundColors.CoverColor ||
                            animatedGradient == AnimatedGradient.FluidCoverColorGradient

                if (!isDynamicPalette) return

                val colorPaletteMode by Preferences.THEME_MODE
                coroutineScope.launch(Dispatchers.Main) {
                    val result = ImageFactory.requestBuilder( url ) {
                        allowHardware( false )
                    }.let { ImageFactory.imageLoader.execute( it ) }
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


            DisposableEffect(binder, !lightTheme) {
                val listener =
                    SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                        when (key) {
                            Preferences.MAIN_THEME.key,
                            Preferences.NAVIGATION_BAR_POSITION.key,
                            Preferences.NAVIGATION_BAR_TYPE.key,
                            Preferences.MINI_PLAYER_TYPE.key -> {
                                this@MainActivity.recreate()
                                println("MainActivity.recreate()")
                            }

                            Preferences.COLOR_PALETTE.key,
                            Preferences.THEME_MODE.key,
                            Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_0.key,
                            Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_1.key,
                            Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_2.key,
                            Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_3.key,
                            Preferences.CUSTOM_LIGHT_THEME_BACKGROUND_4.key,
                            Preferences.CUSTOM_LIGHT_TEXT.key,
                            Preferences.CUSTOM_LIGHT_TEXT_SECONDARY.key,
                            Preferences.CUSTOM_LIGHT_TEXT_DISABLED.key,
                            Preferences.CUSTOM_LIGHT_PLAY_BUTTON.key,
                            Preferences.CUSTOM_LIGHT_ACCENT.key,
                            Preferences.CUSTOM_DARK_THEME_BACKGROUND_0.key,
                            Preferences.CUSTOM_DARK_THEME_BACKGROUND_1.key,
                            Preferences.CUSTOM_DARK_THEME_BACKGROUND_2.key,
                            Preferences.CUSTOM_DARK_THEME_BACKGROUND_3.key,
                            Preferences.CUSTOM_DARK_THEME_BACKGROUND_4.key,
                            Preferences.CUSTOM_DARK_TEXT.key,
                            Preferences.CUSTOM_DARK_TEXT_SECONDARY.key,
                            Preferences.CUSTOM_DARK_TEXT_DISABLED.key,
                            Preferences.CUSTOM_DARK_PLAY_BUTTON.key,
                            Preferences.CUSTOM_DARK_ACCENT.key -> {
                                val colorPaletteName = sharedPreferences.getEnum( Preferences.COLOR_PALETTE.key, Preferences.COLOR_PALETTE.defaultValue )
                                val colorPaletteMode = sharedPreferences.getEnum( Preferences.THEME_MODE.key, Preferences.THEME_MODE.defaultValue )

                                var colorPalette = colorPaletteOf(
                                    colorPaletteName,
                                    colorPaletteMode,
                                    !lightTheme
                                )

                                if (colorPaletteName == ColorPaletteName.Dynamic) {
                                    val artworkUri =
                                        (binder?.player?.currentMediaItem?.mediaMetadata?.artworkUri.thumbnail(1200)
                                            ?: "").toString()
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
                                            Color(monet.getAccentColor(this@MainActivity)),
                                            !lightTheme
                                        )
                                    }

                                    if (colorPaletteName == ColorPaletteName.Customized) {
                                        colorPalette = customColorPalette(
                                            colorPalette,
                                            this@MainActivity,
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

                            Preferences.THUMBNAIL_BORDER_RADIUS.key -> {
                                val thumbnailRoundness =
                                    sharedPreferences.getEnum(key, ThumbnailRoundness.Heavy)

                                appearance = appearance.copy(
                                    thumbnailShape = thumbnailRoundness.shape
                                )
                            }

                            Preferences.USE_SYSTEM_FONT.key,
                            Preferences.APPLY_FONT_PADDING.key,
                            Preferences.FONT.key -> {
                                val useSystemFont = sharedPreferences.getBoolean( Preferences.USE_SYSTEM_FONT.key, Preferences.USE_SYSTEM_FONT.defaultValue )
                                val applyFontPadding = sharedPreferences.getBoolean( Preferences.APPLY_FONT_PADDING.key, Preferences.APPLY_FONT_PADDING.defaultValue )
                                val fontType = sharedPreferences.getEnum( Preferences.FONT.key, Preferences.FONT.defaultValue )

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

                with(preferences) {
                    registerOnSharedPreferenceChangeListener(listener)

                    val colorPaletteName by Preferences.COLOR_PALETTE
                    if (colorPaletteName == ColorPaletteName.Dynamic) {
                        setDynamicPalette(
                            (binder?.player?.currentMediaItem?.mediaMetadata?.artworkUri.thumbnail(1200)
                                ?: "").toString()
                        )
                    }

                    onDispose {
                        unregisterOnSharedPreferenceChangeListener(listener)
                    }
                }
            }

            val rippleConfiguration =
                remember(appearance.colorPalette.text, appearance.colorPalette.isDark) {
                    RippleConfiguration(color = appearance.colorPalette.text)
                }

            LaunchedEffect(Unit) {
                val colorPaletteName by Preferences.COLOR_PALETTE
                if (colorPaletteName == ColorPaletteName.Customized) {
                    appearance = appearance.copy(
                        colorPalette = customColorPalette(
                            appearance.colorPalette,
                            this@MainActivity,
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
                    val pipModule by Preferences.PIP_MODULE
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
                                            thumbnailUrl = binder?.player
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

                    } else
                        // FIXME: Why is this block getting called twice on start?
                        CompositionLocalProvider(
                            LocalAppearance provides appearance,
                            LocalIndication provides ripple(bounded = true),
                            LocalRippleConfiguration provides rippleConfiguration,
                            LocalPlayerServiceBinder provides binder,
                            LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                            LocalLayoutDirection provides LayoutDirection.Ltr,
                            LocalDownloadHelper provides MyDownloadHelper,
                            LocalPlayerSheetState provides playerState,
                            LocalMonetCompat provides monet,
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

                            checkIfAppIsRunningInBackground()


                            val thumbnailRoundness by Preferences.THUMBNAIL_BORDER_RADIUS

                            val isVideo = binder?.player?.currentMediaItem?.isVideo ?: false
                            val isVideoEnabled by Preferences.PLAYER_ACTION_TOGGLE_VIDEO

                            val youtubePlayer: @Composable () -> Unit = {
                                binder?.player?.currentMediaItem?.mediaId?.let {
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

                        }

                }
                DisposableEffect(binder?.player) {
                    val player = binder?.player ?: return@DisposableEffect onDispose { }

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
                                if (mediaItem.mediaMetadata.extras?.getBoolean("isFromPersistentQueue") != true) {
                                    showPlayer = !Preferences.PLAYER_KEEP_MINIMIZED.value
                                }
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
                    "${BuildConfig.APP_NAME} ${this@MainActivity.resources.getString( R.string.opening_url )}",
                    duration = Toast.LENGTH_LONG
                )

                lifecycleScope.launch(Dispatchers.Main) {
                    when (val path = uri.pathSegments.firstOrNull()) {
                        "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                            val browseId = "VL$playlistId"

                            if (playlistId.startsWith("OLAK5uy_")) {
                                Innertube.playlistPage(BrowseBody(browseId = browseId))
                                    ?.getOrNull()?.let {
                                        it.songsPage?.items?.firstOrNull()?.album?.endpoint?.browseId?.let { browseId ->
                                            NavRoutes.YT_ALBUM.navigateHere( navController, browseId )
                                        }
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
                                val binder = snapshotFlow { binder }.filterNotNull().first()
                                withContext(Dispatchers.Main) {
                                    if ( !song.explicit && !Preferences.PARENTAL_CONTROL.value )
                                        binder.player.forcePlay( song )
                                    else
                                        Toaster.w( "Parental control is enabled" )
                                }
                            }
                        }
                    }
                }
                intentUriData = null
            }
        }
    }


    private val sensorListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {

            if ( Preferences.AUDIO_SHAKE_TO_SKIP.value ) {
                // Fetching x,y,z values
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                lastAcceleration = currentAcceleration

                // Getting current accelerations
                // with the help of fetched x,y,z values
                currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                val delta: Float = currentAcceleration - lastAcceleration
                acceleration = acceleration * 0.9f + delta

                // Display a Toast message if
                // acceleration value is over 12
                if (acceleration > 12) {
                    shakeCounter++
                    //Toast.makeText(applicationContext, "Shake event detected", Toast.LENGTH_SHORT).show()
                }
                if (shakeCounter >= 1) {
                    //Toast.makeText(applicationContext, "Shaked $shakeCounter times", Toast.LENGTH_SHORT).show()
                    shakeCounter = 0
                    binder?.player?.playNext()
                }

            }

        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    override fun onResume() {
        super.onResume()
        runCatching {
            sensorManager?.registerListener(
                sensorListener, sensorManager!!.getDefaultSensor(
                    Sensor.TYPE_ACCELEROMETER
                ), SensorManager.SENSOR_DELAY_NORMAL
            )
        }.onFailure {
            Timber.e("MainActivity.onResume registerListener sensorManager ${it.stackTraceToString()}")
        }
        appRunningInBackground = false
    }

    override fun onPause() {
        super.onPause()
        runCatching {
            sensorListener.let { sensorManager?.unregisterListener(it) }
        }.onFailure {
            Timber.e("MainActivity.onPause unregisterListener sensorListener ${it.stackTraceToString()}")
        }
        appRunningInBackground = true
    }

    @UnstableApi
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intentUriData = intent.data ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri()

    }

    override fun onStop() {
        runCatching {
            unbindService(serviceConnection)
        }.onFailure {
            Timber.e("MainActivity.onStop unbindService ${it.stackTraceToString()}")
        }
        super.onStop()
    }

    @UnstableApi
    override fun onDestroy() {
        super.onDestroy()

        runCatching {
            monet.removeMonetColorsChangedListener(this)
            _monet = null
        }.onFailure {
            Timber.e("MainActivity.onDestroy removeMonetColorsChangedListener ${it.stackTraceToString()}")
        }
    }

    private fun setSystemBarAppearance(isDark: Boolean) {
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

    companion object {
        const val action_search = "it.fast4x.rimusic.action.search"
        const val action_songs = "it.fast4x.rimusic.action.songs"
        const val action_albums = "it.fast4x.rimusic.action.albums"
        const val actions_artists = "it.fast4x.rimusic.action.artists"
        const val action_library = "it.fast4x.rimusic.action.library"
        const val action_copy_crash_log = "it.fast4x.rimusic.action.copy_crash_log"
    }


    override fun onMonetColorsChanged(
        monet: MonetCompat,
        monetColors: ColorScheme,
        isInitialChange: Boolean
    ) {
        val colorPaletteName by Preferences.COLOR_PALETTE
        if (!isInitialChange && colorPaletteName == ColorPaletteName.MaterialYou) {
            /*
            monet.updateMonetColors()
            monet.invokeOnReady {
                startApp()
            }
             */
            this@MainActivity.recreate()
        }
    }


}

var appRunningInBackground: Boolean = false

val LocalPlayerServiceBinder = staticCompositionLocalOf<PlayerServiceModern.Binder?> { null }

val LocalPlayerAwareWindowInsets = staticCompositionLocalOf<WindowInsets> { TODO() }

val LocalDownloadHelper = staticCompositionLocalOf<MyDownloadHelper> { error("No Downloader provided") }

@OptIn(ExperimentalMaterial3Api::class)
val LocalPlayerSheetState =
    staticCompositionLocalOf<SheetState> { error("No player sheet state provided") }
