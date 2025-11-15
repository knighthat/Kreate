package me.knighthat.component.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.kreate.android.R
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.themed.common.component.settings.SettingComponents
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.ui.styling.favoritesIcon


abstract class Contributor {

    companion object {

        const val AVATAR_SIZE = 40
        val SM_ARRANGEMENT_SPACE = Arrangement.spacedBy( 5.dp )
        val CARD_SHAPE = RoundedCornerShape( 25.dp )
    }

    abstract val username: String
    abstract val displayName: String?
    abstract val avatarUrl: String
    abstract val profileUrl: String
    abstract val isOwner: Boolean

    @Composable
    protected abstract fun TrailingContent( values: Values, modifier: Modifier = Modifier)

    @Composable
    protected fun Avatar( values: Values ) =
        ImageFactory.AsyncImage(
            thumbnailUrl = this.avatarUrl,
            contentDescription = "$username\'s avatar",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size( AVATAR_SIZE.dp )
                               .clip( CircleShape )
                               .border(
                                   width = 1.dp,
                                   color = values.avatarBorderColor,
                                   shape = CircleShape
                               )
        )

    @Composable
    protected open fun Handle(values: Values ) {
        val uriHandler = LocalUriHandler.current

        Text(
            text = "@$username",
            style = TextStyle(
                color = values.handleColor,
                fontSize = values.handleFontSize,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Start
            ),
            modifier = Modifier.clickable {
                uriHandler.openUri( profileUrl )
            }
        )
    }

    @Composable
    protected fun Title( values: Values ) =
        Row(
            horizontalArrangement = SM_ARRANGEMENT_SPACE
        ) {
            Text(
                text = displayName ?: username,
                style = TextStyle(
                    color = values.displayNameColor,
                    fontSize = values.displayNameFontSize,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Start,
                maxLines = 1
            )

            if( isOwner )
                // Displays crown icon if it's owner card
                Icon(
                    painter = painterResource( R.drawable.crown ),
                    tint = values.crownColor,
                    contentDescription = "Project's owner",
                    modifier = Modifier.size( 12.dp )
                )
        }

    @Composable
    fun Draw( values: Values ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy( 16.dp ),
            modifier = Modifier.background( values.backgroundColor, CARD_SHAPE )
                               .border(
                                   width = 1.dp,
                                   color = values.borderColor,
                                   shape = CARD_SHAPE
                               )
                               .padding(
                                   horizontal = SettingComponents.HORIZONTAL_PADDING.dp,
                                   vertical = SettingComponents.VERTICAL_SPACING.dp
                               )
        ) {
            Avatar( values )

            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = SM_ARRANGEMENT_SPACE,
                modifier = Modifier.weight( 1f )
            ) {
                Title( values )
                Handle( values )
            }

            TrailingContent( values )
        }
    }

    data class Values(
        val backgroundColor: Color,
        val borderColor: Color,
        val displayNameColor: Color,
        val displayNameFontSize: TextUnit,
        val handleColor: Color,
        val handleFontSize: TextUnit,
        val trailingColor: Color,
        val trailingFontSize: TextUnit,
        val avatarBorderColor: Color,
        val crownColor: Color
    ) {

        companion object {

            val default: Values
                @Composable
                get() {
                    val context = LocalContext.current
                    val (colorPalette, typography) = LocalAppearance.current

                    return Values(
                        backgroundColor = colorPalette.background1,
                        borderColor = colorPalette.background2,
                        displayNameColor = colorPalette.text,
                        displayNameFontSize = typography.xs.fontSize,
                        handleColor = colorPalette.textSecondary,
                        handleFontSize = typography.xs.fontSize,
                        trailingColor = colorPalette.favoritesIcon.copy( alpha = .8f ),
                        trailingFontSize = typography.xs.fontSize,
                        avatarBorderColor = Color.White,
                        crownColor = Color(context.getColor( R.color.yellow_warning ))
                    )
                }
        }
    }
}