package com.example.system

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.example.data.db.GameSessionEntity
import com.example.data.model.CrashDiagnosticAnalysis
import com.example.data.model.CrashExitType
import com.example.data.model.SystemTelemetry
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class CrashAnalyzerEngine(
  private val context: Context,
  private val tweaksManager: SystemTweaksManager,
) {

  fun analyzeGameExit(
    packageName: String,
    appName: String,
    telemetry: SystemTelemetry,
    recentSession: GameSessionEntity?,
    isIndo: Boolean,
  ): CrashDiagnosticAnalysis {
    val now = System.currentTimeMillis()
    val gameVersion = resolveVersionString(packageName)
    val availRamMb = (telemetry.memoryInfo.availBytes / (1024L * 1024L)).coerceAtLeast(0L)
    val totalRamMb = (telemetry.memoryInfo.totalBytes / (1024L * 1024L)).coerceAtLeast(1L)
    val tempC = telemetry.batteryInfo.temperatureC.sanitizeFinite(30f)

    val hasUsagePerm = tweaksManager.hasUsageStatsPermission()
    var exitType = CrashExitType.UNKNOWN_EXIT_REASON
    var sessionStart = recentSession?.startedAt ?: (now - 15 * 60_000L)
    var sessionEnd = recentSession?.endedAt ?: now
    var evidenceSummary = ""

    if (hasUsagePerm) {
      val lookbackStart = now - 6 * 3_600_000L
      val timelineResult = inspectUsageTimeline(packageName, lookbackStart, now)
      if (timelineResult != null) {
        exitType = timelineResult.exitType
        sessionStart = timelineResult.startMs
        sessionEnd = timelineResult.endMs
        evidenceSummary = timelineResult.evidence
      }
    }

    if (exitType == CrashExitType.UNKNOWN_EXIT_REASON && recentSession != null) {
      exitType =
        when {
          telemetry.memoryInfo.lowMemory || availRamMb < 450L -> CrashExitType.PROCESS_NO_LONGER_DETECTED
          else -> CrashExitType.USER_CLOSED
        }
      evidenceSummary =
        if (isIndo) "Berdasarkan sesi DRX terakhir (${recentSession.durationMs / 60_000L} mnt)."
        else "Based on last recorded DRX session (${recentSession.durationMs / 60_000L} min)."
    }

    val resultMsg =
      when (exitType) {
        CrashExitType.USER_CLOSED ->
          if (isIndo) "Sesi tampak ditutup secara normal atau pengguna beralih ke beranda/DRX. $evidenceSummary"
          else "Session appears closed normally or switched back to launcher/DRX. $evidenceSummary"
        CrashExitType.SESSION_INTERRUPTED ->
          if (isIndo) "Sesi game terganggu oleh perpindahan ke aplikasi lain atau sistem. $evidenceSummary"
          else "Game session was interrupted by another app or system foreground transition. $evidenceSummary"
        CrashExitType.PROCESS_NO_LONGER_DETECTED ->
          if (isIndo) "Proses game tidak lagi aktif saat tekanan memori/thermal teramati. Android membatasi log crash internal aplikasi lain. $evidenceSummary"
          else "Game process stopped appearing in foreground while memory/thermal load was noted. OS restricts external app stack traces. $evidenceSummary"
        CrashExitType.POSSIBLE_CRASH ->
          if (isIndo) "Keluar mendadak terdeteksi tanpa transisi penutupan normal disertai tekanan RAM/suhu. Kemungkinan crash atau LMK kill. $evidenceSummary"
          else "Abrupt foreground exit detected under memory/thermal pressure without normal switch. Possible crash or OS memory kill. $evidenceSummary"
        CrashExitType.UNKNOWN_EXIT_REASON ->
          if (isIndo) {
            if (!hasUsagePerm) "Izin Usage Access belum aktif sehingga histori transisi aplikasi tidak terbaca. Android tidak membuka crash log aplikasi lain ke non-root."
            else "Belum ada jejak transisi aplikasi dalam 6 jam terakhir untuk game ini."
          } else {
            if (!hasUsagePerm) "Usage Access permission is not granted, so app exit transitions cannot be inspected. OS restricts private crash logs."
            else "No recent foreground/background transition events found in the last 6 hours."
          }
      }

    val tips = mutableListOf<String>()
    if (availRamMb < 900L || telemetry.memoryInfo.lowMemory) {
      tips +=
        if (isIndo) "Bebaskan RAM sebelum bermain (tutup media sosial/browser berat)."
        else "Free up RAM before launching (close heavy browsers/social apps)."
    }
    if (tempC >= 40f || telemetry.thermalStatusCode >= 2) {
      tips +=
        if (isIndo) "Jaga suhu tetap rendah; panas berlebih memicu throttling dan stabilitas menurun."
        else "Keep device cool; elevated temperature degrades stability."
    }
    if ((telemetry.storageInfo.freeBytes / (1024L * 1024L)) < 1024L) {
      tips +=
        if (isIndo) "Kosongkan ruang penyimpanan minimal 1–2 GB untuk aset dan cache shader."
        else "Keep at least 1–2 GB free storage for game assets and shader cache."
    }
    tips +=
      if (isIndo) "Pastikan game dan Android System WebView/layanan OS diperbarui ke versi terbaru."
      else "Ensure the game and Android System WebView are updated."
    if (!hasUsagePerm) {
      tips +=
        if (isIndo) "Aktifkan izin Usage Access agar deteksi transisi sesi lebih akurat."
        else "Grant Usage Access for more accurate app exit transition detection."
    }

    val lastPerf =
      String.format(
        Locale.US,
        "UI %d FPS • RAM %d/%d MB • %.1f°C • %s",
        telemetry.uiFps.safeRoundToInt(60),
        availRamMb,
        totalRamMb,
        tempC,
        telemetry.networkSnapshot.transportType,
      )

    return CrashDiagnosticAnalysis(
      gamePackageName = packageName,
      gameName = appName,
      gameVersion = gameVersion,
      exitType = exitType,
      sessionStartMs = sessionStart,
      sessionEndMs = sessionEnd,
      deviceRamFreeMb = availRamMb,
      deviceRamTotalMb = totalRamMb,
      availableStorageBytes = telemetry.storageInfo.freeBytes,
      batteryLevelPercent = telemetry.batteryInfo.levelPercent,
      batteryTempC = tempC,
      networkStatus = "${telemetry.networkSnapshot.transportType} (${telemetry.networkSnapshot.networkSubType})",
      lastPerformanceSummary = lastPerf,
      diagnosticResult = resultMsg.trim(),
      preventionTips = tips,
    )
  }

  private data class TimelineInspection(
    val exitType: CrashExitType,
    val startMs: Long,
    val endMs: Long,
    val evidence: String,
  )

  private fun inspectUsageTimeline(
    targetPackage: String,
    startMs: Long,
    endMs: Long,
  ): TimelineInspection? {
    return try {
      val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
      val events = usm.queryEvents(startMs, endMs) ?: return null
      val ev = UsageEvents.Event()
      var lastGameFg = 0L
      var lastGameBg = 0L
      var nextFgPkg: String? = null
      var nextFgTime = 0L
      var capturingNextAfterGameBg = false

      while (events.hasNextEvent()) {
        events.getNextEvent(ev)
        @Suppress("DEPRECATION")
        val isFg =
          ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
        @Suppress("DEPRECATION")
        val isBg =
          ev.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ev.eventType == UsageEvents.Event.ACTIVITY_PAUSED)

        if (ev.packageName == targetPackage) {
          if (isFg) {
            lastGameFg = ev.timeStamp
            capturingNextAfterGameBg = false
            nextFgPkg = null
          } else if (isBg) {
            lastGameBg = ev.timeStamp
            capturingNextAfterGameBg = true
          }
        } else if (capturingNextAfterGameBg && isFg && ev.timeStamp >= lastGameBg) {
          nextFgPkg = ev.packageName
          nextFgTime = ev.timeStamp
          capturingNextAfterGameBg = false
        }
      }

      if (lastGameFg == 0L && lastGameBg == 0L) return null
      val end = maxOf(lastGameFg, lastGameBg)
      val start = if (lastGameFg in 1L..end) lastGameFg else (end - 60_000L)

      val exitType =
        when {
          lastGameFg > lastGameBg && (endMs - lastGameFg) > 15_000L -> {
            // Game went foreground without matching clean background close observed
            CrashExitType.POSSIBLE_CRASH
          }
          nextFgPkg == context.packageName ||
            (nextFgPkg != null && nextFgPkg.contains("launcher", ignoreCase = true)) -> {
            CrashExitType.USER_CLOSED
          }
          nextFgPkg != null && nextFgTime - lastGameBg < 4_000L -> {
            CrashExitType.SESSION_INTERRUPTED
          }
          lastGameBg > 0L -> CrashExitType.PROCESS_NO_LONGER_DETECTED
          else -> CrashExitType.UNKNOWN_EXIT_REASON
        }
      val evidence =
        if (nextFgPkg != null) "Next foreground: ${nextFgPkg.substringAfterLast('.')}"
        else "Last foreground event detected"
      TimelineInspection(exitType, start, end, evidence)
    } catch (_: Throwable) {
      null
    }
  }

  private fun resolveVersionString(packageName: String): String {
    return try {
      val info =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
          @Suppress("DEPRECATION")
          context.packageManager.getPackageInfo(packageName, 0)
        }
      info.versionName ?: "Unknown"
    } catch (_: Throwable) {
      "Not Installed"
    }
  }

  fun parseImportedDiagnosticLog(uri: Uri, isIndo: Boolean): String {
    return try {
      val lines = mutableListOf<String>()
      context.contentResolver.openInputStream(uri)?.use { stream ->
        BufferedReader(InputStreamReader(stream)).use { reader ->
          var totalChars = 0
          while (totalChars < 120_000) {
            val line = reader.readLine() ?: break
            lines += line
            totalChars += line.length
          }
        }
      }
      if (lines.isEmpty()) {
        return if (isIndo) "File log kosong atau tidak dapat dibaca." else "Imported log file is empty or unreadable."
      }
      val joined = lines.joinToString("\n")
      val markers = mutableListOf<String>()
      if (joined.contains("FATAL EXCEPTION", ignoreCase = true)) markers += "FATAL EXCEPTION"
      if (joined.contains("OutOfMemoryError", ignoreCase = true)) markers += "OutOfMemoryError"
      if (joined.contains("ANR in", ignoreCase = true)) markers += "ANR (Application Not Responding)"
      if (joined.contains("SIGSEGV", ignoreCase = true) || joined.contains("SIGABRT", ignoreCase = true)) {
        markers += "Native Signal Crash (SIGSEGV/SIGABRT)"
      }
      if (joined.contains("lowmemorykiller", ignoreCase = true) || joined.contains("Killing ", ignoreCase = true)) {
        markers += "LowMemoryKiller Event"
      }

      val excerpt =
        lines
          .filter {
            it.contains("Exception", ignoreCase = true) ||
              it.contains("FATAL", ignoreCase = true) ||
              it.contains("ANR", ignoreCase = true) ||
              it.contains("Error", ignoreCase = true)
          }
          .take(5)
          .joinToString("\n") { redactSensitiveText(it).take(160) }
          .ifBlank { redactSensitiveText(lines.take(3).joinToString(" | ")).take(240) }

      val markerText =
        if (markers.isNotEmpty()) markers.joinToString(", ")
        else if (isIndo) "Tidak ditemukan penanda fatal baku (${lines.size} baris dipindai)"
        else "No standard fatal markers matched (${lines.size} lines scanned)"

      "$markerText\n$excerpt"
    } catch (t: Throwable) {
      if (isIndo) "Gagal membaca file log: ${t.message ?: "Error"}" else "Failed to read log: ${t.message ?: "Error"}"
    }
  }

  private fun redactSensitiveText(raw: String): String {
    return raw
      .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[EMAIL]")
      .replace(Regex("(token|bearer|apikey|password)=[^\\s&]+", RegexOption.IGNORE_CASE), "$1=[REDACTED]")
  }
}
