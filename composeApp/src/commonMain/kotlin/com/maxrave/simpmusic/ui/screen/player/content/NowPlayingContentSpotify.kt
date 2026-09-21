@file:OptIn(ExperimentalMaterial3Api::class)

package com.maxrave.simpmusic.ui.screen.player.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maxrave.common.Config.MAIN_PLAYER
import com.maxrave.domain.mediaservice.handler.RepeatState
import com.maxrave.simpmusic.expect.ui.MediaPlayerView
import com.maxrave.simpmusic.expect.ui.MediaPlayerViewWithSubtitle
import com.maxrave.simpmusic.expect.ui.PlatformCastButton
import com.maxrave.simpmusic.expect.ui.toImageBitmap
import com.maxrave.simpmusic.extension.formatDuration
import com.maxrave.simpmusic.ui.component.AddToPlaylistModalBottomSheet
import com.maxrave.simpmusic.ui.component.ExplicitBadge
import com.maxrave.simpmusic.ui.component.FullscreenLyricsSheet
import com.maxrave.simpmusic.ui.component.HeartCheckBox
import com.maxrave.simpmusic.ui.component.InfoPlayerBottomSheet
import com.maxrave.simpmusic.ui.component.NowPlayingBottomSheet
import com.maxrave.simpmusic.ui.component.QueueBottomSheet
import com.maxrave.simpmusic.ui.component.lyrics.NowPlayingLyricsPage
import com.maxrave.simpmusic.ui.component.rememberHolderPainter
import com.maxrave.simpmusic.ui.screen.player.content.expressive.WavySeekBar
import com.maxrave.simpmusic.ui.icon.Fullscreen
import com.maxrave.simpmusic.ui.icon.Lyrics
import com.maxrave.simpmusic.ui.icon.MoreVert
import com.maxrave.simpmusic.ui.icon.Pause
import com.maxrave.simpmusic.ui.icon.PlayArrow
import com.maxrave.simpmusic.ui.icon.PlaylistAdd
import com.maxrave.simpmusic.ui.icon.QueueMusic
import com.maxrave.simpmusic.ui.icon.Repeat
import com.maxrave.simpmusic.ui.icon.RepeatOne
import com.maxrave.simpmusic.ui.icon.Sensors
import com.maxrave.simpmusic.ui.icon.Shuffle
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.icon.SkipNext
import com.maxrave.simpmusic.ui.icon.SkipPrevious
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.NowPlayingBottomSheetUIEvent
import com.maxrave.simpmusic.viewModel.NowPlayingBottomSheetViewModel
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.maxrave.simpmusic.viewModel.UIEvent
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.earix_nowplaying_bg

private val EarixPurple = Color(0xFFA855F7)
private val EarixPurpleDeep = Color(0xFF7C3AED)
private val EarixMuted = Color(0xFFB0A5C0)
private val EarixSliderTrack = Color(0xFF4A2E6B)
private val EarixGlass = Color.White.copy(alpha = 0.12f)
private val EarixGlassBorder = Color.White.copy(alpha = 0.22f)
private const val SKIP_MS = 10_000L

/**
 * Earix Now Playing UI (default style): nebula backdrop, stacked album art with
 * purple glow, live line-synced lyric, serif title, thick purple seek bar with
 * glowing handle, 7-button transport incl. ±10s skip, and a bottom utility row.
 *
 * Reads only [NowPlayingContentState] and calls back only through
 * [NowPlayingContentActions] — all playback logic lives in the shell / VM.
 * navController + sharedViewModel are threaded through for the sheets only,
 * exactly like the previous revision of this style.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingContentSpotify(
    state: NowPlayingContentState,
    actions: NowPlayingContentActions,
    navController: NavController,
    sharedViewModel: SharedViewModel = koinInject(),
) {
    val localDensity = LocalDensity.current
    val isRepeatOne = state.controllerState.repeatState is RepeatState.One

    // Earix lyrics page overlay (lyrics icon to open).
    var showLyricsPage by rememberSaveable { mutableStateOf(false) }

    // Sheets / dialogs (same set as before — playback logic untouched).
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var showFullscreenLyrics by rememberSaveable { mutableStateOf(false) }
    var showQueueBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showInfoBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showAddToPlaylistDirectly by rememberSaveable { mutableStateOf(false) }
    var showHideFullscreenOverlay by rememberSaveable { mutableStateOf(false) }

    // ±10s seek through the existing UpdateProgress event (fraction 0..100),
    // clamped so it never goes below 0 or past the duration.
    fun seekRelative(deltaMs: Long) {
        val total = state.timelineState.total
        if (total <= 0L) return
        val target = (state.timelineState.current + deltaMs).coerceIn(0L, total)
        actions.onUIEvent(UIEvent.UpdateProgress(target * 100f / total))
    }

    Box(Modifier.fillMaxSize()) {
        // Earix Now Playing backdrop — used ONLY here, nowhere else in the app.
        Image(
            painter = painterResource(Res.drawable.earix_nowplaying_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x660D0618)),
        )

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Spacer(
                Modifier.height(
                    with(localDensity) { WindowInsets.statusBars.getTop(localDensity).toDp() },
                ),
            )

            // ── 1. Top bar ──
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { actions.onDismiss() }) {
                    Icon(
                        imageVector = state.dismissIcon,
                        contentDescription = "Minimize",
                        tint = Color.White,
                    )
                }
                Text(
                    text = "NOW PLAYING",
                    style = typo().bodyMedium,
                    color = Color.White,
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showSheet = true }) {
                    Icon(
                        imageVector = SimpIcons.MoreVert,
                        contentDescription = "More",
                        tint = Color.White,
                    )
                }
            }

            // ── 2. Album art (swipeable across the queue) ──
            BoxWithConstraints(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val artSize = minOf(maxWidth * 0.7f, maxHeight * 0.94f)
                HorizontalPager(
                    state = state.artworkPagerState,
                    modifier = Modifier.size(artSize),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = !isRepeatOne && state.artworkQueue.isNotEmpty(),
                    key = { idx ->
                        val vid = state.artworkQueue.getOrNull(idx)?.videoId.orEmpty()
                        "artwork_${vid}_$idx"
                    },
                ) { page ->
                    val pageTrack = state.artworkQueue.getOrNull(page)
                    val isCurrentArtworkPage = page == state.currentOrderIndex
                    val pageHasCanvas = isCurrentArtworkPage && state.screenData.canvasData != null
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (pageHasCanvas) {
                            // Canvas keeps playing on the current page.
                            Crossfade(targetState = state.screenData.canvasData?.isVideo) { isVideo ->
                                if (isVideo == true) {
                                    state.screenData.canvasData?.url?.let { url ->
                                        MediaPlayerView(
                                            url = url,
                                            modifier =
                                                Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(16.dp)),
                                        )
                                    }
                                } else if (isVideo == false) {
                                    val canvasUrl = state.screenData.canvasData?.url
                                    val canvasContext = LocalPlatformContext.current
                                    val canvasModel =
                                        remember(canvasUrl) {
                                            ImageRequest
                                                .Builder(canvasContext)
                                                .data(canvasUrl)
                                                .diskCachePolicy(CachePolicy.ENABLED)
                                                .diskCacheKey(canvasUrl)
                                                .crossfade(550)
                                                .build()
                                        }
                                    AsyncImage(
                                        model = canvasModel,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(16.dp)),
                                    )
                                }
                            }
                        } else if (isCurrentArtworkPage) {
                            EarixStackedArtwork(
                                state = state,
                                actions = actions,
                                showHideFullscreenOverlay = showHideFullscreenOverlay,
                                onToggleVideoOverlay = {
                                    showHideFullscreenOverlay = !showHideFullscreenOverlay
                                },
                            )
                        } else if (pageTrack != null) {
                            val staticThumb =
                                pageTrack.thumbnails
                                    ?.maxByOrNull { it.width * it.height }
                                    ?.url
                            Box(contentAlignment = Alignment.Center) {
                                // Stacked back card.
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .offset(x = 10.dp, y = 10.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color.White.copy(alpha = 0.08f)),
                                )
                                val staticContext = LocalPlatformContext.current
                                val staticModel =
                                    remember(staticThumb) {
                                        ImageRequest
                                            .Builder(staticContext)
                                            .data(staticThumb)
                                            .diskCachePolicy(CachePolicy.ENABLED)
                                            .diskCacheKey(staticThumb)
                                            .crossfade(300)
                                            .build()
                                    }
                                AsyncImage(
                                    model = staticModel,
                                    contentDescription = pageTrack.title,
                                    contentScale = ContentScale.Crop,
                                    placeholder = rememberHolderPainter(),
                                    error = rememberHolderPainter(),
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .border(1.dp, EarixGlassBorder, RoundedCornerShape(16.dp)),
                                )
                            }
                        }
                    }
                }
            }

            // ── 3. Line-synced lyric (hidden when unavailable) ──
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(32.dp),
            ) {
                val inlineLyrics = state.screenData.lyricsData?.lyrics
                val hasSyncedLyrics =
                    inlineLyrics != null &&
                        inlineLyrics.syncType != null &&
                        inlineLyrics.syncType != "UNSYNCED" &&
                        inlineLyrics.lines != null
                val currentLyricLineText =
                    if (!hasSyncedLyrics ||
                        state.screenData.canvasData != null ||
                        state.currentLyricLineIndex < 0
                    ) {
                        ""
                    } else {
                        inlineLyrics
                            ?.lines
                            ?.getOrNull(state.currentLyricLineIndex)
                            ?.words
                            ?.stripRichSyncTimestamps()
                            .orEmpty()
                    }
                Crossfade(
                    targetState = currentLyricLineText,
                    animationSpec = tween(durationMillis = 300),
                    label = "inlineLyricLine",
                ) { lineText ->
                    if (lineText.isNotBlank()) {
                        Text(
                            text = lineText,
                            color = Color.White,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                        )
                    }
                }
            }

            // ── 4. Title + artist + heart ──
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.screenData.nowPlayingTitle,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        maxLines = 1,
                        color = Color.White,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    animationMode = MarqueeAnimationMode.Immediately,
                                ).focusable(),
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.screenData.isExplicit) {
                            ExplicitBadge(
                                modifier =
                                    Modifier
                                        .size(18.dp)
                                        .padding(end = 4.dp),
                            )
                        }
                        Text(
                            text = state.screenData.artistName,
                            style = typo().bodyMedium,
                            fontSize = 15.sp,
                            color = EarixMuted,
                            maxLines = 1,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        animationMode = MarqueeAnimationMode.Immediately,
                                    ).focusable()
                                    .clickable { actions.onNavigateToArtist() },
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                HeartCheckBox(
                    checked = state.controllerState.isLiked,
                    size = 32,
                    tint = Color.White,
                ) {
                    actions.onUIEvent(UIEvent.ToggleLike)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── 5 + 6. Progress slider + times ──
            if (state.timelineState.loading) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        color = EarixPurple,
                        trackColor = EarixSliderTrack,
                        strokeCap = StrokeCap.Round,
                    )
                }
            } else {
                // Wavy seek bar: animated sine-wave line, purple played portion,
                // muted track, glowing handle — tappable/draggable to seek.
                WavySeekBar(
                    progressFraction = state.sliderValue / 100f,
                    isPlaying = state.controllerState.isPlaying,
                    activeColor = EarixPurple,
                    trackColor = EarixSliderTrack,
                    thumbColor = EarixPurple,
                    onSliderChange = { actions.onSliderChange(it) },
                    onSliderChangeFinished = { actions.onSliderChangeFinished() },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            ) {
                Text(
                    text = formatDuration((state.timelineState.total * (state.sliderValue / 100f)).roundToLong()),
                    style = typo().bodyMedium,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Left,
                )
                Text(
                    text = formatDuration(state.timelineState.total),
                    style = typo().bodyMedium,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Right,
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── 7. Main controls: shuffle, prev, -10s, play, +10s, next, repeat ──
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(84.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Shuffle — glass circle, purple when active.
                Box(
                    modifier =
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(EarixGlass)
                            .border(1.dp, EarixGlassBorder, CircleShape)
                            .clickable { actions.onUIEvent(UIEvent.Shuffle) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SimpIcons.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (state.controllerState.isShuffle) EarixPurple else Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                // Previous.
                Box(
                    modifier =
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .clickable(enabled = state.controllerState.isPreviousAvailable) {
                                actions.onUIEvent(UIEvent.Previous)
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SimpIcons.SkipPrevious,
                        contentDescription = "Previous",
                        tint =
                            if (state.controllerState.isPreviousAvailable) {
                                Color.White
                            } else {
                                Color.White.copy(alpha = 0.4f)
                            },
                        modifier = Modifier.size(34.dp),
                    )
                }
                // -10s.
                SkipButton(forward = false) { seekRelative(-SKIP_MS) }
                // Play / pause — large purple gradient disc with glow ring.
                Box(
                    modifier =
                        Modifier
                            .size(76.dp)
                            .shadow(
                                elevation = 16.dp,
                                shape = CircleShape,
                                spotColor = EarixPurple.copy(alpha = 0.65f),
                                ambientColor = EarixPurple.copy(alpha = 0.3f),
                            ).background(
                                brush =
                                    Brush.radialGradient(
                                        colors = listOf(EarixPurple, EarixPurpleDeep),
                                    ),
                                shape = CircleShape,
                            ).border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                            .clickable { actions.onUIEvent(UIEvent.PlayPause) },
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(targetState = state.controllerState.isPlaying, label = "earixPlayPause") { playing ->
                        Icon(
                            imageVector = if (playing) SimpIcons.Pause else SimpIcons.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                // +10s.
                SkipButton(forward = true) { seekRelative(SKIP_MS) }
                // Next.
                Box(
                    modifier =
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .clickable(enabled = state.controllerState.isNextAvailable) {
                                actions.onUIEvent(UIEvent.Next)
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SimpIcons.SkipNext,
                        contentDescription = "Next",
                        tint =
                            if (state.controllerState.isNextAvailable) {
                                Color.White
                            } else {
                                Color.White.copy(alpha = 0.4f)
                            },
                        modifier = Modifier.size(34.dp),
                    )
                }
                // Repeat — glass circle, purple when active (RepeatOne glyph for One).
                Box(
                    modifier =
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(EarixGlass)
                            .border(1.dp, EarixGlassBorder, CircleShape)
                            .clickable { actions.onUIEvent(UIEvent.Repeat) },
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(targetState = state.controllerState.repeatState, label = "earixRepeat") { rs ->
                        when (rs) {
                            is RepeatState.None ->
                                Icon(
                                    imageVector = SimpIcons.Repeat,
                                    contentDescription = "Repeat",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            RepeatState.All ->
                                Icon(
                                    imageVector = SimpIcons.Repeat,
                                    contentDescription = "Repeat all",
                                    tint = EarixPurple,
                                    modifier = Modifier.size(22.dp),
                                )
                            RepeatState.One ->
                                Icon(
                                    imageVector = SimpIcons.RepeatOne,
                                    contentDescription = "Repeat one",
                                    tint = EarixPurple,
                                    modifier = Modifier.size(22.dp),
                                )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── 8. Bottom utility row ──
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlatformCastButton(
                        modifier = Modifier.size(24.dp),
                        tint = if (state.castState.isRemote) Color.Cyan else Color.White,
                    )
                    Icon(
                        imageVector = SimpIcons.Sensors,
                        contentDescription = "Full lyrics",
                        tint = Color.White,
                        modifier =
                            Modifier
                                .size(24.dp)
                                .clickable { showFullscreenLyrics = true },
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = SimpIcons.Lyrics,
                        contentDescription = "Lyrics",
                        tint = Color.White,
                        modifier =
                            Modifier
                                .size(24.dp)
                                .clickable { showLyricsPage = true },
                    )
                    Icon(
                        imageVector = SimpIcons.PlaylistAdd,
                        contentDescription = "Add to playlist",
                        tint = Color.White,
                        modifier =
                            Modifier
                                .size(24.dp)
                                .clickable { actions.onShowAddToPlaylist() },
                    )
                    Icon(
                        imageVector = SimpIcons.QueueMusic,
                        contentDescription = "Queue",
                        tint = Color.White,
                        modifier =
                            Modifier
                                .size(24.dp)
                                .clickable { actions.onShowQueue() },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Earix lyrics page overlay.
        AnimatedVisibility(
            visible = showLyricsPage,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        ) {
            NowPlayingLyricsPage(
                state = state,
                actions = actions,
                onBack = { showLyricsPage = false },
            )
        }
    }

    // ── Sheets (unchanged behaviour; vote dialog is owned by the shell) ──
    if (showSheet) {
        NowPlayingBottomSheet(
            onDismiss = { showSheet = false },
            navController = navController,
            onNavigateToOtherScreen = { actions.onDismiss() },
            song = null,
            setSleepTimerEnable = true,
            changeMainLyricsProviderEnable = true,
        )
    }
    if (showFullscreenLyrics) {
        FullscreenLyricsSheet(
            sharedViewModel = sharedViewModel,
            navController = navController,
            color = state.startColor.value,
        ) {
            showFullscreenLyrics = false
        }
    }
    if (showQueueBottomSheet) {
        QueueBottomSheet(onDismiss = { showQueueBottomSheet = false })
    }
    if (showInfoBottomSheet) {
        InfoPlayerBottomSheet(onDismiss = { showInfoBottomSheet = false })
    }
    if (showAddToPlaylistDirectly) {
        val viewModel: NowPlayingBottomSheetViewModel = koinViewModel()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) {
            viewModel.resetPlaylists()
            viewModel.setSongEntity(null)
        }
        AddToPlaylistModalBottomSheet(
            isBottomSheetVisible = true,
            listLocalPlaylist = uiState.listLocalPlaylist,
            listYouTubePlaylist = uiState.listYouTubePlaylist,
            onDismiss = { showAddToPlaylistDirectly = false },
            onClick = { playlist ->
                viewModel.onUIEvent(NowPlayingBottomSheetUIEvent.AddToPlaylist(playlist.id))
                showAddToPlaylistDirectly = false
            },
            onYTPlaylistClick = { playlist ->
                viewModel.onUIEvent(NowPlayingBottomSheetUIEvent.AddToYouTubePlaylist(playlist.browseId))
                showAddToPlaylistDirectly = false
            },
            videoId = uiState.songUIState.videoId,
        )
    }
}

/**
 * Circular glass ±10s button: a THIN arc arrow around the edge (never under the
 * text) with a bold "10" in the clear center. Counter-clockwise for rewind,
 * clockwise for forward.
 */
@Composable
private fun SkipButton(
    forward: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(EarixGlass)
                .border(1.dp, EarixGlassBorder, CircleShape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SkipArc(forward = forward)
        Text(
            text = "10",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

/** Thin circular arrow: 2dp arc with a gap at the top + arrowhead at the gap. */
@Composable
private fun SkipArc(forward: Boolean) {
    Canvas(Modifier.size(44.dp)) {
        val stroke = 2.dp.toPx()
        val r = (size.minDimension - stroke) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        // Gap at the top; clockwise sweep for forward, counter-clockwise for rewind.
        val start = if (forward) -60f else -120f
        val sweep = if (forward) 300f else -300f
        drawArc(
            color = Color.White,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(r * 2f, r * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // Arrowhead at the arc's end, pointing along the travel direction.
        val endRad = Math.toRadians((start + sweep).toDouble())
        val ex = c.x + r * cos(endRad).toFloat()
        val ey = c.y + r * sin(endRad).toFloat()
        // Unit tangent: clockwise (-sin, cos), counter-clockwise (sin, -cos).
        val (tx, ty) =
            if (forward) {
                -sin(endRad).toFloat() to cos(endRad).toFloat()
            } else {
                sin(endRad).toFloat() to -cos(endRad).toFloat()
            }
        // Unit normal (perpendicular to tangent).
        val nx = -ty
        val ny = tx
        val tipX = ex + tx * 7.dp.toPx()
        val tipY = ey + ty * 7.dp.toPx()
        val baseX = ex - tx * 1.dp.toPx()
        val baseY = ey - ty * 1.dp.toPx()
        val halfW = 4.dp.toPx()
        drawPath(
            path =
                Path().apply {
                    moveTo(tipX, tipY)
                    lineTo(baseX + nx * halfW, baseY + ny * halfW)
                    lineTo(baseX - nx * halfW, baseY - ny * halfW)
                    close()
                },
            color = Color.White,
        )
    }
}

/**
 * Current-track artwork with the Earix stacked-card treatment: offset back card,
 * 16dp rounded main art, frosted frame and purple glow shadow. Palette extraction
 * is preserved so dependent surfaces keep their tint.
 */
@Composable
private fun EarixStackedArtwork(
    state: NowPlayingContentState,
    actions: NowPlayingContentActions,
    showHideFullscreenOverlay: Boolean,
    onToggleVideoOverlay: () -> Unit,
) {
    val imageUrl = state.screenData.thumbnailURL
    var artworkUrl by remember(imageUrl) { mutableStateOf(imageUrl) }
    val pageScope = rememberCoroutineScope()
    val showVideo = state.screenData.isVideo && state.shouldShowVideo
    Box(contentAlignment = Alignment.Center) {
        // Stacked back card.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .offset(x = 12.dp, y = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxSize()
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(16.dp),
                        spotColor = EarixPurple.copy(alpha = 0.55f),
                        ambientColor = Color.Transparent,
                    ).clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1A0B2E))
                    .border(1.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
        ) {
            // Rebuilt only when the URL changes: this box recomposes on
            // every progress tick while the art itself never does.
            val artContext = LocalPlatformContext.current
            val artModel =
                remember(artworkUrl) {
                    ImageRequest
                        .Builder(artContext)
                        .data(artworkUrl)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .diskCacheKey(artworkUrl + "BIGGER")
                        .crossfade(550)
                        .build()
                }
            AsyncImage(
                model = artModel,
                contentDescription = "",
                onSuccess = {
                    // Palette tint for dependent surfaces (fullscreen lyrics sheet).
                    val bitmap = it.result.image.toImageBitmap()
                    pageScope.launch { actions.onArtworkBitmap(bitmap) }
                },
                onError = {
                    val fallback = artworkUrl?.replace("maxresdefault", "hqdefault")
                    if (fallback != null && fallback != artworkUrl) artworkUrl = fallback
                },
                contentScale = ContentScale.Crop,
                placeholder = rememberHolderPainter(),
                error = rememberHolderPainter(),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .alpha(if (!showVideo) 1f else 0f),
            )
            if (showVideo) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                            .clickable(
                                onClick = onToggleVideoOverlay,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ),
                ) {
                    MediaPlayerViewWithSubtitle(
                        playerName = MAIN_PLAYER,
                        modifier = Modifier.fillMaxSize(),
                        shouldShowSubtitle = true,
                        shouldPip = false,
                        shouldScaleDownSubtitle = true,
                        timelineState = state.timelineState,
                        lyricsData = state.screenData.lyricsData?.lyrics,
                        translatedLyricsData = state.screenData.lyricsData?.translatedLyrics?.first,
                        isInPipMode = state.isInPipMode,
                        mainTextStyle = typo().bodyLarge,
                        translatedTextStyle = typo().bodyMedium,
                    )
                }
                if (showHideFullscreenOverlay) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f))
                                .clickable(
                                    onClick = onToggleVideoOverlay,
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ),
                    ) {
                        IconButton(
                            onClick = { actions.onEnterFullscreenVideo() },
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Icon(
                                imageVector = SimpIcons.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}
