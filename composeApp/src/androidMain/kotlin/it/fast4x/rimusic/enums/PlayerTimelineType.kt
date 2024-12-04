package it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import it.fast4x.rimusic.R
import me.knighthat.enums.TextView

enum class PlayerTimelineType(
    @field:StringRes override val textId: Int
): TextView {

    Default( R.string._default ),

    Wavy( R.string.wavy_timeline ),

    PinBar( R.string.bodied_bar ),

    BodiedBar( R.string.pin_bar ),

    FakeAudioBar( R.string.fake_audio_bar ),

    ThinBar( R.string.thin_bar ),

    ColoredBar( R.string.colored_bar );
}