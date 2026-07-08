package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.SimpleText
import kotlinx.serialization.Serializable


@Serializable
internal data class SimpleTextImpl(
    override val simpleText: String,
    override val accessibility: AccessibilityImpl?
): SimpleText