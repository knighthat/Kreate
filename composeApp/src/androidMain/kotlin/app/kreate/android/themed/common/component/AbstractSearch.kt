package app.kreate.android.themed.common.component

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kreate.android.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.utils.conditional
import it.fast4x.rimusic.utils.secondary
import it.fast4x.rimusic.utils.semiBold

abstract class AbstractSearch(
    textFieldValue: TextFieldValue = TextFieldValue(),
    isVisible: Boolean = false,
    isFocused: Boolean = false
) {

    companion object {
        const val DECO_BOX_ICON_SIZE = 16
        const val DECO_BOX_ITEM_SPACING = 7
        const val SEARCH_BOX_HEIGHT = 30
    }

    var input: TextFieldValue by mutableStateOf( textFieldValue )
    var isVisible: Boolean by mutableStateOf( isVisible )
    var isFocused: Boolean by mutableStateOf( isFocused )

    @Composable
    protected abstract fun DecorationBox( innerTextField: @Composable () -> Unit )

    @Composable
    protected fun Placeholder() =
        BasicText(
            text = stringResource( android.R.string.search_go ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = typography().xs
                                .semiBold
                                .secondary
                                .copy(
                                    color = colorPalette().textDisabled
                                ),
            // Hide placeholder once text from user appears
            modifier = Modifier.conditional( isNotBlank() ) { alpha( 0f ) }
        )

    @Composable
    protected fun ClearSearchButton( modifier: Modifier = Modifier ) =
        Icon(
            painter = painterResource( R.drawable.backspace_outline ),
            contentDescription = stringResource( R.string.clear ),
            tint = colorPalette().text
                                 .copy( alpha = .8f ),     // A little dimmer to prevent eye-candy
            modifier = Modifier.size( DECO_BOX_ICON_SIZE.dp )
                               .clickable( onClick = ::onClearSearchClick )
        )

    protected fun onClearSearchClick() {
        input = TextFieldValue( "" )

        // Regain focus just in case
        isFocused = true
    }

    @Composable
    open fun SearchBar( modifier: Modifier ) {
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current

        // Auto focus on search bar when it's visible
        LaunchedEffect( isVisible, isFocused ) {
            if( !isVisible ) return@LaunchedEffect

            if( isFocused )
                focusRequester.requestFocus()
            else {
                keyboardController?.hide()
                focusManager.clearFocus()
            }
        }

        BasicTextField(
            value = input,
            onValueChange = {
                input = it.copy(
                    text = it.text,
                    selection = it.selection
                )
            },
            textStyle = typography().xs.semiBold,
            singleLine = true,
            keyboardOptions = KeyboardOptions( imeAction = ImeAction.Search ),
            keyboardActions = KeyboardActions(onSearch = {
                isVisible = isNotBlank()
                isFocused = false
                keyboardController?.hide()
            }),
            cursorBrush = SolidColor( colorPalette().text ),
            decorationBox = { DecorationBox( it ) },
            modifier = modifier.height( SEARCH_BOX_HEIGHT.dp )
                               .fillMaxWidth()
                               .focusRequester( focusRequester )
        )
    }

    @Composable
    fun SearchBar() = SearchBar( Modifier )

    fun isBlank() = input.text.isBlank()

    fun isNotBlank() = input.text.isNotBlank()

    /**
     * @return whether current searching value appears in provided [text].
     */
    infix fun appearsIn( text: String ) = isBlank() || text.contains( input.text, true )

    /**
     * Support for localization
     *
     * @return whether current searching value appears in provided localized text with [textId].
     */
    @Composable
    infix fun appearsIn( @StringRes textId: Int ) = this appearsIn stringResource( textId )
}