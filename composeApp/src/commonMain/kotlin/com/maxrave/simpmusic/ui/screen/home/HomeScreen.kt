package com.maxrave.simpmusic.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.maxrave.logger.Logger
import com.maxrave.simpmusic.extension.isScrollingUp
import com.maxrave.simpmusic.ui.component.EndOfPage
import com.maxrave.simpmusic.ui.component.ShareSavedLyricsDialog
import com.maxrave.simpmusic.ui.navigation.destination.home.HomeDestination
import com.maxrave.simpmusic.viewModel.HomeViewModel
import com.maxrave.simpmusic.viewModel.SharedViewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.rememberHazeState
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScrolling: (onTop: Boolean) -> Unit = {},
    viewModel: HomeViewModel =
        koinViewModel(),
    sharedViewModel: SharedViewModel =
        koinInject(),
    navController: NavController,
) {
    val scrollState = rememberLazyListState()
    val isScrollingUp by scrollState.isScrollingUp()
    val reloadDestination by sharedViewModel.reloadDestination.collectAsStateWithLifecycle()

    val openAppTime by sharedViewModel.openAppTime.collectAsStateWithLifecycle()
    val shareLyricsPermissions by sharedViewModel.shareSavedLyrics.collectAsStateWithLifecycle()

    var showRequestShareLyricsPermissions by rememberSaveable {
        mutableStateOf(false)
    }

    val hazeState =
        rememberHazeState(
            blurEnabled = true,
        )

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.firstVisibleItemIndex }
            .collect {
                if (it <= 1) {
                    onScrolling.invoke(true)
                } else {
                    onScrolling.invoke(isScrollingUp)
                }
            }
    }

    LaunchedEffect(key1 = reloadDestination) {
        if (reloadDestination == HomeDestination::class) {
            if (scrollState.firstVisibleItemIndex > 1) {
                Logger.w("HomeScreen", "scrollState.firstVisibleItemIndex: ${scrollState.firstVisibleItemIndex}")
                scrollState.animateScrollToItem(0)
            }
            sharedViewModel.reloadDestinationDone()
        }
    }
    LaunchedEffect(openAppTime, shareLyricsPermissions) {
        Logger.w("HomeScreen", "openAppTime: $openAppTime, shareLyricsPermissions: $shareLyricsPermissions")
        if ((openAppTime == 1 || openAppTime % 15 == 0) && openAppTime <= 60 && !shareLyricsPermissions) {
            showRequestShareLyricsPermissions = true
        } else {
            showRequestShareLyricsPermissions = false
        }
    }

    if (showRequestShareLyricsPermissions) {
        ShareSavedLyricsDialog(
            onDismissRequest = {
                showRequestShareLyricsPermissions = false
            },
            onConfirm = { contributor ->
                sharedViewModel.onDoneRequestingShareLyrics(
                    contributor,
                )
            },
        )
    }

    Box {
        Box(
            modifier =
                Modifier
                    .hazeSource(hazeState),
        ) {
            LazyColumn(
                state = scrollState,
                verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                item {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(top = 16.dp),
                    )
                }
                item {
                    AppBrandingTitle()
                }
                item {
                    HomeGreeting()
                }
                item {
                    YourPlaylistsSection(navController = navController)
                }
                item {
                    YourFavoriteArtistsSection(navController = navController)
                }
                item {
                    EndOfPage()
                }
            }
        }
    }
}
