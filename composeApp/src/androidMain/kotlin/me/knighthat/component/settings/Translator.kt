package me.knighthat.component.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastJoinToString
import app.kreate.android.R
import kotlinx.serialization.Serializable


@Serializable
data class Translator(
    val user: User,
    val languages: List<Language>,
    val translated: Int
): Contributor() {

    override val username: String = ""
    override val displayName: String = user.name
    override val avatarUrl: String = user.avatar
    override val isOwner: Boolean = false
    override val profileUrl: String = ""

    @Composable
    override fun TrailingContent( values: Values, modifier: Modifier ) =
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = SM_ARRANGEMENT_SPACE,
            modifier = modifier
        ) {
            Text(
                text = languages.fastJoinToString(", ") { it.name },
                style = TextStyle(
                    color = values.trailingColor,
                    fontSize = values.trailingFontSize,
                ),
                textAlign = TextAlign.End
            )

            Icon(
                painter = painterResource( R.drawable.git_pull_request_outline ),
                contentDescription = null,
                tint = values.trailingColor,
                modifier = Modifier.size( values.trailingFontSize.value.dp )
            )
        }

    @Composable
    override fun Handle( values: Values ) {}

    @Serializable
    data class User(val name: String, val avatar: String, val joined: String)

    @Serializable
    data class Language(val id: String, val name: String)
}