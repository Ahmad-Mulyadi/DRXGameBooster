package com.example.system

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.data.db.DrxDatabase
import com.example.data.db.DrxRepository
import com.example.data.db.GameNotificationEntity
import com.example.data.prefs.DrxPreferences
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object DrxNotificationIntentCache {
  private const val MAX_INTENTS = 64
  private val intentMap = ConcurrentHashMap<String, PendingIntent>()
  private val insertionOrder = ArrayDeque<String>()

  @Synchronized
  fun put(notificationKey: String, pendingIntent: PendingIntent?) {
    if (pendingIntent == null) return
    if (!intentMap.containsKey(notificationKey)) {
      insertionOrder.addLast(notificationKey)
      while (insertionOrder.size > MAX_INTENTS) {
        val oldest = insertionOrder.removeFirst()
        intentMap.remove(oldest)
      }
    }
    intentMap[notificationKey] = pendingIntent
  }

  fun get(notificationKey: String): PendingIntent? = intentMap[notificationKey]

  @Synchronized
  fun remove(notificationKey: String) {
    intentMap.remove(notificationKey)
    insertionOrder.remove(notificationKey)
  }

  @Synchronized
  fun clear() {
    intentMap.clear()
    insertionOrder.clear()
  }
}

class DrxGameNotificationListenerService : NotificationListenerService() {

  companion object {
    fun isNotificationAccessGranted(context: Context): Boolean {
      return try {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        if (flat.isBlank()) return false
        val target = ComponentName(context, DrxGameNotificationListenerService::class.java)
        val flatShort = target.flattenToShortString()
        val flatFull = target.flattenToString()
        flat.split(":").any { token ->
          val trimmed = token.trim()
          trimmed.equals(flatShort, ignoreCase = true) || trimmed.equals(flatFull, ignoreCase = true)
        }
      } catch (_: Throwable) {
        false
      }
    }

    fun openNotificationAccessSettings(context: Context): Boolean {
      return try {
        val intent =
          Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        context.startActivity(intent)
        true
      } catch (_: Throwable) {
        false
      }
    }
  }

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    val statusBar = sbn ?: return
    val notification = statusBar.notification ?: return
    val pkg = statusBar.packageName ?: return

    // Skip ongoing, media, and group summary container notifications
    if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
    if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
    if (pkg == applicationContext.packageName) return

    serviceScope.launch {
      try {
        val prefs = DrxPreferences(applicationContext).prefsFlow.first()
        if (!prefs.gameNotificationViewerEnabled) return@launch

        val db = DrxDatabase.getInstance(applicationContext)
        val repository = DrxRepository(db)
        val trackedGames = repository.allGames.first().map { it.packageName }.toSet()

        val pm = applicationContext.packageManager
        val appInfo =
          runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
              @Suppress("DEPRECATION")
              pm.getApplicationInfo(pkg, 0)
            }
          }.getOrNull()

        val isGame =
          GameNotificationEngine.isGamePackage(
            packageName = pkg,
            ownPackageName = applicationContext.packageName,
            appInfo = appInfo,
            trackedGamePackages = trackedGames,
            customIncludedPackages = prefs.customNotificationGamePackages,
            excludedPackages = prefs.excludedNotificationPackages,
          )
        if (!isGame) return@launch

        val extras = notification.extras
        val rawTitle = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val rawText = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val rawBigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val rawSubText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()

        if (rawTitle.isBlank() && rawText.isBlank() && rawBigText.isBlank()) return@launch
        if (GameNotificationEngine.shouldSkipSensitiveNotification(rawTitle, rawText, rawBigText)) return@launch

        val safeTitle = GameNotificationEngine.sanitizeNotificationContent(rawTitle).ifBlank { "Game Notification" }
        val safeText = GameNotificationEngine.sanitizeNotificationContent(rawText)
        val safeBigText = GameNotificationEngine.sanitizeNotificationContent(rawBigText)
        val safeSubText = GameNotificationEngine.sanitizeNotificationContent(rawSubText)

        val gameName =
          appInfo?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
            ?: pkg.substringAfterLast('.')

        val postTime = statusBar.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()
        val dedupeKey = "${statusBar.key}:$postTime"
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) notification.channelId.orEmpty() else ""
        val groupKey = notification.group.orEmpty()

        val category = GameNotificationEngine.classifyEventCategory(safeTitle, safeText, safeBigText)
        val thumbPath = saveThumbnailIfPresent(notification, dedupeKey)

        val contentIntent = notification.contentIntent
        if (contentIntent != null) {
          DrxNotificationIntentCache.put(dedupeKey, contentIntent)
        }

        val entity =
          GameNotificationEntity(
            notificationKey = dedupeKey,
            packageName = pkg,
            gameName = gameName,
            title = safeTitle,
            text = safeText,
            bigText = safeBigText,
            subText = safeSubText,
            channelId = channelId,
            groupKey = groupKey,
            timestampMs = postTime,
            dateKey = GameNotificationEngine.formatDateKey(postTime),
            isRead = false,
            isImportant = category.name in setOf("TOURNAMENT", "MAINTENANCE", "REWARD"),
            isFavorite = false,
            eventCategory = category.id,
            thumbnailPath = thumbPath,
            hasActionableIntent = contentIntent != null,
            isActiveInShade = true,
          )
        repository.upsertGameNotification(entity)
      } catch (_: Throwable) {
      }
    }
  }

  override fun onNotificationRemoved(sbn: StatusBarNotification?) {
    val statusBar = sbn ?: return
    val dedupeKey = "${statusBar.key}:${statusBar.postTime}"
    DrxNotificationIntentCache.remove(dedupeKey)
    serviceScope.launch {
      try {
        val repository = DrxRepository(DrxDatabase.getInstance(applicationContext))
        repository.markGameNotificationRemovedFromShade(dedupeKey)
      } catch (_: Throwable) {
      }
    }
  }

  private fun saveThumbnailIfPresent(notification: Notification, dedupeKey: String): String {
    return try {
      val extras = notification.extras ?: return ""
      @Suppress("DEPRECATION")
      val picBmp =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          extras.getParcelable(Notification.EXTRA_PICTURE, Bitmap::class.java)
        } else {
          extras.getParcelable(Notification.EXTRA_PICTURE)
        }
      val bmp: Bitmap? =
        picBmp
          ?: run {
            val largeIconObj = notification.getLargeIcon()
            val drw = largeIconObj?.loadDrawable(applicationContext)
            (drw as? BitmapDrawable)?.bitmap
          }
      if (bmp == null || bmp.isRecycled || bmp.width <= 0 || bmp.height <= 0) return ""

      val dir = File(applicationContext.filesDir, "notif_thumbs")
      if (!dir.exists()) dir.mkdirs()
      pruneThumbnailsIfNeeded(dir)

      val fileName = "thumb_${dedupeKey.hashCode().toUInt()}.jpg"
      val outFile = File(dir, fileName)
      outFile.outputStream().buffered().use { out ->
        bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
      }
      outFile.absolutePath
    } catch (_: Throwable) {
      ""
    }
  }

  private fun pruneThumbnailsIfNeeded(dir: File) {
    val files = dir.listFiles() ?: return
    if (files.size >= 80) {
      files.sortedBy { it.lastModified() }.take(files.size - 60).forEach { it.delete() }
    }
  }

  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }
}
