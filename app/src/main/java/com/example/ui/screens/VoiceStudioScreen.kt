package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.VoicePreset
import com.example.data.prefs.DrxUserPrefs
import com.example.i18n.LocalStrings
import com.example.system.VoiceEffectParams
import com.example.system.VoiceSfxType
import com.example.system.safeRoundToInt
import com.example.ui.theme.DrxCustomProgressBar
import com.example.ui.theme.DrxCustomSlider
import com.example.ui.theme.DrxCustomSwitch
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
import com.example.ui.viewmodel.DrxMainViewModel
import com.example.ui.viewmodel.DrxToolsViewModel
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceStudioScreen(
  prefs: DrxUserPrefs,
  mainViewModel: DrxMainViewModel,
  toolsViewModel: DrxToolsViewModel,
  onBack: (() -> Unit)? = null,
) {
  if (onBack != null) {
    BackHandler(onBack = onBack)
  }
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val context = LocalContext.current

  val params by toolsViewModel.voiceParams.collectAsStateWithLifecycle()
  val isMicActive by toolsViewModel.isMicActive.collectAsStateWithLifecycle()
  val liveMonitor by toolsViewModel.liveMonitor.collectAsStateWithLifecycle()
  val hasClip by toolsViewModel.hasRecordedClip.collectAsStateWithLifecycle()
  val isPlayingClip by toolsViewModel.isPlayingVoiceClip.collectAsStateWithLifecycle()
  val bars by toolsViewModel.voiceVisualizerBars.collectAsStateWithLifecycle()
  val inputLevel by toolsViewModel.micInputLevel.collectAsStateWithLifecycle()
  val voiceError by toolsViewModel.voiceError.collectAsStateWithLifecycle()

  var selectedModeIndex by remember { mutableIntStateOf(0) }
  var savedNotice by remember { mutableStateOf(false) }

  var micPermissionGranted by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  val micPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      micPermissionGranted = granted
      if (granted) {
        toolsViewModel.toggleMicCapture()
      }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.voiceStudioTitle,
        subtitle = "10 Presets • DSP ${if (toolsViewModel.voiceStudioEngine.isNoiseSuppressorSupported) "HW" else "SW"}",
        onBack = onBack,
        trailing = {
          DrxStatusPill(
            text = if (isMicActive) "LIVE" else "READY",
            color = if (isMicActive) drxColors.optimalGreen else drxColors.neonCyan,
          )
        },
      )
    }

    // Modes Row
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        listOf("1. Voice Preview", "2. Voice Studio", "3. Live Route").forEachIndexed { idx, label ->
          DrxFilterPill(
            label = label,
            selected = selectedModeIndex == idx,
            onClick = {
              selectedModeIndex = idx
              toolsViewModel.setLiveMonitor(idx == 0 || idx == 2)
            },
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    item {
      DrxNotificationBanner(
        title = strings.voiceLimitationTitle,
        body = strings.voiceLimitationBody,
        accentColor = drxColors.warningAmber,
        icon = Icons.Default.Info,
      )
    }

    // Studio Audio Waveform, Input Meter & Transport
    item {
      DrxGlassCard(
        accentColor = drxColors.electricBlue,
        highlighted = isMicActive,
        cornerRadius = 22.dp,
      ) {
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(drxColors.secondaryBg)
              .border(1.dp, drxColors.glassBorder, RoundedCornerShape(14.dp))
              .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
          Canvas(modifier = Modifier.fillMaxWidth().height(84.dp)) {
            val count = bars.size.coerceAtLeast(1)
            val gap = 6.dp.toPx()
            val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(4f)
            bars.forEachIndexed { idx, amp ->
              val barHeight = (amp * size.height).coerceAtLeast(6f)
              val x = idx * (barWidth + gap)
              val y = (size.height - barHeight) / 2f
              drawRoundRect(
                color = if (idx % 2 == 0) drxColors.electricBlue else drxColors.neonCyan,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
              )
            }
          }
        }

        Spacer(Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Input Level", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text("${(inputLevel * 100).safeRoundToInt()}%", style = DrxTypographyTokens.numericReadout, color = drxColors.neonCyan)
        }
        Spacer(Modifier.height(4.dp))
        DrxCustomProgressBar(progress = inputLevel, height = 6.dp)

        if (voiceError != null) {
          Spacer(Modifier.height(8.dp))
          Text(
            text = voiceError!!,
            style = MaterialTheme.typography.bodySmall,
            color = drxColors.alertCrimson,
          )
        }

        Spacer(Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(strings.liveMonitorToggle, style = MaterialTheme.typography.titleSmall)
          DrxCustomSwitch(
            checked = liveMonitor,
            onCheckedChange = { toolsViewModel.setLiveMonitor(it) },
          )
        }

        Spacer(Modifier.height(12.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          DrxPrimaryButton(
            label = if (isMicActive) strings.stopMic else strings.micTestRecord,
            icon = if (isMicActive) Icons.Default.Stop else Icons.Default.Mic,
            accentColor = if (isMicActive) drxColors.alertCrimson else drxColors.electricBlue,
            onClick = {
              if (micPermissionGranted) {
                toolsViewModel.toggleMicCapture()
              } else {
                try {
                  micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } catch (_: Throwable) {
                }
              }
            },
            modifier = Modifier.weight(1f).testTag("voice_mic_button"),
          )

          DrxSecondaryButton(
            label = strings.playProcessedClip,
            icon = if (isPlayingClip) Icons.Default.Stop else Icons.Default.PlayArrow,
            enabled = hasClip && !isMicActive,
            onClick = { toolsViewModel.togglePlayVoiceClip() },
            modifier = Modifier.weight(1f).testTag("voice_play_clip_button"),
          )
        }
      }
    }

    // All 10 Voice Presets
    item {
      DrxGlassCard {
        DrxSectionHeader(title = "Voice Presets (10)")
        Spacer(Modifier.height(10.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          VoicePreset.entries.forEach { preset ->
            DrxFilterPill(
              label = strings.voicePresetLabel(preset),
              selected = params.preset == preset,
              onClick = {
                savedNotice = false
                val savedCustom =
                  VoiceEffectParams(
                    preset = VoicePreset.CUSTOM,
                    pitchFactor = prefs.customVoicePitch,
                    eqLowGain = prefs.customVoiceBass,
                    eqHighGain = prefs.customVoiceTreble,
                    echoAmount = prefs.customVoiceEcho,
                    reverbAmount = prefs.customVoiceReverb,
                    robotMix = prefs.customVoiceRobot,
                  )
                toolsViewModel.selectVoicePreset(preset, savedCustom)
              },
              modifier = Modifier.testTag("voice_preset_${preset.name.lowercase()}"),
            )
          }
        }
      }
    }

    // Quick Soundboard FX Pads
    item {
      DrxGlassCard {
        DrxSectionHeader(title = strings.soundboardTitle)
        Spacer(Modifier.height(10.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          VoiceSfxType.entries.forEach { sfx ->
            DrxSecondaryButton(
              label = sfx.label,
              icon = Icons.Default.PlayArrow,
              onClick = { toolsViewModel.triggerSoundboardSfx(sfx) },
              modifier = Modifier.height(40.dp).testTag("sfx_${sfx.name.lowercase()}"),
            )
          }
        }
      }
    }

    // Sliders & Save / Reset
    item {
      DrxGlassCard {
        SliderLabelRow(strings.pitchLabel, String.format(Locale.US, "%.2fx", params.pitchFactor))
        DrxCustomSlider(
          value = params.pitchFactor,
          onValueChange = { v -> toolsViewModel.updateVoiceParams { it.copy(pitchFactor = v) } },
          valueRange = 0.6f..1.75f,
        )

        SliderLabelRow(strings.bassLabel, "${(params.eqLowGain * 100).safeRoundToInt()}%")
        DrxCustomSlider(
          value = params.eqLowGain,
          onValueChange = { v -> toolsViewModel.updateVoiceParams { it.copy(eqLowGain = v) } },
          valueRange = 0.3f..1.8f,
        )

        SliderLabelRow(strings.trebleLabel, "${(params.eqHighGain * 100).safeRoundToInt()}%")
        DrxCustomSlider(
          value = params.eqHighGain,
          onValueChange = { v -> toolsViewModel.updateVoiceParams { it.copy(eqHighGain = v) } },
          valueRange = 0.3f..1.8f,
        )

        SliderLabelRow("Robot Modulator", "${(params.robotMix * 100).safeRoundToInt()}%")
        DrxCustomSlider(
          value = params.robotMix,
          onValueChange = { v -> toolsViewModel.updateVoiceParams { it.copy(robotMix = v) } },
          valueRange = 0f..1f,
        )

        SliderLabelRow(strings.echoLabel, "${(params.echoAmount * 100).safeRoundToInt()}%")
        DrxCustomSlider(
          value = params.echoAmount,
          onValueChange = { v -> toolsViewModel.updateVoiceParams { it.copy(echoAmount = v) } },
          valueRange = 0f..0.8f,
        )

        SliderLabelRow(strings.reverbLabel, "${(params.reverbAmount * 100).safeRoundToInt()}%")
        DrxCustomSlider(
          value = params.reverbAmount,
          onValueChange = { v -> toolsViewModel.updateVoiceParams { it.copy(reverbAmount = v) } },
          valueRange = 0f..0.8f,
        )

        Spacer(Modifier.height(4.dp))
        DrxHairlineDivider()
        Spacer(Modifier.height(6.dp))
        SettingToggleRow(
          label = strings.noiseReductionLabel,
          checked = params.noiseReduction,
          onCheckedChange = { nr ->
            toolsViewModel.updateVoiceParams { it.copy(noiseReduction = nr) }
          },
        )

        Spacer(Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          DrxPrimaryButton(
            label = if (savedNotice) "Saved!" else strings.savePresetBtn,
            icon = Icons.Default.Save,
            onClick = {
              mainViewModel.saveCustomVoicePreset(
                pitch = params.pitchFactor,
                bass = params.eqLowGain,
                treble = params.eqHighGain,
                echo = params.echoAmount,
                reverb = params.reverbAmount,
                robot = params.robotMix,
              )
              savedNotice = true
            },
            modifier = Modifier.weight(1f).testTag("save_voice_preset_button"),
          )
          DrxSecondaryButton(
            label = strings.resetPresetBtn,
            icon = Icons.Default.Refresh,
            onClick = {
              savedNotice = false
              toolsViewModel.resetVoicePreset()
            },
            modifier = Modifier.weight(1f).testTag("reset_voice_preset_button"),
          )
        }
      }
    }
  }
}

@Composable
private fun SliderLabelRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    Text(
      text = value,
      style = DrxTypographyTokens.numericReadout,
      color = LocalDrxColors.current.neonCyan,
    )
  }
}
