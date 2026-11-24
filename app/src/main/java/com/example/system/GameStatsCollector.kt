package com.example.system

import android.app.usage.StorageStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import com.example.data.db.GameEntity
import com.example.data.db.GameSessionEntity
import com.example.data.model.CombinedGameStatistics
import com.example.data.model.DayActivityBucket
import com.example.data.model.GameDetailInfo
import com.example.data.model.GamePlaytimeRank
import com.example.data.model.GlobalGamingStatistics
import com.example.data.model.SystemGameUsageSummary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class GameStatsCollector(
  private val context: Context,
  private val tweaksManager: SystemTweaksManager,
) {

  fun collectGameDetailInfo(packageName: String, fallbackName: String): GameDetailInfo {
    val pm = context.packageManager
    val pkgInfo: PackageInfo? =
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
          @Suppress("DEPRECATION") pm.getPackageInfo(packageName, 0)
        }
      } catch (_: Throwable) {
        null
      }

    if (pkgInfo == null) {
      return GameDetailInfo(
        packageName = packageName,
        appName = fallbackName,
        developerName = null,
        versionName = "Not Available",
        versionCode = 0L,
        isInstalled = false,
        firstInstallTimeMs = null,
        lastUpdateTimeMs = null,
        installSourceLabel = null,
        apkSizeBytes = null,
        appSizeBytes = null,
        dataSizeBytes = null,
        cacheSizeBytes = null,
        totalStorageBytes = null,
        storageAccessRestricted = true,
      )
    }

    val appInfo = pkgInfo.applicationInfo
    val appLabel = appInfo?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: fallbackName
    val versionName = pkgInfo.versionName?.takeIf { it.isNotBlank() } ?: "-"
    val versionCode =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pkgInfo.longVersionCode
      } else {
        @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()
      }

    val apkSizeBytes =
      try {
        val src = appInfo?.sourceDir
        if (!src.isNullOrBlank()) {
          val file = File(src)
          if (file.exists() && file.canRead()) file.length() else null
        } else null
      } catch (_: Throwable) {
        null
      }

    val installerPkg = resolveInstallerPackage(pm, packageName)
    val installSourceLabel = mapInstallerPackageToLabel(installerPkg)
    val hasUsagePerm = tweaksManager.hasUsageStatsPermission()

    var appBytes: Long? = null
    var dataBytes: Long? = null
    var cacheBytes: Long? = null
    var totalStorageBytes: Long? = null
    var storageRestricted = true

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasUsagePerm) {
      try {
        val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val uuid = appInfo?.storageUuid ?: StorageManager.UUID_DEFAULT
        val stats = ssm.queryStatsForPackage(uuid, packageName, Process.myUserHandle())
        appBytes = stats.appBytes
        dataBytes = stats.dataBytes
        cacheBytes = stats.cacheBytes
        totalStorageBytes = stats.appBytes + stats.dataBytes + stats.cacheBytes
        storageRestricted = false
      } catch (_: Throwable) {
        storageRestricted = true
      }
    }

    return GameDetailInfo(
      packageName = packageName,
      appName = appLabel,
      developerName = resolveDeveloperHintIfExposed(installSourceLabel),
      versionName = versionName,
      versionCode = versionCode,
      isInstalled = true,
      firstInstallTimeMs = pkgInfo.firstInstallTime.takeIf { it > 0L },
      lastUpdateTimeMs = pkgInfo.lastUpdateTime.takeIf { it > 0L },
      installSourceLabel = installSourceLabel,
      apkSizeBytes = apkSizeBytes,
      appSizeBytes = appBytes ?: apkSizeBytes,
      dataSizeBytes = dataBytes,
      cacheSizeBytes = cacheBytes,
      totalStorageBytes = totalStorageBytes,
      storageAccessRestricted = storageRestricted,
    )
  }

  private fun resolveInstallerPackage(pm: PackageManager, packageName: String): String? {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val info = pm.getInstallSourceInfo(packageName)
        info.installingPackageName ?: info.initiatingPackageName ?: info.originatingPackageName
      } else {
        @Suppress("DEPRECATION") pm.getInstallerPackageName(packageName)
      }
    } catch (_: Throwable) {
      null
    }
  }

  private fun resolveDeveloperHintIfExposed(installSourceLabel: String?): String? {
    // Android PackageManager does not reliably expose Play Store developer names for third-party apps;
    // return null rather than fabricating publisher metadata.
    return if (installSourceLabel != null && installSourceLabel == "DRX Suite") "DerreX" else null
  }

  fun querySystemGameUsage(
    packageName: String,
    nowMs: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
  ): SystemGameUsageSummary {
    return queryBatchSystemUsage(setOf(packageName), nowMs, timeZone)[packageName]
      ?: SystemGameUsageSummary(
        packageName = packageName,
        hasPermission = tweaksManager.hasUsageStatsPermission(),
      )
  }

  fun queryBatchSystemUsage(
    packageNames: Set<String>,
    nowMs: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
  ): Map<String, SystemGameUsageSummary> {
    val hasPerm = tweaksManager.hasUsageStatsPermission()
    if (!hasPerm || packageNames.isEmpty()) {
      return packageNames.associateWith { SystemGameUsageSummary(packageName = it, hasPermission = hasPerm) }
    }

    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
      ?: return packageNames.associateWith { SystemGameUsageSummary(packageName = it, hasPermission = false) }

    val todayStart = startOfDayMs(nowMs, timeZone)
    val yesterdayStart = startOfDaysAgoMs(nowMs, 1, timeZone)
    val sevenDaysStart = startOfDaysAgoMs(nowMs, 6, timeZone)
    val thirtyDaysStart = startOfDaysAgoMs(nowMs, 29, timeZone)

    val dailyByPkg = HashMap<String, MutableMap<Long, Long>>()
    val lastUsedByPkg = HashMap<String, Long>()
    val launchCountByPkg = HashMap<String, Int>()
    val foregroundByPkg = HashMap<String, Boolean>()

    try {
      val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, thirtyDaysStart, nowMs).orEmpty()
      for (entry in stats) {
        val pkg = entry.packageName ?: continue
        if (pkg !in packageNames) continue
        val fgMs = entry.totalTimeInForeground
        if (fgMs > 0L) {
          val dayKey = startOfDayMs(entry.firstTimeStamp.coerceAtLeast(thirtyDaysStart), timeZone)
          val map = dailyByPkg.getOrPut(pkg) { HashMap() }
          map[dayKey] = (map[dayKey] ?: 0L) + fgMs
        }
        val prevLast = lastUsedByPkg[pkg] ?: 0L
        if (entry.lastTimeUsed > prevLast) {
          lastUsedByPkg[pkg] = entry.lastTimeUsed
        }
      }
    } catch (_: Throwable) {
    }

    // Approximate launch counts & active foreground status from last 7d UsageEvents
    try {
      val events = usm.queryEvents(sevenDaysStart, nowMs)
      val event = UsageEvents.Event()
      val lastEventType = HashMap<String, Int>()
      while (events != null && events.hasNextEvent()) {
        events.getNextEvent(event)
        val pkg = event.packageName ?: continue
        if (pkg !in packageNames) continue
        val type = event.eventType
        val isResume =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            (type == UsageEvents.Event.ACTIVITY_RESUMED || type == UsageEvents.Event.MOVE_TO_FOREGROUND)
          } else {
            @Suppress("DEPRECATION")
            (type == UsageEvents.Event.MOVE_TO_FOREGROUND)
          }
        val isPause =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            (type == UsageEvents.Event.ACTIVITY_PAUSED ||
              type == UsageEvents.Event.MOVE_TO_BACKGROUND ||
              type == UsageEvents.Event.ACTIVITY_STOPPED)
          } else {
            @Suppress("DEPRECATION")
            (type == UsageEvents.Event.MOVE_TO_BACKGROUND)
          }
        if (isResume) {
          launchCountByPkg[pkg] = (launchCountByPkg[pkg] ?: 0) + 1
          lastEventType[pkg] = type
          val prevLast = lastUsedByPkg[pkg] ?: 0L
          if (event.timeStamp > prevLast) lastUsedByPkg[pkg] = event.timeStamp
        } else if (isPause) {
          lastEventType[pkg] = type
        }
      }
      lastEventType.forEach { (pkg, type) ->
        foregroundByPkg[pkg] =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            (type == UsageEvents.Event.ACTIVITY_RESUMED || type == UsageEvents.Event.MOVE_TO_FOREGROUND)
          } else {
            @Suppress("DEPRECATION")
            (type == UsageEvents.Event.MOVE_TO_FOREGROUND)
          }
      }
    } catch (_: Throwable) {
    }

    return packageNames.associateWith { pkg ->
      val dayMap = dailyByPkg[pkg].orEmpty()
      val todayMs = dayMap[todayStart] ?: 0L
      val yesterdayMs = dayMap[yesterdayStart] ?: 0L
      val last7Ms = dayMap.filterKeys { it >= sevenDaysStart }.values.sum()
      val last30Ms = dayMap.filterKeys { it >= thirtyDaysStart }.values.sum()
      SystemGameUsageSummary(
        packageName = pkg,
        hasPermission = true,
        todayMs = todayMs,
        yesterdayMs = yesterdayMs,
        last7DaysMs = last7Ms,
        last30DaysMs = last30Ms,
        lastTimeUsedMs = lastUsedByPkg[pkg] ?: 0L,
        launchCountEstimate = launchCountByPkg[pkg] ?: 0,
        isCurrentlyForeground = foregroundByPkg[pkg] == true,
        dailyBucketsByEpochStart = dayMap,
      )
    }
  }

  companion object {

    fun mapInstallerPackageToLabel(installerPkg: String?): String? {
      if (installerPkg.isNullOrBlank()) return null
      return when (installerPkg.lowercase(Locale.US)) {
        "com.android.vending" -> "Google Play Store"
        "com.sec.android.app.samsungapps" -> "Samsung Galaxy Store"
        "com.xiaomi.market" -> "Xiaomi GetApps"
        "com.heytap.market", "com.oppo.market" -> "App Market (OPPO/Realme)"
        "com.vivo.appstore" -> "V-Appstore"
        "com.huawei.appmarket" -> "Huawei AppGallery"
        "com.amazon.venezia" -> "Amazon Appstore"
        "com.android.packageinstaller", "com.google.android.packageinstaller" -> "Package Installer (APK)"
        "com.android.shell" -> "ADB / Shell Installer"
        else -> installerPkg
      }
    }

    fun startOfDayMs(timestampMs: Long, timeZone: TimeZone = TimeZone.getDefault()): Long {
      val cal =
        Calendar.getInstance(timeZone).apply {
          timeInMillis = timestampMs
          set(Calendar.HOUR_OF_DAY, 0)
          set(Calendar.MINUTE, 0)
          set(Calendar.SECOND, 0)
          set(Calendar.MILLISECOND, 0)
        }
      return cal.timeInMillis
    }

    fun startOfDaysAgoMs(
      nowMs: Long,
      daysAgo: Int,
      timeZone: TimeZone = TimeZone.getDefault(),
    ): Long {
      val cal =
        Calendar.getInstance(timeZone).apply {
          timeInMillis = nowMs
          add(Calendar.DAY_OF_YEAR, -daysAgo)
          set(Calendar.HOUR_OF_DAY, 0)
          set(Calendar.MINUTE, 0)
          set(Calendar.SECOND, 0)
          set(Calendar.MILLISECOND, 0)
        }
      return cal.timeInMillis
    }

    fun computeCombinedGameStats(
      gameInfo: GameDetailInfo,
      drxGame: GameEntity?,
      gameSessions: List<GameSessionEntity>,
      systemUsage: SystemGameUsageSummary,
      nowMs: Long,
      isIndo: Boolean,
      timeZone: TimeZone = TimeZone.getDefault(),
    ): CombinedGameStatistics {
      val todayStart = startOfDayMs(nowMs, timeZone)
      val yesterdayStart = startOfDaysAgoMs(nowMs, 1, timeZone)
      val sevenStart = startOfDaysAgoMs(nowMs, 6, timeZone)
      val thirtyStart = startOfDaysAgoMs(nowMs, 29, timeZone)

      val drxDailyMap = HashMap<Long, Long>()
      for (s in gameSessions) {
        val dayKey = startOfDayMs(s.startedAt, timeZone)
        drxDailyMap[dayKey] = (drxDailyMap[dayKey] ?: 0L) + s.durationMs.coerceAtLeast(0L)
      }

      // Merge per-day without double-counting: take max(DRX tracked day, Android UsageStats day)
      val allDaysKeys = (drxDailyMap.keys + systemUsage.dailyBucketsByEpochStart.keys).toSortedSet()
      val mergedDaily = HashMap<Long, Long>()
      var usedSystemBeyondDrx = false
      for (dayKey in allDaysKeys) {
        val drxVal = drxDailyMap[dayKey] ?: 0L
        val sysVal = systemUsage.dailyBucketsByEpochStart[dayKey] ?: 0L
        if (sysVal > drxVal) {
          usedSystemBeyondDrx = true
        }
        mergedDaily[dayKey] = maxOf(drxVal, sysVal)
      }

      val drxTotalMs = maxOf(gameSessions.sumOf { it.durationMs }, drxGame?.totalPlayTimeMs ?: 0L)
      val merged30dFromDaily = mergedDaily.filterKeys { it >= thirtyStart }.values.sum()
      val mergedOlderDrx = drxDailyMap.filterKeys { it < thirtyStart }.values.sum()
      val totalPlaytimeMs = maxOf(drxTotalMs, merged30dFromDaily + mergedOlderDrx)

      val todayMs = mergedDaily[todayStart] ?: 0L
      val yesterdayMs = mergedDaily[yesterdayStart] ?: 0L
      val last7DaysMs = mergedDaily.filterKeys { it >= sevenStart }.values.sum()
      val last30DaysMs = mergedDaily.filterKeys { it >= thirtyStart }.values.sum()

      val activeDays30 = mergedDaily.filterKeys { it >= thirtyStart && (mergedDaily[it] ?: 0L) > 0L }.size
      val dailyAverageMs = if (activeDays30 > 0) last30DaysMs / activeDays30 else 0L

      val sessionCount = maxOf(gameSessions.size, drxGame?.playCount ?: 0)
      val latestSession = gameSessions.maxByOrNull { it.startedAt }
      val longestSessionMs = gameSessions.maxOfOrNull { it.durationMs } ?: 0L

      val peakEntry = mergedDaily.maxByOrNull { it.value }
      val idLocale = Locale.Builder().setLanguage("id").setRegion("ID").build()
      val locale = if (isIndo) idLocale else Locale.ENGLISH
      val peakLabel =
        if (peakEntry != null && peakEntry.value > 0L) {
          SimpleDateFormat("EEE, dd MMM", locale).apply { this.timeZone = timeZone }
            .format(Date(peakEntry.key))
        } else {
          "-"
        }

      val drxLastPlayed = latestSession?.endedAt ?: drxGame?.lastPlayedAt?.takeIf { it > 0L }
      val sysLastUsed = systemUsage.lastTimeUsedMs.takeIf { it > 0L }
      val lastPlayedTs =
        when {
          drxLastPlayed != null && sysLastUsed != null -> maxOf(drxLastPlayed, sysLastUsed)
          else -> drxLastPlayed ?: sysLastUsed
        }

      val dayShortFmt = SimpleDateFormat("dd", Locale.US).apply { this.timeZone = timeZone }
      val weekDayFmt = SimpleDateFormat("EEE", locale).apply { this.timeZone = timeZone }

      val chart7Days =
        (6 downTo 0).map { ago ->
          val dStart = startOfDaysAgoMs(nowMs, ago, timeZone)
          DayActivityBucket(
            dayLabel = weekDayFmt.format(Date(dStart)),
            dayEpochStartMs = dStart,
            durationMs = mergedDaily[dStart] ?: 0L,
          )
        }
      val chart30Days =
        (29 downTo 0).map { ago ->
          val dStart = startOfDaysAgoMs(nowMs, ago, timeZone)
          DayActivityBucket(
            dayLabel = dayShortFmt.format(Date(dStart)),
            dayEpochStartMs = dStart,
            durationMs = mergedDaily[dStart] ?: 0L,
          )
        }

      return CombinedGameStatistics(
        gameInfo = gameInfo,
        systemUsage = systemUsage,
        totalPlaytimeMs = totalPlaytimeMs,
        drxTrackedPlaytimeMs = drxTotalMs,
        todayMs = todayMs,
        yesterdayMs = yesterdayMs,
        last7DaysMs = last7DaysMs,
        last30DaysMs = last30DaysMs,
        dailyAverageMs = dailyAverageMs,
        sessionCount = sessionCount,
        launchCountEstimate = maxOf(sessionCount, systemUsage.launchCountEstimate),
        lastSessionDurationMs = latestSession?.durationMs ?: 0L,
        longestSessionMs = longestSessionMs,
        peakDayLabel = peakLabel,
        peakDayDurationMs = peakEntry?.value ?: 0L,
        lastPlayedTimestampMs = lastPlayedTs,
        lastSessionStartMs = latestSession?.startedAt,
        lastSessionEndMs = latestSession?.endedAt,
        usesEstimatedSystemUsage = usedSystemBeyondDrx,
        chart7Days = chart7Days,
        chart30Days = chart30Days,
      )
    }

    fun computeGlobalStats(
      games: List<GameEntity>,
      allSessions: List<GameSessionEntity>,
      systemUsageByPkg: Map<String, SystemGameUsageSummary>,
      hasUsagePermission: Boolean,
      installedPackages: Set<String>,
      nowMs: Long,
      isIndo: Boolean,
      timeZone: TimeZone = TimeZone.getDefault(),
    ): GlobalGamingStatistics {
      val sessionsByPkg = allSessions.groupBy { it.gamePackageName }
      val allPkgs = (games.map { it.packageName } + sessionsByPkg.keys).distinct()
      val gamesMap = games.associateBy { it.packageName }

      val mergedDayTotals = HashMap<Long, Long>()
      val ranks = ArrayList<GamePlaytimeRank>()
      var usedAnySystemEstimate = false

      for (pkg in allPkgs) {
        val drxGame = gamesMap[pkg]
        val sessions = sessionsByPkg[pkg].orEmpty()
        val sys = systemUsageByPkg[pkg] ?: SystemGameUsageSummary(packageName = pkg, hasPermission = hasUsagePermission)
        val appName = drxGame?.appName ?: sessions.firstOrNull()?.gameName ?: pkg.substringAfterLast('.')

        val drxDayMap = HashMap<Long, Long>()
        sessions.forEach { s ->
          val dKey = startOfDayMs(s.startedAt, timeZone)
          drxDayMap[dKey] = (drxDayMap[dKey] ?: 0L) + s.durationMs.coerceAtLeast(0L)
        }
        val keys = (drxDayMap.keys + sys.dailyBucketsByEpochStart.keys).distinct()
        var pkgTotalFromDays = 0L
        var pkgEstimated = false
        for (k in keys) {
          val d = drxDayMap[k] ?: 0L
          val s = sys.dailyBucketsByEpochStart[k] ?: 0L
          if (s > d) {
            pkgEstimated = true
            usedAnySystemEstimate = true
          }
          val best = maxOf(d, s)
          pkgTotalFromDays += best
          mergedDayTotals[k] = (mergedDayTotals[k] ?: 0L) + best
        }
        val drxTotal = maxOf(sessions.sumOf { it.durationMs }, drxGame?.totalPlayTimeMs ?: 0L)
        val pkgTotal = maxOf(drxTotal, pkgTotalFromDays)

        val opens = maxOf(sessions.size, drxGame?.playCount ?: 0, sys.launchCountEstimate)
        val drxLast = maxOf(sessions.maxOfOrNull { it.endedAt } ?: 0L, drxGame?.lastPlayedAt ?: 0L)
        val lastAt = maxOf(drxLast, sys.lastTimeUsedMs).takeIf { it > 0L }

        ranks.add(
          GamePlaytimeRank(
            packageName = pkg,
            appName = appName,
            totalPlaytimeMs = pkgTotal,
            sessionOrLaunchCount = opens,
            lastPlayedAtMs = lastAt,
            isEstimated = pkgEstimated,
          )
        )
      }

      val totalPlaytimeMs = ranks.sumOf { it.totalPlaytimeMs }
      val topByTime = ranks.filter { it.totalPlaytimeMs > 0L }.maxByOrNull { it.totalPlaytimeMs }
      val topByOpens = ranks.filter { it.sessionOrLaunchCount > 0 }.maxByOrNull { it.sessionOrLaunchCount }
      val lastPlayed = ranks.filter { it.lastPlayedAtMs != null }.maxByOrNull { it.lastPlayedAtMs ?: 0L }

      val totalSessionsCount = allSessions.size
      val avgSessionMs =
        if (totalSessionsCount > 0) allSessions.sumOf { it.durationMs } / totalSessionsCount else 0L

      val idLocale = Locale.Builder().setLanguage("id").setRegion("ID").build()
      val locale = if (isIndo) idLocale else Locale.ENGLISH
      val weekDayFmt = SimpleDateFormat("EEE", locale).apply { this.timeZone = timeZone }
      val dayShortFmt = SimpleDateFormat("dd", Locale.US).apply { this.timeZone = timeZone }

      val weeklyChart =
        (6 downTo 0).map { ago ->
          val d = startOfDaysAgoMs(nowMs, ago, timeZone)
          DayActivityBucket(weekDayFmt.format(Date(d)), d, mergedDayTotals[d] ?: 0L)
        }
      val monthlyChart =
        (29 downTo 0).map { ago ->
          val d = startOfDaysAgoMs(nowMs, ago, timeZone)
          DayActivityBucket(dayShortFmt.format(Date(d)), d, mergedDayTotals[d] ?: 0L)
        }

      return GlobalGamingStatistics(
        totalTrackedGames = games.size,
        installedTrackedGames = games.count { it.packageName in installedPackages },
        totalPlaytimeMs = totalPlaytimeMs,
        mostPlayedByTimeName = topByTime?.appName,
        mostPlayedByTimePkg = topByTime?.packageName,
        mostPlayedByTimeMs = topByTime?.totalPlaytimeMs ?: 0L,
        mostOpenedGameName = topByOpens?.appName,
        mostOpenedPkg = topByOpens?.packageName,
        mostOpenedCount = topByOpens?.sessionOrLaunchCount ?: 0,
        lastPlayedGameName = lastPlayed?.appName,
        lastPlayedPkg = lastPlayed?.packageName,
        lastPlayedAtMs = lastPlayed?.lastPlayedAtMs,
        totalSessionsCount = totalSessionsCount,
        averageSessionDurationMs = avgSessionMs,
        hasUsagePermission = hasUsagePermission,
        usesEstimatedSystemUsage = usedAnySystemEstimate,
        weeklyChart = weeklyChart,
        monthlyChart = monthlyChart,
        rankedGames = ranks.sortedByDescending { it.totalPlaytimeMs },
      )
    }
  }
}
