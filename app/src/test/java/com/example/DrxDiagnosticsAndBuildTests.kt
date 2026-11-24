package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.BatteryManager
import android.os.PowerManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.CrashReportEntity
import com.example.data.db.DrxDatabase
import com.example.data.db.GameSessionEntity
import com.example.data.db.HealthCheckEntity
import com.example.data.db.LagReportEntity
import com.example.data.db.SessionReportEntity
import com.example.data.model.BatteryInfoData
import com.example.data.model.BitrateTier
import com.example.data.model.CpuInfo
import com.example.data.model.CrashExitType
import com.example.data.model.DisplayInfoData
import com.example.data.model.LagStatus
import com.example.data.model.MediaGalleryItem
import com.example.data.model.MediaItemType
import com.example.data.model.MemoryInfoData
import com.example.data.model.NetworkDiagnosticResult
import com.example.data.model.NetworkSnapshot
import com.example.data.model.RecordingResolution
import com.example.data.model.StorageInfoData
import com.example.data.model.SystemTelemetry
import com.example.i18n.EnglishStrings
import com.example.i18n.IndonesianStrings
import com.example.i18n.LocalStrings
import com.example.system.BuildDownloadManager
import com.example.system.CrashAnalyzerEngine
import com.example.system.DrxMediaStorageManager
import com.example.system.GamingHealthEngine
import com.example.system.LagDetectiveEngine
import com.example.system.MuxerTrackTimestampNormalizer
import com.example.system.ScreenRecordAudioMode
import com.example.system.ScreenRecorderService
import com.example.system.ScreenRecorderStateHolder
import com.example.system.SystemTweaksManager
import com.example.ui.screens.CrashAnalyzerScreen
import com.example.ui.screens.DiagnosticHistoryScreen
import com.example.ui.screens.DownloadCenterScreen
import com.example.ui.screens.GamingHealthScreen
import com.example.ui.screens.LagDetectiveScreen
import com.example.ui.screens.MediaGalleryScreen
import com.example.ui.screens.ScreenRecorderScreen
import com.example.ui.theme.DrxGameTurboTheme
import com.example.ui.viewmodel.DrxDiagnosticsViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class DrxDiagnosticsAndBuildTests {

  @get:Rule val composeTestRule = createComposeRule()

  private fun sampleTelemetry(
    availRamMb: Long = 2400L,
    totalRamMb: Long = 8192L,
    lowMem: Boolean = false,
    tempC: Float = 34.5f,
    thermalCode: Int = PowerManager.THERMAL_STATUS_NONE,
    freeStorageGb: Long = 16L,
    connected: Boolean = true,
    uiFps: Float = 60f,
    cpuUsage: Float? = null,
    cpuCurMhz: Int? = 1800,
    cpuMaxMhz: Int? = 2400,
  ): SystemTelemetry {
    return SystemTelemetry(
      cpuInfo = CpuInfo(cores = 8, usagePercent = cpuUsage, currentFreqMhz = cpuCurMhz, maxFreqMhz = cpuMaxMhz, abi = "arm64-v8a"),
      memoryInfo =
        MemoryInfoData(
          totalBytes = totalRamMb * 1024L * 1024L,
          availBytes = availRamMb * 1024L * 1024L,
          usedPercent = (((totalRamMb - availRamMb).toFloat() / totalRamMb.toFloat()) * 100f).coerceIn(0f, 100f),
          lowMemory = lowMem,
        ),
      batteryInfo =
        BatteryInfoData(
          levelPercent = 84,
          temperatureC = tempC,
          voltageMv = 4120,
          isCharging = false,
          chargeSource = "Battery",
          healthCode = BatteryManager.BATTERY_HEALTH_GOOD,
        ),
      storageInfo =
        StorageInfoData(
          totalBytes = 128L * 1024L * 1024L * 1024L,
          freeBytes = freeStorageGb * 1024L * 1024L * 1024L,
          usedPercent = 68f,
        ),
      displayInfo = DisplayInfoData(widthPx = 1080, heightPx = 2400, densityDpi = 420, refreshRateHz = 120f, supportedRefreshRates = listOf(60f, 120f), isHdr = true, screenTimeoutMs = 30000),
      networkSnapshot = NetworkSnapshot(isConnected = connected, transportType = if (connected) "Wi-Fi" else "Offline", networkSubType = "5GHz", qualityLevel = if (connected) 4 else 0),
      uiFps = uiFps,
      frameTimeMs = if (uiFps > 0f) 1000f / uiFps else 16.6f,
      thermalStatusCode = thermalCode,
    )
  }

  @Test
  fun `lag detective engine adapts thresholds detects memory thermal issues and exposes honest CPU disclosure`() {
    val engine = LagDetectiveEngine()
    val healthy = sampleTelemetry(availRamMb = 3200L, totalRamMb = 8192L, tempC = 33f, cpuUsage = null)
    val cfg = engine.calibrateThresholdConfig(healthy)
    assertEquals(8192L / 6L, cfg.ramWarningFreeMb)

    val stressed =
      sampleTelemetry(
        availRamMb = 420L,
        totalRamMb = 8192L,
        lowMem = true,
        tempC = 44.5f,
        thermalCode = PowerManager.THERMAL_STATUS_SEVERE,
        cpuUsage = null,
        cpuCurMhz = null,
        cpuMaxMhz = null,
      )
    val report =
      engine.analyze(
        telemetry = stressed,
        networkDiag = NetworkDiagnosticResult(host = "1.1.1.1", pingMs = 140f, jitterMs = 42f, packetLossPercent = 6f, downloadMbps = 12f, stabilityScore = 45, samplesMs = listOf(110f, 140f, 210f), networkType = "Wi-Fi"),
        recordingsFootprintBytes = 120L * 1024L * 1024L,
        thresholdConfig = cfg,
        isIndo = false,
      )

    assertEquals(LagStatus.HIGH_LOAD, report.overallStatus)
    assertTrue(report.findings.any { it.title.contains("Memory") && it.status == LagStatus.HIGH_LOAD })
    assertTrue(report.findings.any { it.title.contains("Thermal") && it.status == LagStatus.HIGH_LOAD })
    assertTrue(report.findings.any { it.status == LagStatus.UNSTABLE })
    assertTrue(report.completenessNote.contains("restricted by Android OS", ignoreCase = true))

    val shared = engine.formatShareReport(report, isIndo = true)
    assertTrue(shared.contains("LAPORAN DRX LAG DETECTIVE"))
  }

  @Test
  fun `crash analyzer engine classifies sessions honestly and redacts secrets from imported logs`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tweaks = SystemTweaksManager(context)
    val engine = CrashAnalyzerEngine(context, tweaks)

    val session =
      GameSessionEntity(
        gamePackageName = "com.mobile.legends",
        gameName = "Mobile Legends",
        startedAt = 1_000_000L,
        endedAt = 2_800_000L,
        durationMs = 1_800_000L,
        profileUsed = "performance",
        batteryStartPercent = 80,
        batteryEndPercent = 70,
        peakTempC = 39f,
      )

    val lowMemAnalysis =
      engine.analyzeGameExit(
        packageName = "com.mobile.legends",
        appName = "Mobile Legends",
        telemetry = sampleTelemetry(availRamMb = 350L, lowMem = true),
        recentSession = session,
        isIndo = false,
      )
    assertEquals(CrashExitType.PROCESS_NO_LONGER_DETECTED, lowMemAnalysis.exitType)
    assertTrue(lowMemAnalysis.preventionTips.isNotEmpty())

    val logFile = File(context.cacheDir, "sample_crash_log.txt").apply {
      writeText(
        """
        01-01 12:00:00.000 E/AndroidRuntime: FATAL EXCEPTION: main
        java.lang.OutOfMemoryError: Failed to allocate texture
        Error detail: user=player@example.com token=secret123456
        """.trimIndent()
      )
    }
    try {
      val summary = engine.parseImportedDiagnosticLog(Uri.fromFile(logFile), isIndo = false)
      assertTrue(summary.contains("FATAL EXCEPTION"))
      assertTrue(summary.contains("OutOfMemoryError"))
      assertTrue(summary.contains("[EMAIL]"))
      assertTrue(summary.contains("[REDACTED]"))
      assertFalse(summary.contains("player@example.com"))
      assertFalse(summary.contains("secret123456"))
    } finally {
      logFile.delete()
    }
  }

  @Test
  fun `gaming health engine scores 6 categories and reports Not Available when network probe is missing in full mode`() {
    val engine = GamingHealthEngine()
    val snap = sampleTelemetry(availRamMb = 4500L, totalRamMb = 8192L, tempC = 32f, freeStorageGb = 32L, connected = true)
    val fullNoProbe = engine.evaluateHealth(telemetry = snap, networkDiag = null, isFullDiagnostic = true, isIndo = false)
    assertEquals(6, fullNoProbe.categories.size)
    val netCat = fullNoProbe.categories.first { it.categoryId == "network" }
    assertNull(netCat.scoreOrNull)
    assertEquals("Not Available", netCat.statusLabel)

    val fullWithProbe =
      engine.evaluateHealth(
        telemetry = snap,
        networkDiag = NetworkDiagnosticResult(host = "1.1.1.1", pingMs = 28f, jitterMs = 3.2f, packetLossPercent = 0f, downloadMbps = 48f, stabilityScore = 94, samplesMs = listOf(22f, 28f, 35f), networkType = "Wi-Fi"),
        isFullDiagnostic = true,
        isIndo = false,
      )
    assertNotNull(fullWithProbe.overallScoreOrNull)
    assertTrue((fullWithProbe.overallScoreOrNull ?: 0) >= 80)
  }

  @Test
  fun `media storage manager computes storage summary and recorder enums resolve accurately`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val items =
      listOf(
        MediaGalleryItem("content://a/1", "shot1.png", MediaItemType.SCREENSHOT, 500_000L, 1000L),
        MediaGalleryItem("content://a/2", "clip1.mp4", MediaItemType.VIDEO, 15_000_000L, 2000L, durationSec = 25),
        MediaGalleryItem("content://a/3", "clip2.mp4", MediaItemType.VIDEO, 42_000_000L, 3000L, durationSec = 70),
      )
    val summary = DrxMediaStorageManager.computeStorageSummary(context, items)
    assertEquals(1, summary.screenshotCount)
    assertEquals(2, summary.videoCount)
    assertEquals(57_500_000L, summary.totalMediaBytes)
    assertEquals("clip2.mp4", summary.largestVideoName)
    assertEquals(42_000_000L, summary.largestVideoBytes)

    assertEquals(480, RecordingResolution.P480.presetHeight)
    assertEquals(720, RecordingResolution.P720.presetHeight)
    assertEquals(1080, RecordingResolution.P1080.presetHeight)
    assertEquals(0, RecordingResolution.ORIGINAL.presetHeight)
    assertEquals(3_000_000, BitrateTier.LOW.bps)
    assertEquals(6_000_000, BitrateTier.MEDIUM.bps)
    assertEquals(10_000_000, BitrateTier.HIGH.bps)

    ScreenRecorderStateHolder.updateConfig(
      resolutionPreset = 480,
      targetFps = 24,
      audioMode = ScreenRecordAudioMode.MIC_AUDIO,
      bitrateTier = BitrateTier.CUSTOM,
      customBitrateMbps = 14,
    )
    val st = ScreenRecorderStateHolder.state.value
    assertEquals(480, st.resolutionPreset)
    assertEquals(24, st.targetFps)
    assertEquals(14_000_000, ScreenRecorderService.resolveEffectiveBitrateBps(st))
  }

  @Test
  fun `build download manager validates intact apk zips and rejects corrupt files`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val mgr = BuildDownloadManager(context)
    val validZip = File(context.cacheDir, "valid_test.apk")
    val corruptZip = File(context.cacheDir, "corrupt_test.apk")

    try {
      ZipOutputStream(FileOutputStream(validZip)).use { zos ->
        listOf("AndroidManifest.xml", "classes.dex", "resources.arsc").forEachIndexed { idx, entryName ->
          zos.putNextEntry(ZipEntry(entryName))
          val payload = ByteArray(1200) { i -> (((i * 73) xor (i ushr 3) xor (idx * 31)) and 0xFF).toByte() }
          zos.write(payload)
          zos.closeEntry()
        }
      }
      assertTrue(mgr.validateZipArchive(validZip))
      assertEquals(16, mgr.computeSha256Short(validZip).length)

      corruptZip.writeBytes(byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44))
      assertFalse(mgr.validateZipArchive(corruptZip))

      val aab = mgr.buildAabInfoPlaceholder(isIndo = false)
      assertFalse(aab.downloadSupported)
      assertEquals(BuildDownloadManager.AAB_FILE_NAME, aab.fileName)
    } finally {
      validZip.delete()
      corruptZip.delete()
    }
  }

  @Test
  fun `room database version 3 persists lag crash health and session reports`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, DrxDatabase::class.java).allowMainThreadQueries().build()
    try {
      db.lagReportDao().insert(
        LagReportEntity(
          overallStatus = "WARNING",
          suspectedIssue = "Memory Pressure",
          severityLabel = "Moderate",
          confidencePercent = 84,
          evidenceSummary = "480 MB free",
          recommendation = "Close background apps",
        )
      )
      db.crashReportDao().insert(
        CrashReportEntity(
          gamePackageName = "com.test.game",
          gameName = "Arena Test",
          gameVersion = "1.0",
          exitClassification = "Possible Crash",
          sessionStartMs = 1000L,
          sessionEndMs = 5000L,
          ramFreeMb = 500L,
          ramTotalMb = 4096L,
          storageFreeBytes = 2_000_000_000L,
          batteryLevelPercent = 70,
          batteryTempC = 41.2f,
          networkStatus = "Wi-Fi",
          lastPerformanceSummary = "UI 58 FPS",
          diagnosticResult = "Abrupt exit under memory pressure",
          preventionTips = "Free RAM",
        )
      )
      db.healthCheckDao().insert(
        HealthCheckEntity(
          isFullDiagnostic = true,
          overallScoreOrNull = 88,
          readinessTitle = "GAMING READY",
          categorySummary = "Memory: 90",
          recommendations = "Keep balanced profile",
        )
      )
      db.sessionReportDao().insert(
        SessionReportEntity(
          gamePackageName = "com.test.game",
          gameName = "Arena Test",
          startedAtMs = 1000L,
          endedAtMs = 601_000L,
          durationMs = 600_000L,
          avgUiFpsOrNull = 60f,
          batteryStartPercent = 80,
          batteryEndPercent = 76,
          peakTempC = 37.5f,
          ramAvailEndMb = 1800L,
          networkSummary = "Wi-Fi",
          detectedConditions = "Normal",
          recommendations = "Good",
        )
      )

      assertEquals(1, db.lagReportDao().observeAll().first().size)
      assertEquals(1, db.crashReportDao().observeAll().first().size)
      assertEquals(1, db.healthCheckDao().observeAll().first().size)
      assertEquals(1, db.sessionReportDao().observeAll().first().size)
    } finally {
      db.close()
    }
  }

  @Test
  fun `lag detective crash analyzer health screen and download center render cleanly in robolectric`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DrxDiagnosticsViewModel(app)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          LagDetectiveScreen(
            diagnosticsViewModel = vm,
            onOpenHistory = {},
            onBack = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("DRX Lag Detective").assertIsDisplayed()
    composeTestRule.onNodeWithTag("lag_scan_button").assertIsDisplayed()
  }

  @Test
  fun `crash analyzer health recorder gallery history and download center compose cleanly`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DrxDiagnosticsViewModel(app)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides IndonesianStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          DownloadCenterScreen(diagnosticsViewModel = vm, onBack = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("download_prepare_apk_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("download_pipeline_pill").assertIsDisplayed()
  }

  @Test
  fun `recorder gallery history health and crash screens mount without errors`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DrxDiagnosticsViewModel(app)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          ScreenRecorderScreen(diagnosticsViewModel = vm, onOpenGallery = {}, onBack = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("DRX Pro Recorder").assertIsDisplayed()
    composeTestRule.onNodeWithTag("recorder_status_pill").assertIsDisplayed()
  }

  @Test
  fun `diagnostic history tabs switch cleanly`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DrxDiagnosticsViewModel(app)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          DiagnosticHistoryScreen(diagnosticsViewModel = vm, onBack = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("history_tab_crash").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("history_tab_health").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("history_tab_session").assertIsDisplayed().performClick()
  }

  @Test
  fun `gaming health screen renders quick and full check actions`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DrxDiagnosticsViewModel(app)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          GamingHealthScreen(diagnosticsViewModel = vm, onOpenHistory = {}, onBack = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("health_quick_check_button").assertIsDisplayed()
  }

  @Test
  fun `crash analyzer screen renders exit analysis and log import`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DrxDiagnosticsViewModel(app)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          CrashAnalyzerScreen(
            games = emptyList(),
            installedApps = emptyList(),
            sessions = emptyList(),
            diagnosticsViewModel = vm,
            onOpenHistory = {},
            onBack = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("crash_analyze_button").assertIsDisplayed()
  }

  @Test
  fun `media gallery screen renders storage summary and filters`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = DrxDiagnosticsViewModel(app)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          MediaGalleryScreen(diagnosticsViewModel = vm, onBack = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("DRX Media Hub").assertIsDisplayed()
  }

  @Test
  fun `muxer timestamp normalizer rebases 100h device uptime handles pause resume and guards clock jumps`() {
    val uptime105HoursUs = 105L * 3600L * 1_000_000L // 378,000,000,000 us (>100 hours)
    val videoNorm = MuxerTrackTimestampNormalizer(subtractPauseDelta = true)
    val audioNorm = MuxerTrackTimestampNormalizer(subtractPauseDelta = false)

    // First samples right as muxer starts
    val v0 = videoNorm.nextPtsUs(rawPtsUs = uptime105HoursUs, currentPausedUs = 0L, activeElapsedUs = 15_000L)
    val a0 = audioNorm.nextPtsUs(rawPtsUs = 12_000L, currentPausedUs = 0L, activeElapsedUs = 10_000L)
    assertEquals(15_000L, v0)
    assertEquals(10_000L, a0)

    // Active recording at 2.5s
    val v1 = videoNorm.nextPtsUs(rawPtsUs = uptime105HoursUs + 2_485_000L, currentPausedUs = 0L, activeElapsedUs = 2_500_000L)
    val a1 = audioNorm.nextPtsUs(rawPtsUs = 12_000L + 2_490_000L, currentPausedUs = 0L, activeElapsedUs = 2_500_000L)
    assertEquals(2_500_000L, v1)
    assertEquals(2_500_000L, a1)

    // User pauses for 12 seconds, then resumes and records another 2.5 seconds (total active = 5.0s)
    val paused12sUs = 12_000_000L
    val v2 = videoNorm.nextPtsUs(
      rawPtsUs = uptime105HoursUs + 2_485_000L + paused12sUs + 2_500_000L,
      currentPausedUs = paused12sUs,
      activeElapsedUs = 5_000_000L,
    )
    val a2 = audioNorm.nextPtsUs(
      rawPtsUs = 12_000L + 4_990_000L,
      currentPausedUs = paused12sUs,
      activeElapsedUs = 5_000_000L,
    )
    assertEquals(5_000_000L, v2)
    assertEquals(5_000_000L, a2)

    // Guard against quirky encoder emitting frame 0 = 0us and frame 1 = device uptime 105h+
    val jumpNorm = MuxerTrackTimestampNormalizer(subtractPauseDelta = true)
    assertEquals(0L, jumpNorm.nextPtsUs(rawPtsUs = 0L, currentPausedUs = 0L, activeElapsedUs = 0L))
    val reanchored = jumpNorm.nextPtsUs(rawPtsUs = uptime105HoursUs + 33_000L, currentPausedUs = 0L, activeElapsedUs = 33_000L)
    assertEquals(33_000L, reanchored)
    val afterReanchor = jumpNorm.nextPtsUs(rawPtsUs = uptime105HoursUs + 66_000L, currentPausedUs = 0L, activeElapsedUs = 66_000L)
    assertEquals(66_000L, afterReanchor)

    // Duration verification and formatting
    assertEquals("00:05", ScreenRecorderService.formatDuration(5))
    assertEquals("1:01:05", ScreenRecorderService.formatDuration(3665))
    assertEquals(
      5,
      ScreenRecorderService.resolveVerifiedDurationSec(
        containerDurationMs = 378_005_000L, // corrupt 105h container value
        muxedDurationMs = 5_100L,
        elapsedWallMs = 5_000L,
      ),
    )
  }
}
