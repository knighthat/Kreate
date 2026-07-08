package app.kreate.gateway.innertube.responses


interface Continuation {

    val nextContinuationData: Data?
    val nextRadioContinuationData: Data?

    interface Data {

        val continuation: String
    }
}
