package com.example

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.data.db.DrxDatabase
import com.example.data.db.GameEventReminderEntity
import com.example.data.db.GameNotificationEntity
import com.example.data.model.DrxUpdateCatalog
import com.example.data.model.DrxUpdatePipelineState
import com.example.data.model.GameEventCategory
import com.example.data.model.GlobalGamingStatistics
import com.example.data.model.NotificationReadFilter
import com.example.data.model.NotificationSortOrder
import com.example.data.model.UpdateManifest
import com.example.data.model.UpdateOfferPolicyState
import com.example.i18n.EnglishStrings
import com.example.i18n.IndonesianStrings
import com.example.i18n.LocalStrings
import com.example.system.DrxGameNotificationListenerService
import com.example.system.DrxUpdateManager
import com.example.system.EventReminderWorker
import com.example.system.GameNotificationEngine
import com.example.system.UpdatePolicyEngine
import com.example.ui.screens.GameActivityCenterScreen
import com.example.ui.screens.GameEventTrackerScreen
import com.example.ui.screens.GameNotificationViewerScreen
import com.example.ui.screens.NotificationInsightsScreen
import com.example.ui.screens.SmartUpdateOfferModal
import com.example.ui.screens.UpdateCenterScreen
import com.example.ui.theme.DrxGameTurboTheme
import com.example.ui.viewmodel.DrxMainViewModel
import com.example.ui.viewmodel.DrxNotificationsViewModel
import com.example.ui.viewmodel.DrxUpdateUiState
import com.example.ui.viewmodel.DrxUpdateViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
class DrxNotificationAndUpdateTests {

  @get:Rule val composeTestRule = createComposeRule()

  private val testViewModelStore = ViewModelStore()

  private fun <T : ViewModel> obtainVm(app: Application, clazz: Class<T>): T =
    ViewModelProvider(testViewModelStore, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[clazz]

  @After
  fun tearDownViewModels() {
    testViewModelStore.clear()
  }

  @Test
  fun `version 1_20_0 build config and update catalog are synchronized`() {
    assertEquals("1.20.0", BuildConfig.VERSION_NAME)
    assertEquals(7, BuildConfig.VERSION_CODE)
    assertEquals("com.derrex.gameturbo", BuildConfig.APPLICATION_ID)
    assertEquals("1.20.0", DrxUpdateCatalog.latestVersion)
    assertEquals("1.20.0", DrxUpdateCatalog.entries.first().versionNumber)
  }

  @Test
  fun `game notification engine filters games blocks non-games and respects user overrides`() {
    val ownPkg = "com.derrex.gameturbo"
    val gameAppInfo = ApplicationInfo().apply { category = ApplicationInfo.CATEGORY_GAME }
    val socialAppInfo = ApplicationInfo().apply { category = ApplicationInfo.CATEGORY_SOCIAL }

    assertFalse(
      GameNotificationEngine.isGamePackage(
        packageName = ownPkg,
        ownPackageName = ownPkg,
        appInfo = gameAppInfo,
        trackedGamePackages = setOf(ownPkg),
        customIncludedPackages = setOf(ownPkg),
        excludedPackages = emptySet(),
      )
    )

    assertFalse(
      GameNotificationEngine.isGamePackage(
        packageName = "com.whatsapp",
        ownPackageName = ownPkg,
        appInfo = socialAppInfo,
        trackedGamePackages = emptySet(),
        customIncludedPackages = emptySet(),
        excludedPackages = emptySet(),
      )
    )

    assertTrue(
      GameNotificationEngine.isGamePackage(
        packageName = "com.mobile.legends",
        ownPackageName = ownPkg,
        appInfo = gameAppInfo,
        trackedGamePackages = emptySet(),
        customIncludedPackages = emptySet(),
        excludedPackages = emptySet(),
      )
    )

    assertFalse(
      GameNotificationEngine.isGamePackage(
        packageName = "com.mobile.legends",
        ownPackageName = ownPkg,
        appInfo = gameAppInfo,
        trackedGamePackages = setOf("com.mobile.legends"),
        customIncludedPackages = setOf("com.mobile.legends"),
        excludedPackages = setOf("com.mobile.legends"),
      )
    )

    assertTrue(
      GameNotificationEngine.isGamePackage(
        packageName = "com.custom.companion.tool",
        ownPackageName = ownPkg,
        appInfo = null,
        trackedGamePackages = emptySet(),
        customIncludedPackages = setOf("com.custom.companion.tool"),
        excludedPackages = emptySet(),
      )
    )
  }

  @Test
  fun `sensitive otp password and token notifications are skipped or redacted`() {
    assertTrue(
      GameNotificationEngine.shouldSkipSensitiveNotification(
        title = "Account Verification",
        text = "Your OTP code is 482910. Do not share.",
        bigText = "",
      )
    )
    assertTrue(
      GameNotificationEngine.shouldSkipSensitiveNotification(
        title = "Keamanan Akun",
        text = "Kode verifikasi login 991240",
        bigText = "",
      )
    )
    assertFalse(
      GameNotificationEngine.shouldSkipSensitiveNotification(
        title = "Summer Festival",
        text = "Claim 1000 bonus tickets in the lobby event today!",
        bigText = "",
      )
    )

    val cleaned =
      GameNotificationEngine.sanitizeNotificationContent(
        "Session update password=mySecretPass12 token=abc123xyz kode 449812 event active"
      )
    assertFalse(cleaned.contains("mySecretPass12"))
    assertFalse(cleaned.contains("abc123xyz"))
    assertFalse(cleaned.contains("449812"))
    assertTrue(cleaned.contains("[REDACTED]"))
  }

  @Test
  fun `event category classifier filter sort and insights compute real summaries`() {
    assertEquals(
      GameEventCategory.MAINTENANCE,
      GameNotificationEngine.classifyEventCategory("Server Maintenance", "Scheduled downtime 2 hours", ""),
    )
    assertEquals(
      GameEventCategory.TOURNAMENT,
      GameNotificationEngine.classifyEventCategory("MPL Championship", "Qualifier bracket starts tonight", ""),
    )
    assertEquals(
      GameEventCategory.REWARD,
      GameNotificationEngine.classifyEventCategory("Daily Bonus", "Claim free skin and login bonus reward", ""),
    )
    assertEquals(
      GameEventCategory.UPDATE,
      GameNotificationEngine.classifyEventCategory("Patch Notes v2.4", "New version & new hero season pass", ""),
    )
    assertEquals(
      GameEventCategory.REMINDER,
      GameNotificationEngine.classifyEventCategory("Event Ending Soon", "Jangan lupa klaim sebelum berakhir dalam 1 jam", ""),
    )

    val emptySummary = GameNotificationEngine.computeInsights(emptyList())
    assertEquals(0, emptySummary.totalCount)
    assertNull(emptySummary.mostActiveGameName)
    assertNull(emptySummary.mostActiveTimeRange)

    val cal = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 20)
      set(Calendar.MINUTE, 15)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val todayAt20 = cal.timeInMillis
    val todayKey = GameNotificationEngine.formatDateKey(todayAt20)

    val sample =
      listOf(
        GameNotificationEntity(
          id = 1L,
          notificationKey = "k1",
          packageName = "com.mobile.legends",
          gameName = "Mobile Legends",
          title = "Daily Reward",
          text = "Claim free skin",
          timestampMs = todayAt20,
          dateKey = todayKey,
          isRead = false,
          isFavorite = true,
          eventCategory = GameEventCategory.REWARD.id,
        ),
        GameNotificationEntity(
          id = 2L,
          notificationKey = "k2",
          packageName = "com.mobile.legends",
          gameName = "Mobile Legends",
          title = "Maintenance",
          text = "Scheduled downtime",
          timestampMs = todayAt20 - 30_000L,
          dateKey = todayKey,
          isRead = true,
          isImportant = true,
          eventCategory = GameEventCategory.MAINTENANCE.id,
        ),
        GameNotificationEntity(
          id = 3L,
          notificationKey = "k3",
          packageName = "com.tencent.ig",
          gameName = "PUBG MOBILE",
          title = "Tournament",
          text = "Esports qualifier open",
          timestampMs = todayAt20 - 8 * 86_400_000L,
          dateKey = GameNotificationEngine.formatDateKey(todayAt20 - 8 * 86_400_000L),
          isRead = false,
          eventCategory = GameEventCategory.TOURNAMENT.id,
        ),
      )

    val unreadOnly =
      GameNotificationEngine.filterAndSort(
        items = sample,
        query = "",
        selectedPackage = null,
        selectedDateKey = null,
        selectedCategory = null,
        readFilter = NotificationReadFilter.UNREAD,
        sortOrder = NotificationSortOrder.NEWEST,
      )
    assertEquals(2, unreadOnly.size)
    assertEquals(1L, unreadOnly.first().id)

    val insights = GameNotificationEngine.computeInsights(sample, nowMs = todayAt20)
    assertEquals(3, insights.totalCount)
    assertEquals(2, insights.unreadCount)
    assertEquals(2, insights.todayCount)
    assertEquals("Mobile Legends", insights.mostActiveGameName)
    assertEquals(2, insights.mostActiveGameCount)
    assertEquals("18:00–20:59", insights.mostActiveTimeRange)
    assertEquals(7, insights.daily7dChart.size)
    assertEquals(4, insights.weekly4wChart.size)
  }

  @Test
  fun `room database v4 persists notifications reminders and listener check fails safely when denied`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertFalse(DrxGameNotificationListenerService.isNotificationAccessGranted(context))

    val db = Room.inMemoryDatabaseBuilder(context, DrxDatabase::class.java).allowMainThreadQueries().build()
    try {
      val id =
        db.gameNotificationDao().insertOrReplace(
          GameNotificationEntity(
            notificationKey = "pkg|1",
            packageName = "com.mobile.legends",
            gameName = "Mobile Legends",
            title = "Hero Event",
            text = "Limited time event",
            timestampMs = 1_700_000_000_000L,
            dateKey = "2026-11-23",
            eventCategory = "EVENT",
          )
        )
      assertTrue(id > 0L)
      db.gameNotificationDao().setRead(id, true)
      db.gameNotificationDao().setFavorite(id, true)
      val loaded = db.gameNotificationDao().observeAll().first()
      assertEquals(1, loaded.size)
      assertTrue(loaded.first().isRead)
      assertTrue(loaded.first().isFavorite)

      val remId =
        db.gameEventReminderDao().insert(
          GameEventReminderEntity(
            notificationId = id,
            packageName = "com.mobile.legends",
            gameName = "Mobile Legends",
            title = "Hero Event",
            eventCategory = "EVENT",
            remindAtMs = System.currentTimeMillis() + 600_000L,
            workRequestId = "test-uuid",
          )
        )
      assertTrue(remId > 0L)
      assertEquals(1, db.gameEventReminderDao().observeAll().first().size)
      db.gameEventReminderDao().markCompleted(remId)
      assertTrue(db.gameEventReminderDao().observeAll().first().first().isCompleted)
    } finally {
      db.close()
    }
  }

  @Test
  fun `workmanager schedules and cancels event reminders cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    runCatching { WorkManager.initialize(context, Configuration.Builder().build()) }

    val entity =
      GameEventReminderEntity(
        id = 77L,
        notificationId = 12L,
        packageName = "com.mobile.legends",
        gameName = "Mobile Legends",
        title = "Claim Daily Login",
        eventCategory = "REWARD",
        remindAtMs = System.currentTimeMillis() + 900_000L,
        workRequestId = "",
      )
    val workId = EventReminderWorker.scheduleReminder(context, entity, 900_000L)
    assertTrue(workId.isNotBlank())
    EventReminderWorker.cancelReminder(context, workId)
  }

  @Test
  fun `update policy engine prioritizes release apk evaluates 3-strike skip policy and validates apk files`() {
    val debugOld = UpdateManifest("1.9.0", 5, "2026-11-23", "", "debug", "", "DRX-DEBUG.apk")
    val releaseNew = UpdateManifest("2.0.0", 6, "2026-12-01", "New", "release", "", "DRX-v2.0.0.apk")
    val debugNew = UpdateManifest("2.0.0", 6, "2026-12-01", "Debug", "debug", "", "DRX-DEBUG-v2.0.0.apk")

    val best = UpdatePolicyEngine.selectBestManifest(listOf(debugOld, debugNew, releaseNew))
    assertNotNull(best)
    assertEquals("release", best?.apkType)
    assertEquals(6, best?.versionCode)

    assertEquals(
      UpdateOfferPolicyState.NONE,
      UpdatePolicyEngine.evaluateSkipPolicy(currentVersionCode = 6, targetVersionCode = 6, lastOfferedVersionCode = 6, skipCount = 2),
    )
    assertEquals(
      UpdateOfferPolicyState.OPTIONAL_OFFER_1,
      UpdatePolicyEngine.evaluateSkipPolicy(currentVersionCode = 5, targetVersionCode = 6, lastOfferedVersionCode = 0, skipCount = 0),
    )
    assertEquals(
      UpdateOfferPolicyState.OPTIONAL_OFFER_2,
      UpdatePolicyEngine.evaluateSkipPolicy(currentVersionCode = 5, targetVersionCode = 6, lastOfferedVersionCode = 6, skipCount = 1),
    )
    assertEquals(
      UpdateOfferPolicyState.MANDATORY_REQUIRED,
      UpdatePolicyEngine.evaluateSkipPolicy(currentVersionCode = 5, targetVersionCode = 6, lastOfferedVersionCode = 6, skipCount = 2),
    )
    // Reset when a newer versionCode 7 arrives
    assertEquals(
      UpdateOfferPolicyState.OPTIONAL_OFFER_1,
      UpdatePolicyEngine.evaluateSkipPolicy(currentVersionCode = 5, targetVersionCode = 7, lastOfferedVersionCode = 6, skipCount = 2),
    )

    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = DrxUpdateManager(context)
    val bundled = manager.loadBundledManifests()
    assertTrue(bundled.isNotEmpty())
    assertEquals("1.20.0", bundled.first().versionName)

    val invalidFile = File(context.cacheDir, "invalid_update.apk")
    val incompleteZip = File(context.cacheDir, "incomplete_update.apk")
    try {
      invalidFile.writeText("not an apk")
      val badResult = manager.verifyApkFile(invalidFile)
      assertFalse(badResult.isValid)

      ZipOutputStream(FileOutputStream(incompleteZip)).use { zos ->
        zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
        zos.write(ByteArray(1400) { 0x1A })
        zos.closeEntry()
      }
      val noMetaResult = manager.verifyApkFile(incompleteZip)
      assertFalse(noMetaResult.isValid)
    } finally {
      invalidFile.delete()
      incompleteZip.delete()
    }
  }

  @Test
  fun `notification viewer insights event tracker activity center and update screens render in robolectric`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val mainVm = obtainVm(app, DrxMainViewModel::class.java)
    val notifVm = obtainVm(app, DrxNotificationsViewModel::class.java)
    obtainVm(app, DrxUpdateViewModel::class.java)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides IndonesianStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          GameNotificationViewerScreen(
            notificationsViewModel = notifVm,
            mainViewModel = mainVm,
            installedApps = emptyList(),
            onOpenInsights = {},
            onOpenEvents = {},
            onBack = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("game_notifications_list").assertIsDisplayed()
    composeTestRule.onNodeWithTag("notif_tab_by_date").assertIsDisplayed()
    composeTestRule.onNodeWithTag("notif_tab_by_game").assertIsDisplayed()
  }

  @Test
  fun `notification insights screen renders cleanly in robolectric`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val notifVm = obtainVm(app, DrxNotificationsViewModel::class.java)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          NotificationInsightsScreen(notificationsViewModel = notifVm, onBack = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("notification_insights_list").assertIsDisplayed()
  }

  @Test
  fun `game event tracker screen renders cleanly in robolectric`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val notifVm = obtainVm(app, DrxNotificationsViewModel::class.java)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          GameEventTrackerScreen(notificationsViewModel = notifVm, onBack = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("game_events_list").assertIsDisplayed()
  }

  @Test
  fun `game activity center screen renders cleanly in robolectric`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val notifVm = obtainVm(app, DrxNotificationsViewModel::class.java)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          GameActivityCenterScreen(
            notificationsViewModel = notifVm,
            globalStats =
              GlobalGamingStatistics(
                totalTrackedGames = 0,
                installedTrackedGames = 0,
                totalPlaytimeMs = 0L,
                mostPlayedByTimeName = null,
                mostPlayedByTimePkg = null,
                mostPlayedByTimeMs = 0L,
                mostOpenedGameName = null,
                mostOpenedPkg = null,
                mostOpenedCount = 0,
                lastPlayedGameName = null,
                lastPlayedPkg = null,
                lastPlayedAtMs = null,
                totalSessionsCount = 0,
                averageSessionDurationMs = 0L,
                hasUsagePermission = false,
                usesEstimatedSystemUsage = false,
                weeklyChart = emptyList(),
                monthlyChart = emptyList(),
                rankedGames = emptyList(),
              ),
            onNavigateRoute = {},
            onOpenGameStats = {},
            onBack = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("game_activity_center_list").assertIsDisplayed()
  }

  @Test
  fun `update center screen renders cleanly in robolectric`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val updateVm = obtainVm(app, DrxUpdateViewModel::class.java)

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          UpdateCenterScreen(updateViewModel = updateVm, onOpenUpdateLog = {}, onBack = {})
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("update_center_list").assertIsDisplayed()
    composeTestRule.onNodeWithTag("update_check_btn").assertIsDisplayed()
  }

  @Test
  fun `mandatory smart update modal hides later button and exposes exit and update actions`() {
    val mandatoryState =
      DrxUpdateUiState(
        pipelineState = DrxUpdatePipelineState.AVAILABLE,
        selectedManifest = UpdateManifest("2.0.0", 6, "2026-12-01", "Mandatory release", "release"),
        policyState = UpdateOfferPolicyState.MANDATORY_REQUIRED,
      )
    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          SmartUpdateOfferModal(
            uiState = mandatoryState,
            isIndo = false,
            onUpdateNow = {},
            onLater = {},
            onExitApp = {},
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("smart_update_offer_modal").assertIsDisplayed()
    composeTestRule.onNodeWithTag("smart_update_exit_btn").assertIsDisplayed()
    composeTestRule.onNodeWithTag("smart_update_later_btn").assertDoesNotExist()
    composeTestRule.onNodeWithText("Required Update").assertIsDisplayed()
  }

  @Test
  fun `dashboard shows DRX UPDATE AVAILABLE card and NEW UPDATE tile when higher verified version exists`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val mainVm = obtainVm(app, DrxMainViewModel::class.java)
    val availableState =
      DrxUpdateUiState(
        pipelineState = DrxUpdatePipelineState.AVAILABLE,
        selectedManifest =
          UpdateManifest(
            versionName = "1.21.0",
            versionCode = BuildConfig.VERSION_CODE + 1,
            releaseDate = "2026-12-01",
            releaseNotes = "Automatic APK change detector & updater polish",
            apkType = "release",
            downloadUrl = "https://updates.example.com/drx/v1.21.0.apk",
            localFileName = "DRX-GAME-TURBO-PRO-v1.21.0.apk",
            fileSizeBytes = 21_500_000L,
          ),
        isUpdateAvailable = true,
        isSourceConfigured = true,
      )

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          com.example.ui.screens.DashboardScreen(
            telemetry = com.example.data.model.SystemTelemetry(),
            prefs = com.example.data.prefs.DrxUserPrefs(animationsEnabled = false),
            games = emptyList(),
            isBoosting = false,
            boostReport = null,
            activeSession = null,
            viewModel = mainVm,
            onNavigateGames = {},
            onNavigateProfile = {},
            onNavigateSettings = {},
            onNavigateTool = {},
            updateUiState = availableState,
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("home_dashboard_list")
      .performScrollToNode(hasTestTag("home_update_available_card"))
    composeTestRule.onNodeWithTag("home_update_available_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("DRX UPDATE AVAILABLE").assertIsDisplayed()
    composeTestRule.onNodeWithText("NEW VERSION").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_update_download_btn").assertIsDisplayed()
    composeTestRule.onNodeWithTag("home_dashboard_list")
      .performScrollToNode(hasTestTag("home_quick_updates_tile"))
    composeTestRule.onNodeWithText("NEW UPDATE").assertIsDisplayed()
  }

  @Test
  fun `dashboard hides update card and NEW UPDATE badge when up to date`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val mainVm = obtainVm(app, DrxMainViewModel::class.java)
    val upToDateState =
      DrxUpdateUiState(
        pipelineState = DrxUpdatePipelineState.UP_TO_DATE,
        selectedManifest =
          UpdateManifest(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            releaseDate = "2026-11-23",
            releaseNotes = "Current",
            apkType = "release",
          ),
        isUpdateAvailable = false,
        isSourceConfigured = false,
      )

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          com.example.ui.screens.DashboardScreen(
            telemetry = com.example.data.model.SystemTelemetry(),
            prefs = com.example.data.prefs.DrxUserPrefs(animationsEnabled = false),
            games = emptyList(),
            isBoosting = false,
            boostReport = null,
            activeSession = null,
            viewModel = mainVm,
            onNavigateGames = {},
            onNavigateProfile = {},
            onNavigateSettings = {},
            onNavigateTool = {},
            updateUiState = upToDateState,
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("home_update_available_card").assertDoesNotExist()
    composeTestRule.onNodeWithText("NEW UPDATE").assertDoesNotExist()
  }

  @Test
  fun `update center shows developer warning and never shows download button when update is rejected`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val updateVm = obtainVm(app, DrxUpdateViewModel::class.java)
    updateVm.testManifestJson(
      """
      {
        "appId": "${BuildConfig.APPLICATION_ID}",
        "versionName": "1.9.0",
        "versionCode": ${BuildConfig.VERSION_CODE},
        "apkFileName": "DRX-GAME-TURBO-PRO-v1.9.0.apk",
        "downloadUrl": "https://updates.example.com/drx/DRX.apk",
        "developerWarning": "Developer Warning: Replacement APK changed checksum without raising versionCode."
      }
      """.trimIndent(),
      sourceLabel = "Test Manifest",
    )

    composeTestRule.setContent {
      CompositionLocalProvider(LocalStrings provides EnglishStrings) {
        DrxGameTurboTheme(darkTheme = true) {
          UpdateCenterScreen(updateViewModel = updateVm, onOpenUpdateLog = {}, onBack = {})
        }
      }
    }
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      updateVm.uiState.value.pipelineState == DrxUpdatePipelineState.REJECTED_WARNING
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("update_developer_warning_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("update_download_btn").assertDoesNotExist()
    composeTestRule.onNodeWithTag("update_install_btn").assertDoesNotExist()
  }
}
