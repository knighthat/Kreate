package me.knighthat.kreate.screen

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.sharp.NorthWest
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastJoinToString
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import me.knighthat.innertube.model.InnertubeAlbum
import me.knighthat.innertube.model.InnertubeArtist
import me.knighthat.innertube.model.InnertubeItem
import me.knighthat.innertube.model.InnertubeSearchSuggestion
import me.knighthat.innertube.model.InnertubeSong
import me.knighthat.kreate.component.item.ALBUM_ITEM_THUMBNAIL_ROUNDNESS
import me.knighthat.kreate.component.item.SONG_ITEM_COMPONENTS_SPACING
import me.knighthat.kreate.component.item.SONG_ITEM_HORIZONTAL_PADDING
import me.knighthat.kreate.component.item.SONG_ITEM_MAX_HEIGHT
import me.knighthat.kreate.component.item.SONG_ITEM_THUMBNAIL_SIZE
import me.knighthat.kreate.constant.Route
import me.knighthat.kreate.util.LocalNavController
import me.knighthat.kreate.viewmodel.SearchScreenViewModel
import org.koin.compose.viewmodel.koinViewModel


private const val SEARCH_CONTENT_HORIZONTAL_PADDING = 10
private const val SEARCH_SUGGESTION_HEIGHT = 50

@Composable
private fun Suggestion(
    suggestion: InnertubeSearchSuggestion.Suggestion,
    modifier: Modifier = Modifier,
    onCompletion: () -> Unit,
    onClick: () -> Unit
) =
     Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy( SEARCH_CONTENT_HORIZONTAL_PADDING.dp ),
        modifier = modifier.border( 1.dp, MaterialTheme.colorScheme.inverseOnSurface )
                           .fillMaxWidth()
                           .height( SEARCH_SUGGESTION_HEIGHT.dp )
                           .padding( horizontal = SEARCH_CONTENT_HORIZONTAL_PADDING.dp )
    ) {
        Icon(
            imageVector = Icons.Default.Public,
            contentDescription = "YouTube Music suggestion",
            tint = MaterialTheme.colorScheme.inverseOnSurface
        )

         val text = remember( suggestion ) {
             buildAnnotatedString {
                 suggestion.text.runs.fastForEach { run ->
                     if( run.bold == true )
                         withStyle( SpanStyle(fontWeight = FontWeight.Bold) ) {
                             append( run.text )
                         }
                     else
                         append( run.text )
                 }
             }
         }
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight( 1f )
                               .clickable( onClick = onClick )
        )

         IconButton( onCompletion ) {
             Icon(
                 imageVector = Icons.Sharp.NorthWest,
                 contentDescription = "Complete search",
                 tint = MaterialTheme.colorScheme.inverseOnSurface
             )
         }
    }

@Composable
@NonRestartableComposable
private fun Item( item: InnertubeItem, modifier: Modifier = Modifier ) =
    Row(
        horizontalArrangement = Arrangement.spacedBy( SONG_ITEM_COMPONENTS_SPACING.dp ),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.border( 1.dp, MaterialTheme.colorScheme.inverseOnSurface )
                           .fillMaxWidth()
                           .requiredHeight( SONG_ITEM_MAX_HEIGHT.dp )
                           .padding( horizontal = SONG_ITEM_HORIZONTAL_PADDING.dp )
    ) {
        AsyncImage(
            model = item.thumbnails.firstOrNull()?.url,
            contentScale = ContentScale.FillHeight,
            contentDescription = "${item.name}'s thumbnail",
            modifier = Modifier.size( SONG_ITEM_THUMBNAIL_SIZE.dp )
                               .clip(
                                   when( item ) {
                                       is InnertubeArtist  -> CircleShape
                                       is InnertubeAlbum   -> ALBUM_ITEM_THUMBNAIL_ROUNDNESS
                                       else                -> RectangleShape
                                   }
                               )
        )

        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.weight( 1f )
                               .fillMaxHeight()
        ) {
            Text(
                text = item.name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )

            val subtitle = when( item ) {
                is InnertubeSong    -> item.artistsText
                is InnertubeArtist  -> item.shortNumMonthlyAudience
                is InnertubeAlbum   -> item.subtitle?.runs?.fastJoinToString( "" ) { it.text }
                else                -> null
            }
            if( subtitle != null )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
        }
    }

@Composable
fun SearchScreen(
    viewModel: SearchScreenViewModel = koinViewModel(),
    navController: NavController = LocalNavController.current
) =
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        val suggestions by viewModel.suggestions.collectAsState()
        for( suggestion in suggestions ) {
            fun onCompletion() {
                val query = suggestion.query
                viewModel.input.value = TextFieldValue(query, TextRange(query.length))
            }

            Suggestion(
                suggestion = suggestion,
                onCompletion = ::onCompletion,
                onClick = {
                    onCompletion()
                    navController.navigate( Route.Search.Results(suggestion.query) )
                }
            )
        }

        val items by viewModel.items.collectAsState()
        for( item in items )
            Item( item )
    }