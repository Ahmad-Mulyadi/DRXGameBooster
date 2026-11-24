package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.DrxDatabase
import com.example.data.db.DrxRepository
import com.example.data.db.GameEntity
import com.example.data.db.GameEventReminderEntity
import com.example.data.db.GameNotificationEntity
import com.example.data.model.GameEventCategory
import com.example.data.model.MediaGalleryItem
import com.example.data.model.NotificationDateRangeFilter
import com.example.data.model.NotificationGroupTab
import com.example.data.model.NotificationInsightsSummary
import com.example.data.model.NotificationReadFilter
import com.example.data.model.NotificationSortOrder
import com.example.data.prefs.DrxPreferences
import com.example.data.prefs.DrxUserPrefs
import com.example.system.DrxGameNotificationListenerService
import com.example.system.DrxMediaStorageManager
import com.example.system.DrxNotificationIntentCache
import com.example.system.EventReminderWorker
import com.example.system.GameNotificationEngine
import com.example.system.SystemTweaksManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotificationFilterUiState(
  val selectedTab: NotificationGroupTab = NotificationGroupTab.BY_DATE,
  val searchQuery: String = "",
  val selectedPackage: String? = null,
  val selectedDateKey: String? = null,
  val dateRangeFilter: NotificationDateRangeFilter = NotificationDateRangeFilter.ALL,
  val selectedCategory: GameEventCategory? = null,
  val readFilter: NotificationReadFilter = NotificationReadFilter.ALL,
  val sortOrder: NotificationSortOrder = NotificationSortOrder.NEWEST,
)

class DrxNotificationsViewModel(application: Application) : AndroidViewModel(application) {

  private val appContext = application.applicationContext
  private val database = DrxDatabase.getInstance(appContext)
  private val repository = DrxRepository(database)
  private val prefs = DrxPreferences(appContext)
  val tweaksManager = SystemTweaksManager(appContext)

  val userPrefs: StateFlow<DrxUserPrefs> =
    prefs.prefsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, DrxUserPrefs())

  val allNotifications: StateFlow<List<GameNotificationEntity>> =
    repository.gameNotifications.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList(),
    )

  val allReminders: StateFlow<List<GameEventReminderEntity>> =
    repository.gameEventReminders.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList(),
    )

  val trackedGames: StateFlow<List<GameEntity>> =
    repository.allGames.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _filterState = MutableStateFlow(NotificationFilterUiState())
  val filterState: StateFlow<NotificationFilterUiState> = _filterState.asStateFlow()

  val filteredNotifications: StateFlow<List<GameNotificationEntity>> =
    combine(allNotifications, _filterState) { list, filter ->
      GameNotificationEngine.filterAndSort(
        items = list,
        query = filter.searchQuery,
        selectedPackage = filter.selectedPackage,
        selectedDateKey = filter.selectedDateKey,
        dateRangeFilter = filter.dateRangeFilter,
        selectedCategory = filter.selectedCategory,
        readFilter = filter.readFilter,
        sortOrder = filter.sortOrder,
      )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val insightsSummary: StateFlow<NotificationInsightsSummary> =
    allNotifications
      .map { list -> GameNotificationEngine.computeInsights(list) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationInsightsSummary())

  private val _notificationAccessGranted =
    MutableStateFlow(DrxGameNotificationListenerService.isNotificationAccessGranted(appContext))
  val notificationAccessGranted: StateFlow<Boolean> = _notificationAccessGranted.asStateFlow()

  private val _recentMedia = MutableStateFlow<List<MediaGalleryItem>>(emptyList())
  val recentMedia: StateFlow<List<MediaGalleryItem>> = _recentMedia.asStateFlow()

  private val _actionMessage = MutableStateFlow<String?>(null)
  val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

  fun refreshNotificationAccess() {
    _notificationAccessGranted.value =
      DrxGameNotificationListenerService.isNotificationAccessGranted(appContext)
  }

  fun openNotificationListenerSettings() {
    if (!DrxGameNotificationListenerService.openNotificationAccessSettings(appContext)) {
      runCatching {
        appContext.startActivity(
          Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
      }
    }
  }

  fun refreshRecentMedia() {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        _recentMedia.value = DrxMediaStorageManager.loadGalleryItems(appContext).take(12)
      }
    }
  }

  fun setTab(tab: NotificationGroupTab) {
    _filterState.value = _filterState.value.copy(selectedTab = tab)
  }

  fun setSearchQuery(query: String) {
    _filterState.value = _filterState.value.copy(searchQuery = query)
  }

  fun setSelectedPackage(pkg: String?) {
    _filterState.value = _filterState.value.copy(selectedPackage = pkg)
  }

  fun setSelectedDateKey(dateKey: String?) {
    _filterState.value = _filterState.value.copy(selectedDateKey = dateKey)
  }

  fun setDateRangeFilter(range: NotificationDateRangeFilter) {
    _filterState.value = _filterState.value.copy(dateRangeFilter = range)
  }

  fun setSelectedCategory(category: GameEventCategory?) {
    _filterState.value = _filterState.value.copy(selectedCategory = category)
  }

  fun setReadFilter(filter: NotificationReadFilter) {
    _filterState.value = _filterState.value.copy(readFilter = filter)
  }

  fun setSortOrder(order: NotificationSortOrder) {
    _filterState.value = _filterState.value.copy(sortOrder = order)
  }

  fun resetFilters() {
    val tab = _filterState.value.selectedTab
    _filterState.value = NotificationFilterUiState(selectedTab = tab)
  }

  fun setViewerEnabled(enabled: Boolean) {
    viewModelScope.launch { prefs.setGameNotificationViewerEnabled(enabled) }
  }

  fun markRead(id: Long, isRead: Boolean) {
    viewModelScope.launch(Dispatchers.IO) { repository.setGameNotificationRead(id, isRead) }
  }

  fun markAllRead() {
    viewModelScope.launch(Dispatchers.IO) { repository.markAllGameNotificationsRead() }
  }

  fun toggleFavorite(item: GameNotificationEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.setGameNotificationFavorite(item.id, !item.isFavorite)
    }
  }

  fun toggleImportant(item: GameNotificationEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      repository.setGameNotificationImportant(item.id, !item.isImportant)
    }
  }

  fun deleteNotification(item: GameNotificationEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      if (item.thumbnailPath.isNotBlank()) {
        runCatching { File(item.thumbnailPath).delete() }
      }
      DrxNotificationIntentCache.remove(item.notificationKey)
      repository.deleteGameNotification(item.id)
    }
  }

  fun clearAllNotificationHistory() {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        File(appContext.filesDir, "notif_thumbs").listFiles()?.forEach { it.delete() }
      }
      DrxNotificationIntentCache.clear()
      repository.clearGameNotifications()
    }
  }

  fun excludePackage(packageName: String, removeExistingHistory: Boolean = false) {
    viewModelScope.launch(Dispatchers.IO) {
      prefs.setPackageExcludedFromNotifications(packageName, excluded = true)
      if (removeExistingHistory) {
        repository.deleteGameNotificationsByPackage(packageName)
      }
    }
  }

  fun unexcludePackage(packageName: String) {
    viewModelScope.launch(Dispatchers.IO) {
      prefs.setPackageExcludedFromNotifications(packageName, excluded = false)
    }
  }

  fun addCustomGamePackage(packageName: String) {
    viewModelScope.launch(Dispatchers.IO) {
      prefs.setCustomNotificationGamePackage(packageName, included = true)
    }
  }

  fun removeCustomGamePackage(packageName: String) {
    viewModelScope.launch(Dispatchers.IO) {
      prefs.setCustomNotificationGamePackage(packageName, included = false)
    }
  }

  fun openGameApp(packageName: String): Boolean = tweaksManager.launchApp(packageName)

  fun openOriginalNotificationActivity(item: GameNotificationEntity): Boolean {
    val cachedIntent = DrxNotificationIntentCache.get(item.notificationKey) ?: return false
    return try {
      cachedIntent.send()
      true
    } catch (_: Throwable) {
      DrxNotificationIntentCache.remove(item.notificationKey)
      false
    }
  }

  fun buildShareTextIntent(item: GameNotificationEntity): Intent {
    val fullText =
      buildString {
        append(item.gameName)
        append(" — ")
        append(item.title)
        val body = item.bigText.ifBlank { item.text }
        if (body.isNotBlank()) {
          append("\n\n")
          append(body)
        }
      }
    return Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_SUBJECT, "${item.gameName}: ${item.title}")
      putExtra(Intent.EXTRA_TEXT, fullText)
    }
  }

  fun scheduleEventReminder(
    notificationId: Long,
    packageName: String,
    gameName: String,
    title: String,
    category: String,
    delayMinutes: Int,
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      val delayMs = delayMinutes.coerceAtLeast(1) * 60_000L
      val remindAt = System.currentTimeMillis() + delayMs
      val initial =
        GameEventReminderEntity(
          notificationId = notificationId,
          packageName = packageName,
          gameName = gameName,
          title = title,
          eventCategory = category,
          remindAtMs = remindAt,
          workRequestId = "",
        )
      val insertedId = repository.insertEventReminder(initial)
      val savedEntity = initial.copy(id = insertedId)
      val workId = EventReminderWorker.scheduleReminder(appContext, savedEntity, delayMs)
      repository.insertEventReminder(savedEntity.copy(workRequestId = workId))
      _actionMessage.value = "$gameName ($delayMinutes min)"
    }
  }

  fun cancelAndDeleteReminder(reminder: GameEventReminderEntity) {
    viewModelScope.launch(Dispatchers.IO) {
      EventReminderWorker.cancelReminder(appContext, reminder.workRequestId)
      repository.deleteEventReminder(reminder.id)
    }
  }

  fun clearActionMessage() {
    _actionMessage.value = null
  }

  fun openMediaFile(item: MediaGalleryItem) {
    val mime = if (item.type.name == "VIDEO") "video/mp4" else "image/png"
    runCatching {
      val intent =
        Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(item.uriString.toUri(), mime)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      appContext.startActivity(intent)
    }
  }
}
