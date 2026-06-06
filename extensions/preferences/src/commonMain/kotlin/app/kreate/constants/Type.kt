package app.kreate.constants

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.word_legacy
import kreate.resources.generated.resources.word_modern
import org.jetbrains.compose.resources.StringResource


enum class Type(
    override val textId: StringResource
) : TextView {

    LEGACY(Res.string.word_legacy),
    MODERN(Res.string.word_modern)
}