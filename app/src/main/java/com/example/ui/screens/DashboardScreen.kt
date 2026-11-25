package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.db.GameEntity
import com.example.data.model.BoostReport
import com.example.data.model.DevicePerformanceState
import com.example.data.model.DrxUpdateCatalog
import com.example.data.model.DrxUpdatePipelineState
import com.example.data.model.GamingProfile
import com.example.data.model.GlobalGamingStatistics
import com.example.data.model.LanguageMode
import com.example.data.model.QuickOverlayProfile
import com.example.data.model.SystemTelemetry
import com.example.data.prefs.DrxUserPrefs
import com.example.i18n.LocalStrings
import com.example.system.ScreenRecordConsentActivity
import com.example.system.ScreenRecorderService
import com.example.system.ScreenRecorderStateHolder
import com.example.system.TimeAndSizeFormatters
import com.example.system.safeRoundToInt
import com.example.system.sanitizeFinite
import com.example.ui.theme.DrxBoostNowButton
import com.example.ui.theme.DrxCustomProgressBar
import com.example.ui.theme.DrxDangerButton
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxInfoTooltipIcon
import com.example.ui.theme.DrxLogoBadge
import com.example.ui.theme.DrxNotificationBanner
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxSectionHeader
import com.example.ui.theme.DrxStaggeredEntrance
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.theme.rememberDrxConditionalPulseAlpha
import com.example.ui.viewmodel.ActiveGameSession
import com.example.ui.viewmodel.DrxMainViewModel
import com.example.ui.viewmodel.DrxUpdateUiState
import java.util.Locale

@Composable
fun DashboardScreen(
  telemetry: SystemTelemetry,
  prefs: DrxUserPrefs,
  games: List<GameEntity>,
  isBoosting: Boolean,
  boostReport: BoostReport?,
  activeSession: ActiveGameSession?,
  viewModel: DrxMainViewModel,
  onNavigateGames: () -> Unit,
  onNavigateProfile: () -> Unit,
  onNavigateSettings: () -> Unit,
  onNavigateTool: (String) -> Unit,
  onNavigateUpdateLog: () -> Unit = {},
  onNavigateTools: () -> Unit = {},
  globalStats: GlobalGamingStatistics? = null,
  onOpenGameStats: (String) -> Unit = {},
  onOpenCustomization: () -> Unit = {},
  updateUiState: DrxUpdateUiState? = null,
  unreadGameNotifsCount: Int = 0,
  onDownloadUpdate: () -> Unit = {},
) {
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val activity = LocalActivity.current
  val context = LocalContext.current
  val recState by ScreenRecorderStateHolder.state.collectAsStateWithLifecycle()
  val perfState by viewModel.devicePerformanceState.collectAsStateWithLifecycle()
  val autoProfileBanner by viewModel.lastAutoAppliedProfileBanner.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(Color.Transparent).testTag("home_dashboard_list"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    // 1. ELITE HEADER — Authentic DRX Logo, App Name, Version Pill, Notifications & Settings
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        DrxLogoBadge(size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = strings.appName,
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onBackground,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Surface(
              shape = RoundedCornerShape(50),
              color = drxColors.electricBlue.copy(alpha = 0.16f),
              border = BorderStroke(1.dp, drxColors.electricBlue.copy(alpha = 0.36f)),
              modifier = Modifier.testTag("home_version_pill"),
            ) {
              Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = drxColors.neonCyan,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              )
            }
          }
          Spacer(Modifier.height(2.dp))
          Text(
            text = strings.greetingSubtitle,
            style = DrxTypographyTokens.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Spacer(Modifier.width(8.dp))
        HeaderActionButton(
          icon = Icons.Default.Notifications,
          contentDescription = strings.gameNotificationsTitle,
          badgeDot = unreadGameNotifsCount > 0,
          onClick = { onNavigateTool(ToolRoutes.GAME_NOTIFICATIONS) },
          modifier = Modifier.testTag("home_notifs_button"),
        )
        Spacer(Modifier.width(8.dp))
        HeaderActionButton(
          icon = Icons.Default.Settings,
          contentDescription = strings.settingsTitle,
          onClick = onNavigateSettings,
          modifier = Modifier.testTag("home_settings_button"),
        )
      }
    }

    // Active Session Pill Banner
    if (activeSession != null) {
      item {
        DrxGlassCard(
          accentColor = drxColors.optimalGreen,
          highlighted = true,
          cornerRadius = 22.dp,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              DrxStatusPill(text = strings.activeSessionBanner, color = drxColors.optimalGreen)
              Spacer(Modifier.height(6.dp))
              Text(
                text = activeSession.gameName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              val elapsedMin =
                ((System.currentTimeMillis() - activeSession.startedAtMs) / 60_000L)
                  .coerceAtLeast(0L)
              Text(
                text = "${elapsedMin} min • ${strings.profileName(activeSession.profileUsed)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Spacer(Modifier.width(12.dp))
            DrxPrimaryButton(
              label = strings.stopSaveSession,
              icon = Icons.Default.Stop,
              accentColor = drxColors.optimalGreen,
              onClick = { viewModel.stopAndSaveActiveSession() },
              modifier = Modifier.testTag("stop_session_button"),
            )
          }
        }
      }
    }

    // 2. PERFORMANCE HERO CARD — Elite Blue Focal Glow & iOS-Inspired Layered Depth
    item {
      val heroPulse =
        rememberDrxConditionalPulseAlpha(
          isPulsing = prefs.gamingModeActive,
          animationsEnabled = prefs.animationsEnabled,
        )
      DrxStaggeredEntrance(index = 0, animationsEnabled = prefs.animationsEnabled) {
        val heroShape = RoundedCornerShape(26.dp)
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(heroShape)
              .background(
                Brush.verticalGradient(
                  listOf(drxColors.elevatedSurface, drxColors.cardSurface)
                )
              )
              .drawBehind {
                drawCircle(
                  brush =
                    Brush.radialGradient(
                      colors = listOf(drxColors.electricBlue.copy(alpha = 0.16f), Color.Transparent),
                      center = Offset(size.width * 0.88f, size.height * 0.12f),
                      radius = size.width * 0.58f,
                    ),
                  radius = size.width * 0.58f,
                  center = Offset(size.width * 0.88f, size.height * 0.12f),
                )
              }
              .border(1.dp, drxColors.glassBorder, heroShape)
              .padding(20.dp)
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = strings.heroEyebrow,
                style = MaterialTheme.typography.labelMedium,
                color = drxColors.neonCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
              )
              Spacer(Modifier.width(8.dp))
              Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                val (perfText, perfColor) =
                  when (perfState) {
                    DevicePerformanceState.READY -> strings.perfStateReady to drxColors.optimalGreen
                    DevicePerformanceState.MONITORING -> strings.perfStateMonitoring to drxColors.neonCyan
                    DevicePerformanceState.THERMAL_WARNING -> strings.perfStateThermalWarning to drxColors.alertCrimson
                    DevicePerformanceState.LIMITED_MONITORING -> strings.perfStateLimitedMonitoring to drxColors.warningAmber
                    DevicePerformanceState.DEVICE_IDLE -> strings.perfStateDeviceIdle to MaterialTheme.colorScheme.onSurfaceVariant
                  }
                DrxStatusPill(
                  text = perfText,
                  color = perfColor,
                  modifier = Modifier.testTag("home_perf_status_pill"),
                )
                DrxStatusPill(
                  text =
                    if (prefs.gamingModeActive) strings.gamingModeActive else strings.gamingModeStandby,
                  color =
                    if (prefs.gamingModeActive) drxColors.optimalGreen
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.alpha(heroPulse).testTag("gaming_mode_pill"),
                )
              }
            }

            Spacer(Modifier.height(10.dp))
            Text(
              text = strings.heroHeadline,
              style = MaterialTheme.typography.displayLarge,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text = strings.heroDescription,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            // iOS-Style Segmented Profile Capsule
            val coreProfiles =
              listOf(
                GamingProfile.PERFORMANCE,
                GamingProfile.BALANCED,
                GamingProfile.BATTERY_SAVER,
              )
            Surface(
              shape = RoundedCornerShape(50),
              color = drxColors.secondaryBg,
              border = BorderStroke(1.dp, drxColors.glassBorder),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                coreProfiles.forEach { profile ->
                  val selected = prefs.activeProfile == profile
                  Surface(
                    modifier =
                      Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable { viewModel.selectProfile(profile, activity, applyNow = false) }
                        .testTag("profile_pill_${profile.id}"),
                    shape = RoundedCornerShape(50),
                    color =
                      if (selected) drxColors.electricBlue.copy(alpha = 0.22f)
                      else Color.Transparent,
                    border =
                      if (selected) BorderStroke(1.dp, drxColors.electricBlue.copy(alpha = 0.48f))
                      else null,
                  ) {
                    Box(
                      contentAlignment = Alignment.Center,
                      modifier = Modifier.padding(horizontal = 6.dp),
                    ) {
                      Text(
                        text = strings.profileName(profile),
                        style =
                          MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                          ),
                        color =
                          if (selected) drxColors.neonCyan
                          else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                }
              }
            }

            Spacer(Modifier.height(16.dp))

            DrxBoostNowButton(
              label = if (isBoosting) strings.boostingLabel else strings.boostNowBtn,
              isBoosting = isBoosting,
              animationsEnabled = prefs.animationsEnabled,
              onClick = { viewModel.performSmartBoost(activity) },
              modifier = Modifier.testTag("game_boost_button"),
            )

            Spacer(Modifier.height(10.dp))
            Text(
              text = strings.boostHonestSummary,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    // Boost Feedback Banner
    item {
      AnimatedVisibility(
        visible = boostReport != null,
        enter = fadeIn(),
        exit = fadeOut(),
      ) {
        boostReport?.let { report ->
          DrxNotificationBanner(
            title = strings.boostCompletedTitle,
            body =
              "RAM Free: ${report.ramAfterAvailMb}/${report.totalRamMb} MB • ${String.format(Locale.US, "%.1f°C", report.batteryTempC)}\n" +
                report.actionsApplied.joinToString(" • "),
            accentColor = drxColors.neonCyan,
            icon = Icons.Outlined.Bolt,
            onDismiss = { viewModel.dismissBoostReport() },
          )
        }
      }
      if (!autoProfileBanner.isNullOrBlank()) {
        Spacer(Modifier.height(8.dp))
        DrxNotificationBanner(
          title = strings.profileAutomationTitle,
          body = autoProfileBanner.orEmpty(),
          accentColor = drxColors.optimalGreen,
          icon = Icons.Outlined.Bolt,
          onDismiss = { viewModel.dismissAutoAppliedBanner() },
          modifier = Modifier.testTag("home_auto_profile_banner"),
        )
      }
    }

    // Automatic App Update Available Banner (only shown when validated higher versionCode exists)
    val availableTarget = updateUiState?.selectedManifest
    if (updateUiState?.isUpdateAvailable == true &&
      availableTarget != null &&
      availableTarget.versionCode > BuildConfig.VERSION_CODE
    ) {
      item {
        DrxGlassCard(
          accentColor = drxColors.optimalGreen,
          highlighted = true,
          cornerRadius = 22.dp,
          modifier = Modifier.testTag("home_update_available_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = "DRX Update Available",
                tint = drxColors.optimalGreen,
                modifier = Modifier.size(22.dp),
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text = "DRX UPDATE AVAILABLE",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
              )
            }
            DrxStatusPill(text = "NEW VERSION", color = drxColors.optimalGreen)
          }

          Spacer(Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Current: v${BuildConfig.VERSION_NAME} • Latest: v${availableTarget.versionName}",
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
              color = drxColors.neonCyan,
              modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            DrxStatusPill(
              text = "NEW UPDATE",
              color = drxColors.electricBlue,
              modifier = Modifier.testTag("home_quick_updates_tile"),
            )
          }
          if (availableTarget.fileSizeBytes > 0L) {
            Spacer(Modifier.height(2.dp))
            Text(
              text = "APK Size: ${TimeAndSizeFormatters.formatBytesAuto(availableTarget.fileSizeBytes)}",
              style = DrxTypographyTokens.caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (availableTarget.releaseNotes.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
              text = "What's New: ${availableTarget.releaseNotes}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Spacer(Modifier.height(12.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrxSecondaryButton(
              label = if (isIndo) "Lihat Update" else "View Update",
              icon = Icons.Default.Update,
              onClick = { onNavigateTool(ToolRoutes.UPDATE_CENTER) },
              modifier = Modifier.weight(1f).height(44.dp).testTag("home_update_view_btn"),
            )
            val dlLabel =
              if (updateUiState.pipelineState == DrxUpdatePipelineState.READY_TO_INSTALL) {
                if (isIndo) "Pasang Update" else "Install Update"
              } else {
                if (isIndo) "Unduh Update" else "Download Update"
              }
            DrxPrimaryButton(
              label = dlLabel,
              icon = Icons.Default.Download,
              accentColor = drxColors.optimalGreen,
              onClick = onDownloadUpdate,
              modifier = Modifier.weight(1f).height(44.dp).testTag("home_update_download_btn"),
            )
          }
        }
      }
    }

    // 3. QUICK PERFORMANCE OVERVIEW — Compact 6-KPI Telemetry Grid + Storage Bar
    item {
      val cpuValueText =
        telemetry.cpuInfo.usagePercent?.let { "${it.safeRoundToInt()}%" }
          ?: telemetry.cpuInfo.currentFreqMhz?.let { "${it} MHz" }
          ?: "${telemetry.cpuInfo.cores} Cores"
      val cpuSub =
        telemetry.cpuInfo.currentFreqMhz?.let { "${telemetry.cpuInfo.cores}C • ${telemetry.cpuInfo.abi}" }
          ?: strings.cpuRestrictedNote

      val ramPct = telemetry.memoryInfo.usedPercent.sanitizeFinite(0f)
      val ramUsedGb = (telemetry.memoryInfo.usedBytes / (1024f * 1024f * 1024f)).sanitizeFinite(0f)
      val ramTotGb = (telemetry.memoryInfo.totalBytes / (1024f * 1024f * 1024f)).sanitizeFinite(0f)

      val stoPct = telemetry.storageInfo.usedPercent.sanitizeFinite(0f)
      val stoUsedGb = (telemetry.storageInfo.usedBytes / (1024f * 1024f * 1024f)).sanitizeFinite(0f)
      val stoTotGb = (telemetry.storageInfo.totalBytes / (1024f * 1024f * 1024f)).sanitizeFinite(0f)

      val tempC = telemetry.batteryInfo.temperatureC.sanitizeFinite(30f)
      val tempColor =
        when {
          tempC >= 42f -> drxColors.alertCrimson
          tempC >= 37f -> drxColors.warningAmber
          else -> drxColors.neonCyan
        }

      val netSub =
        telemetry.networkSnapshot.linkSpeedMbps?.let { "$it Mbps" }
          ?: telemetry.networkSnapshot.networkSubType

      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          DrxSectionHeader(
            title = strings.devicePerformanceTitle,
            actionLabel = "${telemetry.displayInfo.refreshRateHz.safeRoundToInt(60)} Hz",
            onAction = { onNavigateTool(ToolRoutes.MONITOR) },
            modifier = Modifier.weight(1f),
          )
          DrxInfoTooltipIcon(
            title = strings.devicePerformanceTitle,
            description =
              if (isIndo) {
                "Android 8+ membatasi pembacaan beban CPU mentah di /proc/stat tanpa root. DRX membaca RAM, suhu baterai, voltase, kapasitas storage, jaringan & UI FPS asli."
              } else {
                "Android 8+ restricts raw /proc/stat CPU load without root. DRX reads live RAM, battery temperature, storage, network link & real Choreographer UI FPS."
              },
          )
        }

        DrxGlassCard(
          onClick = { onNavigateTool(ToolRoutes.MONITOR) },
          cornerRadius = 22.dp,
          modifier = Modifier.testTag("home_telemetry_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            TelemetryKpiTile(
              icon = Icons.Default.Speed,
              label = "UI FPS",
              value = "${telemetry.uiFps.safeRoundToInt(60)} FPS",
              sub = "${telemetry.displayInfo.refreshRateHz.safeRoundToInt(60)} Hz",
              accent = drxColors.neonCyan,
              modifier = Modifier.weight(1f),
            )
            TelemetryKpiTile(
              icon = Icons.Default.Memory,
              label = strings.cpuLabel,
              value = cpuValueText,
              sub = cpuSub,
              accent = drxColors.electricBlue,
              modifier = Modifier.weight(1f),
            )
            TelemetryKpiTile(
              icon = Icons.Default.BarChart,
              label = strings.ramLabel,
              value = "${ramPct.safeRoundToInt()}%",
              sub = String.format(Locale.US, "%.1f/%.1fG", ramUsedGb, ramTotGb),
              accent = drxColors.neonCyan,
              modifier = Modifier.weight(1f),
            )
          }

          Spacer(Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            TelemetryKpiTile(
              icon = Icons.Default.BatteryChargingFull,
              label = strings.batteryLevelLabel,
              value = "${telemetry.batteryInfo.levelPercent}%",
              sub = telemetry.batteryInfo.chargeSource,
              accent = drxColors.optimalGreen,
              modifier = Modifier.weight(1f),
            )
            TelemetryKpiTile(
              icon = Icons.Default.Thermostat,
              label = strings.batteryTempLabel,
              value = String.format(Locale.US, "%.0f°C", tempC),
              sub = "${telemetry.batteryInfo.voltageMv} mV",
              accent = tempColor,
              modifier = Modifier.weight(1f),
            )
            TelemetryKpiTile(
              icon = Icons.Default.Wifi,
              label = strings.networkLabel,
              value = telemetry.networkSnapshot.transportType,
              sub = netSub,
              accent = if (telemetry.networkSnapshot.isConnected) drxColors.electricBlue else drxColors.alertCrimson,
              modifier = Modifier.weight(1f),
            )
          }

          Spacer(Modifier.height(14.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = strings.storageLabel,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text =
                String.format(
                  Locale.US,
                  "%.0f / %.0f GB (%d%%)",
                  stoUsedGb,
                  stoTotGb,
                  stoPct.safeRoundToInt(),
                ),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          Spacer(Modifier.height(8.dp))
          DrxCustomProgressBar(progress = stoPct / 100f)
        }
      }
    }

    // 4. DRX QUICK PROFILE SWITCHER (Live HUD & Polling Intensity)
    item {
      DrxStaggeredEntrance(index = 1, animationsEnabled = prefs.animationsEnabled) {
        DrxGlassCard(
          accentColor = drxColors.neonCyan,
          cornerRadius = 22.dp,
          modifier = Modifier.testTag("home_quick_profile_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = strings.quickProfileTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
              )
              Text(
                text = "${prefs.quickOverlayProfile.pollingIntervalMs}ms • HUD ${prefs.quickOverlayProfile.hudMode.name}",
                style = DrxTypographyTokens.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            DrxStatusPill(
              text = if (isIndo) prefs.quickOverlayProfile.labelId else prefs.quickOverlayProfile.labelEn,
              color = drxColors.neonCyan,
            )
          }
          Spacer(Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            QuickOverlayProfile.entries.forEach { qp ->
              val shortLbl =
                when (qp) {
                  QuickOverlayProfile.BALANCED -> if (isIndo) "Seimbang" else "Balanced"
                  QuickOverlayProfile.PERFORMANCE_MONITORING -> "Perf Mon"
                  QuickOverlayProfile.BATTERY_SAVER -> if (isIndo) "Hemat" else "Bat Saver"
                  QuickOverlayProfile.MINIMAL_OVERLAY -> "Minimal"
                }
              DrxFilterPill(
                label = shortLbl,
                selected = prefs.quickOverlayProfile == qp,
                onClick = { viewModel.applyQuickOverlayProfile(qp) },
                modifier = Modifier.weight(1f).testTag("quick_profile_${qp.id}"),
              )
            }
          }
        }
      }
    }

    // 5. MY GAMES CAROUSEL
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DrxSectionHeader(
          title = strings.myGamesSection,
          actionLabel = strings.viewAllBtn,
          onAction = onNavigateGames,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item {
            DrxFilterPill(
              label = strings.preGameCheckTitle,
              selected = false,
              onClick = { onNavigateTool(ToolRoutes.PRE_GAME_CHECK) },
              modifier = Modifier.testTag("home_pre_game_check_chip"),
            )
          }
          item {
            DrxFilterPill(
              label = strings.profileAutomationTitle,
              selected = false,
              onClick = { onNavigateTool(ToolRoutes.PROFILE_AUTOMATION) },
              modifier = Modifier.testTag("home_automation_chip"),
            )
          }
        }

        if (games.isEmpty()) {
          DrxGlassCard(onClick = onNavigateGames, cornerRadius = 22.dp) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Default.Add, contentDescription = null, tint = drxColors.electricBlue)
              Spacer(Modifier.width(12.dp))
              Text(
                text = strings.noGamesYet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(games.take(10), key = { it.packageName }) { game ->
              val iconBmp = remember(game.packageName) { viewModel.loadAppIcon(game.packageName) }
              val gameProf =
                GamingProfile.entries.find { it.id == game.profileName } ?: GamingProfile.BALANCED
              DrxGlassCard(
                modifier = Modifier.width(174.dp),
                highlighted = game.isFavorite,
                cornerRadius = 22.dp,
                contentPadding = PaddingValues(14.dp),
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  if (iconBmp != null) {
                    Image(
                      bitmap = iconBmp,
                      contentDescription = game.appName,
                      modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)),
                    )
                  } else {
                    Icon(
                      imageVector = Icons.Default.SportsEsports,
                      contentDescription = game.appName,
                      tint = drxColors.electricBlue,
                      modifier = Modifier.size(42.dp),
                    )
                  }
                  Spacer(Modifier.width(10.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = game.appName,
                      style = MaterialTheme.typography.titleMedium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = strings.profileName(gameProf),
                      style = DrxTypographyTokens.caption,
                      color = drxColors.neonCyan,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
                Spacer(Modifier.height(12.dp))
                DrxPrimaryButton(
                  label = strings.playBtn,
                  icon = Icons.Default.PlayArrow,
                  onClick = { viewModel.requestLaunchWithPreGameCheck(game, activity) },
                  modifier =
                    Modifier
                      .fillMaxWidth()
                      .height(42.dp)
                      .testTag("home_play_${game.packageName}"),
                )
              }
            }
          }
        }
      }
    }

    // 6. QUICK TOOLS (Clean 6-Item Grid — zero bloat or duplicate tab/header shortcuts)
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DrxSectionHeader(
          title = strings.quickToolsSection,
          actionLabel = strings.viewAllBtn,
          onAction = onNavigateTools,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          QuickToolTile(
            label = "Sidebar",
            icon = Icons.Default.PictureInPictureAlt,
            active = prefs.floatingSidebarEnabled,
            onClick = { onNavigateTool(ToolRoutes.SIDEBAR) },
            modifier = Modifier.weight(1f).testTag("home_quick_sidebar_tile"),
          )
          QuickToolTile(
            label = "Recorder",
            icon = Icons.Default.FiberManualRecord,
            active = recState.isRecording,
            onClick = { onNavigateTool(ToolRoutes.SCREEN_RECORDER) },
            modifier = Modifier.weight(1f).testTag("home_quick_recorder_tile"),
          )
          QuickToolTile(
            label = "Screenshot",
            icon = Icons.Default.CameraAlt,
            active = false,
            onClick = { onNavigateTool(ToolRoutes.SCREENSHOT) },
            modifier = Modifier.weight(1f).testTag("home_quick_screenshot_button"),
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          QuickToolTile(
            label = if (isIndo) "Notifikasi" else "Game Notifs",
            icon = Icons.Default.Notifications,
            active = prefs.gameNotificationViewerEnabled || unreadGameNotifsCount > 0,
            onClick = { onNavigateTool(ToolRoutes.GAME_NOTIFICATIONS) },
            modifier = Modifier.weight(1f).testTag("home_quick_notifs_tile"),
          )
          QuickToolTile(
            label = "Voice Studio",
            icon = Icons.Default.GraphicEq,
            active = false,
            onClick = { onNavigateTool(ToolRoutes.VOICE) },
            modifier = Modifier.weight(1f).testTag("home_quick_voice_tile"),
          )
          QuickToolTile(
            label = "Lag Check",
            icon = Icons.Default.Radar,
            active = false,
            onClick = { onNavigateTool(ToolRoutes.LAG_DETECTIVE) },
            modifier = Modifier.weight(1f).testTag("home_quick_lag_tile"),
          )
        }
      }
    }

    // 7. RECENT ACTIVITY, RELEASE NOTES & LIVE RECORDER STRIP
    if (globalStats != null && (globalStats.totalPlaytimeMs > 0L || globalStats.lastPlayedGameName != null)) {
      item {
        DrxGlassCard(
          accentColor = drxColors.neonCyan,
          cornerRadius = 22.dp,
          onClick = {
            val pkg = globalStats.lastPlayedPkg
            if (pkg != null) onOpenGameStats(pkg) else onNavigateTool(ToolRoutes.ACTIVITY_CENTER)
          },
          modifier = Modifier.testTag("home_last_played_stats_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = strings.lastPlayedGameLabel,
                style = MaterialTheme.typography.labelMedium,
                color = drxColors.neonCyan,
              )
              Spacer(Modifier.height(2.dp))
              Text(
                text = globalStats.lastPlayedGameName ?: strings.globalStatsTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              val playTimeTxt =
                TimeAndSizeFormatters.formatDurationReadable(globalStats.totalPlaytimeMs, isIndo)
              val lastTxt =
                globalStats.lastPlayedAtMs?.let {
                  TimeAndSizeFormatters.formatDateTimeWithZone(it, isIndo)
                } ?: strings.lastPlayedNever
              Text(
                text = "$playTimeTxt • $lastTxt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = strings.viewGameStatsBtn,
              tint = drxColors.electricBlue,
            )
          }
        }
      }
    }

    val hasUnreadUpdates =
      DrxUpdateCatalog.entries.any { !prefs.readUpdateVersions.contains(it.versionNumber) }
    if (hasUnreadUpdates) {
      item {
        DrxGlassCard(
          accentColor = drxColors.electricBlue,
          highlighted = true,
          cornerRadius = 22.dp,
          onClick = onNavigateUpdateLog,
          modifier = Modifier.testTag("home_update_log_banner"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier
                  .size(40.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(drxColors.electricBlue.copy(alpha = 0.14f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Default.Update,
                contentDescription = strings.updateLogTitle,
                tint = drxColors.neonCyan,
                modifier = Modifier.size(20.dp),
              )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "${strings.updateLogTitle} v${DrxUpdateCatalog.latestVersion}",
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.width(8.dp))
                DrxStatusPill(text = strings.unreadBadgeLabel, color = drxColors.neonCyan)
              }
              Spacer(Modifier.height(2.dp))
              Text(
                text = strings.newUpdateAvailableBanner,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    // Live Recorder Strip (quick capture or live active controls)
    item {
      val isCountingDown = recState.countdownRemainingSec > 0 && !recState.isRecording
      val recPulse =
        rememberDrxConditionalPulseAlpha(
          isPulsing = (recState.isRecording && !recState.isPaused) || isCountingDown,
          animationsEnabled = prefs.animationsEnabled,
        )
      val recColor =
        when {
          isCountingDown -> drxColors.warningAmber
          recState.isRecording && recState.isPaused -> drxColors.warningAmber
          recState.isRecording -> drxColors.alertCrimson
          else -> drxColors.neonCyan
        }
      DrxGlassCard(
        accentColor = recColor,
        highlighted = recState.isRecording || isCountingDown,
        cornerRadius = 22.dp,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("DRX Pro Recorder", style = MaterialTheme.typography.titleMedium)
            Text(
              text =
                when {
                  isCountingDown ->
                    if (isIndo) "Mulai merekam dalam ${recState.countdownRemainingSec} detik..."
                    else "Starting recording in ${recState.countdownRemainingSec}s..."
                  recState.isRecording ->
                    "${if (recState.isPaused) "PAUSED" else "REC"} ${ScreenRecorderService.formatDuration(recState.elapsedSeconds)} • ${TimeAndSizeFormatters.formatBytesAuto(recState.liveFileSizeBytes)}"
                  else ->
                    if (isIndo) "Rekam layar MP4 & buka galeri media" else "Record MP4 gameplay & open media gallery"
                },
              style = DrxTypographyTokens.caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          DrxStatusPill(
            text =
              when {
                isCountingDown -> "IN ${recState.countdownRemainingSec}s"
                recState.isRecording && recState.isPaused -> "PAUSED"
                recState.isRecording -> "LIVE"
                else -> "${recState.resolutionPreset.let { if (it == 0) "Orig" else "${it}p" }} • ${recState.targetFps}fps"
              },
            color = recColor,
            modifier = Modifier.alpha(recPulse),
          )
        }
        Spacer(Modifier.height(10.dp))
        if (isCountingDown) {
          DrxDangerButton(
            label = if (isIndo) "Batalkan Hitung Mundur (${recState.countdownRemainingSec}s)" else "Cancel Countdown (${recState.countdownRemainingSec}s)",
            icon = Icons.Default.Stop,
            onClick = { ScreenRecorderService.stopRecording(context) },
            modifier = Modifier.fillMaxWidth().height(44.dp),
          )
        } else if (recState.isRecording) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrxSecondaryButton(
              label = if (recState.isPaused) "Resume" else "Pause",
              icon = if (recState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
              onClick = {
                if (recState.isPaused) ScreenRecorderService.resumeRecording(context)
                else ScreenRecorderService.pauseRecording(context)
              },
              modifier = Modifier.weight(1f).height(44.dp),
            )
            DrxDangerButton(
              label = "Stop",
              icon = Icons.Default.Stop,
              onClick = { ScreenRecorderService.stopRecording(context) },
              modifier = Modifier.weight(1f).height(44.dp),
            )
          }
        } else {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrxPrimaryButton(
              label = if (isIndo) "Rekam Layar" else "Record",
              icon = Icons.Default.FiberManualRecord,
              onClick = {
                context.startActivity(
                  Intent(context, ScreenRecordConsentActivity::class.java).apply {
                    putExtra(ScreenRecordConsentActivity.EXTRA_CAPTURE_MODE, ScreenRecordConsentActivity.MODE_RECORD)
                    putExtra(ScreenRecordConsentActivity.EXTRA_COUNTDOWN_SEC, prefs.recorderCountdownSec)
                  }
                )
              },
              modifier = Modifier.weight(1f).height(44.dp).testTag("home_quick_record_button"),
            )
            DrxSecondaryButton(
              label = if (isIndo) "Galeri Media" else "Media Gallery",
              icon = Icons.Default.PhotoLibrary,
              onClick = { onNavigateTool(ToolRoutes.MEDIA_GALLERY) },
              modifier = Modifier.weight(1f).height(44.dp).testTag("home_quick_gallery_button"),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TelemetryKpiTile(
  icon: ImageVector,
  label: String,
  value: String,
  sub: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  val drxColors = LocalDrxColors.current
  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(16.dp))
        .background(drxColors.secondaryBg.copy(alpha = 0.72f))
        .border(1.dp, drxColors.glassBorder, RoundedCornerShape(16.dp))
        .padding(horizontal = 10.dp, vertical = 10.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = accent,
        modifier = Modifier.size(14.dp),
      )
      Spacer(Modifier.width(5.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Spacer(Modifier.height(6.dp))
    Text(
      text = value,
      style = DrxTypographyTokens.numericReadout.copy(fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.onBackground,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = sub,
      style = DrxTypographyTokens.caption,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun HeaderActionButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  badgeDot: Boolean = false,
) {
  val drxColors = LocalDrxColors.current
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = drxColors.cardSurface,
    border = BorderStroke(1.dp, drxColors.glassBorder),
    modifier = modifier.minimumInteractiveComponentSize().size(46.dp),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.size(20.dp),
      )
      if (badgeDot) {
        Box(
          modifier =
            Modifier
              .align(Alignment.TopEnd)
              .padding(top = 9.dp, end = 9.dp)
              .size(8.dp)
              .clip(CircleShape)
              .background(drxColors.neonCyan)
        )
      }
    }
  }
}

@Composable
private fun QuickToolTile(
  label: String,
  icon: ImageVector,
  active: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val drxColors = LocalDrxColors.current
  DrxGlassCard(
    modifier = modifier.height(92.dp),
    accentColor = drxColors.electricBlue,
    highlighted = active,
    cornerRadius = 20.dp,
    contentPadding = PaddingValues(10.dp),
    onClick = onClick,
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Box(
        modifier =
          Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(
              if (active) drxColors.electricBlue.copy(alpha = 0.20f)
              else drxColors.elevatedSurface
            ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = label,
          tint = if (active) drxColors.neonCyan else MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.size(19.dp),
        )
      }
      Spacer(Modifier.height(7.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (active) drxColors.neonCyan else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
