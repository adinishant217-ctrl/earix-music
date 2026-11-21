package com.maxrave.simpmusic.ui.screen.listentogether

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maxrave.domain.data.model.listentogether.ListenTogetherRoom
import com.maxrave.domain.data.model.listentogether.RoomConnection
import com.maxrave.domain.data.model.listentogether.RoomMember
import com.maxrave.simpmusic.expect.copyToClipboard
import com.maxrave.simpmusic.expect.ui.PlatformBackdrop
import com.maxrave.simpmusic.expect.ui.layerBackdrop
import com.maxrave.simpmusic.expect.ui.rememberBackdrop
import com.maxrave.simpmusic.ui.component.AmbientThemeGlow
import com.maxrave.simpmusic.ui.component.LiquidGlassIconButton
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.Check
import com.maxrave.simpmusic.ui.icon.Close
import com.maxrave.simpmusic.ui.icon.ContentCopy
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.navigation.destination.listentogether.ListenTogetherSettingsDestination
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.ListenTogetherViewModel
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.cancel
import simpmusic.composeapp.generated.resources.listen_together
import simpmusic.composeapp.generated.resources.lt_background_warning
import simpmusic.composeapp.generated.resources.lt_code_hint
import simpmusic.composeapp.generated.resources.lt_compatible
import simpmusic.composeapp.generated.resources.lt_connect
import simpmusic.composeapp.generated.resources.lt_connected
import simpmusic.composeapp.generated.resources.lt_connecting
import simpmusic.composeapp.generated.resources.lt_copied
import simpmusic.composeapp.generated.resources.lt_copy_code
import simpmusic.composeapp.generated.resources.lt_create_room
import simpmusic.composeapp.generated.resources.lt_disconnect
import simpmusic.composeapp.generated.resources.lt_display_name
import simpmusic.composeapp.generated.resources.lt_display_name_hint
import simpmusic.composeapp.generated.resources.lt_guest_summary
import simpmusic.composeapp.generated.resources.lt_host_badge
import simpmusic.composeapp.generated.resources.lt_host_summary
import simpmusic.composeapp.generated.resources.lt_in_room
import simpmusic.composeapp.generated.resources.lt_join_room
import simpmusic.composeapp.generated.resources.lt_leave_room
import simpmusic.composeapp.generated.resources.lt_not_connected
import simpmusic.composeapp.generated.resources.lt_or_join_with_code
import simpmusic.composeapp.generated.resources.lt_room_code
import simpmusic.composeapp.generated.resources.lt_server_hint
import simpmusic.composeapp.generated.resources.lt_tagline
import simpmusic.composeapp.generated.resources.lt_waiting_approval
import simpmusic.composeapp.generated.resources.lt_waiting_approval_desc
import simpmusic.composeapp.generated.resources.lt_you
import simpmusic.composeapp.generated.resources.settings
import kotlin.math.abs

/**
 * Listen Together — the lobby, and the smallest honest in-room state.
 *
 * Stage 1 of the rebuild: everything that gets you into a room, plus enough of the room to see that
 * you are in one and to get back out. The now-playing card, queue, join requests, suggestions and
 * host controls arrive in stage 2/3; none of that machinery is touched here.
 *
 * Colour, type and surfaces all come from the app's own system — `MaterialTheme.colorScheme`,
 * `typo()`, the Earix primaries and the same ambient glow Home paints — rather than a palette of
 * hard-coded hexes.
 */
private val CardShape = RoundedCornerShape(24.dp)
private val FieldShape = RoundedCornerShape(16.dp)

/**
 * Below this the screen is one column. Two columns need room for two REAL columns — splitting a
 * 700dp window just makes two cramped ones.
 */
private const val TWO_COLUMN_MIN_DP = 900

/** Status-bar inset + the 8.dp above the floating button + its own 48.dp. */
private val BACK_BUTTON_STRIP = 56.dp

/** How long the copy button shows a tick before going back to the copy glyph. */
private const val COPIED_FEEDBACK_MS = 1_800L

private const val ROOM_CODE_LENGTH = ListenTogetherViewModel.ROOM_CODE_LENGTH

/** Avatar tints derived from the theme, so they move with it instead of fighting it. */
@Composable
private fun tintFor(id: String): Color {
    val palette =
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
        )
    val index = id.hashCode().let { if (it == Int.MIN_VALUE) 0 else abs(it) } % palette.size
    return palette[index]
}

private fun initialOf(name: String): String = name.trim().firstOrNull()?.uppercase() ?: "?"

@Composable
fun ListenTogetherScreen(
    navController: NavController,
    innerPadding: PaddingValues,
    viewModel: ListenTogetherViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val codeInput by viewModel.roomCodeInput.collectAsStateWithLifecycle()

    // The back button refracts whatever the page has drawn, so the CONTENT is the backdrop source
    // and the button is its SIBLING — the same arrangement Album/Playlist/Analytics use. Nesting the
    // button inside the source is the render-feedback loop that kills the RuntimeShader.
    val backdrop = rememberBackdrop(MaterialTheme.colorScheme.background)
    val openSettings: () -> Unit = { navController.navigate(ListenTogetherSettingsDestination) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        // Measured from BoxWithConstraints, not the window: this is the space left after the
        // navigation rail on a tablet/desktop shell.
        val wide = maxWidth >= TWO_COLUMN_MIN_DP.dp

        // The backdrop SOURCE is this ground box — page colour plus Home's ambient glow — not the
        // content columns. Glass shows whatever its source recorded, and with the source on mostly
        // transparent content the back button renders as a solid coin over a tinted page.
        Box(Modifier.matchParentSize().layerBackdrop(backdrop)) {
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background))
            AmbientThemeGlow(tint = MaterialTheme.colorScheme.primary)
        }

        if (wide) {
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                // The identity side does not move: the room code is what people read out loud, so
                // it stays on screen while the work area scrolls.
                Column(
                    modifier =
                        Modifier
                            .weight(0.42f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 28.dp, end = 24.dp, top = 14.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    // Reserves the strip the floating back button is drawn over — it is a sibling of
                    // this column and takes part in no measurement.
                    Spacer(Modifier.height(BACK_BUTTON_STRIP))
                    Header(inRoom = state.inRoom)
                    ConnectionRow(
                        connection = state.connection,
                        onConnect = viewModel::connect,
                        onDisconnect = viewModel::disconnect,
                    )
                    AnimatedVisibility(visible = state.inRoom) {
                        RoomCodeCard(state)
                    }
                }

                Column(
                    modifier =
                        Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 8.dp, end = 32.dp, top = 32.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    WorkArea(
                        state = state,
                        displayName = displayName,
                        codeInput = codeInput,
                        viewModel = viewModel,
                        onSettings = openSettings,
                    )
                }
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(innerPadding)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Spacer(Modifier.height(BACK_BUTTON_STRIP))
                Header(inRoom = state.inRoom)
                ConnectionRow(
                    connection = state.connection,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                )
                AnimatedVisibility(visible = state.inRoom) {
                    RoomCodeCard(state)
                }
                WorkArea(
                    state = state,
                    displayName = displayName,
                    codeInput = codeInput,
                    viewModel = viewModel,
                    onSettings = openSettings,
                )
            }
        }

        BackButton(backdrop) { navController.navigateUp() }
    }
}

// ───────────────────────────────── ground ─────────────────────────────────

@Composable
private fun androidx.compose.foundation.layout.BoxScope.BackButton(
    backdrop: PlatformBackdrop,
    onBack: () -> Unit,
) {
    // Glass, like every other back button in the app. It only works as an OVERLAY: in the scrolling
    // column it would sit over the flat page background, and glass with nothing to refract renders
    // as a grey coin. Floating it over the content gives it the page to refract.
    LiquidGlassIconButton(
        backdrop = backdrop,
        imageVector = SimpIcons.ArrowBackIosNew,
        // Not the default Color.White: this page follows the theme, and `onSurface` flips with it.
        tint = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(24.dp),
        modifier =
            Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, top = 8.dp)
                .size(48.dp),
        onClick = onBack,
    )
}

@Composable
private fun Header(inRoom: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(Res.string.listen_together),
            style = typo().titleLarge.copy(fontSize = 30.sp, lineHeight = 34.sp),
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (!inRoom) {
            Text(
                text = stringResource(Res.string.lt_tagline),
                style = typo().bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Connection state as one line of text, not a bordered card with a filled pill in it: it is ambient
 * information, and giving it the same treatment as the room itself made every block rank equally.
 */
@Composable
private fun ConnectionRow(
    connection: RoomConnection,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val connected = connection is RoomConnection.Connected
    val accent =
        when (connection) {
            is RoomConnection.Connected -> MaterialTheme.colorScheme.primary
            is RoomConnection.Connecting -> MaterialTheme.colorScheme.tertiary
            is RoomConnection.Failed -> MaterialTheme.colorScheme.error
            RoomConnection.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val label =
        when (connection) {
            is RoomConnection.Connected -> stringResource(Res.string.lt_connected)
            is RoomConnection.Connecting -> stringResource(Res.string.lt_connecting)
            is RoomConnection.Failed -> connection.reason
            RoomConnection.Disconnected -> stringResource(Res.string.lt_not_connected)
        }

    // Connecting is the one state the user is waiting on, and a static dot is indistinguishable
    // from a stuck one.
    val pulse = rememberInfiniteTransition(label = "ltConnecting")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "ltConnectingAlpha",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    if (connection is RoomConnection.Connecting) accent.copy(alpha = dotAlpha) else accent,
                ),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = typo().bodyMedium,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.lt_server_hint),
                style = typo().bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { if (connected) onDisconnect() else onConnect() }) {
            Text(
                text = if (connected) stringResource(Res.string.lt_disconnect) else stringResource(Res.string.lt_connect),
                style = typo().labelMedium,
                color = if (connected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ───────────────────────────────── room code ─────────────────────────────────

/**
 * The room code, as a poster.
 *
 * This is the one thing on the screen a person reads out loud to someone next to them, so it is the
 * one thing allowed to be outsized — and it sits on a card of its own rather than sharing the
 * primary button's rank. Copy is here in stage 1 because without it the code cannot leave the
 * device; share lands with the room header in stage 2.
 */
@Composable
private fun RoomCodeCard(state: ListenTogetherRoom) {
    val code = state.roomCode.orEmpty()
    val hostName = state.members.firstOrNull { it.isHost }?.username.orEmpty()
    val codeLabel = stringResource(Res.string.lt_room_code)
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_FEEDBACK_MS)
            copied = false
        }
    }

    GlassCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (state.isHost) codeLabel else stringResource(Res.string.lt_in_room),
                style = typo().labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = code.chunked(4).joinToString(" "),
                // Sized so eight monospace characters still fit a 390dp phone once padding is out.
                style =
                    typo().titleLarge.copy(
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    ),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text =
                    if (state.isHost) {
                        stringResource(Res.string.lt_host_summary, state.members.size)
                    } else {
                        stringResource(Res.string.lt_guest_summary, hostName)
                    },
                style = typo().bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    // A silent clipboard write is indistinguishable from a dead button, and this one
                    // is the whole point of the card — the tick is the only proof it did anything.
                    copyToClipboard(codeLabel, code)
                    copied = true
                },
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = if (copied) SimpIcons.Check else SimpIcons.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (copied) stringResource(Res.string.lt_copied) else stringResource(Res.string.lt_copy_code),
                    style = typo().labelMedium,
                )
            }
        }
    }
}

// ───────────────────────────────── work area ─────────────────────────────────

/**
 * Everything that changes as you use the screen: the form before a room, the wait for approval, the
 * people inside one.
 *
 * Emits siblings rather than wrapping itself in a Column, so the compact and two-column skeletons
 * space it identically instead of drifting apart.
 */
@Composable
private fun WorkArea(
    state: ListenTogetherRoom,
    displayName: String,
    codeInput: String,
    viewModel: ListenTogetherViewModel,
    onSettings: () -> Unit,
) {
    // Held so the card keeps its text while it animates out — `state.error` is already null by then
    // and the card would collapse through a frame of empty space.
    var lastError by remember { mutableStateOf("") }
    LaunchedEffect(state.error) { state.error?.let { lastError = it } }
    AnimatedVisibility(visible = state.error != null) {
        ErrorBanner(message = lastError, onDismiss = viewModel::clearError)
    }

    when {
        state.inRoom -> {
            Members(state)
            FooterActions(onLeave = viewModel::leaveRoom, onSettings = onSettings)
        }

        state.pendingJoinCode != null -> {
            WaitingForApproval(code = state.pendingJoinCode.orEmpty(), onCancel = viewModel::cancelJoin)
            FooterActions(onLeave = null, onSettings = onSettings)
        }

        else -> {
            JoinForm(
                displayName = displayName,
                codeInput = codeInput,
                isConnected = state.isConnected,
                onNameChange = viewModel::onDisplayNameChange,
                onCodeChange = viewModel::onRoomCodeChange,
                onCreateRoom = viewModel::createRoom,
                onJoinRoom = viewModel::joinRoom,
            )
            FooterActions(onLeave = null, onSettings = onSettings)
        }
    }

    Text(
        text = stringResource(Res.string.lt_compatible),
        style = typo().bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun JoinForm(
    displayName: String,
    codeInput: String,
    isConnected: Boolean,
    onNameChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = displayName,
            onValueChange = onNameChange,
            singleLine = true,
            label = { Text(stringResource(Res.string.lt_display_name)) },
            supportingText = { Text(stringResource(Res.string.lt_display_name_hint)) },
            shape = FieldShape,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = onCreateRoom,
            enabled = displayName.isNotBlank() && isConnected,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(Res.string.lt_create_room), style = typo().titleSmall)
        }

        DividerLabel(stringResource(Res.string.lt_or_join_with_code))

        CodeInput(code = codeInput, onCodeChange = onCodeChange)

        OutlinedButton(
            onClick = onJoinRoom,
            enabled = displayName.isNotBlank() && codeInput.length == ROOM_CODE_LENGTH && isConnected,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(Res.string.lt_join_room), style = typo().titleSmall)
        }

        Text(
            text = stringResource(Res.string.lt_background_warning),
            style = typo().bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Eight monospace boxes over one invisible text field.
 *
 * `TextFieldValue`, not `String`: a plain String re-places the caret at index 0 on every
 * externally-driven recomposition, and backspace at index 0 deletes nothing. The caret is drawn by
 * hand because the field that owns it is zero-sized — zero-sized but composed, or the field would
 * take no input at all.
 */
@Composable
private fun CodeInput(
    code: String,
    onCodeChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val fieldValue = TextFieldValue(text = code, selection = TextRange(code.length))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicTextField(
            value = fieldValue,
            onValueChange = { onCodeChange(it.text) },
            singleLine = true,
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Box(Modifier.size(0.dp)) { innerTextField() }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    repeat(ROOM_CODE_LENGTH) { index ->
                        val filled = index < code.length
                        val isCaret = index == code.length
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .clip(FieldShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                    .then(
                                        if (isCaret) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, FieldShape)
                                        } else {
                                            Modifier
                                        },
                                    ).clickable { focusRequester.requestFocus() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (filled) code[index].toString() else "",
                                style =
                                    typo().titleSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
            },
        )
        Text(
            text = stringResource(Res.string.lt_code_hint),
            style = typo().bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WaitingForApproval(
    code: String,
    onCancel: () -> Unit,
) {
    GlassCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(Res.string.lt_waiting_approval),
                style = typo().titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = code.chunked(4).joinToString("  "),
                style = typo().titleMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 3.sp),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(Res.string.lt_waiting_approval_desc),
                style = typo().bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.cancel), style = typo().labelMedium)
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.30f), CardShape)
                .clickable { onDismiss() },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                style = typo().bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = SimpIcons.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * The people in the room.
 *
 * Stage 1 deliberately stops at identity and host status: buffering, sync state, moderation and the
 * host's request/suggestion inboxes all arrive with the full room screen.
 */
@Composable
private fun Members(state: ListenTogetherRoom) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(stringResource(Res.string.lt_in_room), state.members.size)
        GlassCard {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                state.members.forEachIndexed { index, member ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .padding(start = 50.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)),
                        )
                    }
                    MemberRow(member = member, isSelf = member.userId == state.selfUserId)
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: RoomMember,
    isSelf: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(tintFor(member.userId).copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initialOf(member.username),
                style = typo().titleSmall,
                color = MaterialTheme.colorScheme.surface,
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = member.username,
                style = typo().bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (member.isHost) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.lt_host_badge),
                        style = typo().labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (isSelf) {
            Text(
                text = stringResource(Res.string.lt_you),
                style = typo().bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Leaving and settings, as quiet text actions on one line.
 *
 * They were two full-width cards in the pre-rebuild screen, which gave "Settings" the same visual
 * weight as the room itself.
 */
@Composable
private fun FooterActions(
    onLeave: (() -> Unit)?,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onLeave != null) {
            TextButton(onClick = onLeave) {
                Text(
                    text = stringResource(Res.string.lt_leave_room),
                    style = typo().labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        TextButton(onClick = onSettings) {
            Text(
                text = stringResource(Res.string.settings),
                style = typo().labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ───────────────────────────────── building blocks ─────────────────────────────────

/** One card. Every panel on this screen is one of these, so they cannot drift apart. */
@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), CardShape),
    ) { content() }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = typo().titleSmall, color = MaterialTheme.colorScheme.onBackground)
        Box(
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                .padding(horizontal = 8.dp, vertical = 1.dp),
        ) {
            Text("$count", style = typo().labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DividerLabel(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)))
        Text(text, style = typo().bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)))
    }
}
