package app.kreate.android

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.staticCompositionLocalOf
import app.kreate.android.themed.common.component.BottomMenu
import it.fast4x.rimusic.service.MyDownloadHelper


val LocalBottomMenu = staticCompositionLocalOf<BottomMenu> { TODO() }

val LocalPlayerAwareWindowInsets = staticCompositionLocalOf<WindowInsets> { TODO() }

val LocalDownloadHelper = staticCompositionLocalOf<MyDownloadHelper> { error("No Downloader provided") }

@OptIn(ExperimentalMaterial3Api::class)
val LocalPlayerSheetState =
    staticCompositionLocalOf<SheetState> { error("No player sheet state provided") }

val LocalFlavorSpecificFunctions = staticCompositionLocalOf<FlavorSpecificFunctions> { TODO() }