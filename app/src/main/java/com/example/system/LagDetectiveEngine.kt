package com.example.system

import android.os.PowerManager
import com.example.data.model.LagCategory
import com.example.data.model.LagCauseFinding
import com.example.data.model.LagDiagnosticReport
import com.example.data.model.LagMetricTileData
import com.example.data.model.LagStatus
import com.example.data.model.LagThresholdConfig
import com.example.data.model.NetworkDiagnosticResult
import com.example.data.model.SystemTelemetry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LagDetectiveEngine {

  fun calibrateThresholdConfig(
    telemetry: SystemTelemetry,
    customRamWarningMb: Int = 0,
    customThermalC: Float = 40.0f,
    customPingMs: Int = 90,
    customStorageMb: Int = 1024,
  ): LagThresholdConfig {
    val totalRamMb = (telemetry.memoryInfo.totalBytes / (1024L * 1024L)).coerceAtLeast(1024L)
    val autoRamMb = (totalRamMb / 6L).coerceIn(450L, 1400L)
    val effectiveRamMb = if (customRamWarningMb > 0) customRamWarningMb.toLong() else autoRamMb
    return LagThresholdConfig(
      ramWarningFreeMb = effectiveRamMb,
      thermalWarningC = customThermalC.coerceIn(34f, 52f),
      pingWarningMs = customPingMs.toFloat().coerceIn(20f, 400f),
      storageWarningFreeMb = customStorageMb.toLong().coerceIn(200L, 16384L),
    )
  }

  fun analyze(
    telemetry: SystemTelemetry,
    networkDiag: NetworkDiagnosticResult?,
    recordingsFootprintBytes: Long,
    thresholdConfig: LagThresholdConfig,
    isIndo: Boolean,
  ): LagDiagnosticReport {
    val findings = mutableListOf<LagCauseFinding>()
    val tiles = mutableListOf<LagMetricTileData>()

    val availRamMb = (telemetry.memoryInfo.availBytes / (1024L * 1024L)).coerceAtLeast(0L)
    val totalRamMb = (telemetry.memoryInfo.totalBytes / (1024L * 1024L)).coerceAtLeast(1L)
    val ramUsedPct = telemetry.memoryInfo.usedPercent.sanitizeFinite(0f)

    val tempC = telemetry.batteryInfo.temperatureC.sanitizeFinite(30f)
    val thermalCode = telemetry.thermalStatusCode
    val storageFreeMb = (telemetry.storageInfo.freeBytes / (1024L * 1024L)).coerceAtLeast(0L)

    // 1. UI Frame Rate & Frame Pacing
    val uiFps = telemetry.uiFps.sanitizeFinite(60f)
    val frameTime = telemetry.frameTimeMs.sanitizeFinite(16.6f)
    val refreshHz = telemetry.displayInfo.refreshRateHz.sanitizeFinite(60f)
    val fpsStatus =
      when {
        uiFps < refreshHz * 0.55f || frameTime > 34f -> LagStatus.UNSTABLE
        uiFps < refreshHz * 0.78f || frameTime > 24f -> LagStatus.WARNING
        else -> LagStatus.NORMAL
      }
    tiles +=
      LagMetricTileData(
        key = "ui_fps",
        label = if (isIndo) "UI FPS / Frame Time" else "UI FPS / Frame Time",
        valueText = "${uiFps.safeRoundToInt(60)} FPS",
        subText = String.format(Locale.US, "%.1f ms • %.0f Hz", frameTime, refreshHz),
        status = fpsStatus,
      )

    // 2. CPU Telemetry (with strict OS restriction disclosure)
    val cpuUsage = telemetry.cpuInfo.usagePercent
    val cpuCurMhz = telemetry.cpuInfo.currentFreqMhz
    val cpuMaxMhz = telemetry.cpuInfo.maxFreqMhz
    val cpuRatioPct =
      if (cpuUsage != null) {
        cpuUsage.sanitizeFinite(0f)
      } else if (cpuCurMhz != null && cpuMaxMhz != null && cpuMaxMhz > 0) {
        ((cpuCurMhz.toFloat() / cpuMaxMhz.toFloat()) * 100f).coerceIn(0f, 100f)
      } else {
        null
      }
    val cpuStatus =
      when {
        cpuRatioPct == null -> LagStatus.DATA_UNAVAILABLE
        cpuRatioPct >= 86f -> LagStatus.HIGH_LOAD
        cpuRatioPct >= 70f -> LagStatus.WARNING
        else -> LagStatus.NORMAL
      }
    tiles +=
      LagMetricTileData(
        key = "cpu",
        label = if (isIndo) "Kondisi CPU" else "CPU Activity",
        valueText =
          cpuUsage?.let { "${it.safeRoundToInt()}%" }
            ?: cpuCurMhz?.let { "$it MHz" }
            ?: if (isIndo) "Dibatasi OS" else "OS Restricted",
        subText =
          if (cpuUsage != null) "${telemetry.cpuInfo.cores} Cores"
          else if (cpuCurMhz != null) "${telemetry.cpuInfo.cores}C • /proc/stat OS-blocked"
          else "${telemetry.cpuInfo.cores} Cores • ${telemetry.cpuInfo.abi}",
        status = cpuStatus,
      )
    findings +=
      when (cpuStatus) {
        LagStatus.DATA_UNAVAILABLE ->
          LagCauseFinding(
            category = LagCategory.CPU_PRESSURE,
            status = LagStatus.DATA_UNAVAILABLE,
            title =
              if (isIndo) "CPU Usage Dibatasi Android OS"
              else "CPU Usage Restricted by Android OS",
            explanation =
              if (isIndo) "Android 8+ memblokir akses langsung /proc/stat untuk aplikasi non-root."
              else "Android 8+ restricts direct /proc/stat CPU load reading for non-root apps.",
            evidence = "${telemetry.cpuInfo.cores} Cores (${telemetry.cpuInfo.abi})",
            recommendation =
              if (isIndo) "Gunakan pemantauan suhu, RAM, dan kestabilan frame rate sebagai indikator beban kerja perangkat."
              else "Use thermal status, RAM pressure, and frame pacing as reliable workload indicators.",
          )
        LagStatus.HIGH_LOAD, LagStatus.WARNING ->
          LagCauseFinding(
            category = LagCategory.CPU_PRESSURE,
            status = cpuStatus,
            title =
              if (isIndo) "Beban CPU Tinggi Terdeteksi"
              else "Elevated CPU Activity Detected",
            explanation =
              if (isIndo) "Frekuensi atau pemanfaatan CPU berada di zona tinggi yang berpotensi memicu frame drop."
              else "CPU utilization or clock scaling is elevated, which may increase frame drops.",
            evidence =
              cpuUsage?.let { "${it.safeRoundToInt()}% CPU load" }
                ?: "${cpuCurMhz ?: 0}/${cpuMaxMhz ?: 0} MHz",
            recommendation =
              if (isIndo) "Tutup aplikasi latar belakang berat atau turunkan preset grafis game."
              else "Close heavy background apps or lower in-game graphics settings.",
          )
        else ->
          LagCauseFinding(
            category = LagCategory.CPU_PRESSURE,
            status = LagStatus.NORMAL,
            title = if (isIndo) "Aktivitas CPU Stabil" else "CPU Activity Normal",
            explanation =
              if (isIndo) "Pembacaan CPU yang tersedia berada di rentang aman."
              else "Accessible CPU metrics are within normal operating range.",
            evidence =
              cpuUsage?.let { "${it.safeRoundToInt()}%" }
                ?: cpuCurMhz?.let { "$it MHz" }
                ?: "${telemetry.cpuInfo.cores} Cores",
            recommendation = if (isIndo) "Pertahankan profil saat ini." else "Keep current profile.",
          )
      }

    // 3. Memory Pressure
    val memStatus =
      when {
        telemetry.memoryInfo.lowMemory || availRamMb < (thresholdConfig.ramWarningFreeMb * 0.65f) ->
          LagStatus.HIGH_LOAD
        availRamMb < thresholdConfig.ramWarningFreeMb || ramUsedPct >= 85f ->
          LagStatus.WARNING
        else -> LagStatus.NORMAL
      }
    tiles +=
      LagMetricTileData(
        key = "ram",
        label = "RAM",
        valueText = "$availRamMb MB",
        subText =
          if (isIndo) "Tersedia dari $totalRamMb MB (${ramUsedPct.safeRoundToInt()}% terpakai)"
          else "Free of $totalRamMb MB (${ramUsedPct.safeRoundToInt()}% used)",
        status = memStatus,
      )
    findings +=
      when (memStatus) {
        LagStatus.HIGH_LOAD ->
          LagCauseFinding(
            category = LagCategory.MEMORY_PRESSURE,
            status = LagStatus.HIGH_LOAD,
            title =
              if (isIndo) "Tekanan Memori Tinggi (Low Available RAM)"
              else "Possible Memory Pressure: Available RAM Critically Low",
            explanation =
              if (isIndo) "Sisa RAM perangkat menipis sehingga sistem dapat memicu garbage collection atau menghentikan proses latar belakang."
              else "Available RAM is critically low; Android LMK may pause or reload assets mid-game.",
            evidence = "$availRamMb MB free / $totalRamMb MB total (${ramUsedPct.safeRoundToInt()}% used)",
            recommendation =
              if (isIndo) "Tutup aplikasi lain yang tidak diperlukan sebelum bermain dan jalankan Boost Now."
              else "Close unused background apps before gaming and run Boost Now.",
          )
        LagStatus.WARNING ->
          LagCauseFinding(
            category = LagCategory.MEMORY_PRESSURE,
            status = LagStatus.WARNING,
            title =
              if (isIndo) "Kemungkinan Memory Pressure Meningkat"
              else "Possible Memory Pressure: Available memory is becoming limited",
            explanation =
              if (isIndo) "Penggunaan RAM perangkat mendekati ambang batas optimal perangkat ini."
              else "Device RAM usage is approaching the calibrated warning threshold.",
            evidence = "$availRamMb MB free (${ramUsedPct.safeRoundToInt()}% used)",
            recommendation =
              if (isIndo) "Pertimbangkan menutup aplikasi yang tidak diperlukan sebelum bermain."
              else "Consider closing unnecessary apps before playing.",
          )
        else ->
          LagCauseFinding(
            category = LagCategory.MEMORY_PRESSURE,
            status = LagStatus.NORMAL,
            title = if (isIndo) "Kapasitas RAM Lega" else "Memory Headroom Healthy",
            explanation =
              if (isIndo) "RAM tersedia mencukupi untuk menjalankan game tanpa tekanan memori."
              else "Available RAM provides healthy headroom for smooth gaming.",
            evidence = "$availRamMb MB free / $totalRamMb MB",
            recommendation = if (isIndo) "Tidak ada tindakan RAM diperlukan." else "No memory action needed.",
          )
      }

    // 4. Thermal Condition (Battery sensor + Android PowerManager Thermal Status API)
    val thermalStatus =
      when {
        thermalCode >= PowerManager.THERMAL_STATUS_SEVERE || tempC >= thresholdConfig.thermalWarningC + 4f ->
          LagStatus.HIGH_LOAD
        thermalCode >= PowerManager.THERMAL_STATUS_MODERATE || tempC >= thresholdConfig.thermalWarningC ->
          LagStatus.WARNING
        else -> LagStatus.NORMAL
      }
    val thermalApiName =
      when (thermalCode) {
        PowerManager.THERMAL_STATUS_NONE -> "None"
        PowerManager.THERMAL_STATUS_LIGHT -> "Light"
        PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
        PowerManager.THERMAL_STATUS_EMERGENCY, PowerManager.THERMAL_STATUS_SHUTDOWN -> "Extreme"
        else -> "Standard"
      }
    tiles +=
      LagMetricTileData(
        key = "thermal",
        label = if (isIndo) "Suhu Baterai & Thermal" else "Battery Temp & Thermal",
        valueText = String.format(Locale.US, "%.1f°C", tempC),
        subText = "Thermal API: $thermalApiName • Bat ${telemetry.batteryInfo.levelPercent}%",
        status = thermalStatus,
      )
    findings +=
      when (thermalStatus) {
        LagStatus.HIGH_LOAD ->
          LagCauseFinding(
            category = LagCategory.THERMAL_CONDITION,
            status = LagStatus.HIGH_LOAD,
            title =
              if (isIndo) "Peringatan Panas & Potensi Thermal Throttling"
              else "Thermal Warning: Severe Heat / Possible Throttling",
            explanation =
              if (isIndo) "Suhu baterai tinggi dan status thermal OS mengindikasikan sistem dapat menurunkan clock CPU/GPU secara otomatis."
              else "Battery temperature and Android Thermal API indicate likely system frequency throttling.",
            evidence = String.format(Locale.US, "Battery %.1f°C • Thermal API: %s", tempC, thermalApiName),
            recommendation =
              if (isIndo) "Lepas charger saat bermain, turunkan kecerahan layar, dan istirahatkan perangkat sejenak."
              else "Unplug charger while gaming, lower screen brightness, and let the device cool down.",
          )
        LagStatus.WARNING ->
          LagCauseFinding(
            category = LagCategory.THERMAL_CONDITION,
            status = LagStatus.WARNING,
            title =
              if (isIndo) "Suhu Perangkat Mulai Meningkat"
              else "Thermal Warning: Device temperature is elevated",
            explanation =
              if (isIndo) "Suhu sensor baterai melebihi ambang peringatan (${thresholdConfig.thermalWarningC.safeRoundToInt()}°C)."
              else "Battery sensor temperature exceeds warning threshold (${thresholdConfig.thermalWarningC.safeRoundToInt()}°C).",
            evidence = String.format(Locale.US, "Battery %.1f°C • Thermal API: %s", tempC, thermalApiName),
            recommendation =
              if (isIndo) "Kurangi kecerahan, gunakan profil Balanced, atau main di ruangan lebih sejuk."
              else "Lower brightness, switch to Balanced profile, or improve ventilation.",
          )
        else ->
          LagCauseFinding(
            category = LagCategory.THERMAL_CONDITION,
            status = LagStatus.NORMAL,
            title = if (isIndo) "Kondisi Thermal Normal" else "Thermal Condition Normal",
            explanation =
              if (isIndo) "Suhu baterai dan status thermal Android stabil (sensor baterai; CPU die tidak diekspos Android)."
              else "Battery temp and Android thermal status are normal (battery sensor; CPU die temp is OS-restricted).",
            evidence = String.format(Locale.US, "Battery %.1f°C • Thermal API: %s", tempC, thermalApiName),
            recommendation = if (isIndo) "Aman untuk sesi bermain reguler." else "Safe for extended gaming.",
          )
      }

    // 5. Network Instability
    val netSnap = telemetry.networkSnapshot
    val netStatus =
      when {
        !netSnap.isConnected -> LagStatus.UNSTABLE
        networkDiag == null ->
          if (netSnap.qualityLevel <= 1) LagStatus.WARNING else LagStatus.NORMAL
        networkDiag.packetLossPercent >= 5f ||
          networkDiag.pingMs >= thresholdConfig.pingWarningMs * 1.6f ||
          networkDiag.jitterMs >= 35f -> LagStatus.UNSTABLE
        networkDiag.pingMs >= thresholdConfig.pingWarningMs || networkDiag.jitterMs >= 18f ->
          LagStatus.WARNING
        else -> LagStatus.NORMAL
      }
    tiles +=
      LagMetricTileData(
        key = "network",
        label = if (isIndo) "Jaringan (${netSnap.transportType})" else "Network (${netSnap.transportType})",
        valueText =
          when {
            !netSnap.isConnected -> if (isIndo) "Offline" else "Offline"
            networkDiag != null -> "${networkDiag.pingMs.safeRoundToInt()} ms"
            else -> netSnap.linkSpeedMbps?.let { "$it Mbps" } ?: netSnap.networkSubType
          },
        subText =
          if (networkDiag != null) {
            String.format(
              Locale.US,
              "Endpoint • Jitter %.1fms • Loss %.0f%%",
              networkDiag.jitterMs,
              networkDiag.packetLossPercent,
            )
          } else {
            if (isIndo) "Tekan Scan untuk uji ping endpoint" else "Tap Scan for endpoint latency probe"
          },
        status = netStatus,
      )
    findings +=
      when {
        !netSnap.isConnected ->
          LagCauseFinding(
            category = LagCategory.NETWORK_INSTABILITY,
            status = LagStatus.UNSTABLE,
            title = if (isIndo) "Tidak Ada Koneksi Internet" else "Network Disconnected",
            explanation =
              if (isIndo) "Perangkat tidak terhubung ke Wi-Fi maupun data seluler."
              else "Device has no active internet transport.",
            evidence = "Transport: ${netSnap.transportType}",
            recommendation =
              if (isIndo) "Aktifkan Wi-Fi stabil atau data seluler sebelum masuk match online."
              else "Enable stable Wi-Fi or cellular data before online matches.",
          )
        netStatus == LagStatus.UNSTABLE || netStatus == LagStatus.WARNING ->
          LagCauseFinding(
            category = LagCategory.NETWORK_INSTABILITY,
            status = netStatus,
            title =
              if (isIndo) "Ketidakstabilan Koneksi Terdeteksi"
              else "Network Instability Detected",
            explanation =
              if (isIndo) "Latensi atau jitter ke endpoint diagnostik (bukan server internal game) sedang tinggi/fluktuatif."
              else "Latency or jitter to diagnostic endpoint (not an internal game server) is elevated.",
            evidence =
              networkDiag?.let {
                String.format(Locale.US, "Endpoint Ping %.1fms • Jitter %.1fms • Loss %.0f%%", it.pingMs, it.jitterMs, it.packetLossPercent)
              } ?: "${netSnap.transportType} • ${netSnap.networkSubType}",
            recommendation =
              if (isIndo) "Dekatkan posisi ke router 5GHz, hentikan unduhan latar belakang, atau ganti jalur jaringan."
              else "Move closer to 5GHz router, pause background downloads, or switch network.",
          )
        else ->
          LagCauseFinding(
            category = LagCategory.NETWORK_INSTABILITY,
            status = LagStatus.NORMAL,
            title = if (isIndo) "Koneksi Terlihat Stabil" else "Network Appears Stable",
            explanation =
              if (isIndo) "Pengukuran ke endpoint diagnostik berada pada rentang responsif."
              else "Diagnostic endpoint measurements are responsive.",
            evidence =
              networkDiag?.let {
                String.format(Locale.US, "Ping %.1fms • Jitter %.1fms • Loss %.0f%%", it.pingMs, it.jitterMs, it.packetLossPercent)
              } ?: "${netSnap.transportType} (${netSnap.networkSubType})",
            recommendation = if (isIndo) "Koneksi siap untuk sesi online." else "Ready for online sessions.",
          )
      }

    // 6. Storage Pressure
    val storageStatus =
      when {
        storageFreeMb < 350L -> LagStatus.HIGH_LOAD
        storageFreeMb < thresholdConfig.storageWarningFreeMb -> LagStatus.WARNING
        else -> LagStatus.NORMAL
      }
    val recMb = recordingsFootprintBytes / (1024L * 1024L)
    tiles +=
      LagMetricTileData(
        key = "storage",
        label = if (isIndo) "Ruang Penyimpanan" else "Storage Free",
        valueText = TimeAndSizeFormatters.formatBytesAuto(telemetry.storageInfo.freeBytes),
        subText =
          if (recMb > 0) "DRX Media: $recMb MB"
          else "${telemetry.storageInfo.usedPercent.safeRoundToInt()}% used",
        status = storageStatus,
      )
    findings +=
      when (storageStatus) {
        LagStatus.HIGH_LOAD, LagStatus.WARNING ->
          LagCauseFinding(
            category = LagCategory.STORAGE_PRESSURE,
            status = storageStatus,
            title = if (isIndo) "Ruang Penyimpanan Mulai Penuh" else "Low Device Storage Warning",
            explanation =
              if (isIndo) "Sisa ruang penyimpanan rendah dapat memperlambat caching tekstur dan mencegah Screen Recorder menyimpan video."
              else "Low free storage can slow shader caching and prevent Screen Recorder from saving videos.",
            evidence = "Free ${TimeAndSizeFormatters.formatBytesAuto(telemetry.storageInfo.freeBytes)} (DRX Media: $recMb MB)",
            recommendation =
              if (isIndo) "Hapus rekaman lama di DRX Media Gallery atau bersihkan file besar yang tidak terpakai."
              else "Remove old recordings in DRX Media Gallery or free up unused files.",
          )
        else ->
          LagCauseFinding(
            category = LagCategory.STORAGE_PRESSURE,
            status = LagStatus.NORMAL,
            title = if (isIndo) "Penyimpanan Mencukupi" else "Storage Capacity Healthy",
            explanation =
              if (isIndo) "Ruang penyimpanan cukup untuk cache game dan perekaman layar."
              else "Free space is sufficient for game assets and screen recording.",
            evidence = "Free ${TimeAndSizeFormatters.formatBytesAuto(telemetry.storageInfo.freeBytes)}",
            recommendation = if (isIndo) "Tidak ada pembersihan mendesak." else "No cleanup needed right now.",
          )
      }

    // Determine main suspected cause & completeness confidence
    val nonNormalFindings =
      findings.filter { it.status == LagStatus.HIGH_LOAD || it.status == LagStatus.UNSTABLE || it.status == LagStatus.WARNING }
    val worstStatus =
      when {
        findings.any { it.status == LagStatus.HIGH_LOAD } -> LagStatus.HIGH_LOAD
        findings.any { it.status == LagStatus.UNSTABLE } -> LagStatus.UNSTABLE
        findings.any { it.status == LagStatus.WARNING } -> LagStatus.WARNING
        else -> LagStatus.NORMAL
      }
    val prioritizedFinding =
      nonNormalFindings.maxByOrNull {
        when (it.status) {
          LagStatus.HIGH_LOAD -> 3
          LagStatus.UNSTABLE -> 3
          LagStatus.WARNING -> 2
          else -> 1
        }
      }

    val availableSignals =
      listOfNotNull(
        if (telemetry.memoryInfo.totalBytes > 0) "RAM" else null,
        if (tempC > 0f) "Thermal" else null,
        if (telemetry.storageInfo.totalBytes > 0) "Storage" else null,
        if (cpuRatioPct != null) "CPU" else null,
        if (networkDiag != null || !netSnap.isConnected) "NetworkProbe" else null,
        "UI_FPS",
      ).size
    val confidence = ((availableSignals / 6f) * 100f).safeRoundToInt(70).coerceIn(45, 96)

    val mainSuspected =
      prioritizedFinding?.title
        ?: if (isIndo) "Tidak Ada Hambatan Besar Terdeteksi" else "No Major Bottleneck Detected"
    val severityLabel =
      when (worstStatus) {
        LagStatus.HIGH_LOAD, LagStatus.UNSTABLE -> if (isIndo) "Tinggi" else "High"
        LagStatus.WARNING -> if (isIndo) "Sedang" else "Moderate"
        else -> if (isIndo) "Optimal" else "Optimal"
      }
    val completenessNote =
      if (isIndo) {
        "Kelengkapan Data: $availableSignals/6 indikator terukur ($confidence%)." +
          if (cpuRatioPct == null) " Beban CPU dibatasi Android OS." else ""
      } else {
        "Data Completeness: $availableSignals/6 signals available ($confidence%)." +
          if (cpuRatioPct == null) " Raw CPU load restricted by Android OS." else ""
      }

    return LagDiagnosticReport(
      timestampMs = System.currentTimeMillis(),
      overallStatus = worstStatus,
      mainSuspectedIssue = mainSuspected,
      severityLabel = severityLabel,
      confidencePercent = confidence,
      completenessNote = completenessNote,
      metricTiles = tiles,
      findings = findings,
      recommendationSummary =
        prioritizedFinding?.recommendation
          ?: if (isIndo) "Perangkat dalam kondisi optimal untuk bermain." else "Device is in healthy gaming condition.",
      ramFreeMb = availRamMb,
      ramTotalMb = totalRamMb,
      batteryTempC = tempC,
      thermalStatusCode = thermalCode,
      storageFreeBytes = telemetry.storageInfo.freeBytes,
      networkType = netSnap.transportType,
      networkPingMs = networkDiag?.pingMs,
      networkJitterMs = networkDiag?.jitterMs,
      networkLossPercent = networkDiag?.packetLossPercent,
    )
  }

  fun formatShareReport(report: LagDiagnosticReport, isIndo: Boolean): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val timeStr = fmt.format(Date(report.timestampMs))
    val header = if (isIndo) "=== LAPORAN DRX LAG DETECTIVE ===" else "=== DRX LAG DETECTIVE REPORT ==="
    val lines = mutableListOf(
      header,
      "Waktu / Time: $timeStr",
      "Status: ${report.overallStatus.label} (${report.severityLabel})",
      "Temuan Utama: ${report.mainSuspectedIssue}",
      "Confidence: ${report.confidencePercent}% (${report.completenessNote})",
      "RAM Free: ${report.ramFreeMb} MB / ${report.ramTotalMb} MB",
      "Battery Temp: ${String.format(Locale.US, "%.1f°C", report.batteryTempC)}",
      "Storage Free: ${TimeAndSizeFormatters.formatBytesAuto(report.storageFreeBytes)}",
      "Network: ${report.networkType}" + (report.networkPingMs?.let { " • Ping ${it.safeRoundToInt()}ms" } ?: ""),
      "",
      if (isIndo) "Rincian Analisis:" else "Findings:",
    )
    report.findings.forEach { f ->
      lines += "- [${f.status.label}] ${f.title}: ${f.evidence}"
      lines += "  Saran: ${f.recommendation}"
    }
    return lines.joinToString("\n")
  }
}
