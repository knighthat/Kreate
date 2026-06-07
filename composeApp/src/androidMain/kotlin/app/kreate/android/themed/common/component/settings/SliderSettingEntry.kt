package app.kreate.android.themed.common.component.settings

import androidx.annotation.IntRange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kreate.android.themed.common.component.settings.SettingComponents.Action
import app.kreate.preferences.Preferences
import it.fast4x.rimusic.ui.styling.LocalAppearance


@ExperimentalMaterial3Api
@Composable
fun <K, V> SettingComponents.SliderEntry(
    preference: Preferences<K, V>,
    title: String,
    constraints: String,
    valueRange: ClosedFloatingPointRange<Float>,
    @IntRange(from = 0) steps: Int,
    onTextDisplay: @Composable (Float) -> String,
    onValueChangeFinished: (Preferences<K, V>, Float) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    isEnabled: Boolean = true,
    action: Action = Action.NONE,
    trailingContent: @Composable () -> Unit = {}
) where V: Number, V: Comparable<V> =
    Column (
        verticalArrangement = Arrangement.spacedBy( 5.dp ),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding( vertical = HORIZONTAL_PADDING.dp )
    ) {
        InputDialogEntry(
            preference = preference,
            title = title,
            subtitle = subtitle,
            constraint = constraints,
            keyboardOption = KeyboardOptions(keyboardType = KeyboardType.Number),
            isEnabled = isEnabled,
            action = action,
            trailingContent = trailingContent
        )

        val (colorPalette, typography) = LocalAppearance.current
        /**
         * To display real-time value without updating the setting constantly,
         * this mutable state is introduced as a temporal value holder.
         *
         * Until the value is finalized, then it's written into setting
         */
        var realtimeValue by remember( preference.value ) {
            mutableFloatStateOf( preference.value.toFloat() )
        }
        Slider(
            value = realtimeValue,
            onValueChange = { realtimeValue = it },
            modifier = Modifier.padding( horizontal = HORIZONTAL_PADDING.dp )
                               .fillMaxWidth()
                               .height( 15.dp ),
            enabled = isEnabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = { onValueChangeFinished( preference, realtimeValue ) },
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
            )
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding( horizontal = HORIZONTAL_PADDING.dp )
                               .padding( horizontal = 5.dp )
        ) {
           BasicText(
               text = onTextDisplay( valueRange.start ),
               style = typography.xxs.copy( colorPalette.textSecondary )
           )

            BasicText(
                text = onTextDisplay( realtimeValue ),
                style = typography.xxs
                                    .copy(
                                        color = colorPalette.text,
                                        textAlign = TextAlign.Center
                                    ),
                modifier = Modifier.weight( 1f )
            )

            BasicText(
                text = onTextDisplay( valueRange.endInclusive ),
                style = typography.xxs.copy( colorPalette.textSecondary )
            )
        }
    }