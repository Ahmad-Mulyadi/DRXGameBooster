package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BitrateTier
import com.example.data.model.LanguageMode
import com.example.data.model.RecordingResolution
import com.example.system.DrxMediaStorageManager
import com.example.system.ScreenRecordAudioMode
import com.example.system.ScreenRecordConsentActivity
import com.example.system.ScreenRecorderService
import com.example.system.ScreenRecorderStateHolder
import com.example.system.TimeAndSizeFormatters
import com.example.system.safeRoundToInt
import com.example.ui.theme.DrxCustomSlider
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxNotificationBanner
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxSectionHeader
import com.example.ui.theme.DrxStaggeredEntrance
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.theme.rememberDrxAnimationsActive
import com.example.ui.theme.rememberDrxConditionalPulseAlpha
import com.example.ui.viewmodel.DrxDiagnosticsViewModel
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ScreenRecorderScreen(
  diagnosticsViewModel: DrxDiagnosticsViewModel,
  onOpenGallery: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val drxColors = LocalDrxColors.current
  val prefs by diagnosticsViewModel.userPrefs.collectAsStateWithLifecycle()
  val recState by ScreenRecorderStateHolder.state.collectAsStateWithLifecycle()
  val actionBanner by diagnosticsViewModel.actionBanner.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN
  val animationsActive = rememberDrxAnimationsActive(prefs.animationsEnabled)

  val capabilities = remember { ScreenRecorderService.querySupportedRecordingCapabilities() }
  val storageCheck =
    remember(recState.isRecording, recState.recentVideos.size) {
      DrxMediaStorageManager.checkStorageCapacityForRecording(context)
    }

  var showCaptureFlash by remember { mutableStateOf(false) }
  LaunchedEffect(recState.lastCapturedScreenshot?.uriString) {
    if (recState.lastCapturedScreenshot != null) {
      showCaptureFlash = true
      delay(900L)
      showCaptureFlash = false
    }
  }

  val safFolderPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      if (uri != null) {
        diagnosticsViewModel.saveSafTreeUri(uri)
      }
    }

  LaunchedEffect(Unit) {
    diagnosticsViewModel.refreshGallery()
  }

  val isCountingDown = !recState.isRecording && recState.countdownRemainingSec > 0
  val isPulsing = isCountingDown || (recState.isRecording && !recState.isPaused)
  val pulseAlpha = rememberDrxConditionalPulseAlpha(isPulsing = isPulsing, animationsEnabled = prefs.animationsEnabled)

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("screen_recorder_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = "DRX Pro Recorder",
        subtitle =
          if (isIndo) "Perekam Layar H.264 & Screenshot Asli"
          else "Hardware H.264 Screen Recorder & Capture",
        onBack = onBack,
        trailing = {
          DrxSecondaryButton(
            label = if (isIndo) "Galeri" else "Gallery",
            icon = Icons.Default.PhotoLibrary,
            onClick = onOpenGallery,
            modifier = Modifier.height(40.dp).testTag("recorder_open_gallery_button"),
          )
        },
      )
    }

    actionBanner?.let { msg ->
      item {
        DrxNotificationBanner(
          title = "DRX Recorder",
          body = msg,
          accentColor = drxColors.optimalGreen,
          onDismiss = { diagnosticsViewModel.clearActionBanner() },
        )
      }
    }

    // Live Recording Control & Waveform Card
    item {
      DrxStaggeredEntrance(index = 0, animationsEnabled = animationsActive) {
        val statusText =
          when {
            isCountingDown ->
              if (isIndo) "MULAI DALAM ${recState.countdownRemainingSec}d"
              else "STARTING IN ${recState.countdownRemainingSec}s"
            recState.isRecording && recState.isPaused ->
              "PAUSED ${ScreenRecorderService.formatDuration(recState.elapsedSeconds)}"
            recState.isRecording ->
              "REC ${ScreenRecorderService.formatDuration(recState.elapsedSeconds)}"
            else -> "STANDBY • MP4"
          }
        val statusColor =
          when {
            isCountingDown -> drxColors.warningAmber
            recState.isRecording && recState.isPaused -> drxColors.warningAmber
            recState.isRecording -> drxColors.alertCrimson
            else -> drxColors.neonCyan
          }

        DrxGlassCard(
          accentColor = statusColor,
          highlighted = recState.isRecording || isCountingDown,
          cornerRadius = 22.dp,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (isPulsing) {
                Box(
                  modifier =
                    Modifier
                      .size(10.dp)
                      .alpha(pulseAlpha)
                      .clip(CircleShape)
                      .background(statusColor),
                )
              }
              DrxStatusPill(text = statusText, color = statusColor, modifier = Modifier.testTag("recorder_status_pill"))
            }
            Text(
              text =
                if (recState.liveFileSizeBytes > 0L) TimeAndSizeFormatters.formatBytesAuto(recState.liveFileSizeBytes)
                else "${if (isIndo) "Bebas" else "Free"}: ${TimeAndSizeFormatters.formatBytesAuto(storageCheck.second)}",
              style = DrxTypographyTokens.caption.copy(fontFeatureSettings = "tnum"),
              color = if (storageCheck.first) MaterialTheme.colorScheme.onSurfaceVariant else drxColors.alertCrimson,
            )
          }

          if (recState.isRecording) {
            Spacer(Modifier.height(10.dp))
            Text(
              text = ScreenRecorderService.formatDuration(recState.elapsedSeconds),
              style =
                MaterialTheme.typography.displaySmall.copy(
                  fontFeatureSettings = "tnum",
                  color = if (recState.isPaused) drxColors.warningAmber else drxColors.neonCyan,
                ),
            )
          }

          // Real Mic Audio Amplitude Visualizer (strictly 0f / flat when idle, paused, or non-mic)
          val micCapable =
            recState.audioMode == ScreenRecordAudioMode.MIC_AUDIO ||
              recState.audioMode == ScreenRecordAudioMode.INTERNAL_AND_MIC
          if (recState.isRecording && micCapable) {
            Spacer(Modifier.height(10.dp))
            val activeAmplitude = if (recState.isPaused) 0f else recState.micAmplitudeLevel.coerceIn(0f, 1f)
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = drxColors.neonCyan, modifier = Modifier.size(16.dp))
                Text(
                  text = if (isIndo) "Level Audio Mikrofon" else "Live Mic Waveform",
                  style = DrxTypographyTokens.caption,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Text(
                text = "${(activeAmplitude * 100f).safeRoundToInt(0)}%",
                style = DrxTypographyTokens.caption.copy(fontFeatureSettings = "tnum"),
                color = drxColors.neonCyan,
              )
            }
            Spacer(Modifier.height(6.dp))
            MicAmplitudeVisualizer(
              amplitude = activeAmplitude,
              barColor = if (recState.isPaused) drxColors.warningAmber else drxColors.neonCyan,
              modifier = Modifier.fillMaxWidth().height(30.dp).testTag("recorder_mic_waveform"),
            )
          }

          recState.statusMessage?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(msg, style = MaterialTheme.typography.bodySmall, color = drxColors.neonCyan)
          }
          recState.errorMessage?.let { err ->
            Spacer(Modifier.height(8.dp))
            Text(err, style = MaterialTheme.typography.bodySmall, color = drxColors.alertCrimson)
          }

          Spacer(Modifier.height(12.dp))
          when {
            isCountingDown -> {
              DrxPrimaryButton(
                label =
                  if (isIndo) "Batalkan Hitung Mundur (${recState.countdownRemainingSec}d)"
                  else "Cancel Countdown (${recState.countdownRemainingSec}s)",
                icon = Icons.Default.Close,
                accentColor = drxColors.alertCrimson,
                onClick = { ScreenRecorderService.stopRecording(context) },
                modifier = Modifier.fillMaxWidth().testTag("recorder_cancel_countdown_button"),
              )
            }
            recState.isRecording -> {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DrxSecondaryButton(
                  label = if (recState.isPaused) (if (isIndo) "Lanjut" else "Resume") else (if (isIndo) "Jeda" else "Pause"),
                  icon = if (recState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                  onClick = {
                    if (recState.isPaused) ScreenRecorderService.resumeRecording(context)
                    else ScreenRecorderService.pauseRecording(context)
                  },
                  modifier = Modifier.weight(1f).testTag("recorder_pause_resume_button"),
                )
                DrxPrimaryButton(
                  label = "Stop",
                  icon = Icons.Default.Stop,
                  accentColor = drxColors.alertCrimson,
                  onClick = { ScreenRecorderService.stopRecording(context) },
                  modifier = Modifier.weight(1f).testTag("recorder_stop_button"),
                )
              }
            }
            else -> {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DrxPrimaryButton(
                  label =
                    if (prefs.recorderCountdownSec > 0) {
                      if (isIndo) "Rekam (${prefs.recorderCountdownSec}d)" else "Record (${prefs.recorderCountdownSec}s)"
                    } else {
                      if (isIndo) "Mulai Rekam" else "Start Recording"
                    },
                  icon = Icons.Default.FiberManualRecord,
                  enabled = storageCheck.first,
                  onClick = {
                    context.startActivity(
                      Intent(context, ScreenRecordConsentActivity::class.java).apply {
                        putExtra(ScreenRecordConsentActivity.EXTRA_CAPTURE_MODE, ScreenRecordConsentActivity.MODE_RECORD)
                        putExtra(ScreenRecordConsentActivity.EXTRA_COUNTDOWN_SEC, prefs.recorderCountdownSec)
                      }
                    )
                  },
                  modifier = Modifier.weight(1.3f).testTag("recorder_start_button"),
                )
                DrxSecondaryButton(
                  label = "Screenshot",
                  icon = Icons.Default.CameraAlt,
                  onClick = {
                    context.startActivity(
                      Intent(context, ScreenRecordConsentActivity::class.java).apply {
                        putExtra(ScreenRecordConsentActivity.EXTRA_CAPTURE_MODE, ScreenRecordConsentActivity.MODE_SCREENSHOT)
                      }
                    )
                  },
                  modifier = Modifier.weight(1f).testTag("recorder_screenshot_button"),
                )
              }
              if (!storageCheck.first) {
                Spacer(Modifier.height(6.dp))
                Text(
                  text =
                    if (isIndo) "Ruang penyimpanan kurang dari 250 MB. Kosongkan file di Galeri sebelum merekam."
                    else "Less than 250 MB free storage available. Free up space before recording.",
                  style = DrxTypographyTokens.caption,
                  color = drxColors.alertCrimson,
                )
              }
            }
          }
        }
      }
    }

    // Recent Screenshot Capture Preview Feedback
    val latestShot = recState.lastCapturedScreenshot
    if (latestShot != null) {
      item {
        val shotBitmap by
          produceState<Bitmap?>(initialValue = null, key1 = latestShot.uriString) {
            value = withContext(Dispatchers.IO) { DrxMediaStorageManager.loadThumbnailBitmap(context, latestShot) }
          }
        AnimatedVisibility(
          visible = true,
          enter = fadeIn(),
          exit = fadeOut(),
        ) {
          DrxGlassCard(
            accentColor = if (showCaptureFlash) drxColors.optimalGreen else drxColors.electricBlue,
            highlighted = showCaptureFlash,
            contentPadding = PaddingValues(12.dp),
            onClick = onOpenGallery,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier =
                  Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(drxColors.elevatedSurface)
                    .border(1.dp, drxColors.glassBorder, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
              ) {
                val bmp = shotBitmap
                if (bmp != null) {
                  Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = latestShot.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                  )
                } else {
                  Icon(Icons.Default.CameraAlt, contentDescription = null, tint = drxColors.neonCyan)
                }
              }
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = if (isIndo) "Screenshot Terakhir" else "Latest Screenshot",
                  style = DrxTypographyTokens.caption,
                  color = drxColors.optimalGreen,
                )
                Text(
                  text = latestShot.displayName,
                  style = MaterialTheme.typography.titleSmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = TimeAndSizeFormatters.formatBytesAuto(latestShot.sizeBytes),
                  style = DrxTypographyTokens.caption,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              DrxStatusPill(text = if (isIndo) "Galeri" else "Gallery", color = drxColors.neonCyan)
            }
          }
        }
      }
    }

    // Video, Audio & Countdown Configuration
    item {
      DrxStaggeredEntrance(index = 1, animationsEnabled = animationsActive) {
        val locked = recState.isRecording || isCountingDown
        DrxGlassCard {
          Text(if (isIndo) "Hitung Mundur Mulai" else "Recording Countdown Delay", style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.height(8.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 3, 5).forEach { sec ->
              val lbl =
                when {
                  sec == 0 && isIndo -> "Tanpa Jeda"
                  sec == 0 -> "Off (0s)"
                  isIndo -> "${sec}d"
                  else -> "${sec}s"
                }
              DrxFilterPill(
                label = lbl,
                selected = prefs.recorderCountdownSec == sec,
                onClick = { if (!locked) diagnosticsViewModel.setRecorderCountdownSec(sec) },
                modifier = Modifier.weight(1f).testTag("recorder_countdown_pill_$sec"),
              )
            }
          }

          Spacer(Modifier.height(12.dp))
          Text(if (isIndo) "Resolusi Video" else "Video Resolution", style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.height(8.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RecordingResolution.entries.forEach { res ->
              DrxFilterPill(
                label = res.label,
                selected = recState.resolutionPreset == res.presetHeight,
                onClick = { if (!locked) ScreenRecorderStateHolder.updateConfig(resolutionPreset = res.presetHeight) },
                modifier = Modifier.weight(1f),
              )
            }
          }

          Spacer(Modifier.height(12.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(if (isIndo) "Target Frame Rate" else "Target Frame Rate", style = MaterialTheme.typography.titleMedium)
            Text(
              text = if (capabilities.supports60Fps) "AVC 60 FPS OK" else "Max ~${capabilities.maxSupportedFpsAt1080p} FPS",
              style = DrxTypographyTokens.caption,
              color = if (capabilities.supports60Fps) drxColors.optimalGreen else drxColors.warningAmber,
            )
          }
          Spacer(Modifier.height(8.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(24, 30, 60).forEach { fps ->
              DrxFilterPill(
                label = "$fps FPS",
                selected = recState.targetFps == fps,
                onClick = { if (!locked) ScreenRecorderStateHolder.updateConfig(targetFps = fps) },
                modifier = Modifier.weight(1f),
              )
            }
          }

          Spacer(Modifier.height(12.dp))
          Text(if (isIndo) "Bitrate Video" else "Video Bitrate", style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.height(8.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BitrateTier.entries.forEach { tier ->
              DrxFilterPill(
                label = tier.label,
                selected = recState.bitrateTier == tier,
                onClick = { if (!locked) ScreenRecorderStateHolder.updateConfig(bitrateTier = tier) },
                modifier = Modifier.weight(1f),
              )
            }
          }
          if (recState.bitrateTier == BitrateTier.CUSTOM) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
              Text("Custom Bitrate", style = MaterialTheme.typography.bodySmall)
              Text("${recState.customBitrateMbps} Mbps", style = DrxTypographyTokens.numericReadout, color = drxColors.neonCyan)
            }
            DrxCustomSlider(
              value = recState.customBitrateMbps.toFloat(),
              onValueChange = { if (!locked) ScreenRecorderStateHolder.updateConfig(customBitrateMbps = it.safeRoundToInt(8)) },
              valueRange = 2f..24f,
              enabled = !locked,
            )
          }

          Spacer(Modifier.height(12.dp))
          Text(if (isIndo) "Sumber Audio" else "Audio Capture Source", style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.height(8.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DrxFilterPill(
              label = if (isIndo) "Bisu" else "None",
              selected = recState.audioMode == ScreenRecordAudioMode.NONE,
              onClick = { if (!locked) ScreenRecorderStateHolder.updateConfig(audioMode = ScreenRecordAudioMode.NONE) },
              modifier = Modifier.weight(1f),
            )
            DrxFilterPill(
              label = "Mic",
              selected = recState.audioMode == ScreenRecordAudioMode.MIC_AUDIO,
              onClick = { if (!locked) ScreenRecorderStateHolder.updateConfig(audioMode = ScreenRecordAudioMode.MIC_AUDIO) },
              modifier = Modifier.weight(1f),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
              DrxFilterPill(
                label = "System",
                selected = recState.audioMode == ScreenRecordAudioMode.SYSTEM_AUDIO,
                onClick = { if (!locked) ScreenRecorderStateHolder.updateConfig(audioMode = ScreenRecordAudioMode.SYSTEM_AUDIO) },
                modifier = Modifier.weight(1f),
              )
              DrxFilterPill(
                label = "Int+Mic",
                selected = recState.audioMode == ScreenRecordAudioMode.INTERNAL_AND_MIC,
                onClick = { if (!locked) ScreenRecorderStateHolder.updateConfig(audioMode = ScreenRecordAudioMode.INTERNAL_AND_MIC) },
                modifier = Modifier.weight(1f),
              )
            }
          }
        }
      }
    }

    // Storage Destination + SAF Link
    item {
      DrxStaggeredEntrance(index = 2, animationsEnabled = animationsActive) {
        DrxGlassCard {
          Text(if (isIndo) "Lokasi Penyimpanan Media" else "Media Storage Location", style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.height(4.dp))
          Text(
            text = "Video: Movies/DRXGameBooster/ScreenRecorder\nScreenshot: Pictures/DRXGameBooster/Screenshot",
            style = DrxTypographyTokens.caption,
            color = drxColors.silver,
          )
          Spacer(Modifier.height(8.dp))
          DrxSecondaryButton(
            label =
              if (prefs.safRootTreeUri.isBlank()) {
                if (isIndo) "Sambungkan Folder SAF (Opsional)" else "Link Custom SAF Folder (Optional)"
              } else {
                if (isIndo) "Folder SAF Tersambung • Ganti" else "SAF Folder Linked • Change"
              },
            icon = Icons.Default.FolderOpen,
            onClick = { safFolderPicker.launch(null) },
            modifier = Modifier.fillMaxWidth().testTag("recorder_saf_picker_button"),
          )
        }
      }
    }

    if (recState.recentVideos.isNotEmpty()) {
      item {
        DrxSectionHeader(
          title = if (isIndo) "Rekaman Terbaru" else "Recent Recordings",
          actionLabel = if (isIndo) "Buka Galeri" else "Open Gallery",
          onAction = onOpenGallery,
        )
      }
      items(recState.recentVideos.take(5), key = { it.uriString }) { clip ->
        val sizeMb = String.format(Locale.US, "%.1f MB", clip.sizeBytes / (1024.0 * 1024.0))
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(drxColors.cardSurface)
              .border(1.dp, drxColors.glassBorder, RoundedCornerShape(16.dp))
              .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(clip.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
              text = "${clip.resolutionLabel} • $sizeMb" +
                if (clip.durationSec > 0) " • ${ScreenRecorderService.formatDuration(clip.durationSec)}" else "",
              style = DrxTypographyTokens.caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(onClick = {
            runCatching {
              context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                  setDataAndType(clip.uriString.toUri(), "video/mp4")
                  addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
              )
            }
          }) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = drxColors.neonCyan)
          }
          IconButton(onClick = {
            runCatching {
              context.startActivity(
                Intent.createChooser(
                  Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, clip.uriString.toUri())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                  },
                  "Share Video",
                )
              )
            }
          }) {
            Icon(Icons.Default.Share, contentDescription = "Share", tint = drxColors.electricBlue)
          }
          IconButton(onClick = { ScreenRecorderService.deleteRecording(context, clip.uriString) }) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = drxColors.alertCrimson)
          }
        }
      }
    }

    item {
      DrxNotificationBanner(
        title = if (isIndo) "Catatan Audio Internal Android" else "Internal Audio & OS Notice",
        body =
          if (isIndo) {
            "Audio internal membutuhkan Android 10+ dan hanya merekam aplikasi yang mengizinkan AudioPlaybackCapture. Jika game memblokir tangkapan audio internal, DRX otomatis fallback ke Video Only tanpa gagal."
          } else {
            "Internal audio requires Android 10+ and respects each app's AudioPlaybackCapture policy. If blocked by a game, DRX gracefully falls back to Video Only."
          },
        accentColor = drxColors.electricBlue,
        icon = Icons.Outlined.Info,
      )
    }
  }
}

@Composable
private fun MicAmplitudeVisualizer(
  amplitude: Float,
  barColor: androidx.compose.ui.graphics.Color,
  modifier: Modifier = Modifier,
) {
  val safeAmp = amplitude.coerceIn(0f, 1f)
  val barCount = 16
  Canvas(
    modifier =
      modifier
        .clip(RoundedCornerShape(10.dp))
        .background(barColor.copy(alpha = 0.08f))
        .padding(horizontal = 8.dp, vertical = 4.dp),
  ) {
    val slotW = size.width / barCount
    val barW = (slotW * 0.58f).coerceAtLeast(3f)
    val midY = size.height / 2f
    // Symmetric center-weighted envelope driven strictly by real mic RMS amplitude (0f = flat hairline)
    for (i in 0 until barCount) {
      val centerDist = kotlin.math.abs(i - (barCount - 1) / 2f) / ((barCount - 1) / 2f)
      val weight = 1f - (centerDist * 0.55f)
      val activeFraction = (safeAmp * weight).coerceIn(0f, 1f)
      val barHeight = (size.height * activeFraction).coerceAtLeast(2.dp.toPx())
      val x = i * slotW + (slotW - barW) / 2f
      drawRoundRect(
        color = if (safeAmp > 0.01f) barColor else barColor.copy(alpha = 0.35f),
        topLeft = Offset(x, midY - barHeight / 2f),
        size = Size(barW, barHeight),
        cornerRadius = CornerRadius(barW / 2f, barW / 2f),
      )
    }
  }
}
