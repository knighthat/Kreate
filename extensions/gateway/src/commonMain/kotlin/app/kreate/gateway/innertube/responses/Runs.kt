package app.kreate.gateway.innertube.responses


interface Runs : Iterable<String>, CharSequence {

    val runs: List<Run>
    val accessibility: Accessibility?

    operator fun plus( runs: Runs ): Runs

    interface Run : CharSequence {

        val bold: Boolean?
        val text: String
        val navigationEndpoint: Endpoint?
    }
}