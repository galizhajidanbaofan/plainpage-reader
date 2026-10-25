package io.github.galizhajidanbaofan.plainpage.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.galizhajidanbaofan.plainpage.PlainPageApplication
import io.github.galizhajidanbaofan.plainpage.R
import io.github.galizhajidanbaofan.plainpage.data.importing.ImportRejection
import io.github.galizhajidanbaofan.plainpage.di.AppContainer
import io.github.galizhajidanbaofan.plainpage.domain.model.Book
import io.github.galizhajidanbaofan.plainpage.ui.library.LibraryMessage
import io.github.galizhajidanbaofan.plainpage.ui.library.LibraryViewModel
import io.github.galizhajidanbaofan.plainpage.ui.reader.ReaderScreen
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

private const val ROUTE_READER = "reader"
private const val ARG_BOOK_ID = "bookId"
private const val ROUTE_READER_PATTERN = "$ROUTE_READER/{$ARG_BOOK_ID}"

private fun readerRoute(bookId: Long) = "$ROUTE_READER/$bookId"

@Composable
fun PlainPageApp() {
    // 阶段 1：外观状态暂存内存；阶段 2 换成 AppearanceRepository + DataStore。
    var appearance by rememberSaveable(stateSaver = AppearanceSaver) {
        mutableStateOf(AppearanceState())
    }
    val tokens = ThemeTokensCatalog.of(appearance.theme)
    val navController = rememberNavController()

    val context = LocalContext.current
    val container = remember(context) {
        (context.applicationContext as PlainPageApplication).container
    }
    val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
    val shelf by libraryViewModel.shelf.collectAsStateWithLifecycle()
    val recent by libraryViewModel.recent.collectAsStateWithLifecycle()
    val importing by libraryViewModel.importing.collectAsStateWithLifecycle()
    val message by libraryViewModel.message.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = message?.let { libraryMessageText(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            libraryViewModel.consumeMessage()
        }
    }

    // 「最近阅读」的写入只由阅读页负责，避免同一次打开写两遍。
    val openBook: (Book) -> Unit = { book -> navController.navigate(readerRoute(book.id)) }

    PlainPageTheme(tokens = tokens) {
        CompositionLocalProvider(LocalAppearance provides appearance) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val route = backStackEntry?.destination?.route
                    // 阅读页全屏，不显示底部导航
                    if (route?.startsWith(ROUTE_READER) != true) {
                        PlainPageBottomBar(navController = navController, currentRoute = route)
                    }
                },
            ) { innerPadding ->
                PlainPageNavHost(
                    navController = navController,
                    container = container,
                    appearance = appearance,
                    onAppearanceChange = { appearance = it },
                    libraryViewModel = libraryViewModel,
                    shelf = shelf,
                    recent = recent,
                    importing = importing,
                    onOpenBook = openBook,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
    }
}

/** 把导入结果映射成一句人话；资源文案在这里取，ViewModel 不碰 Android 资源。 */
@Composable
private fun libraryMessageText(message: LibraryMessage): String = when (message) {
    is LibraryMessage.Imported -> stringResource(R.string.import_done, message.count)
    is LibraryMessage.ImportFailed ->
        stringResource(R.string.import_failed, importRejectionText(message.reason))
}

@Composable
private fun importRejectionText(reason: ImportRejection): String = when (reason) {
    ImportRejection.SOURCE_UNAVAILABLE -> stringResource(R.string.import_reason_unavailable)
    ImportRejection.UNSUPPORTED_FORMAT -> stringResource(R.string.import_reason_unsupported)
    ImportRejection.TOO_LARGE -> stringResource(R.string.import_reason_too_large)
    ImportRejection.EMPTY_FILE -> stringResource(R.string.import_reason_empty)
    ImportRejection.IO_ERROR -> stringResource(R.string.import_reason_io)
}

@Composable
private fun PlainPageBottomBar(navController: NavHostController, currentRoute: String?) {
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
    container: AppContainer,
    appearance: AppearanceState,
    onAppearanceChange: (AppearanceState) -> Unit,
    libraryViewModel: LibraryViewModel,
    shelf: List<Book>,
    recent: List<Book>,
    importing: Boolean,
    onOpenBook: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Destination.SHELF.route,
        modifier = modifier,
    ) {
        composable(Destination.SHELF.route) {
            ShelfScreen(
                layout = appearance.shelfLayout,
                books = shelf,
                importing = importing,
                onImport = libraryViewModel::import,
                onOpen = onOpenBook,
                onDelete = libraryViewModel::delete,
            )
        }
        composable(Destination.RECENT.route) {
            RecentScreen(
                books = recent,
                onOpen = onOpenBook,
                onDelete = libraryViewModel::delete,
            )
        }
        composable(Destination.SETTINGS.route) {
            SettingsScreen(
                appearance = appearance,
                onAppearanceChange = onAppearanceChange,
            )
        }
        composable(
            route = ROUTE_READER_PATTERN,
            arguments = listOf(navArgument(ARG_BOOK_ID) { type = NavType.LongType }),
        ) { entry ->
            val bookId = entry.arguments?.getLong(ARG_BOOK_ID) ?: return@composable
            ReaderScreen(
                bookId = bookId,
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
