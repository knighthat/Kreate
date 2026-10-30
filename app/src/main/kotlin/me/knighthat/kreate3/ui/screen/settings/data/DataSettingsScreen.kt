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

package me.knighthat.kreate3.ui.screen.settings.data

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.repository.UserRepository
import me.knighthat.kreate3.preferences.GlobalPreferences
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.Profile
import me.knighthat.kreate3.preferences.ProfileManager
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.StorageUsage
import me.knighthat.kreate3.ui.component.StorageUse
import me.knighthat.kreate3.ui.component.fileSize
import me.knighthat.kreate3.ui.screen.settings.DISABLED_ALPHA
import me.knighthat.kreate3.ui.screen.settings.NumberSetting
import me.knighthat.kreate3.ui.screen.settings.SettingsPage
import me.knighthat.kreate3.ui.screen.settings.SettingsSection
import me.knighthat.kreate3.ui.screen.settings.SwitchSetting
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle as collectPreference

private const val MIB = 1024L * 1024
private const val GIB = 1024 * MIB

/** Bytes in a MiB while [binary], in a MB otherwise, see [Preferences.BINARY_SIZES]. */
private fun mega(binary: Boolean): Long = if (binary) MIB else 1_000_000

/** Bytes in a GiB while [binary], in a GB otherwise. */
private fun giga(binary: Boolean): Long = if (binary) GIB else 1_000_000_000

/** The largest size that can be typed, in MiB or MB: 4 TiB or 4 TB. */
private fun maxMega(binary: Boolean): Long = (giga(binary) / mega(binary)).let { 4 * it * it }

/** The sizes the image disk cache's slider stops at, it is small. */
private fun imageCacheSizes(binary: Boolean): List<Long> {
    val mega = mega(binary)
    return listOf(
        0L, 32 * mega, 64 * mega, 128 * mega, 256 * mega, 512 * mega, giga(binary), Long.MAX_VALUE,
    )
}

/** The sizes the song cache's slider stops at. */
private fun songCacheSizes(binary: Boolean): List<Long> =
    listOf(0L) + listOf(1, 2, 4, 8, 16, 32, 64).map { it * giga(binary) } + Long.MAX_VALUE

/** The sizes the song downloads' slider stops at. */
private fun songDownloadSizes(binary: Boolean): List<Long> =
    listOf(0L) + listOf(1, 2, 4, 8, 16, 32, 64, 128, 256).map { it * giga(binary) } +
            Long.MAX_VALUE

/**
 * What the app keeps on the device: how full the device is and the app's part of it, the room the
 * image disk cache, the song cache and the song downloads take with their sizes, which the default
 * profile can also wipe, then how songs download and the image memory cache. The sizes and the
 * memory cache are [GlobalPreferences], only the default profile changes them.
 */
@Composable
fun DataSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DataSettingsViewModel = koinViewModel(),
    profileManager: ProfileManager = koinInject(),
    userRepository: UserRepository = koinInject(),
) {
    val activeId by profileManager.activeId.collectAsStateWithLifecycle()
    val defaultUser by remember(userRepository) { userRepository.find(Profile.DEFAULT_ID) }
        .collectAsStateWithLifecycle(null)
    val deviceStorage by viewModel.deviceStorage.collectAsStateWithLifecycle()
    val imageCache by viewModel.imageCache.collectAsStateWithLifecycle()
    val songCache by viewModel.songCache.collectAsStateWithLifecycle()
    val songDownloads by viewModel.songDownloads.collectAsStateWithLifecycle()
    val imageCacheSize by GlobalPreferences.IMAGE_DISK_CACHE.collectPreference()
    val songCacheSize by GlobalPreferences.MEDIA3_CACHE_SIZE.collectPreference()
    val songDownloadsSize by GlobalPreferences.MEDIA3_DOWNLOAD_SIZE.collectPreference()
    val parallelDownloads by Preferences.DOWNLOAD_PARALLEL_COUNT.collectPreference()
    val downloadRetries by Preferences.DOWNLOAD_RETRY_COUNT.collectPreference()
    val downloadWifiOnly by Preferences.DOWNLOAD_WIFI_ONLY.collectPreference()
    val downloadOnLowBattery by Preferences.DOWNLOAD_ON_LOW_BATTERY.collectPreference()
    val downloadLyrics by Preferences.DOWNLOAD_LYRICS.collectPreference()
    val memoryCache by GlobalPreferences.IMAGE_MEMORY_CACHE_PERCENT.collectPreference()
    val binarySizes by Preferences.BINARY_SIZES.collectPreference()

    // Caches and downloads hold every profile's songs, only the default one sizes or wipes them
    val defaultProfile = activeId == Profile.DEFAULT_ID
    DataSettingsContent(
        deviceStorage = deviceStorage,
        imageCache = imageCache,
        songCache = songCache,
        songDownloads = songDownloads,
        imageCacheSize = imageCacheSize,
        songCacheSize = songCacheSize,
        songDownloadsSize = songDownloadsSize,
        parallelDownloads = parallelDownloads,
        downloadRetries = downloadRetries,
        downloadWifiOnly = downloadWifiOnly,
        downloadOnLowBattery = downloadOnLowBattery,
        downloadLyrics = downloadLyrics,
        memoryCache = memoryCache,
        binarySizes = binarySizes,
        appWide = defaultProfile,
        defaultProfileName = defaultUser?.name,
        onClearImageCache = if (defaultProfile) ({ viewModel.clearImageCache(it) }) else null,
        onClearSongCache = if (defaultProfile) ({ viewModel.clearSongCache() }) else null,
        onDeleteAllDownloads = if (defaultProfile) viewModel::deleteAllDownloads else null,
        onRestart = { viewModel.restart() },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * @param imageCacheSize in bytes, like [songCacheSize] and [songDownloadsSize]
 * @param memoryCache the part of the app's memory, `0.15` for 15%
 * @param binarySizes [Preferences.BINARY_SIZES]
 * @param appWide whether the profile may change the [GlobalPreferences] here, the sizes and
 * [memoryCache]; shown read-only otherwise
 * @param defaultProfileName named in the notes of what only the default profile changes, `null`
 * while it loads and the notes stay hidden
 * @param onClearImageCache `null` while the profile may not wipe, like [onClearSongCache] and
 * [onDeleteAllDownloads]; given whether to clear the memory cache too
 * @param onRestart restarts the app, for a smaller cache to apply
 */
@Composable
private fun DataSettingsContent(
    deviceStorage: DeviceStorage?,
    imageCache: StorageUse?,
    songCache: StorageUse?,
    songDownloads: StorageUse?,
    imageCacheSize: Long,
    songCacheSize: Long,
    songDownloadsSize: Long,
    parallelDownloads: Int,
    downloadRetries: Int,
    downloadWifiOnly: Boolean,
    downloadOnLowBattery: Boolean,
    downloadLyrics: Boolean,
    memoryCache: Double,
    binarySizes: Boolean,
    appWide: Boolean,
    defaultProfileName: String?,
    onClearImageCache: ((memory: Boolean) -> Unit)?,
    onClearSongCache: (() -> Unit)?,
    onDeleteAllDownloads: (() -> Unit)?,
    onRestart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(
        stringResource(R.string.screen_title_data),
        onBack,
        modifier,
        restartApp = true
    ) {
        val resources = LocalResources.current
        val size = { bytes: Long -> resources.fileSize(bytes, binarySizes) }
        val defaultProfileOnly = defaultProfileName?.let {
            stringResource(
                R.string.setting_description_default_profile_only,
                stringResource(R.string.quoted, it)
            )
        }
        val cacheWarning = { used: String, bytes: Long ->
            if (bytes <= 0) resources.getString(R.string.dialog_body_turn_off_cache, used)
            else resources.getString(R.string.dialog_body_shrink_cache, used, size(bytes))
        }

        SettingsSection(stringResource(R.string.setting_header_storage))
        deviceStorage?.let {
            DeviceStorageUsage(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        StorageSetting(
            title = stringResource(R.string.setting_title_disk_cache),
            description = stringResource(R.string.setting_description_disk_cache),
            note = defaultProfileOnly.takeUnless { appWide },
            size = imageCacheSize,
            sizes = imageCacheSizes(binarySizes),
            binary = binarySizes,
            storage = imageCache,
            onSave = { GlobalPreferences.IMAGE_DISK_CACHE.update(it) },
            shrinkWarning = cacheWarning,
            onRestart = onRestart,
            wipe = onClearImageCache?.let {
                Wipe(
                    stringResource(R.string.dialog_title_clear_image_cache),
                    stringResource(R.string.dialog_body_clear_image_cache_1),
                    stringResource(R.string.action_clear),
                    it,
                    option = stringResource(R.string.dialog_body_clear_image_cache_2),
                )
            },
        )
        StorageSetting(
            title = stringResource(R.string.setting_title_song_cache),
            description = stringResource(R.string.setting_description_song_cache),
            note = defaultProfileOnly.takeUnless { appWide },
            size = songCacheSize,
            sizes = songCacheSizes(binarySizes),
            binary = binarySizes,
            storage = songCache,
            onSave = { GlobalPreferences.MEDIA3_CACHE_SIZE.update(it) },
            shrinkWarning = cacheWarning,
            onRestart = onRestart,
            wipe = onClearSongCache?.let {
                Wipe(
                    stringResource(R.string.dialog_title_clear_song_cache),
                    stringResource(R.string.dialog_body_clear_song_cache),
                    stringResource(R.string.action_clear),
                    { _ -> it() },
                )
            },
        )
        StorageSetting(
            title = stringResource(R.string.setting_title_song_downloads),
            description = stringResource(R.string.setting_description_song_downloads),
            note = defaultProfileOnly.takeUnless { appWide },
            size = songDownloadsSize,
            sizes = songDownloadSizes(binarySizes),
            binary = binarySizes,
            storage = songDownloads,
            onSave = { GlobalPreferences.MEDIA3_DOWNLOAD_SIZE.update(it) },
            // Downloads apply at once, nothing to restart for
            shrinkWarning = { used, bytes ->
                if (bytes <= 0) resources.getString(R.string.dialog_body_turn_off_downloads, used)
                else resources.getString(R.string.dialog_body_shrink_downloads, used, size(bytes))
            },
            onRestart = null,
            wipe = onDeleteAllDownloads?.let {
                Wipe(
                    stringResource(R.string.dialog_title_delete_every_download),
                    stringResource(R.string.dialog_body_delete_every_download),
                    stringResource(R.string.action_delete),
                    { _ -> it() },
                )
            },
        )

        SettingsSection(stringResource(R.string.setting_header_downloads))
        NumberSetting(
            title = stringResource(R.string.setting_title_parallel_downloads),
            value = parallelDownloads.toLong(),
            min = 1,
            max = 6,
            onSave = { Preferences.DOWNLOAD_PARALLEL_COUNT.update(it.toInt()) },
            note = {
                stringResource(
                    R.string.setting_description_parallel_downloads,
                    pluralStringResource(R.plurals.song, it.toInt(), it),
                )
            },
        )
        NumberSetting(
            title = stringResource(R.string.setting_title_download_retries),
            value = downloadRetries.toLong(),
            min = 0,
            max = 20,
            onSave = { Preferences.DOWNLOAD_RETRY_COUNT.update(it.toInt()) },
            note = {
                stringResource(
                    R.string.setting_description_download_retries,
                    pluralStringResource(R.plurals.time, it.toInt(), it),
                )
            },
        )
        SwitchSetting(
            title = stringResource(R.string.setting_title_download_wifi_only),
            checked = downloadWifiOnly,
            onCheckedChange = { Preferences.DOWNLOAD_WIFI_ONLY.update(it) },
            summary = stringResource(R.string.setting_description_download_wifi_only),
        )
        SwitchSetting(
            title = stringResource(R.string.setting_title_download_on_low_battery),
            checked = downloadOnLowBattery,
            onCheckedChange = { Preferences.DOWNLOAD_ON_LOW_BATTERY.update(it) },
            summary = stringResource(
                if (downloadOnLowBattery) R.string.setting_description_download_on_low_battery_on
                else R.string.setting_description_download_on_low_battery_off
            ),
        )
        SwitchSetting(
            title = stringResource(R.string.setting_title_download_lyrics),
            checked = downloadLyrics,
            onCheckedChange = { Preferences.DOWNLOAD_LYRICS.update(it) },
            summary = stringResource(R.string.setting_description_download_lyrics),
        )

        SettingsSection(stringResource(R.string.setting_header_images))
        NumberSetting(
            title = stringResource(R.string.setting_title_memory_cache),
            value = (memoryCache * 100).roundToLong(),
            min = 5,
            max = 50,
            onSave = { GlobalPreferences.IMAGE_MEMORY_CACHE_PERCENT.update(it / 100.0) },
            note = {
                val description = stringResource(
                    R.string.setting_description_memory_cache,
                    stringResource(R.string.percent, it)
                )
                if (appWide || defaultProfileOnly == null) description
                else "$description\n$defaultProfileOnly"
            },
            enabled = appWide,
        )
    }
}

/**
 * Wiping what a [StorageSetting] keeps, asked with [title] and [body] first.
 *
 * @param option a box to tick under [body], whether it is ticked goes to [onConfirm]
 */
private class Wipe(
    val title: String,
    val body: String,
    val action: String,
    val onConfirm: (option: Boolean) -> Unit,
    val option: String? = null,
)

/**
 * The size of something kept on the device, with the room it takes ([storage]) under the
 * [description]. A tap opens [SizeDialog], a size smaller than what is kept is saved only once
 * the user agrees to [shrinkWarning], and with [onRestart] the app can restart right away for it
 * to apply. With [wipe], a button at the end empties it once confirmed.
 *
 * @param note a line under [description] while the size can't be changed, the row is dimmed and
 * taps do nothing then; the bar stays as it is
 * @param size in bytes, `0` while it is off, [Long.MAX_VALUE] without a limit
 * @param sizes the sizes the slider stops at, from off to unlimited
 * @param binary [Preferences.BINARY_SIZES], typed in MiB while on, in MB otherwise
 * @param shrinkWarning the warning for a size smaller than what is kept: what is kept as text and
 * the new size in bytes, `0` to turn it off
 */
@Composable
private fun StorageSetting(
    title: String,
    description: String,
    note: String?,
    size: Long,
    sizes: List<Long>,
    binary: Boolean,
    storage: StorageUse?,
    onSave: (Long) -> Unit,
    shrinkWarning: (used: String, size: Long) -> String,
    onRestart: (() -> Unit)?,
    wipe: Wipe?,
) {
    var editing by remember { mutableStateOf(false) }
    var shrinkTo by remember { mutableStateOf<Long?>(null) }
    var wiping by remember { mutableStateOf(false) }

    // The bar spans the whole row, under the button too
    Column(Modifier.clickable(enabled = note == null) { editing = true }) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(note?.let { "$description\n$it" } ?: description) },
            trailingContent = wipe?.let {
                {
                    TextButton(
                        onClick = { wiping = true },
                        enabled = storage != null && storage.allBytes > 0,
                    ) { Text(it.action) }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = if (note == null) Modifier else Modifier.alpha(DISABLED_ALPHA),
        )
        storage?.let {
            StorageUsage(it, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
        }
    }

    if (editing)
        SizeDialog(
            title = title,
            size = size,
            sizes = sizes,
            binary = binary,
            onSave = { picked ->
                editing = false
                val used = storage?.allBytes ?: 0
                if (picked != size && picked < used) shrinkTo = picked else onSave(picked)
            },
            onDismiss = { editing = false },
        )

    shrinkTo?.let { picked ->
        AlertDialog(
            onDismissRequest = { shrinkTo = null },
            title = {
                Text(
                    stringResource(
                        if (picked <= 0) R.string.dialog_title_turn_off
                        else R.string.dialog_title_shrink_cache
                    )
                )
            },
            text = { Text(shrinkWarning(storage?.all.orEmpty(), picked)) },
            // The dialog's button row wraps, both buttons fit beside Cancel or under it
            confirmButton = {
                TextButton(onClick = {
                    shrinkTo = null
                    onSave(picked)
                }) { Text(stringResource(R.string.action_apply)) }
                if (onRestart != null)
                    TextButton(onClick = {
                        shrinkTo = null
                        onSave(picked)
                        onRestart()
                    }) { Text(stringResource(R.string.action_apply_and_restart)) }
            },
            dismissButton = {
                TextButton(onClick = { shrinkTo = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (wiping && wipe != null) {
        var ticked by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { wiping = false },
            title = { Text(wipe.title) },
            text = {
                Column {
                    Text(wipe.body)
                    wipe.option?.let {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .toggleable(ticked, role = Role.Checkbox) { ticked = it },
                        ) {
                            Checkbox(checked = ticked, onCheckedChange = null)
                            Text(it, Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    wiping = false
                    wipe.onConfirm(ticked)
                }) { Text(wipe.action) }
            },
            dismissButton = {
                TextButton(onClick = { wiping = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * Picks a size: typed in MiB (MB unless [binary]), or slid to one of [sizes], from off to
 * unlimited. Typing moves the slider to the largest of [sizes] not above the number, sliding
 * writes the size in the field.
 *
 * @param size in bytes, see [StorageSetting]
 */
@Composable
private fun SizeDialog(
    title: String,
    size: Long,
    sizes: List<Long>,
    binary: Boolean,
    onSave: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val resources = LocalResources.current
    val mega = mega(binary)
    val max = maxMega(binary)
    // A size not of whole units (1 GiB in MB) is cut in the field, kept as it was until edited
    val shown = if (size == Long.MAX_VALUE) "" else (size / mega).toString()
    var unlimited by remember { mutableStateOf(size == Long.MAX_VALUE) }
    var text by remember { mutableStateOf(shown) }
    val picked: Long? = when {
        unlimited -> Long.MAX_VALUE
        text == shown && size != Long.MAX_VALUE -> size
        else -> text.toLongOrNull()?.takeIf { it in 0..max }?.times(mega)
    }
    // Where the typed size falls between the stops, the slider moves it to a stop
    var position by remember(picked) {
        val index = when (picked) {
            null -> 0
            Long.MAX_VALUE -> sizes.lastIndex
            else -> sizes.indexOfLast { it <= picked }.coerceIn(0, sizes.lastIndex - 1)
        }
        mutableFloatStateOf(index.toFloat())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { typed ->
                        text = typed.filter(Char::isDigit)
                        unlimited = false
                    },
                    label = {
                        Text(
                            stringResource(
                                if (binary) R.string.setting_title_size_in_mib
                                else R.string.setting_title_size_in_mb
                            )
                        )
                    },
                    placeholder = { Text(stringResource(R.string.status_unlimited)) },
                    singleLine = true,
                    isError = picked == null,
                    supportingText = {
                        Text(
                            if (picked == null)
                                stringResource(R.string.setting_description_between, 0, max)
                            else sizeLabel(resources, picked, binary)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Slider(
                    value = position,
                    onValueChange = {
                        position = it
                        val stop = sizes[it.roundToInt().coerceIn(sizes.indices)]
                        unlimited = stop == Long.MAX_VALUE
                        text = if (unlimited) "" else (stop / mega).toString()
                    },
                    valueRange = 0f..sizes.lastIndex.toFloat(),
                    steps = (sizes.size - 2).coerceAtLeast(0),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = picked != null,
                onClick = { picked?.let(onSave) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * [bytes] as a size setting reads: off, unlimited, whole GiB or MiB (GB or MB unless [binary]),
 * or a short size when it isn't of whole units.
 */
private fun sizeLabel(resources: Resources, bytes: Long, binary: Boolean): String {
    val giga = giga(binary)
    val mega = mega(binary)
    return when {
        bytes <= 0 -> resources.getString(R.string.status_off)
        bytes == Long.MAX_VALUE -> resources.getString(R.string.status_unlimited)
        bytes % giga == 0L ->
            resources.getString(if (binary) R.string.gib else R.string.gb, "${bytes / giga}")

        bytes % mega == 0L ->
            resources.getString(if (binary) R.string.mib else R.string.mb, "${bytes / mega}")

        else -> resources.fileSize(bytes, binary)
    }
}

@ScreenPreviews
@Composable
private fun DataSettingsPreview() = PreviewTheme { DataSettingsSample(defaultProfile = true) }

/**
 * Another profile than the default one: nothing can be wiped, no Clear or Delete buttons, and the
 * sizes and the memory cache are read-only.
 */
@ScreenPreviews
@Composable
private fun DataSettingsOtherProfilePreview() = PreviewTheme {
    DataSettingsSample(defaultProfile = false)
}

@Composable
private fun DataSettingsSample(defaultProfile: Boolean) {
    DataSettingsContent(
        deviceStorage = previewDeviceStorage(),
        imageCache = StorageUse(
            96_000_000, 96_000_000, 128 * MIB, 128 * MIB, "96 MB", "96 MB", "134 MB",
        ),
        songCache = StorageUse(
            6_200_000_000, 6_200_000_000, Long.MAX_VALUE, 40_000_000_000,
            "6.2 GB", "6.2 GB", "Unlimited",
        ),
        songDownloads = StorageUse(
            1_400_000_000, 2_900_000_000, 4 * GIB, 4 * GIB, "1.4 GB", "2.9 GB", "4.3 GB",
        ),
        imageCacheSize = 128 * MIB,
        songCacheSize = Long.MAX_VALUE,
        songDownloadsSize = 4 * GIB,
        parallelDownloads = 2,
        downloadRetries = 5,
        downloadWifiOnly = false,
        downloadOnLowBattery = true,
        downloadLyrics = false,
        memoryCache = 0.15,
        binarySizes = true,
        appWide = defaultProfile,
        defaultProfileName = "Default",
        onClearImageCache = if (defaultProfile) ({}) else null,
        onClearSongCache = if (defaultProfile) ({}) else null,
        onDeleteAllDownloads = if (defaultProfile) ({}) else null,
        onRestart = {},
        onBack = {},
    )
}
