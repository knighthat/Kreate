package me.knighthat.kreate

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.SingletonImageLoader
import kreate.composeapp.generated.resources.Res
import kreate.composeapp.generated.resources.app_icon_no_ring
import kreate.composeapp.generated.resources.app_name
import me.knighthat.kreate.coil.setupCoil
import me.knighthat.kreate.di.initKoin
import me.knighthat.kreate.logging.setupLogging
import me.knighthat.kreate.util.CrashHandler
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource


fun main() {
    Thread.setDefaultUncaughtExceptionHandler( CrashHandler() )

    initKoin()

    setupLogging()

    SingletonImageLoader.setSafe( ::setupCoil )

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = stringResource( Res.string.app_name ),
            icon = painterResource(Res.drawable.app_icon_no_ring )
        ) {
            MainContentLayout()
        }
    }
}