package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SystemTelemetry
import com.example.i18n.LocalStrings
import com.example.system.ScreenRecordAudioMode
import com.example.system.ScreenRecorderService
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
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val context = LocalContext.current
  val activity = LocalActivity.current

  val recState by ScreenRecorderStateHolder.state.collectAsStateWithLifecycle()
  LaunchedEffect(Unit) {
    ScreenRecorderStateHolder.setRecentVideos(ScreenRecorderService.loadExistingRecordings(context))
  }

  val screenRecordLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      val data = result.data
      if (result.resultCode == Activity.RESULT_OK && data != null) {
        val current = ScreenRecorderStateHolder.state.value
        ScreenRecorderService.startRecording(
          context = context,
          resultCode = result.resultCode,
          data = data,
          resolutionPreset = current.resolutionPreset,
          targetFps = current.targetFps,
          audioMode = current.audioMode,
        )
      } else {
        Toast.makeText(context, "Screen recording permission cancelled", Toast.LENGTH_SHORT).show()
      }
    }

  fun launchMediaProjectionPromptOnly() {
    try {
      val pm = context.getSystemService(MediaProjectionManager::class.java)
      if (pm == null) {
        Toast.makeText(context, "MediaProjection not supported on device", Toast.LENGTH_SHORT).show()
        return
      }
      screenRecordLauncher.launch(pm.createScreenCaptureIntent())
    } catch (_: Throwable) {
      Toast.makeText(context, "Unable to launch screen capture prompt", Toast.LENGTH_SHORT).show()
    }
  }

  val permissionsBeforeCaptureLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
      val audioDenied =
        ScreenRecorderStateHolder.state.value.audioMode != ScreenRecordAudioMode.NONE &&
          grants[Manifest.permission.RECORD_AUDIO] == false
      if (audioDenied) {
        ScreenRecorderStateHolder.updateConfig(audioMode = ScreenRecordAudioMode.NONE)
        Toast.makeText(context, "Audio permission denied — recording video only", Toast.LENGTH_SHORT).show()
      }
      launchMediaProjectionPromptOnly()
    }

  fun launchScreenCapturePrompt() {
    val neededPermissions = buildList {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
      ) {
        add(Manifest.permission.POST_NOTIFICATIONS)
      }
      if (recState.audioMode != ScreenRecordAudioMode.NONE &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
          PackageManager.PERMISSION_GRANTED
      ) {
        add(Manifest.permission.RECORD_AUDIO)
      }
    }
    if (neededPermissions.isNotEmpty()) {
      try {
        permissionsBeforeCaptureLauncher.launch(neededPermissions.toTypedArray())
      } catch (_: Throwable) {
        launchMediaProjectionPromptOnly()
      }
    } else {
      launchMediaProjectionPromptOnly()
    }
  }

  val immersive by viewModel.immersiveEnabled.collectAsStateWithLifecycle()
  var selectedOrientation by remember { mutableIntStateOf(0) }
  val activePointers = remember { mutableStateMapOf<Long, Offset>() }
  val trail = remember { mutableListOf<Offset>() }
  var touchEventCount by remember { mutableIntStateOf(0) }
  var lastWindowStart by remember { mutableStateOf(System.currentTimeMillis()) }
  var measuredTouchHz by remember { mutableFloatStateOf(0f) }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item { DrxScreenHeader(title = strings.displayTouchTitle, onBack = onBack) }

    item {
      DrxGlassCard(accentColor = drxColors.neonCyan) {
        val d = telemetry.displayInfo
        Text(
          "${strings.resolutionLabel}: ${d.widthPx} × ${d.heightPx} (${d.densityDpi} dpi)",
          style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          "${strings.refreshRateLabel}: ${d.refreshRateHz.safeRoundToInt(60)} Hz (Supported: ${d.supportedRefreshRates.joinToString { "${it.safeRoundToInt(60)}Hz" }})",
          style = MaterialTheme.typography.bodySmall,
          color = drxColors.neonCyan,
        )
        Text(
          "HDR: ${if (d.isHdr) "Supported" else "SDR"} • ${strings.screenTimeoutLabel}: ${d.screenTimeoutMs / 1000}s",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          DrxSecondaryButton(
            label = "Display Settings",
            onClick = { viewModel.tweaksManager.openSystemSettings(android.provider.Settings.ACTION_DISPLAY_SETTINGS) },
            modifier = Modifier.weight(1f),
          )
          DrxSecondaryButton(
            label =
              if (recState.isRecording) "Stop Rec (${ScreenRecorderService.formatDuration(recState.elapsedSeconds)})"
              else "Quick Record",
            onClick = {
              if (recState.isRecording) ScreenRecorderService.stopRecording(context)
              else launchScreenCapturePrompt()
            },
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    // Native MediaProjection Screen Recorder
    item {
      DrxGlassCard(
        accentColor = if (recState.isRecording) drxColors.alertCrimson else drxColors.electricBlue,
        highlighted = recState.isRecording,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
              imageVector = if (recState.isRecording) Icons.Default.FiberManualRecord else Icons.Default.Videocam,
              contentDescription = "Screen Recorder",
              tint = if (recState.isRecording) drxColors.alertCrimson else drxColors.neonCyan,
              modifier = Modifier.size(20.dp),
            )
            Text("DRX Screen Recorder", style = MaterialTheme.typography.titleMedium)
          }
          DrxStatusPill(
            text =
              if (recState.isRecording) "REC ${ScreenRecorderService.formatDuration(recState.elapsedSeconds)}"
              else "READY • MP4",
            color = if (recState.isRecording) drxColors.alertCrimson else drxColors.neonCyan,
          )
        }
        Spacer(Modifier.height(6.dp))
        Text(
          text = "Record gameplay in real MP4 directly to Movies/DRX Game Turbo with H.264 hardware encoding.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          DrxFilterPill(
            label = "720p HD",
            selected = recState.resolutionPreset == 720,
            onClick = { if (!recState.isRecording) ScreenRecorderStateHolder.updateConfig(resolutionPreset = 720) },
            modifier = Modifier.weight(1f),
          )
          DrxFilterPill(
            label = "1080p FHD",
            selected = recState.resolutionPreset == 1080,
            onClick = { if (!recState.isRecording) ScreenRecorderStateHolder.updateConfig(resolutionPreset = 1080) },
            modifier = Modifier.weight(1f),
          )
          DrxFilterPill(
            label = "30 FPS",
            selected = recState.targetFps == 30,
            onClick = { if (!recState.isRecording) ScreenRecorderStateHolder.updateConfig(targetFps = 30) },
            modifier = Modifier.weight(1f),
          )
          DrxFilterPill(
            label = "60 FPS",
            selected = recState.targetFps == 60,
            onClick = { if (!recState.isRecording) ScreenRecorderStateHolder.updateConfig(targetFps = 60) },
            modifier = Modifier.weight(1f),
          )
        }

        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            DrxFilterPill(
              label = "System Audio",
              selected = recState.audioMode == ScreenRecordAudioMode.SYSTEM_AUDIO,
              onClick = {
                if (!recState.isRecording) {
                  ScreenRecorderStateHolder.updateConfig(audioMode = ScreenRecordAudioMode.SYSTEM_AUDIO)
                }
              },
              modifier = Modifier.weight(1f),
            )
          }
          DrxFilterPill(
            label = "Mic Audio",
            selected = recState.audioMode == ScreenRecordAudioMode.MIC_AUDIO,
            onClick = {
              if (!recState.isRecording) {
                ScreenRecorderStateHolder.updateConfig(audioMode = ScreenRecordAudioMode.MIC_AUDIO)
              }
            },
            modifier = Modifier.weight(1f),
          )
          DrxFilterPill(
            label = "Mute",
            selected = recState.audioMode == ScreenRecordAudioMode.NONE,
            onClick = {
              if (!recState.isRecording) {
                ScreenRecorderStateHolder.updateConfig(audioMode = ScreenRecordAudioMode.NONE)
              }
            },
            modifier = Modifier.weight(1f),
          )
        }

        recState.statusMessage?.let { msg ->
          Spacer(Modifier.height(6.dp))
          Text(msg, style = MaterialTheme.typography.bodySmall, color = drxColors.neonCyan)
        }
        recState.errorMessage?.let { err ->
          Spacer(Modifier.height(6.dp))
          Text(err, style = MaterialTheme.typography.bodySmall, color = drxColors.alertCrimson)
        }

        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          DrxPrimaryButton(
            label =
              if (recState.isRecording) "Stop Rec (${ScreenRecorderService.formatDuration(recState.elapsedSeconds)})"
              else "Start Screen Recording",
            icon = if (recState.isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
            onClick = {
              if (recState.isRecording) ScreenRecorderService.stopRecording(context)
              else launchScreenCapturePrompt()
            },
            modifier = Modifier.weight(1.2f).testTag("screen_record_toggle_button"),
          )
          DrxSecondaryButton(
            label = "Pro Recorder",
            onClick = onOpenProRecorder,
            modifier = Modifier.weight(0.9f),
          )
        }

        if (recState.recentVideos.isNotEmpty()) {
          Spacer(Modifier.height(12.dp))
          Text(
            "Saved Recordings (${recState.recentVideos.size})",
            style = MaterialTheme.typography.titleSmall,
            color = drxColors.silver,
          )
          Spacer(Modifier.height(6.dp))
          recState.recentVideos.take(4).forEach { clip ->
            val sizeMb = String.format(Locale.US, "%.1f MB", clip.sizeBytes / (1024.0 * 1024.0))
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .background(drxColors.secondaryBg)
                  .border(1.dp, drxColors.glassBorder, RoundedCornerShape(14.dp))
                  .padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  clip.displayName,
                  style = MaterialTheme.typography.titleSmall,
                  maxLines = 1,
                )
                Text(
                  "${clip.resolutionLabel} • $sizeMb" +
                    if (clip.durationSec > 0) " • ${ScreenRecorderService.formatDuration(clip.durationSec)}" else "",
                  style = DrxTypographyTokens.caption,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Spacer(Modifier.width(4.dp))
              IconButton(
                onClick = {
                  runCatching {
                    val viewIntent =
                      Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(clip.uriString.toUri(), "video/mp4")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                      }
                    context.startActivity(viewIntent)
                  }.onFailure {
                    Toast.makeText(context, "No video player found", Toast.LENGTH_SHORT).show()
                  }
                }
              ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play Recording", tint = drxColors.neonCyan)
              }
              IconButton(
                onClick = {
                  runCatching {
                    val shareIntent =
                      Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, clip.uriString.toUri())
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                      }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Recording"))
                  }
                }
              ) {
                Icon(Icons.Default.Share, contentDescription = "Share Recording", tint = drxColors.electricBlue)
              }
              IconButton(onClick = { ScreenRecorderService.deleteRecording(context, clip.uriString) }) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Recording", tint = drxColors.alertCrimson)
              }
            }
          }
        }
      }
    }

    // Multi-Touch & Touch Rate Interactive Canvas
    item {
      DrxGlassCard(accentColor = drxColors.electricBlue) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(strings.touchTestTitle, style = MaterialTheme.typography.titleMedium)
          DrxStatusPill(
            text = "${strings.activePointers}: ${activePointers.size} • ${measuredTouchHz.safeRoundToInt()} Hz",
            color = drxColors.neonCyan,
          )
        }
        Spacer(Modifier.height(4.dp))
        Text(
          text = strings.touchTestHint,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))

        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(260.dp)
              .clip(RoundedCornerShape(18.dp))
              .background(drxColors.secondaryBg)
              .border(1.dp, drxColors.glassBorder, RoundedCornerShape(18.dp))
              .testTag("multi_touch_canvas")
              .pointerInput(Unit) {
                awaitPointerEventScope {
                  while (true) {
                    val event = awaitPointerEvent()
                    val now = System.currentTimeMillis()
                    touchEventCount++
                    val elapsed = now - lastWindowStart
                    if (elapsed >= 500L) {
                      measuredTouchHz = (touchEventCount * 1000f) / elapsed.toFloat()
                      touchEventCount = 0
                      lastWindowStart = now
                    }
                    activePointers.clear()
                    event.changes.forEach { change ->
                      if (change.pressed) {
                        activePointers[change.id.value] = change.position
                        trail.add(change.position)
                        if (trail.size > 60) trail.removeAt(0)
                      }
                    }
                    if (activePointers.isEmpty()) trail.clear()
                  }
                }
              }
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 40.dp.toPx()
            var x = step
            while (x < size.width) {
              drawLine(drxColors.glassBorder.copy(alpha = 0.25f), Offset(x, 0f), Offset(x, size.height))
              x += step
            }
            var y = step
            while (y < size.height) {
              drawLine(drxColors.glassBorder.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y))
              y += step
            }

            for (i in 1 until trail.size) {
              drawLine(
                color = drxColors.electricBlue.copy(alpha = 0.65f),
                start = trail[i - 1],
                end = trail[i],
                strokeWidth = 3.dp.toPx(),
              )
            }

            activePointers.values.forEach { pt ->
              drawLine(drxColors.neonCyan.copy(alpha = 0.45f), Offset(0f, pt.y), Offset(size.width, pt.y))
              drawLine(drxColors.neonCyan.copy(alpha = 0.45f), Offset(pt.x, 0f), Offset(pt.x, size.height))
              drawCircle(drxColors.neonCyan.copy(alpha = 0.22f), radius = 34.dp.toPx(), center = pt)
              drawCircle(
                drxColors.neonCyan,
                radius = 22.dp.toPx(),
                center = pt,
                style = Stroke(width = 2.5.dp.toPx()),
              )
            }
          }
        }
      }
    }
  }
}
