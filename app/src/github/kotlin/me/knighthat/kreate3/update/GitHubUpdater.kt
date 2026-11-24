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

package me.knighthat.kreate3.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import androidx.core.content.IntentCompat
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.BuildConfig
import me.knighthat.kreate3.constant.AutoUpdateMode
import me.knighthat.kreate3.preferences.GlobalPreferences
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.utils.IS_ANDROID_12_AND_UP
import me.knighthat.kreate3.utils.IS_ANDROID_8_AND_UP
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

private const val TAG = "GitHubUpdater"

/** The latest stable release of the official repository; GitHub leaves out drafts and betas. */
private const val LATEST_URL = "https://api.github.com/repos/knighthat/kreate/releases/latest"

/** How long after a check the app checks again on its own. */
private val INTERVAL: Duration = 1.days

/**
 * Updates the github build from the repository's GitHub releases: one request to [LATEST_URL] a
 * day at most when the app comes on screen ([checkOnOpen]), plus each check the user asks for.
 * The APK of the build's ABI is downloaded to [DOWNLOAD_DIR] in `cacheDir`, checked against the
 * release's size and checksum, and installed with [PackageInstaller]; the system ends the app as
 * it replaces it. Before every check the downloaded APK is deleted.
 *
 * What it keeps lies in `noBackupFilesDir`, so it stays on the device: the time of the last answer
 * ([LAST_FILE]) and the version the user ignored ([IGNORED_FILE]).
 */
class GitHubUpdater internal constructor(
    private val context: Context,
    private val client: HttpClient,
    private val scope: CoroutineScope,
) : AppUpdater {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** A check, download or install runs; another one waits for it to end. */
    private val busy = AtomicBoolean(false)

    /** The install session waiting for the system, and its last confirmation page. */
    @Volatile
    private var sessionId = -1

    @Volatile
    private var lastConfirmation: Intent? = null

    private val lastFile get() = File(context.noBackupFilesDir, LAST_FILE)
    private val ignoredFile get() = File(context.noBackupFilesDir, IGNORED_FILE)
    private val downloadDir get() = File(context.cacheDir, DOWNLOAD_DIR)

    override val canInstall: Boolean
        get() = !IS_ANDROID_8_AND_UP || context.packageManager.canRequestPackageInstalls()

    override fun installPermissionIntent(): Intent? =
        if (IS_ANDROID_8_AND_UP)
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.fromParts("package", context.packageName, null))
        else
            null

    override fun checkOnOpen() {
        if (resumeInstall()) return
        if (!start()) return
        scope.launch {
            try {
                Preferences.awaitLoaded()
                val mode = GlobalPreferences.AUTO_UPDATE_MODE.value
                if (mode == AutoUpdateMode.OFF) return@launch
                val last = withContext(Dispatchers.IO) { readLong(lastFile) } ?: 0L
                if (System.currentTimeMillis() - last < INTERVAL.inWholeMilliseconds) return@launch

                val release = check(manual = false) ?: return@launch
                val ignored = withContext(Dispatchers.IO) { readText(ignoredFile) }
                when {
                    release.version == ignored -> {
                        Logger.i(tag = TAG) { "Version ${release.version} is ignored" }
                        _state.value = UpdateState.Idle
                    }

                    mode == AutoUpdateMode.AUTOMATIC -> download(release)
                    else -> _state.value = UpdateState.Available(release)
                }
            } finally {
                finish()
            }
        }
    }

    override fun checkNow() {
        if (!start()) return
        scope.launch {
            try {
                check(manual = true)?.let { _state.value = UpdateState.Available(it) }
            } finally {
                finish()
            }
        }
    }

    override fun install(release: AppRelease) {
        if (!start()) return
        scope.launch {
            try {
                download(release)
            } finally {
                finish()
            }
        }
    }

    override fun ignore(release: AppRelease) {
        _state.value = UpdateState.Idle
        scope.launch(Dispatchers.IO) {
            try {
                ignoredFile.writeText(release.version)
            } catch (e: IOException) {
                Logger.w(e, TAG) { "Couldn't keep the ignored version" }
            }
        }
    }

    override fun confirmationShown() {
        _state.value.let {
            if (it is UpdateState.Installing) _state.value = it.copy(confirmation = null)
        }
    }

    override fun dismiss() {
        // What runs keeps its dialog, only what is shown and done can be closed
        if (_state.value !is UpdateState.Downloading && _state.value !is UpdateState.Installing)
            _state.value = UpdateState.Idle
    }

    /**
     * The answer of the system to the install, see [UpdateInstallReceiver]. A successful one
     * rarely comes: the system ends the app to replace it.
     */
    internal fun onInstallStatus(intent: Intent) {
        val installing = _state.value as? UpdateState.Installing ?: return
        val status =
            intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_INTENT,
                    Intent::class.java
                )
                lastConfirmation = confirmation
                _state.value = installing.copy(confirmation = confirmation)
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Logger.i(tag = TAG) { "Installed ${installing.release.version}" }
                _state.value = UpdateState.Idle
            }

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Logger.w(tag = TAG) { "Install of ${installing.release.version} ended with $status: $message" }
                val reason =
                    if (status == PackageInstaller.STATUS_FAILURE_ABORTED)
                        UpdateFailure.CANCELED
                    else
                        UpdateFailure.INSTALL
                _state.value = UpdateState.Failed(installing.release, reason)
            }
        }
    }

    /**
     * Back on screen while an install waits for the user, who left its confirmation without an
     * answer: opens it again, or fails the install when its session is gone. Whether it did so.
     */
    private fun resumeInstall(): Boolean {
        val installing = _state.value as? UpdateState.Installing ?: return false
        val confirmation = lastConfirmation
        if (installing.confirmation != null || confirmation == null) return true
        if (context.packageManager.packageInstaller.getSessionInfo(sessionId) != null) {
            _state.value = installing.copy(confirmation = confirmation)
        } else {
            Logger.w(tag = TAG) { "Install session of ${installing.release.version} is gone" }
            _state.value = UpdateState.Failed(installing.release, UpdateFailure.CANCELED)
        }
        return true
    }

    /**
     * Whether a check, download or install may start now: nothing else runs and no install waits
     * for the system's answer.
     */
    private fun start(): Boolean {
        val state = _state.value
        if (state is UpdateState.Downloading || state is UpdateState.Installing) return false
        return busy.compareAndSet(false, true)
    }

    private fun finish() {
        busy.set(false)
    }

    /**
     * Deletes what was downloaded before, then asks for the latest release. Returns it when it's
     * newer than the app; otherwise shows the answer when [manual] and returns `null`.
     */
    private suspend fun check(manual: Boolean): AppRelease? {
        _state.value = UpdateState.Checking(manual)
        withContext(Dispatchers.IO) { downloadDir.deleteRecursively() }

        val latest = try {
            val response = client.get(LATEST_URL) {
                header(HttpHeaders.Accept, "application/vnd.github+json")
            }
            if (!response.status.isSuccess())
                throw IOException("GitHub answered ${response.status.value}")
            response.body<GitHubRelease>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e, TAG) { "Couldn't find the latest release" }
            _state.value = if (manual) UpdateState.CheckFailed else UpdateState.Idle
            return null
        }
        withContext(Dispatchers.IO) {
            try {
                lastFile.writeText(System.currentTimeMillis().toString())
            } catch (e: IOException) {
                Logger.w(e, TAG) { "Couldn't keep the time of the check" }
            }
        }

        val release = latest.updateFor(BuildConfig.VERSION_NAME, BuildConfig.FLAVOR_abi)
        Logger.i(tag = TAG) {
            "Latest release ${latest.tagName}, installed ${BuildConfig.VERSION_NAME}: " +
                    if (release != null) "newer" else "nothing to install"
        }
        if (release == null)
            _state.value = if (manual) UpdateState.UpToDate else UpdateState.Idle
        return release
    }

    /** Downloads [release]'s APK, checks it and hands it to the system. */
    private suspend fun download(release: AppRelease) {
        _state.value = UpdateState.Downloading(release, null)
        val file = try {
            withContext(Dispatchers.IO) { fetch(release) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: DamagedDownloadException) {
            Logger.w(e, TAG) { "Downloaded ${release.version} doesn't match the release" }
            _state.value = UpdateState.Failed(release, UpdateFailure.DAMAGED)
            return
        } catch (e: Exception) {
            Logger.w(e, TAG) { "Couldn't download ${release.version}" }
            _state.value = UpdateState.Failed(release, UpdateFailure.DOWNLOAD)
            return
        }

        _state.value = UpdateState.Installing(release)
        try {
            withContext(Dispatchers.IO) { commit(file) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e, TAG) { "Couldn't hand ${release.version} to the installer" }
            _state.value = UpdateState.Failed(release, UpdateFailure.INSTALL)
        }
    }

    private suspend fun fetch(release: AppRelease): File {
        downloadDir.mkdirs()
        val file = File(downloadDir, "kreate-${release.version}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        var read = 0L
        client.prepareGet(release.apkUrl).execute { response ->
            if (!response.status.isSuccess())
                throw IOException("Download answered ${response.status.value}")
            val total = response.contentLength()?.takeIf { it > 0 } ?: release.size
            var shown = -1
            response.bodyAsChannel().toInputStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        read += count
                        if (total > 0) {
                            // A new state for each percent, not for each read
                            val percent = (read * 100 / total).toInt().coerceAtMost(100)
                            if (percent != shown) {
                                shown = percent
                                _state.value = UpdateState.Downloading(release, percent / 100f)
                            }
                        }
                    }
                }
            }
        }

        if (release.size > 0 && read != release.size)
            throw DamagedDownloadException("Got $read bytes of ${release.size}")
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        if (release.sha256 != null && sha256 != release.sha256)
            throw DamagedDownloadException("Checksum $sha256 isn't ${release.sha256}")
        return file
    }

    /** Writes [file] to an install session and commits it, the answer goes to [onInstallStatus]. */
    private fun commit(file: File) {
        val installer = context.packageManager.packageInstaller
        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                .apply {
                    setAppPackageName(context.packageName)
                    setSize(file.length())
                    // Once the app installed itself, Android 12+ updates it without asking
                    if (IS_ANDROID_12_AND_UP)
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
        val id = installer.createSession(params)
        sessionId = id
        lastConfirmation = null
        try {
            installer.openSession(id).use { session ->
                session.openWrite(file.name, 0, file.length()).use { output ->
                    file.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                val answer = Intent(context, UpdateInstallReceiver::class.java)
                // The system adds the status to it, so it must stay mutable
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        if (IS_ANDROID_12_AND_UP) PendingIntent.FLAG_MUTABLE else 0
                val pending = PendingIntent.getBroadcast(context, id, answer, flags)
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            installer.abandonSession(id)
            throw e
        }
    }

    private fun readLong(file: File): Long? =
        try {
            file.takeIf(File::exists)?.readText()?.trim()?.toLongOrNull()
        } catch (e: IOException) {
            Logger.w(e, TAG) { "Couldn't read ${file.name}" }
            null
        }

    private fun readText(file: File): String? =
        try {
            file.takeIf(File::exists)?.readText()?.trim()
        } catch (e: IOException) {
            Logger.w(e, TAG) { "Couldn't read ${file.name}" }
            null
        }

    private class DamagedDownloadException(message: String) : IOException(message)

    companion object {
        /** In `noBackupFilesDir`: when GitHub last answered a check, epoch milliseconds. */
        internal const val LAST_FILE = "update_check_last"

        /** In `noBackupFilesDir`: the version the user chose to ignore. */
        internal const val IGNORED_FILE = "update_ignored_version"

        /** In `cacheDir`: the downloaded APK. */
        internal const val DOWNLOAD_DIR = "updates"

        private const val BUFFER_SIZE = 64 * 1024
    }
}
