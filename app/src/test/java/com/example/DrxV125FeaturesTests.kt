package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.BatteryManager
import android.os.PowerManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.DrxDatabase
import com.example.data.db.GameEntity
import com.example.data.db.GameProfileAutomationEntity
import com.example.data.db.GameSessionEntity
import com.example.data.db.SessionReportEntity
import com.example.data.db.SessionTimelineEventEntity
import com.example.data.model.BatteryInfoData
import com.example.data.model.CompatibilityStatus
import com.example.data.model.CpuInfo
import com.example.data.model.DevicePerformanceState
import com.example.data.model.DisplayInfoData
import com.example.data.model.LanguageMode
import com.example.data.model.MemoryInfoData
import com.example.data.model.NetworkSnapshot
import com.example.data.model.PreGameCheckItem
import com.example.data.model.PreGameCheckReport
import com.example.data.model.PreGameCheckStatus
import com.example.data.model.ScreenshotFormat
import com.example.data.model.SessionTimelineEventType
import com.example.data.model.StorageInfoData
import com.example.data.model.SystemTelemetry
import com.example.data.model.ThermalTier
import com.example.data.model.TouchTestMode
import com.example.data.prefs.DrxUserPrefs
import com.example.i18n.EnglishStrings
import com.example.i18n.LocalStrings
import com.example.system.GameCompatibilityEngine
import com.example.system.GameResourceReportEngine
import com.example.system.GamingFocusModeController
import com.example.system.PreGameCheckEngine
import com.example.system.ScreenshotEngine
import com.example.system.SmartStorageInspectorEngine
import com.example.system.SystemTweaksManager
import com.example.system.TelemetryMonitor
import com.example.system.ThermalGuardEngine
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DisplayTouchScreen
import com.example.ui.screens.DrxBoostAnimationOverlay
import com.example.ui.screens.GameCompatibilityScreen
import com.example.ui.screens.GameProfileAutomationScreen
import com.example.ui.screens.GameResourceReportScreen
import com.example.ui.screens.GamingFocusModeScreen
import com.example.ui.screens.PreGameCheckModal
import com.example.ui.screens.PreGameCheckScreen
import com.example.ui.screens.ScreenshotScreen
import com.example.ui.screens.SessionRecapScreen
import com.example.ui.screens.SessionTimelineScreen
import com.example.ui.screens.SmartStorageInspectorScreen
import com.example.ui.screens.ThermalGuardScreen
import com.example.ui.screens.TouchResponseTestScreen
import com.example.ui.theme.DrxGameTurboTheme
import com.example.ui.viewmodel.DrxDiagnosticsViewModel
import com.example.ui.viewmodel.DrxMainViewModel
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
class DrxV125FeaturesTests {

  @get:Rule val composeTestRule = createComposeRule()

  private val viewModelStore = ViewModelStore()

  @After
  fun cleanup() {
    viewModelStore.clear()
  }

  private fun obtainMainVm(app: Application): DrxMainViewModel =
    ViewModelProvider(viewModelStore, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[
      DrxMainViewModel::class.java
    ]

  private fun sampleTelemetry(
    availRamMb: Long = 3200L,
    totalRamMb: Long = 8192L,
    tempC: Float = 34.0f,
    batteryPercent: Int = 82,
    thermalCode: Int = PowerManager.THERMAL_STATUS_NONE,
    freeStorageBytes: Long = 20L * 1024L * 1024L * 1024L,
    isConnected: Boolean = true,
    qualityLevel: Int = 4,
  ): SystemTelemetry =
    SystemTelemetry(
      cpuInfo = CpuInfo(cores = 8, usagePercent = null, currentFreqMhz = 1800, maxFreqMhz = 2400, abi = "arm64-v8a"),
      memoryInfo =
        MemoryInfoData(
          totalBytes = totalRamMb * 1024L * 1024L,
          availBytes = availRamMb * 1024L * 1024L,
          usedPercent = (((totalRamMb - availRamMb).toFloat() / totalRamMb.toFloat()) * 100f).coerceIn(0f, 100f),
          lowMemory = availRamMb < 500L,
        ),
      batteryInfo =
        BatteryInfoData(
          levelPercent = batteryPercent,
          temperatureC = tempC,
          voltageMv = 4150,
          isCharging = false,
          chargeSource = "Battery",
          healthCode = BatteryManager.BATTERY_HEALTH_GOOD,
        ),
      storageInfo =
        StorageInfoData(
          totalBytes = 128L * 1024L * 1024L * 1024L,
          freeBytes = freeStorageBytes,
          usedPercent = 55f,
        ),
      networkSnapshot =
        NetworkSnapshot(
          isConnected = isConnected,
          transportType = if (isConnected) "Wi-Fi" else "Offline",
          networkSubType = if (isConnected) "5 GHz" else "-",
          linkSpeedMbps = if (isConnected) 350 else null,
          qualityLevel = if (isConnected) qualityLevel else 0,
        ),
      displayInfo = DisplayInfoData(widthPx = 1080, heightPx = 2400, densityDpi = 420, refreshRateHz = 120f, supportedRefreshRates = listOf(60f, 120f)),
      thermalStatusCode = thermalCode,
      uiFps = 120f,
    )

  @Test
  fun `room migration 5 to 6 creates timeline and automation tables and supports CRUD`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val helper =
      FrameworkSQLiteOpenHelperFactory().create(
        androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
          .name(null)
          .callback(
            object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(5) {
              override fun onCreate(db: SupportSQLiteDatabase) {}

              override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }
          )
          .build()
      )
    val rawDb = helper.writableDatabase
    DrxDatabase.MIGRATION_5_6.migrate(rawDb)

    rawDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='session_timeline_events'").use { c ->
      assertTrue(c.moveToFirst())
    }
    rawDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='game_profile_automations'").use { c ->
      assertTrue(c.moveToFirst())
    }
    rawDb.close()

    val db = Room.inMemoryDatabaseBuilder(context, DrxDatabase::class.java).allowMainThreadQueries().build()
    try {
      db.sessionTimelineEventDao().insert(
        SessionTimelineEventEntity(
          sessionId = "s_1",
          gamePackageName = "com.game.alpha",
          gameName = "Alpha Strike",
          eventType = SessionTimelineEventType.LAUNCHED.code,
          title = "Game Launched",
          detail = "Alpha Strike • PERFORMANCE",
          timestampMs = 1_700_000_000_000L,
          batteryPercent = 88,
          batteryTempC = 34.5f,
          ramFreeMb = 3100L,
          ramUsedPercent = 62f,
          networkStatus = "Wi-Fi",
          thermalStatusCode = 0,
        )
      )
      val events = db.sessionTimelineEventDao().observeAll().first()
      assertEquals(1, events.size)
      assertEquals("s_1", events.first().sessionId)

      db.gameProfileAutomationDao().upsert(
        GameProfileAutomationEntity(
          packageName = "com.game.alpha",
          appName = "Alpha Strike",
          gamingProfileId = "performance",
          hudLayoutId = "full",
          fpsMonitorEnabled = true,
        )
      )
      val loadedAuto = db.gameProfileAutomationDao().getByPackage("com.game.alpha")
      assertNotNull(loadedAuto)
      assertEquals("performance", loadedAuto?.gamingProfileId)
      db.gameProfileAutomationDao().deleteByPackage("com.game.alpha")
      assertNull(db.gameProfileAutomationDao().getByPackage("com.game.alpha"))
    } finally {
      db.close()
    }
  }

  @Test
  fun `game resource report engine honors restricted CPU and compares two sessions accurately`() {
    val pkg = "com.game.arena"
    val game = GameEntity(packageName = pkg, appName = "Arena Clash", category = "moba")
    val sessions =
      listOf(
        GameSessionEntity(
          id = 1L,
          gamePackageName = pkg,
          gameName = "Arena Clash",
          startedAt = 1000L,
          endedAt = 1_801_000L,
          durationMs = 1_800_000L,
          profileUsed = "performance",
          batteryStartPercent = 90,
          batteryEndPercent = 78,
          peakTempC = 42.0f,
        ),
        GameSessionEntity(
          id = 2L,
          gamePackageName = pkg,
          gameName = "Arena Clash",
          startedAt = 2_000_000L,
          endedAt = 2_900_000L,
          durationMs = 900_000L,
          profileUsed = "balanced",
          batteryStartPercent = 78,
          batteryEndPercent = 72,
          peakTempC = 37.5f,
        ),
      )
    val events =
      listOf(
        SessionTimelineEventEntity(
          sessionId = "s_1",
          gamePackageName = pkg,
          gameName = "Arena Clash",
          eventType = SessionTimelineEventType.THERMAL_WARNING.code,
          title = "Thermal Warning",
          detail = "42.0°C",
          timestampMs = 500_000L,
          batteryPercent = 82,
          batteryTempC = 42.0f,
          ramFreeMb = 2100L,
          ramUsedPercent = 74f,
          networkStatus = "Wi-Fi",
          thermalStatusCode = 2,
        )
      )
    val reports =
      GameResourceReportEngine.buildPerGameReports(
        games = listOf(game),
        sessions = sessions,
        sessionReports = emptyList<SessionReportEntity>(),
        timelineEvents = events,
        cpuUsageRestricted = true,
        avgCpuPercentSample = 51f,
        isIndo = false,
      )
    assertEquals(1, reports.size)
    val report = reports.first()
    assertEquals(2, report.sessionCount)
    assertEquals(2_700_000L, report.totalPlaytimeMs)
    assertEquals(18, report.estimatedTotalBatteryDrainPercent)
    assertNull(report.avgCpuPercentOrNull)
    assertTrue(report.hasRestrictedCpu)
    assertTrue(report.monitoringAvailabilityLabel.contains("restricted", ignoreCase = true))
    assertEquals(1, report.thermalWarningCount)
    assertEquals(42.0f, report.peakTempC, 0.01f)

    val comparison =
      GameResourceReportEngine.compareTwoSessionsForGame(
        left = sessions[0],
        right = sessions[1],
        leftEvents = events,
        rightEvents = emptyList(),
        isIndo = false,
      )
    assertEquals(5, comparison.size)
    assertEquals("12%", comparison[1].leftValue)
    assertEquals("6%", comparison[1].rightValue)
    assertEquals("+6%", comparison[1].deltaText)
  }

  @Test
  fun `pre game check engine surfaces warnings honestly and marks monitoring inactive when paused`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tweaks = SystemTweaksManager(context)

    val healthy =
      PreGameCheckEngine.runCheck(
        telemetry = sampleTelemetry(),
        tweaksManager = tweaks,
        isMonitoringActive = true,
        targetGameName = "Arena",
        targetPackageName = "com.game.arena",
        isIndo = false,
      )
    assertEquals(7, healthy.items.size)
    assertTrue(healthy.items.first { it.id == "storage" }.status == PreGameCheckStatus.READY)
    assertTrue(healthy.items.first { it.id == "battery_temp" }.status == PreGameCheckStatus.READY)
    assertTrue(healthy.items.first { it.id == "network" }.status == PreGameCheckStatus.READY)
    assertTrue(healthy.items.first { it.id == "monitoring" }.status == PreGameCheckStatus.READY)

    val stressed =
      PreGameCheckEngine.runCheck(
        telemetry =
          sampleTelemetry(
            tempC = 43.0f,
            batteryPercent = 15,
            thermalCode = PowerManager.THERMAL_STATUS_MODERATE,
            freeStorageBytes = 400L * 1024L * 1024L,
            isConnected = false,
          ),
        tweaksManager = tweaks,
        isMonitoringActive = false,
        targetGameName = "Arena",
        targetPackageName = "com.game.arena",
        isIndo = false,
      )
    assertEquals(PreGameCheckStatus.WARNING, stressed.overallStatus)
    assertEquals(PreGameCheckStatus.WARNING, stressed.items.first { it.id == "storage" }.status)
    assertEquals(PreGameCheckStatus.WARNING, stressed.items.first { it.id == "battery_temp" }.status)
    assertEquals(PreGameCheckStatus.WARNING, stressed.items.first { it.id == "thermal" }.status)
    assertEquals(PreGameCheckStatus.WARNING, stressed.items.first { it.id == "network" }.status)
    assertEquals(PreGameCheckStatus.UNAVAILABLE, stressed.items.first { it.id == "monitoring" }.status)
  }

  @Test
  fun `gaming focus mode controller does not silently force DND without policy access and builds summary`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tweaks = SystemTweaksManager(context)
    val telemetry = TelemetryMonitor(context)

    val active =
      GamingFocusModeController.activateFocusSession(
        tweaksManager = tweaks,
        telemetryMonitor = telemetry,
        targetMinutes = 45,
        enableDnd = true,
        extendTimeoutMs = null,
      )
    assertTrue(active.isActive)
    assertEquals(45, active.targetMinutes)
    assertFalse(active.dndActuallyEnabled)

    val stopped =
      GamingFocusModeController.stopFocusSession(
        current = active,
        tweaksManager = tweaks,
        restoreDnd = true,
        restoreTimeout = true,
      )
    assertFalse(stopped.isActive)
    val summary = stopped.lastSummary
    assertNotNull(summary)
    assertFalse(summary!!.dndActivated)
    val formatted = GamingFocusModeController.formatSummaryText(summary, isIndo = false)
    assertTrue(formatted.contains("Duration"))
    assertTrue(formatted.contains("Target 45m"))
  }

  @Test
  fun `v1_25_5 model enums match expected count`() {
    assertEquals(5, TouchTestMode.entries.size)
    assertEquals(5, DevicePerformanceState.entries.size)
    assertEquals(10, SessionTimelineEventType.entries.size)
  }

  @Test
  fun `dashboard renders v1_25_5 performance status pill and display screen removes duplicate recorder`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = obtainMainVm(app)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          DashboardScreen(
            telemetry = sampleTelemetry(),
            prefs = DrxUserPrefs(language = LanguageMode.ENGLISH, animationsEnabled = false),
            games = emptyList(),
            isBoosting = false,
            boostReport = null,
            activeSession = null,
            viewModel = vm,
            onNavigateGames = {},
            onNavigateProfile = {},
            onNavigateSettings = {},
            onNavigateTool = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("home_perf_status_pill").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_version_pill").assertIsDisplayed()
    composeTestRule.onNodeWithTag("game_boost_button").assertIsDisplayed()
  }

  @Test
  fun `display intelligence screen navigates to touch test and unified recorder without embedded duplicate`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = obtainMainVm(app)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          DisplayTouchScreen(
            telemetry = sampleTelemetry(),
            viewModel = vm,
            onBack = {},
            onOpenProRecorder = {},
            onOpenTouchTest = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("display_intelligence_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("display_open_touch_test_card").assertExists()
    composeTestRule.onNodeWithTag("display_open_recorder_card").assertExists()
    composeTestRule.onNodeWithTag("screen_record_toggle_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("multi_touch_canvas").assertDoesNotExist()
  }

  @Test
  fun `session timeline screen renders cleanly in robolectric`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = obtainMainVm(app)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          SessionTimelineScreen(viewModel = vm, onBack = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("session_timeline_list").assertIsDisplayed()
  }

  @Test
  fun `game resource report screen renders cleanly in robolectric`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = obtainMainVm(app)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          GameResourceReportScreen(viewModel = vm, onBack = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("resource_report_list").assertIsDisplayed()
  }

  @Test
  fun `game profile automation screen renders cleanly in robolectric`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = obtainMainVm(app)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          GameProfileAutomationScreen(viewModel = vm, onBack = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("automation_list").assertIsDisplayed()
    composeTestRule.onNodeWithTag("automation_add_button").assertIsDisplayed()
  }

  @Test
  fun `pre game check screen renders cleanly in robolectric`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = obtainMainVm(app)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          PreGameCheckScreen(viewModel = vm, onOpenTool = {}, onBack = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("pre_game_check_list").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pre_game_recheck_button").assertIsDisplayed()
  }

  @Test
  fun `touch response test screen renders 5 modes and interactive canvas`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          TouchResponseTestScreen(language = LanguageMode.ENGLISH, onBack = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("touch_response_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("multi_touch_canvas").assertExists()
    composeTestRule.onNodeWithTag("touch_test_reset_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("touch_mode_tap").assertIsDisplayed()
    composeTestRule.onNodeWithTag("touch_mode_multi_touch").assertIsDisplayed()
  }

  @Test
  fun `gaming focus mode screen renders cleanly in robolectric`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = obtainMainVm(app)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          GamingFocusModeScreen(viewModel = vm, onBack = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("gaming_focus_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("focus_mode_toggle_button").assertIsDisplayed()
  }

  @Test
  fun `pre game check modal keeps Play Anyway enabled even when warnings are present`() {
    var playAnywayClicked = false
    val warningReport =
      PreGameCheckReport(
        targetGameName = "Arena",
        targetPackageName = "com.game.arena",
        overallStatus = PreGameCheckStatus.WARNING,
        checkedAtMs = 1_000L,
        items =
          listOf(
            PreGameCheckItem(
              id = "thermal",
              title = "Thermal",
              valueText = "Moderate",
              status = PreGameCheckStatus.WARNING,
              detailNote = "Warm device",
            )
          ),
        summaryTip = "Warm advisory",
      )
    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          PreGameCheckModal(
            game = GameEntity(packageName = "com.game.arena", appName = "Arena", category = "moba"),
            report = warningReport,
            isIndo = false,
            onPlayAnyway = { playAnywayClicked = true },
            onDismiss = {},
            onDisablePrompt = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("pre_game_play_anyway_button").assertIsDisplayed().assertIsEnabled().performClick()
    assertTrue(playAnywayClicked)
  }

  private fun obtainDiagVm(app: Application): DrxDiagnosticsViewModel {
    val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app)
    return ViewModelProvider(ViewModelStore(), factory)[DrxDiagnosticsViewModel::class.java]
  }

  @Test
  fun `screenshot engine detects blank black bitmaps and creates unique filenames`() {
    val blackBmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
    blackBmp.eraseColor(Color.BLACK)
    assertTrue(ScreenshotEngine.isBlankOrBlackBitmap(blackBmp))
    blackBmp.recycle()

    val validBmp = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
    validBmp.eraseColor(Color.rgb(8, 123, 255))
    assertFalse(ScreenshotEngine.isBlankOrBlackBitmap(validBmp))
    validBmp.recycle()

    val pngName = ScreenshotEngine.buildUniqueScreenshotFilename(1_700_000_000_000L, ScreenshotFormat.PNG)
    val jpgName = ScreenshotEngine.buildUniqueScreenshotFilename(1_700_000_000_000L, ScreenshotFormat.JPEG)
    assertTrue(pngName.endsWith(".png"))
    assertTrue(jpgName.endsWith(".jpg"))
    assertTrue(pngName != jpgName)
  }

  @Test
  fun `thermal guard engine classifies 4 tiers accurately`() {
    assertEquals(ThermalTier.NORMAL, ThermalGuardEngine.classify(33.5f, 0))
    assertEquals(ThermalTier.WARM, ThermalGuardEngine.classify(37.5f, 1))
    assertEquals(ThermalTier.ELEVATED, ThermalGuardEngine.classify(40.5f, 2))
    assertEquals(ThermalTier.CRITICAL, ThermalGuardEngine.classify(44.0f, 3))
  }

  @Test
  fun `smart storage inspector computes and clears drx internal cache`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val testFile = File(context.cacheDir, "drx_test_cache_blob.tmp")
    testFile.writeBytes(ByteArray(4096) { 1 })
    assertTrue(SmartStorageInspectorEngine.computeDrxCacheBytes(context) >= 4096L)

    val cleared = SmartStorageInspectorEngine.clearDrxCache(context)
    assertTrue(cleared >= 4096L)
    assertFalse(testFile.exists())
  }

  @Test
  fun `game compatibility engine evaluates system and package checks`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tweaks = SystemTweaksManager(context)
    val report =
      GameCompatibilityEngine.evaluate(
        context = context,
        telemetry = sampleTelemetry(),
        permissionStates = emptyList(),
        games = listOf(GameEntity(packageName = context.packageName, appName = "Self", category = "other")),
        tweaksManager = tweaks,
        isIndo = false,
      )
    assertTrue(report.systemChecks.any { it.id == "android_os" })
    assertTrue(report.systemChecks.any { it.id == "display_panel" })
    assertTrue(report.gameChecks.size == 1)
    assertTrue(report.overallStatus in CompatibilityStatus.entries)
  }

  @Test
  fun `new v1_25_5 screens and boost overlay render cleanly in robolectric`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val mainVm = obtainMainVm(app)
    val diagVm = obtainDiagVm(app)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          ScreenshotScreen(diagnosticsViewModel = diagVm, onOpenGallery = {}, onBack = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("screenshot_studio_list").assertIsDisplayed()
    composeTestRule.onNodeWithTag("screenshot_capture_button").assertIsDisplayed()
  }

  @Test
  fun `thermal guard screen renders in robolectric`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val mainVm = obtainMainVm(app)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          ThermalGuardScreen(viewModel = mainVm, onBack = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("thermal_guard_list").assertIsDisplayed()
  }

  @Test
  fun `session recap screen renders in robolectric`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val mainVm = obtainMainVm(app)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          SessionRecapScreen(viewModel = mainVm, onOpenTimeline = {}, onOpenGallery = {}, onBack = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("session_recap_list").assertIsDisplayed()
  }

  @Test
  fun `smart storage inspector screen renders in robolectric`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val diagVm = obtainDiagVm(app)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          SmartStorageInspectorScreen(
            diagnosticsViewModel = diagVm,
            games = emptyList(),
            onOpenGallery = {},
            onOpenRecorder = {},
            onBack = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("storage_inspector_list").assertIsDisplayed()
  }

  @Test
  fun `game compatibility screen renders in robolectric`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val diagVm = obtainDiagVm(app)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          GameCompatibilityScreen(
            diagnosticsViewModel = diagVm,
            telemetry = sampleTelemetry(),
            permissionStates = emptyList(),
            games = emptyList(),
            onBack = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("compatibility_check_list").assertIsDisplayed()
  }

  @Test
  fun `boost animation overlay renders in robolectric`() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          DrxBoostAnimationOverlay(
            stageIndex = 1,
            isReadyToPlay = false,
            animationsEnabled = false,
            isIndo = false,
            onSkip = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("drx_boost_animation_overlay").assertIsDisplayed()
    composeTestRule.onNodeWithTag("drx_boost_skip_button").assertIsDisplayed()
  }
}
