package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.data.model.SystemTelemetry
import com.example.system.TimeAndSizeFormatters
import com.example.i18n.LocalStrings
import com.example.system.ScreenRecorderStateHolder
import com.example.system.safeRoundToInt
import com.example.ui.theme.DrxCustomProgressBar
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxSectionHeader
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxMainViewModel
import com.example.ui.viewmodel.DrxToolsViewModel
import java.util.Locale

@Composable
fun NetworkToolsScreen(
  telemetry: SystemTelemetry,
  toolsViewModel: DrxToolsViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current

  val isTesting by toolsViewModel.isTestingNetwork.collectAsStateWithLifecycle()
  val progress by toolsViewModel.networkTestProgress.collectAsStateWithLifecycle()
  val latestResult by toolsViewModel.latestNetworkResult.collectAsStateWithLifecycle()
  val history by toolsViewModel.networkHistory.collectAsStateWithLifecycle()
  val stability by toolsViewModel.networkStability.collectAsStateWithLifecycle()
  val isIndo = strings.navStats == "Statistik"

  LaunchedEffect(telemetry.networkSnapshot) {
    toolsViewModel.observeTelemetryNetworkSnapshot(telemetry.networkSnapshot)
  }

  var hostInput by remember { mutableStateOf("1.1.1.1") }
  var includeSpeedTest by remember { mutableStateOf(true) }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item { DrxScreenHeader(title = strings.networkToolsTitle, subtitle = "TCP Probe • Jitter • Throughput", onBack = onBack) }

    item {
      DrxGlassCard(accentColor = drxColors.optimalGreen) {
        val net = telemetry.networkSnapshot
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "${net.transportType} • ${net.networkSubType}",
              style = MaterialTheme.typography.titleMedium,
            )
            val details =
              buildList {
                net.linkSpeedMbps?.let { add("Link: $it Mbps") }
                net.frequencyMhz?.let { add("${it} MHz") }
                net.rssiDbm?.let { add("RSSI: $it dBm") }
                add(if (net.isMetered) "Metered" else "Unmetered")
              }.joinToString(" • ")
            Text(
              text = details,
              style = DrxTypographyTokens.caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          val qText =
            when (net.qualityLevel) {
              4 -> "EXCELLENT"
              3 -> "GOOD"
              2 -> "FAIR"
              1 -> "POOR"
              else -> "OFFLINE"
            }
          DrxStatusPill(
            text = qText,
            color = if (net.qualityLevel >= 3) drxColors.optimalGreen else drxColors.warningAmber,
          )
        }
        Spacer(Modifier.height(8.dp))
        Text(
          text = strings.noVpnNotice,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text =
            if (isIndo) "Catatan Diagnostik: Latensi diukur melalui probe TCP ke host diagnostik publik — bukan ping server game privat."
            else "Diagnostic host latency is measured via TCP probes — not private game server ping.",
          style = DrxTypographyTokens.caption,
          color = drxColors.neonCyan,
        )
      }
    }

    // Live Network Stability Monitor (ConnectivityManager NetworkCallback + transitions)
    item {
      DrxGlassCard(
        accentColor = drxColors.electricBlue,
        cornerRadius = 20.dp,
        modifier = Modifier.testTag("network_stability_monitor_card"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (isIndo) "Monitor Stabilitas Jaringan Live" else "Live Network Stability Monitor",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
          )
          DrxStatusPill(
            text =
              if (stability.isConnected) stability.currentTransport
              else (if (isIndo) "Terputus" else "Offline"),
            color = if (stability.isConnected) drxColors.optimalGreen else drxColors.alertCrimson,
          )
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          MetricTile(
            if (isIndo) "Gangguan" else "Interruptions",
            "${stability.interruptionCount}",
          )
          MetricTile(
            if (isIndo) "Transisi" else "Transitions",
            "${stability.transitionCount}",
          )
          MetricTile(
            if (isIndo) "Sub-Tipe" else "Link",
            stability.currentSubType,
          )
        }
        if (stability.recentQualitySamples.isNotEmpty()) {
          Spacer(Modifier.height(10.dp))
          Canvas(modifier = Modifier.fillMaxWidth().height(56.dp)) {
            val pts = stability.recentQualitySamples
            val stepX = if (pts.size > 1) size.width / (pts.size - 1) else size.width
            val path = Path()
            pts.forEachIndexed { idx, q ->
              val x = idx * stepX
              val norm = (q.toFloat() / 4f).coerceIn(0f, 1f)
              val y = size.height - (norm * (size.height - 10.dp.toPx())) - 5.dp.toPx()
              if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
              drawCircle(
                color = if (q >= 3) drxColors.optimalGreen else if (q >= 1) drxColors.warningAmber else drxColors.alertCrimson,
                radius = 3.dp.toPx(),
                center = Offset(x, y),
              )
            }
            if (pts.size > 1) {
              drawPath(path = path, color = drxColors.neonCyan, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }
          }
        }
        if (stability.events.isNotEmpty()) {
          Spacer(Modifier.height(8.dp))
          stability.events.take(4).forEach { ev ->
            Text(
              text = "• ${TimeAndSizeFormatters.formatDateTimeWithZone(ev.timestampMs, isIndo)}: ${ev.note}",
              style = DrxTypographyTokens.caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(Modifier.height(8.dp))
          DrxSecondaryButton(
            label = if (isIndo) "Reset Log Stabilitas" else "Reset Monitor Log",
            icon = Icons.Default.Refresh,
            onClick = { toolsViewModel.resetNetworkMonitorLog() },
            modifier = Modifier.fillMaxWidth().height(40.dp),
          )
        }
      }
    }

    item {
      DrxGlassCard(accentColor = drxColors.neonCyan) {
        OutlinedTextField(
          value = hostInput,
          onValueChange = { hostInput = it },
          label = { Text("Target Host (1.1.1.1, 8.8.8.8)") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = drxColors.electricBlue,
              unfocusedBorderColor = drxColors.glassBorder,
              focusedContainerColor = drxColors.secondaryBg,
              unfocusedContainerColor = drxColors.secondaryBg,
            ),
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf("1.1.1.1", "8.8.8.8", "dns.google").forEach { preset ->
            DrxFilterPill(
              label = preset,
              selected = hostInput == preset,
              onClick = { hostInput = preset },
            )
          }
        }
        Spacer(Modifier.height(8.dp))
        SettingToggleRow(
          label = strings.speedLabel,
          checked = includeSpeedTest,
          onCheckedChange = { includeSpeedTest = it },
        )
        Spacer(Modifier.height(8.dp))
        if (isTesting) {
          DrxCustomProgressBar(progress = progress, height = 6.dp)
          Spacer(Modifier.height(10.dp))
        }
        DrxPrimaryButton(
          label = if (isTesting) strings.testingProgress else strings.startTest,
          icon = Icons.Default.NetworkCheck,
          enabled = !isTesting,
          onClick = {
            toolsViewModel.runNetworkDiagnostics(
              host = hostInput,
              includeSpeedTest = includeSpeedTest,
              networkType = telemetry.networkSnapshot.transportType,
            )
          },
          modifier = Modifier.fillMaxWidth().testTag("start_network_test_button"),
        )
      }
    }

    latestResult?.let { res ->
      item {
        DrxGlassCard(accentColor = drxColors.electricBlue, highlighted = true) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(res.host, style = MaterialTheme.typography.titleMedium, color = drxColors.silver)
            DrxStatusPill(
              text = "${strings.stabilityLabel}: ${res.stabilityScore}/100",
              color = drxColors.neonCyan,
            )
          }
          Spacer(Modifier.height(12.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MetricTile(strings.pingLabel, String.format(Locale.US, "%.1f ms", res.pingMs))
            MetricTile(strings.jitterLabel, String.format(Locale.US, "%.1f ms", res.jitterMs))
            MetricTile(strings.packetLossLabel, String.format(Locale.US, "%.0f%%", res.packetLossPercent))
            MetricTile(strings.speedLabel, String.format(Locale.US, "%.1f Mbps", res.downloadMbps))
          }
        }
      }
    }

    if (history.isNotEmpty()) {
      item { DrxSectionHeader(title = strings.testHistory) }
      items(history.take(10), key = { it.id }) { item ->
        DrxGlassCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("${item.host} (${item.networkType})", style = MaterialTheme.typography.titleMedium)
            Text(
              "${item.stabilityScore}/100",
              style = DrxTypographyTokens.numericReadout,
              color = drxColors.neonCyan,
            )
          }
          Spacer(Modifier.height(4.dp))
          Text(
            String.format(
              Locale.US,
              "Ping %.1fms • Jitter %.1fms • Loss %.0f%% • %.1f Mbps",
              item.pingMs,
              item.jitterMs,
              item.packetLossPercent,
              item.downloadMbps,
            ),
            style = DrxTypographyTokens.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

@Composable
private fun MetricTile(label: String, value: String) {
  Column {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = DrxTypographyTokens.numericReadout, color = LocalDrxColors.current.neonCyan)
  }
}

@Composable
fun DisplayTouchScreen(
  telemetry: SystemTelemetry,
  viewModel: DrxMainViewModel,
  onBack: () -> Unit,
  onOpenProRecorder: () -> Unit = {},
  onOpenTouchTest: () -> Unit = {},
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val activity = LocalActivity.current

  val prefs by viewModel.userPrefs.collectAsStateWithLifecycle()
  val recState by ScreenRecorderStateHolder.state.collectAsStateWithLifecycle()
  val immersive by viewModel.immersiveEnabled.collectAsStateWithLifecycle()
  var selectedOrientation by remember { mutableIntStateOf(0) }
  val isIndo = prefs.language == LanguageMode.INDONESIAN
  val d = telemetry.displayInfo

  LazyColumn(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("display_intelligence_screen"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.displayIntelTitle,
        subtitle = strings.displayIntelSub,
        onBack = onBack,
      )
    }

    item {
      DrxGlassCard(accentColor = drxColors.neonCyan, cornerRadius = 22.dp) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.DisplaySettings, contentDescription = null, tint = drxColors.neonCyan)
            Text(
              text = if (isIndo) "Spesifikasi Layar Perangkat" else "Display Panel Specs",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
          }
          DrxStatusPill(
            text = "${d.refreshRateHz.safeRoundToInt(60)} Hz",
            color = drxColors.neonCyan,
          )
        }

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          MetricTile(strings.resolutionLabel, "${d.widthPx}×${d.heightPx}")
          Spacer(Modifier.weight(1f))
          MetricTile("DPI", "${d.densityDpi} dpi")
          Spacer(Modifier.weight(1f))
          MetricTile(if (isIndo) "Ukuran" else "Size", d.physicalSizeInches)
        }

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          MetricTile(if (isIndo) "Orientasi" else "Orientation", d.orientationLabel)
          Spacer(Modifier.weight(1f))
          MetricTile("HDR", if (d.isHdr) "Supported" else "SDR")
          Spacer(Modifier.weight(1f))
          MetricTile(strings.screenTimeoutLabel, "${d.screenTimeoutMs / 1000}s")
        }

        if (d.supportedModes.isNotEmpty()) {
          Spacer(Modifier.height(14.dp))
          Text(
            text = if (isIndo) "Mode Refresh Rate Didukung (Display.Mode)" else "Supported Refresh Rate Modes (Display.Mode)",
            style = MaterialTheme.typography.labelLarge,
            color = drxColors.neonCyan,
          )
          Spacer(Modifier.height(6.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
              DrxFilterPill(
                label = if (isIndo) "Default OS" else "OS Default",
                selected = prefs.preferredDisplayModeId == 0,
                onClick = { viewModel.setPreferredDisplayModeId(0, activity) },
              )
            }
            items(d.supportedModes, key = { it.modeId }) { mode ->
              val isSelected =
                prefs.preferredDisplayModeId == mode.modeId ||
                  (prefs.preferredDisplayModeId == 0 && d.activeDisplayModeId == mode.modeId)
              DrxFilterPill(
                label = "${mode.refreshRateHz.safeRoundToInt(60)} Hz (${mode.widthPx}×${mode.heightPx})",
                selected = isSelected,
                onClick = { viewModel.setPreferredDisplayModeId(mode.modeId, activity) },
                modifier = Modifier.testTag("display_mode_${mode.modeId}"),
              )
            }
          }
          Spacer(Modifier.height(6.dp))
          Text(
            text =
              if (isIndo) {
                "Meminta jendela DRX memakai Display.Mode pilihan pada perangkat & Android yang mendukung. Aplikasi lain tetap diatur sistem."
              } else {
                "Requests preferred Display.Mode for the DRX window where supported by Android & panel hardware."
              },
            style = DrxTypographyTokens.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    item {
      DrxGlassCard(cornerRadius = 20.dp) {
        Text(
          text = if (isIndo) "Kontrol Orientasi & Tampilan" else "Orientation & Window Controls",
          style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(0 to "Auto", 1 to "Portrait", 2 to "Landscape").forEach { (mode, label) ->
            DrxFilterPill(
              label = label,
              selected = selectedOrientation == mode,
              onClick = {
                selectedOrientation = mode
                activity?.let { viewModel.tweaksManager.setOrientation(it, mode) }
              },
              modifier = Modifier.weight(1f),
            )
          }
        }
        Spacer(Modifier.height(10.dp))
        SettingToggleRow(
          label = strings.immersiveMode,
          checked = immersive,
          onCheckedChange = { viewModel.setImmersiveMode(activity, it) },
        )
        Spacer(Modifier.height(10.dp))
        DrxSecondaryButton(
          label = if (isIndo) "Buka Setelan Layar OS" else "Open OS Display Settings",
          onClick = { viewModel.tweaksManager.openSystemSettings(android.provider.Settings.ACTION_DISPLAY_SETTINGS) },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    // Navigation Card to Touch Response Test (v1.25.5)
    item {
      DrxGlassCard(
        accentColor = drxColors.electricBlue,
        cornerRadius = 20.dp,
        onClick = onOpenTouchTest,
        modifier = Modifier.testTag("display_open_touch_test_card"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.TouchApp, contentDescription = strings.touchResponseTestTitle, tint = drxColors.electricBlue)
            Spacer(Modifier.width(12.dp))
            Column {
              Text(strings.touchResponseTestTitle, style = MaterialTheme.typography.titleMedium)
              Text(
                strings.touchResponseTestSub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = drxColors.neonCyan)
        }
      }
    }

    // Link to Unified DRX Pro Recorder (duplicate embedded recorder removed)
    item {
      DrxGlassCard(
        accentColor = if (recState.isRecording) drxColors.alertCrimson else drxColors.neonCyan,
        cornerRadius = 20.dp,
        onClick = onOpenProRecorder,
        modifier = Modifier.testTag("display_open_recorder_card"),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
              Icons.Default.FiberManualRecord,
              contentDescription = "DRX Pro Recorder",
              tint = if (recState.isRecording) drxColors.alertCrimson else drxColors.neonCyan,
            )
            Spacer(Modifier.width(12.dp))
            Column {
              Text("DRX Pro Recorder", style = MaterialTheme.typography.titleMedium)
              Text(
                text =
                  if (isIndo) "Buka studio perekam layar 480p-1080p, audio mix & galeri media"
                  else "Open dedicated 480p-1080p screen recorder, audio mixer & media gallery",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = drxColors.neonCyan)
        }
      }
    }
  }
}
