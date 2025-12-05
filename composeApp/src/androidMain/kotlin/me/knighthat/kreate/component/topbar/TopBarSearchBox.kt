package me.knighthat.kreate.component.topbar

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import me.knighthat.kreate.constant.Route


@Composable
fun TopBarSearchBox(
    value: TextFieldValue,
    onSearch: KeyboardActionScope.() -> Unit,
    modifier: Modifier = Modifier,
    onValueChange: (TextFieldValue) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val isShowingResults = Route.Search.isNotHere

    TextField(
        value = value,
        singleLine = true,
        readOnly = isShowingResults,
        shape = MaterialTheme.shapes.small,
        textStyle = MaterialTheme.typography.headlineSmall,
        colors = TextFieldDefaults.colors().copy(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedIndicatorColor = if( isShowingResults ) Color.Transparent else MaterialTheme.colorScheme.primary
        ),
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = onSearch
        ),
        modifier = modifier.focusRequester( focusRequester )
                           .fillMaxWidth()
    )

    LaunchedEffect( Unit ) {
        focusRequester.requestFocus()
    }
}