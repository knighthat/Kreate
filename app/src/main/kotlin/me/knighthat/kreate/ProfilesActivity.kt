/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.knighthat.kreate.constant.ContrastLevel
import me.knighthat.kreate.constant.ThemeMode
import me.knighthat.kreate.database.model.User
import me.knighthat.kreate.database.repository.UserRepository
import me.knighthat.kreate.preferences.Profile
import me.knighthat.kreate.preferences.ProfileManager
import me.knighthat.kreate.ui.theme.KreateTheme
import org.koin.android.ext.android.inject

/**
 * Lists the profiles and lets you add, switch to and delete them.
 */
class ProfilesActivity : ComponentActivity() {

    private lateinit var themeMode: ThemeMode
    private lateinit var contrastLevel: ContrastLevel

    private val profileManager: ProfileManager by inject()
    private val profileUserSync: ProfileUserSync by inject()
    private val userRepository: UserRepository by inject()

    @Composable
    private fun ProfileRow(
        user: User,
        isActive: Boolean,
        onSwitch: () -> Unit,
        onDelete: () -> Unit,
    ) {
        val context = LocalContext.current
        // The active profile shows since when it is active, the others when they were last used
        val subtitle = if (isActive)
            user.activeSince?.let {
                "Active since " + DateUtils.formatDateTime(
                    context,
                    it.toEpochMilliseconds(),
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_ALL,
                )
            }
        else
            user.lastSeen?.let {
                "Last seen " + DateUtils.getRelativeTimeSpanString(it.toEpochMilliseconds())
            }

        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Stripe marks the active profile, inactive ones keep the space so names stay aligned
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent),
            )
            ListItem(
                headlineContent = { Text(user.name) },
                supportingContent = subtitle?.let { { Text(it) } },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onSwitch, enabled = !isActive) { Text("Switch") }
                        // The default profile can't be deleted
                        TextButton(
                            onClick = onDelete,
                            enabled = user.id != Profile.DEFAULT_ID
                        ) { Text("Delete") }
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    @Composable
    private fun SearchOrAdd(
        text: String,
        onTextChange: (String) -> Unit,
        onCreate: (String) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text("Search or add profile") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    onCreate(text.trim())
                    onTextChange("")
                },
                enabled = text.isNotBlank(),
            ) { Text("Add") }
        }
    }

    @Composable
    private fun Profiles(modifier: Modifier = Modifier) {
        val scope = rememberCoroutineScope()
        val ids by profileManager.ids.collectAsState(emptySet())
        val activeId by profileManager.activeId.collectAsState()
        val users by userRepository.all().collectAsState(emptyList())
        var text by rememberSaveable { mutableStateOf("") }
        val shown =
            users.filter { it.id in ids && it.name.contains(text.trim(), ignoreCase = true) }

        LazyColumn(modifier = modifier.fillMaxSize()) {
            item {
                SearchOrAdd(
                    text = text,
                    onTextChange = { text = it },
                    onCreate = { name -> scope.launch { profileUserSync.createProfile(name) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
                HorizontalDivider()
            }
            items(shown, key = User::id) { user ->
                ProfileRow(
                    user = user,
                    isActive = user.id == activeId,
                    onSwitch = { scope.launch { profileManager.switchTo(user.id) } },
                    // Also removes the user from the database, ProfileManager alone would leave it behind
                    onDelete = { scope.launch { profileUserSync.deleteProfile(user.id) } },
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Given by the caller, not observed. Switching profile here changes the preferences, following
        // them would repaint this screen in the new profile's colors while it is still in use.
        // The screen behind this one follows them and shows the new colors when we return.
        themeMode = intent.getStringExtra(EXTRA_THEME_MODE)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.AUTOMATIC
        contrastLevel = intent.getStringExtra(EXTRA_CONTRAST_LEVEL)
            ?.let { name -> ContrastLevel.entries.firstOrNull { it.name == name } }
            ?: ContrastLevel.NORMAL
    }

    override fun onStart() {
        super.onStart()

        setContent {
            KreateTheme(
                themeMode = themeMode,
                contrastLevel = contrastLevel,
                colorSeed = Color.Unspecified,
            ) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Profiles(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    companion object {

        private const val EXTRA_THEME_MODE = "theme_mode"
        private const val EXTRA_CONTRAST_LEVEL = "contrast_level"

        /**
         * Starts the screen with the colors it keeps for its whole life.
         */
        fun intent(context: Context, themeMode: ThemeMode, contrastLevel: ContrastLevel) =
            Intent(context, ProfilesActivity::class.java)
                .putExtra(EXTRA_THEME_MODE, themeMode.name)
                .putExtra(EXTRA_CONTRAST_LEVEL, contrastLevel.name)
    }
}
