package app.kreate.android.themed.common.component.dialog

import android.content.res.Configuration
import androidx.annotation.CallSuper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.bold
import me.knighthat.innertube.Localized

abstract class Dialog {

    companion object {
        /**
         * Space between top and bottom line of dialog.
         */
        const val VERTICAL_PADDING = 15

        /**
         * Represents the maximum height allowed
         * for this component in portrait mode.
         *
         * Larger number may cause accessibility issue
         */
        const val MAX_WIDTH_PORTRAIT = .95f

        /**
         * Represents the maximum height allowed
         * for this component in landscape mode.
         *
         * Larger number may cause accessibility issue
         */
        const val MAX_WIDTH_LANDSCAPE = .6f

        /**
         * Represents the maximum width allowed
         * for this component in portrait mode.
         *
         * Larger number may cause accessibility issue
         */
        const val MAX_HEIGHT_PORTRAIT = .5f

        /**
         * Represents the maximum width allowed
         * for this component in landscape mode.
         *
         * Larger number may cause accessibility issue
         */
        const val MAX_HEIGHT_LANDSCAPE = .7f

        /**
         * Space between sections in dialog.
         *
         * A section is an individual part of dialog,
         * i.e. title, body, buttons
         */
        const val SPACE_BETWEEN_SECTIONS = 20
    }

    @get:Composable
    @get:Localized
    abstract val dialogTitle: String

    /**
     * Whether the dialog should be shown or not.
     *
     * **Avoid** changing this value directly, instead,
     * use [showDialog] and/or [hideDialog] to
     * modify dialog's visibility.
     */
    open var isActive: Boolean by mutableStateOf( false )

    @Composable
    protected abstract fun DialogBody()

    @CallSuper
    open fun showDialog() { isActive = true }

    @CallSuper
    open fun hideDialog() { isActive = false }

    @Composable
    open fun DialogFooter() {}

    @Composable
    open fun DialogHeader() {
        val typography = LocalAppearance.current.typography

        Column( horizontalAlignment = Alignment.CenterHorizontally ) {
            BasicText(
                text = dialogTitle,
                style = typography.m.bold,
                modifier = Modifier.padding( bottom = 8.dp )
                                   .fillMaxWidth( .9f )
            )

            HorizontalDivider( Modifier.fillMaxWidth( .95f ) )
        }
    }

    @Composable
    open fun Render() {
        if( !isActive ) return

        val configuration = LocalConfiguration.current
        val colorPalette = LocalAppearance.current.colorPalette
        val (isLandscape, screenWidthDp, screenHeightDp) = Triple(
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
            configuration.screenWidthDp.dp,
            configuration.screenHeightDp.dp
        )
        val (maxWidth, maxHeight) = if( isLandscape ) {
            MAX_WIDTH_LANDSCAPE to MAX_HEIGHT_LANDSCAPE
        } else {
            MAX_WIDTH_PORTRAIT to MAX_HEIGHT_PORTRAIT
        }

        androidx.compose.ui.window.Dialog( ::hideDialog ) dialogComp@ {
            Column(
                verticalArrangement = Arrangement.spacedBy( SPACE_BETWEEN_SECTIONS.dp ),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.wrapContentSize()
                                   .sizeIn(
                                       maxWidth = screenWidthDp * maxWidth,
                                       maxHeight = screenHeightDp * maxHeight
                                   )
                                   .background(
                                       color = colorPalette.background0,
                                       shape = RoundedCornerShape( 8.dp )
                                   )
                                   .padding( vertical = VERTICAL_PADDING.dp )
            ) {
                DialogHeader()
                DialogBody()
                DialogFooter()
            }
        }
    }
}