package com.maxrave.simpmusic.ui.screen.listentogether

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maxrave.simpmusic.expect.ui.PlatformBackdrop
import com.maxrave.simpmusic.expect.ui.layerBackdrop
import com.maxrave.simpmusic.expect.ui.rememberBackdrop
import com.maxrave.simpmusic.ui.component.AmbientThemeGlow
import com.maxrave.simpmusic.ui.component.LiquidGlassIconButton
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.Check
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.ListenTogetherSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.listen_together
import simpmusic.composeapp.generated.resources.lt_as_host
import simpmusic.composeapp.generated.resources.lt_auto_approve_joins
import simpmusic.composeapp.generated.resources.lt_auto_approve_joins_desc
import simpmusic.composeapp.generated.resources.lt_auto_approve_suggestions
import simpmusic.composeapp.generated.resources.lt_auto_approve_suggestions_desc
import simpmusic.composeapp.generated.resources.lt_custom_server
import simpmusic.composeapp.generated.resources.lt_custom_server_desc
import simpmusic.composeapp.generated.resources.lt_default_server_location
import simpmusic.composeapp.generated.resources.lt_default_server_name
import simpmusic.composeapp.generated.resources.lt_save_server
import simpmusic.composeapp.generated.resources.lt_server
import simpmusic.composeapp.generated.resources.lt_settings_subtitle

/** Content is capped on a wide window; the header and the ground stay full width. */
private const val CONTENT_MAX_WIDTH_DP = 640

private val FieldShape = RoundedCornerShape(14.dp)

/**
 * Listen Together settings.
 *
 * Stage 1 wires the settings that are already consumed end to end: the server address (read on
 * every connection attempt by the DI module that builds the client) and the two host-side
 * auto-approve switches (applied by the session where requests arrive). The blocklist and the
 * moderation dialog belong to the room screen's stage, and the stored "follow the host's volume"
 * flag is deliberately not shown — nothing reads it yet, so offering the switch would be a lie.
 *
 * The default server is described rather than named-on-a-map: a custom address replaces it, and a
 * hard-coded host/location line goes stale the moment someone points the app elsewhere.
 */
@Composable
fun ListenTogetherSettingsScreen(
    navController: NavController,
    innerPadding: PaddingValues,
    viewModel: ListenTogetherSettingsViewModel = koinViewModel(),
) {
    val usingCustom by viewModel.usingCustomServer.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val autoJoins by viewModel.autoApproveJoins.collectAsStateWithLifecycle()
    val autoSuggestions by viewModel.autoApproveSuggestions.collectAsStateWithLifecycle()

    // Selection is local until it is committed. Deriving it from the stored URL alone meant
    // "choose custom" had to persist a half-typed address to show itself as selected — which then
    // became the address the next connection attempt used.
    var selectedCustom by remember(usingCustom) { mutableStateOf(usingCustom) }
    var draftUrl by remember(serverUrl) { mutableStateOf(serverUrl) }

    val backdrop = rememberBackdrop(MaterialTheme.colorScheme.background)

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        Box(Modifier.matchParentSize().layerBackdrop(backdrop)) {
            Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.background))
            AmbientThemeGlow(tint = MaterialTheme.colorScheme.primary)
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            Column(
                modifier =
                    Modifier
                        .widthIn(max = CONTENT_MAX_WIDTH_DP.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(Res.string.listen_together),
                        style = typo().titleLarge.copy(fontSize = 28.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(Res.string.lt_settings_subtitle),
                        style = typo().bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle(stringResource(Res.string.lt_server))

                    ServerOption(
                        title = stringResource(Res.string.lt_default_server_name),
                        subtitle = stringResource(Res.string.lt_default_server_location),
                        selected = !selectedCustom,
                        onClick = {
                            selectedCustom = false
                            // Clearing the override is safe and immediate: the client falls back to
                            // its default URL on the next connection attempt.
                            viewModel.useDefaultServer()
                        },
                    )
                    ServerOption(
                        title = stringResource(Res.string.lt_custom_server),
                        subtitle = stringResource(Res.string.lt_custom_server_desc),
                        selected = selectedCustom,
                        onClick = { selectedCustom = true },
                    )

                    if (selectedCustom) {
                        OutlinedTextField(
                            value = draftUrl,
                            onValueChange = { draftUrl = it },
                            singleLine = true,
                            placeholder = { Text("wss://…", style = typo().bodySmall) },
                            shape = FieldShape,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Committing on focus loss would silently store a half-typed address, so the
                        // value is only written when the user asks for it.
                        if (draftUrl.trim() != serverUrl && draftUrl.isNotBlank()) {
                            OutlinedButton(
                                onClick = { viewModel.setServerUrl(draftUrl) },
                                shape = CircleShape,
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text(stringResource(Res.string.lt_save_server), style = typo().labelMedium)
                            }
                        }
                    }
                }

                HLine()

                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    SectionTitle(stringResource(Res.string.lt_as_host))
                    ToggleRow(
                        title = stringResource(Res.string.lt_auto_approve_joins),
                        subtitle = stringResource(Res.string.lt_auto_approve_joins_desc),
                        checked = autoJoins,
                        // Lambdas rather than method references: the setters return the Job they
                        // launch, and a function reference would have to be adapted to Unit.
                        onCheckedChange = { viewModel.setAutoApproveJoins(it) },
                    )
                    ToggleRow(
                        title = stringResource(Res.string.lt_auto_approve_suggestions),
                        subtitle = stringResource(Res.string.lt_auto_approve_suggestions_desc),
                        checked = autoSuggestions,
                        onCheckedChange = { viewModel.setAutoApproveSuggestions(it) },
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }

        BackButton(backdrop) { navController.navigateUp() }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.BackButton(
    backdrop: PlatformBackdrop,
    onBack: () -> Unit,
) {
    LiquidGlassIconButton(
        backdrop = backdrop,
        imageVector = SimpIcons.ArrowBackIosNew,
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
private fun SectionTitle(text: String) {
    Text(text, style = typo().titleSmall, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun ServerOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (selected) {
                        Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f))
                    } else {
                        Modifier
                    },
                ).border(
                    1.dp,
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)
                    },
                    RoundedCornerShape(16.dp),
                ).clickable { onClick() }
                .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .then(
                        if (selected) {
                            Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                        } else {
                            Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.26f),
                                CircleShape,
                            )
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = SimpIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = typo().bodyMedium,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Spacer(Modifier.height(3.dp))
            Text(subtitle, style = typo().bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = typo().bodyMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, style = typo().bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun HLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)))
}
