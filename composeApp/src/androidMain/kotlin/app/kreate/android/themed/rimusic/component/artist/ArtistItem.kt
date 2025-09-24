package app.kreate.android.themed.rimusic.component.artist

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.enums.PlatformIndicatorType
import app.kreate.android.themed.rimusic.component.MultiplatformItem
import app.kreate.android.themed.rimusic.component.Visual
import app.kreate.android.themed.rimusic.component.album.AlbumItem
import app.kreate.android.utils.ItemUtils
import app.kreate.android.utils.innertube.toArtist
import app.kreate.android.utils.scrollingText
import it.fast4x.innertube.Innertube
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.models.Artist
import it.fast4x.rimusic.ui.styling.Appearance
import it.fast4x.rimusic.ui.styling.ColorPalette
import it.fast4x.rimusic.ui.styling.Typography
import it.fast4x.rimusic.utils.asArtist
import it.fast4x.rimusic.utils.semiBold
import it.fast4x.rimusic.utils.shimmerEffect
import me.knighthat.innertube.model.InnertubeArtist

object ArtistItem: Visual(), MultiplatformItem {

    const val VERTICAL_SPACING = 5
    const val ROW_SPACING = AlbumItem.VERTICAL_SPACING * 4
    const val COLUMN_SPACING = 10

    override val platformIndicatorType: PlatformIndicatorType by Preferences.ARTISTS_PLATFORM_INDICATOR
    override val thumbnailRoundnessPercent: Preferences.Int = Preferences.ARTIST_THUMBNAIL_ROUNDNESS_PERCENT

        /**
     * Text is clipped if exceeds length limit, plus,
     * conditional marquee effect is applied by default.
     *
     * @param title of the artist, must **not** contain artifacts or prefixes
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

    @Composable
    fun Thumbnail(
        artistId: String,
        thumbnailUrl: String?,
        widthDp: Dp,
        modifier: Modifier = Modifier,
        showPlatformIcon: Boolean = true
    ) =
        Box(
            modifier.requiredSize( widthDp )
                               .padding( bottom = VERTICAL_SPACING.dp ),
        ) {
            Thumbnail( thumbnailUrl, ContentScale.FillWidth )

            if( showPlatformIcon && artistId.startsWith( "UC" ) )
                PlatformIndicator()
        }

    @Composable
    fun Structure(
        thumbnail: @Composable ColumnScope.() -> Unit,
        widthDp: Dp,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        modifier: Modifier = Modifier,
        firstLine: @Composable ColumnScope.() -> Unit = {},
        secondLine: @Composable ColumnScope.() -> Unit = {},
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
        }

    @Composable
    fun Placeholder(
        widthDp: Dp,
        modifier: Modifier = Modifier,
        showTitle: Boolean = false
    ) =
        Structure(
            widthDp = widthDp,
            modifier = modifier,
            thumbnail = {
                ItemUtils.ThumbnailPlaceholder( widthDp )
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
    fun Render(
        artist: Artist,
        widthDp: Dp,
        values: Values,
        navController: NavController?,
        modifier: Modifier = Modifier,
        showTitle: Boolean = true,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {}
    ) =
        Structure(
            widthDp = widthDp,
            modifier = modifier,
            thumbnail = {
                Thumbnail( artist.id, artist.thumbnailUrl, widthDp )
            },
            firstLine = st@ {
                val cleanedName = artist.cleanName()
                if( !showTitle || cleanedName.isBlank() ) return@st

                Title(
                    title = cleanedName,
                    values = values,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding( vertical = VERTICAL_SPACING.dp )
                                       .fillMaxWidth( .9f )
                )
            },
            onClick = {
                onClick.invoke()

                if( navController != null )
                    NavRoutes.YT_ARTIST.navigateHere( navController, artist.id )
            },
            onLongClick = onLongClick
        )

    @Composable
    fun Render(
        innertubeArtist: Innertube.ArtistItem,
        widthDp: Dp,
        values: Values,
        navController: NavController?,
        modifier: Modifier = Modifier,
        showTitle: Boolean = true,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {}
    ) = Render( innertubeArtist.asArtist, widthDp, values, navController, modifier, showTitle, onClick, onLongClick )

    @Composable
    fun Render(
        innertubeArtist: InnertubeArtist,
        widthDp: Dp,
        values: Values,
        navController: NavController?,
        modifier: Modifier = Modifier,
        showTitle: Boolean = true,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {}
    ) = Render( innertubeArtist.toArtist, widthDp, values, navController, modifier, showTitle, onClick, onLongClick )

    data class Values(
        val titleTextStyle: TextStyle,
        val titleColor: Color,
        val subtitleTextStyle: TextStyle,
        val subtitleTextColor: Color
    ) {
        companion object {
            val unspecified: Values by lazy {
                val textStyle = TextStyle()

                Values(
                    titleTextStyle = textStyle,
                    titleColor = Color.Transparent,
                    subtitleTextStyle = textStyle,
                    subtitleTextColor = Color.Transparent
                )
            }

            fun from( colorPalette: ColorPalette, typography: Typography) =
                Values(
                    titleTextStyle = typography.xs.semiBold,
                    titleColor = colorPalette.text,
                    subtitleTextStyle = typography.xs.semiBold,
                    subtitleTextColor = colorPalette.textSecondary,
                )

            fun from( appearance: Appearance ) =
                from( appearance.colorPalette, appearance.typography )
        }
    }
}