package com.example.system

import android.Manifest
import android.app.Activity
import android.app.ActivityOptions
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.data.model.AppUsageSummary
import com.example.data.model.BoostReport
import com.example.data.model.FloatingAppShortcut
import com.example.data.model.GameCategory
import com.example.data.model.InstalledAppItem
import com.example.data.model.PermissionStateItem
import com.example.data.model.ProfileConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

class SystemTweaksManager(private val context: Context) {

  private val iconCache = ConcurrentHashMap<String, ImageBitmap>()
  @Volatile var isTorchEnabled: Boolean = false
    private set

  private val knownGameKeywords =
    listOf(
      "mobile.legends" to GameCategory.MOBA,
      "tencent.ig" to GameCategory.BATTLE_ROYALE,
      "pubg" to GameCategory.BATTLE_ROYALE,
      "freefire" to GameCategory.BATTLE_ROYALE,
      "garena" to GameCategory.FPS,
      "mihoyo" to GameCategory.RPG,
      "hoyoverse" to GameCategory.RPG,
      "activision" to GameCategory.FPS,
      "callofduty" to GameCategory.FPS,
      "riotgames" to GameCategory.MOBA,
      "supercell" to GameCategory.STRATEGY,
      "roblox" to GameCategory.CASUAL,
      "minecraft" to GameCategory.CASUAL,
      "asphalt" to GameCategory.RACING,
      "gameloft" to GameCategory.RACING,
      "ea.gp" to GameCategory.RACING,
      "valorant" to GameCategory.FPS,
      "honkai" to GameCategory.RPG,
      "genshin" to GameCategory.RPG,
      "arena" to GameCategory.MOBA,
      "racing" to GameCategory.RACING,
      "shooter" to GameCategory.FPS,
      "craft" to GameCategory.STRATEGY,
    )

  fun scanInstalledLauncherApps(): List<InstalledAppItem> {
    return try {
      val pm = context.packageManager
      val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
      val resolved =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
          @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
        }

      val seen = HashSet<String>()
      val results = ArrayList<InstalledAppItem>()
      val ownPkg = context.packageName

      for (info in resolved) {
        val pkg = info.activityInfo?.packageName ?: continue
        if (pkg == ownPkg || !seen.add(pkg)) continue
        val label =
          try {
            info.loadLabel(pm).toString().takeIf { it.isNotBlank() } ?: pkg
          } catch (_: Throwable) {
            pkg
          }
        val appInfo = info.activityInfo?.applicationInfo
        val isOsGameCategory =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo != null) {
            appInfo.category == ApplicationInfo.CATEGORY_GAME
          } else {
            false
          }
        val matchedKeyword =
          knownGameKeywords.firstOrNull { (kw, _) -> pkg.lowercase().contains(kw) }
        val isLikelyGame = isOsGameCategory || matchedKeyword != null
        val category = matchedKeyword?.second ?: if (isOsGameCategory) GameCategory.CUSTOM else GameCategory.CASUAL
        results.add(
          InstalledAppItem(
            packageName = pkg,
            appName = label,
            isLikelyGame = isLikelyGame,
            suggestedCategory = category,
          )
        )
      }
      results.sortedWith(compareByDescending<InstalledAppItem> { it.isLikelyGame }.thenBy { it.appName.lowercase() })
    } catch (_: Throwable) {
      emptyList()
    }
  }

  fun isFreeformWindowSupported(): Boolean {
    return context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
  }

  fun resolveFloatingAppShortcuts(pinnedPackages: Set<String> = emptySet()): List<FloatingAppShortcut> {
    val pm = context.packageManager
    val freeformSupported = isFreeformWindowSupported()
    val installed = scanInstalledLauncherApps()

    fun findByPackageOrKeyword(exactPackages: List<String>, keywords: List<String>): InstalledAppItem? {
      exactPackages.forEach { pkg ->
        installed.firstOrNull { it.packageName.equals(pkg, ignoreCase = true) }?.let { return it }
      }
      return installed.firstOrNull { item ->
        keywords.any { kw ->
          item.packageName.contains(kw, ignoreCase = true) || item.appName.contains(kw, ignoreCase = true)
        }
      }
    }

    fun resolveIntentPackage(intent: Intent): String? {
      val resolved =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
          @Suppress("DEPRECATION") pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
      val pkg = resolved?.activityInfo?.packageName
      return if (pkg != null && pkg != "android" && pkg != context.packageName) pkg else null
    }

    val waPkg = findByPackageOrKeyword(listOf("com.whatsapp", "com.whatsapp.w4b"), listOf("whatsapp"))?.packageName
    val browserPkg =
      resolveIntentPackage(Intent(Intent.ACTION_VIEW, "https://example.com".toUri()))
        ?: findByPackageOrKeyword(listOf("com.android.chrome", "org.mozilla.firefox"), listOf("browser", "chrome"))?.packageName
    val calcPkg =
      resolveIntentPackage(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))
        ?: findByPackageOrKeyword(listOf("com.google.android.calculator", "com.android.calculator2"), listOf("calculator", "kalkulator"))?.packageName
    val clockPkg =
      findByPackageOrKeyword(listOf("com.google.android.deskclock", "com.android.deskclock"), listOf("clock", "deskclock", "jam"))?.packageName
    val musicPkg =
      resolveIntentPackage(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC))
        ?: findByPackageOrKeyword(listOf("com.spotify.music", "com.google.android.apps.youtube.music"), listOf("music", "spotify", "player"))?.packageName
    val galleryPkg =
      resolveIntentPackage(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY))
        ?: findByPackageOrKeyword(listOf("com.google.android.apps.photos", "com.miui.gallery"), listOf("gallery", "photos", "galeri"))?.packageName
    val filesPkg =
      findByPackageOrKeyword(listOf("com.google.android.apps.nbu.files", "com.android.documentsui"), listOf("files", "documentsui", "filemanager"))?.packageName
    val notesPkg =
      findByPackageOrKeyword(listOf("com.google.android.keep"), listOf("keep", "notes", "catatan"))?.packageName

    val coreShortcuts =
      listOf(
        FloatingAppShortcut("whatsapp", "WhatsApp", waPkg, isBuiltInMiniWindow = false, supportsFreeform = freeformSupported),
        FloatingAppShortcut("calculator", "Calculator", calcPkg, isBuiltInMiniWindow = true, supportsFreeform = true),
        FloatingAppShortcut("browser", "Browser", browserPkg, isBuiltInMiniWindow = false, supportsFreeform = freeformSupported),
        FloatingAppShortcut("notes", "Notes", notesPkg, isBuiltInMiniWindow = true, supportsFreeform = true),
        FloatingAppShortcut("clock", "Clock", clockPkg, isBuiltInMiniWindow = true, supportsFreeform = true),
        FloatingAppShortcut("music", "Music Player", musicPkg, isBuiltInMiniWindow = false, supportsFreeform = freeformSupported),
        FloatingAppShortcut("gallery", "Gallery", galleryPkg, isBuiltInMiniWindow = false, supportsFreeform = freeformSupported),
        FloatingAppShortcut("files", "File Manager", filesPkg, isBuiltInMiniWindow = false, supportsFreeform = freeformSupported),
        FloatingAppShortcut("drx_tools", "DRX Tools", context.packageName, isBuiltInMiniWindow = true, supportsFreeform = true),
      )

    val customPinned =
      pinnedPackages.mapNotNull { pkg ->
        val found = installed.firstOrNull { it.packageName == pkg }
        if (found != null) {
          FloatingAppShortcut(
            key = "pin_${found.packageName}",
            defaultLabel = found.appName,
            packageName = found.packageName,
            isBuiltInMiniWindow = false,
            supportsFreeform = freeformSupported,
          )
        } else {
          null
        }
      }

    return coreShortcuts + customPinned
  }

  fun launchAppFreeformOrShortcut(packageName: String, tryFreeform: Boolean = true): Boolean {
    return try {
      val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
      launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
      if (tryFreeform && isFreeformWindowSupported()) {
        val opts = ActivityOptions.makeBasic()
        val metrics = context.resources.displayMetrics
        val w = (metrics.widthPixels * 0.68f).roundToInt()
        val h = (metrics.heightPixels * 0.55f).roundToInt()
        val left = (metrics.widthPixels - w) / 2
        val top = (metrics.heightPixels - h) / 3
        opts.setLaunchBounds(Rect(left, top, left + w, top + h))
        context.startActivity(launchIntent, opts.toBundle())
      } else {
        context.startActivity(launchIntent)
      }
      true
    } catch (_: Throwable) {
      launchApp(packageName)
    }
  }

  fun toggleFlashlight(): Boolean? {
    return try {
      val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
      val cameraId =
        cm.cameraIdList.firstOrNull { id ->
          val chars = cm.getCameraCharacteristics(id)
          chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
            chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: cm.cameraIdList.firstOrNull { id ->
          cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return null
      val next = !isTorchEnabled
      cm.setTorchMode(cameraId, next)
      isTorchEnabled = next
      next
    } catch (_: Throwable) {
      null
    }
  }

  fun dispatchMediaKey(keyCode: Int): Boolean {
    return try {
      val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
      val eventTime = SystemClock.uptimeMillis()
      am.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
      am.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0))
      true
    } catch (_: Throwable) {
      false
    }
  }

  fun readClipboardText(): String {
    return try {
      val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      val clip = cb.primaryClip
      if (clip != null && clip.itemCount > 0) {
        clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
      } else ""
    } catch (_: Throwable) {
      ""
    }
  }

  fun copyToClipboard(label: String, text: String): Boolean {
    return try {
      val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      cb.setPrimaryClip(ClipData.newPlainText(label, text))
      true
    } catch (_: Throwable) {
      false
    }
  }

  fun cycleScreenTimeoutMs(): Int? {
    if (!canWriteSystemSettings()) return null
    val current =
      try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60_000)
      } catch (_: Throwable) {
        60_000
      }
    val next =
      when {
        current < 60_000 -> 120_000
        current < 300_000 -> 600_000
        else -> 30_000
      }
    return if (setScreenTimeoutMs(next)) next else null
  }

  fun loadAppIcon(packageName: String): ImageBitmap? {
    iconCache[packageName]?.let { return it }
    return try {
      if (iconCache.size > 80) {
        iconCache.clear()
      }
      val drawable: Drawable = context.packageManager.getApplicationIcon(packageName)
      val bitmap =
        if (drawable is BitmapDrawable && drawable.bitmap != null && drawable.bitmap.width > 0 && drawable.bitmap.height > 0) {
          drawable.bitmap
        } else {
          val w = drawable.intrinsicWidth.coerceIn(64, 192)
          val h = drawable.intrinsicHeight.coerceIn(64, 192)
          createBitmap(w, h).also { bmp ->
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
          }
        }
      bitmap.asImageBitmap().also { iconCache[packageName] = it }
    } catch (_: Throwable) {
      null
    }
  }

  fun launchApp(packageName: String): Boolean {
    return try {
      val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
      launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(launchIntent)
      true
    } catch (_: Throwable) {
      false
    }
  }

  fun performSmartBoost(
    telemetryMonitor: TelemetryMonitor,
    profileConfig: ProfileConfig,
    activity: Activity?,
    isIndonesian: Boolean,
  ): BoostReport {
    val beforeMem = telemetryMonitor.readMemoryInfo()
    val actions = mutableListOf<String>()

    // 1. Clean own app temporary cache directory safely
    val freedBytes = clearAppTempCaches()
    val freedKb = freedBytes / 1024L
    actions.add(
      if (isIndonesian) "Bersihkan cache internal DRX (${freedKb} KB)"
      else "Cleared DRX internal cache (${freedKb} KB)"
    )

    // 2. Request JVM heap compaction advisory for this app process
    System.gc()
    actions.add(
      if (isIndonesian) "Optimasi memori heap proses aplikasi"
      else "Optimized app process heap memory"
    )

    // 3. Apply Brightness if WRITE_SETTINGS is granted, else window-level fallback
    val brightnessApplied = setSystemBrightness(profileConfig.brightnessPercent, profileConfig.autoBrightness, activity)
    if (brightnessApplied) {
      actions.add(
        if (isIndonesian) "Kecerahan diatur ke ${profileConfig.brightnessPercent}%"
        else "Brightness set to ${profileConfig.brightnessPercent}%"
      )
    }

    // 4. Apply Do Not Disturb if notification policy access is granted
    if (profileConfig.enableDnd && isDndPolicyGranted()) {
      setDoNotDisturb(true)
      actions.add(
        if (isIndonesian) "Do Not Disturb (Prioritas Gaming) diaktifkan"
        else "Do Not Disturb (Gaming Priority) enabled"
      )
    }

    // 5. Apply Immersive & Orientation to DRX UI if Activity provided
    if (activity != null) {
      setImmersiveMode(activity, profileConfig.immersiveMode)
      setOrientation(activity, profileConfig.orientationMode)
      if (profileConfig.immersiveMode) {
        actions.add(
          if (isIndonesian) "Mode Immersive antarmuka aktif"
          else "UI Immersive Mode enabled"
        )
      }
    }

    val afterMem = telemetryMonitor.readMemoryInfo()
    val battery = telemetryMonitor.readBatteryInfo()
    return BoostReport(
      timestampMs = System.currentTimeMillis(),
      ramBeforeAvailMb = beforeMem.availBytes / (1024L * 1024L),
      ramAfterAvailMb = afterMem.availBytes / (1024L * 1024L),
      totalRamMb = afterMem.totalBytes / (1024L * 1024L),
      batteryTempC = battery.temperatureC,
      profileApplied = profileConfig.profile,
      actionsApplied = actions,
    )
  }

  private fun clearAppTempCaches(): Long {
    var cleared = 0L
    val dirs = listOfNotNull(context.cacheDir, context.codeCacheDir)
    for (dir in dirs) {
      cleared += deleteDirChildrenSize(dir)
    }
    return cleared
  }

  private fun deleteDirChildrenSize(dir: File): Long {
    var total = 0L
    val files = dir.listFiles() ?: return 0L
    for (file in files) {
      if (file.isDirectory) {
        total += deleteDirChildrenSize(file)
        file.delete()
      } else {
        val len = file.length()
        if (file.delete()) total += len
      }
    }
    return total
  }

  fun canWriteSystemSettings(): Boolean =
    try {
      Settings.System.canWrite(context)
    } catch (_: Throwable) {
      false
    }

  fun canDrawOverlays(): Boolean =
    try {
      Settings.canDrawOverlays(context)
    } catch (_: Throwable) {
      false
    }

  fun isDndPolicyGranted(): Boolean =
    try {
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      nm?.isNotificationPolicyAccessGranted == true
    } catch (_: Throwable) {
      false
    }

  fun isDndEnabled(): Boolean =
    try {
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      nm != null && nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    } catch (_: Throwable) {
      false
    }

  fun setDoNotDisturb(enable: Boolean): Boolean {
    if (!isDndPolicyGranted()) return false
    return try {
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      nm.setInterruptionFilter(
        if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY
        else NotificationManager.INTERRUPTION_FILTER_ALL
      )
      true
    } catch (_: Throwable) {
      false
    }
  }

  fun setSystemBrightness(percent: Int, auto: Boolean, activity: Activity? = null): Boolean {
    val clamped = percent.coerceIn(5, 100)
    if (canWriteSystemSettings()) {
      return try {
        Settings.System.putInt(
          context.contentResolver,
          Settings.System.SCREEN_BRIGHTNESS_MODE,
          if (auto) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
          else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        if (!auto) {
          val value255 = ((clamped / 100f) * 255f).roundToInt().coerceIn(10, 255)
          Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value255)
        }
        true
      } catch (_: Throwable) {
        false
      }
    }
    // Safe fallback for current app window
    if (activity != null) {
      val lp = activity.window.attributes
      lp.screenBrightness = if (auto) -1f else clamped / 100f
      activity.window.attributes = lp
      return true
    }
    return false
  }

  fun setScreenTimeoutMs(timeoutMs: Int): Boolean {
    if (!canWriteSystemSettings()) return false
    return try {
      Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, timeoutMs)
      true
    } catch (_: Throwable) {
      false
    }
  }

  fun setSystemAutoRotation(enabled: Boolean): Boolean {
    if (!canWriteSystemSettings()) return false
    return try {
      Settings.System.putInt(
        context.contentResolver,
        Settings.System.ACCELEROMETER_ROTATION,
        if (enabled) 1 else 0,
      )
      true
    } catch (_: Throwable) {
      false
    }
  }

  fun isSystemAutoRotationEnabled(): Boolean {
    return try {
      Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
    } catch (_: Throwable) {
      true
    }
  }

  fun setOrientation(activity: Activity, mode: Int) {
    try {
      activity.requestedOrientation =
        when (mode) {
          1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
          2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
          else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    } catch (_: Throwable) {
    }
  }

  fun setImmersiveMode(activity: Activity, enabled: Boolean) {
    try {
      val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
      controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      if (enabled) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
      } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
      }
    } catch (_: Throwable) {
    }
  }

  fun applyPreferredDisplayMode(activity: Activity, modeId: Int): Boolean {
    return try {
      val lp = activity.window.attributes
      lp.preferredDisplayModeId = modeId.coerceAtLeast(0)
      activity.window.attributes = lp
      true
    } catch (_: Throwable) {
      false
    }
  }

  fun getStreamVolumePercent(streamType: Int): Int {
    return try {
      val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 50
      val max = am.getStreamMaxVolume(streamType).coerceAtLeast(1)
      val cur = am.getStreamVolume(streamType)
      ((cur * 100f) / max).safeRoundToInt(50).coerceIn(0, 100)
    } catch (_: Throwable) {
      50
    }
  }

  fun setStreamVolumePercent(streamType: Int, percent: Int) {
    try {
      val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
      val max = am.getStreamMaxVolume(streamType).coerceAtLeast(1)
      val target = ((percent.coerceIn(0, 100) / 100f) * max).safeRoundToInt(0).coerceIn(0, max)
      am.setStreamVolume(streamType, target, 0)
    } catch (_: Throwable) {
    }
  }

  fun hasUsageStatsPermission(): Boolean {
    return try {
      val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
      val mode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          @Suppress("DEPRECATION")
          appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
          )
        } else {
          @Suppress("DEPRECATION")
          appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
          )
        }
      mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Throwable) {
      false
    }
  }

  fun queryRecentAppUsage(hoursBack: Int = 24): List<AppUsageSummary> {
    if (!hasUsageStatsPermission()) return emptyList()
    return try {
      val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        ?: return emptyList()
      val end = System.currentTimeMillis()
      val start = end - hoursBack * 3_600_000L
      val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end) ?: return emptyList()
      val pm = context.packageManager
      stats
        .filter { it.totalTimeInForeground > 10_000L && it.packageName != context.packageName }
        .groupBy { it.packageName }
        .map { (pkg, entries) ->
          val totalMs = entries.sumOf { it.totalTimeInForeground }
          val lastUsed = entries.maxOf { it.lastTimeUsed }
          val appName =
            try {
              val ai = pm.getApplicationInfo(pkg, 0)
              pm.getApplicationLabel(ai).toString()
            } catch (_: Throwable) {
              pkg.substringAfterLast('.')
            }
          AppUsageSummary(pkg, appName, totalMs, lastUsed)
        }
        .sortedByDescending { it.totalForegroundMs }
        .take(8)
    } catch (_: Throwable) {
      emptyList()
    }
  }

  fun detectRecentForegroundPackage(
    lookbackMs: Long = 15_000L,
    minTimestampMs: Long = 0L,
  ): String? {
    if (!hasUsageStatsPermission()) return null
    return try {
      val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
      val end = System.currentTimeMillis()
      val start = maxOf((end - lookbackMs).coerceAtLeast(0L), minTimestampMs)
      if (start >= end) return null
      val events = usm.queryEvents(start, end) ?: return null
      val event = UsageEvents.Event()
      var latestPkg: String? = null
      var latestTs = minTimestampMs
      val resumedType =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          UsageEvents.Event.ACTIVITY_RESUMED
        } else {
          @Suppress("DEPRECATION")
          UsageEvents.Event.MOVE_TO_FOREGROUND
        }
      while (events.hasNextEvent()) {
        events.getNextEvent(event)
        if (event.eventType == resumedType && event.timeStamp >= latestTs) {
          latestTs = event.timeStamp
          latestPkg = event.packageName
        }
      }
      latestPkg
    } catch (_: Throwable) {
      null
    }
  }

  fun getAllPermissionStates(): List<PermissionStateItem> {
    val micGranted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    val notifGranted =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      } else {
        true
      }
    return listOf(
      PermissionStateItem("overlay", canDrawOverlays(), isSpecialSystemSetting = true),
      PermissionStateItem("write_settings", canWriteSystemSettings(), isSpecialSystemSetting = true),
      PermissionStateItem("dnd", isDndPolicyGranted(), isSpecialSystemSetting = true),
      PermissionStateItem("usage_stats", hasUsageStatsPermission(), isSpecialSystemSetting = true),
      PermissionStateItem("record_audio", micGranted, isSpecialSystemSetting = false),
      PermissionStateItem("notifications", notifGranted, isSpecialSystemSetting = false),
    )
  }

  fun openOverlayPermissionSettings() {
    val primary =
      Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "package:${context.packageName}".toUri(),
      ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (!safeStartIntent(primary)) {
      if (!safeStartIntent(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
        openSystemSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
      }
    }
  }

  fun openWriteSettingsPermission() {
    val primary =
      Intent(
        Settings.ACTION_MANAGE_WRITE_SETTINGS,
        "package:${context.packageName}".toUri(),
      ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (!safeStartIntent(primary)) {
      if (!safeStartIntent(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
        openSystemSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
      }
    }
  }

  fun openDndPolicySettings() {
    if (!safeStartIntent(
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    ) {
      openSystemSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    }
  }

  fun openUsageAccessSettings() {
    if (!safeStartIntent(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    ) {
      openSystemSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    }
  }

  fun openSystemSettings(action: String) {
    val intent =
      if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS ||
        action == Settings.ACTION_APP_NOTIFICATION_SETTINGS
      ) {
        if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          Intent(action)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
          Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      } else {
        Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    safeStartIntent(intent)
  }

  fun openSystemScreenRecorderOrDisplaySettings(): Boolean {
    val candidates =
      listOf(
        Intent("com.android.systemui.screenrecord.ScreenRecordDialog"),
        Intent("android.settings.CAST_SETTINGS"),
        Intent(Settings.ACTION_DISPLAY_SETTINGS),
      )
    for (intent in candidates) {
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      if (safeStartIntent(intent)) return true
    }
    return false
  }

  fun openScreenshotShortcut(): Boolean {
    val candidates =
      listOf(
        Intent("android.intent.action.SCREENSHOT"),
        Intent("com.android.systemui.screenshot.TAKE_SCREENSHOT"),
      )
    for (intent in candidates) {
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      if (safeStartIntent(intent)) return true
    }
    return false
  }

  private fun safeStartIntent(intent: Intent): Boolean {
    return try {
      context.startActivity(intent)
      true
    } catch (_: Throwable) {
      false
    }
  }
}
