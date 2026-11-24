package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AlarmAdd
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.GameNotificationEntity
import com.example.data.model.GameEventCategory
import com.example.data.model.InstalledAppItem
import com.example.data.prefs.DrxUserPrefs
import com.example.i18n.LocalStrings
import com.example.system.DrxNotificationIntentCache
import com.example.system.TimeAndSizeFormatters
import com.example.ui.theme.DrxCustomModal
import com.example.ui.theme.DrxHairlineDivider
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxNotificationsViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameNotificationDetailModal(
  item: GameNotificationEntity,
  isIndo: Boolean,
  viewModel: DrxNotificationsViewModel,
  onDismiss: () -> Unit,
  onScheduleReminder: (GameNotificationEntity) -> Unit,
) {
  val context = LocalContext.current
  val drxColors = LocalDrxColors.current
  val cat = GameEventCategory.fromId(item.eventCategory)
  val fullBody = item.bigText.ifBlank { item.text }.ifBlank { "-" }
  val hasLiveIntent = remember(item.notificationKey) { DrxNotificationIntentCache.get(item.notificationKey) != null }

  val thumbBmp by
    produceState<ImageBitmap?>(initialValue = null, key1 = item.thumbnailPath) {
      value =
        if (item.thumbnailPath.isBlank()) null
        else withContext(Dispatchers.IO) {
          runCatching {
            val f = File(item.thumbnailPath)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
          }.getOrNull()
        }
    }

  DrxCustomModal(title = item.gameName, onDismiss = onDismiss) {
    Column(
      modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        DrxStatusPill(
          text = if (isIndo) cat.labelId else cat.labelEn,
          color = drxColors.neonCyan,
        )
        Text(
          text = TimeAndSizeFormatters.formatDateTimeWithZone(item.timestampMs, isIndo),
          style = DrxTypographyTokens.caption,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Text(
        text = item.title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
      )

      if (item.subText.isNotBlank()) {
        Text(
          text = item.subText,
          style = DrxTypographyTokens.caption,
          color = drxColors.neonCyan,
        )
      }

      thumbBmp?.let { bmp ->
        Image(
          bitmap = bmp,
          contentDescription = item.title,
          contentScale = ContentScale.Crop,
          modifier =
            Modifier
              .fillMaxWidth()
              .height(140.dp)
              .clip(RoundedCornerShape(14.dp))
              .border(1.dp, drxColors.glassBorder, RoundedCornerShape(14.dp)),
        )
      }

      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(drxColors.elevatedSurface)
            .border(1.dp, drxColors.glassBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
      ) {
        Text(
          text = fullBody,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }

      Text(
        text = "${item.packageName} • ${if (item.isActiveInShade) (if (isIndo) "Aktif di Panel" else "In Shade") else (if (isIndo) "Riwayat Tersimpan" else "Archived")}",
        style = DrxTypographyTokens.caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        DrxFilterPill(
          label = if (item.isFavorite) (if (isIndo) "Favorit ★" else "Favorited ★") else (if (isIndo) "Favorit" else "Favorite"),
          selected = item.isFavorite,
          onClick = { viewModel.toggleFavorite(item) },
          modifier = Modifier.testTag("notif_detail_fav_btn"),
        )
        DrxFilterPill(
          label = if (item.isImportant) (if (isIndo) "Penting !" else "Important !") else (if (isIndo) "Penting" else "Important"),
          selected = item.isImportant,
          onClick = { viewModel.toggleImportant(item) },
          modifier = Modifier.testTag("notif_detail_important_btn"),
        )
        DrxFilterPill(
          label = if (item.isRead) (if (isIndo) "Tandai Belum Dibaca" else "Mark Unread") else (if (isIndo) "Tandai Dibaca" else "Mark Read"),
          selected = item.isRead,
          onClick = { viewModel.markRead(item.id, !item.isRead) },
          modifier = Modifier.testTag("notif_detail_read_btn"),
        )
      }

      DrxHairlineDivider()

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DrxPrimaryButton(
          label = if (isIndo) "Buka Game" else "Open Game",
          icon = Icons.Default.PlayArrow,
          onClick = {
            if (!viewModel.openGameApp(item.packageName)) {
              Toast.makeText(context, if (isIndo) "Game tidak terpasang" else "Game not installed", Toast.LENGTH_SHORT).show()
            } else {
              onDismiss()
            }
          },
          modifier = Modifier.weight(1f).testTag("notif_detail_open_game_btn"),
        )
        DrxSecondaryButton(
          label = if (isIndo) "Pengingat" else "Remind Me",
          icon = Icons.Default.AlarmAdd,
          onClick = { onScheduleReminder(item) },
          modifier = Modifier.weight(1f).testTag("notif_detail_remind_btn"),
        )
      }

      if (hasLiveIntent) {
        DrxSecondaryButton(
          label = if (isIndo) "Buka Halaman Asli Notifikasi" else "Open Notification Target",
          icon = Icons.AutoMirrored.Filled.OpenInNew,
          onClick = {
            if (!viewModel.openOriginalNotificationActivity(item)) {
              Toast.makeText(
                context,
                if (isIndo) "Aksi notifikasi asli sudah kedaluwarsa di OS" else "Original notification PendingIntent expired",
                Toast.LENGTH_SHORT,
              ).show()
            } else {
              onDismiss()
            }
          },
          modifier = Modifier.fillMaxWidth().testTag("notif_detail_open_intent_btn"),
        )
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DrxSecondaryButton(
          label = if (isIndo) "Salin Teks" else "Copy",
          icon = Icons.Default.ContentCopy,
          onClick = {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText(item.gameName, "${item.title}\n$fullBody"))
            Toast.makeText(context, if (isIndo) "Disalin ke clipboard" else "Copied to clipboard", Toast.LENGTH_SHORT).show()
          },
          modifier = Modifier.weight(1f).testTag("notif_detail_copy_btn"),
        )
        DrxSecondaryButton(
          label = if (isIndo) "Bagikan" else "Share",
          icon = Icons.Default.Share,
          onClick = {
            runCatching {
              context.startActivity(Intent.createChooser(viewModel.buildShareTextIntent(item), item.gameName))
            }
          },
          modifier = Modifier.weight(1f).testTag("notif_detail_share_btn"),
        )
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DrxSecondaryButton(
          label = if (isIndo) "Kecualikan" else "Exclude App",
          icon = Icons.Default.Block,
          onClick = {
            viewModel.excludePackage(item.packageName, removeExistingHistory = false)
            onDismiss()
          },
          modifier = Modifier.weight(1f),
        )
        DrxPrimaryButton(
          label = if (isIndo) "Hapus Riwayat" else "Delete",
          icon = Icons.Default.Delete,
          accentColor = drxColors.alertCrimson,
          onClick = {
            viewModel.deleteNotification(item)
            onDismiss()
          },
          modifier = Modifier.weight(1f).testTag("notif_detail_delete_btn"),
        )
      }
    }
  }
}

@Composable
fun NotificationPrivacyControlModal(
  prefs: DrxUserPrefs,
  installedApps: List<InstalledAppItem>,
  isIndo: Boolean,
  viewModel: DrxNotificationsViewModel,
  onDismiss: () -> Unit,
) {
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  var showAddAppPicker by remember { mutableStateOf(false) }

  DrxCustomModal(title = strings.notifPrivacyTitle, onDismiss = onDismiss) {
    Column(
      modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      DrxStatusPill(text = "100% ON-DEVICE • ROOM", color = drxColors.optimalGreen)
      Text(
        text = strings.notifPrivacyNote,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      SettingToggleRow(
        label = if (isIndo) "Aktifkan Pencatatan Notifikasi Game" else "Enable Game Notification Capture",
        checked = prefs.gameNotificationViewerEnabled,
        onCheckedChange = { viewModel.setViewerEnabled(it) },
        modifier = Modifier.testTag("notif_privacy_enable_switch"),
      )

      DrxHairlineDivider()

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = if (isIndo) "Aplikasi Game Kustom (${prefs.customNotificationGamePackages.size})" else "Custom Included Games (${prefs.customNotificationGamePackages.size})",
          style = MaterialTheme.typography.titleSmall,
        )
        DrxFilterPill(
          label = if (showAddAppPicker) (if (isIndo) "Tutup" else "Done") else (if (isIndo) "+ Tambah" else "+ Add"),
          selected = showAddAppPicker,
          onClick = { showAddAppPicker = !showAddAppPicker },
        )
      }

      if (showAddAppPicker) {
        LazyColumn(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(170.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(drxColors.elevatedSurface)
              .padding(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          items(installedApps.take(40), key = { it.packageName }) { app ->
            val included = prefs.customNotificationGamePackages.contains(app.packageName)
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .clickable {
                    if (included) viewModel.removeCustomGamePackage(app.packageName)
                    else viewModel.addCustomGamePackage(app.packageName)
                  }
                  .padding(horizontal = 8.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
              if (included) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = drxColors.optimalGreen, modifier = Modifier.size(18.dp))
              }
            }
          }
        }
      }

      if (prefs.excludedNotificationPackages.isNotEmpty()) {
        DrxHairlineDivider()
        Text(
          text = if (isIndo) "Aplikasi Dikecualikan (${prefs.excludedNotificationPackages.size})" else "Excluded Packages (${prefs.excludedNotificationPackages.size})",
          style = MaterialTheme.typography.titleSmall,
        )
        prefs.excludedNotificationPackages.forEach { pkg ->
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = drxColors.elevatedSurface,
            border = BorderStroke(1.dp, drxColors.glassBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(pkg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(
                text = if (isIndo) "Pulihkan" else "Restore",
                style = MaterialTheme.typography.labelMedium,
                color = drxColors.neonCyan,
                modifier = Modifier.clickable { viewModel.unexcludePackage(pkg) }.padding(horizontal = 6.dp),
              )
            }
          }
        }
      }

      DrxHairlineDivider()

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DrxSecondaryButton(
          label = if (isIndo) "Akses Notifikasi" else "OS Access",
          onClick = { viewModel.openNotificationListenerSettings() },
          modifier = Modifier.weight(1f),
        )
        DrxPrimaryButton(
          label = if (isIndo) "Bersihkan Semua" else "Clear History",
          icon = Icons.Default.Delete,
          accentColor = drxColors.alertCrimson,
          onClick = {
            viewModel.clearAllNotificationHistory()
            onDismiss()
          },
          modifier = Modifier.weight(1f).testTag("notif_clear_all_btn"),
        )
      }
    }
  }
}

@Composable
fun ScheduleReminderQuickModal(
  item: GameNotificationEntity,
  isIndo: Boolean,
  onConfirmMinutes: (Int) -> Unit,
  onDismiss: () -> Unit,
) {
  val drxColors = LocalDrxColors.current
  val presets = listOf(15 to "15 min", 60 to "1 hr", 180 to "3 hr", 720 to "12 hr", 1440 to "24 hr")
  DrxCustomModal(
    title = if (isIndo) "Jadwalkan Pengingat Event" else "Schedule Event Reminder",
    onDismiss = onDismiss,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
        text = "${item.gameName} — ${item.title}",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = if (isIndo) "Pilih kapan DRX WorkManager akan mengirim pengingat lokal:" else "Select when DRX WorkManager should post a local reminder:",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      presets.forEach { (mins, label) ->
        Surface(
          onClick = {
            onConfirmMinutes(mins)
            onDismiss()
          },
          shape = RoundedCornerShape(12.dp),
          color = drxColors.elevatedSurface,
          border = BorderStroke(1.dp, drxColors.glassBorder),
          modifier = Modifier.fillMaxWidth().testTag("reminder_preset_$mins"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = if (isIndo) "Ingatkan dalam $label" else "Remind in $label",
              style = MaterialTheme.typography.labelLarge,
            )
            Icon(Icons.Default.AlarmAdd, contentDescription = null, tint = drxColors.neonCyan, modifier = Modifier.size(18.dp))
          }
        }
      }
    }
  }
}
