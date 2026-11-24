package com.example.system

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.example.data.db.DrxDatabase
import com.example.data.db.DrxRepository
import com.example.data.db.GamingNoteEntity
import com.example.data.model.HudDisplayMode
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class MiniWindowType(val titleEn: String, val titleId: String) {
  CALCULATOR("Mini Calculator", "Kalkulator Mini"),
  NOTES("Tactical Notes", "Catatan Taktis"),
  TIMER("Gaming Timer", "Timer Gaming"),
  MONITOR("Live HUD", "Live HUD"),
}

@SuppressLint("SetTextI18n")
class FloatingMiniWindowManager(
  private val context: Context,
  private val windowManager: WindowManager,
  private val telemetryMonitor: TelemetryMonitor,
  private val scope: CoroutineScope,
  private val onSaveBounds: (x: Int, y: Int, w: Int, h: Int, alpha: Float) -> Unit,
  private val onSaveHudConfig: (mode: HudDisplayMode?, alpha: Float?, x: Int?, y: Int?, enabled: Boolean?) -> Unit =
    { _, _, _, _, _ -> },
  private val onToggleTimer: () -> Unit,
  private val onResetTimer: () -> Unit,
  private val timerStateProvider: () -> Pair<Boolean, Long>,
) {
  // Auxiliary Mini-Window (Calculator, Notes, Timer)
  private var container: LinearLayout? = null
  private var bodyContainer: LinearLayout? = null
  private var layoutParams: WindowManager.LayoutParams? = null
  private var activeType: MiniWindowType? = null
  private var isIndonesian = false
  private var isMinimized = false
  private var isMaximized = false
  private var currentAlpha = 0.94f
  private var widthDp = 280
  private var heightDp = 310

  // Dynamic views for auxiliary mini-windows
  private var timerHudText: TextView? = null
  private var timerActionBtn: TextView? = null

  // Calculator state
  private var calcDisplay = "0"
  private var calcAccumulator = 0.0
  private var calcPendingOp: Char? = null
  private var calcResetOnNext = false
  private var calcDisplayView: TextView? = null

  // Dedicated Live Performance HUD Overlay (Floating Capsule: Mini / Standard / Full)
  private var hudContainer: LinearLayout? = null
  private var hudParams: WindowManager.LayoutParams? = null
  private var hudHeaderTitle: TextView? = null
  private var hudModeBtn: TextView? = null
  private var hudPrimaryLine: TextView? = null
  private var hudSecondaryLine: TextView? = null
  private var hudHonestNote: TextView? = null
  private var hudDisplayMode: HudDisplayMode = HudDisplayMode.STANDARD
  private var hudAlpha: Float = 0.90f
  private var hudAutoHide: Boolean = false
  private var hudIsIndo: Boolean = false
  private var hudPollTick: Int = 0
  private var cachedStorageFreeText: String = "-"
  private val hudHandler = Handler(Looper.getMainLooper())
  private val hudFadeRunnable =
    Runnable {
      if (hudAutoHide) {
        hudContainer?.animate()?.alpha((hudAlpha * 0.48f).coerceAtLeast(0.28f))?.setDuration(250L)?.start()
      }
    }

  var animationsEnabled: Boolean = true

  val isLiveHudShowing: Boolean
    get() = hudContainer != null

  fun showLiveHud(
    mode: HudDisplayMode,
    isIndo: Boolean,
    x: Int,
    y: Int,
    alpha: Float,
    autoHide: Boolean,
  ) {
    dismissLiveHud(notifyCallback = false)
    if (!Settings.canDrawOverlays(context)) return
    hudDisplayMode = mode
    hudIsIndo = isIndo
    hudAlpha = alpha.sanitizeFinite(0.90f).coerceIn(0.35f, 1.0f)
    hudAutoHide = autoHide
    hudPollTick = 0

    val overlayType =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
      } else {
        @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
      }

    val params =
      WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x.coerceIn(8, (context.resources.displayMetrics.widthPixels - dp(120)).coerceAtLeast(8))
        this.y = y.coerceIn(48, (context.resources.displayMetrics.heightPixels - dp(90)).coerceAtLeast(48))
      }
    hudParams = params

    val bg =
      GradientDrawable().apply {
        setColor("#EB0B0E14".toColorInt())
        setStroke(dp(1), "#3813D9FF".toColorInt())
        cornerRadius = dp(22).toFloat()
      }

    val root =
      LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = bg
        this.alpha = hudAlpha
        setPadding(dp(10), dp(7), dp(10), dp(8))
      }

    val header =
      LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
      }

    val titleView =
      TextView(context).apply {
        text = "DRX HUD"
        setTextColor("#13D9FF".toColorInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = DrxOverlayFonts.manropeSemiBold(context)
        setPadding(0, 0, dp(8), 0)
      }
    hudHeaderTitle = titleView
    header.addView(titleView)

    val modeBtn =
      hudBadgeBtn(hudDisplayMode.label, "#172030", "#F5F8FF") {
        wakeHud()
        val next =
          when (hudDisplayMode) {
            HudDisplayMode.MINI -> HudDisplayMode.STANDARD
            HudDisplayMode.STANDARD -> HudDisplayMode.FULL
            HudDisplayMode.FULL -> HudDisplayMode.MINI
          }
        hudDisplayMode = next
        applyHudModeVisibility()
        updateLiveHudMetrics(forceHeavyRefresh = true)
        onSaveHudConfig(next, null, null, null, null)
      }
    hudModeBtn = modeBtn
    header.addView(modeBtn)

    header.addView(
      hudBadgeBtn("◐", "#111722", "#929EAF") {
        wakeHud()
        hudAlpha =
          when {
            hudAlpha > 0.82f -> 0.68f
            hudAlpha > 0.56f -> 0.45f
            else -> 0.92f
          }
        root.alpha = hudAlpha
        onSaveHudConfig(null, hudAlpha, null, null, null)
      }
    )

    header.addView(
      hudBadgeBtn("✕", "#111722", "#F07878") {
        dismissLiveHud(notifyCallback = true)
      }
    )

    val primaryTxt =
      TextView(context).apply {
        setTextColor("#F5F8FF".toColorInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = DrxOverlayFonts.interSemiBold(context)
        fontFeatureSettings = "tnum"
        setPadding(0, dp(4), 0, 0)
      }
    hudPrimaryLine = primaryTxt

    val secondaryTxt =
      TextView(context).apply {
        setTextColor("#DCE4F0".toColorInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = DrxOverlayFonts.interRegular(context)
        fontFeatureSettings = "tnum"
        setLineSpacing(dp(2).toFloat(), 1f)
        setPadding(0, dp(3), 0, 0)
      }
    hudSecondaryLine = secondaryTxt

    val honestNote =
      TextView(context).apply {
        setTextColor("#929EAF".toColorInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
        typeface = DrxOverlayFonts.interRegular(context)
        setPadding(0, dp(3), 0, 0)
        text =
          if (hudIsIndo) "Game FPS dibatasi OS • Menampilkan UI FPS & telemetri asli"
          else "Game FPS unavailable on this device • Live UI FPS & telemetry"
      }
    hudHonestNote = honestNote

    // Drag to move + swipe horizontal quick dismiss + tap wakes auto-hide
    var startX = 0
    var startY = 0
    var downRawX = 0f
    var downRawY = 0f
    var moved = false
    @Suppress("ClickableViewAccessibility")
    root.setOnTouchListener { _, ev ->
      when (ev.action) {
        MotionEvent.ACTION_DOWN -> {
          wakeHud()
          startX = params.x
          startY = params.y
          downRawX = ev.rawX
          downRawY = ev.rawY
          moved = false
          true
        }
        MotionEvent.ACTION_MOVE -> {
          val dx = ev.rawX - downRawX
          val dy = ev.rawY - downRawY
          if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
          if (moved) {
            val screenW = context.resources.displayMetrics.widthPixels
            val screenH = context.resources.displayMetrics.heightPixels
            params.x = (startX + dx.safeRoundToInt()).coerceIn(0, (screenW - dp(80)).coerceAtLeast(0))
            params.y = (startY + dy.safeRoundToInt()).coerceIn(36, (screenH - dp(60)).coerceAtLeast(36))
            safeUpdateViewLayout(root, params)
          }
          true
        }
        MotionEvent.ACTION_UP -> {
          val dx = ev.rawX - downRawX
          val dy = ev.rawY - downRawY
          if (!moved) {
            // Tap capsule cycles Mini -> Standard -> Full cleanly
            val next =
              when (hudDisplayMode) {
                HudDisplayMode.MINI -> HudDisplayMode.STANDARD
                HudDisplayMode.STANDARD -> HudDisplayMode.FULL
                HudDisplayMode.FULL -> HudDisplayMode.MINI
              }
            hudDisplayMode = next
            applyHudModeVisibility()
            updateLiveHudMetrics(forceHeavyRefresh = true)
            onSaveHudConfig(next, null, null, null, null)
          } else {
            val screenW = context.resources.displayMetrics.widthPixels
            if (params.x <= 4 && dx < -dp(64) && abs(dy) < dp(48)) {
              dismissLiveHud(notifyCallback = true)
            } else {
              if (params.x < dp(20)) params.x = dp(8)
              else if (screenW - (params.x + root.width) < dp(20)) {
                params.x = (screenW - root.width - dp(8)).coerceAtLeast(8)
              }
              safeUpdateViewLayout(root, params)
              onSaveHudConfig(null, null, params.x, params.y, null)
            }
          }
          scheduleHudAutoHide()
          true
        }
        else -> false
      }
    }

    root.addView(header)
    root.addView(primaryTxt)
    root.addView(secondaryTxt)
    root.addView(honestNote)
    hudContainer = root

    applyHudModeVisibility()
    try {
      if (animationsEnabled) {
        root.alpha = 0f
        root.scaleX = 0.92f
        root.scaleY = 0.92f
      }
      windowManager.addView(root, params)
      if (animationsEnabled) {
        root.animate()
          .alpha(hudAlpha)
          .scaleX(1f)
          .scaleY(1f)
          .setDuration(220L)
          .setInterpolator(OvershootInterpolator(0.75f))
          .start()
      }
      updateLiveHudMetrics(forceHeavyRefresh = true)
      scheduleHudAutoHide()
    } catch (_: Throwable) {
      dismissLiveHud(notifyCallback = false)
    }
  }

  fun updateHudStyle(mode: HudDisplayMode, alpha: Float, autoHide: Boolean, isIndo: Boolean) {
    hudIsIndo = isIndo
    hudAutoHide = autoHide
    hudAlpha = alpha.sanitizeFinite(0.90f).coerceIn(0.35f, 1.0f)
    if (hudDisplayMode != mode) {
      hudDisplayMode = mode
      applyHudModeVisibility()
    }
    hudContainer?.alpha = hudAlpha
    hudHonestNote?.text =
      if (hudIsIndo) "Game FPS dibatasi OS • Menampilkan UI FPS & telemetri asli"
      else "Game FPS unavailable on this device • Live UI FPS & telemetry"
    updateLiveHudMetrics(forceHeavyRefresh = true)
    scheduleHudAutoHide()
  }

  private fun wakeHud() {
    hudHandler.removeCallbacks(hudFadeRunnable)
    hudContainer?.animate()?.alpha(hudAlpha)?.setDuration(120L)?.start()
  }

  private fun scheduleHudAutoHide() {
    hudHandler.removeCallbacks(hudFadeRunnable)
    if (hudAutoHide && hudContainer != null) {
      hudHandler.postDelayed(hudFadeRunnable, 4200L)
    }
  }

  private fun applyHudModeVisibility() {
    hudModeBtn?.text = hudDisplayMode.label
    when (hudDisplayMode) {
      HudDisplayMode.MINI -> {
        hudSecondaryLine?.visibility = View.GONE
        hudHonestNote?.visibility = View.GONE
      }
      HudDisplayMode.STANDARD -> {
        hudSecondaryLine?.visibility = View.VISIBLE
        hudHonestNote?.visibility = View.VISIBLE
      }
      HudDisplayMode.FULL -> {
        hudSecondaryLine?.visibility = View.VISIBLE
        hudHonestNote?.visibility = View.VISIBLE
      }
    }
  }

  fun updateLiveHudMetrics(forceHeavyRefresh: Boolean = false) {
    if (hudContainer == null) return
    hudPollTick++
    val snap = telemetryMonitor.readSnapshot()
    val rec = ScreenRecorderStateHolder.state.value
    val (timerOn, timerSec) = timerStateProvider()

    val freeMb = snap.memoryInfo.availBytes / (1024L * 1024L)
    val totalMb = snap.memoryInfo.totalBytes / (1024L * 1024L)
    val usedPct = snap.memoryInfo.usedPercent.safeRoundToInt()
    val uiFps = snap.uiFps.safeRoundToInt(60)
    val refreshHz = snap.displayInfo.refreshRateHz.safeRoundToInt(60)
    val tempC = snap.batteryInfo.temperatureC.sanitizeFinite(30f)

    if (forceHeavyRefresh || hudPollTick % 3 == 1) {
      cachedStorageFreeText = TimeAndSizeFormatters.formatBytesAuto(snap.storageInfo.freeBytes)
    }

    val recBadge =
      when {
        rec.isRecording && rec.isPaused -> " • ⏸ ${ScreenRecorderService.formatDuration(rec.elapsedSeconds)}"
        rec.isRecording -> " • ● REC ${ScreenRecorderService.formatDuration(rec.elapsedSeconds)}"
        timerOn -> " • ⏱ ${String.format(Locale.US, "%02d:%02d", timerSec / 60, timerSec % 60)}"
        else -> ""
      }

    hudHeaderTitle?.text =
      if (rec.isRecording) "DRX HUD$recBadge" else "DRX HUD (${hudDisplayMode.label})"

    hudPrimaryLine?.text =
      String.format(
        Locale.US,
        "UI %d FPS • %.1f°C • RAM %d MB",
        uiFps,
        tempC,
        freeMb,
      )

    if (hudDisplayMode == HudDisplayMode.STANDARD) {
      val cpuLabel =
        snap.cpuInfo.usagePercent?.let { "${it.safeRoundToInt()}%" }
          ?: snap.cpuInfo.currentFreqMhz?.let { "${it}MHz" }
          ?: (if (hudIsIndo) "Dibatasi OS" else "OS Restricted")
      hudSecondaryLine?.text =
        "CPU: $cpuLabel • Bat ${snap.batteryInfo.levelPercent}% • ${snap.networkSnapshot.transportType} • ${refreshHz}Hz\n" +
          "RAM Used: $usedPct% ($freeMb/$totalMb MB) • Sto Free: $cachedStorageFreeText"
    } else if (hudDisplayMode == HudDisplayMode.FULL) {
      val cpuFreq = snap.cpuInfo.currentFreqMhz?.let { "${it}MHz" } ?: "${snap.cpuInfo.cores} Cores"
      val cpuPct = snap.cpuInfo.usagePercent?.let { "${it.safeRoundToInt()}%" } ?: "OS Restricted"
      val thermalLabel =
        when (snap.thermalStatusCode) {
          PowerManager.THERMAL_STATUS_NONE -> "None"
          PowerManager.THERMAL_STATUS_LIGHT -> "Light"
          PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
          PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
          PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
          else -> "Normal"
        }
      val link = snap.networkSnapshot.linkSpeedMbps?.let { "$it Mbps" } ?: snap.networkSnapshot.networkSubType
      hudSecondaryLine?.text =
        "CPU: $cpuFreq ($cpuPct) • Frame: ${String.format(Locale.US, "%.1f", snap.frameTimeMs.sanitizeFinite(16.6f))} ms\n" +
          "RAM: $usedPct% ($freeMb/${totalMb} MB Free) • Sto: $cachedStorageFreeText\n" +
          "Bat: ${snap.batteryInfo.levelPercent}% (${snap.batteryInfo.chargeSource}) • Thermal: $thermalLabel\n" +
          "Net: ${snap.networkSnapshot.transportType} ($link) • Screen ${refreshHz}Hz"
    }
  }

  fun dismissLiveHud(notifyCallback: Boolean = false) {
    hudHandler.removeCallbacks(hudFadeRunnable)
    hudContainer?.let { view ->
      view.animate().cancel()
      try {
        if (view.isAttachedToWindow) {
          windowManager.removeView(view)
        }
      } catch (_: Throwable) {
      }
    }
    hudContainer = null
    hudParams = null
    hudHeaderTitle = null
    hudModeBtn = null
    hudPrimaryLine = null
    hudSecondaryLine = null
    hudHonestNote = null
    if (notifyCallback) {
      onSaveHudConfig(null, null, null, null, false)
    }
  }

  fun show(
    type: MiniWindowType,
    isIndo: Boolean,
    x: Int,
    y: Int,
    wDp: Int,
    hDp: Int,
    alpha: Float,
  ) {
    if (type == MiniWindowType.MONITOR) {
      showLiveHud(
        mode = HudDisplayMode.STANDARD,
        isIndo = isIndo,
        x = x,
        y = y,
        alpha = alpha,
        autoHide = false,
      )
      return
    }
    dismiss()
    if (!Settings.canDrawOverlays(context)) return
    isIndonesian = isIndo
    activeType = type
    widthDp = wDp.coerceIn(220, 380)
    heightDp = hDp.coerceIn(210, 440)
    currentAlpha = alpha.sanitizeFinite(0.94f).coerceIn(0.50f, 1.0f)
    isMinimized = false
    isMaximized = false

    val overlayType =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
      } else {
        @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
      }

    val params =
      WindowManager.LayoutParams(
        dp(widthDp),
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x.coerceAtLeast(16)
        this.y = y.coerceAtLeast(60)
      }
    layoutParams = params

    val bg =
      GradientDrawable().apply {
        setColor("#F4111722".toColorInt())
        setStroke(dp(1), "#38087BFF".toColorInt())
        cornerRadius = dp(24).toFloat()
      }

    val root =
      LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = bg
        this.alpha = currentAlpha
        setPadding(dp(12), dp(10), dp(12), dp(12))
      }

    // Header Bar (Draggable + Controls)
    val header =
      LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(2), dp(2), dp(8))
      }

    val titleView =
      TextView(context).apply {
        text = if (isIndo) type.titleId else type.titleEn
        setTextColor("#F5F8FF".toColorInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        typeface = DrxOverlayFonts.manropeSemiBold(context)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
      }

    header.addView(titleView)
    header.addView(
      headerIconBtn("◐") {
        currentAlpha =
          when {
            currentAlpha > 0.85f -> 0.72f
            currentAlpha > 0.62f -> 0.54f
            else -> 0.94f
          }
        root.alpha = currentAlpha
        persistBounds()
      }
    )
    header.addView(
      headerIconBtn("–") {
        isMinimized = !isMinimized
        bodyContainer?.visibility = if (isMinimized) View.GONE else View.VISIBLE
      }
    )
    header.addView(
      headerIconBtn("□") {
        isMaximized = !isMaximized
        val targetW = if (isMaximized) 340 else 270
        widthDp = targetW
        params.width = dp(targetW)
        safeUpdateViewLayout(root, params)
        persistBounds()
      }
    )
    header.addView(
      headerIconBtn("✕", "#F07878") {
        dismiss()
      }
    )

    var startX = 0
    var startY = 0
    var touchX = 0f
    var touchY = 0f
    @Suppress("ClickableViewAccessibility")
    header.setOnTouchListener { _, ev ->
      when (ev.action) {
        MotionEvent.ACTION_DOWN -> {
          startX = params.x
          startY = params.y
          touchX = ev.rawX
          touchY = ev.rawY
          true
        }
        MotionEvent.ACTION_MOVE -> {
          val screenW = context.resources.displayMetrics.widthPixels
          val screenH = context.resources.displayMetrics.heightPixels
          params.x = (startX + (ev.rawX - touchX).safeRoundToInt()).coerceIn(0, (screenW - dp(120)).coerceAtLeast(0))
          params.y = (startY + (ev.rawY - touchY).safeRoundToInt()).coerceIn(40, (screenH - dp(80)).coerceAtLeast(40))
          safeUpdateViewLayout(root, params)
          true
        }
        MotionEvent.ACTION_UP -> {
          val screenW = context.resources.displayMetrics.widthPixels
          val curWidthPx = dp(widthDp)
          val snapThreshold = dp(32)
          val targetX =
            when {
              params.x < snapThreshold -> dp(10)
              screenW - (params.x + curWidthPx) < snapThreshold -> (screenW - curWidthPx - dp(10)).coerceAtLeast(0)
              else -> params.x
            }
          if (animationsEnabled && targetX != params.x) {
            ValueAnimator.ofInt(params.x, targetX).apply {
              duration = 200L
              interpolator = DecelerateInterpolator()
              addUpdateListener { anim ->
                params.x = anim.animatedValue as Int
                safeUpdateViewLayout(root, params)
              }
              start()
            }
          } else {
            params.x = targetX
            safeUpdateViewLayout(root, params)
          }
          persistBounds()
          true
        }
        else -> false
      }
    }

    val body =
      LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(4), 0, 0)
      }
    bodyContainer = body

    when (type) {
      MiniWindowType.CALCULATOR -> buildCalculatorBody(body)
      MiniWindowType.NOTES -> buildNotesBody(body, params, root)
      MiniWindowType.TIMER -> buildTimerBody(body)
      MiniWindowType.MONITOR -> {}
    }

    val resizeRow =
      LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        setPadding(0, dp(6), 0, 0)
      }
    resizeRow.addView(
      miniPillBtn("Size -", "#172030") {
        widthDp = (widthDp - 24).coerceAtLeast(220)
        params.width = dp(widthDp)
        safeUpdateViewLayout(root, params)
        persistBounds()
      }
    )
    resizeRow.addView(
      miniPillBtn("Size +", "#172030") {
        widthDp = (widthDp + 24).coerceAtMost(380)
        params.width = dp(widthDp)
        safeUpdateViewLayout(root, params)
        persistBounds()
      }
    )
    body.addView(resizeRow)

    root.addView(header)
    root.addView(body)
    container = root

    try {
      if (animationsEnabled) {
        root.alpha = 0f
        root.scaleX = 0.92f
        root.scaleY = 0.92f
      }
      windowManager.addView(root, params)
      if (animationsEnabled) {
        root.animate()
          .alpha(currentAlpha)
          .scaleX(1f)
          .scaleY(1f)
          .setDuration(220L)
          .setInterpolator(OvershootInterpolator(0.75f))
          .start()
      }
      updateLiveMetrics()
    } catch (_: Throwable) {
      dismiss()
    }
  }

  private fun buildCalculatorBody(parent: LinearLayout) {
    val dispBg =
      GradientDrawable().apply {
        setColor("#0B0E14".toColorInt())
        setStroke(1, "#1AFFFFFF".toColorInt())
        cornerRadius = dp(14).toFloat()
      }
    val display =
      TextView(context).apply {
        text = calcDisplay
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        setTextColor("#13D9FF".toColorInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        typeface = DrxOverlayFonts.manropeBold(context)
        fontFeatureSettings = "tnum"
        background = dispBg
        setPadding(dp(10), dp(10), dp(10), dp(10))
      }
    calcDisplayView = display
    parent.addView(display)

    val rows =
      listOf(
        listOf("C", "⌫", "/", "*"),
        listOf("7", "8", "9", "-"),
        listOf("4", "5", "6", "+"),
        listOf("1", "2", "3", "="),
        listOf("0", ".", ""),
      )
    rows.forEach { r ->
      val rowView =
        LinearLayout(context).apply {
          orientation = LinearLayout.HORIZONTAL
          setPadding(0, dp(5), 0, 0)
        }
      r.forEach { key ->
        if (key.isNotEmpty()) {
          val bgHex =
            when (key) {
              "=", "+", "-", "*", "/" -> "#087BFF"
              "C", "⌫" -> "#1E293B"
              else -> "#172030"
            }
          val btn =
            TextView(context).apply {
              text = key
              gravity = Gravity.CENTER
              setTextColor("#F5F8FF".toColorInt())
              setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
              typeface = DrxOverlayFonts.interSemiBold(context)
              fontFeatureSettings = "tnum"
              background =
                GradientDrawable().apply {
                  setColor(bgHex.toColorInt())
                  setStroke(1, "#1AFFFFFF".toColorInt())
                  cornerRadius = dp(12).toFloat()
                }
              setPadding(0, dp(9), 0, dp(9))
              layoutParams =
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                  marginEnd = dp(4)
                }
              setOnClickListener { handleCalcKey(key) }
            }
          rowView.addView(btn)
        } else {
          rowView.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
          })
        }
      }
      parent.addView(rowView)
    }
  }

  private fun handleCalcKey(key: String) {
    when (key) {
      "C" -> {
        calcDisplay = "0"
        calcAccumulator = 0.0
        calcPendingOp = null
        calcResetOnNext = false
      }
      "⌫" -> {
        calcDisplay = if (calcDisplay.length > 1) calcDisplay.dropLast(1) else "0"
      }
      "+", "-", "*", "/" -> {
        val current = calcDisplay.toDoubleOrNull() ?: 0.0
        if (calcPendingOp != null && !calcResetOnNext) {
          calcAccumulator = evalOp(calcAccumulator, current, calcPendingOp!!)
          calcDisplay = formatCalc(calcAccumulator)
        } else {
          calcAccumulator = current
        }
        calcPendingOp = key[0]
        calcResetOnNext = true
      }
      "=" -> {
        val current = calcDisplay.toDoubleOrNull() ?: 0.0
        if (calcPendingOp != null) {
          val res = evalOp(calcAccumulator, current, calcPendingOp!!)
          calcDisplay = formatCalc(res)
          calcPendingOp = null
          calcResetOnNext = true
        }
      }
      "." -> {
        if (calcResetOnNext) {
          calcDisplay = "0."
          calcResetOnNext = false
        } else if (!calcDisplay.contains('.')) {
          calcDisplay += "."
        }
      }
      else -> {
        calcDisplay =
          if (calcResetOnNext || calcDisplay == "0") {
            calcResetOnNext = false
            key
          } else {
            (calcDisplay + key).take(14)
          }
      }
    }
    calcDisplayView?.text = calcDisplay
  }

  private fun evalOp(a: Double, b: Double, op: Char): Double =
    when (op) {
      '+' -> a + b
      '-' -> a - b
      '*' -> a * b
      '/' -> if (abs(b) > 1e-9) a / b else 0.0
      else -> b
    }

  private fun formatCalc(v: Double): String {
    if (!v.isFinite()) return "0"
    return if (v == v.toLong().toDouble() && abs(v) < 1e15) {
      v.toLong().toString()
    } else {
      String.format(Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')
    }
  }

  private fun buildNotesBody(
    parent: LinearLayout,
    params: WindowManager.LayoutParams,
    root: View,
  ) {
    val repo = DrxRepository(DrxDatabase.getInstance(context))
    val fieldBg =
      GradientDrawable().apply {
        setColor("#0B0E14".toColorInt())
        setStroke(1, "#1AFFFFFF".toColorInt())
        cornerRadius = dp(14).toFloat()
      }
    val editText =
      EditText(context).apply {
        hint = if (isIndonesian) "Catatan cepat (loadout/strategi)..." else "Quick tactical note..."
        setHintTextColor("#626D7D".toColorInt())
        setTextColor("#F5F8FF".toColorInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = DrxOverlayFonts.interRegular(context)
        background = fieldBg
        minLines = 3
        maxLines = 5
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        setPadding(dp(10), dp(8), dp(10), dp(8))
        setOnClickListener {
          params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
          safeUpdateViewLayout(root, params)
          requestFocus()
        }
      }

    val statusText =
      TextView(context).apply {
        setTextColor("#929EAF".toColorInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        typeface = DrxOverlayFonts.interRegular(context)
        setPadding(0, dp(4), 0, dp(4))
        text = if (isIndonesian) "Tersimpan lokal di DRX Notes" else "Saves locally to DRX Notes"
      }

    val savedList =
      TextView(context).apply {
        setTextColor("#F5F8FF".toColorInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = DrxOverlayFonts.interRegular(context)
        setPadding(0, dp(4), 0, 0)
      }

    val scroll =
      ScrollView(context).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(88))
        addView(savedList)
      }

    fun refreshSavedPreview() {
      scope.launch(Dispatchers.IO) {
        val formatted =
          try {
            val list = repo.getNotesSnapshot().take(4)
            if (list.isEmpty()) "-"
            else list.joinToString("\n") { "• ${it.title}: ${it.content}" }
          } catch (_: Throwable) {
            "-"
          }
        scope.launch(Dispatchers.Main) { savedList.text = formatted }
      }
    }
    refreshSavedPreview()

    val btnRow =
      LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(6), 0, 0)
      }
    btnRow.addView(
      miniPillBtn(if (isIndonesian) "Simpan" else "Save", "#087BFF") {
        val txt = editText.text?.toString()?.trim().orEmpty()
        if (txt.isNotEmpty()) {
          scope.launch(Dispatchers.IO) {
            try {
              repo.upsertNote(
                GamingNoteEntity(
                  title = "Overlay Note",
                  content = txt,
                  gameTag = "Quick",
                )
              )
              refreshSavedPreview()
            } catch (_: Throwable) {
            }
          }
          editText.setText("")
          statusText.text = if (isIndonesian) "Tersimpan!" else "Saved!"
        }
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        safeUpdateViewLayout(root, params)
      }
    )
    btnRow.addView(
      miniPillBtn(if (isIndonesian) "Selesai Ketik" else "Done", "#172030") {
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        safeUpdateViewLayout(root, params)
      }
    )

    parent.addView(editText)
    parent.addView(btnRow)
    parent.addView(statusText)
    parent.addView(scroll)
  }

  private fun buildTimerBody(parent: LinearLayout) {
    val label =
      TextView(context).apply {
        setTextColor("#13D9FF".toColorInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        typeface = DrxOverlayFonts.manropeBold(context)
        fontFeatureSettings = "tnum"
        gravity = Gravity.CENTER
        setPadding(0, dp(10), 0, dp(10))
      }
    timerHudText = label

    val controls =
      LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
      }
    val toggleBtn =
      miniPillBtn("Start / Pause", "#087BFF") {
        onToggleTimer()
        updateLiveMetrics()
      }
    timerActionBtn = toggleBtn
    controls.addView(toggleBtn)
    controls.addView(
      miniPillBtn("Reset", "#172030") {
        onResetTimer()
        updateLiveMetrics()
      }
    )

    parent.addView(label)
    parent.addView(controls)
  }

  fun updateLiveMetrics() {
    updateLiveHudMetrics(forceHeavyRefresh = false)
    if (container == null) return
    val (running, secs) = timerStateProvider()
    timerHudText?.text = String.format(Locale.US, "%02d:%02d", secs / 60, secs % 60)
    timerActionBtn?.text =
      if (running) (if (isIndonesian) "Jeda" else "Pause")
      else (if (isIndonesian) "Mulai" else "Start")
  }

  private fun persistBounds() {
    val lp = layoutParams ?: return
    onSaveBounds(lp.x, lp.y, widthDp, heightDp, currentAlpha)
  }

  private fun headerIconBtn(label: String, textHex: String = "#F5F8FF", onClick: () -> Unit): TextView {
    return TextView(context).apply {
      text = label
      gravity = Gravity.CENTER
      setTextColor(textHex.toColorInt())
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      typeface = DrxOverlayFonts.interSemiBold(context)
      setPadding(dp(7), dp(4), dp(7), dp(4))
      setOnClickListener { onClick() }
    }
  }

  private fun hudBadgeBtn(label: String, bgHex: String, textHex: String, onClick: () -> Unit): TextView {
    return TextView(context).apply {
      text = label
      gravity = Gravity.CENTER
      setTextColor(textHex.toColorInt())
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
      typeface = DrxOverlayFonts.interSemiBold(context)
      background =
        GradientDrawable().apply {
          setColor(bgHex.toColorInt())
          setStroke(1, "#26FFFFFF".toColorInt())
          cornerRadius = dp(50).toFloat()
        }
      layoutParams =
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = dp(4) }
      setPadding(dp(8), dp(3), dp(8), dp(3))
      setOnClickListener { onClick() }
    }
  }

  private fun miniPillBtn(label: String, bgHex: String, onClick: () -> Unit): TextView {
    return TextView(context).apply {
      text = label
      setTextColor("#F5F8FF".toColorInt())
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
      typeface = DrxOverlayFonts.interSemiBold(context)
      background =
        GradientDrawable().apply {
          setColor(bgHex.toColorInt())
          setStroke(1, "#1AFFFFFF".toColorInt())
          cornerRadius = dp(50).toFloat()
        }
      layoutParams =
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.WRAP_CONTENT,
          LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = dp(5) }
      setPadding(dp(12), dp(7), dp(12), dp(7))
      setOnClickListener { onClick() }
    }
  }

  private fun safeUpdateViewLayout(view: View, params: WindowManager.LayoutParams) {
    try {
      if (view.isAttachedToWindow) {
        windowManager.updateViewLayout(view, params)
      }
    } catch (_: Throwable) {
    }
  }

  fun dismiss() {
    container?.let { view ->
      try {
        if (view.isAttachedToWindow) {
          windowManager.removeView(view)
        }
      } catch (_: Throwable) {
      }
    }
    container = null
    bodyContainer = null
    layoutParams = null
    activeType = null
    timerHudText = null
    timerActionBtn = null
    calcDisplayView = null
  }

  fun dismissAll() {
    dismiss()
    dismissLiveHud(notifyCallback = false)
  }

  private fun dp(v: Int): Int =
    TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      v.toFloat(),
      context.resources.displayMetrics,
    ).safeRoundToInt(v)
}
