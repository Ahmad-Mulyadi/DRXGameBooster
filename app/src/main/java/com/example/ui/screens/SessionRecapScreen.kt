package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LanguageMode
import com.example.data.model.SessionRecapSummary
import com.example.i18n.LocalStrings
import com.example.system.TimeAndSizeFormatters
import com.example.ui.theme.DrxCustomModal
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxMainViewModel
import java.util.Locale

@Composable
fun SessionRecapScreen(
  viewModel: DrxMainViewModel,
  onOpenTimeline: () -> Unit,
  onOpenGallery: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val context = LocalContext.current
  val prefs by viewModel.userPrefs.collectAsStateWithLifecycle()
  val sessions by viewModel.allSessions.collectAsStateWithLifecycle()
  val timelineEvents by viewModel.sessionTimelineEvents.collectAsStateWithLifecycle()
  val reports by viewModel.sessionReports.collectAsStateWithLifecycle()
  val latestRecap by viewModel.latestSessionRecap.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  val recap =
    remember(latestRecap, sessions, timelineEvents, reports) {
      viewModel.buildLatestSessionRecapFallback(sessions, timelineEvents, reports)
    }

  LazyColumn(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("session_recap_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.sessionRecapTitle,
        subtitle = strings.sessionRecapSub,
        onBack = onBack,
      )
    }

    if (recap == null) {
      item {
        DrxGlassCard {
          Text(
            text =
              if (isIndo) "Belum ada ringkasan sesi bermain. Mulai dan simpan sesi bermain dari Dashboard untuk melihat recap otomatis."
              else "No gameplay session recap yet. Start and save a game session from Dashboard to generate your automatic recap.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      item {
        SessionRecapContentCard(recap = recap, isIndo = isIndo)
      }
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          DrxSecondaryButton(
            label = strings.sessionTimelineTitle,
            icon = Icons.Default.Timeline,
            onClick = onOpenTimeline,
            modifier = Modifier.weight(1f),
          )
          DrxSecondaryButton(
            label = if (isIndo) "Galeri Media" else "Media Gallery",
            icon = Icons.Default.PhotoLibrary,
            onClick = onOpenGallery,
            modifier = Modifier.weight(1f),
          )
        }
      }
      item {
        DrxPrimaryButton(
          label = if (isIndo) "Bagikan Recap Sesi" else "Share Session Recap",
          icon = Icons.Default.Share,
          onClick = {
            val text = formatSessionRecapShareText(recap, isIndo)
            val shareIntent =
              Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
              }
            context.startActivity(Intent.createChooser(shareIntent, strings.sessionRecapTitle))
          },
          modifier = Modifier.fillMaxWidth().testTag("session_recap_share_button"),
        )
      }
    }
  }
}

@Composable
fun SessionRecapModal(
  recap: SessionRecapSummary,
  isIndo: Boolean,
  onOpenTimeline: () -> Unit,
  onOpenGallery: () -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val strings = LocalStrings.current
  DrxCustomModal(
    title = strings.sessionRecapTitle,
    onDismiss = onDismiss,
  ) {
    SessionRecapContentCard(recap = recap, isIndo = isIndo)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      DrxSecondaryButton(
        label = if (isIndo) "Timeline" else "Timeline",
        icon = Icons.Default.Timeline,
        onClick = {
          onDismiss()
          onOpenTimeline()
        },
        modifier = Modifier.weight(1f).height(42.dp),
      )
      DrxSecondaryButton(
        label = if (isIndo) "Media" else "Media",
        icon = Icons.Default.PhotoLibrary,
        onClick = {
          onDismiss()
          onOpenGallery()
        },
        modifier = Modifier.weight(1f).height(42.dp),
      )
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      DrxSecondaryButton(
        label = if (isIndo) "Bagikan" else "Share",
        icon = Icons.Default.Share,
        onClick = {
          val text = formatSessionRecapShareText(recap, isIndo)
          context.startActivity(
            Intent.createChooser(
              Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
              },
              strings.sessionRecapTitle,
            )
          )
        },
        modifier = Modifier.weight(1f).height(44.dp),
      )
      DrxPrimaryButton(
        label = strings.closeLabel,
        onClick = onDismiss,
        modifier = Modifier.weight(1f).height(44.dp),
      )
    }
  }
}

@Composable
private fun SessionRecapContentCard(
  recap: SessionRecapSummary,
  isIndo: Boolean,
) {
  val drxColors = LocalDrxColors.current
  val strings = LocalStrings.current
  DrxGlassCard(
    accentColor = drxColors.neonCyan,
    highlighted = true,
    cornerRadius = 22.dp,
    modifier = Modifier.testTag("session_recap_card"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = recap.gameName,
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        Text(
          text = TimeAndSizeFormatters.formatDateTimeWithZone(recap.endedAtMs, isIndo),
          style = DrxTypographyTokens.caption,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      DrxStatusPill(text = strings.profileName(recap.profileUsed), color = drxColors.electricBlue)
    }

    Spacer(Modifier.height(14.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      RecapKpi(
        label = if (isIndo) "Durasi Bermain" else "Playtime",
        value = TimeAndSizeFormatters.formatDurationReadable(recap.durationMs, isIndo),
        color = drxColors.neonCyan,
      )
      RecapKpi(
        label = "UI FPS",
        value = recap.avgUiFps?.let { "${it.toInt()} FPS" } ?: strings.notAvailableLabel,
        color = drxColors.optimalGreen,
      )
    }

    Spacer(Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      RecapKpi(
        label = if (isIndo) "Baterai" else "Battery",
        value = "${recap.batteryStartPercent}% -> ${recap.batteryEndPercent}%",
        color = drxColors.silver,
      )
      RecapKpi(
        label = if (isIndo) "Suhu Puncak" else "Peak Temp",
        value = String.format(Locale.US, "%.1f°C", recap.peakTempC),
        color = if (recap.peakTempC >= 41f) drxColors.warningAmber else drxColors.neonCyan,
      )
    }

    Spacer(Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      RecapKpi(
        label = if (isIndo) "Peringatan Performa" else "Perf Warnings",
        value = "${recap.performanceWarningsCount}",
        color = if (recap.performanceWarningsCount > 0) drxColors.warningAmber else drxColors.optimalGreen,
      )
      RecapKpi(
        label = if (isIndo) "Event Jaringan" else "Net Events",
        value = "${recap.networkEventsCount}",
        color = drxColors.electricBlue,
      )
    }

    Spacer(Modifier.height(10.dp))
    Text(
      text =
        if (isIndo) "Media Sesi: ${recap.recordingCount} rekaman • ${recap.screenshotCount} screenshot"
        else "Session Media: ${recap.recordingCount} recording(s) • ${recap.screenshotCount} screenshot(s)",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun RecapKpi(
  label: String,
  value: String,
  color: androidx.compose.ui.graphics.Color,
) {
  Column {
    Text(label, style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = color)
  }
}

private fun formatSessionRecapShareText(recap: SessionRecapSummary, isIndo: Boolean): String {
  val dur = TimeAndSizeFormatters.formatDurationReadable(recap.durationMs, isIndo)
  return buildString {
    appendLine("DRX GAME TURBO PRO — Session Recap")
    appendLine("Game: ${recap.gameName} (${recap.packageName})")
    appendLine("Duration: $dur | Profile: ${recap.profileUsed.name}")
    appendLine("UI FPS: ${recap.avgUiFps?.toInt()?.toString() ?: "N/A"}")
    appendLine("Battery: ${recap.batteryStartPercent}% -> ${recap.batteryEndPercent}% | Peak Temp: ${String.format(Locale.US, "%.1f°C", recap.peakTempC)}")
    appendLine("Warnings: ${recap.performanceWarningsCount} | Network Changes: ${recap.networkEventsCount}")
    appendLine("Media Captured: ${recap.recordingCount} recordings, ${recap.screenshotCount} screenshots")
  }
}
