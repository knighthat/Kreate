package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.Runs
import kotlinx.serialization.Serializable


@Serializable
internal data class RunsImpl(
    override val runs: List<RunImpl> = emptyList(),
    override val accessibility: AccessibilityImpl?
): Runs {

    @Serializable
    internal data class RunImpl(
        override val bold: Boolean?,
        override val text: String,
        override val navigationEndpoint: EndpointImpl?
    ): Runs.Run
}