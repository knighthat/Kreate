package it.fast4x.rimusic

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
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
import android.window.OnBackInvokedDispatcher
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi
import app.kreate.android.AppContent
import app.kreate.android.service.player.StatefulPlayer
import app.kreate.android.service.updater.UpdatePlugins
import app.kreate.android.themed.common.component.dialog.CrashReportDialog
import app.kreate.preferences.Preferences
import co.touchlab.kermit.Logger
import com.kieronquinn.monetcompat.core.MonetActivityAccessException
import com.kieronquinn.monetcompat.core.MonetCompat
import com.kieronquinn.monetcompat.interfaces.MonetColorsChangedListener
import dev.kdrag0n.monet.theme.ColorScheme
import it.fast4x.rimusic.enums.ColorPaletteName
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import it.fast4x.rimusic.utils.intent
import it.fast4x.rimusic.utils.invokeOnReady
import it.fast4x.rimusic.utils.playNext
import it.fast4x.rimusic.utils.setDefaultPalette
import org.koin.java.KoinJavaComponent.inject
import java.util.Objects
import kotlin.math.sqrt


@UnstableApi
class
MainActivity :
//MonetCompatActivity(),
    AppCompatActivity(),
    MonetColorsChangedListener
//,PersistMapOwner
{
    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {}

        override fun onServiceDisconnected(name: ComponentName?) {}
    }

    private var intentUriData by mutableStateOf<Uri?>(null)

    private var sensorManager: SensorManager? = null
    private var acceleration = 0f
    private var currentAcceleration = 0f
    private var lastAcceleration = 0f
    private var shakeCounter = 0

    private var _monet: MonetCompat? by mutableStateOf(null)
    private val monet get() = _monet ?: throw MonetActivityAccessException()

    private val pipState: MutableState<Boolean> = mutableStateOf(false)
    private val logger = Logger.withTag( this::class.java.simpleName )

    override fun onStart() {
        super.onStart()

        runCatching {
            bindService(intent<PlayerServiceModern>(), serviceConnection, Context.BIND_AUTO_CREATE)
        }.onFailure {
            logger.e( it ) { "Failed to bind PlayerServiceModern" }
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
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        pipState.value = isInPictureInPictureMode
        println("MainActivity.onPictureInPictureModeChanged isInPictureInPictureMode: $isInPictureInPictureMode")
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

    }

    @SuppressLint("UnusedBoxWithConstraintsScope")
    @OptIn(
        ExperimentalTextApi::class,
        ExperimentalFoundationApi::class, ExperimentalAnimationApi::class,
        ExperimentalMaterial3Api::class
    )
    fun startApp() {
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
            AppContent(monet, pipState, launchedFromNotification, intentUriData)
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
                    inject<StatefulPlayer>(StatefulPlayer::class.java).value.playNext()
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
            logger.e( it ) { "onResume failed" }
        }
    }

    override fun onPause() {
        super.onPause()
        runCatching {
            sensorListener.let { sensorManager?.unregisterListener(it) }
        }.onFailure {
            logger.e( it ) { "onPause failed" }
        }
    }

    @UnstableApi
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intentUriData = intent.data ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri()

    }

    @UnstableApi
    override fun onDestroy() =
        try {
            //<editor-fold desc="Stop player">
            // Stop music
            val player by inject<StatefulPlayer>(StatefulPlayer::class.java)
            player.run {
                stop()
                // FIXME: Android will try to recreate service if
                //  there's some MediaItems left in the queue .
                clearMediaItems()
            }
            // Unbind service (making sure there's no connection with the service)
            unbindService( serviceConnection )
            // Stop service (release resources)
            val intent = Intent(this, PlayerServiceModern::class.java)
            stopService( intent )

            logger.d { "Successfully stop player and unbind PlayerServiceModern service" }
            //</editor-fold>

            // Delete latest report
            val report = CrashReportDialog(this)
            if( report.isAvailable() ) {
                report.crashlogFile.delete()
                logger.d { "Successfully deleted latest crashlog" }
            }

            monet.removeMonetColorsChangedListener(this)
            _monet = null
            logger.d { "Successfully removed MonetColorsChangedListener" }

//            Preferences.unload()
            logger.d { "Unloaded preferences" }
        } catch( err: Exception ) {
            logger.e( err ) { "onDestroy failed!" }
        } finally {
            super.onDestroy()
        }

    override fun onMonetColorsChanged(
        monet: MonetCompat,
        monetColors: ColorScheme,
        isInitialChange: Boolean
    ) {
        val colorPaletteName = Preferences.COLOR_PALETTE.value
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
