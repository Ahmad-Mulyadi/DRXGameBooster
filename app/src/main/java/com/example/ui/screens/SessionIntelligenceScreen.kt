package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.GameSessionEntity
import com.example.data.model.LanguageMode
import com.example.i18n.LocalStrings
import com.example.system.SessionIntelligenceEngine
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
import com.example.ui.theme.rememberDrxAnimatedFloat
import com.example.ui.viewmodel.DrxMainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SessionIntelligenceScreen(
  viewModel: DrxMainViewModel,
  onOpenGameStats: (String) -> Unit,
  onBack: () -> Unit,
  onOpenSessionRecap: () -> Unit = {},
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val prefs by viewModel.userPrefs.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN
  val summary by viewModel.sessionIntelligence.collectAsStateWithLifecycle()
  val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()
  val anims = prefs.animationsEnabled

  var chartMode by remember { mutableIntStateOf(0) } // 0 = 7d Daily, 1 = 4w Weekly
  var sessionAId by remember { mutableStateOf<Long?>(null) }
  var sessionBId by remember { mutableStateOf<Long?>(null) }
  var exportStatusBanner by remember { mutableStateOf<String?>(null) }

  val sortedSessions = remember(allSessions) { allSessions.sortedByDescending { it.startedAt } }
  val avgBatteryDrainPercent =
    remember(allSessions) {
      if (allSessions.isEmpty()) null
      else allSessions.map { (it.batteryStartPercent - it.batteryEndPercent).coerceAtLeast(0).toFloat() }.average().toFloat()
    }
  val maxObservedTempC =
    remember(allSessions) {
      allSessions.maxOfOrNull { it.peakTempC }
    }
  val resolvedA =
    remember(sortedSessions, sessionAId) {
      sortedSessions.find { it.id == sessionAId } ?: sortedSessions.getOrNull(0)
    }
  val resolvedB =
    remember(sortedSessions, sessionBId, resolvedA) {
      sortedSessions.find { it.id == sessionBId }
        ?: sortedSessions.firstOrNull { it.id != resolvedA?.id }
    }
  val comparisonMetrics =
    remember(resolvedA, resolvedB, isIndo) {
      if (resolvedA != null && resolvedB != null) {
        SessionIntelligenceEngine.compareSessions(resolvedA, resolvedB, isIndo)
      } else {
        emptyList()
      }
    }

  val saveReportLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
      if (uri != null) {
        scope.launch(Dispatchers.IO) {
          val text = viewModel.buildSessionExportReportText()
          val ok =
            runCatching {
              context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
              }
              true
            }.getOrDefault(false)
          exportStatusBanner =
            if (ok) {
              if (isIndo) "Laporan sesi berhasil diekspor (.txt)." else "Session intelligence report exported (.txt)."
            } else {
              if (isIndo) "Gagal menulis file laporan ke lokasi tujuan." else "Failed to write report file."
            }
        }
      }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.sessionIntelligenceTitle,
        subtitle = strings.sessionIntelligenceSub,
        onBack = onBack,
      )
    }

    exportStatusBanner?.let { banner ->
      item {
        DrxNotificationBanner(
          title = strings.exportReportBtn,
          body = banner,
          accentColor = drxColors.optimalGreen,
          icon = Icons.Default.Insights,
          onDismiss = { exportStatusBanner = null },
        )
      }
    }

    // Export Actions
    item {
      DrxStaggeredEntrance(index = 0, animationsEnabled = anims) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          DrxPrimaryButton(
            label = strings.exportReportBtn,
            icon = Icons.Default.Share,
            onClick = {
              val text = viewModel.buildSessionExportReportText()
              val intent =
                Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_SUBJECT, "DRX Session Intelligence Report")
                  putExtra(Intent.EXTRA_TEXT, text)
                }
              runCatching { context.startActivity(Intent.createChooser(intent, strings.exportReportBtn)) }
            },
            modifier = Modifier.weight(1f).testTag("session_share_report_btn"),
          )
          DrxSecondaryButton(
            label = if (isIndo) "Simpan .TXT" else "Save .TXT",
            icon = Icons.Default.Download,
            onClick = {
              val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
              runCatching { saveReportLauncher.launch("DRX_Session_Report_$stamp.txt") }
            },
            modifier = Modifier.weight(1f).testTag("session_save_txt_btn"),
          )
        }
        Spacer(Modifier.height(8.dp))
        DrxSecondaryButton(
          label = strings.sessionRecapTitle,
          icon = Icons.Default.Timer,
          onClick = onOpenSessionRecap,
          modifier = Modifier.fillMaxWidth().height(42.dp).testTag("session_open_recap_btn"),
        )
      }
    }

    // Top KPI Summary Card
    item {
      DrxStaggeredEntrance(index = 1, animationsEnabled = anims) {
        DrxGlassCard(
          accentColor = drxColors.electricBlue,
          highlighted = true,
          modifier = Modifier.testTag("session_kpi_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = if (isIndo) "Ringkasan Sesi Bermain" else "Gaming Session Overview",
              style = MaterialTheme.typography.titleLarge,
            )
            DrxStatusPill(
              text = "${summary.totalSessionsCount} ${if (isIndo) "sesi" else "sessions"}",
              color = drxColors.neonCyan,
            )
          }
          Spacer(Modifier.height(12.dp))

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SessionKpiBox(
              label = if (isIndo) "Total Waktu Main" else "Total Playtime",
              value = TimeAndSizeFormatters.formatDurationCompact(summary.totalGamingTimeMs, isIndo),
              modifier = Modifier.weight(1f),
            )
            SessionKpiBox(
              label = if (isIndo) "Rata-rata Sesi" else "Avg Session",
              value = TimeAndSizeFormatters.formatDurationCompact(summary.averageSessionDurationMs, isIndo),
              modifier = Modifier.weight(1f),
            )
          }
          Spacer(Modifier.height(8.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SessionKpiBox(
              label = if (isIndo) "Sesi Terlama" else "Longest Session",
              value =
                if (summary.longestSessionDurationMs > 0L) {
                  val namePart = summary.longestSessionGameName?.take(10)?.let { " ($it)" } ?: ""
                  "${TimeAndSizeFormatters.formatDurationCompact(summary.longestSessionDurationMs, isIndo)}$namePart"
                } else {
                  "-"
                },
              modifier = Modifier.weight(1f),
            )
            SessionKpiBox(
              label = if (isIndo) "Terakhir Dimainkan" else "Last Played",
              value = summary.lastPlayedGameName ?: "-",
              modifier = Modifier.weight(1f),
            )
          }
          Spacer(Modifier.height(8.dp))
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(drxColors.elevatedSurface)
                .clickable(enabled = !summary.mostPlayedGamePkg.isNullOrBlank()) {
                  summary.mostPlayedGamePkg?.let(onOpenGameStats)
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = if (isIndo) "Game Paling Sering Dimainkan" else "Most Played Game",
                style = DrxTypographyTokens.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = summary.mostPlayedGameName ?: if (isIndo) "Belum ada data sesi" else "No session records yet",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Text(
              text =
                if (summary.mostPlayedTimeMs > 0L) TimeAndSizeFormatters.formatDurationCompact(summary.mostPlayedTimeMs, isIndo)
                else "-",
              style = DrxTypographyTokens.statNumberCompact.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
              color = drxColors.neonCyan,
            )
          }
        }
      }
    }

    // Minimalist Daily / Weekly Chart + Weekly Summary Delta
    item {
      DrxStaggeredEntrance(index = 2, animationsEnabled = anims) {
        val buckets = if (chartMode == 0) summary.dailyChart7d else summary.weeklyChart4w
        val maxVal = remember(buckets) { (buckets.maxOfOrNull { it.durationMs } ?: 0L).coerceAtLeast(60_000L) }
        DrxGlassCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = strings.weeklySummaryTitle,
              style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              DrxFilterPill(
                label = "7D",
                selected = chartMode == 0,
                onClick = { chartMode = 0 },
              )
              DrxFilterPill(
                label = "4W",
                selected = chartMode == 1,
                onClick = { chartMode = 1 },
              )
            }
          }
          Spacer(Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth().height(126.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
          ) {
            buckets.forEach { b ->
              val ratio = (b.durationMs.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
              val animRatio = rememberDrxAnimatedFloat(targetValue = ratio, animationsEnabled = anims)
              Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
              ) {
                Text(
                  text = if (b.durationMs > 0L) "${(b.durationMs / 60_000L)}m" else "0",
                  style = DrxTypographyTokens.caption,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                  modifier =
                    Modifier
                      .fillMaxWidth(0.68f)
                      .height((animRatio * 80f).coerceAtLeast(if (b.durationMs > 0L) 6f else 2f).dp)
                      .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                      .background(
                        Brush.verticalGradient(
                          listOf(drxColors.neonCyan, drxColors.electricBlue)
                        )
                      )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                  text = b.dayLabel,
                  style = DrxTypographyTokens.caption,
                  maxLines = 1,
                )
              }
            }
          }

          Spacer(Modifier.height(12.dp))
          DrxHairlineDivider()
          Spacer(Modifier.height(10.dp))

          val deltaMin = (summary.thisWeekTimeMs - summary.previousWeekTimeMs) / 60_000L
          val pctSuffix = summary.weekDeltaPercent?.let { " (${if (it >= 0) "+" else ""}$it%)" } ?: ""
          val deltaText =
            when {
              deltaMin > 0L -> "+${deltaMin}m vs prev 7d$pctSuffix"
              deltaMin < 0L -> "${deltaMin}m vs prev 7d$pctSuffix"
              else -> if (isIndo) "Sama dengan minggu lalu" else "Even vs previous 7d"
            }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(
                text = if (isIndo) "7 Hari Terakhir" else "Current 7 Days",
                style = DrxTypographyTokens.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = TimeAndSizeFormatters.formatDurationCompact(summary.thisWeekTimeMs, isIndo),
                style = MaterialTheme.typography.titleMedium,
              )
            }
            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = if (isIndo) "7 Hari Sebelumnya" else "Previous 7 Days",
                style = DrxTypographyTokens.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = TimeAndSizeFormatters.formatDurationCompact(summary.previousWeekTimeMs, isIndo),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Spacer(Modifier.height(6.dp))
          DrxStatusPill(
            text = deltaText,
            color = if (deltaMin >= 0L) drxColors.optimalGreen else drxColors.warningAmber,
          )
        }
      }
    }

    // Personal Gaming Statistics
    item {
      DrxStaggeredEntrance(index = 3, animationsEnabled = anims) {
        DrxGlassCard {
          Text(strings.personalStatsTitle, style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.height(10.dp))
          PersonalStatRow(
            label = if (isIndo) "Jam Bermain Teraktif" else "Peak Gaming Window",
            value = summary.peakHourWindow ?: "-",
          )
          PersonalStatRow(
            label = if (isIndo) "Rata-rata Harian" else "Daily Avg (Active Days)",
            value = TimeAndSizeFormatters.formatDurationCompact(summary.averageDailyPlaytimeMs, isIndo),
          )
          PersonalStatRow(
            label = if (isIndo) "Profil Favorit" else "Favorite Profile",
            value = summary.favoriteProfileUsed?.uppercase(Locale.US) ?: "-",
          )
          PersonalStatRow(
            label = if (isIndo) "Rata-rata Penurunan Baterai" else "Avg Battery Drain / Session",
            value =
              avgBatteryDrainPercent?.let { "${String.format(Locale.US, "%.1f", it)}%" } ?: "-",
          )
          PersonalStatRow(
            label = if (isIndo) "Suhu Puncak Tertinggi" else "Highest Recorded Temp",
            value =
              maxObservedTempC?.let { "${String.format(Locale.US, "%.1f", it)}°C" } ?: "-",
          )
        }
      }
    }

    // Session Comparison Card
    item {
      DrxStaggeredEntrance(index = 4, animationsEnabled = anims) {
        DrxGlassCard(modifier = Modifier.testTag("session_comparison_card")) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.CompareArrows,
              contentDescription = null,
              tint = drxColors.neonCyan,
              modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(strings.compareSessionsTitle, style = MaterialTheme.typography.titleMedium)
          }
          Spacer(Modifier.height(8.dp))
          if (sortedSessions.size < 2) {
            Text(
              text =
                if (isIndo)
                  "Minimal 2 sesi tersimpan diperlukan untuk membandingkan durasi, profil, baterai, dan suhu."
                else
                  "At least 2 recorded gaming sessions are needed to compare duration, profile, battery drain, and temperature.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            val fmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.US) }
            Text(
              text = if (isIndo) "Pilih Sesi A:" else "Select Session A:",
              style = DrxTypographyTokens.caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              sortedSessions.take(8).forEach { s ->
                DrxFilterPill(
                  label = "${s.gameName.take(10)} • ${fmt.format(Date(s.startedAt))}",
                  selected = s.id == resolvedA?.id,
                  onClick = { sessionAId = s.id },
                )
              }
            }
            Spacer(Modifier.height(8.dp))
            Text(
              text = if (isIndo) "Pilih Sesi B:" else "Select Session B:",
              style = DrxTypographyTokens.caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              sortedSessions.take(8).forEach { s ->
                DrxFilterPill(
                  label = "${s.gameName.take(10)} • ${fmt.format(Date(s.startedAt))}",
                  selected = s.id == resolvedB?.id,
                  onClick = { sessionBId = s.id },
                )
              }
            }
            Spacer(Modifier.height(10.dp))
            DrxHairlineDivider()
            Spacer(Modifier.height(8.dp))
            comparisonMetrics.forEach { row ->
              Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = row.label,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.weight(0.32f),
                )
                Text(
                  text = row.leftValue,
                  style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                  modifier = Modifier.weight(0.24f),
                )
                Text(
                  text = row.rightValue,
                  style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                  modifier = Modifier.weight(0.24f),
                )
                Text(
                  text = row.deltaText,
                  style = DrxTypographyTokens.caption.copy(fontWeight = FontWeight.SemiBold),
                  color = drxColors.neonCyan,
                  modifier = Modifier.weight(0.20f),
                )
              }
            }
          }
        }
      }
    }

    // Recent Session History
    item {
      Text(
        text = if (isIndo) "Riwayat Sesi Terbaru" else "Recent Gaming Sessions",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 4.dp),
      )
    }

    if (sortedSessions.isEmpty()) {
      item {
        DrxGlassCard {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Timer, contentDescription = null, tint = drxColors.mutedText)
            Spacer(Modifier.width(10.dp))
            Text(
              text =
                if (isIndo) "Belum ada sesi bermain yang terekam oleh DRX."
                else "No DRX gaming sessions recorded yet. Launch a game from Games or Quick Play to begin.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      items(sortedSessions.take(15), key = { it.id }) { session ->
        SessionHistoryCard(
          session = session,
          isIndo = isIndo,
          onClick = { onOpenGameStats(session.gamePackageName) },
        )
      }
    }
  }
}

@Composable
private fun SessionKpiBox(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  val drxColors = LocalDrxColors.current
  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(14.dp))
        .background(drxColors.elevatedSurface)
        .padding(10.dp),
  ) {
    Text(
      text = label,
      style = DrxTypographyTokens.caption,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(4.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun PersonalStatRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum",
      ),
    )
  }
}

@Composable
private fun SessionHistoryCard(
  session: GameSessionEntity,
  isIndo: Boolean,
  onClick: () -> Unit,
) {
  val drxColors = LocalDrxColors.current
  val dateStr =
    remember(session.startedAt) {
      SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(session.startedAt))
    }
  val drain = (session.batteryStartPercent - session.batteryEndPercent).coerceAtLeast(0)
  DrxGlassCard(onClick = onClick) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = session.gameName,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "$dateStr • ${session.profileUsed.uppercase()} • Bat -$drain% • Peak ${String.format(Locale.US, "%.1f°C", session.peakTempC)}",
          style = DrxTypographyTokens.caption,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(Modifier.width(8.dp))
      DrxStatusPill(
        text = TimeAndSizeFormatters.formatDurationCompact(session.durationMs, isIndo),
        color = drxColors.neonCyan,
      )
    }
  }
}
