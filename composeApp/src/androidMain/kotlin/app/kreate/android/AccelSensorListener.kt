package app.kreate.android

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import app.kreate.android.service.player.StatefulPlayer
import it.fast4x.rimusic.utils.playNext
import org.koin.java.KoinJavaComponent.inject
import kotlin.math.sqrt


class AccelSensorListener : SensorEventListener {

    private var acceleration = 0f
    private var currentAcceleration = 0f
    private var lastAcceleration = 0f
    private var shakeCounter = 0

    override fun onAccuracyChanged( p0: Sensor?, p1: Int ) {}

    override fun onSensorChanged( event: SensorEvent ) {
        if( !Preferences.AUDIO_SHAKE_TO_SKIP.value )
            return

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