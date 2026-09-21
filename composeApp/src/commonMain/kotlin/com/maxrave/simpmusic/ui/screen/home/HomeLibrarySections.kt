package com.maxrave.simpmusic.ui.screen.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maxrave.domain.data.entities.ArtistEntity
import com.maxrave.domain.data.entities.LocalPlaylistEntity
import com.maxrave.domain.extension.now
import com.maxrave.domain.repository.ArtistRepository
import com.maxrave.domain.utils.LocalResource
import com.maxrave.simpmusic.ui.component.rememberHolderPainter
import com.maxrave.simpmusic.ui.icon.ArrowForwardIos
import com.maxrave.simpmusic.ui.icon.LibraryMusic
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.navigation.destination.library.LibraryDestination
import com.maxrave.simpmusic.ui.navigation.destination.list.ArtistDestination
import com.maxrave.simpmusic.ui.navigation.destination.list.LocalPlaylistDestination
import com.maxrave.simpmusic.ui.navigation.destination.search.SearchDestination
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.LibraryViewModel
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.add_artist
import simpmusic.composeapp.generated.resources.add_playlist
import simpmusic.composeapp.generated.resources.cancel
import simpmusic.composeapp.generated.resources.create
import simpmusic.composeapp.generated.resources.good_afternoon
import simpmusic.composeapp.generated.resources.good_evening
import simpmusic.composeapp.generated.resources.good_morning
import simpmusic.composeapp.generated.resources.good_night
import simpmusic.composeapp.generated.resources.home_your_favorite_artists
import simpmusic.composeapp.generated.resources.home_your_playlists
import simpmusic.composeapp.generated.resources.playlist_name
import simpmusic.composeapp.generated.resources.song
import simpmusic.composeapp.generated.resources.songs

private val EarixPurple = Color(0xFFA855F7)
private val EarixMuted = Color(0xFFB0A5C0)
private val CardSize = 72.dp
private val CardShape = RoundedCornerShape(12.dp)
private val CardBg = Color(0x26A855F7)
private val CardBorder = Color(0x4DA855F7)
private val VinylIconSize = 32.dp

private fun Modifier.homeCard(onClick: () -> Unit): Modifier =
    Modifier
        .size(CardSize)
        .clip(CardShape)
        .background(CardBg)
        .border(1.dp, CardBorder, CardShape)
        .clickable(onClick = onClick)

@Composable
fun AppBrandingTitle() {
    Text(
        text = "\u018E\u039BRIX",
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
fun HomeGreeting() {
    var hour by remember { mutableStateOf(now().time.hour) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            hour = now().time.hour
        }
    }
    val greeting = when (hour) {
        in 5..11 -> stringResource(Res.string.good_morning)
        in 12..16 -> stringResource(Res.string.good_afternoon)
        in 17..21 -> stringResource(Res.string.good_evening)
        else -> stringResource(Res.string.good_night)
    }
    val parts = greeting.split(" ", limit = 2)
    val firstWord = parts.getOrElse(0) { greeting }
    val secondWord = parts.getOrElse(1) { "" }
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                append(firstWord)
            }
            if (secondWord.isNotEmpty()) {
                append(" ")
                withStyle(SpanStyle(color = EarixPurple, fontWeight = FontWeight.Bold)) {
                    append(secondWord)
                }
            }
        },
        fontSize = 28.sp,
        style = typo().titleLarge,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun SectionHeader(
    title: String,
    onChevronClick: () -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(EarixPurple),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            style = typo().titleMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = SimpIcons.ArrowForwardIos,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier =
                Modifier
                    .size(24.dp)
                    .clickable(onClick = onChevronClick),
        )
    }
}

@Composable
fun YourPlaylistsSection(
    navController: NavController,
    libraryViewModel: LibraryViewModel = koinViewModel(),
) {
    val playlistsResource by libraryViewModel.yourLocalPlaylist.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        libraryViewModel.getLocalPlaylist()
    }
    val playlists =
        (playlistsResource as? LocalResource.Success<List<LocalPlaylistEntity>>)?.data ?: emptyList()
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }

    SectionHeader(
        title = stringResource(Res.string.home_your_playlists),
        onChevronClick = { navController.navigate(LibraryDestination) },
    )
    Spacer(Modifier.height(12.dp))
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        item(key = "add_playlist") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier
                            .homeCard(onClick = { showCreateDialog = true }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SimpIcons.LibraryMusic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(VinylIconSize),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.add_playlist),
                    color = Color.White,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        items(playlists, key = { it.id }) { playlist ->
            PlaylistCard(
                playlist = playlist,
                onClick = { navController.navigate(LocalPlaylistDestination(playlist.id)) },
            )
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { title ->
                libraryViewModel.createPlaylist(title)
                showCreateDialog = false
            },
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: LocalPlaylistEntity,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .homeCard(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (playlist.thumbnail != null) {
                // Built once per thumbnail: the builder allocates on every call,
                // and this card recomposes on every playlist-list emission.
                val platformContext = LocalPlatformContext.current
                val artModel =
                    remember(playlist.thumbnail) {
                        ImageRequest
                            .Builder(platformContext)
                            .data(playlist.thumbnail)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .diskCacheKey(playlist.thumbnail)
                            .crossfade(true)
                            .build()
                    }
                AsyncImage(
                    model = artModel,
                    contentDescription = null,
                    placeholder = rememberHolderPainter(),
                    error = rememberHolderPainter(),
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(CardSize - 4.dp)
                            .clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Icon(
                    imageVector = SimpIcons.LibraryMusic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(VinylIconSize),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = playlist.title,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(CardSize),
        )
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.add_playlist)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(Res.string.playlist_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onConfirm(title.trim()) },
            ) {
                Text(stringResource(Res.string.create), color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
fun YourFavoriteArtistsSection(
    navController: NavController,
    artistRepository: ArtistRepository = koinInject(),
) {
    val followedArtists by artistRepository.getFollowedArtists().collectAsStateWithLifecycle(initialValue = emptyList())

    SectionHeader(
        title = stringResource(Res.string.home_your_favorite_artists),
        onChevronClick = { navController.navigate(LibraryDestination) },
    )
    Spacer(Modifier.height(12.dp))
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        item(key = "add_artist") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier
                            .homeCard(onClick = {
                                navController.navigate(SearchDestination)
                            }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SimpIcons.LibraryMusic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(VinylIconSize),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.add_artist),
                    color = Color.White,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        items(followedArtists, key = { it.channelId }) { artist ->
            ArtistCard(
                artist = artist,
                onClick = { navController.navigate(ArtistDestination(artist.channelId)) },
            )
        }
    }
}

@Composable
private fun ArtistCard(
    artist: ArtistEntity,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .homeCard(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (artist.thumbnails != null) {
                val platformContext = LocalPlatformContext.current
                val artModel =
                    remember(artist.thumbnails) {
                        ImageRequest
                            .Builder(platformContext)
                            .data(artist.thumbnails)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .diskCacheKey(artist.thumbnails)
                            .crossfade(true)
                            .build()
                    }
                AsyncImage(
                    model = artModel,
                    contentDescription = null,
                    placeholder = rememberHolderPainter(),
                    error = rememberHolderPainter(),
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(CardSize - 4.dp)
                            .clip(CircleShape),
                )
            } else {
                Icon(
                    imageVector = SimpIcons.LibraryMusic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(VinylIconSize),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = artist.name,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(CardSize),
        )
    }
}
