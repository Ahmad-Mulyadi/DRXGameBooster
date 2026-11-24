package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LagStatus
import com.example.data.model.LanguageMode
import com.example.system.safeRoundToInt
import com.example.ui.theme.DrxCustomSlider
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxHairlineDivider
import com.example.ui.theme.DrxNotificationBanner
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxSectionHeader
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxDiagnosticsViewModel
import java.util.Locale

@Composable
fun LagDetectiveScreen(
  diagnosticsViewModel: DrxDiagnosticsViewModel,
  onOpenHistory: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val drxColors = LocalDrxColors.current
  val prefs by diagnosticsViewModel.userPrefs.collectAsStateWithLifecycle()
  val report by diagnosticsViewModel.currentLagReport.collectAsStateWithLifecycle()
  val isScanning by diagnosticsViewModel.isScanningLag.collectAsStateWithLifecycle()
  val actionBanner by diagnosticsViewModel.actionBanner.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  var showCalibration by remember { mutableStateOf(false) }
  var ramMbSlider by remember(prefs.lagRamWarningMb) { mutableIntStateOf(prefs.lagRamWarningMb) }
  var thermalSlider by remember(prefs.lagThermalWarningC) { mutableFloatStateOf(prefs.lagThermalWarningC) }
  var pingSlider by remember(prefs.lagPingWarningMs) { mutableIntStateOf(prefs.lagPingWarningMs) }
  var storageSlider by remember(prefs.lagStorageWarningMb) { mutableIntStateOf(prefs.lagStorageWarningMb) }

  LaunchedEffect(Unit) {
    if (report == null) {
      diagnosticsViewModel.runLagScan(includeNetworkProbe = true)
    }
  }

  @Composable
  fun statusColorFor(status: LagStatus): Color =
    when (status) {
      LagStatus.NORMAL -> drxColors.optimalGreen
      LagStatus.WARNING -> drxColors.warningAmber
      LagStatus.HIGH_LOAD, LagStatus.UNSTABLE -> drxColors.alertCrimson
      LagStatus.DATA_UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("lag_detective_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = "DRX Lag Detective",
        subtitle =
          if (isIndo) "Diagnostik Penyebab Lag & Beban Perangkat"
          else "Adaptive Lag Root-Cause Diagnostics",
        onBack = onBack,
        trailing = {
          DrxSecondaryButton(
            label = if (isIndo) "Riwayat" else "History",
            icon = Icons.Default.History,
            onClick = onOpenHistory,
            modifier = Modifier.height(40.dp).testTag("lag_open_history_button"),
          )
        },
      )
    }

    actionBanner?.let { msg ->
      item {
        DrxNotificationBanner(
          title = "DRX Lag Detective",
          body = msg,
          accentColor = drxColors.optimalGreen,
          onDismiss = { diagnosticsViewModel.clearActionBanner() },
        )
      }
    }

    // Scan Controls + Summary Hero
    item {
      val overall = report?.overallStatus ?: LagStatus.NORMAL
      val overallColor = statusColorFor(overall)
      DrxGlassCard(
        accentColor = overallColor,
        highlighted = report != null,
        cornerRadius = 22.dp,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          DrxStatusPill(
            text = report?.overallStatus?.label ?: if (isScanning) "SCANNING" else "READY",
            color = overallColor,
            modifier = Modifier.testTag("lag_overall_status_pill"),
          )
          report?.let { r ->
            Text(
              text = "${r.confidencePercent}% ${if (isIndo) "Akurasi Data" else "Confidence"}",
              style = MaterialTheme.typography.labelMedium,
              color = drxColors.neonCyan,
            )
          }
        }
        Spacer(Modifier.height(10.dp))
        Text(
          text =
            report?.mainSuspectedIssue
              ?: if (isScanning) {
                if (isIndo) "Memindai RAM, Thermal, Storage & Latensi..." else "Scanning RAM, Thermal, Storage & Latency..."
              } else {
                if (isIndo) "Tekan Scan untuk analisis penyebab lag" else "Tap Scan to diagnose bottlenecks"
              },
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text =
            report?.completenessNote
              ?: if (isIndo) "Menggunakan sensor nyata Android & uji endpoint jaringan." else "Uses real Android sensors and endpoint latency probe.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          DrxPrimaryButton(
            label =
              if (isScanning) {
                if (isIndo) "Memindai..." else "Scanning..."
              } else {
                if (isIndo) "Scan Ulang" else "Scan Now"
              },
            icon = Icons.Default.Radar,
            enabled = !isScanning,
            onClick = { diagnosticsViewModel.runLagScan(includeNetworkProbe = true) },
            modifier = Modifier.weight(1f).testTag("lag_scan_button"),
          )
          if (isScanning) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
              CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = drxColors.neonCyan)
            }
          }
        }
      }
    }

    // Adaptive Calibration Card
    item {
      DrxGlassCard {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .clickable { showCalibration = !showCalibration }
              .padding(vertical = 2.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Tune, contentDescription = null, tint = drxColors.neonCyan, modifier = Modifier.size(18.dp))
            Column {
              Text(
                text = if (isIndo) "Kalibrasi Ambang Perangkat" else "Adaptive Threshold Calibration",
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text =
                  if (prefs.lagRamWarningMb == 0) {
                    if (isIndo) "Mode Otomatis (sesuai kapasitas RAM perangkat)" else "Auto-calibrated to device RAM tier"
                  } else {
                    if (isIndo) "Kustom: RAM <${prefs.lagRamWarningMb}MB • ${prefs.lagThermalWarningC.safeRoundToInt()}°C"
                    else "Custom: RAM <${prefs.lagRamWarningMb}MB • ${prefs.lagThermalWarningC.safeRoundToInt()}°C"
                  },
                style = DrxTypographyTokens.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Icon(
            imageVector = if (showCalibration) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = "Toggle calibration",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        AnimatedVisibility(visible = showCalibration) {
          Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            DrxHairlineDivider()
            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
              Text(if (isIndo) "Ambang Peringatan RAM Bebas" else "Free RAM Warning Threshold", style = MaterialTheme.typography.labelLarge)
              Text(
                if (ramMbSlider == 0) "Auto" else "$ramMbSlider MB",
                style = DrxTypographyTokens.numericReadout,
                color = drxColors.neonCyan,
              )
            }
            DrxCustomSlider(
              value = ramMbSlider.toFloat(),
              onValueChange = { ramMbSlider = it.safeRoundToInt(0) },
              valueRange = 0f..2048f,
            )

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
              Text(if (isIndo) "Ambang Peringatan Suhu" else "Thermal Warning Temp", style = MaterialTheme.typography.labelLarge)
              Text(
                String.format(Locale.US, "%.1f°C", thermalSlider),
                style = DrxTypographyTokens.numericReadout,
                color = drxColors.warningAmber,
              )
            }
            DrxCustomSlider(
              value = thermalSlider,
              onValueChange = { thermalSlider = it },
              valueRange = 35f..48f,
            )

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
              Text(if (isIndo) "Ambang Latensi Ping" else "Network Ping Warning", style = MaterialTheme.typography.labelLarge)
              Text("$pingSlider ms", style = DrxTypographyTokens.numericReadout, color = drxColors.electricBlue)
            }
            DrxCustomSlider(
              value = pingSlider.toFloat(),
              onValueChange = { pingSlider = it.safeRoundToInt(90) },
              valueRange = 30f..250f,
            )

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
              Text(if (isIndo) "Ambang Sisa Storage" else "Free Storage Warning", style = MaterialTheme.typography.labelLarge)
              Text("$storageSlider MB", style = DrxTypographyTokens.numericReadout, color = drxColors.silver)
            }
            DrxCustomSlider(
              value = storageSlider.toFloat(),
              onValueChange = { storageSlider = it.safeRoundToInt(1024) },
              valueRange = 250f..4096f,
            )

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              DrxSecondaryButton(
                label = if (isIndo) "Reset Auto" else "Reset Auto",
                onClick = {
                  ramMbSlider = 0
                  thermalSlider = 40.0f
                  pingSlider = 90
                  storageSlider = 1024
                  diagnosticsViewModel.updateLagThresholds(0, 40.0f, 90, 1024)
                },
                modifier = Modifier.weight(1f),
              )
              DrxPrimaryButton(
                label = if (isIndo) "Terapkan" else "Apply",
                onClick = {
                  diagnosticsViewModel.updateLagThresholds(ramMbSlider, thermalSlider, pingSlider, storageSlider)
                  showCalibration = false
                },
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }
    }

    // 6-Metric Monitoring Tiles (2x3 grid)
    val currentReport = report
    if (currentReport != null) {
      item {
        DrxSectionHeader(title = if (isIndo) "Pemantauan Telemetri Nyata" else "Live Telemetry Breakdown")
      }
      val rows = currentReport.metricTiles.chunked(2)
      items(rows) { pair ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          pair.forEach { tile ->
            val col = statusColorFor(tile.status)
            DrxGlassCard(
              modifier = Modifier.weight(1f),
              accentColor = col,
              highlighted = tile.status != LagStatus.NORMAL && tile.status != LagStatus.DATA_UNAVAILABLE,
              contentPadding = PaddingValues(14.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = tile.label,
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f),
                )
              }
              Spacer(Modifier.height(6.dp))
              Text(
                text = tile.valueText,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = col,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Spacer(Modifier.height(2.dp))
              Text(
                text = tile.subText,
                style = DrxTypographyTokens.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              Spacer(Modifier.height(6.dp))
              DrxStatusPill(text = tile.status.label, color = col)
            }
          }
          if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
      }

      // 5 Cause Findings
      item {
        DrxSectionHeader(title = if (isIndo) "Analisis Penyebab Lag" else "Diagnostic Cause Findings")
      }
      items(currentReport.findings, key = { it.category.name }) { finding ->
        val col = statusColorFor(finding.status)
        DrxGlassCard(
          accentColor = col,
          highlighted = finding.status == LagStatus.HIGH_LOAD || finding.status == LagStatus.UNSTABLE,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = finding.title,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            DrxStatusPill(text = finding.status.label, color = col)
          }
          Spacer(Modifier.height(6.dp))
          Text(
            text = finding.explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(6.dp))
          Text(
            text = "${if (isIndo) "Bukti" else "Evidence"}: ${finding.evidence}",
            style = DrxTypographyTokens.caption,
            color = drxColors.silver,
          )
          Spacer(Modifier.height(4.dp))
          Text(
            text = "${if (isIndo) "Saran" else "Advice"}: ${finding.recommendation}",
            style = MaterialTheme.typography.bodySmall,
            color = drxColors.neonCyan,
          )
        }
      }

      // Honesty Disclosure + Save & Share Actions
      item {
        DrxNotificationBanner(
          title = if (isIndo) "Transparansi Pembacaan OS" else "OS Telemetry Honesty",
          body =
            if (isIndo) {
              "Android 8+ membatasi /proc/stat CPU dan suhu die CPU internal. Latensi yang diuji adalah ke endpoint diagnostik (bukan server internal game). Semua metrik lain berasal dari sensor perangkat nyata."
            } else {
              "Android 8+ restricts raw /proc/stat CPU load and internal CPU die temp. Ping measures the diagnostic endpoint rather than private game servers. All other values come from live device sensors."
            },
          accentColor = drxColors.electricBlue,
          icon = Icons.Outlined.Info,
        )
      }

      item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          DrxPrimaryButton(
            label = if (isIndo) "Simpan Laporan" else "Save Report",
            icon = Icons.Default.BookmarkAdd,
            onClick = { diagnosticsViewModel.saveCurrentLagReport() },
            modifier = Modifier.weight(1f).testTag("lag_save_report_button"),
          )
          DrxSecondaryButton(
            label = if (isIndo) "Bagikan" else "Share",
            icon = Icons.Default.Share,
            onClick = {
              val txt = diagnosticsViewModel.formatLagReportShareText()
              if (txt.isNotBlank()) {
                val intent =
                  Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, txt)
                  }
                runCatching {
                  context.startActivity(Intent.createChooser(intent, "Share DRX Lag Report"))
                }
              }
            },
            modifier = Modifier.weight(1f).testTag("lag_share_report_button"),
          )
        }
      }
    }
  }
}
