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

package me.knighthat.kreate3.presence

import android.content.Context
import android.net.Uri
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.knighthat.kreate3.BuildConfig
import me.knighthat.kreate3.discord.ActivityButton
import me.knighthat.kreate3.discord.ActivityImage
import me.knighthat.kreate3.discord.ActivityType
import me.knighthat.kreate3.discord.DiscordActivity
import me.knighthat.kreate3.discord.DiscordClient
import me.knighthat.kreate3.discord.DiscordStatus
import me.knighthat.kreate3.discord.StatusText
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.ProfileManager
import me.knighthat.kreate3.preferences.Secrets
import me.knighthat.kreate3.provider.Providers
import me.knighthat.kreate3.provider.youtube.YouTubeLinks
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.ArtistPictures
import me.knighthat.kreate3.ui.component.EXTRA_ARTIST_IDS
import me.knighthat.kreate3.ui.component.origin
import me.knighthat.kreate3.ui.component.songThumbnailModels
import me.knighthat.kreate3.ui.component.thumbnailModel
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

private const val TAG = "DiscordPresence"

/** The app's picture on Discord's developer portal, shown when there is no cover. */
private val APP_LOGO = ActivityImage.Asset("app_logo")

/** How long the presence waits for its pictures before it's shown with the app's instead. */
private val PICTURE_WAIT = 1500.milliseconds

/** How long the connection stays once there's nothing to show, in case something comes. */
private val IDLE_CONNECTION = 1.minutes

/** How long a refresh that failed waits before it's tried again. */
private val RETRY_REFRESH = 1.minutes

/** How long the app waits for what it does to settle, a page loading or songs being skipped. */
private val SETTLE = 300.milliseconds

/** Hosts of YouTube's pictures, which anyone can open. */
private val YOUTUBE_PICTURE_HOSTS = listOf("ytimg.com", "googleusercontent.com", "ggpht.com")

/**
 * Shows on the user's Discord profile what they do in the app ([PresenceState]): the song in the
 * player (playing or paused) above all, else the album, artist or playlist page of YouTube open,
 * or the search; a page of the device or the OpenSubsonic server shows nothing. A song of a source whose setting (like
 * [Preferences.DISCORD_YOUTUBE]) is off shows nothing at all, a page of one shows nothing either.
 *
 * Connected to Discord while [Preferences.DISCORD_ENABLED] is on, someone is signed in, and
 * there's something to show or was in the last [IDLE_CONNECTION].
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class DiscordPresence internal constructor(
    private val context: Context,
    private val state: PresenceState,
    private val client: DiscordClient,
    private val account: DiscordAccount,
    private val uploads: CoverUploads,
    private val providers: Providers,
    private val artists: ArtistPictures,
    private val youTube: YouTubeProvider,
    profiles: ProfileManager,
    scope: CoroutineScope,
) {

    init {
        // Which origins may show, none while it's off or nobody is signed in: nothing is uploaded
        // for a presence that isn't sent
        val allowed: Flow<Set<Origin>> = combine(
            Preferences.DISCORD_ENABLED.state,
            Secrets.DISCORD_ACCESS_TOKEN.state.map { it.isNotEmpty() },
            Preferences.DISCORD_YOUTUBE.state,
            Preferences.DISCORD_OPEN_SUBSONIC.state,
            Preferences.DISCORD_LOCAL.state,
        ) { enabled, signedIn, youTube, openSubsonic, local ->
            if (!enabled || !signedIn) return@combine emptySet()
            buildSet {
                if (youTube) add(Origin.YOUTUBE)
                if (openSubsonic) add(Origin.OPEN_SUBSONIC)
                if (local) add(Origin.LOCAL)
            }
        }
        val shown: Flow<Doing?> = combine(
            state.listening,
            state.browsing,
            allowed,
            Preferences.DISCORD_PRIVATE_PLAYLISTS.state,
        ) { listening, browsing, allowed, privateShown ->
            // Opened files have no origin, they are the device's
            val shows = { origin: Origin? -> (origin ?: Origin.LOCAL) in allowed }
            when {
                listening != null -> listening.takeIf { shows(it.item.origin) }
                // Only YouTube's pages show: any other picture would be an upload per page.
                // A private playlist shows only when the user allowed it
                browsing is Doing.Browsing -> browsing.takeIf {
                    it.origin == Origin.YOUTUBE && shows(it.origin) &&
                            (privateShown || !it.isPrivate)
                }

                browsing is Doing.Searching -> browsing.takeIf { shows(it.origin) }
                else -> null
            }
        }

        scope.launch {
            shown
                .debounce(SETTLE)
                .map { it?.let(::Shown) }
                .distinctUntilChanged()
                .collectLatest { show(it?.doing) }
        }

        scope.launch {
            val wanted = shown
                .map { it != null }
                .distinctUntilChanged()
                .transformLatest { showing ->
                    if (!showing) delay(IDLE_CONNECTION)
                    emit(showing)
                }
            combine(
                Preferences.DISCORD_ENABLED.state,
                Secrets.DISCORD_ACCESS_TOKEN.state.map { it.isNotEmpty() }.distinctUntilChanged(),
                profiles.activeId,
                wanted,
            ) { enabled, signedIn, profile, wanted ->
                // Another profile is another account, it connects again
                profile.takeIf { enabled && signedIn && wanted }
            }
                .distinctUntilChanged()
                .collectLatest { profile ->
                    if (profile == null) return@collectLatest
                    connect(profile)
                }
        }
    }

    /**
     * Keeps connected with the token of [profile]'s account until cancelled, refreshing it when
     * the gateway refuses it. Returns once nobody is signed in or Discord refuses the app.
     */
    private suspend fun connect(profile: String) {
        var force = false
        while (true) {
            val access = try {
                account.access(profile, force)
            } catch (e: IOException) {
                Logger.w(e, TAG) { "Couldn't refresh Discord's tokens" }
                delay(RETRY_REFRESH)
                continue
            } ?: return

            Logger.i(tag = TAG) { "Connecting to Discord" }
            when (client.connect(access)) {
                DiscordStatus.REJECTED -> {
                    // Refused right after a refresh: Discord may not have it yet, not at once
                    if (force) delay(RETRY_REFRESH)
                    force = true
                }

                else -> {
                    Logger.w(tag = TAG) { "Discord refused the app on its gateway" }
                    return
                }
            }
        }
    }

    /** Shows [doing] on the profile, its pictures as soon as they are ready. */
    private suspend fun show(doing: Doing?) {
        if (doing == null) {
            client.show(null)
            return
        }

        val activity = activityOf(doing)
        coroutineScope {
            val pictures = async { pictures(doing) }
            val ready = withTimeoutOrNull(PICTURE_WAIT) { pictures.await() }
            if (ready == null)
                client.show(activity.copy(largeImage = APP_LOGO))
            val (large, small) = ready ?: pictures.await()
            client.show(activity.copy(largeImage = large ?: APP_LOGO, smallImage = small))
        }
    }

    /** What [doing] reads on the profile, without its pictures. */
    private fun activityOf(doing: Doing): DiscordActivity {
        val name = DiscordTexts.APP_NAME
        val getApp = ActivityButton(DiscordTexts.GET_APP, BuildConfig.HOMEPAGE_URL)

        return when (doing) {
            is Doing.Listening -> {
                val metadata = doing.item.mediaMetadata
                val artists = metadata.artist?.toString()?.takeIf { it.isNotBlank() }
                // Discord counts from the start and fills the bar to the end, only while playing
                val start = (doing.at - doing.position).takeIf { doing.playing }
                val link = YouTubeLinks.song(doing.item.mediaId)
                    .takeIf { doing.item.origin == Origin.YOUTUBE }
                DiscordActivity(
                    name = name,
                    type = ActivityType.LISTENING,
                    details = metadata.title?.toString(),
                    state = when {
                        doing.playing -> artists
                        artists != null -> DiscordTexts.paused(artists)
                        else -> DiscordTexts.PAUSED
                    },
                    statusText = StatusText.DETAILS,
                    start = start,
                    end = start?.let { from -> doing.duration?.let { from + it } },
                    largeText = metadata.albumTitle?.toString(),
                    smallText = artists,
                    buttons = listOfNotNull(
                        link?.let { ActivityButton(DiscordTexts.PLAY_ON_YOUTUBE_MUSIC, it) },
                        getApp,
                    ),
                )
            }

            // The item's name first, who made it under, what the user does last
            is Doing.Browsing -> DiscordActivity(
                name = doing.name,
                type = ActivityType.PLAYING,
                details = doing.creator,
                state = when (doing.kind) {
                    BrowsedKind.ALBUM -> DiscordTexts.VIEWING_ALBUM
                    BrowsedKind.ARTIST -> DiscordTexts.VIEWING_ARTIST
                    BrowsedKind.PLAYLIST -> DiscordTexts.VIEWING_PLAYLIST
                },
                start = doing.since,
                largeText = doing.name,
                buttons = listOfNotNull(
                    // Nobody but its owner could open a private one
                    doing.link?.takeUnless { doing.isPrivate }
                        ?.let { ActivityButton(DiscordTexts.VIEW_ON_YOUTUBE_MUSIC, it) },
                    getApp,
                ),
            )

            is Doing.Searching -> DiscordActivity(
                name = name,
                type = ActivityType.PLAYING,
                details = DiscordTexts.SEARCHING,
                state = DiscordTexts.quoted(doing.query),
                start = doing.since,
                buttons = listOf(getApp),
            )
        }
    }

    /** The large and the small picture of [doing], `null` for one it has none of. */
    private suspend fun pictures(doing: Doing): Pair<ActivityImage?, ActivityImage?> =
        when (doing) {
            is Doing.Listening -> {
                val origin = doing.item.origin ?: Origin.LOCAL
                val size = Preferences.DISCORD_COVER_SIZE.value
                val cover = picture(songThumbnailModels(doing.item, providers, size), origin)
                val artistId = doing.item.mediaMetadata.extras
                    ?.getStringArray(EXTRA_ARTIST_IDS)?.firstOrNull()
                // Artists stored from credits come without a picture, asked of the source once per run
                val artist = artistId
                    ?.let { artists.pictureOf(it, origin) }
                    ?.let { picture(listOfNotNull(thumbnailModel(it, null)), origin) }
                cover to artist
            }

            is Doing.Browsing -> picture(doing.models, doing.origin ?: Origin.LOCAL) to null
            is Doing.Searching -> null to null
        }

    /**
     * A picture Discord can fetch of the first of [models] there is: YouTube's own address for
     * one of YouTube (never a copy on the device), else one uploaded by [uploads].
     */
    private suspend fun picture(models: List<Any>, origin: Origin): ActivityImage? {
        if (origin == Origin.YOUTUBE) {
            val url = models.firstNotNullOfOrNull { model ->
                (model as? Uri)?.takeIf { it.isYouTubePicture() }?.toString()
            } ?: return null
            val sized = youTube.artworkUri(url, Preferences.DISCORD_COVER_SIZE.value)
            return ActivityImage.Link(sized?.toString() ?: url)
        }
        // A cover that doesn't load is passed over, the first that does is uploaded
        return uploads.publicUrl(models)?.let(ActivityImage::Link)
    }

    private fun Uri.isYouTubePicture(): Boolean {
        val host = host ?: return false
        return (scheme == "https" || scheme == "http")
                && YOUTUBE_PICTURE_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    /**
     * [doing] as it reads, for telling a change apart from the same thing told again: a song's
     * start moves by the time it took to tell it.
     */
    private class Shown(val doing: Doing) {

        private val key: Any = when (doing) {
            is Doing.Listening -> listOf(
                doing.item.mediaId,
                doing.item.mediaMetadata,
                doing.playing,
                doing.duration,
                // A second either way is the same start
                if (doing.playing) (doing.at - doing.position) / 2000 else null,
            )

            else -> doing
        }

        override fun equals(other: Any?): Boolean = other is Shown && other.key == key

        override fun hashCode(): Int = key.hashCode()
    }
}
