package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.model.LanguageMode
import com.example.data.model.LaunchAnimationQuality
import com.example.data.model.PermissionStateItem
import com.example.data.prefs.DrxUserPrefs
import com.example.i18n.LocalStrings
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxHairlineDivider
import com.example.ui.theme.DrxLogoBadge
import com.example.ui.theme.DrxNotificationBanner
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxSectionHeader
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxMainViewModel

@Composable
fun SettingsScreen(
  prefs: DrxUserPrefs,
  permissionStates: List<PermissionStateItem>,
  viewModel: DrxMainViewModel,
  onOpenSidebarSettings: () -> Unit,
  onOpenAbout: () -> Unit,
  onOpenAnnouncementCenter: () -> Unit = {},
  onOpenCustomization: () -> Unit = {},
  onOpenGameNotifications: () -> Unit = {},
  onOpenUpdateCenter: () -> Unit = {},
  onBack: (() -> Unit)? = null,
) {
  if (onBack != null) {
    BackHandler(onBack = onBack)
  }
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  val runtimePermLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      viewModel.refreshSystemStates()
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.settingsTitle,
        subtitle = strings.appName,
        onBack = onBack,
      )
    }

    // Appearance, Language & Edge Sidebar
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DrxSectionHeader(title = if (isIndo) "Tampilan & Antarmuka" else "Appearance & Interface")
        DrxGlassCard(cornerRadius = 20.dp) {
          SettingToggleRow(
            label = strings.darkModeLabel,
            checked = prefs.darkMode,
            onCheckedChange = { viewModel.setDarkMode(it) },
            modifier = Modifier.testTag("dark_mode_switch"),
          )

          DrxHairlineDivider(modifier = Modifier.padding(vertical = 10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(strings.languageLabel, style = MaterialTheme.typography.titleSmall)
            Text(
              text = prefs.language.displayName,
              style = MaterialTheme.typography.labelMedium,
              color = drxColors.neonCyan,
            )
          }
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LanguageMode.entries.forEach { mode ->
              DrxFilterPill(
                label = mode.displayName,
                selected = prefs.language == mode,
                onClick = { viewModel.setLanguage(mode) },
                modifier = Modifier.weight(1f).testTag("lang_${mode.code}"),
              )
            }
          }

          DrxHairlineDivider(modifier = Modifier.padding(vertical = 10.dp))
          SettingToggleRow(
            label = strings.animationsLabel,
            checked = prefs.animationsEnabled,
            onCheckedChange = { viewModel.setAnimationsEnabled(it) },
          )
          SettingToggleRow(
            label = strings.uiSoundsLabel,
            checked = prefs.uiSoundsEnabled,
            onCheckedChange = { viewModel.setUiSoundsEnabled(it) },
          )
          SettingToggleRow(
            label = strings.hapticFeedbackLabel,
            checked = prefs.hapticFeedbackEnabled,
            onCheckedChange = { viewModel.setHapticFeedbackEnabled(it) },
          )

          Spacer(Modifier.height(12.dp))
          DrxPrimaryButton(
            label = strings.customizationCenterTitle,
            icon = Icons.Default.Palette,
            onClick = onOpenCustomization,
            modifier = Modifier.fillMaxWidth().testTag("settings_customization_btn"),
          )

          Spacer(Modifier.height(8.dp))
          DrxSecondaryButton(
            label = strings.toolSidebarTitle,
            icon = Icons.Default.PictureInPictureAlt,
            onClick = onOpenSidebarSettings,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }

    // Game Launch Animation & Sound Settings
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DrxSectionHeader(title = if (isIndo) "Peluncuran Game" else "Game Launch Experience")
        DrxGlassCard(accentColor = drxColors.electricBlue, cornerRadius = 20.dp) {
          SettingToggleRow(
            label = strings.launchAnimEnabledLabel,
            checked = prefs.launchAnimationEnabled,
            onCheckedChange = { viewModel.setLaunchAnimationEnabled(it) },
            modifier = Modifier.testTag("launch_anim_switch"),
          )

          Spacer(Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(strings.launchAnimQualityLabel, style = MaterialTheme.typography.titleSmall)
            Text(
              text = strings.qualityLabel(prefs.launchAnimationQuality),
              style = MaterialTheme.typography.labelMedium,
              color = drxColors.neonCyan,
            )
          }
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LaunchAnimationQuality.entries.forEach { q ->
              DrxFilterPill(
                label = strings.qualityLabel(q),
                selected = prefs.launchAnimationQuality == q,
                onClick = { viewModel.setLaunchAnimationQuality(q) },
                modifier = Modifier.weight(1f).testTag("quality_${q.id}"),
              )
            }
          }

          Spacer(Modifier.height(10.dp))
          SettingToggleRow(
            label = strings.launchSoundEnabledLabel,
            checked = prefs.launchSoundEnabled,
            onCheckedChange = { viewModel.setLaunchSoundEnabled(it) },
            modifier = Modifier.testTag("launch_sound_switch"),
          )
        }
      }
    }

    // Screen Recorder Countdown Preference
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DrxSectionHeader(title = "DRX Pro Recorder")
        DrxGlassCard(cornerRadius = 20.dp) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = if (isIndo) "Hitung Mundur Rekam" else "Recorder Countdown Delay",
              style = MaterialTheme.typography.titleSmall,
            )
            Text(
              text = if (prefs.recorderCountdownSec == 0) (if (isIndo) "Mati (0s)" else "Off (0s)") else "${prefs.recorderCountdownSec}s",
              style = MaterialTheme.typography.labelMedium,
              color = drxColors.neonCyan,
            )
          }
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 3, 5).forEach { sec ->
              DrxFilterPill(
                label = if (sec == 0) (if (isIndo) "Mati" else "Off") else "${sec}s",
                selected = prefs.recorderCountdownSec == sec,
                onClick = { viewModel.setRecorderCountdownSec(sec) },
                modifier = Modifier.weight(1f).testTag("settings_recorder_countdown_${sec}s"),
              )
            }
          }
        }
      }
    }

    // Notification Settings & DRX Smart Announcement Center
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DrxSectionHeader(title = if (isIndo) "Notifikasi & Pengumuman" else "Notifications & Announcements")
        DrxGlassCard(cornerRadius = 20.dp) {
          SettingToggleRow(
            label = strings.boostNotifLabel,
            checked = prefs.boostNotificationsEnabled,
            onCheckedChange = { viewModel.setBoostNotifications(it) },
          )
          SettingToggleRow(
            label = strings.timerNotifLabel,
            checked = prefs.timerNotificationsEnabled,
            onCheckedChange = { viewModel.setTimerNotifications(it) },
          )
        Spacer(Modifier.height(12.dp))
        DrxGlassCard(
          accentColor = drxColors.electricBlue,
          highlighted = prefs.announcementsEnabled,
          cornerRadius = 18.dp,
          onClick = onOpenAnnouncementCenter,
          modifier = Modifier.testTag("settings_announcement_center_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.NotificationsActive,
              contentDescription = strings.announcementCenterTitle,
              tint = drxColors.neonCyan,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.announcementCenterTitle, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                DrxStatusPill(
                  text = if (prefs.announcementsEnabled) "ON" else "OFF",
                  color =
                    if (prefs.announcementsEnabled) drxColors.optimalGreen
                    else drxColors.warningAmber,
                )
              }
              Spacer(Modifier.height(2.dp))
              Text(
                text =
                  if (isIndo) "Otomatis Acak • WorkManager"
                  else "Automatic Random • WorkManager",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = strings.announcementCenterTitle,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Spacer(Modifier.height(10.dp))
        DrxGlassCard(
          accentColor = drxColors.neonCyan,
          highlighted = prefs.gameNotificationViewerEnabled,
          cornerRadius = 18.dp,
          onClick = onOpenGameNotifications,
          modifier = Modifier.testTag("settings_game_notifications_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.Notifications,
              contentDescription = strings.gameNotificationsTitle,
              tint = drxColors.neonCyan,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(strings.gameNotificationsTitle, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                DrxStatusPill(
                  text = if (prefs.gameNotificationViewerEnabled) "ON" else "OFF",
                  color =
                    if (prefs.gameNotificationViewerEnabled) drxColors.optimalGreen
                    else drxColors.warningAmber,
                )
              }
              Spacer(Modifier.height(2.dp))
              Text(
                text = strings.gameNotificationsSub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = strings.gameNotificationsTitle,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
    }

    // Permission Manager
    item {
      DrxGlassCard(cornerRadius = 20.dp) {
        DrxSectionHeader(title = strings.permissionManagerTitle)
        Spacer(Modifier.height(10.dp))
        permissionStates.forEachIndexed { idx, perm ->
          if (idx > 0) {
            DrxHairlineDivider(modifier = Modifier.padding(vertical = 8.dp))
          }
          val title =
            when (perm.key) {
              "overlay" -> "Display Over Other Apps (Floating Sidebar)"
              "write_settings" -> "Modify System Settings (Brightness / Rotation)"
              "dnd" -> "Do Not Disturb Policy Access"
              "usage_stats" -> "Usage Stats (Playtime & App Analytics)"
              "record_audio" -> "Microphone (Voice Studio & Preview)"
              else -> "Notifications (Boost & Timer Alerts)"
            }
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(title, style = MaterialTheme.typography.titleSmall)
              Spacer(Modifier.height(4.dp))
              DrxStatusPill(
                text = if (perm.granted) strings.permissionGranted else strings.grantPermission,
                color = if (perm.granted) drxColors.optimalGreen else drxColors.warningAmber,
              )
            }
            if (!perm.granted) {
              Spacer(Modifier.width(8.dp))
              DrxSecondaryButton(
                label = strings.grantPermission,
                onClick = {
                  when (perm.key) {
                    "overlay" -> viewModel.tweaksManager.openOverlayPermissionSettings()
                    "write_settings" -> viewModel.tweaksManager.openWriteSettingsPermission()
                    "dnd" -> viewModel.tweaksManager.openDndPolicySettings()
                    "usage_stats" -> viewModel.tweaksManager.openUsageAccessSettings()
                    "record_audio" ->
                      try {
                        runtimePermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                      } catch (_: Throwable) {
                      }
                    "notifications" ->
                      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        try {
                          runtimePermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } catch (_: Throwable) {
                        }
                      }
                  }
                },
                modifier = Modifier.height(38.dp),
              )
            }
          }
        }
      }
    }

    // Privacy & Reset
    item {
      DrxNotificationBanner(
        title = strings.privacySectionTitle,
        body = strings.privacyZeroDataNote,
        accentColor = drxColors.optimalGreen,
        icon = Icons.Default.Shield,
      )
    }

    item {
      DrxGlassCard(cornerRadius = 20.dp) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          DrxSecondaryButton(
            label = strings.clearHistoryBtn,
            onClick = { viewModel.clearHistory() },
            modifier = Modifier.weight(1f),
          )
          DrxPrimaryButton(
            label = strings.resetSettingsBtn,
            accentColor = drxColors.alertCrimson,
            onClick = { viewModel.resetAllSettings() },
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    // About Application & Developer Profile Entry
    item {
      DrxGlassCard(
        accentColor = drxColors.electricBlue,
        cornerRadius = 20.dp,
        onClick = onOpenAbout,
        modifier = Modifier.testTag("settings_about_card"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          DrxLogoBadge(size = 48.dp)
          Spacer(Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(strings.aboutTitle, style = MaterialTheme.typography.labelMedium, color = drxColors.neonCyan)
            Text(
              "DRX GAME TURBO PRO v${BuildConfig.VERSION_NAME}",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
              "DerreX • Supported by Nanay",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = strings.aboutTitle,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // DRX Update Center Entry
    item {
      DrxGlassCard(
        accentColor = drxColors.neonCyan,
        cornerRadius = 20.dp,
        onClick = onOpenUpdateCenter,
        modifier = Modifier.testTag("settings_update_center_card"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Default.SystemUpdate,
            contentDescription = strings.updateCenterTitle,
            tint = drxColors.neonCyan,
          )
          Spacer(Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(strings.updateCenterTitle, style = MaterialTheme.typography.titleMedium)
            Text(
              text = "v${BuildConfig.VERSION_NAME} (Build #${BuildConfig.VERSION_CODE}) • ${strings.updateCenterSub}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = strings.updateCenterTitle,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
