package it.fast4x.rimusic.enums

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.size_big
import kreate.resources.generated.resources.size_bigger
import kreate.resources.generated.resources.size_biggest
import kreate.resources.generated.resources.size_medium
import kreate.resources.generated.resources.size_small
import org.jetbrains.compose.resources.StringResource

enum class CarouselSize(
    val size: Int,
    override val textId: StringResource
): TextView {

    Small(90, Res.string.size_small),

    Medium(55, Res.string.size_medium),

    Big(30, Res.string.size_big),

    Biggest(20, Res.string.size_bigger),

    Expanded(0, Res.string.size_biggest);
}
