package com.example

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.model.DrxUpdatePipelineState
import com.example.data.model.LanguageMode
import com.example.data.model.UpdateOfferPolicyState
import com.example.i18n.LocalStrings
import com.example.i18n.stringsFor
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AnnouncementCenterScreen
import com.example.ui.screens.BoosterScreen
import com.example.ui.screens.CrashAnalyzerScreen
import com.example.ui.screens.CustomizationScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DeviceMonitorScreen
import com.example.ui.screens.DiagnosticHistoryScreen
import com.example.ui.screens.DisplayTouchScreen
import com.example.ui.screens.DownloadCenterScreen
import com.example.ui.screens.DrxCinematicIntroOverlay
import com.example.ui.screens.FloatingSidebarScreen
import com.example.ui.screens.GameActivityCenterScreen
import com.example.ui.screens.GameDetailStatsScreen
import com.example.ui.screens.GameEventTrackerScreen
import com.example.ui.screens.GameLaunchOverlay
import com.example.ui.screens.GameNotificationViewerScreen
import com.example.ui.screens.GamesScreen
import com.example.ui.screens.GamingHealthScreen
import com.example.ui.screens.LagDetectiveScreen
import com.example.ui.screens.MediaGalleryScreen
import com.example.ui.screens.NetworkToolsScreen
import com.example.ui.screens.NotificationInsightsScreen
import com.example.ui.screens.ProfileHubScreen
import com.example.ui.screens.ProfilesScreen
import com.example.ui.screens.ScreenRecorderScreen
import com.example.ui.screens.SessionIntelligenceScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SmartUpdateOfferModal
import com.example.ui.screens.SoundCenterScreen
import com.example.ui.screens.StabilityWatchScreen
import com.example.ui.screens.StatisticsScreen
import com.example.ui.screens.ToolRoutes
import com.example.ui.screens.ToolsHubScreen
import com.example.ui.screens.UpdateCenterScreen
import com.example.ui.screens.UpdateLogScreen
import com.example.ui.screens.UtilitiesScreen
import com.example.ui.screens.VoiceStudioScreen
import com.example.ui.theme.DrxCustomNavigationBar
import com.example.ui.theme.DrxGameTurboTheme
import com.example.ui.theme.DrxLogoBadge
import com.example.ui.theme.DrxNavItemUi
import com.example.ui.theme.LocalDrxColors
import com.example.system.DrxImageUtils
import com.example.system.UpdateCheckWorker
import com.example.ui.viewmodel.DrxDiagnosticsViewModel
import com.example.ui.viewmodel.DrxMainViewModel
import com.example.ui.viewmodel.DrxNotificationsViewModel
import com.example.ui.viewmodel.DrxToolsViewModel
import com.example.ui.viewmodel.DrxUpdateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object MainRoutes {
  const val HOME = "home"
  const val GAMES = "games"
  const val TOOLS = "tools"
  const val PROFILE = "profile"
  const val SETTINGS = "settings"
  const val ABOUT = "about"
  const val UPDATE_LOG = "update_log"
  const val ANNOUNCEMENT_CENTER = "announcement_center"
  const val CUSTOMIZATION = "customization"
  const val GAME_DETAIL_STATS = "game_detail_stats/{packageName}"

  fun gameDetailStatsRoute(packageName: String): String =
    "game_detail_stats/${Uri.encode(packageName)}"
}

private data class NavTab(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
)

class MainActivity : ComponentActivity() {
  private var mainViewModelRef: DrxMainViewModel? = null
  private var notificationsViewModelRef: DrxNotificationsViewModel? = null
  private var updateViewModelRef: DrxUpdateViewModel? = null
  private val pendingDeepRoute = mutableStateOf<String?>(null)
  private var wasOutsideAppWithFloating = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    pendingDeepRoute.value = intent?.getStringExtra("open_route")
    enableEdgeToEdge()
    setContent {
      val mainViewModel: DrxMainViewModel = viewModel()
      val toolsViewModel: DrxToolsViewModel = viewModel()
      val diagnosticsViewModel: DrxDiagnosticsViewModel = viewModel()
      val notificationsViewModel: DrxNotificationsViewModel = viewModel()
      val updateViewModel: DrxUpdateViewModel = viewModel()
      mainViewModelRef = mainViewModel
      notificationsViewModelRef = notificationsViewModel
      updateViewModelRef = updateViewModel

      val prefs by mainViewModel.userPrefs.collectAsStateWithLifecycle()
      val strings = remember(prefs.language) { stringsFor(prefs.language) }
      val routeToOpen by pendingDeepRoute

      CompositionLocalProvider(LocalStrings provides strings) {
        DrxGameTurboTheme(
          darkTheme = prefs.darkMode,
          themePreset = prefs.themePreset,
          customAccentArgb = prefs.customAccentArgb,
        ) {
          DrxRootShell(
            mainViewModel = mainViewModel,
            toolsViewModel = toolsViewModel,
            diagnosticsViewModel = diagnosticsViewModel,
            notificationsViewModel = notificationsViewModel,
            updateViewModel = updateViewModel,
            pendingDeepRoute = routeToOpen,
            onConsumeDeepRoute = { pendingDeepRoute.value = null },
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    intent.getStringExtra("open_route")?.let { pendingDeepRoute.value = it }
  }

  override fun onStop() {
    super.onStop()
    if (!isChangingConfigurations && mainViewModelRef?.shouldTrackFloatingReturnToApp() == true) {
      wasOutsideAppWithFloating = true
    }
  }

  override fun onResume() {
    super.onResume()
    if (wasOutsideAppWithFloating) {
      wasOutsideAppWithFloating = false
      mainViewModelRef?.autoHideFloatingOnReturnToApp()
    }
    mainViewModelRef?.refreshSystemStates()
    mainViewModelRef?.syncAnnouncementSchedule(false)
    notificationsViewModelRef?.refreshNotificationAccess()
    updateViewModelRef?.refreshInstallPermissionState()
    updateViewModelRef?.checkForUpdatesAuto(forceRefresh = false)
  }
}

@Composable
fun DrxRootShell(
  mainViewModel: DrxMainViewModel,
  toolsViewModel: DrxToolsViewModel,
  diagnosticsViewModel: DrxDiagnosticsViewModel,
  notificationsViewModel: DrxNotificationsViewModel,
  updateViewModel: DrxUpdateViewModel,
  pendingDeepRoute: String? = null,
  onConsumeDeepRoute: () -> Unit = {},
) {
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val navController = rememberNavController()
  val activity = LocalActivity.current

  val prefs by mainViewModel.userPrefs.collectAsStateWithLifecycle()
  val telemetry by mainViewModel.telemetry.collectAsStateWithLifecycle()
  val telemetryHistory by mainViewModel.telemetryHistory.collectAsStateWithLifecycle()
  val games by mainViewModel.games.collectAsStateWithLifecycle()
  val installedApps by mainViewModel.installedApps.collectAsStateWithLifecycle()
  val isScanningGames by mainViewModel.isScanningGames.collectAsStateWithLifecycle()
  val allSessions by mainViewModel.allSessions.collectAsStateWithLifecycle()
  val appUsage by mainViewModel.appUsageStats.collectAsStateWithLifecycle()
  val isBoosting by mainViewModel.isBoosting.collectAsStateWithLifecycle()
  val boostReport by mainViewModel.lastBoostReport.collectAsStateWithLifecycle()
  val activeSession by mainViewModel.activeSession.collectAsStateWithLifecycle()
  val permissionStates by mainViewModel.permissionStates.collectAsStateWithLifecycle()
  val launchState by mainViewModel.launchState.collectAsStateWithLifecycle()
  val announcementHistory by mainViewModel.announcementHistory.collectAsStateWithLifecycle()
  val systemGameUsageMap by mainViewModel.systemGameUsageMap.collectAsStateWithLifecycle()
  val globalStats by mainViewModel.globalStats.collectAsStateWithLifecycle()
  val isMonitoringActive by mainViewModel.isDeviceMonitoringActive.collectAsStateWithLifecycle()
  val notificationCountsByPackage by mainViewModel.notificationCountsByPackage.collectAsStateWithLifecycle()
  val allNotifications by notificationsViewModel.allNotifications.collectAsStateWithLifecycle()
  val unreadGameNotifsCount = remember(allNotifications) { allNotifications.count { !it.isRead } }
  val updateUiState by updateViewModel.uiState.collectAsStateWithLifecycle()

  val customWallpaperBitmap by
    produceState<ImageBitmap?>(initialValue = null, key1 = prefs.customWallpaperPath) {
      val path = prefs.customWallpaperPath
      value =
        if (path.isBlank()) {
          null
        } else {
          withContext(Dispatchers.IO) {
            DrxImageUtils.decodeSampledImageBitmap(path, maxWidthPx = 1080, maxHeightPx = 1920)
          }
        }
    }

  val context = LocalContext.current
  val isLowRamDevice =
    remember(context) {
      (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    }

  var showIntro by remember { mutableStateOf(true) }
  LaunchedEffect(Unit) {
    UpdateCheckWorker.enqueuePeriodicCheck(context)
  }

  LaunchedEffect(pendingDeepRoute) {
    if (!pendingDeepRoute.isNullOrBlank()) {
      runCatching {
        navController.navigate(pendingDeepRoute) { launchSingleTop = true }
      }
      onConsumeDeepRoute()
    }
  }

  val navTabs =
    listOf(
      NavTab(MainRoutes.HOME, strings.navHome, Icons.Filled.Home, Icons.Outlined.Home),
      NavTab(MainRoutes.GAMES, strings.navGames, Icons.Filled.SportsEsports, Icons.Outlined.SportsEsports),
      NavTab(MainRoutes.TOOLS, strings.navTools, Icons.Filled.Build, Icons.Outlined.Build),
      NavTab(ToolRoutes.ACTIVITY_CENTER, strings.navActivity, Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
      NavTab(MainRoutes.PROFILE, strings.navProfile, Icons.Filled.Person, Icons.Outlined.Person),
    )

  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentDest = backStackEntry?.destination

  val navigateTab: (String) -> Unit = { route ->
    runCatching {
      navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
      }
    }
  }

  val navItemsUi =
    navTabs.map { tab ->
      val selected = currentDest?.hierarchy?.any { it.route == tab.route } == true
      DrxNavItemUi(
        route = tab.route,
        label = tab.label,
        selectedIcon = tab.selectedIcon,
        unselectedIcon = tab.unselectedIcon,
        selected = selected,
        onClick = { navigateTab(tab.route) },
      )
    }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isWideScreen = maxWidth >= 600.dp

    Scaffold(
      contentWindowInsets =
        if (isWideScreen) WindowInsets.safeDrawing
        else WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
      containerColor = MaterialTheme.colorScheme.background,
      bottomBar = {
        if (!isWideScreen) {
          DrxCustomNavigationBar(items = navItemsUi)
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
      ) {
        if (isWideScreen) {
          NavigationRail(
            containerColor = drxColors.secondaryBg,
            modifier = Modifier.fillMaxHeight(),
          ) {
            Spacer(Modifier.height(12.dp))
            DrxLogoBadge(size = 38.dp)
            Spacer(Modifier.height(16.dp))
            navTabs.forEach { tab ->
              val selected = currentDest?.hierarchy?.any { it.route == tab.route } == true
              NavigationRailItem(
                selected = selected,
                onClick = { navigateTab(tab.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tab.label,
                  )
                },
                label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                colors =
                  NavigationRailItemDefaults.colors(
                    selectedIconColor = drxColors.electricBlue,
                    selectedTextColor = drxColors.electricBlue,
                    indicatorColor = drxColors.electricBlue.copy(alpha = 0.16f),
                  ),
                modifier = Modifier.testTag("nav_rail_${tab.route}"),
              )
            }
          }
        }

        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.TopCenter,
        ) {
          val wallpaperBmp = customWallpaperBitmap
          if (wallpaperBmp != null) {
            Image(
              bitmap = wallpaperBmp,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              alpha = 0.20f,
              modifier = Modifier.fillMaxSize(),
            )
            Box(
              modifier =
                Modifier
                  .fillMaxSize()
                  .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f))
            )
          }
          val animDur = if (prefs.animationsEnabled) 200 else 50
          NavHost(
            navController = navController,
            startDestination = MainRoutes.HOME,
            enterTransition = {
              fadeIn(tween(animDur)) +
                slideInHorizontally(tween(animDur)) { if (prefs.animationsEnabled) it / 20 else 0 }
            },
            exitTransition = {
              fadeOut(tween(animDur)) +
                slideOutHorizontally(tween(animDur)) { if (prefs.animationsEnabled) -it / 20 else 0 }
            },
            popEnterTransition = {
              fadeIn(tween(animDur)) +
                slideInHorizontally(tween(animDur)) { if (prefs.animationsEnabled) -it / 20 else 0 }
            },
            popExitTransition = {
              fadeOut(tween(animDur)) +
                slideOutHorizontally(tween(animDur)) { if (prefs.animationsEnabled) it / 20 else 0 }
            },
            modifier = Modifier.widthIn(max = 860.dp).fillMaxSize(),
          ) {
            composable(MainRoutes.HOME) {
              LaunchedEffect(Unit) {
                updateViewModel.checkForUpdatesAuto(forceRefresh = false)
              }
              DashboardScreen(
                telemetry = telemetry,
                prefs = prefs,
                games = games,
                isBoosting = isBoosting,
                boostReport = boostReport,
                activeSession = activeSession,
                viewModel = mainViewModel,
                onNavigateGames = { navigateTab(MainRoutes.GAMES) },
                onNavigateProfile = { navigateTab(MainRoutes.PROFILE) },
                onNavigateSettings = { navController.navigate(MainRoutes.SETTINGS) },
                onNavigateTool = { route -> navController.navigate(route) },
                onNavigateUpdateLog = { navController.navigate(MainRoutes.UPDATE_LOG) },
                onNavigateTools = { navigateTab(MainRoutes.TOOLS) },
                globalStats = globalStats,
                onOpenGameStats = { pkg -> navController.navigate(MainRoutes.gameDetailStatsRoute(pkg)) },
                onOpenCustomization = { navController.navigate(MainRoutes.CUSTOMIZATION) },
                updateUiState = updateUiState,
                unreadGameNotifsCount = unreadGameNotifsCount,
                onDownloadUpdate = {
                  if (updateUiState.pipelineState == DrxUpdatePipelineState.READY_TO_INSTALL) {
                    updateViewModel.installVerifiedApk()
                  } else {
                    updateViewModel.startDownloadAndVerify()
                  }
                  navController.navigate(ToolRoutes.UPDATE_CENTER) { launchSingleTop = true }
                },
              )
            }
            composable(MainRoutes.GAMES) {
              GamesScreen(
                games = games,
                sessions = allSessions,
                installedApps = installedApps,
                isScanning = isScanningGames,
                isGridView = prefs.gamesGridView,
                viewModel = mainViewModel,
                systemUsageMap = systemGameUsageMap,
                notificationCountsByPackage = notificationCountsByPackage,
                onOpenGameStats = { pkg -> navController.navigate(MainRoutes.gameDetailStatsRoute(pkg)) },
              )
            }
            composable(MainRoutes.TOOLS) {
              ToolsHubScreen(onNavigateTool = { route -> navController.navigate(route) })
            }
            composable(MainRoutes.PROFILE) {
              ProfileHubScreen(
                prefs = prefs,
                sessions = allSessions,
                appUsage = appUsage,
                viewModel = mainViewModel,
                onOpenAllProfiles = { navController.navigate(ToolRoutes.PROFILES) },
                onOpenAllStats = { navController.navigate(ToolRoutes.STATS) },
                onOpenSettings = { navController.navigate(MainRoutes.SETTINGS) },
                onOpenAbout = { navController.navigate(MainRoutes.ABOUT) },
                onOpenCustomization = { navController.navigate(MainRoutes.CUSTOMIZATION) },
              )
            }
            composable(MainRoutes.SETTINGS) {
              SettingsScreen(
                prefs = prefs,
                permissionStates = permissionStates,
                viewModel = mainViewModel,
                onOpenSidebarSettings = { navController.navigate(ToolRoutes.SIDEBAR) },
                onOpenAbout = { navController.navigate(MainRoutes.ABOUT) },
                onOpenAnnouncementCenter = { navController.navigate(MainRoutes.ANNOUNCEMENT_CENTER) },
                onOpenCustomization = { navController.navigate(MainRoutes.CUSTOMIZATION) },
                onOpenGameNotifications = { navController.navigate(ToolRoutes.GAME_NOTIFICATIONS) },
                onOpenUpdateCenter = { navController.navigate(ToolRoutes.UPDATE_CENTER) },
                onBack = { navController.popBackStack() },
              )
            }
            composable(MainRoutes.CUSTOMIZATION) {
              CustomizationScreen(
                prefs = prefs,
                viewModel = mainViewModel,
                onBack = { navController.popBackStack() },
              )
            }
            composable(ToolRoutes.CUSTOMIZATION) {
              CustomizationScreen(
                prefs = prefs,
                viewModel = mainViewModel,
                onBack = { navController.popBackStack() },
              )
            }
            composable(
              route = MainRoutes.GAME_DETAIL_STATS,
              arguments = listOf(navArgument("packageName") { type = NavType.StringType }),
            ) { entry ->
              val encodedPkg = entry.arguments?.getString("packageName").orEmpty()
              val pkg = Uri.decode(encodedPkg)
              GameDetailStatsScreen(
                packageName = pkg,
                prefs = prefs,
                games = games,
                sessions = allSessions,
                viewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                notificationCount = notificationCountsByPackage[pkg] ?: 0,
                onOpenNotifications = { navController.navigate(ToolRoutes.GAME_NOTIFICATIONS) },
                onOpenMediaGallery = { navController.navigate(ToolRoutes.MEDIA_GALLERY) },
              )
            }
            composable(MainRoutes.ABOUT) {
              AboutScreen(
                prefs = prefs,
                viewModel = mainViewModel,
                onOpenUpdateLog = { navController.navigate(MainRoutes.UPDATE_LOG) },
                onOpenUpdateCenter = { navController.navigate(ToolRoutes.UPDATE_CENTER) },
                onBack = { navController.popBackStack() },
              )
            }
            composable(MainRoutes.UPDATE_LOG) {
              UpdateLogScreen(
                prefs = prefs,
                viewModel = mainViewModel,
                onBack = { navController.popBackStack() },
              )
            }
            composable(MainRoutes.ANNOUNCEMENT_CENTER) {
              AnnouncementCenterScreen(
                prefs = prefs,
                history = announcementHistory,
                viewModel = mainViewModel,
                onBack = { navController.popBackStack() },
              )
            }

            composable(ToolRoutes.BOOSTER) {
              BoosterScreen(
                telemetry = telemetry,
                viewModel = mainViewModel,
                onBack = { navController.popBackStack() },
              )
            }
            composable(ToolRoutes.SIDEBAR) {
              FloatingSidebarScreen(
                prefs = prefs,
                viewModel = mainViewModel,
                onBack = { navController.popBackStack() },
              )
            }
            composable(ToolRoutes.NETWORK) {
              NetworkToolsScreen(
                telemetry = telemetry,
                toolsViewModel = toolsViewModel,
                onBack = { navController.popBackStack() },
              )
            }
            composable(ToolRoutes.DISPLAY) {
              DisplayTouchScreen(
                telemetry = telemetry,
                viewModel = mainViewModel,
                onBack = { navController.popBackStack() },
                onOpenProRecorder = { navController.navigate(ToolRoutes.SCREEN_RECORDER) },
              )
            }
            composable(ToolRoutes.SOUND) {
              SoundCenterScreen(
                toolsViewModel = toolsViewModel,
                onBack = { navController.popBackStack() },
              )
            }
            composable(ToolRoutes.VOICE) {
              VoiceStudioScreen(
                prefs = prefs,
                mainViewModel = mainViewModel,
                toolsViewModel = toolsViewModel,
                onBack = { navController.popBackStack() },
              )
            }
            composable(ToolRoutes.MONITOR) {
              DeviceMonitorScreen(
                telemetry = telemetry,
                history = telemetryHistory,
                onBack = { navController.popBackStack() },
                isMonitoringActive = isMonitoringActive,
                onToggleMonitoring = { mainViewModel.setDeviceMonitoringActive(it) },
              )
            }
            composable(ToolRoutes.PROFILES) {
              ProfilesScreen(
                prefs = prefs,
                viewModel = mainViewModel,
                onBack = { navController.popBackStack() },
              )
            }
            composable(ToolRoutes.UTILITIES) {
              UtilitiesScreen(
                prefs = prefs,
                toolsViewModel = toolsViewModel,
                onBack = { navController.popBackStack() },
              )
            }
            composable(ToolRoutes.STATS) {
              val prevRoute = navController.previousBackStackEntry?.destination?.route
              val canPopStats = prevRoute != null
              StatisticsScreen(
                sessions = allSessions,
                onBack = if (canPopStats) { { navController.popBackStack() } } else null,
                globalStats = globalStats,
                onOpenGameStats = { pkg -> navController.navigate(MainRoutes.gameDetailStatsRoute(pkg)) },
                onGrantUsageAccess = { mainViewModel.tweaksManager.openUsageAccessSettings() },
                onClearHistory = { mainViewModel.clearHistory() },
              )
            }
            composable(ToolRoutes.LAG_DETECTIVE) {
              LagDetectiveScreen(
                diagnosticsViewModel = diagnosticsViewModel,
                onOpenHistory = { navController.navigate(ToolRoutes.DIAGNOSTIC_HISTORY) },
                onBack = { navController.popBackStack() },
              )
            }
            composable(ToolRoutes.CRASH_ANALYZER) {
              CrashAnalyzerScreen(
                games = games,
                installedApps = installedApps,
                sessions = allSessions,
                diagnosticsViewModel = diagnosticsViewModel,
                onOpenHistory = { navController.navigate(ToolRoutes.DIAGNOSTIC_HISTORY) },
                onBack = { navController.popBackStack() },
              )
            }
            composable(ToolRoutes.GAMING_HEALTH) {
              GamingHealthScreen(
                diagnosticsViewModel = diagnosticsViewModel,
                onOpenHistory = { navController.navigate(ToolRoutes.DIAGNOSTIC_HISTORY) },
                onBack = { navController.popBackStack() },
              )
            }
            composable(ToolRoutes.SCREEN_RECORDER) {
              ScreenRecorderScreen(
                diagnosticsViewModel = diagnosticsViewModel,
                onOpenGallery = { navController.navigate(ToolRoutes.MEDIA_GALLERY) },
                onBack = { navController.popBackStack() },
              )
            }
            composable(ToolRoutes.MEDIA_GALLERY) {
              MediaGalleryScreen(
                diagnosticsViewModel = diagnosticsViewModel,
                onBack = { navController.popBackStack() },
              )
            }
            composable(ToolRoutes.DIAGNOSTIC_HISTORY) {
              DiagnosticHistoryScreen(
                diagnosticsViewModel = diagnosticsViewModel,
                onBack = { navController.popBackStack() },
              )
            }
            composable(ToolRoutes.DOWNLOAD_CENTER) {
              DownloadCenterScreen(
                diagnosticsViewModel = diagnosticsViewModel,
                onBack = { navController.popBackStack() },
              )
            }
            composable(ToolRoutes.GAME_NOTIFICATIONS) {
              GameNotificationViewerScreen(
                notificationsViewModel = notificationsViewModel,
                mainViewModel = mainViewModel,
                installedApps = installedApps,
                onOpenInsights = { navController.navigate(ToolRoutes.NOTIFICATION_INSIGHTS) },
                onOpenEvents = { navController.navigate(ToolRoutes.GAME_EVENTS) },
                onBack = { navController.popBackStack() },
              )
            }
            composable(ToolRoutes.NOTIFICATION_INSIGHTS) {
              NotificationInsightsScreen(
                notificationsViewModel = notificationsViewModel,
                onBack = { navController.popBackStack() },
              )
            }
            composable(ToolRoutes.GAME_EVENTS) {
              GameEventTrackerScreen(
                notificationsViewModel = notificationsViewModel,
                onBack = { navController.popBackStack() },
              )
            }
            composable(ToolRoutes.ACTIVITY_CENTER) {
              val prevRoute = navController.previousBackStackEntry?.destination?.route
              val bottomTabRoutes =
                setOf(
                  MainRoutes.HOME,
                  MainRoutes.GAMES,
                  MainRoutes.TOOLS,
                  ToolRoutes.ACTIVITY_CENTER,
                  MainRoutes.PROFILE,
                )
              val canPopActivity = prevRoute != null && prevRoute !in bottomTabRoutes
              GameActivityCenterScreen(
                notificationsViewModel = notificationsViewModel,
                globalStats = globalStats,
                onNavigateRoute = { route -> navController.navigate(route) },
                onOpenGameStats = { pkg -> navController.navigate(MainRoutes.gameDetailStatsRoute(pkg)) },
                onBack = if (canPopActivity) { { navController.popBackStack() } } else null,
              )
            }
            composable(ToolRoutes.UPDATE_CENTER) {
              UpdateCenterScreen(
                updateViewModel = updateViewModel,
                onOpenUpdateLog = { navController.navigate(MainRoutes.UPDATE_LOG) },
                onBack = { navController.popBackStack() },
              )
            }
            composable(ToolRoutes.SESSION_INTELLIGENCE) {
              SessionIntelligenceScreen(
                viewModel = mainViewModel,
                onOpenGameStats = { pkg -> navController.navigate(MainRoutes.gameDetailStatsRoute(pkg)) },
                onBack = { navController.popBackStack() },
              )
            }
            composable(ToolRoutes.STABILITY_WATCH) {
              StabilityWatchScreen(
                diagnosticsViewModel = diagnosticsViewModel,
                onBack = { navController.popBackStack() },
              )
            }
          }
        }
      }
    }

    // DRX Cinematic Logo Intro Overlay (6 scenes, skippable, respects Reduced Motion & Low-RAM)
    AnimatedVisibility(
      visible = showIntro,
      enter = fadeIn(tween(120)),
      exit = fadeOut(tween(260)),
    ) {
      DrxCinematicIntroOverlay(
        animationsEnabled = prefs.animationsEnabled,
        isLowRamDevice = isLowRamDevice,
        onSkip = { showIntro = false },
        onFinished = { showIntro = false },
      )
    }

    // Cinematic Game Launch Animation Overlay
    val activeLaunchState = launchState
    AnimatedVisibility(
      visible = activeLaunchState != null,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      if (activeLaunchState != null) {
        GameLaunchOverlay(
          state = activeLaunchState,
          quality = prefs.launchAnimationQuality,
          soundEnabled = prefs.launchSoundEnabled,
          onSkip = { mainViewModel.completePendingLaunch() },
          onAnimationCompleted = { mainViewModel.completePendingLaunch() },
          onRetry = { failedGame -> mainViewModel.startGameLaunch(failedGame, activity) },
          onDismissError = { mainViewModel.dismissLaunchError() },
        )
      }
    }

    // Smart Update Offer Policy Modal (Offers 1 & 2 optional; Offer 3 mandatory blocking)
    val updateTarget = updateUiState.selectedManifest
    val isMandatoryOffer = updateUiState.policyState == UpdateOfferPolicyState.MANDATORY_REQUIRED
    val isOptionalOffer =
      updateUiState.policyState == UpdateOfferPolicyState.OPTIONAL_OFFER_1 ||
        updateUiState.policyState == UpdateOfferPolicyState.OPTIONAL_OFFER_2
    val isOnUpdateCenter = currentDest?.route == ToolRoutes.UPDATE_CENTER
    val shouldShowUpdateModal =
      !showIntro &&
        updateUiState.isUpdateAvailable &&
        updateTarget != null &&
        updateTarget.versionCode > BuildConfig.VERSION_CODE &&
        (isMandatoryOffer || (isOptionalOffer && !updateUiState.dialogDismissedThisSession && !isOnUpdateCenter))

    if (shouldShowUpdateModal) {
      SmartUpdateOfferModal(
        uiState = updateUiState,
        isIndo = prefs.language == LanguageMode.INDONESIAN,
        onUpdateNow = {
          if (isMandatoryOffer) {
            if (updateUiState.stagedApkFile != null) {
              updateViewModel.installVerifiedApk()
            } else if (updateUiState.pipelineState != DrxUpdatePipelineState.DOWNLOADING &&
              updateUiState.pipelineState != DrxUpdatePipelineState.VERIFYING
            ) {
              updateViewModel.startDownloadAndVerify()
            }
          } else {
            updateViewModel.dismissOptionalDialogForSession()
            navController.navigate(ToolRoutes.UPDATE_CENTER) { launchSingleTop = true }
          }
        },
        onLater = { updateViewModel.skipCurrentUpdateOffer() },
        onExitApp = { activity?.finishAffinity() },
        onViewChangelog = {
          if (!isMandatoryOffer) {
            updateViewModel.dismissOptionalDialogForSession()
          }
          navController.navigate(MainRoutes.UPDATE_LOG) { launchSingleTop = true }
        },
      )
    }
  }
}
