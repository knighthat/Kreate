package it.fast4x.rimusic.enums

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.word_both
import kreate.resources.generated.resources.word_disabled
import kreate.resources.generated.resources.word_home
import kreate.resources.generated.resources.word_lockscreen
import org.jetbrains.compose.resources.StringResource

enum class WallpaperType(
    override val textId: StringResource
): TextView {

    DISABLED(Res.string.word_disabled),
    HOME(Res.string.word_home),
    LOCKSCREEN(Res.string.word_lockscreen),
    BOTH(Res.string.word_both);
}