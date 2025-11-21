package app.kreate.android.themed.common

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.kreate.android.R
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.utils.isLocalFile
import coil3.request.SuccessResult
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.component.dialog.InputDialogConstraints
import me.knighthat.component.dialog.TextInputDialog
import me.knighthat.utils.Toaster
import timber.log.Timber


abstract class ChangeThumbnail(
    private val context: Context
): TextInputDialog(InputDialogConstraints.ALL), MenuIcon, Descriptive {

    override val keyboardOption: KeyboardOptions = KeyboardOptions.Default
    override val iconId: Int = R.drawable.cover_edit
    override val messageId: Int = R.string.description_change_thumbnail
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.title_change_thumbnail )
    override val menuIconTitle: String
        @Composable
        get() = dialogTitle

    protected var uri: Uri by mutableStateOf("".toUri())

    private suspend fun validateUrl( url: String ): Boolean {
        val request = ImageFactory.requestBuilder( url )
        val result = ImageFactory.imageLoader.execute( request )

        if( result !is SuccessResult )
            withContext(Dispatchers.Main) {
                errorMessage = context.getString( R.string.error_invalid_path )
            }

        return result is SuccessResult
    }

    protected abstract fun setThumbnail( url: String )

    override fun onShortClick() = showDialog()

    override fun hideDialog() {
        super.hideDialog()
        uri = "".toUri()
        value = TextFieldValue()
    }

    @Composable
    override fun LeadingIcon() {
        if( uri.toString().isNotBlank() )
            ImageFactory.AsyncImage(
                thumbnailUrl = uri.toString(),
                modifier = Modifier.size( 40.dp )
            )
    }

    @Composable
    override fun TrailingIcon() {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            // [uri] must be non-null (meaning path exists) in order to work
            if( uri == null
                // Must contains path, even if it's a domain name,
                // if there's no path to image, it's not valid
                || uri.path.isNullOrEmpty()
                // Only accept local file
                || !uri.isLocalFile()
            ) {
                errorMessage = context.getString( R.string.error_invalid_path )
                return@rememberLauncherForActivityResult
            } else
                errorMessage = ""

            // Without this step, coil3 can't read the image with `content:///` scheme
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission( uri, flags )

            this.uri = uri
            this.value = TextFieldValue()
        }

        Icon(
            painter = painterResource( R.drawable.folder ),
            tint = colorPalette().text,
            contentDescription = stringResource( R.string.description_select_from_device ),
            modifier = Modifier.clip(CircleShape )
                               .clickable {
                                   try {
                                       launcher.launch(
                                           PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                       )
                                   } catch( e: Exception ) {
                                       Timber.tag("ChangeThumbnail").e( e )
                                       Toaster.e( R.string.error_failed_to_load_image )
                                   }
                               }
        )
    }

    @SuppressLint("MissingSuperCall")       // Handled differently
    override fun onSet(newValue: String ) {
        if( uri.toString().isBlank() && newValue.isBlank() ) {
            errorMessage = appContext().resources.getString( R.string.value_cannot_be_empty )
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            if( newValue.isNotBlank() && !validateUrl( newValue ) )
                return@launch

            val url = newValue.ifBlank { uri.toString() }
            setThumbnail( url )
            Toaster.done()

            withContext( Dispatchers.Main ) {
                hideDialog()
            }
        }
    }
}