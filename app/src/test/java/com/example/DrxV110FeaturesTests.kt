package com.example

import android.app.Application
import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.CrashReportEntity
import com.example.data.db.DrxDatabase
import com.example.data.db.GameEntity
import com.example.data.db.GameNotificationEntity
import com.example.data.db.GameSessionEntity
import com.example.data.db.StabilityWarningEntity
import com.example.data.model.BatteryInfoData
import com.example.data.model.CpuInfo
import com.example.data.model.DisplayInfoData
import com.example.data.model.GameEventCategory
import com.example.data.model.HudDisplayMode
import com.example.data.model.MemoryInfoData
import com.example.data.model.NetworkSnapshot
import com.example.data.model.NotificationDateRangeFilter
import com.example.data.model.NotificationReadFilter
import com.example.data.model.NotificationSortOrder
import com.example.data.model.QuickOverlayProfile
import com.example.data.model.StabilitySeverity
import com.example.data.model.StorageInfoData
import com.example.data.model.SystemGameUsageSummary
import com.example.data.model.SystemTelemetry
import com.example.data.model.UpdateManifest
import com.example.i18n.EnglishStrings
import com.example.i18n.LocalStrings
import com.example.system.GameNotificationEngine
import com.example.system.ManifestEvaluationStatus
import com.example.system.SessionIntelligenceEngine
import com.example.system.StabilityWatchEngine
import com.example.system.SystemAudioVideoMuxerEngine
import com.example.system.UpdatePolicyEngine
import com.example.ui.screens.SessionIntelligenceScreen
import com.example.ui.screens.StabilityWatchScreen
import com.example.ui.theme.DrxGameTurboTheme
import com.example.ui.viewmodel.DrxDiagnosticsViewModel
import com.example.ui.viewmodel.DrxMainViewModel
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class DrxV110FeaturesTests {

  @get:Rule val composeTestRule = createComposeRule()

  private val viewModelStore = ViewModelStore()

  @After
  fun cleanup() {
    viewModelStore.clear()
  }

  private fun sampleTelemetry(
    availRamMb: Long = 2600L,
    totalRamMb: Long = 8192L,
    lowMemory: Boolean = false,
    tempC: Float = 34.0f,
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
          usedPercent = (((totalRamMb - availRamMb).toFloat() / totalRamMb) * 100f).coerceIn(0f, 100f),
          lowMemory = lowMemory,
        ),
      batteryInfo =
        BatteryInfoData(
          levelPercent = 82,
          temperatureC = tempC,
          voltageMv = 4100,
          isCharging = false,
          chargeSource = "Battery",
          healthCode = BatteryManager.BATTERY_HEALTH_GOOD,
        ),
      storageInfo = StorageInfoData(totalBytes = 128L * 1024L * 1024L * 1024L, freeBytes = freeStorageBytes, usedPercent = 70f),
      displayInfo = DisplayInfoData(1080, 2400, 420, 120f, listOf(60f, 120f), true, 30000),
      networkSnapshot = NetworkSnapshot(isConnected = isConnected, transportType = if (isConnected) "Wi-Fi" else "Offline", networkSubType = "5GHz", qualityLevel = qualityLevel),
      uiFps = 60f,
      frameTimeMs = 16.6f,
      thermalStatusCode = thermalCode,
    )

  @Test
  fun `quick overlay profiles map accurate polling intervals and hud modes`() {
    assertEquals(1500L, QuickOverlayProfile.BALANCED.pollingIntervalMs)
    assertEquals(HudDisplayMode.STANDARD, QuickOverlayProfile.BALANCED.hudMode)

    assertEquals(750L, QuickOverlayProfile.PERFORMANCE_MONITORING.pollingIntervalMs)
    assertEquals(HudDisplayMode.FULL, QuickOverlayProfile.PERFORMANCE_MONITORING.hudMode)

    assertEquals(3000L, QuickOverlayProfile.BATTERY_SAVER.pollingIntervalMs)
    assertEquals(HudDisplayMode.MINI, QuickOverlayProfile.BATTERY_SAVER.hudMode)
    assertFalse(QuickOverlayProfile.BATTERY_SAVER.preferAnimations)

    assertEquals(2000L, QuickOverlayProfile.MINIMAL_OVERLAY.pollingIntervalMs)
    assertEquals(HudDisplayMode.MINI, QuickOverlayProfile.MINIMAL_OVERLAY.hudMode)
    assertEquals(QuickOverlayProfile.PERFORMANCE_MONITORING, QuickOverlayProfile.fromId("performance_monitoring"))
    assertEquals(QuickOverlayProfile.BALANCED, QuickOverlayProfile.fromId("unknown_id"))
  }

  @Test
  fun `session intelligence computes stats, comparisons, buckets, and export report`() {
    val now = 1_760_000_000_000L // deterministic epoch ms
    val games =
      listOf(
        GameEntity("com.game.alpha", "Alpha Arena", "FPS", totalPlayTimeMs = 3_600_000L, lastPlayedAt = now - 3600_000L),
        GameEntity("com.game.beta", "Beta Racing", "RACING", totalPlayTimeMs = 1_800_000L, lastPlayedAt = now - 86_400_000L),
      )
    val s1 =
      GameSessionEntity(
        id = 1L,
        gamePackageName = "com.game.alpha",
        gameName = "Alpha Arena",
        startedAt = now - 7_200_000L,
        endedAt = now - 3_600_000L,
        durationMs = 3_600_000L,
        profileUsed = "performance",
        batteryStartPercent = 85,
        batteryEndPercent = 73,
        peakTempC = 41.2f,
      )
    val s2 =
      GameSessionEntity(
        id = 2L,
        gamePackageName = "com.game.beta",
        gameName = "Beta Racing",
        startedAt = now - 88_200_000L,
        endedAt = now - 86_400_000L,
        durationMs = 1_800_000L,
        profileUsed = "balanced",
        batteryStartPercent = 90,
        batteryEndPercent = 85,
        peakTempC = 37.0f,
      )
    val sysMap = mapOf("com.game.alpha" to SystemGameUsageSummary("com.game.alpha", hasPermission = true, lastTimeUsedMs = now - 3600_000L))

    val emptySummary = SessionIntelligenceEngine.computeSummary(emptyList(), emptyList(), emptyMap(), nowMs = now)
    assertEquals(0L, emptySummary.totalGamingTimeMs)
    assertEquals(7, emptySummary.dailyChart7d.size)
    assertEquals(4, emptySummary.weeklyChart4w.size)

    val summary =
      SessionIntelligenceEngine.computeSummary(
        games = games,
        sessions = listOf(s1, s2),
        systemUsageByPkg = sysMap,
        nowMs = now,
        isIndo = false,
        timeZone = TimeZone.getTimeZone("UTC"),
      )
    assertEquals(5_400_000L, summary.totalGamingTimeMs)
    assertEquals(2, summary.totalSessionsCount)
    assertEquals(2_700_000L, summary.averageSessionDurationMs)
    assertEquals(3_600_000L, summary.longestSessionDurationMs)
    assertEquals("Alpha Arena", summary.mostPlayedGameName)
    assertEquals(7, summary.dailyChart7d.size)
    assertEquals(4, summary.weeklyChart4w.size)

    val comparison = SessionIntelligenceEngine.compareSessions(s1, s2, isIndo = false)
    assertEquals(4, comparison.size)
    assertEquals("+30m", comparison[0].deltaText)
    assertEquals("+7%", comparison[1].deltaText)

    val report = SessionIntelligenceEngine.formatExportReport(summary, listOf(s1, s2), isIndo = false)
    assertTrue(report.contains("DRX GAMING SESSION INTELLIGENCE REPORT"))
    assertTrue(report.contains("Alpha Arena"))
    assertTrue(report.contains("Beta Racing"))
  }

  @Test
  fun `stability watch evaluates normal, low ram, high thermal, and low storage states`() {
    val normal =
      StabilityWatchEngine.evaluate(
        telemetry = sampleTelemetry(),
        recentCrashes = emptyList(),
        isIndo = false,
      )
    assertEquals(StabilitySeverity.NORMAL, normal.overallSeverity)
    assertTrue(normal.warnings.isEmpty())

    val critical =
      StabilityWatchEngine.evaluate(
        telemetry =
          sampleTelemetry(
            availRamMb = 350L,
            lowMemory = true,
            tempC = 46.5f,
            thermalCode = PowerManager.THERMAL_STATUS_SEVERE,
            freeStorageBytes = 300L * 1024L * 1024L,
            isConnected = false,
            qualityLevel = 0,
          ),
        recentCrashes =
          listOf(
            CrashReportEntity(
              gamePackageName = "com.game.a",
              gameName = "Game A",
              gameVersion = "1.0",
              exitClassification = "CRASH_SUSPECTED",
              sessionStartMs = 1L,
              sessionEndMs = 2L,
              ramFreeMb = 350L,
              ramTotalMb = 8192L,
              storageFreeBytes = 300_000_000L,
              batteryLevelPercent = 50,
              batteryTempC = 45f,
              networkStatus = "Offline",
              lastPerformanceSummary = "Low RAM",
              diagnosticResult = "OOM",
              preventionTips = "Free RAM",
            ),
            CrashReportEntity(
              gamePackageName = "com.game.a",
              gameName = "Game A",
              gameVersion = "1.0",
              exitClassification = "PROCESS_KILLED",
              sessionStartMs = 1L,
              sessionEndMs = 2L,
              ramFreeMb = 350L,
              ramTotalMb = 8192L,
              storageFreeBytes = 300_000_000L,
              batteryLevelPercent = 50,
              batteryTempC = 45f,
              networkStatus = "Offline",
              lastPerformanceSummary = "Low RAM",
              diagnosticResult = "LMK",
              preventionTips = "Free RAM",
            ),
          ),
        isIndo = false,
      )
    assertEquals(StabilitySeverity.CRITICAL, critical.overallSeverity)
    assertTrue(critical.warnings.any { it.categoryId == "THERMAL" })
    assertTrue(critical.warnings.any { it.categoryId == "MEMORY" })
    assertTrue(critical.warnings.any { it.categoryId == "STORAGE" })
    assertTrue(critical.warnings.any { it.categoryId == "NETWORK" })
    assertTrue(critical.warnings.any { it.categoryId == "SESSION_ANOMALY" })

    val entities = StabilityWatchEngine.toEntities(critical)
    assertEquals(critical.warnings.size, entities.size)
    val export = StabilityWatchEngine.formatExportReport(critical, entities, isIndo = false)
    assertTrue(export.contains("DRX GAME STABILITY WATCH REPORT"))
  }

  @Test
  fun `room migration 4 to 5 creates stability_warnings table and dao crud works`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // Verify MIGRATION_4_5 directly on SQLite schema
    val dbFile = context.getDatabasePath("test_migration_4_5.db")
    dbFile.delete()
    val helper =
      FrameworkSQLiteOpenHelperFactory().create(
        androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
          .name(dbFile.absolutePath)
          .callback(
            object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
              override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS dummy_v4 (`id` INTEGER PRIMARY KEY)")
              }

              override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }
          )
          .build()
      )
    val rawDb = helper.writableDatabase
    DrxDatabase.MIGRATION_4_5.migrate(rawDb)
    rawDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='stability_warnings'").use { c ->
      assertTrue(c.moveToFirst())
      assertEquals("stability_warnings", c.getString(0))
    }
    rawDb.close()
    dbFile.delete()

    // Test StabilityWarningDao via in-memory DrxDatabase
    val db = Room.inMemoryDatabaseBuilder(context, DrxDatabase::class.java).allowMainThreadQueries().build()
    try {
      val id =
        db.stabilityWarningDao().insert(
          StabilityWarningEntity(
            categoryId = "THERMAL",
            severity = "WARNING",
            title = "PERFORMANCE WARNING",
            subtitle = "Possible Thermal Pressure",
            explanation = "Battery heated",
            recommendation = "Cool down",
            metricEvidence = "Battery=41.5°C",
            ramAvailMb = 2200L,
            batteryTempC = 41.5f,
            thermalStatusCode = PowerManager.THERMAL_STATUS_MODERATE,
            networkSummary = "Wi-Fi",
          )
        )
      assertTrue(id > 0L)
      assertEquals(1, db.stabilityWarningDao().observeAll().first().size)
      db.stabilityWarningDao().deleteById(id)
      assertEquals(0, db.stabilityWarningDao().observeAll().first().size)

      db.stabilityWarningDao().insert(
        StabilityWarningEntity(
          categoryId = "MEMORY",
          severity = "CRITICAL",
          title = "MEMORY WARNING",
          subtitle = "Low RAM",
          explanation = "Low",
          recommendation = "Close apps",
          metricEvidence = "FreeRAM=300MB",
          ramAvailMb = 300L,
          batteryTempC = 34f,
          thermalStatusCode = PowerManager.THERMAL_STATUS_NONE,
          networkSummary = "Wi-Fi",
        )
      )
      db.stabilityWarningDao().clearAll()
      assertEquals(0, db.stabilityWarningDao().observeAll().first().size)
    } finally {
      db.close()
    }
  }

  @Test
  fun `notification date range filters select today, yesterday, and 7 days accurately`() {
    val now = 1_760_050_000_000L
    val todayKey = GameNotificationEngine.formatDateKey(now)
    val yesterdayKey = GameNotificationEngine.formatDateKey(now - 86_400_000L)
    val fiveDaysKey = GameNotificationEngine.formatDateKey(now - 5L * 86_400_000L)
    val twentyDaysKey = GameNotificationEngine.formatDateKey(now - 20L * 86_400_000L)

    val items =
      listOf(
        GameNotificationEntity(1L, "k1", "com.g", "Game", "Today Event", "Body", timestampMs = now, dateKey = todayKey, eventCategory = GameEventCategory.EVENT.id),
        GameNotificationEntity(2L, "k2", "com.g", "Game", "Yesterday Reward", "Body", timestampMs = now - 86_400_000L, dateKey = yesterdayKey, eventCategory = GameEventCategory.REWARD.id),
        GameNotificationEntity(3L, "k3", "com.g", "Game", "5d Update", "Body", timestampMs = now - 5L * 86_400_000L, dateKey = fiveDaysKey, eventCategory = GameEventCategory.UPDATE.id),
        GameNotificationEntity(4L, "k4", "com.g", "Game", "20d Old", "Body", timestampMs = now - 20L * 86_400_000L, dateKey = twentyDaysKey, eventCategory = GameEventCategory.GENERAL.id),
      )

    val todayList =
      GameNotificationEngine.filterAndSort(
        items = items,
        query = "",
        selectedPackage = null,
        selectedDateKey = null,
        selectedCategory = null,
        readFilter = NotificationReadFilter.ALL,
        sortOrder = NotificationSortOrder.NEWEST,
        dateRangeFilter = NotificationDateRangeFilter.TODAY,
        nowMs = now,
      )
    assertEquals(listOf(1L), todayList.map { it.id })

    val yesterdayList =
      GameNotificationEngine.filterAndSort(
        items = items,
        query = "",
        selectedPackage = null,
        selectedDateKey = null,
        selectedCategory = null,
        readFilter = NotificationReadFilter.ALL,
        sortOrder = NotificationSortOrder.NEWEST,
        dateRangeFilter = NotificationDateRangeFilter.YESTERDAY,
        nowMs = now,
      )
    assertEquals(listOf(2L), yesterdayList.map { it.id })

    val sevenDaysList =
      GameNotificationEngine.filterAndSort(
        items = items,
        query = "",
        selectedPackage = null,
        selectedDateKey = null,
        selectedCategory = null,
        readFilter = NotificationReadFilter.ALL,
        sortOrder = NotificationSortOrder.NEWEST,
        dateRangeFilter = NotificationDateRangeFilter.DAYS_7,
        nowMs = now,
      )
    assertEquals(listOf(1L, 2L, 3L), sevenDaysList.map { it.id })
  }

  @Test
  fun `update policy resolves relative download urls against manifest url and enforces versionCode gating`() {
    val rawList =
      listOf(
        UpdateManifest(
          versionName = "1.11.0",
          versionCode = BuildConfig.VERSION_CODE + 1,
          releaseDate = "2026-12-05",
          releaseNotes = "Next release",
          apkType = "release",
          downloadUrl = "",
          localFileName = "DRX-GAME-TURBO-PRO-v1.11.0.apk",
        )
      )
    val resolved =
      UpdatePolicyEngine.resolveDownloadUrlsAgainstManifest(
        manifests = rawList,
        manifestUrl = "https://updates.example.com/drx/update-manifest.json",
      )
    assertEquals(
      "https://updates.example.com/drx/DRX-GAME-TURBO-PRO-v1.11.0.apk",
      resolved.first().downloadUrl,
    )

    val evalHigher =
      UpdatePolicyEngine.evaluateManifestAgainstInstalled(
        manifest = resolved.first(),
        installedPackageName = BuildConfig.APPLICATION_ID,
        installedVersionCode = BuildConfig.VERSION_CODE,
        hasRemoteSourceConfigured = true,
        isOnline = true,
      )
    assertEquals(ManifestEvaluationStatus.UPDATE_AVAILABLE, evalHigher.status)
    assertTrue(evalHigher.isInstallableUpdate)

    val evalEqual =
      UpdatePolicyEngine.evaluateManifestAgainstInstalled(
        manifest = resolved.first().copy(versionCode = BuildConfig.VERSION_CODE),
        installedPackageName = BuildConfig.APPLICATION_ID,
        installedVersionCode = BuildConfig.VERSION_CODE,
        hasRemoteSourceConfigured = true,
        isOnline = true,
      )
    assertEquals(ManifestEvaluationStatus.UP_TO_DATE, evalEqual.status)
    assertFalse(evalEqual.isInstallableUpdate)
  }

  @Test
  fun `muxer normalized rms outputs 0 on silence and non-zero bounded float on real pcm audio`() {
    val silent = ByteArray(1024)
    assertEquals(0f, SystemAudioVideoMuxerEngine.computeNormalizedRms(silent, silent.size), 0.0001f)
    assertEquals(0f, SystemAudioVideoMuxerEngine.computeNormalizedRms(silent, 0), 0.0001f)

    // Encode 16-bit LE PCM samples around amplitude 6000
    val signal = ByteArray(1024)
    var i = 0
    while (i + 1 < signal.size) {
      val sample: Short = if ((i / 2) % 2 == 0) 6000 else -6000
      signal[i] = (sample.toInt() and 0xFF).toByte()
      signal[i + 1] = ((sample.toInt() ushr 8) and 0xFF).toByte()
      i += 2
    }
    val rms = SystemAudioVideoMuxerEngine.computeNormalizedRms(signal, signal.size)
    assertTrue("Expected positive bounded RMS, got $rms", rms > 0.1f && rms <= 1.0f)
  }

  @Test
  fun `session intelligence and stability watch screens compose cleanly in robolectric`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val mainVm = ViewModelProvider(viewModelStore, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[DrxMainViewModel::class.java]
    val diagVm = ViewModelProvider(viewModelStore, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[DrxDiagnosticsViewModel::class.java]

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          SessionIntelligenceScreen(viewModel = mainVm, onOpenGameStats = {}, onBack = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Gaming Session Intelligence").assertIsDisplayed()
    composeTestRule.onNodeWithTag("session_share_report_btn").assertIsDisplayed()
    composeTestRule.onNodeWithTag("session_save_txt_btn").assertIsDisplayed()
    composeTestRule.onNodeWithTag("session_kpi_card").assertIsDisplayed()
  }

  @Test
  fun `stability watch screen displays status card and actions`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val diagVm = ViewModelProvider(viewModelStore, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[DrxDiagnosticsViewModel::class.java]

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          StabilityWatchScreen(diagnosticsViewModel = diagVm, onBack = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Game Stability Watch").assertIsDisplayed()
    composeTestRule.onNodeWithTag("stability_status_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("stability_run_check_btn").assertIsDisplayed()
    assertNotNull(diagVm.stabilityWarnings)
  }
}
