package app.kreate.android.themed.common.component.menu

import android.content.Context
import androidx.annotation.CallSuper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.kreate.android.R
import app.kreate.android.themed.common.component.BottomMenu
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.utils.conditional
import me.knighthat.component.dialog.Dialog
import me.knighthat.component.dialog.InputDialog
import me.knighthat.component.dialog.InputDialogConstraints
import org.koin.java.KoinJavaComponent.get


abstract class TextInputDialog<T>(
    @InputDialogConstraints constraint: String
) : MenuButton<T>(), InputDialog {

    /**
     * Whether the [value] is allowed to be empty
     * when [onSet] is called.
     */
    abstract val allowEmpty: Boolean
    val constraint: Regex = Regex(constraint)
    override val dialogTitle: String
        @Composable
        get() = title

    var errorMessage: String by mutableStateOf("")
        protected set
    override var value: TextFieldValue by mutableStateOf( TextFieldValue() )
    override var isActive: Boolean by mutableStateOf( false )

    override fun onValueChanged( newValue: String ): Boolean {
        val result = newValue.matches( constraint )
        errorMessage = if( !result )
            get<Context>(Context::class.java)
                .resources
                .getString( R.string.invalid_input )
        else
            ""

        return result
    }

    @CallSuper
    override fun onSet( newValue: String ) {
        if( !allowEmpty && newValue.isEmpty() ) {
            val context: Context = get(Context::class.java)
            errorMessage = context.resources.getString( R.string.value_cannot_be_empty )
        }
    }

    @CallSuper
    override fun onClick( menu: BottomMenu, item: T ) = showDialog()

    @Composable
    override fun LeadingIcon() =
        Icon(
            painter = painterResource( iconId ),
            tint = colorPalette().text,
            // Icon not clickable
            contentDescription = null,
            modifier = Modifier.size( 20.dp )
        )

    @Composable
    override fun DialogBody() {
        TextField(
            value = value,
            onValueChange = {
                if( onValueChanged( it.text ) )
                    value = it
            },
            placeholder = { TextPlaceholder() },
            maxLines = 1,
            keyboardOptions = keyboardOption,
            leadingIcon = { LeadingIcon() },
            trailingIcon = { TrailingIcon() },
            modifier = Modifier.fillMaxWidth( .9f )
                               .conditional( keyboardOption.keyboardType == KeyboardType.Password
                                       || keyboardOption.keyboardType == KeyboardType.NumberPassword
                               ) {
                                   semantics { password() }
                               },
            colors = InputDialog.defaultTextFieldColors()
                                .copy(
                                    errorTextColor = colorPalette().text,
                                    errorContainerColor = colorPalette().background1,
                                    errorIndicatorColor = Color.Red
                                ),
            isError = errorMessage.isNotEmpty()
        )

        AnimatedVisibility(
            visible = errorMessage.isNotEmpty(),
            enter = fadeIn() + slideInHorizontally() + expandIn(),
        ) {
            BasicText(
                text = errorMessage,
                style = typography().xs.copy( color = Color(android.graphics.Color.RED) ),
                modifier = Modifier.fillMaxWidth( .7f )
                                   .padding( top = Dialog.VERTICAL_PADDING.dp )
            )
        }
    }
}