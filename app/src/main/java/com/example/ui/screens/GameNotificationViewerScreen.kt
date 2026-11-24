package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.GameNotificationEntity
import com.example.data.model.GameEventCategory
import com.example.data.model.InstalledAppItem
import com.example.data.model.LanguageMode
import com.example.data.model.NotificationDateRangeFilter
import com.example.data.model.NotificationGroupTab
import com.example.data.model.NotificationReadFilter
import com.example.data.model.NotificationSortOrder
import com.example.i18n.LocalStrings
import com.example.system.GameNotificationEngine
import com.example.system.TimeAndSizeFormatters
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxHairlineDivider
import com.example.ui.theme.DrxNotificationBanner
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxStaggeredEntrance
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.theme.rememberDrxAnimationsActive
import com.example.ui.theme.rememberDrxConditionalPulseAlpha
import com.example.ui.viewmodel.DrxMainViewModel
import com.example.ui.viewmodel.DrxNotificationsViewModel

@Composable
fun GameNotificationViewerScreen(
  notificationsViewModel: DrxNotificationsViewModel,
  mainViewModel: DrxMainViewModel,
  installedApps: List<InstalledAppItem>,
  onOpenInsights: () -> Unit,
  onOpenEvents: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val context = LocalContext.current

  val prefs by notificationsViewModel.userPrefs.collectAsStateWithLifecycle()
  val filterState by notificationsViewModel.filterState.collectAsStateWithLifecycle()
  val filteredItems by notificationsViewModel.filteredNotifications.collectAsStateWithLifecycle()
  val allItems by notificationsViewModel.allNotifications.collectAsStateWithLifecycle()
  val accessGranted by notificationsViewModel.notificationAccessGranted.collectAsStateWithLifecycle()
  val actionMsg by notificationsViewModel.actionMessage.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN
  val animationsActive = rememberDrxAnimationsActive(prefs.animationsEnabled)

  var selectedItem by remember { mutableStateOf<GameNotificationEntity?>(null) }
  var showPrivacyModal by remember { mutableStateOf(false) }
  var reminderTarget by remember { mutableStateOf<GameNotificationEntity?>(null) }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    notificationsViewModel.refreshNotificationAccess()
  }

  actionMsg?.let { msg ->
    Toast.makeText(
      context,
      if (isIndo) "Pengingat dijadwalkan: $msg" else "Reminder scheduled: $msg",
      Toast.LENGTH_SHORT,
    ).show()
    notificationsViewModel.clearActionMessage()
  }

  val unreadTotal = remember(allItems) { allItems.count { !it.isRead } }

  LazyColumn(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("game_notifications_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.gameNotificationsTitle,
        subtitle =
          if (allItems.isEmpty()) strings.gameNotificationsSub
          else if (isIndo) "${allItems.size} tercatat • $unreadTotal belum dibaca"
          else "${allItems.size} recorded • $unreadTotal unread",
        onBack = onBack,
        trailing = {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(
              onClick = onOpenInsights,
              shape = RoundedCornerShape(12.dp),
              color = drxColors.cardSurface,
              border = BorderStroke(1.dp, drxColors.glassBorder),
              modifier = Modifier.size(44.dp).testTag("notif_open_insights_btn"),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.BarChart, contentDescription = strings.notificationInsightsTitle, tint = drxColors.neonCyan, modifier = Modifier.size(20.dp))
              }
            }
            Surface(
              onClick = { showPrivacyModal = true },
              shape = RoundedCornerShape(12.dp),
              color = drxColors.cardSurface,
              border = BorderStroke(1.dp, drxColors.glassBorder),
              modifier = Modifier.size(44.dp).testTag("notif_open_privacy_btn"),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Shield, contentDescription = strings.notifPrivacyTitle, tint = drxColors.electricBlue, modifier = Modifier.size(20.dp))
              }
            }
          }
        },
      )
    }

    if (!accessGranted) {
      item {
        DrxGlassCard(
          accentColor = drxColors.warningAmber,
          highlighted = true,
          cornerRadius = 20.dp,
        ) {
          Text(
            text = strings.notifPermissionMissingTitle,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = drxColors.warningAmber,
          )
          Spacer(Modifier.height(6.dp))
          Text(
            text = strings.notifPermissionMissingBody,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(12.dp))
          DrxPrimaryButton(
            label = strings.notifGrantAccessBtn,
            accentColor = drxColors.electricBlue,
            onClick = { notificationsViewModel.openNotificationListenerSettings() },
            modifier = Modifier.fillMaxWidth().testTag("notif_grant_access_btn"),
          )
        }
      }
    }

    if (!prefs.gameNotificationViewerEnabled) {
      item {
        DrxNotificationBanner(
          title = strings.notifPrivacyTitle,
          body = strings.notifViewerDisabledNotice,
          accentColor = drxColors.warningAmber,
          icon = Icons.Default.NotificationsOff,
          onDismiss = { notificationsViewModel.setViewerEnabled(true) },
        )
      }
    }

    // Segmented Mode Tabs: BY DATE | BY GAME
    item {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = drxColors.secondaryBg,
        border = BorderStroke(1.dp, drxColors.glassBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(4.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          val tabs =
            listOf(
              NotificationGroupTab.BY_DATE to strings.notifTabByDate,
              NotificationGroupTab.BY_GAME to strings.notifTabByGame,
            )
          tabs.forEach { (tab, label) ->
            val selected = filterState.selectedTab == tab
            Surface(
              onClick = { notificationsViewModel.setTab(tab) },
              shape = RoundedCornerShape(10.dp),
              color = if (selected) drxColors.electricBlue.copy(alpha = 0.22f) else Color.Transparent,
              border = if (selected) BorderStroke(1.dp, drxColors.electricBlue.copy(alpha = 0.5f)) else null,
              modifier =
                Modifier
                  .weight(1f)
                  .height(42.dp)
                  .testTag(if (tab == NotificationGroupTab.BY_DATE) "notif_tab_by_date" else "notif_tab_by_game"),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelLarge,
                  color = if (selected) drxColors.neonCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                )
              }
            }
          }
        }
      }
    }

    // Search + Date Range + Read/Category Filter Strip
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = filterState.searchQuery,
          onValueChange = { notificationsViewModel.setSearchQuery(it) },
          placeholder = { Text(strings.notifSearchPlaceholder, style = MaterialTheme.typography.bodySmall) },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          trailingIcon = {
            if (filterState.searchQuery.isNotEmpty()) {
              IconButton(onClick = { notificationsViewModel.setSearchQuery("") }) {
                Icon(Icons.Default.Close, contentDescription = "Clear")
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("notif_search_input"),
        )

        // Date range filter strip
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf(
            NotificationDateRangeFilter.ALL to (if (isIndo) "Semua Tanggal" else "All Dates"),
            NotificationDateRangeFilter.TODAY to strings.notifDateTodayLabel,
            NotificationDateRangeFilter.YESTERDAY to strings.notifDateYesterdayLabel,
            NotificationDateRangeFilter.DAYS_7 to strings.notifDate7DaysLabel,
          ).forEach { (range, lbl) ->
            DrxFilterPill(
              label = lbl,
              selected = filterState.dateRangeFilter == range,
              onClick = { notificationsViewModel.setDateRangeFilter(range) },
              modifier = Modifier.testTag("notif_date_filter_${range.name.lowercase()}"),
            )
          }
        }

        // Sort + Read Status strip
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          DrxFilterPill(
            label =
              if (filterState.sortOrder == NotificationSortOrder.NEWEST) (if (isIndo) "Terbaru" else "Newest")
              else (if (isIndo) "Terlama" else "Oldest"),
            selected = true,
            onClick = {
              val next =
                if (filterState.sortOrder == NotificationSortOrder.NEWEST) NotificationSortOrder.OLDEST
                else NotificationSortOrder.NEWEST
              notificationsViewModel.setSortOrder(next)
            },
          )
          NotificationReadFilter.entries.forEach { rf ->
            val lbl =
              when (rf) {
                NotificationReadFilter.ALL -> if (isIndo) "Semua" else "All"
                NotificationReadFilter.UNREAD -> if (isIndo) "Belum Dibaca" else "Unread"
                NotificationReadFilter.READ -> if (isIndo) "Dibaca" else "Read"
                NotificationReadFilter.FAVORITE -> if (isIndo) "Favorit" else "Favorite"
                NotificationReadFilter.IMPORTANT -> if (isIndo) "Penting" else "Important"
              }
            DrxFilterPill(
              label = lbl,
              selected = filterState.readFilter == rf,
              onClick = { notificationsViewModel.setReadFilter(rf) },
            )
          }
        }

        // Category Filter Pills
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          DrxFilterPill(
            label = if (isIndo) "Semua Kategori" else "All Categories",
            selected = filterState.selectedCategory == null,
            onClick = { notificationsViewModel.setSelectedCategory(null) },
          )
          listOf(
            GameEventCategory.EVENT,
            GameEventCategory.REWARD,
            GameEventCategory.UPDATE,
            GameEventCategory.MAINTENANCE,
            GameEventCategory.TOURNAMENT,
          ).forEach { cat ->
            DrxFilterPill(
              label = if (isIndo) cat.labelId else cat.labelEn,
              selected = filterState.selectedCategory == cat,
              onClick = {
                notificationsViewModel.setSelectedCategory(
                  if (filterState.selectedCategory == cat) null else cat
                )
              },
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          DrxSecondaryButton(
            label = strings.gameEventsTitle,
            icon = Icons.Default.Event,
            onClick = onOpenEvents,
            modifier = Modifier.weight(1f).height(42.dp).testTag("notif_open_events_btn"),
          )
          if (unreadTotal > 0) {
            DrxSecondaryButton(
              label = if (isIndo) "Tandai Semua Dibaca" else "Mark All Read",
              icon = Icons.Default.DoneAll,
              onClick = { notificationsViewModel.markAllRead() },
              modifier = Modifier.weight(1f).height(42.dp).testTag("notif_mark_all_read_btn"),
            )
          }
        }
      }
    }

    if (filteredItems.isEmpty()) {
      item {
        val emptyPulse =
          rememberDrxConditionalPulseAlpha(
            isPulsing = !accessGranted,
            animationsEnabled = prefs.animationsEnabled,
            minAlpha = 0.6f,
          )
        DrxStaggeredEntrance(index = 0, animationsEnabled = animationsActive) {
          DrxGlassCard(cornerRadius = 20.dp) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Icon(
                imageVector = Icons.Default.SportsEsports,
                contentDescription = null,
                tint = drxColors.electricBlue,
                modifier = Modifier.size(38.dp).alpha(emptyPulse),
              )
              Spacer(Modifier.height(10.dp))
              Text(
                text = strings.notifEmptyTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
              )
              Spacer(Modifier.height(4.dp))
              Text(
                text = strings.notifEmptyBody,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    } else if (filterState.selectedTab == NotificationGroupTab.BY_DATE) {
      val groupedByDate = filteredItems.groupBy { it.dateKey }
      var groupIdx = 0
      groupedByDate.forEach { (dateKey, dateItems) ->
        item(key = "date_hdr_$dateKey") {
          Text(
            text = GameNotificationEngine.formatDisplayDateHeader(dateKey, isIndo = isIndo),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = drxColors.neonCyan,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
          )
        }
        val groupedByGameInDate = dateItems.groupBy { it.packageName }
        groupedByGameInDate.forEach { (pkg, pkgItems) ->
          val currentIdx = groupIdx++
          item(key = "date_${dateKey}_game_$pkg") {
            DrxStaggeredEntrance(index = currentIdx.coerceAtMost(5), animationsEnabled = animationsActive) {
              GameNotificationGroupCard(
                packageName = pkg,
                gameName = pkgItems.first().gameName,
                items = pkgItems,
                isIndo = isIndo,
                animationsEnabled = animationsActive,
                mainViewModel = mainViewModel,
                onItemClick = { clicked ->
                  if (!clicked.isRead) notificationsViewModel.markRead(clicked.id, true)
                  selectedItem = clicked
                },
              )
            }
          }
        }
      }
    } else {
      val groupedByGame = filteredItems.groupBy { it.packageName }
      var groupIdx = 0
      groupedByGame.forEach { (pkg, pkgItems) ->
        val currentIdx = groupIdx++
        item(key = "game_grp_$pkg") {
          DrxStaggeredEntrance(index = currentIdx.coerceAtMost(5), animationsEnabled = animationsActive) {
            GameNotificationGroupCard(
              packageName = pkg,
              gameName = pkgItems.first().gameName,
              items = pkgItems,
              isIndo = isIndo,
              animationsEnabled = animationsActive,
              mainViewModel = mainViewModel,
              onItemClick = { clicked ->
                if (!clicked.isRead) notificationsViewModel.markRead(clicked.id, true)
                selectedItem = clicked
              },
            )
          }
        }
      }
    }
  }

  selectedItem?.let { current ->
    val liveCurrent = allItems.find { it.id == current.id } ?: current
    GameNotificationDetailModal(
      item = liveCurrent,
      isIndo = isIndo,
      viewModel = notificationsViewModel,
      onDismiss = { selectedItem = null },
      onScheduleReminder = { target ->
        selectedItem = null
        reminderTarget = target
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

  if (showPrivacyModal) {
    NotificationPrivacyControlModal(
      prefs = prefs,
      installedApps = installedApps,
      isIndo = isIndo,
      viewModel = notificationsViewModel,
      onDismiss = { showPrivacyModal = false },
    )
  }
}

@Composable
private fun GameNotificationGroupCard(
  packageName: String,
  gameName: String,
  items: List<GameNotificationEntity>,
  isIndo: Boolean,
  animationsEnabled: Boolean,
  mainViewModel: DrxMainViewModel,
  onItemClick: (GameNotificationEntity) -> Unit,
) {
  val drxColors = LocalDrxColors.current
  val iconBmp = remember(packageName) { mainViewModel.loadAppIcon(packageName) }
  val unreadCount = items.count { !it.isRead }

  DrxGlassCard(
    accentColor = if (unreadCount > 0) drxColors.electricBlue else drxColors.glassBorder,
    highlighted = unreadCount > 0,
    cornerRadius = 18.dp,
    contentPadding = PaddingValues(14.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (iconBmp != null) {
        Image(
          bitmap = iconBmp,
          contentDescription = gameName,
          modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)),
        )
      } else {
        Icon(
          imageVector = Icons.Default.SportsEsports,
          contentDescription = gameName,
          tint = drxColors.electricBlue,
          modifier = Modifier.size(34.dp),
        )
      }
      Spacer(Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = gameName,
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text =
            if (isIndo) "${items.size} notifikasi" + if (unreadCount > 0) " • $unreadCount baru" else ""
            else "${items.size} notifications" + if (unreadCount > 0) " • $unreadCount unread" else "",
          style = DrxTypographyTokens.caption,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (unreadCount > 0) {
        DrxStatusPill(text = "$unreadCount", color = drxColors.neonCyan)
      }
    }

    Spacer(Modifier.height(10.dp))

    items.forEachIndexed { idx, item ->
      if (idx > 0) {
        DrxHairlineDivider(modifier = Modifier.padding(vertical = 8.dp))
      }
      var expanded by remember(item.id) { mutableStateOf(false) }
      val cat = GameEventCategory.fromId(item.eventCategory)
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .then(
              if (animationsEnabled) {
                Modifier.animateContentSize(
                  animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
              } else {
                Modifier
              }
            )
            .clip(RoundedCornerShape(10.dp))
            .clickable { onItemClick(item) }
            .padding(vertical = 4.dp)
            .testTag("notif_item_${item.id}"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (!item.isRead) {
              Box(
                modifier =
                  Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(drxColors.neonCyan),
              )
              Spacer(Modifier.width(6.dp))
            }
            Text(
              text = item.title,
              style =
                MaterialTheme.typography.titleSmall.copy(
                  fontWeight = if (!item.isRead) FontWeight.SemiBold else FontWeight.Medium,
                ),
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = if (expanded) 3 else 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Spacer(Modifier.width(8.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.isFavorite) {
              Icon(Icons.Default.Star, contentDescription = "Favorite", tint = drxColors.warningAmber, modifier = Modifier.size(14.dp))
              Spacer(Modifier.width(4.dp))
            }
            Text(
              text = TimeAndSizeFormatters.formatClockShort(item.timestampMs),
              style = DrxTypographyTokens.caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val previewText = item.bigText.ifBlank { item.text }
            if (previewText.length > 60) {
              IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(28.dp),
              ) {
                Icon(
                  imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                  contentDescription = if (expanded) "Collapse" else "Expand",
                  tint = drxColors.silver,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
        }
        val preview = item.bigText.ifBlank { item.text }
        if (preview.isNotBlank()) {
          Spacer(Modifier.height(2.dp))
          Text(
            text = preview,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) 12 else 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (cat != GameEventCategory.GENERAL) {
          Spacer(Modifier.height(5.dp))
          DrxStatusPill(
            text = if (isIndo) cat.labelId else cat.labelEn,
            color = drxColors.electricBlue,
          )
        }
      }
    }
  }
}
