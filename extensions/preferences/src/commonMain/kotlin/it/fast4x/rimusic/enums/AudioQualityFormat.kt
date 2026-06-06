package it.fast4x.rimusic.enums

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.audio_quality_automatic
import kreate.resources.generated.resources.audio_quality_format_high
import kreate.resources.generated.resources.audio_quality_format_low
import org.jetbrains.compose.resources.StringResource

enum class AudioQualityFormat(
    override val textId: StringResource,
): TextView {

    Auto(Res.string.audio_quality_automatic),

    High(Res.string.audio_quality_format_high),

    Low(Res.string.audio_quality_format_low);
}