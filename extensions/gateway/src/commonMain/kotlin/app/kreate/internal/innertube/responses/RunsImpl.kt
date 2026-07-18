package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.Runs
import kotlinx.serialization.Serializable
import java.util.StringJoiner


@Serializable
internal data class RunsImpl(
    override val runs: List<RunImpl> = emptyList(),
    override val accessibility: AccessibilityImpl?
): Runs {

    private val fullString: String by lazy { runs.joinToString( "" ) { it } }

    override val length: Int = runs.sumOf( CharSequence::length )

    override fun get( index: Int ): Char = fullString[index]

    override fun subSequence( startIndex: Int, endIndex: Int ): CharSequence =
        fullString.subSequence( startIndex, endIndex )

    override fun toString(): String = fullString

    override fun iterator(): Iterator<String> = runs.map( Runs.Run::text ).iterator()

    override fun plus( runs: Runs ): Runs {
        check( runs is RunsImpl ) { "Incompatible type" }

        val accessibilityLabel = StringJoiner(", ").apply {
            add( accessibility?.accessibilityData?.label )
            add( runs.accessibility?.accessibilityData?.label )
        }
        val accessibility = AccessibilityImpl(
            accessibilityData = AccessibilityImpl.DataImpl(accessibilityLabel.toString())
        )
        val runs = this.runs + runs.runs

        return RunsImpl(runs, accessibility)
    }

    @Serializable
    internal data class RunImpl(
        override val bold: Boolean?,
        override val text: String,
        override val navigationEndpoint: EndpointImpl?
    ): Runs.Run {

        override val length: Int = text.length

        override fun get( index: Int ): Char = text[index]

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            text.subSequence( startIndex, endIndex )

        override fun toString(): String = text
    }
}