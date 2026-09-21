package com.maxrave.simpmusic.ui.component.lyrics

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxrave.simpmusic.ui.component.LyricsView
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.Share
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.screen.player.content.NowPlayingContentActions
import com.maxrave.simpmusic.ui.screen.player.content.NowPlayingContentState
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.LyricsProvider
import com.maxrave.simpmusic.viewModel.UIEvent
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.earix_background
import simpmusic.composeapp.generated.resources.line_synced
import simpmusic.composeapp.generated.resources.lyrics
import simpmusic.composeapp.generated.resources.lyrics_provider_betterlyrics
import simpmusic.composeapp.generated.resources.lyrics_provider_lrc
import simpmusic.composeapp.generated.resources.lyrics_provider_simpmusic
import simpmusic.composeapp.generated.resources.lyrics_provider_youtube
import simpmusic.composeapp.generated.resources.offline_mode
import simpmusic.composeapp.generated.resources.rich_synced
import simpmusic.composeapp.generated.resources.spotify_lyrics_provider
import simpmusic.composeapp.generated.resources.unsynced

private val EarixPurple = Color(0xFFA855F7)
private val EarixMuted = Color(0xFFB0A5C0)

/** Horizontal swipe with a 120px slop so taps and vertical scrolls never trigger it. */
fun Modifier.onSwipeRight(onSwipe: () -> Unit): Modifier =
    pointerInput(onSwipe) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragCancel = { total = 0f },
            onDragEnd = { if (total > 120f) onSwipe() },
        ) { _, dragAmount ->
            total += dragAmount
        }
    }

fun Modifier.onSwipeLeft(onSwipe: () -> Unit): Modifier =
    pointerInput(onSwipe) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragCancel = { total = 0f },
            onDragEnd = { if (total < -120f) onSwipe() },
        ) { _, dragAmount ->
            total += dragAmount
        }
    }

/**
 * Earix lyrics page: cosmic background, centered lyrics, sync + provider footer.
 * Opened by swiping right on Now Playing (header/title/controls) or the lyrics
 * icon; closed by swiping left or the back arrow.
 */
@Composable
fun NowPlayingLyricsPage(
    state: NowPlayingContentState,
    actions: NowPlayingContentActions,
    onBack: () -> Unit,
) {
    var showShareSheet by rememberSaveable { mutableStateOf(false) }
    val lyricsData = state.screenData.lyricsData

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSwipeLeft(onBack),
    ) {
        Image(
            painter = painterResource(Res.drawable.earix_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.6f,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x8C0D0618)),
        )
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = SimpIcons.ArrowBackIosNew,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Text(
                    text = stringResource(Res.string.lyrics).uppercase(),
                    style = typo().titleMedium,
                    color = Color.White,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    enabled = lyricsData != null,
                    onClick = { showShareSheet = true },
                ) {
                    Icon(
                        imageVector = SimpIcons.Share,
                        contentDescription = "Share",
                        tint = if (lyricsData != null) Color.White else Color.White.copy(alpha = 0.3f),
                    )
                }
            }
            if (lyricsData != null) {
                LyricsView(
                    lyricsData = lyricsData,
                    timeLine = state.timelineFlow,
                    onLineClick = { fraction ->
                        actions.onUIEvent(UIEvent.UpdateProgress(fraction))
                    },
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            // Breathing room: the Classic renderer insets nothing itself
                            // (the Apple branch insets its own 20dp), so without this
                            // every line sat flush against the screen edges.
                            .padding(horizontal = 24.dp),
                    backgroundColor = Color.Transparent,
                )
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text =
                            stringResource(
                                when (lyricsData.lyrics?.syncType) {
                                    "RICH_SYNCED" -> Res.string.rich_synced
                                    "LINE_SYNCED" -> Res.string.line_synced
                                    else -> Res.string.unsynced
                                },
                            ),
                        style = typo().bodySmall,
                        color = EarixMuted,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text =
                            when (lyricsData.lyricsProvider) {
                                LyricsProvider.SIMPMUSIC -> stringResource(Res.string.lyrics_provider_simpmusic)
                                LyricsProvider.LRCLIB -> stringResource(Res.string.lyrics_provider_lrc)
                                LyricsProvider.YOUTUBE -> stringResource(Res.string.lyrics_provider_youtube)
                                LyricsProvider.SPOTIFY -> stringResource(Res.string.spotify_lyrics_provider)
                                LyricsProvider.OFFLINE -> stringResource(Res.string.offline_mode)
                                LyricsProvider.BETTER_LYRICS -> stringResource(Res.string.lyrics_provider_betterlyrics)
                                LyricsProvider.AI -> ""
                            },
                        style = typo().bodySmall,
                        color = EarixMuted.copy(alpha = 0.7f),
                    )
                }
            } else {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.unsynced),
                        style = typo().bodyMedium,
                        color = EarixMuted,
                    )
                }
            }
            // Purple home indicator to match the player controls.
            Box(
                modifier =
                    Modifier
                        .padding(bottom = 8.dp)
                        .size(width = 48.dp, height = 4.dp)
                        .background(EarixPurple, shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                        .align(Alignment.CenterHorizontally),
            )
        }
    }

    if (showShareSheet && lyricsData != null) {
        ShareLyricsSheet(
            lines = lyricsData.toShareLyricsLines(),
            songTitle = state.screenData.nowPlayingTitle,
            artistName = state.screenData.artistName,
            artwork = state.screenData.bitmap,
            seedColor = EarixPurple,
            initialLineIndex = state.currentLyricLineIndex,
            onDismiss = { showShareSheet = false },
        )
    }
}
