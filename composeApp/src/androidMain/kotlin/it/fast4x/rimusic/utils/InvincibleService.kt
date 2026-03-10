package it.fast4x.rimusic.utils

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ServiceCompat
import co.touchlab.kermit.Logger

// https://stackoverflow.com/q/53502244/16885569
// I found four ways to make the system not kill the stopped foreground service: e.g. when
// the player is paused:
// 1 - Use the solution below - hacky;
// 2 - Do not call stopForeground but provide a button to dismiss the notification - bad UX;
// 3 - Lower the targetSdk (e.g. to 23) - security concerns;
// 4 - Host the service in a separate process - overkill and pathetic.
abstract class InvincibleService : Service() {

    protected val handler = Handler(Looper.getMainLooper())

    protected abstract val isInvincibilityEnabled: Boolean

    protected abstract val notificationId: Int

    private var invincibility: Invincibility? = null

    private val isAllowedToStartForegroundServices: Boolean
        get() = !isAtLeastAndroid12 || isIgnoringBatteryOptimizations

    override fun onBind(intent: Intent?): Binder? {
        invincibility?.stop()
        invincibility = null
        return null
    }

    override fun onRebind(intent: Intent?) {
        invincibility?.stop()
        invincibility = null
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (isInvincibilityEnabled && isAllowedToStartForegroundServices) {
            invincibility = Invincibility()
        }
        return true
    }

    override fun onDestroy() {
        invincibility?.stop()
        invincibility = null
        super.onDestroy()
    }

    protected fun makeInvincible(isInvincible: Boolean = true) {
        if (isInvincible) {
            invincibility?.start()
        } else {
            invincibility?.stop()
        }
    }

    protected abstract fun shouldBeInvincible(): Boolean

    protected abstract fun notification(): Notification?

    private inner class Invincibility : BroadcastReceiver(), Runnable {
        private var isStarted = false
        private val intervalMs = 30_000L

        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> handler.post(this)
                Intent.ACTION_SCREEN_OFF -> notification()?.let { notification ->
                    handler.removeCallbacks(this)
                    runCatching {
                        //startForeground(notificationId, notification)
                            ServiceCompat.startForeground(
                                this@InvincibleService,
                                notificationId,
                                notification,
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                                } else {
                                    0
                                }
                            )
                    }.onFailure {
                        Logger.e( it, this::class.java.simpleName ) { "startForeground failed!" }
                    }
                }
            }
        }

        @Synchronized
        fun start() {
            if (!isStarted) {
                isStarted = true
                handler.postDelayed(this, intervalMs)
                registerReceiver(this, IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                })
            }
        }

        @Synchronized
        fun stop() {
            if (isStarted) {
                handler.removeCallbacks(this)
                unregisterReceiver(this)
                isStarted = false
            }
        }

        override fun run() {
            if (shouldBeInvincible() && isAllowedToStartForegroundServices) {
                notification()?.let { notification ->
                    runCatching {
                        //startForeground(notificationId, notification)
                        ServiceCompat.startForeground(
                            this@InvincibleService,
                            notificationId,
                            notification,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                            } else {
                                0
                            }
                        )
                    }.onFailure {
                        Logger.e( "", it, this::class.java.simpleName )
                    }
                    runCatching {
                        stopForeground(false)
                    }.onFailure {
                        Logger.e( "", it, this::class.java.simpleName )
                    }
                    handler.postDelayed(this, intervalMs)
                }
            }
        }
    }
}
