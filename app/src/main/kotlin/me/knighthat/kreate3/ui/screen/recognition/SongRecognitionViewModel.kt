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

package me.knighthat.kreate3.ui.screen.recognition

import android.content.Context
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.R
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.provider.youtube.YouTubeSearchFilter
import me.knighthat.kreate3.recognition.RecognizedSong
import me.knighthat.kreate3.recognition.SongRecognizer
import me.knighthat.kreate3.ui.component.playTapped
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import me.knighthat.kreate3.source.youtube.toMediaItem as toYouTubeMediaItem

private const val TAG = "SongRecognitionViewModel"

/** Shorter holds are too short to be recognized. */
val MIN_LISTEN: Duration = 8.seconds

/** From here on, a hold is long enough for a good match. */
val GOOD_LISTEN: Duration = 12.seconds

/** Listening stops by itself here, even while still held. */
val MAX_LISTEN: Duration = 16.seconds

/** Where naming the song around the phone is at. */
sealed interface RecognitionState {

    /** Nothing going on. */
    data object Idle : RecognitionState

    /**
     * Listening while a button is held.
     *
     * @param since when it started, in [SystemClock.elapsedRealtime] milliseconds
     * @param inDialog held on the dialog's button, not on the Songs tab
     */
    data class Listening(val since: Long, val inDialog: Boolean) : RecognitionState

    /** Looking up what was heard; the dialog can't be closed. */
    data object Identifying : RecognitionState

    /**
     * @param searching Play is looking for the song on YouTube
     * @param notFound the last Play found nothing to play
     */
    data class Found(
        val song: RecognizedSong,
        val searching: Boolean = false,
        val notFound: Boolean = false,
    ) : RecognitionState

    data class Failed(@param:StringRes val reason: Int) : RecognitionState

    /**
     * The dialog shows the button to hold, after Retry or from the Songs tab's sheet.
     *
     * @param tooShort the last hold ended before [MIN_LISTEN]
     */
    data class Ready(val tooShort: Boolean = false) : RecognitionState
}

/**
 * Names the song playing around the phone: holding the Songs tab (or the dialog's button) listens,
 * letting go after [MIN_LISTEN] looks it up, [MAX_LISTEN] ends it on its own.
 */
class SongRecognitionViewModel(
    private val context: Context,
    private val notifier: Notifier,
    private val recognizer: SongRecognizer,
    private val youTube: YouTubeProvider,
    private val playback: PlaybackController,
) : ViewModel() {

    private val _state = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val state: StateFlow<RecognitionState> = _state.asStateFlow()

    /** Ends listening at [MAX_LISTEN]. */
    private var timeLimit: Job? = null

    /** Opens the dialog on the button to hold, from the Songs tab's sheet. */
    fun open() {
        if (_state.value == RecognitionState.Idle) _state.value = RecognitionState.Ready()
    }

    /** Starts listening, from the Songs tab or the dialog's button. */
    fun listen() {
        val current = _state.value
        if (current != RecognitionState.Idle && current !is RecognitionState.Ready) return
        recognizer.start()
        _state.value = RecognitionState.Listening(
            since = SystemClock.elapsedRealtime(),
            inDialog = current is RecognitionState.Ready,
        )
        timeLimit = viewModelScope.launch {
            delay(MAX_LISTEN)
            timeLimit = null
            stop()
        }
    }

    /** The button was let go, or [MAX_LISTEN] passed. */
    fun stop() {
        val listening = _state.value as? RecognitionState.Listening ?: return
        timeLimit?.cancel()
        timeLimit = null
        val heard = (SystemClock.elapsedRealtime() - listening.since).milliseconds
            .coerceAtMost(MAX_LISTEN)
        if (heard < MIN_LISTEN) {
            recognizer.cancel()
            if (listening.inDialog) {
                _state.value = RecognitionState.Ready(tooShort = true)
            } else {
                _state.value = RecognitionState.Idle
                val seconds = MIN_LISTEN.inWholeSeconds.toInt()
                notifier.post(
                    context.getString(
                        R.string.snackbar_recognize_song_too_short,
                        context.resources.getQuantityString(R.plurals.second, seconds, seconds),
                    )
                )
            }
            return
        }

        _state.value = RecognitionState.Identifying
        viewModelScope.launch {
            _state.value = try {
                recognizer.finish(heard)
                    ?.let(RecognitionState::Found)
                    ?: RecognitionState.Failed(R.string.dialog_body_recognize_song_no_match)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Couldn't look up the recorded song" }
                RecognitionState.Failed(R.string.dialog_body_recognize_song_failed)
            }
        }
    }

    /** The microphone was denied at the prompt: says it's needed. */
    fun microphoneDenied() {
        notifier.post(context.getString(R.string.snackbar_recognize_song_no_microphone))
    }

    /** Song recognition is off ([Preferences.SHAZAM_ENABLED]): says so instead of listening. */
    fun disabled() {
        notifier.post(context.getString(R.string.snackbar_recognize_song_disabled))
    }

    /** Turns the dialog with a result into the button to hold. */
    fun retry() {
        val current = _state.value
        if (current is RecognitionState.Failed || current is RecognitionState.Found)
            _state.value = RecognitionState.Ready()
    }

    /** Closes the dialog, except while listening or looking up. */
    fun close() {
        val current = _state.value
        if (current is RecognitionState.Listening || current == RecognitionState.Identifying) return
        _state.value = RecognitionState.Idle
    }

    /**
     * Plays the first song YouTube finds for [song], the way a song tapped in the search results
     * plays ([Preferences.SEARCH_SONGS_TAP_RADIO]: a radio, or the song alone). The dialog closes
     * once it plays; when nothing is found it stays and says so.
     */
    fun play(song: RecognizedSong) {
        val found = _state.value as? RecognitionState.Found ?: return
        if (found.searching) return
        val searching = found.copy(searching = true, notFound = false)
        _state.value = searching
        viewModelScope.launch {
            val first = try {
                withContext(Dispatchers.Default) {
                    youTube.search(song.searchQuery, YouTubeSearchFilter.SONGS).items
                        .asSequence()
                        .map { it.toYouTubeMediaItem(youTube) }
                        .firstOrNull { it.mediaMetadata.isPlayable == true }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Couldn't search YouTube for the recognized song" }
                null
            }
            // Closed or retried meanwhile
            if (_state.value != searching) return@launch
            if (first == null) {
                _state.value = found.copy(notFound = true)
                return@launch
            }

            _state.value = RecognitionState.Idle
            val radio = Preferences.SEARCH_SONGS_TAP_RADIO.value
            withContext(Dispatchers.Default) {
                playback.playTapped(context.resources, first, listOf(first), radio)
            }?.let(notifier::post)
        }
    }

    override fun onCleared() {
        if (_state.value is RecognitionState.Listening) recognizer.cancel()
    }
}
