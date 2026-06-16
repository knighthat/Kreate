package app.kreate.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.AlphaSlider
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.check
import kreate.resources.generated.resources.close
import org.jetbrains.compose.resources.painterResource


class ColorPickerActivity : ComponentActivity() {

    companion object {

        const val EXTRA_INIT_COLOR = "extra_init_color"
        const val EXTRA_RESULT_COLOR = "extra_result_color"
        const val BACKGROUND_COLOR = "background_color"
    }

    // Pack the color as a Long and close the activity successfully
    private fun returnColorResult( color: Color ) {
        val resultIntent = Intent().apply {
            putExtra( EXTRA_RESULT_COLOR, color.value.toLong() )
        }
        setResult( RESULT_OK, resultIntent )
        finish()
    }

    override fun onCreate( savedInstanceState: Bundle? ) {
        super.onCreate( savedInstanceState )

        val initialColorLong = intent.getLongExtra( EXTRA_INIT_COLOR, Color.White.value.toLong() )
        val initialColor = Color(initialColorLong.toULong())
        val backgroundColorLong = intent.getLongExtra( BACKGROUND_COLOR, Color.Black.value.toLong() )
        val backgroundColor = Color(backgroundColorLong.toULong())

        setContent {
            val controller = rememberColorPickerController()
            val padding = WindowInsets.safeContent.asPaddingValues()
            var selectedColor by remember { mutableStateOf(initialColor) }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy( 32.dp ),
                modifier = Modifier.background( backgroundColor )
                                   .padding( padding )
                                   .fillMaxSize()
            ) {
                // Color Picker Wheel (Flexes to fill remaining space)
                HsvColorPicker(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight( .5f ),
                    controller = controller,
                    initialColor = initialColor,
                    onColorChanged = { colorEnvelope ->
                        selectedColor = colorEnvelope.color
                    }
                )

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { finish() }
                        ) {
                            Icon(
                                painter = painterResource( Res.drawable.close ),
                                contentDescription = null,
                                tint = Color(0xffc62828),
                                modifier = Modifier.size( 64.dp )
                            )
                        }
                    }

                    // Color Preview Box
                    Box(
                        Modifier.size( 70.dp )
                                .clip( RoundedCornerShape(12.dp) )
                                .background( selectedColor )
                    )

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { returnColorResult(selectedColor) }
                        ) {
                            Icon(
                                painter = painterResource( Res.drawable.check ),
                                contentDescription = null,
                                tint = Color(0xff10b981),
                                modifier = Modifier.size( 64.dp )
                            )
                        }
                    }
                }

                // Control Sliders
                BrightnessSlider(
                    modifier = Modifier.fillMaxWidth().height( 35.dp ),
                    controller = controller
                )
                AlphaSlider(
                    modifier = Modifier.fillMaxWidth().height( 35.dp ),
                    controller = controller
                )
            }
        }
    }
}