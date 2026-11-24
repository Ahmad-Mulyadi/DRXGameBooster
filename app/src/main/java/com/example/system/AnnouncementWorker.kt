package com.example.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.db.AnnouncementHistoryEntity
import com.example.data.db.DrxDatabase
import com.example.data.db.DrxRepository
import com.example.data.model.AnnouncementMessage
import com.example.data.prefs.DrxPreferences
import com.example.data.prefs.DrxUserPrefs
import java.util.Calendar
import kotlinx.coroutines.flow.first

object AnnouncementNotifier {

  fun postNotification(
    context: Context,
    message: AnnouncementMessage,
    prefs: DrxUserPrefs,
  ): Boolean {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted =
          ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return false
      }

      val nm = context.getSystemService(NotificationManager::class.java) ?: return false
      val channelId = resolveChannelId(prefs.announcementSoundEnabled, prefs.announcementVibrationEnabled)
      ensureNotificationChannel(nm, channelId, prefs.announcementSoundEnabled, prefs.announcementVibrationEnabled)

      val launchIntent =
        Intent(context, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
          putExtra("open_route", message.targetRoute)
        }
      val reqCode = (message.id.hashCode() and 0x7FFFFFFF)
      val pendingIntent =
        PendingIntent.getActivity(
          context,
          reqCode,
          launchIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

      val builder =
        NotificationCompat.Builder(context, channelId)
          .setSmallIcon(R.drawable.ic_drx_notification)
          .setContentTitle(message.category.notificationTitle)
          .setContentText(message.body)
          .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
          .setContentIntent(pendingIntent)
          .setAutoCancel(true)
          .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
          .setPriority(NotificationCompat.PRIORITY_DEFAULT)

      if (!prefs.announcementSoundEnabled) {
        builder.setSilent(true)
      }

      val notificationId = 8400 + (System.currentTimeMillis() % 500L).toInt()
      nm.notify(notificationId, builder.build())
      true
    } catch (_: Throwable) {
      false
    }
  }

  private fun resolveChannelId(soundEnabled: Boolean, vibrationEnabled: Boolean): String =
    when {
      soundEnabled && vibrationEnabled -> "drx_daily_messages_sv"
      soundEnabled -> "drx_daily_messages_sound"
      vibrationEnabled -> "drx_daily_messages_vib"
      else -> "drx_daily_messages_silent"
    }

  private fun ensureNotificationChannel(
    nm: NotificationManager,
    channelId: String,
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
  ) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val importance =
      if (soundEnabled || vibrationEnabled) NotificationManager.IMPORTANCE_DEFAULT
      else NotificationManager.IMPORTANCE_LOW
    val channel =
      NotificationChannel(channelId, "DRX Daily Messages", importance).apply {
        description = "DRX Daily Messages: gaming tips, feature discovery, motivation & companion updates"
        enableVibration(vibrationEnabled)
        if (!soundEnabled) {
          setSound(null, null)
        } else {
          setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
        }
      }
    nm.createNotificationChannel(channel)
  }
}

class AnnouncementWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    return try {
      val context = applicationContext
      val prefsStore = DrxPreferences(context)
      val prefs = prefsStore.prefsFlow.first()
      val scheduler = AnnouncementScheduler(context)

      if (!prefs.announcementsEnabled || !scheduler.hasNotificationPermission()) {
        return Result.success()
      }

      val isReplanOnly = inputData.getBoolean(AnnouncementScheduler.KEY_IS_REPLAN_ONLY, false)
      if (isReplanOnly) {
        scheduler.syncSchedule(forceReschedule = true)
        return Result.success()
      }

      val cal = Calendar.getInstance()
      val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
      if (AnnouncementSchedulePlanner.isMinuteInQuietHours(minuteOfDay, prefs.quietHoursStartHour, prefs.quietHoursEndHour)) {
        return Result.success()
      }

      val picked =
        AnnouncementSchedulePlanner.pickNonDuplicateMessage(
          enabledCategoryIds = prefs.enabledAnnouncementCategories,
          recentIds = prefs.recentAnnouncementIds,
          lastCategoryId = prefs.lastAnnouncementCategoryId,
        )

      val posted = AnnouncementNotifier.postNotification(context, picked, prefs)
      if (posted) {
        prefsStore.recordSentAnnouncement(picked.id, picked.category.id)
        val repository = DrxRepository(DrxDatabase.getInstance(context))
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

      Result.success()
    } catch (_: Throwable) {
      Result.success()
    }
  }
}
