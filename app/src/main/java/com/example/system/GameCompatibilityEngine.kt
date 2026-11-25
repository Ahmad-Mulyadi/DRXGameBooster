package com.example.system

import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import com.example.BuildConfig
import com.example.data.db.GameEntity
import com.example.data.model.CompatibilityCheckItem
import com.example.data.model.CompatibilityStatus
import com.example.data.model.GameCompatibilityReport
import com.example.data.model.GameLaunchCompatibilityItem
import com.example.data.model.PermissionStateItem
import com.example.data.model.SystemTelemetry

object GameCompatibilityEngine {

  fun evaluate(
    context: Context,
    telemetry: SystemTelemetry,
    permissionStates: List<PermissionStateItem>,
    games: List<GameEntity>,
    tweaksManager: SystemTweaksManager,
    isIndo: Boolean,
  ): GameCompatibilityReport {
    val apiLevel = Build.VERSION.SDK_INT
    val androidVersionLabel = "Android ${Build.VERSION.RELEASE} (API $apiLevel)"
    val drxVersionLabel = "v${BuildConfig.VERSION_NAME} (#${BuildConfig.VERSION_CODE})"

    val checks = mutableListOf<CompatibilityCheckItem>()

    checks +=
      CompatibilityCheckItem(
        id = "android_os",
        title = if (isIndo) "Versi Android OS" else "Android OS Version",
        valueText = androidVersionLabel,
        status = if (apiLevel >= Build.VERSION_CODES.O) CompatibilityStatus.SUPPORTED else CompatibilityStatus.LIMITED,
        detailNote =
          if (apiLevel >= Build.VERSION_CODES.Q) {
            if (isIndo) "Mendukung MediaStore Scoped, PixelCopy, dan AudioPlaybackCapture penuh."
            else "Supports Scoped MediaStore, PixelCopy & AudioPlaybackCapture."
          } else {
            if (isIndo) "Audio internal layar memerlukan Android 10+ (API 29)."
            else "Internal game audio capture requires Android 10+ (API 29)."
          },
      )

    val disp = telemetry.displayInfo
    val maxHz = (disp.supportedRefreshRates.maxOrNull() ?: disp.refreshRateHz).safeRoundToInt(60)
    checks +=
      CompatibilityCheckItem(
        id = "display_panel",
        title = if (isIndo) "Resolusi & Refresh Rate" else "Display & Refresh Rate",
        valueText = "${disp.widthPx}×${disp.heightPx} • ${maxHz} Hz",
        status = CompatibilityStatus.SUPPORTED,
        detailNote =
          if (disp.supportedModes.size > 1) {
            if (isIndo) "${disp.supportedModes.size} Display.Mode tersedia."
            else "${disp.supportedModes.size} Display.Mode options available."
          } else {
            if (isIndo) "Panel standar 1 mode aktif."
            else "Standard single display mode active."
          },
      )

    val overlayGranted = tweaksManager.canDrawOverlays()
    checks +=
      CompatibilityCheckItem(
        id = "overlay_ready",
        title = if (isIndo) "Kompatibilitas Overlay & Sidebar" else "Floating Overlay Readiness",
        valueText = if (overlayGranted) (if (isIndo) "Siap" else "Ready") else (if (isIndo) "Perlu Izin" else "Needs Permission"),
        status = if (overlayGranted) CompatibilityStatus.SUPPORTED else CompatibilityStatus.LIMITED,
        detailNote =
          if (overlayGranted) {
            if (isIndo) "Edge Sidebar & Floating HUD dapat ditampilkan di atas game."
            else "Edge Sidebar & Floating HUD can render over games."
          } else {
            if (isIndo) "Aktifkan izin Display Over Other Apps untuk sidebar overlay."
            else "Grant Display Over Other Apps permission to show floating tools."
          },
      )

    val mpMgr = runCatching { context.getSystemService(MediaProjectionManager::class.java) }.getOrNull()
    checks +=
      CompatibilityCheckItem(
        id = "media_projection",
        title = if (isIndo) "Screen Capture (MediaProjection)" else "Screen Capture (MediaProjection)",
        valueText = if (mpMgr != null) (if (isIndo) "Tersedia" else "Available") else (if (isIndo) "Tidak Ada" else "Unavailable"),
        status = if (mpMgr != null) CompatibilityStatus.SUPPORTED else CompatibilityStatus.UNSUPPORTED,
        detailNote =
          if (mpMgr != null) {
            if (isIndo) "Perekam layar & Screenshot layar penuh siap digunakan."
            else "Full-screen Screenshot & Pro Screen Recorder services are supported."
          } else {
            if (isIndo) "Layanan MediaProjection OS tidak tersedia di perangkat ini."
            else "OS MediaProjection service is unavailable on this device."
          },
      )

    val grantedCount = permissionStates.count { it.granted }
    checks +=
      CompatibilityCheckItem(
        id = "permissions_summary",
        title = if (isIndo) "Ringkasan Izin Gaming" else "Permissions Summary",
        valueText = "$grantedCount / ${permissionStates.size}",
        status =
          when {
            grantedCount == permissionStates.size -> CompatibilityStatus.SUPPORTED
            grantedCount >= 2 -> CompatibilityStatus.LIMITED
            else -> CompatibilityStatus.LIMITED
          },
        detailNote =
          if (isIndo) "Termasuk Usage Access, DND Policy, Write Settings, Audio & Notifikasi."
          else "Includes Usage Access, DND Policy, Write Settings, Mic & Notifications.",
      )

    val pm = context.packageManager
    val gameChecks =
      games.map { game ->
        val launchIntent = runCatching { pm.getLaunchIntentForPackage(game.packageName) }.getOrNull()
        val pkgInfo =
          runCatching {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(game.packageName, 0)
          }.getOrNull()
        val installedAndLaunchable = launchIntent != null && pkgInfo != null
        GameLaunchCompatibilityItem(
          packageName = game.packageName,
          appName = game.appName,
          versionName = pkgInfo?.versionName ?: "-",
          isLaunchable = installedAndLaunchable,
          statusNote =
            when {
              installedAndLaunchable ->
                if (isIndo) "Siap diluncurkan via DRX" else "Launchable via DRX"
              pkgInfo != null ->
                if (isIndo) "Terpasang tanpa Activity peluncur utama" else "Installed without launcher Activity"
              else ->
                if (isIndo) "Paket tidak ditemukan / dihapus" else "Package not installed / removed"
            },
        )
      }

    val overall =
      when {
        checks.any { it.status == CompatibilityStatus.UNSUPPORTED } -> CompatibilityStatus.UNSUPPORTED
        checks.any { it.status == CompatibilityStatus.LIMITED } -> CompatibilityStatus.LIMITED
        else -> CompatibilityStatus.SUPPORTED
      }

    return GameCompatibilityReport(
      androidVersionLabel = androidVersionLabel,
      drxVersionLabel = drxVersionLabel,
      overallStatus = overall,
      systemChecks = checks,
      gameChecks = gameChecks,
    )
  }
}
