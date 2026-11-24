package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.GameEntity
import com.example.data.db.GameSessionEntity
import com.example.data.model.CrashExitType
import com.example.data.model.InstalledAppItem
import com.example.data.model.LanguageMode
import com.example.system.TimeAndSizeFormatters
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
fun CrashAnalyzerScreen(
  games: List<GameEntity>,
  installedApps: List<InstalledAppItem>,
  sessions: List<GameSessionEntity>,
  diagnosticsViewModel: DrxDiagnosticsViewModel,
  onOpenHistory: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val drxColors = LocalDrxColors.current
  val prefs by diagnosticsViewModel.userPrefs.collectAsStateWithLifecycle()
  val analysis by diagnosticsViewModel.currentCrashAnalysis.collectAsStateWithLifecycle()
  val actionBanner by diagnosticsViewModel.actionBanner.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  val candidateApps =
    remember(games, installedApps) {
      val list = games.map { it.packageName to it.appName }.toMutableList()
      installedApps.take(15).forEach { app ->
        if (list.none { it.first == app.packageName }) {
          list += app.packageName to app.appName
        }
      }
      if (list.isEmpty()) {
        list += "com.derrex.gameturbo" to "DRX GAME TURBO PRO"
      }
      list
    }

  var selectedPkg by remember(candidateApps) {
    mutableStateOf(candidateApps.firstOrNull()?.first ?: "com.derrex.gameturbo")
  }
  val selectedName =
    candidateApps.firstOrNull { it.first == selectedPkg }?.second ?: selectedPkg.substringAfterLast('.')

  val hasUsageAccess = diagnosticsViewModel.tweaksManager.hasUsageStatsPermission()

  val logPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        diagnosticsViewModel.importDiagnosticLog(uri)
      }
    }

  LaunchedEffect(selectedPkg) {
    val latestSession = sessions.firstOrNull { it.gamePackageName == selectedPkg }
    diagnosticsViewModel.analyzeGameExit(selectedPkg, selectedName, latestSession)
  }

  @Composable
  fun exitTypeColor(exitType: CrashExitType): Color =
    when (exitType) {
      CrashExitType.USER_CLOSED -> drxColors.optimalGreen
      CrashExitType.SESSION_INTERRUPTED -> drxColors.neonCyan
      CrashExitType.PROCESS_NO_LONGER_DETECTED -> drxColors.warningAmber
      CrashExitType.POSSIBLE_CRASH -> drxColors.alertCrimson
      CrashExitType.UNKNOWN_EXIT_REASON -> MaterialTheme.colorScheme.onSurfaceVariant
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("crash_analyzer_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = "DRX Crash Analyzer",
        subtitle =
          if (isIndo) "Deteksi Interupsi Sesi & Analisis Log Resmi"
          else "Honest Session Exit & Log Diagnostics",
        onBack = onBack,
        trailing = {
          DrxSecondaryButton(
            label = if (isIndo) "Riwayat" else "History",
            icon = Icons.Default.History,
            onClick = onOpenHistory,
            modifier = Modifier.height(40.dp).testTag("crash_open_history_button"),
          )
        },
      )
    }

    actionBanner?.let { msg ->
      item {
        DrxNotificationBanner(
          title = "DRX Crash Analyzer",
          body = msg,
          accentColor = drxColors.optimalGreen,
          onDismiss = { diagnosticsViewModel.clearActionBanner() },
        )
      }
    }

    // Usage Access Card
    if (!hasUsageAccess) {
      item {
        DrxGlassCard(accentColor = drxColors.warningAmber, highlighted = true) {
          Text(
            text = if (isIndo) "Izin Usage Access Disarankan" else "Usage Access Recommended",
            style = MaterialTheme.typography.titleMedium,
          )
          Spacer(Modifier.height(4.dp))
          Text(
            text =
              if (isIndo) "Diperlukan untuk memeriksa riwayat transisi foreground/background via UsageStatsManager secara sah."
              else "Required to inspect app foreground/background transition events via Android UsageStatsManager.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(10.dp))
          DrxPrimaryButton(
            label = if (isIndo) "Aktifkan Usage Access" else "Grant Usage Access",
            onClick = { diagnosticsViewModel.tweaksManager.openUsageAccessSettings() },
            accentColor = drxColors.warningAmber,
            modifier = Modifier.fillMaxWidth().testTag("crash_grant_usage_button"),
          )
        }
      }
    }

    // Game Selector Chips + Actions
    item {
      DrxSectionHeader(title = if (isIndo) "Pilih Game / Aplikasi" else "Select Game / App")
      Spacer(Modifier.height(8.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(candidateApps, key = { it.first }) { (pkg, label) ->
          DrxFilterPill(
            label = label,
            selected = pkg == selectedPkg,
            onClick = { selectedPkg = pkg },
          )
        }
      }
      Spacer(Modifier.height(10.dp))
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DrxPrimaryButton(
          label = if (isIndo) "Analisis Sesi" else "Analyze Exit",
          icon = Icons.Default.BugReport,
          onClick = {
            val latestSession = sessions.firstOrNull { it.gamePackageName == selectedPkg }
            diagnosticsViewModel.analyzeGameExit(selectedPkg, selectedName, latestSession)
          },
          modifier = Modifier.weight(1f).testTag("crash_analyze_button"),
        )
        DrxSecondaryButton(
          label = if (isIndo) "Impor Log" else "Import Log",
          icon = Icons.Default.UploadFile,
          onClick = { logPickerLauncher.launch(arrayOf("text/*", "*/*")) },
          modifier = Modifier.weight(1f).testTag("crash_import_log_button"),
        )
      }
    }

    // Analysis Result Card
    val current = analysis
    if (current != null) {
      item {
        val badgeColor = exitTypeColor(current.exitType)
        DrxGlassCard(accentColor = badgeColor, highlighted = true, cornerRadius = 22.dp) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(current.gameName, style = MaterialTheme.typography.titleLarge)
              Text(
                text = "${current.gamePackageName} • v${current.gameVersion}",
                style = DrxTypographyTokens.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            DrxStatusPill(
              text = if (isIndo) current.exitType.labelId else current.exitType.labelEn,
              color = badgeColor,
              modifier = Modifier.testTag("crash_exit_type_pill"),
            )
          }

          Spacer(Modifier.height(10.dp))
          Text(
            text = current.diagnosticResult,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(Modifier.height(10.dp))
          DrxHairlineDivider()
          Spacer(Modifier.height(10.dp))

          Text(
            text = if (isIndo) "Snapshot Kondisi Perangkat" else "Device Condition Snapshot",
            style = MaterialTheme.typography.titleSmall,
            color = drxColors.neonCyan,
          )
          Spacer(Modifier.height(6.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (isIndo) "RAM Bebas" else "Free RAM", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${current.deviceRamFreeMb} / ${current.deviceRamTotalMb} MB", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (isIndo) "Storage Bebas" else "Free Storage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(TimeAndSizeFormatters.formatBytesAuto(current.availableStorageBytes), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (isIndo) "Baterai & Suhu" else "Battery & Temp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(String.format(Locale.US, "%d%% • %.1f°C", current.batteryLevelPercent, current.batteryTempC), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (isIndo) "Jaringan" else "Network", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(current.networkStatus, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
          }
          Spacer(Modifier.height(6.dp))
          Text(
            text = current.lastPerformanceSummary,
            style = DrxTypographyTokens.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (!current.importedLogSummary.isNullOrBlank()) {
        item {
          DrxGlassCard(accentColor = drxColors.neonCyan) {
            Text(
              text = if (isIndo) "Ringkasan Log Diagnostik (Tersensor)" else "Imported Log Summary (Redacted)",
              style = MaterialTheme.typography.titleMedium,
              color = drxColors.neonCyan,
            )
            Spacer(Modifier.height(6.dp))
            Text(
              text = current.importedLogSummary,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      item {
        DrxGlassCard {
          Text(
            text = if (isIndo) "Saran Pencegahan" else "Prevention Recommendations",
            style = MaterialTheme.typography.titleMedium,
          )
          Spacer(Modifier.height(8.dp))
          current.preventionTips.forEach { tip ->
            Text(
              text = "• $tip",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 2.dp),
            )
          }
          Spacer(Modifier.height(12.dp))
          DrxPrimaryButton(
            label = if (isIndo) "Simpan Laporan Crash" else "Save Crash Report",
            icon = Icons.Default.BookmarkAdd,
            onClick = { diagnosticsViewModel.saveCurrentCrashReport() },
            modifier = Modifier.fillMaxWidth().testTag("crash_save_report_button"),
          )
        }
      }
    }

    item {
      DrxNotificationBanner(
        title = if (isIndo) "Batasan Sistem Android" else "Android OS Privacy Boundary",
        body =
          if (isIndo) {
            "Aplikasi non-sistem/non-root tidak dapat membaca stack trace crash internal milik game lain. DRX mengklasifikasikan penghentian sesi secara jujur dari event sistem & telemetri, atau dari file log yang Anda pilih manual."
          } else {
            "Non-root apps cannot read private crash stack traces from other apps. DRX classifies exits strictly from UsageEvents + device telemetry, plus user-imported log files."
          },
        accentColor = drxColors.electricBlue,
        icon = Icons.Outlined.Info,
      )
    }
  }
}
