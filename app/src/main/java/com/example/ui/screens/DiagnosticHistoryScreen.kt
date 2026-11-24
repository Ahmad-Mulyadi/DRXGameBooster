package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
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
import com.example.data.model.LanguageMode
import com.example.system.GameStatsCollector
import com.example.system.TimeAndSizeFormatters
import com.example.ui.theme.DrxCustomModal
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxDiagnosticsViewModel
import java.util.Locale

private enum class HistoryTab {
  LAG,
  CRASH,
  HEALTH,
  SESSION,
}

private enum class DateRangeFilter {
  ALL,
  TODAY,
  WEEK,
}

private data class DetailModalPayload(
  val title: String,
  val body: String,
)

@Composable
fun DiagnosticHistoryScreen(
  diagnosticsViewModel: DrxDiagnosticsViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val drxColors = LocalDrxColors.current
  val prefs by diagnosticsViewModel.userPrefs.collectAsStateWithLifecycle()
  val lagList by diagnosticsViewModel.lagReports.collectAsStateWithLifecycle()
  val crashList by diagnosticsViewModel.crashReports.collectAsStateWithLifecycle()
  val healthList by diagnosticsViewModel.healthChecks.collectAsStateWithLifecycle()
  val sessionList by diagnosticsViewModel.sessionReports.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  var activeTab by remember { mutableStateOf(HistoryTab.LAG) }
  var dateFilter by remember { mutableStateOf(DateRangeFilter.ALL) }
  var query by remember { mutableStateOf("") }
  var detailModal by remember { mutableStateOf<DetailModalPayload?>(null) }

  fun matchesDate(ts: Long): Boolean {
    val now = System.currentTimeMillis()
    return when (dateFilter) {
      DateRangeFilter.ALL -> true
      DateRangeFilter.TODAY -> ts >= GameStatsCollector.startOfDayMs(now)
      DateRangeFilter.WEEK -> ts >= now - 7L * 86_400_000L
    }
  }

  fun shareReport(title: String, body: String) {
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, "$title\n\n$body")
      }
    runCatching {
      context.startActivity(Intent.createChooser(intent, "Share Diagnostic Report"))
    }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("diagnostic_history_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      DrxScreenHeader(
        title = if (isIndo) "Riwayat Diagnostik" else "Diagnostic History",
        subtitle =
          if (isIndo) "Laporan Lag, Crash, Kesehatan & Sesi Bermain"
          else "Saved Lag, Crash, Health & Session Reports",
        onBack = onBack,
      )
    }

    // 4-Tab Selector + Date Filter + Search
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          DrxFilterPill(
            label = "Lag (${lagList.size})",
            selected = activeTab == HistoryTab.LAG,
            onClick = { activeTab = HistoryTab.LAG },
            modifier = Modifier.weight(1f).testTag("history_tab_lag"),
          )
          DrxFilterPill(
            label = "Crash (${crashList.size})",
            selected = activeTab == HistoryTab.CRASH,
            onClick = { activeTab = HistoryTab.CRASH },
            modifier = Modifier.weight(1f).testTag("history_tab_crash"),
          )
          DrxFilterPill(
            label = "Health (${healthList.size})",
            selected = activeTab == HistoryTab.HEALTH,
            onClick = { activeTab = HistoryTab.HEALTH },
            modifier = Modifier.weight(1f).testTag("history_tab_health"),
          )
          DrxFilterPill(
            label = "Sesi (${sessionList.size})",
            selected = activeTab == HistoryTab.SESSION,
            onClick = { activeTab = HistoryTab.SESSION },
            modifier = Modifier.weight(1f).testTag("history_tab_session"),
          )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          DrxFilterPill(
            label = if (isIndo) "Semua" else "All",
            selected = dateFilter == DateRangeFilter.ALL,
            onClick = { dateFilter = DateRangeFilter.ALL },
            modifier = Modifier.weight(1f),
          )
          DrxFilterPill(
            label = if (isIndo) "Hari Ini" else "Today",
            selected = dateFilter == DateRangeFilter.TODAY,
            onClick = { dateFilter = DateRangeFilter.TODAY },
            modifier = Modifier.weight(1f),
          )
          DrxFilterPill(
            label = if (isIndo) "7 Hari" else "7 Days",
            selected = dateFilter == DateRangeFilter.WEEK,
            onClick = { dateFilter = DateRangeFilter.WEEK },
            modifier = Modifier.weight(1f),
          )
        }

        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
          placeholder = { Text(if (isIndo) "Cari game atau status..." else "Search game or status...") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("history_search_input"),
        )
      }
    }

    when (activeTab) {
      HistoryTab.LAG -> {
        val rows = lagList.filter {
          matchesDate(it.timestampMs) &&
            (query.isBlank() || it.suspectedIssue.contains(query, true) || it.overallStatus.contains(query, true) || it.gamePackageName.contains(query, true))
        }
        if (rows.isEmpty()) {
          item { EmptyHistoryCard(isIndo) }
        } else {
          items(rows, key = { it.id }) { item ->
            val fullText =
              "${item.suspectedIssue}\nStatus: ${item.overallStatus} (${item.severityLabel}, ${item.confidencePercent}%)\nRAM Free: ${item.ramFreeMb} MB • Temp: ${String.format(Locale.US, "%.1f°C", item.batteryTempC)}\nNetwork: ${item.networkType}\n\n${item.evidenceSummary}\n\nRecommendation: ${item.recommendation}"
            HistoryReportRowCard(
              title = item.suspectedIssue,
              badge = item.overallStatus,
              subtitle = "${TimeAndSizeFormatters.formatDateTimeWithZone(item.timestampMs, isIndo)} • ${item.ramFreeMb} MB RAM • ${String.format(Locale.US, "%.1f°C", item.batteryTempC)}",
              onView = { detailModal = DetailModalPayload("DRX Lag Report", fullText) },
              onShare = { shareReport("DRX Lag Report", fullText) },
              onDelete = { diagnosticsViewModel.deleteLagReport(item.id) },
            )
          }
        }
      }

      HistoryTab.CRASH -> {
        val rows = crashList.filter {
          matchesDate(it.timestampMs) &&
            (query.isBlank() || it.gameName.contains(query, true) || it.exitClassification.contains(query, true) || it.diagnosticResult.contains(query, true))
        }
        if (rows.isEmpty()) {
          item { EmptyHistoryCard(isIndo) }
        } else {
          items(rows, key = { it.id }) { item ->
            val fullText =
              "${item.gameName} (${item.gamePackageName} v${item.gameVersion})\nClassification: ${item.exitClassification}\n${item.lastPerformanceSummary}\n\nDiagnosis: ${item.diagnosticResult}\n\nPrevention Tips:\n${item.preventionTips}" +
                if (item.importedLogSummary.isNotBlank()) "\n\nLog Summary:\n${item.importedLogSummary}" else ""
            HistoryReportRowCard(
              title = item.gameName,
              badge = item.exitClassification,
              subtitle = "${TimeAndSizeFormatters.formatDateTimeWithZone(item.timestampMs, isIndo)} • ${item.ramFreeMb} MB free",
              onView = { detailModal = DetailModalPayload("DRX Crash Report", fullText) },
              onShare = { shareReport("DRX Crash Report — ${item.gameName}", fullText) },
              onDelete = { diagnosticsViewModel.deleteCrashReport(item.id) },
            )
          }
        }
      }

      HistoryTab.HEALTH -> {
        val rows = healthList.filter {
          matchesDate(it.timestampMs) &&
            (query.isBlank() || it.readinessTitle.contains(query, true) || it.categorySummary.contains(query, true))
        }
        if (rows.isEmpty()) {
          item { EmptyHistoryCard(isIndo) }
        } else {
          items(rows, key = { it.id }) { item ->
            val scoreLabel = item.overallScoreOrNull?.let { "$it/100" } ?: "N/A"
            val fullText =
              "Readiness: ${item.readinessTitle} ($scoreLabel)\nType: ${if (item.isFullDiagnostic) "Full Diagnostic" else "Quick Check"}\n\nCategories:\n${item.categorySummary}\n\nRecommendations:\n${item.recommendations}"
            HistoryReportRowCard(
              title = "${item.readinessTitle} ($scoreLabel)",
              badge = if (item.isFullDiagnostic) "FULL" else "QUICK",
              subtitle = TimeAndSizeFormatters.formatDateTimeWithZone(item.timestampMs, isIndo),
              onView = { detailModal = DetailModalPayload("DRX Gaming Health", fullText) },
              onShare = { shareReport("DRX Gaming Health ($scoreLabel)", fullText) },
              onDelete = { diagnosticsViewModel.deleteHealthCheck(item.id) },
            )
          }
        }
      }

      HistoryTab.SESSION -> {
        val rows = sessionList.filter {
          matchesDate(it.timestampMs) &&
            (query.isBlank() || it.gameName.contains(query, true) || it.detectedConditions.contains(query, true))
        }
        if (rows.isEmpty()) {
          item { EmptyHistoryCard(isIndo) }
        } else {
          items(rows, key = { it.id }) { item ->
            val durText = TimeAndSizeFormatters.formatDurationReadable(item.durationMs, isIndo)
            val fullText =
              "${item.gameName} ($durText)\nBattery: ${item.batteryStartPercent}% -> ${item.batteryEndPercent}%\nPeak Temp: ${String.format(Locale.US, "%.1f°C", item.peakTempC)}\nEnd RAM Free: ${item.ramAvailEndMb} MB\nUI FPS: ${item.avgUiFpsOrNull?.let { "${it.toInt()} FPS" } ?: "N/A"}\nNetwork: ${item.networkSummary}\n\nConditions: ${item.detectedConditions}\nRecommendations: ${item.recommendations}"
            HistoryReportRowCard(
              title = "${item.gameName} • $durText",
              badge = String.format(Locale.US, "%.1f°C", item.peakTempC),
              subtitle = "${TimeAndSizeFormatters.formatDateTimeWithZone(item.endedAtMs, isIndo)} • Bat ${item.batteryStartPercent}%→${item.batteryEndPercent}%",
              onView = { detailModal = DetailModalPayload("DRX Session Report", fullText) },
              onShare = { shareReport("DRX Session — ${item.gameName}", fullText) },
              onDelete = { diagnosticsViewModel.deleteSessionReport(item.id) },
            )
          }
        }
      }
    }
  }

  val modal = detailModal
  if (modal != null) {
    DrxCustomModal(
      title = modal.title,
      onDismiss = { detailModal = null },
    ) {
      Text(
        text = modal.body,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(Modifier.height(14.dp))
      DrxSecondaryButton(
        label = if (isIndo) "Bagikan" else "Share",
        icon = Icons.Default.Share,
        onClick = { shareReport(modal.title, modal.body) },
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

@Composable
private fun EmptyHistoryCard(isIndo: Boolean) {
  DrxGlassCard {
    Text(
      text = if (isIndo) "Belum ada laporan tersimpan di kategori ini." else "No saved reports in this category yet.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun HistoryReportRowCard(
  title: String,
  badge: String,
  subtitle: String,
  onView: () -> Unit,
  onShare: () -> Unit,
  onDelete: () -> Unit,
) {
  val drxColors = LocalDrxColors.current
  DrxGlassCard(onClick = onView, contentPadding = PaddingValues(14.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          DrxStatusPill(text = badge, color = drxColors.neonCyan)
        }
        Spacer(Modifier.height(4.dp))
        Text(
          text = subtitle,
          style = DrxTypographyTokens.caption,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(onClick = onShare) {
        Icon(Icons.Default.Share, contentDescription = "Share", tint = drxColors.electricBlue)
      }
      IconButton(onClick = onDelete) {
        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = drxColors.alertCrimson)
      }
    }
  }
}
