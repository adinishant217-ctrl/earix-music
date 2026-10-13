package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.SimpleMediaState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource
import org.simpmusic.lyrics.publish.LRCLIBPublisher
import org.simpmusic.lyrics.publish.MAX_NONCE
import org.simpmusic.lyrics.publish.PublishResult
import org.simpmusic.lyrics.publish.TokenResult

/** Song being synced. Duration is resolved live from the player at submit time. */
data class SyncSongMetadata(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String,
)

data class SyncedLine(
    val timestampMs: Long,
    val text: String,
)

data class SyncLyricsState(
    val rawLyrics: String = "",
    val lines: List<String> = emptyList(),
    val syncedLines: List<SyncedLine> = emptyList(),
    val currentIndex: Int = 0,
    val isPublishing: Boolean = false,
    val powProgressText: String? = null,
    val publishResult: PublishResult? = null,
    val error: String? = null,
)

/**
 * Tap-to-sync + anonymous LRCLIB publishing. Read-only towards the player:
 * position is observed, never written. All network/PoW runs on Dispatchers.IO.
 */
class SyncLyricsViewModel(
    private val mediaPlayerHandler: MediaPlayerHandler,
) : ViewModel() {
    companion object {
        /** Sentinel error value meaning LRCLIB already has lyrics (UI maps it to a message). */
        const val ALREADY_EXISTS = "ALREADY_EXISTS_IN_LRCLIB"

        private fun formatMillions(attempts: Long): String =
            "${attempts / 1_000_000}.${(attempts % 1_000_000) / 100_000}M"
    }

    private val _state = MutableStateFlow(SyncLyricsState())
    val state: StateFlow<SyncLyricsState> = _state.asStateFlow()

    /** Live player position in ms, for stamping taps. Observed only. */
    val currentPositionMs: StateFlow<Long> =
        mediaPlayerHandler.simpleMediaState
            .map { s -> if (s is SimpleMediaState.Progress) s.progress else 0L }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private var submitJob: Job? = null

    /** Cancels an in-flight submit (PoW + publish) and returns to Review. */
    fun cancelSubmit() {
        submitJob?.cancel()
        submitJob = null
        _state.value = _state.value.copy(isPublishing = false, powProgressText = null)
    }

    fun setRawLyrics(text: String) {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        _state.value =
            SyncLyricsState(
                rawLyrics = text,
                lines = lines,
                syncedLines = emptyList(),
                currentIndex = 0,
            )
    }

    fun tapAtPosition(playerPositionMs: Long) {
        val s = _state.value
        if (s.currentIndex >= s.lines.size) return
        _state.value =
            s.copy(
                syncedLines = s.syncedLines + SyncedLine(playerPositionMs, s.lines[s.currentIndex]),
                currentIndex = s.currentIndex + 1,
            )
    }

    fun undoLastTap() {
        val s = _state.value
        if (s.syncedLines.isEmpty()) return
        _state.value =
            s.copy(
                syncedLines = s.syncedLines.dropLast(1),
                currentIndex = (s.currentIndex - 1).coerceAtLeast(0),
            )
    }

    fun resync() {
        val s = _state.value
        _state.value = s.copy(syncedLines = emptyList(), currentIndex = 0, publishResult = null, error = null)
    }

    fun clearResult() {
        _state.value = _state.value.copy(publishResult = null, error = null)
    }

    fun submit(metadata: SyncSongMetadata) {
        val s = _state.value
        if (s.isPublishing || s.syncedLines.isEmpty()) return
        _state.value = s.copy(isPublishing = true, error = null, publishResult = null, powProgressText = null)
        val solveStart = TimeSource.Monotonic.markNow()
        submitJob?.cancel()
        submitJob =
            viewModelScope.launch {
                val outcome =
                    withContext(Dispatchers.IO) {
                        when (
                            val t =
                                LRCLIBPublisher.getPublishTokenDetailed { attempts ->
                                    val elapsedSec = solveStart.elapsedNow().inWholeSeconds.coerceAtLeast(1)
                                    val rate = attempts / elapsedSec
                                    val eta =
                                        if (rate > 0) {
                                            " · ~${(MAX_NONCE - attempts) / rate}s left"
                                        } else {
                                            ""
                                        }
                                    _state.value =
                                        _state.value.copy(
                                            powProgressText = "Solving... ${formatMillions(attempts)} / 500M$eta",
                                        )
                                }
                        ) {
                            is TokenResult.Ok -> {
                            val durationSeconds =
                                when (val st = mediaPlayerHandler.simpleMediaState.value) {
                                    is SimpleMediaState.Ready -> (st.duration / 1000).toInt()
                                    is SimpleMediaState.Progress -> (st.progress / 1000).toInt()
                                    else -> 0
                                }
                            val pairs = s.syncedLines.map { it.timestampMs to it.text }
                            LRCLIBPublisher.publishLyrics(
                                token = t.token,
                                trackName = metadata.title,
                                artistName = metadata.artist,
                                albumName = metadata.album,
                                durationSeconds = durationSeconds,
                                syncedLyrics = LRCLIBPublisher.buildLRC(pairs),
                                plainLyrics = LRCLIBPublisher.buildPlainLyrics(pairs),
                            )
                        }

                        TokenResult.PowUnsolved ->
                            PublishResult.Error(-2, "Could not solve PoW challenge")

                        TokenResult.TimedOut ->
                            PublishResult.Error(-3, "PoW took too long. Please try again.")

                        TokenResult.NetworkError ->
                            PublishResult.Error(-1, "Could not obtain publish token. Check internet.")
                    }
                }
            _state.value =
                _state.value.copy(
                    isPublishing = false,
                    powProgressText = null,
                    publishResult = outcome.takeUnless { it is PublishResult.Error || it is PublishResult.AlreadyExists },
                    error =
                        when (outcome) {
                            is PublishResult.AlreadyExists -> ALREADY_EXISTS
                            is PublishResult.Error -> outcome.message
                            else -> null
                        },
                )
        }
    }
}
