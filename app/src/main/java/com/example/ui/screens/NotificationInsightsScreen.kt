package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LanguageMode
import com.example.data.model.NotificationDayBucket
import com.example.i18n.LocalStrings
import com.example.ui.theme.DrxCustomProgressBar
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSectionHeader
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxNotificationsViewModel
import kotlin.math.roundToInt

@Composable
fun NotificationInsightsScreen(
  notificationsViewModel: DrxNotificationsViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val prefs by notificationsViewModel.userPrefs.collectAsStateWithLifecycle()
  val summary by notificationsViewModel.insightsSummary.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  LazyColumn(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("notification_insights_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.notificationInsightsTitle,
        subtitle = strings.notificationInsightsSub,
        onBack = onBack,
      )
    }

    if (summary.totalCount == 0) {
      item {
        DrxGlassCard(cornerRadius = 20.dp) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Icon(Icons.Default.BarChart, contentDescription = null, tint = drxColors.electricBlue, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(10.dp))
            Text(
              text = if (isIndo) "Belum Ada Data Analitik" else "No Insights Data Yet",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text = if (isIndo) "Statistik hanya dihitung dari notifikasi game nyata yang tercatat di Room." else "Analytics are calculated strictly from real game notifications stored locally.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      return@LazyColumn
    }

    // Core KPI Row
    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DrxGlassCard(modifier = Modifier.weight(1f), accentColor = drxColors.electricBlue) {
          Text(if (isIndo) "Total Notifikasi" else "Total Logged", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(Modifier.height(4.dp))
          Text("${summary.totalCount}", style = MaterialTheme.typography.displaySmall, color = drxColors.neonCyan)
          Text(
            text = if (isIndo) "${summary.unreadCount} belum dibaca" else "${summary.unreadCount} unread",
            style = DrxTypographyTokens.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        DrxGlassCard(modifier = Modifier.weight(1f), accentColor = drxColors.optimalGreen) {
          Text(if (isIndo) "Hari Ini / 7 Hari" else "Today / 7 Days", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(Modifier.height(4.dp))
          Text("${summary.todayCount} / ${summary.weekCount}", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
          Text(
            text =
              when (summary.trendDirection) {
                "UP" -> "+${summary.trendPercentDelta ?: 0}% vs 7d"
                "DOWN" -> "${summary.trendPercentDelta ?: 0}% vs 7d"
                "STABLE" -> if (isIndo) "Stabil" else "Stable"
                else -> if (isIndo) "Data pembanding kurang" else "Need prior week data"
              },
            style = DrxTypographyTokens.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Most Active Game & Peak Hours
    item {
      DrxGlassCard(accentColor = drxColors.neonCyan) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Notifications, contentDescription = null, tint = drxColors.neonCyan, modifier = Modifier.size(22.dp))
          Spacer(Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(if (isIndo) "Game Paling Aktif" else "Most Active Game", style = MaterialTheme.typography.labelMedium, color = drxColors.neonCyan)
            Text(
              text = summary.mostActiveGameName ?: "-",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = if (isIndo) "${summary.mostActiveGameCount} notifikasi tercatat" else "${summary.mostActiveGameCount} notifications recorded",
              style = DrxTypographyTokens.caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          val trendIcon =
            when (summary.trendDirection) {
              "UP" -> Icons.AutoMirrored.Filled.TrendingUp
              "DOWN" -> Icons.AutoMirrored.Filled.TrendingDown
              else -> Icons.AutoMirrored.Filled.TrendingFlat
            }
          Icon(trendIcon, contentDescription = null, tint = drxColors.electricBlue)
        }

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Schedule, contentDescription = null, tint = drxColors.electricBlue, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(
            text = if (isIndo) "Jam Paling Aktif: ${summary.mostActiveTimeRange ?: "-"}" else "Peak Activity Window: ${summary.mostActiveTimeRange ?: "-"}",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }

    // 7-Day Daily Bar Chart
    item {
      DrxSectionHeader(title = if (isIndo) "Grafik Harian (7 Hari)" else "Daily Activity (7 Days)")
      Spacer(Modifier.height(8.dp))
      InsightBarChartCard(buckets = summary.daily7dChart, barColor = drxColors.electricBlue)
    }

    // 4-Week Bar Chart
    item {
      DrxSectionHeader(title = if (isIndo) "Grafik Mingguan (4 Minggu)" else "Weekly Activity (4 Weeks)")
      Spacer(Modifier.height(8.dp))
      InsightBarChartCard(buckets = summary.weekly4wChart, barColor = drxColors.neonCyan)
    }

    // Distribution per Game
    item {
      DrxSectionHeader(title = if (isIndo) "Distribusi Notifikasi per Game" else "Per-Game Distribution")
      Spacer(Modifier.height(8.dp))
      DrxGlassCard {
        summary.gameDistribution.forEachIndexed { idx, share ->
          if (idx > 0) Spacer(Modifier.height(12.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = share.gameName,
              style = MaterialTheme.typography.titleSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            DrxStatusPill(
              text = "${share.count} (${(share.fraction * 100f).roundToInt()}%)",
              color = drxColors.neonCyan,
            )
          }
          Spacer(Modifier.height(6.dp))
          DrxCustomProgressBar(progress = share.fraction)
        }
      }
    }
  }
}

@Composable
private fun InsightBarChartCard(
  buckets: List<NotificationDayBucket>,
  barColor: androidx.compose.ui.graphics.Color,
) {
  val drxColors = LocalDrxColors.current
  val maxCount = (buckets.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
  DrxGlassCard {
    Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
      if (buckets.isEmpty()) return@Canvas
      val count = buckets.size
      val slotW = size.width / count.toFloat()
      val barW = (slotW * 0.48f).coerceAtLeast(10f)
      buckets.forEachIndexed { idx, b ->
        val ratio = b.count.toFloat() / maxCount.toFloat()
        val barH = (size.height - 10.dp.toPx()) * ratio
        val x = idx * slotW + (slotW - barW) / 2f
        val y = size.height - barH
        drawRoundRect(
          color = drxColors.elevatedSurface,
          topLeft = Offset(x, 0f),
          size = Size(barW, size.height),
          cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        )
        if (b.count > 0) {
          drawRoundRect(
            color = barColor,
            topLeft = Offset(x, y),
            size = Size(barW, barH.coerceAtLeast(6.dp.toPx())),
            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
          )
        }
      }
    }
    Spacer(Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      buckets.forEach { b ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
          Text("${b.count}", style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurface)
          Text(b.label, style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
      }
    }
  }
}
