package app.kreate.android.themed.common.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kreate.compose.R
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.components.settings.EnumEntry
import app.kreate.components.settings.SettingComponents
import app.kreate.database.Database
import app.kreate.preferences.Preferences
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.ui.components.themed.ConfirmationDialog
import it.fast4x.rimusic.ui.screens.settings.isYouTubeLoggedIn
import it.fast4x.rimusic.ui.styling.Dimensions
import kotlinx.coroutines.Dispatchers
import me.knighthat.utils.Toaster

@Composable
fun QuickPicksSettings( paddingValues: PaddingValues ) {
    val context = LocalContext.current
    val scrollState = rememberLazyListState()

    val search = remember( isYouTubeLoggedIn() ) {
        val (titleId, iconId) = if( isYouTubeLoggedIn() )
            R.string.home to R.drawable.ytmusic
        else
            R.string.quick_picks to R.drawable.sparkles

        SettingEntrySearch( scrollState, titleId, iconId )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.background( colorPalette().background0 )
                           .padding( paddingValues )
                           .fillMaxHeight()
                           .fillMaxWidth(
                               if ( NavigationBarPosition.Right.isCurrent() )
                                   Dimensions.contentWidthRightBar
                               else
                                   1f
                           )
    ) {
        search.ToolBarButton()

        LazyColumn(
            state = scrollState,
            contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer)
        ) {
            item {
                if( search appearsIn R.string.enable_quick_picks_page )
                    SettingComponents.BooleanEntry(
                        preference = Preferences.QUICK_PICKS_PAGE,
                        title = stringResource( R.string.enable_quick_picks_page )
                    )
            }

            item {
                val title = stringResource( R.string.show_quotes, stringResource( R.string.tips ) )
                if( search appearsIn title )
                    SettingComponents.BooleanEntry(
                        preference = Preferences.QUICK_PICKS_SHOW_TIPS,
                        title = title
                    )
            }

            item {
                val isVisible by Preferences.QUICK_PICKS_SHOW_TIPS.collectAsStateWithLifecycle()
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(tween(100)),
                    exit = fadeOut(tween(100)),
                ) {
                    if( search appearsIn R.string.tips )
                        SettingComponents.EnumEntry(
                            preference = Preferences.QUICK_PICKS_TYPE,
                            title = stringResource( R.string.tips )
                        )
                }
            }

            item {
                val title = stringResource( R.string.show_quotes, stringResource( R.string.charts ) )
                if( search appearsIn title )
                    SettingComponents.BooleanEntry(
                        preference = Preferences.QUICK_PICKS_SHOW_CHARTS,
                        title = title
                    )
            }

            item {
                val title = stringResource( R.string.show_quotes, stringResource( R.string.related_albums ) )
                if( search appearsIn title )
                    SettingComponents.BooleanEntry(
                        preference = Preferences.QUICK_PICKS_SHOW_RELATED_ALBUMS,
                        title = title
                    )
            }

            item {
                val title = stringResource( R.string.show_quotes, stringResource( R.string.similar_artists ) )
                if( search appearsIn title )
                    SettingComponents.BooleanEntry(
                        preference = Preferences.QUICK_PICKS_SHOW_RELATED_ARTISTS,
                        title = title
                    )
            }

            item {
                val title = stringResource( R.string.show_quotes, stringResource( R.string.new_albums_of_your_artists ) )
                if( search appearsIn title )
                    SettingComponents.BooleanEntry(
                        preference = Preferences.QUICK_PICKS_SHOW_NEW_ALBUMS_ARTISTS,
                        title = title
                    )
            }

            item {
                val title = stringResource( R.string.show_quotes, stringResource( R.string.new_albums ) )
                if( search appearsIn title )
                    SettingComponents.BooleanEntry(
                        preference = Preferences.QUICK_PICKS_SHOW_NEW_ALBUMS,
                        title = title
                    )
            }

            item {
                val title = stringResource( R.string.show_quotes, stringResource( R.string.playlists_you_might_like ) )
                if( search appearsIn title )
                    SettingComponents.BooleanEntry(
                        preference = Preferences.QUICK_PICKS_SHOW_MIGHT_LIKE_PLAYLISTS,
                        title = title
                    )
            }

            item {
                val title = stringResource( R.string.show_quotes, stringResource( R.string.moods_and_genres ) )
                if( search appearsIn title )
                    SettingComponents.BooleanEntry(
                        preference = Preferences.QUICK_PICKS_SHOW_MOODS_AND_GENRES,
                        title = title
                    )
            }

            item {
                val title = stringResource( R.string.show_quotes, stringResource( R.string.monthly_playlists ) )
                if( search appearsIn title )
                    SettingComponents.BooleanEntry(
                        preference = Preferences.QUICK_PICKS_SHOW_MONTHLY_PLAYLISTS,
                        title = title
                    )
            }

            item {
                val eventsCount by remember {
                    Database.eventTable
                            .countAll()
                }.collectAsState( 0L, Dispatchers.IO )

                var clearEvents by remember { mutableStateOf(false) }
                if (clearEvents) {
                    ConfirmationDialog(
                        text = stringResource(R.string.do_you_really_want_to_delete_all_playback_events),
                        onDismiss = { clearEvents = false },
                        onConfirm = {
                            Database.asyncTransaction {
                                eventTable.deleteAll()
                                Toaster.done()
                            }
                        }
                    )
                }

                val subtitle by remember { derivedStateOf {
                    if( eventsCount > 0 )
                        context.getString( R.string.delete_playback_events, eventsCount.toString() )
                    else
                        context.getString( R.string.quick_picks_are_cleared )
                }}

                SettingComponents.Entry(
                    title = stringResource( R.string.reset_quick_picks ),
                    subtitle = subtitle,
                    onClick = { clearEvents = true },
                    enabled = eventsCount > 0
                )
            }
        }
    }
}