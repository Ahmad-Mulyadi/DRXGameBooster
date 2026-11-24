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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LanguageMode
import com.example.ui.theme.DrxCircularGauge
import com.example.ui.theme.DrxCustomProgressBar
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxNotificationBanner
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxSectionHeader
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxDiagnosticsViewModel

@Composable
fun GamingHealthScreen(
  diagnosticsViewModel: DrxDiagnosticsViewModel,
  onOpenHistory: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val drxColors = LocalDrxColors.current
  val prefs by diagnosticsViewModel.userPrefs.collectAsStateWithLifecycle()
  val report by diagnosticsViewModel.currentHealthReport.collectAsStateWithLifecycle()
  val isRunning by diagnosticsViewModel.isRunningHealthCheck.collectAsStateWithLifecycle()
  val actionBanner by diagnosticsViewModel.actionBanner.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  LaunchedEffect(Unit) {
    if (report == null) {
      diagnosticsViewModel.runHealthCheck(isFullDiagnostic = false)
    }
  }

  @Composable
  fun scoreColor(score: Int?): Color =
    when {
      score == null -> MaterialTheme.colorScheme.onSurfaceVariant
      score >= 80 -> drxColors.optimalGreen
      score >= 60 -> drxColors.warningAmber
      else -> drxColors.alertCrimson
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("gaming_health_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = "DRX Gaming Health",
        subtitle =
          if (isIndo) "Pemeriksaan Kesiapan Sistem Sebelum Bermain"
          else "Pre-Game Hardware & Network Readiness",
        onBack = onBack,
        trailing = {
          DrxSecondaryButton(
            label = if (isIndo) "Riwayat" else "History",
            icon = Icons.Default.History,
            onClick = onOpenHistory,
            modifier = Modifier.height(40.dp).testTag("health_open_history_button"),
          )
        },
      )
    }

    actionBanner?.let { msg ->
      item {
        DrxNotificationBanner(
          title = "DRX Gaming Health",
          body = msg,
          accentColor = drxColors.optimalGreen,
          onDismiss = { diagnosticsViewModel.clearActionBanner() },
        )
      }
    }

    // Score Hero Card
    item {
      val overallScore = report?.overallScoreOrNull
      val overallColor = scoreColor(overallScore)
      DrxGlassCard(accentColor = overallColor, highlighted = true, cornerRadius = 22.dp) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          DrxCircularGauge(
            label = if (isIndo) "Skor" else "Score",
            valueText = overallScore?.toString() ?: "N/A",
            subText =
              if (report?.isFullDiagnostic == true) (if (isIndo) "Penuh" else "Full")
              else (if (isIndo) "Cepat" else "Quick"),
            progress = ((overallScore ?: 0) / 100f).coerceIn(0f, 1f),
            accentColor = overallColor,
          )
          Spacer(Modifier.width(16.dp))
          Column(modifier = Modifier.weight(1f)) {
            DrxStatusPill(
              text = report?.readinessTitle ?: if (isRunning) "CHECKING..." else "READY",
              color = overallColor,
              modifier = Modifier.testTag("health_readiness_pill"),
            )
            Spacer(Modifier.height(6.dp))
            Text(
              text =
                report?.readinessDetail
                  ?: if (isIndo) "Jalankan Quick Check atau Full Diagnostic." else "Run Quick Check or Full Diagnostic.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          DrxSecondaryButton(
            label = if (isIndo) "Quick Check" else "Quick Check",
            icon = Icons.Default.Speed,
            enabled = !isRunning,
            onClick = { diagnosticsViewModel.runHealthCheck(isFullDiagnostic = false) },
            modifier = Modifier.weight(1f).testTag("health_quick_check_button"),
          )
          DrxPrimaryButton(
            label =
              if (isRunning) (if (isIndo) "Memeriksa..." else "Running...")
              else (if (isIndo) "Full Diagnostic" else "Full Diagnostic"),
            icon = Icons.Default.HealthAndSafety,
            enabled = !isRunning,
            onClick = { diagnosticsViewModel.runHealthCheck(isFullDiagnostic = true) },
            modifier = Modifier.weight(1f).testTag("health_full_check_button"),
          )
        }
      }
    }

    val currentReport = report
    if (currentReport != null) {
      item {
        DrxSectionHeader(title = if (isIndo) "6 Kategori Kesehatan" else "6 Health Categories")
      }
      items(currentReport.categories, key = { it.categoryId }) { cat ->
        val cColor = scoreColor(cat.scoreOrNull)
        DrxGlassCard(accentColor = cColor, contentPadding = PaddingValues(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(cat.title, style = MaterialTheme.typography.titleMedium)
              Spacer(Modifier.height(2.dp))
              Text(
                text = cat.detailText,
                style = DrxTypographyTokens.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = cat.scoreOrNull?.let { "$it / 100" } ?: "Not Available",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = cColor,
              )
              DrxStatusPill(text = cat.statusLabel, color = cColor)
            }
          }
          Spacer(Modifier.height(8.dp))
          DrxCustomProgressBar(
            progress = (cat.scoreOrNull ?: 0) / 100f,
            startColor = cColor,
            endColor = cColor.copy(alpha = 0.7f),
          )
        }
      }

      item {
        DrxGlassCard {
          Text(
            text = if (isIndo) "Rekomendasi" else "Recommendations",
            style = MaterialTheme.typography.titleMedium,
          )
          Spacer(Modifier.height(8.dp))
          currentReport.recommendations.forEach { rec ->
            Text(
              text = "• $rec",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 2.dp),
            )
          }
          Spacer(Modifier.height(12.dp))
          DrxPrimaryButton(
            label = if (isIndo) "Simpan Hasil Kesehatan" else "Save Health Check",
            icon = Icons.Default.BookmarkAdd,
            onClick = { diagnosticsViewModel.saveCurrentHealthCheck() },
            modifier = Modifier.fillMaxWidth().testTag("health_save_button"),
          )
        }
      }
    }

    item {
      DrxNotificationBanner(
        title = if (isIndo) "Penilaian Berbasis Data Nyata" else "Honest Sensor Scoring",
        body =
          if (isIndo) {
            "Setiap kategori dihitung dari sensor perangkat aktual. Bila tes jaringan atau sensor tidak tersedia, kategori ditandai 'Not Available' secara jujur."
          } else {
            "Each category is computed from real device sensors. Unavailable probes or sensors are marked 'Not Available' without fabricated numbers."
          },
        accentColor = drxColors.electricBlue,
        icon = Icons.Outlined.Info,
      )
    }
  }
}
