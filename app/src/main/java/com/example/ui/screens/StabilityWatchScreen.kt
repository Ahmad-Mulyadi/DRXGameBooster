package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LanguageMode
import com.example.data.model.StabilitySeverity
import com.example.i18n.LocalStrings
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
import com.example.ui.theme.rememberDrxConditionalPulseAlpha
import com.example.ui.viewmodel.DrxDiagnosticsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun StabilityWatchScreen(
  diagnosticsViewModel: DrxDiagnosticsViewModel,
  onBack: () -> Unit,
) {
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val prefs by diagnosticsViewModel.userPrefs.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN
  val anims = prefs.animationsEnabled

  val assessment by diagnosticsViewModel.stabilityAssessment.collectAsStateWithLifecycle()
  val isRunning by diagnosticsViewModel.isRunningStabilityWatch.collectAsStateWithLifecycle()
  val history by diagnosticsViewModel.stabilityWarnings.collectAsStateWithLifecycle()
  val banner by diagnosticsViewModel.actionBanner.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    if (assessment == null) {
      diagnosticsViewModel.runStabilityCheck(saveWarnings = false)
    }
  }

  val saveReportLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
      if (uri != null) {
        scope.launch(Dispatchers.IO) {
          val text = diagnosticsViewModel.formatStabilityReportText()
          if (text.isNotBlank()) {
            runCatching {
              context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
              }
            }
          }
        }
      }
    }

  val activeSeverity = assessment?.overallSeverity ?: StabilitySeverity.NORMAL
  val isAlertState = activeSeverity != StabilitySeverity.NORMAL
  val warningPulseAlpha =
    rememberDrxConditionalPulseAlpha(
      isPulsing = isAlertState,
      animationsEnabled = anims,
      minAlpha = 0.55f,
      maxAlpha = 1f,
    )

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.stabilityWatchTitle,
        subtitle = strings.stabilityWatchSub,
        onBack = onBack,
      )
    }

    banner?.let { msg ->
      item {
        DrxNotificationBanner(
          title = strings.stabilityWatchTitle,
          body = msg,
          accentColor = drxColors.optimalGreen,
          icon = Icons.Default.HealthAndSafety,
          onDismiss = { diagnosticsViewModel.clearActionBanner() },
        )
      }
    }

    // Main Assessment Banner
    item {
      val severityColor =
        when (activeSeverity) {
          StabilitySeverity.CRITICAL -> drxColors.alertCrimson
          StabilitySeverity.WARNING -> drxColors.warningAmber
          StabilitySeverity.NORMAL -> drxColors.optimalGreen
        }
      DrxStaggeredEntrance(index = 0, animationsEnabled = anims) {
        DrxGlassCard(
          accentColor = severityColor,
          highlighted = true,
          modifier = Modifier.testTag("stability_status_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(
                imageVector = if (isAlertState) Icons.Default.Warning else Icons.Default.HealthAndSafety,
                contentDescription = null,
                tint = severityColor,
                modifier = Modifier.size(22.dp).alpha(warningPulseAlpha),
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text =
                  when {
                    isRunning -> if (isIndo) "Memeriksa Stabilitas..." else "Scanning Telemetry..."
                    activeSeverity == StabilitySeverity.CRITICAL -> "PERFORMANCE WARNING"
                    activeSeverity == StabilitySeverity.WARNING -> "PERFORMANCE WARNING"
                    else -> if (isIndo) "Stabilitas Optimal" else "System Stability Normal"
                  },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = severityColor,
              )
            }
            DrxStatusPill(
              text = activeSeverity.name,
              color = severityColor,
              modifier = Modifier.alpha(warningPulseAlpha),
            )
          }

          Spacer(Modifier.height(10.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrxPrimaryButton(
              label = if (isRunning) (if (isIndo) "Memindai..." else "Scanning...") else (if (isIndo) "Pindai Ulang" else "Run Check"),
              icon = Icons.Default.Refresh,
              enabled = !isRunning,
              accentColor = severityColor,
              onClick = { diagnosticsViewModel.runStabilityCheck(saveWarnings = false) },
              modifier = Modifier.weight(1f).testTag("stability_run_check_btn"),
            )
            DrxSecondaryButton(
              label = strings.saveReportBtn,
              icon = Icons.Default.BookmarkAdd,
              enabled = assessment != null && !isRunning,
              onClick = { diagnosticsViewModel.saveCurrentStabilityReport() },
              modifier = Modifier.weight(1f).testTag("stability_save_report_btn"),
            )
          }

          if (assessment != null) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              DrxSecondaryButton(
                label = strings.exportReportBtn,
                icon = Icons.Default.Share,
                onClick = {
                  val txt = diagnosticsViewModel.formatStabilityReportText()
                  if (txt.isNotBlank()) {
                    val intent =
                      Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "DRX Stability Watch Report")
                        putExtra(Intent.EXTRA_TEXT, txt)
                      }
                    runCatching { context.startActivity(Intent.createChooser(intent, strings.exportReportBtn)) }
                  }
                },
                modifier = Modifier.weight(1f),
              )
              DrxSecondaryButton(
                label = "Save .TXT",
                icon = Icons.Default.Download,
                onClick = {
                  val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                  runCatching { saveReportLauncher.launch("DRX_Stability_Report_$stamp.txt") }
                },
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }
    }

    // Active Real Warnings List (or Normal confirmation)
    val currentAssessment = assessment
    if (currentAssessment != null) {
      if (currentAssessment.warnings.isEmpty()) {
        item {
          DrxStaggeredEntrance(index = 1, animationsEnabled = anims) {
            DrxGlassCard(accentColor = drxColors.optimalGreen) {
              Text(
                text =
                  if (isIndo)
                    "Tidak ada ancaman stabilitas yang terdeteksi saat ini. RAM, suhu, baterai, penyimpanan, dan jaringan berada dalam kondisi aman."
                  else
                    "No active instability triggers detected. Memory headroom, thermal state, battery, storage, and network telemetry are within normal gaming limits.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        items(currentAssessment.warnings, key = { "${it.categoryId}_${it.severity.name}" }) { warn ->
          val wColor =
            if (warn.severity == StabilitySeverity.CRITICAL) drxColors.alertCrimson
            else drxColors.warningAmber
          DrxGlassCard(accentColor = wColor, highlighted = true) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = warn.title,
                style = DrxTypographyTokens.caption.copy(fontWeight = FontWeight.Bold),
                color = wColor,
              )
              DrxStatusPill(text = warn.severity.name, color = wColor)
            }
            Spacer(Modifier.height(4.dp))
            Text(text = warn.subtitle, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
              text = warn.explanation,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text = warn.metricEvidence,
              style = DrxTypographyTokens.caption.copy(fontFeatureSettings = "tnum"),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
              text = warn.recommendation,
              style = MaterialTheme.typography.bodySmall,
              color = drxColors.neonCyan,
            )
          }
        }
      }

      // Diagnostic Details Card (Real Sensor Readings + Honest Limitation Note)
      item {
        val ramUsedPct =
          ((currentAssessment.ramTotalMb - currentAssessment.ramAvailMb).toFloat() /
            currentAssessment.ramTotalMb.coerceAtLeast(1L).toFloat() * 100f)
            .toInt()
            .coerceIn(0, 100)
        DrxStaggeredEntrance(index = 2, animationsEnabled = anims) {
          DrxGlassCard {
            Text(strings.diagnosticDetailsTitle, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            DetailMetricRow(
              label = "RAM",
              value = "${currentAssessment.ramAvailMb} MB Free ($ramUsedPct% used)",
            )
            DetailMetricRow(
              label = if (isIndo) "Suhu & Termal" else "Battery Temp & Thermal",
              value = "${String.format(Locale.US, "%.1f°C", currentAssessment.batteryTempC)} • ${currentAssessment.thermalLabel}",
            )
            DetailMetricRow(
              label = if (isIndo) "Baterai" else "Battery",
              value = "${currentAssessment.batteryLevelPercent}%",
            )
            DetailMetricRow(
              label = if (isIndo) "Penyimpanan Bebas" else "Free Storage",
              value = TimeAndSizeFormatters.formatBytesAuto(currentAssessment.storageFreeBytes),
            )
            DetailMetricRow(
              label = if (isIndo) "Jaringan" else "Network",
              value = currentAssessment.networkSummary,
            )
            Spacer(Modifier.height(8.dp))
            DrxHairlineDivider()
            Spacer(Modifier.height(8.dp))
            Text(
              text = currentAssessment.osLimitationNote,
              style = DrxTypographyTokens.caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    // Persisted Warning History
    item {
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(strings.warningHistoryTitle, style = MaterialTheme.typography.titleMedium)
        if (history.isNotEmpty()) {
          DrxSecondaryButton(
            label = if (isIndo) "Bersihkan" else "Clear All",
            onClick = { diagnosticsViewModel.clearStabilityWarnings() },
            modifier = Modifier.height(36.dp),
          )
        }
      }
    }

    if (history.isEmpty()) {
      item {
        DrxGlassCard {
          Text(
            text =
              if (isIndo) "Belum ada riwayat peringatan stabilitas tersimpan."
              else "No saved stability warnings in local history yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      items(history.take(25), key = { it.id }) { item ->
        val c =
          when (item.severity) {
            StabilitySeverity.CRITICAL.name -> drxColors.alertCrimson
            StabilitySeverity.WARNING.name -> drxColors.warningAmber
            else -> drxColors.optimalGreen
          }
        val dateStr =
          remember(item.timestampMs) {
            SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(item.timestampMs))
          }
        DrxGlassCard(accentColor = c) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                DrxStatusPill(text = item.severity, color = c)
                Spacer(Modifier.width(8.dp))
                Text(dateStr, style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Spacer(Modifier.height(4.dp))
              Text(item.subtitle.ifBlank { item.title }, style = MaterialTheme.typography.titleSmall)
              Text(
                item.metricEvidence,
                style = DrxTypographyTokens.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              if (item.recommendation.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(item.recommendation, style = DrxTypographyTokens.caption, color = drxColors.neonCyan)
              }
            }
            IconButton(onClick = { diagnosticsViewModel.deleteStabilityWarning(item.id) }) {
              Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete warning",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DetailMetricRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall.copy(
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum",
      ),
    )
  }
}
