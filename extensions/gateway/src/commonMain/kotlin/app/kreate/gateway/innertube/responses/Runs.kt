package app.kreate.gateway.innertube.responses


interface Runs : Iterable<String> {

    val runs: List<Run>
    val accessibility: Accessibility?

    operator fun plus( runs: Runs ): Runs

    interface Run {

        val bold: Boolean?
        val text: String
        val navigationEndpoint: Endpoint?
    }
}