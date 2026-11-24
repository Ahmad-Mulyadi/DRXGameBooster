package com.example.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AnnouncementBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val action = intent.action ?: return
    if (
      action == Intent.ACTION_BOOT_COMPLETED ||
      action == Intent.ACTION_TIMEZONE_CHANGED ||
      action == Intent.ACTION_TIME_CHANGED
    ) {
      val pendingResult = goAsync()
      val force = action == Intent.ACTION_TIMEZONE_CHANGED || action == Intent.ACTION_TIME_CHANGED
      CoroutineScope(Dispatchers.IO).launch {
        try {
          AnnouncementScheduler(context.applicationContext).syncSchedule(forceReschedule = force)
        } catch (_: Throwable) {
        } finally {
          pendingResult.finish()
        }
      }
    }
  }
}
