package com.example.system

import com.example.data.model.FocusModeSessionSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GamingFocusUiState(
  val isActive: Boolean = false,
  val startedAtMs: Long = 0L,
  val elapsedSeconds: Long = 0L,
  val targetMinutes: Int = 45,
  val dndRequested: Boolean = true,
  val dndActuallyEnabled: Boolean = false,
  val screenTimeoutAppliedMs: Int? = null,
  val previousScreenTimeoutMs: Int? = null,
  val lastSummary: FocusModeSessionSummary? = null,
)

object GamingFocusModeController {

  fun activateFocusSession(
    tweaksManager: SystemTweaksManager,
    telemetryMonitor: TelemetryMonitor,
    targetMinutes: Int,
    enableDnd: Boolean,
    extendTimeoutMs: Int?,
  ): GamingFocusUiState {
    val prevTimeout = telemetryMonitor.readDisplayInfo().screenTimeoutMs
    var dndActive = false
    if (enableDnd && tweaksManager.isDndPolicyGranted()) {
      // Android PRIORITY filter keeps alarms and starred/repeat emergency callers intact.
      dndActive = tweaksManager.setDoNotDisturb(true)
    }

    var appliedTimeout: Int? = null
    if (extendTimeoutMs != null && tweaksManager.canWriteSystemSettings()) {
      if (tweaksManager.setScreenTimeoutMs(extendTimeoutMs)) {
        appliedTimeout = extendTimeoutMs
      }
    }

    return GamingFocusUiState(
      isActive = true,
      startedAtMs = System.currentTimeMillis(),
      elapsedSeconds = 0L,
      targetMinutes = targetMinutes.coerceIn(5, 240),
      dndRequested = enableDnd,
      dndActuallyEnabled = dndActive,
      screenTimeoutAppliedMs = appliedTimeout,
      previousScreenTimeoutMs = prevTimeout,
      lastSummary = null,
    )
  }

  fun stopFocusSession(
    current: GamingFocusUiState,
    tweaksManager: SystemTweaksManager,
    restoreDnd: Boolean = true,
    restoreTimeout: Boolean = true,
  ): GamingFocusUiState {
    val endedAt = System.currentTimeMillis()
    val elapsedMs =
      if (current.startedAtMs > 0L) (endedAt - current.startedAtMs).coerceAtLeast(1000L)
      else current.elapsedSeconds * 1000L

    if (restoreDnd && current.dndActuallyEnabled && tweaksManager.isDndPolicyGranted()) {
      tweaksManager.setDoNotDisturb(false)
    }
    if (restoreTimeout && current.screenTimeoutAppliedMs != null && current.previousScreenTimeoutMs != null &&
      tweaksManager.canWriteSystemSettings()
    ) {
      tweaksManager.setScreenTimeoutMs(current.previousScreenTimeoutMs)
    }

    val summary =
      FocusModeSessionSummary(
        startedAtMs = current.startedAtMs,
        endedAtMs = endedAt,
        durationMs = elapsedMs,
        targetMinutes = current.targetMinutes,
        dndActivated = current.dndActuallyEnabled,
        screenTimeoutAppliedMs = current.screenTimeoutAppliedMs,
        completedTarget = elapsedMs >= current.targetMinutes * 60_000L,
      )

    return GamingFocusUiState(
      isActive = false,
      targetMinutes = current.targetMinutes,
      dndRequested = current.dndRequested,
      dndActuallyEnabled = tweaksManager.isDndEnabled(),
      lastSummary = summary,
    )
  }

  fun formatSummaryText(summary: FocusModeSessionSummary, isIndo: Boolean): String {
    val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.US)
    val dur = TimeAndSizeFormatters.formatDurationCompact(summary.durationMs, isIndo)
    val dndStr =
      if (summary.dndActivated) (if (isIndo) "DND Prioritas Aktif" else "Priority DND Enabled")
      else (if (isIndo) "DND Tidak Diaktifkan" else "DND Not Enabled")
    return if (isIndo) {
      "${fmt.format(Date(summary.endedAtMs))} • Durasi $dur (Target ${summary.targetMinutes}m) • $dndStr"
    } else {
      "${fmt.format(Date(summary.endedAtMs))} • Duration $dur (Target ${summary.targetMinutes}m) • $dndStr"
    }
  }
}
