package it.fast4x.rimusic.enums

import app.kreate.component.TextView
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.cache_location_private
import kreate.resources.generated.resources.cache_location_split
import kreate.resources.generated.resources.cache_location_system
import kreate.resources.generated.resources.setting_description_cache_location_private
import kreate.resources.generated.resources.setting_description_cache_location_split
import kreate.resources.generated.resources.setting_description_cache_location_system
import org.jetbrains.compose.resources.StringResource

enum class ExoPlayerCacheLocation(
    override val textId: StringResource,
    val subtitleId: StringResource
): TextView {

    System(Res.string.cache_location_system, Res.string.setting_description_cache_location_system),
    Private(Res.string.cache_location_private, Res.string.setting_description_cache_location_private),
    SPLIT(Res.string.cache_location_split, Res.string.setting_description_cache_location_split);
}