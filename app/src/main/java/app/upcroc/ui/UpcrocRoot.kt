package app.upcroc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.upcroc.BuildConfig
import app.upcroc.data.prefs.UiState
import app.upcroc.di.AppContainer
import app.upcroc.ui.components.AppDialog
import app.upcroc.ui.components.AppText
import app.upcroc.ui.components.DialogAction
import app.upcroc.ui.components.FloatingDock
import app.upcroc.ui.components.ToastHost
import app.upcroc.ui.components.rememberToastState
import app.upcroc.ui.onboarding.OnboardingScreen
import app.upcroc.ui.screens.DiagnosticsScreen
import app.upcroc.ui.screens.FeedScreen
import app.upcroc.ui.screens.JobDetailScreen
import app.upcroc.ui.screens.Layer0Screen
import app.upcroc.ui.screens.SearchScreen
import app.upcroc.ui.screens.SettingsScreen
import app.upcroc.ui.screens.SplashHold
import app.upcroc.ui.screens.SplashScreen
import app.upcroc.ui.theme.GoogleSansFlex
import app.upcroc.ui.theme.LocalFontFamily
import app.upcroc.ui.theme.LocalFontScaleFactor
import app.upcroc.ui.theme.LocalMainWeight
import app.upcroc.ui.theme.LocalSubWeight
import app.upcroc.ui.theme.Manrope
import app.upcroc.ui.theme.Roboto
import app.upcroc.ui.theme.UpType
import app.upcroc.ui.theme.Upcroc
import app.upcroc.update.UpdateInfo
import app.upcroc.update.UpdateState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.abs

object Routes {
    const val FEED = "feed"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
    const val LAYER0 = "layer0"
    const val JOB = "job/{id}"

    fun job(id: String) = "job/$id"
}

private val mainRoutes = listOf(Routes.FEED, Routes.SEARCH, Routes.SETTINGS)

@Composable
fun UpcrocRoot(container: AppContainer) {
    val vm: MainViewModel = viewModel(factory = MainViewModel.factory(container))
    val ready by vm.ready.collectAsStateWithLifecycle()
    val onboarding by vm.onboardingComplete.collectAsStateWithLifecycle()
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    var splashDone by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val baseFontScale = density.fontScale
    val appFontScale = uiState.fontScale
    val fontScale = (baseFontScale * appFontScale).coerceIn(0.8f, 1.6f)
    val fontFamily = when (uiState.fontChoice) {
        "manrope" -> Manrope
        "roboto" -> Roboto
        "system" -> FontFamily.Default
        else -> GoogleSansFlex
    }

    CompositionLocalProvider(
        LocalFontScaleFactor provides fontScale,
        LocalFontFamily provides fontFamily,
        LocalMainWeight provides FontWeight(uiState.mainWeight.coerceIn(100, 900)),
        LocalSubWeight provides FontWeight(uiState.subWeight.coerceIn(100, 900)),
        LocalDensity provides Density(density = density.density, fontScale = fontScale),
    ) {
        when {
            !splashDone -> SplashScreen(onFinished = { splashDone = true })
            !ready -> SplashHold()
            !onboarding -> OnboardingScreen(vm = vm, onFinished = {})
            else -> MainShell(vm = vm, uiState = uiState)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainShell(vm: MainViewModel, uiState: UiState) {
    val context = LocalContext.current
    val toast = rememberToastState()
    val nav = rememberNavController()
    val start = remember {
        uiState.lastRoute.takeIf { it in mainRoutes } ?: Routes.FEED
    }
    val update by vm.updateState.collectAsStateWithLifecycle()
    val haze = rememberHazeState()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val imeVisible = WindowInsets.isImeVisible
    val showDock = route != null && route in mainRoutes && !imeVisible
    val tabIndex = route?.let { mainRoutes.indexOf(it) }?.coerceAtLeast(0) ?: 0

    LaunchedEffect(Unit) {
        vm.messages.collect { toast.show(it) }
    }
    LaunchedEffect(Unit) {
        vm.ensureWatcherRunning(context)
    }
    LaunchedEffect(Unit) {
        if (BuildConfig.SELF_UPDATE) vm.checkForUpdates(force = false)
    }
    LaunchedEffect(route) {
        if (route != null && route in mainRoutes) {
            vm.updateUiState { state ->
                state.copy(
                    lastRoute = route,
                    selectedTab = mainRoutes.indexOf(route).coerceAtLeast(0),
                )
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Upcroc.Ink)) {
        Box(
            Modifier
                .fillMaxSize()
                .hazeSource(haze)
                .background(Upcroc.Ink)
                .then(
                    if (route != null && route in mainRoutes) {
                        Modifier.pointerInput(route) {
                            val threshold = 64.dp.toPx()
                            var drag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { drag = 0f },
                                onHorizontalDrag = { _, amount -> drag += amount },
                                onDragEnd = {
                                    if (abs(drag) > threshold) {
                                        val next = (tabIndex + if (drag < 0f) 1 else -1)
                                            .coerceIn(0, mainRoutes.lastIndex)
                                        if (next != tabIndex) navigateTab(nav, mainRoutes[next])
                                    }
                                },
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
        NavHost(
            navController = nav,
            startDestination = start,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.FEED) {
                FeedScreen(
                    vm = vm,
                    onOpenJob = { id -> nav.navigate(Routes.job(id)) },
                    onSearch = { navigateTab(nav, Routes.SEARCH) },
                    onDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
                    onToast = { toast.show(it) },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    vm = vm,
                    onLayer0 = { nav.navigate(Routes.LAYER0) },
                    onDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
                    onToast = { toast.show(it) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    vm = vm,
                    onDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
                    onLayer0 = { nav.navigate(Routes.LAYER0) },
                )
            }
            composable(Routes.DIAGNOSTICS) {
                DiagnosticsScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.LAYER0) {
                Layer0Screen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.JOB) { entry ->
                val id = entry.arguments?.getString("id").orEmpty()
                JobDetailScreen(vm = vm, jobId = id, onBack = { nav.popBackStack() })
            }
        }
        }

        if (showDock) {
            FloatingDock(
                current = tabIndex,
                onSelect = { navigateTab(nav, mainRoutes[it]) },
                hazeState = haze,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
        }

        ToastHost(
            state = toast,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showDock) 118.dp else 24.dp),
        )
    }

    UpdateDialog(
        state = update,
        onInstall = { vm.downloadUpdate(it) },
        onDismiss = { vm.dismissUpdate() },
    )
}

private fun navigateTab(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun UpdateDialog(
    state: UpdateState,
    onInstall: (UpdateInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        is UpdateState.Available -> AppDialog(
            onDismissRequest = onDismiss,
            title = "Update available",
            body = {
                AppText(
                    buildString {
                        append("Upcroc ${state.info.versionName} is ready.")
                        if (state.info.changelog.isNotBlank()) {
                            append("\n\n")
                            append(state.info.changelog)
                        }
                    },
                    style = UpType.muted,
                    color = Upcroc.Muted,
                )
            },
            buttons = {
                DialogAction("Later", onDismiss, Modifier.weight(1f))
                DialogAction("Install", { onInstall(state.info) }, Modifier.weight(1f), filled = true)
            },
        )

        is UpdateState.Downloading -> AppDialog(
            onDismissRequest = {},
            title = "Downloading update",
            dismissible = false,
            body = {
                AppText(
                    "Keep Upcroc open. Android will ask you to confirm the install when the download finishes.",
                    style = UpType.muted,
                    color = Upcroc.Muted,
                )
            },
            buttons = {},
        )

        is UpdateState.Error -> AppDialog(
            onDismissRequest = onDismiss,
            title = "Update failed",
            body = {
                AppText(state.message, style = UpType.muted, color = Upcroc.Muted)
            },
            buttons = {
                DialogAction("Close", onDismiss, Modifier.weight(1f), filled = true)
            },
        )

        is UpdateState.Ready -> AppDialog(
            onDismissRequest = onDismiss,
            title = "Update ready",
            body = {
                AppText(state.message, style = UpType.muted, color = Upcroc.Muted)
            },
            buttons = {
                DialogAction("Close", onDismiss, Modifier.weight(1f), filled = true)
            },
        )

        UpdateState.Checking, UpdateState.Idle, UpdateState.UpToDate -> Unit
    }
}
