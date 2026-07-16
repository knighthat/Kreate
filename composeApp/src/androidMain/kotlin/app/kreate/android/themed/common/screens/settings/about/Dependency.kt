package app.kreate.android.themed.common.screens.settings.about

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kreate.compose.R
import app.kreate.util.scrollingText
import it.fast4x.rimusic.ui.styling.ColorPalette
import it.fast4x.rimusic.ui.styling.Typography
import it.fast4x.rimusic.utils.bold
import kotlinx.serialization.Serializable
import me.knighthat.utils.Toaster

@Serializable
@Immutable
data class Dependency(
    val moduleName: String,
    val moduleVersion: String,
    val moduleUrl: String? = null,
    val moduleLicense: String? = null,
    val moduleLicenseUrl: String? = null
) {

    @Composable
    private fun InteractiveIcon(
        @DrawableRes iconId: Int,
        contentDescription: String,
        tint: Color,
        modifier: Modifier = Modifier,
        sizeDp: Dp = 30.dp,
        onClick: () -> Unit
    ) =
        Icon(
            painter = painterResource( iconId ),
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier.size( sizeDp )
                               .padding( all = 5.dp )
                               .combinedClickable(
                                   onClick = onClick,
                                   onLongClick = {
                                       Toaster.i( contentDescription )
                                   }
                               )
        )

    @Composable
    private fun AnnotatedString(
        sectionTitle: String,
        text: String,
        colorPalette: ColorPalette,
        typography: Typography,
        modifier: Modifier = Modifier
    ) {
        val annotatedString = buildAnnotatedString {
            val style = SpanStyle(
                fontWeight = FontWeight.Bold,
                color = colorPalette.text
            )
            withStyle( style = style ) {
                append( "$sectionTitle:" )
            }
            append( " $text" )
        }

        Text(
            text = annotatedString,
            style = typography.xxs,
            color = colorPalette.textDisabled,
            modifier = modifier.padding( top = 3.dp )
                               .scrollingText()
        )
    }


    @Composable
    fun Draw(
        colorPalette: ColorPalette,
        typography: Typography,
        uriHandler: UriHandler,
        modifier: Modifier = Modifier
    ) =
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy( 10.dp ),
            modifier = modifier.fillMaxWidth( .9f )
                               .border(
                                   width = 2.dp,
                                   color = colorPalette.textDisabled,
                                   shape = RoundedCornerShape(5.dp)
                               )
                               .padding( all = 10.dp )
        ) {
            Column(
                Modifier.weight( 1f )
            ) {
                // Title
                Text(
                    text = moduleName,
                    style = typography.xs.bold,
                    color = colorPalette.text
                )

                // Url to module
                moduleUrl?.also { url ->
                    Text(
                        text = url,
                        style = typography.xxxs,
                        color = colorPalette.textDisabled,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                AnnotatedString(
                    sectionTitle = stringResource( R.string.word_version ),
                    text = moduleVersion,
                    colorPalette = colorPalette,
                    typography = typography,
                    modifier = Modifier.padding( top = 5.dp )
                )

                moduleLicense?.also { license ->
                    AnnotatedString(
                        sectionTitle = stringResource( R.string.word_license ),
                        text = license,
                        colorPalette = colorPalette,
                        typography = typography
                    )
                }
            }

            moduleLicenseUrl?.also {
                InteractiveIcon(
                    iconId = R.drawable.newspaper_outline,
                    contentDescription = stringResource( R.string.view_license ),
                    tint = colorPalette.text
                ) {
                    uriHandler.openUri( it )
                }

            }

            moduleUrl?.also {
                InteractiveIcon(
                    iconId = R.drawable.export_outline,
                    contentDescription = stringResource( R.string.opens_link_in_web_browser ),
                    tint = colorPalette.text
                ) {
                    uriHandler.openUri( it )
                }
            }
        }
}
