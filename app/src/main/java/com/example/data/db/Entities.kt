package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class GameEntity(
  @PrimaryKey val packageName: String,
  val appName: String,
  val category: String,
  val isFavorite: Boolean = false,
  val profileName: String = "balanced",
  val lastPlayedAt: Long = 0L,
  val playCount: Int = 0,
  val totalPlayTimeMs: Long = 0L,
  val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "game_sessions")
data class GameSessionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val gamePackageName: String,
  val gameName: String,
  val startedAt: Long,
  val endedAt: Long,
  val durationMs: Long,
  val profileUsed: String,
  val batteryStartPercent: Int,
  val batteryEndPercent: Int,
  val peakTempC: Float,
)

@Entity(tableName = "network_tests")
data class NetworkTestEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val timestamp: Long = System.currentTimeMillis(),
  val host: String,
  val pingMs: Float,
  val jitterMs: Float,
  val packetLossPercent: Float,
  val downloadMbps: Float,
  val stabilityScore: Int,
  val networkType: String,
)

@Entity(tableName = "gaming_notes")
data class GamingNoteEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val title: String,
  val content: String,
  val gameTag: String = "",
  val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "announcement_history")
data class AnnouncementHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val messageId: String,
  val categoryId: String,
  val title: String,
  val body: String,
  val targetRoute: String,
  val deliveredAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "lag_reports")
data class LagReportEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val timestampMs: Long = System.currentTimeMillis(),
  val overallStatus: String,
  val suspectedIssue: String,
  val severityLabel: String,
  val confidencePercent: Int,
  val evidenceSummary: String,
  val recommendation: String,
  val gamePackageName: String = "",
  val ramFreeMb: Long = 0L,
  val batteryTempC: Float = 0f,
  val thermalStatus: Int = 0,
  val storageFreeBytes: Long = 0L,
  val networkLatencyMs: Float? = null,
  val networkType: String = "",
)

@Entity(tableName = "crash_reports")
data class CrashReportEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val gamePackageName: String,
  val gameName: String,
  val gameVersion: String,
  val exitClassification: String,
  val sessionStartMs: Long,
  val sessionEndMs: Long,
  val ramFreeMb: Long,
  val ramTotalMb: Long,
  val storageFreeBytes: Long,
  val batteryLevelPercent: Int,
  val batteryTempC: Float,
  val networkStatus: String,
  val lastPerformanceSummary: String,
  val diagnosticResult: String,
  val preventionTips: String,
  val importedLogSummary: String = "",
  val timestampMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "health_checks")
data class HealthCheckEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val timestampMs: Long = System.currentTimeMillis(),
  val isFullDiagnostic: Boolean,
  val overallScoreOrNull: Int?,
  val readinessTitle: String,
  val categorySummary: String,
  val recommendations: String,
)

@Entity(tableName = "session_reports")
data class SessionReportEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val gamePackageName: String,
  val gameName: String,
  val startedAtMs: Long,
  val endedAtMs: Long,
  val durationMs: Long,
  val avgUiFpsOrNull: Float?,
  val batteryStartPercent: Int,
  val batteryEndPercent: Int,
  val peakTempC: Float,
  val ramAvailEndMb: Long,
  val networkSummary: String,
  val detectedConditions: String,
  val recommendations: String,
  val timestampMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "game_notifications")
data class GameNotificationEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val notificationKey: String,
  val packageName: String,
  val gameName: String,
  val title: String,
  val text: String,
  val bigText: String = "",
  val subText: String = "",
  val channelId: String = "",
  val groupKey: String = "",
  val timestampMs: Long,
  val dateKey: String,
  val isRead: Boolean = false,
  val isImportant: Boolean = false,
  val isFavorite: Boolean = false,
  val eventCategory: String = "GENERAL",
  val thumbnailPath: String = "",
  val hasActionableIntent: Boolean = false,
  val isActiveInShade: Boolean = true,
)

@Entity(tableName = "game_event_reminders")
data class GameEventReminderEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val notificationId: Long,
  val packageName: String,
  val gameName: String,
  val title: String,
  val eventCategory: String,
  val remindAtMs: Long,
  val workRequestId: String = "",
  val createdAtMs: Long = System.currentTimeMillis(),
  val isCompleted: Boolean = false,
)

@Entity(tableName = "stability_warnings")
data class StabilityWarningEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val timestampMs: Long = System.currentTimeMillis(),
  val categoryId: String,
  val severity: String,
  val title: String,
  val subtitle: String,
  val explanation: String,
  val recommendation: String,
  val metricEvidence: String,
  val ramAvailMb: Long,
  val batteryTempC: Float,
  val thermalStatusCode: Int,
  val networkSummary: String,
)

@Entity(tableName = "session_timeline_events")
data class SessionTimelineEventEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val sessionId: String,
  val gamePackageName: String,
  val gameName: String,
  val eventType: String, // LAUNCHED, MONITORING_STARTED, TEMP_INCREASED, NETWORK_CHANGED, THERMAL_WARNING, BATTERY_DROP, SESSION_ENDED
  val title: String,
  val detail: String,
  val timestampMs: Long,
  val batteryPercent: Int,
  val batteryTempC: Float,
  val ramFreeMb: Long,
  val ramUsedPercent: Float,
  val networkStatus: String,
  val thermalStatusCode: Int,
)

@Entity(tableName = "game_profile_automations")
data class GameProfileAutomationEntity(
  @PrimaryKey val packageName: String,
  val appName: String,
  val enabled: Boolean = true,
  val gamingProfileId: String = "balanced",
  val hudLayoutId: String = "standard",
  val fpsMonitorEnabled: Boolean = true,
  val floatingSidebarEnabled: Boolean = true,
  val networkMonitorEnabled: Boolean = true,
  val quickToolsEnabled: Boolean = true,
  val recorderShortcutEnabled: Boolean = true,
  val screenshotShortcutEnabled: Boolean = true,
  val sessionTimerEnabled: Boolean = true,
  val performanceDisplayModeId: Int = 0,
  val autoApplyOnForeground: Boolean = true,
  val updatedAtMs: Long = System.currentTimeMillis(),
)
