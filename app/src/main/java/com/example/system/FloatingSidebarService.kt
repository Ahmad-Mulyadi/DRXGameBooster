package com.example.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.toColorInt
import com.example.MainActivity
import com.example.R
import com.example.data.model.FloatingAppShortcut
import com.example.data.model.GamingProfile
import com.example.data.model.HudDisplayMode
import com.example.data.model.QuickOverlayProfile
import com.example.data.model.VoicePreset
import com.example.data.prefs.DrxPreferences
import com.example.data.prefs.DrxUserPrefs
import com.example.ui.screens.ToolRoutes
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal object DrxOverlayFonts {
  @Volatile private var cachedInterReg: Typeface? = null
  @Volatile private var cachedInterMed: Typeface? = null
  @Volatile private var cachedInterSemi: Typeface? = null
  @Volatile private var cachedInterBold: Typeface? = null
  @Volatile private var cachedManropeSemi: Typeface? = null
  @Volatile private var cachedManropeBold: Typeface? = null

  private fun load(context: Context, resId: Int, fallback: Typeface): Typeface =
    runCatching { ResourcesCompat.getFont(context, resId) }.getOrNull() ?: fallback

  fun interRegular(context: Context): Typeface =
    cachedInterReg ?: load(context, R.font.inter_regular, Typeface.SANS_SERIF).also { cachedInterReg = it }

  fun interMedium(context: Context): Typeface =
    cachedInterMed ?: load(context, R.font.inter_medium, Typeface.SANS_SERIF).also { cachedInterMed = it }

  fun interSemiBold(context: Context): Typeface =
    cachedInterSemi ?: load(context, R.font.inter_semibold, Typeface.DEFAULT_BOLD).also { cachedInterSemi = it }

  fun interBold(context: Context): Typeface =
    cachedInterBold ?: load(context, R.font.inter_bold, Typeface.DEFAULT_BOLD).also { cachedInterBold = it }

  fun manropeSemiBold(context: Context): Typeface =
    cachedManropeSemi ?: load(context, R.font.manrope_semibold, Typeface.DEFAULT_BOLD).also { cachedManropeSemi = it }

  fun manropeBold(context: Context): Typeface =
    cachedManropeBold ?: load(context, R.font.manrope_bold, Typeface.DEFAULT_BOLD).also { cachedManropeBold = it }
}

@SuppressLint("SetTextI18n")
class FloatingSidebarService : Service() {

  companion object {
    const val CHANNEL_ID = "drx_floating_sidebar_channel"
    const val NOTIF_ID = 7001
    const val ACTION_START = "com.derrex.gameturbo.ACTION_START_SIDEBAR"
    const val ACTION_STOP = "com.derrex.gameturbo.ACTION_STOP_SIDEBAR"
    const val ACTION_REFRESH_STYLE = "com.derrex.gameturbo.ACTION_REFRESH_SIDEBAR"
    const val EXTRA_LAUNCHED_GAME_PKG = "extra_launched_game_pkg"

    @Volatile
    var isRunning: Boolean = false
      private set
  }

  private enum class SidebarTab(val label: String, val iconRes: Int) {
    HUD("HUD", R.drawable.ic_sidebar_home),
    APPS("Apps", R.drawable.ic_sidebar_apps),
    VOICE("Voice", R.drawable.ic_sidebar_voice),
    TOOLS("Tools", R.drawable.ic_sidebar_tools),
    RECORD("Record", R.drawable.ic_sidebar_record),
    MONITOR("Monitor", R.drawable.ic_sidebar_monitor),
  }

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private lateinit var windowManager: WindowManager
  private lateinit var telemetryMonitor: TelemetryMonitor
  private lateinit var tweaksManager: SystemTweaksManager
  private lateinit var prefs: DrxPreferences
  private lateinit var voiceEngine: VoiceStudioEngine
  private lateinit var miniWindowManager: FloatingMiniWindowManager

  private var handleView: LinearLayout? = null
  private var handleBarStrip: View? = null
  private var handleLayoutParams: WindowManager.LayoutParams? = null

  private var panelBackdrop: FrameLayout? = null
  private var panelCard: LinearLayout? = null
  private var tabBarContainer: LinearLayout? = null
  private var tabContentScroll: ScrollView? = null
  private var tabContentContainer: LinearLayout? = null

  private var activeTab = SidebarTab.HUD
  private var isExpanded = false
  @Volatile private var currentPrefs = DrxUserPrefs()
  @Volatile private var cachedShortcuts: List<FloatingAppShortcut> = emptyList()

  // Live service states
  private var timerRunning = false
  private var elapsedSeconds = 0L
  private var timerRunStartElapsedMs = 0L
  private var timerAccumulatedMs = 0L
  private var boostStatusText = ""
  private var activeVoicePreset = VoicePreset.NORMAL
  private var isVoicePreviewRunning = false
  private var lastVoicePitch = 1.0f
  private var pollCycleCount = 0

  // Foreground game-exit / return-to-DRX auto-close tracking
  private var launchedGamePkg: String? = null
  private var monitorSinceMs: Long = 0L
  private var suppressAutoCloseUntilElapsedMs: Long = 0L
  private var hasEnteredExternalApp: Boolean = false
  private var leftGameTicks: Int = 0

  // Dynamic view references for adaptive ticker refresh
  private var statusPillView: TextView? = null
  private var homeTimerView: TextView? = null
  private var homeBoostStatusView: TextView? = null
  private var monitorDetailsView: TextView? = null
  private var toolsStatusView: TextView? = null
  private var voiceStatusView: TextView? = null
  private var recordStatusView: TextView? = null

  private val handler = Handler(Looper.getMainLooper())
  private val autoHideRunnable =
    Runnable {
      if (!isExpanded && currentPrefs.floatingAutoHide) {
        handleView?.animate()?.alpha(0.30f)?.setDuration(260L)?.start()
      }
    }

  private val ticker =
    object : Runnable {
      override fun run() {
        try {
          pollCycleCount++
          if (timerRunning && timerRunStartElapsedMs > 0L) {
            elapsedSeconds = ((timerAccumulatedMs + (SystemClock.elapsedRealtime() - timerRunStartElapsedMs)) / 1000L).coerceAtLeast(0L)
          } else {
            elapsedSeconds = (timerAccumulatedMs / 1000L).coerceAtLeast(0L)
          }
          val recState = ScreenRecorderStateHolder.state.value
          val needsFastRefresh =
            isExpanded ||
              miniWindowManager.isLiveHudShowing ||
              recState.isRecording ||
              recState.countdownRemainingSec > 0 ||
              timerRunning
          if (needsFastRefresh || pollCycleCount % 2 == 0) {
            updateLiveMetrics()
            miniWindowManager.updateLiveMetrics()
          }
          checkForegroundAutoClose()
        } catch (_: Throwable) {
        }
        if (isRunning) {
          val profileMs = currentPrefs.quickOverlayProfile.pollingIntervalMs
          val rec = ScreenRecorderStateHolder.state.value
          val delayMs =
            if (rec.isRecording || rec.countdownRemainingSec > 0 || timerRunning) {
              minOf(1000L, profileMs)
            } else {
              profileMs
            }
          handler.postDelayed(this, delayMs)
        }
      }
    }

  private fun checkForegroundAutoClose() {
    if (!currentPrefs.floatingAutoCloseOnExit) return
    if (SystemClock.elapsedRealtime() < suppressAutoCloseUntilElapsedMs) return
    if (!tweaksManager.hasUsageStatsPermission()) return
    val fgPkg = tweaksManager.detectRecentForegroundPackage(15_000L, monitorSinceMs) ?: return
    if (fgPkg == "com.android.systemui") return

    if (fgPkg != packageName) {
      hasEnteredExternalApp = true
      val targetGame = launchedGamePkg
      if (targetGame != null) {
        if (fgPkg == targetGame) {
          leftGameTicks = 0
        } else {
          leftGameTicks++
          if (leftGameTicks >= 3) {
            autoDismissFloatingControlCenter()
          }
        }
      }
    } else if (hasEnteredExternalApp) {
      autoDismissFloatingControlCenter()
    }
  }

  private fun autoDismissFloatingControlCenter() {
    isRunning = false
    handler.removeCallbacks(ticker)
      serviceScope.launch {
        try {
          prefs.setFloatingEnabled(false)
        } catch (_: Throwable) {
        }
        miniWindowManager.dismissAll()
        stopSelf()
      }
    }

    override fun onCreate() {
      super.onCreate()
      windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
      telemetryMonitor = TelemetryMonitor(this)
      tweaksManager = SystemTweaksManager(this)
      prefs = DrxPreferences(this)
      voiceEngine = VoiceStudioEngine()
      miniWindowManager =
        FloatingMiniWindowManager(
          context = this,
          windowManager = windowManager,
          telemetryMonitor = telemetryMonitor,
          scope = serviceScope,
          onSaveBounds = { x, y, w, h, alpha ->
            serviceScope.launch { prefs.setMiniWindowBounds(x, y, w, h, alpha) }
          },
          onSaveHudConfig = { mode, alpha, x, y, enabled ->
            serviceScope.launch {
              prefs.setHudOverlayConfig(
                enabled = enabled,
                mode = mode,
                opacity = alpha,
                posX = x,
                posY = y,
              )
              if (enabled == false) {
                currentPrefs = currentPrefs.copy(hudOverlayEnabled = false)
              }
            }
          },
          onToggleTimer = {
            toggleSessionTimer()
            updateLiveMetrics()
          },
          onResetTimer = {
            resetSessionTimer()
            updateLiveMetrics()
          },
          timerStateProvider = {
            val secs =
              if (timerRunning && timerRunStartElapsedMs > 0L) {
                ((timerAccumulatedMs + (SystemClock.elapsedRealtime() - timerRunStartElapsedMs)) / 1000L).coerceAtLeast(0L)
              } else {
                (timerAccumulatedMs / 1000L).coerceAtLeast(0L)
              }
            timerRunning to secs
          },
        )
    try {
      createNotificationChannel()
      startForegroundNotification()
    } catch (_: Throwable) {
      stopSelf()
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> {
        serviceScope.launch {
          prefs.setFloatingEnabled(false)
          stopSelf()
        }
        return START_NOT_STICKY
      }
      ACTION_REFRESH_STYLE -> {
        serviceScope.launch { applyStoredPreferences() }
        return START_STICKY
      }
    }

    if (!Settings.canDrawOverlays(this)) {
      serviceScope.launch { prefs.setFloatingEnabled(false) }
      stopSelf()
      return START_NOT_STICKY
    }

    val extraGame = intent?.getStringExtra(EXTRA_LAUNCHED_GAME_PKG)?.takeIf { it.isNotBlank() }
    if (extraGame != null) {
      launchedGamePkg = extraGame
      monitorSinceMs = System.currentTimeMillis()
      hasEnteredExternalApp = false
      leftGameTicks = 0
    } else if (intent?.action == ACTION_START || !isRunning) {
      launchedGamePkg = null
      monitorSinceMs = System.currentTimeMillis()
      hasEnteredExternalApp = false
      leftGameTicks = 0
    }

    isRunning = true
    telemetryMonitor.startFpsMonitoring()
    serviceScope.launch {
      applyStoredPreferences()
      if (handleView == null) {
        attachEdgeHandle()
      }
    }
    handler.removeCallbacks(ticker)
    handler.post(ticker)
    return START_STICKY
  }

  private suspend fun applyStoredPreferences() {
    val p =
      try {
        prefs.prefsFlow.first()
      } catch (_: Throwable) {
        currentPrefs
      }
    val sideChanged = currentPrefs.floatingEdgeSide != p.floatingEdgeSide
    currentPrefs = p
    miniWindowManager.animationsEnabled = p.animationsEnabled
    serviceScope.launch(Dispatchers.IO) {
      try {
        cachedShortcuts = tweaksManager.resolveFloatingAppShortcuts(p.floatingPinnedApps)
        if (isExpanded && activeTab == SidebarTab.APPS) {
          serviceScope.launch(Dispatchers.Main) { rebuildTabContent() }
        }
      } catch (_: Throwable) {
      }
    }

    if (sideChanged && handleView != null) {
      removeEdgeHandle()
      attachEdgeHandle()
    } else {
      updateEdgeHandleVisuals()
    }

    if (isExpanded) {
      panelCard?.alpha = p.floatingAlpha
      panelCard?.scaleX = p.floatingScale
      panelCard?.scaleY = p.floatingScale
    }

    val isIndo = p.language.code == "id"
    if (p.hudOverlayEnabled && Settings.canDrawOverlays(this)) {
      if (!miniWindowManager.isLiveHudShowing) {
        miniWindowManager.showLiveHud(
          mode = p.hudMode,
          isIndo = isIndo,
          x = p.hudPosX,
          y = p.hudPosY,
          alpha = p.hudOpacity,
          autoHide = p.hudAutoHide,
        )
      } else {
        miniWindowManager.updateHudStyle(
          mode = p.hudMode,
          alpha = p.hudOpacity,
          autoHide = p.hudAutoHide,
          isIndo = isIndo,
        )
      }
    } else if (!p.hudOverlayEnabled && miniWindowManager.isLiveHudShowing) {
      miniWindowManager.dismissLiveHud(notifyCallback = false)
    }
  }

  private fun attachEdgeHandle() {
    val overlayType =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
      } else {
        @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
      }

    val isRight = currentPrefs.floatingEdgeSide == 1
    val params =
      WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.TOP or (if (isRight) Gravity.END else Gravity.START)
        x = 0
        y = currentPrefs.floatingPosY.coerceIn(80, (resources.displayMetrics.heightPixels - dp(120)).coerceAtLeast(100))
      }
    handleLayoutParams = params

    val handleBg =
      GradientDrawable().apply {
        setColor("#EB0B0E14".toColorInt())
        setStroke(1, "#33087BFF".toColorInt())
        val r = dp(14).toFloat()
        cornerRadii =
          if (isRight) floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
          else floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
      }

    val strip =
      View(this).apply {
        background =
          GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf("#13D9FF".toColorInt(), "#087BFF".toColorInt()),
          ).apply { cornerRadius = dp(4).toFloat() }
        layoutParams = LinearLayout.LayoutParams(dp(4), dp(46))
      }
    handleBarStrip = strip

    val edgeContainer =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        background = handleBg
        alpha = currentPrefs.floatingHandleAlpha
        val thicknessPx = dp(currentPrefs.floatingHandleThicknessDp.coerceIn(8, 24))
        minimumWidth = thicknessPx
        setPadding(dp(4), dp(14), dp(4), dp(14))
        addView(strip)
      }

    // Gesture handler: Swipe L->R (Left edge) or R->L (Right edge) opens panel;
    // Tap opens panel as fallback; Long-press drags vertically to avoid OS back-gesture area!
    var downX = 0f
    var downY = 0f
    var startParamY = 0
    var isVerticalDrag = false
    var longPressTriggered = false
    val longPressRunnable =
      Runnable {
        longPressTriggered = true
        isVerticalDrag = true
        triggerHaptic(32L)
        edgeContainer.animate().scaleY(1.15f).alpha(1f).setDuration(120L).start()
      }

    @Suppress("ClickableViewAccessibility")
    edgeContainer.setOnTouchListener { _, ev ->
      when (ev.action) {
        MotionEvent.ACTION_DOWN -> {
          handler.removeCallbacks(autoHideRunnable)
          edgeContainer.animate().alpha(1f).setDuration(100L).start()
          downX = ev.rawX
          downY = ev.rawY
          startParamY = params.y
          isVerticalDrag = false
          longPressTriggered = false
          handler.postDelayed(longPressRunnable, 300L)
          true
        }
        MotionEvent.ACTION_MOVE -> {
          val dx = ev.rawX - downX
          val dy = ev.rawY - downY
          if (!longPressTriggered && (abs(dx) > dp(10) || abs(dy) > dp(10))) {
            handler.removeCallbacks(longPressRunnable)
          }
          if (isVerticalDrag) {
            val maxY = (resources.displayMetrics.heightPixels - dp(110)).coerceAtLeast(100)
            params.y = (startParamY + dy.safeRoundToInt()).coerceIn(60, maxY)
            safeUpdateViewLayout(edgeContainer, params)
          }
          true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          handler.removeCallbacks(longPressRunnable)
          edgeContainer.animate().scaleY(1f).setDuration(120L).start()
          val dx = ev.rawX - downX
          val dy = ev.rawY - downY
          val sens = currentPrefs.floatingSwipeSensitivity.sanitizeFinite(1f).coerceIn(0.4f, 2.5f)
          val swipeThresh = dp((34f / sens).safeRoundToInt(34).coerceIn(20, 64))

          if (isVerticalDrag) {
            serviceScope.launch { prefs.setFloatingPosition(0, params.y) }
            scheduleAutoHide()
          } else {
            val inwardSwipe = if (isRight) dx < -swipeThresh else dx > swipeThresh
            val isTap = abs(dx) < dp(12) && abs(dy) < dp(12)
            if (inwardSwipe || isTap) {
              triggerHaptic(18L)
              expandControlCenter()
            } else {
              scheduleAutoHide()
            }
          }
          true
        }
        else -> false
      }
    }

    handleView = edgeContainer
    if (!safeAddView(edgeContainer, params)) {
      handleView = null
      return
    }
    scheduleAutoHide()
  }

  private fun updateEdgeHandleVisuals() {
    val edge = handleView ?: return
    val lp = handleLayoutParams ?: return
    val thicknessPx = dp(currentPrefs.floatingHandleThicknessDp.coerceIn(8, 24))
    edge.minimumWidth = thicknessPx
    edge.alpha = currentPrefs.floatingHandleAlpha.sanitizeFinite(0.85f).coerceIn(0.15f, 1f)
    lp.y = currentPrefs.floatingPosY.coerceIn(60, (resources.displayMetrics.heightPixels - dp(110)).coerceAtLeast(100))
    safeUpdateViewLayout(edge, lp)
    scheduleAutoHide()
  }

  private fun removeEdgeHandle() {
    handleView?.let {
      it.animate().cancel()
      safeRemoveView(it)
    }
    handleView = null
  }

  private fun scheduleAutoHide() {
    handler.removeCallbacks(autoHideRunnable)
    if (currentPrefs.floatingAutoHide && !isExpanded) {
      handler.postDelayed(autoHideRunnable, 3400L)
    }
  }

  private fun expandControlCenter() {
    if (isExpanded) return
    if (!Settings.canDrawOverlays(this)) return
    isExpanded = true
    handler.removeCallbacks(autoHideRunnable)
    handleView?.visibility = View.GONE

    val overlayType =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
      } else {
        @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
      }

    val isRight = currentPrefs.floatingEdgeSide == 1
    val safeScale = currentPrefs.floatingScale.sanitizeFinite(1f).coerceIn(0.75f, 1.3f)
    val panelWidth = dp((312 * safeScale).safeRoundToInt(312).coerceIn(265, 360))
    val maxHeight = (resources.displayMetrics.heightPixels * 0.78f).safeRoundToInt(600).coerceAtLeast(320)

    // Full-screen translucent click-outside backdrop so tapping anywhere outside closes cleanly
    val backdrop =
      FrameLayout(this).apply {
        setBackgroundColor("#70050609".toColorInt())
        alpha = 0f
        setOnClickListener { collapseControlCenter() }
      }

    val panelBg =
      GradientDrawable().apply {
        setColor("#F40B0E14".toColorInt())
        setStroke(dp(1), "#26FFFFFF".toColorInt())
        cornerRadius = dp(28).toFloat()
      }

    val card =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = panelBg
        alpha = currentPrefs.floatingAlpha
        setPadding(dp(14), dp(14), dp(14), dp(14))
        isClickable = true // Prevent clicks inside panel from bubbling to backdrop
        setOnClickListener {}
      }

    // Swipe-back gesture on panel header to collapse smoothly
    var panelDownX = 0f
    @Suppress("ClickableViewAccessibility")
    card.setOnTouchListener { _, ev ->
      when (ev.action) {
        MotionEvent.ACTION_DOWN -> {
          panelDownX = ev.rawX
          false
        }
        MotionEvent.ACTION_UP -> {
          val dx = ev.rawX - panelDownX
          val outwardSwipe = if (isRight) dx > dp(72) else dx < -dp(72)
          if (outwardSwipe) {
            collapseControlCenter()
            true
          } else false
        }
        else -> false
      }
    }

    // Header Row: Authentic DRX Logo + Title + Settings + Close
    val headerRow =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
      }
    val logoBadge =
      ImageView(this).apply {
        setImageResource(R.drawable.drx_logo)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = "DRX Logo"
        background =
          GradientDrawable().apply {
            setColor("#050609".toColorInt())
            setStroke(1, "#33087BFF".toColorInt())
            cornerRadius = dp(10).toFloat()
          }
        setPadding(dp(4), dp(4), dp(4), dp(4))
        layoutParams =
          LinearLayout.LayoutParams(dp(32), dp(32)).apply {
            marginEnd = dp(9)
          }
      }
    val brandCol =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
      }
    val titleTxt =
      TextView(this).apply {
        text = "DRX Control Center"
        setTextColor("#F5F8FF".toColorInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = DrxOverlayFonts.manropeSemiBold(this@FloatingSidebarService)
      }
    val statusPill =
      TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = DrxOverlayFonts.interMedium(this@FloatingSidebarService)
        fontFeatureSettings = "tnum"
      }
    statusPillView = statusPill
    brandCol.addView(titleTxt)
    brandCol.addView(statusPill)

    headerRow.addView(logoBadge)
    headerRow.addView(brandCol)
    headerRow.addView(
      createSmallIconBtn("⚙", "#172030", "#DCE4F0") {
        collapseControlCenter()
        val i =
          Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("open_route", "tool_sidebar")
        safeStartActivity(i)
      }
    )
    headerRow.addView(
      createSmallIconBtn("✕", "#172030", "#F07878") {
        collapseControlCenter()
      }
    )

    // 5-Tab Bar: HOME | APPS | VOICE | TOOLS | MONITOR
    val tabBar =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(8), 0, dp(8))
      }
    tabBarContainer = tabBar

    val scroll =
      ScrollView(this).apply {
        layoutParams =
          LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
          )
      }
    tabContentScroll = scroll

    val contentBox =
      LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
      }
    tabContentContainer = contentBox
    scroll.addView(contentBox)

    card.addView(headerRow)
    card.addView(tabBar)
    card.addView(scroll)

    val cardLp =
      FrameLayout.LayoutParams(panelWidth, maxHeight).apply {
        gravity = Gravity.CENTER_VERTICAL or (if (isRight) Gravity.END else Gravity.START)
        marginStart = if (isRight) dp(8) else dp(12)
        marginEnd = if (isRight) dp(12) else dp(8)
      }
    backdrop.addView(card, cardLp)

    val winParams =
      WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        overlayType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
      )

    panelBackdrop = backdrop
    panelCard = card
    if (!safeAddView(backdrop, winParams)) {
      isExpanded = false
      panelBackdrop = null
      panelCard = null
      tabBarContainer = null
      tabContentScroll = null
      tabContentContainer = null
      handleView?.visibility = View.VISIBLE
      return
    }

    rebuildTabBar()
    rebuildTabContent()
    updateLiveMetrics()

    // Smooth slide-in + fade (gated by DRX Fluid Motion user preference)
    if (currentPrefs.animationsEnabled) {
      val slideOffset = if (isRight) dp(84).toFloat() else -dp(84).toFloat()
      card.translationX = slideOffset
      card.alpha = 0f
      backdrop.animate().alpha(1f).setDuration(190L).start()
      card.animate()
        .translationX(0f)
        .alpha(currentPrefs.floatingAlpha)
        .setDuration(260L)
        .setInterpolator(OvershootInterpolator(0.55f))
        .start()
    } else {
      card.translationX = 0f
      card.alpha = currentPrefs.floatingAlpha
      backdrop.alpha = 1f
    }
  }

  private fun collapseControlCenter() {
    if (!isExpanded) return
    isExpanded = false
    val backdrop = panelBackdrop
    val card = panelCard
    val isRight = currentPrefs.floatingEdgeSide == 1
    val slideOut = if (isRight) dp(64).toFloat() else -dp(64).toFloat()

    if (backdrop != null && card != null) {
      if (currentPrefs.animationsEnabled) {
        backdrop.animate().alpha(0f).setDuration(150L).start()
        card.animate()
          .translationX(slideOut)
          .alpha(0f)
          .setDuration(150L)
          .setInterpolator(DecelerateInterpolator())
          .withEndAction {
            safeRemoveView(backdrop)
            panelBackdrop = null
            panelCard = null
            tabBarContainer = null
            tabContentScroll = null
            tabContentContainer = null
            if (isRunning) {
              handleView?.visibility = View.VISIBLE
              scheduleAutoHide()
            }
          }
          .start()
      } else {
        safeRemoveView(backdrop)
        panelBackdrop = null
        panelCard = null
        tabBarContainer = null
        tabContentScroll = null
        tabContentContainer = null
        if (isRunning) {
          handleView?.visibility = View.VISIBLE
          scheduleAutoHide()
        }
      }
    } else {
      handleView?.visibility = View.VISIBLE
      scheduleAutoHide()
    }
  }

  private fun rebuildTabBar() {
    val bar = tabBarContainer ?: return
    bar.removeAllViews()
    SidebarTab.entries.forEach { tab ->
      val selected = tab == activeTab
      val fgHex = if (selected) "#13D9FF" else "#929EAF"
      val itemBox =
        LinearLayout(this).apply {
          orientation = LinearLayout.VERTICAL
          gravity = Gravity.CENTER
          background =
            GradientDrawable().apply {
              setColor((if (selected) "#172030" else "#0B0E14").toColorInt())
              setStroke(1, (if (selected) "#087BFF" else "#16FFFFFF").toColorInt())
              cornerRadius = dp(14).toFloat()
            }
          layoutParams =
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
              marginEnd = dp(4)
            }
          setPadding(dp(2), dp(7), dp(2), dp(7))
          setOnClickListener {
            triggerHaptic(12L)
            activeTab = tab
            rebuildTabBar()
            rebuildTabContent()
          }
        }
      val iconView =
        ImageView(this).apply {
          setImageResource(tab.iconRes)
          setColorFilter(fgHex.toColorInt())
          layoutParams =
            LinearLayout.LayoutParams(dp(18), dp(18)).apply {
              bottomMargin = dp(3)
            }
        }
      val labelView =
        TextView(this).apply {
          text = tab.label
          gravity = Gravity.CENTER
          maxLines = 1
          setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
          typeface =
            if (selected) DrxOverlayFonts.interSemiBold(this@FloatingSidebarService)
            else DrxOverlayFonts.interMedium(this@FloatingSidebarService)
          setTextColor(fgHex.toColorInt())
        }
      itemBox.addView(iconView)
      itemBox.addView(labelView)
      bar.addView(itemBox)
    }
  }

  private fun rebuildTabContent() {
    val container = tabContentContainer ?: return
    container.animate().cancel()
    container.removeAllViews()
    val isIndo = currentPrefs.language.code == "id"

    when (activeTab) {
      SidebarTab.HUD -> buildHudTab(container, isIndo)
      SidebarTab.APPS -> buildAppsTab(container, isIndo)
      SidebarTab.VOICE -> buildVoiceTab(container, isIndo)
      SidebarTab.TOOLS -> buildToolsTab(container, isIndo)
      SidebarTab.RECORD -> buildRecordTab(container, isIndo)
      SidebarTab.MONITOR -> buildMonitorTab(container, isIndo)
    }
    updateLiveMetrics()
    if (currentPrefs.animationsEnabled) {
      container.alpha = 0f
      container.animate().alpha(1f).setDuration(160L).start()
    } else {
      container.alpha = 1f
    }
  }

  private fun buildHudTab(container: LinearLayout, isIndo: Boolean) {
    // Live Performance HUD Floating Capsule Controls
    val hudCard = createSectionBox()
    hudCard.addView(sectionTitle(if (isIndo) "Live Performance HUD" else "Live Performance HUD"))
    val hudActive = miniWindowManager.isLiveHudShowing || currentPrefs.hudOverlayEnabled
    val hudToggleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(6)) }
    hudToggleRow.addView(
      createGridBtn(
        if (hudActive) (if (isIndo) "HUD Aktif • Matikan" else "HUD ON • Turn Off")
        else (if (isIndo) "Tampilkan Live HUD" else "Show Live HUD"),
        if (hudActive) "#55D69A" else "#087BFF",
        hudActive,
      ) {
        val nextEnabled = !hudActive
        serviceScope.launch {
          prefs.setHudOverlayConfig(enabled = nextEnabled)
          currentPrefs = currentPrefs.copy(hudOverlayEnabled = nextEnabled)
          if (nextEnabled) {
            miniWindowManager.showLiveHud(
              mode = currentPrefs.hudMode,
              isIndo = isIndo,
              x = currentPrefs.hudPosX,
              y = currentPrefs.hudPosY,
              alpha = currentPrefs.hudOpacity,
              autoHide = currentPrefs.hudAutoHide,
            )
          } else {
            miniWindowManager.dismissLiveHud(notifyCallback = false)
          }
          rebuildTabContent()
        }
      }
    )
    hudCard.addView(hudToggleRow)

    val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(6)) }
    HudDisplayMode.entries.forEach { mode ->
      val selected = currentPrefs.hudMode == mode
      modeRow.addView(
        createGridBtn(mode.label, if (selected) "#087BFF" else "#172030", selected) {
          serviceScope.launch {
            prefs.setHudOverlayConfig(enabled = true, mode = mode)
            currentPrefs = currentPrefs.copy(hudOverlayEnabled = true, hudMode = mode)
            if (miniWindowManager.isLiveHudShowing) {
              miniWindowManager.updateHudStyle(mode, currentPrefs.hudOpacity, currentPrefs.hudAutoHide, isIndo)
            } else {
              miniWindowManager.showLiveHud(
                mode = mode,
                isIndo = isIndo,
                x = currentPrefs.hudPosX,
                y = currentPrefs.hudPosY,
                alpha = currentPrefs.hudOpacity,
                autoHide = currentPrefs.hudAutoHide,
              )
            }
            rebuildTabContent()
          }
        }
      )
    }
    hudCard.addView(modeRow)
    hudCard.addView(
      sectionSubtitle(
        if (isIndo) "Ketuk kapsul HUD untuk ganti Mini/Standard/Full, geser untuk pindah. Game FPS dibatasi OS."
        else "Tap HUD capsule to cycle Mini/Standard/Full, drag to reposition. Game FPS is OS-restricted."
      )
    )
    container.addView(hudCard)

    val boostCard = createSectionBox()
    val boostLabel =
      TextView(this).apply {
        setTextColor("#13D9FF".toColorInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = DrxOverlayFonts.interMedium(this@FloatingSidebarService)
        text = boostStatusText.ifBlank {
          if (isIndo) "Cache internal siap dioptimalkan" else "DRX internal cache ready"
        }
        setPadding(0, 0, 0, dp(6))
      }
    homeBoostStatusView = boostLabel

    val boostBtn =
      createPrimaryActionBtn("⚡ BOOST NOW", "#087BFF") {
        triggerHaptic(24L)
        System.gc()
        val mem = telemetryMonitor.readMemoryInfo()
        val freeMb = mem.availBytes / (1024L * 1024L)
        boostStatusText =
          if (isIndo) "Optimasi selesai • RAM Bebas ${freeMb} MB"
          else "Boost done • ${freeMb} MB Free RAM"
        boostLabel.text = boostStatusText
        serviceScope.launch { prefs.recordBoost(System.currentTimeMillis()) }
      }
    boostCard.addView(boostLabel)
    boostCard.addView(boostBtn)
    container.addView(boostCard)

    // Quick Overlay Profile Switcher (4 modes)
    val qProfCard = createSectionBox()
    qProfCard.addView(sectionTitle(if (isIndo) "DRX Quick Profile" else "DRX Quick Profile"))
    val qProfRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(5)) }
    val qProfRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    QuickOverlayProfile.entries.forEachIndexed { idx, qp ->
      val active = currentPrefs.quickOverlayProfile == qp
      val lbl =
        when (qp) {
          QuickOverlayProfile.BALANCED -> "Balanced"
          QuickOverlayProfile.PERFORMANCE_MONITORING -> "Perf Mon"
          QuickOverlayProfile.BATTERY_SAVER -> "Bat Saver"
          QuickOverlayProfile.MINIMAL_OVERLAY -> "Minimal"
        }
      val targetRow = if (idx < 2) qProfRow1 else qProfRow2
      targetRow.addView(
        createGridBtn(lbl, if (active) "#087BFF" else "#172030", active) {
          serviceScope.launch {
            prefs.setQuickOverlayProfile(qp)
            currentPrefs =
              currentPrefs.copy(
                quickOverlayProfile = qp,
                hudMode = qp.hudMode,
              )
            if (miniWindowManager.isLiveHudShowing) {
              miniWindowManager.updateHudStyle(qp.hudMode, currentPrefs.hudOpacity, currentPrefs.hudAutoHide, isIndo)
            }
            rebuildTabContent()
          }
        }
      )
    }
    qProfCard.addView(qProfRow1)
    qProfCard.addView(qProfRow2)
    qProfCard.addView(
      sectionSubtitle(
        if (isIndo)
          "Mengatur mode kapsul HUD & interval polling (${currentPrefs.quickOverlayProfile.pollingIntervalMs}ms)."
        else
          "Controls HUD capsule density & telemetry polling (${currentPrefs.quickOverlayProfile.pollingIntervalMs}ms)."
      )
    )
    container.addView(qProfCard)

    // Profile Switcher
    val profCard = createSectionBox()
    profCard.addView(sectionTitle(if (isIndo) "Mode Performa" else "Performance Mode"))
    val profRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    listOf(
      GamingProfile.PERFORMANCE to "Perf",
      GamingProfile.BALANCED to "Balanced",
      GamingProfile.BATTERY_SAVER to "Saver",
    ).forEach { (prof, label) ->
      val active = currentPrefs.activeProfile == prof
      profRow.addView(
        createGridBtn(label, if (active) "#087BFF" else "#172030", active) {
          serviceScope.launch {
            prefs.setActiveProfile(prof)
            currentPrefs = currentPrefs.copy(activeProfile = prof)
            rebuildTabContent()
          }
        }
      )
    }
    profCard.addView(profRow)
    container.addView(profCard)

    // Session Timer & Quick Switches
    val timerCard = createSectionBox()
    timerCard.addView(sectionTitle(if (isIndo) "Timer Sesi Bermain" else "Gaming Session Timer"))
    val timerTxt =
      TextView(this).apply {
        setTextColor("#F5F8FF".toColorInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        typeface = DrxOverlayFonts.manropeBold(this@FloatingSidebarService)
        fontFeatureSettings = "tnum"
        setPadding(0, 0, 0, dp(6))
      }
    homeTimerView = timerTxt
    val timerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    timerRow.addView(
      createGridBtn(if (timerRunning) "Pause" else "Start", "#087BFF", timerRunning) {
        toggleSessionTimer()
        rebuildTabContent()
      }
    )
    timerRow.addView(
      createGridBtn("Reset", "#172030") {
        resetSessionTimer()
        updateLiveMetrics()
      }
    )
    timerRow.addView(
      createGridBtn("Mini", "#172030") {
        collapseControlCenter()
        openMiniWindow(MiniWindowType.TIMER, isIndo)
      }
    )
    timerCard.addView(timerTxt)
    timerCard.addView(timerRow)
    container.addView(timerCard)

    // Quick Open App & Stop Overlay
    val footerRow =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(4), 0, 0)
      }
    footerRow.addView(
      createGridBtn(if (isIndo) "Buka DRX" else "Open DRX", "#111722") {
        collapseControlCenter()
        safeStartActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      }
    )
    footerRow.addView(
      createGridBtn(if (isIndo) "Tutup Sidebar" else "Turn Off", "#3A1E24") {
        serviceScope.launch {
          prefs.setFloatingEnabled(false)
          stopSelf()
        }
      }
    )
    container.addView(footerRow)
  }

  private fun buildAppsTab(container: LinearLayout, isIndo: Boolean) {
    val infoCard = createSectionBox()
    infoCard.addView(
      sectionSubtitle(
        if (isIndo)
          "Buka jendela mini DRX di atas game atau luncurkan aplikasi (Freeform jika didukung OS)."
        else
          "Open DRX mini-windows over games or launch apps (Android Freeform if supported)."
      )
    )
    container.addView(infoCard)

    val shortcuts =
      cachedShortcuts.ifEmpty { tweaksManager.resolveFloatingAppShortcuts(currentPrefs.floatingPinnedApps) }

    val pairs = shortcuts.chunked(2)
    pairs.forEach { rowItems ->
      val row =
        LinearLayout(this).apply {
          orientation = LinearLayout.HORIZONTAL
          setPadding(0, 0, 0, dp(6))
        }
      rowItems.forEach { app ->
        val badge =
          when {
            app.key == "calculator" || app.key == "notes" || app.key == "clock" -> "MINI / APP"
            app.key == "drx_tools" -> "DRX"
            app.packageName != null && app.supportsFreeform -> "FREEFORM"
            app.packageName != null -> "APP"
            else -> "N/A"
          }
        val cardBtn =
          LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background =
              GradientDrawable().apply {
                setColor("#111722".toColorInt())
                setStroke(1, (if (app.isBuiltInMiniWindow) "#087BFF" else "#1AFFFFFF").toColorInt())
                cornerRadius = dp(16).toFloat()
              }
            layoutParams =
              LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
              }
            setPadding(dp(10), dp(9), dp(10), dp(9))
            setOnClickListener {
              triggerHaptic(16L)
              handleAppShortcutClick(app, isIndo)
            }
          }
        cardBtn.addView(
          TextView(this).apply {
            text = app.defaultLabel
            setTextColor("#F5F8FF".toColorInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = DrxOverlayFonts.interMedium(this@FloatingSidebarService)
            maxLines = 1
          }
        )
        cardBtn.addView(
          TextView(this).apply {
            text = badge
            setTextColor((if (app.isBuiltInMiniWindow) "#13D9FF" else "#929EAF").toColorInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = DrxOverlayFonts.interSemiBold(this@FloatingSidebarService)
          }
        )
        row.addView(cardBtn)
      }
      if (rowItems.size == 1) {
        row.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
      }
      container.addView(row)
    }

    container.addView(
      createPrimaryActionBtn(
        if (isIndo) "+ Semat / Kelola Aplikasi" else "+ Pin / Manage Apps",
        "#172030",
      ) {
        collapseControlCenter()
        safeStartActivity(
          Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("open_route", "tool_sidebar")
        )
      }
    )
  }

  private fun handleAppShortcutClick(app: FloatingAppShortcut, isIndo: Boolean) {
    when (app.key) {
      "calculator" -> {
        collapseControlCenter()
        openMiniWindow(MiniWindowType.CALCULATOR, isIndo)
      }
      "notes" -> {
        collapseControlCenter()
        openMiniWindow(MiniWindowType.NOTES, isIndo)
      }
      "clock" -> {
        collapseControlCenter()
        openMiniWindow(MiniWindowType.TIMER, isIndo)
      }
      "drx_tools" -> {
        collapseControlCenter()
        safeStartActivity(
          Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
      }
      else -> {
        val pkg = app.packageName ?: return
        collapseControlCenter()
        tweaksManager.launchAppFreeformOrShortcut(pkg, tryFreeform = true)
      }
    }
  }

  private fun buildVoiceTab(container: LinearLayout, isIndo: Boolean) {
    val micGranted =
      ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    val topCard = createSectionBox()
    topCard.addView(sectionTitle("Voice Studio Presets"))
    val statusView =
      TextView(this).apply {
        setTextColor("#13D9FF".toColorInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = DrxOverlayFonts.interMedium(this@FloatingSidebarService)
        fontFeatureSettings = "tnum"
        setPadding(0, 0, 0, dp(6))
        text =
          if (isVoicePreviewRunning) "LIVE PREVIEW ACTIVE • ${activeVoicePreset.name}"
          else "${activeVoicePreset.name} • Pitch ${String.format(Locale.US, "%.2fx", lastVoicePitch)}"
      }
    voiceStatusView = statusView
    topCard.addView(statusView)

    val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    actionRow.addView(
      createGridBtn(
        if (!micGranted) (if (isIndo) "Beri Izin Mic" else "Grant Mic")
        else if (isVoicePreviewRunning) (if (isIndo) "Stop Preview" else "Stop Preview")
        else (if (isIndo) "Voice Preview" else "Voice Preview"),
        if (isVoicePreviewRunning) "#D95F5F" else "#087BFF",
        isVoicePreviewRunning,
      ) {
        if (!micGranted) {
          collapseControlCenter()
          safeStartActivity(
            Intent(this, MainActivity::class.java)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
              .putExtra("open_route", "tool_voice")
          )
        } else {
          if (isVoicePreviewRunning) {
            voiceEngine.stopCapture()
            isVoicePreviewRunning = false
          } else {
            isVoicePreviewRunning = true
            voiceEngine.startCapture(
              liveMonitor = true,
              onAmplitudeFrame = { _, _ -> },
              onError = {
                isVoicePreviewRunning = false
                serviceScope.launch { rebuildTabContent() }
              },
              onClipReady = {},
            )
          }
          rebuildTabContent()
        }
      }
    )
    actionRow.addView(
      createGridBtn("Studio EQ", "#172030") {
        collapseControlCenter()
        safeStartActivity(
          Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("open_route", "tool_voice")
        )
      }
    )
    topCard.addView(actionRow)
    container.addView(topCard)

    // 10 Voice Presets in 2-column grid
    val presetCard = createSectionBox()
    VoicePreset.entries.chunked(2).forEach { pair ->
      val r =
        LinearLayout(this).apply {
          orientation = LinearLayout.HORIZONTAL
          setPadding(0, 0, 0, dp(5))
        }
      pair.forEach { preset ->
        val selected = activeVoicePreset == preset
        val label = preset.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
        r.addView(
          createGridBtn(label, if (selected) "#087BFF" else "#172030", selected) {
            activeVoicePreset = preset
            val params =
              if (preset == VoicePreset.CUSTOM) {
                VoiceEffectParams(
                  preset = VoicePreset.CUSTOM,
                  pitchFactor = currentPrefs.customVoicePitch,
                  eqLowGain = currentPrefs.customVoiceBass,
                  eqHighGain = currentPrefs.customVoiceTreble,
                  echoAmount = currentPrefs.customVoiceEcho,
                  reverbAmount = currentPrefs.customVoiceReverb,
                  robotMix = currentPrefs.customVoiceRobot,
                )
              } else {
                VoiceEffectParams.forPreset(preset)
              }
            lastVoicePitch = params.pitchFactor
            voiceEngine.params = params
            rebuildTabContent()
          }
        )
      }
      if (pair.size == 1) {
        r.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
      }
      presetCard.addView(r)
    }

    // Pitch Steppers + Audio Routing Note
    val pitchRow =
      LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(4), 0, dp(4))
      }
    pitchRow.addView(
      createGridBtn("Pitch -", "#0B0E14") {
        lastVoicePitch = (lastVoicePitch - 0.1f).coerceIn(0.6f, 1.75f)
        voiceEngine.params = voiceEngine.params.copy(pitchFactor = lastVoicePitch)
        voiceStatusView?.text = "${activeVoicePreset.name} • Pitch ${String.format(Locale.US, "%.2fx", lastVoicePitch)}"
      }
    )
    pitchRow.addView(
      createGridBtn("Pitch +", "#0B0E14") {
        lastVoicePitch = (lastVoicePitch + 0.1f).coerceIn(0.6f, 1.75f)
        voiceEngine.params = voiceEngine.params.copy(pitchFactor = lastVoicePitch)
        voiceStatusView?.text = "${activeVoicePreset.name} • Pitch ${String.format(Locale.US, "%.2fx", lastVoicePitch)}"
      }
    )
    presetCard.addView(pitchRow)
    presetCard.addView(
      sectionSubtitle(
        if (isIndo)
          "Preview headset real-time aktif. Injeksi mic global ke semua game dibatasi oleh sistem audio Android tanpa OEM HAL."
        else
          "Real-time headset preview active. Global mic injection into external games is restricted by Android audio policy."
      )
    )
    container.addView(presetCard)
  }

  private fun buildToolsTab(container: LinearLayout, isIndo: Boolean) {
    val feedback =
      TextView(this).apply {
        setTextColor("#13D9FF".toColorInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = DrxOverlayFonts.interMedium(this@FloatingSidebarService)
        setPadding(0, 0, 0, dp(6))
        text = if (isIndo) "Alat cepat siap digunakan" else "Quick controls ready"
      }
    toolsStatusView = feedback
    container.addView(feedback)

    // System & Capture Shortcuts
    val sysCard = createSectionBox()
    sysCard.addView(sectionTitle(if (isIndo) "Kontrol Cepat" else "Quick Actions"))

    val r1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(5)) }
    r1.addView(
      createGridBtn("Screenshot", "#172030") {
        val ok = launchCaptureConsentFromOverlay(ScreenRecordConsentActivity.MODE_SCREENSHOT, countdownSec = 0)
        feedback.text = if (ok) "Capturing DRX Screenshot..." else "Unable to capture screenshot"
        if (ok) collapseControlCenter()
      }
    )
    val isRecording = ScreenRecorderStateHolder.state.value.isRecording
    r1.addView(
      createGridBtn(
        if (isRecording) "Stop Rec" else "Screen Rec",
        if (isRecording) "#D95F5F" else "#172030",
        isRecording,
      ) {
        if (ScreenRecorderStateHolder.state.value.isRecording || ScreenRecorderStateHolder.state.value.countdownRemainingSec > 0) {
          ScreenRecorderService.stopRecording(this)
          feedback.text = if (isIndo) "Rekaman disimpan ke Movies/DRXGameBooster" else "Recording saved to Movies/DRXGameBooster"
          rebuildTabContent()
        } else {
          val ok =
            launchCaptureConsentFromOverlay(
              ScreenRecordConsentActivity.MODE_RECORD,
              countdownSec = currentPrefs.recorderCountdownSec,
            )
          feedback.text =
            if (ok) {
              if (isIndo) "Konfirmasi rekam layar dibuka..." else "Screen capture prompt opened..."
            } else {
              if (isIndo) "Gagal membuka dialog rekam layar" else "Unable to start screen capture"
            }
          collapseControlCenter()
        }
      }
    )
    r1.addView(
      createGridBtn(if (tweaksManager.isTorchEnabled) "Torch ON" else "Flashlight", if (tweaksManager.isTorchEnabled) "#087BFF" else "#172030", tweaksManager.isTorchEnabled) {
        val state = tweaksManager.toggleFlashlight()
        feedback.text =
          when (state) {
            true -> "Flashlight ON"
            false -> "Flashlight OFF"
            null -> "Flashlight unavailable"
          }
        rebuildTabContent()
      }
    )
    sysCard.addView(r1)

    val r2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(5)) }
    val dndOn = tweaksManager.isDndEnabled()
    val rotOn = tweaksManager.isSystemAutoRotationEnabled()
    r2.addView(
      createGridBtn(if (dndOn) "DND ON" else "DND OFF", if (dndOn) "#087BFF" else "#172030", dndOn) {
        if (tweaksManager.isDndPolicyGranted()) {
          tweaksManager.setDoNotDisturb(!dndOn)
          rebuildTabContent()
        } else {
          tweaksManager.openDndPolicySettings()
        }
      }
    )
    r2.addView(
      createGridBtn(if (rotOn) "Auto-Rot" else "Rot Lock", if (!rotOn) "#087BFF" else "#172030", !rotOn) {
        if (tweaksManager.canWriteSystemSettings()) {
          tweaksManager.setSystemAutoRotation(!rotOn)
          rebuildTabContent()
        } else {
          tweaksManager.openWriteSettingsPermission()
        }
      }
    )
    r2.addView(
      createGridBtn("Timeout", "#172030") {
        val next = tweaksManager.cycleScreenTimeoutMs()
        feedback.text = if (next != null) "Screen timeout: ${next / 1000}s" else "Grant Write Settings"
      }
    )
    sysCard.addView(r2)
    container.addView(sysCard)

    // Brightness & Media Volume Steppers
    val avCard = createSectionBox()
    val curBrt = telemetryMonitor.readDisplayInfo().brightnessPercent
    val curVol = tweaksManager.getStreamVolumePercent(AudioManager.STREAM_MUSIC)
    avCard.addView(sectionTitle("${if (isIndo) "Kecerahan" else "Brightness"} ($curBrt%) • Vol ($curVol%)"))
    val avRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(5)) }
    avRow.addView(
      createGridBtn("Brt -", "#0B0E14") {
        tweaksManager.setSystemBrightness((curBrt - 15).coerceAtLeast(5), false, null)
        rebuildTabContent()
      }
    )
    avRow.addView(
      createGridBtn("Brt +", "#0B0E14") {
        tweaksManager.setSystemBrightness((curBrt + 15).coerceAtMost(100), false, null)
        rebuildTabContent()
      }
    )
    avRow.addView(
      createGridBtn("Vol -", "#0B0E14") {
        tweaksManager.setStreamVolumePercent(AudioManager.STREAM_MUSIC, curVol - 10)
        rebuildTabContent()
      }
    )
    avRow.addView(
      createGridBtn("Vol +", "#0B0E14") {
        tweaksManager.setStreamVolumePercent(AudioManager.STREAM_MUSIC, curVol + 10)
        rebuildTabContent()
      }
    )
    avCard.addView(avRow)

    // Media Controls
    val mediaRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(5)) }
    mediaRow.addView(createGridBtn("⏮ Prev", "#172030") { tweaksManager.dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) })
    mediaRow.addView(createGridBtn("⏯ Play", "#087BFF") { tweaksManager.dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) })
    mediaRow.addView(createGridBtn("⏭ Next", "#172030") { tweaksManager.dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) })
    avCard.addView(mediaRow)
    container.addView(avCard)

    // Mini-Windows & Clipboard
    val miniCard = createSectionBox()
    miniCard.addView(sectionTitle(if (isIndo) "Jendela Mini & Clipboard" else "Floating Mini-Windows & Clipboard"))
    val miniRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(5)) }
    miniRow.addView(createGridBtn("Calc", "#087BFF") { collapseControlCenter(); openMiniWindow(MiniWindowType.CALCULATOR, isIndo) })
    miniRow.addView(createGridBtn("Notes", "#087BFF") { collapseControlCenter(); openMiniWindow(MiniWindowType.NOTES, isIndo) })
    miniRow.addView(createGridBtn("Timer", "#172030") { collapseControlCenter(); openMiniWindow(MiniWindowType.TIMER, isIndo) })
    miniRow.addView(createGridBtn("HUD", "#172030") { collapseControlCenter(); openMiniWindow(MiniWindowType.MONITOR, isIndo) })
    miniCard.addView(miniRow)

    val clipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    clipRow.addView(
      createGridBtn(if (isIndo) "Baca Clipboard" else "Read Clipboard", "#0B0E14") {
        val clip = tweaksManager.readClipboardText()
        feedback.text = if (clip.isBlank()) "Clipboard empty" else "Clip: ${clip.take(40)}"
      }
    )
    clipRow.addView(
      createGridBtn(if (isIndo) "Salin Statistik" else "Copy Telemetry", "#0B0E14") {
        val snap = telemetryMonitor.readSnapshot()
        val summary =
          "DRX Telemetry | RAM ${snap.memoryInfo.usedPercent.safeRoundToInt()}% | Bat ${snap.batteryInfo.levelPercent}% (${snap.batteryInfo.temperatureC}°C) | ${snap.displayInfo.refreshRateHz.safeRoundToInt(60)}Hz"
        tweaksManager.copyToClipboard("DRX", summary)
        feedback.text = if (isIndo) "Statistik disalin ke clipboard" else "Telemetry copied to clipboard"
      }
    )
    miniCard.addView(clipRow)
    container.addView(miniCard)
  }

  private fun buildRecordTab(container: LinearLayout, isIndo: Boolean) {
    val rec = ScreenRecorderStateHolder.state.value
    val statusCard = createSectionBox()
    statusCard.addView(sectionTitle(if (isIndo) "Pro Screen Recorder & Capture" else "Pro Screen Recorder & Capture"))

    val recStatusTxt =
      TextView(this).apply {
        setTextColor((if (rec.isRecording) "#F07878" else "#13D9FF").toColorInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        typeface = DrxOverlayFonts.interSemiBold(this@FloatingSidebarService)
        fontFeatureSettings = "tnum"
        setPadding(0, 0, 0, dp(8))
      }
    recordStatusView = recStatusTxt
    statusCard.addView(recStatusTxt)

    val ctrlRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(6)) }
    if (rec.isRecording) {
      ctrlRow.addView(
        createGridBtn(if (rec.isPaused) "Resume" else "Pause", "#087BFF", true) {
          if (ScreenRecorderStateHolder.state.value.isPaused) {
            ScreenRecorderService.resumeRecording(this)
          } else {
            ScreenRecorderService.pauseRecording(this)
          }
          rebuildTabContent()
        }
      )
      ctrlRow.addView(
        createGridBtn("Stop & Save", "#D95F5F", true) {
          ScreenRecorderService.stopRecording(this)
          rebuildTabContent()
        }
      )
    } else if (rec.countdownRemainingSec > 0) {
      ctrlRow.addView(
        createGridBtn(
          if (isIndo) "Batal (${rec.countdownRemainingSec}d)" else "Cancel (${rec.countdownRemainingSec}s)",
          "#D95F5F",
          true,
        ) {
          ScreenRecorderService.stopRecording(this)
          rebuildTabContent()
        }
      )
    } else {
      ctrlRow.addView(
        createGridBtn(if (isIndo) "● Mulai Rekam" else "● Start Record", "#087BFF", true) {
          collapseControlCenter()
          launchCaptureConsentFromOverlay(
            ScreenRecordConsentActivity.MODE_RECORD,
            countdownSec = currentPrefs.recorderCountdownSec,
          )
        }
      )
      ctrlRow.addView(
        createGridBtn("Screenshot", "#172030") {
          collapseControlCenter()
          launchCaptureConsentFromOverlay(
            ScreenRecordConsentActivity.MODE_SCREENSHOT,
            countdownSec = 0,
          )
        }
      )
    }
    statusCard.addView(ctrlRow)

    // Countdown Selector (Off / 3s / 5s)
    val cdRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(2)) }
    listOf(0 to "Delay Off", 3 to "3s Delay", 5 to "5s Delay").forEach { (sec, lbl) ->
      val sel = currentPrefs.recorderCountdownSec == sec
      cdRow.addView(
        createGridBtn(lbl, if (sel) "#087BFF" else "#172030", sel) {
          serviceScope.launch {
            prefs.setRecorderCountdownSec(sec)
            currentPrefs = currentPrefs.copy(recorderCountdownSec = sec)
            rebuildTabContent()
          }
        }
      )
    }
    statusCard.addView(cdRow)
    container.addView(statusCard)

    // Quick Quality & Audio Config (synced with ScreenRecorderStateHolder)
    val cfgCard = createSectionBox()
    cfgCard.addView(sectionTitle(if (isIndo) "Resolusi • FPS • Audio" else "Resolution • FPS • Audio"))

    val resRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(5)) }
    listOf(480 to "480p", 720 to "720p", 1080 to "1080p", 0 to "Orig").forEach { (preset, lbl) ->
      val sel = rec.resolutionPreset == preset
      resRow.addView(
        createGridBtn(lbl, if (sel) "#087BFF" else "#172030", sel) {
          if (!ScreenRecorderStateHolder.state.value.isRecording) {
            ScreenRecorderStateHolder.updateConfig(resolutionPreset = preset)
            rebuildTabContent()
          }
        }
      )
    }
    cfgCard.addView(resRow)

    val fpsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(5)) }
    listOf(24, 30, 60).forEach { fps ->
      val sel = rec.targetFps == fps
      fpsRow.addView(
        createGridBtn("$fps FPS", if (sel) "#087BFF" else "#172030", sel) {
          if (!ScreenRecorderStateHolder.state.value.isRecording) {
            ScreenRecorderStateHolder.updateConfig(targetFps = fps)
            rebuildTabContent()
          }
        }
      )
    }
    cfgCard.addView(fpsRow)

    val audRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(6)) }
    val audioModes = buildList {
      add(ScreenRecordAudioMode.NONE to "Mute")
      add(ScreenRecordAudioMode.MIC_AUDIO to "Mic")
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        add(ScreenRecordAudioMode.SYSTEM_AUDIO to "Sys")
        add(ScreenRecordAudioMode.INTERNAL_AND_MIC to "Mix")
      }
    }
    audioModes.forEach { (mode, lbl) ->
      val sel = rec.audioMode == mode
      audRow.addView(
        createGridBtn(lbl, if (sel) "#087BFF" else "#172030", sel) {
          if (!ScreenRecorderStateHolder.state.value.isRecording) {
            ScreenRecorderStateHolder.updateConfig(audioMode = mode)
            rebuildTabContent()
          }
        }
      )
    }
    cfgCard.addView(audRow)

    val deepRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    deepRow.addView(
      createGridBtn(if (isIndo) "Full Recorder" else "Full Recorder", "#0B0E14") {
        collapseControlCenter()
        safeStartActivity(
          Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("open_route", ToolRoutes.SCREEN_RECORDER)
        )
      }
    )
    deepRow.addView(
      createGridBtn(if (isIndo) "Galeri Media" else "Gallery", "#0B0E14") {
        collapseControlCenter()
        safeStartActivity(
          Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("open_route", ToolRoutes.MEDIA_GALLERY)
        )
      }
    )
    cfgCard.addView(deepRow)
    container.addView(cfgCard)
  }

  private fun buildMonitorTab(container: LinearLayout, isIndo: Boolean) {
    val monCard = createSectionBox()
    monCard.addView(sectionTitle(if (isIndo) "Telemetri Perangkat Langsung" else "Live Hardware Telemetry"))
    val monTxt =
      TextView(this).apply {
        setTextColor("#F5F8FF".toColorInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = DrxOverlayFonts.interRegular(this@FloatingSidebarService)
        fontFeatureSettings = "tnum"
        setLineSpacing(dp(5).toFloat(), 1f)
      }
    monitorDetailsView = monTxt
    monCard.addView(monTxt)
    monCard.addView(
      createPrimaryActionBtn(if (isIndo) "Pin Live HUD Capsule di Layar" else "Pin Live HUD Capsule", "#087BFF") {
        collapseControlCenter()
        serviceScope.launch {
          prefs.setHudOverlayConfig(enabled = true)
          currentPrefs = currentPrefs.copy(hudOverlayEnabled = true)
          miniWindowManager.showLiveHud(
            mode = currentPrefs.hudMode,
            isIndo = isIndo,
            x = currentPrefs.hudPosX,
            y = currentPrefs.hudPosY,
            alpha = currentPrefs.hudOpacity,
            autoHide = currentPrefs.hudAutoHide,
          )
        }
      }
    )
    container.addView(monCard)
  }

  private fun openMiniWindow(type: MiniWindowType, isIndo: Boolean) {
    miniWindowManager.show(
      type = type,
      isIndo = isIndo,
      x = currentPrefs.miniWindowX,
      y = currentPrefs.miniWindowY,
      wDp = currentPrefs.miniWindowW,
      hDp = currentPrefs.miniWindowH,
      alpha = currentPrefs.miniWindowAlpha,
    )
  }

  private fun updateLiveMetrics() {
    val snap = telemetryMonitor.readSnapshot()
    val rec = ScreenRecorderStateHolder.state.value
    val availMb = snap.memoryInfo.availBytes / (1024L * 1024L)
    val totalMb = snap.memoryInfo.totalBytes / (1024L * 1024L)
    val freeSto = TimeAndSizeFormatters.formatBytesAuto(snap.storageInfo.freeBytes)
    val mins = elapsedSeconds / 60
    val secs = elapsedSeconds % 60
    val timerFormatted = String.format(Locale.US, "%02d:%02d%s", mins, secs, if (timerRunning) " •" else "")

    statusPillView?.apply {
      text =
        when {
          rec.isRecording -> "● REC ${ScreenRecorderService.formatDuration(rec.elapsedSeconds)} • ${currentPrefs.activeProfile.name}"
          currentPrefs.gamingModeActive -> "● GAMING ACTIVE • ${currentPrefs.activeProfile.name}"
          else -> "● STANDBY • ${currentPrefs.activeProfile.name}"
        }
      setTextColor((if (rec.isRecording) "#F07878" else if (currentPrefs.gamingModeActive) "#55D69A" else "#13D9FF").toColorInt())
    }
    homeTimerView?.text = timerFormatted

    recordStatusView?.apply {
      val sizeTxt = if (rec.liveFileSizeBytes > 0L) " • ${TimeAndSizeFormatters.formatBytesAuto(rec.liveFileSizeBytes)}" else ""
      text =
        when {
          rec.countdownRemainingSec > 0 -> "COUNTDOWN • Starts in ${rec.countdownRemainingSec}s..."
          rec.isRecording && rec.isPaused -> "PAUSED ${ScreenRecorderService.formatDuration(rec.elapsedSeconds)}$sizeTxt"
          rec.isRecording -> "● RECORDING ${ScreenRecorderService.formatDuration(rec.elapsedSeconds)}$sizeTxt"
          else -> "READY • ${if (rec.resolutionPreset == 0) "Original" else "${rec.resolutionPreset}p"} @ ${rec.targetFps}fps"
        }
      setTextColor((if (rec.isRecording || rec.countdownRemainingSec > 0) "#F07878" else "#13D9FF").toColorInt())
    }

    monitorDetailsView?.text =
      "CPU     : ${snap.cpuInfo.cores} Cores • ${snap.cpuInfo.currentFreqMhz?.let { "$it MHz" } ?: "OS Restricted"}\n" +
        "RAM     : ${availMb} MB Free / ${totalMb} MB (${snap.memoryInfo.usedPercent.safeRoundToInt()}%)\n" +
        "BATTERY : ${snap.batteryInfo.levelPercent}% • ${String.format(Locale.US, "%.1f", snap.batteryInfo.temperatureC.sanitizeFinite(30f))}°C (${snap.batteryInfo.chargeSource})\n" +
        "STORAGE : $freeSto Free\n" +
        "NETWORK : ${snap.networkSnapshot.transportType} • ${snap.networkSnapshot.linkSpeedMbps?.let { "$it Mbps" } ?: snap.networkSnapshot.networkSubType}\n" +
        "DISPLAY : ${snap.displayInfo.refreshRateHz.safeRoundToInt(60)} Hz • DRX UI ${snap.uiFps.safeRoundToInt(60)} FPS\n" +
        "SESSION : $timerFormatted"
  }

  private fun toggleSessionTimer() {
    if (timerRunning) {
      if (timerRunStartElapsedMs > 0L) {
        timerAccumulatedMs += (SystemClock.elapsedRealtime() - timerRunStartElapsedMs).coerceAtLeast(0L)
      }
      timerRunStartElapsedMs = 0L
      timerRunning = false
    } else {
      timerRunStartElapsedMs = SystemClock.elapsedRealtime()
      timerRunning = true
    }
    elapsedSeconds = (timerAccumulatedMs / 1000L).coerceAtLeast(0L)
  }

  private fun resetSessionTimer() {
    timerRunning = false
    timerRunStartElapsedMs = 0L
    timerAccumulatedMs = 0L
    elapsedSeconds = 0L
  }

  private fun launchCaptureConsentFromOverlay(mode: String, countdownSec: Int): Boolean {
    suppressAutoCloseUntilElapsedMs = SystemClock.elapsedRealtime() + 15_000L
    val intent =
      Intent(this, ScreenRecordConsentActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        .putExtra(ScreenRecordConsentActivity.EXTRA_CAPTURE_MODE, mode)
        .putExtra(ScreenRecordConsentActivity.EXTRA_COUNTDOWN_SEC, countdownSec)
    return safeStartActivity(intent)
  }

  @Suppress("ClickableViewAccessibility")
  private fun attachPressScaleFeedback(view: View) {
    view.setOnTouchListener { v, ev ->
      if (!currentPrefs.animationsEnabled) return@setOnTouchListener false
      when (ev.action) {
        MotionEvent.ACTION_DOWN ->
          v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90L).start()
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
          v.animate().scaleX(1f).scaleY(1f).setDuration(140L).setInterpolator(OvershootInterpolator(1.2f)).start()
      }
      false
    }
  }

  private fun triggerHaptic(ms: Long) {
    if (!currentPrefs.floatingHapticsEnabled) return
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
      } else {
        @Suppress("DEPRECATION")
        val vib = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
          @Suppress("DEPRECATION") vib.vibrate(ms)
        }
      }
    } catch (_: Throwable) {
    }
  }

  private fun createSectionBox(): LinearLayout {
    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      background =
        GradientDrawable().apply {
          setColor("#111722".toColorInt())
          setStroke(1, "#1AFFFFFF".toColorInt())
          cornerRadius = dp(20).toFloat()
        }
      layoutParams =
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(8) }
      setPadding(dp(12), dp(12), dp(12), dp(12))
    }
  }

  private fun sectionTitle(text: String): TextView =
    TextView(this).apply {
      this.text = text
      setTextColor("#F5F8FF".toColorInt())
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      typeface = DrxOverlayFonts.manropeSemiBold(this@FloatingSidebarService)
      setPadding(0, 0, 0, dp(6))
    }

  private fun sectionSubtitle(text: String): TextView =
    TextView(this).apply {
      this.text = text
      setTextColor("#929EAF".toColorInt())
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
      typeface = DrxOverlayFonts.interRegular(this@FloatingSidebarService)
      setLineSpacing(dp(3).toFloat(), 1f)
    }

  private fun createSmallIconBtn(label: String, bgHex: String, textHex: String, onClick: () -> Unit): TextView {
    return TextView(this).apply {
      text = label
      gravity = Gravity.CENTER
      setTextColor(textHex.toColorInt())
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      typeface = DrxOverlayFonts.interSemiBold(this@FloatingSidebarService)
      background =
        GradientDrawable().apply {
          setColor(bgHex.toColorInt())
          setStroke(1, "#1AFFFFFF".toColorInt())
          cornerRadius = dp(17).toFloat()
        }
      layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginStart = dp(6) }
      setOnClickListener { onClick() }
    }
  }

  private fun createPrimaryActionBtn(label: String, bgHex: String, onClick: () -> Unit): TextView {
    return TextView(this).apply {
      text = label
      gravity = Gravity.CENTER
      setTextColor("#F5F8FF".toColorInt())
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
      typeface = DrxOverlayFonts.interSemiBold(this@FloatingSidebarService)
      background =
        GradientDrawable().apply {
          setColor(bgHex.toColorInt())
          setStroke(1, "#6613D9FF".toColorInt())
          cornerRadius = dp(50).toFloat()
        }
      setPadding(dp(14), dp(11), dp(14), dp(11))
      attachPressScaleFeedback(this)
      setOnClickListener { onClick() }
    }
  }

  private fun createGridBtn(
    label: String,
    bgHex: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
  ): TextView {
    return TextView(this).apply {
      text = label
      gravity = Gravity.CENTER
      maxLines = 1
      setTextColor("#F5F8FF".toColorInt())
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      typeface =
        if (highlighted) DrxOverlayFonts.interSemiBold(this@FloatingSidebarService)
        else DrxOverlayFonts.interMedium(this@FloatingSidebarService)
      background =
        GradientDrawable().apply {
          setColor(bgHex.toColorInt())
          setStroke(1, (if (highlighted) "#13D9FF" else "#1AFFFFFF").toColorInt())
          cornerRadius = dp(14).toFloat()
        }
      layoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
          marginEnd = dp(5)
        }
      setPadding(dp(6), dp(9), dp(6), dp(9))
      attachPressScaleFeedback(this)
      setOnClickListener {
        triggerHaptic(10L)
        onClick()
      }
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = getSystemService(NotificationManager::class.java)
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          getString(R.string.floating_service_channel_name),
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          description = getString(R.string.floating_service_channel_desc)
          setShowBadge(false)
        }
      nm.createNotificationChannel(channel)
    }
  }

  private fun startForegroundNotification() {
    val openIntent =
      PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    val stopIntent =
      PendingIntent.getService(
        this,
        1,
        Intent(this, FloatingSidebarService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )

    val notification: Notification =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("DRX GAME TURBO PRO")
        .setContentText("Edge Floating Control Center active")
        .setOngoing(true)
        .setContentIntent(openIntent)
        .addAction(0, "Hide", stopIntent)
        .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      ServiceCompat.startForeground(
        this,
        NOTIF_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
      )
    } else {
      startForeground(NOTIF_ID, notification)
    }
  }

  private fun safeAddView(view: View, params: WindowManager.LayoutParams): Boolean {
    return try {
      if (!Settings.canDrawOverlays(this)) return false
      windowManager.addView(view, params)
      true
    } catch (_: Throwable) {
      false
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

  private fun safeRemoveView(view: View) {
    try {
      if (view.isAttachedToWindow) {
        windowManager.removeView(view)
      }
    } catch (_: Throwable) {
    }
  }

  private fun safeStartActivity(intent: Intent): Boolean {
    return try {
      startActivity(intent)
      true
    } catch (_: Throwable) {
      false
    }
  }

  private fun dp(v: Int): Int =
    TypedValue.applyDimension(
      TypedValue.COMPLEX_UNIT_DIP,
      v.toFloat(),
      resources.displayMetrics,
    ).safeRoundToInt(v)

  override fun onDestroy() {
    isRunning = false
    handler.removeCallbacks(ticker)
    handler.removeCallbacks(autoHideRunnable)
    voiceEngine.stopAll()
    telemetryMonitor.stopFpsMonitoring()
    miniWindowManager.dismissAll()
    panelCard?.animate()?.cancel()
    panelBackdrop?.animate()?.cancel()
    panelBackdrop?.let { safeRemoveView(it) }
    panelBackdrop = null
    panelCard = null
    removeEdgeHandle()
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
