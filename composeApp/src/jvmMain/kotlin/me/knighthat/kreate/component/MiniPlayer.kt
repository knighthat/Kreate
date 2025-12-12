package me.knighthat.kreate.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kreate.composeapp.generated.resources.Res
import kreate.composeapp.generated.resources.button_next
import kreate.composeapp.generated.resources.button_play
import kreate.composeapp.generated.resources.button_previous
import me.knighthat.kreate.CONTENT_SPACING
import org.jetbrains.compose.resources.stringResource


@Composable
private fun RowScope.ActionButton(
    imageVector: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) =
    IconButton(
        onClick = onClick,
        modifier = modifier.weight( 1f )
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxHeight()
                               .aspectRatio( 1f )
        )
    }

@Composable
private fun Actionbar( isHovered: Boolean ) =
    AnimatedVisibility(
        visible = isHovered,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            ActionButton(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = stringResource( Res.string.button_previous )
            ) { /* TODO: Implement action */ }

            ActionButton(
                imageVector = Icons.Rounded.PlayCircleFilled,
                contentDescription = stringResource( Res.string.button_play )
            ) { /* TODO: Implement action */ }

            ActionButton(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = stringResource( Res.string.button_next )
            ) { /* TODO: Implement action */ }
        }
    }


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MiniPlayer(
    containerShape: Shape,
    modifier: Modifier = Modifier
) {
    val windowInfo = LocalWindowInfo.current
    val containerHeight by remember {derivedStateOf {
        (windowInfo.containerSize.height * .25f).dp
    }}

    var isHovered by remember { mutableStateOf(false) }

    Surface(
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = containerShape,
        modifier = modifier.fillMaxWidth()
                           .requiredHeight( containerHeight )
                           .onPointerEvent(
                               eventType = PointerEventType.Enter,
                               onEvent = { isHovered = true }
                           )
                           .onPointerEvent(
                               eventType = PointerEventType.Exit,
                               onEvent = { isHovered = false }
                           )
    ) {
        val containerColor = MaterialTheme.colorScheme.surface
        AsyncImage(
            model = "https://lh3.googleusercontent.com/DGKlxgIEvOEy2YEN2gpVsoUVpgynHWKGBnhoubf6m6rSCmEpWghTQyphw9h5eeS8kekWSmfZqTuuV_w=w300-h300-l90-rj",
            contentDescription = "thumbnail",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.drawWithCache {
                val gradient = Brush.verticalGradient(
                   .0f to containerColor.copy( .01f ),
                   .5f to containerColor.copy( .6f ),
                   .75f to containerColor.copy( .9f ),
                   1f to containerColor.copy( 1f )
                )
                onDrawWithContent {
                   drawContent()
                   drawRect(
                       brush = gradient,
                       blendMode = BlendMode.Multiply
                   )
                }
            }
        )

        Column(
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier.fillMaxWidth()
                               .padding( horizontal = CONTENT_SPACING.dp )
        ) {
            Text(
                text = "Kreate is awesome",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1
            )

            Text(
                text = "KnightHat",
                color = MaterialTheme.colorScheme.outline,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )

            Actionbar( isHovered )
        }
    }
}