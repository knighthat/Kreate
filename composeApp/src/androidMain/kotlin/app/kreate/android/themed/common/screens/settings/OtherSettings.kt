package app.kreate.android.themed.common.screens.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.kreate.compose.R
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import app.kreate.android.themed.common.component.settings.entry
import app.kreate.android.themed.common.component.settings.header
import app.kreate.android.themed.common.screens.settings.other.debugSection
import app.kreate.component.DialogCancelButton
import app.kreate.component.DialogConfirmButton
import app.kreate.components.settings.SETTING_DIALOG_LIST_HEIGHT
import app.kreate.components.settings.SettingComponents
import app.kreate.preferences.Preferences
import app.kreate.util.isDocumentTree
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.isAtLeastAndroid6
import it.fast4x.rimusic.utils.isIgnoringBatteryOptimizations
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.create_new_folder
import kreate.resources.generated.resources.remove
import kreate.resources.generated.resources.semantic_remove_path
import me.knighthat.utils.Toaster
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource


private fun getFolderNameFromUri( context: Context, uri: Uri ): String {
    // Get the actual document ID representing the folder root
    val documentUri =
        if( uri.isDocumentTree ) {
            DocumentsContract.buildDocumentUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId( uri )
            )
        } else {
            // This is just a failsafe, if user imports settings from another device,
            // there's a good chance that those blacklisted folders aren't exist on new device
            uri
        }
    // Query the ContentResolver for the display name column
    val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

    context.contentResolver.query(documentUri, projection, null, null, null)?.use { cursor ->
        if ( cursor.moveToFirst() ) {
            val nameIndex = cursor.getColumnIndex( DocumentsContract.Document.COLUMN_DISPLAY_NAME )
            if ( nameIndex != -1 )
                // Immediately return if display name found
                return cursor.getString( nameIndex )
        }
    }

    // If the query fails (usually the folder is no longer exist), return placeholder value.
    return context.getString( R.string.placeholder_unknown_folder )
}

@Composable
private fun BlacklistConfigDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    preference: Preferences.StringSetPref = Preferences.BLACKLISTED_FOLDERS,
) {
    val context = LocalContext.current
    val (colorPalette, typography) = LocalAppearance.current
    val saver = listSaver(
        save = { it.toList() },
        restore = { mutableStateListOf<String>().apply { addAll(it) } }
    )
    val values = rememberSaveable( saver = saver ) {
        mutableStateListOf(*preference.value.toTypedArray())
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.width( 350.dp ),
        containerColor = colorPalette.background0,
        iconContentColor = colorPalette.accent,
        titleContentColor = colorPalette.text,
        textContentColor = colorPalette.textSecondary,
        confirmButton = {
            DialogConfirmButton( colorPalette = colorPalette ) {
                onDismissRequest()
                preference.update( values.toSet() )
            }
        },
        dismissButton = {
            DialogCancelButton(
                onClick = onDismissRequest,
                colorPalette = colorPalette
            )
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = typography.l.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight( 1f )
                )

                //<editor-fold desc="Add button">
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree()
                ) { uri ->
                    // Do nothing if no folder is selected
                    if( uri?.toString().isNullOrBlank() )
                        return@rememberLauncherForActivityResult

                    try {
                        // Because Uri returned by this activity is only valid for a single session,
                        // Saving this uri without appending read flag will not cause any issue,
                        // but will crash the app when this value is used to read
                        // display name, content, etc. later on.
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        values.add( uri.toString() )
                    } catch( err: SecurityException ) {
                        Logger.e( "", err, "BlacklistConfigDialog" )
                    }
                }
                IconButton(
                    onClick = {
                        val initialPath = DocumentsContract.buildDocumentUri(
                            "com.android.externalstorage.documents",
                            "primary:Music"
                        )
                        launcher.launch( initialPath )
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = colorPalette.accent
                    )
                ) {
                    Icon(
                        painter = painterResource( Res.drawable.create_new_folder ),
                        // Not clickable
                        contentDescription = stringResource( R.string.semantic_add_blacklist_entry )
                    )
                }
                //</editor-fold>
            }
        },
        text = {
            LazyColumn(
                Modifier.heightIn( max = SETTING_DIALOG_LIST_HEIGHT.dp )
            ) {
                items( values.reversed() ) { path ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = getFolderNameFromUri( context, path.toUri() ),
                                maxLines = 1,
                                overflow = TextOverflow.StartEllipsis,
                                fontSize = typography.s.fontSize,
                                color = colorPalette.textSecondary
                            )
                        },
                        trailingContent = {
                            IconButton(
                                onClick = { values.remove(path) }
                            ) {
                                Icon(
                                    painter = painterResource( Res.drawable.remove ),
                                    contentDescription = stringResource( Res.string.semantic_remove_path )
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = colorPalette.text,
                            trailingIconColor = colorPalette.red
                        )
                    )
                }
            }
        }
    )
}

@Composable
fun OtherSettings( paddingValues: PaddingValues ) {
    val context = LocalContext.current
    val scrollState = rememberLazyListState()

    val search = remember {
        SettingEntrySearch( scrollState, R.string.tab_miscellaneous, R.drawable.equalizer )
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
            header( R.string.on_device )
            entry( search, R.string.blacklisted_folders ) {
                var isDialogVisible by rememberSaveable { mutableStateOf(false) }

                SettingComponents.Entry(
                    onClick = { isDialogVisible = true },
                    title = stringResource( R.string.blacklisted_folders ),
                    subtitle = stringResource( R.string.edit_blacklist_for_on_device_songs ),
                )

                if( isDialogVisible )
                    BlacklistConfigDialog(
                        onDismissRequest = { isDialogVisible = false },
                        title = stringResource(R.string.blacklisted_folders)
                    )
            }
            entry( search, R.string.folders ) {
                SettingComponents.BooleanEntry(
                    preference = Preferences.HOME_SONGS_ON_DEVICE_SHOW_FOLDERS,
                    title = stringResource( R.string.folders ),
                    subtitle = stringResource( R.string.show_folders_in_on_device_page )
                )
            }

            header( R.string.androidheadunit )
            entry( search, R.string.extra_space ) {
                SettingComponents.BooleanEntry(
                    preference = Preferences.PLAYER_EXTRA_SPACE,
                    title = stringResource( R.string.extra_space )
                )
            }

            header(
                titleId = R.string.service_lifetime,
                subtitle = { stringResource( R.string.battery_optimizations_applied ) }
            )
            entry( search, R.string.keep_screen_on ) {
                SettingComponents.BooleanEntry(
                    preference = Preferences.KEEP_SCREEN_ON,
                    title = stringResource( R.string.keep_screen_on ),
                    subtitle = stringResource( R.string.prevents_screen_timeout )
                )
            }
            entry(
                search = search,
                titleId = R.string.ignore_battery_optimizations,
                additionalCheck = isAtLeastAndroid6
            ) {
                var isIgnoringBatteryOptimizations by remember {
                    mutableStateOf( context.isIgnoringBatteryOptimizations )
                }
                val activityResultLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) {
                        isIgnoringBatteryOptimizations = context.isIgnoringBatteryOptimizations
                    }
                val subtitle by remember { derivedStateOf {
                    if (isIgnoringBatteryOptimizations)
                        context.getString( R.string.already_unrestricted )
                    else
                        context.getString( R.string.disable_background_restrictions )
                }}

                SettingComponents.Entry(
                    title = stringResource( R.string.ignore_battery_optimizations ),
                    subtitle = subtitle,
                    onClick = {
                        try {
                            activityResultLauncher.launch(
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = "package:${context.packageName}".toUri()
                                }
                            )
                        } catch (e: ActivityNotFoundException) {
                            try {
                                activityResultLauncher.launch(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                )
                            } catch (e: ActivityNotFoundException) {
                                Toaster.i( R.string.not_find_battery_optimization_settings )
                            }
                        }
                    }
                )

                // TODO: ADD this to comment of "ignore optimization"
                // SettingComponents.Description( R.string.is_android12 )
            }

            header( R.string.parental_control )
            entry( search, R.string.parental_control ) {
                SettingComponents.BooleanEntry(
                    preference = Preferences.PARENTAL_CONTROL,
                    title = stringResource( R.string.parental_control ),
                    subtitle = stringResource( R.string.info_prevent_play_songs_with_age_limitation )
                )
            }

            debugSection( search )
        }
    }
}