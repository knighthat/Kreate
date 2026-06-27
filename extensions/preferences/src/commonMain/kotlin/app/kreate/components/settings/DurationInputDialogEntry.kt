package app.kreate.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.component.ConfirmDialog
import app.kreate.preferences.Preferences
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.ui.styling.LocalAppearance
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.hour
import kreate.resources.generated.resources.minute
import kreate.resources.generated.resources.second
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.pluralStringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


@Composable
private fun TimeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    range: IntRange,
    unit: PluralStringResource,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next
) {
    val (colorPalette, typography) = LocalAppearance.current
    val focusRequester = remember { FocusRequester() }
    var field by rememberSaveable( stateSaver = TextFieldValue.Saver ) {
        mutableStateOf( TextFieldValue(value, TextRange(2)) )
    }
    var isError by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = field,
        singleLine = true,
        isError = isError,
        onValueChange = { input ->
            // Keep digits only
            val digits = input.text.filter { it.isDigit() }
            val paddedValue = digits.takeLast( 2 ).padStart( 2, '0' )

            if( paddedValue.toIntOrNull()?.let(range::contains) == true ) {
                isError = false
                field = TextFieldValue(paddedValue, TextRange(2))
                onValueChange( paddedValue )
            } else
                isError = true
        },
        textStyle = LocalTextStyle.current.copy(
            fontSize = 28.sp,
            textAlign = TextAlign.Center
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = imeAction
        ),
        supportingText = {
            val quantity = field.text.toIntOrNull() ?: 0

            Text(
                text = pluralStringResource( unit, quantity ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = typography.xxxs.fontSize,
                    maxFontSize = typography.xs.fontSize
                )
            )
        },
        colors = TextFieldDefaults.colors(
            focusedTextColor = colorPalette.text,
            unfocusedTextColor = colorPalette.textDisabled,
            errorTextColor = colorPalette.red,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
            focusedIndicatorColor = colorPalette.accent,
            unfocusedIndicatorColor = colorPalette.textDisabled,
            errorIndicatorColor = colorPalette.red,
            focusedSupportingTextColor = colorPalette.textSecondary,
            unfocusedSupportingTextColor = colorPalette.textSecondary,
            errorSupportingTextColor = colorPalette.textSecondary,
        ),
        modifier = modifier.width( 80.dp )
                           .focusRequester( focusRequester )
                           .onFocusChanged {
                               if( !it.isFocused ) isError = false
                           }
    )

    var cancelErrorJob: Job? by remember { mutableStateOf(null) }
    LaunchedEffect( isError ) {
        cancelErrorJob?.cancel()
        cancelErrorJob =
            if( isError )
                launch {
                    // Disable error after a while
                    delay( 2.seconds )
                    isError = false
                }
            else
                null
    }
}

@Composable
private fun TimeSeparator( modifier: Modifier = Modifier ) =
    Text( text = ":", fontSize = 28.sp, modifier = modifier.padding(bottom = 24.dp) )

@Composable
private fun TimeInputWithSeconds(
    onDismissRequest: () -> Unit,
    onConfirmRequest: (Duration) -> Unit,
    initValue: Duration,
    title: String,
    modifier: Modifier = Modifier,
    icon: DrawableResource? = null,
) {
    var hour by rememberSaveable { mutableStateOf("00") }
    var minute by rememberSaveable { mutableStateOf("00") }
    var second by rememberSaveable { mutableStateOf("00") }

    ConfirmDialog(
        onDismissRequest = onDismissRequest,
        onConfirmRequest = {
            onDismissRequest()

            try {
                val duration = hour.toInt().hours + minute.toInt().minutes + second.toInt().seconds
                onConfirmRequest( duration )
            } catch( err: NumberFormatException ) {
                Logger.e( "", err, "DurationInputDialog" )
            }
        },
        icon = icon,
        title = title,
        text = {
            Row(
                modifier = Modifier.height( 100.dp ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy( 8.dp )
            ) {
                // Hour Field
                TimeTextField(
                    value = hour,
                    range = IntRange(0, 23),
                    unit = Res.plurals.hour,
                    onValueChange = { newValue ->
                        hour = newValue
                    }
                )

                TimeSeparator()

                // Minute Field
                TimeTextField(
                    value = minute,
                    range = IntRange(0, 59),
                    unit = Res.plurals.minute,
                    onValueChange = { newValue ->
                        minute = newValue
                    }
                )

                TimeSeparator()

                // Second Field
                TimeTextField(
                    value = second,
                    range = IntRange(0, 59),
                    unit = Res.plurals.second,
                    imeAction = ImeAction.Done,
                    onValueChange = { newValue ->
                        second = newValue
                    }
                )
            }
        },
        modifier = modifier
    )

    LaunchedEffect( initValue ) {
        initValue.toComponents { hours, minutes, seconds, _ ->
            hour = hours.coerceIn( 0L, 23L ).toString().padStart( 2, '0' )
            minute = minutes.coerceIn( 0, 59 ).toString().padStart( 2, '0' )
            second = seconds.coerceIn( 0, 59 ).toString().padStart( 2, '0' )
        }
    }
}

@Composable
fun SettingComponents.DurationEntry(
    preference: Preferences.DurationPref,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    trailingContent: @Composable () -> Unit = {}
) {
    val value by preference.collectAsStateWithLifecycle()
    var isDialogVisible by rememberSaveable { mutableStateOf(false) }

    Entry(
        onClick = {
            isDialogVisible = true
        },
        title = title,
        modifier = modifier,
        subtitle = subtitle ?: value.toString(),
        enabled = enabled,
        trailingContent = trailingContent
    )

    if( isDialogVisible )
        TimeInputWithSeconds(
            onDismissRequest = { isDialogVisible = false },
            onConfirmRequest = preference::update,
            initValue = value,
            title = title
        )
}