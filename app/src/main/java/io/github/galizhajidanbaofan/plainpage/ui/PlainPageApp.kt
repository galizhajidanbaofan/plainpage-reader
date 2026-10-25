package io.github.galizhajidanbaofan.plainpage.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.galizhajidanbaofan.plainpage.R
import io.github.galizhajidanbaofan.plainpage.ui.recent.RecentScreen
import io.github.galizhajidanbaofan.plainpage.ui.settings.SettingsScreen
import io.github.galizhajidanbaofan.plainpage.ui.shelf.ShelfScreen
import io.github.galizhajidanbaofan.plainpage.ui.theme.PlainPageTheme
import io.github.galizhajidanbaofan.plainpage.ui.theme.ThemeTokensCatalog

private enum class Destination(val route: String, @StringRes val labelRes: Int) {
    SHELF("shelf", R.string.nav_shelf),
    RECENT("recent", R.string.nav_recent),
    SETTINGS("settings", R.string.nav_settings),
}

@Composable
fun PlainPageApp() {
    // 阶段 1：外观状态暂存内存；阶段 2 换成 AppearanceRepository + DataStore。
    var appearance by rememberSaveable(stateSaver = AppearanceSaver) {
        mutableStateOf(AppearanceState())
    }
    val tokens = ThemeTokensCatalog.of(appearance.theme)
    val navController = rememberNavController()

    PlainPageTheme(tokens = tokens) {
        CompositionLocalProvider(LocalAppearance provides appearance) {
            Scaffold(
                bottomBar = { PlainPageBottomBar(navController) },
            ) { innerPadding ->
                PlainPageNavHost(
                    navController = navController,
                    appearance = appearance,
                    onAppearanceChange = { appearance = it },
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun PlainPageBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar {
        Destination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = {
                    if (currentRoute != destination.route) {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {},
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

@Composable
private fun PlainPageNavHost(
    navController: NavHostController,
    appearance: AppearanceState,
    onAppearanceChange: (AppearanceState) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Destination.SHELF.route,
        modifier = modifier,
    ) {
        composable(Destination.SHELF.route) {
            ShelfScreen(layout = appearance.shelfLayout)
        }
        composable(Destination.RECENT.route) {
            RecentScreen()
        }
        composable(Destination.SETTINGS.route) {
            SettingsScreen(
                appearance = appearance,
                onAppearanceChange = onAppearanceChange,
            )
        }
    }
}
