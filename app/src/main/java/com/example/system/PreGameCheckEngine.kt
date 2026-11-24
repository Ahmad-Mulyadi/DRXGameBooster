package com.example.system

import android.os.PowerManager
import com.example.data.model.PreGameCheckItem
import com.example.data.model.PreGameCheckReport
import com.example.data.model.PreGameCheckStatus
import com.example.data.model.SystemTelemetry
import java.util.Locale

object PreGameCheckEngine {

  fun runCheck(
    telemetry: SystemTelemetry,
    tweaksManager: SystemTweaksManager,
    isMonitoringActive: Boolean,
    targetGameName: String? = null,
    targetPackageName: String? = null,
    isIndo: Boolean = false,
  ): PreGameCheckReport {
    val freeStorageMb = telemetry.storageInfo.freeBytes / (1024L * 1024L)
    val storageStatus =
      when {
        freeStorageMb < 1024L -> PreGameCheckStatus.WARNING
        else -> PreGameCheckStatus.READY
      }
    val storageItem =
      PreGameCheckItem(
        id = "storage",
        title = if (isIndo) "Penyimpanan Tersedia" else "Available Storage",
        valueText = TimeAndSizeFormatters.formatBytesAuto(telemetry.storageInfo.freeBytes),
        status = storageStatus,
        detailNote =
          if (storageStatus == PreGameCheckStatus.READY) {
            if (isIndo) "Ruang cukup untuk cache game & rekaman layar."
            else "Sufficient free space for game caches and recordings."
          } else {
            if (isIndo) "Ruang tersisa < 1 GB. Rekam layar & pembaruan bisa terhambat."
            else "Less than 1 GB remaining. Recordings or game updates may stutter."
          },
        tipText =
          if (storageStatus == PreGameCheckStatus.WARNING) {
            if (isIndo) "Bersihkan file media atau aplikasi tak terpakai sebelum bermain."
            else "Free up unused media or files before starting."
          } else null,
      )

    val batPct = telemetry.batteryInfo.levelPercent
    val tempC = telemetry.batteryInfo.temperatureC.sanitizeFinite(30f)
    val batteryStatus =
      when {
        batPct < 20 || tempC >= 41.5f -> PreGameCheckStatus.WARNING
        else -> PreGameCheckStatus.READY
      }
    val batteryItem =
      PreGameCheckItem(
        id = "battery_temp",
        title = if (isIndo) "Baterai & Suhu" else "Battery & Temperature",
        valueText = String.format(Locale.US, "%d%% • %.1f°C", batPct, tempC),
        status = batteryStatus,
        detailNote =
          if (telemetry.batteryInfo.isCharging) {
            if (isIndo) "Sedang mengisi daya (${telemetry.batteryInfo.chargeSource})."
            else "Charging via ${telemetry.batteryInfo.chargeSource}."
          } else {
            if (isIndo) "Berjalan dengan baterai perangkat."
            else "Running on device battery."
          },
        tipText =
          when {
            batPct < 20 ->
              if (isIndo) "Baterai rendah (< 20%). Pertimbangkan mengisi daya atau pakai mode Hemat."
              else "Battery below 20%. Consider charging or switching to Battery Saver profile."
            tempC >= 41.5f ->
              if (isIndo) "Suhu baterai sudah hangat. Hindari bermain sambil fast-charging."
              else "Battery is warm. Avoid heavy sessions while fast charging."
            else -> null
          },
      )

    val thermalCode = telemetry.thermalStatusCode
    val thermalStatus =
      when {
        thermalCode >= PowerManager.THERMAL_STATUS_MODERATE -> PreGameCheckStatus.WARNING
        else -> PreGameCheckStatus.READY
      }
    val thermalLabel =
      when (thermalCode) {
        PowerManager.THERMAL_STATUS_NONE -> if (isIndo) "Normal" else "Normal"
        PowerManager.THERMAL_STATUS_LIGHT -> if (isIndo) "Hangat Ringan" else "Light"
        PowerManager.THERMAL_STATUS_MODERATE -> if (isIndo) "Sedang (Moderate)" else "Moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> if (isIndo) "Tinggi (Severe)" else "Severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> if (isIndo) "Kritis" else "Critical"
        else -> if (isIndo) "Normal" else "Normal"
      }
    val thermalItem =
      PreGameCheckItem(
        id = "thermal",
        title = if (isIndo) "Status Thermal OS" else "OS Thermal Status",
        valueText = thermalLabel,
        status = thermalStatus,
        detailNote =
          if (thermalStatus == PreGameCheckStatus.READY) {
            if (isIndo) "Tidak ada throttling thermal terdeteksi dari PowerManager."
            else "No thermal throttling reported by Android PowerManager."
          } else {
            if (isIndo) "Sistem mendeteksi beban panas meningkat; FPS bisa dibatasi OS."
            else "Elevated thermal state detected; OS may throttle clocks."
          },
        tipText =
          if (thermalStatus == PreGameCheckStatus.WARNING) {
            if (isIndo) "Lepas casing tebal atau turunkan grafis in-game."
            else "Remove thick case or reduce in-game graphics preset."
          } else null,
      )

    val net = telemetry.networkSnapshot
    val netStatus =
      when {
        !net.isConnected -> PreGameCheckStatus.WARNING
        net.qualityLevel <= 1 -> PreGameCheckStatus.WARNING
        else -> PreGameCheckStatus.READY
      }
    val netItem =
      PreGameCheckItem(
        id = "network",
        title = if (isIndo) "Koneksi Jaringan" else "Network Connection",
        valueText =
          if (net.isConnected) {
            "${net.transportType} • ${net.linkSpeedMbps?.let { "$it Mbps" } ?: net.networkSubType}"
          } else {
            if (isIndo) "Offline" else "Offline"
          },
        status = netStatus,
        detailNote =
          if (net.isConnected) {
            if (net.isMetered) {
              if (isIndo) "Terhubung (Metered / Kuota Seluler)." else "Connected (Metered connection)."
            } else {
              if (isIndo) "Terhubung & siap untuk permainan daring." else "Connected and ready for online play."
            }
          } else {
            if (isIndo) "Tidak ada koneksi internet aktif terdeteksi." else "No active internet connection detected."
          },
        tipText =
          if (netStatus == PreGameCheckStatus.WARNING) {
            if (isIndo) "Aktifkan Wi-Fi stabil atau data seluler untuk game online."
            else "Connect to stable Wi-Fi or cellular data for online matches."
          } else null,
      )

    val hasOverlay = tweaksManager.canDrawOverlays()
    val overlayItem =
      PreGameCheckItem(
        id = "overlay",
        title = if (isIndo) "Izin Floating Overlay" else "Overlay Permission",
        valueText = if (hasOverlay) (if (isIndo) "Aktif" else "Granted") else (if (isIndo) "Tidak Aktif" else "Not Granted"),
        status = if (hasOverlay) PreGameCheckStatus.READY else PreGameCheckStatus.UNAVAILABLE,
        detailNote =
          if (hasOverlay) {
            if (isIndo) "Floating Sidebar & Live HUD dapat muncul di atas game."
            else "Floating Control Center and Live HUD can appear over games."
          } else {
            if (isIndo) "Game tetap bisa dimainkan tanpa overlay mengambang."
            else "Games still launch normally without the floating overlay."
          },
        tipText =
          if (!hasOverlay) {
            if (isIndo) "Aktifkan Display Over Other Apps jika ingin memakai sidebar."
            else "Enable Display Over Other Apps if you want the floating sidebar."
          } else null,
      )

    val recState = ScreenRecorderStateHolder.state.value
    val recReady = freeStorageMb >= 512L && !recState.isRecording
    val recItem =
      PreGameCheckItem(
        id = "recorder",
        title = if (isIndo) "Kesiapan Screen Recorder" else "Recorder Readiness",
        valueText =
          when {
            recState.isRecording -> if (isIndo) "Sedang Merekam" else "Recording Active"
            recReady -> if (isIndo) "Siap (MediaProjection)" else "Ready"
            else -> if (isIndo) "Penyimpanan Minim" else "Low Storage"
          },
        status = if (recReady || recState.isRecording) PreGameCheckStatus.READY else PreGameCheckStatus.WARNING,
        detailNote =
          if (isIndo) "Memerlukan konfirmasi sistem Android saat mulai merekam."
          else "Requires standard Android MediaProjection prompt when started.",
      )

    val monStatus =
      when {
        !isMonitoringActive -> PreGameCheckStatus.UNAVAILABLE
        telemetry.cpuInfo.isUsageRestricted -> PreGameCheckStatus.READY
        else -> PreGameCheckStatus.READY
      }
    val monItem =
      PreGameCheckItem(
        id = "monitoring",
        title = if (isIndo) "Status Monitoring Telemetri" else "Telemetry Monitoring",
        valueText =
          when {
            !isMonitoringActive -> if (isIndo) "Dijeda (Idle)" else "Paused (Idle)"
            telemetry.cpuInfo.isUsageRestricted -> if (isIndo) "Aktif (CPU Dibatasi OS)" else "Active (CPU OS-Restricted)"
            else -> if (isIndo) "Aktif Penuh" else "Full Active"
          },
        status = monStatus,
        detailNote =
          if (!isMonitoringActive) {
            if (isIndo) "Aktifkan Device Monitoring untuk mencatat Timeline & Resource Report."
            else "Enable Device Monitoring to log timeline events and resource reports."
          } else {
            if (isIndo) "Memantau RAM, suhu, baterai, jaringan, dan UI FPS."
            else "Actively monitoring RAM, temperature, battery, network, and UI FPS."
          },
      )

    val items = listOf(storageItem, batteryItem, thermalItem, netItem, overlayItem, recItem, monItem)
    val overall =
      when {
        items.any { it.status == PreGameCheckStatus.WARNING } -> PreGameCheckStatus.WARNING
        items.all { it.status == PreGameCheckStatus.READY } -> PreGameCheckStatus.READY
        else -> PreGameCheckStatus.UNAVAILABLE
      }

    val summaryTip =
      when (overall) {
        PreGameCheckStatus.READY ->
          if (isIndo) "Perangkat dalam kondisi optimal. Anda siap bermain."
          else "Device conditions look healthy. Ready for smooth gameplay."
        PreGameCheckStatus.WARNING ->
          if (isIndo) "Ada beberapa catatan kondisi perangkat, namun Anda tetap dapat langsung bermain."
          else "A few advisories were detected, though you can always launch immediately."
        PreGameCheckStatus.UNAVAILABLE ->
          if (isIndo) "Beberapa fitur opsional (seperti overlay) nonaktif. Game tetap siap dijalankan."
          else "Optional permissions are off, but your game is ready to launch."
      }

    return PreGameCheckReport(
      targetGameName = targetGameName,
      targetPackageName = targetPackageName,
      overallStatus = overall,
      checkedAtMs = System.currentTimeMillis(),
      items = items,
      summaryTip = summaryTip,
    )
  }
}
