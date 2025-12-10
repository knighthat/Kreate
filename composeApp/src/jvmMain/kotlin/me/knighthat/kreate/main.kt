package me.knighthat.kreate

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.SingletonImageLoader
import kreate.composeapp.generated.resources.Res
import kreate.composeapp.generated.resources.app_icon_no_ring
import kreate.composeapp.generated.resources.app_name
import me.knighthat.kreate.coil.setupCoil
import me.knighthat.kreate.di.initKoin
import me.knighthat.kreate.logging.KoinBufferedLogger
import me.knighthat.kreate.logging.setupLogging
import me.knighthat.kreate.util.CrashHandler
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import picocli.CommandLine
import kotlin.system.exitProcess


fun main( args: Array<String> ) {
    val cli = CommandLine(Args())
    val exitCode = cli.execute( *args )
    if( exitCode > 0 )
        exitProcess( exitCode )
    else if( cli.isUsageHelpRequested ) {
        cli.printVersionHelp( System.out )
        exitProcess( 0 )
    }

    Thread.setDefaultUncaughtExceptionHandler( CrashHandler() )

    val koinLogger = KoinBufferedLogger()
    initKoin {
        logger( koinLogger )
    }

    setupLogging( koinLogger )

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