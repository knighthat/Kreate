package app.kreate.android.themed.common.component

import androidx.annotation.FloatRange
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import app.kreate.compose.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.utils.semiBold
import me.knighthat.component.dialog.ConfirmDialog
import me.knighthat.component.dialog.Dialog
import me.knighthat.component.dialog.InputDialog
import kotlin.math.roundToInt

/**
 * A color picker used HSV as main values to display color
 */
@OptIn(ExperimentalMaterial3Api::class)
class ColorPickerDialog(
    private val initialHSV: FloatArray,
    private val onColorSelected: (Color) -> Unit
): ConfirmDialog {

    constructor(color: Color, onColorSelected: (Color) -> Unit): this(
        FloatArray(3).apply {
            android.graphics.Color.colorToHSV( color.toArgb(), this )
        },
        onColorSelected = onColorSelected
    )

    @get:FloatRange(0.0, 360.0)
    private var hue: Float by mutableFloatStateOf( initialHSV[0] )
    @get:FloatRange(0.0, 1.0)
    private var saturation: Float by mutableFloatStateOf( initialHSV[1] )
    @get:FloatRange(0.0, 1.0)
    private var value: Float by mutableFloatStateOf( initialHSV[2] )

    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.dialog_title_color_picker )

    val color: Color by derivedStateOf {
        val hsv = floatArrayOf( hue, saturation, value )
        Color(android.graphics.Color.HSVToColor( hsv ))
    }
    override var isActive: Boolean by mutableStateOf( false )

    @Composable
    private fun HueSlider() =
        Slider(
            value = hue,
            onValueChange = { hue = it.coerceIn( 0f, 360f ) },
            valueRange = 0f..360f,
            steps = 359, // smooth slider
            modifier = Modifier.fillMaxWidth()
                               .height( 30.dp ),
            track = {
                // Gradient with full hue spectrum
                val hueColors = remember {
                    listOf(
                        0f, 60f, 120f, 180f, 240f, 300f, 360f
                    ).map { h ->
                        Color(android.graphics.Color.HSVToColor( floatArrayOf(h, 1f, 1f) ))
                    }
                }

                // Custom tracker with brush (gradient) color
                Box(
                    Modifier.fillMaxWidth()
                            .height( 15.dp )
                            .background(
                                Brush.horizontalGradient( hueColors ),
                                shape = RoundedCornerShape( 2.dp )
                            )
                )
            },
            thumb = {
                Box(
                    Modifier.size( 10.dp, 24.dp )
                            .border( 2.dp, colorPalette().text, RoundedCornerShape(2.dp) )
                )
            }
        )

    @Composable
    private fun SaturationPicker() =
        BoxWithConstraints(
            Modifier.fillMaxSize( .6f )
                    .aspectRatio( 1f )      // A square
                        .pointerInput( Unit ) {
                            detectDragGestures { change, _ ->
                                val x = change.position.x.coerceIn(0f, size.width.toFloat())
                                val y = change.position.y.coerceIn(0f, size.height.toFloat())

                                saturation = x / size.width
                                value = 1f - (y / size.height)
                            }
                        }
        ) {
            val baseColor by remember { derivedStateOf {
                val hsv = floatArrayOf( hue, 1f, 1f )
                Color(android.graphics.Color.HSVToColor( hsv ))
            }}

            Canvas( Modifier.matchParentSize() ) {
                // Saturation gradient (horizontal)
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf( Color.White, baseColor )
                    )
                )

                // Value gradient (vertical, overlay)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf( Color.Transparent, Color.Black )
                    )
                )
            }

            // Putting indicator's X and Y to remember makes it go out of box
            val indicatorX = saturation * constraints.maxWidth
            val indicatorY = (1f - value) * constraints.maxHeight
            val indicatorColor by remember { derivedStateOf {
                val hsv = floatArrayOf( hue, saturation, value )
                val rgb = Color(android.graphics.Color.HSVToColor( hsv ))
                val luminance = (0.299f * rgb.red + 0.587f * rgb.green + 0.114f * rgb.blue)
                lerp( Color.White, Color.Black, luminance )
            }}

            Box(
                Modifier.offset {
                            // 16 for the size and 2 for the border
                            IntOffset(indicatorX.toInt() - 18, indicatorY.toInt() - 18)
                        }
                        .size( 16.dp )
                        .border( 2.dp, indicatorColor, CircleShape )
            )
        }

    @Composable
    private fun ValueDisplay() {
        @Composable
        fun Value( letter: Char, value: String ) =
            Row {
                BasicText(
                    text = "$letter:",
                    style = typography().s.copy( colorPalette().text, textAlign = TextAlign.Center ),
                    modifier = Modifier.weight( 1f )
                )

                BasicText(
                    text = value,
                    style = typography().m.copy( colorPalette().text ),
                    maxLines = 1,
                    modifier = Modifier.weight( 1f )
                )
            }

        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            //<editor-fold defaultstate="collapsed" desc="Display RGB">
            val (red, green, blue) = remember( color ) {
                Triple(
                    (color.red * 255).roundToInt(),
                    (color.green * 255).roundToInt(),
                    (color.blue * 255).roundToInt()
                )
            }

            Value( 'R', red.toString() )
            Value( 'G', green.toString() )
            Value( 'B', blue.toString() )
            //</editor-fold>

            Spacer( Modifier.height( 10.dp ) )

            //<editor-fold defaultstate="collapsed" desc="Display HSV">
            val (hue, saturation, value) = remember( hue, saturation, value ) {
                Triple(
                    this@ColorPickerDialog.hue.roundToInt(),
                    (this@ColorPickerDialog.saturation * 100).roundToInt(),
                    (this@ColorPickerDialog.value * 100).roundToInt()
                )
            }

            Value( 'H', hue.toString() )
            Value( 'S', saturation.toString() )
            Value( 'V', value.toString() )
            //</editor-fold>

            Spacer( Modifier.height( 10.dp ) )

            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                /**
                 * This is the value to display to user.
                 *
                 * It follows RGB so when color changes by dragging
                 * on one of the slider, this value will be updated
                 * accordingly.
                 */
                var hex by remember( red, green, blue ) {
                    val text = "%02X%02X%02X".format( red, green, blue )
                    mutableStateOf(
                        TextFieldValue(text, TextRange(text.length))
                    )
                }
                val regex = remember { Regex("^[A-Fa-f0-9]{6}\$") }
                TextField(
                    value = hex,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    onValueChange = {
                        // In case selection or cursor index changes
                        if( it.text == hex.text
                            && (it.selection != hex.selection || it.composition != hex.composition)
                        )
                            hex = it

                        // If user paste string with '#' or with spaces, it'll be removed before processing
                        val trimmed = it.text.replace( "#", "" ).trim()
                        if( regex.matches( trimmed ) ) {
                            val colorInt = "#$trimmed".toColorInt()
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV( colorInt, hsv )

                            this@ColorPickerDialog.hue = hsv[0]
                            this@ColorPickerDialog.saturation = hsv[1]
                            this@ColorPickerDialog.value = hsv[2]
                        } else if( it.text.length > 6 )
                            return@TextField
                        else
                            // Changing this TextFieldValue will not update selecting color.
                            // This separation allows user to modify HEX string freely
                            hex = it
                    },
                    leadingIcon = {
                        // Prefix "#" with reactive color (showing currently selected color)
                        BasicText(
                            text = "#",
                            style = typography().m.semiBold.copy( color )
                        )
                    },
                    colors = InputDialog.defaultTextFieldColors().copy(
                        unfocusedContainerColor = colorPalette().background1,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
        }
    }

    override fun onConfirm() {
        onColorSelected( color )

        hideDialog()
    }

    override fun hideDialog() {
        super.hideDialog()

        // Reset all values
        hue = initialHSV[0]
        saturation = initialHSV[1]
        value = initialHSV[2]
    }

    @Composable
    override fun DialogBody() {
        // TODO: Make separate UI for landscape view
        Column(
            verticalArrangement = Arrangement.spacedBy( Dialog.SPACE_BETWEEN_SECTIONS.dp ),
            modifier = Modifier.fillMaxWidth( .9f )
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy( 10.dp ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SaturationPicker()
                ValueDisplay()
            }

            HueSlider()
        }
    }
}