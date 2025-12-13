package me.knighthat.kreate.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import kreate.composeapp.generated.resources.Res
import kreate.composeapp.generated.resources.button_close_app
import me.knighthat.kreate.GlobalWindowActions
import me.knighthat.kreate.util.LocalApplicationScope
import org.jetbrains.compose.resources.stringResource


private val closeButtonColorsNormal
    @Composable
    get() = IconButtonDefaults.iconButtonColors(
        contentColor = MaterialTheme.colorScheme.outlineVariant
    )
private val closeButtonColorsHovered
    @Composable
    get() = IconButtonDefaults.iconButtonColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    )

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Button(
    imageVector: ImageVector,
    contentDescription: String,
    getColors: @Composable (Boolean) -> IconButtonColors,
    onClick: () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }

    IconButton(
        onClick = onClick,
        shape = RectangleShape,
        colors = getColors( isHovered ),
        modifier = Modifier.fillMaxWidth( .2f )
                           .onPointerEvent( PointerEventType.Enter ) { isHovered = true }
                           .onPointerEvent( PointerEventType.Exit ) { isHovered = false }
    ) {
        Icon( imageVector, contentDescription )
    }
}

@Composable
fun WindowControlButtons( modifier: Modifier = Modifier ) =
    Row(
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
                           .requiredHeight(
                               MAIN_TITLE_TYPOGRAPHY.lineHeight.value.dp
                           )
    ) {
        val application = LocalApplicationScope.current
        Button(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource( Res.string.button_close_app ),
            getColors = { if( it ) closeButtonColorsHovered else closeButtonColorsNormal },
            onClick = {
                GlobalWindowActions.closeApplication( application )
            }
        )
    }