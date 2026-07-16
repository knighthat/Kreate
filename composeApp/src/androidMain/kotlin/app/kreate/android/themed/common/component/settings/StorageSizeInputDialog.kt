package app.kreate.android.themed.common.component.settings

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.text.isDigitsOnly
import app.kreate.compose.R
import app.kreate.preferences.Preferences
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.isAtLeastAndroid8
import me.knighthat.component.dialog.Dialog
import me.knighthat.component.dialog.TextInputDialog
import org.intellij.lang.annotations.MagicConstant
import java.math.BigInteger
import kotlin.math.roundToInt

class StorageSizeInputDialog(
    constraint: String,
    currentValue: Long,
    private val context: Context,
    private val preference: Preferences.LongPref,
    private val title: String,
    private val onConfirm: () -> Unit
) : TextInputDialog(constraint) {

    companion object {
        val units: List<String> = listOf( "B", "KB", "MB", "GB", "TB" )
        val presets: List<Pair<Int, String>> = listOf(
            512 to units[2],        // 512MB
            1 to units[3],          // 1GB
            2 to units[3],          // 2GB
            4 to units[3],          // 4GB
            8 to units[3],          // 8GB
            16 to units[3],         // 16GB
            32 to units[3],         // 32GB
            64 to units[3],         // 64GB
            128 to units[3]         // 128GB
        )
    }

    override val keyboardOption: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    override val dialogTitle: String
        @Composable
        get() = title

    @get:MagicConstant(stringValues = ["B", "KB", "MB", "GB", "TB"])
    var unit: String by mutableStateOf( "B" )
        private set
    override var value: TextFieldValue by mutableStateOf(TextFieldValue(currentValue.toString()) )
    override var isActive: Boolean by mutableStateOf( false )

    override fun onSet( newValue: String ) {
        super.onSet(newValue)
        if( errorMessage.isNotBlank() )
            return

        if( newValue == context.getString( R.string.unlimited ) )
            preference.update( Long.MAX_VALUE )
        else if( newValue == context.getString( R.string.vt_disabled ) )
            preference.update( 0L )
        else if( newValue.isDigitsOnly() ) {
            // https://developer.android.com/reference/android/text/format/Formatter#formatFileSize(android.content.Context,%20long)
            val unitSystem = if( isAtLeastAndroid8 ) 1000L else 1024L
            // Take the value multiply 1024 powered to whatever
            // current unit is to "B"
            BigInteger.valueOf( unitSystem )
                      .pow( units.indexOf( unit ) )
                      .multiply( BigInteger(newValue) )
                      .also {
                          if( it < BigInteger.ZERO || it > BigInteger.valueOf( Long.MAX_VALUE ) )
                              errorMessage = context.getString( R.string.invalid_input )
                          else
                              preference.update( it.toLong() )
                      }
        } else
            errorMessage = context.getString( R.string.invalid_input )

        if( errorMessage.isBlank() )
            onConfirm.invoke()
    }

    @Composable
    override fun LeadingIcon() =
        Icon(
            painter = painterResource( R.drawable.server ),
            contentDescription = null,
            tint = LocalAppearance.current.colorPalette.text,
            modifier = Modifier.requiredSize( 20.dp )
        )

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun TrailingIcon() {
        val colorPalette = LocalAppearance.current.colorPalette
        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            TextField(
                modifier = Modifier.menuAnchor( MenuAnchorType.PrimaryNotEditable )
                                   .fillMaxWidth( .3f ),
                readOnly = true,
                value = unit,
                onValueChange = {},
                trailingIcon = {
                    val direction by animateFloatAsState(
                        targetValue = if (expanded) 0f else 180f,
                        label = "",
                        animationSpec = tween(easing = LinearEasing)
                    )

                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.rotate( direction )
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = colorPalette.text,
                    unfocusedTextColor = colorPalette.textDisabled,
                    // Hide indicator when menu is open
                    focusedContainerColor = if( expanded ) colorPalette.background0 else Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = colorPalette.background0
            ) {
                units.forEach { selectionOption ->
                    if( selectionOption == unit ) return@forEach

                    DropdownMenuItem(
                        text = { Text(selectionOption) },
                        onClick = {
                            unit = selectionOption
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        colors = MenuDefaults.itemColors()
                                             .copy( colorPalette.text )
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun DialogBody() {
        super.DialogBody()

        Spacer( Modifier.height(Dialog.SPACE_BETWEEN_SECTIONS.dp) )

        var realtimeValue by remember {
            mutableFloatStateOf(
                when( preference.value ) {
                    0L              -> 0f
                    Long.MAX_VALUE  -> 10f
                    else            -> preference.value.toFloat() / Long.MAX_VALUE
                }
            )
        }

        val colorPalette = LocalAppearance.current.colorPalette
        Slider(
            value = realtimeValue,
            onValueChange = { realtimeValue = it },
            steps = 9,
            valueRange = 0f..10f,
            modifier = Modifier.fillMaxWidth( .9f )
                               .height( 20.dp ),
            colors = SliderDefaults.colors().copy(
                thumbColor = colorPalette.accent,
                activeTrackColor = colorPalette.accent.copy( .8f ),
                activeTickColor = Color.Transparent,
                inactiveTrackColor = colorPalette.background1,
                inactiveTickColor = colorPalette.textSecondary
            ),
            onValueChangeFinished = {
                this.value = when( val valueInt = realtimeValue.roundToInt()  ) {
                    0 -> TextFieldValue(context.getString(R.string.vt_disabled) )
                    1,2,3,4,5,6,7,8,9 -> {
                        val (value, unitStr) = presets[valueInt - 1]
                        this.unit = unitStr
                        TextFieldValue( value.toString() )
                    }
                    10 -> TextFieldValue( context.getString(R.string.unlimited) )
                    else -> throw IllegalStateException("value must be a number between 0 and 10 inclusively")
                }
            }
        )
    }
}