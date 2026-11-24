package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
  @Query("SELECT * FROM games ORDER BY isFavorite DESC, lastPlayedAt DESC, appName ASC")
  fun observeAllGames(): Flow<List<GameEntity>>

  @Query("SELECT * FROM games WHERE packageName = :pkg LIMIT 1")
  suspend fun getGame(pkg: String): GameEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertGame(game: GameEntity)

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAllIgnore(games: List<GameEntity>)

  @Update
  suspend fun updateGame(game: GameEntity)

  @Query("DELETE FROM games WHERE packageName = :pkg")
  suspend fun deleteGame(pkg: String)

  @Query("DELETE FROM games")
  suspend fun clearAll()
}

@Dao
interface SessionDao {
  @Query("SELECT * FROM game_sessions ORDER BY startedAt DESC")
  fun observeAllSessions(): Flow<List<GameSessionEntity>>

  @Query("SELECT * FROM game_sessions ORDER BY startedAt DESC LIMIT :limit")
  fun observeRecentSessions(limit: Int): Flow<List<GameSessionEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSession(session: GameSessionEntity): Long

  @Query("DELETE FROM game_sessions")
  suspend fun clearAll()
}

@Dao
interface NetworkTestDao {
  @Query("SELECT * FROM network_tests ORDER BY timestamp DESC LIMIT 50")
  fun observeRecentTests(): Flow<List<NetworkTestEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTest(test: NetworkTestEntity): Long

  @Query("DELETE FROM network_tests")
  suspend fun clearAll()
}

@Dao
interface NoteDao {
  @Query("SELECT * FROM gaming_notes ORDER BY updatedAt DESC")
  fun observeAllNotes(): Flow<List<GamingNoteEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertNote(note: GamingNoteEntity): Long

  @Query("DELETE FROM gaming_notes WHERE id = :id")
  suspend fun deleteNote(id: Long)

  @Query("DELETE FROM gaming_notes")
  suspend fun clearAll()
}

@Dao
interface AnnouncementHistoryDao {
  @Query("SELECT * FROM announcement_history ORDER BY deliveredAt DESC LIMIT 50")
  fun observeRecentAnnouncements(): Flow<List<AnnouncementHistoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAnnouncement(item: AnnouncementHistoryEntity): Long

  @Query("DELETE FROM announcement_history")
  suspend fun clearAll()
}

@Dao
interface LagReportDao {
  @Query("SELECT * FROM lag_reports ORDER BY timestampMs DESC LIMIT 100")
  fun observeAll(): Flow<List<LagReportEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(item: LagReportEntity): Long

  @Query("DELETE FROM lag_reports WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM lag_reports")
  suspend fun clearAll()
}

@Dao
interface CrashReportDao {
  @Query("SELECT * FROM crash_reports ORDER BY timestampMs DESC LIMIT 100")
  fun observeAll(): Flow<List<CrashReportEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(item: CrashReportEntity): Long

  @Query("DELETE FROM crash_reports WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM crash_reports")
  suspend fun clearAll()
}

@Dao
interface HealthCheckDao {
  @Query("SELECT * FROM health_checks ORDER BY timestampMs DESC LIMIT 100")
  fun observeAll(): Flow<List<HealthCheckEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(item: HealthCheckEntity): Long

  @Query("DELETE FROM health_checks WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM health_checks")
  suspend fun clearAll()
}

@Dao
interface SessionReportDao {
  @Query("SELECT * FROM session_reports ORDER BY endedAtMs DESC LIMIT 100")
  fun observeAll(): Flow<List<SessionReportEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(item: SessionReportEntity): Long

  @Query("DELETE FROM session_reports WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM session_reports")
  suspend fun clearAll()
}

@Dao
interface GameNotificationDao {
  @Query("SELECT * FROM game_notifications ORDER BY timestampMs DESC LIMIT 500")
  fun observeAll(): Flow<List<GameNotificationEntity>>

  @Query("SELECT * FROM game_notifications WHERE notificationKey = :key LIMIT 1")
  suspend fun getByNotificationKey(key: String): GameNotificationEntity?

  @Query("SELECT * FROM game_notifications WHERE id = :id LIMIT 1")
  suspend fun getById(id: Long): GameNotificationEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrReplace(item: GameNotificationEntity): Long

  @Query("UPDATE game_notifications SET isRead = :isRead WHERE id = :id")
  suspend fun setRead(id: Long, isRead: Boolean)

  @Query("UPDATE game_notifications SET isRead = 1 WHERE isRead = 0")
  suspend fun markAllRead()

  @Query("UPDATE game_notifications SET isFavorite = :isFavorite WHERE id = :id")
  suspend fun setFavorite(id: Long, isFavorite: Boolean)

  @Query("UPDATE game_notifications SET isImportant = :isImportant WHERE id = :id")
  suspend fun setImportant(id: Long, isImportant: Boolean)

  @Query("UPDATE game_notifications SET isActiveInShade = 0 WHERE notificationKey = :key")
  suspend fun markRemovedFromShade(key: String)

  @Query("DELETE FROM game_notifications WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM game_notifications WHERE packageName = :packageName")
  suspend fun deleteByPackage(packageName: String)

  @Query("DELETE FROM game_notifications")
  suspend fun clearAll()
}

@Dao
interface GameEventReminderDao {
  @Query("SELECT * FROM game_event_reminders ORDER BY remindAtMs ASC")
  fun observeAll(): Flow<List<GameEventReminderEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(item: GameEventReminderEntity): Long

  @Query("UPDATE game_event_reminders SET isCompleted = 1 WHERE id = :id")
  suspend fun markCompleted(id: Long)

  @Query("DELETE FROM game_event_reminders WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM game_event_reminders")
  suspend fun clearAll()
}

@Dao
interface StabilityWarningDao {
  @Query("SELECT * FROM stability_warnings ORDER BY timestampMs DESC LIMIT 200")
  fun observeAll(): Flow<List<StabilityWarningEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(item: StabilityWarningEntity): Long

  @Query("DELETE FROM stability_warnings WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM stability_warnings")
  suspend fun clearAll()
}

@Dao
interface SessionTimelineEventDao {
  @Query("SELECT * FROM session_timeline_events ORDER BY timestampMs DESC LIMIT 500")
  fun observeAll(): Flow<List<SessionTimelineEventEntity>>

  @Query("SELECT * FROM session_timeline_events WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
  suspend fun getBySessionId(sessionId: String): List<SessionTimelineEventEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(item: SessionTimelineEventEntity): Long

  @Query("DELETE FROM session_timeline_events WHERE sessionId = :sessionId")
  suspend fun deleteBySessionId(sessionId: String)

  @Query("DELETE FROM session_timeline_events")
  suspend fun clearAll()
}

@Dao
interface GameProfileAutomationDao {
  @Query("SELECT * FROM game_profile_automations ORDER BY updatedAtMs DESC")
  fun observeAll(): Flow<List<GameProfileAutomationEntity>>

  @Query("SELECT * FROM game_profile_automations WHERE packageName = :packageName LIMIT 1")
  suspend fun getByPackage(packageName: String): GameProfileAutomationEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(item: GameProfileAutomationEntity)

  @Query("DELETE FROM game_profile_automations WHERE packageName = :packageName")
  suspend fun deleteByPackage(packageName: String)

  @Query("DELETE FROM game_profile_automations")
  suspend fun clearAll()
}
