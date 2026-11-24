package com.example.system

import com.example.data.db.GameEntity
import com.example.data.db.GameSessionEntity
import com.example.data.db.SessionReportEntity
import com.example.data.db.SessionTimelineEventEntity
import com.example.data.model.GameResourceReport
import com.example.data.model.SessionComparisonMetric
import com.example.data.model.SessionTimelineEventType
import java.util.Locale

object GameResourceReportEngine {

  fun buildPerGameReports(
    games: List<GameEntity>,
    sessions: List<GameSessionEntity>,
    sessionReports: List<SessionReportEntity>,
    timelineEvents: List<SessionTimelineEventEntity>,
    cpuUsageRestricted: Boolean,
    avgCpuPercentSample: Float?,
    isIndo: Boolean,
  ): List<GameResourceReport> {
    val sessionsByPkg = sessions.groupBy { it.gamePackageName }
    val reportsByPkg = sessionReports.groupBy { it.gamePackageName }
    val eventsByPkg = timelineEvents.groupBy { it.gamePackageName }
    val gamesMap = games.associateBy { it.packageName }

    val allPkgs =
      (sessionsByPkg.keys + reportsByPkg.keys + eventsByPkg.keys + games.map { it.packageName })
        .distinct()

    return allPkgs
      .mapNotNull { pkg ->
        val pkgSessions = sessionsByPkg[pkg].orEmpty()
        val pkgReports = reportsByPkg[pkg].orEmpty()
        val pkgEvents = eventsByPkg[pkg].orEmpty()
        val sessionCount = pkgSessions.size
        if (sessionCount == 0 && pkgReports.isEmpty() && pkgEvents.isEmpty()) {
          return@mapNotNull null
        }
        val gameName =
          gamesMap[pkg]?.appName
            ?: pkgSessions.firstOrNull()?.gameName
            ?: pkgReports.firstOrNull()?.gameName
            ?: pkgEvents.firstOrNull()?.gameName
            ?: pkg.substringAfterLast('.')

        val totalPlaytimeMs = pkgSessions.sumOf { it.durationMs.coerceAtLeast(0L) }
        val avgDurationMs =
          if (sessionCount > 0) totalPlaytimeMs / sessionCount
          else if (pkgReports.isNotEmpty()) pkgReports.sumOf { it.durationMs } / pkgReports.size
          else 0L

        val estBatteryDrain =
          pkgSessions.sumOf { (it.batteryStartPercent - it.batteryEndPercent).coerceAtLeast(0) }

        val ramPercentSamples = pkgEvents.map { it.ramUsedPercent }.filter { it.isFinite() && it > 0f }
        val avgRamPct =
          if (ramPercentSamples.isNotEmpty()) ramPercentSamples.average().toFloat() else null

        val ramFreeSamples =
          (pkgEvents.map { it.ramFreeMb } + pkgReports.map { it.ramAvailEndMb }).filter { it > 0L }
        val avgRamFreeMb =
          if (ramFreeSamples.isNotEmpty()) ramFreeSamples.average().toLong() else null

        val thermalWarnings =
          pkgEvents.count {
            it.eventType == SessionTimelineEventType.THERMAL_WARNING.code || it.thermalStatusCode >= 2
          }.coerceAtLeast(
            pkgSessions.count { it.peakTempC >= 41f }
          )

        val peakTemp =
          maxOf(
            pkgSessions.maxOfOrNull { it.peakTempC } ?: 0f,
            pkgEvents.maxOfOrNull { it.batteryTempC } ?: 0f,
            pkgReports.maxOfOrNull { it.peakTempC } ?: 0f,
          )

        val availLabel =
          when {
            !cpuUsageRestricted && avgCpuPercentSample != null ->
              if (isIndo) "Lengkap (CPU + RAM + Suhu + Baterai)" else "Full Telemetry Available"
            pkgEvents.isNotEmpty() ->
              if (isIndo) "Sebagian (CPU dibatasi OS Android)" else "Partial (CPU restricted by Android OS)"
            else ->
              if (isIndo) "Ringkas (Data sesi & estimasi baterai)" else "Session Summary (CPU restricted by OS)"
          }

        GameResourceReport(
          packageName = pkg,
          gameName = gameName,
          sessionCount = maxOf(sessionCount, pkgReports.size),
          totalPlaytimeMs = totalPlaytimeMs,
          averageSessionDurationMs = avgDurationMs,
          estimatedTotalBatteryDrainPercent = estBatteryDrain,
          avgRamUsedPercent = avgRamPct,
          avgRamFreeMb = avgRamFreeMb,
          avgCpuPercentOrNull = if (cpuUsageRestricted) null else avgCpuPercentSample,
          thermalWarningCount = thermalWarnings,
          peakTempC = peakTemp,
          monitoringAvailabilityLabel = availLabel,
          hasRestrictedCpu = cpuUsageRestricted,
        )
      }
      .sortedWith(compareByDescending<GameResourceReport> { it.totalPlaytimeMs }.thenByDescending { it.sessionCount })
  }

  fun compareTwoSessionsForGame(
    left: GameSessionEntity,
    right: GameSessionEntity,
    leftEvents: List<SessionTimelineEventEntity>,
    rightEvents: List<SessionTimelineEventEntity>,
    isIndo: Boolean,
  ): List<SessionComparisonMetric> {
    val durDiffMin = (left.durationMs - right.durationMs) / 60_000L
    val leftDrain = (left.batteryStartPercent - left.batteryEndPercent).coerceAtLeast(0)
    val rightDrain = (right.batteryStartPercent - right.batteryEndPercent).coerceAtLeast(0)
    val drainDiff = leftDrain - rightDrain
    val tempDiff = left.peakTempC - right.peakTempC

    val leftRamAvg =
      leftEvents.map { it.ramUsedPercent }.filter { it > 0f }.takeIf { it.isNotEmpty() }?.average()
    val rightRamAvg =
      rightEvents.map { it.ramUsedPercent }.filter { it > 0f }.takeIf { it.isNotEmpty() }?.average()
    val ramDelta =
      if (leftRamAvg != null && rightRamAvg != null) {
        String.format(Locale.US, "%+.0f%%", leftRamAvg - rightRamAvg)
      } else {
        "N/A"
      }

    val leftWarnings =
      leftEvents.count { it.eventType == SessionTimelineEventType.THERMAL_WARNING.code || it.thermalStatusCode >= 2 }
    val rightWarnings =
      rightEvents.count { it.eventType == SessionTimelineEventType.THERMAL_WARNING.code || it.thermalStatusCode >= 2 }

    return listOf(
      SessionComparisonMetric(
        label = if (isIndo) "Durasi Bermain" else "Duration",
        leftValue = TimeAndSizeFormatters.formatDurationCompact(left.durationMs, isIndo),
        rightValue = TimeAndSizeFormatters.formatDurationCompact(right.durationMs, isIndo),
        deltaText = "${if (durDiffMin >= 0) "+" else ""}${durDiffMin}m",
      ),
      SessionComparisonMetric(
        label = if (isIndo) "Est. Baterai Terpakai" else "Est. Battery Drain",
        leftValue = "${leftDrain}%",
        rightValue = "${rightDrain}%",
        deltaText = "${if (drainDiff >= 0) "+" else ""}${drainDiff}%",
      ),
      SessionComparisonMetric(
        label = if (isIndo) "Suhu Puncak" else "Peak Temp",
        leftValue = String.format(Locale.US, "%.1f°C", left.peakTempC),
        rightValue = String.format(Locale.US, "%.1f°C", right.peakTempC),
        deltaText = String.format(Locale.US, "%+.1f°C", tempDiff),
      ),
      SessionComparisonMetric(
        label = if (isIndo) "Rata RAM Terpakai" else "Avg RAM Usage",
        leftValue = leftRamAvg?.let { "${it.toInt()}%" } ?: "-",
        rightValue = rightRamAvg?.let { "${it.toInt()}%" } ?: "-",
        deltaText = ramDelta,
      ),
      SessionComparisonMetric(
        label = if (isIndo) "Peringatan Thermal" else "Thermal Warnings",
        leftValue = "$leftWarnings",
        rightValue = "$rightWarnings",
        deltaText = "${if (leftWarnings - rightWarnings >= 0) "+" else ""}${leftWarnings - rightWarnings}",
      ),
    )
  }
}
