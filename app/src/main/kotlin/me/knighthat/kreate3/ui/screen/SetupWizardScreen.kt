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

package me.knighthat.kreate3.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.KreateActivity
import me.knighthat.kreate3.KreateActivity.Companion.SETUP_WIZARD_CONTRAST_LEVEL
import me.knighthat.kreate3.KreateActivity.Companion.SETUP_WIZARD_DONE_ACTION
import me.knighthat.kreate3.KreateActivity.Companion.SETUP_WIZARD_PROFILE_NAME
import me.knighthat.kreate3.KreateActivity.Companion.SETUP_WIZARD_THEME_MODE
import me.knighthat.kreate3.KreateActivity.Companion.SETUP_WIZARD_TOS_CHECKSUM
import me.knighthat.kreate3.KreateApplication
import me.knighthat.kreate3.R
import me.knighthat.kreate3.backup.Backup
import me.knighthat.kreate3.backup.BackupException
import me.knighthat.kreate3.constant.ContrastLevel
import me.knighthat.kreate3.constant.ThemeMode
import me.knighthat.kreate3.ui.component.TileButton
import me.knighthat.kreate3.ui.theme.KreateTheme
import me.knighthat.kreate3.utils.AUDIO_PERMISSION
import me.knighthat.kreate3.utils.hasAudioPermission
import me.knighthat.kreate3.utils.hasNotificationPermission
import me.knighthat.kreate3.utils.shouldShowRequestPermissionRationale
import me.knighthat.kreate3.utils.verticalScrollbar
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest


// Same name the profile manager gives the profile it creates
private const val DEFAULT_PROFILE_NAME = "Default"

private const val LICENSE_URL = "https://opensource.org/license/gpl-3.0"

private const val PAGE_WELCOME = 0
private const val PAGE_PERMISSIONS = 1
private const val PAGE_IMPORT = 2
private const val PAGE_PROFILE = 3
private const val PAGE_TERMS = 4

private val PAGE_TITLES = listOf(
    R.string.screen_title_welcome,
    R.string.screen_title_permissions,
    R.string.screen_title_import,
    R.string.screen_title_basic_settings,
    R.string.screen_title_terms_of_service,
)

/**
 * Keeps the backup at [uri], it is restored when the app starts after setup.
 *
 * @return why it can't be restored, `null` when it will be
 */
private suspend fun Context.importBackup(uri: Uri): String? =
    try {
        val input = withContext(Dispatchers.IO) { contentResolver.openInputStream(uri) }
            ?: throw FileNotFoundException()
        Backup.stageWithoutRollback(this, input)
        null
    } catch (e: BackupException) {
        e.message
    } catch (_: IOException) {
        "Couldn't read the backup"
    } catch (_: SecurityException) {
        "Couldn't read the backup"
    }

private fun String.prettify() =
    lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun StepColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
private fun ListEntryTitle(text: String) =
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium
    )

@Composable
private fun ListEntrySubtitle(text: String) =
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = ListItemDefaults.colors().supportingTextColor.copy(alpha = .7f)
    )

@Composable
private fun Welcome() {
    @Composable
    fun Entry(
        headline: String,
        supportingText: String
    ) {
        ListItem(
            headlineContent = { ListEntryTitle(headline) },
            supportingContent = {
                if (supportingText.isNotBlank())
                    ListEntrySubtitle(supportingText)
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            ),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Dear User,", style = MaterialTheme.typography.headlineMedium)
            Text("A few quick steps and you are ready to listen. Here is what comes next:")

            Entry(stringResource(PAGE_TITLES[1]), "Control what Kreate can and cannot do or see.")
            Entry(
                stringResource(PAGE_TITLES[2]),
                "Bring over existing data from another machine or backup."
            )
            Entry(stringResource(PAGE_TITLES[3]), "Taylor the app to your taste.")
            Entry(stringResource(PAGE_TITLES[4]), "")
        }
    }
}

@Composable
private fun PermissionItem(
    title: String,
    subtitle: String,
    permission: String,
    granted: Boolean,
    onRationaleFailed: () -> Unit
) {
    var granted by rememberSaveable(granted) { mutableStateOf(granted) }
    val requestLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            granted = it
        }

    ListItem(
        headlineContent = { ListEntryTitle(title) },
        supportingContent = { ListEntrySubtitle(subtitle) },
        trailingContent = {
            val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            // Disable ripple effect, button is always guaranteed to do something, or visibly disabled
            CompositionLocalProvider(LocalRippleConfiguration provides null) {

                val context = LocalContext.current
                TextButton(
                    onClick = {
                        // After multiple denies, this check becomes true, bring
                        // user to settings page if that's the case
                        if (!context.shouldShowRequestPermissionRationale(permission))
                            onRationaleFailed()
                        else
                            requestLauncher.launch(permission)
                    },
                    enabled = !granted,
                    modifier = Modifier
                        .fillMaxWidth(.28f)
                        .drawBehind {
                            // Draw a vertical divider with 2 fading ends
                            val width = 1.dp.toPx()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        borderColor,
                                        Color.Transparent
                                    )
                                ),
                                size = Size(width, size.height)
                            )
                        }
                ) {
                    Text(if (granted) "Granted" else "Grant")
                }
            }
        }
    )
}

// POST_NOTIFICATIONS is only read when needsNotification (API 33+) is true
@SuppressLint("InlinedApi")
@Composable
private fun Permissions() {
    val context = LocalContext.current

    var audioGranted by remember { mutableStateOf(context.hasAudioPermission()) }
    var notificationGranted by remember {
        mutableStateOf(context.hasNotificationPermission())
    }

    StepColumn {
        Text("Kreate needs your permission for the following. You can skip any of them and allow it later.")

        PermissionItem(
            title = "Music",
            subtitle = "Read the audio files stored on this device to build your library",
            permission = AUDIO_PERMISSION,
            granted = audioGranted,
            onRationaleFailed = {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    .also(context::startActivity)
            }
        )
        PermissionItem(
            title = "Notifications",
            subtitle = "Allow app to inform you what is going on within the app",
            permission = Manifest.permission.POST_NOTIFICATIONS,
            granted = notificationGranted,
            onRationaleFailed = {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .also(context::startActivity)
            }
        )
    }

    // The user may flip the switch in system settings and come back
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver

            notificationGranted = context.hasNotificationPermission()
            audioGranted = context.hasAudioPermission()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * @param imported whether a backup is kept to be restored
 * @param importing whether the chosen backup is being read
 * @param message why the last chosen backup can't be restored
 * @param onImport called with the backup the user chose
 */
@Composable
private fun Import(
    imported: Boolean,
    importing: Boolean,
    message: String?,
    onImport: (Uri) -> Unit,
) {
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(onImport)
        }

    StepColumn {
        Text("Have a backup of Kreate? Choose it to bring your profiles, settings and library along. You can skip this.")

        val ready = imported && !importing
        // The theme has no green, so it is picked by how dark the theme is
        val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        TileButton(
            icon = if (ready) R.drawable.check else R.drawable.database,
            title = when {
                importing -> "Reading the backup…"
                ready -> "Backup ready"
                else -> "Restore a backup"
            },
            subtitle = if (ready) "It is restored once setup finishes, tap to choose another"
            else "Choose a backup made in Settings, Backup",
            onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream")) },
            busy = importing,
            colors = if (ready)
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (dark) Color(0xFF1E4D24) else Color(0xFFC6EFC0),
                    contentColor = if (dark) Color(0xFFB8F0B0) else Color(0xFF0A3A12),
                )
            else ButtonDefaults.filledTonalButtonColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Settings(
    name: String,
    onNameChange: (String) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    contrastLevel: ContrastLevel,
    onContrastLevelChange: (ContrastLevel) -> Unit,
) {
    StepColumn {
        Text("Leave the name empty to keep \"${DEFAULT_PROFILE_NAME}\". You can change all of this later.")

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Name") },
            placeholder = { Text(DEFAULT_PROFILE_NAME) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Theme", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // THUMBNAIL needs a playing song, it isn't a choice to make up front
            ThemeMode.entries.filter { it != ThemeMode.THUMBNAIL }.forEach {
                FilterChip(
                    selected = it == themeMode,
                    onClick = { onThemeModeChange(it) },
                    label = { Text(it.name.prettify()) },
                )
            }
        }

        Text("Contrast", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ContrastLevel.entries.forEach {
                FilterChip(
                    selected = it == contrastLevel,
                    onClick = { onContrastLevelChange(it) },
                    label = { Text(it.name.prettify()) },
                )
            }
        }
    }
}

@Composable
private fun TermOfService() {
    val resources = LocalResources.current
    val terms by produceState("") {
        value = withContext(Dispatchers.IO) {
            resources.openRawResource(R.raw.terms_of_service).bufferedReader().use { it.readText() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.weight(1f),
        ) {
            val scrollState = rememberScrollState()
            Text(
                text = terms,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .verticalScrollbar(scrollState)
                    .verticalScroll(scrollState)
                    .padding(12.dp),
            )
        }

        Text(
            text = buildAnnotatedString {
                append("Distributed under the ")
                withLink(LinkAnnotation.Url(LICENSE_URL)) { append("GPL 3.0 License") }
                append(".")
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Two chevrons around the title of the current step, with the progress of the wizard below.
 * The title slides in the direction of travel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WizardTopBar(page: Int) {
    Surface(
        modifier = Modifier.padding(WindowInsets.statusBars.asPaddingValues()),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = page,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        val forward = targetState > initialState
                        (slideInHorizontally { if (forward) it else -it } + fadeIn() togetherWith
                                slideOutHorizontally { if (forward) -it else it } + fadeOut())
                            .using(SizeTransform(clip = true))
                    },
                    label = "wizard title",
                ) {
                    Text(
                        text = stringResource(PAGE_TITLES[it]),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            val progress by animateFloatAsState(
                targetValue = (page + 1f) / PAGE_TITLES.size,
                animationSpec = tween(durationMillis = 600, easing = LinearOutSlowInEasing)
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun WizardBottomBar(
    page: Int,
    locked: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onSkip: () -> Unit,
    onDone: () -> Unit
) {
    Surface(
        modifier = Modifier.padding(WindowInsets.navigationBars.asPaddingValues()),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val canGoBack = page > 0
            val canGoForward = page != PAGE_TERMS

            OutlinedButton(
                onClick = if (canGoBack) onBack else onSkip,
                enabled = !locked,
                modifier = Modifier.weight(1f)
            ) {
                val text = if (canGoBack) "Go back" else "Skip"
                Text(text)
            }
            Button(
                onClick = if (canGoForward) onForward else onDone,
                enabled = !locked,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    when (page) {
                        PAGE_WELCOME -> "Get started"
                        PAGE_TERMS -> "Start listening"
                        else -> "Next"
                    }
                )
            }
        }
    }
}

private fun Context.termsChecksum(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    resources.openRawResource(R.raw.terms_of_service).use { input ->
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Asks the first questions of a new install: access to music, old data, profile name,
 * and the terms and license.
 *
 * Nothing is loaded or started while it is open: no preferences, no database, no music scan.
 * The answers are kept in memory and handed to [KreateActivity], which applies them under its
 * splash screen.
 */
@Composable
fun SetupWizardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var profileName by rememberSaveable { mutableStateOf("") }
    var themeMode by rememberSaveable { mutableStateOf(ThemeMode.AUTOMATIC_DYNAMIC) }
    var contrastLevel by rememberSaveable { mutableStateOf(ContrastLevel.NORMAL) }
    var imported by rememberSaveable { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importMessage by rememberSaveable { mutableStateOf<String?>(null) }

    // Shown live, so the choices on the profile page can be judged right away
    KreateTheme(themeMode, contrastLevel, Color.Unspecified) {
        val state = rememberPagerState { PAGE_TITLES.size }
        val page = state.currentPage

        // Imported data already carries a profile, so the profile page is skipped
        val next = if (page == PAGE_IMPORT && imported) PAGE_TERMS else page + 1
        val previous = if (page == PAGE_TERMS && imported) PAGE_IMPORT else page - 1

        fun goTo(target: Int) {
            scope.launch { state.animateScrollToPage(target) }
        }

        // Pages stay put while a backup is read, "next" depends on whether it can be restored
        BackHandler(enabled = page > PAGE_WELCOME || importing) {
            if (!importing) goTo(previous)
        }

        Scaffold(
            modifier = modifier,
            topBar = {
                WizardTopBar(page = page)
            },
            bottomBar = {
                WizardBottomBar(
                    page = page,
                    locked = importing,
                    onBack = { goTo(previous) },
                    onForward = { goTo(next) },
                    onSkip = { goTo(PAGE_TERMS) },
                    onDone = {
                        context.setupCompletedFile().createNewFile()

                        scope.launch {
                            val checksum = withContext(Dispatchers.IO) { context.termsChecksum() }
                            (context.applicationContext as KreateApplication).restart {
                                action = SETUP_WIZARD_DONE_ACTION
                                putExtra(SETUP_WIZARD_TOS_CHECKSUM, checksum)
                                // A restored backup brings its own profile and settings
                                if (imported) return@restart
                                putExtra(SETUP_WIZARD_PROFILE_NAME, profileName)
                                putExtra(SETUP_WIZARD_THEME_MODE, themeMode.name)
                                putExtra(SETUP_WIZARD_CONTRAST_LEVEL, contrastLevel.name)
                            }
                        }
                    }
                )
            }
        ) { paddingValues ->
            HorizontalPager(
                state = state,
                // Where "next" leads depends on the answers, so only the buttons move the pages
                userScrollEnabled = false,
                modifier = Modifier.padding(paddingValues),
            ) { page ->
                when (page) {
                    PAGE_WELCOME -> Welcome()
                    PAGE_PERMISSIONS -> Permissions()
                    PAGE_IMPORT -> Import(
                        imported = imported,
                        importing = importing,
                        message = importMessage,
                        onImport = { uri ->
                            importing = true
                            scope.launch {
                                importMessage = context.importBackup(uri)
                                // A failed one leaves the backup chosen before in place
                                if (importMessage == null) imported = true
                                importing = false
                            }
                        },
                    )
                    PAGE_PROFILE -> Settings(
                        name = profileName,
                        onNameChange = { profileName = it },
                        themeMode = themeMode,
                        onThemeModeChange = { themeMode = it },
                        contrastLevel = contrastLevel,
                        onContrastLevelChange = { contrastLevel = it },
                    )

                    PAGE_TERMS -> TermOfService()
                }
            }
        }
    }
}

fun Context.setupCompletedFile(): File = filesDir.resolve("setup_completed")