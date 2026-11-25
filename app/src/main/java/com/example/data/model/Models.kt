package com.example.data.model

enum class LanguageMode(val code: String, val displayName: String) {
  INDONESIAN("id", "Bahasa Indonesia"),
  ENGLISH("en", "English"),
}

enum class LaunchAnimationQuality(val id: String) {
  HIGH("high"),
  BALANCED("balanced"),
  MINIMAL("minimal"),
}

enum class GamingProfile(val id: String) {
  PERFORMANCE("performance"),
  BALANCED("balanced"),
  BATTERY_SAVER("battery_saver"),
  CUSTOM("custom"),
}

enum class GameCategory(val id: String) {
  ALL("all"),
  FPS("fps"),
  MOBA("moba"),
  BATTLE_ROYALE("battle_royale"),
  RPG("rpg"),
  RACING("racing"),
  STRATEGY("strategy"),
  CASUAL("casual"),
  CUSTOM("custom"),
}

enum class SoundPreset(val id: String) {
  BALANCED("balanced"),
  FPS_FOOTSTEPS("fps_footsteps"),
  MOBA_VOICE("moba_voice"),
  BASS_IMPACT("bass_impact"),
  TREBLE_CLARITY("treble_clarity"),
  CUSTOM("custom"),
}

enum class VoicePreset(val id: String) {
  NORMAL("normal"),
  DEEP_VOICE("deep_voice"),
  HIGH_PITCH("high_pitch"),
  ROBOT("robot"),
  ELECTRONIC("electronic"),
  RADIO("radio"),
  ALIEN("alien"),
  ECHO("echo"),
  REVERB("reverb"),
  CUSTOM("custom"),
}

data class ProfileConfig(
  val profile: GamingProfile,
  val brightnessPercent: Int,
  val autoBrightness: Boolean,
  val enableDnd: Boolean,
  val orientationMode: Int, // 0 = Auto, 1 = Portrait, 2 = Landscape
  val immersiveMode: Boolean,
  val soundPreset: SoundPreset,
  val autoFloatingSidebar: Boolean,
)

data class CpuInfo(
  val cores: Int = Runtime.getRuntime().availableProcessors(),
  val abi: String = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "ARM64",
  val usagePercent: Float? = null,
  val currentFreqMhz: Int? = null,
  val maxFreqMhz: Int? = null,
  val isUsageRestricted: Boolean = true,
)

data class MemoryInfoData(
  val totalBytes: Long = 0L,
  val availBytes: Long = 0L,
  val usedBytes: Long = 0L,
  val usedPercent: Float = 0f,
  val lowMemory: Boolean = false,
  val thresholdBytes: Long = 0L,
  val appHeapUsedMb: Float = 0f,
  val appHeapMaxMb: Float = 0f,
)

data class StorageInfoData(
  val totalBytes: Long = 0L,
  val freeBytes: Long = 0L,
  val usedBytes: Long = 0L,
  val usedPercent: Float = 0f,
)

data class BatteryInfoData(
  val levelPercent: Int = 0,
  val temperatureC: Float = 0f,
  val voltageMv: Int = 0,
  val currentMa: Int? = null,
  val isCharging: Boolean = false,
  val chargeSource: String = "Battery",
  val healthCode: Int = android.os.BatteryManager.BATTERY_HEALTH_GOOD,
)

data class NetworkSnapshot(
  val isConnected: Boolean = false,
  val transportType: String = "Offline",
  val networkSubType: String = "-",
  val linkSpeedMbps: Int? = null,
  val frequencyMhz: Int? = null,
  val rssiDbm: Int? = null,
  val isMetered: Boolean = false,
  val qualityLevel: Int = 0, // 0 = Offline, 1 = Poor, 2 = Fair, 3 = Good, 4 = Excellent
)

data class DisplayModeOption(
  val modeId: Int,
  val widthPx: Int,
  val heightPx: Int,
  val refreshRateHz: Float,
)

data class DisplayInfoData(
  val widthPx: Int = 1080,
  val heightPx: Int = 2400,
  val densityDpi: Int = 420,
  val refreshRateHz: Float = 60f,
  val supportedRefreshRates: List<Float> = listOf(60f),
  val isHdr: Boolean = false,
  val brightnessPercent: Int = 50,
  val screenTimeoutMs: Int = 60_000,
  val physicalSizeInches: String = "-",
  val orientationLabel: String = "Portrait",
  val activeDisplayModeId: Int? = null,
  val supportedModes: List<DisplayModeOption> = emptyList(),
)

data class SystemTelemetry(
  val cpuInfo: CpuInfo = CpuInfo(),
  val memoryInfo: MemoryInfoData = MemoryInfoData(),
  val storageInfo: StorageInfoData = StorageInfoData(),
  val batteryInfo: BatteryInfoData = BatteryInfoData(),
  val networkSnapshot: NetworkSnapshot = NetworkSnapshot(),
  val displayInfo: DisplayInfoData = DisplayInfoData(),
  val thermalStatusCode: Int = 0,
  val uiFps: Float = 60f,
  val frameTimeMs: Float = 16.6f,
  val timestampMs: Long = System.currentTimeMillis(),
)

data class InstalledAppItem(
  val packageName: String,
  val appName: String,
  val isLikelyGame: Boolean,
  val suggestedCategory: GameCategory,
)

data class FloatingAppShortcut(
  val key: String,
  val defaultLabel: String,
  val packageName: String?,
  val isBuiltInMiniWindow: Boolean = false,
  val supportsFreeform: Boolean = false,
)

data class BoostReport(
  val timestampMs: Long,
  val ramBeforeAvailMb: Long,
  val ramAfterAvailMb: Long,
  val totalRamMb: Long,
  val batteryTempC: Float,
  val profileApplied: GamingProfile,
  val actionsApplied: List<String>,
)

data class AppUsageSummary(
  val packageName: String,
  val appName: String,
  val totalForegroundMs: Long,
  val lastTimeUsedMs: Long,
)

data class NetworkDiagnosticResult(
  val host: String,
  val pingMs: Float,
  val jitterMs: Float,
  val packetLossPercent: Float,
  val downloadMbps: Float,
  val stabilityScore: Int,
  val samplesMs: List<Float>,
  val networkType: String,
)

data class PermissionStateItem(
  val key: String,
  val granted: Boolean,
  val isSpecialSystemSetting: Boolean,
)

enum class DrxThemePreset(
  val id: String,
  val displayName: String,
  val primaryHex: Long,
  val secondaryHex: Long,
  val bgHex: Long,
  val cardHex: Long,
) {
  DRX_DEFAULT("drx_default", "DRX Default", 0xFF087BFFL, 0xFF15D9FFL, 0xFF050609L, 0xFF101722L),
  AMOLED_BLACK("amoled_black", "AMOLED Black", 0xFF2563EBL, 0xFF38BDF8L, 0xFF000000L, 0xFF0B0D13L),
  CYBERPUNK_BLUE("cyberpunk_blue", "Cyberpunk Blue", 0xFF00A3FFL, 0xFF00F5D4L, 0xFF050B16L, 0xFF0D1B2EL),
  RGB_GAMING("rgb_gaming", "RGB Gaming", 0xFF3B82F6L, 0xFF10B981L, 0xFF080912L, 0xFF131626L),
  PURPLE_NEON("purple_neon", "Purple Neon", 0xFF8B5CF6L, 0xFFC084FCL, 0xFF080612L, 0xFF151029L),
  RED_INFERNO("red_inferno", "Red Inferno", 0xFFEF4444L, 0xFFF97316L, 0xFF100608L, 0xFF1D0E12L),
  MINIMAL_DARK("minimal_dark", "Minimal Dark", 0xFF64748BL, 0xFF94A3B8L, 0xFF0B0D11L, 0xFF151921L),
}

enum class DrxEdition(val id: String, val badge: String) {
  FREE("free", "DRX FREE"),
  PRO("pro", "DRX PRO"),
  CREATOR("creator", "DRX CREATOR"),
}

enum class GameSortOption(val id: String) {
  NAME_ASC("name"),
  PLAYTIME_DESC("playtime"),
  LAST_PLAYED_DESC("last_played"),
}

enum class StatsTimeFilter(val id: String) {
  TODAY("today"),
  DAYS_7("7d"),
  DAYS_30("30d"),
  ALL("all"),
}

data class GameDetailInfo(
  val packageName: String,
  val appName: String,
  val developerName: String?,
  val versionName: String,
  val versionCode: Long,
  val isInstalled: Boolean,
  val firstInstallTimeMs: Long?,
  val lastUpdateTimeMs: Long?,
  val installSourceLabel: String?,
  val apkSizeBytes: Long?,
  val appSizeBytes: Long?,
  val dataSizeBytes: Long?,
  val cacheSizeBytes: Long?,
  val totalStorageBytes: Long?,
  val storageAccessRestricted: Boolean,
)

data class DayActivityBucket(
  val dayLabel: String,
  val dayEpochStartMs: Long,
  val durationMs: Long,
)

data class SystemGameUsageSummary(
  val packageName: String,
  val hasPermission: Boolean = false,
  val todayMs: Long = 0L,
  val yesterdayMs: Long = 0L,
  val last7DaysMs: Long = 0L,
  val last30DaysMs: Long = 0L,
  val lastTimeUsedMs: Long = 0L,
  val launchCountEstimate: Int = 0,
  val isCurrentlyForeground: Boolean = false,
  val dailyBucketsByEpochStart: Map<Long, Long> = emptyMap(),
)

data class CombinedGameStatistics(
  val gameInfo: GameDetailInfo,
  val systemUsage: SystemGameUsageSummary,
  val totalPlaytimeMs: Long,
  val drxTrackedPlaytimeMs: Long,
  val todayMs: Long,
  val yesterdayMs: Long,
  val last7DaysMs: Long,
  val last30DaysMs: Long,
  val dailyAverageMs: Long,
  val sessionCount: Int,
  val launchCountEstimate: Int,
  val lastSessionDurationMs: Long,
  val longestSessionMs: Long,
  val peakDayLabel: String,
  val peakDayDurationMs: Long,
  val lastPlayedTimestampMs: Long?,
  val lastSessionStartMs: Long?,
  val lastSessionEndMs: Long?,
  val usesEstimatedSystemUsage: Boolean,
  val chart7Days: List<DayActivityBucket>,
  val chart30Days: List<DayActivityBucket>,
)

data class GamePlaytimeRank(
  val packageName: String,
  val appName: String,
  val totalPlaytimeMs: Long,
  val sessionOrLaunchCount: Int,
  val lastPlayedAtMs: Long?,
  val isEstimated: Boolean,
)

data class GlobalGamingStatistics(
  val totalTrackedGames: Int,
  val installedTrackedGames: Int,
  val totalPlaytimeMs: Long,
  val mostPlayedByTimeName: String?,
  val mostPlayedByTimePkg: String?,
  val mostPlayedByTimeMs: Long,
  val mostOpenedGameName: String?,
  val mostOpenedPkg: String?,
  val mostOpenedCount: Int,
  val lastPlayedGameName: String?,
  val lastPlayedPkg: String?,
  val lastPlayedAtMs: Long?,
  val totalSessionsCount: Int,
  val averageSessionDurationMs: Long,
  val hasUsagePermission: Boolean,
  val usesEstimatedSystemUsage: Boolean,
  val weeklyChart: List<DayActivityBucket>,
  val monthlyChart: List<DayActivityBucket>,
  val rankedGames: List<GamePlaytimeRank>,
)

enum class QuickOverlayProfile(
  val id: String,
  val labelEn: String,
  val labelId: String,
  val pollingIntervalMs: Long,
  val hudMode: HudDisplayMode,
  val hudOpacity: Float,
  val compactHud: Boolean,
  val preferAnimations: Boolean,
  val alertBannersEnabled: Boolean,
) {
  BALANCED(
    id = "balanced",
    labelEn = "Balanced",
    labelId = "Seimbang",
    pollingIntervalMs = 1500L,
    hudMode = HudDisplayMode.STANDARD,
    hudOpacity = 0.90f,
    compactHud = false,
    preferAnimations = true,
    alertBannersEnabled = true,
  ),
  PERFORMANCE_MONITORING(
    id = "performance_monitoring",
    labelEn = "Performance Monitoring",
    labelId = "Monitor Performa",
    pollingIntervalMs = 750L,
    hudMode = HudDisplayMode.FULL,
    hudOpacity = 0.95f,
    compactHud = false,
    preferAnimations = true,
    alertBannersEnabled = true,
  ),
  BATTERY_SAVER(
    id = "battery_saver",
    labelEn = "Battery Saver",
    labelId = "Hemat Baterai",
    pollingIntervalMs = 3000L,
    hudMode = HudDisplayMode.MINI,
    hudOpacity = 0.78f,
    compactHud = true,
    preferAnimations = false,
    alertBannersEnabled = false,
  ),
  MINIMAL_OVERLAY(
    id = "minimal_overlay",
    labelEn = "Minimal Overlay",
    labelId = "Overlay Minimal",
    pollingIntervalMs = 2000L,
    hudMode = HudDisplayMode.MINI,
    hudOpacity = 0.68f,
    compactHud = true,
    preferAnimations = true,
    alertBannersEnabled = false,
  );

  companion object {
    fun fromId(raw: String): QuickOverlayProfile =
      entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: BALANCED
  }
}

data class SessionComparisonMetric(
  val label: String,
  val leftValue: String,
  val rightValue: String,
  val deltaText: String,
)

data class SessionIntelligenceSummary(
  val totalGamingTimeMs: Long = 0L,
  val averageSessionDurationMs: Long = 0L,
  val longestSessionDurationMs: Long = 0L,
  val longestSessionGameName: String? = null,
  val mostPlayedGameName: String? = null,
  val mostPlayedGamePkg: String? = null,
  val mostPlayedTimeMs: Long = 0L,
  val lastPlayedGameName: String? = null,
  val lastPlayedGamePkg: String? = null,
  val lastPlayedAtMs: Long? = null,
  val totalSessionsCount: Int = 0,
  val thisWeekTimeMs: Long = 0L,
  val previousWeekTimeMs: Long = 0L,
  val weekDeltaPercent: Int? = null,
  val averageDailyPlaytimeMs: Long = 0L,
  val peakHourWindow: String? = null,
  val favoriteProfileUsed: String? = null,
  val dailyChart7d: List<DayActivityBucket> = emptyList(),
  val weeklyChart4w: List<DayActivityBucket> = emptyList(),
  val usesSystemUsageEstimate: Boolean = false,
)

enum class DevicePerformanceState {
  READY,
  MONITORING,
  THERMAL_WARNING,
  LIMITED_MONITORING,
  DEVICE_IDLE,
}

enum class SessionTimelineEventType(val code: String) {
  LAUNCHED("launched"),
  MONITORING_STARTED("monitoring_started"),
  TEMP_INCREASED("temp_increased"),
  THERMAL_WARNING("thermal_warning"),
  BATTERY_DROP("battery_drop"),
  NETWORK_CHANGED("network_changed"),
  MEMORY_PRESSURE("memory_pressure"),
  RECORDING_STARTED("recording_started"),
  FPS_DROP("fps_drop"),
  SESSION_ENDED("session_ended"),
}

data class GameResourceReport(
  val packageName: String,
  val gameName: String,
  val sessionCount: Int,
  val totalPlaytimeMs: Long,
  val averageSessionDurationMs: Long,
  val estimatedTotalBatteryDrainPercent: Int,
  val avgRamUsedPercent: Float?,
  val avgRamFreeMb: Long?,
  val avgCpuPercentOrNull: Float?,
  val thermalWarningCount: Int,
  val peakTempC: Float,
  val monitoringAvailabilityLabel: String,
  val hasRestrictedCpu: Boolean,
)

enum class PreGameCheckStatus {
  READY,
  WARNING,
  UNAVAILABLE,
}

data class PreGameCheckItem(
  val id: String,
  val title: String,
  val valueText: String,
  val status: PreGameCheckStatus,
  val detailNote: String,
  val tipText: String? = null,
)

data class PreGameCheckReport(
  val targetGameName: String?,
  val targetPackageName: String?,
  val overallStatus: PreGameCheckStatus,
  val checkedAtMs: Long,
  val items: List<PreGameCheckItem>,
  val summaryTip: String,
)

enum class TouchTestMode(val id: String, val labelEn: String, val labelId: String) {
  TAP("tap", "Tap Test", "Uji Tap"),
  MULTI_TOUCH("multi_touch", "Multi-Touch", "Multi-Touch"),
  TRACKING("tracking", "Drag & Tracking", "Tracking & Geser"),
  GESTURE("gesture", "Swipe & Gesture", "Swipe & Gestur"),
  ACCURACY("accuracy", "Accuracy Target", "Target Akurasi"),
}

data class FocusModeSessionSummary(
  val startedAtMs: Long,
  val endedAtMs: Long,
  val durationMs: Long,
  val targetMinutes: Int,
  val dndActivated: Boolean,
  val screenTimeoutAppliedMs: Int?,
  val completedTarget: Boolean,
)

enum class ScreenshotFormat(val ext: String, val mimeType: String, val label: String) {
  PNG("png", "image/png", "PNG"),
  JPEG("jpg", "image/jpeg", "JPEG"),
}

enum class ScreenshotCaptureMode {
  APP_WINDOW,
  FULL_SCREEN,
}

enum class ThermalTier(val labelEn: String, val labelId: String) {
  NORMAL("Normal", "Normal"),
  WARM("Warm", "Hangat"),
  ELEVATED("Elevated", "Tinggi"),
  CRITICAL("Critical", "Kritis"),
}

data class ThermalGuardSnapshot(
  val batteryTempC: Float,
  val thermalStatusCode: Int,
  val thermalStatusLabel: String,
  val tier: ThermalTier,
  val isCharging: Boolean,
  val sessionPeakTempC: Float,
  val adviceList: List<String>,
  val timestampMs: Long = System.currentTimeMillis(),
)

data class NetworkStabilityEvent(
  val timestampMs: Long,
  val transportLabel: String,
  val subType: String,
  val isConnected: Boolean,
  val qualityLevel: Int,
  val note: String,
)

data class NetworkStabilitySummary(
  val isConnected: Boolean = false,
  val currentTransport: String = "Offline",
  val currentSubType: String = "-",
  val interruptionCount: Int = 0,
  val transitionCount: Int = 0,
  val recentQualitySamples: List<Int> = emptyList(),
  val events: List<NetworkStabilityEvent> = emptyList(),
)

data class SessionRecapSummary(
  val sessionId: String,
  val gameName: String,
  val packageName: String,
  val startedAtMs: Long,
  val endedAtMs: Long,
  val durationMs: Long,
  val profileUsed: GamingProfile,
  val avgUiFps: Float?,
  val performanceWarningsCount: Int,
  val networkEventsCount: Int,
  val batteryStartPercent: Int,
  val batteryEndPercent: Int,
  val peakTempC: Float,
  val recordingCount: Int,
  val screenshotCount: Int,
)

data class GameAppStorageEntry(
  val packageName: String,
  val appName: String,
  val totalBytes: Long,
)

data class SmartStorageSnapshot(
  val deviceTotalBytes: Long,
  val deviceUsedBytes: Long,
  val deviceFreeBytes: Long,
  val drxCacheBytes: Long,
  val screenshotsCount: Int,
  val screenshotsBytes: Long,
  val recordingsCount: Int,
  val recordingsBytes: Long,
  val gameAppsTotalBytes: Long?,
  val gameAppsCount: Int,
  val topGamesStorage: List<GameAppStorageEntry>,
  val hasUsageAccessForApps: Boolean,
  val timestampMs: Long = System.currentTimeMillis(),
)

enum class CompatibilityStatus {
  SUPPORTED,
  LIMITED,
  UNSUPPORTED,
}

data class CompatibilityCheckItem(
  val id: String,
  val title: String,
  val valueText: String,
  val status: CompatibilityStatus,
  val detailNote: String,
)

data class GameLaunchCompatibilityItem(
  val packageName: String,
  val appName: String,
  val versionName: String,
  val isLaunchable: Boolean,
  val statusNote: String,
)

data class GameCompatibilityReport(
  val androidVersionLabel: String,
  val drxVersionLabel: String,
  val overallStatus: CompatibilityStatus,
  val systemChecks: List<CompatibilityCheckItem>,
  val gameChecks: List<GameLaunchCompatibilityItem>,
  val checkedAtMs: Long = System.currentTimeMillis(),
)



