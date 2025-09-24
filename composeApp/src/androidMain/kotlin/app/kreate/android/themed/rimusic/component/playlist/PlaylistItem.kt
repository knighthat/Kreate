package app.kreate.android.themed.rimusic.component.playlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastJoinToString
import androidx.compose.ui.util.fastZip
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.drawable.APP_ICON_IMAGE_BITMAP
import app.kreate.android.enums.PlatformIndicatorType
import app.kreate.android.themed.rimusic.component.MultiplatformItem
import app.kreate.android.themed.rimusic.component.Visual
import app.kreate.android.utils.ItemUtils
import app.kreate.android.utils.scrollingText
import it.fast4x.innertube.Innertube
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.models.Playlist
import it.fast4x.rimusic.ui.styling.Appearance
import it.fast4x.rimusic.ui.styling.ColorPalette
import it.fast4x.rimusic.ui.styling.Typography
import it.fast4x.rimusic.ui.styling.onOverlay
import it.fast4x.rimusic.ui.styling.overlay
import it.fast4x.rimusic.utils.medium
import it.fast4x.rimusic.utils.semiBold
import it.fast4x.rimusic.utils.shimmerEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.mapLatest
import me.knighthat.innertube.model.InnertubePlaylist
import me.knighthat.innertube.response.Runs

object PlaylistItem: Visual(), MultiplatformItem {

    const val VERTICAL_SPACING = 5
    const val HORIZONTAL_SPACING = 10
    const val ROW_SPACING = VERTICAL_SPACING * 4
    const val COLUMN_SPACING = HORIZONTAL_SPACING

    val REGEX_PLAYLIST_ID = Regex("^(PL|UU|LL|RD|OL|VL)")
    val CORNERS = listOf( Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd )
    override val platformIndicatorType: PlatformIndicatorType by Preferences.PLAYLISTS_PLATFORM_INDICATOR
    override val thumbnailRoundnessPercent: Preferences.Int = Preferences.PLAYLIST_THUMBNAIL_ROUNDNESS_PERCENT

    /**
     * Text is clipped if exceeds length limit, plus,
     * conditional marquee effect is applied by default.
     *
     * @param title of the playlist, must **not** contain artifacts or prefixes
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
     * @param text to go along
     * @param values contains [TextStyle] and [Color] configs for this component
     * @param modifier the [Modifier] to be applied to this layout node
     *
     * @see scrollingText
     */
    @Composable
    fun Subtitle(
        text: String,
        values: Values,
        textAlign: TextAlign,
        modifier: Modifier = Modifier
    ) =
        Text(
            text = text,
            style = values.subtitleTextStyle,
            color = values.subtitleTextColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = textAlign,
            modifier = modifier.scrollingText()
        )

    @Composable
    fun Thumbnail(
        browseId: String?,
        thumbnailUrl: String?,
        widthDp: Dp,
        modifier: Modifier = Modifier,
        showPlatformIcon: Boolean = true
    ) =
        Box(
            modifier = modifier.requiredSize( widthDp )
                               .padding( bottom = VERTICAL_SPACING.dp ),
        ) {
            Thumbnail( thumbnailUrl, ContentScale.FillWidth )

            if( showPlatformIcon && browseId?.matches( REGEX_PLAYLIST_ID ) == true )
                PlatformIndicator()
        }

    /**
     * Renders thumbnail(s) depends on number of urls provided in [thumbnailUrls].
     *
     * [thumbnailUrls] is a set to ensure uniqueness.
     *
     * There are 3 scenarios:
     * - Empty set: No render made, but will create an empty box
     * - Less than 4 urls: pick a random url if [useRandom] is `true`, else pick first url
     * - 4 or more urls: take first **4** and assign them at each corner of a box
     *
     * @param browseId used to determine whether, for example, this playlist is a YouTube Playlist to assign
     * platform icon. `null` is allowed, and will default to "**Not a YouTube playlist**"
     * @param thumbnailUrls a set of unique urls, number of elements dictates the final render
     * @param sizeDp size of the thumbnail, not individual component when there's more than 1 render.
     * @param modifier the modifier to be applied to the layout.
     * @param showPlatformIcon whether to show platform specific icon. Disabling this will hide
     * the icon even when [browseId] is from a platform
     * @param useRandom whether to pick a random url in [thumbnailUrls] if there are more than 1 url
     * and less than 4 urls
     *
     * @see Iterable.first
     * @see Collection.random
     */
    @Composable
    fun Thumbnail(
        browseId: String?,
        thumbnailUrls: Set<String>,
        sizeDp: Dp,
        modifier: Modifier = Modifier,
        showPlatformIcon: Boolean = true,
        useRandom: Boolean = true,
        thumbnailContent: @Composable BoxScope.() -> Unit = {}
    ) =
        Box(
            modifier = modifier.requiredSize( sizeDp )
                               .clip( thumbnailShape )
        ) {
            BoxWithConstraints( Modifier.fillMaxSize() ) {
                val indvModifier = remember( thumbnailUrls.size ) {
                    val (width, height) = with( thumbnailUrls.size ) {
                        if( this < 4 )
                            maxWidth to maxHeight
                        else
                            maxWidth / 2 to maxHeight / 2
                    }

                    Modifier.size( width, height )
                }

                if( thumbnailUrls.isEmpty() )
                    Image(
                        bitmap = APP_ICON_IMAGE_BITMAP,
                        contentDescription = null,
                        contentScale = ContentScale.FillHeight,
                        modifier = indvModifier
                    )
                else if( thumbnailUrls.size < 4 )
                    ImageFactory.AsyncImage(
                        thumbnailUrl = if( useRandom ) thumbnailUrls.random() else thumbnailUrls.first(),
                        contentScale = ContentScale.FillHeight,
                        modifier = indvModifier
                    )
                else
                    CORNERS.fastZip( thumbnailUrls.toList(), Alignment::to )
                           .fastForEach { (corner, url) ->
                               ImageFactory.AsyncImage(
                                   thumbnailUrl = url,
                                   // Some thumbnails aren't in 1:1 ratio (4:3, 16:9, etc.)
                                   // Without [contentScale], the image is *squished*
                                   contentScale = ContentScale.FillHeight,
                                   contentDescription = corner.toString(),
                                   modifier = indvModifier.align( corner)
                               )
                           }

            }

            if( showPlatformIcon && browseId?.matches( REGEX_PLAYLIST_ID ) == true )
                PlatformIndicator()

            thumbnailContent()
        }

    @ExperimentalCoroutinesApi
    @Composable
    fun Thumbnail(
        playlist: Playlist,
        sizeDp: Dp,
        modifier: Modifier = Modifier,
        showPlatformIcon: Boolean = true,
        useRandom: Boolean = true,
        thumbnailContent: @Composable BoxScope.() -> Unit = {}
    ) {
        val thumbnails by remember {
            Database.songPlaylistMapTable
                    .findThumbnailsOfMostPlayedSongIn( playlist.id, CORNERS.size )
                    .mapLatest( ::LinkedHashSet )
        }.collectAsState( emptySet(), Dispatchers.IO )

        Thumbnail(
            playlist.browseId, thumbnails, sizeDp, modifier, showPlatformIcon, useRandom, thumbnailContent
        )
    }

    @Composable
    fun SongCount(
        count: Int,
        values: Values,
        modifier: Modifier = Modifier
    ) =
        Text(
            text = count.toString(),
            style = values.songCountTextStyle,
            color = values.songCountTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
                .background(
                    color = values.songCountBackgroundColor,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(all = 4.dp)
        )

    @Composable
    fun VerticalStructure(
        thumbnail: @Composable ColumnScope.() -> Unit,
        widthDp: Dp,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        modifier: Modifier = Modifier,
        firstLine: @Composable ColumnScope.() -> Unit = {},
        secondLine: @Composable ColumnScope.() -> Unit = {}
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
    fun HorizontalStructure(
        thumbnail: @Composable BoxScope.() -> Unit,
        heightDp: Dp,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        modifier: Modifier = Modifier,
        firstLine: @Composable ColumnScope.() -> Unit = {},
        secondLine: @Composable ColumnScope.() -> Unit = {}
    ) =
        Row(
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
            }
        }

    @Composable
    fun VerticalPlaceholder(
        widthDp: Dp,
        modifier: Modifier = Modifier,
        showTitle: Boolean = false
    ) =
        VerticalStructure(
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

    @ExperimentalCoroutinesApi
    @Composable
    fun Vertical(
        playlist: Playlist,
        widthDp: Dp,
        values: Values,
        navController: NavController?,
        modifier: Modifier = Modifier,
        songCount: Int = 0,
        showSongCount: Boolean = true,
        showPlatformIcon: Boolean = true,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {}
    ) =
        VerticalStructure(
            widthDp = widthDp,
            modifier = modifier,
            thumbnail = {
                Thumbnail(
                    playlist = playlist,
                    sizeDp = widthDp,
                    showPlatformIcon = showPlatformIcon,
                    modifier = Modifier.padding( bottom = VERTICAL_SPACING.dp )
                ) thumb@ {
                    if( songCount < 0 || !showSongCount ) return@thumb

                    SongCount(
                        count = songCount,
                        values = values,
                        modifier = Modifier.padding( all = 4.dp )
                                           .align( Alignment.BottomEnd )
                    )
                }
            },
            firstLine = {
                Title( playlist.cleanName(), values, TextAlign.Center )
            },
            onClick = click@ {
                onClick.invoke()

                if( navController == null ) return@click

                if( playlist.browseId != null && playlist.id == -1L )
                    NavRoutes.YT_PLAYLIST.navigateHere( navController, playlist.browseId )
                else
                    NavRoutes.localPlaylist.navigateHere( navController, playlist.id )
            },
            onLongClick = onLongClick
        )

    @Composable
    fun Vertical(
        innertubePlaylist: Innertube.PlaylistItem,
        widthDp: Dp,
        values: Values,
        navController: NavController?,
        modifier: Modifier = Modifier,
        showPlatformIcon: Boolean = true,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {}
    ) =
        VerticalStructure(
            widthDp = widthDp,
            modifier = modifier,
            thumbnail = {
                Thumbnail(
                    browseId = innertubePlaylist.key,
                    thumbnailUrl = innertubePlaylist.thumbnail?.url,
                    widthDp = widthDp,
                    showPlatformIcon = showPlatformIcon
                )
            },
            firstLine = {
                Title(
                    title = innertubePlaylist.title.orEmpty(),
                    values = values,
                    textAlign = TextAlign.Center
                )
            },
            onClick = click@ {
                onClick.invoke()

                if( navController == null ) return@click
                    NavRoutes.YT_PLAYLIST.navigateHere( navController, innertubePlaylist.key )
            },
            onLongClick = onLongClick
        )

    @Composable
    fun Vertical(
        innertubePlaylist: InnertubePlaylist,
        widthDp: Dp,
        values: Values,
        navController: NavController?,
        modifier: Modifier = Modifier,
        showSubtitle: Boolean = true,
        showPlatformIcon: Boolean = true,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {}
    ) =
        VerticalStructure(
            widthDp = widthDp,
            modifier = modifier,
            thumbnail = {
                Thumbnail(
                    browseId = innertubePlaylist.id,
                    thumbnailUrl = innertubePlaylist.thumbnails.firstOrNull()?.url,
                    widthDp = widthDp,
                    showPlatformIcon = showPlatformIcon
                )
            },
            firstLine = {
                Title(
                    title = innertubePlaylist.name,
                    values = values,
                    textAlign = TextAlign.Center
                )
            },
            secondLine = nd@ {
                val subtitle = remember {
                    innertubePlaylist.subtitle
                                     ?.runs
                                     ?.fastJoinToString(
                                         separator = "",
                                         transform = Runs.Run::text
                                     )
                                     .orEmpty()
                }
                if( !showSubtitle || subtitle.isBlank() ) return@nd

                Subtitle(
                    text = subtitle,
                    values = values,
                    textAlign = TextAlign.Center
                )
            },
            onClick = click@ {
                onClick.invoke()

                if( navController == null ) return@click
                    NavRoutes.YT_PLAYLIST.navigateHere( navController, innertubePlaylist.id )
            },
            onLongClick = onLongClick
        )

    @ExperimentalCoroutinesApi
    @Composable
    fun Horizontal(
        playlist: Playlist,
        heightDp: Dp,
        values: Values,
        navController: NavController?,
        modifier: Modifier = Modifier,
        songCount: Int = 0,
        showSongCount: Boolean = true,
        showPlatformIcon: Boolean = true,
        useRandom: Boolean = true,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {}
    ) =
        HorizontalStructure(
            heightDp = heightDp,
            modifier = modifier,
            thumbnail = {
                Thumbnail(
                    playlist = playlist,
                    sizeDp = heightDp,
                    showPlatformIcon = showPlatformIcon,
                    useRandom = useRandom,
                    modifier = Modifier.padding( bottom = VERTICAL_SPACING.dp )
                ) thumb@ {
                    if( songCount < 0 || !showSongCount ) return@thumb

                    SongCount(
                        count = songCount,
                        values = values,
                        modifier = Modifier.padding( all = 4.dp )
                                           .align( Alignment.BottomEnd )
                    )
                }
            },
            firstLine = {
                Title( playlist.cleanName(), values, TextAlign.Start )
            },
            onClick = click@ {
                onClick.invoke()

                if( navController == null ) return@click

                if( playlist.browseId != null && playlist.id == -1L )
                    NavRoutes.YT_PLAYLIST.navigateHere( navController, playlist.browseId )
                else
                    NavRoutes.localPlaylist.navigateHere( navController, playlist.id )
            },
            onLongClick = onLongClick
        )

    @ExperimentalCoroutinesApi
    @Composable
    fun Horizontal(
        innertubePlaylist: Innertube.PlaylistItem,
        heightDp: Dp,
        values: Values,
        navController: NavController?,
        modifier: Modifier = Modifier,
        showPlatformIcon: Boolean = true,
        onClick: () -> Unit = {},
        onLongClick: () -> Unit = {}
    ) =
        HorizontalStructure(
            heightDp = heightDp,
            modifier = modifier,
            thumbnail = {
                Thumbnail(
                    browseId = innertubePlaylist.key,
                    thumbnailUrl = innertubePlaylist.thumbnail?.url,
                    widthDp = heightDp,
                    showPlatformIcon = showPlatformIcon
                )
            },
            firstLine = {
                Title( innertubePlaylist.title.toString(), values, TextAlign.Start )
            },
            onClick = click@ {
                onClick.invoke()

                if( navController == null ) return@click
                    NavRoutes.YT_PLAYLIST.navigateHere( navController, innertubePlaylist.key )
            },
            onLongClick = onLongClick
        )

    data class Values(
        val titleTextStyle: TextStyle,
        val titleColor: Color,
        val subtitleTextStyle: TextStyle,
        val subtitleTextColor: Color,
        val songCountTextStyle: TextStyle,
        val songCountTextColor: Color,
        val songCountBackgroundColor: Color
    ) {
        companion object {
            val unspecified: Values by lazy {
                val textStyle = TextStyle()

                Values(
                    titleTextStyle = textStyle,
                    titleColor = Color.Transparent,
                    subtitleTextStyle = textStyle,
                    subtitleTextColor = Color.Transparent,
                    songCountTextStyle = textStyle,
                    songCountTextColor = Color.Transparent,
                    songCountBackgroundColor = Color.Transparent
                )
            }

            fun from( colorPalette: ColorPalette, typography: Typography ) =
                Values(
                    titleTextStyle = typography.xs.semiBold,
                    titleColor = colorPalette.text,
                    subtitleTextStyle = typography.xs.semiBold,
                    subtitleTextColor = colorPalette.textSecondary,
                    songCountTextStyle = typography.xxs.medium,
                    songCountTextColor = colorPalette.onOverlay,
                    songCountBackgroundColor = colorPalette.overlay
                )

            fun from( appearance: Appearance ) =
                from( appearance.colorPalette, appearance.typography )
        }
    }
}