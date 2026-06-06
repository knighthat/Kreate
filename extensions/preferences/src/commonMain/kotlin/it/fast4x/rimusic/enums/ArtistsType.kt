package it.fast4x.rimusic.enums

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.chip_library
import kreate.resources.generated.resources.chip_liked
import org.jetbrains.compose.resources.StringResource

enum class ArtistsType(
    override val textId: StringResource
): TextView {

    Favorites(Res.string.chip_liked),
    Library(Res.string.chip_library);
}