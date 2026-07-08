package app.kreate.gateway.innertube.models


interface InnertubeCharts: MultiContent {

    val selectedCountryName: String
    val menu: Menu

    interface Menu {

        val title: String
        val items: List<Item>

        interface Item {

            val countryDisplayName: String
            val countryCode: String
        }
    }
}