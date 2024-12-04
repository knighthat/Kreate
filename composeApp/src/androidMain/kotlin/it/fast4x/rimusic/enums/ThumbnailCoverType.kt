package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class ThumbnailCoverType(
    @field:StringRes override val textId: Int
): TextView {

    Vinyl (R.string.cover_type_vinyl ),
    CD( R.string.cover_type_cd ),
    CDwithCover( R.string.cover_type_cd_with_cover );
}