package it.fast4x.rimusic.enums

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.tab_albums
import kreate.resources.generated.resources.tab_artists
import kreate.resources.generated.resources.tab_playlists
import kreate.resources.generated.resources.tab_quick_picks
import kreate.resources.generated.resources.tab_search
import kreate.resources.generated.resources.tab_songs
import kreate.resources.generated.resources.word_default
import org.jetbrains.compose.resources.StringResource

enum class HomeScreenTabs(
    val index: Int,
    override val textId: StringResource
): TextView {

    Default(0, Res.string.word_default),

    QuickPics(0, Res.string.tab_quick_picks),

    Songs(1, Res.string.tab_songs),

    Artists(2, Res.string.tab_artists),

    Albums(3, Res.string.tab_albums),

    Playlists(4, Res.string.tab_playlists),

    Search(5, Res.string.tab_search);
}