package app.kreate.android.themed.common.component.settings.about

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.kreate.coil3.ImageFactory
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.ui.styling.favoritesIcon
import kotlinx.serialization.Serializable

@Serializable
abstract class InfoCard {

    companion object {
        val defaultValues: Values
            @Composable
            get() {
                val (colorPalette, typography) = LocalAppearance.current

                return Values(
                    backgroundColor = Color.Transparent,
                    ownerBackgroundColor = colorPalette.background1,
                    displayNameColor = colorPalette.text,
                    displayNameFontSize = typography.xs.fontSize,
                    handleColor = colorPalette.textSecondary,
                    handleFontSize = typography.xs.fontSize,
                    trailingColor = colorPalette.favoritesIcon.copy( alpha = .8f ),
                    trailingFontSize = typography.xs.fontSize
                )
            }
    }

    abstract val username: String
    abstract val displayName: String?
    abstract val profileUrl: String
    abstract val avatarUrl: String
    abstract val handle: String

    @Composable
    protected abstract fun TrailingContent( color: Color, fontSize: TextUnit )

    protected abstract fun isOwner(): Boolean

    @Composable
    protected fun Avatar() =
        ImageFactory.AsyncImage(
            thumbnailUrl = this.avatarUrl,
            contentDescription = "$username\'s avatar",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(40.dp)
                               .clip( RoundedCornerShape( 25.dp ) )
                               .border(
                                   width = 1.dp,
                                   color = Color.White,
                                   shape = RoundedCornerShape( 25.dp )
                               )
        )

    @Composable
    fun Draw( values: Values, uriHandler: UriHandler ) {
        Card(
            modifier = Modifier.padding(
                                   start = 12.dp,
                                   end = 20.dp,
                                   bottom = 10.dp
                               )
                               .fillMaxWidth(),
            colors = CardColors(
                containerColor = Color.Transparent,
                contentColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = Color.Transparent
            ),
        ) {
            val backgroundColor = remember {
                if( isOwner() )
                    values.ownerBackgroundColor
                else
                    values.backgroundColor
            }
            Row(
                modifier = Modifier.fillMaxWidth()
                                   .padding(
                                       vertical = 5.dp,
                                       horizontal = 15.dp
                                   )
                                   .background(
                                       color = backgroundColor,
                                       shape = RoundedCornerShape( 12.dp )
                                   ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy( 16.dp )
            ) {
                Avatar()

                Column( Modifier.fillMaxWidth() ) {
                    Text(
                        text = displayName ?: username,
                        style = TextStyle(
                            color = values.displayNameColor,
                            fontSize = values.displayNameFontSize,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Start
                    )

                    Row( Modifier.fillMaxWidth() ) {

                        Text(
                            text = "@$handle",
                            style = TextStyle(
                                color = values.handleColor,
                                fontSize = values.handleFontSize,
                                fontStyle = FontStyle.Italic,
                                textAlign = TextAlign.Start
                            ),
                            modifier = Modifier.weight( 1f )
                                               .clickable {
                                                   uriHandler.openUri( profileUrl )
                                               }
                        )

                        TrailingContent( values.trailingColor, values.trailingFontSize )
                    }
                }
            }
        }
    }

    data class Values(
        val backgroundColor: Color,
        val ownerBackgroundColor: Color,
        val displayNameColor: Color,
        val displayNameFontSize: TextUnit,
        val handleColor: Color,
        val handleFontSize: TextUnit,
        val trailingColor: Color,
        val trailingFontSize: TextUnit
    )
}