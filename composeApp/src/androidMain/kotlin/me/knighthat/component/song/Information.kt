package me.knighthat.component.song

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import app.kreate.android.R
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon

class Information(
    private val navController: NavController,
    private val songId: String
): MenuIcon, Descriptive {

    override val messageId: Int = R.string.description_view_song_info
    override val iconId: Int = R.drawable.information
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.word_information )

    override fun onShortClick() {
        NavRoutes.SONG_DETAILS.navigateHere(  navController, songId )
    }
}