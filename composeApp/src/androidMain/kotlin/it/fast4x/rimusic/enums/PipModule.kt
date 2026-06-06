package it.fast4x.rimusic.enums

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.pipmodule_cover
import org.jetbrains.compose.resources.StringResource

enum class PipModule(
    override val textId: StringResource
): TextView {

    Cover(Res.string.pipmodule_cover)
}