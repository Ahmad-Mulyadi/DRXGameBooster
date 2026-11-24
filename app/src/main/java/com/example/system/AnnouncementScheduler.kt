package com.example.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.model.AnnouncementCatalog
import com.example.data.model.AnnouncementMessage
import com.example.data.prefs.DrxPreferences
import com.example.data.prefs.DrxUserPrefs
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.flow.first

object AnnouncementSchedulePlanner {

  fun isMinuteInQuietHours(minuteOfDay: Int, quietStartHour: Int, quietEndHour: Int): Boolean {
    if (quietStartHour == quietEndHour) return false
    val startMin = quietStartHour.coerceIn(0, 23) * 60
    val endMin = quietEndHour.coerceIn(0, 23) * 60
    return if (startMin < endMin) {
      minuteOfDay in startMin until endMin
    } else {
      minuteOfDay >= startMin || minuteOfDay < endMin
    }
  }

  /**
   * Generates 1–3 randomized notification times within the non-quiet hours of the remaining day
   * (or next day if remaining active minutes are too short), spacing slots naturally apart so they
   * never fire back-to-back or at predictable fixed times.
   */
  fun generateRandomTimestampsForWindow(
    nowMs: Long,
    minPerDay: Int,
    maxPerDay: Int,
    quietStartHour: Int,
    quietEndHour: Int,
    random: Random = Random.Default,
  ): List<Long> {
    val safeMin = minPerDay.coerceIn(1, 3)
    val safeMax = maxPerDay.coerceIn(safeMin, 3)
    val targetCount = if (safeMin == safeMax) safeMin else random.nextInt(safeMin, safeMax + 1)

    val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
    val dayStartCal =
      Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
    val currentMinuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

    val allowedToday =
      (currentMinuteOfDay + 20 until 1440).filter { m ->
        !isMinuteInQuietHours(m, quietStartHour, quietEndHour)
      }

    // If remaining active window today is < 90 minutes, plan for tomorrow instead
    val baseDayMs: Long
    val allowedMinutes: List<Int>
    if (allowedToday.size < 90) {
      baseDayMs = dayStartCal.timeInMillis + TimeUnit.DAYS.toMillis(1)
      allowedMinutes =
        (0 until 1440).filter { m ->
          !isMinuteInQuietHours(m, quietStartHour, quietEndHour)
        }
    } else {
      baseDayMs = dayStartCal.timeInMillis
      allowedMinutes = allowedToday
    }

    if (allowedMinutes.isEmpty()) return emptyList()

    val span = allowedMinutes.size
    val effectiveCount =
      when {
        span < 120 -> 1
        span < 300 -> targetCount.coerceAtMost(2)
        else -> targetCount
      }

    val minGapMinutes = (span / (effectiveCount + 1)).coerceIn(95, 210)
    val chosenMinutes = mutableListOf<Int>()

    val segmentSize = (span / effectiveCount).coerceAtLeast(1)
    for (i in 0 until effectiveCount) {
      val segStart = i * segmentSize
      val segEnd = if (i == effectiveCount - 1) span else ((i + 1) * segmentSize)
      val candidates =
        allowedMinutes.subList(segStart, segEnd).filter { candidate ->
          chosenMinutes.all { prev -> kotlin.math.abs(candidate - prev) >= minGapMinutes }
        }
      val pool = if (candidates.isNotEmpty()) candidates else allowedMinutes.subList(segStart, segEnd)
      if (pool.isNotEmpty()) {
        chosenMinutes.add(pool[random.nextInt(pool.size)])
      }
    }

    return chosenMinutes
      .sorted()
      .map { min ->
        val secondJitter = random.nextInt(5, 55) * 1000L
        baseDayMs + min * 60_000L + secondJitter
      }
      .filter { it > nowMs + 60_000L }
  }

  fun pickNonDuplicateMessage(
    enabledCategoryIds: Set<String>,
    recentIds: List<String>,
    lastCategoryId: String? = null,
    useComboChancePercent: Int = 20,
    random: Random = Random.Default,
  ): AnnouncementMessage {
    val categoryPool =
      AnnouncementCatalog.allMessages.filter { msg ->
        enabledCategoryIds.isEmpty() || enabledCategoryIds.contains(msg.category.id)
      }.ifEmpty { AnnouncementCatalog.allMessages }

    val allCats = categoryPool.map { it.category }.distinct()
    val nonConsecutiveCats =
      if (allCats.size > 1 && !lastCategoryId.isNullOrBlank()) {
        allCats.filter { it.id != lastCategoryId }.ifEmpty { allCats }
      } else {
        allCats
      }

    val pickedCategory = nonConsecutiveCats[random.nextInt(nonConsecutiveCats.size)]
    if (useComboChancePercent > 0 && random.nextInt(100) < useComboChancePercent) {
      val generated = com.example.data.model.AnnouncementSentenceGenerator.generateCombinedMessage(pickedCategory, random)
      if (generated.id !in recentIds) {
        return generated
      }
    }

    val inCat = categoryPool.filter { it.category == pickedCategory }
    val nonRecentInCat = inCat.filter { it.id !in recentIds }
    if (nonRecentInCat.isNotEmpty()) {
      return nonRecentInCat[random.nextInt(nonRecentInCat.size)]
    }
    val nonRecentGlobal = categoryPool.filter { it.id !in recentIds }
    if (nonRecentGlobal.isNotEmpty()) {
      return nonRecentGlobal[random.nextInt(nonRecentGlobal.size)]
    }
    return inCat[random.nextInt(inCat.size)]
  }
}

class AnnouncementScheduler(private val context: Context) {

  companion object {
    const val TAG_ANNOUNCEMENT_SLOT = "drx_announcement_slot"
    const val TAG_DAILY_REPLANNER = "drx_announcement_replanner"
    const val WORK_NAME_REPLANNER = "drx_smart_announcement_daily_replanner"
    const val KEY_IS_REPLAN_ONLY = "is_replan_only"
    const val KEY_SLOT_INDEX = "slot_index"
  }

  fun hasNotificationPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
  }

  suspend fun syncSchedule(forceReschedule: Boolean = false) {
    val prefsStore = DrxPreferences(context)
    val prefs = prefsStore.prefsFlow.first()

    if (!prefs.announcementsEnabled || !hasNotificationPermission()) {
      cancelAll()
      if (!prefs.announcementsEnabled) {
        prefsStore.saveScheduleMetadata("", "")
      }
      return
    }

    val todayKey = currentScheduleDateKey(prefs)
    if (!forceReschedule && prefs.lastScheduleDateKey == todayKey && prefs.scheduledSlotsSummary.isNotBlank()) {
      return
    }

    scheduleNextDayOrRemaining(prefs, prefsStore)
  }

  private fun currentScheduleDateKey(prefs: DrxUserPrefs): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val tz = java.util.TimeZone.getDefault().id
    return "${sdf.format(Date())}|$tz|${prefs.announcementMinPerDay}-${prefs.announcementMaxPerDay}|${prefs.quietHoursStartHour}-${prefs.quietHoursEndHour}"
  }

  private fun getWorkManagerOrNull(): WorkManager? =
    try {
      WorkManager.getInstance(context)
    } catch (_: Throwable) {
      null
    }

  private suspend fun scheduleNextDayOrRemaining(
    prefs: DrxUserPrefs,
    prefsStore: DrxPreferences,
  ) {
    val wm = getWorkManagerOrNull() ?: return
    wm.cancelAllWorkByTag(TAG_ANNOUNCEMENT_SLOT)

    val now = System.currentTimeMillis()
    val timestamps =
      AnnouncementSchedulePlanner.generateRandomTimestampsForWindow(
        nowMs = now,
        minPerDay = prefs.announcementMinPerDay,
        maxPerDay = prefs.announcementMaxPerDay,
        quietStartHour = prefs.quietHoursStartHour,
        quietEndHour = prefs.quietHoursEndHour,
      )

    timestamps.forEachIndexed { idx, targetMs ->
      val delayMs = (targetMs - now).coerceAtLeast(60_000L)
      val request =
        OneTimeWorkRequestBuilder<AnnouncementWorker>()
          .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
          .addTag(TAG_ANNOUNCEMENT_SLOT)
          .setInputData(
            workDataOf(
              KEY_IS_REPLAN_ONLY to false,
              KEY_SLOT_INDEX to idx,
            )
          )
          .build()
      wm.enqueueUniqueWork("drx_announce_slot_$idx", ExistingWorkPolicy.REPLACE, request)
    }

    val nextMorningCal =
      Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, Random.nextInt(0, 2))
        set(Calendar.MINUTE, Random.nextInt(20, 55))
        set(Calendar.SECOND, 0)
      }
    val replanDelayMs = (nextMorningCal.timeInMillis - now).coerceAtLeast(TimeUnit.HOURS.toMillis(2))
    val replanRequest =
      OneTimeWorkRequestBuilder<AnnouncementWorker>()
        .setInitialDelay(replanDelayMs, TimeUnit.MILLISECONDS)
        .addTag(TAG_DAILY_REPLANNER)
        .setInputData(workDataOf(KEY_IS_REPLAN_ONLY to true))
        .build()
    wm.enqueueUniqueWork(WORK_NAME_REPLANNER, ExistingWorkPolicy.REPLACE, replanRequest)

    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val summary =
      if (timestamps.isEmpty()) ""
      else timestamps.joinToString(" • ") { timeFmt.format(Date(it)) }
    prefsStore.saveScheduleMetadata(currentScheduleDateKey(prefs), summary)
  }

  fun cancelAll() {
    val wm = getWorkManagerOrNull() ?: return
    wm.cancelAllWorkByTag(TAG_ANNOUNCEMENT_SLOT)
    wm.cancelAllWorkByTag(TAG_DAILY_REPLANNER)
  }
}
