package it.fast4x.rimusic.enums

import app.kreate.component.Drawable
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.download
import kreate.resources.generated.resources.download_done
import org.jetbrains.compose.resources.DrawableResource

enum class DownloadedStateMedia(
    override val iconId: DrawableResource
): Drawable {

    CACHED(Res.drawable.download),

    CACHED_AND_DOWNLOADED(Res.drawable.download_done),

    DOWNLOADED(Res.drawable.download_done),

    NOT_CACHED_OR_DOWNLOADED(Res.drawable.download);
}