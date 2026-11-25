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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.GameEntity
import com.example.data.model.CompatibilityStatus
import com.example.data.model.LanguageMode
import com.example.data.model.PermissionStateItem
import com.example.data.model.SystemTelemetry
import com.example.i18n.LocalStrings
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSectionHeader
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxDiagnosticsViewModel

@Composable
fun GameCompatibilityScreen(
  diagnosticsViewModel: DrxDiagnosticsViewModel,
  telemetry: SystemTelemetry,
  permissionStates: List<PermissionStateItem>,
  games: List<GameEntity>,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val prefs by diagnosticsViewModel.userPrefs.collectAsStateWithLifecycle()
  val report by diagnosticsViewModel.compatibilityReport.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  LaunchedEffect(telemetry, permissionStates, games) {
    diagnosticsViewModel.refreshCompatibilityReport(telemetry, permissionStates, games)
  }

  LazyColumn(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("compatibility_check_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.compatibilityCheckTitle,
        subtitle = strings.compatibilityCheckSub,
        onBack = onBack,
      )
    }

    val current = report
    if (current != null) {
      val overallColor =
        when (current.overallStatus) {
          CompatibilityStatus.SUPPORTED -> drxColors.optimalGreen
          CompatibilityStatus.LIMITED -> drxColors.warningAmber
          CompatibilityStatus.UNSUPPORTED -> drxColors.alertCrimson
        }
      val overallLabel =
        when (current.overallStatus) {
          CompatibilityStatus.SUPPORTED -> if (isIndo) "DIDUKUNG PENUH" else "SUPPORTED"
          CompatibilityStatus.LIMITED -> if (isIndo) "TERBATAS" else "LIMITED"
          CompatibilityStatus.UNSUPPORTED -> if (isIndo) "TIDAK DIDUKUNG" else "UNSUPPORTED"
        }

      item {
        DrxGlassCard(accentColor = overallColor, highlighted = true, cornerRadius = 22.dp) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(current.androidVersionLabel, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
              Text("DRX ${current.drxVersionLabel}", style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DrxStatusPill(text = overallLabel, color = overallColor)
          }
          Spacer(Modifier.height(12.dp))
          DrxPrimaryButton(
            label = if (isIndo) "Periksa Ulang Kompatibilitas" else "Re-check Compatibility",
            icon = Icons.Default.Refresh,
            onClick = { diagnosticsViewModel.refreshCompatibilityReport(telemetry, permissionStates, games) },
            modifier = Modifier.fillMaxWidth().testTag("compatibility_recheck_button"),
          )
        }
      }

      item {
        DrxSectionHeader(title = if (isIndo) "Kompatibilitas Fitur Perangkat" else "Device & Feature Checks")
      }

      items(current.systemChecks, key = { it.id }) { check ->
        val cColor =
          when (check.status) {
            CompatibilityStatus.SUPPORTED -> drxColors.optimalGreen
            CompatibilityStatus.LIMITED -> drxColors.warningAmber
            CompatibilityStatus.UNSUPPORTED -> drxColors.alertCrimson
          }
        val statusText =
          when (check.status) {
            CompatibilityStatus.SUPPORTED -> if (isIndo) "Siap" else "Supported"
            CompatibilityStatus.LIMITED -> if (isIndo) "Terbatas" else "Limited"
            CompatibilityStatus.UNSUPPORTED -> if (isIndo) "Tidak Ada" else "Unsupported"
          }
        DrxGlassCard(accentColor = cColor, contentPadding = PaddingValues(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(check.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
              Text(check.valueText, style = MaterialTheme.typography.bodySmall, color = drxColors.neonCyan)
              Text(check.detailNote, style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            DrxStatusPill(text = statusText, color = cColor)
          }
        }
      }

      item {
        DrxSectionHeader(title = if (isIndo) "Status Peluncur Game (${current.gameChecks.size})" else "Game Launcher Readiness (${current.gameChecks.size})")
      }
      if (current.gameChecks.isEmpty()) {
        item {
          DrxGlassCard {
            Text(
              text = if (isIndo) "Belum ada game terdaftar di Game Library." else "No games added to Game Library yet.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        items(current.gameChecks, key = { it.packageName }) { g ->
          DrxGlassCard(
            accentColor = if (g.isLaunchable) drxColors.optimalGreen else drxColors.alertCrimson,
            contentPadding = PaddingValues(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                  if (g.isLaunchable) Icons.Default.CheckCircle else Icons.Default.SportsEsports,
                  contentDescription = null,
                  tint = if (g.isLaunchable) drxColors.optimalGreen else drxColors.alertCrimson,
                )
                Spacer(Modifier.width(10.dp))
                Column {
                  Text(g.appName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(
                    text = "v${g.versionName} • ${g.statusNote}",
                    style = DrxTypographyTokens.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
              DrxStatusPill(
                text = if (g.isLaunchable) (if (isIndo) "Siap" else "Ready") else (if (isIndo) "Cek" else "Check"),
                color = if (g.isLaunchable) drxColors.optimalGreen else drxColors.alertCrimson,
              )
            }
          }
        }
      }
    }
  }
}
