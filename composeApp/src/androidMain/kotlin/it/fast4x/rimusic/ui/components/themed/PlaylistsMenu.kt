package it.fast4x.rimusic.ui.components.themed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.database.models.PlaylistPreview
import app.kreate.util.MONTHLY_PREFIX
import app.kreate.util.PINNED_PREFIX
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.MenuStyle
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.MenuState
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.Menu
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.Dispatchers
import me.knighthat.component.playlist.NewPlaylistDialog
import me.knighthat.utils.Toaster

class PlaylistsMenu private constructor(
    private val navController: NavController,
    private val mediaItems: (PlaylistPreview) -> List<MediaItem>,
    private val onFailure: (Throwable, PlaylistPreview) -> Unit,
    private val finalAction: (PlaylistPreview) -> Unit,
    override val menuState: MenuState,
    styleState: MutableState<MenuStyle>
): MenuIcon, Descriptive, Menu {

    companion object {
        @JvmStatic
        @Composable
        fun init(
            navController: NavController,
            mediaItems: (PlaylistPreview) -> List<MediaItem>,
            onFailure: (Throwable, PlaylistPreview) -> Unit,
            finalAction: (PlaylistPreview) -> Unit
        ) = PlaylistsMenu(
            navController,
            mediaItems,
            onFailure,
            finalAction,
            LocalMenuState.current,
            Preferences.MENU_STYLE
        )
    }

    override val iconId: Int = R.drawable.add_in_playlist
    override val messageId: Int = R.string.add_to_playlist
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

    override var menuStyle: MenuStyle by styleState

    private fun onAdd( preview: PlaylistPreview ) = Database.asyncTransaction {
        try {
            mapIgnore(preview.playlist, *mediaItems(preview).toTypedArray())
            Toaster.done()
        } catch (e: Throwable) {
            onFailure(e, preview)
        } finally {
            finalAction(preview)
        }
    }

    @Composable
    private fun PlaylistCard( playlistPreview: PlaylistPreview ) {
        val playlist = playlistPreview.playlist

        MenuEntry(
            icon = R.drawable.add_in_playlist,
            text = playlist.name.substringAfter( PINNED_PREFIX ),
            secondaryText = "${playlistPreview.songCount} ${stringResource( R.string.songs )}",
            onClick = {
                onAdd( playlistPreview )
            },
            trailingContent = {
                IconButton(
                    icon = R.drawable.open,
                    color = colorPalette().text,
                    onClick = {
                        menuState.hide()
                        NavRoutes.localPlaylist.navigateHere( navController, playlist.id.toString() )
                    },
                    modifier = Modifier.size( 24.dp )
                )
            }
        )
    }

    override fun onShortClick() {
        menuState.hide()
        openMenu()
    }

    @Composable
    override fun ListMenu() { /* Does nothing */ }

    @Composable
    override fun GridMenu() { /* Does nothing */ }

    @Composable
    override fun MenuComponent() {
        val playlistPreviews by remember {
            Database.playlistTable.sortPreviewsByName()
        }.collectAsState( emptyList(), Dispatchers.IO )

        val pinnedPlaylists = playlistPreviews.filter {
            it.playlist.name.startsWith(PINNED_PREFIX, 0, true)
        }
        val unpinnedPlaylists = playlistPreviews.filter {
            !it.playlist.name.startsWith(PINNED_PREFIX, 0, true) &&
                    !it.playlist.name.startsWith(MONTHLY_PREFIX, 0, true)
        }

        val newPlaylistButton = NewPlaylistDialog()
        newPlaylistButton.Render()

        Menu {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
            ) {
                IconButton(
                    onClick = ::onShortClick,
                    icon = R.drawable.chevron_back,
                    color = colorPalette().textSecondary,
                    modifier = Modifier
                        .padding(all = 4.dp)
                        .size(20.dp)
                )

                newPlaylistButton.ToolBarButton()
            }
            if (pinnedPlaylists.isNotEmpty()) {
                BasicText(
                    text = stringResource(R.string.pinned_playlists),
                    style = typography().m.semiBold,
                    modifier = Modifier.padding(start = 20.dp, top = 5.dp)
                )

                pinnedPlaylists.forEach { PlaylistCard(it) }
            }

            if (unpinnedPlaylists.isNotEmpty()) {
                BasicText(
                    text = stringResource(R.string.playlists),
                    style = typography().m.semiBold,
                    modifier = Modifier.padding(start = 20.dp, top = 5.dp)
                )

                unpinnedPlaylists.forEach { PlaylistCard(it) }
            }
        }
    }
}