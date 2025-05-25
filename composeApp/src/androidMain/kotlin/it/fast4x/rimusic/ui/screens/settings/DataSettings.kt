package it.fast4x.rimusic.ui.screens.settings

import android.annotation.SuppressLint
import android.text.format.Formatter
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.kreate.android.R
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.CacheType
import it.fast4x.rimusic.enums.CoilDiskCacheMaxSize
import it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import it.fast4x.rimusic.enums.ExoPlayerDiskCacheMaxSize
import it.fast4x.rimusic.enums.ExoPlayerDiskDownloadCacheMaxSize
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.ui.components.themed.CacheSpaceIndicator
import it.fast4x.rimusic.ui.components.themed.ConfirmationDialog
import it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import it.fast4x.rimusic.ui.components.themed.InputNumericDialog
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.utils.RestartPlayerService
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.coilCustomDiskCacheKey
import it.fast4x.rimusic.utils.coilDiskCacheMaxSizeKey
import it.fast4x.rimusic.utils.exoPlayerCacheLocationKey
import it.fast4x.rimusic.utils.exoPlayerCustomCacheKey
import it.fast4x.rimusic.utils.exoPlayerDiskCacheMaxSizeKey
import it.fast4x.rimusic.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import it.fast4x.rimusic.utils.pauseSearchHistoryKey
import it.fast4x.rimusic.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.knighthat.component.export.ExportDatabaseDialog
import me.knighthat.component.export.ExportSettingsDialog
import me.knighthat.component.import.ImportDatabase
import me.knighthat.component.import.ImportMigration
import me.knighthat.component.import.ImportSettings
import me.knighthat.utils.Toaster

@SuppressLint("SuspiciousIndentation")
@OptIn(ExperimentalCoilApi::class)
@ExperimentalAnimationApi
@UnstableApi
@Composable
fun DataSettings() {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current

    var coilDiskCacheMaxSize by rememberPreference(
        coilDiskCacheMaxSizeKey,
        CoilDiskCacheMaxSize.`128MB`
    )
    var exoPlayerDiskCacheMaxSize by rememberPreference(
        exoPlayerDiskCacheMaxSizeKey,
        ExoPlayerDiskCacheMaxSize.`2GB`
    )

    var exoPlayerDiskDownloadCacheMaxSize by rememberPreference(
        exoPlayerDiskDownloadCacheMaxSizeKey,
        ExoPlayerDiskDownloadCacheMaxSize.`2GB`
    )

    /*
     *    var exoPlayerAlternateCacheLocation by rememberPreference(
     *        exoPlayerAlternateCacheLocationKey,""
     *    )
     */

    var exoPlayerCacheLocation by rememberPreference(
        exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System
    )


    /*
     *    val dirRequest = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
     *        uri?.let {
     *            context.applicationContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
     *            context.applicationContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
     *            //Log.d("exoAltLocationCache",uri.path.toString())
     *            exoPlayerAlternateCacheLocation = uri.path.toString()
}
}
*/


    var showExoPlayerCustomCacheDialog by remember { mutableStateOf(false) }
    var exoPlayerCustomCache by rememberPreference(
        exoPlayerCustomCacheKey,32
    )

    var showCoilCustomDiskCacheDialog by remember { mutableStateOf(false) }
    var coilCustomDiskCache by rememberPreference(
        coilCustomDiskCacheKey,32
    )

    var pauseSearchHistory by rememberPreference(pauseSearchHistoryKey, false)

    var cleanCacheOfflineSongs by remember {
        mutableStateOf(false)
    }

    var cleanDownloadCache by remember {
        mutableStateOf(false)
    }
    var cleanCacheImages by remember {
        mutableStateOf(false)
    }

    if (cleanCacheOfflineSongs) {
        ConfirmationDialog(
            text = stringResource(R.string.do_you_really_want_to_delete_cache),
                           onDismiss = {
                               cleanCacheOfflineSongs = false
                           },
                           onConfirm = {
                               binder?.cache?.keys?.forEach { song ->
                                   binder.cache.removeResource(song)
                               }
                           }
        )
    }

    if (cleanDownloadCache) {
        ConfirmationDialog(
            text = stringResource(R.string.do_you_really_want_to_delete_cache),
            onDismiss = {
                cleanDownloadCache = false
            },
            onConfirm = {
                binder?.downloadCache?.keys?.forEach { songId ->
                    binder.downloadCache.removeResource(songId)

                    CoroutineScope(Dispatchers.IO).launch {
                        Database.songTable
                                .findById( songId )
                                .first()
                                ?.asMediaItem
                                ?.let { MyDownloadHelper.removeDownload( context, it ) }
                    }
                }
            }
        )
    }

    if (cleanCacheImages) {
        ConfirmationDialog(
            text = stringResource(R.string.do_you_really_want_to_delete_cache),
                           onDismiss = {
                               cleanCacheImages = false
                           },
                           onConfirm = {
                               Coil.imageLoader(context).diskCache?.clear()
                           }
        )
    }

    var restartService by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            //.fillMaxSize()
        .fillMaxHeight()
            .fillMaxWidth(
                if (NavigationBarPosition.Right.isCurrent())
                    Dimensions.contentWidthRightBar
                else
                    1f
            )
            .verticalScroll(rememberScrollState())
        /*
         *            .padding(
         *                LocalPlayerAwareWindowInsets.current
         *                    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
         *                    .asPaddingValues()
    )

    */
    ) {
        HeaderWithIcon(
            title = stringResource(R.string.tab_data),
                       iconId = R.drawable.server,
                       enabled = false,
                       showIcon = true,
                       modifier = Modifier,
                       onClick = {}
        )

        SettingsDescription(text = stringResource(R.string.cache_cleared))

        Coil.imageLoader(context).diskCache?.let { diskCache ->
            val diskCacheSize = remember(diskCache.size, cleanCacheImages) {
                diskCache.size
            }

            SettingsGroupSpacer()
            SettingsEntryGroupText(title = stringResource(R.string.cache))

            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.image_cache_max_size),
                                           titleSecondary = when (coilDiskCacheMaxSize) {
                                               CoilDiskCacheMaxSize.Custom -> Formatter.formatShortFileSize(context, diskCacheSize) +
                                               "/${Formatter.formatShortFileSize(context, coilCustomDiskCache.toLong() * 1000 * 1000)}" +
                                               stringResource(R.string.used)
                                               else -> Formatter.formatShortFileSize(context, diskCacheSize) +
                                                   stringResource(R.string.used) +
                                                   " (${diskCacheSize * 100 / coilDiskCacheMaxSize.bytes}%)"
                                           },
                                           trailingContent = {
                                               HeaderIconButton(
                                                   icon = R.drawable.trash,
                                                   enabled = true,
                                                   color = colorPalette().text,
                                                                onClick = { cleanCacheImages = true }
                                               )
                                           },
                                           selectedValue = coilDiskCacheMaxSize,
                                           onValueSelected = {
                                               coilDiskCacheMaxSize = it
                                               if (coilDiskCacheMaxSize == CoilDiskCacheMaxSize.Custom)
                                                   showCoilCustomDiskCacheDialog = true

                                                   restartService = true
                                           },
                                           valueText = { it.text }
            )
            RestartPlayerService(restartService, onRestart = { restartService = false } )

            if (showCoilCustomDiskCacheDialog) {
                InputNumericDialog(
                    title = stringResource(R.string.set_custom_cache),
                                   placeholder = stringResource(R.string.enter_value_in_mb),
                                   value = coilCustomDiskCache.toString(),
                                   valueMin = "32",
                                   valueMax = "10000",
                                   onDismiss = { showCoilCustomDiskCacheDialog = false },
                                   setValue = {
                                       //Log.d("customCache", it)
                                       coilCustomDiskCache = it.toInt()
                                       showCoilCustomDiskCacheDialog = false
                                       restartService = true
                                   }
                )
                RestartPlayerService(restartService, onRestart = { restartService = false } )
            }

            CacheSpaceIndicator(cacheType = CacheType.Images, horizontalPadding = 20.dp)
        }

        binder?.cache?.let { cache ->
            val diskCacheSize = remember(cache.cacheSpace, cleanCacheOfflineSongs) {
                cache.cacheSpace
            }

            //SettingsGroup {
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.song_cache_max_size),
                                           titleSecondary = when (exoPlayerDiskCacheMaxSize) {
                                               ExoPlayerDiskCacheMaxSize.Disabled -> ""
                                               ExoPlayerDiskCacheMaxSize.Custom -> Formatter.formatShortFileSize(context, diskCacheSize) +
                                               "/${Formatter.formatShortFileSize(context,
                                                   exoPlayerCustomCache.toLong() * 1000 * 1000
                                               )}" + " ${stringResource(R.string.used)}"
                                               else -> Formatter.formatShortFileSize(context, diskCacheSize) +
                                                   " ${stringResource(R.string.used)}" +
                                                   when (val size = exoPlayerDiskCacheMaxSize) {
                                                       ExoPlayerDiskCacheMaxSize.Unlimited -> ""
                                                       ExoPlayerDiskCacheMaxSize.Custom -> "" // only needed because of UNLIMITED
                                                       else -> " (${diskCacheSize * 100 / size.bytes}%)"
                                                   }
                                           },
                                           trailingContent = {
                                               HeaderIconButton(
                                                   icon = R.drawable.trash,
                                                   enabled = true,
                                                   color = colorPalette().text,
                                                                onClick = { cleanCacheOfflineSongs = true }
                                               )
                                           },
                                           selectedValue = exoPlayerDiskCacheMaxSize,
                                           onValueSelected = {
                                               exoPlayerDiskCacheMaxSize = it
                                               if (exoPlayerDiskCacheMaxSize == ExoPlayerDiskCacheMaxSize.Custom)
                                                   showExoPlayerCustomCacheDialog = true

                                                   restartService = true
                                           },
                                           valueText = { it.text }
            )
            RestartPlayerService(restartService, onRestart = { restartService = false } )

            if (showExoPlayerCustomCacheDialog) {
                InputNumericDialog(
                    title = stringResource(R.string.set_custom_cache),
                                   placeholder = stringResource(R.string.enter_value_in_mb),
                                   value = exoPlayerCustomCache.toString(),
                                   valueMin = "32",
                                   valueMax = "10000",
                                   onDismiss = { showExoPlayerCustomCacheDialog = false },
                                   setValue = {
                                       //Log.d("customCache", it)
                                       exoPlayerCustomCache = it.toInt()
                                       showExoPlayerCustomCacheDialog = false
                                       restartService = true
                                   }
                )
                RestartPlayerService(restartService, onRestart = { restartService = false } )
            }

            CacheSpaceIndicator(cacheType = CacheType.CachedSongs, horizontalPadding = 20.dp)
        }

        binder?.downloadCache?.let { downloadCache ->
            val diskDownloadCacheSize = remember(downloadCache.cacheSpace, cleanDownloadCache) {
                downloadCache.cacheSpace
            }

            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.song_download_max_size),
                                           titleSecondary = when (exoPlayerDiskDownloadCacheMaxSize) {
                                               ExoPlayerDiskDownloadCacheMaxSize.Disabled -> ""
                                               else -> Formatter.formatShortFileSize(context, diskDownloadCacheSize) +
                                                   " ${stringResource(R.string.used)}" +
                                                   when (val size = exoPlayerDiskDownloadCacheMaxSize) {
                                                       ExoPlayerDiskDownloadCacheMaxSize.Unlimited -> ""
                                                       else -> " (${diskDownloadCacheSize * 100 / size.bytes}%)"
                                                   }
                                           },
                                           trailingContent = {
                                               HeaderIconButton(
                                                   icon = R.drawable.trash,
                                                   enabled = true,
                                                   color = colorPalette().text,
                                                                onClick = { cleanDownloadCache = true }
                                               )
                                           },
                                           selectedValue = exoPlayerDiskDownloadCacheMaxSize,
                                           onValueSelected = {
                                               exoPlayerDiskDownloadCacheMaxSize = it
                                               restartService = true
                                           },
                                           valueText = { it.text }
            )
            RestartPlayerService(restartService, onRestart = { restartService = false } )

            CacheSpaceIndicator(cacheType = CacheType.DownloadedSongs, horizontalPadding = 20.dp)
        }

        EnumValueSelectorSettingsEntry(
            title = stringResource(R.string.set_cache_location),
                                       selectedValue = exoPlayerCacheLocation,
                                       onValueSelected = {
                                           exoPlayerCacheLocation = it
                                           restartService = true
                                       },
                                       valueText = { it.text }
        )

        SettingsDescription(stringResource(R.string.info_private_cache_location_can_t_cleaned))
        RestartPlayerService(restartService, onRestart = { restartService = false } )
        //ImportantSettingsDescription(stringResource(R.string.restarting_rimusic_is_required))

        SettingsGroupSpacer()

        //SettingsEntryGroupText(title = stringResource(R.string.folder_cache))

        /*
         *        SettingsDescription(
         *            text = stringResource(R.string.custom_cache_from_android_10_may_not_be_available)
         *        )
         *
         *
         *        SettingsEntry(
         *            title = stringResource(R.string.cache_location_folder),
         *            text = if (exoPlayerAlternateCacheLocation == "") stringResource(R.string._default) else exoPlayerAlternateCacheLocation,
         *            //isEnabled = if (sdkVersion.toShort() < 29) true else false,
         *            onClick = {
         *                dirRequest.launch(null)
    }
    )


    ImportantSettingsDescription(text = stringResource(R.string.cache_reset_by_clicking_button))

    SettingsEntry(
        title = stringResource(R.string.reset_cache_location_folder),
        text = "",
        //isEnabled = if (sdkVersion.toShort() < 29) true else false,
        onClick = {
        exoPlayerAlternateCacheLocation = ""
    }
    )
    */


        SettingsEntryGroupText(title = stringResource(R.string.title_backup_and_restore))

        val exportDbDialog = ExportDatabaseDialog( context )
        exportDbDialog.Render()

        SettingsEntry(
            title = stringResource( R.string.save_to_backup ),
            text = stringResource( R.string.export_the_database ),
            onClick = exportDbDialog::showDialog
        )
        SettingsDescription(text = stringResource(R.string.personal_preference))

        val importDatabase = ImportDatabase( context )

        SettingsEntry(
            title = stringResource(R.string.restore_from_backup),
            text = stringResource(R.string.import_the_database),
            onClick = importDatabase::onShortClick
        )
        ImportantSettingsDescription(text = stringResource(
            R.string.existing_data_will_be_overwritten,
            context.applicationInfo.nonLocalizedLabel
        ))

        val exportSettingsDialog = ExportSettingsDialog( context )
        exportSettingsDialog.Render()

        SettingsEntry(
            title = exportSettingsDialog.dialogTitle,
            text = stringResource( R.string.store_settings_in_a_file ),
            onClick = exportSettingsDialog::showDialog
        )
        ImportantSettingsDescription(
            stringResource( R.string.description_exclude_credentials )
        )

        val importSettings = ImportSettings( context )

        SettingsEntry(
            title = stringResource( R.string.title_import_settings ),
            text = stringResource( R.string.restore_settings_from_file, stringResource( R.string.title_export_settings ) ),
            onClick = importSettings::onShortClick
        )
        ImportantSettingsDescription(text = stringResource(
            R.string.existing_data_will_be_overwritten,
            context.applicationInfo.nonLocalizedLabel
        ))

        val importMigration = ImportMigration( context, binder )

        SettingsEntry(
            title = stringResource( R.string.title_import_settings_migration ),
            text = stringResource( R.string.description_import_settings_migration ),
            onClick = importMigration::onShortClick
        )

        SettingsGroupSpacer()
        SettingsEntryGroupText(title = stringResource(R.string.search_history))

        SwitchSettingEntry(
            title = stringResource(R.string.pause_search_history),
                           text = stringResource(R.string.neither_save_new_searched_query),
                           isChecked = pauseSearchHistory,
                           onCheckedChange = {
                               pauseSearchHistory = it
                               restartService = true
                           }
        )
        RestartPlayerService(restartService, onRestart = { restartService = false } )

        val queriesCount by remember {
            Database.searchTable
                    .findAllContain("")
                    .map { it.size }
        }.collectAsState( 0, Dispatchers.IO )

        SettingsEntry(
            title = stringResource(R.string.clear_search_history),
                      text = if (queriesCount > 0) {
                          "${stringResource(R.string.delete)} " + queriesCount + stringResource(R.string.search_queries)
                      } else {
                          stringResource(R.string.history_is_empty)
                      },
                      isEnabled = queriesCount > 0,
                      onClick = {
                          Database.asyncTransaction {
                              searchTable.deleteAll()
                          }

                          Toaster.done()
                      }
        )
        SettingsGroupSpacer(
            modifier = Modifier.height(Dimensions.bottomSpacer)
        )
    }
}
