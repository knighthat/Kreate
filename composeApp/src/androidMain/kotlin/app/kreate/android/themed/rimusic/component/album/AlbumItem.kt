package app.kreate.android.themed.rimusic.component.album

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.enums.PlatformIndicatorType
import app.kreate.android.themed.rimusic.component.MultiplatformItem
import app.kreate.android.themed.rimusic.component.Visual
import app.kreate.android.utils.ItemUtils
import app.kreate.android.utils.innertube.toAlbum
import app.kreate.android.utils.scrollingText
import app.kreate.database.models.Album
import it.fast4x.innertube.Innertube
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.ui.styling.Appearance
import it.fast4x.rimusic.ui.styling.ColorPalette
import it.fast4x.rimusic.ui.styling.Typography
import it.fast4x.rimusic.utils.asAlbum
import it.fast4x.rimusic.utils.semiBold
import it.fast4x.rimusic.utils.shimmerEffect
import me.knighthat.innertube.model.InnertubeAlbum

object AlbumItem: Visual(), MultiplatformItem {

    const val VERTICAL_SPACING = 5
    const val HORIZONTAL_SPACING = 10
    const val ROW_SPACING = VERTICAL_SPACING * 4
    const val COLUMN_SPACING = HORIZONTAL_SPACING

    const val MENU_THUMBNAIL_SIZE = 74

    override val platformIndicatorType: PlatformIndicatorType by Preferences.ALBUMS_PLATFORM_INDICATOR
    override val thumbnailRoundnessPercent: Preferences.Int = Preferences.ALBUM_THUMBNAIL_ROUNDNESS_PERCENT

    override fun thumbnailSize() = DpSize(Preferences.ALBUM_THUMBNAIL_SIZE.value.dp, Preferences.ALBUM_THUMBNAIL_SIZE.value.dp)

    /**
     * Text is clipped if exceeds length limit, plus,
     * conditional marquee effect is applied by default.
     *
     * @param title of the album, must **not** contain artifacts or prefixes
     * @param values contains [TextStyle] and [Color] configs for this component
     * @param modifier the [Modifier] to be applied to this layout node
     *
     * @see scrollingText
     */
    @Composable
    fun Title(
        title: String,
        values: Values,
        textAlign: TextAlign,
        modifier: Modifier = Modifier
    ) =
        Text(
            text = title,
            style = values.titleTextStyle,
            color = values.titleColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = textAlign,
            modifier = modifier.scrollingText()
        )

    /**
     * Text is clipped if exceeds length limit, plus,
     * conditional marquee effect is applied by default.
     *
     * @param artistsText name of the artists, must **not** contain artifacts or prefixes
     * @param values contains [TextStyle] and [Color] configs for this component
     * @param modifier the [Modifier] to be applied to this layout node
     *
     * @see scrollingText
     */
    @Composable
    fun Artists(
        artistsText: String,
        values: Values,
        textAlign: TextAlign,
        modifier: Modifier = Modifier
    ) =
        Text(
            text = artistsText,
            style = values.artistsTextStyle,
            color = values.artistsColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = textAlign,
            modifier = modifier.scrollingText()
        )

    /**
     * Text is clipped if exceeds length limit, plus,
     * conditional marquee effect is applied by default.
     *
     * @param year of the album, must **not** contain artifacts or prefixes
     * @param values contains [TextStyle] and [Color] configs for this component
     * @param modifier the [Modifier] to be applied to this layout node
     *
     * @see scrollingText
     */
    @Composable
    fun Year(
        year: String,
        values: Values,
        textAlign: TextAlign,
        modifier: Modifier = Modifier
    ) =
        Text(
            text = year,
            style = values.yearTextStyle,
            color = values.yearColor,
            maxLines = 1,
            textAlign = textAlign,
            modifier = modifier
        )

    @Composable
    fun Thumbnail(
        albumId: String,
        thumbnailUrl: String?,
        modifier: Modifier = Modifier,
        sizeDp: DpSize = thumbnailSize()
    ) =
        Thumbnail(
            url = thumbnailUrl,
            contentScale = ContentScale.FillWidth,
            modifier = modifier.padding( bottom = VERTICAL_SPACING.dp ),
            sizeDp = sizeDp
        ) {
            if( albumId.startsWith( "MPREb_" ) )
                PlatformIndicator()
        }

    @Composable
    fun VerticalStructure(
        widthDp: Dp,
        thumbnail: @Composable ColumnScope.() -> Unit,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        modifier: Modifier = Modifier,
        firstLine: @Composable ColumnScope.() -> Unit = {},
        secondLine: @Composable ColumnScope.() -> Unit = {},
        thirdLine: @Composable ColumnScope.() -> Unit = {}
    ) =
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier.requiredWidth( widthDp )
                               .combinedClickable(
                                   onClick = onClick,
                                   onLongClick = onLongClick
                               )
        ) {
            thumbnail()
            firstLine()
            secondLine()
            thirdLine()
        }

    @Composable
    fun HorizontalStructure(
        heightDp: Dp,
        thumbnail: @Composable BoxScope.() -> Unit,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        modifier: Modifier = Modifier,
        firstLine: @Composable ColumnScope.() -> Unit = {},
        secondLine: @Composable ColumnScope.() -> Unit = {},
        thirdLine: @Composable ColumnScope.() -> Unit = {}
    ) =
        Row(
            horizontalArrangement = Arrangement.spacedBy( HORIZONTAL_SPACING.dp ),
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.requiredHeight( heightDp )
                               .combinedClickable(
                                   onClick = onClick,
                                   onLongClick = onLongClick
                               )
        ) {
            Box(
                modifier = Modifier.requiredSize( heightDp ),
                content = thumbnail
            )

            Column( modifier.requiredHeight( heightDp ) ) {
                firstLine()
                secondLine()
                thirdLine()
            }
        }

    @Composable
    fun VerticalPlaceholder(
        modifier: Modifier = Modifier,
        sizeDp: DpSize = thumbnailSize(),
        showTitle: Boolean = false
    ) =
        VerticalStructure(
            widthDp = sizeDp.width,
            modifier = modifier,
            thumbnail = {
                ItemUtils.ThumbnailPlaceholder( sizeDp )
            },
            firstLine = st@ {
                if( !showTitle ) return@st

                Title(
                    title = "",
                    values = Values.unspecified,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().shimmerEffect()
                )
            },
            onClick = {},
            onLongClick = {}
        )

    @Composable
    fun Vertical(
        album: Album,
        values: Values,
        navController: NavController?,
        modifier: Modifier = Modifier,
        sizeDp: DpSize = thumbnailSize(),
        showYear: Boolean = true,
        showArtists: Boolean = true,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {}
    ) =
        VerticalStructure(
            widthDp = sizeDp.width,
            modifier = modifier,
            thumbnail = {
                Thumbnail( album.id, album.cleanThumbnailUrl(), sizeDp = sizeDp )
            },
            firstLine = {
                Title(
                    title = album.cleanTitle(),
                    values = values,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding( vertical = VERTICAL_SPACING.dp )
                                       .fillMaxWidth( .9f )
                )
            },
            secondLine = nd@ {
                val cleanedArtists = album.cleanAuthorsText()
                if( !showArtists || cleanedArtists.isBlank() ) return@nd

                Artists(
                    artistsText = album.cleanAuthorsText(),
                    values = values,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth( .9f )
                )
            },
            thirdLine = rd@ {
                if( !showYear || album.year == null ) return@rd

                Year(
                    year = album.year,
                    values = values,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth( .9f )
                )
            },
            onClick = {
                onClick.invoke()

                if( navController != null )
                    NavRoutes.YT_ALBUM.navigateHere( navController, album.id )
            },
            onLongClick = onLongClick
        )

    @Composable
    fun Horizontal(
        album: Album,
        values: Values,
        navController: NavController?,
        modifier: Modifier = Modifier,
        sizeDp: DpSize = thumbnailSize(),
        showYear: Boolean = true,
        showArtists: Boolean = true,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {}
    ) =
        HorizontalStructure(
            heightDp = sizeDp.height,
            modifier = modifier,
            thumbnail = {
                Thumbnail( album.id, album.cleanThumbnailUrl(), sizeDp = sizeDp )
            },
            firstLine = {
                Title(
                    title = album.cleanTitle(),
                    values = values,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding( bottom = VERTICAL_SPACING.dp )
                                       .fillMaxWidth()
                )
            },
            secondLine = nd@ {
                val cleanedArtists = album.cleanAuthorsText()
                if( !showArtists || cleanedArtists.isBlank() ) return@nd

                Artists(
                    artistsText = album.cleanAuthorsText(),
                    values = values,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding( vertical = VERTICAL_SPACING.dp )
                                       .fillMaxWidth()
                )
            },
            thirdLine = rd@ {
                if( !showYear || album.year == null ) return@rd

                Year(
                    year = album.year,
                    values = values,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            onClick = {
                onClick.invoke()

                if( navController != null )
                    NavRoutes.YT_ALBUM.navigateHere( navController, album.id )
            },
            onLongClick = onLongClick
        )

    @Composable
    fun Horizontal(
        innertubeAlbum: Innertube.AlbumItem,
        values: Values,
        navController: NavController?,
        modifier: Modifier = Modifier,
        sizeDp: DpSize = thumbnailSize(),
        showYear: Boolean = true,
        showArtists: Boolean = true,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {}
    ) = Horizontal( innertubeAlbum.asAlbum, values, navController, modifier, sizeDp, showYear, showArtists, onClick, onLongClick )

    @Composable
    fun Vertical(
        innertubeAlbum: Innertube.AlbumItem,
        values: Values,
        navController: NavController?,
        modifier: Modifier = Modifier,
        sizeDp: DpSize = thumbnailSize(),
        showYear: Boolean = true,
        showArtists: Boolean = true,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {}
    ) =
        Vertical( innertubeAlbum.asAlbum, values, navController, modifier, sizeDp, showYear, showArtists, onClick, onLongClick )

    @Composable
    fun Vertical(
        innertubeAlbum: InnertubeAlbum,
        values: Values,
        navController: NavController?,
        modifier: Modifier = Modifier,
        sizeDp: DpSize = thumbnailSize(),
        showYear: Boolean = true,
        showArtists: Boolean = true,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {}
    ) =
        Vertical( innertubeAlbum.toAlbum, values, navController, modifier, sizeDp, showYear, showArtists, onClick, onLongClick )

    data class Values(
        val titleTextStyle: TextStyle,
        val titleColor: Color,
        val artistsTextStyle: TextStyle,
        val artistsColor: Color,
        val yearTextStyle: TextStyle,
        val yearColor: Color
    ) {
        companion object {
            val unspecified: Values by lazy {
                val textStyle = TextStyle()

                Values(
                    titleTextStyle = textStyle,
                    titleColor = Color.Transparent,
                    artistsTextStyle = textStyle,
                    artistsColor = Color.Transparent,
                    yearTextStyle = textStyle,
                    yearColor = Color.Transparent
                )
            }

            fun from( colorPalette: ColorPalette, typography: Typography ) =
                Values(
                    titleTextStyle = typography.xs.semiBold,
                    titleColor = colorPalette.text,
                    artistsTextStyle = typography.xs.semiBold,
                    artistsColor = colorPalette.textSecondary,
                    yearTextStyle = typography.xs.semiBold,
                    yearColor = colorPalette.textSecondary
                )

            fun from( appearance: Appearance ) =
                from( appearance.colorPalette, appearance.typography )
        }
    }
}