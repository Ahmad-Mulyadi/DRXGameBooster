package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import com.example.data.remote.SocialMetricsRepository
import com.example.data.remote.SocialStatsState
import java.io.File
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.db.AnnouncementHistoryEntity
import com.example.data.db.DrxDatabase
import com.example.data.db.DrxRepository
import com.example.data.db.GameEntity
import com.example.data.db.GameProfileAutomationEntity
import com.example.data.db.GameSessionEntity
import com.example.data.db.SessionReportEntity
import com.example.data.db.SessionTimelineEventEntity
import com.example.data.model.AppUsageSummary
import com.example.data.model.BoostReport
import com.example.data.model.CombinedGameStatistics
import com.example.data.model.DevicePerformanceState
import com.example.data.model.DrxThemePreset
import com.example.data.model.DrxUpdateCatalog
import com.example.data.model.FloatingAppShortcut
import com.example.data.model.GameCategory
import com.example.data.model.GameResourceReport
import com.example.data.model.GamingProfile
import com.example.data.model.HudDisplayMode
import com.example.data.model.GlobalGamingStatistics
import com.example.data.model.InstalledAppItem
import com.example.data.model.LanguageMode
import com.example.data.model.LaunchAnimationQuality
import com.example.data.model.MediaItemType
import com.example.data.model.PermissionStateItem
import com.example.data.model.PreGameCheckReport
import com.example.data.model.ProfileConfig
import com.example.data.model.QuickOverlayProfile
import com.example.data.model.SessionComparisonMetric
import com.example.data.model.SessionIntelligenceSummary
import com.example.data.model.SessionRecapSummary
import com.example.data.model.SessionTimelineEventType
import com.example.data.model.SystemGameUsageSummary
import com.example.data.model.SystemTelemetry
import com.example.data.prefs.DrxPreferences
import com.example.data.prefs.DrxUserPrefs
import com.example.system.AnnouncementNotifier
import com.example.system.AnnouncementSchedulePlanner
import com.example.system.AnnouncementScheduler
import com.example.system.DrxMediaStorageManager
import com.example.system.FloatingSidebarService
import com.example.system.GameResourceReportEngine
import com.example.system.GameStatsCollector
import com.example.system.GamingFocusModeController
import com.example.system.GamingFocusUiState
import com.example.system.PreGameCheckEngine
import com.example.system.ScreenRecorderStateHolder
import com.example.system.SessionIntelligenceEngine
import com.example.system.SmartStorageInspectorEngine
import com.example.system.SystemTweaksManager
import com.example.system.TelemetryMonitor
import com.example.system.TimeAndSizeFormatters
import com.example.system.sanitizeFinite
import com.example.ui.screens.GameLaunchState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ActiveGameSession(
  val packageName: String,
  val gameName: String,
  val startedAtMs: Long,
  val profileUsed: GamingProfile,
  val batteryStartPercent: Int,
  val peakTempC: Float,
  val sessionId: String = "${packageName}_${startedAtMs}",
)

data class TelemetryHistory(
  val ramSamples: List<Float> = emptyList(),
  val cpuSamples: List<Float> = emptyList(),
  val batterySamples: List<Float> = emptyList(),
  val tempSamples: List<Float> = emptyList(),
  val networkQualitySamples: List<Float> = emptyList(),
)

class DrxMainViewModel(application: Application) : AndroidViewModel(application) {

  private val appContext = application.applicationContext
  private val database = DrxDatabase.getInstance(appContext)
  val repository = DrxRepository(database)
  val prefs = DrxPreferences(appContext)
  val telemetryMonitor = TelemetryMonitor(appContext)
  val tweaksManager = SystemTweaksManager(appContext)
  val gameStatsCollector = GameStatsCollector(appContext, tweaksManager)
  val socialRepository = SocialMetricsRepository(appContext)
  val announcementScheduler = AnnouncementScheduler(appContext)

  val userPrefs: StateFlow<DrxUserPrefs> =
    prefs.prefsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, DrxUserPrefs())

  val games: StateFlow<List<GameEntity>> =
    repository.allGames.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val allSessions: StateFlow<List<GameSessionEntity>> =
    repository.allSessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val recentSessions: StateFlow<List<GameSessionEntity>> =
    repository.recentSessions.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList(),
    )

  val announcementHistory: StateFlow<List<AnnouncementHistoryEntity>> =
    repository.announcementHistory.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList(),
    )

  private val _telemetry = MutableStateFlow(telemetryMonitor.readSnapshot())
  val telemetry: StateFlow<SystemTelemetry> = _telemetry.asStateFlow()

  private val _telemetryHistory = MutableStateFlow(TelemetryHistory())
  val telemetryHistory: StateFlow<TelemetryHistory> = _telemetryHistory.asStateFlow()

  private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
  val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

  private val _floatingShortcuts = MutableStateFlow<List<FloatingAppShortcut>>(emptyList())
  val floatingShortcuts: StateFlow<List<FloatingAppShortcut>> = _floatingShortcuts.asStateFlow()

  private val _isScanningGames = MutableStateFlow(false)
  val isScanningGames: StateFlow<Boolean> = _isScanningGames.asStateFlow()

  private val _isBoosting = MutableStateFlow(false)
  val isBoosting: StateFlow<Boolean> = _isBoosting.asStateFlow()

  private val _lastBoostReport = MutableStateFlow<BoostReport?>(null)
  val lastBoostReport: StateFlow<BoostReport?> = _lastBoostReport.asStateFlow()

  private val _activeSession = MutableStateFlow<ActiveGameSession?>(null)
  val activeSession: StateFlow<ActiveGameSession?> = _activeSession.asStateFlow()

  private val _launchState = MutableStateFlow<GameLaunchState?>(null)
  val launchState: StateFlow<GameLaunchState?> = _launchState.asStateFlow()

  private val _appUsageStats = MutableStateFlow<List<AppUsageSummary>>(emptyList())
  val appUsageStats: StateFlow<List<AppUsageSummary>> = _appUsageStats.asStateFlow()

  private val _permissionStates = MutableStateFlow(tweaksManager.getAllPermissionStates())
  val permissionStates: StateFlow<List<PermissionStateItem>> = _permissionStates.asStateFlow()

  private val _immersiveEnabled = MutableStateFlow(false)
  val immersiveEnabled: StateFlow<Boolean> = _immersiveEnabled.asStateFlow()

  private val _dndEnabled = MutableStateFlow(tweaksManager.isDndEnabled())
  val dndEnabled: StateFlow<Boolean> = _dndEnabled.asStateFlow()

  private val _autoRotateEnabled = MutableStateFlow(tweaksManager.isSystemAutoRotationEnabled())
  val autoRotateEnabled: StateFlow<Boolean> = _autoRotateEnabled.asStateFlow()

  private val _socialStats = MutableStateFlow<SocialStatsState>(SocialStatsState.Loading())
  val socialStats: StateFlow<SocialStatsState> = _socialStats.asStateFlow()

  private val _isDeviceMonitoringActive = MutableStateFlow(true)
  val isDeviceMonitoringActive: StateFlow<Boolean> = _isDeviceMonitoringActive.asStateFlow()

  private val _systemGameUsageMap = MutableStateFlow<Map<String, SystemGameUsageSummary>>(emptyMap())
  val systemGameUsageMap: StateFlow<Map<String, SystemGameUsageSummary>> = _systemGameUsageMap.asStateFlow()

  val sessionTimelineEvents: StateFlow<List<SessionTimelineEventEntity>> =
    repository.sessionTimelineEvents.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList(),
    )

  val gameProfileAutomations: StateFlow<List<GameProfileAutomationEntity>> =
    repository.gameProfileAutomations.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList(),
    )

  val sessionReports: StateFlow<List<SessionReportEntity>> =
    repository.sessionReports.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList(),
    )

  private val _selectedGameStats = MutableStateFlow<CombinedGameStatistics?>(null)
  val selectedGameStats: StateFlow<CombinedGameStatistics?> = _selectedGameStats.asStateFlow()

  private val _preGameCheckReport = MutableStateFlow<PreGameCheckReport?>(null)
  val preGameCheckReport: StateFlow<PreGameCheckReport?> = _preGameCheckReport.asStateFlow()

  private val _pendingPreGameLaunch = MutableStateFlow<GameEntity?>(null)
  val pendingPreGameLaunch: StateFlow<GameEntity?> = _pendingPreGameLaunch.asStateFlow()

  private val _focusModeState = MutableStateFlow(GamingFocusUiState())
  val focusModeState: StateFlow<GamingFocusUiState> = _focusModeState.asStateFlow()

  private val _lastAutoAppliedProfileBanner = MutableStateFlow<String?>(null)
  val lastAutoAppliedProfileBanner: StateFlow<String?> = _lastAutoAppliedProfileBanner.asStateFlow()

  private val _boostAnimationVisible = MutableStateFlow(false)
  val boostAnimationVisible: StateFlow<Boolean> = _boostAnimationVisible.asStateFlow()

  private val _boostStageIndex = MutableStateFlow(0)
  val boostStageIndex: StateFlow<Int> = _boostStageIndex.asStateFlow()

  private val _boostReadyToPlay = MutableStateFlow(false)
  val boostReadyToPlay: StateFlow<Boolean> = _boostReadyToPlay.asStateFlow()

  private val _latestSessionRecap = MutableStateFlow<SessionRecapSummary?>(null)
  val latestSessionRecap: StateFlow<SessionRecapSummary?> = _latestSessionRecap.asStateFlow()

  private var focusTimerJob: Job? = null
  @Volatile private var lastTimelineTempC: Float = 0f
  @Volatile private var lastTimelineBatteryPct: Int = -1
  @Volatile private var lastTimelineNetStatus: String = ""
  @Volatile private var lastTimelineThermalWarnAtMs: Long = 0L
  @Volatile private var lastTimelineMemoryWarnAtMs: Long = 0L
  @Volatile private var lastTimelineFpsDropAtMs: Long = 0L
  @Volatile private var wasRecordingActive: Boolean = false
  @Volatile private var lastAutoAppliedForegroundPkg: String? = null

  val devicePerformanceState: StateFlow<DevicePerformanceState> =
    combine(_isDeviceMonitoringActive, _telemetry, _activeSession) { monActive, snap, session ->
      when {
        !monActive -> DevicePerformanceState.DEVICE_IDLE
        snap.thermalStatusCode >= 2 || snap.batteryInfo.temperatureC >= 41.5f ->
          DevicePerformanceState.THERMAL_WARNING
        session != null -> DevicePerformanceState.MONITORING
        snap.cpuInfo.isUsageRestricted -> DevicePerformanceState.LIMITED_MONITORING
        else -> DevicePerformanceState.READY
      }
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = DevicePerformanceState.READY,
    )

  val globalStats: StateFlow<GlobalGamingStatistics> =
    combine(
      games,
      allSessions,
      _systemGameUsageMap,
      _installedApps,
      userPrefs,
    ) { gameList, sessionList, usageMap, apps, pref ->
      val installedPkgs = apps.map { it.packageName }.toSet()
      GameStatsCollector.computeGlobalStats(
        games = gameList,
        allSessions = sessionList,
        systemUsageByPkg = usageMap,
        hasUsagePermission = tweaksManager.hasUsageStatsPermission(),
        installedPackages = installedPkgs,
        nowMs = System.currentTimeMillis(),
        isIndo = pref.language == LanguageMode.INDONESIAN,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue =
        GlobalGamingStatistics(
          totalTrackedGames = 0,
          installedTrackedGames = 0,
          totalPlaytimeMs = 0L,
          mostPlayedByTimeName = null,
          mostPlayedByTimePkg = null,
          mostPlayedByTimeMs = 0L,
          mostOpenedGameName = null,
          mostOpenedPkg = null,
          mostOpenedCount = 0,
          lastPlayedGameName = null,
          lastPlayedPkg = null,
          lastPlayedAtMs = null,
          totalSessionsCount = 0,
          averageSessionDurationMs = 0L,
          hasUsagePermission = false,
          usesEstimatedSystemUsage = false,
          weeklyChart = emptyList(),
          monthlyChart = emptyList(),
          rankedGames = emptyList(),
        ),
    )

  val notificationCountsByPackage: StateFlow<Map<String, Int>> =
    repository.gameNotifications
      .map { list -> list.groupingBy { it.packageName }.eachCount() }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

  val sessionIntelligence: StateFlow<SessionIntelligenceSummary> =
    combine(games, allSessions, _systemGameUsageMap, userPrefs) { gameList, sessionList, usageMap, pref ->
      SessionIntelligenceEngine.computeSummary(
        games = gameList,
        sessions = sessionList,
        systemUsageByPkg = usageMap,
        isIndo = pref.language == LanguageMode.INDONESIAN,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = SessionIntelligenceSummary(),
    )

  init {
    telemetryMonitor.startFpsMonitoring()
    startTelemetryLoop()
    observeRecorderTimelineEvents()
    scanInstalledGames(autoSeed = true)
    refreshUsageStats()
    refreshFloatingShortcuts()
    syncAnnouncementSchedule(forceReschedule = false)
  }

  private fun observeRecorderTimelineEvents() {
    viewModelScope.launch(Dispatchers.IO) {
      ScreenRecorderStateHolder.state.collect { rec ->
        val isRec = rec.isRecording
        if (isRec && !wasRecordingActive) {
          val active = _activeSession.value
          if (active != null) {
            val snap = _telemetry.value
            val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
            val safeTemp = snap.batteryInfo.temperatureC.sanitizeFinite(30f)
            val ramFreeMb = (snap.memoryInfo.availBytes / (1024L * 1024L)).coerceAtLeast(0L)
            val netSummary =
              if (snap.networkSnapshot.isConnected) "${snap.networkSnapshot.transportType} (${snap.networkSnapshot.networkSubType})"
              else "Offline"
            runCatching {
              repository.insertSessionTimelineEvent(
                SessionTimelineEventEntity(
                  sessionId = active.sessionId,
                  gamePackageName = active.packageName,
                  gameName = active.gameName,
                  eventType = SessionTimelineEventType.RECORDING_STARTED.code,
                  title = if (isIndo) "Rekaman Layar Dimulai" else "Screen Recording Started",
                  detail = "${rec.resolutionPreset}p • ${rec.targetFps} FPS",
                  timestampMs = System.currentTimeMillis(),
                  batteryPercent = snap.batteryInfo.levelPercent,
                  batteryTempC = safeTemp,
                  ramFreeMb = ramFreeMb,
                  ramUsedPercent = snap.memoryInfo.usedPercent.sanitizeFinite(0f),
                  networkStatus = netSummary,
                  thermalStatusCode = snap.thermalStatusCode,
                )
              )
            }
          }
        }
        wasRecordingActive = isRec
      }
    }
  }

  private fun startTelemetryLoop() {
    viewModelScope.launch(Dispatchers.IO) {
      while (isActive) {
        if (!_isDeviceMonitoringActive.value) {
          delay(500L)
          continue
        }
        try {
          val snap = telemetryMonitor.readSnapshot()
          _telemetry.value = snap

          val prevHist = _telemetryHistory.value
          val cpuVal =
            (snap.cpuInfo.usagePercent
              ?: snap.cpuInfo.currentFreqMhz?.let { cur ->
                val max = (snap.cpuInfo.maxFreqMhz ?: 2400).coerceAtLeast(1000)
                (cur.toFloat() / max.toFloat()) * 100f
              }
              ?: 0f).sanitizeFinite(0f)
          _telemetryHistory.value =
            TelemetryHistory(
              ramSamples = (prevHist.ramSamples + snap.memoryInfo.usedPercent.sanitizeFinite(0f)).takeLast(20),
              cpuSamples = (prevHist.cpuSamples + cpuVal).takeLast(20),
              batterySamples =
                (prevHist.batterySamples + snap.batteryInfo.levelPercent.toFloat()).takeLast(20),
              tempSamples = (prevHist.tempSamples + snap.batteryInfo.temperatureC.sanitizeFinite(30f)).takeLast(20),
              networkQualitySamples =
                (prevHist.networkQualitySamples + (snap.networkSnapshot.qualityLevel * 25f))
                  .takeLast(20),
            )

          val currentSession = _activeSession.value
          val safeTemp = snap.batteryInfo.temperatureC.sanitizeFinite(30f)
          val ramFreeMb = (snap.memoryInfo.availBytes / (1024L * 1024L)).coerceAtLeast(0L)
          val netSummary =
            if (snap.networkSnapshot.isConnected) "${snap.networkSnapshot.transportType} (${snap.networkSnapshot.networkSubType})"
            else "Offline"
          val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
          val nowMs = System.currentTimeMillis()

          if (currentSession != null) {
            if (safeTemp > currentSession.peakTempC) {
              _activeSession.value = currentSession.copy(peakTempC = safeTemp)
            }
            if (lastTimelineTempC > 0f && safeTemp - lastTimelineTempC >= 1.5f) {
              lastTimelineTempC = safeTemp
              repository.insertSessionTimelineEvent(
                SessionTimelineEventEntity(
                  sessionId = currentSession.sessionId,
                  gamePackageName = currentSession.packageName,
                  gameName = currentSession.gameName,
                  eventType = SessionTimelineEventType.TEMP_INCREASED.code,
                  title = if (isIndo) "Suhu Meningkat" else "Temperature Increased",
                  detail = String.format(Locale.US, "%.1f°C", safeTemp),
                  timestampMs = nowMs,
                  batteryPercent = snap.batteryInfo.levelPercent,
                  batteryTempC = safeTemp,
                  ramFreeMb = ramFreeMb,
                  ramUsedPercent = snap.memoryInfo.usedPercent.sanitizeFinite(0f),
                  networkStatus = netSummary,
                  thermalStatusCode = snap.thermalStatusCode,
                )
              )
            }
            if ((safeTemp >= 41.0f || snap.thermalStatusCode >= 2) && (nowMs - lastTimelineThermalWarnAtMs >= 30_000L)) {
              lastTimelineThermalWarnAtMs = nowMs
              repository.insertSessionTimelineEvent(
                SessionTimelineEventEntity(
                  sessionId = currentSession.sessionId,
                  gamePackageName = currentSession.packageName,
                  gameName = currentSession.gameName,
                  eventType = SessionTimelineEventType.THERMAL_WARNING.code,
                  title = if (isIndo) "Peringatan Thermal" else "Thermal Warning",
                  detail = String.format(Locale.US, "%.1f°C (Code %d)", safeTemp, snap.thermalStatusCode),
                  timestampMs = nowMs,
                  batteryPercent = snap.batteryInfo.levelPercent,
                  batteryTempC = safeTemp,
                  ramFreeMb = ramFreeMb,
                  ramUsedPercent = snap.memoryInfo.usedPercent.sanitizeFinite(0f),
                  networkStatus = netSummary,
                  thermalStatusCode = snap.thermalStatusCode,
                )
              )
            }
            if (lastTimelineBatteryPct > 0 && lastTimelineBatteryPct - snap.batteryInfo.levelPercent >= 1) {
              val dropped = lastTimelineBatteryPct - snap.batteryInfo.levelPercent
              lastTimelineBatteryPct = snap.batteryInfo.levelPercent
              repository.insertSessionTimelineEvent(
                SessionTimelineEventEntity(
                  sessionId = currentSession.sessionId,
                  gamePackageName = currentSession.packageName,
                  gameName = currentSession.gameName,
                  eventType = SessionTimelineEventType.BATTERY_DROP.code,
                  title = if (isIndo) "Penurunan Baterai (-$dropped%)" else "Battery Drop (-$dropped%)",
                  detail = "${snap.batteryInfo.levelPercent}%",
                  timestampMs = nowMs,
                  batteryPercent = snap.batteryInfo.levelPercent,
                  batteryTempC = safeTemp,
                  ramFreeMb = ramFreeMb,
                  ramUsedPercent = snap.memoryInfo.usedPercent.sanitizeFinite(0f),
                  networkStatus = netSummary,
                  thermalStatusCode = snap.thermalStatusCode,
                )
              )
            }
            if (lastTimelineNetStatus.isNotEmpty() && lastTimelineNetStatus != netSummary) {
              lastTimelineNetStatus = netSummary
              repository.insertSessionTimelineEvent(
                SessionTimelineEventEntity(
                  sessionId = currentSession.sessionId,
                  gamePackageName = currentSession.packageName,
                  gameName = currentSession.gameName,
                  eventType = SessionTimelineEventType.NETWORK_CHANGED.code,
                  title = if (isIndo) "Status Jaringan Berubah" else "Network Changed",
                  detail = netSummary,
                  timestampMs = nowMs,
                  batteryPercent = snap.batteryInfo.levelPercent,
                  batteryTempC = safeTemp,
                  ramFreeMb = ramFreeMb,
                  ramUsedPercent = snap.memoryInfo.usedPercent.sanitizeFinite(0f),
                  networkStatus = netSummary,
                  thermalStatusCode = snap.thermalStatusCode,
                )
              )
            }
            if ((snap.memoryInfo.lowMemory || snap.memoryInfo.usedPercent >= 88f) && (nowMs - lastTimelineMemoryWarnAtMs >= 30_000L)) {
              lastTimelineMemoryWarnAtMs = nowMs
              repository.insertSessionTimelineEvent(
                SessionTimelineEventEntity(
                  sessionId = currentSession.sessionId,
                  gamePackageName = currentSession.packageName,
                  gameName = currentSession.gameName,
                  eventType = SessionTimelineEventType.MEMORY_PRESSURE.code,
                  title = if (isIndo) "Tekanan Memori RAM" else "RAM Memory Pressure",
                  detail = "${snap.memoryInfo.usedPercent.toInt()}% • ${ramFreeMb} MB free",
                  timestampMs = nowMs,
                  batteryPercent = snap.batteryInfo.levelPercent,
                  batteryTempC = safeTemp,
                  ramFreeMb = ramFreeMb,
                  ramUsedPercent = snap.memoryInfo.usedPercent.sanitizeFinite(0f),
                  networkStatus = netSummary,
                  thermalStatusCode = snap.thermalStatusCode,
                )
              )
            }
            if (snap.uiFps in 1f..45f && (nowMs - lastTimelineFpsDropAtMs >= 20_000L)) {
              lastTimelineFpsDropAtMs = nowMs
              repository.insertSessionTimelineEvent(
                SessionTimelineEventEntity(
                  sessionId = currentSession.sessionId,
                  gamePackageName = currentSession.packageName,
                  gameName = currentSession.gameName,
                  eventType = SessionTimelineEventType.FPS_DROP.code,
                  title = if (isIndo) "Penurunan UI FPS Terdeteksi" else "UI FPS Dip Detected",
                  detail = "${snap.uiFps.toInt()} FPS / ${snap.displayInfo.refreshRateHz.toInt()} Hz",
                  timestampMs = nowMs,
                  batteryPercent = snap.batteryInfo.levelPercent,
                  batteryTempC = safeTemp,
                  ramFreeMb = ramFreeMb,
                  ramUsedPercent = snap.memoryInfo.usedPercent.sanitizeFinite(0f),
                  networkStatus = netSummary,
                  thermalStatusCode = snap.thermalStatusCode,
                )
              )
            }
          }

          // Foreground game automation trigger when Usage Access is granted
          if (tweaksManager.hasUsageStatsPermission()) {
            val fgPkg = tweaksManager.detectRecentForegroundPackage(lookbackMs = 5_000L)
            if (!fgPkg.isNullOrBlank() && fgPkg != appContext.packageName && fgPkg != lastAutoAppliedForegroundPkg) {
              val autoProfile = repository.getGameProfileAutomation(fgPkg)
              if (autoProfile != null && autoProfile.enabled && autoProfile.autoApplyOnForeground) {
                lastAutoAppliedForegroundPkg = fgPkg
                applyGameProfileAutomation(autoProfile, activity = null)
              }
            }
          }
        } catch (_: Throwable) {
        }
        val pollMs = userPrefs.value.quickOverlayProfile.pollingIntervalMs.coerceIn(750L, 2500L)
        delay(pollMs)
      }
    }
  }

  fun refreshSystemStates() {
    try {
      _permissionStates.value = tweaksManager.getAllPermissionStates()
      _dndEnabled.value = tweaksManager.isDndEnabled()
      _autoRotateEnabled.value = tweaksManager.isSystemAutoRotationEnabled()
      _telemetry.value = telemetryMonitor.readSnapshot()
    } catch (_: Throwable) {
    }
    refreshUsageStats()
  }

  fun refreshUsageStats() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        _appUsageStats.value = tweaksManager.queryRecentAppUsage()
        val trackedPkgs =
          (games.value.map { it.packageName } + allSessions.value.map { it.gamePackageName }).toSet()
        if (trackedPkgs.isNotEmpty()) {
          _systemGameUsageMap.value = gameStatsCollector.queryBatchSystemUsage(trackedPkgs)
        }
      } catch (_: Throwable) {
      }
    }
  }

  fun loadGameDetailStatistics(packageName: String) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val drxGame = games.value.find { it.packageName == packageName }
        val fallback = drxGame?.appName ?: packageName.substringAfterLast('.')
        val detail = gameStatsCollector.collectGameDetailInfo(packageName, fallback)
        val sysUsage = gameStatsCollector.querySystemGameUsage(packageName)
        val sessions = allSessions.value.filter { it.gamePackageName == packageName }
        val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
        val combined =
          GameStatsCollector.computeCombinedGameStats(
            gameInfo = detail,
            drxGame = drxGame,
            gameSessions = sessions,
            systemUsage = sysUsage,
            nowMs = System.currentTimeMillis(),
            isIndo = isIndo,
          )
        _selectedGameStats.value = combined
        _systemGameUsageMap.update { it + (packageName to sysUsage) }
      } catch (_: Throwable) {
      }
    }
  }

  fun setDeviceMonitoringActive(active: Boolean) {
    _isDeviceMonitoringActive.value = active
    if (active) {
      telemetryMonitor.startFpsMonitoring()
      _telemetry.value = telemetryMonitor.readSnapshot()
    } else {
      telemetryMonitor.stopFpsMonitoring()
    }
  }

  fun scanInstalledGames(autoSeed: Boolean = false) {
    viewModelScope.launch(Dispatchers.IO) {
      _isScanningGames.value = true
      try {
        val apps = tweaksManager.scanInstalledLauncherApps()
        _installedApps.value = apps
        if (autoSeed || games.value.isEmpty()) {
          val detectedGames =
            apps
              .filter { it.isLikelyGame }
              .map {
                GameEntity(
                  packageName = it.packageName,
                  appName = it.appName,
                  category = it.suggestedCategory.id,
                )
              }
          if (detectedGames.isNotEmpty()) {
            repository.seedGamesIfAbsent(detectedGames)
          }
        }
        val pkgs = (games.value.map { it.packageName } + apps.filter { it.isLikelyGame }.map { it.packageName }).toSet()
        if (pkgs.isNotEmpty()) {
          _systemGameUsageMap.value = gameStatsCollector.queryBatchSystemUsage(pkgs)
        }
      } catch (_: Throwable) {
      } finally {
        _isScanningGames.value = false
      }
    }
  }

  fun loadAppIcon(packageName: String): ImageBitmap? = tweaksManager.loadAppIcon(packageName)

  fun addGameManually(item: InstalledAppItem, category: GameCategory) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.upsertGame(
        GameEntity(
          packageName = item.packageName,
          appName = item.appName,
          category = category.id,
        )
      )
    }
  }

  fun removeGame(packageName: String) {
    viewModelScope.launch(Dispatchers.IO) { repository.deleteGame(packageName) }
  }

  fun toggleFavorite(packageName: String) {
    viewModelScope.launch(Dispatchers.IO) { repository.toggleFavorite(packageName) }
  }

  fun updateGameProfile(packageName: String, profile: GamingProfile) {
    viewModelScope.launch(Dispatchers.IO) { repository.updateGameProfile(packageName, profile) }
  }

  fun updateGameCategory(packageName: String, category: GameCategory) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.updateGameCategory(packageName, category.id)
    }
  }

  /**
   * Starts the Game Launch sequence:
   * User taps PLAY -> Cinematic DRX animation + real Android session prep -> Launches target game.
   */
  fun startGameLaunch(game: GameEntity, activity: Activity?) {
    val currentPrefs = userPrefs.value
    if (!currentPrefs.launchAnimationEnabled) {
      viewModelScope.launch {
        prepareGameSession(game, activity)
        executeActualGameLaunch(game)
      }
      return
    }

    _launchState.value = GameLaunchState.Launching(game = game, prepProgress = 0.1f, prepStageIndex = 0)
    viewModelScope.launch {
      _launchState.value = GameLaunchState.Launching(game, prepProgress = 0.35f, prepStageIndex = 1)
      prepareGameSession(game, activity)
      _launchState.value = GameLaunchState.Launching(game, prepProgress = 0.85f, prepStageIndex = 2)
      delay(150L)
      val cur = _launchState.value
      if (cur is GameLaunchState.Launching && cur.game.packageName == game.packageName) {
        _launchState.value = cur.copy(prepProgress = 1.0f, prepStageIndex = 3)
      }
    }
  }

  private suspend fun prepareGameSession(game: GameEntity, activity: Activity?) {
    val currentPrefs = userPrefs.value
    val automation = repository.getGameProfileAutomation(game.packageName)
    val autoProfile =
      if (automation != null && automation.enabled) {
        GamingProfile.entries.find { it.id == automation.gamingProfileId }
      } else null
    val gameProfile =
      autoProfile ?: GamingProfile.entries.find { it.id == game.profileName } ?: currentPrefs.activeProfile
    val config = currentPrefs.resolveProfileConfig(gameProfile)
    val isIndo = currentPrefs.language == LanguageMode.INDONESIAN

    tweaksManager.performSmartBoost(telemetryMonitor, config, activity, isIndo)
    prefs.recordBoost(System.currentTimeMillis())

    if (automation != null && automation.enabled) {
      applyGameProfileAutomation(automation, activity)
    } else if (config.autoFloatingSidebar && tweaksManager.canDrawOverlays()) {
      setFloatingSidebar(true, targetGamePackage = game.packageName)
    }

    val snap = _telemetry.value
    val nowMs = System.currentTimeMillis()
    val sessionId = "${game.packageName}_${nowMs}"
    val safeTemp = snap.batteryInfo.temperatureC.sanitizeFinite(30f)
    val ramFreeMb = (snap.memoryInfo.availBytes / (1024L * 1024L)).coerceAtLeast(0L)
    val netSummary =
      if (snap.networkSnapshot.isConnected) "${snap.networkSnapshot.transportType} (${snap.networkSnapshot.networkSubType})"
      else "Offline"

    lastTimelineTempC = safeTemp
    lastTimelineBatteryPct = snap.batteryInfo.levelPercent
    lastTimelineNetStatus = netSummary
    lastTimelineThermalWarnAtMs = 0L
    lastTimelineMemoryWarnAtMs = 0L
    lastTimelineFpsDropAtMs = 0L

    _activeSession.value =
      ActiveGameSession(
        packageName = game.packageName,
        gameName = game.appName,
        startedAtMs = nowMs,
        profileUsed = gameProfile,
        batteryStartPercent = snap.batteryInfo.levelPercent,
        peakTempC = safeTemp,
        sessionId = sessionId,
      )

    withContext(Dispatchers.IO) {
      runCatching {
        repository.insertSessionTimelineEvent(
          SessionTimelineEventEntity(
            sessionId = sessionId,
            gamePackageName = game.packageName,
            gameName = game.appName,
            eventType = SessionTimelineEventType.LAUNCHED.code,
            title = if (isIndo) "Game Diluncurkan" else "Game Launched",
            detail = "${game.appName} • ${gameProfile.name}",
            timestampMs = nowMs,
            batteryPercent = snap.batteryInfo.levelPercent,
            batteryTempC = safeTemp,
            ramFreeMb = ramFreeMb,
            ramUsedPercent = snap.memoryInfo.usedPercent.sanitizeFinite(0f),
            networkStatus = netSummary,
            thermalStatusCode = snap.thermalStatusCode,
          )
        )
        if (_isDeviceMonitoringActive.value) {
          repository.insertSessionTimelineEvent(
            SessionTimelineEventEntity(
              sessionId = sessionId,
              gamePackageName = game.packageName,
              gameName = game.appName,
              eventType = SessionTimelineEventType.MONITORING_STARTED.code,
              title = if (isIndo) "Monitoring Sesi Aktif" else "Session Monitoring Started",
              detail = "${ramFreeMb} MB RAM free • ${String.format(Locale.US, "%.1f°C", safeTemp)}",
              timestampMs = nowMs + 10L,
              batteryPercent = snap.batteryInfo.levelPercent,
              batteryTempC = safeTemp,
              ramFreeMb = ramFreeMb,
              ramUsedPercent = snap.memoryInfo.usedPercent.sanitizeFinite(0f),
              networkStatus = netSummary,
              thermalStatusCode = snap.thermalStatusCode,
            )
          )
        }
      }
    }
  }

  fun completePendingLaunch() {
    val state = _launchState.value as? GameLaunchState.Launching ?: return
    executeActualGameLaunch(state.game)
  }

  private fun executeActualGameLaunch(game: GameEntity) {
    val launched = tweaksManager.launchApp(game.packageName)
    if (launched) {
      _launchState.value = null
    } else {
      val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
      val msg =
        if (isIndo) {
          "Paket '${game.packageName}' tidak dapat dibuka atau sudah dihapus."
        } else {
          "Package '${game.packageName}' could not be launched or was uninstalled."
        }
      _launchState.value = GameLaunchState.Failed(game = game, message = msg)
    }
  }

  fun dismissLaunchError() {
    _launchState.value = null
  }

  fun stopAndSaveActiveSession() {
    val active = _activeSession.value ?: return
    _activeSession.value = null
    val endedAt = System.currentTimeMillis()
    val durationMs = (endedAt - active.startedAtMs).coerceAtLeast(1000L)
    val snap = _telemetry.value
    val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val peakTemp = maxOf(active.peakTempC, snap.batteryInfo.temperatureC.sanitizeFinite(30f))
        val ramFreeMb = (snap.memoryInfo.availBytes / (1024L * 1024L)).coerceAtLeast(0L)
        val avgUiFps = snap.uiFps.takeIf { it > 0f }
        val netSummary =
          if (snap.networkSnapshot.isConnected) "${snap.networkSnapshot.transportType} (${snap.networkSnapshot.networkSubType})"
          else "Offline"
        repository.recordSession(
          GameSessionEntity(
            gamePackageName = active.packageName,
            gameName = active.gameName,
            startedAt = active.startedAtMs,
            endedAt = endedAt,
            durationMs = durationMs,
            profileUsed = active.profileUsed.id,
            batteryStartPercent = active.batteryStartPercent,
            batteryEndPercent = snap.batteryInfo.levelPercent,
            peakTempC = peakTemp,
          )
        )
        repository.insertSessionTimelineEvent(
          SessionTimelineEventEntity(
            sessionId = active.sessionId,
            gamePackageName = active.packageName,
            gameName = active.gameName,
            eventType = SessionTimelineEventType.SESSION_ENDED.code,
            title = if (isIndo) "Sesi Selesai" else "Session Ended",
            detail = "${TimeAndSizeFormatters.formatDurationCompact(durationMs, isIndo)} • Peak ${String.format(Locale.US, "%.1f°C", peakTemp)}",
            timestampMs = endedAt,
            batteryPercent = snap.batteryInfo.levelPercent,
            batteryTempC = snap.batteryInfo.temperatureC.sanitizeFinite(30f),
            ramFreeMb = ramFreeMb,
            ramUsedPercent = snap.memoryInfo.usedPercent.sanitizeFinite(0f),
            networkStatus = netSummary,
            thermalStatusCode = snap.thermalStatusCode,
          )
        )
        val conditions = buildList {
          if (peakTemp >= 41f || snap.thermalStatusCode >= 2) {
            add(if (isIndo) "Suhu puncak meningkat (${String.format(Locale.US, "%.1f°C", peakTemp)})" else "Elevated peak temp (${String.format(Locale.US, "%.1f°C", peakTemp)})")
          }
          if (ramFreeMb < 700L || snap.memoryInfo.lowMemory) {
            add(if (isIndo) "Tekanan RAM ($ramFreeMb MB bebas)" else "Low RAM headroom ($ramFreeMb MB free)")
          }
          if (!snap.networkSnapshot.isConnected) {
            add(if (isIndo) "Jaringan terputus saat selesai" else "Network disconnected")
          }
          if (isEmpty()) {
            add(if (isIndo) "Kondisi perangkat normal selama sesi" else "Device operating within normal range")
          }
        }.joinToString(" • ")

        val recSummary = buildList {
          if (peakTemp >= 40f) add(if (isIndo) "Istirahatkan perangkat sebelum sesi berikutnya" else "Let device cool before next match")
          if (ramFreeMb < 900L) add(if (isIndo) "Bersihkan aplikasi latar belakang" else "Clear unused background apps")
          if (isEmpty()) add(if (isIndo) "Lanjutkan profil ${active.profileUsed.name}" else "Profile ${active.profileUsed.name} performing well")
        }.joinToString(" • ")

        repository.insertSessionReport(
          SessionReportEntity(
            gamePackageName = active.packageName,
            gameName = active.gameName,
            startedAtMs = active.startedAtMs,
            endedAtMs = endedAt,
            durationMs = durationMs,
            avgUiFpsOrNull = avgUiFps,
            batteryStartPercent = active.batteryStartPercent,
            batteryEndPercent = snap.batteryInfo.levelPercent,
            peakTempC = peakTemp,
            ramAvailEndMb = ramFreeMb,
            networkSummary = netSummary,
            detectedConditions = conditions,
            recommendations = recSummary,
          )
        )

        val sessionEvents =
          sessionTimelineEvents.value.filter {
            it.gamePackageName == active.packageName && it.timestampMs in active.startedAtMs..endedAt
          }
        val perfWarnCount =
          sessionEvents.count {
            it.eventType == SessionTimelineEventType.THERMAL_WARNING.code ||
              it.eventType == SessionTimelineEventType.MEMORY_PRESSURE.code ||
              it.eventType == SessionTimelineEventType.FPS_DROP.code
          }
        val netEvtCount =
          sessionEvents.count { it.eventType == SessionTimelineEventType.NETWORK_CHANGED.code }
        val capturedMedia =
          runCatching { DrxMediaStorageManager.loadGalleryItems(appContext) }
            .getOrDefault(emptyList())
            .filter { it.timestampMs >= active.startedAtMs }

        _latestSessionRecap.value =
          SessionRecapSummary(
            sessionId = active.sessionId,
            gameName = active.gameName,
            packageName = active.packageName,
            startedAtMs = active.startedAtMs,
            endedAtMs = endedAt,
            durationMs = durationMs,
            profileUsed = active.profileUsed,
            avgUiFps = avgUiFps,
            performanceWarningsCount = perfWarnCount,
            networkEventsCount = netEvtCount,
            batteryStartPercent = active.batteryStartPercent,
            batteryEndPercent = snap.batteryInfo.levelPercent,
            peakTempC = peakTemp,
            recordingCount = capturedMedia.count { it.type == MediaItemType.VIDEO },
            screenshotCount = capturedMedia.count { it.type == MediaItemType.SCREENSHOT },
          )
      } catch (_: Throwable) {
      }
    }
  }

  fun dismissSessionRecap() {
    _latestSessionRecap.value = null
  }

  fun buildLatestSessionRecapFallback(
    sessions: List<GameSessionEntity>,
    timelineEvents: List<com.example.data.db.SessionTimelineEventEntity>,
    reports: List<SessionReportEntity>,
  ): SessionRecapSummary? {
    _latestSessionRecap.value?.let { return it }
    val latest = sessions.maxByOrNull { it.endedAt } ?: return null
    val matchingReport =
      reports.firstOrNull {
        it.gamePackageName == latest.gamePackageName && it.endedAtMs == latest.endedAt
      }
    val eventsInSession =
      timelineEvents.filter {
        it.gamePackageName == latest.gamePackageName && it.timestampMs in latest.startedAt..latest.endedAt
      }
    val perfWarnCount =
      eventsInSession.count {
        it.eventType == SessionTimelineEventType.THERMAL_WARNING.code ||
          it.eventType == SessionTimelineEventType.MEMORY_PRESSURE.code ||
          it.eventType == SessionTimelineEventType.FPS_DROP.code
      }
    val netEvtCount =
      eventsInSession.count { it.eventType == SessionTimelineEventType.NETWORK_CHANGED.code }
    val profile =
      GamingProfile.entries.find { it.id == latest.profileUsed } ?: GamingProfile.BALANCED
    return SessionRecapSummary(
      sessionId = "${latest.gamePackageName}_${latest.startedAt}",
      gameName = latest.gameName,
      packageName = latest.gamePackageName,
      startedAtMs = latest.startedAt,
      endedAtMs = latest.endedAt,
      durationMs = latest.durationMs,
      profileUsed = profile,
      avgUiFps = matchingReport?.avgUiFpsOrNull,
      performanceWarningsCount = perfWarnCount,
      networkEventsCount = netEvtCount,
      batteryStartPercent = latest.batteryStartPercent,
      batteryEndPercent = latest.batteryEndPercent,
      peakTempC = latest.peakTempC,
      recordingCount = 0,
      screenshotCount = 0,
    )
  }

  fun performSmartBoost(activity: Activity?) {
    if (_isBoosting.value) return
    viewModelScope.launch {
      _isBoosting.value = true
      try {
        val currentPrefs = prefs.prefsFlow.first()
        val config = currentPrefs.resolveProfileConfig()
        val isIndo = currentPrefs.language == LanguageMode.INDONESIAN
        val animateBoost = currentPrefs.animationsEnabled

        if (animateBoost) {
          _boostStageIndex.value = 0
          _boostReadyToPlay.value = false
          _boostAnimationVisible.value = true
          withContext(Dispatchers.IO) { SmartStorageInspectorEngine.clearDrxCache(appContext) }
          delay(360L)
          _boostStageIndex.value = 1
        } else {
          withContext(Dispatchers.IO) { SmartStorageInspectorEngine.clearDrxCache(appContext) }
        }

        val report = tweaksManager.performSmartBoost(telemetryMonitor, config, activity, isIndo)
        if (animateBoost) {
          delay(340L)
          _boostStageIndex.value = 2
        }

        _lastBoostReport.value = report
        prefs.recordBoost(report.timestampMs)
        _dndEnabled.value = tweaksManager.isDndEnabled()
        _immersiveEnabled.value = config.immersiveMode
        _telemetry.value = telemetryMonitor.readSnapshot()
        if (currentPrefs.boostNotificationsEnabled) {
          postBoostNotification(report, isIndo)
        }

        if (animateBoost) {
          delay(280L)
          _boostReadyToPlay.value = true
          delay(620L)
          _boostAnimationVisible.value = false
        }
      } catch (_: Throwable) {
        _boostAnimationVisible.value = false
      } finally {
        _isBoosting.value = false
      }
    }
  }

  fun dismissBoostAnimation() {
    _boostAnimationVisible.value = false
  }

  fun dismissBoostReport() {
    _lastBoostReport.value = null
  }

  fun setBrightness(percent: Int, auto: Boolean, activity: Activity?) {
    tweaksManager.setSystemBrightness(percent, auto, activity)
    _telemetry.value = telemetryMonitor.readSnapshot()
  }

  fun setAutoRotation(enabled: Boolean) {
    if (tweaksManager.setSystemAutoRotation(enabled)) {
      _autoRotateEnabled.value = enabled
    }
  }

  fun setImmersiveMode(activity: Activity?, enabled: Boolean) {
    if (activity != null) {
      tweaksManager.setImmersiveMode(activity, enabled)
      _immersiveEnabled.value = enabled
    }
  }

  fun toggleDoNotDisturb(enable: Boolean) {
    if (tweaksManager.setDoNotDisturb(enable)) {
      _dndEnabled.value = enable
    }
  }

  fun refreshFloatingShortcuts() {
    viewModelScope.launch(Dispatchers.IO) {
      val currentPrefs = prefs.prefsFlow.first()
      _floatingShortcuts.value = tweaksManager.resolveFloatingAppShortcuts(currentPrefs.floatingPinnedApps)
    }
  }

  fun setFloatingSidebar(enable: Boolean, targetGamePackage: String? = null) {
    viewModelScope.launch {
      if (enable && !tweaksManager.canDrawOverlays()) {
        prefs.setFloatingEnabled(false)
        return@launch
      }
      prefs.setFloatingEnabled(enable)
      val intent =
        Intent(appContext, FloatingSidebarService::class.java).apply {
          action =
            if (enable) FloatingSidebarService.ACTION_START
            else FloatingSidebarService.ACTION_STOP
          if (enable && !targetGamePackage.isNullOrBlank()) {
            putExtra(FloatingSidebarService.EXTRA_LAUNCHED_GAME_PKG, targetGamePackage)
          }
        }
      try {
        if (enable) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
          } else {
            appContext.startService(intent)
          }
        } else {
          appContext.stopService(intent)
        }
      } catch (_: Throwable) {
        if (enable) {
          prefs.setFloatingEnabled(false)
        }
      }
    }
  }

  fun setFloatingAutoCloseOnExit(enabled: Boolean) {
    viewModelScope.launch {
      prefs.setFloatingAutoCloseOnExit(enabled)
      notifyFloatingStyleChanged()
    }
  }

  fun shouldTrackFloatingReturnToApp(): Boolean {
    return userPrefs.value.floatingAutoCloseOnExit &&
      (FloatingSidebarService.isRunning || _activeSession.value != null)
  }

  fun autoHideFloatingOnReturnToApp() {
    if (userPrefs.value.floatingAutoCloseOnExit &&
      (FloatingSidebarService.isRunning || userPrefs.value.floatingSidebarEnabled)
    ) {
      setFloatingSidebar(false)
    }
  }

  fun updateFloatingAppearance(alpha: Float, scale: Float) {
    viewModelScope.launch {
      prefs.setFloatingAppearance(alpha, scale)
      notifyFloatingStyleChanged()
    }
  }

  fun setFloatingEdgeConfig(
    edgeSide: Int,
    handleThicknessDp: Int,
    handleAlpha: Float,
    autoHide: Boolean,
    haptics: Boolean,
    swipeSensitivity: Float,
  ) {
    viewModelScope.launch {
      prefs.setFloatingEdgeConfig(
        edgeSide = edgeSide,
        handleThicknessDp = handleThicknessDp,
        handleAlpha = handleAlpha,
        autoHide = autoHide,
        haptics = haptics,
        swipeSensitivity = swipeSensitivity,
      )
      notifyFloatingStyleChanged()
    }
  }

  fun togglePinnedFloatingApp(packageName: String) {
    viewModelScope.launch {
      prefs.togglePinnedFloatingApp(packageName)
      refreshFloatingShortcuts()
      notifyFloatingStyleChanged()
    }
  }

  fun resetFloatingPosition() {
    viewModelScope.launch {
      prefs.setFloatingPosition(0, 260)
      notifyFloatingStyleChanged()
    }
  }

  fun setHudOverlayConfig(
    enabled: Boolean? = null,
    mode: HudDisplayMode? = null,
    opacity: Float? = null,
    autoHide: Boolean? = null,
  ) {
    viewModelScope.launch {
      prefs.setHudOverlayConfig(
        enabled = enabled,
        mode = mode,
        opacity = opacity,
        autoHide = autoHide,
      )
      if (enabled == true && !FloatingSidebarService.isRunning && tweaksManager.canDrawOverlays()) {
        setFloatingSidebar(true)
      } else {
        notifyFloatingStyleChanged()
      }
    }
  }

  fun saveCustomVoicePreset(
    pitch: Float,
    bass: Float,
    treble: Float,
    echo: Float,
    reverb: Float,
    robot: Float,
  ) {
    viewModelScope.launch {
      prefs.saveCustomVoicePreset(pitch, bass, treble, echo, reverb, robot)
    }
  }

  private fun notifyFloatingStyleChanged() {
    if (FloatingSidebarService.isRunning) {
      val intent =
        Intent(appContext, FloatingSidebarService::class.java).apply {
          action = FloatingSidebarService.ACTION_REFRESH_STYLE
        }
      try {
        appContext.startService(intent)
      } catch (_: Throwable) {
      }
    }
  }

  fun selectProfile(profile: GamingProfile, activity: Activity?, applyNow: Boolean = true) {
    viewModelScope.launch {
      try {
        prefs.setActiveProfile(profile)
        if (applyNow) {
          val currentPrefs = prefs.prefsFlow.first()
          val config = currentPrefs.resolveProfileConfig(profile)
          tweaksManager.performSmartBoost(
            telemetryMonitor,
            config,
            activity,
            currentPrefs.language == LanguageMode.INDONESIAN,
          )
          _dndEnabled.value = tweaksManager.isDndEnabled()
          _immersiveEnabled.value = config.immersiveMode
        }
      } catch (_: Throwable) {
      }
    }
  }

  fun updateCustomProfileConfig(config: ProfileConfig) {
    viewModelScope.launch { prefs.updateCustomConfig(config) }
  }

  fun setDarkMode(enabled: Boolean) {
    viewModelScope.launch { prefs.setDarkMode(enabled) }
  }

  fun setLanguage(mode: LanguageMode) {
    viewModelScope.launch { prefs.setLanguage(mode) }
  }

  fun setAnimationsEnabled(enabled: Boolean) {
    viewModelScope.launch { prefs.setAnimationsEnabled(enabled) }
  }

  fun applyQuickOverlayProfile(profile: QuickOverlayProfile) {
    viewModelScope.launch {
      prefs.setQuickOverlayProfile(profile)
      notifyFloatingStyleChanged()
    }
  }

  fun setRecorderCountdownSec(sec: Int) {
    viewModelScope.launch {
      prefs.setRecorderCountdownSec(sec)
      notifyFloatingStyleChanged()
    }
  }

  fun buildSessionExportReportText(): String {
    val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
    return SessionIntelligenceEngine.formatExportReport(sessionIntelligence.value, allSessions.value, isIndo)
  }

  // --- v1.25.5 Session Timeline, Resource Report, Profile Automation, Pre-Game Check, Display & Focus Mode ---

  fun deleteTimelineSession(sessionId: String) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.deleteSessionTimelineSession(sessionId)
    }
  }

  fun clearTimelineEvents() {
    viewModelScope.launch(Dispatchers.IO) {
      repository.clearSessionTimelineEvents()
    }
  }

  fun buildTimelineExportText(events: List<SessionTimelineEventEntity>): String {
    val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val sb = StringBuilder()
    sb.appendLine("DRX GAME TURBO PRO v${com.example.BuildConfig.VERSION_NAME} — Session Timeline")
    sb.appendLine(if (isIndo) "Total Event: ${events.size}" else "Total Events: ${events.size}")
    sb.appendLine("--------------------------------------------------")
    events.sortedBy { it.timestampMs }.forEach { ev ->
      sb.appendLine(
        "[${fmt.format(Date(ev.timestampMs))}] ${ev.gameName} | ${ev.eventType} | ${ev.title} — ${ev.detail} " +
          "(Bat ${ev.batteryPercent}%, ${String.format(Locale.US, "%.1f°C", ev.batteryTempC)}, " +
          "RAM ${ev.ramFreeMb} MB free, Net: ${ev.networkStatus})"
      )
    }
    return sb.toString()
  }

  fun buildGameResourceReports(): List<GameResourceReport> {
    val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
    val snap = _telemetry.value
    val avgCpu =
      _telemetryHistory.value.cpuSamples.takeIf { it.isNotEmpty() && !snap.cpuInfo.isUsageRestricted }
        ?.average()
        ?.toFloat()
    return GameResourceReportEngine.buildPerGameReports(
      games = games.value,
      sessions = allSessions.value,
      sessionReports = sessionReports.value,
      timelineEvents = sessionTimelineEvents.value,
      cpuUsageRestricted = snap.cpuInfo.isUsageRestricted,
      avgCpuPercentSample = avgCpu,
      isIndo = isIndo,
    )
  }

  fun compareGameSessions(
    left: GameSessionEntity,
    right: GameSessionEntity,
  ): List<SessionComparisonMetric> {
    val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
    val allEvents = sessionTimelineEvents.value
    val leftEvents = allEvents.filter { it.gamePackageName == left.gamePackageName && it.timestampMs in left.startedAt..left.endedAt }
    val rightEvents = allEvents.filter { it.gamePackageName == right.gamePackageName && it.timestampMs in right.startedAt..right.endedAt }
    return GameResourceReportEngine.compareTwoSessionsForGame(left, right, leftEvents, rightEvents, isIndo)
  }

  fun upsertGameProfileAutomation(item: GameProfileAutomationEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.upsertGameProfileAutomation(item.copy(updatedAtMs = System.currentTimeMillis()))
    }
  }

  fun duplicateGameProfileAutomation(
    source: GameProfileAutomationEntity,
    targetPackageName: String,
    targetAppName: String,
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.upsertGameProfileAutomation(
        source.copy(
          packageName = targetPackageName,
          appName = targetAppName,
          updatedAtMs = System.currentTimeMillis(),
        )
      )
    }
  }

  fun deleteGameProfileAutomation(packageName: String) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.deleteGameProfileAutomation(packageName)
    }
  }

  fun applyGameProfileAutomation(automation: GameProfileAutomationEntity, activity: Activity?) {
    viewModelScope.launch {
      val profile =
        GamingProfile.entries.find { it.id == automation.gamingProfileId } ?: GamingProfile.BALANCED
      prefs.setActiveProfile(profile)
      val hudMode =
        when (automation.hudLayoutId.lowercase()) {
          "mini", "compact", "minimal" -> HudDisplayMode.MINI
          "full" -> HudDisplayMode.FULL
          else -> HudDisplayMode.STANDARD
        }
      prefs.setHudOverlayConfig(enabled = automation.fpsMonitorEnabled, mode = hudMode)
      if (automation.performanceDisplayModeId >= 0) {
        prefs.setPreferredDisplayModeId(automation.performanceDisplayModeId)
        if (activity != null) {
          tweaksManager.applyPreferredDisplayMode(activity, automation.performanceDisplayModeId)
        }
      }
      if (automation.floatingSidebarEnabled && tweaksManager.canDrawOverlays()) {
        setFloatingSidebar(true, targetGamePackage = automation.packageName)
      } else {
        notifyFloatingStyleChanged()
      }
      val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
      _lastAutoAppliedProfileBanner.value =
        if (isIndo) "Profil ${automation.appName} diterapkan (${profile.name})"
        else "${automation.appName} profile applied (${profile.name})"
    }
  }

  fun dismissAutoAppliedBanner() {
    _lastAutoAppliedProfileBanner.value = null
  }

  fun runPreGameCheck(targetGame: GameEntity? = null): PreGameCheckReport {
    val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
    val snap = telemetryMonitor.readSnapshot()
    _telemetry.value = snap
    val report =
      PreGameCheckEngine.runCheck(
        telemetry = snap,
        tweaksManager = tweaksManager,
        isMonitoringActive = _isDeviceMonitoringActive.value,
        targetGameName = targetGame?.appName,
        targetPackageName = targetGame?.packageName,
        isIndo = isIndo,
      )
    _preGameCheckReport.value = report
    return report
  }

  fun requestLaunchWithPreGameCheck(game: GameEntity, activity: Activity?) {
    if (userPrefs.value.preGameCheckBeforeLaunch) {
      runPreGameCheck(game)
      _pendingPreGameLaunch.value = game
    } else {
      startGameLaunch(game, activity)
    }
  }

  fun confirmPreGameLaunch(activity: Activity?) {
    val game = _pendingPreGameLaunch.value ?: return
    _pendingPreGameLaunch.value = null
    startGameLaunch(game, activity)
  }

  fun dismissPreGameModal() {
    _pendingPreGameLaunch.value = null
  }

  fun setPreGameCheckBeforeLaunch(enabled: Boolean) {
    viewModelScope.launch { prefs.setPreGameCheckBeforeLaunch(enabled) }
  }

  fun setPreferredDisplayModeId(modeId: Int, activity: Activity? = null) {
    viewModelScope.launch {
      prefs.setPreferredDisplayModeId(modeId)
      if (activity != null) {
        tweaksManager.applyPreferredDisplayMode(activity, modeId)
      }
      _telemetry.value = telemetryMonitor.readSnapshot()
    }
  }

  fun setFocusTargetMinutes(minutes: Int) {
    val clamped = minutes.coerceIn(5, 240)
    _focusModeState.update { it.copy(targetMinutes = clamped) }
    viewModelScope.launch { prefs.setFocusSessionTargetMinutes(clamped) }
  }

  fun startFocusMode(enableDnd: Boolean, extendTimeoutMs: Int?) {
    focusTimerJob?.cancel()
    val targetMin = _focusModeState.value.targetMinutes.coerceIn(5, 240)
    val nextState =
      GamingFocusModeController.activateFocusSession(
        tweaksManager = tweaksManager,
        telemetryMonitor = telemetryMonitor,
        targetMinutes = targetMin,
        enableDnd = enableDnd,
        extendTimeoutMs = extendTimeoutMs,
      )
    _focusModeState.value = nextState
    _dndEnabled.value = tweaksManager.isDndEnabled()
    focusTimerJob =
      viewModelScope.launch {
        while (isActive && _focusModeState.value.isActive) {
          delay(1000L)
          _focusModeState.update { cur ->
            if (!cur.isActive) cur else cur.copy(elapsedSeconds = cur.elapsedSeconds + 1L)
          }
        }
      }
  }

  fun stopFocusMode(restoreDnd: Boolean = true, restoreTimeout: Boolean = true) {
    focusTimerJob?.cancel()
    focusTimerJob = null
    val cur = _focusModeState.value
    if (!cur.isActive) return
    val stopped =
      GamingFocusModeController.stopFocusSession(
        current = cur,
        tweaksManager = tweaksManager,
        restoreDnd = restoreDnd,
        restoreTimeout = restoreTimeout,
      )
    _focusModeState.value = stopped
    _dndEnabled.value = tweaksManager.isDndEnabled()
    val summary = stopped.lastSummary
    if (summary != null) {
      val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
      val text = GamingFocusModeController.formatSummaryText(summary, isIndo)
      viewModelScope.launch { prefs.setLastFocusSummaryText(text) }
    }
  }

  fun setLaunchAnimationEnabled(enabled: Boolean) {
    viewModelScope.launch { prefs.setLaunchAnimationEnabled(enabled) }
  }

  fun setLaunchAnimationQuality(quality: LaunchAnimationQuality) {
    viewModelScope.launch { prefs.setLaunchAnimationQuality(quality) }
  }

  fun setLaunchSoundEnabled(enabled: Boolean) {
    viewModelScope.launch { prefs.setLaunchSoundEnabled(enabled) }
  }

  fun setGamesGridView(grid: Boolean) {
    viewModelScope.launch { prefs.setGamesGridView(grid) }
  }

  fun setBoostNotifications(enabled: Boolean) {
    viewModelScope.launch { prefs.setBoostNotifications(enabled) }
  }

  fun setTimerNotifications(enabled: Boolean) {
    viewModelScope.launch { prefs.setTimerNotifications(enabled) }
  }

  fun clearHistory() {
    viewModelScope.launch(Dispatchers.IO) { repository.clearHistoryAndStats() }
  }

  fun refreshSocialStats() {
    val prevUpdated =
      when (val cur = _socialStats.value) {
        is SocialStatsState.Loaded -> cur.lastUpdatedMs
        is SocialStatsState.Error -> cur.lastUpdatedMs
        is SocialStatsState.Loading -> cur.lastUpdatedMs
      }
    _socialStats.value = SocialStatsState.Loading(lastUpdatedMs = prevUpdated)
    viewModelScope.launch {
      _socialStats.value = socialRepository.fetchSocialMetrics()
    }
  }

  fun updateSupporterAvatarFromUri(uri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        appContext.filesDir.listFiles { f -> f.name.startsWith("supporter_avatar_") }?.forEach { it.delete() }
        val target = File(appContext.filesDir, "supporter_avatar_${System.currentTimeMillis()}.bin")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
          target.outputStream().use { output -> input.copyTo(output) }
        }
        if (target.exists() && target.length() > 0L) {
          prefs.setSupporterAvatarPath(target.absolutePath)
        }
      } catch (_: Throwable) {
      }
    }
  }

  fun clearSupporterAvatar() {
    viewModelScope.launch(Dispatchers.IO) {
      appContext.filesDir.listFiles { f -> f.name.startsWith("supporter_avatar_") }?.forEach { it.delete() }
      prefs.setSupporterAvatarPath("")
    }
  }

  fun markUpdateRead(version: String) {
    viewModelScope.launch { prefs.markUpdateVersionRead(version) }
  }

  fun markAllUpdatesRead() {
    viewModelScope.launch {
      prefs.markAllUpdatesRead(DrxUpdateCatalog.entries.map { it.versionNumber })
    }
  }

  fun syncAnnouncementSchedule(forceReschedule: Boolean = false) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        announcementScheduler.syncSchedule(forceReschedule = forceReschedule)
      } catch (_: Throwable) {
      }
    }
  }

  fun setAnnouncementsEnabled(enabled: Boolean) {
    viewModelScope.launch {
      prefs.setAnnouncementsEnabled(enabled)
      if (enabled) {
        syncAnnouncementSchedule(forceReschedule = true)
      } else {
        announcementScheduler.cancelAll()
        prefs.saveScheduleMetadata("", "")
      }
    }
  }

  fun setAnnouncementFrequency(minPerDay: Int, maxPerDay: Int) {
    viewModelScope.launch {
      prefs.setAnnouncementFrequency(minPerDay, maxPerDay)
      syncAnnouncementSchedule(forceReschedule = true)
    }
  }

  fun setQuietHours(startHour: Int, endHour: Int) {
    viewModelScope.launch {
      prefs.setQuietHours(startHour, endHour)
      syncAnnouncementSchedule(forceReschedule = true)
    }
  }

  fun setAnnouncementSound(enabled: Boolean) {
    viewModelScope.launch { prefs.setAnnouncementSound(enabled) }
  }

  fun setAnnouncementVibration(enabled: Boolean) {
    viewModelScope.launch { prefs.setAnnouncementVibration(enabled) }
  }

  fun toggleAnnouncementCategory(categoryId: String) {
    viewModelScope.launch { prefs.toggleAnnouncementCategory(categoryId) }
  }

  fun sendPreviewAnnouncement() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val current = prefs.prefsFlow.first()
        val picked =
          AnnouncementSchedulePlanner.pickNonDuplicateMessage(
            enabledCategoryIds = current.enabledAnnouncementCategories,
            recentIds = current.recentAnnouncementIds,
            lastCategoryId = current.lastAnnouncementCategoryId,
          )
        val posted = AnnouncementNotifier.postNotification(appContext, picked, current)
        if (posted) {
          prefs.recordSentAnnouncement(picked.id, picked.category.id)
          repository.insertAnnouncement(
            AnnouncementHistoryEntity(
              messageId = picked.id,
              categoryId = picked.category.id,
              title = picked.category.notificationTitle,
              body = picked.body,
              targetRoute = picked.targetRoute,
              deliveredAt = System.currentTimeMillis(),
            )
          )
        }
      } catch (_: Throwable) {
      }
    }
  }

  fun resetAnnouncementRotation() {
    viewModelScope.launch {
      prefs.resetAnnouncementRotation()
    }
  }

  fun clearAnnouncementHistory() {
    viewModelScope.launch(Dispatchers.IO) { repository.clearAnnouncements() }
  }

  fun setThemePreset(preset: DrxThemePreset) {
    viewModelScope.launch { prefs.setThemePreset(preset) }
  }

  fun setCustomAccentArgb(argb: Long) {
    viewModelScope.launch { prefs.setCustomAccentArgb(argb) }
  }

  fun updateCustomWallpaperFromUri(uri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        appContext.filesDir.listFiles { f -> f.name.startsWith("drx_wallpaper_") }?.forEach { it.delete() }
        val target = File(appContext.filesDir, "drx_wallpaper_${System.currentTimeMillis()}.bin")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
          target.outputStream().use { output -> input.copyTo(output) }
        }
        if (target.exists() && target.length() > 0L) {
          prefs.setCustomWallpaperPath(target.absolutePath)
        }
      } catch (_: Throwable) {
      }
    }
  }

  fun clearCustomWallpaper() {
    viewModelScope.launch(Dispatchers.IO) {
      appContext.filesDir.listFiles { f -> f.name.startsWith("drx_wallpaper_") }?.forEach { it.delete() }
      prefs.setCustomWallpaperPath("")
    }
  }

  fun setUiSoundsEnabled(enabled: Boolean) {
    viewModelScope.launch { prefs.setUiSoundsEnabled(enabled) }
  }

  fun setHapticFeedbackEnabled(enabled: Boolean) {
    viewModelScope.launch { prefs.setHapticFeedbackEnabled(enabled) }
  }

  fun setManualSocialCounters(ytSubs: String, ttFollowers: String) {
    viewModelScope.launch { prefs.setManualSocialCounters(ytSubs, ttFollowers) }
  }

  fun resetAllSettings() {
    viewModelScope.launch {
      setFloatingSidebar(false)
      announcementScheduler.cancelAll()
      appContext.filesDir.listFiles { f -> f.name.startsWith("supporter_avatar_") || f.name.startsWith("drx_wallpaper_") }?.forEach { it.delete() }
      prefs.resetAll()
    }
  }

  private fun postBoostNotification(report: BoostReport, isIndo: Boolean) {
    try {
      val nm = appContext.getSystemService(NotificationManager::class.java)
      val channelId = "drx_boost_alerts"
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        nm.createNotificationChannel(
          NotificationChannel(
            channelId,
            "DRX Boost Alerts",
            NotificationManager.IMPORTANCE_LOW,
          )
        )
      }
      val text =
        if (isIndo) {
          "RAM Tersedia: ${report.ramAfterAvailMb} MB • Suhu ${String.format(java.util.Locale.US, "%.1f", report.batteryTempC)}°C"
        } else {
          "Available RAM: ${report.ramAfterAvailMb} MB • Temp ${String.format(java.util.Locale.US, "%.1f", report.batteryTempC)}°C"
        }
      val notif =
        NotificationCompat.Builder(appContext, channelId)
          .setSmallIcon(R.mipmap.ic_launcher)
          .setContentTitle("DRX GAME TURBO PRO")
          .setContentText(text)
          .setAutoCancel(true)
          .build()
      nm.notify(7002, notif)
    } catch (_: Throwable) {
    }
  }

  override fun onCleared() {
    focusTimerJob?.cancel()
    telemetryMonitor.stopFpsMonitoring()
    super.onCleared()
  }
}
