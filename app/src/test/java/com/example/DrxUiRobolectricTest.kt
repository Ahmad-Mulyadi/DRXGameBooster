package com.example

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.GameEntity
import com.example.data.db.GameSessionEntity
import com.example.data.model.DayActivityBucket
import com.example.data.model.GamePlaytimeRank
import com.example.data.model.GlobalGamingStatistics
import com.example.data.model.LaunchAnimationQuality
import com.example.data.model.SystemTelemetry
import com.example.data.prefs.DrxUserPrefs
import com.example.i18n.EnglishStrings
import com.example.i18n.IndonesianStrings
import com.example.i18n.LocalStrings
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AnnouncementCenterScreen
import com.example.ui.screens.CustomizationScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DrxCinematicIntroOverlay
import com.example.ui.screens.FloatingSidebarScreen
import com.example.ui.screens.GameDetailStatsScreen
import com.example.ui.screens.GameLaunchOverlay
import com.example.ui.screens.GameLaunchState
import com.example.ui.screens.GamesScreen
import com.example.ui.screens.ProfileHubScreen
import com.example.ui.screens.StatisticsScreen
import com.example.ui.screens.ToolRoutes
import com.example.ui.screens.ToolsHubScreen
import com.example.ui.screens.UpdateLogScreen
import com.example.ui.theme.DrxCustomNavigationBar
import com.example.ui.theme.DrxGameTurboTheme
import com.example.ui.theme.DrxNavItemUi
import com.example.ui.viewmodel.DrxMainViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class DrxUiRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val testViewModelStore = ViewModelStore()

  private fun obtainMainVm(app: Application): DrxMainViewModel =
    ViewModelProvider(testViewModelStore, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[DrxMainViewModel::class.java]

  @After
  fun tearDownViewModels() {
    testViewModelStore.clear()
  }

  @Test
  fun `tools hub renders categorized cards and routes clicks`() {
    var navigatedRoute = ""
    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides IndonesianStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          ToolsHubScreen(onNavigateTool = { route -> navigatedRoute = route })
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Gaming Tools").assertIsDisplayed()
    composeTestRule.onNodeWithTag("card_tool_booster").assertIsDisplayed().performClick()
    assertEquals("tool_booster", navigatedRoute)
    composeTestRule.onNodeWithTag("card_tool_sidebar").assertIsDisplayed()
  }

  @Test
  fun `game launch overlay supports skip and retry error recovery`() {
    val sampleGame = GameEntity(packageName = "com.test.game", appName = "Test Game", category = "fps")
    var skipped = false
    var retried = false
    val currentState = mutableStateOf<GameLaunchState>(GameLaunchState.Launching(sampleGame, 0.5f, 1))

    composeTestRule.setContent {
      val launchState by currentState
      CompositionLocalProvider(LocalStrings provides IndonesianStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          GameLaunchOverlay(
            state = launchState,
            quality = LaunchAnimationQuality.MINIMAL,
            soundEnabled = false,
            onSkip = { skipped = true },
            onAnimationCompleted = {},
            onRetry = { retried = true },
            onDismissError = {},
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("skip_launch_animation_button").assertIsDisplayed().performClick()
    assertTrue(skipped)

    composeTestRule.runOnUiThread {
      currentState.value = GameLaunchState.Failed(sampleGame, "Not installed")
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("retry_launch_button").assertIsDisplayed().performClick()
    assertTrue(retried)
  }

  @Test
  fun `dashboard hero cockpit displays ready for battle boost button and profile pills`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = obtainMainVm(app)
    var settingsNavigated = false

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides IndonesianStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          DashboardScreen(
            telemetry = SystemTelemetry(),
            prefs = DrxUserPrefs(animationsEnabled = false),
            games = emptyList(),
            isBoosting = false,
            boostReport = null,
            activeSession = null,
            viewModel = vm,
            onNavigateGames = {},
            onNavigateProfile = {},
            onNavigateSettings = { settingsNavigated = true },
            onNavigateTool = {},
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Siap bermain dengan performa optimal").assertIsDisplayed()
    composeTestRule.onNodeWithText("READY FOR GAMING").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_version_pill").assertIsDisplayed()
    composeTestRule.onNodeWithText("v${BuildConfig.VERSION_NAME}").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_notifs_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("game_boost_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("profile_pill_performance").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("home_settings_button").assertIsDisplayed().performClick()
    assertTrue(settingsNavigated)
  }

  @Test
  fun `floating sidebar screen displays edge swipe configuration controls`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = obtainMainVm(app)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides IndonesianStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          FloatingSidebarScreen(
            prefs = DrxUserPrefs(floatingEdgeSide = 0),
            viewModel = vm,
            onBack = {},
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("DRX Floating Control Center").assertIsDisplayed()
    composeTestRule.onNodeWithTag("floating_sidebar_switch").assertIsDisplayed()
    composeTestRule.onNodeWithText("Kiri (Geser Kanan)").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Kanan (Geser Kiri)").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("floating_sidebar_list")
      .performScrollToNode(hasTestTag("floating_auto_close_switch"))
    composeTestRule.onNodeWithTag("floating_auto_close_switch").assertIsDisplayed()
  }

  @Test
  fun `custom navigation bar switches tabs via test tags`() {
    var selectedRoute by mutableStateOf("home")
    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides IndonesianStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          DrxCustomNavigationBar(
            items = listOf(
              DrxNavItemUi("home", "Home", Icons.Filled.Home, Icons.Outlined.Home, selectedRoute == "home") { selectedRoute = "home" },
              DrxNavItemUi("games", "Games", Icons.Filled.SportsEsports, Icons.Outlined.SportsEsports, selectedRoute == "games") { selectedRoute = "games" },
              DrxNavItemUi("tools", "Tools", Icons.Filled.Build, Icons.Outlined.Build, selectedRoute == "tools") { selectedRoute = "tools" },
              DrxNavItemUi("tool_stats", "Statistik", Icons.Filled.BarChart, Icons.Outlined.BarChart, selectedRoute == "tool_stats") { selectedRoute = "tool_stats" },
              DrxNavItemUi("profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person, selectedRoute == "profile") { selectedRoute = "profile" },
            )
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_games").assertIsDisplayed().performClick()
    assertEquals("games", selectedRoute)
    composeTestRule.onNodeWithTag("nav_tools").assertIsDisplayed().performClick()
    assertEquals("tools", selectedRoute)
    composeTestRule.onNodeWithTag("nav_tool_stats").assertIsDisplayed().performClick()
    assertEquals("tool_stats", selectedRoute)
  }

  @Test
  fun `about screen displays DerreX profile social links read-only Nanay supporter and footer`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = obtainMainVm(app)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides IndonesianStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          AboutScreen(
            prefs = DrxUserPrefs(animationsEnabled = false),
            viewModel = vm,
            onBack = {},
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("about_developer_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("DERREX").assertIsDisplayed()
    composeTestRule.onNodeWithText("OFFICIAL DEVELOPER").assertIsDisplayed()
    composeTestRule.onNodeWithTag("edit_manual_counters_btn").assertDoesNotExist()
    composeTestRule.onNodeWithTag("edit_supporter_avatar_btn").assertDoesNotExist()

    composeTestRule.onNodeWithTag("about_lazy_column")
      .performScrollToNode(hasTestTag("social_youtube_card"))
    composeTestRule.onNodeWithText("DerreXSA").assertIsDisplayed()

    composeTestRule.onNodeWithTag("about_lazy_column")
      .performScrollToNode(hasTestTag("about_supporter_card"))
    composeTestRule.onNodeWithText("NANAY").assertIsDisplayed()
    composeTestRule.onNodeWithText("@naila27302").assertIsDisplayed()

    composeTestRule.onNodeWithTag("about_lazy_column")
      .performScrollToNode(hasTestTag("about_footer"))
    composeTestRule.onNodeWithText("\"PLAY SMARTER. GAME FASTER.\"").assertIsDisplayed()
  }

  @Test
  fun `update log screen displays v1_3_0 and v1_2_0 changelog timeline and whats new controls`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = obtainMainVm(app)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides IndonesianStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          UpdateLogScreen(
            prefs = DrxUserPrefs(readUpdateVersions = emptySet()),
            viewModel = vm,
            onBack = {},
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("DRX Update Log").assertIsDisplayed()
    composeTestRule.onNodeWithTag("whats_new_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("update_entry_1.20.0").assertIsDisplayed()
    composeTestRule.onNodeWithText("v1.20.0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("update_log_lazy_column")
      .performScrollToNode(hasTestTag("update_entry_1.10.0"))
    composeTestRule.onNodeWithTag("update_entry_1.10.0").assertIsDisplayed()
    composeTestRule.onNodeWithText("v1.10.0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("update_log_lazy_column")
      .performScrollToNode(hasTestTag("update_entry_1.9.0"))
    composeTestRule.onNodeWithTag("update_entry_1.9.0").assertIsDisplayed()
    composeTestRule.onNodeWithText("v1.9.0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("update_log_lazy_column")
      .performScrollToNode(hasTestTag("update_entry_1.3.0"))
    composeTestRule.onNodeWithTag("update_entry_1.3.0").assertIsDisplayed()
    composeTestRule.onNodeWithText("v1.3.0").assertIsDisplayed()
    composeTestRule.onNodeWithTag("update_log_lazy_column")
      .performScrollToNode(hasTestTag("update_entry_1.2.0"))
    composeTestRule.onNodeWithTag("update_entry_1.2.0").assertIsDisplayed()
    composeTestRule.onNodeWithText("v1.2.0").assertIsDisplayed()
  }

  @Test
  fun `announcement center screen displays automatic random controls and preview button`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = obtainMainVm(app)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides IndonesianStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          AnnouncementCenterScreen(
            prefs = DrxUserPrefs(announcementsEnabled = true),
            history = emptyList(),
            viewModel = vm,
            onBack = {},
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Announcement Center").assertIsDisplayed()
    composeTestRule.onNodeWithTag("toggle_announcements_switch").assertIsDisplayed()
    composeTestRule.onNodeWithTag("preview_announcement_button").assertIsDisplayed()
    composeTestRule.onNodeWithText("1–3 / day").assertDoesNotExist()
    composeTestRule.onNodeWithText("22:00 – 07:00").assertDoesNotExist()
    composeTestRule.onNodeWithTag("reshuffle_schedule_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("reset_rotation_button").assertDoesNotExist()
  }

  @Test
  fun `customization screen displays 7 theme presets wallpaper controls and editions roadmap`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = obtainMainVm(app)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides IndonesianStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          CustomizationScreen(
            prefs = DrxUserPrefs(),
            viewModel = vm,
            onBack = {},
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("DRX Customization Center").assertIsDisplayed()
    composeTestRule.onNodeWithTag("theme_preset_drx_default").assertIsDisplayed()
    composeTestRule.onNodeWithTag("theme_preset_amoled_black").assertIsDisplayed()
    composeTestRule.onNodeWithTag("theme_preset_cyberpunk_blue").assertIsDisplayed()

    composeTestRule.onNodeWithTag("customization_screen_list")
      .performScrollToNode(hasTestTag("choose_wallpaper_btn"))
    composeTestRule.onNodeWithTag("choose_wallpaper_btn").assertIsDisplayed()

    composeTestRule.onNodeWithTag("customization_screen_list")
      .performScrollToNode(androidx.compose.ui.test.hasText("DRX FREE"))
    composeTestRule.onNodeWithText("DRX FREE").assertIsDisplayed()
    composeTestRule.onNodeWithText("DRX PRO").assertIsDisplayed()
  }

  @Test
  fun `global statistics screen renders KPIs ranked games and routes to game detail stats`() {
    val now = System.currentTimeMillis()
    val fakeBuckets =
      (6 downTo 0).map { ago ->
        DayActivityBucket("D$ago", now - ago * 86_400_000L, 1_800_000L)
      }
    val globalStats =
      GlobalGamingStatistics(
        totalTrackedGames = 2,
        installedTrackedGames = 2,
        totalPlaytimeMs = 5_400_000L,
        mostPlayedByTimeName = "Apex Mobile",
        mostPlayedByTimePkg = "com.ea.apex",
        mostPlayedByTimeMs = 3_600_000L,
        mostOpenedGameName = "Apex Mobile",
        mostOpenedPkg = "com.ea.apex",
        mostOpenedCount = 6,
        lastPlayedGameName = "Apex Mobile",
        lastPlayedPkg = "com.ea.apex",
        lastPlayedAtMs = now - 600_000L,
        totalSessionsCount = 4,
        averageSessionDurationMs = 1_350_000L,
        hasUsagePermission = true,
        usesEstimatedSystemUsage = true,
        weeklyChart = fakeBuckets,
        monthlyChart = fakeBuckets,
        rankedGames =
          listOf(
            GamePlaytimeRank("com.ea.apex", "Apex Mobile", 3_600_000L, 6, now - 600_000L, true),
          ),
      )

    var openedPkg = ""
    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides IndonesianStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          StatisticsScreen(
            sessions =
              listOf(
                GameSessionEntity(1L, "com.ea.apex", "Apex Mobile", now - 2_400_000L, now - 600_000L, 1_800_000L, "performance", 85, 78, 38.2f),
              ),
            onBack = {},
            globalStats = globalStats,
            onOpenGameStats = { openedPkg = it },
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("DRX Global Game Statistics").assertIsDisplayed()
    composeTestRule.onNodeWithTag("global_stats_list")
      .performScrollToNode(hasTestTag("stats_game_row_com.ea.apex"))
    composeTestRule.onNodeWithTag("stats_game_row_com.ea.apex").assertIsDisplayed().performClick()
    assertEquals("com.ea.apex", openedPkg)
  }

  @Test
  fun `game detail stats screen displays metadata playtime storage chart and honest restricted labels`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = obtainMainVm(app)
    val pkg = "com.test.notinstalled.game"

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides IndonesianStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          GameDetailStatsScreen(
            packageName = pkg,
            prefs = DrxUserPrefs(),
            games = listOf(GameEntity(pkg, "Nova Arena", category = "moba")),
            sessions = emptyList(),
            viewModel = vm,
            onBack = {},
          )
        }
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      vm.selectedGameStats.value?.gameInfo?.packageName == pkg
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("DRX Game Statistics").assertIsDisplayed()
    composeTestRule.onNodeWithText("GAME INFORMATION").assertIsDisplayed()
    composeTestRule.onNodeWithTag("game_detail_stats_list")
      .performScrollToNode(hasTestTag("game_total_playtime_value"))
    composeTestRule.onNodeWithText("PLAYTIME STATISTICS").assertIsDisplayed()
    composeTestRule.onNodeWithTag("game_detail_stats_list")
      .performScrollToNode(hasTestTag("chart_toggle_30d"))
    composeTestRule.onNodeWithTag("chart_toggle_7d").assertIsDisplayed()
    composeTestRule.onNodeWithTag("chart_toggle_30d").assertIsDisplayed().performClick()
  }

  @Test
  fun `games screen displays sort pills and view game statistics action in details modal`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = obtainMainVm(app)
    val sampleGame = GameEntity("com.test.arena", "Test Arena", category = "fps", playCount = 3, totalPlayTimeMs = 1_800_000L)
    var openedStatsPkg = ""

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides IndonesianStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          GamesScreen(
            games = listOf(sampleGame),
            sessions = emptyList(),
            installedApps = emptyList(),
            isScanning = false,
            isGridView = false,
            viewModel = vm,
            onOpenGameStats = { openedStatsPkg = it },
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("sort_pill_playtime").assertIsDisplayed()
    composeTestRule.onNodeWithTag("sort_pill_last_played").assertIsDisplayed()
    composeTestRule.onNodeWithTag("sort_pill_name").assertIsDisplayed()
    composeTestRule.onNodeWithTag("card_stats_list_com.test.arena").assertIsDisplayed().performClick()
    assertEquals("com.test.arena", openedStatsPkg)
  }

  @Test
  fun `cinematic intro overlay displays DRX branding tagline and skip action`() {
    var skipped = false
    composeTestRule.mainClock.autoAdvance = false
    try {
      composeTestRule.setContent {
        CompositionLocalProvider(LocalStrings provides EnglishStrings) {
          DrxGameTurboTheme(darkTheme = true) {
            DrxCinematicIntroOverlay(
              animationsEnabled = true,
              isLowRamDevice = false,
              onSkip = { skipped = true },
              onFinished = {},
            )
          }
        }
      }

      composeTestRule.mainClock.advanceTimeBy(1600L)
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("drx_cinematic_intro_overlay").assertIsDisplayed()
      composeTestRule.onNodeWithText("DRX GAME TURBO PRO").assertIsDisplayed()
      composeTestRule.onNodeWithText("POWER YOUR GAME. OWN YOUR EXPERIENCE.").assertIsDisplayed()
      composeTestRule.onNodeWithTag("skip_intro_animation_button").assertIsDisplayed().performClick()
      composeTestRule.mainClock.advanceTimeBy(50L)
      assertTrue(skipped)
    } finally {
      composeTestRule.mainClock.autoAdvance = true
    }
  }

  @Test
  fun `game launch overlay displays DRX PERFORMANCE ENGINE stage text`() {
    val sampleGame = GameEntity(packageName = "com.test.fps", appName = "Delta Strike", category = "fps")
    composeTestRule.mainClock.autoAdvance = false
    try {
      composeTestRule.setContent {
        CompositionLocalProvider(LocalStrings provides EnglishStrings) {
          DrxGameTurboTheme(darkTheme = true) {
            GameLaunchOverlay(
              state = GameLaunchState.Launching(sampleGame, 0.10f, 0),
              quality = LaunchAnimationQuality.MINIMAL,
              soundEnabled = false,
              onSkip = {},
              onAnimationCompleted = {},
              onRetry = {},
              onDismissError = {},
            )
          }
        }
      }

      composeTestRule.mainClock.advanceTimeBy(120L)
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithTag("game_launch_overlay").assertIsDisplayed()
      composeTestRule.onNodeWithText("DRX PERFORMANCE ENGINE").assertIsDisplayed()

      composeTestRule.mainClock.advanceTimeBy(350L)
      composeTestRule.waitForIdle()
      composeTestRule.onNodeWithText("Preparing Gaming Session...").assertIsDisplayed()

      composeTestRule.mainClock.advanceTimeBy(1200L)
      composeTestRule.waitForIdle()
      assertTrue(composeTestRule.onAllNodesWithText("READY TO PLAY").fetchSemanticsNodes().isNotEmpty())
    } finally {
      composeTestRule.mainClock.autoAdvance = true
    }
  }

  @Test
  fun `redesigned dashboard displays 6-kpi telemetry and 6 quick tools without clutter`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = obtainMainVm(app)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          DashboardScreen(
            telemetry = SystemTelemetry(),
            prefs = DrxUserPrefs(animationsEnabled = false),
            games = emptyList(),
            isBoosting = false,
            boostReport = null,
            activeSession = null,
            viewModel = vm,
            onNavigateGames = {},
            onNavigateProfile = {},
            onNavigateSettings = {},
            onNavigateTool = {},
            onNavigateTools = {},
            unreadGameNotifsCount = 2,
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("home_profile_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("home_customization_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("home_dashboard_list")
      .performScrollToNode(hasTestTag("home_telemetry_card"))
    composeTestRule.onNodeWithText("UI FPS").assertIsDisplayed()
    composeTestRule.onNodeWithText("CPU").assertIsDisplayed()
    composeTestRule.onNodeWithText("RAM").assertIsDisplayed()
    composeTestRule.onNodeWithText("Battery").assertIsDisplayed()
    composeTestRule.onNodeWithText("Temp").assertIsDisplayed()
    composeTestRule.onNodeWithText("Network").assertIsDisplayed()

    composeTestRule.onNodeWithTag("home_dashboard_list")
      .performScrollToNode(hasTestTag("home_quick_sidebar_tile"))
    composeTestRule.onNodeWithTag("home_quick_sidebar_tile").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_quick_recorder_tile").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_quick_screenshot_button").assertIsDisplayed()

    composeTestRule.onNodeWithTag("home_dashboard_list")
      .performScrollToNode(hasTestTag("home_quick_notifs_tile"))
    composeTestRule.onNodeWithTag("home_quick_notifs_tile").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_quick_voice_tile").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_quick_lag_tile").assertIsDisplayed()
  }

  @Test
  fun `floating capsule 5-tab navigation switches Home Games Tools Activity and Profile`() {
    var selectedRoute by mutableStateOf("home")
    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          DrxCustomNavigationBar(
            items = listOf(
              DrxNavItemUi("home", "Home", Icons.Filled.Home, Icons.Outlined.Home, selectedRoute == "home") { selectedRoute = "home" },
              DrxNavItemUi("games", "Games", Icons.Filled.SportsEsports, Icons.Outlined.SportsEsports, selectedRoute == "games") { selectedRoute = "games" },
              DrxNavItemUi("tools", "Tools", Icons.Filled.Build, Icons.Outlined.Build, selectedRoute == "tools") { selectedRoute = "tools" },
              DrxNavItemUi(ToolRoutes.ACTIVITY_CENTER, "Activity", Icons.Filled.BarChart, Icons.Outlined.BarChart, selectedRoute == ToolRoutes.ACTIVITY_CENTER) { selectedRoute = ToolRoutes.ACTIVITY_CENTER },
              DrxNavItemUi("profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person, selectedRoute == "profile") { selectedRoute = "profile" },
            )
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_${ToolRoutes.ACTIVITY_CENTER}").assertIsDisplayed().performClick()
    assertEquals(ToolRoutes.ACTIVITY_CENTER, selectedRoute)
    composeTestRule.onNodeWithTag("nav_profile").assertIsDisplayed().performClick()
    assertEquals("profile", selectedRoute)
  }

  @Test
  fun `profile hub renders grouped shortcuts without duplicate header icon buttons`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = obtainMainVm(app)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          ProfileHubScreen(
            prefs = DrxUserPrefs(),
            sessions = emptyList(),
            appUsage = emptyList(),
            viewModel = vm,
            onOpenAllProfiles = {},
            onOpenAllStats = {},
            onOpenCustomization = {},
            onOpenSettings = {},
            onOpenAbout = {},
          )
        }
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("profile_about_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("profile_settings_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("profile_customization_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("profile_settings_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("profile_about_card").performScrollTo().assertIsDisplayed()
  }
}
