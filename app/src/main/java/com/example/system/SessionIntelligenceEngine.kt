package com.example.system

import com.example.data.db.GameEntity
import com.example.data.db.GameSessionEntity
import com.example.data.model.DayActivityBucket
import com.example.data.model.SessionComparisonMetric
import com.example.data.model.SessionIntelligenceSummary
import com.example.data.model.SystemGameUsageSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

object SessionIntelligenceEngine {

  fun computeSummary(
    games: List<GameEntity>,
    sessions: List<GameSessionEntity>,
    systemUsageByPkg: Map<String, SystemGameUsageSummary>,
    nowMs: Long = System.currentTimeMillis(),
    isIndo: Boolean = false,
    timeZone: TimeZone = TimeZone.getDefault(),
  ): SessionIntelligenceSummary {
    val todayStart = GameStatsCollector.startOfDayMs(nowMs, timeZone)
    val sevenDaysStart = GameStatsCollector.startOfDaysAgoMs(nowMs, 6, timeZone)
    val prevWeekStart = GameStatsCollector.startOfDaysAgoMs(nowMs, 13, timeZone)
    val twentyEightStart = GameStatsCollector.startOfDaysAgoMs(nowMs, 27, timeZone)

    val sessionsByPkg = sessions.groupBy { it.gamePackageName }
    val allPkgs = (games.map { it.packageName } + sessionsByPkg.keys + systemUsageByPkg.keys).distinct()
    val gamesMap = games.associateBy { it.packageName }

    val mergedDailyTotals = HashMap<Long, Long>()
    val totalByPkg = HashMap<String, Long>()
    var usedSystemEstimate = false

    for (pkg in allPkgs) {
      val drxDayMap = HashMap<Long, Long>()
      val pkgSessions = sessionsByPkg[pkg].orEmpty()
      pkgSessions.forEach { s ->
        val dKey = GameStatsCollector.startOfDayMs(s.startedAt, timeZone)
        drxDayMap[dKey] = (drxDayMap[dKey] ?: 0L) + s.durationMs.coerceAtLeast(0L)
      }
      val sysMap = systemUsageByPkg[pkg]?.dailyBucketsByEpochStart.orEmpty()
      val allDays = (drxDayMap.keys + sysMap.keys).distinct()
      var mergedPkgSum = 0L
      for (dayKey in allDays) {
        val drxVal = drxDayMap[dayKey] ?: 0L
        val sysVal = sysMap[dayKey] ?: 0L
        if (sysVal > drxVal) usedSystemEstimate = true
        val chosen = maxOf(drxVal, sysVal)
        mergedPkgSum += chosen
        mergedDailyTotals[dayKey] = (mergedDailyTotals[dayKey] ?: 0L) + chosen
      }
      val recordedTotal = maxOf(pkgSessions.sumOf { it.durationMs }, gamesMap[pkg]?.totalPlayTimeMs ?: 0L)
      totalByPkg[pkg] = maxOf(recordedTotal, mergedPkgSum)
    }

    val totalGamingTimeMs = totalByPkg.values.sum()
    val totalSessionsCount = sessions.size
    val avgSessionDurationMs =
      if (totalSessionsCount > 0) sessions.sumOf { it.durationMs.coerceAtLeast(0L) } / totalSessionsCount
      else 0L
    val longestSession = sessions.maxByOrNull { it.durationMs }

    val mostPlayedEntry = totalByPkg.maxByOrNull { it.value }
    val mostPlayedPkg = mostPlayedEntry?.key
    val mostPlayedName =
      mostPlayedPkg?.let { pkg ->
        gamesMap[pkg]?.appName ?: sessionsByPkg[pkg]?.firstOrNull()?.gameName ?: pkg
      }

    val latestSession = sessions.maxByOrNull { it.endedAt }
    val latestGame = games.filter { it.lastPlayedAt > 0L }.maxByOrNull { it.lastPlayedAt }
    val latestSysPkg = systemUsageByPkg.values.filter { it.lastTimeUsedMs > 0L }.maxByOrNull { it.lastTimeUsedMs }
    val candidatesLast =
      listOfNotNull(
        latestSession?.let { Triple(it.gameName, it.gamePackageName, it.endedAt) },
        latestGame?.let { Triple(it.appName, it.packageName, it.lastPlayedAt) },
        latestSysPkg?.let {
          val n = gamesMap[it.packageName]?.appName ?: it.packageName
          Triple(n, it.packageName, it.lastTimeUsedMs)
        },
      ).maxByOrNull { it.third }

    val thisWeekTimeMs = mergedDailyTotals.filterKeys { it >= sevenDaysStart }.values.sum()
    val prevWeekTimeMs =
      mergedDailyTotals.filterKeys { it in prevWeekStart until sevenDaysStart }.values.sum()
    val weekDeltaPercent =
      if (prevWeekTimeMs > 0L) {
        (((thisWeekTimeMs - prevWeekTimeMs).toFloat() / prevWeekTimeMs.toFloat()) * 100f).roundToInt()
      } else null

    val activeDaysCount =
      mergedDailyTotals.filterKeys { it >= twentyEightStart && (mergedDailyTotals[it] ?: 0L) > 0L }.size
    val avgDailyMs =
      if (activeDaysCount > 0) {
        mergedDailyTotals.filterKeys { it >= twentyEightStart }.values.sum() / activeDaysCount
      } else 0L

    // Peak 3-hour window from real recorded sessions
    val peakWindow =
      if (sessions.isNotEmpty()) {
        val buckets = IntArray(8)
        val cal = Calendar.getInstance(timeZone)
        sessions.forEach { s ->
          cal.timeInMillis = s.startedAt
          buckets[(cal.get(Calendar.HOUR_OF_DAY) / 3).coerceIn(0, 7)]++
        }
        val best = buckets.indices.maxByOrNull { buckets[it] } ?: 0
        String.format(Locale.US, "%02d:00–%02d:59", best * 3, best * 3 + 2)
      } else null

    val favProfile =
      sessions
        .groupBy { it.profileUsed.ifBlank { "balanced" } }
        .maxByOrNull { it.value.size }
        ?.key

    val idLocale = Locale.forLanguageTag("id-ID")
    val locale = if (isIndo) idLocale else Locale.ENGLISH
    val dayFmt = SimpleDateFormat("EEE", locale).apply { this.timeZone = timeZone }

    val daily7d =
      (6 downTo 0).map { ago ->
        val d = GameStatsCollector.startOfDaysAgoMs(nowMs, ago, timeZone)
        DayActivityBucket(dayFmt.format(Date(d)), d, mergedDailyTotals[d] ?: 0L)
      }

    val weekly4w =
      (3 downTo 0).map { weekAgo ->
        val endMs = todayStart + 86_400_000L - (weekAgo * 7L * 86_400_000L)
        val startMs = endMs - 7L * 86_400_000L
        val sum = mergedDailyTotals.filterKeys { it in startMs until endMs }.values.sum()
        DayActivityBucket("W-${weekAgo + 1}", startMs, sum)
      }

    return SessionIntelligenceSummary(
      totalGamingTimeMs = totalGamingTimeMs,
      averageSessionDurationMs = avgSessionDurationMs,
      longestSessionDurationMs = longestSession?.durationMs ?: 0L,
      longestSessionGameName = longestSession?.gameName,
      mostPlayedGameName = mostPlayedName,
      mostPlayedGamePkg = mostPlayedPkg,
      mostPlayedTimeMs = mostPlayedEntry?.value ?: 0L,
      lastPlayedGameName = candidatesLast?.first,
      lastPlayedGamePkg = candidatesLast?.second,
      lastPlayedAtMs = candidatesLast?.third,
      totalSessionsCount = totalSessionsCount,
      thisWeekTimeMs = thisWeekTimeMs,
      previousWeekTimeMs = prevWeekTimeMs,
      weekDeltaPercent = weekDeltaPercent,
      averageDailyPlaytimeMs = avgDailyMs,
      peakHourWindow = peakWindow,
      favoriteProfileUsed = favProfile,
      dailyChart7d = daily7d,
      weeklyChart4w = weekly4w,
      usesSystemUsageEstimate = usedSystemEstimate,
    )
  }

  fun compareSessions(
    left: GameSessionEntity,
    right: GameSessionEntity,
    isIndo: Boolean,
  ): List<SessionComparisonMetric> {
    val durDiffMin = ((left.durationMs - right.durationMs) / 60_000L)
    val drainLeft = (left.batteryStartPercent - left.batteryEndPercent).coerceAtLeast(0)
    val drainRight = (right.batteryStartPercent - right.batteryEndPercent).coerceAtLeast(0)
    val drainDiff = drainLeft - drainRight
    val tempDiff = left.peakTempC - right.peakTempC
    return listOf(
      SessionComparisonMetric(
        label = if (isIndo) "Durasi" else "Duration",
        leftValue = TimeAndSizeFormatters.formatDurationCompact(left.durationMs, isIndo),
        rightValue = TimeAndSizeFormatters.formatDurationCompact(right.durationMs, isIndo),
        deltaText = "${if (durDiffMin >= 0) "+" else ""}${durDiffMin}m",
      ),
      SessionComparisonMetric(
        label = if (isIndo) "Kuras Baterai" else "Battery Drain",
        leftValue = "${drainLeft}%",
        rightValue = "${drainRight}%",
        deltaText = "${if (drainDiff >= 0) "+" else ""}${drainDiff}%",
      ),
      SessionComparisonMetric(
        label = if (isIndo) "Suhu Puncak" else "Peak Temp",
        leftValue = String.format(Locale.US, "%.1f°C", left.peakTempC),
        rightValue = String.format(Locale.US, "%.1f°C", right.peakTempC),
        deltaText = String.format(Locale.US, "%+.1f°C", tempDiff),
      ),
      SessionComparisonMetric(
        label = if (isIndo) "Profil" else "Profile",
        leftValue = left.profileUsed,
        rightValue = right.profileUsed,
        deltaText = if (left.profileUsed == right.profileUsed) "=" else "≠",
      ),
    )
  }

  fun formatExportReport(
    summary: SessionIntelligenceSummary,
    sessions: List<GameSessionEntity>,
    isIndo: Boolean,
  ): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    return buildString {
      appendLine("=== DRX GAMING SESSION INTELLIGENCE REPORT ===")
      appendLine("Generated          : ${fmt.format(Date())}")
      appendLine("Total Gaming Time  : ${TimeAndSizeFormatters.formatDurationCompact(summary.totalGamingTimeMs, isIndo)}")
      appendLine("Recorded Sessions  : ${summary.totalSessionsCount}")
      appendLine("Average Session    : ${TimeAndSizeFormatters.formatDurationCompact(summary.averageSessionDurationMs, isIndo)}")
      appendLine("Longest Session    : ${TimeAndSizeFormatters.formatDurationCompact(summary.longestSessionDurationMs, isIndo)} (${summary.longestSessionGameName ?: "-"})")
      appendLine("Most Played Game   : ${summary.mostPlayedGameName ?: "-"} (${TimeAndSizeFormatters.formatDurationCompact(summary.mostPlayedTimeMs, isIndo)})")
      appendLine("Last Played Game   : ${summary.lastPlayedGameName ?: "-"}")
      appendLine("This Week (7d)     : ${TimeAndSizeFormatters.formatDurationCompact(summary.thisWeekTimeMs, isIndo)}")
      appendLine("Previous Week (7d) : ${TimeAndSizeFormatters.formatDurationCompact(summary.previousWeekTimeMs, isIndo)}")
      appendLine("Daily Average (28d): ${TimeAndSizeFormatters.formatDurationCompact(summary.averageDailyPlaytimeMs, isIndo)}")
      appendLine("Peak Gaming Window : ${summary.peakHourWindow ?: "-"}")
      appendLine("Favorite Profile   : ${summary.favoriteProfileUsed ?: "-"}")
      appendLine()
      appendLine("--- Recent Sessions ---")
      sessions.take(20).forEach { s ->
        appendLine("- ${fmt.format(Date(s.startedAt))} | ${s.gameName} | ${TimeAndSizeFormatters.formatDurationCompact(s.durationMs, isIndo)} | ${s.profileUsed} | ${s.batteryStartPercent}%->${s.batteryEndPercent}% | ${String.format(Locale.US, "%.1f°C", s.peakTempC)}")
      }
    }
  }
}
