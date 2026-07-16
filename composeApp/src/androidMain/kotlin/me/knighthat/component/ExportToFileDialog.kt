package me.knighthat.component

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import app.kreate.compose.R
import me.knighthat.component.dialog.InputDialogConstraints
import me.knighthat.component.dialog.TextInputDialog
import me.knighthat.utils.Toaster

abstract class ExportToFileDialog(
    valueState: MutableState<TextFieldValue>,
    activeState: MutableState<Boolean>,
    private val launcher: ManagedActivityResultLauncher<String, Uri?>
): TextInputDialog(InputDialogConstraints.ALL) {

    abstract val extension: String
    override val keyboardOption: KeyboardOptions = KeyboardOptions.Default
    override val allowEmpty: Boolean = true

    override var value: TextFieldValue by valueState
    override var isActive: Boolean by activeState

    /**
     * Called when input field is left empty.
     */
    abstract fun defaultFileName(): String

    override fun onSet( newValue: String ) {
        super.onSet( newValue )
        if( errorMessage.isNotEmpty() ) return

        /**
         *  Create another reference so any changes applied
         *  to this variable instead of [newValue].
         *  So that [newValue] remains immutable
         */
        val fileName = newValue.ifBlank( ::defaultFileName )

        try {
            launcher.launch( "$fileName.$extension" )
        } catch ( _: ActivityNotFoundException) {
            Toaster.e( R.string.info_not_find_app_open_doc )
        } finally { hideDialog() }
    }
}