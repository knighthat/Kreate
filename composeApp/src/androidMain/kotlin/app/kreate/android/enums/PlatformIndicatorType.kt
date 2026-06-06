package app.kreate.android.enums

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.word_disabled
import kreate.resources.generated.resources.word_icon
import org.jetbrains.compose.resources.StringResource

enum class PlatformIndicatorType(
    override val textId: StringResource
): TextView {

    ICON(Res.string.word_icon),
    DISABLED(Res.string.word_disabled);
}