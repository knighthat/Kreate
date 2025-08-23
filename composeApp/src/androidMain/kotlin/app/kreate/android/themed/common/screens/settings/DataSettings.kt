package app.kreate.android.themed.common.screens.settings

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.themed.common.component.settings.RestartPlayerService
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.StorageSizeInputDialog
import app.kreate.android.themed.common.component.settings.data.ExoCacheIndicator
import app.kreate.android.themed.common.component.settings.data.ImageCacheIndicator
import app.kreate.android.themed.common.component.settings.entry
import app.kreate.android.themed.common.component.settings.header
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.ui.styling.Dimensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import me.knighthat.component.dialog.InputDialogConstraints
import me.knighthat.component.dialog.RestartAppDialog
import me.knighthat.component.export.ExportDatabaseDialog
import me.knighthat.component.export.ExportSettingsDialog
import me.knighthat.component.import.ImportDatabase
import me.knighthat.component.import.ImportSettings
import me.knighthat.utils.Toaster
import kotlin.math.roundToInt

@Composable
fun SettingComponents.StorageSizeEntry(
    context: Context,
    preference: Preferences.Long,
    title: String,
    subtitle: String,
    currentValue: Long,
    action: SettingComponents.Action,
    trailingContent: @Composable RowScope.() -> Unit = {}
) {
    val dialog = remember( context, currentValue ) {
        StorageSizeInputDialog(
            constraint = InputDialogConstraints.ALL,
            currentValue = currentValue,
            context = context,
            preference = preference,
            title = title,
            onConfirm = when( action ) {
                SettingComponents.Action.NONE -> ::println
                SettingComponents.Action.RESTART_APP -> RestartAppDialog::showDialog
                SettingComponents.Action.RESTART_PLAYER_SERVICE -> RestartPlayerService::requestRestart
            }
        )
    }
    dialog.Render()

    Text(
        title = title,
        onClick = dialog::showDialog,
        subtitle = subtitle,
        trailingContent = trailingContent
    )
}

fun cacheSubtitle( context: Context, preference: Preferences.Long, currentValue: () -> Long ) =
    derivedStateOf {
        val fromSetting by preference
        if( fromSetting == 0L ) return@derivedStateOf context.getString( R.string.vt_disabled )

        val maxSize = when( fromSetting ) {
            Long.MAX_VALUE -> context.getString( R.string.unlimited )
            else -> Formatter.formatShortFileSize( context, fromSetting )
        }
        val usage = Formatter.formatShortFileSize( context, currentValue() )
        val percentage = ((currentValue().toFloat() / fromSetting) * 100).roundToInt()

        context.getString( R.string.setting_description_storage_usage, maxSize, usage, percentage )
    }

@UnstableApi
@Composable
fun DataSettings( paddingValues: PaddingValues ) {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val scrollState = rememberLazyListState()

    val search = remember {
        SettingEntrySearch( scrollState, R.string.tab_data, R.drawable.server )
    }

    val progressBarModifier = remember {
        Modifier.padding(
                    horizontal = SettingComponents.HORIZONTAL_PADDING.dp,
                    vertical = 12.dp
                )
                .fillMaxWidth()
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
            header(
                titleId = R.string.cache,
                subtitle = { stringResource( R.string.cache_cleared ) }
            )
            entry( search, R.string.image_cache_max_size ) {
                ImageFactory.diskCache.let { cache ->
                    val indicator = remember( cache ) {
                        ImageCacheIndicator( cache )
                    }
                    // indicator's progress is observable, and is actually gets updated when
                    // cache is cleared. So this is used to re-compose subtitle when action is performed.
                    val subtitle by remember( indicator.progress ) {
                        cacheSubtitle( context, Preferences.IMAGE_CACHE_SIZE, cache::size )
                    }

                    SettingComponents.StorageSizeEntry(
                        context = context,
                        preference = Preferences.IMAGE_CACHE_SIZE,
                        title = stringResource( R.string.image_cache_max_size ),
                        subtitle = subtitle,
                        currentValue = cache.size,
                        action = SettingComponents.Action.RESTART_APP
                    ) {
                        if( Preferences.IMAGE_CACHE_SIZE.value > 0 )
                            indicator.ToolBarButton()
                    }

                    indicator.ProgressBar( progressBarModifier )

                    LaunchedEffect( Preferences.IMAGE_CACHE_SIZE.value ) {
                        if( 0L == Preferences.IMAGE_CACHE_SIZE.value )
                            indicator.onConfirm()
                    }
                }
            }
            entry( search, R.string.song_cache_max_size ) {
                binder?.cache?.let { cache ->
                    val indicator = remember( cache ) {
                        ExoCacheIndicator(Preferences.EXO_CACHE_SIZE, cache)
                    }
                    // indicator's progress is observable, and is actually gets updated when
                    // cache is cleared. So this is used to re-compose subtitle when action is performed.
                    val subtitle by remember( indicator.progress ) {
                        cacheSubtitle( context, Preferences.EXO_CACHE_SIZE, cache::getCacheSpace )
                    }
                    val maxCacheSize by Preferences.EXO_CACHE_SIZE

                    SettingComponents.StorageSizeEntry(
                        context = context,
                        preference = Preferences.EXO_CACHE_SIZE,
                        title = stringResource( R.string.song_cache_max_size ),
                        subtitle = subtitle,
                        currentValue = cache.cacheSpace,
                        action = SettingComponents.Action.RESTART_PLAYER_SERVICE
                    ) {
                        if( maxCacheSize > 0 )
                            indicator.ToolBarButton()
                    }

                    indicator.ProgressBar( progressBarModifier )

                    LaunchedEffect( maxCacheSize ) {
                        if( 0L == maxCacheSize )
                            indicator.onConfirm()
                    }
                }
            }
            entry( search, R.string.song_download_max_size ) {
                binder?.downloadCache?.let { cache ->
                    val indicator = remember( cache ) {
                        ExoCacheIndicator(Preferences.EXO_DOWNLOAD_SIZE, cache)
                    }
                    // indicator's progress is observable, and is actually gets updated when
                    // cache is cleared. So this is used to re-compose subtitle when action is performed.
                    val subtitle by remember( indicator.progress ) {
                        cacheSubtitle( context, Preferences.EXO_DOWNLOAD_SIZE, cache::getCacheSpace )
                    }
                    val maxCacheSize by Preferences.EXO_DOWNLOAD_SIZE

                    SettingComponents.StorageSizeEntry(
                        context = context,
                        preference = Preferences.EXO_DOWNLOAD_SIZE,
                        title = stringResource( R.string.song_download_max_size ),
                        subtitle = subtitle,
                        currentValue = cache.cacheSpace,
                        action = SettingComponents.Action.RESTART_APP
                    ) {
                        if( maxCacheSize > 0 )
                            indicator.ToolBarButton()
                    }

                    indicator.ProgressBar( progressBarModifier )

                    LaunchedEffect( maxCacheSize ) {
                        if( 0L == maxCacheSize )
                            indicator.onConfirm()
                    }
                }
            }

            header(
                titleId = R.string.title_backup_and_restore,
                subtitle = { stringResource( R.string.existing_data_will_be_overwritten, BuildConfig.APP_NAME ) }
            )
            entry( search, R.string.save_to_backup ) {
                val exportDbDialog = ExportDatabaseDialog( context )
                exportDbDialog.Render()

                SettingComponents.Text(
                    title = stringResource( R.string.save_to_backup ),
                    subtitle = stringResource( R.string.export_the_database ),
                    onClick = exportDbDialog::showDialog
                )
            }
            entry( search, R.string.restore_from_backup ) {
                val importDatabase = ImportDatabase( context )

                SettingComponents.Text(
                    title = stringResource(R.string.restore_from_backup),
                    subtitle = stringResource(R.string.import_the_database),
                    onClick = importDatabase::onShortClick
                )
            }
            entry( search, R.string.store_settings_in_a_file ) {
                val exportSettingsDialog = ExportSettingsDialog( context )
                exportSettingsDialog.Render()

                SettingComponents.Text(
                    title = exportSettingsDialog.dialogTitle,
                    subtitle = stringResource( R.string.description_exclude_credentials ),
                    onClick = exportSettingsDialog::showDialog
                )
            }
            entry( search, R.string.title_import_settings ) {
                val importSettings = ImportSettings( context )

                SettingComponents.Text(
                    title = stringResource( R.string.title_import_settings ),
                    subtitle = stringResource( R.string.restore_settings_from_file, stringResource( R.string.title_export_settings ) ),
                    onClick = importSettings::onShortClick
                )
            }

            header( R.string.search_history )
            entry( search, R.string.pause_search_history ) {
                SettingComponents.BooleanEntry(
                    Preferences.PAUSE_SEARCH_HISTORY,
                    R.string.pause_search_history,
                    R.string.neither_save_new_searched_query,
                    action = SettingComponents.Action.RESTART_PLAYER_SERVICE
                )
            }
            entry( search, R.string.clear_search_history ) {
                val subtitle by remember {
                    Database.searchTable
                            .findAllContain( "" )
                            .map { queries ->

                                if ( queries.isNotEmpty() )
                                    context.getString(
                                        R.string.setting_description_delete_search_history,
                                        context.resources.getQuantityString(
                                            R.plurals.query,
                                            queries.size,
                                            queries.size
                                        )
                                    )
                                else
                                    context.getString( R.string.history_is_empty )
                            }
                }.collectAsState( "", Dispatchers.IO )

                SettingComponents.Text(
                    title = stringResource( R.string.clear_search_history ),
                    subtitle = subtitle,
                    onClick = {
                        Database.asyncTransaction {
                            searchTable.deleteAll()

                            Toaster.done()
                        }
                    }
                )
            }
        }
    }
}