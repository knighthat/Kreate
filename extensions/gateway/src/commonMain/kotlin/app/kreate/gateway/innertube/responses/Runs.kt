package app.kreate.gateway.innertube.responses


interface Runs : Iterable<String> {

    val runs: List<Run>
    val accessibility: Accessibility?

    interface Run {

        val bold: Boolean?
        val text: String
        val navigationEndpoint: Endpoint?
    }
}