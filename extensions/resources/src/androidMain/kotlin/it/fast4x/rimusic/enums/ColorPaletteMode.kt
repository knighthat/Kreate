package it.fast4x.rimusic.enums

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.palette_mode_dark
import kreate.resources.generated.resources.palette_mode_light
import kreate.resources.generated.resources.palette_mode_pitch_black
import kreate.resources.generated.resources.palette_mode_system
import org.jetbrains.compose.resources.StringResource

enum class ColorPaletteMode(
    override val textId: StringResource
): TextView {

    Light(Res.string.palette_mode_light),

    Dark(Res.string.palette_mode_dark),

    PitchBlack(Res.string.palette_mode_pitch_black),

    System(Res.string.palette_mode_system);
}
