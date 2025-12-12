package me.knighthat.kreate.constant

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector
import kreate.composeapp.generated.resources.Res
import kreate.composeapp.generated.resources.tab_albums
import kreate.composeapp.generated.resources.tab_artists
import kreate.composeapp.generated.resources.tab_playlists
import kreate.composeapp.generated.resources.tab_search
import kreate.composeapp.generated.resources.tab_songs
import me.knighthat.kreate.component.MaterialDrawable
import me.knighthat.kreate.component.TextView
import org.jetbrains.compose.resources.StringResource


enum class MainNavigationTab(
    val route: Route,
    override val imageVector: ImageVector,
    override val stringRes: StringResource,
    val isSeparated: Boolean = false
): MaterialDrawable, TextView {

    SONGS(Route.Songs, Icons.Rounded.MusicNote, Res.string.tab_songs),

    ALBUMS(Route.Albums, Icons.Rounded.Album, Res.string.tab_albums),

    ARTISTS(Route.Artists, Icons.Rounded.Person, Res.string.tab_artists),

    PLAYLISTS(Route.Library, Icons.Rounded.LibraryMusic, Res.string.tab_playlists),

    SEARCH(Route.Search, Icons.Rounded.Search, Res.string.tab_search, true);
}