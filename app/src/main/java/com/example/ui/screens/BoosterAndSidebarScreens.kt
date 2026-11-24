package com.example.ui.screens

import android.media.AudioManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.HudDisplayMode
import com.example.data.model.SystemTelemetry
import com.example.data.prefs.DrxUserPrefs
import com.example.i18n.LocalStrings
import com.example.system.safeRoundToInt
import com.example.ui.theme.DrxBoostNowButton
import com.example.ui.theme.DrxCustomModal
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

@Composable
fun BoosterScreen(
  telemetry: SystemTelemetry,
  viewModel: DrxMainViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val activity = LocalActivity.current

  val prefs by viewModel.userPrefs.collectAsStateWithLifecycle()
  val immersiveEnabled by viewModel.immersiveEnabled.collectAsStateWithLifecycle()
  val dndEnabled by viewModel.dndEnabled.collectAsStateWithLifecycle()
  val autoRotateEnabled by viewModel.autoRotateEnabled.collectAsStateWithLifecycle()
  val isBoosting by viewModel.isBoosting.collectAsStateWithLifecycle()

  var brightnessPct by remember(telemetry.displayInfo.brightnessPercent) {
    mutableIntStateOf(telemetry.displayInfo.brightnessPercent)
  }
  var autoBrightness by remember { mutableStateOf(false) }
  var mediaVol by remember {
    mutableIntStateOf(viewModel.tweaksManager.getStreamVolumePercent(AudioManager.STREAM_MUSIC))
  }
  var ringVol by remember {
    mutableIntStateOf(viewModel.tweaksManager.getStreamVolumePercent(AudioManager.STREAM_RING))
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.smartBoosterTitle,
        subtitle = strings.profileName(prefs.activeProfile),
        onBack = onBack,
      )
    }

    item {
      DrxNotificationBanner(
        title = "Android System Policy",
        body = strings.androidHonestyNote,
        accentColor = drxColors.warningAmber,
        icon = Icons.Default.Info,
      )
    }

    item {
      DrxGlassCard(accentColor = drxColors.neonCyan, highlighted = true) {
        val availMb = telemetry.memoryInfo.availBytes / (1024L * 1024L)
        val totalMb = telemetry.memoryInfo.totalBytes / (1024L * 1024L)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "RAM: $availMb / $totalMb MB Free",
            style = MaterialTheme.typography.titleMedium,
          )
          DrxStatusPill(
            text = "Heap ${telemetry.memoryInfo.appHeapUsedMb.safeRoundToInt()} MB",
            color = drxColors.neonCyan,
          )
        }
        Spacer(Modifier.height(10.dp))
        DrxCustomProgressBar(progress = telemetry.memoryInfo.usedPercent / 100f)
        Spacer(Modifier.height(14.dp))
        DrxBoostNowButton(
          label = if (isBoosting) strings.boostingLabel else strings.boostNowBtn,
          isBoosting = isBoosting,
          animationsEnabled = prefs.animationsEnabled,
          onClick = { viewModel.performSmartBoost(activity) },
          modifier = Modifier.testTag("booster_optimize_button"),
        )
      }
    }

    // Display & Immersion
    item {
      DrxGlassCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.brightnessControl, style = MaterialTheme.typography.titleSmall)
            Text("$brightnessPct%", style = DrxTypographyTokens.numericReadout, color = drxColors.neonCyan)
          }
          if (!viewModel.tweaksManager.canWriteSystemSettings()) {
            DrxSecondaryButton(
              label = strings.grantPermission,
              onClick = { viewModel.tweaksManager.openWriteSettingsPermission() },
              modifier = Modifier.height(38.dp),
            )
          }
        }
        DrxCustomSlider(
          value = brightnessPct.toFloat(),
          onValueChange = { v ->
            brightnessPct = v.safeRoundToInt(50)
            viewModel.setBrightness(brightnessPct, autoBrightness, activity)
          },
          valueRange = 5f..100f,
        )

        Spacer(Modifier.height(6.dp))
        DrxHairlineDivider()
        Spacer(Modifier.height(6.dp))
        SettingToggleRow(
          label = strings.autoBrightness,
          checked = autoBrightness,
          onCheckedChange = {
            autoBrightness = it
            viewModel.setBrightness(brightnessPct, it, activity)
          },
        )
        DrxHairlineDivider()
        SettingToggleRow(
          label = strings.rotationControl,
          checked = autoRotateEnabled,
          onCheckedChange = { viewModel.setAutoRotation(it) },
        )
        DrxHairlineDivider()
        SettingToggleRow(
          label = strings.immersiveMode,
          checked = immersiveEnabled,
          onCheckedChange = { viewModel.setImmersiveMode(activity, it) },
        )
      }
    }

    // Do Not Disturb & Volume
    item {
      DrxGlassCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(strings.dndMode, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            DrxStatusPill(
              text =
                if (viewModel.tweaksManager.isDndPolicyGranted()) strings.permissionGranted
                else strings.grantPermission,
              color =
                if (viewModel.tweaksManager.isDndPolicyGranted()) drxColors.optimalGreen
                else drxColors.warningAmber,
            )
          }
          if (viewModel.tweaksManager.isDndPolicyGranted()) {
            DrxCustomSwitch(
              checked = dndEnabled,
              onCheckedChange = { viewModel.toggleDoNotDisturb(it) },
            )
          } else {
            DrxPrimaryButton(
              label = strings.grantPermission,
              onClick = { viewModel.tweaksManager.openDndPolicySettings() },
              modifier = Modifier.height(40.dp),
            )
          }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
          Text(strings.mediaVolume, style = MaterialTheme.typography.titleSmall)
          Text("$mediaVol%", style = DrxTypographyTokens.numericReadout, color = drxColors.neonCyan)
        }
        DrxCustomSlider(
          value = mediaVol.toFloat(),
          onValueChange = { v ->
            mediaVol = v.safeRoundToInt(50)
            viewModel.tweaksManager.setStreamVolumePercent(AudioManager.STREAM_MUSIC, mediaVol)
          },
          valueRange = 0f..100f,
        )

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
          Text(strings.ringVolume, style = MaterialTheme.typography.titleSmall)
          Text("$ringVol%", style = DrxTypographyTokens.numericReadout, color = drxColors.neonCyan)
        }
        DrxCustomSlider(
          value = ringVol.toFloat(),
          onValueChange = { v ->
            ringVol = v.safeRoundToInt(50)
            viewModel.tweaksManager.setStreamVolumePercent(AudioManager.STREAM_RING, ringVol)
          },
          valueRange = 0f..100f,
        )
      }
    }

    // System Settings Shortcuts
    item {
      DrxGlassCard {
        DrxSectionHeader(title = strings.systemShortcuts)
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          DrxSecondaryButton(
            label = "Wi-Fi",
            onClick = { viewModel.tweaksManager.openSystemSettings(Settings.ACTION_WIFI_SETTINGS) },
            modifier = Modifier.weight(1f),
          )
          DrxSecondaryButton(
            label = "Display",
            onClick = { viewModel.tweaksManager.openSystemSettings(Settings.ACTION_DISPLAY_SETTINGS) },
            modifier = Modifier.weight(1f),
          )
          DrxSecondaryButton(
            label = "App Info",
            onClick = { viewModel.tweaksManager.openSystemSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) },
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

@Composable
fun FloatingSidebarScreen(
  prefs: DrxUserPrefs,
  viewModel: DrxMainViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val hasOverlay = viewModel.tweaksManager.canDrawOverlays()
  val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
  val floatingShortcuts by viewModel.floatingShortcuts.collectAsStateWithLifecycle()

  var showPinModal by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("floating_sidebar_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.sidebarControlTitle,
        subtitle = "Edge Swipe • 6-Tab Control Center",
        onBack = onBack,
      )
    }

    // Overlay Permission
    item {
      DrxGlassCard(accentColor = drxColors.electricBlue) {
        Text(strings.overlayExplanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          DrxStatusPill(
            text = if (hasOverlay) strings.permissionGranted else strings.grantPermission,
            color = if (hasOverlay) drxColors.optimalGreen else drxColors.warningAmber,
          )
          if (!hasOverlay) {
            DrxPrimaryButton(
              label = strings.grantPermission,
              onClick = { viewModel.tweaksManager.openOverlayPermissionSettings() },
              modifier = Modifier.height(40.dp).testTag("grant_overlay_button"),
            )
          }
        }
      }
    }

    // Back Gesture Guidance Banner
    item {
      DrxNotificationBanner(
        title = "Edge Swipe & Android Back Gesture",
        body = strings.edgeGestureConflictNote,
        accentColor = drxColors.neonCyan,
        icon = Icons.Default.Info,
      )
    }

    // Edge Handle & Activation
    item {
      DrxGlassCard(accentColor = drxColors.neonCyan, highlighted = prefs.floatingSidebarEnabled) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(strings.enableSidebar, style = MaterialTheme.typography.titleMedium)
            Text(
              "Swipe handle or tap to open 5-tab overlay",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          DrxCustomSwitch(
            checked = prefs.floatingSidebarEnabled,
            enabled = hasOverlay,
            onCheckedChange = { viewModel.setFloatingSidebar(it) },
            modifier = Modifier.testTag("floating_sidebar_switch"),
          )
        }

        Spacer(Modifier.height(14.dp))
        Text(strings.edgeSideLabel, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          DrxFilterPill(
            label = strings.edgeLeftLabel,
            selected = prefs.floatingEdgeSide == 0,
            onClick = {
              viewModel.setFloatingEdgeConfig(
                edgeSide = 0,
                handleThicknessDp = prefs.floatingHandleThicknessDp,
                handleAlpha = prefs.floatingHandleAlpha,
                autoHide = prefs.floatingAutoHide,
                haptics = prefs.floatingHapticsEnabled,
                swipeSensitivity = prefs.floatingSwipeSensitivity,
              )
            },
            modifier = Modifier.weight(1f),
          )
          DrxFilterPill(
            label = strings.edgeRightLabel,
            selected = prefs.floatingEdgeSide == 1,
            onClick = {
              viewModel.setFloatingEdgeConfig(
                edgeSide = 1,
                handleThicknessDp = prefs.floatingHandleThicknessDp,
                handleAlpha = prefs.floatingHandleAlpha,
                autoHide = prefs.floatingAutoHide,
                haptics = prefs.floatingHapticsEnabled,
                swipeSensitivity = prefs.floatingSwipeSensitivity,
              )
            },
            modifier = Modifier.weight(1f),
          )
        }

        Spacer(Modifier.height(12.dp))
        DrxHairlineDivider()
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
          Text(strings.handleThicknessLabel, style = MaterialTheme.typography.titleSmall)
          Text("${prefs.floatingHandleThicknessDp} dp", style = DrxTypographyTokens.numericReadout, color = drxColors.neonCyan)
        }
        DrxCustomSlider(
          value = prefs.floatingHandleThicknessDp.toFloat(),
          onValueChange = { th ->
            viewModel.setFloatingEdgeConfig(
              edgeSide = prefs.floatingEdgeSide,
              handleThicknessDp = th.safeRoundToInt(14),
              handleAlpha = prefs.floatingHandleAlpha,
              autoHide = prefs.floatingAutoHide,
              haptics = prefs.floatingHapticsEnabled,
              swipeSensitivity = prefs.floatingSwipeSensitivity,
            )
          },
          valueRange = 8f..24f,
        )

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
          Text(strings.handleOpacityLabel, style = MaterialTheme.typography.titleSmall)
          Text("${(prefs.floatingHandleAlpha * 100).safeRoundToInt(85)}%", style = DrxTypographyTokens.numericReadout, color = drxColors.neonCyan)
        }
        DrxCustomSlider(
          value = prefs.floatingHandleAlpha,
          onValueChange = { ha ->
            viewModel.setFloatingEdgeConfig(
              edgeSide = prefs.floatingEdgeSide,
              handleThicknessDp = prefs.floatingHandleThicknessDp,
              handleAlpha = ha,
              autoHide = prefs.floatingAutoHide,
              haptics = prefs.floatingHapticsEnabled,
              swipeSensitivity = prefs.floatingSwipeSensitivity,
            )
          },
          valueRange = 0.25f..1.0f,
        )

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
          Text(strings.swipeSensitivityLabel, style = MaterialTheme.typography.titleSmall)
          Text(String.format(java.util.Locale.US, "%.1fx", prefs.floatingSwipeSensitivity), style = DrxTypographyTokens.numericReadout, color = drxColors.neonCyan)
        }
        DrxCustomSlider(
          value = prefs.floatingSwipeSensitivity,
          onValueChange = { sens ->
            viewModel.setFloatingEdgeConfig(
              edgeSide = prefs.floatingEdgeSide,
              handleThicknessDp = prefs.floatingHandleThicknessDp,
              handleAlpha = prefs.floatingHandleAlpha,
              autoHide = prefs.floatingAutoHide,
              haptics = prefs.floatingHapticsEnabled,
              swipeSensitivity = sens,
            )
          },
          valueRange = 0.6f..1.5f,
        )

      }
    }

    // Overlay Behavior & Auto-Hide
    item {
      DrxGlassCard {
        SettingToggleRow(
          label = strings.autoHideHandleLabel,
          checked = prefs.floatingAutoHide,
          onCheckedChange = { ah ->
            viewModel.setFloatingEdgeConfig(
              edgeSide = prefs.floatingEdgeSide,
              handleThicknessDp = prefs.floatingHandleThicknessDp,
              handleAlpha = prefs.floatingHandleAlpha,
              autoHide = ah,
              haptics = prefs.floatingHapticsEnabled,
              swipeSensitivity = prefs.floatingSwipeSensitivity,
            )
          },
        )
        DrxHairlineDivider()
        SettingToggleRow(
          label = strings.hapticsLabel,
          checked = prefs.floatingHapticsEnabled,
          onCheckedChange = { hap ->
            viewModel.setFloatingEdgeConfig(
              edgeSide = prefs.floatingEdgeSide,
              handleThicknessDp = prefs.floatingHandleThicknessDp,
              handleAlpha = prefs.floatingHandleAlpha,
              autoHide = prefs.floatingAutoHide,
              haptics = hap,
              swipeSensitivity = prefs.floatingSwipeSensitivity,
            )
          },
        )
        DrxHairlineDivider()
        SettingToggleRow(
          label = strings.autoCloseFloatingOnExitLabel,
          description = strings.autoCloseFloatingOnExitDesc,
          checked = prefs.floatingAutoCloseOnExit,
          onCheckedChange = { viewModel.setFloatingAutoCloseOnExit(it) },
          switchModifier = Modifier.testTag("floating_auto_close_switch"),
        )
      }
    }

    // Panel Appearance & Size
    item {
      DrxGlassCard {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
          Text(strings.panelTransparency, style = MaterialTheme.typography.titleSmall)
          Text(
            "${(prefs.floatingAlpha * 100).safeRoundToInt(90)}%",
            style = DrxTypographyTokens.numericReadout,
            color = drxColors.neonCyan,
          )
        }
        DrxCustomSlider(
          value = prefs.floatingAlpha,
          onValueChange = { alpha -> viewModel.updateFloatingAppearance(alpha, prefs.floatingScale) },
          valueRange = 0.35f..1.0f,
        )

        Spacer(Modifier.height(8.dp))
        Text(strings.panelSize, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(0.85f to "Compact", 1.0f to "Normal", 1.18f to "Large").forEach { (scale, label) ->
            DrxFilterPill(
              label = label,
              selected = kotlin.math.abs(prefs.floatingScale - scale) < 0.05f,
              onClick = { viewModel.updateFloatingAppearance(prefs.floatingAlpha, scale) },
              modifier = Modifier.weight(1f),
            )
          }
        }

        Spacer(Modifier.height(12.dp))
        DrxSecondaryButton(
          label = "${strings.resetPosition} (Y=${prefs.floatingPosY})",
          onClick = { viewModel.resetFloatingPosition() },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    // Live Performance HUD Overlay Controls
    item {
      DrxGlassCard(accentColor = drxColors.electricBlue, highlighted = prefs.hudOverlayEnabled) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("DRX Live Performance HUD", style = MaterialTheme.typography.titleMedium)
            Text(
              text = "Translucent draggable capsule showing real UI FPS, RAM, thermal & network",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          DrxCustomSwitch(
            checked = prefs.hudOverlayEnabled,
            enabled = hasOverlay,
            onCheckedChange = { viewModel.setHudOverlayConfig(enabled = it) },
            modifier = Modifier.testTag("hud_overlay_switch"),
          )
        }

        Spacer(Modifier.height(12.dp))
        Text("HUD Capsule Mode", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          HudDisplayMode.entries.forEach { mode ->
            DrxFilterPill(
              label = mode.label,
              selected = prefs.hudMode == mode,
              onClick = { viewModel.setHudOverlayConfig(mode = mode) },
              modifier = Modifier.weight(1f),
            )
          }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
          Text("HUD Opacity", style = MaterialTheme.typography.titleSmall)
          Text(
            "${(prefs.hudOpacity * 100).safeRoundToInt(90)}%",
            style = DrxTypographyTokens.numericReadout,
            color = drxColors.neonCyan,
          )
        }
        DrxCustomSlider(
          value = prefs.hudOpacity,
          onValueChange = { viewModel.setHudOverlayConfig(opacity = it) },
          valueRange = 0.35f..1.0f,
        )

        DrxHairlineDivider()
        SettingToggleRow(
          label = "Auto-Hide HUD When Idle",
          description = "Fades capsule to subtle chip after inactivity",
          checked = prefs.hudAutoHide,
          onCheckedChange = { viewModel.setHudOverlayConfig(autoHide = it) },
        )
      }
    }

    // Pinned Floating Apps
    item {
      DrxGlassCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(strings.pinnedFloatingAppsTitle, style = MaterialTheme.typography.titleMedium)
          DrxPrimaryButton(
            label = strings.addFloatingAppBtn,
            icon = Icons.Default.Add,
            onClick = {
              viewModel.scanInstalledGames(autoSeed = false)
              showPinModal = true
            },
            modifier = Modifier.height(38.dp).testTag("add_floating_app_button"),
          )
        }
        Spacer(Modifier.height(8.dp))
        Text(
          text = floatingShortcuts.joinToString(" • ") { it.defaultLabel },
          style = MaterialTheme.typography.bodySmall,
          color = drxColors.silver,
        )
      }
    }
  }

  if (showPinModal) {
    DrxCustomModal(
      title = strings.pinnedFloatingAppsTitle,
      onDismiss = { showPinModal = false },
    ) {
      LazyColumn(
        modifier = Modifier.height(340.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(installedApps.take(40), key = { it.packageName }) { app ->
          val pinned = prefs.floatingPinnedApps.contains(app.packageName)
          val iconBmp = remember(app.packageName) { viewModel.loadAppIcon(app.packageName) }
          DrxGlassCard(
            highlighted = pinned,
            onClick = { viewModel.togglePinnedFloatingApp(app.packageName) },
            contentPadding = PaddingValues(12.dp),
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (iconBmp != null) {
                Image(
                  bitmap = iconBmp,
                  contentDescription = app.appName,
                  modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)),
                )
              } else {
                Icon(Icons.Default.SportsEsports, contentDescription = app.appName, tint = drxColors.electricBlue, modifier = Modifier.size(34.dp))
              }
              Spacer(Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
              if (pinned) {
                Icon(Icons.Default.Check, contentDescription = "Pinned", tint = drxColors.neonCyan)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun SettingToggleRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  description: String? = null,
  switchModifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(label, style = MaterialTheme.typography.titleSmall)
      if (!description.isNullOrBlank()) {
        Spacer(Modifier.height(2.dp))
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Spacer(Modifier.width(12.dp))
    DrxCustomSwitch(checked = checked, onCheckedChange = onCheckedChange, modifier = switchModifier)
  }
}
