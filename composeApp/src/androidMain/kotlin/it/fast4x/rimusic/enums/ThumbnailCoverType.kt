package it.fast4x.rimusic.enums

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.cover_type_cd
import kreate.resources.generated.resources.cover_type_cd_with_cover
import kreate.resources.generated.resources.cover_type_vinyl
import org.jetbrains.compose.resources.StringResource

enum class ThumbnailCoverType(
    override val textId: StringResource
): TextView {

    Vinyl(Res.string.cover_type_vinyl),
    CD(Res.string.cover_type_cd),
    CDwithCover(Res.string.cover_type_cd_with_cover);
}