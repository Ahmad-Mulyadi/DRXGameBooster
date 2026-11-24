package com.example.data.model

enum class GameEventCategory(
  val id: String,
  val labelEn: String,
  val labelId: String,
) {
  EVENT("EVENT", "Event", "Event"),
  REWARD("REWARD", "Reward", "Hadiah"),
  UPDATE("UPDATE", "Update", "Pembaruan"),
  MAINTENANCE("MAINTENANCE", "Maintenance", "Pemeliharaan"),
  TOURNAMENT("TOURNAMENT", "Tournament", "Turnamen"),
  REMINDER("REMINDER", "Reminder", "Pengingat"),
  GENERAL("GENERAL", "General", "Umum");

  companion object {
    fun fromId(raw: String): GameEventCategory =
      entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: GENERAL
  }
}

enum class NotificationGroupTab {
  BY_DATE,
  BY_GAME,
}

enum class NotificationSortOrder {
  NEWEST,
  OLDEST,
}

enum class NotificationReadFilter {
  ALL,
  UNREAD,
  READ,
  FAVORITE,
  IMPORTANT,
}

enum class NotificationDateRangeFilter {
  ALL,
  TODAY,
  YESTERDAY,
  DAYS_7,
}

data class NotificationDayBucket(
  val label: String,
  val count: Int,
)

data class GameNotificationShare(
  val packageName: String,
  val gameName: String,
  val count: Int,
  val fraction: Float,
)

data class NotificationInsightsSummary(
  val totalCount: Int = 0,
  val unreadCount: Int = 0,
  val todayCount: Int = 0,
  val weekCount: Int = 0,
  val mostActiveGameName: String? = null,
  val mostActiveGamePkg: String? = null,
  val mostActiveGameCount: Int = 0,
  val mostActiveTimeRange: String? = null,
  val trendPercentDelta: Int? = null,
  val trendDirection: String = "INSUFFICIENT", // "UP", "DOWN", "STABLE", "INSUFFICIENT"
  val daily7dChart: List<NotificationDayBucket> = emptyList(),
  val weekly4wChart: List<NotificationDayBucket> = emptyList(),
  val gameDistribution: List<GameNotificationShare> = emptyList(),
)

data class UpdateManifest(
  val versionName: String,
  val versionCode: Int,
  val releaseDate: String,
  val releaseNotes: String,
  val apkType: String, // "release" or "debug"
  val downloadUrl: String = "",
  val localFileName: String = "",
  val fileSizeBytes: Long = 0L,
  val sha256: String = "",
  val isMandatorySuggested: Boolean = false,
  val minSdkVersion: Int = 24,
  val appId: String = "com.derrex.gameturbo",
  val signingCertSha256: String = "",
  val developerWarning: String = "",
) {
  val apkFileName: String
    get() = localFileName

  val fileSize: Long
    get() = fileSizeBytes

  val minimumSupportedVersion: Int
    get() = minSdkVersion
}

enum class UpdateOfferPolicyState {
  NONE,
  OPTIONAL_OFFER_1,
  OPTIONAL_OFFER_2,
  MANDATORY_REQUIRED,
}

enum class DrxUpdatePipelineState {
  IDLE,
  CHECKING,
  UP_TO_DATE,
  SOURCE_NOT_CONFIGURED,
  OFFLINE,
  REJECTED_WARNING,
  AVAILABLE,
  DOWNLOADING,
  VERIFYING,
  READY_TO_INSTALL,
  INSTALLING,
  FAILED,
}

data class ApkVerificationResult(
  val isValid: Boolean,
  val packageName: String = "",
  val versionName: String = "",
  val versionCode: Long = 0L,
  val fileSizeBytes: Long = 0L,
  val sha256Full: String = "",
  val signatureMatchesInstalled: Boolean? = null,
  val errorReason: String = "",
  val signingCertSha256: String = "",
  val sameOrLowerVersionCode: Boolean = false,
  val appIdMatches: Boolean = true,
)
