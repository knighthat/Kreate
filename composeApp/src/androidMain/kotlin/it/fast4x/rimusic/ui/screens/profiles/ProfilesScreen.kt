package it.fast4x.rimusic.ui.screens.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.kreate.android.R
import app.kreate.android.themed.common.component.settings.SettingEntrySearch
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.Skeleton
import it.fast4x.rimusic.ui.components.themed.ConfirmationDialog
import it.fast4x.rimusic.ui.components.themed.DefaultDialog
import it.fast4x.rimusic.ui.components.themed.DialogTextButton
import it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import it.fast4x.rimusic.ui.components.themed.InputTextDialog
import it.fast4x.rimusic.ui.components.themed.StringListDialog
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.utils.center
import it.fast4x.rimusic.utils.color
import it.fast4x.rimusic.utils.medium
import it.fast4x.rimusic.utils.semiBold
import java.io.File

@Composable
fun ProfileScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberLazyListState()

    var showPopup by remember { mutableStateOf(false) }
    var showAddPopup by remember { mutableStateOf(false) }
    var showRemovePopup by remember { mutableStateOf(false) }
    var showErrorPopup by remember { mutableStateOf(false) }
    var removingProfile by remember { mutableStateOf("") }

    var profilesNames by remember {
        val file = File(context.filesDir, "Profiles_names.txt")
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
//            item(0, stringResource(R.string.history), R.drawable.history)

        }
    ) {
        LazyColumn(
            state = scrollState,
            contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer)
        ) {
            item(key = "header", contentType = 0) {
                HeaderWithIcon(
                    title = "Profiles",
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
                        }
                    ) {
                        //TODO switch profile
                    }
                    profilesNames.forEach { item ->
                        ProfileItem(
                            item,
                            removePopup = { item ->
                                removingProfile = item
                                showRemovePopup = true
                            }
                        ) {
                            //TODO switch profile
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                DialogTextButton(
                    text = "Add profile",
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
            placeholder = "Enter profile name",
            setValue = {
                if (it !in profilesNames) {
                    profilesNames = profilesNames + it
                    val file = File(context.filesDir, "Profiles_names.txt")
                    file.writeText(profilesNames.joinToString("\n"))
                } else {
                    showErrorPopup = true
                }
            },
            title = "Add profile",
            value = ""
        )
    }

    if (showRemovePopup) {
        ConfirmationDialog(
            text = "Remove profile",
            onDismiss = { showRemovePopup = false },
            onConfirm = {
                profilesNames = profilesNames.filter { it != removingProfile }
                val file = File(context.filesDir, "Blacklisted_paths.txt")
                file.writeText(profilesNames.joinToString("\n"))
            }
        )
    }

    if (showErrorPopup) {
        DefaultDialog(
            onDismiss = {showErrorPopup = false},
            modifier = Modifier
        ) {
            BasicText(
                text = "Profile already exist",
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
    if (showPopup) {
        InputTextDialog(
            onDismiss = { showPopup = false },
            title = "new profile",
            value = "",
            placeholder = "Enter a profile name",
            setValue = { name ->
                profilesNames = profilesNames + name
                val file = File(context.filesDir, "Profiles_names.txt")
                file.writeText(profilesNames.joinToString("\n"))
            }
        )
    }
}

fun LazyListScope.entry(
    content: @Composable LazyItemScope.() -> Unit
) {
    item { content.invoke(this) }
}


fun saveNewProfile(name: String) {
    println(name)
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
            .clickable(enabled = true, onClick = { })
            .alpha(if (true) 1f else 0.5f)
//            .padding(start = 16.dp)
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
        Icon(
            painter = painterResource(R.drawable.trash),
            contentDescription = null,
            tint = Color.Red,
            modifier = Modifier.clickable {
                removePopup(title)
            }
        )
    }
//    Row(
//        horizontalArrangement = Arrangement.spacedBy(16.dp),
//        verticalAlignment = Alignment.CenterVertically,
//        modifier = modifier
//            .clickable(enabled = isEnabled, onClick = onClick)
//            .alpha(if (isEnabled) 1f else 0.5f)
//            .padding(start = 16.dp)
//            //.padding(all = 16.dp)
//            .padding(all = 12.dp)
//            .fillMaxWidth()
//    ) {
//        BasicText(
//            text = title,
//            style = typography().xs.semiBold.copy(color = colorPalette().text),
//            modifier = Modifier
//                .padding(bottom = 4.dp, top = 4.dp)
//        )
//    }

}
