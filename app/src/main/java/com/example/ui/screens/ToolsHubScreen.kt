package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.i18n.LocalStrings
import com.example.ui.theme.DrxGroupedCard
import com.example.ui.theme.DrxHairlineDivider
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSectionHeader
import com.example.ui.theme.DrxStaggeredEntrance
import com.example.ui.theme.LocalDrxColors

object ToolRoutes {
  const val BOOSTER = "tool_booster"
  const val SIDEBAR = "tool_sidebar"
  const val NETWORK = "tool_network"
  const val DISPLAY = "tool_display"
  const val SOUND = "tool_sound"
  const val VOICE = "tool_voice"
  const val MONITOR = "tool_monitor"
  const val PROFILES = "tool_profiles"
  const val UTILITIES = "tool_utilities"
  const val STATS = "tool_stats"
  const val CUSTOMIZATION = "tool_customization"
  const val LAG_DETECTIVE = "tool_lag_detective"
  const val CRASH_ANALYZER = "tool_crash_analyzer"
  const val GAMING_HEALTH = "tool_gaming_health"
  const val SCREEN_RECORDER = "tool_screen_recorder"
  const val MEDIA_GALLERY = "tool_media_gallery"
  const val DIAGNOSTIC_HISTORY = "tool_diagnostic_history"
  const val DOWNLOAD_CENTER = "tool_download_center"
  const val GAME_NOTIFICATIONS = "tool_game_notifications"
  const val NOTIFICATION_INSIGHTS = "tool_notification_insights"
  const val GAME_EVENTS = "tool_game_events"
  const val ACTIVITY_CENTER = "tool_activity_center"
  const val UPDATE_CENTER = "tool_update_center"
  const val SESSION_INTELLIGENCE = "tool_session_intelligence"
  const val STABILITY_WATCH = "tool_stability_watch"
  // v1.25.5 New Intelligence & Gaming Tools
  const val SESSION_TIMELINE = "tool_session_timeline"
  const val RESOURCE_REPORT = "tool_resource_report"
  const val PROFILE_AUTOMATION = "tool_profile_automation"
  const val PRE_GAME_CHECK = "tool_pre_game_check"
  const val TOUCH_TEST = "tool_touch_test"
  const val FOCUS_MODE = "tool_focus_mode"
  const val SCREENSHOT = "tool_screenshot"
  const val THERMAL_GUARD = "tool_thermal_guard"
  const val SESSION_RECAP = "tool_session_recap"
  const val STORAGE_INSPECTOR = "tool_storage_inspector"
  const val COMPATIBILITY_CHECK = "tool_compatibility_check"
}

private data class ToolEntry(
  val route: String,
  val title: String,
  val subtitle: String,
  val icon: ImageVector,
)

private data class ToolSection(
  val categoryTitle: String,
  val items: List<ToolEntry>,
)

@Composable
fun ToolsHubScreen(onNavigateTool: (String) -> Unit) {
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current

  val sections =
    listOf(
      ToolSection(
        categoryTitle = strings.catGamingTools,
        items =
          listOf(
            ToolEntry(ToolRoutes.BOOSTER, strings.toolBoosterTitle, strings.toolBoosterSub, Icons.Default.Bolt),
            ToolEntry(ToolRoutes.SIDEBAR, strings.toolSidebarTitle, strings.toolSidebarSub, Icons.Default.PictureInPictureAlt),
            ToolEntry(ToolRoutes.PROFILE_AUTOMATION, strings.profileAutomationTitle, strings.profileAutomationSub, Icons.Default.AutoAwesome),
            ToolEntry(ToolRoutes.FOCUS_MODE, strings.focusModeTitle, strings.focusModeSub, Icons.Default.DoNotDisturbOn),
            ToolEntry(ToolRoutes.PRE_GAME_CHECK, strings.preGameCheckTitle, strings.preGameCheckSub, Icons.AutoMirrored.Filled.FactCheck),
            ToolEntry(ToolRoutes.CUSTOMIZATION, strings.customizationCenterTitle, strings.customizationCenterSub, Icons.Default.Palette),
            ToolEntry(ToolRoutes.UTILITIES, strings.toolUtilitiesTitle, strings.toolUtilitiesSub, Icons.Default.Build),
          ),
      ),
      ToolSection(
        categoryTitle = "Diagnostics & Intelligence",
        items =
          listOf(
            ToolEntry(ToolRoutes.THERMAL_GUARD, strings.thermalGuardTitle, strings.thermalGuardSub, Icons.Default.Thermostat),
            ToolEntry(ToolRoutes.SESSION_TIMELINE, strings.sessionTimelineTitle, strings.sessionTimelineSub, Icons.Default.Timeline),
            ToolEntry(ToolRoutes.SESSION_RECAP, strings.sessionRecapTitle, strings.sessionRecapSub, Icons.Default.Summarize),
            ToolEntry(ToolRoutes.RESOURCE_REPORT, strings.resourceReportTitle, strings.resourceReportSub, Icons.Default.Assessment),
            ToolEntry(ToolRoutes.COMPATIBILITY_CHECK, strings.compatibilityCheckTitle, strings.compatibilityCheckSub, Icons.Default.VerifiedUser),
            ToolEntry(ToolRoutes.STORAGE_INSPECTOR, strings.storageInspectorTitle, strings.storageInspectorSub, Icons.Default.Storage),
            ToolEntry(ToolRoutes.STABILITY_WATCH, strings.stabilityWatchTitle, strings.stabilityWatchSub, Icons.Default.Security),
            ToolEntry(ToolRoutes.SESSION_INTELLIGENCE, strings.sessionIntelligenceTitle, strings.sessionIntelligenceSub, Icons.Default.Insights),
            ToolEntry(ToolRoutes.LAG_DETECTIVE, "DRX Lag Detective", "Memory, thermal, CPU & latency bottleneck finder", Icons.Default.Radar),
            ToolEntry(ToolRoutes.CRASH_ANALYZER, "DRX Crash Analyzer", "Session exit classification & diagnostic log scan", Icons.Default.BugReport),
            ToolEntry(ToolRoutes.GAMING_HEALTH, "DRX Gaming Health", "Pre-game readiness score across 6 hardware metrics", Icons.Default.HealthAndSafety),
            ToolEntry(ToolRoutes.DIAGNOSTIC_HISTORY, "Diagnostic History", "Saved lag, crash, health & session reports", Icons.Default.History),
          ),
      ),
      ToolSection(
        categoryTitle = "Game Notifications & Activity",
        items =
          listOf(
            ToolEntry(ToolRoutes.GAME_NOTIFICATIONS, strings.gameNotificationsTitle, strings.gameNotificationsSub, Icons.Default.Notifications),
            ToolEntry(ToolRoutes.GAME_EVENTS, strings.gameEventsTitle, strings.gameEventsSub, Icons.Default.Event),
            ToolEntry(ToolRoutes.NOTIFICATION_INSIGHTS, strings.notificationInsightsTitle, strings.notificationInsightsSub, Icons.Default.Insights),
            ToolEntry(ToolRoutes.ACTIVITY_CENTER, strings.activityCenterTitle, strings.activityCenterSub, Icons.Default.Dashboard),
          ),
      ),
      ToolSection(
        categoryTitle = "Screen Capture & Gallery",
        items =
          listOf(
            ToolEntry(ToolRoutes.SCREENSHOT, strings.screenshotStudioTitle, strings.screenshotStudioSub, Icons.Default.CameraAlt),
            ToolEntry(ToolRoutes.SCREEN_RECORDER, "DRX Pro Recorder", "480p-1080p MP4 recorder, audio mix & screenshot", Icons.Default.FiberManualRecord),
            ToolEntry(ToolRoutes.MEDIA_GALLERY, "DRX Media Gallery", "Screenshots, recordings, multi-share & SAF cleanup", Icons.Default.PhotoLibrary),
          ),
      ),
      ToolSection(
        categoryTitle = strings.catAudioTools,
        items =
          listOf(
            ToolEntry(ToolRoutes.VOICE, strings.toolVoiceTitle, strings.toolVoiceSub, Icons.Default.Mic),
            ToolEntry(ToolRoutes.SOUND, strings.toolSoundTitle, strings.toolSoundSub, Icons.Default.GraphicEq),
          ),
      ),
      ToolSection(
        categoryTitle = strings.catDisplayTools,
        items =
          listOf(
            ToolEntry(ToolRoutes.DISPLAY, strings.displayIntelTitle, strings.displayIntelSub, Icons.Default.DisplaySettings),
            ToolEntry(ToolRoutes.TOUCH_TEST, strings.touchResponseTestTitle, strings.touchResponseTestSub, Icons.Default.TouchApp),
          ),
      ),
      ToolSection(
        categoryTitle = strings.catNetworkTools,
        items =
          listOf(
            ToolEntry(ToolRoutes.NETWORK, strings.toolNetworkTitle, strings.toolNetworkSub, Icons.Default.NetworkCheck),
          ),
      ),
      ToolSection(
        categoryTitle = strings.catDeviceMonitor,
        items =
          listOf(
            ToolEntry(ToolRoutes.MONITOR, strings.toolMonitorTitle, strings.toolMonitorSub, Icons.Default.MonitorHeart),
            ToolEntry(ToolRoutes.STATS, strings.toolStatsTitle, strings.toolStatsSub, Icons.Default.BarChart),
          ),
      ),
      ToolSection(
        categoryTitle = "Build & Export",
        items =
          listOf(
            ToolEntry(ToolRoutes.UPDATE_CENTER, strings.updateCenterTitle, strings.updateCenterSub, Icons.Default.SystemUpdate),
            ToolEntry(ToolRoutes.DOWNLOAD_CENTER, "Download & APK Export", "Verified SHA-256 APK export & AAB build status", Icons.Default.Download),
          ),
      ),
    )

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    item {
      DrxScreenHeader(title = strings.toolsHubTitle, subtitle = "Minimalist Native Utilities • DerreX")
    }

    sections.forEachIndexed { sectionIndex, section ->
      item(key = section.categoryTitle) {
        DrxStaggeredEntrance(index = if (sectionIndex == 0) 0 else sectionIndex.coerceAtMost(5)) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DrxSectionHeader(title = section.categoryTitle)
            DrxGroupedCard(cornerRadius = 20.dp) {
            section.items.forEachIndexed { idx, item ->
              if (idx > 0) {
                DrxHairlineDivider(modifier = Modifier.padding(start = 68.dp, end = 16.dp))
              }
              Row(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clickable { onNavigateTool(item.route) }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("card_${item.route}"),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier =
                    Modifier
                      .size(40.dp)
                      .clip(RoundedCornerShape(12.dp))
                      .background(drxColors.elevatedSurface),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = drxColors.electricBlue,
                    modifier = Modifier.size(20.dp),
                  )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = item.subtitle,
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
        }
      }
    }
  }
}
