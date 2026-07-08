package app.kreate.gateway.innertube.responses


interface Accessibility {

    val accessibilityData: Data

    interface Data {

        /**
         * Contains detail text, mostly used in text-to-speech
         */
        val label: String
    }
}