package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.models.Icon
import kotlinx.serialization.Serializable


@Serializable
internal data class IconImpl(
    override val iconType: String
) : Icon
