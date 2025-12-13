package me.knighthat.kreate.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.Role.Companion
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kreate.composeapp.generated.resources.Res
import kreate.composeapp.generated.resources.dark_mode
import kreate.composeapp.generated.resources.light_mode
import me.knighthat.kreate.constant.ColorScheme
import me.knighthat.kreate.preference.Preferences
import org.jetbrains.compose.resources.stringResource


@Composable
fun SwitchColorSchemeButton( modifier: Modifier = Modifier ) {
    val colors = IconButtonDefaults.iconButtonColors()
    val shape = IconButtonDefaults.outlinedShape
    val isSystemDarkMode = isSystemInDarkTheme()
    val colorScheme by Preferences.COLOR_SCHEME.collectAsState()
    val isDarkMode by remember {derivedStateOf {
        when( colorScheme ) {
            ColorScheme.LIGHT -> false
            ColorScheme.DARK -> true
            ColorScheme.DYNAMIC -> isSystemDarkMode
        }
    }}

    AnimatedContent(
        targetState = if( isDarkMode ) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
        transitionSpec = {
            slideInVertically { it } togetherWith slideOutVertically()
        },
        contentAlignment = Alignment.Center,
        // Ripped from IconButton with some minor modification
        modifier = modifier
            .border(
                width = 1.dp,
                color = colors.contentColor.copy( .2f ),
                shape = shape
            )
            .minimumInteractiveComponentSize()
            .size( 48.dp )
            .clip( shape )
            .background( color = colors.containerColor, shape = shape )
            .clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple()
            ) {
                Preferences.COLOR_SCHEME.value = when( colorScheme ) {
                    ColorScheme.LIGHT -> ColorScheme.DARK
                    ColorScheme.DARK -> ColorScheme.LIGHT
                    ColorScheme.DYNAMIC -> if( isSystemDarkMode ) ColorScheme.LIGHT else ColorScheme.DARK
                }
            }
    ) { target ->
        Icon(
            imageVector = target,
            tint = colors.contentColor,
            contentDescription = stringResource(
                if( isDarkMode ) Res.string.dark_mode else Res.string.light_mode
            ),
            modifier = Modifier.requiredSize( 24.dp )
        )
    }
}