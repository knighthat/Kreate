package app.kreate.android.themed.common.component.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kreate.android.R
import app.kreate.util.scrollingText
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.semiBold
import me.knighthat.component.dialog.Dialog
import me.knighthat.component.dialog.RestartAppDialog
import me.knighthat.utils.Toaster

object SettingComponents {

    /**
     * Default indentation, applied to all entries
     */
    private const val DEFAULT_HORIZONTAL_PADDING = 12

    /**
     * Use for subsequent indentation.
     *
     * ```kotlin
     * Modifier.padding( start = SettingComponents.HORIZONTAL_PADDING )
     * ```
     *
     * Avoid using `all` for children's padding, it'll
     * shrink the width, making it more clustered.
     */
    const val HORIZONTAL_PADDING = 12

    /**
     * Space between entries in vertical layout
     */
    const val VERTICAL_SPACING = 12

    const val CHILDREN_PADDING = 25

    @Composable
    fun getColors( isEnabled: Boolean ): ListItemColors =
        with( LocalAppearance.current.colorPalette ) {
            ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = if( isEnabled ) text else textDisabled,
                supportingColor = if( isEnabled ) textSecondary else textDisabled
            )
        }

    @Composable
    fun Description(
        text: String,
        modifier: Modifier = Modifier,
        isImportant: Boolean = false
    ) =
        BasicText(
            text = text,
            maxLines = 2,
            style = typography().xs
                                .semiBold
                                .copy(
                                    if( isImportant )
                                        colorPalette().red
                                    else
                                        colorPalette().textSecondary
                                ),
            modifier = modifier.padding( horizontal = DEFAULT_HORIZONTAL_PADDING.dp )
        )

    @Composable
    fun Description(
        @StringRes textId: Int,
        modifier: Modifier = Modifier,
        isImportant: Boolean = false
    ) = Description( stringResource( textId ), modifier, isImportant )

    @Composable
    fun Text(
        title: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        subtitle: String = "",
        isEnabled: Boolean = true,
        trailingContent: @Composable RowScope.() -> Unit = {}
    ) =
        Row(
            horizontalArrangement = Arrangement.spacedBy( 16.dp ),
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .padding(all = DEFAULT_HORIZONTAL_PADDING.dp)
                .fillMaxWidth()
                .clickable(
                    enabled = isEnabled,
                    onClick = onClick
                )
                .alpha(if (isEnabled) 1f else 0.5f)
        ) {
            Column( Modifier.weight( 1f ) ) {
                BasicText(
                    text = title,
                    maxLines = 1,
                    style = typography().xs
                                        .semiBold
                                        .copy( colorPalette().text ),
                    modifier = Modifier.padding( bottom = 4.dp )
                                       .scrollingText()
                )

                if( subtitle.isNotBlank() )
                    BasicText(
                        text = subtitle,
                        maxLines = 2,
                        style = typography().xs
                                            .semiBold
                                            .copy( colorPalette().textSecondary )
                    )
            }

            trailingContent()
        }

    @Composable
    fun <T> ListEntry(
        title: String,
        getName: @Composable (T) -> String,
        initialValue: T,
        getList: () -> Array<T>,
        modifier: Modifier = Modifier,
        subtitle: String = "",
        isEnabled: Boolean = true,
        action: Action = Action.NONE,
        trailingContent: @Composable RowScope.() -> Unit = {},
        onValueChanged: (T) -> Unit = {}
    ) {
        var selected by remember { mutableStateOf(initialValue) }

        val dialog = remember {
            object : Dialog {
                override val dialogTitle: String
                    @Composable
                    get() = title

                override var isActive: Boolean by mutableStateOf( false )

                @Composable
                override fun DialogBody() {
                    LazyColumn( Modifier.wrapContentHeight() ) {
                        items(
                            items = getList(),
                            key = System::identityHashCode
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding( vertical = 12.dp, horizontal = 24.dp )
                                                   .fillMaxWidth()
                                                   .clickable {
                                                       selected = it
                                                       onValueChanged(it)

                                                       when (action) {
                                                           Action.RESTART_APP -> {
                                                               hideDialog()
                                                               RestartAppDialog.showDialog()
                                                           }

                                                           Action.RESTART_PLAYER_SERVICE -> {
                                                               Toaster.i( R.string.minimum_silence_length_warning )
                                                               RestartPlayerService.requestRestart()
                                                           }

                                                           else -> { /* Does nothing */ }
                                                       }
                                                   }
                            ) {
                                val colorPalette = colorPalette()
                                val (inner, outer, width) = remember( selected ) {
                                    if( selected == it )
                                        Triple(colorPalette.accent, colorPalette.onAccent, 4.dp)
                                    else
                                        Triple(colorPalette.textDisabled, Color.Transparent, 1.dp)
                                }
                                // Draws select indicator before the text
                                Canvas(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(inner, CircleShape)
                                ) {
                                    drawCircle(
                                        color = outer,
                                        radius = width.toPx(),
                                        center = size.center
                                    )
                                }

                                BasicText(
                                    text = getName( it ),
                                    style = if( selected == it ) typography().xs.semiBold else typography().xs
                                )
                            }
                        }
                    }

                    Spacer( Modifier.height( Dialog.SPACE_BETWEEN_SECTIONS.dp ) )

                    if( action == Action.RESTART_APP )
                        BasicText(
                            text = stringResource( R.string.restarting_rimusic_is_required ),
                            style = typography().xs.copy(
                                colorPalette().red.copy( .8f )
                            ),
                            modifier = Modifier.fillMaxWidth( .9f )
                        )
                }
            }
        }
        dialog.Render()

        Text(
            title = title,
            onClick = dialog::showDialog,
            modifier = modifier,
            // Default to show current value's name if no subtitle is provided
            subtitle = subtitle.ifBlank { getName( selected ) },
            isEnabled = isEnabled,
            trailingContent = trailingContent
        )
    }

    /**
     * A set of actions to enact once the setting is set.
     */
    enum class Action {

        NONE,
        RESTART_APP,
        RESTART_PLAYER_SERVICE;
    }
}