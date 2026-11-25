package com.example.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GameResourceReport
import com.example.data.model.LanguageMode
import com.example.i18n.LocalStrings
import com.example.system.TimeAndSizeFormatters
import com.example.ui.theme.DrxCustomModal
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxMainViewModel
import java.util.Locale

@Composable
fun GameResourceReportScreen(
  viewModel: DrxMainViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val prefs by viewModel.userPrefs.collectAsStateWithLifecycle()
  val games by viewModel.games.collectAsStateWithLifecycle()
  val sessions by viewModel.allSessions.collectAsStateWithLifecycle()
  val timelineEvents by viewModel.sessionTimelineEvents.collectAsStateWithLifecycle()
  val sessionReports by viewModel.sessionReports.collectAsStateWithLifecycle()
  val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  val reports =
    remember(games, sessions, timelineEvents, sessionReports, telemetry.cpuInfo.isUsageRestricted, isIndo) {
      viewModel.buildGameResourceReports()
    }

  var comparePackage by remember { mutableStateOf<String?>(null) }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("resource_report_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.resourceReportTitle,
        subtitle = strings.resourceReportSub,
        onBack = onBack,
      )
    }

    item {
      DrxGlassCard(accentColor = drxColors.electricBlue, cornerRadius = 18.dp) {
        Text(
          text =
            if (isIndo) {
              "Catatan Transparansi Android: Konsumsi baterai dihitung sebagai estimasi delta persentase selama sesi DRX. Data CPU per aplikasi dibatasi Android 8+ tanpa root."
            } else {
              "Android Transparency Note: Battery drain reflects estimated session delta while DRX monitoring is active. Per-app raw CPU/GPU telemetry is restricted by Android 8+ without root."
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (reports.isEmpty()) {
      item {
        DrxGlassCard {
          Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Icon(Icons.Default.Assessment, contentDescription = null, tint = drxColors.neonCyan, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text(
              text = if (isIndo) "Belum ada laporan sumber daya game" else "No Game Resource Reports Yet",
              style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text =
                if (isIndo) "Mainkan & simpan sesi dari DRX untuk melihat laporan per-game."
                else "Launch and save a gaming session through DRX to generate per-game reports.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      items(reports, key = { it.packageName }) { report ->
        val gameSessionCount = sessions.count { it.gamePackageName == report.packageName }
        ResourceReportCard(
          report = report,
          canCompareSameGame = gameSessionCount >= 2,
          isIndo = isIndo,
          onCompareClick = { comparePackage = report.packageName },
        )
      }
    }
  }

  val pkgToCompare = comparePackage
  if (pkgToCompare != null) {
    val sameGameSessions =
      remember(sessions, pkgToCompare) {
        sessions.filter { it.gamePackageName == pkgToCompare }.sortedByDescending { it.endedAt }
      }
    if (sameGameSessions.size >= 2) {
      val s1 = sameGameSessions[0]
      val s2 = sameGameSessions[1]
      val metrics = remember(s1, s2) { viewModel.compareGameSessions(s1, s2) }
      DrxCustomModal(
        title = "${s1.gameName} — ${if (isIndo) "Perbandingan Sesi" else "Session Compare"}",
        onDismiss = { comparePackage = null },
      ) {
        Text(
          text = if (isIndo) "Sesi Terbaru vs Sesi Sebelumnya" else "Latest Session vs Previous Session",
          style = MaterialTheme.typography.bodySmall,
          color = drxColors.neonCyan,
        )
        Spacer(Modifier.height(10.dp))
        metrics.forEach { m ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(m.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.2f))
            Text(m.leftValue, style = DrxTypographyTokens.numericReadout, modifier = Modifier.weight(0.9f))
            Text(m.rightValue, style = DrxTypographyTokens.numericReadout, modifier = Modifier.weight(0.9f))
            Text(m.deltaText, style = MaterialTheme.typography.labelMedium, color = drxColors.electricBlue)
          }
        }
      }
    }
  }
}

@Composable
private fun ResourceReportCard(
  report: GameResourceReport,
  canCompareSameGame: Boolean,
  isIndo: Boolean,
  onCompareClick: () -> Unit,
) {
  val drxColors = LocalDrxColors.current
  DrxGlassCard(
    accentColor = if (report.thermalWarningCount > 0) drxColors.warningAmber else drxColors.electricBlue,
    cornerRadius = 22.dp,
    modifier = Modifier.testTag("resource_report_card_${report.packageName}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(report.gameName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Text(
          text = report.monitoringAvailabilityLabel,
          style = DrxTypographyTokens.caption,
          color = drxColors.neonCyan,
        )
      }
      DrxStatusPill(
        text = "${report.sessionCount} ${if (isIndo) "Sesi" else "Sessions"}",
        color = drxColors.electricBlue,
      )
    }

    Spacer(Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      ReportStatItem(
        label = if (isIndo) "Total Playtime" else "Total Playtime",
        value = TimeAndSizeFormatters.formatDurationCompact(report.totalPlaytimeMs, isIndo),
        modifier = Modifier.weight(1f),
      )
      ReportStatItem(
        label = if (isIndo) "Rata-rata Sesi" else "Avg Session",
        value = TimeAndSizeFormatters.formatDurationCompact(report.averageSessionDurationMs, isIndo),
        modifier = Modifier.weight(1f),
      )
      ReportStatItem(
        label = if (isIndo) "Est. Baterai" else "Est. Battery",
        value = "-${report.estimatedTotalBatteryDrainPercent}%",
        modifier = Modifier.weight(1f),
      )
    }

    Spacer(Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      ReportStatItem(
        label = if (isIndo) "Rata RAM" else "Avg RAM",
        value = report.avgRamUsedPercent?.let { "${it.toInt()}%" }
          ?: report.avgRamFreeMb?.let { "$it MB free" }
          ?: "-",
        modifier = Modifier.weight(1f),
      )
      ReportStatItem(
        label = if (isIndo) "Rata CPU" else "Avg CPU",
        value =
          report.avgCpuPercentOrNull?.let { "${it.toInt()}%" }
            ?: if (report.hasRestrictedCpu) (if (isIndo) "Dibatasi OS" else "OS Restricted") else "-",
        modifier = Modifier.weight(1f),
      )
      ReportStatItem(
        label = if (isIndo) "Warn / Puncak" else "Warn / Peak",
        value = String.format(Locale.US, "%d • %.1f°C", report.thermalWarningCount, report.peakTempC),
        modifier = Modifier.weight(1f),
      )
    }

    if (canCompareSameGame) {
      Spacer(Modifier.height(12.dp))
      DrxSecondaryButton(
        label = if (isIndo) "Bandingkan 2 Sesi Game Ini" else "Compare 2 Sessions for This Game",
        icon = Icons.AutoMirrored.Filled.CompareArrows,
        onClick = onCompareClick,
        modifier = Modifier.fillMaxWidth().height(42.dp),
      )
    }
  }
}

@Composable
private fun ReportStatItem(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    Text(label, style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = DrxTypographyTokens.numericReadout, color = MaterialTheme.colorScheme.onSurface)
  }
}
