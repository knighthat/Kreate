package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class HomeScreenTabs(
    val index: Int,
    @field:StringRes override val androidTextId: Int
): TextView {

    Default( 0, R.string._default ),

    QuickPics( 0, R.string.quick_picks ),

    Songs( 1, R.string.songs ),

    Artists( 2, R.string.artists ),

    Albums( 3, R.string.albums ),

    Playlists( 4, R.string.playlists ),

    Search( 5, android.R.string.search_go );
}