package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LanguageMode
import com.example.i18n.LocalStrings
import com.example.ui.theme.DrxCustomSwitch
import com.example.ui.theme.DrxDangerButton
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
fun GamingFocusModeScreen(
  viewModel: DrxMainViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val prefs by viewModel.userPrefs.collectAsStateWithLifecycle()
  val focusState by viewModel.focusModeState.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  val dndPolicyGranted = viewModel.tweaksManager.isDndPolicyGranted()
  val writeSettingsGranted = viewModel.tweaksManager.canWriteSystemSettings()

  var requestDnd by remember { mutableStateOf(dndPolicyGranted) }
  var selectedTimeoutMs by remember { mutableStateOf<Int?>(300_000) } // 5m default if granted

  val targetSeconds = (focusState.targetMinutes * 60L).coerceAtLeast(60L)
  val progress = (focusState.elapsedSeconds.toFloat() / targetSeconds.toFloat()).coerceIn(0f, 1f)
  val mins = focusState.elapsedSeconds / 60L
  val secs = focusState.elapsedSeconds % 60L

  LazyColumn(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("gaming_focus_screen"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.focusModeTitle,
        subtitle = strings.focusModeSub,
        onBack = onBack,
      )
    }

    item {
      val ringColor = if (focusState.isActive) drxColors.optimalGreen else drxColors.electricBlue
      DrxGlassCard(accentColor = ringColor, highlighted = focusState.isActive, cornerRadius = 24.dp) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (focusState.isActive) (if (isIndo) "SESI FOKUS AKTIF" else "FOCUS SESSION ACTIVE") else (if (isIndo) "SIAP FOKUS" else "FOCUS READY"),
            style = MaterialTheme.typography.labelLarge,
            color = ringColor,
          )
          DrxStatusPill(
            text = if (focusState.dndActuallyEnabled) "Priority DND ON" else "DND Standby",
            color = if (focusState.dndActuallyEnabled) drxColors.optimalGreen else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Spacer(Modifier.height(16.dp))
        Box(
          modifier = Modifier.fillMaxWidth(),
          contentAlignment = Alignment.Center,
        ) {
          Box(modifier = Modifier.size(164.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
              val stroke = 10.dp.toPx()
              drawArc(
                color = drxColors.elevatedSurface,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
              )
              drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
              )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = String.format(Locale.US, "%02d:%02d", mins, secs),
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onBackground,
              )
              Text(
                text = if (isIndo) "Target ${focusState.targetMinutes} mnt" else "Target ${focusState.targetMinutes} min",
                style = DrxTypographyTokens.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        Spacer(Modifier.height(16.dp))
        Text(
          text = if (isIndo) "Target Durasi Fokus" else "Focus Duration Target",
          style = MaterialTheme.typography.labelLarge,
          color = drxColors.neonCyan,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(listOf(15, 30, 45, 60, 90)) { m ->
            DrxFilterPill(
              label = "${m}m",
              selected = focusState.targetMinutes == m,
              onClick = { if (!focusState.isActive) viewModel.setFocusTargetMinutes(m) },
            )
          }
        }

        Spacer(Modifier.height(16.dp))
        if (focusState.isActive) {
          DrxDangerButton(
            label = if (isIndo) "Hentikan & Pulihkan Pengaturan" else "Stop Focus & Restore",
            icon = Icons.Default.Stop,
            onClick = { viewModel.stopFocusMode(restoreDnd = true, restoreTimeout = true) },
            modifier = Modifier.fillMaxWidth().testTag("focus_mode_toggle_button"),
          )
        } else {
          DrxPrimaryButton(
            label = if (isIndo) "Mulai Mode Fokus Gaming" else "Start Gaming Focus Mode",
            icon = Icons.Default.PlayArrow,
            accentColor = drxColors.electricBlue,
            onClick = { viewModel.startFocusMode(enableDnd = requestDnd, extendTimeoutMs = selectedTimeoutMs) },
            modifier = Modifier.fillMaxWidth().testTag("focus_mode_toggle_button"),
          )
        }
      }
    }

    // Priority DND (Alarm-Safe) Control Card
    item {
      DrxGlassCard(accentColor = drxColors.neonCyan, cornerRadius = 20.dp) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.DoNotDisturbOn, contentDescription = null, tint = drxColors.neonCyan)
            Spacer(Modifier.width(10.dp))
            Column {
              Text(
                text = if (isIndo) "Priority Do Not Disturb" else "Priority Do Not Disturb",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
              )
              Text(
                text =
                  if (isIndo) {
                    "Memakai filter Prioritas Android: alarm, jam pengingat & kontak darurat tetap diizinkan."
                  } else {
                    "Uses Android Priority interruption filter: alarms and emergency calls remain allowed."
                  },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Spacer(Modifier.width(8.dp))
          DrxCustomSwitch(
            checked = requestDnd && dndPolicyGranted,
            enabled = dndPolicyGranted && !focusState.isActive,
            onCheckedChange = { requestDnd = it },
          )
        }

        if (!dndPolicyGranted) {
          Spacer(Modifier.height(10.dp))
          DrxSecondaryButton(
            label = if (isIndo) "Izinkan Akses DND" else "Grant DND Access",
            icon = Icons.Default.Security,
            onClick = { viewModel.tweaksManager.openDndPolicySettings() },
            modifier = Modifier.fillMaxWidth().testTag("focus_grant_dnd_button"),
          )
        }
      }
    }

    // Screen Timeout Control Card
    item {
      DrxGlassCard(cornerRadius = 20.dp) {
        Text(
          text = strings.screenTimeoutLabel,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text =
            if (isIndo) "Perpanjang waktu mati layar selama sesi fokus lalu pulihkan saat berhenti."
            else "Extend screen-off timeout while focus mode is active and restore on stop.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(30_000 to "30s", 120_000 to "2m", 300_000 to "5m", 600_000 to "10m").forEach { (ms, label) ->
            DrxFilterPill(
              label = label,
              selected = selectedTimeoutMs == ms,
              onClick = { if (!focusState.isActive) selectedTimeoutMs = ms },
              modifier = Modifier.weight(1f),
            )
          }
        }
        if (!writeSettingsGranted) {
          Spacer(Modifier.height(10.dp))
          DrxSecondaryButton(
            label = if (isIndo) "Izinkan Ubah Pengaturan Sistem" else "Grant Modify System Settings",
            onClick = { viewModel.tweaksManager.openWriteSettingsPermission() },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }

    // Guidance & Last Summary
    item {
      DrxGlassCard(cornerRadius = 20.dp) {
        Row(verticalAlignment = Alignment.Top) {
          Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = drxColors.electricBlue)
          Spacer(Modifier.width(10.dp))
          Column {
            Text(
              text = if (isIndo) "Panduan Minimalkan Gangguan" else "Interruption Control Guide",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text =
                if (isIndo) {
                  "• DRX tidak memblokir notifikasi sistem secara paksa tanpa izin DND.\n" +
                    "• Alarm & panggilan darurat tidak pernah dibisukan oleh Mode Fokus.\n" +
                    "• Gunakan Game Notification Viewer setelah sesi selesai untuk meninjau notifikasi yang masuk."
                } else {
                  "• DRX never alters system notifications silently without explicit DND Policy permission.\n" +
                    "• Alarms & emergency calls are always preserved by Priority DND.\n" +
                    "• Review caught game alerts anytime in Game Notification Viewer after finishing."
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    if (prefs.lastFocusSummaryText.isNotBlank()) {
      item {
        DrxGlassCard(accentColor = drxColors.optimalGreen, cornerRadius = 18.dp) {
          Text(
            text = if (isIndo) "Ringkasan Sesi Fokus Terakhir" else "Last Focus Session Summary",
            style = MaterialTheme.typography.labelLarge,
            color = drxColors.optimalGreen,
          )
          Spacer(Modifier.height(4.dp))
          Text(
            text = prefs.lastFocusSummaryText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}
