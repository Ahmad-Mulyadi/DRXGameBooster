package com.example.system

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Choreographer
import android.view.Display
import android.view.WindowManager
import android.content.res.Configuration
import com.example.data.model.BatteryInfoData
import com.example.data.model.CpuInfo
import com.example.data.model.DisplayInfoData
import com.example.data.model.DisplayModeOption
import com.example.data.model.MemoryInfoData
import com.example.data.model.NetworkSnapshot
import com.example.data.model.StorageInfoData
import com.example.data.model.SystemTelemetry
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

class TelemetryMonitor(private val context: Context) {

  private var prevCpuTotal = 0L
  private var prevCpuIdle = 0L

  @Volatile private var latestUiFps: Float = 60f
  @Volatile private var latestFrameTimeMs: Float = 16.6f
  private var frameCount = 0
  private var lastFpsSampleNs = 0L
  private var lastFrameNs = 0L
  @Volatile private var choreographerRunning = false
  private val mainHandler = Handler(Looper.getMainLooper())
  private val isRobolectricRuntime: Boolean =
    Build.FINGERPRINT.equals("robolectric", ignoreCase = true)

  private val frameCallback =
    object : Choreographer.FrameCallback {
      override fun doFrame(frameTimeNanos: Long) {
        if (!choreographerRunning || isRobolectricRuntime) return
        if (lastFrameNs != 0L) {
          val deltaMs = (frameTimeNanos - lastFrameNs) / 1_000_000f
          if (deltaMs.isFinite() && deltaMs in 1f..500f) {
            latestFrameTimeMs = deltaMs
          }
        }
        lastFrameNs = frameTimeNanos
        if (lastFpsSampleNs == 0L) {
          lastFpsSampleNs = frameTimeNanos
        }
        frameCount++
        val elapsedNs = frameTimeNanos - lastFpsSampleNs
        if (elapsedNs >= 500_000_000L) {
          val fps = (frameCount * 1_000_000_000f) / elapsedNs.toFloat()
          if (fps.isFinite() && fps in 1f..360f) {
            latestUiFps = fps
          }
          frameCount = 0
          lastFpsSampleNs = frameTimeNanos
        }
        try {
          Choreographer.getInstance().postFrameCallback(this)
        } catch (_: Throwable) {
        }
      }
    }

  fun startFpsMonitoring() {
    if (isRobolectricRuntime || choreographerRunning) return
    choreographerRunning = true
    val starter = Runnable {
      if (!choreographerRunning) return@Runnable
      frameCount = 0
      lastFpsSampleNs = 0L
      lastFrameNs = 0L
      try {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        Choreographer.getInstance().postFrameCallback(frameCallback)
      } catch (_: Throwable) {
      }
    }
    if (Looper.myLooper() == Looper.getMainLooper()) {
      starter.run()
    } else {
      mainHandler.post(starter)
    }
  }

  fun stopFpsMonitoring() {
    choreographerRunning = false
    val stopper = Runnable {
      try {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
      } catch (_: Throwable) {
      }
    }
    if (Looper.myLooper() == Looper.getMainLooper()) {
      stopper.run()
    } else {
      mainHandler.post(stopper)
    }
  }

  fun readSnapshot(): SystemTelemetry {
    return SystemTelemetry(
      cpuInfo = readCpuInfo(),
      memoryInfo = readMemoryInfo(),
      storageInfo = readStorageInfo(),
      batteryInfo = readBatteryInfo(),
      networkSnapshot = readNetworkSnapshot(),
      displayInfo = readDisplayInfo(),
      thermalStatusCode = readThermalStatus(),
      uiFps = latestUiFps,
      frameTimeMs = latestFrameTimeMs,
      timestampMs = System.currentTimeMillis(),
    )
  }

  fun readCpuInfo(): CpuInfo {
    val cores = Runtime.getRuntime().availableProcessors()
    val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "ARM64"
    val curFreqKhz = readIntFromSysFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
    val maxFreqKhz = readIntFromSysFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")

    var usagePercent: Float? = null
    var restricted = true
    try {
      RandomAccessFile("/proc/stat", "r").use { reader ->
        val line = reader.readLine()
        if (line != null && line.startsWith("cpu ")) {
          val parts = line.trim().split(Regex("\\s+"))
          if (parts.size >= 5) {
            val user = parts[1].toLongOrNull() ?: 0L
            val nice = parts[2].toLongOrNull() ?: 0L
            val system = parts[3].toLongOrNull() ?: 0L
            val idle = parts[4].toLongOrNull() ?: 0L
            val iowait = parts.getOrNull(5)?.toLongOrNull() ?: 0L
            val irq = parts.getOrNull(6)?.toLongOrNull() ?: 0L
            val softIrq = parts.getOrNull(7)?.toLongOrNull() ?: 0L

            val total = user + nice + system + idle + iowait + irq + softIrq
            val idleAll = idle + iowait
            if (prevCpuTotal > 0L && total > prevCpuTotal) {
              val totalDiff = total - prevCpuTotal
              val idleDiff = idleAll - prevCpuIdle
              val usage = ((totalDiff - idleDiff).toFloat() / totalDiff.toFloat()) * 100f
              usagePercent = usage.coerceIn(0f, 100f)
            }
            prevCpuTotal = total
            prevCpuIdle = idleAll
            restricted = false
          }
        }
      }
    } catch (_: Throwable) {
      restricted = true
    }

    return CpuInfo(
      cores = cores,
      abi = abi,
      usagePercent = usagePercent,
      currentFreqMhz = curFreqKhz?.div(1000),
      maxFreqMhz = maxFreqKhz?.div(1000),
      isUsageRestricted = restricted,
    )
  }

  private fun readIntFromSysFile(path: String): Int? {
    return try {
      val f = File(path)
      if (f.exists() && f.canRead()) {
        f.readText().trim().toIntOrNull()
      } else {
        null
      }
    } catch (_: Throwable) {
      null
    }
  }

  fun readMemoryInfo(): MemoryInfoData {
    return try {
      val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        ?: return MemoryInfoData()
      val memInfo = ActivityManager.MemoryInfo()
      am.getMemoryInfo(memInfo)
      val total = memInfo.totalMem.coerceAtLeast(1L)
      val avail = memInfo.availMem.coerceAtLeast(0L)
      val used = (total - avail).coerceAtLeast(0L)
      val usedPct = ((used.toFloat() / total.toFloat()) * 100f).sanitizeFinite(0f).coerceIn(0f, 100f)
      val rt = Runtime.getRuntime()
      val appUsedMb = ((rt.totalMemory() - rt.freeMemory()) / (1024f * 1024f)).sanitizeFinite(0f)
      val appMaxMb = (rt.maxMemory() / (1024f * 1024f)).sanitizeFinite(128f)
      MemoryInfoData(
        totalBytes = total,
        availBytes = avail,
        usedBytes = used,
        usedPercent = usedPct,
        lowMemory = memInfo.lowMemory,
        thresholdBytes = memInfo.threshold,
        appHeapUsedMb = appUsedMb,
        appHeapMaxMb = appMaxMb,
      )
    } catch (_: Throwable) {
      MemoryInfoData()
    }
  }

  fun readStorageInfo(): StorageInfoData {
    return try {
      val stat = StatFs(Environment.getDataDirectory().path)
      val total = stat.totalBytes.coerceAtLeast(1L)
      val free = stat.availableBytes.coerceAtLeast(0L)
      val used = (total - free).coerceAtLeast(0L)
      val pct = ((used.toFloat() / total.toFloat()) * 100f).sanitizeFinite(0f).coerceIn(0f, 100f)
      StorageInfoData(
        totalBytes = total,
        freeBytes = free,
        usedBytes = used,
        usedPercent = pct,
      )
    } catch (_: Throwable) {
      StorageInfoData()
    }
  }

  fun readBatteryInfo(): BatteryInfoData {
    return try {
      val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
      val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
      val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
      val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
      val pct =
        if (level >= 0 && scale > 0) ((level * 100f) / scale).safeRoundToInt(0)
        else bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
      val tempDeci = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
      val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
      val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
      val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
      val health =
        intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD)
          ?: BatteryManager.BATTERY_HEALTH_GOOD
      val isCharging =
        status == BatteryManager.BATTERY_STATUS_CHARGING ||
          status == BatteryManager.BATTERY_STATUS_FULL ||
          plugged != 0

      val source =
        when (plugged) {
          BatteryManager.BATTERY_PLUGGED_AC -> "AC Fast"
          BatteryManager.BATTERY_PLUGGED_USB -> "USB"
          BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
          else -> if (isCharging) "Charging" else "Battery"
        }

      val currentUa = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
      val currentMa =
        if (currentUa != null && currentUa != Int.MIN_VALUE) {
          if (kotlin.math.abs(currentUa) > 10_000) currentUa / 1000 else currentUa
        } else {
          null
        }

      BatteryInfoData(
        levelPercent = pct.coerceIn(0, 100),
        temperatureC = (tempDeci / 10f).sanitizeFinite(30f),
        voltageMv = voltageMv,
        currentMa = currentMa,
        isCharging = isCharging,
        chargeSource = source,
        healthCode = health,
      )
    } catch (_: Throwable) {
      BatteryInfoData()
    }
  }

  @Suppress("DEPRECATION")
  fun readNetworkSnapshot(): NetworkSnapshot {
    return try {
      val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return NetworkSnapshot()
      val active = cm.activeNetwork ?: return NetworkSnapshot()
      val caps = cm.getNetworkCapabilities(active) ?: return NetworkSnapshot()
      val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

      when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
          var wifiInfo: WifiInfo? = null
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            wifiInfo = caps.transportInfo as? WifiInfo
          }
          if (wifiInfo == null) {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiInfo = wm?.connectionInfo
          }
          val linkSpeed = wifiInfo?.linkSpeed?.takeIf { it > 0 }
          val freq = wifiInfo?.frequency?.takeIf { it > 0 }
          val rssi = wifiInfo?.rssi?.takeIf { it in -120..-10 }
          val band =
            when {
              freq == null -> "Wi-Fi"
              freq >= 5925 -> "Wi-Fi 6E (6 GHz)"
              freq >= 4900 -> "Wi-Fi 5 GHz"
              else -> "Wi-Fi 2.4 GHz"
            }
          val quality =
            when {
              !hasInternet -> 1
              rssi != null && rssi >= -55 -> 4
              rssi != null && rssi >= -70 -> 3
              rssi != null && rssi >= -82 -> 2
              else -> if (hasInternet) 3 else 1
            }
          NetworkSnapshot(
            isConnected = hasInternet,
            transportType = "Wi-Fi",
            networkSubType = band,
            linkSpeedMbps = linkSpeed,
            frequencyMhz = freq,
            rssiDbm = rssi,
            isMetered = isMetered,
            qualityLevel = quality,
          )
        }
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
          val downKbps = caps.linkDownstreamBandwidthKbps
          val estMbps = if (downKbps > 0) downKbps / 1000 else null
          NetworkSnapshot(
            isConnected = hasInternet,
            transportType = "Cellular",
            networkSubType = "Mobile Data",
            linkSpeedMbps = estMbps,
            frequencyMhz = null,
            rssiDbm = null,
            isMetered = true,
            qualityLevel = if (hasInternet) 3 else 1,
          )
        }
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
          NetworkSnapshot(
            isConnected = hasInternet,
            transportType = "Ethernet",
            networkSubType = "Wired LAN",
            linkSpeedMbps = caps.linkDownstreamBandwidthKbps.takeIf { it > 0 }?.div(1000),
            isMetered = false,
            qualityLevel = if (hasInternet) 4 else 1,
          )
        else -> NetworkSnapshot(isConnected = hasInternet, transportType = "Other", qualityLevel = 2)
      }
    } catch (_: Throwable) {
      NetworkSnapshot()
    }
  }

  @Suppress("DEPRECATION")
  fun readDisplayInfo(): DisplayInfoData {
    return try {
      val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
      val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
      val display: Display? = dm?.getDisplay(Display.DEFAULT_DISPLAY)

      val metrics = DisplayMetrics()
      if (wm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = wm.currentWindowMetrics.bounds
        metrics.widthPixels = bounds.width()
        metrics.heightPixels = bounds.height()
        metrics.densityDpi = context.resources.configuration.densityDpi
      } else if (display != null) {
        display.getRealMetrics(metrics)
      } else {
        metrics.setTo(context.resources.displayMetrics)
      }

      val refreshRate = (display?.refreshRate ?: 60f).sanitizeFinite(60f).coerceAtLeast(1f)
      val rawModes = display?.supportedModes?.toList().orEmpty()
      val supportedModes =
        rawModes
          .map { it.refreshRate }
          .filter { it.isFinite() && it > 1f }
          .distinct()
          .sorted()
          .ifEmpty { listOf(refreshRate) }
      val modeOptions =
        rawModes
          .map {
            DisplayModeOption(
              modeId = it.modeId,
              widthPx = it.physicalWidth.coerceAtLeast(1),
              heightPx = it.physicalHeight.coerceAtLeast(1),
              refreshRateHz = it.refreshRate.sanitizeFinite(60f),
            )
          }
          .distinctBy { "${it.widthPx}x${it.heightPx}@${it.refreshRateHz.roundToInt()}" }
          .sortedByDescending { it.refreshRateHz }
      val activeModeId = display?.mode?.modeId
      val isHdr =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          display?.isHdr ?: false
        } else {
          false
        }

      val realDpiMetrics = context.resources.displayMetrics
      val xdpi = realDpiMetrics.xdpi.takeIf { it.isFinite() && it >= 50f } ?: metrics.densityDpi.toFloat()
      val ydpi = realDpiMetrics.ydpi.takeIf { it.isFinite() && it >= 50f } ?: metrics.densityDpi.toFloat()
      val widthInches = metrics.widthPixels.toFloat() / xdpi
      val heightInches = metrics.heightPixels.toFloat() / ydpi
      val diagInches = hypot(widthInches.toDouble(), heightInches.toDouble())
      val physicalSizeText =
        if (diagInches.isFinite() && diagInches in 2.0..25.0) {
          String.format(Locale.US, "%.2f\"", diagInches)
        } else {
          "-"
        }

      val orientationLabel =
        if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
          "Landscape"
        } else {
          "Portrait"
        }

      val sysBrightnessRaw =
        try {
          Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (_: Throwable) {
          128
        }
      val brightnessPct = ((sysBrightnessRaw / 255f) * 100f).safeRoundToInt(50).coerceIn(0, 100)

      val timeoutMs =
        try {
          Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        } catch (_: Throwable) {
          60_000
        }

      DisplayInfoData(
        widthPx = metrics.widthPixels.coerceAtLeast(1),
        heightPx = metrics.heightPixels.coerceAtLeast(1),
        densityDpi = metrics.densityDpi.coerceAtLeast(120),
        refreshRateHz = refreshRate,
        supportedRefreshRates = supportedModes,
        isHdr = isHdr,
        brightnessPercent = brightnessPct,
        screenTimeoutMs = timeoutMs,
        physicalSizeInches = physicalSizeText,
        orientationLabel = orientationLabel,
        activeDisplayModeId = activeModeId,
        supportedModes = modeOptions,
      )
    } catch (_: Throwable) {
      DisplayInfoData()
    }
  }

  fun readThermalStatus(): Int {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.currentThermalStatus ?: 0
      } else {
        0
      }
    } catch (_: Throwable) {
      0
    }
  }
}
