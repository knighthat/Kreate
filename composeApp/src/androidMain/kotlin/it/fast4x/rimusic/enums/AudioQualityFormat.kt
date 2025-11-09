package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class AudioQualityFormat(
    @field:StringRes override val androidTextId: Int
): TextView {

    Auto( R.string.audio_quality_automatic ),

    High( R.string.audio_quality_format_high ),

    Medium( R.string.audio_quality_format_medium ),

    Low( R.string.audio_quality_format_low );
}