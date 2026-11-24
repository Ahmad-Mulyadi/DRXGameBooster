package com.example.system

import android.os.PowerManager
import com.example.data.db.CrashReportEntity
import com.example.data.db.StabilityWarningEntity
import com.example.data.model.StabilitySeverity
import com.example.data.model.StabilityWarningItem
import com.example.data.model.StabilityWatchAssessment
import com.example.data.model.SystemTelemetry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StabilityWatchEngine {

  fun evaluate(
    telemetry: SystemTelemetry,
    recentCrashes: List<CrashReportEntity>,
    ramWarningFreeMb: Long = 900L,
    thermalWarningC: Float = 40.5f,
    storageWarningFreeMb: Long = 1024L,
    nowMs: Long = System.currentTimeMillis(),
    isIndo: Boolean = false,
  ): StabilityWatchAssessment {
    val totalMb = (telemetry.memoryInfo.totalBytes / (1024L * 1024L)).coerceAtLeast(1L)
    val availMb = (telemetry.memoryInfo.availBytes / (1024L * 1024L)).coerceAtLeast(0L)
    val freeStorageMb = (telemetry.storageInfo.freeBytes / (1024L * 1024L)).coerceAtLeast(0L)
    val tempC = telemetry.batteryInfo.temperatureC.sanitizeFinite(30f)
    val thermalCode = telemetry.thermalStatusCode
    val thermalLabel = thermalStatusLabel(thermalCode, isIndo)
    val netSnap = telemetry.networkSnapshot

    val warnings = mutableListOf<StabilityWarningItem>()

    // 1. Thermal Pressure Check (OS ThermalStatus + Battery Temp Sensor)
    val isThermalCritical =
      thermalCode >= PowerManager.THERMAL_STATUS_SEVERE || tempC >= (thermalWarningC + 4.5f)
    val isThermalWarning =
      !isThermalCritical &&
        (thermalCode >= PowerManager.THERMAL_STATUS_MODERATE || tempC >= thermalWarningC)

    if (isThermalCritical || isThermalWarning) {
      val sev = if (isThermalCritical) StabilitySeverity.CRITICAL else StabilitySeverity.WARNING
      warnings +=
        StabilityWarningItem(
          categoryId = "THERMAL",
          severity = sev,
          title =
            if (isIndo) "PERINGATAN PERFORMA"
            else "PERFORMANCE WARNING",
          subtitle =
            if (isIndo) "Kemungkinan Tekanan Thermal"
            else "Possible Thermal Pressure",
          explanation =
            if (isIndo) {
              "Status thermal perangkat meningkat ($thermalLabel, baterai ${String.format(Locale.US, "%.1f°C", tempC)}). OS Android dapat menurunkan clock CPU/GPU untuk melindungi hardware."
            } else {
              "Device thermal status is elevated ($thermalLabel, battery ${String.format(Locale.US, "%.1f°C", tempC)}). Android OS may throttle clocks to protect hardware."
            },
          recommendation =
            if (isIndo) "Rekomendasi: Biarkan perangkat mendingin sejenak atau lepaskan casing tebal."
            else "Recommendation: Allow your device to cool down before demanding sessions.",
          metricEvidence = "Thermal=$thermalLabel • Battery=${String.format(Locale.US, "%.1f°C", tempC)}",
        )
    }

    // 2. Memory Pressure Check
    val effectiveRamThresh =
      if (ramWarningFreeMb > 0L) ramWarningFreeMb
      else (totalMb * 0.14f).toLong().coerceIn(500L, 1200L)
    val isRamCritical = telemetry.memoryInfo.lowMemory || availMb < (effectiveRamThresh * 0.6f).toLong()
    val isRamWarning = !isRamCritical && availMb < effectiveRamThresh
    if (isRamCritical || isRamWarning) {
      val sev = if (isRamCritical) StabilitySeverity.CRITICAL else StabilitySeverity.WARNING
      warnings +=
        StabilityWarningItem(
          categoryId = "MEMORY",
          severity = sev,
          title = if (isIndo) "PERINGATAN MEMORI" else "MEMORY WARNING",
          subtitle = if (isIndo) "Tekanan RAM Terdeteksi" else "High Memory Pressure",
          explanation =
            if (isIndo) {
              "RAM bebas saat ini tinggal $availMb MB dari $totalMb MB (${telemetry.memoryInfo.usedPercent.safeRoundToInt()}% terpakai). OS dapat menutup aplikasi latar secara agresif."
            } else {
              "Available RAM dropped to $availMb MB of $totalMb MB (${telemetry.memoryInfo.usedPercent.safeRoundToInt()}% used). Android LMK may terminate background processes."
            },
          recommendation =
            if (isIndo) "Rekomendasi: Tutup aplikasi berat di Recent Apps sebelum membuka game."
            else "Recommendation: Close heavy background apps from Recents before launching games.",
          metricEvidence = "FreeRAM=${availMb}MB / Total=${totalMb}MB (LowMem=${telemetry.memoryInfo.lowMemory})",
        )
    }

    // 3. Storage Pressure Check
    if (freeStorageMb < storageWarningFreeMb) {
      val sev = if (freeStorageMb < 450L) StabilitySeverity.CRITICAL else StabilitySeverity.WARNING
      warnings +=
        StabilityWarningItem(
          categoryId = "STORAGE",
          severity = sev,
          title = if (isIndo) "PENYIMPANAN RENDAH" else "LOW STORAGE WARNING",
          subtitle = if (isIndo) "Ruang Kosong Terbatas" else "Low Internal Storage Space",
          explanation =
            if (isIndo) {
              "Sisa penyimpanan internal ${TimeAndSizeFormatters.formatBytesAuto(telemetry.storageInfo.freeBytes)}. Ruang rendah memengaruhi cache shader & perekaman layar."
            } else {
              "Remaining internal storage is ${TimeAndSizeFormatters.formatBytesAuto(telemetry.storageInfo.freeBytes)}. Low free space impacts shader cache and recording."
            },
          recommendation =
            if (isIndo) "Rekomendasi: Bersihkan media lama di DRX Media Hub atau hapus cache tidak terpakai."
            else "Recommendation: Remove unneeded recordings in DRX Media Hub to free at least 1 GB.",
          metricEvidence = "FreeStorage=${TimeAndSizeFormatters.formatBytesAuto(telemetry.storageInfo.freeBytes)}",
        )
    }

    // 4. Network Instability Check
    if (!netSnap.isConnected || netSnap.qualityLevel in 1..1) {
      warnings +=
        StabilityWarningItem(
          categoryId = "NETWORK",
          severity = StabilitySeverity.WARNING,
          title = if (isIndo) "PERINGATAN JARINGAN" else "NETWORK WARNING",
          subtitle = if (isIndo) "Koneksi Lemah atau Terputus" else "Network Disconnected or Weak Signal",
          explanation =
            if (isIndo) {
              "Status jaringan: ${netSnap.transportType} (${netSnap.networkSubType}). Koneksi yang tidak stabil memicu spike ping dalam game online."
            } else {
              "Current connection: ${netSnap.transportType} (${netSnap.networkSubType}). Weak or offline links cause online ping spikes."
            },
          recommendation =
            if (isIndo) "Rekomendasi: Dekatkan ke router Wi-Fi 5GHz atau uji latensi di Network Monitor."
            else "Recommendation: Move closer to a 5GHz Wi-Fi router or run a ping test in Network Monitor.",
          metricEvidence = "Net=${netSnap.transportType} (${netSnap.networkSubType}), Quality=${netSnap.qualityLevel}/4",
        )
    }

    // 5. Recent Session Interruptions / Abnormal Exits Check (from local Room history)
    val sevenDaysAgo = nowMs - 7L * 86_400_000L
    val recentAbnormal =
      recentCrashes.count {
        it.timestampMs >= sevenDaysAgo &&
          (it.exitClassification.contains("CRASH", true) ||
            it.exitClassification.contains("PROCESS", true) ||
            it.exitClassification.contains("INTERRUPTED", true))
      }
    if (recentAbnormal >= 2) {
      warnings +=
        StabilityWarningItem(
          categoryId = "SESSION_ANOMALY",
          severity = StabilitySeverity.WARNING,
          title = if (isIndo) "ANOMALI SESI GAME" else "RECENT SESSION INTERRUPTIONS",
          subtitle =
            if (isIndo) "$recentAbnormal keluar tidak normal dalam 7 hari"
            else "$recentAbnormal abnormal exits recorded in 7 days",
          explanation =
            if (isIndo) {
              "DRX mencatat $recentAbnormal riwayat sesi berhenti mendadak / Crash Analyzer dalam 7 hari terakhir."
            } else {
              "DRX logged $recentAbnormal unexpected exit or Crash Analyzer incidents over the past 7 days."
            },
          recommendation =
            if (isIndo) "Rekomendasi: Periksa rincian di Crash Analyzer dan pastikan suhu & RAM aman."
            else "Recommendation: Review affected packages in Crash Analyzer and verify thermal & RAM headroom.",
          metricEvidence = "RecentAbnormalExits=$recentAbnormal (7d window)",
        )
    }

    val overall =
      when {
        warnings.any { it.severity == StabilitySeverity.CRITICAL } -> StabilitySeverity.CRITICAL
        warnings.isNotEmpty() -> StabilitySeverity.WARNING
        else -> StabilitySeverity.NORMAL
      }

    val headline =
      when (overall) {
        StabilitySeverity.CRITICAL ->
          if (isIndo) "Tekanan Kritis Terdeteksi (${warnings.size} Peringatan)"
          else "Critical Pressure Detected (${warnings.size} Warnings)"
        StabilitySeverity.WARNING ->
          if (isIndo) "Peringatan Performa Aktif (${warnings.size})"
          else "Performance Warning Active (${warnings.size})"
        StabilitySeverity.NORMAL ->
          if (isIndo) "Stabilitas Perangkat Terpantau Normal"
          else "Device Stability Normal"
      }

    val summary =
      when (overall) {
        StabilitySeverity.CRITICAL,
        StabilitySeverity.WARNING ->
          warnings.joinToString(" • ") { it.subtitle }
        StabilitySeverity.NORMAL ->
          if (isIndo) {
            "RAM bebas $availMb MB, suhu baterai ${String.format(Locale.US, "%.1f°C", tempC)}, status thermal $thermalLabel."
          } else {
            "$availMb MB free RAM, battery ${String.format(Locale.US, "%.1f°C", tempC)}, thermal status $thermalLabel."
          }
      }

    val netSummary = "${netSnap.transportType}${if (netSnap.networkSubType != "-") " (${netSnap.networkSubType})" else ""}"
    val osLimitNote =
      if (isIndo) {
        "Catatan Transparansi OS: Android membatasi aplikasi pihak ketiga untuk membaca log crash internal game atau frame time proses game lain secara langsung. Stability Watch menganalisis sensor sistem nyata (RAM, PowerManager Thermal, Baterai, Storage, Jaringan, dan riwayat Crash Analyzer lokal)."
      } else {
        "OS Transparency: Android prevents third-party apps from reading internal crash logs or per-frame FPS of other games directly. Stability Watch evaluates real system telemetry (RAM, PowerManager Thermal, Battery, Storage, Network, and local Crash Analyzer logs)."
      }

    return StabilityWatchAssessment(
      timestampMs = nowMs,
      overallSeverity = overall,
      headline = headline,
      summaryText = summary,
      warnings = warnings,
      ramAvailMb = availMb,
      ramTotalMb = totalMb,
      lowMemoryFlag = telemetry.memoryInfo.lowMemory,
      batteryLevelPercent = telemetry.batteryInfo.levelPercent,
      batteryTempC = tempC,
      thermalStatusCode = thermalCode,
      thermalLabel = thermalLabel,
      storageFreeBytes = telemetry.storageInfo.freeBytes,
      networkSummary = netSummary,
      recentAbnormalExitsCount = recentAbnormal,
      osLimitationNote = osLimitNote,
    )
  }

  fun toEntities(assessment: StabilityWatchAssessment): List<StabilityWarningEntity> =
    assessment.warnings.map { w ->
      StabilityWarningEntity(
        timestampMs = assessment.timestampMs,
        categoryId = w.categoryId,
        severity = w.severity.name,
        title = w.title,
        subtitle = w.subtitle,
        explanation = w.explanation,
        recommendation = w.recommendation,
        metricEvidence = w.metricEvidence,
        ramAvailMb = assessment.ramAvailMb,
        batteryTempC = assessment.batteryTempC,
        thermalStatusCode = assessment.thermalStatusCode,
        networkSummary = assessment.networkSummary,
      )
    }

  fun formatExportReport(
    assessment: StabilityWatchAssessment,
    history: List<StabilityWarningEntity>,
    isIndo: Boolean,
  ): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
    return buildString {
      appendLine("=== DRX GAME STABILITY WATCH REPORT ===")
      appendLine("Timestamp : ${fmt.format(Date(assessment.timestampMs))}")
      appendLine("Status    : ${assessment.overallSeverity.name} — ${assessment.headline}")
      appendLine("RAM       : ${assessment.ramAvailMb} MB free / ${assessment.ramTotalMb} MB total (LowMem=${assessment.lowMemoryFlag})")
      appendLine("Thermal   : ${assessment.thermalLabel} | Battery ${assessment.batteryLevelPercent}% (${String.format(Locale.US, "%.1f°C", assessment.batteryTempC)})")
      appendLine("Storage   : ${TimeAndSizeFormatters.formatBytesAuto(assessment.storageFreeBytes)} free")
      appendLine("Network   : ${assessment.networkSummary}")
      appendLine()
      appendLine("--- Active Warnings (${assessment.warnings.size}) ---")
      if (assessment.warnings.isEmpty()) {
        appendLine(if (isIndo) "Tidak ada peringatan stabilitas aktif." else "No active stability warnings detected.")
      } else {
        assessment.warnings.forEachIndexed { idx, w ->
          appendLine("${idx + 1}. [${w.severity.name}] ${w.title} — ${w.subtitle}")
          appendLine("   Evidence: ${w.metricEvidence}")
          appendLine("   Action  : ${w.recommendation}")
        }
      }
      appendLine()
      appendLine("--- Saved Warning History (${history.size}) ---")
      history.take(15).forEach { h ->
        appendLine("- [${fmt.format(Date(h.timestampMs))}] ${h.severity} | ${h.subtitle} (${h.metricEvidence})")
      }
      appendLine()
      appendLine(assessment.osLimitationNote)
    }
  }

  private fun thermalStatusLabel(code: Int, isIndo: Boolean): String =
    when (code) {
      PowerManager.THERMAL_STATUS_NONE -> if (isIndo) "Normal" else "None"
      PowerManager.THERMAL_STATUS_LIGHT -> if (isIndo) "Hangat Ringan" else "Light"
      PowerManager.THERMAL_STATUS_MODERATE -> if (isIndo) "Sedang" else "Moderate"
      PowerManager.THERMAL_STATUS_SEVERE -> if (isIndo) "Berat" else "Severe"
      PowerManager.THERMAL_STATUS_CRITICAL -> if (isIndo) "Kritis" else "Critical"
      PowerManager.THERMAL_STATUS_EMERGENCY,
      PowerManager.THERMAL_STATUS_SHUTDOWN -> if (isIndo) "Darurat" else "Emergency"
      else -> if (isIndo) "Normal" else "Normal"
    }
}
