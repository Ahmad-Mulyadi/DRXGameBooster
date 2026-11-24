package com.example.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.db.DrxDatabase
import com.example.data.db.DrxRepository
import com.example.data.db.GameEventReminderEntity
import com.example.ui.screens.ToolRoutes
import java.util.UUID
import java.util.concurrent.TimeUnit

class EventReminderWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  companion object {
    const val CHANNEL_ID = "drx_game_event_reminders"
    const val KEY_REMINDER_ID = "reminder_id"
    const val KEY_GAME_NAME = "game_name"
    const val KEY_TITLE = "event_title"
    const val KEY_CATEGORY = "event_category"

    fun scheduleReminder(
      context: Context,
      reminderEntity: GameEventReminderEntity,
      delayMs: Long,
    ): String {
      val safeDelay = delayMs.coerceAtLeast(1_000L)
      val inputData =
        Data.Builder()
          .putLong(KEY_REMINDER_ID, reminderEntity.id)
          .putString(KEY_GAME_NAME, reminderEntity.gameName)
          .putString(KEY_TITLE, reminderEntity.title)
          .putString(KEY_CATEGORY, reminderEntity.eventCategory)
          .build()

      val request =
        OneTimeWorkRequestBuilder<EventReminderWorker>()
          .setInitialDelay(safeDelay, TimeUnit.MILLISECONDS)
          .setInputData(inputData)
          .addTag("drx_event_reminder")
          .build()

      WorkManager.getInstance(context).enqueue(request)
      return request.id.toString()
    }

    fun cancelReminder(context: Context, workRequestId: String) {
      if (workRequestId.isBlank()) return
      runCatching {
        WorkManager.getInstance(context).cancelWorkById(UUID.fromString(workRequestId))
      }
    }
  }

  override suspend fun doWork(): Result {
    return try {
      val reminderId = inputData.getLong(KEY_REMINDER_ID, -1L)
      val gameName = inputData.getString(KEY_GAME_NAME).orEmpty().ifBlank { "DRX Event" }
      val title = inputData.getString(KEY_TITLE).orEmpty().ifBlank { "Game Event Reminder" }
      val category = inputData.getString(KEY_CATEGORY).orEmpty().ifBlank { "EVENT" }

      if (reminderId > 0L) {
        runCatching {
          val repo = DrxRepository(DrxDatabase.getInstance(applicationContext))
          repo.markEventReminderCompleted(reminderId)
        }
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted =
          ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return Result.success()
      }

      val nm = applicationContext.getSystemService(NotificationManager::class.java) ?: return Result.success()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel =
          NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.event_reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
          ).apply {
            description = applicationContext.getString(R.string.event_reminder_channel_desc)
          }
        nm.createNotificationChannel(channel)
      }

      val openIntent =
        Intent(applicationContext, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
          putExtra("open_route", ToolRoutes.GAME_EVENTS)
        }
      val pi =
        PendingIntent.getActivity(
          applicationContext,
          (reminderId.toInt() and 0x7FFF) + 9100,
          openIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

      val notif =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
          .setSmallIcon(R.drawable.ic_drx_notification)
          .setContentTitle("$gameName • $category")
          .setContentText(title)
          .setContentIntent(pi)
          .setAutoCancel(true)
          .setPriority(NotificationCompat.PRIORITY_HIGH)
          .build()

      nm.notify(9200 + (reminderId.toInt() and 0x3FF), notif)
      Result.success()
    } catch (_: Throwable) {
      Result.success()
    }
  }
}
