package com.example.ui.screens

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.GameEntity
import com.example.data.model.LanguageMode
import com.example.data.model.PreGameCheckItem
import com.example.data.model.PreGameCheckReport
import com.example.data.model.PreGameCheckStatus
import com.example.i18n.LocalStrings
import com.example.ui.theme.DrxCustomModal
import com.example.ui.theme.DrxCustomSwitch
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxMainViewModel

@Composable
fun PreGameCheckScreen(
  viewModel: DrxMainViewModel,
  onOpenTool: (String) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val prefs by viewModel.userPrefs.collectAsStateWithLifecycle()
  val report by viewModel.preGameCheckReport.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  LaunchedEffect(Unit) {
    viewModel.runPreGameCheck(null)
  }

  val activeReport = report

  LazyColumn(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("pre_game_check_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.preGameCheckTitle,
        subtitle = strings.preGameCheckSub,
        onBack = onBack,
        trailing = {
          DrxSecondaryButton(
            label = if (isIndo) "Cek Ulang" else "Re-Check",
            icon = Icons.Default.Refresh,
            onClick = { viewModel.runPreGameCheck(null) },
            modifier = Modifier.height(42.dp).testTag("pre_game_recheck_button"),
          )
        },
      )
    }

    if (activeReport != null) {
      item {
        val statusColor =
          when (activeReport.overallStatus) {
            PreGameCheckStatus.READY -> drxColors.optimalGreen
            PreGameCheckStatus.WARNING -> drxColors.warningAmber
            PreGameCheckStatus.UNAVAILABLE -> drxColors.neonCyan
          }
        val statusLabel =
          when (activeReport.overallStatus) {
            PreGameCheckStatus.READY -> if (isIndo) "READY" else "READY"
            PreGameCheckStatus.WARNING -> if (isIndo) "WARNING" else "WARNING"
            PreGameCheckStatus.UNAVAILABLE -> if (isIndo) "UNAVAILABLE" else "UNAVAILABLE"
          }
        DrxGlassCard(accentColor = statusColor, highlighted = true, cornerRadius = 22.dp) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.FactCheck,
                contentDescription = statusLabel,
                tint = statusColor,
                modifier = Modifier.size(26.dp),
              )
              Spacer(Modifier.width(10.dp))
              Column {
                Text(
                  text = if (isIndo) "Ringkasan Kesiapan" else "Readiness Summary",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                  text = activeReport.summaryTip,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Spacer(Modifier.width(8.dp))
            DrxStatusPill(text = statusLabel, color = statusColor)
          }
        }
      }

      items(activeReport.items, key = { it.id }) { checkItem ->
        PreGameCheckItemRow(
          item = checkItem,
          isIndo = isIndo,
          onActionClick = {
            when (checkItem.id) {
              "overlay" -> viewModel.tweaksManager.openOverlayPermissionSettings()
              "network" -> viewModel.tweaksManager.openSystemSettings(Settings.ACTION_WIFI_SETTINGS)
              "storage" -> viewModel.tweaksManager.openSystemSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
              "recorder" -> onOpenTool(ToolRoutes.SCREEN_RECORDER)
              "monitoring" -> viewModel.setDeviceMonitoringActive(true)
              else -> onOpenTool(ToolRoutes.BOOSTER)
            }
          },
        )
      }
    }

    item {
      DrxGlassCard(cornerRadius = 20.dp) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = if (isIndo) "Cek Sebelum Membuka Game" else "Check Before Launching Games",
              style = MaterialTheme.typography.titleSmall,
            )
            Text(
              text =
                if (isIndo) {
                  "Selalu menyediakan tombol Mainkan Sekarang agar tidak pernah menghalangi launch."
                } else {
                  "Non-blocking check modal with an instant Play Anyway option."
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(Modifier.width(10.dp))
          DrxCustomSwitch(
            checked = prefs.preGameCheckBeforeLaunch,
            onCheckedChange = { viewModel.setPreGameCheckBeforeLaunch(it) },
            modifier = Modifier.testTag("pre_game_auto_toggle"),
          )
        }
      }
    }
  }
}

@Composable
private fun PreGameCheckItemRow(
  item: PreGameCheckItem,
  isIndo: Boolean,
  onActionClick: (() -> Unit)? = null,
) {
  val drxColors = LocalDrxColors.current
  val (icon, color, badgeText) =
    when (item.status) {
      PreGameCheckStatus.READY -> Triple(Icons.Default.CheckCircle, drxColors.optimalGreen, "READY")
      PreGameCheckStatus.WARNING -> Triple(Icons.Default.WarningAmber, drxColors.warningAmber, "WARNING")
      PreGameCheckStatus.UNAVAILABLE -> Triple(Icons.Default.Info, drxColors.neonCyan, "UNAVAILABLE")
    }

  DrxGlassCard(accentColor = color, cornerRadius = 18.dp, contentPadding = PaddingValues(14.dp)) {
    Row(verticalAlignment = Alignment.Top) {
      Box(
        modifier =
          Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(icon, contentDescription = item.title, tint = color, modifier = Modifier.size(18.dp))
      }
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
          )
          DrxStatusPill(text = badgeText, color = color)
        }
        Spacer(Modifier.height(2.dp))
        Text(
          text = item.valueText,
          style = DrxTypographyTokens.numericReadout,
          color = drxColors.neonCyan,
        )
        Text(
          text = item.detailNote,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!item.tipText.isNullOrBlank()) {
          Spacer(Modifier.height(4.dp))
          Text(
            text = item.tipText,
            style = DrxTypographyTokens.caption,
            color = color,
          )
        }
        if (item.status != PreGameCheckStatus.READY && onActionClick != null) {
          Spacer(Modifier.height(8.dp))
          DrxSecondaryButton(
            label = if (isIndo) "Buka Pengaturan" else "Inspect / Fix",
            onClick = onActionClick,
            modifier = Modifier.height(36.dp),
          )
        }
      }
    }
  }
}

@Composable
fun PreGameCheckModal(
  game: GameEntity,
  report: PreGameCheckReport,
  isIndo: Boolean,
  onPlayAnyway: () -> Unit,
  onDismiss: () -> Unit,
  onDisablePrompt: () -> Unit,
) {
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val statusColor =
    when (report.overallStatus) {
      PreGameCheckStatus.READY -> drxColors.optimalGreen
      PreGameCheckStatus.WARNING -> drxColors.warningAmber
      PreGameCheckStatus.UNAVAILABLE -> drxColors.neonCyan
    }

  DrxCustomModal(
    title = "${strings.preGameCheckTitle}: ${game.appName}",
    onDismiss = onDismiss,
  ) {
    Text(
      text = report.summaryTip,
      style = MaterialTheme.typography.bodySmall,
      color = statusColor,
    )
    Spacer(Modifier.height(10.dp))
    Column(
      modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      report.items.forEach { item ->
        val itemColor =
          when (item.status) {
            PreGameCheckStatus.READY -> drxColors.optimalGreen
            PreGameCheckStatus.WARNING -> drxColors.warningAmber
            PreGameCheckStatus.UNAVAILABLE -> drxColors.neonCyan
          }
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(drxColors.elevatedSurface)
              .padding(10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.labelLarge)
            Text(item.valueText, style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          DrxStatusPill(text = item.status.name, color = itemColor)
        }
      }
    }

    Spacer(Modifier.height(12.dp))
    DrxPrimaryButton(
      label = strings.playAnywayBtn,
      icon = Icons.Default.PlayArrow,
      accentColor = drxColors.electricBlue,
      onClick = onPlayAnyway,
      modifier = Modifier.fillMaxWidth().testTag("pre_game_play_anyway_button"),
    )
    Spacer(Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      DrxSecondaryButton(
        label = if (isIndo) "Jangan Tanyakan Lagi" else "Disable Prompt",
        onClick = onDisablePrompt,
        modifier = Modifier.weight(1f).height(42.dp),
      )
      DrxSecondaryButton(
        label = strings.closeLabel,
        onClick = onDismiss,
        modifier = Modifier.weight(1f).height(42.dp),
      )
    }
  }
}
