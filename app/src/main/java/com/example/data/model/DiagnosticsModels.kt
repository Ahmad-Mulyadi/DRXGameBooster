package com.example.data.model

enum class LagStatus(val label: String) {
  NORMAL("NORMAL"),
  WARNING("WARNING"),
  HIGH_LOAD("HIGH LOAD"),
  UNSTABLE("UNSTABLE"),
  DATA_UNAVAILABLE("DATA UNAVAILABLE"),
}

enum class LagCategory {
  MEMORY_PRESSURE,
  THERMAL_CONDITION,
  CPU_PRESSURE,
  NETWORK_INSTABILITY,
  STORAGE_PRESSURE,
}

data class LagThresholdConfig(
  val ramWarningFreeMb: Long = 900L,
  val thermalWarningC: Float = 40.0f,
  val pingWarningMs: Float = 90f,
  val storageWarningFreeMb: Long = 1024L,
)

data class LagMetricTileData(
  val key: String,
  val label: String,
  val valueText: String,
  val subText: String,
  val status: LagStatus,
)

data class LagCauseFinding(
  val category: LagCategory,
  val status: LagStatus,
  val title: String,
  val explanation: String,
  val evidence: String,
  val recommendation: String,
)

data class LagDiagnosticReport(
  val timestampMs: Long,
  val overallStatus: LagStatus,
  val mainSuspectedIssue: String,
  val severityLabel: String,
  val confidencePercent: Int,
  val completenessNote: String,
  val metricTiles: List<LagMetricTileData>,
  val findings: List<LagCauseFinding>,
  val recommendationSummary: String,
  val ramFreeMb: Long,
  val ramTotalMb: Long,
  val batteryTempC: Float,
  val thermalStatusCode: Int,
  val storageFreeBytes: Long,
  val networkType: String,
  val networkPingMs: Float?,
  val networkJitterMs: Float?,
  val networkLossPercent: Float?,
)

enum class CrashExitType(val id: String, val labelId: String, val labelEn: String) {
  USER_CLOSED("user_closed", "User Closed", "User Closed"),
  PROCESS_NO_LONGER_DETECTED("process_gone", "Process No Longer Detected", "Process No Longer Detected"),
  SESSION_INTERRUPTED("interrupted", "Session Interrupted", "Session Interrupted"),
  POSSIBLE_CRASH("possible_crash", "Possible Crash", "Possible Crash"),
  UNKNOWN_EXIT_REASON("unknown", "Unknown Exit Reason", "Unknown Exit Reason"),
}

data class CrashDiagnosticAnalysis(
  val gamePackageName: String,
  val gameName: String,
  val gameVersion: String,
  val exitType: CrashExitType,
  val sessionStartMs: Long,
  val sessionEndMs: Long,
  val deviceRamFreeMb: Long,
  val deviceRamTotalMb: Long,
  val availableStorageBytes: Long,
  val batteryLevelPercent: Int,
  val batteryTempC: Float,
  val networkStatus: String,
  val lastPerformanceSummary: String,
  val diagnosticResult: String,
  val preventionTips: List<String>,
  val importedLogSummary: String? = null,
)

data class HealthCategoryScore(
  val categoryId: String,
  val title: String,
  val scoreOrNull: Int?,
  val statusLabel: String,
  val detailText: String,
)

data class GamingHealthReport(
  val timestampMs: Long,
  val isFullDiagnostic: Boolean,
  val overallScoreOrNull: Int?,
  val readinessTitle: String,
  val readinessDetail: String,
  val categories: List<HealthCategoryScore>,
  val recommendations: List<String>,
)

enum class HudDisplayMode(val id: String, val label: String) {
  MINI("mini", "Mini"),
  STANDARD("standard", "Standard"),
  FULL("full", "Full"),
}

enum class RecordingResolution(val presetHeight: Int, val label: String) {
  P480(480, "480p"),
  P720(720, "720p HD"),
  P1080(1080, "1080p FHD"),
  ORIGINAL(0, "Original"),
}

enum class BitrateTier(val id: String, val label: String, val bps: Int) {
  LOW("low", "Low (3M)", 3_000_000),
  MEDIUM("medium", "Medium (6M)", 6_000_000),
  HIGH("high", "High (10M)", 10_000_000),
  CUSTOM("custom", "Custom", 8_000_000),
}

enum class MediaItemType(val id: String) {
  SCREENSHOT("screenshot"),
  VIDEO("video"),
}

data class MediaGalleryItem(
  val uriString: String,
  val displayName: String,
  val type: MediaItemType,
  val sizeBytes: Long,
  val timestampMs: Long,
  val durationSec: Int = 0,
  val width: Int = 0,
  val height: Int = 0,
  val folderLabel: String = "",
)

data class StorageGallerySummary(
  val screenshotCount: Int = 0,
  val videoCount: Int = 0,
  val totalMediaBytes: Long = 0L,
  val largestVideoName: String? = null,
  val largestVideoBytes: Long = 0L,
  val availableDeviceBytes: Long = 0L,
  val totalDeviceBytes: Long = 0L,
)

enum class BuildPipelineState(val label: String) {
  IDLE("IDLE"),
  CHECKING_SOURCE("CHECKING SOURCE"),
  BUILDING_APK("BUILDING APK"),
  BUILDING_AAB("BUILDING AAB"),
  VALIDATING("VALIDATING"),
  REPLACING_OLD_BUILD("REPLACING OLD BUILD"),
  READY_TO_DOWNLOAD("READY TO DOWNLOAD"),
  BUILD_FAILED("BUILD FAILED"),
}

data class BuildArtifactStatus(
  val title: String,
  val fileName: String,
  val locationLabel: String,
  val sizeBytes: Long,
  val isValidArchive: Boolean,
  val sha256Short: String,
  val signingNote: String,
  val downloadSupported: Boolean,
  val exportFilePath: String? = null,
)

enum class StabilitySeverity(val id: String, val labelEn: String, val labelId: String) {
  NORMAL("normal", "NORMAL", "NORMAL"),
  WARNING("warning", "PERFORMANCE WARNING", "PERINGATAN PERFORMA"),
  CRITICAL("critical", "CRITICAL ALERT", "PERINGATAN KRITIS"),
}

data class StabilityWarningItem(
  val categoryId: String,
  val severity: StabilitySeverity,
  val title: String,
  val subtitle: String,
  val explanation: String,
  val recommendation: String,
  val metricEvidence: String,
)

data class StabilityWatchAssessment(
  val timestampMs: Long,
  val overallSeverity: StabilitySeverity,
  val headline: String,
  val summaryText: String,
  val warnings: List<StabilityWarningItem>,
  val ramAvailMb: Long,
  val ramTotalMb: Long,
  val lowMemoryFlag: Boolean,
  val batteryLevelPercent: Int,
  val batteryTempC: Float,
  val thermalStatusCode: Int,
  val thermalLabel: String,
  val storageFreeBytes: Long,
  val networkSummary: String,
  val recentAbnormalExitsCount: Int,
  val osLimitationNote: String,
)

