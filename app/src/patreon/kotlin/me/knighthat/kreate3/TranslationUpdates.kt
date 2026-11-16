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

package me.knighthat.kreate3

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.AtomicFile
import androidx.core.content.edit
import co.touchlab.kermit.Logger
import com.crowdin.platform.Crowdin
import com.crowdin.platform.CrowdinConfig
import com.crowdin.platform.LoadingStateListener
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.knighthat.kreate3.TranslationUpdates.HASH
import me.knighthat.kreate3.TranslationUpdates.INTERVAL
import java.io.File
import java.util.Locale
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * The patreon APK has English only, the other languages come from Crowdin's distribution [HASH],
 * delivered by the Crowdin SDK. Every request is held to one per [INTERVAL]:
 *
 * - [languages] are those of the distribution's manifest, kept on the device; [refresh] (the
 *   language page) fetches a new one once the kept one is older than [INTERVAL].
 * - [check] (at start, on a switch of language and every [INTERVAL] while the app runs) downloads
 *   the texts of the shown language when it has none, and when its download is older than
 *   [INTERVAL] and the manifest names a newer release. A download younger than that stands.
 *   English, the source, is never downloaded.
 *
 * New texts show from the next screen made: activities [wrap]ped get them, the application and
 * services keep the APK's texts, since their resources would otherwise miss later configuration
 * changes. A language shows English until its first download, [delivered] tells screens to be
 * made again then.
 *
 * Only in the patreon build, foss and github have a stand-in (src/crowdin) that keeps the APK's
 * texts.
 */
object TranslationUpdates {

    // The parts of the manifest read, the SDK reads the rest itself
    @Serializable
    private class Manifest(
        val timestamp: Long = 0,
        val content: Map<String, List<String>> = emptyMap(),
    )

    private const val TAG = "TranslationUpdates"

    // When the manifest was fetched, and per language tag when its texts were downloaded and the
    // release they are of ("<tag>.release")
    private const val FILE = "translation_checks"
    private const val KEY_MANIFEST_AT = "manifest_fetched_at"
    private const val RELEASE = ".release"

    // Under noBackupFilesDir: a restore on another device fetches its own
    private const val MANIFEST_FILE = "crowdin_manifest.json"

    // Crowdin's distribution "android-app-texts" of the "Android app texts" bundle: every
    // *.strings.xml and plurals.xml in one values-<code>/strings.xml (the SDK reads only paths with
    // ".xml"). From the build's environment, empty when it had none: English only then
    private const val HASH = BuildConfig.CROWDIN_DISTRIBUTION_HASH
    private const val MANIFEST_URL = "https://distributions.crowdin.net/$HASH/manifest.json"

    private val INTERVAL = 48.hours

    // "pt", "pt-rBR" or "b+sr+Latn", not "content" or "strings.xml"
    private val ANDROID_CODE = Regex("[a-z]{2,3}(-r[A-Z]{2})?|b\\+[A-Za-z0-9+]+")
    private val DOWNLOAD_WAIT = 1.minutes

    private lateinit var app: Context
    private lateinit var scope: CoroutineScope
    private lateinit var client: HttpClient
    private lateinit var json: Json

    // The page and a check may want the manifest together, it is fetched once
    private val manifestLock = Mutex()

    // The language a download runs for, whether it had none before, and when it ends
    private class Download(val tag: String, val first: Boolean) {
        val done = CompletableDeferred<Unit>()
    }

    // One download at a time, a switch of language while one runs waits for it
    private val downloadLock = Mutex()

    @Volatile
    private var downloading: Download? = null

    private val _languages = MutableStateFlow<List<Locale>?>(null)

    /** The languages of the kept manifest, `null` until it was read. English isn't one. */
    val languages: StateFlow<List<Locale>?> = _languages.asStateFlow()

    private val _delivered = MutableStateFlow(0)

    /** Goes up when the first download of a language ended. */
    val delivered: StateFlow<Int> = _delivered.asStateFlow()

    private val preferences: SharedPreferences
        get() = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Starts the SDK with the texts it kept, reads the kept manifest and checks the shown language
     * now and every [INTERVAL] on [scope], from `onCreate` of the application.
     */
    fun init(context: Context, scope: CoroutineScope, client: HttpClient, json: Json) {
        app = context.applicationContext
        this.scope = scope
        this.client = client
        this.json = json

        if (HASH.isEmpty()) {
            Logger.w(tag = TAG) { "Built without a Crowdin distribution, English only" }
            _languages.value = emptyList()
            return
        }
        Crowdin.init(
            context,
            CrowdinConfig.Builder()
                .withDistributionHash(HASH)
                // check() decides when
                .withInitSyncDisabled()
                .build()
        )
        Crowdin.registerDataLoadingObserver(object : LoadingStateListener {
            override fun onDataChanged() = downloaded()

            override fun onFailure(throwable: Throwable) = failed(throwable)
        })

        scope.launch {
            _languages.value = readManifest()?.let(::localesOf).orEmpty()
            while (true) {
                check(app)
                delay(INTERVAL)
            }
        }
    }

    /** [base] showing the texts delivered, for `attachBaseContext` of activities. */
    fun wrap(base: Context): Context = if (HASH.isEmpty()) base else Crowdin.wrapContext(base)

    /** Fetches a new manifest when the kept one is older than [INTERVAL], for the language page. */
    suspend fun refresh() {
        if (HASH.isNotEmpty()) manifest()
    }

    /**
     * Downloads the texts of the language [context] shows, see [TranslationUpdates]. Called at
     * start, on configuration changes (a switch of language) and every [INTERVAL]. Without a
     * connection, the next call tries again.
     */
    fun check(context: Context) {
        if (HASH.isEmpty()) return
        val locale = context.resources.configuration.locales[0]
        if (locale.language == "en") return

        scope.launch { update(context, locale) }
    }

    private suspend fun update(context: Context, locale: Locale) = downloadLock.withLock {
        // The SDK may end one without a word (offline), the wait has an end
        downloading?.let { withTimeoutOrNull(DOWNLOAD_WAIT) { it.done.await() } }
        downloading = null

        val tag = locale.toLanguageTag()
        val downloadedAt = preferences.getLong(tag, 0)
        if (!passed(downloadedAt) || !isOnline(context)) return@withLock

        val manifest = manifest() ?: return@withLock
        // A language Crowdin doesn't have, it shows English
        if (localesOf(manifest).none { it.language == locale.language }) return@withLock
        val first = downloadedAt == 0L
        if (!first && manifest.timestamp == preferences.getLong(tag + RELEASE, 0)) return@withLock

        // Before the request, a configuration change meanwhile doesn't start another
        preferences.edit {
            putLong(tag, System.currentTimeMillis())
            putLong(tag + RELEASE, manifest.timestamp)
        }
        downloading = Download(tag, first)
        Logger.i(tag = TAG) { "Downloading the texts of $tag" }
        // The SDK downloads on its own threads, in the language of the context's configuration
        withContext(Dispatchers.Main) { Crowdin.forceUpdate(context) }
    }

    private fun downloaded() {
        val download = downloading ?: return
        downloading = null
        Logger.i(tag = TAG) { "Downloaded the texts of ${download.tag}" }
        if (download.first) _delivered.update { it + 1 }
        download.done.complete(Unit)
    }

    // Forgotten, the next check tries again
    private fun failed(throwable: Throwable) {
        val download = downloading ?: return
        downloading = null
        Logger.w(throwable, TAG) { "Couldn't download the texts of ${download.tag}" }
        preferences.edit {
            remove(download.tag)
            remove(download.tag + RELEASE)
        }
        download.done.complete(Unit)
    }

    /**
     * The kept manifest while younger than [INTERVAL], else a new one, or the kept one if that
     * fails.
     */
    private suspend fun manifest(): Manifest? = manifestLock.withLock {
        val kept = readManifest()
        if (kept != null && !passed(preferences.getLong(KEY_MANIFEST_AT, 0))) return@withLock kept

        val fetched = fetchManifest() ?: return@withLock kept
        _languages.value = localesOf(fetched)
        fetched
    }

    private suspend fun fetchManifest(): Manifest? = withContext(Dispatchers.IO) {
        try {
            val response = client.get(MANIFEST_URL)
            if (!response.status.isSuccess()) {
                Logger.w(tag = TAG) { "Crowdin's manifest answered ${response.status}" }
                return@withContext null
            }
            val text = response.bodyAsText()
            val manifest = json.decodeFromString<Manifest>(text)

            val file = AtomicFile(manifestFile())
            val out = file.startWrite()
            try {
                out.write(text.toByteArray())
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
                throw e
            }
            preferences.edit { putLong(KEY_MANIFEST_AT, System.currentTimeMillis()) }
            manifest
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e, TAG) { "Couldn't fetch Crowdin's manifest" }
            null
        }
    }

    // Missing or damaged reads as none, a fetch replaces it
    private suspend fun readManifest(): Manifest? = withContext(Dispatchers.IO) {
        try {
            val file = manifestFile()
            if (!file.isFile) return@withContext null
            json.decodeFromString<Manifest>(AtomicFile(file).readFully().decodeToString())
        } catch (e: Exception) {
            Logger.w(e, TAG) { "Couldn't read the kept Crowdin manifest" }
            null
        }
    }

    private fun manifestFile() = File(app.noBackupFilesDir, MANIFEST_FILE)

    // Each language's Android code in its paths, with or without the values- folder of the export
    // pattern: "/content/values-pt-rBR/strings.xml" or "/content/pt-rBR" as "pt-BR",
    // "b+sr+Latn" as "sr-Latn"
    private fun localesOf(manifest: Manifest): List<Locale> =
        manifest.content.values
            .flatten()
            .mapNotNull { path ->
                path.split('/')
                    .map { it.removePrefix("values-") }
                    .firstOrNull { ANDROID_CODE.matches(it) }
            }
            .distinct()
            .map { code ->
                val tag =
                    if (code.startsWith("b+"))
                        code.removePrefix("b+").replace('+', '-')
                    else
                        code.replace("-r", "-")
                Locale.forLanguageTag(tag)
            }

    // A clock set back counts as passed
    private fun passed(at: Long): Boolean =
        at == 0L || System.currentTimeMillis() - at !in 0..<INTERVAL.inWholeMilliseconds

    private fun isOnline(context: Context): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity?.activeNetwork ?: return false
        return connectivity.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}
