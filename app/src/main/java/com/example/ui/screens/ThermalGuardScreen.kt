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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LanguageMode
import com.example.data.model.SessionTimelineEventType
import com.example.data.model.ThermalTier
import com.example.i18n.LocalStrings
import com.example.system.ThermalGuardEngine
import com.example.system.TimeAndSizeFormatters
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSectionHeader
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxMainViewModel
import java.util.Locale

@Composable
fun ThermalGuardScreen(
  viewModel: DrxMainViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val prefs by viewModel.userPrefs.collectAsStateWithLifecycle()
  val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
  val history by viewModel.telemetryHistory.collectAsStateWithLifecycle()
  val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
  val timelineEvents by viewModel.sessionTimelineEvents.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  val sessionPeak = activeSession?.peakTempC ?: history.tempSamples.maxOrNull() ?: telemetry.batteryInfo.temperatureC
  val snapshot =
    remember(telemetry, sessionPeak, isIndo) {
      ThermalGuardEngine.evaluate(
        telemetry = telemetry,
        sessionPeakTempC = sessionPeak,
        isIndo = isIndo,
      )
    }

  val tierColor =
    when (snapshot.tier) {
      ThermalTier.NORMAL -> drxColors.optimalGreen
      ThermalTier.WARM -> drxColors.neonCyan
      ThermalTier.ELEVATED -> drxColors.warningAmber
      ThermalTier.CRITICAL -> drxColors.alertCrimson
    }

  val thermalEvents =
    remember(timelineEvents) {
      timelineEvents
        .filter {
          it.eventType == SessionTimelineEventType.TEMP_INCREASED.code ||
            it.eventType == SessionTimelineEventType.THERMAL_WARNING.code
        }
        .sortedByDescending { it.timestampMs }
        .take(15)
    }

  LazyColumn(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("thermal_guard_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.thermalGuardTitle,
        subtitle = strings.thermalGuardSub,
        onBack = onBack,
      )
    }

    item {
      DrxGlassCard(
        accentColor = tierColor,
        highlighted = snapshot.tier == ThermalTier.ELEVATED || snapshot.tier == ThermalTier.CRITICAL,
        cornerRadius = 22.dp,
        modifier = Modifier.testTag("thermal_guard_status_card"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Thermostat, contentDescription = null, tint = tierColor, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column {
              Text(
                text = String.format(Locale.US, "%.1f°C", snapshot.batteryTempC),
                style = DrxTypographyTokens.statNumberCompact,
                color = tierColor,
              )
              Text(
                text =
                  if (isIndo) "PowerManager: ${snapshot.thermalStatusLabel}"
                  else "PowerManager: ${snapshot.thermalStatusLabel}",
                style = DrxTypographyTokens.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          DrxStatusPill(
            text = if (isIndo) snapshot.tier.labelId else snapshot.tier.labelEn,
            color = tierColor,
          )
        }

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Column {
            Text(
              text = if (isIndo) "Suhu Puncak Sesi" else "Session Peak Temp",
              style = DrxTypographyTokens.caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = String.format(Locale.US, "%.1f°C", snapshot.sessionPeakTempC),
              style = MaterialTheme.typography.titleMedium,
              color = drxColors.neonCyan,
            )
          }
          Column(horizontalAlignment = Alignment.End) {
            Text(
              text = if (isIndo) "Status Pengisian" else "Charging Heat",
              style = DrxTypographyTokens.caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Default.BatteryChargingFull,
                contentDescription = null,
                tint = if (snapshot.isCharging) drxColors.warningAmber else drxColors.optimalGreen,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(4.dp))
              Text(
                text = if (snapshot.isCharging) telemetry.batteryInfo.chargeSource else (if (isIndo) "Tidak Mengisi" else "Discharging"),
                style = MaterialTheme.typography.titleSmall,
              )
            }
          }
        }
      }
    }

    // Live Thermal Sparkline
    if (history.tempSamples.isNotEmpty()) {
      item {
        DrxGlassCard(accentColor = drxColors.neonCyan) {
          Text(
            text = if (isIndo) "Tren Suhu Baterai Real-Time" else "Live Battery Thermal Trend",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
          )
          Spacer(Modifier.height(10.dp))
          Canvas(modifier = Modifier.fillMaxWidth().height(92.dp)) {
            val samples = history.tempSamples
            val minT = (samples.minOrNull() ?: 25f) - 2f
            val maxT = ((samples.maxOrNull() ?: 45f) + 2f).coerceAtLeast(minT + 4f)
            val stepX = if (samples.size > 1) size.width / (samples.size - 1) else size.width
            val path = Path()
            samples.forEachIndexed { idx, temp ->
              val x = idx * stepX
              val norm = ((temp - minT) / (maxT - minT)).coerceIn(0f, 1f)
              val y = size.height - (norm * (size.height - 12.dp.toPx())) - 6.dp.toPx()
              if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
              drawCircle(color = tierColor, radius = 3.dp.toPx(), center = Offset(x, y))
            }
            if (samples.size > 1) {
              drawPath(
                path = path,
                color = tierColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
              )
            }
          }
        }
      }
    }

    // Guidance Cards
    item {
      DrxGlassCard(accentColor = drxColors.electricBlue) {
        Text(
          text = if (isIndo) "Rekomendasi Pendinginan Aman" else "Safe Cooling Recommendations",
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(8.dp))
        snapshot.adviceList.forEach { tip ->
          Text(
            text = "• $tip",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 3.dp),
          )
        }
      }
    }

    // Thermal Event History
    item {
      DrxSectionHeader(
        title = if (isIndo) "Riwayat Kejadian Thermal (${thermalEvents.size})" else "Thermal Event Log (${thermalEvents.size})",
      )
    }
    if (thermalEvents.isEmpty()) {
      item {
        DrxGlassCard {
          Text(
            text =
              if (isIndo) "Belum ada lonjakan suhu atau peringatan thermal yang tercatat selama sesi bermain."
              else "No thermal spikes or warnings logged during recorded gaming sessions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      items(thermalEvents, key = { it.id }) { ev ->
        val isWarn = ev.eventType == SessionTimelineEventType.THERMAL_WARNING.code
        DrxGlassCard(
          accentColor = if (isWarn) drxColors.alertCrimson else drxColors.warningAmber,
          contentPadding = PaddingValues(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = if (isWarn) drxColors.alertCrimson else drxColors.warningAmber,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(8.dp))
              Column {
                Text(ev.title, style = MaterialTheme.typography.titleSmall)
                Text(
                  text = "${ev.gameName} • ${ev.detail}",
                  style = DrxTypographyTokens.caption,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Text(
              text = TimeAndSizeFormatters.formatDateTimeWithZone(ev.timestampMs, isIndo),
              style = DrxTypographyTokens.caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}
