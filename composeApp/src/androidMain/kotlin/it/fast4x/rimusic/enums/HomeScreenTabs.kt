package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class HomeScreenTabs(
    @field:StringRes override val textId: Int
): TextView {

    Default( R.string._default ),

    QuickPics( R.string.quick_picks ),

    Songs( R.string.songs ),

    Artists( R.string.albums ),

    Albums( R.string.artists ),

    Playlists( R.string.playlists ),

    Search( R.string.search );

    val index: Int
        get() = when (this) {
            Default -> 100
            QuickPics -> 0
            Songs -> 1
            Artists -> 2
            Albums -> 3
            Playlists -> 4
            Search -> 5
        }

}