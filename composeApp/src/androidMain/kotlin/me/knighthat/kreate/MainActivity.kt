package me.knighthat.kreate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import me.knighthat.kreate.di.TopLayoutConfiguration
import org.koin.compose.koinInject


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash screen *before* calling super.onCreate()
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // Enable E2E
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val topLayoutConfiguration = koinInject<TopLayoutConfiguration>()
            splashScreen.setKeepOnScreenCondition { !topLayoutConfiguration.isAppReady }

            MainContentLayout()
        }
    }
}
