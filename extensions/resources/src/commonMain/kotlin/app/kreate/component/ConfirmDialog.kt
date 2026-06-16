package app.kreate.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import it.fast4x.rimusic.ui.styling.ColorPalette
import it.fast4x.rimusic.ui.styling.LocalAppearance
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.button_cancel
import kreate.resources.generated.resources.button_confirm
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource


@Composable
fun DialogConfirmButton(
    modifier: Modifier = Modifier,
    colorPalette: ColorPalette = LocalAppearance.current.colorPalette,
    enabled: Boolean = true,
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
) =
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10),
        enabled = enabled,
        modifier = modifier,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        colors = ButtonDefaults.textButtonColors(
            containerColor = colorPalette.accent,
            contentColor = colorPalette.onAccent
        )
    ) {
        Text(
            text = stringResource( Res.string.button_confirm ),
            fontWeight = FontWeight.Bold
        )
    }

@Composable
fun DialogCancelButton(
    modifier: Modifier = Modifier,
    colorPalette: ColorPalette = LocalAppearance.current.colorPalette,
    enabled: Boolean = true,
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
) =
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(10),
        colors = ButtonDefaults.textButtonColors(
            contentColor = colorPalette.red,
            disabledContentColor = colorPalette.textDisabled
        )
    ) {
        Text( stringResource(Res.string.button_cancel) )
    }

@Composable
fun ConfirmDialog(
    onDismissRequest: () -> Unit,
    onConfirmRequest: () -> Unit,
    modifier: Modifier = Modifier,
    icon: DrawableResource? = null,
    title: String? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    colorPalette: ColorPalette = LocalAppearance.current.colorPalette,
    containerColor: Color = colorPalette.background0,
    iconContentColor: Color = colorPalette.accent,
    titleContentColor: Color = colorPalette.text,
    textContentColor: Color = colorPalette.textSecondary,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) =
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
        text = text,
        shape = shape,
        confirmButton = {
            DialogConfirmButton(
                onClick = onConfirmRequest,
                colorPalette = colorPalette
            )
        },
        dismissButton = {
            DialogCancelButton(
                onClick = onDismissRequest,
                colorPalette = colorPalette
            )
        },
        icon = icon?.let { icon ->
            {
                Icon(
                    painter = painterResource( icon ),
                    // Not clickable
                    contentDescription =  null
                )
            }
        },
        title = title?.let { title ->
            {
                val typography = LocalAppearance.current.typography

                Text(
                    text = title,
                    fontSize = typography.l.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
            }
        }
    )

@Composable
fun ConfirmDialog(
    onDismissRequest: () -> Unit,
    onConfirmRequest: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    icon: DrawableResource? = null,
    title: String? = null,
    shape: Shape = AlertDialogDefaults.shape,
    colorPalette: ColorPalette = LocalAppearance.current.colorPalette,
    containerColor: Color = colorPalette.background0,
    iconContentColor: Color = colorPalette.accent,
    titleContentColor: Color = colorPalette.text,
    textContentColor: Color = colorPalette.textSecondary,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) =
    ConfirmDialog(
        onDismissRequest = onDismissRequest,
        onConfirmRequest = onConfirmRequest,
        modifier = modifier,
        icon = icon,
        title = title,
        text = {
            val typography = LocalAppearance.current.typography

            Text(
                text = text,
                fontSize = typography.xs.fontSize,
                maxLines = 3
            )
        },
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )