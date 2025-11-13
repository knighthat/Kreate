package it.fast4x.rimusic.ui.screens.profiles

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.Skeleton
import it.fast4x.rimusic.ui.components.themed.ConfirmationDialog
import it.fast4x.rimusic.ui.components.themed.DefaultDialog
import it.fast4x.rimusic.ui.components.themed.DialogTextButton
import it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import it.fast4x.rimusic.ui.components.themed.InputTextDialog
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.utils.center
import it.fast4x.rimusic.utils.medium
import it.fast4x.rimusic.utils.semiBold
import java.io.File
import androidx.core.content.edit
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.service.MyDownloadService
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import it.fast4x.rimusic.utils.intent
import kotlin.system.exitProcess

private const val PREFERENCES_BASE_FILENAME = "preferences"
private const val PRIVATE_PREFERENCES_BASE_FILENAME = "private_preferences"
private const val DEFAULT_PROFILE_NAME = "default"
private const val PROFILE_FILE_NAME = "Profiles_names.txt"

@Composable
fun ProfileScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberLazyListState()

    var showAddPopup by remember { mutableStateOf(false) }
    var showRemovePopup by remember { mutableStateOf(false) }
    var showErrorPopup by remember { mutableStateOf(false) }
    var showChangePopup by remember { mutableStateOf(false) }
    var removingProfile by remember { mutableStateOf("") }
    var profileToSwitch by remember { mutableStateOf("") }

    var profilesNames by remember {
        val file = File(context.filesDir, PROFILE_FILE_NAME)
        if (file.exists()) {
            mutableStateOf(file.readLines())
        } else {
            mutableStateOf(emptyList())
        }
    }

    Skeleton(
        navController,
        miniPlayer = miniPlayer,
        navBarContent = { item ->
//            item(0, stringResource(R.string.profiles), R.drawable.person)

        }
    ) {
        LazyColumn(
            state = scrollState,
            contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer)
        ) {
            item(key = "header", contentType = 0) {
                HeaderWithIcon(
                    title = stringResource(R.string.profiles),
                    iconId = R.drawable.person,
                    enabled = false,
                    showIcon = false,
                    modifier = Modifier,
                    onClick = {}
                )
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileItem(
                        stringResource(R.string._default),
                        removePopup = { item ->
                            removingProfile = item
                            showRemovePopup = true
                        },
                        isEnabled = Preferences.ACTIVE_PROFILE.value != DEFAULT_PROFILE_NAME
                    ) {
                        profileToSwitch = DEFAULT_PROFILE_NAME
                        showChangePopup = true
                    }
                    profilesNames.forEach { item ->
                        ProfileItem(
                            item,
                            removePopup = { item ->
                                removingProfile = item
                                showRemovePopup = true
                            },
                            isEnabled = Preferences.ACTIVE_PROFILE.value != item
                        ) {
                            profileToSwitch = item
                            showChangePopup = true
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                DialogTextButton(
                    text = stringResource(R.string.add_profile),
                    primary = true,
                    onClick = {
                        showAddPopup = true
                    }
                )
            }
        }

    }

    if (showAddPopup) {
        InputTextDialog(
            onDismiss = { showAddPopup = false },
            placeholder = stringResource(R.string.enter_profile_name),
            setValue = {
                if (it !in profilesNames) {
                    profilesNames = profilesNames + it
                    val file = File(context.filesDir, PROFILE_FILE_NAME)
                    file.writeText(profilesNames.joinToString("\n"))
                } else {
                    showErrorPopup = true
                }
            },
            title = stringResource(R.string.add_profile),
            value = ""
        )
    }

    if (showRemovePopup) {
        ConfirmationDialog(
            text = stringResource(R.string.remove_profile),
            onDismiss = { showRemovePopup = false },
            onConfirm = {
                profilesNames = profilesNames.filter { it != removingProfile }
                val file = File(context.filesDir, PROFILE_FILE_NAME)
                file.writeText(profilesNames.joinToString("\n"))
                deletePreferencesForProfile(context, removingProfile)
                deleteRoomDatabaseByName(context, "data_$removingProfile.db")
            }
        )
    }

    if (showErrorPopup) {
        DefaultDialog(
            onDismiss = { showErrorPopup = false },
            modifier = Modifier
        ) {
            BasicText(
                text = stringResource(R.string.this_profile_alreaty_exist),
                style = typography().xs.medium.center,
                modifier = Modifier
                    .padding(all = 16.dp)
            )

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
            ) {

                DialogTextButton(
                    text = stringResource(R.string.confirm),
                    primary = true,
                    onClick = {
                        showErrorPopup = false
                    }
                )
            }
        }
    }
    if (showChangePopup) {
        ConfirmationDialog(
            text = stringResource(R.string.profile_restart_required),
            onDismiss = { showChangePopup = false },
            onConfirm = {
                changeProfile(profileToSwitch)
            }
        )

    }
}


fun changeProfile(profile: String) {
    Preferences.ACTIVE_PROFILE.value = profile

    // Unload the preferences to save all changes
    Preferences.unload()

    appContext().stopService( appContext().intent<PlayerServiceModern>() )
    appContext().stopService( appContext().intent<MyDownloadService>() )

    // Close other activities
    (appContext() as? Activity)?.finishAffinity()

    // Close app with exit 0 notify that no problem occurred
    exitProcess( 0 )
}

fun deletePreferencesForProfile(
    context: Context,
    profileName: String
): Boolean {
    val plainName = PREFERENCES_BASE_FILENAME + "_$profileName"
    val privateName = PRIVATE_PREFERENCES_BASE_FILENAME + "_$profileName"

    val okPlain = deleteSharedPrefsByName(context, plainName)
    val okPrivate = deleteSharedPrefsByName(context, privateName)

    return okPlain && okPrivate
}

fun deleteSharedPrefsByName(context: Context, prefsName: String): Boolean {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        context.deleteSharedPreferences(prefsName)
    } else {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit(commit = true) { clear() }

        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        val xml = File(dir, "$prefsName.xml")
        val xmlBak = File(dir, "$prefsName.xml.bak")

        var ok = true
        if (xml.exists()) ok = xml.delete() && ok
        if (xmlBak.exists()) ok = xmlBak.delete() && ok
        ok
    }
}

fun deleteRoomDatabaseByName(context: Context, dbName: String): Boolean {
    val primaryOk = context.deleteDatabase(dbName)

    val dbFile = context.getDatabasePath(dbName)
    var ok = primaryOk
    listOf(
        dbFile,                                   // "…/app_db__{id}"
        File(dbFile.path + "-journal"),   // mode journal (ישן)
        File(dbFile.path + "-wal"),       // write-ahead log
        File(dbFile.path + "-shm")        // shared memory
    ).forEach { f ->
        if (f.exists()) ok = f.delete() && ok
    }
    return ok
}

@Composable
fun ProfileItem(
    title: String,
    modifier: Modifier = Modifier,
    removePopup: (String) -> Unit,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .clickable(enabled = isEnabled, onClick = onClick)
            .alpha(if (isEnabled) 1f else 0.5f)
            //.padding(start = 16.dp)
            //.padding(all = 16.dp)
            .padding(all = 12.dp)
            .fillMaxWidth(),
    ) {
        BasicText(
            text = title,
            style = typography().s.semiBold.copy(color = colorPalette().text),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp, vertical = 4.dp)
        )
        if (isEnabled && title != DEFAULT_PROFILE_NAME) {
            Icon(
                painter = painterResource(R.drawable.trash),
                contentDescription = null,
                tint = Color.Red,
                modifier = Modifier.clickable {
                    removePopup(title)

                }
            )
        }
    }
}
