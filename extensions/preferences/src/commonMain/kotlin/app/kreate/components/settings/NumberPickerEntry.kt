package app.kreate.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.component.ConfirmDialog
import app.kreate.component.InfinitePicker
import app.kreate.components.settings.SettingComponents.Action
import app.kreate.preferences.Preferences
import it.fast4x.rimusic.ui.styling.LocalAppearance
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.pluralStringResource


@Composable
private fun VisualGuide(
    color: Color,
    yOffset: Dp,
    modifier: Modifier = Modifier
) =
    HorizontalDivider(
        color = color.copy( alpha = 0.2f ),
        modifier = modifier.offset( y = yOffset )
            // Force a separate layer so BlendMode.SrcIn behaves correctly
            .graphicsLayer( compositingStrategy = CompositingStrategy.Offscreen )
            .drawWithContent {
                // Draw the actual HorizontalDivider first
                drawContent()

                val fadeBrush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    .15f to color,
                    .85f to color,
                    1f to Color.Transparent
                )

                // Overwrite the divider pixels with the gradient
                drawRect(
                    brush = fadeBrush,
                    blendMode = BlendMode.SrcIn
                )
            }
    )

@Composable
fun SettingComponents.NumberPickerEntry(
    preferences: Preferences.IntPref,
    unit: PluralStringResource,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: DrawableResource? = null,
    action: Action = Action.NONE,
    trailingContent: @Composable () -> Unit = {}
) {
    val (colorPalette, typography) = LocalAppearance.current

    val selected by preferences.collectAsStateWithLifecycle()
    val (realtimeValue, setRealtimeValue) = rememberSaveable( selected ) {
        mutableIntStateOf( selected )
    }
    var isDialogVisible by rememberSaveable { mutableStateOf(false) }
    val isScrollingState = rememberSaveable { mutableStateOf(false) }
    //<editor-fold desc="Helper functions">
    fun onDismissRequest() {
        isDialogVisible = false
    }
    //</editor-fold>

    Entry(
        title = title,
        subtitle = subtitle,
        trailingContent = trailingContent,
        modifier = modifier,
        onClick = { isDialogVisible = true }
    )

    if( isDialogVisible )
        ConfirmDialog(
            onDismissRequest = ::onDismissRequest,
            onConfirmRequest = {
                onDismissRequest()
                preferences.update( realtimeValue )
                consumeAction( action )
            },
            icon = icon,
            title = title,
            text = {
                Box(
                    modifier = Modifier.size( 300.dp, 180.dp ),
                    contentAlignment = Alignment.Center
                ) {
                    // Visual guides: Subtle selection lines spanning the middle slot
                    VisualGuide( colorPalette.accent, (-25).dp )
                    VisualGuide( colorPalette.accent, 24.dp )

                    // Layout content bundled tight in the middle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy( 12.dp )
                    ) {
                        Box(
                            modifier = Modifier.weight( 1f ),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            InfinitePicker(
                                numbers = preferences.range.toList(),
                                startIndex = preferences.range.indexOf( selected ),
                                textStyle = typography.xxxl.copy( color = colorPalette.accent ),
                                onValueChange = setRealtimeValue,
                                modifier = Modifier.wrapContentWidth(),
                                color = colorPalette.textDisabled,
                                isScrollingState = isScrollingState
                            )
                        }

                        Box(
                            modifier = Modifier.weight( 1f ),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = pluralStringResource( unit, realtimeValue ),
                                style = typography.s,
                                color = colorPalette.textDisabled
                            )
                        }
                    }
                }
            },
            containerColor = colorPalette.background0,
            iconContentColor = colorPalette.text,
            titleContentColor = colorPalette.text,
            textContentColor = colorPalette.text
        )
}