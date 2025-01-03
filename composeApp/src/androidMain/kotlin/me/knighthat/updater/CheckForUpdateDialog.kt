package me.knighthat.updater

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.fast4x.rimusic.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.CheckUpdateState
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.tab.toolbar.Dialog
import it.fast4x.rimusic.ui.components.themed.DefaultDialog
import it.fast4x.rimusic.utils.*

object CheckForUpdateDialog: Dialog {

    private var isCanceled: Boolean by mutableStateOf( false )

    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.check_at_github_for_updates )

    override var isActive: Boolean by mutableStateOf( false )

    fun onDismiss() {
        isCanceled = true
        isActive = false
    }

    @Composable
    override fun Render() {
        if( isCanceled || !isActive ) return

        var checkUpdateState by rememberPreference( checkUpdateStateKey, CheckUpdateState.Disabled )

        @Composable
        fun DescriptionText( @StringRes textId: Int ) =
            BasicText(
                text = stringResource( textId ),
                style = typography().xs.medium.copy( color = colorPalette().textSecondary ),
                modifier = Modifier.padding( all = 5.dp )
            )

        @Composable
        fun Button(
            @StringRes textId: Int,
            isPrimary: Boolean = false,
            modifier: Modifier = Modifier,
            onClick: () -> Unit
        ) {
            val textColor = if( isPrimary ) colorPalette().onAccent else colorPalette().text
            val bgColor = if( isPrimary ) colorPalette().accent else Color.Transparent

            Box(
                modifier = modifier.clip( RoundedCornerShape(36.dp) )
                    .background( bgColor )
                    .fillMaxWidth()
                    .padding( vertical = 16.dp )
                    .clickable( onClick = onClick ),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = stringResource( textId ),
                    style = typography().xs.medium.color(textColor)
                )
            }
        }

        DefaultDialog( ::onDismiss ) {
            // Title
            BasicText(
                text = dialogTitle,
                style = typography().s.bold,
            )
            Spacer( Modifier.height(10.dp) )

            DescriptionText( R.string.when_an_update_is_available_you_will_be_asked_if_you_want_to_install_info )
            DescriptionText( R.string.but_these_updates_would_not_go_through )
            DescriptionText( R.string.you_can_still_turn_it_on_or_off_from_the_settings )

            Spacer( Modifier.height(30.dp) )

            Column( Modifier.fillMaxWidth() ) {
                // Agree to check
                Button(
                    textId = R.string.check_update,
                    isPrimary = true,
                    onClick = {
                        onDismiss()
                        Updater.checkForUpdate()
                    }
                )
                Spacer( Modifier.height(10.dp) )

                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Cancel (skip this time)
                    Button(
                        textId = R.string.cancel,
                        onClick = ::onDismiss,
                        modifier = Modifier.weight( 1f )
                    )

                    // Disable auto check 4 update
                    Button(
                        textId = R.string.turn_off,
                        modifier = Modifier.weight( 1f ),
                        onClick = {
                            checkUpdateState = CheckUpdateState.Disabled

                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}