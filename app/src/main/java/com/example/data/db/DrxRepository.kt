package com.example.data.db

import com.example.data.model.GamingProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class DrxRepository(private val database: DrxDatabase) {
  private val gameDao = database.gameDao()
  private val sessionDao = database.sessionDao()
  private val networkTestDao = database.networkTestDao()
  private val noteDao = database.noteDao()
  private val announcementHistoryDao = database.announcementHistoryDao()
  private val lagReportDao = database.lagReportDao()
  private val crashReportDao = database.crashReportDao()
  private val healthCheckDao = database.healthCheckDao()
  private val sessionReportDao = database.sessionReportDao()
  private val gameNotificationDao = database.gameNotificationDao()
  private val gameEventReminderDao = database.gameEventReminderDao()
  private val stabilityWarningDao = database.stabilityWarningDao()
  private val sessionTimelineEventDao = database.sessionTimelineEventDao()
  private val gameProfileAutomationDao = database.gameProfileAutomationDao()

  val allGames: Flow<List<GameEntity>> = gameDao.observeAllGames()
  val allSessions: Flow<List<GameSessionEntity>> = sessionDao.observeAllSessions()
  val recentSessions: Flow<List<GameSessionEntity>> = sessionDao.observeRecentSessions(10)
  val recentNetworkTests: Flow<List<NetworkTestEntity>> = networkTestDao.observeRecentTests()
  val allNotes: Flow<List<GamingNoteEntity>> = noteDao.observeAllNotes()
  val announcementHistory: Flow<List<AnnouncementHistoryEntity>> =
    announcementHistoryDao.observeRecentAnnouncements()
  val lagReports: Flow<List<LagReportEntity>> = lagReportDao.observeAll()
  val crashReports: Flow<List<CrashReportEntity>> = crashReportDao.observeAll()
  val healthChecks: Flow<List<HealthCheckEntity>> = healthCheckDao.observeAll()
  val sessionReports: Flow<List<SessionReportEntity>> = sessionReportDao.observeAll()
  val gameNotifications: Flow<List<GameNotificationEntity>> = gameNotificationDao.observeAll()
  val gameEventReminders: Flow<List<GameEventReminderEntity>> = gameEventReminderDao.observeAll()
  val stabilityWarnings: Flow<List<StabilityWarningEntity>> = stabilityWarningDao.observeAll()
  val sessionTimelineEvents: Flow<List<SessionTimelineEventEntity>> = sessionTimelineEventDao.observeAll()
  val gameProfileAutomations: Flow<List<GameProfileAutomationEntity>> = gameProfileAutomationDao.observeAll()

  suspend fun getNotesSnapshot(): List<GamingNoteEntity> = allNotes.first()

  suspend fun seedGamesIfAbsent(games: List<GameEntity>) {
    gameDao.insertAllIgnore(games)
  }

  suspend fun upsertGame(game: GameEntity) {
    gameDao.upsertGame(game)
  }

  suspend fun toggleFavorite(packageName: String) {
    val existing = gameDao.getGame(packageName) ?: return
    gameDao.updateGame(existing.copy(isFavorite = !existing.isFavorite))
  }

  suspend fun updateGameProfile(packageName: String, profile: GamingProfile) {
    val existing = gameDao.getGame(packageName) ?: return
    gameDao.updateGame(existing.copy(profileName = profile.id))
  }

  suspend fun updateGameCategory(packageName: String, categoryId: String) {
    val existing = gameDao.getGame(packageName) ?: return
    gameDao.updateGame(existing.copy(category = categoryId))
  }

  suspend fun deleteGame(packageName: String) {
    gameDao.deleteGame(packageName)
  }

  suspend fun recordSession(session: GameSessionEntity) {
    sessionDao.insertSession(session)
    val game = gameDao.getGame(session.gamePackageName)
    if (game != null) {
      gameDao.updateGame(
        game.copy(
          lastPlayedAt = session.endedAt,
          playCount = game.playCount + 1,
          totalPlayTimeMs = game.totalPlayTimeMs + session.durationMs,
        )
      )
    }
  }

  suspend fun insertNetworkTest(test: NetworkTestEntity) {
    networkTestDao.insertTest(test)
  }

  suspend fun upsertNote(note: GamingNoteEntity) {
    noteDao.upsertNote(note)
  }

  suspend fun deleteNote(id: Long) {
    noteDao.deleteNote(id)
  }

  suspend fun clearHistoryAndStats() {
    sessionDao.clearAll()
    networkTestDao.clearAll()
    lagReportDao.clearAll()
    crashReportDao.clearAll()
    healthCheckDao.clearAll()
    sessionReportDao.clearAll()
    stabilityWarningDao.clearAll()
    sessionTimelineEventDao.clearAll()
  }

  suspend fun insertAnnouncement(item: AnnouncementHistoryEntity) {
    announcementHistoryDao.insertAnnouncement(item)
  }

  suspend fun clearAnnouncements() {
    announcementHistoryDao.clearAll()
  }

  suspend fun insertLagReport(item: LagReportEntity): Long = lagReportDao.insert(item)

  suspend fun deleteLagReport(id: Long) {
    lagReportDao.deleteById(id)
  }

  suspend fun insertCrashReport(item: CrashReportEntity): Long = crashReportDao.insert(item)

  suspend fun deleteCrashReport(id: Long) {
    crashReportDao.deleteById(id)
  }

  suspend fun insertHealthCheck(item: HealthCheckEntity): Long = healthCheckDao.insert(item)

  suspend fun deleteHealthCheck(id: Long) {
    healthCheckDao.deleteById(id)
  }

  suspend fun insertSessionReport(item: SessionReportEntity): Long = sessionReportDao.insert(item)

  suspend fun deleteSessionReport(id: Long) {
    sessionReportDao.deleteById(id)
  }

  suspend fun upsertGameNotification(item: GameNotificationEntity): Long {
    val existing = gameNotificationDao.getByNotificationKey(item.notificationKey)
    val merged =
      if (existing != null) {
        item.copy(
          id = existing.id,
          isRead = existing.isRead,
          isImportant = existing.isImportant,
          isFavorite = existing.isFavorite,
        )
      } else {
        item
      }
    return gameNotificationDao.insertOrReplace(merged)
  }

  suspend fun getGameNotificationById(id: Long): GameNotificationEntity? =
    gameNotificationDao.getById(id)

  suspend fun setGameNotificationRead(id: Long, isRead: Boolean) {
    gameNotificationDao.setRead(id, isRead)
  }

  suspend fun markAllGameNotificationsRead() {
    gameNotificationDao.markAllRead()
  }

  suspend fun setGameNotificationFavorite(id: Long, isFavorite: Boolean) {
    gameNotificationDao.setFavorite(id, isFavorite)
  }

  suspend fun setGameNotificationImportant(id: Long, isImportant: Boolean) {
    gameNotificationDao.setImportant(id, isImportant)
  }

  suspend fun markGameNotificationRemovedFromShade(key: String) {
    gameNotificationDao.markRemovedFromShade(key)
  }

  suspend fun deleteGameNotification(id: Long) {
    gameNotificationDao.deleteById(id)
  }

  suspend fun deleteGameNotificationsByPackage(packageName: String) {
    gameNotificationDao.deleteByPackage(packageName)
  }

  suspend fun clearGameNotifications() {
    gameNotificationDao.clearAll()
  }

  suspend fun insertEventReminder(item: GameEventReminderEntity): Long =
    gameEventReminderDao.insert(item)

  suspend fun markEventReminderCompleted(id: Long) {
    gameEventReminderDao.markCompleted(id)
  }

  suspend fun deleteEventReminder(id: Long) {
    gameEventReminderDao.deleteById(id)
  }

  suspend fun clearEventReminders() {
    gameEventReminderDao.clearAll()
  }

  suspend fun insertStabilityWarning(item: StabilityWarningEntity): Long =
    stabilityWarningDao.insert(item)

  suspend fun deleteStabilityWarning(id: Long) {
    stabilityWarningDao.deleteById(id)
  }

  suspend fun clearStabilityWarnings() {
    stabilityWarningDao.clearAll()
  }

  suspend fun insertSessionTimelineEvent(item: SessionTimelineEventEntity): Long =
    sessionTimelineEventDao.insert(item)

  suspend fun getSessionTimelineBySessionId(sessionId: String): List<SessionTimelineEventEntity> =
    sessionTimelineEventDao.getBySessionId(sessionId)

  suspend fun deleteSessionTimelineSession(sessionId: String) {
    sessionTimelineEventDao.deleteBySessionId(sessionId)
  }

  suspend fun clearSessionTimelineEvents() {
    sessionTimelineEventDao.clearAll()
  }

  suspend fun getGameProfileAutomation(packageName: String): GameProfileAutomationEntity? =
    gameProfileAutomationDao.getByPackage(packageName)

  suspend fun upsertGameProfileAutomation(item: GameProfileAutomationEntity) {
    gameProfileAutomationDao.upsert(item)
  }

  suspend fun deleteGameProfileAutomation(packageName: String) {
    gameProfileAutomationDao.deleteByPackage(packageName)
  }

  suspend fun clearAllData() {
    gameDao.clearAll()
    sessionDao.clearAll()
    networkTestDao.clearAll()
    noteDao.clearAll()
    announcementHistoryDao.clearAll()
    lagReportDao.clearAll()
    crashReportDao.clearAll()
    healthCheckDao.clearAll()
    sessionReportDao.clearAll()
    gameNotificationDao.clearAll()
    gameEventReminderDao.clearAll()
    stabilityWarningDao.clearAll()
    sessionTimelineEventDao.clearAll()
    gameProfileAutomationDao.clearAll()
  }
}
