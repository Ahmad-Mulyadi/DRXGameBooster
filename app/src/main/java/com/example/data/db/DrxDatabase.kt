package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [
    GameEntity::class,
    GameSessionEntity::class,
    NetworkTestEntity::class,
    GamingNoteEntity::class,
    AnnouncementHistoryEntity::class,
    LagReportEntity::class,
    CrashReportEntity::class,
    HealthCheckEntity::class,
    SessionReportEntity::class,
    GameNotificationEntity::class,
    GameEventReminderEntity::class,
    StabilityWarningEntity::class,
    SessionTimelineEventEntity::class,
    GameProfileAutomationEntity::class,
  ],
  version = 6,
  exportSchema = false,
)
abstract class DrxDatabase : RoomDatabase() {
  abstract fun gameDao(): GameDao

  abstract fun sessionDao(): SessionDao

  abstract fun networkTestDao(): NetworkTestDao

  abstract fun noteDao(): NoteDao

  abstract fun announcementHistoryDao(): AnnouncementHistoryDao

  abstract fun lagReportDao(): LagReportDao

  abstract fun crashReportDao(): CrashReportDao

  abstract fun healthCheckDao(): HealthCheckDao

  abstract fun sessionReportDao(): SessionReportDao

  abstract fun gameNotificationDao(): GameNotificationDao

  abstract fun gameEventReminderDao(): GameEventReminderDao

  abstract fun stabilityWarningDao(): StabilityWarningDao

  abstract fun sessionTimelineEventDao(): SessionTimelineEventDao

  abstract fun gameProfileAutomationDao(): GameProfileAutomationDao

  companion object {
    @Volatile private var INSTANCE: DrxDatabase? = null

    private val MIGRATION_1_2 =
      object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `announcement_history` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `messageId` TEXT NOT NULL,
              `categoryId` TEXT NOT NULL,
              `title` TEXT NOT NULL,
              `body` TEXT NOT NULL,
              `targetRoute` TEXT NOT NULL,
              `deliveredAt` INTEGER NOT NULL
            )
            """.trimIndent()
          )
        }
      }

    private val MIGRATION_2_3 =
      object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `lag_reports` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `timestampMs` INTEGER NOT NULL,
              `overallStatus` TEXT NOT NULL,
              `suspectedIssue` TEXT NOT NULL,
              `severityLabel` TEXT NOT NULL,
              `confidencePercent` INTEGER NOT NULL,
              `evidenceSummary` TEXT NOT NULL,
              `recommendation` TEXT NOT NULL,
              `gamePackageName` TEXT NOT NULL,
              `ramFreeMb` INTEGER NOT NULL,
              `batteryTempC` REAL NOT NULL,
              `thermalStatus` INTEGER NOT NULL,
              `storageFreeBytes` INTEGER NOT NULL,
              `networkLatencyMs` REAL,
              `networkType` TEXT NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `crash_reports` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `gamePackageName` TEXT NOT NULL,
              `gameName` TEXT NOT NULL,
              `gameVersion` TEXT NOT NULL,
              `exitClassification` TEXT NOT NULL,
              `sessionStartMs` INTEGER NOT NULL,
              `sessionEndMs` INTEGER NOT NULL,
              `ramFreeMb` INTEGER NOT NULL,
              `ramTotalMb` INTEGER NOT NULL,
              `storageFreeBytes` INTEGER NOT NULL,
              `batteryLevelPercent` INTEGER NOT NULL,
              `batteryTempC` REAL NOT NULL,
              `networkStatus` TEXT NOT NULL,
              `lastPerformanceSummary` TEXT NOT NULL,
              `diagnosticResult` TEXT NOT NULL,
              `preventionTips` TEXT NOT NULL,
              `importedLogSummary` TEXT NOT NULL,
              `timestampMs` INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `health_checks` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `timestampMs` INTEGER NOT NULL,
              `isFullDiagnostic` INTEGER NOT NULL,
              `overallScoreOrNull` INTEGER,
              `readinessTitle` TEXT NOT NULL,
              `categorySummary` TEXT NOT NULL,
              `recommendations` TEXT NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `session_reports` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `gamePackageName` TEXT NOT NULL,
              `gameName` TEXT NOT NULL,
              `startedAtMs` INTEGER NOT NULL,
              `endedAtMs` INTEGER NOT NULL,
              `durationMs` INTEGER NOT NULL,
              `avgUiFpsOrNull` REAL,
              `batteryStartPercent` INTEGER NOT NULL,
              `batteryEndPercent` INTEGER NOT NULL,
              `peakTempC` REAL NOT NULL,
              `ramAvailEndMb` INTEGER NOT NULL,
              `networkSummary` TEXT NOT NULL,
              `detectedConditions` TEXT NOT NULL,
              `recommendations` TEXT NOT NULL,
              `timestampMs` INTEGER NOT NULL
            )
            """.trimIndent()
          )
        }
      }

    val MIGRATION_3_4 =
      object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `game_notifications` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `notificationKey` TEXT NOT NULL,
              `packageName` TEXT NOT NULL,
              `gameName` TEXT NOT NULL,
              `title` TEXT NOT NULL,
              `text` TEXT NOT NULL,
              `bigText` TEXT NOT NULL,
              `subText` TEXT NOT NULL,
              `channelId` TEXT NOT NULL,
              `groupKey` TEXT NOT NULL,
              `timestampMs` INTEGER NOT NULL,
              `dateKey` TEXT NOT NULL,
              `isRead` INTEGER NOT NULL,
              `isImportant` INTEGER NOT NULL,
              `isFavorite` INTEGER NOT NULL,
              `eventCategory` TEXT NOT NULL,
              `thumbnailPath` TEXT NOT NULL,
              `hasActionableIntent` INTEGER NOT NULL,
              `isActiveInShade` INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `game_event_reminders` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `notificationId` INTEGER NOT NULL,
              `packageName` TEXT NOT NULL,
              `gameName` TEXT NOT NULL,
              `title` TEXT NOT NULL,
              `eventCategory` TEXT NOT NULL,
              `remindAtMs` INTEGER NOT NULL,
              `workRequestId` TEXT NOT NULL,
              `createdAtMs` INTEGER NOT NULL,
              `isCompleted` INTEGER NOT NULL
            )
            """.trimIndent()
          )
        }
      }

    val MIGRATION_4_5 =
      object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `stability_warnings` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `timestampMs` INTEGER NOT NULL,
              `categoryId` TEXT NOT NULL,
              `severity` TEXT NOT NULL,
              `title` TEXT NOT NULL,
              `subtitle` TEXT NOT NULL,
              `explanation` TEXT NOT NULL,
              `recommendation` TEXT NOT NULL,
              `metricEvidence` TEXT NOT NULL,
              `ramAvailMb` INTEGER NOT NULL,
              `batteryTempC` REAL NOT NULL,
              `thermalStatusCode` INTEGER NOT NULL,
              `networkSummary` TEXT NOT NULL
            )
            """.trimIndent()
          )
        }
      }

    val MIGRATION_5_6 =
      object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `session_timeline_events` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `sessionId` TEXT NOT NULL,
              `gamePackageName` TEXT NOT NULL,
              `gameName` TEXT NOT NULL,
              `eventType` TEXT NOT NULL,
              `title` TEXT NOT NULL,
              `detail` TEXT NOT NULL,
              `timestampMs` INTEGER NOT NULL,
              `batteryPercent` INTEGER NOT NULL,
              `batteryTempC` REAL NOT NULL,
              `ramFreeMb` INTEGER NOT NULL,
              `ramUsedPercent` REAL NOT NULL,
              `networkStatus` TEXT NOT NULL,
              `thermalStatusCode` INTEGER NOT NULL
            )
            """.trimIndent()
          )
          db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `game_profile_automations` (
              `packageName` TEXT NOT NULL PRIMARY KEY,
              `appName` TEXT NOT NULL,
              `enabled` INTEGER NOT NULL,
              `gamingProfileId` TEXT NOT NULL,
              `hudLayoutId` TEXT NOT NULL,
              `fpsMonitorEnabled` INTEGER NOT NULL,
              `floatingSidebarEnabled` INTEGER NOT NULL,
              `networkMonitorEnabled` INTEGER NOT NULL,
              `quickToolsEnabled` INTEGER NOT NULL,
              `recorderShortcutEnabled` INTEGER NOT NULL,
              `screenshotShortcutEnabled` INTEGER NOT NULL,
              `sessionTimerEnabled` INTEGER NOT NULL,
              `performanceDisplayModeId` INTEGER NOT NULL,
              `autoApplyOnForeground` INTEGER NOT NULL,
              `updatedAtMs` INTEGER NOT NULL
            )
            """.trimIndent()
          )
        }
      }

    fun getInstance(context: Context): DrxDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                DrxDatabase::class.java,
                "drx_game_turbo_pro.db",
              )
              .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
              )
              .build()
              .also { INSTANCE = it }
        }
    }
  }
}
