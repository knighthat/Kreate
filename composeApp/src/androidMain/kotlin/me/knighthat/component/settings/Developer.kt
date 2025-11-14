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
import app.kreate.android.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class Developer(
    val id: Int,

    val contributions: Int,

    @SerialName("login")
    override val username: String,

    @SerialName("name")
    override val displayName: String?,

    @SerialName("avatar_url")
    override val avatarUrl: String,

    @SerialName("html_url")
    override val profileUrl: String,

    @SerialName("is_owner")
    override val isOwner: Boolean
): Contributor() {

    @Composable
    override fun TrailingContent( values: Values, modifier: Modifier ) =
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = SM_ARRANGEMENT_SPACE,
            modifier = modifier
        ) {
            Text(
                text = contributions.toString(),
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
}