package com.example.ui.screens

import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.data.model.SoundPreset
import com.example.data.model.SystemTelemetry
import com.example.i18n.LocalStrings
import com.example.system.safeRoundToInt
import com.example.system.sanitizeFinite
import com.example.ui.theme.DrxCustomModal
import com.example.ui.theme.DrxCustomProgressBar
import com.example.ui.theme.DrxCustomSlider
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxHairlineDivider
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxSectionHeader
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.theme.ThinLineSparkline
import com.example.ui.viewmodel.DrxToolsViewModel
import com.example.ui.viewmodel.TelemetryHistory
import java.util.Locale

@Composable
fun SoundCenterScreen(
  toolsViewModel: DrxToolsViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current

  val bands by toolsViewModel.eqBands.collectAsStateWithLifecycle()
  val preset by toolsViewModel.soundPreset.collectAsStateWithLifecycle()
  val bass by toolsViewModel.bassStrength.collectAsStateWithLifecycle()
  val surround by toolsViewModel.virtualSurround.collectAsStateWithLifecycle()
  val volBoost by toolsViewModel.volumeBoostPercent.collectAsStateWithLifecycle()
  val isPlayingTest by toolsViewModel.isPlayingAudioTest.collectAsStateWithLifecycle()

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item { DrxScreenHeader(title = strings.soundCenterTitle, subtitle = "5-Band EQ • Spatial Test", onBack = onBack) }

    item {
      DrxGlassCard(accentColor = drxColors.electricBlue) {
        Text(
          text =
            "DSP: EQ=${if (toolsViewModel.audioFxEngine.eqSupported) "HW" else "Fallback"} • Bass=${if (toolsViewModel.audioFxEngine.bassSupported) "Active" else "N/A"} • Surround=${if (toolsViewModel.audioFxEngine.virtualizerSupported) "Active" else "N/A"}",
          style = DrxTypographyTokens.caption,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        DrxPrimaryButton(
          label = if (isPlayingTest) strings.stopAudioTestBtn else strings.audioTestBtn,
          icon = if (isPlayingTest) Icons.Default.Stop else Icons.Default.PlayArrow,
          accentColor = if (isPlayingTest) drxColors.alertCrimson else drxColors.electricBlue,
          onClick = { toolsViewModel.toggleSpatialAudioTest() },
          modifier = Modifier.fillMaxWidth().testTag("audio_test_button"),
        )
      }
    }

    item {
      DrxGlassCard {
        DrxSectionHeader(title = "Gaming Sound Profiles")
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(SoundPreset.entries) { p ->
            DrxFilterPill(
              label = p.name.replace('_', ' '),
              selected = preset == p,
              onClick = { toolsViewModel.selectSoundPreset(p) },
            )
          }
        }
      }
    }

    item {
      DrxGlassCard {
        DrxSectionHeader(title = strings.equalizerTitle)
        Spacer(Modifier.height(10.dp))
        bands.forEach { band ->
          val freqLabel =
            if (band.centerFreqHz >= 1000) "${band.centerFreqHz / 1000} kHz"
            else "${band.centerFreqHz} Hz"
          val db = band.levelMb / 100f
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = freqLabel,
              style = MaterialTheme.typography.labelMedium,
              modifier = Modifier.width(64.dp),
            )
            val minMb = band.minMb.toFloat()
            val maxMb = band.maxMb.toFloat().coerceAtLeast(minMb + 100f)
            DrxCustomSlider(
              value = band.levelMb.toFloat().coerceIn(minMb, maxMb),
              onValueChange = { v -> toolsViewModel.setEqBand(band.index, v.safeRoundToInt().toShort()) },
              valueRange = minMb..maxMb,
              modifier = Modifier.weight(1f),
            )
            Text(
              text = String.format(Locale.US, "%+.1fdB", db),
              style = DrxTypographyTokens.numericReadout,
              color = drxColors.neonCyan,
              modifier = Modifier.width(60.dp),
            )
          }
        }
      }
    }

    item {
      DrxGlassCard {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
          Text(strings.bassBoostLabel, style = MaterialTheme.typography.titleSmall)
          Text("${bass / 10}%", style = DrxTypographyTokens.numericReadout, color = drxColors.neonCyan)
        }
        DrxCustomSlider(
          value = bass.toFloat(),
          onValueChange = { toolsViewModel.setBassStrength(it.safeRoundToInt()) },
          valueRange = 0f..1000f,
          enabled = toolsViewModel.audioFxEngine.bassSupported,
        )

        DrxHairlineDivider()
        Spacer(Modifier.height(4.dp))
        SettingToggleRow(
          label = strings.virtualSurroundLabel,
          checked = surround,
          onCheckedChange = { toolsViewModel.setVirtualSurround(it) },
        )
        DrxHairlineDivider()
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
          Text(strings.safetyVolumeBoost, style = MaterialTheme.typography.titleSmall)
          Text("$volBoost%", style = DrxTypographyTokens.numericReadout, color = drxColors.neonCyan)
        }
        DrxCustomSlider(
          value = volBoost.toFloat(),
          onValueChange = { toolsViewModel.setVolumeBoost(it.safeRoundToInt()) },
          valueRange = 0f..100f,
        )
      }
    }
  }
}

@Composable
fun DeviceMonitorScreen(
  telemetry: SystemTelemetry,
  history: TelemetryHistory,
  onBack: () -> Unit,
  isMonitoringActive: Boolean = true,
  onToggleMonitoring: (Boolean) -> Unit = {},
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  var showReportModal by remember { mutableStateOf(false) }

  val bat = telemetry.batteryInfo
  val mem = telemetry.memoryInfo
  val sto = telemetry.storageInfo
  val cpu = telemetry.cpuInfo
  val disp = telemetry.displayInfo
  val net = telemetry.networkSnapshot

  val healthText =
    when (bat.healthCode) {
      BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
      BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
      BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
      BatteryManager.BATTERY_HEALTH_DEAD -> "Degraded"
      else -> "Normal"
    }

  val thermalText =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      when (telemetry.thermalStatusCode) {
        PowerManager.THERMAL_STATUS_NONE -> "Nominal"
        PowerManager.THERMAL_STATUS_LIGHT -> "Light"
        PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
        PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
        else -> "Elevated"
      }
    } else {
      "API 29+"
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.deviceMonitorTitle,
        subtitle = "Thermal: $thermalText • ${telemetry.uiFps.safeRoundToInt(60)} FPS",
        onBack = onBack,
        trailing = {
          DrxStatusPill(
            text = if (isMonitoringActive) "LIVE" else "PAUSED",
            color = if (isMonitoringActive) drxColors.optimalGreen else drxColors.warningAmber,
          )
        },
      )
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        DrxPrimaryButton(
          label = if (isMonitoringActive) strings.stopMonitoringBtn else strings.startMonitoringBtn,
          icon = if (isMonitoringActive) Icons.Default.Stop else Icons.Default.PlayArrow,
          accentColor = if (isMonitoringActive) drxColors.warningAmber else drxColors.optimalGreen,
          onClick = { onToggleMonitoring(!isMonitoringActive) },
          modifier = Modifier.weight(1f).testTag("toggle_device_monitoring_btn"),
        )
        DrxSecondaryButton(
          label = strings.performanceReportBtn,
          icon = Icons.Default.Assessment,
          onClick = { showReportModal = true },
          modifier = Modifier.weight(1f).testTag("performance_report_btn"),
        )
      }
    }

    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MonitorChartCard(
          title = strings.ramLabel,
          value = "${mem.usedPercent.safeRoundToInt()}%",
          detail = "${mem.usedBytes / (1024L * 1024L)} / ${mem.totalBytes / (1024L * 1024L)} MB",
          samples = history.ramSamples,
          lineColor = drxColors.electricBlue,
          modifier = Modifier.weight(1f),
        )
        MonitorChartCard(
          title = strings.cpuLabel,
          value = cpu.usagePercent?.let { "${it.safeRoundToInt()}%" } ?: "${cpu.cores} Cores",
          detail = cpu.currentFreqMhz?.let { "$it MHz" } ?: strings.cpuRestrictedNote,
          samples = history.cpuSamples,
          lineColor = drxColors.neonCyan,
          modifier = Modifier.weight(1f),
        )
      }
    }

    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MonitorChartCard(
          title = strings.batteryLevelLabel,
          value = "${bat.levelPercent}%",
          detail = if (bat.isCharging) bat.chargeSource else healthText,
          samples = history.batterySamples,
          lineColor = drxColors.optimalGreen,
          modifier = Modifier.weight(1f),
        )
        MonitorChartCard(
          title = strings.batteryTempLabel,
          value = String.format(Locale.US, "%.1f°C", bat.temperatureC),
          detail = "Thermal: $thermalText",
          samples = history.tempSamples,
          minVal = 20f,
          maxVal = 55f,
          lineColor = if (bat.temperatureC >= 40f) drxColors.warningAmber else drxColors.neonCyan,
          modifier = Modifier.weight(1f),
        )
      }
    }

    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DrxGlassCard(modifier = Modifier.weight(1f)) {
          Text(strings.storageLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(Modifier.height(4.dp))
          Text("${sto.usedPercent.safeRoundToInt()}%", style = DrxTypographyTokens.statNumberCompact)
          Text(
            "${sto.freeBytes / (1024L * 1024L * 1024L)} GB Free",
            style = DrxTypographyTokens.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(14.dp))
          DrxCustomProgressBar(progress = sto.usedPercent.sanitizeFinite(0f) / 100f)
        }

        MonitorChartCard(
          title = strings.networkLabel,
          value = if (net.isConnected) net.transportType else "Offline",
          detail = net.linkSpeedMbps?.let { "$it Mbps" } ?: net.networkSubType,
          samples = history.networkQualitySamples,
          lineColor = if (net.isConnected) drxColors.electricBlue else drxColors.alertCrimson,
          modifier = Modifier.weight(1f),
        )
      }
    }

    item {
      DrxGlassCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(strings.deviceInfoTitle, style = MaterialTheme.typography.titleMedium)
          DrxStatusPill(text = healthText, color = drxColors.optimalGreen)
        }
        Spacer(Modifier.height(10.dp))
        SpecRow("Model", "${Build.MANUFACTURER} ${Build.MODEL}")
        SpecRow("Hardware", "${Build.HARDWARE} • ${cpu.abi}")
        SpecRow("Android", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        SpecRow("Battery Voltage", "${bat.voltageMv} mV")
        bat.currentMa?.let { SpecRow("Battery Current", "$it mA") }
        SpecRow("Display", "${disp.widthPx}×${disp.heightPx} @ ${disp.refreshRateHz.safeRoundToInt(60)} Hz")
        SpecRow("UI Frame", "${telemetry.uiFps.safeRoundToInt(60)} FPS (${String.format(Locale.US, "%.1f", telemetry.frameTimeMs.sanitizeFinite(16.6f))} ms)")
      }
    }
  }

  if (showReportModal) {
    val avgRam = if (history.ramSamples.isNotEmpty()) history.ramSamples.average() else mem.usedPercent.sanitizeFinite(0f).toDouble()
    val peakRam = history.ramSamples.maxOrNull() ?: mem.usedPercent.sanitizeFinite(0f)
    val avgTemp = if (history.tempSamples.isNotEmpty()) history.tempSamples.average() else bat.temperatureC.sanitizeFinite(30f).toDouble()
    val peakTemp = history.tempSamples.maxOrNull() ?: bat.temperatureC.sanitizeFinite(30f)
    DrxCustomModal(
      title = strings.performanceReportBtn,
      onDismiss = { showReportModal = false },
    ) {
      SpecRow("Samples Collected", "${history.ramSamples.size}")
      SpecRow("Avg RAM Usage", String.format(Locale.US, "%.1f%%", avgRam))
      SpecRow("Peak RAM Usage", String.format(Locale.US, "%.1f%%", peakRam))
      SpecRow("Avg Battery Temp", String.format(Locale.US, "%.1f°C", avgTemp))
      SpecRow("Peak Battery Temp", String.format(Locale.US, "%.1f°C", peakTemp))
      SpecRow("Thermal State", thermalText)
      SpecRow("Current UI FPS", "${telemetry.uiFps.safeRoundToInt(60)} FPS")
      SpecRow("Network State", if (net.isConnected) "${net.transportType} (${net.networkSubType})" else "Offline")
    }
  }
}

@Composable
private fun MonitorChartCard(
  title: String,
  value: String,
  detail: String,
  samples: List<Float>,
  lineColor: Color,
  minVal: Float = 0f,
  maxVal: Float = 100f,
  modifier: Modifier = Modifier,
) {
  DrxGlassCard(modifier = modifier) {
    Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(2.dp))
    Text(value, style = DrxTypographyTokens.statNumberCompact)
    Text(detail, style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    ThinLineSparkline(
      samples = samples,
      lineColor = lineColor,
      minVal = minVal,
      maxVal = maxVal,
    )
  }
}

@Composable
private fun SpecRow(label: String, value: String) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurface)
    }
    DrxHairlineDivider()
  }
}
