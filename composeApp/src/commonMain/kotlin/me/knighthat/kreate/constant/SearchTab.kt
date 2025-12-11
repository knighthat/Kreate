package me.knighthat.kreate.constant

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector
import kreate.composeapp.generated.resources.Res
import kreate.composeapp.generated.resources.tab_albums
import kreate.composeapp.generated.resources.tab_artists
import kreate.composeapp.generated.resources.tab_playlists
import kreate.composeapp.generated.resources.tab_songs
import me.knighthat.innertube.SearchFilter
import me.knighthat.kreate.component.MaterialDrawable
import me.knighthat.kreate.component.TextView
import org.jetbrains.compose.resources.StringResource
import org.koin.core.component.KoinComponent


enum class SearchTab(
    val params: String,
    override val stringRes: StringResource,
    override val imageVector: ImageVector
): TextView, MaterialDrawable, KoinComponent {

    SONGS(SearchFilter.SONGS, Res.string.tab_songs, Icons.Rounded.MusicNote),

    ALBUMS(SearchFilter.ALBUMS, Res.string.tab_albums, Icons.Rounded.Album),

    ARTISTS(SearchFilter.ARTISTS, Res.string.tab_artists, Icons.Rounded.Person),

    PLAYLISTS(SearchFilter.COMMUNITY_PLAYLISTS, Res.string.tab_playlists, Icons.Rounded.LibraryMusic);
}