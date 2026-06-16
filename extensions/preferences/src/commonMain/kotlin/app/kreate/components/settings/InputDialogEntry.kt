package app.kreate.components.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.kreate.components.settings.SettingComponents.Action
import app.kreate.util.awaitFrame
import it.fast4x.rimusic.ui.styling.LocalAppearance
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.error_empty_input
import kreate.resources.generated.resources.error_input_invalid
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource


@Composable
fun SettingComponents.InputDialogEntry(
    title: String,
    constraint: Regex,
    onConfirmRequest: (TextFieldState) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    state: TextFieldState = rememberTextFieldState( "" ),
    icon: DrawableResource? = null,
    placeholder: String = "",
    enabled: Boolean = true,
    action: Action = Action.NONE,
    properties: InputDialogProperties = InputDialogProperties.default,
    onKeyboardAction: KeyboardActionHandler = KeyboardActionHandler { onConfirmRequest(state) },
    trailingContent: @Composable () -> Unit = {},
) {
    var isDialogVisible by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    Entry(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        enabled = enabled,
        onClick = { isDialogVisible = true },
        trailingContent = trailingContent
    )

    if( isDialogVisible )
        AlertDialog(
            onDismissRequest = { isDialogVisible = false },
            confirmButton = {
                ConfirmButton {
                    isDialogVisible = false

                    if( !errorMessage.isNullOrBlank() || !constraint.matches(state.text) )
                        return@ConfirmButton

                    onConfirmRequest( state )
                }
            },
            dismissButton = {
                CancelButton { isDialogVisible = false }
            },
            icon = {
                if( icon == null ) return@AlertDialog

                Icon(
                    painter = painterResource( icon ),
                    // Not clickable
                    contentDescription = null
                )
            },
            text = {
                val focusRequester = remember { FocusRequester() }

                OutlinedTextField(
                    state = state,
                    isError = !errorMessage.isNullOrBlank(),
                    lineLimits = properties.lineLimits,
                    modifier = Modifier.focusRequester( focusRequester )
                                       .width( 350.dp ),
                    keyboardOptions = properties.keyboardOptions,
                    onKeyboardAction = onKeyboardAction,
                    label = { Text(title) },
                    placeholder = { Text(placeholder) },
                    supportingText = {
                        AnimatedVisibility(
                            visible = !errorMessage.isNullOrBlank(),
                            enter = slideInVertically { -it },
                            exit = slideOutVertically { -it },
                            label = "error_message"
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                color = properties.textFieldColors.errorTextColor
                            )
                        }
                    },
                    colors = properties.textFieldColors
                )

                LaunchedEffect( Unit ) {
                    awaitFrame()
                    focusRequester.requestFocus()
                }
            },
            shape = properties.shape,
            containerColor = properties.containerColor,
            iconContentColor = properties.iconContentColor,
            titleContentColor = properties.titleContentColor,
            textContentColor = Color.Transparent,
            tonalElevation = properties.tonalElevation,
            properties = properties.properties
        )

    LaunchedEffect( state.text ) {
        errorMessage =
            if( state.text.isBlank() && !properties.allowEmpty )
                getString( Res.string.error_empty_input )
            else if( !constraint.matches(state.text) )
                getString( Res.string.error_input_invalid )
            else
                null
    }
}

@Immutable
data class InputDialogProperties(
    val shape: Shape,
    val containerColor: Color,
    val iconContentColor: Color,
    val titleContentColor: Color,
    val textFieldColors: TextFieldColors,
    val tonalElevation: Dp,
    val properties: DialogProperties,
    val keyboardOptions: KeyboardOptions,
    val lineLimits: TextFieldLineLimits,
    val allowEmpty: Boolean
) {

    companion object {

        val default: InputDialogProperties
            @Composable
            get() {
                val colorPalette = LocalAppearance.current.colorPalette

                return InputDialogProperties(
                    shape = AlertDialogDefaults.shape,
                    containerColor = colorPalette.background0,
                    iconContentColor = colorPalette.text,
                    titleContentColor = colorPalette.text,
                    textFieldColors = TextFieldDefaults.colors(
                        //<editor-fold desc="Text">
                        focusedTextColor = colorPalette.text,
                        unfocusedTextColor = colorPalette.textDisabled,
                        errorTextColor = colorPalette.red,
                        //</editor-fold>
                        //<editor-fold desc="Container">
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                        //</editor-fold>
                        //<editor-fold desc="Indicator">
                        focusedIndicatorColor = colorPalette.accent,
                        unfocusedIndicatorColor = colorPalette.textDisabled,
                        errorIndicatorColor = colorPalette.red,
                        //</editor-fold>
                        //<editor-fold desc="Label">
                        focusedLabelColor = colorPalette.onAccent,
                        unfocusedLabelColor = colorPalette.textDisabled,
                        errorLabelColor = colorPalette.red,
                        //</editor-fold>
                        //<editor-fold desc="Placeholder">
                        focusedPlaceholderColor = colorPalette.textSecondary,
                        unfocusedPlaceholderColor = colorPalette.textSecondary,
                        errorPlaceholderColor = colorPalette.textSecondary,
                        //</editor-fold>
                    ),
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                    properties = DialogProperties(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Unspecified,
                        imeAction = ImeAction.Done
                    ),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    allowEmpty = false
                )
            }
    }
}