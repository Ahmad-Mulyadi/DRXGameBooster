package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.GameEntity
import com.example.data.model.LanguageMode
import com.example.i18n.LocalStrings
import com.example.system.TimeAndSizeFormatters
import com.example.ui.theme.DrxCustomModal
import com.example.ui.theme.DrxCustomProgressBar
import com.example.ui.theme.DrxDangerButton
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
fun SmartStorageInspectorScreen(
  diagnosticsViewModel: DrxDiagnosticsViewModel,
  games: List<GameEntity>,
  onOpenGallery: () -> Unit,
  onOpenRecorder: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val context = LocalContext.current
  val prefs by diagnosticsViewModel.userPrefs.collectAsStateWithLifecycle()
  val snapshot by diagnosticsViewModel.smartStorage.collectAsStateWithLifecycle()
  val isClearing by diagnosticsViewModel.isClearingCache.collectAsStateWithLifecycle()
  val actionBanner by diagnosticsViewModel.actionBanner.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  var confirmClearCache by remember { mutableStateOf(false) }

  LaunchedEffect(games) {
    diagnosticsViewModel.refreshSmartStorage(games)
  }

  LazyColumn(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("storage_inspector_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.storageInspectorTitle,
        subtitle = strings.storageInspectorSub,
        onBack = onBack,
      )
    }

    actionBanner?.let { msg ->
      item {
        DrxNotificationBanner(
          title = strings.storageInspectorTitle,
          body = msg,
          accentColor = drxColors.optimalGreen,
          icon = Icons.Default.CleaningServices,
          onDismiss = { diagnosticsViewModel.clearActionBanner() },
        )
      }
    }

    val snap = snapshot
    if (snap != null) {
      val usedFraction =
        if (snap.deviceTotalBytes > 0L) (snap.deviceUsedBytes.toFloat() / snap.deviceTotalBytes.toFloat()).coerceIn(0f, 1f)
        else 0f

      item {
        DrxGlassCard(accentColor = drxColors.electricBlue, cornerRadius = 22.dp) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Storage, contentDescription = null, tint = drxColors.electricBlue)
              Spacer(Modifier.width(8.dp))
              Text(
                text = if (isIndo) "Kapasitas Penyimpanan Perangkat" else "Device Storage Capacity",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
              )
            }
            DrxStatusPill(text = "${(usedFraction * 100f).toInt()}%", color = drxColors.neonCyan)
          }
          Spacer(Modifier.height(10.dp))
          DrxCustomProgressBar(progress = usedFraction, height = 8.dp)
          Spacer(Modifier.height(10.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
              text =
                if (isIndo) "Terpakai: ${TimeAndSizeFormatters.formatBytesAuto(snap.deviceUsedBytes)}"
                else "Used: ${TimeAndSizeFormatters.formatBytesAuto(snap.deviceUsedBytes)}",
              style = DrxTypographyTokens.caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text =
                if (isIndo) "Bebas: ${TimeAndSizeFormatters.formatBytesAuto(snap.deviceFreeBytes)} / ${TimeAndSizeFormatters.formatBytesAuto(snap.deviceTotalBytes)}"
                else "Free: ${TimeAndSizeFormatters.formatBytesAuto(snap.deviceFreeBytes)} / ${TimeAndSizeFormatters.formatBytesAuto(snap.deviceTotalBytes)}",
              style = DrxTypographyTokens.caption,
              color = drxColors.optimalGreen,
            )
          }
        }
      }

      // Breakdown Card
      item {
        DrxGlassCard(accentColor = drxColors.neonCyan) {
          StorageRow(
            label = if (isIndo) "Cache Internal DRX" else "DRX App Internal Cache",
            value = TimeAndSizeFormatters.formatBytesAuto(snap.drxCacheBytes),
          )
          Spacer(Modifier.height(8.dp))
          StorageRow(
            label = if (isIndo) "Screenshot DRX (${snap.screenshotsCount})" else "DRX Screenshots (${snap.screenshotsCount})",
            value = TimeAndSizeFormatters.formatBytesAuto(snap.screenshotsBytes),
          )
          Spacer(Modifier.height(8.dp))
          StorageRow(
            label = if (isIndo) "Rekaman Layar (${snap.recordingsCount})" else "Screen Recordings (${snap.recordingsCount})",
            value = TimeAndSizeFormatters.formatBytesAuto(snap.recordingsBytes),
          )
          Spacer(Modifier.height(8.dp))
          StorageRow(
            label = if (isIndo) "Total Ukuran Game Terpasang (${snap.gameAppsCount})" else "Installed Game Apps (${snap.gameAppsCount})",
            value =
              snap.gameAppsTotalBytes?.let { TimeAndSizeFormatters.formatBytesAuto(it) }
                ?: if (snap.hasUsageAccessForApps) strings.notAvailableLabel else strings.grantUsagePermissionCta,
          )
          Spacer(Modifier.height(14.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DrxPrimaryButton(
              label = if (isIndo) "Bersihkan Cache DRX" else "Clear DRX Cache",
              icon = Icons.Default.CleaningServices,
              enabled = !isClearing,
              onClick = { confirmClearCache = true },
              modifier = Modifier.weight(1f).testTag("storage_clear_cache_button"),
            )
            DrxSecondaryButton(
              label = if (isIndo) "Refresh" else "Refresh",
              icon = Icons.Default.Refresh,
              onClick = { diagnosticsViewModel.refreshSmartStorage(games) },
              modifier = Modifier.weight(0.65f),
            )
          }
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DrxSecondaryButton(
              label = if (isIndo) "Kelola Screenshot" else "Screenshots",
              icon = Icons.Default.PhotoLibrary,
              onClick = onOpenGallery,
              modifier = Modifier.weight(1f),
            )
            DrxSecondaryButton(
              label = if (isIndo) "Kelola Rekaman" else "Recordings",
              icon = Icons.Default.FiberManualRecord,
              onClick = onOpenRecorder,
              modifier = Modifier.weight(1f),
            )
          }
          Spacer(Modifier.height(8.dp))
          DrxSecondaryButton(
            label = if (isIndo) "Buka Pengaturan Penyimpanan OS" else "Open OS Storage Settings",
            onClick = {
              runCatching {
                context.startActivity(
                  Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
              }
            },
            modifier = Modifier.fillMaxWidth(),
          )
          if (!snap.hasUsageAccessForApps) {
            Spacer(Modifier.height(8.dp))
            DrxSecondaryButton(
              label = strings.grantUsagePermissionCta,
              onClick = { diagnosticsViewModel.tweaksManager.openUsageAccessSettings() },
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }

      if (snap.topGamesStorage.isNotEmpty()) {
        item {
          DrxSectionHeader(title = if (isIndo) "Ukuran Game Teratas (StorageStatsManager)" else "Top Installed Games (StorageStatsManager)")
        }
        items(snap.topGamesStorage, key = { it.packageName }) { gameEntry ->
          DrxGlassCard(contentPadding = PaddingValues(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(gameEntry.appName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
              Text(
                text = TimeAndSizeFormatters.formatBytesAuto(gameEntry.totalBytes),
                style = DrxTypographyTokens.numericReadout,
                color = drxColors.neonCyan,
              )
            }
          }
        }
      }
    }
  }

  if (confirmClearCache) {
    DrxCustomModal(
      title = if (isIndo) "Bersihkan Cache Internal DRX?" else "Clear DRX Internal Cache?",
      onDismiss = { confirmClearCache = false },
    ) {
      Text(
        text =
          if (isIndo) "Tindakan ini hanya menghapus cache sementara milik DRX GAME TURBO PRO, tanpa menyentuh data atau file game."
          else "This clears only temporary DRX internal cache files and never touches other apps or game data.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(14.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        DrxSecondaryButton(
          label = strings.closeLabel,
          onClick = { confirmClearCache = false },
          modifier = Modifier.weight(1f),
        )
        DrxDangerButton(
          label = if (isIndo) "Bersihkan" else "Clear Cache",
          icon = Icons.Default.CleaningServices,
          onClick = {
            confirmClearCache = false
            diagnosticsViewModel.clearDrxCache(games)
          },
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun StorageRow(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.titleSmall, color = LocalDrxColors.current.neonCyan)
  }
}
