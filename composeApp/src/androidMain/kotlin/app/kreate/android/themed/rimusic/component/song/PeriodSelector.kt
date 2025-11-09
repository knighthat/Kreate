package app.kreate.android.themed.rimusic.component.song

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kreate.android.Preferences
import app.kreate.android.R
import it.fast4x.rimusic.enums.MenuStyle
import it.fast4x.rimusic.enums.StatisticsType
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.MenuState
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.Menu
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import it.fast4x.rimusic.ui.components.themed.Menu
import it.fast4x.rimusic.ui.components.themed.MenuEntry
import it.fast4x.rimusic.utils.semiBold

class PeriodSelector(override val menuState: MenuState): MenuIcon, Descriptive, Menu {

    var period: StatisticsType by Preferences.HOME_SONGS_TOP_PLAYLIST_PERIOD

    override val iconId: Int
        get() = period.androidIconId
    override val messageId: Int = R.string.statistics
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override var menuStyle: MenuStyle = Preferences.MENU_STYLE.value

    fun onDismiss( period: StatisticsType ) {
        this.period = period
        menuState.hide()
    }

    override fun onShortClick() = openMenu()

    @Composable
    override fun ListMenu() { /* Does nothing */ }

    @Composable
    override fun GridMenu() { /* Does nothing */ }

    @Composable
    override fun MenuComponent() {
        val size by Preferences.MAX_NUMBER_OF_TOP_PLAYED

        Menu {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier
                    .padding(end = 12.dp)
            ) {
                BasicText(
                    text = stringResource( R.string.header_view_top_of, size ),
                    style = typography().m.semiBold,
                    modifier = Modifier.padding(
                        vertical = 8.dp,
                        horizontal = 24.dp
                    )
                )
            }

            Spacer( Modifier.height( 8.dp ) )

            StatisticsType.entries.forEach {
                MenuEntry(
                    icon = it.androidIconId,
                    text = it.text,
                    onClick = {
                        onDismiss( it )
                    }
                )
            }
        }
    }
}