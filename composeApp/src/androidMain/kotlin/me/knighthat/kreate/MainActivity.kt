package me.knighthat.kreate

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        // Install the splash screen *before* calling super.onCreate()
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // Optional: Keep the splash screen visible while loading data
        // For a single theme, you often only need the installSplashScreen() call.
        // If you need to observe a condition:
        var keepSplashOn = true

        // Example: Dismiss after a condition is met
        splashScreen.setKeepOnScreenCondition { keepSplashOn }

        // Simulating data loading
        lifecycleScope.launch {
            delay(2000)
            keepSplashOn = false // Set to false when your data is loaded
        }

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}