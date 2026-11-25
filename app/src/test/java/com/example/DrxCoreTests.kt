package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.GamingProfile
import com.example.data.model.LanguageMode
import com.example.data.model.VoicePreset
import com.example.data.prefs.DrxUserPrefs
import com.example.i18n.stringsFor
import com.example.system.SystemTweaksManager
import com.example.system.TelemetryMonitor
import com.example.system.VoiceEffectParams
import com.example.system.VoiceStudioEngine
import com.example.system.safeRoundToInt
import com.example.system.sanitizeFinite
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DrxCoreTests {

  @Test
  fun `gaming profiles resolve distinct realistic configurations`() {
    val prefs = DrxUserPrefs(customBrightnessPercent = 77)
    val perf = prefs.resolveProfileConfig(GamingProfile.PERFORMANCE)
    val saver = prefs.resolveProfileConfig(GamingProfile.BATTERY_SAVER)
    val custom = prefs.resolveProfileConfig(GamingProfile.CUSTOM)

    assertEquals(90, perf.brightnessPercent)
    assertTrue(perf.enableDnd)
    assertEquals(40, saver.brightnessPercent)
    assertEquals(77, custom.brightnessPercent)
  }

  @Test
  fun `bilingual strings and all 10 voice presets resolve properly`() {
    val id = stringsFor(LanguageMode.INDONESIAN)
    val en = stringsFor(LanguageMode.ENGLISH)
    assertEquals("DRX GAME TURBO PRO", id.appName)
    assertEquals("Home", id.navHome)
    assertEquals("Setelan", id.navSettings)
    assertEquals("Settings", en.navSettings)
    assertEquals("Siap bermain dengan performa optimal", id.greetingSubtitle)
    assertEquals("Ready for smooth, focused gameplay", en.greetingSubtitle)
    assertTrue(id.autoCloseFloatingOnExitLabel.isNotBlank())
    assertTrue(en.autoCloseFloatingOnExitLabel.isNotBlank())
    assertTrue(id.autoCloseFloatingOnExitDesc.isNotBlank())
    assertTrue(en.autoCloseFloatingOnExitDesc.isNotBlank())

    assertEquals(10, VoicePreset.entries.size)
    VoicePreset.entries.forEach { preset ->
      assertTrue(id.voicePresetLabel(preset).isNotBlank())
      assertTrue(en.voicePresetLabel(preset).isNotBlank())
    }
    assertEquals("Electronic", en.voicePresetLabel(VoicePreset.ELECTRONIC))
    assertEquals("Alien", en.voicePresetLabel(VoicePreset.ALIEN))
    assertEquals("Custom Voice", en.voicePresetLabel(VoicePreset.CUSTOM))
  }

  @Test
  fun `voice studio dsp processes PCM audio buffers cleanly for all 10 presets`() {
    val engine = VoiceStudioEngine()
    val input = ShortArray(512) { i -> ((sin(i * 0.15) * 14000).toInt()).toShort() }

    VoicePreset.entries.forEach { preset ->
      val output = ShortArray(512)
      val params = VoiceEffectParams.forPreset(preset)
      engine.processDspFrame(input, output, 512, params)
      val nonZeroCount = output.count { it != 0.toShort() }
      assertTrue(
        "Preset $preset output should contain active audio signal",
        nonZeroCount > 100,
      )
    }
  }

  @Test
  fun `floating edge sidebar defaults and mini-window shortcuts resolve safely`() {
    val defaults = DrxUserPrefs()
    assertEquals(0, defaults.floatingEdgeSide)
    assertEquals(12, defaults.floatingHandleThicknessDp)
    assertTrue(defaults.floatingAutoHide)
    assertTrue(defaults.floatingAutoCloseOnExit)
    assertTrue(defaults.floatingHapticsEnabled)
    assertEquals(1.0f, defaults.floatingSwipeSensitivity, 0.01f)

    val context = ApplicationProvider.getApplicationContext<Context>()
    val tweaks = SystemTweaksManager(context)
    val shortcuts = tweaks.resolveFloatingAppShortcuts()
    assertTrue(shortcuts.any { it.key == "calculator" && it.isBuiltInMiniWindow })
    assertTrue(shortcuts.any { it.key == "notes" && it.isBuiltInMiniWindow })
    assertTrue(shortcuts.any { it.key == "clock" && it.isBuiltInMiniWindow })
    assertTrue(shortcuts.any { it.key == "drx_tools" && it.isBuiltInMiniWindow })
    assertFalse(shortcuts.first { it.key == "whatsapp" }.isBuiltInMiniWindow)
  }

  @Test
  fun `telemetry monitor reads memory storage and display metrics safely`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val monitor = TelemetryMonitor(context)
    val snap = monitor.readSnapshot()
    assertNotNull(snap.cpuInfo)
    assertTrue(snap.cpuInfo.cores >= 1)
    assertTrue(snap.storageInfo.totalBytes >= 0L)
    assertTrue(snap.displayInfo.widthPx > 0)
  }

  @Test
  fun `social links match official DerreX and Nanay accounts`() {
    assertEquals("https://www.youtube.com/@DerreXSA", com.example.data.remote.SocialLinks.YOUTUBE_URL)
    assertEquals("https://www.tiktok.com/@ahmdmlyadi_", com.example.data.remote.SocialLinks.TIKTOK_URL)
    assertEquals(
      "https://whatsapp.com/channel/0029Vb9MpkcBFLgMrfQwpm0R",
      com.example.data.remote.SocialLinks.WHATSAPP_URL,
    )
    assertEquals("https://www.tiktok.com/@naila27302", com.example.data.remote.SocialLinks.SUPPORTER_URL)
    assertEquals("NANAY", com.example.data.remote.SocialLinks.SUPPORTER_NAME)
  }

  @Test
  fun `update catalog maintains newest-to-oldest order with real badges`() {
    val entries = com.example.data.model.DrxUpdateCatalog.entries
    assertTrue(entries.size >= 8)
    assertEquals("1.25.5", entries.first().versionNumber)
    assertEquals("1.20.0", entries[1].versionNumber)
    assertEquals("1.10.0", entries[2].versionNumber)
    assertEquals("1.9.0", entries[3].versionNumber)
    assertEquals("1.3.0", entries[4].versionNumber)
    assertEquals("1.2.0", entries[5].versionNumber)
    assertEquals("1.1.0", entries[6].versionNumber)
    assertEquals("1.0.0", entries.last().versionNumber)
    assertEquals(com.example.data.model.DrxUpdateCatalog.latestVersion, entries.first().versionNumber)
    assertTrue(entries.all { it.newFeatures.isNotEmpty() || it.improvements.isNotEmpty() })
  }

  @Test
  fun `announcement catalog contains at least 100 messages across 7 categories avoids duplicates and prevents consecutive category repeat`() {
    val messages = com.example.data.model.AnnouncementCatalog.allMessages
    assertTrue("Expected >= 100 messages, got ${messages.size}", messages.size >= 100)
    assertEquals(7, com.example.data.model.AnnouncementCategory.entries.size)
    com.example.data.model.AnnouncementCategory.entries.forEach { cat ->
      assertTrue("Category $cat should have messages", messages.any { it.category == cat })
    }

    val recent = messages.take(messages.size - 1).map { it.id }
    val picked =
      com.example.system.AnnouncementSchedulePlanner.pickNonDuplicateMessage(
        enabledCategoryIds = emptySet(),
        recentIds = recent,
        useComboChancePercent = 0,
      )
    assertEquals(messages.last().id, picked.id)

    repeat(12) { idx ->
      val prevCat = com.example.data.model.AnnouncementCategory.GAMING_TIPS.id
      val next =
        com.example.system.AnnouncementSchedulePlanner.pickNonDuplicateMessage(
          enabledCategoryIds = emptySet(),
          recentIds = emptyList(),
          lastCategoryId = prevCat,
          random = kotlin.random.Random(100 + idx),
        )
      assertTrue(
        "Picked category should differ from previous $prevCat",
        next.category.id != prevCat,
      )
    }

    val generated =
      com.example.data.model.AnnouncementSentenceGenerator.generateCombinedMessage(
        com.example.data.model.AnnouncementCategory.PREMIUM_VIBES,
        kotlin.random.Random(7),
      )
    assertTrue(generated.body.isNotBlank())
    assertEquals("customization", generated.targetRoute)
  }

  @Test
  fun `announcement schedule planner avoids overnight quiet hours and spaces randomized slots`() {
    assertTrue(com.example.system.AnnouncementSchedulePlanner.isMinuteInQuietHours(22 * 60, 22, 7))
    assertTrue(com.example.system.AnnouncementSchedulePlanner.isMinuteInQuietHours(23 * 60 + 30, 22, 7))
    assertTrue(com.example.system.AnnouncementSchedulePlanner.isMinuteInQuietHours(6 * 60 + 59, 22, 7))
    assertFalse(com.example.system.AnnouncementSchedulePlanner.isMinuteInQuietHours(7 * 60, 22, 7))
    assertFalse(com.example.system.AnnouncementSchedulePlanner.isMinuteInQuietHours(15 * 60, 22, 7))

    val morningCal =
      java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 8)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
      }
    val slots =
      com.example.system.AnnouncementSchedulePlanner.generateRandomTimestampsForWindow(
        nowMs = morningCal.timeInMillis,
        minPerDay = 2,
        maxPerDay = 3,
        quietStartHour = 22,
        quietEndHour = 7,
        random = kotlin.random.Random(42),
      )
    assertTrue("Expected 2-3 randomized slots, got ${slots.size}", slots.size in 2..3)
    slots.forEach { ts ->
      val c = java.util.Calendar.getInstance().apply { timeInMillis = ts }
      val minute = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
      assertFalse(
        "Slot fell inside quiet hours: ${c.get(java.util.Calendar.HOUR_OF_DAY)}:${c.get(java.util.Calendar.MINUTE)}",
        com.example.system.AnnouncementSchedulePlanner.isMinuteInQuietHours(minute, 22, 7),
      )
    }
    for (i in 1 until slots.size) {
      assertTrue("Slots must be sorted and spaced apart", slots[i] - slots[i - 1] >= 60 * 60_000L)
    }
  }

  @Test
  fun `time and size formatters handle durations bytes and WIB timezone accurately`() {
    assertEquals("0 menit", com.example.system.TimeAndSizeFormatters.formatDurationReadable(0L, true))
    assertEquals("0 min", com.example.system.TimeAndSizeFormatters.formatDurationReadable(0L, false))
    assertEquals("2 jam 15 menit", com.example.system.TimeAndSizeFormatters.formatDurationReadable((135 * 60_000L), true))
    assertEquals("2h 15m", com.example.system.TimeAndSizeFormatters.formatDurationReadable((135 * 60_000L), false))

    assertEquals("Not Available", com.example.system.TimeAndSizeFormatters.formatBytesAuto(null))
    assertEquals("512.0 KB", com.example.system.TimeAndSizeFormatters.formatBytesAuto(512L * 1024L))
    assertEquals("2.50 GB", com.example.system.TimeAndSizeFormatters.formatBytesAuto((2.5 * 1024 * 1024 * 1024).toLong()))

    assertEquals("WIB", com.example.system.TimeAndSizeFormatters.resolveTimezoneTag(java.util.TimeZone.getTimeZone("Asia/Jakarta")))
  }

  @Test
  fun `game stats collector merges drx and system usage without double counting and preserves restricted storage honesty`() {
    val tz = java.util.TimeZone.getTimeZone("Asia/Jakarta")
    val now = 1_763_690_000_000L
    val todayStart = com.example.system.GameStatsCollector.startOfDayMs(now, tz)
    val yesterdayStart = com.example.system.GameStatsCollector.startOfDaysAgoMs(now, 1, tz)

    val gameInfo =
      com.example.data.model.GameDetailInfo(
        packageName = "com.mobile.legends",
        appName = "Mobile Legends",
        developerName = null,
        versionName = "1.9.20",
        versionCode = 1920L,
        isInstalled = true,
        firstInstallTimeMs = now - 10 * 86_400_000L,
        lastUpdateTimeMs = now - 2 * 86_400_000L,
        installSourceLabel = "Google Play Store",
        apkSizeBytes = 140_000_000L,
        appSizeBytes = 140_000_000L,
        dataSizeBytes = null,
        cacheSizeBytes = null,
        totalStorageBytes = null,
        storageAccessRestricted = true,
      )

    val drxSessions =
      listOf(
        com.example.data.db.GameSessionEntity(
          id = 1L,
          gamePackageName = "com.mobile.legends",
          gameName = "Mobile Legends",
          startedAt = todayStart + 3_600_000L,
          endedAt = todayStart + 5_400_000L,
          durationMs = 1_800_000L, // 30m
          profileUsed = "performance",
          batteryStartPercent = 82,
          batteryEndPercent = 76,
          peakTempC = 37.5f,
        ),
        com.example.data.db.GameSessionEntity(
          id = 2L,
          gamePackageName = "com.mobile.legends",
          gameName = "Mobile Legends",
          startedAt = yesterdayStart + 3_600_000L,
          endedAt = yesterdayStart + 4_200_000L,
          durationMs = 600_000L, // 10m
          profileUsed = "balanced",
          batteryStartPercent = 90,
          batteryEndPercent = 87,
          peakTempC = 35.0f,
        ),
      )

    // System usage overlaps today (20m < DRX 30m) and exceeds yesterday (45m > DRX 10m)
    val sysUsage =
      com.example.data.model.SystemGameUsageSummary(
        packageName = "com.mobile.legends",
        hasPermission = true,
        todayMs = 1_200_000L,
        yesterdayMs = 2_700_000L,
        last7DaysMs = 3_900_000L,
        last30DaysMs = 3_900_000L,
        lastTimeUsedMs = todayStart + 7_200_000L,
        launchCountEstimate = 5,
        dailyBucketsByEpochStart = mapOf(todayStart to 1_200_000L, yesterdayStart to 2_700_000L),
      )

    val combined =
      com.example.system.GameStatsCollector.computeCombinedGameStats(
        gameInfo = gameInfo,
        drxGame = null,
        gameSessions = drxSessions,
        systemUsage = sysUsage,
        nowMs = now,
        isIndo = false,
        timeZone = tz,
      )

    // Today takes max(30m, 20m) = 30m; Yesterday takes max(10m, 45m) = 45m; Total = 75m (never doubled)
    assertEquals(1_800_000L, combined.todayMs)
    assertEquals(2_700_000L, combined.yesterdayMs)
    assertEquals(4_500_000L, combined.totalPlaytimeMs)
    assertTrue(combined.usesEstimatedSystemUsage)
    assertEquals(7, combined.chart7Days.size)
    assertEquals(30, combined.chart30Days.size)
    assertTrue(combined.gameInfo.storageAccessRestricted)
    assertEquals(null, combined.gameInfo.dataSizeBytes)
    assertEquals(1_800_000L, combined.longestSessionMs)
  }

  @Test
  fun `global gaming stats ranks games by playtime and finds most opened and last played`() {
    val now = 1_763_690_000_000L
    val games =
      listOf(
        com.example.data.db.GameEntity("com.game.alpha", "Alpha", category = "fps", playCount = 2, totalPlayTimeMs = 1_200_000L, lastPlayedAt = now - 5_000_000L),
        com.example.data.db.GameEntity("com.game.beta", "Beta", category = "moba", playCount = 8, totalPlayTimeMs = 3_600_000L, lastPlayedAt = now - 10_000_000L),
      )
    val sessions =
      listOf(
        com.example.data.db.GameSessionEntity(1L, "com.game.alpha", "Alpha", now - 6_200_000L, now - 5_000_000L, 1_200_000L, "balanced", 80, 75, 36.2f),
      )
    val global =
      com.example.system.GameStatsCollector.computeGlobalStats(
        games = games,
        allSessions = sessions,
        systemUsageByPkg = emptyMap(),
        hasUsagePermission = false,
        installedPackages = setOf("com.game.alpha"),
        nowMs = now,
        isIndo = false,
      )

    assertEquals(2, global.totalTrackedGames)
    assertEquals(1, global.installedTrackedGames)
    assertEquals("com.game.beta", global.mostPlayedByTimePkg)
    assertEquals("com.game.beta", global.mostOpenedPkg)
    assertEquals("com.game.alpha", global.lastPlayedPkg)
    assertEquals(4_800_000L, global.totalPlaytimeMs)
    assertEquals(2, global.rankedGames.size)
    assertEquals("com.game.beta", global.rankedGames.first().packageName)
  }

  @Test
  fun `drx image utils handles missing blank and corrupt files safely without crashing`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val corruptFile = java.io.File(context.cacheDir, "corrupt_test_wallpaper.bin").apply {
      writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x7F))
    }
    try {
      assertEquals(null, com.example.system.DrxImageUtils.decodeSampledImageBitmap(""))
      assertEquals(null, com.example.system.DrxImageUtils.decodeSampledImageBitmap("/non/existent/path/img.png"))
      assertEquals(null, com.example.system.DrxImageUtils.decodeSampledImageBitmap(corruptFile.absolutePath))
    } finally {
      corruptFile.delete()
    }
  }

  @Test
  fun `safe numeric helpers guard NaN and Infinity telemetry values without throwing`() {
    assertEquals(0, Float.NaN.safeRoundToInt())
    assertEquals(60, Float.NaN.safeRoundToInt(60))
    assertEquals(60, Float.POSITIVE_INFINITY.safeRoundToInt(60))
    assertEquals(60, Float.NEGATIVE_INFINITY.safeRoundToInt(60))
    assertEquals(120, 119.6f.safeRoundToInt(60))

    assertEquals(30f, Float.NaN.sanitizeFinite(30f), 0.001f)
    assertEquals(30f, Float.POSITIVE_INFINITY.sanitizeFinite(30f), 0.001f)
    assertEquals(42.5f, 42.5f.sanitizeFinite(30f), 0.001f)
  }

  @Test
  fun `voice dsp handles zero-length and extreme pitch frames safely and audio fallback provides valid bands`() {
    val engine = VoiceStudioEngine()
    val empty = ShortArray(0)
    engine.processDspFrame(empty, empty, 0, VoiceEffectParams(pitchFactor = 5.0f))

    val smallIn = ShortArray(64) { 1000 }
    val smallOut = ShortArray(64)
    val extremeParams = VoiceEffectParams(pitchFactor = 0.0f, echoAmount = 2.0f, reverbAmount = 2.0f, robotMix = 1.0f)
    engine.processDspFrame(smallIn, smallOut, 64, extremeParams)

    val fx = com.example.system.AudioFxEngine()
    val bands = fx.initializeSession()
    fx.release()
    assertEquals(5, bands.size)
    assertTrue(bands.all { it.maxMb > it.minMb })
  }

  @Test
  fun `screen recorder state holder and duration formatter work accurately without native projection`() {
    assertEquals("00:00", com.example.system.ScreenRecorderService.formatDuration(-5))
    assertEquals("01:05", com.example.system.ScreenRecorderService.formatDuration(65))
    assertEquals("12:34", com.example.system.ScreenRecorderService.formatDuration(12 * 60 + 34))

    com.example.system.ScreenRecorderStateHolder.updateConfig(
      resolutionPreset = 1080,
      targetFps = 60,
      audioMode = com.example.system.ScreenRecordAudioMode.SYSTEM_AUDIO,
    )
    val updated = com.example.system.ScreenRecorderStateHolder.state.value
    assertEquals(1080, updated.resolutionPreset)
    assertEquals(60, updated.targetFps)
    assertEquals(com.example.system.ScreenRecordAudioMode.SYSTEM_AUDIO, updated.audioMode)

    val idStrings = com.example.i18n.stringsFor(com.example.data.model.LanguageMode.INDONESIAN)
    val enStrings = com.example.i18n.stringsFor(com.example.data.model.LanguageMode.ENGLISH)
    assertEquals("DRX GAME TURBO PRO", idStrings.appName)
    assertEquals("DRX GAME TURBO PRO", enStrings.appName)
  }
}
