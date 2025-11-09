package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class ExoPlayerCacheLocation(
    @field:StringRes override val androidTextId: Int,
    @field:StringRes val subtitleId: Int
): TextView {

    System( R.string.cache_location_system, R.string.setting_description_cache_location_system ),
    Private( R.string.cache_location_private, R.string.setting_description_cache_location_private ),
    SPLIT( R.string.cache_location_split, R.string.setting_description_cache_location_split );
}