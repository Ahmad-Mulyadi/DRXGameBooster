package com.example.system

import android.os.BatteryManager
import com.example.data.model.GamingHealthReport
import com.example.data.model.HealthCategoryScore
import com.example.data.model.NetworkDiagnosticResult
import com.example.data.model.SystemTelemetry
import java.util.Locale

class GamingHealthEngine {

  fun evaluateHealth(
    telemetry: SystemTelemetry,
    networkDiag: NetworkDiagnosticResult?,
    isFullDiagnostic: Boolean,
    isIndo: Boolean,
  ): GamingHealthReport {
    val categories = mutableListOf<HealthCategoryScore>()
    val recs = mutableListOf<String>()

    // 1. Memory Health
    val memPct = telemetry.memoryInfo.usedPercent.sanitizeFinite(0f)
    val availMb = (telemetry.memoryInfo.availBytes / (1024L * 1024L)).coerceAtLeast(0L)
    val memScore =
      if (telemetry.memoryInfo.totalBytes <= 0L) null
      else (100f - memPct * 0.9f - if (telemetry.memoryInfo.lowMemory) 25f else 0f).safeRoundToInt(70).coerceIn(10, 100)
    categories +=
      HealthCategoryScore(
        categoryId = "memory",
        title = if (isIndo) "Kesehatan Memori (RAM)" else "Memory Health",
        scoreOrNull = memScore,
        statusLabel = scoreStatusLabel(memScore, isIndo),
        detailText = "$availMb MB free (${memPct.safeRoundToInt()}% used)",
      )
    if (memScore != null && memScore < 65) {
      recs +=
        if (isIndo) "Tutup aplikasi latar belakang dan jalankan Boost Now sebelum bermain."
        else "Close background apps and run Boost Now before gaming."
    }

    // 2. Thermal Health
    val tempC = telemetry.batteryInfo.temperatureC.sanitizeFinite(30f)
    val thermalPenalty =
      when {
        telemetry.thermalStatusCode >= 3 -> 45
        telemetry.thermalStatusCode == 2 -> 25
        telemetry.thermalStatusCode == 1 -> 10
        else -> 0
      }
    val thermalScore =
      if (tempC <= 0f) null
      else (100 - ((tempC - 30f).coerceAtLeast(0f) * 4.2f).safeRoundToInt() - thermalPenalty).coerceIn(10, 100)
    categories +=
      HealthCategoryScore(
        categoryId = "thermal",
        title = if (isIndo) "Kondisi Thermal" else "Thermal Health",
        scoreOrNull = thermalScore,
        statusLabel = scoreStatusLabel(thermalScore, isIndo),
        detailText = String.format(Locale.US, "Battery %.1f°C • OS Thermal %d", tempC, telemetry.thermalStatusCode),
      )
    if (thermalScore != null && thermalScore < 65) {
      recs +=
        if (isIndo) "Biarkan perangkat dingin sejenak dan hindari bermain sambil fast-charging."
        else "Allow the device to cool and avoid gaming while fast charging."
    }

    // 3. Battery Condition
    val batPct = telemetry.batteryInfo.levelPercent
    val batHealthPenalty =
      if (telemetry.batteryInfo.healthCode == BatteryManager.BATTERY_HEALTH_GOOD) 0 else 25
    val batScore =
      ((batPct * 0.85f) + 15f - batHealthPenalty).safeRoundToInt(75).coerceIn(10, 100)
    categories +=
      HealthCategoryScore(
        categoryId = "battery",
        title = if (isIndo) "Kondisi Baterai" else "Battery Condition",
        scoreOrNull = batScore,
        statusLabel = scoreStatusLabel(batScore, isIndo),
        detailText = "$batPct% • ${telemetry.batteryInfo.chargeSource}",
      )
    if (batPct < 25 && !telemetry.batteryInfo.isCharging) {
      recs +=
        if (isIndo) "Isi daya baterai sebelum sesi kompetitif agar tidak terjadi low-battery throttling."
        else "Charge battery before ranked sessions to prevent low-battery throttling."
    }

    // 4. Storage Health
    val freeGb = telemetry.storageInfo.freeBytes / (1024.0 * 1024.0 * 1024.0)
    val stoScore =
      if (telemetry.storageInfo.totalBytes <= 0L) null
      else when {
        freeGb >= 10.0 -> 96
        freeGb >= 4.0 -> 82
        freeGb >= 1.5 -> 62
        freeGb >= 0.5 -> 38
        else -> 18
      }
    categories +=
      HealthCategoryScore(
        categoryId = "storage",
        title = if (isIndo) "Kesehatan Penyimpanan" else "Storage Health",
        scoreOrNull = stoScore,
        statusLabel = scoreStatusLabel(stoScore, isIndo),
        detailText = "Free ${TimeAndSizeFormatters.formatBytesAuto(telemetry.storageInfo.freeBytes)}",
      )
    if (stoScore != null && stoScore < 60) {
      recs +=
        if (isIndo) "Bersihkan rekaman video lama atau cache berlebih agar penyimpanan minimal 2 GB bebas."
        else "Delete old recordings or caches to maintain at least 2 GB free space."
    }

    // 5. Network Stability
    val netSnap = telemetry.networkSnapshot
    val netScore: Int? =
      when {
        !netSnap.isConnected -> 15
        networkDiag != null -> networkDiag.stabilityScore.coerceIn(10, 100)
        isFullDiagnostic -> null // No ping measurement available
        else ->
          when (netSnap.qualityLevel) {
            4 -> 92
            3 -> 80
            2 -> 60
            else -> 40
          }
      }
    categories +=
      HealthCategoryScore(
        categoryId = "network",
        title = if (isIndo) "Stabilitas Jaringan" else "Network Stability",
        scoreOrNull = netScore,
        statusLabel = scoreStatusLabel(netScore, isIndo),
        detailText =
          when {
            !netSnap.isConnected -> "Offline"
            networkDiag != null ->
              String.format(Locale.US, "%s • Ping %.0fms • Jitter %.1fms", netSnap.transportType, networkDiag.pingMs, networkDiag.jitterMs)
            else -> "${netSnap.transportType} • ${netSnap.networkSubType}"
          },
      )
    if (netScore != null && netScore < 65) {
      recs +=
        if (isIndo) "Gunakan Wi-Fi 5GHz stabil atau jalankan tes ping sebelum masuk game online."
        else "Switch to stable 5GHz Wi-Fi or run a ping check before ranked play."
    }

    // 6. Performance Stability (UI frame rate / pacing)
    val refresh = telemetry.displayInfo.refreshRateHz.sanitizeFinite(60f).coerceAtLeast(30f)
    val uiFps = telemetry.uiFps.sanitizeFinite(60f)
    val perfScore: Int? =
      if (uiFps <= 0f) null
      else ((uiFps / refresh).coerceIn(0.3f, 1.0f) * 100f).safeRoundToInt(80).coerceIn(15, 100)
    categories +=
      HealthCategoryScore(
        categoryId = "performance",
        title = if (isIndo) "Stabilitas Frame UI" else "UI Performance Stability",
        scoreOrNull = perfScore,
        statusLabel = scoreStatusLabel(perfScore, isIndo),
        detailText = "${uiFps.safeRoundToInt(60)} UI FPS / ${refresh.safeRoundToInt(60)} Hz",
      )

    val validScores = categories.mapNotNull { it.scoreOrNull }
    val overall = if (validScores.isEmpty()) null else (validScores.sum().toFloat() / validScores.size.toFloat()).safeRoundToInt()

    val (readinessTitle, readinessDetail) =
      when {
        overall == null ->
          (if (isIndo) "DATA BELUM LENGKAP" else "INCOMPLETE DATA") to
            (if (isIndo) "Sebagian sensor perangkat tidak tersedia." else "Some hardware telemetry sensors are unavailable.")
        overall >= 80 ->
          (if (isIndo) "SIAP BERMAIN OPTIMAL" else "GAMING READY") to
            (if (isIndo) "RAM, suhu, baterai, penyimpanan & jaringan dalam kondisi prima." else "Memory, thermal, battery, storage & network are healthy.")
        overall >= 60 ->
          (if (isIndo) "CUKUP SIAP" else "FAIR READINESS") to
            (if (isIndo) "Masih layak bermain, namun optimasi ringan disarankan." else "Ready for play, though light optimization is recommended.")
        else ->
          (if (isIndo) "PERLU OPTIMASI" else "NEEDS ATTENTION") to
            (if (isIndo) "Terdapat kendala RAM/suhu/penyimpanan/jaringan yang perlu diperbaiki dulu." else "Address memory, thermal, storage, or network warnings before playing.")
      }

    if (recs.isEmpty()) {
      recs +=
        if (isIndo) "Semua parameter utama sehat. Aktifkan mode Performance saat mulai bermain."
        else "All primary indicators are healthy. Engage Performance profile when ready."
    }

    return GamingHealthReport(
      timestampMs = System.currentTimeMillis(),
      isFullDiagnostic = isFullDiagnostic,
      overallScoreOrNull = overall,
      readinessTitle = readinessTitle,
      readinessDetail = readinessDetail,
      categories = categories,
      recommendations = recs,
    )
  }

  private fun scoreStatusLabel(score: Int?, isIndo: Boolean): String =
    when {
      score == null -> "Not Available"
      score >= 80 -> if (isIndo) "Optimal" else "Optimal"
      score >= 60 -> if (isIndo) "Cukup" else "Fair"
      else -> if (isIndo) "Perlu Perhatian" else "Attention"
    }
}
