package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GlobalGamingStatistics
import com.example.data.model.LanguageMode
import com.example.data.model.MediaItemType
import com.example.i18n.LocalStrings
import com.example.system.TimeAndSizeFormatters
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxHairlineDivider
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSectionHeader
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxNotificationsViewModel

@Composable
fun GameActivityCenterScreen(
  notificationsViewModel: DrxNotificationsViewModel,
  globalStats: GlobalGamingStatistics,
  onNavigateRoute: (String) -> Unit,
  onOpenGameStats: (String) -> Unit,
  onBack: (() -> Unit)? = null,
) {
  if (onBack != null) {
    BackHandler(onBack = onBack)
  }
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val prefs by notificationsViewModel.userPrefs.collectAsStateWithLifecycle()
  val notifications by notificationsViewModel.allNotifications.collectAsStateWithLifecycle()
  val reminders by notificationsViewModel.allReminders.collectAsStateWithLifecycle()
  val recentMedia by notificationsViewModel.recentMedia.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  LaunchedEffect(Unit) {
    notificationsViewModel.refreshRecentMedia()
  }

  val latestVideo = remember(recentMedia) { recentMedia.firstOrNull { it.type == MediaItemType.VIDEO } }
  val latestShot = remember(recentMedia) { recentMedia.firstOrNull { it.type == MediaItemType.SCREENSHOT } }
  val unreadCount = remember(notifications) { notifications.count { !it.isRead } }
  val eventCount = remember(notifications) { notifications.count { it.eventCategory != "GENERAL" } }

  LazyColumn(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("game_activity_center_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.activityCenterTitle,
        subtitle = strings.activityCenterSub,
        onBack = onBack,
      )
    }

    // Playtime & Recently Played Summary
    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DrxGlassCard(
          modifier = Modifier.weight(1f).testTag("activity_total_playtime_card"),
          accentColor = drxColors.electricBlue,
          onClick = { onNavigateRoute(ToolRoutes.STATS) },
        ) {
          Icon(Icons.Default.BarChart, contentDescription = null, tint = drxColors.electricBlue, modifier = Modifier.size(20.dp))
          Spacer(Modifier.height(6.dp))
          Text(strings.totalPlaytime, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(
            text = TimeAndSizeFormatters.formatDurationReadable(globalStats.totalPlaytimeMs, isIndo),
            style = DrxTypographyTokens.statNumberCompact,
            color = drxColors.neonCyan,
            maxLines = 1,
          )
          Text(
            text = "${globalStats.totalSessionsCount} ${strings.totalSessions.lowercase()}",
            style = DrxTypographyTokens.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        DrxGlassCard(
          modifier = Modifier.weight(1f).testTag("activity_last_played_card"),
          accentColor = drxColors.optimalGreen,
          onClick = {
            val pkg = globalStats.lastPlayedPkg
            if (pkg != null) onOpenGameStats(pkg) else onNavigateRoute(ToolRoutes.STATS)
          },
        ) {
          Icon(Icons.Default.SportsEsports, contentDescription = null, tint = drxColors.optimalGreen, modifier = Modifier.size(20.dp))
          Spacer(Modifier.height(6.dp))
          Text(strings.lastPlayedGameLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(
            text = globalStats.lastPlayedGameName ?: strings.notAvailableLabel,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text =
              globalStats.lastPlayedAtMs?.let { TimeAndSizeFormatters.formatDateTimeWithZone(it, isIndo) }
                ?: strings.lastPlayedNever,
            style = DrxTypographyTokens.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    // Game Notifications Summary
    item {
      DrxGlassCard(
        accentColor = drxColors.neonCyan,
        highlighted = unreadCount > 0,
        onClick = { onNavigateRoute(ToolRoutes.GAME_NOTIFICATIONS) },
        modifier = Modifier.testTag("activity_notifications_card"),
      ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Notifications, contentDescription = null, tint = drxColors.neonCyan, modifier = Modifier.size(22.dp))
          Spacer(Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(strings.gameNotificationsTitle, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
              if (unreadCount > 0) {
                Spacer(Modifier.width(8.dp))
                DrxStatusPill(text = "$unreadCount", color = drxColors.neonCyan)
              }
            }
            Text(
              text =
                if (notifications.isEmpty()) strings.notifEmptyTitle
                else "${notifications.first().gameName}: ${notifications.first().title}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }

    // Game Events & Reminders Summary
    item {
      DrxGlassCard(
        accentColor = drxColors.electricBlue,
        onClick = { onNavigateRoute(ToolRoutes.GAME_EVENTS) },
        modifier = Modifier.testTag("activity_events_card"),
      ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Event, contentDescription = null, tint = drxColors.electricBlue, modifier = Modifier.size(22.dp))
          Spacer(Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(strings.gameEventsTitle, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
              text =
                if (isIndo) "$eventCount event terklasifikasi • ${reminders.count { !it.isCompleted }} pengingat aktif"
                else "$eventCount classified events • ${reminders.count { !it.isCompleted }} active reminders",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }

    // Recent Screen Recording & Screenshot
    item {
      DrxSectionHeader(
        title = if (isIndo) "Rekaman & Tangkapan Layar Terbaru" else "Recent Recording & Screenshot",
        actionLabel = if (isIndo) "Galeri" else "Gallery",
        onAction = { onNavigateRoute(ToolRoutes.MEDIA_GALLERY) },
      )
      Spacer(Modifier.height(8.dp))
      DrxGlassCard {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .clickable {
                if (latestVideo != null) notificationsViewModel.openMediaFile(latestVideo)
                else onNavigateRoute(ToolRoutes.SCREEN_RECORDER)
              }
              .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = drxColors.alertCrimson, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(if (isIndo) "Screen Recording Terbaru" else "Latest Screen Recording", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
              text = latestVideo?.displayName ?: (if (isIndo) "Belum ada rekaman MP4" else "No MP4 recordings yet"),
              style = MaterialTheme.typography.titleSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (latestVideo != null) {
              Text(
                text = "${TimeAndSizeFormatters.formatBytesAuto(latestVideo.sizeBytes)} • ${TimeAndSizeFormatters.formatDateTimeWithZone(latestVideo.timestampMs, isIndo)}",
                style = DrxTypographyTokens.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          if (latestVideo != null) {
            DrxStatusPill(
              text = if (isIndo) "Buka" else "Open",
              color = drxColors.neonCyan,
              modifier = Modifier,
            )
          }
        }

        DrxHairlineDivider(modifier = Modifier.padding(vertical = 8.dp))

        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .clickable {
                if (latestShot != null) notificationsViewModel.openMediaFile(latestShot)
                else onNavigateRoute(ToolRoutes.MEDIA_GALLERY)
              }
              .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Default.CameraAlt, contentDescription = null, tint = drxColors.neonCyan, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(if (isIndo) "Screenshot Terbaru" else "Latest Screenshot", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
              text = latestShot?.displayName ?: (if (isIndo) "Belum ada tangkapan layar" else "No screenshots captured yet"),
              style = MaterialTheme.typography.titleSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (latestShot != null) {
              Text(
                text = "${TimeAndSizeFormatters.formatBytesAuto(latestShot.sizeBytes)} • ${TimeAndSizeFormatters.formatDateTimeWithZone(latestShot.timestampMs, isIndo)}",
                style = DrxTypographyTokens.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Icon(Icons.Default.PhotoLibrary, contentDescription = "Open Gallery", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }

    // Quick Insights & Update Center shortcuts
    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DrxGlassCard(
          modifier = Modifier.weight(1f),
          onClick = { onNavigateRoute(ToolRoutes.SESSION_RECAP) },
        ) {
          Text(strings.sessionRecapTitle, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
          Spacer(Modifier.height(2.dp))
          Text(if (isIndo) "Ringkasan sesi terakhir" else "Latest session summary", style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DrxGlassCard(
          modifier = Modifier.weight(1f),
          onClick = { onNavigateRoute(ToolRoutes.NOTIFICATION_INSIGHTS) },
        ) {
          Text(strings.notificationInsightsTitle, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
          Spacer(Modifier.height(2.dp))
          Text(if (isIndo) "Grafik & jam puncak" else "Charts & peak hours", style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      Spacer(Modifier.height(10.dp))
      DrxGlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onNavigateRoute(ToolRoutes.UPDATE_CENTER) },
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = drxColors.electricBlue, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(strings.updateCenterTitle, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(2.dp))
        Text(if (isIndo) "Cek & verifikasi APK" else "Check & verify APK", style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}
