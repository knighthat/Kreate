package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class ExoPlayerCacheLocation(
    @field:StringRes override val textId: Int
): TextView {

    System( R.string.cache_location_private ),
    Private( R.string.cache_location_system );
}