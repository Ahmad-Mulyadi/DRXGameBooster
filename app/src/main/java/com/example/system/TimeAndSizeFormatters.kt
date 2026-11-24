package com.example.system

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TimeAndSizeFormatters {

  fun formatDurationReadable(durationMs: Long, isIndo: Boolean): String {
    if (durationMs <= 0L) return if (isIndo) "0 menit" else "0 min"
    val totalMinutes = durationMs / 60_000L
    if (totalMinutes < 1L) {
      val secs = (durationMs / 1000L).coerceAtLeast(1L)
      return if (isIndo) "$secs detik" else "${secs}s"
    }
    val days = totalMinutes / (60L * 24L)
    val remHoursAfterDays = (totalMinutes % (60L * 24L)) / 60L
    if (days > 0L) {
      return if (isIndo) {
        if (remHoursAfterDays > 0L) "$days hari $remHoursAfterDays jam" else "$days hari"
      } else {
        if (remHoursAfterDays > 0L) "${days}d ${remHoursAfterDays}h" else "${days}d"
      }
    }
    val hours = totalMinutes / 60L
    val mins = totalMinutes % 60L
    return if (hours > 0L) {
      if (isIndo) {
        if (mins > 0L) "$hours jam $mins menit" else "$hours jam"
      } else {
        if (mins > 0L) "${hours}h ${mins}m" else "${hours}h"
      }
    } else {
      if (isIndo) "$mins menit" else "$mins min"
    }
  }

  fun formatDurationCompact(durationMs: Long, isIndo: Boolean = false): String =
    formatDurationReadable(durationMs, isIndo)

  fun formatBytesAuto(bytes: Long?, notAvailableLabel: String = "Not Available"): String {
    if (bytes == null || bytes < 0L) return notAvailableLabel
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
  }

  fun resolveTimezoneTag(timeZone: TimeZone = TimeZone.getDefault()): String {
    return if (timeZone.id == "Asia/Jakarta") {
      "WIB"
    } else {
      timeZone.getDisplayName(false, TimeZone.SHORT, Locale.getDefault()) ?: timeZone.id
    }
  }

  fun formatDate(
    timestampMs: Long?,
    isIndo: Boolean,
    timeZone: TimeZone = TimeZone.getDefault(),
    notAvailableLabel: String = "Not Available",
  ): String {
    if (timestampMs == null || timestampMs <= 0L) return notAvailableLabel
    val locale = if (isIndo) Locale.Builder().setLanguage("id").setRegion("ID").build() else Locale.ENGLISH
    val fmt = SimpleDateFormat("dd MMMM yyyy", locale).apply { this.timeZone = timeZone }
    return fmt.format(Date(timestampMs))
  }

  fun formatDayOfWeek(
    timestampMs: Long?,
    isIndo: Boolean,
    timeZone: TimeZone = TimeZone.getDefault(),
    notAvailableLabel: String = "-",
  ): String {
    if (timestampMs == null || timestampMs <= 0L) return notAvailableLabel
    val locale = if (isIndo) Locale.Builder().setLanguage("id").setRegion("ID").build() else Locale.ENGLISH
    val fmt = SimpleDateFormat("EEEE", locale).apply { this.timeZone = timeZone }
    return fmt.format(Date(timestampMs))
  }

  fun formatTimeWithZone(
    timestampMs: Long?,
    timeZone: TimeZone = TimeZone.getDefault(),
    notAvailableLabel: String = "-",
  ): String {
    if (timestampMs == null || timestampMs <= 0L) return notAvailableLabel
    val fmt = SimpleDateFormat("HH:mm", Locale.US).apply { this.timeZone = timeZone }
    return "${fmt.format(Date(timestampMs))} ${resolveTimezoneTag(timeZone)}"
  }

  fun formatClockShort(
    timestampMs: Long?,
    timeZone: TimeZone = TimeZone.getDefault(),
  ): String {
    if (timestampMs == null || timestampMs <= 0L) return "--:--"
    val fmt = SimpleDateFormat("HH:mm", Locale.US).apply { this.timeZone = timeZone }
    return fmt.format(Date(timestampMs))
  }

  fun formatDateTimeWithZone(
    timestampMs: Long?,
    isIndo: Boolean,
    timeZone: TimeZone = TimeZone.getDefault(),
    notAvailableLabel: String = "Not Available",
  ): String {
    if (timestampMs == null || timestampMs <= 0L) return notAvailableLabel
    return "${formatDate(timestampMs, isIndo, timeZone)} • ${formatTimeWithZone(timestampMs, timeZone)}"
  }
}
