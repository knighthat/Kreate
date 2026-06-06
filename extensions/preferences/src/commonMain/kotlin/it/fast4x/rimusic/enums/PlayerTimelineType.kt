package it.fast4x.rimusic.enums

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.effect_snake
import kreate.resources.generated.resources.fake_waveform
import kreate.resources.generated.resources.modernized_common
import kreate.resources.generated.resources.thick_bar
import kreate.resources.generated.resources.thin_bar
import kreate.resources.generated.resources.word_common
import kreate.resources.generated.resources.word_default
import org.jetbrains.compose.resources.StringResource

enum class PlayerTimelineType(
    override val textId: StringResource
): TextView {

    Default(Res.string.word_default),

    Wavy(Res.string.effect_snake),

    PinBar(Res.string.word_common),

    BodiedBar(Res.string.thick_bar),

    FakeAudioBar(Res.string.fake_waveform),

    ThinBar(Res.string.thin_bar),

    ColoredBar(Res.string.modernized_common);
}