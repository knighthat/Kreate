package app.kreate.android.themed.rimusic.screen.home.onDevice

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kreate.android.R
import app.kreate.android.viewmodel.home.OnDeviceSongsViewModel
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.bold
import me.knighthat.utils.Toaster


@Composable
fun RequestMediaPermissionScreen(
    viewModel: OnDeviceSongsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (colorPalette, typography) = LocalAppearance.current

    /**
     * Opens a prompt saying that a permission (should be either [Manifest.permission.READ_MEDIA_AUDIO] or [Manifest.permission.READ_EXTERNAL_STORAGE])
     * Then apply result of that prompt.
     */
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        viewModel::onPermissionGranted
    )
    // Halt UI here to show dialog. If it was denied before,
    // this step will be skipped automatically.
    SideEffect {
        try {
            permissionLauncher.launch( OnDeviceSongsViewModel.PERMISSION )
        } catch ( e: Exception ) {
            Logger.e( "", e, "OnDeviceSongs" )
            e.message?.let( Toaster::e )
        }
    }
    /**
     * if [permissionLauncher] was denied before, it won't be shown again,
     * this launcher opens system's settings to allow user to modify the
     * app's permission.
     */
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val result = viewModel.isPermissionGranted( context )
        viewModel.onPermissionGranted( result )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                tint = colorPalette.textDisabled,
                contentDescription = null,
                modifier = Modifier.fillMaxSize( .4f )
            )

            BasicText(
                text = stringResource( R.string.media_permission_required_please_grant ),
                style = typography.m.copy( color = colorPalette.textDisabled )
            )

            Spacer( Modifier.height( 20.dp ) )

            Button(
                border = BorderStroke( 2.dp, colorPalette.accent ),
                colors = ButtonDefaults.buttonColors().copy( containerColor = Color.Transparent ),
                onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null )
                        }
                        settingsLauncher.launch( intent )
                    } catch ( e: Exception ) {
                        Logger.e( "", e, "OnDeviceSongs" )
                        e.message?.let( Toaster::e )
                    }
                }
            ) {
                BasicText(
                    text = stringResource( R.string.open_permission_settings ),
                    style = typography.l.bold.copy( color = colorPalette.accent )
                )
            }
        }
    }
}