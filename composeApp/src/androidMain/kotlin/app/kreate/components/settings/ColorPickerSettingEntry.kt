package app.kreate.components.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.android.ColorPickerActivity
import app.kreate.components.settings.SettingComponents.Action
import app.kreate.preferences.Preferences
import it.fast4x.rimusic.ui.styling.LocalAppearance


@Composable
fun SettingComponents.ColorPickerEntry(
    preference: Preferences.ColorPref,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    action: Action = Action.NONE
) {
    val context = LocalContext.current
    val colorPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Don't set if no color returned from the activity
        if( result.resultCode != Activity.RESULT_OK ) return@rememberLauncherForActivityResult

        // Retrieve the Long value and cast safely back into a Compose Color object
        result.data
              ?.getLongExtra(
                  ColorPickerActivity.EXTRA_RESULT_COLOR,
                  preference.value.value.toLong()
              )
              ?.toULong()
              ?.let( ::Color )
              ?.also( preference::update )
    }
    val color by preference.collectAsStateWithLifecycle()
    val backgroundColor = LocalAppearance.current.colorPalette.background0

    Entry(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        trailingContent = {
            Box(
                Modifier.size( 24.dp )
                        .background( color, RoundedCornerShape(8.dp) )
            )
        },
        onClick = {
            // Setup intent, passing our initial color value converted to Long
            val intent = Intent(context, ColorPickerActivity::class.java)
            intent.putExtra(
                ColorPickerActivity.EXTRA_INIT_COLOR,
                color.value.toLong()
            )
            intent.putExtra(
                ColorPickerActivity.BACKGROUND_COLOR,
                backgroundColor.value.toLong()
            )

            colorPickerLauncher.launch( intent )
        }
    )
}