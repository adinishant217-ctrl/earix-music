package com.maxrave.simpmusic.ui.navigation.graph

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.maxrave.simpmusic.ui.navigation.destination.library.LibraryDynamicPlaylistDestination
import com.maxrave.simpmusic.ui.navigation.destination.listentogether.ListenTogetherDestination
import com.maxrave.simpmusic.ui.navigation.destination.listentogether.ListenTogetherSettingsDestination
import com.maxrave.simpmusic.ui.screen.library.LibraryDynamicPlaylistScreen
import com.maxrave.simpmusic.ui.screen.listentogether.ListenTogetherScreen
import com.maxrave.simpmusic.ui.screen.listentogether.ListenTogetherSettingsScreen

@ExperimentalMaterial3Api
fun NavGraphBuilder.libraryScreenGraph(
    innerPadding: PaddingValues,
    navController: NavController,
) {
    composable<LibraryDynamicPlaylistDestination> { entry ->
        val data = entry.toRoute<LibraryDynamicPlaylistDestination>()
        LibraryDynamicPlaylistScreen(
            innerPadding = innerPadding,
            navController = navController,
            type = data.type,
        )
    }
    // Reached from the Listen Together button in Library's top bar. Both screens keep the bottom
    // navigation bar visible, like Settings does, and pad themselves with `innerPadding`.
    composable<ListenTogetherDestination> {
        ListenTogetherScreen(
            innerPadding = innerPadding,
            navController = navController,
        )
    }
    composable<ListenTogetherSettingsDestination> {
        ListenTogetherSettingsScreen(
            innerPadding = innerPadding,
            navController = navController,
        )
    }
}