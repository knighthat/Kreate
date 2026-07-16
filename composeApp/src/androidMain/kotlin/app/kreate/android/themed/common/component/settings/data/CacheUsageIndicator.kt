package app.kreate.android.themed.common.component.settings.data

import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kreate.compose.R
import it.fast4x.rimusic.ui.components.tab.toolbar.Button
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.bold
import me.knighthat.component.dialog.ConfirmDialog

abstract class CacheUsageIndicator: ConfirmDialog, Button {

    @get:DrawableRes
    val iconId: Int = R.drawable.trash
    val iconSize: Int = 20
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.dialog_title_are_you_sure )

    var progress: Float by mutableFloatStateOf( 0f )
        protected set
    override var isActive: Boolean by mutableStateOf( false )

    protected abstract fun updateProgress()

    @Deprecated(
        message = "ProgressBar already includes Render, use it!",
        replaceWith = ReplaceWith("CacheUsageIndicator.ProgressBar")
    )
    @Composable
    override fun Render() = super.Render()

    @CallSuper
    @Composable
    open fun ProgressBar( modifier: Modifier = Modifier ) {
        updateProgress()

        val colorPalette = LocalAppearance.current.colorPalette
        LinearProgressIndicator(
            progress = { progress },
            modifier = modifier,
            color = colorPalette.text,
            trackColor = colorPalette.textDisabled,
            strokeCap = StrokeCap.Round,
            gapSize = Dp.Hairline
        ) {}
        super.Render()
    }

    @Composable
    override fun DialogBody() =
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy( 5.dp ),
            modifier = Modifier.fillMaxWidth( .9f )
        ) {
            Icon(
                painter = painterResource( R.drawable.warning_outline ),
                contentDescription = null,
                tint = colorResource( R.color.yellow_warning ),
                modifier = Modifier.size( 32.dp )
            )

            BasicText(
                text = stringResource( R.string.dialog_text_this_action_is_irreversible ),
                style = LocalAppearance.current
                                       .typography
                                       .s
                                       .bold
                                       .copy( colorResource( R.color.yellow_warning ) )
            )
        }

    @Composable
    override fun ToolBarButton() =
        Icon(
            painter = painterResource( iconId ),
            contentDescription = stringResource( R.string.info_delete_cache ),
            tint = LocalAppearance.current.colorPalette.text,
            modifier = Modifier.size( iconSize.dp )
                               .clickable(
                                   interactionSource = null,
                                   indication = null,
                                   onClick = ::showDialog
                               )
        )
}