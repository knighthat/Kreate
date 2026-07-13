package app.kreate.internal.innertube.models

import app.kreate.gateway.innertube.models.InnertubeCharts
import app.kreate.gateway.innertube.models.InnertubeItem
import app.kreate.gateway.innertube.models.InnertubeRankedArtist
import app.kreate.gateway.innertube.models.Section
import app.kreate.gateway.innertube.responses.MusicCarouselShelfRenderer
import app.kreate.gateway.innertube.responses.MusicMultiSelectMenuRenderer
import app.kreate.gateway.innertube.responses.MusicResponsiveListItemRenderer
import app.kreate.gateway.innertube.responses.MusicShelfRenderer
import app.kreate.gateway.innertube.responses.Runs
import app.kreate.gateway.innertube.responses.SectionListRenderer
import app.kreate.gateway.innertube.responses.Thumbnails
import app.kreate.internal.innertube.utils.firstText
import app.kreate.internal.innertube.utils.toThumbnailList
import java.util.Base64


private val REGION_CODE_REGEX = Regex("^EidleHBsb3JlX2NoYXJ0c19jb3VudHJ5X21lbnVfMzE2NzY2NTY3([a-zA-Z0-9]{2,3})gkQEoAQ%3D%3D$")

private fun createInnertubeChartMenuItemFrom( renderer: MusicMultiSelectMenuRenderer.Option.ItemRenderer ): InnertubeCharts.Menu.Item {
    // No option to fail
    val encodedKey = REGION_CODE_REGEX.find( renderer.formItemEntityKey )!!.groupValues[1]
    // This capture group usually contains 3 letters, but base64 requires modulo of 4
    // so this step is added to pad the missing letter(s)
    val padding = when ( encodedKey.length % 4 ) {
        0 -> ""
        2 -> "=="
        3 -> "="
        1 -> {
            // If length % 4 is 1, it's an invalid unpadded Base64 string length
            // This case should ideally not happen if the unpadded string was correctly formed
            // from binary data, as it implies an incomplete 6-bit block.
            // A decoder expecting padding might throw an error anyway.
            // Forcing padding here might lead to incorrect decoding if the original data was truly malformed.
            throw IllegalArgumentException("Invalid Base64 string length for padding: ${encodedKey.length}")
        }
        else -> "" // Should not happen
    }
    val decodedKeyRaw = Base64.getDecoder().decode( encodedKey + padding )
    val decodedKey = decodedKeyRaw.decodeToString()
    val countryDisplayName = renderer.title.firstText

    return object : InnertubeCharts.Menu.Item {

        override val countryDisplayName: String = countryDisplayName
        override val countryCode: String = decodedKey
    }
}

private fun createInnertubeChartMenuFrom( renderer: MusicMultiSelectMenuRenderer ): InnertubeCharts.Menu {
    val title = renderer.title.musicMenuTitleRenderer.primaryText.firstText
    val items = renderer.options
        .mapNotNull( MusicMultiSelectMenuRenderer.Option::musicMultiSelectMenuItemRenderer )
        .map( ::createInnertubeChartMenuItemFrom )

    return object : InnertubeCharts.Menu {

        override val title: String = title
        override val items: List<InnertubeCharts.Menu.Item> = items
    }
}

private fun createInnertubeRankedArtistFrom( renderer: MusicResponsiveListItemRenderer ): InnertubeRankedArtist {
    val id = renderer.navigationEndpoint?.browseEndpoint?.browseId
    requireNotNull( id ) { "MusicResponsiveListItemRenderer doesn't contain browseId" }
    //<editor-fold desc="First column">
    val name = renderer.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.firstText
    requireNotNull( name ) { "MusicResponsiveListItemRenderer doesn't contain name" }
    val thumbnails = renderer.thumbnail?.toThumbnailList().orEmpty()
    //</editor-fold>
    //<editor-fold desc="Second column">
    val secondColumn = renderer.flexColumns.getOrNull( 1 )?.musicResponsiveListItemFlexColumnRenderer
    val shortNumSubscribers = secondColumn?.text?.firstText
    val subtitle = secondColumn?.text
    //</editor-fold>
    //<editor-fold desc="Index column">
    val indexColumn = renderer.customIndexColumn?.musicCustomIndexColumnRenderer
    requireNotNull( indexColumn ) { "MusicResponsiveListItemRenderer doesn't contain index column" }
    val rank = indexColumn.text.firstText
    val iconType = indexColumn.icon?.iconType
    //</editor-fold>

    return object : InnertubeRankedArtist {

        override val rank: String = rank
        override val iconType: String? = iconType
        override val shortNumSubscribers: String? = shortNumSubscribers
        override val longNumSubscribers: String? = null
        override val shortNumMonthlyAudience: String? = null
        override val subtitle: Runs? = subtitle
        override val id: String = id
        override val name: String = name
        override val thumbnails: List<Thumbnails.Thumbnail> = thumbnails
        override val description: String? = null
        override val sections: List<Section> = emptyList()
    }
}

private fun createChartSectionFrom( renderer: MusicCarouselShelfRenderer ): Section {
    val mutableContents = mutableListOf<InnertubeItem>()
    renderer.contents.forEach { item ->
        item.musicResponsiveListItemRenderer
            ?.let( ::createInnertubeRankedArtistFrom )
            ?.also( mutableContents::add )

        item.musicTwoRowItemRenderer
            ?.let( ::createInnertubeItemFrom )
            ?.also( mutableContents::add )
    }

    val contents = mutableContents.toList()
    val title = renderer.header.musicCarouselShelfBasicHeaderRenderer.title.firstText
    return object : Section {
        override val title: String = title
        override val accessibilityLabel: String? = null
        override val browseId: String? = null
        override val params: String? = null
        override val contents: List<InnertubeItem> = contents
    }
}

internal fun createInnertubeCharsFrom( renderer: SectionListRenderer ): InnertubeCharts {
    var selected: String? = null
    var menu: InnertubeCharts.Menu? = null
    val mutableSections = mutableListOf<Section>()

    renderer.contents.forEach { item ->
        item.musicShelfRenderer
            ?.subheaders
            ?.firstNotNullOfOrNull(MusicShelfRenderer.Subheader::musicSideAlignedItemRenderer )
            ?.startItems
            ?.firstNotNullOfOrNull( MusicShelfRenderer.Subheader.Renderer.Item::musicSortFilterButtonRenderer )
            ?.also { selected = it.title.firstText }
            ?.menu
            ?.musicMultiSelectMenuRenderer
            ?.let( ::createInnertubeChartMenuFrom )
            ?.also { menu = it }

        item.musicCarouselShelfRenderer
            ?.let(::createChartSectionFrom )
            ?.also( mutableSections::add )
    }

    requireNotNull( selected ) { "selected is null" }
    requireNotNull( menu ) { "menu is null" }
    val sections = mutableSections.toList()
    return object : InnertubeCharts {

        override val selectedCountryName: String = selected
        override val menu: InnertubeCharts.Menu = menu
        override val sections: List<Section> = sections
    }
}