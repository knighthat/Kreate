package app.kreate.android.themed.common.component.settings

import androidx.annotation.IntRange
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.common.component.ColorPickerDialog
import app.kreate.android.utils.scrollingText
import app.kreate.constant.TextView
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.themed.Switch
import it.fast4x.rimusic.utils.semiBold
import me.knighthat.component.dialog.Dialog
import me.knighthat.component.dialog.RestartAppDialog
import me.knighthat.utils.Toaster

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
    fun Description(
        text: String,
        modifier: Modifier = Modifier,
        isImportant: Boolean = false
    ) =
        BasicText(
            text = text,
            maxLines = 2,
            style = typography().xs
                                .semiBold
                                .copy(
                                    if( isImportant )
                                        colorPalette().red
                                    else
                                        colorPalette().textSecondary
                                ),
            modifier = modifier.padding( horizontal = DEFAULT_HORIZONTAL_PADDING.dp )
        )

    @Composable
    fun Description(
        @StringRes textId: Int,
        modifier: Modifier = Modifier,
        isImportant: Boolean = false
    ) = Description( stringResource( textId ), modifier, isImportant )

    @Composable
    fun Text(
        title: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        subtitle: String = "",
        isEnabled: Boolean = true,
        trailingContent: @Composable RowScope.() -> Unit = {}
    ) =
        Row(
            horizontalArrangement = Arrangement.spacedBy( 16.dp ),
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .padding(all = DEFAULT_HORIZONTAL_PADDING.dp)
                .fillMaxWidth()
                .clickable(
                    enabled = isEnabled,
                    onClick = onClick
                )
                .alpha(if (isEnabled) 1f else 0.5f)
        ) {
            Column( Modifier.weight( 1f ) ) {
                BasicText(
                    text = title,
                    maxLines = 1,
                    style = typography().xs
                                        .semiBold
                                        .copy( colorPalette().text ),
                    modifier = Modifier.padding( bottom = 4.dp )
                                       .scrollingText()
                )

                if( subtitle.isNotBlank() )
                    BasicText(
                        text = subtitle,
                        maxLines = 2,
                        style = typography().xs
                                            .semiBold
                                            .copy( colorPalette().textSecondary )
                    )
            }

            trailingContent()
        }

    @Composable
    fun <T> ListEntry(
        title: String,
        getName: @Composable (T) -> String,
        initialValue: T,
        getList: () -> Array<T>,
        modifier: Modifier = Modifier,
        subtitle: String = "",
        isEnabled: Boolean = true,
        action: Action = Action.NONE,
        trailingContent: @Composable RowScope.() -> Unit = {},
        onValueChanged: (T) -> Unit = {}
    ) {
        var selected by remember { mutableStateOf(initialValue) }

        val dialog = remember {
            object : Dialog {
                override val dialogTitle: String
                    @Composable
                    get() = title

                override var isActive: Boolean by mutableStateOf( false )

                @Composable
                override fun DialogBody() {
                    LazyColumn( Modifier.wrapContentHeight() ) {
                        items(
                            items = getList(),
                            key = System::identityHashCode
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding( vertical = 12.dp, horizontal = 24.dp )
                                                   .fillMaxWidth()
                                                   .clickable {
                                                       selected = it
                                                       onValueChanged(it)

                                                       when (action) {
                                                           Action.RESTART_APP -> {
                                                               hideDialog()
                                                               RestartAppDialog.showDialog()
                                                           }

                                                           Action.RESTART_PLAYER_SERVICE -> {
                                                               Toaster.i( R.string.minimum_silence_length_warning )
                                                               RestartPlayerService.requestRestart()
                                                           }

                                                           else -> { /* Does nothing */ }
                                                       }
                                                   }
                            ) {
                                val colorPalette = colorPalette()
                                val (inner, outer, width) = remember( selected ) {
                                    if( selected == it )
                                        Triple(colorPalette.accent, colorPalette.onAccent, 4.dp)
                                    else
                                        Triple(colorPalette.textDisabled, Color.Transparent, 1.dp)
                                }
                                // Draws select indicator before the text
                                Canvas(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(inner, CircleShape)
                                ) {
                                    drawCircle(
                                        color = outer,
                                        radius = width.toPx(),
                                        center = size.center
                                    )
                                }

                                BasicText(
                                    text = getName( it ),
                                    style = if( selected == it ) typography().xs.semiBold else typography().xs
                                )
                            }
                        }
                    }

                    Spacer( Modifier.height( Dialog.SPACE_BETWEEN_SECTIONS.dp ) )

                    if( action == Action.RESTART_APP )
                        BasicText(
                            text = stringResource( R.string.restarting_rimusic_is_required ),
                            style = typography().xs.copy(
                                colorPalette().red.copy( .8f )
                            ),
                            modifier = Modifier.fillMaxWidth( .9f )
                        )
                }
            }
        }
        dialog.Render()

        Text(
            title = title,
            onClick = dialog::showDialog,
            modifier = modifier,
            // Default to show current value's name if no subtitle is provided
            subtitle = subtitle.ifBlank { getName( selected ) },
            isEnabled = isEnabled,
            trailingContent = trailingContent
        )
    }

    @Composable
    fun <T> ListEntry(
        preference: Preferences<T>,
        title: String,
        getName: @Composable (T) -> String,
        getList: Preferences<T>.() -> Array<T>,
        modifier: Modifier = Modifier,
        subtitle: String = "",
        isEnabled: Boolean = true,
        action: Action = Action.NONE,
        trailingContent: @Composable RowScope.() -> Unit = {},
        onValueChanged: (T) -> Unit = {}
    ) =
        ListEntry(
            title = title,
            getName = getName,
            initialValue = preference.value,
            getList = { preference.getList() },
            modifier = modifier,
            subtitle = subtitle,
            isEnabled = isEnabled,
            action = action,
            trailingContent = trailingContent,
            onValueChanged = {
                preference.value = it
                onValueChanged( it )
            }
        )

    @Composable
    inline fun <reified T: Enum<T>> EnumEntry(
        preference: Preferences.Enum<T>,
        title: String,
        noinline getName: @Composable (T) -> String,
        modifier: Modifier = Modifier,
        subtitle: String = "",
        isEnabled: Boolean = true,
        action: Action = Action.NONE,
        noinline trailingContent: @Composable RowScope.() -> Unit = {},
        noinline onValueChanged: (T) -> Unit = {}
    ) =
        ListEntry(
            preference = preference,
            title = title,
            getName = getName,
            getList = { enumValues<T>() },
            modifier = modifier,
            subtitle = subtitle,
            isEnabled = isEnabled,
            action = action,
            trailingContent = trailingContent,
            onValueChanged = onValueChanged
        )

    @Composable
    inline fun <reified T> EnumEntry(
        preference: Preferences.Enum<T>,
        title: String,
        modifier: Modifier = Modifier,
        subtitle: String = "",
        isEnabled: Boolean = true,
        action: Action = Action.NONE,
        noinline trailingContent: @Composable RowScope.() -> Unit = {},
        noinline onValueChanged: (T) -> Unit = {}
    ) where T: Enum<T>, T: TextView =
        EnumEntry( preference, title, { it.text }, modifier, subtitle, isEnabled, action, trailingContent, onValueChanged )

    @Composable
    inline fun <reified T: Enum<T>> EnumEntry(
        preference: Preferences.Enum<T>,
        @StringRes titleId: Int,
        noinline getName: @Composable (T) -> String,
        modifier: Modifier = Modifier,
        subtitle: String = "",
        isEnabled: Boolean = true,
        action: Action = Action.NONE,
        noinline trailingContent: @Composable RowScope.() -> Unit = {},
        noinline onValueChanged: (T) -> Unit = {}
    ) =
        EnumEntry( preference, stringResource( titleId ), getName, modifier, subtitle, isEnabled, action, trailingContent, onValueChanged )

    @Composable
    inline fun <reified T> EnumEntry(
        preference: Preferences.Enum<T>,
        @StringRes titleId: Int,
        modifier: Modifier = Modifier,
        subtitle: String = "",
        isEnabled: Boolean = true,
        action: Action = Action.NONE,
        noinline trailingContent: @Composable RowScope.() -> Unit = {},
        noinline onValueChanged: (T) -> Unit = {}
    ) where T: Enum<T>, T: TextView =
        EnumEntry( preference, stringResource( titleId ), modifier, subtitle, isEnabled, action, trailingContent, onValueChanged )

    @Composable
    inline fun <reified T> EnumEntry(
        preference: Preferences.Enum<T>,
        @StringRes titleId: Int,
        @StringRes subtitleId: Int,
        modifier: Modifier = Modifier,
        isEnabled: Boolean = true,
        action: Action = Action.NONE,
        noinline trailingContent: @Composable RowScope.() -> Unit = {},
        noinline onValueChanged: (T) -> Unit = {}
    ) where T: Enum<T>, T: TextView =
        EnumEntry( preference, stringResource( titleId ), modifier, stringResource( subtitleId ), isEnabled, action, trailingContent, onValueChanged )

    @Composable
    fun BooleanEntry(
        preference: Preferences.Boolean,
        title: String,
        modifier: Modifier = Modifier,
        subtitle: String = "",
        isEnabled: Boolean = true,
        action: Action = Action.NONE,
        onValueChanged: (Boolean) -> Unit = {}
    ) =
        Text(
            title = title,
            onClick = {
                onValueChanged( preference.flip() )

                if ( action == Action.RESTART_APP )
                    RestartAppDialog.showDialog()
            },
            modifier = modifier,
            subtitle = subtitle,
            isEnabled = isEnabled,
            trailingContent = { Switch( preference.value ) }
        )

    @Composable
    fun BooleanEntry(
        preference: Preferences.Boolean,
        @StringRes titleId: Int,
        modifier: Modifier = Modifier,
        subtitle: String = "",
        isEnabled: Boolean = true,
        action: Action = Action.NONE,
        onValueChanged: (Boolean) -> Unit = {}
    ) = BooleanEntry(
        preference, stringResource( titleId ), modifier, subtitle, isEnabled, action, onValueChanged
    )

    @Composable
    fun BooleanEntry(
        preference: Preferences.Boolean,
        @StringRes titleId: Int,
        @StringRes subtitleId: Int,
        modifier: Modifier = Modifier,
        isEnabled: Boolean = true,
        action: Action = Action.NONE,
        onValueChanged: (Boolean) -> Unit = {}
    ) = BooleanEntry(
        preference, stringResource( titleId ), modifier, stringResource( subtitleId ), isEnabled, action, onValueChanged
    )

    @Composable
    private fun <T> InputDialogEntry(
        preference: Preferences<T>,
        title: String,
        constraint: String,
        onValueChanged: (String) -> Unit,
        modifier: Modifier = Modifier,
        keyboardOption: KeyboardOptions = KeyboardOptions.Default,
        subtitle: String = "",
        isEnabled: Boolean = true,
        action: Action = Action.NONE,
        trailingContent: @Composable RowScope.() -> Unit = {}
    ) {
        val dialog = remember( constraint, title, keyboardOption, onValueChanged, action ) {
            SettingInputDialog(
                constraint = constraint,
                preference = preference,
                title = title,
                keyboardOption = keyboardOption,
                onValueSet = {
                    onValueChanged( it )

                    when( action ) {
                        Action.NONE                     -> { /* Does nothing */ }
                        Action.RESTART_APP              -> RestartAppDialog.showDialog()
                        Action.RESTART_PLAYER_SERVICE   -> RestartPlayerService.requestRestart()
                    }
                }
            )
        }
        dialog.Render()

        Text(
            title = title,
            onClick = dialog::showDialog,
            modifier = modifier,
            subtitle = subtitle.ifBlank { preference.value.toString() },
            isEnabled = isEnabled,
            trailingContent = trailingContent
        )
    }

    @Composable
    fun InputDialogEntry(
        preference: Preferences.String,
        title: String,
        constraint: String,
        modifier: Modifier = Modifier,
        keyboardOption: KeyboardOptions = KeyboardOptions.Default,
        subtitle: String = "",
        isEnabled: Boolean = true,
        action: Action = Action.NONE,
        trailingContent: @Composable RowScope.() -> Unit = {}
    ) =
        InputDialogEntry(
            preference, title, constraint, { preference.value = it }, modifier, keyboardOption, subtitle, isEnabled, action, trailingContent
        )

    @Composable
    fun InputDialogEntry(
        preference: Preferences.String,
        @StringRes titleId: Int,
        constraint: String,
        modifier: Modifier = Modifier,
        keyboardOption: KeyboardOptions = KeyboardOptions.Default,
        subtitle: String = "",
        isEnabled: Boolean = true,
        action: Action = Action.NONE,
        trailingContent: @Composable RowScope.() -> Unit = {}
    ) =
        InputDialogEntry(
            preference, stringResource( titleId ), constraint, { preference.value = it }, modifier, keyboardOption, subtitle, isEnabled, action, trailingContent
        )

    @Composable
    fun <T: Number> InputDialogEntry(
        preference: Preferences<T>,
        title: String,
        constraint: String,
        modifier: Modifier = Modifier,
        keyboardOption: KeyboardOptions = KeyboardOptions.Default,
        subtitle: String = "",
        isEnabled: Boolean = true,
        action: Action = Action.NONE,
        trailingContent: @Composable RowScope.() -> Unit = {}
    ) {
        fun valueProcessor( newValue: String ) {
            val toSet = newValue.ifBlank( preference.defaultValue::toString )
            when( preference ) {
                is Preferences.Int -> preference.value = toSet.toInt()
                is Preferences.Long -> preference.value = toSet.toLong()
                is Preferences.Float -> preference.value = toSet.toFloat()
                else -> throw UnsupportedOperationException(
                    "${preference::class} is not supported in <T: Number> InputDialogEntry"
                )
            }
        }

        InputDialogEntry(
            preference, title, constraint, ::valueProcessor, modifier, keyboardOption, subtitle, isEnabled, action, trailingContent
        )
    }

    @Composable
    fun <T: Number> InputDialogEntry(
        preference: Preferences<T>,
        @StringRes titleId: Int,
        constraint: String,
        modifier: Modifier = Modifier,
        keyboardOption: KeyboardOptions = KeyboardOptions.Default,
        subtitle: String = "",
        isEnabled: Boolean = true,
        action: Action = Action.NONE,
        trailingContent: @Composable RowScope.() -> Unit = {}
    ) =
        InputDialogEntry(
            preference, stringResource( titleId ), constraint, modifier, keyboardOption, subtitle, isEnabled, action, trailingContent
        )

    @ExperimentalMaterial3Api
    @Composable
    fun <T> SliderEntry(
        preference: Preferences<T>,
        title: String,
        constraints: String,
        valueRange: ClosedFloatingPointRange<Float>,
        @IntRange(from = 0) steps: Int,
        onTextDisplay: @Composable (Float) -> String,
        onValueChangeFinished: (Preferences<T>, Float) -> Unit,
        modifier: Modifier = Modifier,
        subtitle: String = "",
        isEnabled: Boolean = true,
        action: Action = Action.NONE,
        trailingContent: @Composable RowScope.() -> Unit = {}
    ) where T: Number, T: Comparable<T> =
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
                    thumbColor = colorPalette().onAccent,
                    activeTrackColor = colorPalette().accent,
                    activeTickColor = Color.Transparent,
                    inactiveTrackColor = colorPalette().text.copy( 0.75f ),
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
                   style = typography().xxs.copy( colorPalette().textSecondary )
               )

                BasicText(
                    text = onTextDisplay( realtimeValue ),
                    style = typography().xxs
                                        .copy(
                                            color = colorPalette().text,
                                            textAlign = TextAlign.Center
                                        ),
                    modifier = Modifier.weight( 1f )
                )

                BasicText(
                    text = onTextDisplay( valueRange.endInclusive ),
                    style = typography().xxs.copy( colorPalette().textSecondary )
                )
            }
        }

    @ExperimentalMaterial3Api
    @Composable
    fun <T> SliderEntry(
        preference: Preferences<T>,
        @StringRes titleId: Int,
        @StringRes subtitleId: Int,
        constraints: String,
        valueRange: ClosedFloatingPointRange<Float>,
        @IntRange(from = 0) steps: Int,
        onTextDisplay: @Composable (Float) -> String,
        onValueChangeFinished: (Preferences<T>, Float) -> Unit,
        modifier: Modifier = Modifier,
        isEnabled: Boolean = true,
        action: Action = Action.NONE,
        trailingContent: @Composable RowScope.() -> Unit = {}
    ) where T: Number, T: Comparable<T> = SliderEntry(
        preference = preference,
        title = stringResource( titleId ),
        constraints = constraints,
        valueRange = valueRange,
        steps = steps,
        onTextDisplay = onTextDisplay,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,
        subtitle = stringResource( subtitleId ),
        isEnabled = isEnabled,
        action = action,
        trailingContent = trailingContent
    )

    @Composable
    fun ColorPicker(
        preference: Preferences.Color,
        title: String,
        modifier: Modifier = Modifier,
        subtitle: String = "",
        isEnabled: Boolean = true,
        action: Action = Action.NONE
    ) {
        val dialog = remember( preference.value ) {
            ColorPickerDialog(preference.value) {
                preference.value = it
            }
        }
        dialog.Render()

        Text( title, dialog::showDialog, modifier, subtitle, isEnabled ) {
            Box(
                Modifier
                    .size(24.dp)
                    .background(dialog.color, RoundedCornerShape(3.dp))
            )
        }
    }

    @Composable
    fun ColorPicker(
        preference: Preferences.Color,
        @StringRes titleId: Int,
        modifier: Modifier = Modifier,
        subtitle: String = "",
        isEnabled: Boolean = true,
        action: Action = Action.NONE
    ) = ColorPicker( preference, stringResource( titleId ), modifier, subtitle, isEnabled, action )

    /**
     * A set of actions to enact once the setting is set.
     */
    enum class Action {

        NONE,
        RESTART_APP,
        RESTART_PLAYER_SERVICE;
    }
}