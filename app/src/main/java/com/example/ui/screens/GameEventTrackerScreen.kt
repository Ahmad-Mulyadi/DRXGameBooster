package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.GameNotificationEntity
import com.example.data.model.GameEventCategory
import com.example.data.model.LanguageMode
import com.example.i18n.LocalStrings
import com.example.system.TimeAndSizeFormatters
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSectionHeader
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxNotificationsViewModel

@Composable
fun GameEventTrackerScreen(
  notificationsViewModel: DrxNotificationsViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val context = LocalContext.current
  val prefs by notificationsViewModel.userPrefs.collectAsStateWithLifecycle()
  val allNotifications by notificationsViewModel.allNotifications.collectAsStateWithLifecycle()
  val reminders by notificationsViewModel.allReminders.collectAsStateWithLifecycle()
  val actionMsg by notificationsViewModel.actionMessage.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  var selectedCategory by remember { mutableStateOf<GameEventCategory?>(null) }
  var favoritesOnly by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var detailTarget by remember { mutableStateOf<GameNotificationEntity?>(null) }
  var reminderTarget by remember { mutableStateOf<GameNotificationEntity?>(null) }

  actionMsg?.let { msg ->
    Toast.makeText(
      context,
      if (isIndo) "Pengingat WorkManager aktif: $msg" else "WorkManager reminder active: $msg",
      Toast.LENGTH_SHORT,
    ).show()
    notificationsViewModel.clearActionMessage()
  }

  val filteredEvents =
    remember(allNotifications, selectedCategory, favoritesOnly, searchQuery) {
      val q = searchQuery.trim().lowercase()
      allNotifications.filter { n ->
        val cat = GameEventCategory.fromId(n.eventCategory)
        val matchCat = selectedCategory == null || cat == selectedCategory
        val matchFav = !favoritesOnly || n.isFavorite || n.isImportant
        val matchQ =
          q.isEmpty() ||
            n.gameName.lowercase().contains(q) ||
            n.title.lowercase().contains(q) ||
            n.text.lowercase().contains(q)
        matchCat && matchFav && matchQ
      }
    }

  LazyColumn(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("game_events_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.gameEventsTitle,
        subtitle = strings.gameEventsSub,
        onBack = onBack,
      )
    }

    // Active WorkManager Scheduled Reminders
    if (reminders.isNotEmpty()) {
      item {
        DrxSectionHeader(
          title = if (isIndo) "Pengingat Event Terjadwal (${reminders.size})" else "Scheduled Event Reminders (${reminders.size})",
        )
      }
      items(reminders, key = { "rem_${it.id}" }) { rem ->
        val cat = GameEventCategory.fromId(rem.eventCategory)
        DrxGlassCard(
          accentColor = if (rem.isCompleted) drxColors.optimalGreen else drxColors.neonCyan,
          cornerRadius = 16.dp,
          contentPadding = PaddingValues(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Default.Alarm,
              contentDescription = rem.title,
              tint = if (rem.isCompleted) drxColors.optimalGreen else drxColors.neonCyan,
              modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "${rem.gameName} • ${if (isIndo) cat.labelId else cat.labelEn}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = rem.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text =
                  if (rem.isCompleted) {
                    if (isIndo) "Terkirim • ${TimeAndSizeFormatters.formatDateTimeWithZone(rem.remindAtMs, true)}"
                    else "Delivered • ${TimeAndSizeFormatters.formatDateTimeWithZone(rem.remindAtMs, false)}"
                  } else {
                    TimeAndSizeFormatters.formatDateTimeWithZone(rem.remindAtMs, isIndo)
                  },
                style = DrxTypographyTokens.caption,
                color = if (rem.isCompleted) drxColors.optimalGreen else drxColors.neonCyan,
              )
            }
            IconButton(
              onClick = { notificationsViewModel.cancelAndDeleteReminder(rem) },
              modifier = Modifier.testTag("delete_reminder_${rem.id}"),
            ) {
              Icon(Icons.Default.Delete, contentDescription = "Delete reminder", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }
    }

    // Search & Category Filter
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = {
            Text(
              if (isIndo) "Cari event, hadiah, turnamen..." else "Search events, rewards, tournaments...",
              style = MaterialTheme.typography.bodySmall,
            )
          },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Default.Close, contentDescription = "Clear")
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("events_search_input"),
        )

        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          DrxFilterPill(
            label = if (isIndo) "Semua" else "All",
            selected = selectedCategory == null && !favoritesOnly,
            onClick = {
              selectedCategory = null
              favoritesOnly = false
            },
          )
          DrxFilterPill(
            label = if (isIndo) "Favorit / Penting" else "Favorite / Important",
            selected = favoritesOnly,
            onClick = { favoritesOnly = !favoritesOnly },
          )
          GameEventCategory.entries.forEach { cat ->
            DrxFilterPill(
              label = if (isIndo) cat.labelId else cat.labelEn,
              selected = selectedCategory == cat,
              onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
              modifier = Modifier.testTag("event_cat_${cat.id}"),
            )
          }
        }
      }
    }

    if (filteredEvents.isEmpty()) {
      item {
        DrxGlassCard(cornerRadius = 20.dp) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Icon(Icons.Default.Event, contentDescription = null, tint = drxColors.electricBlue, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(10.dp))
            Text(
              text = if (isIndo) "Belum Ada Event Terklasifikasi" else "No Classified Game Events Yet",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text =
                if (isIndo) "Notifikasi bertema event, hadiah, turnamen, pembaruan, atau maintenance dari game akan otomatis dikelompokkan di sini."
                else "Incoming game notifications containing event, reward, update, maintenance, or tournament keywords appear automatically here.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      items(filteredEvents, key = { "evt_${it.id}" }) { item ->
        val cat = GameEventCategory.fromId(item.eventCategory)
        DrxGlassCard(
          accentColor = if (item.isImportant) drxColors.warningAmber else drxColors.electricBlue,
          highlighted = item.isImportant || item.isFavorite,
          cornerRadius = 18.dp,
          onClick = { detailTarget = item },
          modifier = Modifier.testTag("event_card_${item.id}"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              DrxStatusPill(
                text = if (isIndo) cat.labelId else cat.labelEn,
                color = drxColors.neonCyan,
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text = item.gameName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            IconButton(
              onClick = { notificationsViewModel.toggleFavorite(item) },
              modifier = Modifier.size(36.dp),
            ) {
              Icon(
                imageVector = if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Favorite",
                tint = if (item.isFavorite) drxColors.warningAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
              )
            }
            IconButton(
              onClick = { reminderTarget = item },
              modifier = Modifier.size(36.dp).testTag("event_remind_btn_${item.id}"),
            ) {
              Icon(
                imageVector = Icons.Default.AlarmAdd,
                contentDescription = "Schedule reminder",
                tint = drxColors.neonCyan,
                modifier = Modifier.size(18.dp),
              )
            }
          }
          Spacer(Modifier.height(6.dp))
          Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          val preview = item.bigText.ifBlank { item.text }
          if (preview.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
              text = preview,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Spacer(Modifier.height(6.dp))
          Text(
            text = TimeAndSizeFormatters.formatDateTimeWithZone(item.timestampMs, isIndo),
            style = DrxTypographyTokens.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }

  detailTarget?.let { current ->
    val live = allNotifications.find { it.id == current.id } ?: current
    GameNotificationDetailModal(
      item = live,
      isIndo = isIndo,
      viewModel = notificationsViewModel,
      onDismiss = { detailTarget = null },
      onScheduleReminder = {
        detailTarget = null
        reminderTarget = it
      },
    )
  }

  reminderTarget?.let { target ->
    ScheduleReminderQuickModal(
      item = target,
      isIndo = isIndo,
      onConfirmMinutes = { mins ->
        notificationsViewModel.scheduleEventReminder(
          notificationId = target.id,
          packageName = target.packageName,
          gameName = target.gameName,
          title = target.title,
          category = target.eventCategory,
          delayMinutes = mins,
        )
      },
      onDismiss = { reminderTarget = null },
    )
  }
}
