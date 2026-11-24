package com.example.system

import android.content.pm.ApplicationInfo
import android.os.Build
import com.example.data.db.GameNotificationEntity
import com.example.data.model.GameEventCategory
import com.example.data.model.GameNotificationShare
import com.example.data.model.NotificationDateRangeFilter
import com.example.data.model.NotificationDayBucket
import com.example.data.model.NotificationInsightsSummary
import com.example.data.model.NotificationReadFilter
import com.example.data.model.NotificationSortOrder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object GameNotificationEngine {

  private val blockedNonGamePackages =
    setOf(
      "com.android.systemui",
      "com.android.settings",
      "com.android.vending",
      "com.android.mms",
      "com.android.dialer",
      "com.android.phone",
      "com.google.android.gms",
      "com.google.android.gsf",
      "com.google.android.apps.messaging",
      "com.google.android.gm",
      "com.google.android.youtube",
      "com.whatsapp",
      "com.whatsapp.w4b",
      "org.telegram.messenger",
      "org.telegram.plus",
      "com.instagram.android",
      "com.facebook.katana",
      "com.facebook.orca",
      "com.zhiliaoapp.musically",
      "com.ss.android.ugc.trill",
      "com.twitter.android",
      "com.discord",
      "jp.naver.line.android",
      "com.shopee.id",
      "com.tokopedia.tkpd",
      "com.gojek.app",
      "com.grabtaxi.passenger",
    )

  private val knownGamePackagePrefixesOrKeywords =
    listOf(
      "com.mobile.legends",
      "com.tencent.ig",
      "com.pubg.",
      "com.dts.freefire",
      "com.garena.",
      "com.mihoyo.",
      "com.hoyoverse.",
      "com.activision.",
      "com.riotgames.",
      "com.supercell.",
      "com.roblox.",
      "com.mojang.minecraft",
      "com.gameloft.",
      "com.ea.gp",
      "com.ea.game",
      "com.netease.",
      "com.nexon.",
      "com.bandainamco",
      "com.square_enix",
      "com.konami.",
      "com.nianticlabs.",
      "com.kiloo.",
      "com.innersloth.",
      "com.king.",
      "com.kurogame.",
      "com.proximabeta.",
      "com.levelinfinite.",
      "com.epicgames.",
    )

  private val otpKeywordRegex =
    Regex(
      "(?i)\\b(otp|one[- ]?time\\s*password|verification\\s*code|kode\\s*verifikasi|kode\\s*otp|auth(?:entication)?\\s*code|2fa|two[- ]?factor|security\\s*code|kode\\s*keamanan|pin\\s*code)\\b"
    )

  private val credentialTokenRegex =
    Regex(
      "(?i)(password\\s*[:=]\\s*\\S+|kata\\s*sandi\\s*[:=]\\s*\\S+|bearer\\s+[a-z0-9\\-._~+/]+=*|token\\s*[:=]\\s*\\S+|secret\\s*[:=]\\s*\\S+|eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,})"
    )

  private val numericOtpWithContextRegex =
    Regex("(?i)(kode|code|otp|pin|verif\\w*).{0,24}\\b\\d{4,8}\\b|\\b\\d{4,8}\\b.{0,24}(kode|code|otp|pin|verif\\w*)")

  fun isGamePackage(
    packageName: String,
    ownPackageName: String,
    appInfo: ApplicationInfo?,
    trackedGamePackages: Set<String>,
    customIncludedPackages: Set<String>,
    excludedPackages: Set<String>,
  ): Boolean {
    if (packageName.isBlank() || packageName == ownPackageName) return false
    if (excludedPackages.contains(packageName)) return false
    if (customIncludedPackages.contains(packageName)) return true
    if (blockedNonGamePackages.contains(packageName)) return false
    if (trackedGamePackages.contains(packageName)) return true

    if (appInfo != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo.category == ApplicationInfo.CATEGORY_GAME) {
        return true
      }
      @Suppress("DEPRECATION")
      if ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0) {
        return true
      }
    }

    val lower = packageName.lowercase(Locale.US)
    return knownGamePackagePrefixesOrKeywords.any { lower.contains(it) }
  }

  fun shouldSkipSensitiveNotification(title: String, text: String, bigText: String): Boolean {
    val combined = "$title $text $bigText"
    if (otpKeywordRegex.containsMatchIn(combined) && numericOtpWithContextRegex.containsMatchIn(combined)) {
      return true
    }
    if (Regex("(?i)\\b(otp|2fa|kode\\s*verifikasi|verification\\s*code|kata\\s*sandi|password\\s*reset)\\b").containsMatchIn(combined)) {
      return true
    }
    return false
  }

  fun sanitizeNotificationContent(raw: String): String {
    if (raw.isBlank()) return ""
    var cleaned = raw.trim()
    cleaned = credentialTokenRegex.replace(cleaned, "[REDACTED]")
    cleaned = numericOtpWithContextRegex.replace(cleaned) { match ->
      match.value.replace(Regex("\\b\\d{4,8}\\b"), "****")
    }
    return cleaned.take(1500)
  }

  fun classifyEventCategory(title: String, text: String, bigText: String): GameEventCategory {
    val combined = "$title $text $bigText".lowercase(Locale.US)
    val rules: List<Pair<GameEventCategory, List<String>>> =
      listOf(
        GameEventCategory.MAINTENANCE to listOf("maintenance", "pemeliharaan", "server down", "downtime", "scheduled downtime", "perbaikan server"),
        GameEventCategory.TOURNAMENT to listOf("tournament", "turnamen", "championship", "esports", "qualifier", "kualifikasi", "bracket", "scrim", "mpl", "piala"),
        GameEventCategory.REWARD to listOf("reward", "hadiah", "claim", "klaim", "login bonus", "daily bonus", "free skin", "redeem", "kompensasi", "compensation", "gift", "voucher", "diamond gratis"),
        GameEventCategory.UPDATE to listOf("update", "pembaruan", "patch note", "patch notes", "new version", "versi baru", "new hero", "new season", "season pass", "battle pass", "rilis versi"),
        GameEventCategory.REMINDER to listOf("reminder", "pengingat", "starting soon", "segera dimulai", "berakhir dalam", "last chance", "jangan lupa", "expires soon", "ending soon"),
        GameEventCategory.EVENT to listOf("event", "acara", "festival", "collab", "kolaborasi", "limited time", "special mode", "giveaway", "anniversary", "misi spesial"),
      )

    var bestCategory = GameEventCategory.GENERAL
    var bestScore = 0
    for ((category, keywords) in rules) {
      val hits = keywords.count { kw -> combined.contains(kw) }
      if (hits > bestScore) {
        bestScore = hits
        bestCategory = category
      }
    }
    return bestCategory
  }

  fun formatDateKey(timestampMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestampMs))

  fun formatDisplayDateHeader(dateKey: String, nowMs: Long = System.currentTimeMillis(), isIndo: Boolean): String {
    val todayKey = formatDateKey(nowMs)
    val yesterdayKey = formatDateKey(nowMs - 86_400_000L)
    return when (dateKey) {
      todayKey -> if (isIndo) "Hari Ini • $dateKey" else "Today • $dateKey"
      yesterdayKey -> if (isIndo) "Kemarin • $dateKey" else "Yesterday • $dateKey"
      else -> {
        val parsed = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateKey) }.getOrNull()
        if (parsed != null) {
          SimpleDateFormat("EEE, dd MMM yyyy", if (isIndo) Locale.forLanguageTag("id-ID") else Locale.US).format(parsed)
        } else {
          dateKey
        }
      }
    }
  }

  fun filterAndSort(
    items: List<GameNotificationEntity>,
    query: String,
    selectedPackage: String?,
    selectedDateKey: String?,
    selectedCategory: GameEventCategory?,
    readFilter: NotificationReadFilter,
    sortOrder: NotificationSortOrder,
    dateRangeFilter: NotificationDateRangeFilter = NotificationDateRangeFilter.ALL,
    nowMs: Long = System.currentTimeMillis(),
  ): List<GameNotificationEntity> {
    val q = query.trim().lowercase(Locale.US)
    val todayKey = formatDateKey(nowMs)
    val yesterdayKey = formatDateKey(nowMs - 86_400_000L)
    val cal =
      Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }
    val sevenDaysStartMs = cal.timeInMillis - 6L * 86_400_000L

    val filtered =
      items.filter { item ->
        val matchesQuery =
          q.isEmpty() ||
            item.gameName.lowercase(Locale.US).contains(q) ||
            item.title.lowercase(Locale.US).contains(q) ||
            item.text.lowercase(Locale.US).contains(q) ||
            item.bigText.lowercase(Locale.US).contains(q)
        val matchesPkg = selectedPackage.isNullOrBlank() || item.packageName == selectedPackage
        val matchesDate = selectedDateKey.isNullOrBlank() || item.dateKey == selectedDateKey
        val matchesDateRange =
          when (dateRangeFilter) {
            NotificationDateRangeFilter.ALL -> true
            NotificationDateRangeFilter.TODAY -> item.dateKey == todayKey
            NotificationDateRangeFilter.YESTERDAY -> item.dateKey == yesterdayKey
            NotificationDateRangeFilter.DAYS_7 -> item.timestampMs >= sevenDaysStartMs
          }
        val matchesCat = selectedCategory == null || item.eventCategory.equals(selectedCategory.id, ignoreCase = true)
        val matchesRead =
          when (readFilter) {
            NotificationReadFilter.ALL -> true
            NotificationReadFilter.UNREAD -> !item.isRead
            NotificationReadFilter.READ -> item.isRead
            NotificationReadFilter.FAVORITE -> item.isFavorite
            NotificationReadFilter.IMPORTANT -> item.isImportant
          }
        matchesQuery && matchesPkg && matchesDate && matchesDateRange && matchesCat && matchesRead
      }
    return when (sortOrder) {
      NotificationSortOrder.NEWEST -> filtered.sortedByDescending { it.timestampMs }
      NotificationSortOrder.OLDEST -> filtered.sortedBy { it.timestampMs }
    }
  }

  fun computeInsights(
    items: List<GameNotificationEntity>,
    nowMs: Long = System.currentTimeMillis(),
  ): NotificationInsightsSummary {
    if (items.isEmpty()) return NotificationInsightsSummary()

    val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val startOfTodayMs = cal.timeInMillis
    val startOf7dMs = startOfTodayMs - 6L * 86_400_000L
    val startOfPrev7dMs = startOf7dMs - 7L * 86_400_000L

    val todayCount = items.count { it.timestampMs >= startOfTodayMs }
    val weekCount = items.count { it.timestampMs >= startOf7dMs }
    val prevWeekCount = items.count { it.timestampMs in startOfPrev7dMs until startOf7dMs }
    val unreadCount = items.count { !it.isRead }

    val byGame = items.groupBy { it.packageName }.values.maxByOrNull { it.size }
    val mostActiveName = byGame?.firstOrNull()?.gameName
    val mostActivePkg = byGame?.firstOrNull()?.packageName
    val mostActiveCount = byGame?.size ?: 0

    // Peak 3-hour window
    val bucketCounts = IntArray(8)
    val tempCal = Calendar.getInstance()
    items.forEach { n ->
      tempCal.timeInMillis = n.timestampMs
      val hour = tempCal.get(Calendar.HOUR_OF_DAY)
      bucketCounts[(hour / 3).coerceIn(0, 7)]++
    }
    var bestBucket = 0
    for (i in 1 until 8) {
      if (bucketCounts[i] > bucketCounts[bestBucket]) bestBucket = i
    }
    val startHour = bestBucket * 3
    val endHour = startHour + 2
    val peakTimeRange = String.format(Locale.US, "%02d:00–%02d:59", startHour, endHour)

    // Trend comparing current 7d vs previous 7d
    val (trendDirection, trendDelta) =
      when {
        prevWeekCount == 0 && weekCount == 0 -> "INSUFFICIENT" to null
        prevWeekCount == 0 -> "INSUFFICIENT" to null
        else -> {
          val delta = (((weekCount - prevWeekCount).toFloat() / prevWeekCount.toFloat()) * 100f).roundToInt()
          when {
            delta >= 10 -> "UP" to delta
            delta <= -10 -> "DOWN" to delta
            else -> "STABLE" to delta
          }
        }
      }

    // 7-day daily buckets
    val dayFormat = SimpleDateFormat("dd/MM", Locale.US)
    val daily7d =
      (6 downTo 0).map { offset ->
        val dayStart = startOfTodayMs - offset * 86_400_000L
        val dayEnd = dayStart + 86_400_000L
        val key = dayFormat.format(Date(dayStart))
        val cnt = items.count { it.timestampMs in dayStart until dayEnd }
        NotificationDayBucket(label = key, count = cnt)
      }

    // 4-week buckets
    val weekly4w =
      (3 downTo 0).map { weekOffset ->
        val wEnd = startOfTodayMs + 86_400_000L - weekOffset * 7L * 86_400_000L
        val wStart = wEnd - 7L * 86_400_000L
        val cnt = items.count { it.timestampMs in wStart until wEnd }
        NotificationDayBucket(label = "W-${weekOffset + 1}", count = cnt)
      }

    val total = items.size
    val distribution =
      items
        .groupBy { it.packageName }
        .values
        .map { list ->
          val first = list.first()
          GameNotificationShare(
            packageName = first.packageName,
            gameName = first.gameName,
            count = list.size,
            fraction = list.size.toFloat() / total.toFloat(),
          )
        }
        .sortedByDescending { it.count }
        .take(6)

    return NotificationInsightsSummary(
      totalCount = total,
      unreadCount = unreadCount,
      todayCount = todayCount,
      weekCount = weekCount,
      mostActiveGameName = mostActiveName,
      mostActiveGamePkg = mostActivePkg,
      mostActiveGameCount = mostActiveCount,
      mostActiveTimeRange = peakTimeRange,
      trendPercentDelta = trendDelta,
      trendDirection = trendDirection,
      daily7dChart = daily7d,
      weekly4wChart = weekly4w,
      gameDistribution = distribution,
    )
  }
}
