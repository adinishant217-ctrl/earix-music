package com.maxrave.simpmusic.ui.screen.lyrics

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.simpmusic.expect.ui.rememberBackdrop
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.CheckCircle
import com.maxrave.simpmusic.ui.icon.Error
import com.maxrave.simpmusic.ui.icon.LibraryMusic
import com.maxrave.simpmusic.ui.screen.MiniPlayer
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.maxrave.simpmusic.viewModel.SyncLyricsViewModel
import com.maxrave.simpmusic.viewModel.SyncSongMetadata
import com.maxrave.simpmusic.viewModel.SyncedLine
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.add_lyrics
import simpmusic.composeapp.generated.resources.already_has_lyrics
import simpmusic.composeapp.generated.resources.cancel
import simpmusic.composeapp.generated.resources.done
import simpmusic.composeapp.generated.resources.earix_background
import simpmusic.composeapp.generated.resources.line_of
import simpmusic.composeapp.generated.resources.lyrics_published
import simpmusic.composeapp.generated.resources.next
import simpmusic.composeapp.generated.resources.no_connection
import simpmusic.composeapp.generated.resources.no_lyrics_available
import simpmusic.composeapp.generated.resources.one_line_per_lyric
import simpmusic.composeapp.generated.resources.paste_lyrics_here
import simpmusic.composeapp.generated.resources.publishing
import simpmusic.composeapp.generated.resources.resync
import simpmusic.composeapp.generated.resources.retry
import simpmusic.composeapp.generated.resources.review
import simpmusic.composeapp.generated.resources.submit_to_lrclib
import simpmusic.composeapp.generated.resources.sync_lyrics
import simpmusic.composeapp.generated.resources.tap
import simpmusic.composeapp.generated.resources.tap_in_time
import simpmusic.composeapp.generated.resources.thanks_contributing
import simpmusic.composeapp.generated.resources.undo

private val EarixPurple = Color(0xFFA855F7)
private val EarixMuted = Color(0xFFB0A5C0)
private val GlassCard = Color.White.copy(alpha = 0.08f)
private val TapGradient = Brush.linearGradient(listOf(Color(0xFFA855F7), Color(0xFF7C3AED)))

private fun formatLrcTimestamp(ms: Long): String {
    val minutes = (ms / 60000).toString().padStart(2, '0')
    val seconds = ((ms % 60000) / 1000).toString().padStart(2, '0')
    val centis = ((ms % 1000) / 10).toString().padStart(2, '0')
    return "[$minutes:$seconds.$centis]"
}

/**
 * Tap-to-sync + anonymous LRCLIB publishing. Dormant feature: pure UI over
 * [SyncLyricsViewModel]; never touches playback (position is observed only).
 */
@Composable
fun SyncLyricsScreen(
    metadata: SyncSongMetadata,
    onBack: () -> Unit,
    vm: SyncLyricsViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val positionMs by vm.currentPositionMs.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
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
        // System bars + IME: buttons sit above the nav bar and rise with the keyboard.
        // Content takes its own weighted space; the mini player is pinned at the bottom
        // in every state so nothing overlaps and playback stays controllable.
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SyncTopBar(
                    title =
                        if (state.syncedLines.isNotEmpty() && state.currentIndex >= state.lines.size &&
                            state.publishResult == null && !state.isPublishing
                        ) {
                            stringResource(Res.string.review)
                        } else {
                            stringResource(Res.string.sync_lyrics)
                        },
                    showUndo = state.lines.isNotEmpty() && state.currentIndex < state.lines.size,
                    onBack = onBack,
                    onUndo = { vm.undoLastTap() },
                )
                SongInfoCard(title = metadata.title, artist = metadata.artist)
                when {
                    state.isPublishing -> PublishingState(powText = state.powProgressText)
                    state.publishResult != null -> SuccessState(onDone = onBack)
                    state.error != null -> ErrorState(
                        message =
                            if (state.error == SyncLyricsViewModel.ALREADY_EXISTS) {
                                stringResource(Res.string.already_has_lyrics)
                            } else {
                                state.error ?: ""
                            },
                        onRetry = { vm.clearResult() },
                        onCancel = onBack,
                    )

                    state.lines.isEmpty() -> PasteState(
                        rawLyrics = state.rawLyrics,
                        onRawChange = { vm.setRawLyrics(it) },
                        onNext = {},
                    )

                    state.currentIndex < state.lines.size -> SyncingState(
                        lines = state.lines,
                        currentIndex = state.currentIndex,
                        positionMs = positionMs,
                        onTap = { vm.tapAtPosition(positionMs) },
                    )

                    else -> ReviewState(
                        synced = state.syncedLines,
                        onResync = { vm.resync() },
                        onSubmit = { vm.submit(metadata) },
                    )
                }
            }
            SyncMiniPlayer(onBack = onBack)
        }
    }
}

@Composable
private fun SyncTopBar(
    title: String,
    showUndo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = SimpIcons.ArrowBackIosNew,
                contentDescription = "Back",
                tint = Color.White,
            )
        }
        Text(
            text = title,
            style = typo().titleMedium,
            color = Color.White,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        if (showUndo) {
            TextButton(onClick = onUndo) {
                Text(text = stringResource(Res.string.undo), color = EarixPurple)
            }
        } else {
            Spacer(modifier = Modifier.width(48.dp))
        }
    }
}

@Composable
private fun SongInfoCard(
    title: String,
    artist: String,
) {
    Surface(
        color = GlassCard,
        shape = RoundedCornerShape(16.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = typo().titleSmall, color = Color.White, maxLines = 1)
            Text(text = artist, style = typo().bodySmall, color = EarixMuted, maxLines = 1)
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun PasteState(
    rawLyrics: String,
    onRawChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
    ) {
        Surface(
            color = GlassCard,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = rawLyrics,
                onValueChange = onRawChange,
                placeholder = { Text(stringResource(Res.string.paste_lyrics_here), color = EarixMuted) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
                minLines = 8,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.one_line_per_lyric),
            style = typo().bodySmall,
            color = EarixMuted,
        )
        Spacer(modifier = Modifier.weight(1f))
        PurpleButton(
            text = stringResource(Res.string.next),
            enabled = rawLyrics.isNotBlank(),
            onClick = onNext,
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun SyncingState(
    lines: List<String>,
    currentIndex: Int,
    positionMs: Long,
    onTap: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val pressSource = remember { MutableInteractionSource() }
    val pressed by pressSource.collectIsPressedAsState()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.line_of, currentIndex + 1, lines.size),
            style = typo().bodySmall,
            color = EarixMuted,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            color = GlassCard,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = lines[currentIndex],
                    style = typo().headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                if (currentIndex + 1 < lines.size) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = lines[currentIndex + 1],
                        style = typo().bodySmall,
                        color = EarixMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(200.dp)
                    .graphicsLayer {
                        scaleX = if (pressed) 0.95f else 1f
                        scaleY = if (pressed) 0.95f else 1f
                    }.clip(CircleShape)
                    .background(TapGradient)
                    .clickable(
                        interactionSource = pressSource,
                        indication = null,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTap()
                        },
                    ),
        ) {
            Text(
                text = stringResource(Res.string.tap),
                style = typo().headlineMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = Color.White,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.tap_in_time),
            style = typo().bodySmall,
            color = EarixMuted,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { currentIndex.toFloat() / lines.size.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth(),
            color = EarixPurple,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatLrcTimestamp(positionMs),
            style = typo().labelSmall,
            color = EarixMuted,
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ReviewState(
    synced: List<SyncedLine>,
    onResync: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(synced, key = { it.timestampMs to it.text }) { line ->
                Surface(
                    color = GlassCard,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = formatLrcTimestamp(line.timestampMs),
                            style = typo().labelSmall,
                            color = EarixPurple,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = line.text,
                            style = typo().bodyMedium,
                            color = Color.White,
                        )
                    }
                }
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onResync,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(Res.string.resync))
            }
            PurpleButton(
                text = stringResource(Res.string.submit_to_lrclib),
                enabled = true,
                onClick = onSubmit,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PublishingState(powText: String?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = EarixPurple)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = stringResource(Res.string.publishing), style = typo().bodyMedium, color = Color.White)
        if (powText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = powText, style = typo().labelSmall, color = EarixMuted)
        }
    }
}

/**
 * The shared MiniPlayer, pinned at the bottom in every sync state. Same composable
 * as Home/Library/Search (play/pause, prev/next, artwork). Tap returns to the
 * player underneath (the sync screen opened from it); close mirrors the bottom
 * bar behaviour (stops playback).
 */
@Composable
private fun SyncMiniPlayer(onBack: () -> Unit) {
    val backdrop = rememberBackdrop(Color.Black)
    val sharedVm: SharedViewModel = koinInject()
    MiniPlayer(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
        backdrop = backdrop,
        sharedViewModel = sharedVm,
        onClose = {
            sharedVm.stopPlayer()
            sharedVm.isServiceRunning = false
        },
        onClick = onBack,
    )
}

@Composable
private fun SuccessState(onDone: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = SimpIcons.CheckCircle,
            contentDescription = null,
            tint = EarixPurple,
            modifier = Modifier.size(72.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.lyrics_published),
            style = typo().headlineSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.thanks_contributing),
            style = typo().bodyMedium,
            color = EarixMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        PurpleButton(text = stringResource(Res.string.done), enabled = true, onClick = onDone)
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = SimpIcons.Error,
            contentDescription = null,
            tint = Color(0xFFF87171),
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = typo().bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.cancel))
            }
            PurpleButton(text = stringResource(Res.string.retry), enabled = true, onClick = onRetry, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun PurpleButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            ),
        contentPadding = PaddingValues(0.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .background(TapGradient, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp)),
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

@Composable
fun SyncLyricsRetryState(onRetry: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.no_connection),
            style = typo().bodyMedium,
            color = EarixMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PurpleButton(text = stringResource(Res.string.retry), enabled = true, onClick = onRetry)
    }
}

@Composable
fun SyncLyricsEmptyState(onAddLyrics: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = SimpIcons.LibraryMusic,
            contentDescription = null,
            tint = EarixMuted,
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.no_lyrics_available),
            style = typo().bodyMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PurpleButton(text = stringResource(Res.string.add_lyrics), enabled = true, onClick = onAddLyrics)
    }
}
