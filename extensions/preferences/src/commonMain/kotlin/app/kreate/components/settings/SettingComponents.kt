package app.kreate.components.settings

import androidx.annotation.IntRange
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.preferences.Preferences
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.ui.styling.ColorPalette
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.ui.styling.Typography
import org.jetbrains.compose.resources.DrawableResource

object SettingComponents {

    /**
     * Default indentation, applied to all entries
     */
    private const val DEFAULT_HORIZONTAL_PADDING = 12

    /**
     * Use for subsequent indentation.
     *
     * ```kotlin
     * Modifier.padding( start = SettingComponents.HORIZONTAL_PADDING )
     * ```
     *
     * Avoid using `all` for children's padding, it'll
     * shrink the width, making it more clustered.
     */
    const val HORIZONTAL_PADDING = 12

    /**
     * Space between entries in vertical layout
     */
    const val VERTICAL_SPACING = 12

    const val CHILDREN_PADDING = 25

    @Composable
    internal fun colors(
        colorPalette: ColorPalette = LocalAppearance.current.colorPalette,
        containerColor: Color = Color.Transparent,
        headlineColor: Color = colorPalette.text,
        leadingIconColor: Color = Color.Unspecified,    // Let each implementation decide which color to use
        overlineColor: Color = colorPalette.accent,
        supportingColor: Color = colorPalette.textSecondary,
        trailingIconColor: Color = Color.Unspecified,   // Let each implementation decide which color to use
        disabledHeadlineColor: Color = colorPalette.textDisabled,
        disabledLeadingIconColor: Color = colorPalette.textDisabled,
        disabledTrailingIconColor: Color = colorPalette.textDisabled,
    ): ListItemColors =
        ListItemDefaults.colors(
            containerColor = containerColor,
            headlineColor = headlineColor,
            leadingIconColor = leadingIconColor,
            overlineColor = overlineColor,
            supportingColor = supportingColor,
            trailingIconColor = trailingIconColor,
            disabledHeadlineColor = disabledHeadlineColor,
            disabledLeadingIconColor = disabledLeadingIconColor,
            disabledTrailingIconColor = disabledTrailingIconColor,
        )


    @Composable
    fun Header(
        title: String,
        modifier: Modifier = Modifier,
        subtitle: String? = null,
        colorPalette: ColorPalette = LocalAppearance.current.colorPalette,
        typography: Typography = LocalAppearance.current.typography,
        trailingContent: @Composable () -> Unit = {}
    ) =
        ListItem(
            headlineContent = {
                Text(
                    text = title.uppercase(),
                    fontSize = typography.xl.fontSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = subtitle?.let { subtitle -> {
                    Text(
                        text = subtitle,
                        fontSize = typography.xs.fontSize,
                        maxLines = 2,
                        overflow = TextOverflow.Clip
                    )
                }
            },
            trailingContent = trailingContent,
            colors = colors(
                containerColor = colorPalette.background0,
                headlineColor = colorPalette.accent,
                supportingColor = colorPalette.textSecondary
            ),
            modifier = modifier.background( colorPalette.background0 )
                               .padding( top = 32.dp )
                                   .drawBehind {
                                   // Simple dimmed line to make distinction
                                   // between header and other elements.
                                   drawLine(
                                       color = colorPalette.textDisabled.copy( .6f ),
                                       start = Offset(0f, size.height),
                                       end = Offset(size.width, size.height),
                                       strokeWidth = 2f
                                   )
                               }
        )

    /**
     * A non-clickable entry, suitable for settings
     * that have external button (e.g. switch, dropdown menu, etc.)
     */
    @Composable
    fun Entry(
        title: String,
        modifier: Modifier = Modifier,
        subtitle: String? = null,
        colors: ListItemColors = colors(),
        typography: Typography = LocalAppearance.current.typography,
        trailingContent: @Composable () -> Unit = {}
    ) =
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    fontSize = typography.s.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                if( subtitle.isNullOrBlank() ) return@ListItem

                Text(
                    text = subtitle,
                    fontSize = typography.xs.fontSize,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingContent = trailingContent,
            colors = colors,
            modifier = modifier
        )

    /**
     * A clickable button. It comes with ripple effect when pressed
     */
    @Composable
    fun Entry(
        onClick: () -> Unit,
        title: String,
        modifier: Modifier = Modifier,
        subtitle: String? = null,
        enabled: Boolean = true,
        colors: ListItemColors = colors(),
        typography: Typography = LocalAppearance.current.typography,
        trailingContent: @Composable () -> Unit = {}
    ) =
        Entry(
            title = title,
            subtitle = subtitle,
            colors = colors,
            typography = typography,
            trailingContent = trailingContent,
            modifier = modifier.clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
        )

    @Composable
    fun BooleanEntry(
        preference: Preferences.BooleanPref,
        title: String,
        modifier: Modifier = Modifier,
        subtitle: String = "",
        enabled: Boolean = true,
        action: Action = Action.NONE,
        onValueChanged: (Boolean) -> Unit = {}
    ) =
        Entry(
            title = title,
            modifier = modifier,
            subtitle = subtitle,
            trailingContent = {
                val colorPalette = LocalAppearance.current.colorPalette
                val checked by preference.collectAsStateWithLifecycle()

                Switch(
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = {
                        preference.update(it)
                        onValueChanged(it)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colorPalette.onAccent,
                        checkedTrackColor = colorPalette.accent,
                        uncheckedThumbColor = colorPalette.textDisabled,
                        uncheckedTrackColor = colorPalette.background1,
                        disabledCheckedThumbColor = colorPalette.textDisabled,
                        disabledCheckedTrackColor = colorPalette.background1,
                        disabledUncheckedThumbColor = colorPalette.textDisabled,
                        disabledUncheckedTrackColor = colorPalette.background1
                    )
                )
            }
        )

    @ExperimentalMaterial3Api
    @Composable
    fun <K, V> SliderEntry(
        preference: Preferences<K, V>,
        title: String,
        constraint: String,
        valueRange: ClosedFloatingPointRange<Float>,
        @IntRange(from = 0) steps: Int,
        onTextDisplay: @Composable (Float) -> String,
        onValueChangeFinished: (Preferences<K, V>, Float) -> Unit,
        modifier: Modifier = Modifier,
        subtitle: String? = null,
        icon: DrawableResource? = null,
        placeholder: String = "",
        enabled: Boolean = true,
        action: Action = Action.NONE,
        properties: InputDialogProperties = InputDialogProperties.default,
        trailingContent: @Composable (Float) -> Unit = {}
    ) where V: Number, V: Comparable<V> =
        Column {
            val (colorPalette, typography) = LocalAppearance.current
            val currentValue by preference.collectAsStateWithLifecycle()
            val (realtimeValue, setRealtimeValue) = remember { mutableFloatStateOf(currentValue.toFloat()) }

            InputDialogEntry(
                title = title,
                constraint = Regex(constraint),
                onConfirmRequest = {
                    try {
                        val value = it.text.toString().toFloat()
                        onValueChangeFinished( preference, value )
                    } catch( err: NumberFormatException ) {
                        Logger.e( "", err, "SliderEntry" )
                    }
                },
                modifier = modifier,
                subtitle = subtitle,
                state = rememberTextFieldState( currentValue.toString() ),
                icon = icon,
                placeholder = placeholder,
                enabled = enabled,
                action = action,
                properties = properties,
                trailingContent = { trailingContent(realtimeValue) }
            )

            ListItem(
                headlineContent = {
                    Slider(
                        value = realtimeValue,
                        valueRange = valueRange,
                        steps = steps,
                        onValueChange = setRealtimeValue,
                        onValueChangeFinished = {
                            onValueChangeFinished( preference, realtimeValue )
                        },
                        colors = SliderColors(
                            thumbColor = colorPalette.onAccent,
                            activeTrackColor = colorPalette.accent,
                            activeTickColor = Color.Transparent,
                            inactiveTrackColor = colorPalette.text.copy( 0.75f ),
                            inactiveTickColor = Color.Transparent,
                            disabledThumbColor = Color.Unspecified,
                            disabledActiveTrackColor = Color.Unspecified,
                            disabledActiveTickColor = Color.Transparent,
                            disabledInactiveTrackColor = Color.Transparent,
                            disabledInactiveTickColor = Color.Unspecified
                        ),
                        modifier = Modifier.height( 15.dp )
                    )
                },
                supportingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding( top = 6.dp )
                    ) {
                        Text(
                            text = onTextDisplay( valueRange.start ),
                            fontSize = typography.xxs.fontSize,
                            color = colorPalette.textSecondary
                        )

                        Text(
                            text = onTextDisplay( realtimeValue ),
                            fontSize = typography.xxs.fontSize,
                            color = colorPalette.text,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight( 1f )
                        )

                        Text(
                            text = onTextDisplay( valueRange.endInclusive ),
                            fontSize = typography.xxs.fontSize,
                            color = colorPalette.textSecondary
                        )
                    }
                },
                colors = colors()
            )
        }

    /**
     * A set of actions to enact once the setting is set.
     */
    enum class Action {

        NONE,
        RESTART_APP,
        RESTART_PLAYER_SERVICE;
    }
}