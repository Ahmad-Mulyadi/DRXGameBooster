package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.db.AnnouncementHistoryEntity
import com.example.data.model.LanguageMode
import com.example.data.prefs.DrxUserPrefs
import com.example.i18n.LocalStrings
import com.example.ui.theme.DrxCustomSwitch
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxHairlineDivider
import com.example.ui.theme.DrxNotificationBanner
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxSectionHeader
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxMainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AnnouncementCenterScreen(
  prefs: DrxUserPrefs,
  history: List<AnnouncementHistoryEntity>,
  viewModel: DrxMainViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val context = LocalContext.current
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  var notifPermissionGranted by remember {
    mutableStateOf(
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      } else {
        true
      }
    )
  }

  val notifPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      notifPermissionGranted = granted
      if (granted) {
        viewModel.setAnnouncementsEnabled(true)
      }
    }

  LazyColumn(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("announcement_center_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.announcementCenterTitle,
        subtitle = strings.announcementCenterSubtitle,
        onBack = onBack,
        trailing = {
          DrxStatusPill(
            text = if (prefs.announcementsEnabled && notifPermissionGranted) "ON" else "OFF",
            color =
              if (prefs.announcementsEnabled && notifPermissionGranted) drxColors.optimalGreen
              else drxColors.warningAmber,
          )
        },
      )
    }

    if (!notifPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      item {
        DrxGlassCard(accentColor = drxColors.warningAmber, highlighted = true) {
          Text(
            text =
              if (isIndo) "Izin Notifikasi Android 13+" else "Android 13+ Notification Permission",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(Modifier.height(6.dp))
          Text(
            text =
              if (isIndo) {
                "DRX Smart Announcement membutuhkan izin POST_NOTIFICATIONS agar tips & pengumuman dapat tampil resmi di panel notifikasi Android."
              } else {
                "DRX Smart Announcement requires POST_NOTIFICATIONS permission to display automated tips in the Android notification shade."
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(12.dp))
          DrxPrimaryButton(
            label = strings.grantPermission,
            icon = Icons.Default.NotificationsActive,
            onClick = { notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            modifier = Modifier.fillMaxWidth().testTag("grant_announcement_notif_btn"),
          )
        }
      }
    }

    // Automatic Random Announcement Control Card (Random Default Only)
    item {
      DrxGlassCard(
        accentColor = drxColors.electricBlue,
        highlighted = prefs.announcementsEnabled,
        cornerRadius = 22.dp,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(strings.enableAnnouncementsLabel, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text(
              text =
                if (isIndo) {
                  "Berjalan otomatis dengan jadwal & variasi pesan acak tanpa pengaturan manual."
                } else {
                  "Runs automatically with randomized delivery times and non-repeating messages."
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          DrxCustomSwitch(
            checked = prefs.announcementsEnabled,
            onCheckedChange = { enabled ->
              if (enabled && !notifPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              } else {
                viewModel.setAnnouncementsEnabled(enabled)
              }
            },
            modifier = Modifier.testTag("toggle_announcements_switch"),
          )
        }

        Spacer(Modifier.height(14.dp))
        DrxPrimaryButton(
          label = strings.previewNotificationBtn,
          icon = Icons.Default.NotificationsActive,
          onClick = {
            if (!notifPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
              viewModel.sendPreviewAnnouncement()
            }
          },
          modifier = Modifier.fillMaxWidth().testTag("preview_announcement_button"),
        )
      }
    }

    // Notification Delivery Behavior (Sound & Vibration)
    item {
      DrxGlassCard(cornerRadius = 20.dp) {
        SettingToggleRow(
          label = strings.soundLabel,
          checked = prefs.announcementSoundEnabled,
          onCheckedChange = { viewModel.setAnnouncementSound(it) },
        )
        DrxHairlineDivider(modifier = Modifier.padding(vertical = 6.dp))
        SettingToggleRow(
          label = strings.vibrationLabel,
          checked = prefs.announcementVibrationEnabled,
          onCheckedChange = { viewModel.setAnnouncementVibration(it) },
        )
      }
    }

    item {
      DrxGlassCard(cornerRadius = 20.dp) {
        DrxNotificationBanner(
          title = "WorkManager & Background Policy",
          body = strings.workManagerScheduleNotice,
          accentColor = drxColors.neonCyan,
          icon = Icons.Default.Info,
        )
        Spacer(Modifier.height(12.dp))
        DrxSecondaryButton(
          label = "App Battery & Notification Settings",
          icon = Icons.Default.BatteryChargingFull,
          onClick = {
            viewModel.tweaksManager.openSystemSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
          },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        DrxSectionHeader(
          title = strings.announcementHistoryLabel,
          modifier = Modifier.weight(1f),
        )
        if (history.isNotEmpty()) {
          DrxSecondaryButton(
            label = strings.clearAnnouncementHistoryBtn,
            icon = Icons.Default.DeleteSweep,
            onClick = { viewModel.clearAnnouncementHistory() },
            modifier = Modifier.height(38.dp).testTag("clear_announcement_history_btn"),
          )
        }
      }
    }

    if (history.isEmpty()) {
      item {
        DrxGlassCard {
          Text(
            text = strings.noAnnouncementHistoryText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      items(history, key = { it.id }) { item ->
        val timeText =
          SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(item.deliveredAt))
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = drxColors.cardSurface,
          border = BorderStroke(1.dp, drxColors.glassBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                color = drxColors.neonCyan,
              )
              Text(
                timeText,
                style = DrxTypographyTokens.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Spacer(Modifier.height(4.dp))
            Text(
              item.body,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }
    }
  }
}
