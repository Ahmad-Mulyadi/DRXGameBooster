package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.GameEntity
import com.example.data.db.GameSessionEntity
import com.example.data.model.CombinedGameStatistics
import com.example.data.model.DayActivityBucket
import com.example.data.model.LanguageMode
import com.example.data.model.MediaGalleryItem
import com.example.data.model.MediaItemType
import com.example.data.model.StatsTimeFilter
import com.example.data.prefs.DrxUserPrefs
import com.example.i18n.LocalStrings
import com.example.system.DrxMediaStorageManager
import com.example.system.GameStatsCollector
import com.example.system.TimeAndSizeFormatters
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxHairlineDivider
import com.example.ui.theme.DrxInfoTooltipIcon
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxSectionHeader
import com.example.ui.theme.DrxStaggeredEntrance
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.theme.ManropeFamily
import com.example.ui.viewmodel.DrxMainViewModel
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GameDetailStatsScreen(
  packageName: String,
  prefs: DrxUserPrefs,
  games: List<GameEntity>,
  sessions: List<GameSessionEntity>,
  viewModel: DrxMainViewModel,
  onBack: () -> Unit,
  notificationCount: Int = 0,
  onOpenNotifications: (() -> Unit)? = null,
  onOpenMediaGallery: (() -> Unit)? = null,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val activity = LocalActivity.current
  val context = LocalContext.current
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  val selectedStats by viewModel.selectedGameStats.collectAsStateWithLifecycle()
  val stats: CombinedGameStatistics? =
    selectedStats?.takeIf { it.gameInfo.packageName == packageName }

  var is30DaysChart by remember { mutableStateOf(false) }
  var historyFilter by remember { mutableStateOf(StatsTimeFilter.ALL) }

  LaunchedEffect(packageName, sessions.size) {
    viewModel.loadGameDetailStatistics(packageName)
  }

  val gameSessions =
    remember(sessions, packageName, historyFilter) {
      val now = System.currentTimeMillis()
      val todayStart = GameStatsCollector.startOfDayMs(now)
      val sevenStart = GameStatsCollector.startOfDaysAgoMs(now, 6)
      val thirtyStart = GameStatsCollector.startOfDaysAgoMs(now, 29)
      sessions
        .filter { it.gamePackageName == packageName }
        .filter { s ->
          when (historyFilter) {
            StatsTimeFilter.TODAY -> s.startedAt >= todayStart
            StatsTimeFilter.DAYS_7 -> s.startedAt >= sevenStart
            StatsTimeFilter.DAYS_30 -> s.startedAt >= thirtyStart
            StatsTimeFilter.ALL -> true
          }
        }
        .sortedByDescending { it.startedAt }
    }

  val drxGame = remember(games, packageName) { games.find { it.packageName == packageName } }
  val iconBmp = remember(packageName) { viewModel.loadAppIcon(packageName) }
  val recentMedia by
    produceState<List<MediaGalleryItem>>(initialValue = emptyList(), key1 = packageName) {
      value =
        withContext(Dispatchers.IO) {
          runCatching { DrxMediaStorageManager.loadGalleryItems(context).take(8) }.getOrDefault(emptyList())
        }
    }

  LazyColumn(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("game_detail_stats_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.gameStatsTitle,
        subtitle = stats?.gameInfo?.appName ?: drxGame?.appName ?: packageName,
        onBack = onBack,
        trailing = {
          IconButton(
            onClick = { viewModel.loadGameDetailStatistics(packageName) },
            modifier = Modifier.size(48.dp).testTag("refresh_game_stats_btn"),
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = strings.refreshStatsBtn,
              tint = drxColors.neonCyan,
            )
          }
        },
      )
    }

    if (stats == null) {
      item {
        DrxGlassCard(modifier = Modifier.fillMaxWidth()) {
          Box(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(color = drxColors.neonCyan)
          }
        }
      }
      return@LazyColumn
    }

    // Usage Access CTA banner if denied
    if (!stats.systemUsage.hasPermission) {
      item {
        DrxGlassCard(accentColor = drxColors.warningAmber, highlighted = true) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Security,
              contentDescription = null,
              tint = drxColors.warningAmber,
              modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
              text = strings.usageAccessNotice,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f),
            )
          }
          Spacer(Modifier.height(10.dp))
          DrxPrimaryButton(
            label = strings.grantUsagePermissionCta,
            accentColor = drxColors.warningAmber,
            onClick = { viewModel.tweaksManager.openUsageAccessSettings() },
            modifier = Modifier.fillMaxWidth().testTag("grant_usage_stats_detail_btn"),
          )
        }
      }
    }

    // 1. HERO GAME HEADER CARD (Artwork-Framed Banner)
    item {
      val info = stats.gameInfo
      DrxStaggeredEntrance(index = 0, animationsEnabled = prefs.animationsEnabled) {
        val bannerShape = RoundedCornerShape(24.dp)
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(bannerShape)
              .background(
                Brush.linearGradient(
                  listOf(
                    drxColors.electricBlue.copy(alpha = 0.20f),
                    drxColors.elevatedSurface,
                    drxColors.cardSurface,
                  )
                )
              )
              .border(1.dp, drxColors.electricBlue.copy(alpha = 0.38f), bannerShape)
              .padding(18.dp),
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              if (iconBmp != null) {
                Image(
                  bitmap = iconBmp,
                  contentDescription = info.appName,
                  modifier =
                    Modifier
                      .size(64.dp)
                      .clip(RoundedCornerShape(16.dp))
                      .border(1.dp, drxColors.neonCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                )
              } else {
                Icon(
                  imageVector = Icons.Default.SportsEsports,
                  contentDescription = info.appName,
                  tint = drxColors.electricBlue,
                  modifier = Modifier.size(64.dp),
                )
              }
              Spacer(Modifier.width(14.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = info.appName,
                  style = MaterialTheme.typography.headlineMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "v${info.versionName} • ${TimeAndSizeFormatters.formatBytesAuto(info.totalStorageBytes ?: info.apkSizeBytes, info.packageName)}",
                  style = DrxTypographyTokens.caption,
                  color = drxColors.silver,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  item {
                    DrxStatusPill(
                      text = if (info.isInstalled) strings.installedStatus else strings.notInstalledStatus,
                      color = if (info.isInstalled) drxColors.optimalGreen else drxColors.alertCrimson,
                    )
                  }
                  if (stats.systemUsage.isCurrentlyForeground) {
                    item {
                      DrxStatusPill(
                        text = if (isIndo) "AKTIF SEKARANG" else "ACTIVE NOW",
                        color = drxColors.neonCyan,
                      )
                    }
                  }
                  if (notificationCount > 0) {
                    item {
                      DrxStatusPill(
                        text = "$notificationCount Notifs",
                        color = drxColors.electricBlue,
                        modifier =
                          Modifier.clickable(enabled = onOpenNotifications != null) {
                            onOpenNotifications?.invoke()
                          },
                      )
                    }
                  }
                }
              }
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (info.isInstalled && drxGame != null) {
                DrxPrimaryButton(
                  label = strings.boostAndPlayBtn,
                  icon = Icons.Default.PlayArrow,
                  onClick = { viewModel.startGameLaunch(drxGame, activity) },
                  modifier = Modifier.weight(1f).testTag("detail_stats_play_btn"),
                )
              }
              if (onOpenNotifications != null) {
                DrxSecondaryButton(
                  label = if (notificationCount > 0) "Notifs ($notificationCount)" else "Notifs",
                  icon = Icons.Default.Notifications,
                  onClick = onOpenNotifications,
                  modifier = Modifier.height(48.dp),
                )
              }
            }
          }
        }
      }
    }

    // Recent Captured Screenshots & Recordings Strip
    if (recentMedia.isNotEmpty() || onOpenMediaGallery != null) {
      item {
        DrxGlassCard(cornerRadius = 20.dp) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            DrxSectionHeader(
              title = if (isIndo) "Tangkapan & Rekaman Terbaru" else "Recent Screenshots & Recordings",
              actionLabel = if (onOpenMediaGallery != null) (if (isIndo) "Buka Hub" else "Media Hub") else null,
              onAction = onOpenMediaGallery,
              modifier = Modifier.weight(1f),
            )
          }
          if (recentMedia.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(recentMedia, key = { it.uriString }) { media ->
                val thumbBmp by
                  produceState<Bitmap?>(initialValue = null, key1 = media.uriString) {
                    value =
                      withContext(Dispatchers.IO) {
                        DrxMediaStorageManager.loadThumbnailBitmap(context, media)
                      }
                  }
                Box(
                  modifier =
                    Modifier
                      .size(72.dp)
                      .clip(RoundedCornerShape(12.dp))
                      .background(drxColors.elevatedSurface)
                      .border(1.dp, drxColors.glassBorder, RoundedCornerShape(12.dp))
                      .clickable(enabled = onOpenMediaGallery != null) { onOpenMediaGallery?.invoke() },
                  contentAlignment = Alignment.Center,
                ) {
                  val bmp = thumbBmp
                  if (bmp != null) {
                    Image(
                      bitmap = bmp.asImageBitmap(),
                      contentDescription = media.displayName,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize(),
                    )
                  } else {
                    Icon(
                      imageVector = if (media.type == MediaItemType.VIDEO) Icons.Default.Videocam else Icons.Default.PhotoLibrary,
                      contentDescription = media.displayName,
                      tint = drxColors.neonCyan,
                      modifier = Modifier.size(24.dp),
                    )
                  }
                }
              }
            }
          } else {
            Spacer(Modifier.height(4.dp))
            Text(
              text = if (isIndo) "Rekaman & screenshot dari DRX Pro Recorder akan tampil di sini." else "Videos & screenshots captured with DRX Pro Recorder appear here.",
              style = DrxTypographyTokens.caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    // 2. GAME INFORMATION
    item {
      val info = stats.gameInfo
      DrxGlassCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          DrxSectionHeader(title = strings.gameInfoSectionTitle, modifier = Modifier.weight(1f))
          DrxInfoTooltipIcon(
            title = strings.gameInfoSectionTitle,
            description =
              if (isIndo) {
                "Metadata paket diambil langsung dari Android PackageManager. Jika sistem membatasi nama developer pihak ketiga, DRX menampilkan Not Available."
              } else {
                "Package metadata is read directly from Android PackageManager. When the OS hides third-party publisher names, DRX honestly displays Not Available."
              },
          )
        }
        Spacer(Modifier.height(8.dp))
        StatsKeyValueRow("Package Name", info.packageName)
        StatsKeyValueRow("Developer", info.developerName ?: strings.notAvailableLabel)
        StatsKeyValueRow("Version", info.versionName)
        StatsKeyValueRow("Version Code", if (info.versionCode > 0L) info.versionCode.toString() else strings.notAvailableLabel)
        StatsKeyValueRow(
          "Status",
          if (info.isInstalled) strings.installedStatus else strings.notInstalledStatus,
        )
        StatsKeyValueRow(
          if (isIndo) "Sumber Instalasi" else "Install Source",
          info.installSourceLabel ?: strings.notAvailableLabel,
        )
      }
    }

    // 3. PLAYTIME STATISTICS
    item {
      DrxGlassCard(accentColor = drxColors.neonCyan) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            DrxSectionHeader(title = strings.playtimeSectionTitle, modifier = Modifier.weight(1f, fill = false))
            if (stats.usesEstimatedSystemUsage) {
              Spacer(Modifier.width(8.dp))
              DrxStatusPill(text = strings.estimatedBadge, color = drxColors.warningAmber)
            }
          }
          DrxInfoTooltipIcon(
            title = strings.playtimeSectionTitle,
            description =
              if (isIndo) {
                "Statistik menggabungkan sesi akurat yang diluncurkan via DRX (Room) dengan perkiraan Android UsageStatsManager tanpa penghitungan ganda harian."
              } else {
                "Combines exact DRX-launched session logs with Android UsageStatsManager estimates using daily max deduplication to prevent double-counting."
              },
          )
        }

        Spacer(Modifier.height(10.dp))
        Text(
          text = if (isIndo) "Total Waktu Bermain" else "Total Playtime",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = TimeAndSizeFormatters.formatDurationReadable(stats.totalPlaytimeMs, isIndo),
          style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp, lineHeight = 34.sp),
          color = drxColors.neonCyan,
          modifier = Modifier.testTag("game_total_playtime_value"),
        )
        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          StatMiniTile(
            label = if (isIndo) "Hari Ini" else "Today",
            value = TimeAndSizeFormatters.formatDurationReadable(stats.todayMs, isIndo),
            modifier = Modifier.weight(1f),
          )
          StatMiniTile(
            label = if (isIndo) "Kemarin" else "Yesterday",
            value = TimeAndSizeFormatters.formatDurationReadable(stats.yesterdayMs, isIndo),
            modifier = Modifier.weight(1f),
          )
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          StatMiniTile(
            label = if (isIndo) "7 Hari Terakhir" else "Last 7 Days",
            value = TimeAndSizeFormatters.formatDurationReadable(stats.last7DaysMs, isIndo),
            modifier = Modifier.weight(1f),
          )
          StatMiniTile(
            label = if (isIndo) "30 Hari Terakhir" else "Last 30 Days",
            value = TimeAndSizeFormatters.formatDurationReadable(stats.last30DaysMs, isIndo),
            modifier = Modifier.weight(1f),
          )
        }

        Spacer(Modifier.height(10.dp))
        StatsKeyValueRow(
          if (isIndo) "Rata-rata / Hari Aktif" else "Daily Average",
          TimeAndSizeFormatters.formatDurationReadable(stats.dailyAverageMs, isIndo),
        )
        StatsKeyValueRow(
          if (isIndo) "Jumlah Sesi (DRX)" else "Tracked Sessions (DRX)",
          "${stats.sessionCount}",
        )
        StatsKeyValueRow(
          if (isIndo) "Perkiraan Dibuka (OS)" else "Launch Count Estimate",
          if (stats.systemUsage.hasPermission) "${stats.launchCountEstimate} (${strings.estimatedBadge})"
          else "${stats.sessionCount}",
        )
        StatsKeyValueRow(
          if (isIndo) "Durasi Sesi Terakhir" else "Last Session Duration",
          if (stats.lastSessionDurationMs > 0L) TimeAndSizeFormatters.formatDurationReadable(stats.lastSessionDurationMs, isIndo)
          else strings.notAvailableLabel,
        )
        StatsKeyValueRow(
          if (isIndo) "Sesi Terlama" else "Longest Session",
          if (stats.longestSessionMs > 0L) TimeAndSizeFormatters.formatDurationReadable(stats.longestSessionMs, isIndo)
          else strings.notAvailableLabel,
        )
        StatsKeyValueRow(
          if (isIndo) "Hari Terbanyak Bermain" else "Peak Activity Day",
          if (stats.peakDayDurationMs > 0L) {
            "${stats.peakDayLabel} (${TimeAndSizeFormatters.formatDurationReadable(stats.peakDayDurationMs, isIndo)})"
          } else {
            strings.notAvailableLabel
          },
        )
      }
    }

    // 4. LAST PLAYED INFO
    item {
      DrxGlassCard {
        DrxSectionHeader(title = strings.lastPlayedSectionTitle)
        Spacer(Modifier.height(8.dp))
        if (stats.lastPlayedTimestampMs == null) {
          Text(
            text = strings.lastPlayedNever,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          StatsKeyValueRow(
            if (isIndo) "Tanggal" else "Date",
            TimeAndSizeFormatters.formatDate(stats.lastPlayedTimestampMs, isIndo),
          )
          StatsKeyValueRow(
            if (isIndo) "Jam" else "Time",
            TimeAndSizeFormatters.formatTimeWithZone(stats.lastPlayedTimestampMs),
          )
          StatsKeyValueRow(
            if (isIndo) "Hari" else "Day",
            TimeAndSizeFormatters.formatDayOfWeek(stats.lastPlayedTimestampMs, isIndo),
          )
          if (stats.lastSessionStartMs != null && stats.lastSessionEndMs != null) {
            StatsKeyValueRow(
              if (isIndo) "Rentang Sesi Terakhir" else "Last Session Window",
              "${TimeAndSizeFormatters.formatClockShort(stats.lastSessionStartMs)} – ${TimeAndSizeFormatters.formatClockShort(stats.lastSessionEndMs)}",
            )
          }
        }
      }
    }

    // 5. INSTALLATION INFORMATION
    item {
      val info = stats.gameInfo
      DrxGlassCard {
        DrxSectionHeader(title = strings.installInfoSectionTitle)
        Spacer(Modifier.height(8.dp))
        StatsKeyValueRow(
          if (isIndo) "Pertama Dipasang" else "First Installed",
          TimeAndSizeFormatters.formatDateTimeWithZone(info.firstInstallTimeMs, isIndo, notAvailableLabel = strings.notAvailableLabel),
        )
        StatsKeyValueRow(
          if (isIndo) "Terakhir Diperbarui" else "Last Updated",
          TimeAndSizeFormatters.formatDateTimeWithZone(info.lastUpdateTimeMs, isIndo, notAvailableLabel = strings.notAvailableLabel),
        )
        StatsKeyValueRow(
          if (isIndo) "Sumber Instalasi" else "Install Source",
          info.installSourceLabel ?: strings.notAvailableLabel,
        )
        Spacer(Modifier.height(8.dp))
        Text(
          text = strings.installDateNote,
          style = DrxTypographyTokens.caption,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // 6. STORAGE INFORMATION
    item {
      val info = stats.gameInfo
      DrxGlassCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          DrxSectionHeader(title = strings.storageInfoSectionTitle, modifier = Modifier.weight(1f))
          Icon(
            imageVector = Icons.Default.Storage,
            contentDescription = null,
            tint = drxColors.electricBlue,
            modifier = Modifier.size(18.dp),
          )
        }
        Spacer(Modifier.height(10.dp))

        if (!info.storageAccessRestricted && info.totalStorageBytes != null && info.totalStorageBytes > 0L) {
          val totalF = info.totalStorageBytes.toFloat().coerceAtLeast(1f)
          val appRatio = ((info.appSizeBytes ?: 0L).coerceAtLeast(0L) / totalF).coerceIn(0f, 1f)
          val dataRatio = ((info.dataSizeBytes ?: 0L).coerceAtLeast(0L) / totalF).coerceIn(0f, 1f)
          val cacheRatio = ((info.cacheSizeBytes ?: 0L).coerceAtLeast(0L) / totalF).coerceIn(0f, 1f)
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(drxColors.elevatedSurface),
          ) {
            if (appRatio.isFinite() && appRatio > 0.01f) {
              Box(modifier = Modifier.weight(appRatio).fillMaxHeight().background(drxColors.electricBlue))
            }
            if (dataRatio.isFinite() && dataRatio > 0.01f) {
              Box(modifier = Modifier.weight(dataRatio).fillMaxHeight().background(drxColors.neonCyan))
            }
            if (cacheRatio.isFinite() && cacheRatio > 0.01f) {
              Box(modifier = Modifier.weight(cacheRatio).fillMaxHeight().background(drxColors.warningAmber))
            }
          }
          Spacer(Modifier.height(10.dp))
        }

        StatsKeyValueRow(
          if (isIndo) "Ukuran Aplikasi" else "App Size",
          TimeAndSizeFormatters.formatBytesAuto(info.appSizeBytes, strings.notAvailableLabel),
        )
        StatsKeyValueRow(
          if (isIndo) "Ukuran File APK" else "APK Size",
          TimeAndSizeFormatters.formatBytesAuto(info.apkSizeBytes, strings.notAvailableLabel),
        )
        StatsKeyValueRow(
          if (isIndo) "Ukuran Data Aplikasi" else "Data Size",
          TimeAndSizeFormatters.formatBytesAuto(info.dataSizeBytes, strings.notAvailableLabel),
        )
        StatsKeyValueRow(
          if (isIndo) "Ukuran Cache" else "Cache Size",
          TimeAndSizeFormatters.formatBytesAuto(info.cacheSizeBytes, strings.notAvailableLabel),
        )
        StatsKeyValueRow(
          if (isIndo) "Total Penyimpanan" else "Total Storage Usage",
          TimeAndSizeFormatters.formatBytesAuto(info.totalStorageBytes, strings.notAvailableLabel),
        )

        if (info.storageAccessRestricted) {
          Spacer(Modifier.height(8.dp))
          Text(
            text =
              if (isIndo) "Data & Cache aplikasi lain dilindungi OS tanpa izin Usage Access."
              else "OS restricts other apps' Data & Cache sizes unless Usage Access is granted.",
            style = DrxTypographyTokens.caption,
            color = drxColors.warningAmber,
          )
        }
      }
    }

    // 7. INTERACTIVE ACTIVITY CHART (7 Days / 30 Days)
    item {
      val buckets = if (is30DaysChart) stats.chart30Days else stats.chart7Days
      DrxGlassCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.BarChart, contentDescription = null, tint = drxColors.neonCyan, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            DrxSectionHeader(title = strings.activityChartTitle, modifier = Modifier.weight(1f))
          }
          Spacer(Modifier.width(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DrxFilterPill(
              label = strings.filter7Days,
              selected = !is30DaysChart,
              onClick = { is30DaysChart = false },
              modifier = Modifier.testTag("chart_toggle_7d"),
            )
            DrxFilterPill(
              label = strings.filter30Days,
              selected = is30DaysChart,
              onClick = { is30DaysChart = true },
              modifier = Modifier.testTag("chart_toggle_30d"),
            )
          }
        }
        Spacer(Modifier.height(12.dp))
        DrxActivityBarChart(buckets = buckets)
      }
    }

    // 8. FILTERABLE PLAY SESSION HISTORY
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          DrxSectionHeader(title = strings.sessionHistoryTitle, modifier = Modifier.weight(1f))
          if (gameSessions.isNotEmpty()) {
            DrxSecondaryButton(
              label = strings.clearHistoryBtn,
              icon = Icons.Default.DeleteSweep,
              onClick = { viewModel.clearHistory() },
              modifier = Modifier.height(38.dp).testTag("clear_game_sessions_btn"),
            )
          }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          val filters =
            listOf(
              StatsTimeFilter.TODAY to strings.filterToday,
              StatsTimeFilter.DAYS_7 to strings.filter7Days,
              StatsTimeFilter.DAYS_30 to strings.filter30Days,
              StatsTimeFilter.ALL to strings.filterAll,
            )
          items(filters) { (filter, label) ->
            DrxFilterPill(
              label = label,
              selected = historyFilter == filter,
              onClick = { historyFilter = filter },
              modifier = Modifier.testTag("session_filter_${filter.id}"),
            )
          }
        }
      }
    }

    if (gameSessions.isEmpty()) {
      item {
        DrxGlassCard {
          Text(
            text = strings.noSessionsYet,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      items(gameSessions, key = { it.id }) { s ->
        val dateText = TimeAndSizeFormatters.formatDate(s.startedAt, isIndo)
        val startClock = TimeAndSizeFormatters.formatClockShort(s.startedAt)
        val endClock = TimeAndSizeFormatters.formatClockShort(s.endedAt)
        val durText = TimeAndSizeFormatters.formatDurationReadable(s.durationMs, isIndo)
        DrxGlassCard(contentPadding = PaddingValues(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "$dateText • ${s.gameName}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Spacer(Modifier.height(3.dp))
              Text(
                text = "$startClock–$endClock • $durText • Peak ${String.format(Locale.US, "%.1f°C", s.peakTempC)}",
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              text = s.profileUsed.uppercase(Locale.US),
              style = MaterialTheme.typography.labelSmall,
              color = drxColors.neonCyan,
            )
          }
        }
      }
    }
  }
}

@Composable
fun DrxActivityBarChart(
  buckets: List<DayActivityBucket>,
  modifier: Modifier = Modifier,
) {
  val drxColors = LocalDrxColors.current
  val maxMs = (buckets.maxOfOrNull { it.durationMs } ?: 1L).coerceAtLeast(60_000L).toFloat()

  Column(modifier = modifier.fillMaxWidth()) {
    Canvas(modifier = Modifier.fillMaxWidth().height(126.dp)) {
      if (buckets.isEmpty()) return@Canvas
      val count = buckets.size
      val gap = if (count > 12) 4.dp.toPx() else 10.dp.toPx()
      val barW = ((size.width - gap * (count - 1)) / count).coerceAtLeast(4f)
      buckets.forEachIndexed { idx, bucket ->
        val ratio = (bucket.durationMs.toFloat() / maxMs).coerceIn(0f, 1f)
        val barH = (ratio * (size.height - 12f)).coerceAtLeast(if (bucket.durationMs > 0L) 10f else 4f)
        val x = idx * (barW + gap)
        val isLatest = idx == count - 1
        drawRoundRect(
          brush =
            Brush.verticalGradient(
              when {
                bucket.durationMs <= 0L -> listOf(drxColors.elevatedSurface, drxColors.elevatedSurface)
                isLatest -> listOf(drxColors.neonCyan, drxColors.electricBlue)
                else -> listOf(drxColors.electricBlue, drxColors.electricBlueDeep)
              }
            ),
          topLeft = Offset(x, size.height - barH),
          size = Size(barW, barH),
          cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        )
      }
    }
    Spacer(Modifier.height(6.dp))
    if (buckets.size <= 7) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        buckets.forEach { b ->
          Text(
            text = b.dayLabel,
            style = DrxTypographyTokens.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else if (buckets.size >= 2) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(buckets.first().dayLabel, style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(buckets[buckets.size / 2].dayLabel, style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(buckets.last().dayLabel, style = DrxTypographyTokens.caption, color = drxColors.neonCyan)
      }
    }
  }
}

@Composable
private fun StatsKeyValueRow(
  label: String,
  value: String,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(0.45f),
      )
      Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(0.55f),
      )
    }
    DrxHairlineDivider()
  }
}

@Composable
private fun StatMiniTile(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  val drxColors = LocalDrxColors.current
  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(16.dp))
        .background(drxColors.secondaryBg)
        .border(1.dp, drxColors.glassBorder, RoundedCornerShape(16.dp))
        .padding(12.dp),
  ) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    Text(
      value,
      style =
        DrxTypographyTokens.statNumberCompact.copy(
          fontFamily = ManropeFamily,
          fontSize = 18.sp,
          lineHeight = 22.sp,
        ),
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
