package me.knighthat.component.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.android.R
import app.kreate.android.themed.common.component.settings.BooleanEntry
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.preferences.Preferences
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.ui.components.themed.IconButton
import it.fast4x.rimusic.ui.components.themed.SliderControl
import it.fast4x.rimusic.ui.styling.favoritesIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import me.knighthat.component.dialog.Dialog

class BlurAdjuster private constructor(
    coroutineScope: CoroutineScope,
    activeState: MutableState<Boolean>,
    rotatingCoverState: State<Boolean>
): Dialog {

    companion object {
        @Composable
        operator fun invoke() = BlurAdjuster(
            rememberCoroutineScope(),
            remember { mutableStateOf( false ) },
            Preferences.PLAYER_ROTATING_ALBUM_COVER.collectAsStateWithLifecycle()
        )
    }

    val isCoverRotating: Boolean by rotatingCoverState
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.controls_title_blur_effect )

    var strength: Float by mutableFloatStateOf( Preferences.PLAYER_BACKGROUND_BLUR_STRENGTH.value )
    var backdrop: Float by mutableFloatStateOf( Preferences.PLAYER_BACKGROUND_BACK_DROP.value )
    override var isActive: Boolean by activeState

    init {
        coroutineScope.launch {
            Preferences.PLAYER_BACKGROUND_BLUR_STRENGTH
                       .drop( 1 )
                       .collect { strength = it }
        }
        coroutineScope.launch {
            Preferences.PLAYER_BACKGROUND_BACK_DROP
                       .drop( 1 )
                       .collect { backdrop = it }
        }
    }

    fun onDismiss()  { isActive = false }

    @Composable
    override fun DialogBody() {
        Column(
            modifier = Modifier.wrapContentSize()
                               .padding( horizontal = 8.dp ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            //<editor-fold defaultstate="collapsed" desc="Blur slider">
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = { strength = 25f },
                    icon = R.drawable.drop_blur,
                    color = colorPalette().favoritesIcon,
                    modifier = Modifier.size(24.dp)
                )

                SliderControl(
                    state = strength,
                    onSlide = { strength = it },
                    onSlideComplete = {},
                    toDisplay = { "%.02f".format(it) },
                    range = 0f..100f
                )
            }
            //</editor-fold>
            //<editor-fold defaultstate="collapsed" desc="Backdrop slider">
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = { backdrop = 0f },
                    icon = R.drawable.drop_half_fill,
                    color = colorPalette().favoritesIcon,
                    modifier = Modifier.size( 24.dp )
                )

                SliderControl(
                    state = backdrop,
                    onSlide = { backdrop = it },
                    onSlideComplete = {},
                    toDisplay = { "%.0f".format(it) },
                    range = 0f..100f
                )
            }
            //</editor-fold>
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = { backdrop = 0f },
                    icon = R.drawable.image,
                    color = colorPalette().favoritesIcon,
                    modifier = Modifier.size( 24.dp )
                )

                SettingComponents.BooleanEntry(
                    preference = app.kreate.preferences.Preferences.PLAYER_ROTATING_ALBUM_COVER,
                    title = stringResource( R.string.rotating_cover_title )
                )
            }
        }
    }
}