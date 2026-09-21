package com.maxrave.simpmusic.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import com.maxrave.simpmusic.ui.icon.History
import com.maxrave.simpmusic.ui.icon.Home
import com.maxrave.simpmusic.ui.icon.LibraryMusic
import com.maxrave.simpmusic.ui.icon.Notifications
import com.maxrave.simpmusic.ui.icon.Search
import com.maxrave.simpmusic.ui.icon.Settings
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.extension.greyScale
import com.maxrave.simpmusic.ui.navigation.destination.home.HomeDestination
import com.maxrave.simpmusic.ui.navigation.destination.home.NotificationDestination
import com.maxrave.simpmusic.ui.navigation.destination.home.RecentlySongsDestination
import com.maxrave.simpmusic.ui.navigation.destination.home.SettingsDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.LibraryDestination
import com.maxrave.simpmusic.ui.navigation.destination.search.SearchDestination
import com.maxrave.simpmusic.ui.theme.typo
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.earix_background
import simpmusic.composeapp.generated.resources.home
import simpmusic.composeapp.generated.resources.library
import kotlin.reflect.KClass

private val EarixPurple = Color(0xFFA855F7)
private val EarixMuted = Color(0xFFB0A5C0)
private val PillShape = RoundedCornerShape(24.dp)
private val CircleBtnSize = 44.dp
private val CircleBg = Color(0x1FA855F7)
private val CircleBorder = Color(0x33A855F7)

@Composable
fun AppBottomNavigationBar(
    startDestination: Any = HomeDestination,
    navController: NavController,
    isTranslucentBackground: Boolean = false,
    showAnalyticsTab: Boolean = false,
    showMixForYouTab: Boolean = false,
    reloadDestinationIfNeeded: (KClass<*>) -> Unit = { _ -> },
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    var selectedIndex by rememberSaveable {
        mutableIntStateOf(
            when (startDestination) {
                is HomeDestination -> 0
                is SearchDestination -> 1
                is LibraryDestination -> 2
                else -> 0
            },
        )
    }

    LaunchedEffect(showAnalyticsTab, showMixForYouTab) {
        if ((!showAnalyticsTab && selectedIndex == 3) ||
            (!showMixForYouTab && selectedIndex == 4)
        ) {
            selectedIndex = 0
        }
    }

    fun navigateTo(destination: Any) {
        navController.navigate(destination) {
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun checkAndReload(destination: Any, clazz: KClass<*>) {
        if (currentBackStackEntry?.destination?.hierarchy?.any {
                it.hasRoute(clazz)
            } == true
        ) {
            reloadDestinationIfNeeded(clazz)
        } else {
            navigateTo(destination)
        }
    }

    val isHomeSelected = selectedIndex == 0
    val isLibrarySelected = selectedIndex == 2

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        // Left pill: Home + Library
        Row(
            modifier =
                Modifier
                    .clip(PillShape)
                    .background(CircleBg)
                    .border(1.dp, CircleBorder, PillShape)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(CircleBtnSize)
                        .clip(
                            if (isHomeSelected) CircleShape else RoundedCornerShape(20.dp),
                        )
                        .background(
                            if (isHomeSelected) EarixPurple else Color.Transparent,
                        )
                        .clickable {
                            selectedIndex = 0
                            checkAndReload(HomeDestination, HomeDestination::class)
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SimpIcons.Home,
                    contentDescription = stringResource(Res.string.home),
                    tint = if (isHomeSelected) Color.White else EarixMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
            Box(
                modifier =
                    Modifier
                        .size(CircleBtnSize)
                        .clip(
                            if (isLibrarySelected) CircleShape else RoundedCornerShape(20.dp),
                        )
                        .background(
                            if (isLibrarySelected) EarixPurple else Color.Transparent,
                        )
                        .clickable {
                            selectedIndex = 2
                            checkAndReload(LibraryDestination, LibraryDestination::class)
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SimpIcons.LibraryMusic,
                    contentDescription = stringResource(Res.string.library),
                    tint = if (isLibrarySelected) Color.White else EarixMuted,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // Center circles: Search, History, Notification
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleNavButton(
                icon = SimpIcons.Search,
                isSelected = selectedIndex == 1,
                onClick = {
                    selectedIndex = 1
                    navigateTo(SearchDestination)
                },
            )
            CircleNavButton(
                icon = SimpIcons.History,
                isSelected = false,
                onClick = { navigateTo(RecentlySongsDestination) },
            )
            CircleNavButton(
                icon = SimpIcons.Notifications,
                isSelected = false,
                onClick = { navigateTo(NotificationDestination) },
            )
        }

        // Right: Settings FAB
        Box(
            modifier =
                Modifier
                    .size(CircleBtnSize)
                    .clip(CircleShape)
                    .background(CircleBg)
                    .border(1.dp, CircleBorder, CircleShape)
                    .clickable { navigateTo(SettingsDestination) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SimpIcons.Settings,
                contentDescription = null,
                tint = EarixMuted,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun CircleNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(CircleBtnSize)
                .clip(CircleShape)
                .background(if (isSelected) EarixPurple else CircleBg)
                .then(
                    if (!isSelected) Modifier.border(1.dp, CircleBorder, CircleShape) else Modifier,
                )
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) Color.White else EarixMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun AppNavigationRail(
    startDestination: Any = HomeDestination,
    navController: NavController,
    showAnalyticsTab: Boolean = false,
    showMixForYouTab: Boolean = false,
    reloadDestinationIfNeeded: (KClass<*>) -> Unit = { _ -> },
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val bottomNavScreens =
        listOfNotNull(
            BottomNavScreen.Home,
            BottomNavScreen.MixForYou.takeIf { showMixForYouTab },
            BottomNavScreen.Analytics.takeIf { showAnalyticsTab },
            BottomNavScreen.Library,
            BottomNavScreen.Search,
        )
    var selectedIndex by rememberSaveable {
        mutableIntStateOf(
            when (startDestination) {
                is HomeDestination -> BottomNavScreen.Home.ordinal
                is SearchDestination -> BottomNavScreen.Search.ordinal
                is LibraryDestination -> BottomNavScreen.Library.ordinal
                is com.maxrave.simpmusic.ui.navigation.destination.home.AnalyticsDestination -> BottomNavScreen.Analytics.ordinal
                is com.maxrave.simpmusic.ui.navigation.destination.library.MixForYouDestination -> BottomNavScreen.MixForYou.ordinal
                else -> BottomNavScreen.Home.ordinal
            },
        )
    }
    LaunchedEffect(showAnalyticsTab, showMixForYouTab) {
        if ((!showAnalyticsTab && selectedIndex == BottomNavScreen.Analytics.ordinal) ||
            (!showMixForYouTab && selectedIndex == BottomNavScreen.MixForYou.ordinal)
        ) {
            selectedIndex = BottomNavScreen.Home.ordinal
        }
    }
    androidx.compose.material3.NavigationRail {
        Spacer(Modifier.height(16.dp))
        Box(Modifier.padding(horizontal = 16.dp)) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.earix_background),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .height(32.dp)
                            .clip(CircleShape),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        bottomNavScreens.forEach { screen ->
            androidx.compose.material3.NavigationRailItem(
                icon = screen.icon,
                label = {
                    androidx.compose.material3.Text(
                        stringResource(screen.title),
                        style =
                            if (selectedIndex == screen.ordinal) {
                                typo().bodySmall
                            } else {
                                typo().bodySmall.greyScale()
                            },
                    )
                },
                selected = selectedIndex == screen.ordinal,
                onClick = {
                    if (selectedIndex == screen.ordinal) {
                        if (currentBackStackEntry?.destination?.hierarchy?.any {
                                it.hasRoute(screen.destination::class)
                            } == true
                        ) {
                            reloadDestinationIfNeeded(
                                screen.destination::class,
                            )
                        } else {
                            navController.navigate(screen.destination)
                        }
                    } else {
                        selectedIndex = screen.ordinal
                        navController.navigate(screen.destination) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}
