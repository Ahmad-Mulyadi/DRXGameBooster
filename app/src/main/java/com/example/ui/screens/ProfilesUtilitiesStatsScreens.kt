package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.GameSessionEntity
import com.example.data.model.AppUsageSummary
import com.example.data.model.GamingProfile
import com.example.data.model.GlobalGamingStatistics
import com.example.data.model.StatsTimeFilter
import com.example.data.prefs.DrxUserPrefs
import com.example.i18n.LocalStrings
import com.example.system.GameStatsCollector
import com.example.system.TimeAndSizeFormatters
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.minimumInteractiveComponentSize
import com.example.ui.theme.DrxCustomSlider
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxGroupedCard
import com.example.ui.theme.DrxHairlineDivider
import com.example.ui.theme.DrxInfoTooltipIcon
import com.example.ui.theme.DrxLogoBadge
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
import kotlin.math.roundToInt

@Composable
fun ProfileHubScreen(
  prefs: DrxUserPrefs,
  sessions: List<GameSessionEntity>,
  appUsage: List<AppUsageSummary>,
  viewModel: DrxMainViewModel,
  onOpenAllProfiles: () -> Unit,
  onOpenAllStats: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenAbout: () -> Unit,
  onOpenCustomization: () -> Unit = {},
) {
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val activity = LocalActivity.current

  val totalMinutes = sessions.sumOf { it.durationMs } / 60_000L
  val mostPlayed =
    sessions
      .groupBy { it.gameName }
      .maxByOrNull { (_, list) -> list.sumOf { it.durationMs } }
      ?.key ?: "-"

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.navProfile,
        subtitle = "${strings.appName} • DerreX",
      )
    }

    // Active Profile Card
    item {
      DrxGlassCard(accentColor = drxColors.electricBlue, highlighted = true) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          DrxLogoBadge(size = 46.dp)
          Spacer(Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(strings.profileName(prefs.activeProfile), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
              "Boosts: ${prefs.totalBoostCount} • ${if (prefs.gamingModeActive) strings.gamingModeActive else strings.gamingModeStandby}",
              style = DrxTypographyTokens.caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          DrxSecondaryButton(
            label = strings.selectProfile,
            onClick = onOpenAllProfiles,
            modifier = Modifier.height(38.dp),
          )
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(GamingProfile.entries) { prof ->
            DrxFilterPill(
              label = strings.profileName(prof),
              selected = prefs.activeProfile == prof,
              onClick = { viewModel.selectProfile(prof, activity) },
            )
          }
        }
      }
    }

    // Play Statistics Summary
    item {
      DrxGlassCard(onClick = onOpenAllStats) {
        DrxSectionHeader(
          title = strings.statisticsTitle,
          actionLabel = "${sessions.size} ${strings.totalSessions}",
          onAction = onOpenAllStats,
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Column {
            Text(strings.totalPlaytime, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${totalMinutes} min", style = MaterialTheme.typography.displaySmall, color = drxColors.neonCyan)
          }
          Column(horizontalAlignment = Alignment.End) {
            Text(strings.mostPlayedGame, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(mostPlayed, style = MaterialTheme.typography.titleMedium, color = drxColors.silver)
          }
        }
      }
    }

    // App Usage Stats (24h)
    item {
      DrxGlassCard {
        DrxSectionHeader(title = strings.appUsageSection)
        Spacer(Modifier.height(10.dp))
        if (appUsage.isEmpty()) {
          Text(
            text = strings.grantUsageAccessHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(10.dp))
          DrxPrimaryButton(
            label = strings.grantPermission,
            onClick = { viewModel.tweaksManager.openUsageAccessSettings() },
            modifier = Modifier.height(40.dp),
          )
        } else {
          appUsage.take(5).forEachIndexed { idx, app ->
            val mins = (app.totalForegroundMs / 60_000L).coerceAtLeast(1L)
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text("${idx + 1}. ${app.appName}", style = MaterialTheme.typography.bodySmall)
              Text("${mins} min", style = DrxTypographyTokens.numericReadout, color = drxColors.neonCyan)
            }
          }
        }
      }
    }

    // Grouped Profile Shortcuts (Customization, Settings, About DRX)
    item {
      DrxGroupedCard(cornerRadius = 20.dp) {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .clickable(onClick = onOpenCustomization)
              .padding(horizontal = 16.dp, vertical = 14.dp)
              .testTag("profile_customization_card"),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Default.Palette,
            contentDescription = strings.customizationCenterTitle,
            tint = drxColors.electricBlue,
            modifier = Modifier.size(21.dp),
          )
          Spacer(Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(strings.customizationCenterTitle, style = MaterialTheme.typography.titleMedium)
            Text(
              strings.customizationCenterSub,
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
        DrxHairlineDivider(modifier = Modifier.padding(start = 48.dp, end = 16.dp))
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .clickable(onClick = onOpenSettings)
              .padding(horizontal = 16.dp, vertical = 14.dp)
              .testTag("profile_settings_card"),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = strings.settingsTitle,
            tint = drxColors.neonCyan,
            modifier = Modifier.size(21.dp),
          )
          Spacer(Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(strings.settingsTitle, style = MaterialTheme.typography.titleMedium)
            Text(
              "${strings.languageLabel} • ${strings.permissionManagerTitle}",
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
        DrxHairlineDivider(modifier = Modifier.padding(start = 48.dp, end = 16.dp))
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .minimumInteractiveComponentSize()
              .clickable(onClick = onOpenAbout)
              .padding(horizontal = 16.dp, vertical = 14.dp)
              .testTag("profile_about_card"),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = strings.aboutTitle,
            tint = drxColors.electricBlue,
            modifier = Modifier.size(21.dp),
          )
          Spacer(Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(strings.aboutTitle, style = MaterialTheme.typography.titleMedium)
            Text(
              "Developer Profile (DerreX) • Supported by Nanay",
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

@Composable
fun ProfilesScreen(
  prefs: DrxUserPrefs,
  viewModel: DrxMainViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val activity = LocalActivity.current

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item { DrxScreenHeader(title = strings.profilesTitle, onBack = onBack) }

    items(GamingProfile.entries) { profile ->
      val isActive = prefs.activeProfile == profile
      val cfg = prefs.resolveProfileConfig(profile)
      DrxGlassCard(
        accentColor = drxColors.electricBlue,
        highlighted = isActive,
        onClick = { viewModel.selectProfile(profile, activity) },
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(strings.profileName(profile), style = MaterialTheme.typography.titleLarge)
          if (isActive) {
            DrxStatusPill(text = "ACTIVE", color = drxColors.optimalGreen)
          }
        }
        Spacer(Modifier.height(6.dp))
        Text(
          text =
            "Brightness ${cfg.brightnessPercent}% • DND ${if (cfg.enableDnd) "ON" else "OFF"} • Immersive ${if (cfg.immersiveMode) "ON" else "OFF"} • Sidebar ${if (cfg.autoFloatingSidebar) "AUTO" else "MANUAL"}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (profile == GamingProfile.CUSTOM) {
          Spacer(Modifier.height(12.dp))
          Text(
            "${strings.brightnessControl}: ${cfg.brightnessPercent}%",
            style = DrxTypographyTokens.numericReadout,
          )
          DrxCustomSlider(
            value = cfg.brightnessPercent.toFloat(),
            onValueChange = { v ->
              viewModel.updateCustomProfileConfig(cfg.copy(brightnessPercent = v.roundToInt()))
            },
            valueRange = 15f..100f,
          )
          SettingToggleRow(
            label = strings.dndMode,
            checked = cfg.enableDnd,
            onCheckedChange = { viewModel.updateCustomProfileConfig(cfg.copy(enableDnd = it)) },
          )
          SettingToggleRow(
            label = strings.immersiveMode,
            checked = cfg.immersiveMode,
            onCheckedChange = { viewModel.updateCustomProfileConfig(cfg.copy(immersiveMode = it)) },
          )
        }

        Spacer(Modifier.height(10.dp))
        DrxPrimaryButton(
          label = strings.applyProfile,
          accentColor = if (isActive) drxColors.optimalGreen else drxColors.electricBlue,
          onClick = { viewModel.selectProfile(profile, activity) },
          modifier = Modifier.fillMaxWidth().testTag("apply_profile_${profile.id}"),
        )
      }
    }
  }
}

@Composable
fun UtilitiesScreen(
  prefs: DrxUserPrefs,
  toolsViewModel: DrxToolsViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current

  val swMs by toolsViewModel.stopwatchMs.collectAsStateWithLifecycle()
  val swRunning by toolsViewModel.stopwatchRunning.collectAsStateWithLifecycle()
  val swLaps by toolsViewModel.stopwatchLaps.collectAsStateWithLifecycle()

  val timerSec by toolsViewModel.timerRemainingSec.collectAsStateWithLifecycle()
  val timerRunning by toolsViewModel.timerRunning.collectAsStateWithLifecycle()
  val notes by toolsViewModel.notes.collectAsStateWithLifecycle()

  var noteTitle by remember { mutableStateOf("") }
  var noteBody by remember { mutableStateOf("") }

  var killsInput by remember { mutableStateOf("12") }
  var deathsInput by remember { mutableStateOf("3") }
  var dpiInput by remember { mutableStateOf("800") }
  var sensInput by remember { mutableStateOf("1.5") }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item { DrxScreenHeader(title = strings.utilitiesTitle, onBack = onBack) }

    // Stopwatch
    item {
      DrxGlassCard {
        DrxSectionHeader(title = strings.stopwatchTitle)
        Spacer(Modifier.height(8.dp))
        val totalSec = swMs / 1000L
        val mins = totalSec / 60L
        val secs = totalSec % 60L
        val tenths = (swMs % 1000L) / 10L
        Text(
          text = String.format(Locale.US, "%02d:%02d.%02d", mins, secs, tenths),
          style = MaterialTheme.typography.displaySmall,
          color = drxColors.neonCyan,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          DrxPrimaryButton(
            label = if (swRunning) "Pause" else "Start",
            onClick = { toolsViewModel.toggleStopwatch() },
            modifier = Modifier.weight(1f).testTag("stopwatch_toggle_button"),
          )
          DrxSecondaryButton(
            label = "Lap",
            enabled = swMs > 0L,
            onClick = { toolsViewModel.lapStopwatch() },
            modifier = Modifier.weight(1f),
          )
          DrxSecondaryButton(
            label = "Reset",
            onClick = { toolsViewModel.resetStopwatch() },
            modifier = Modifier.weight(1f),
          )
        }
        if (swLaps.isNotEmpty()) {
          Spacer(Modifier.height(8.dp))
          swLaps.take(3).forEachIndexed { idx, lapMs ->
            val s = lapMs / 1000L
            Text(
              text = String.format(Locale.US, "Lap %d: %02d:%02d.%02d", swLaps.size - idx, s / 60, s % 60, (lapMs % 1000) / 10),
              style = DrxTypographyTokens.caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    // Countdown Gaming Timer
    item {
      DrxGlassCard {
        DrxSectionHeader(title = strings.timerTitle)
        Spacer(Modifier.height(8.dp))
        Text(
          text = String.format(Locale.US, "%02d:%02d", timerSec / 60, timerSec % 60),
          style = MaterialTheme.typography.displaySmall,
          color = drxColors.electricBlue,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(10, 15, 30, 45).forEach { m ->
            DrxFilterPill(
              label = "${m}m",
              selected = timerSec == m * 60,
              onClick = { toolsViewModel.setTimerMinutes(m) },
              modifier = Modifier.weight(1f),
            )
          }
        }
        Spacer(Modifier.height(10.dp))
        DrxPrimaryButton(
          label = if (timerRunning) "Pause Timer" else "Start Timer",
          onClick = { toolsViewModel.toggleTimer(prefs.timerNotificationsEnabled) },
          modifier = Modifier.fillMaxWidth().testTag("timer_toggle_button"),
        )
      }
    }

    // Gaming Calculator
    item {
      DrxGlassCard {
        DrxSectionHeader(title = strings.calcTitle)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = killsInput,
            onValueChange = { killsInput = it },
            label = { Text("Kills") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = drxColors.glassBorder),
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = deathsInput,
            onValueChange = { deathsInput = it },
            label = { Text("Deaths") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = drxColors.glassBorder),
            modifier = Modifier.weight(1f),
          )
        }
        val k = killsInput.toFloatOrNull() ?: 0f
        val d = (deathsInput.toFloatOrNull() ?: 1f).coerceAtLeast(1f)
        Text(
          text = String.format(Locale.US, "K/D Ratio: %.2f", k / d),
          style = DrxTypographyTokens.numericReadout,
          color = drxColors.neonCyan,
          modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = dpiInput,
            onValueChange = { dpiInput = it },
            label = { Text("DPI") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = drxColors.glassBorder),
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = sensInput,
            onValueChange = { sensInput = it },
            label = { Text("Sensitivity") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = drxColors.glassBorder),
            modifier = Modifier.weight(1f),
          )
        }
        val dpi = dpiInput.toFloatOrNull() ?: 0f
        val sens = sensInput.toFloatOrNull() ?: 0f
        Text(
          text = String.format(Locale.US, "Effective eDPI: %.0f", dpi * sens),
          style = DrxTypographyTokens.numericReadout,
          color = drxColors.optimalGreen,
          modifier = Modifier.padding(top = 6.dp),
        )
      }
    }

    // Tactical Gaming Notes
    item {
      DrxGlassCard {
        DrxSectionHeader(title = strings.notesTitle)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = noteTitle,
          onValueChange = { noteTitle = it },
          label = { Text("Title / Game") },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = drxColors.glassBorder),
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          value = noteBody,
          onValueChange = { noteBody = it },
          label = { Text("Loadout / Sensi / Strategy Notes") },
          shape = RoundedCornerShape(12.dp),
          colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = drxColors.glassBorder),
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        DrxPrimaryButton(
          label = strings.addNote,
          onClick = {
            toolsViewModel.saveNote(noteTitle, noteBody, "")
            noteTitle = ""
            noteBody = ""
          },
          modifier = Modifier.fillMaxWidth().testTag("save_note_button"),
        )
      }
    }

    items(notes, key = { it.id }) { note ->
      DrxGlassCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(note.title, style = MaterialTheme.typography.titleMedium)
            Text(note.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          IconButton(onClick = { toolsViewModel.deleteNote(note.id) }, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Note", tint = drxColors.silver)
          }
        }
      }
    }
  }
}

@Composable
fun StatisticsScreen(
  sessions: List<GameSessionEntity>,
  onBack: (() -> Unit)? = null,
  globalStats: GlobalGamingStatistics? = null,
  onOpenGameStats: (String) -> Unit = {},
  onGrantUsageAccess: () -> Unit = {},
  onClearHistory: () -> Unit = {},
) {
  if (onBack != null) {
    BackHandler(onBack = onBack)
  }
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val isIndo = strings.navSettings == "Setelan"

  var isMonthlyChart by remember { mutableStateOf(false) }
  var timeFilter by remember { mutableStateOf(StatsTimeFilter.ALL) }
  var selectedGameFilterPkg by remember { mutableStateOf<String?>(null) }

  val totalMs = globalStats?.totalPlaytimeMs ?: sessions.sumOf { it.durationMs }
  val totalSessionCount = globalStats?.totalSessionsCount ?: sessions.size
  val avgMs =
    globalStats?.averageSessionDurationMs
      ?: if (sessions.isNotEmpty()) sessions.sumOf { it.durationMs } / sessions.size else 0L
  val mostPlayedName =
    globalStats?.mostPlayedByTimeName
      ?: sessions.groupBy { it.gameName }.maxByOrNull { (_, l) -> l.sumOf { it.durationMs } }?.key
      ?: "-"

  val filteredSessions =
    remember(sessions, timeFilter, selectedGameFilterPkg) {
      val now = System.currentTimeMillis()
      val todayStart = GameStatsCollector.startOfDayMs(now)
      val sevenStart = GameStatsCollector.startOfDaysAgoMs(now, 6)
      val thirtyStart = GameStatsCollector.startOfDaysAgoMs(now, 29)
      sessions.filter { s ->
        val matchesPkg = selectedGameFilterPkg == null || s.gamePackageName == selectedGameFilterPkg
        val matchesTime =
          when (timeFilter) {
            StatsTimeFilter.TODAY -> s.startedAt >= todayStart
            StatsTimeFilter.DAYS_7 -> s.startedAt >= sevenStart
            StatsTimeFilter.DAYS_30 -> s.startedAt >= thirtyStart
            StatsTimeFilter.ALL -> true
          }
        matchesPkg && matchesTime
      }.sortedByDescending { it.startedAt }
    }

  val fallbackWeeklyBuckets =
    remember(sessions, isIndo) {
      val now = System.currentTimeMillis()
      val idLocale = Locale.Builder().setLanguage("id").setRegion("ID").build()
      val dayFmt = java.text.SimpleDateFormat("EEE", if (isIndo) idLocale else Locale.ENGLISH)
      (6 downTo 0).map { ago ->
        val dStart = GameStatsCollector.startOfDaysAgoMs(now, ago)
        val dEnd = dStart + 86_400_000L
        val sum = sessions.filter { it.startedAt in dStart until dEnd }.sumOf { it.durationMs }
        com.example.data.model.DayActivityBucket(dayFmt.format(java.util.Date(dStart)), dStart, sum)
      }
    }

  val chartBuckets =
    if (globalStats != null) {
      if (isMonthlyChart) globalStats.monthlyChart else globalStats.weeklyChart
    } else {
      fallbackWeeklyBuckets
    }

  LazyColumn(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("global_stats_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.globalStatsTitle,
        subtitle = "${globalStats?.installedTrackedGames ?: 0} ${strings.totalTrackedGamesLabel}",
        onBack = onBack,
        trailing = {
          DrxInfoTooltipIcon(
            title = strings.globalStatsTitle,
            description =
              if (isIndo) {
                "Menampilkan total waktu bermain dari sesi DRX ditambah estimasi UsageStatsManager Android tanpa penjumlahan ganda harian. Tap game mana saja untuk melihat Game Detail Statistics."
              } else {
                "Combines tracked DRX sessions with Android UsageStatsManager estimates without daily double-counting. Tap any game to open Game Detail Statistics."
              },
          )
        },
      )
    }

    if (globalStats != null && !globalStats.hasUsagePermission) {
      item {
        DrxGlassCard(accentColor = drxColors.warningAmber, highlighted = true) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, contentDescription = null, tint = drxColors.warningAmber, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
              text = strings.usageAccessNotice,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f),
            )
          }
          Spacer(Modifier.height(10.dp))
          DrxPrimaryButton(
            label = strings.grantUsagePermissionCta,
            accentColor = drxColors.warningAmber,
            onClick = onGrantUsageAccess,
            modifier = Modifier.fillMaxWidth().testTag("grant_usage_global_stats_btn"),
          )
        }
      }
    }

    // Overview KPI Grid
    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DrxGlassCard(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.totalPlaytime, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (globalStats?.usesEstimatedSystemUsage == true) {
              Spacer(Modifier.width(6.dp))
              DrxStatusPill(text = strings.estimatedBadge, color = drxColors.warningAmber)
            }
          }
          Spacer(Modifier.height(4.dp))
          Text(
            text = TimeAndSizeFormatters.formatDurationReadable(totalMs, isIndo),
            style = MaterialTheme.typography.headlineSmall,
            color = drxColors.neonCyan,
          )
        }
        DrxGlassCard(modifier = Modifier.weight(1f)) {
          Text(strings.totalTrackedGamesLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(Modifier.height(4.dp))
          Text(
            text = "${globalStats?.installedTrackedGames ?: 0} / ${globalStats?.totalTrackedGames ?: 0}",
            style = MaterialTheme.typography.headlineSmall,
            color = drxColors.electricBlue,
          )
        }
      }
    }

    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DrxGlassCard(
          onClick = { globalStats?.mostPlayedByTimePkg?.let(onOpenGameStats) },
          modifier = Modifier.weight(1f),
        ) {
          Text(strings.mostPlayedGame, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(mostPlayedName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
          if (globalStats != null && globalStats.mostPlayedByTimeMs > 0L) {
            Text(
              TimeAndSizeFormatters.formatDurationReadable(globalStats.mostPlayedByTimeMs, isIndo),
              style = DrxTypographyTokens.numericReadout,
              color = drxColors.neonCyan,
            )
          }
        }
        DrxGlassCard(
          onClick = { globalStats?.mostOpenedPkg?.let(onOpenGameStats) },
          modifier = Modifier.weight(1f),
        ) {
          Text(strings.mostOpenedGameLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(globalStats?.mostOpenedGameName ?: "-", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(
            "${globalStats?.mostOpenedCount ?: 0}x",
            style = DrxTypographyTokens.numericReadout,
            color = drxColors.electricBlue,
          )
        }
      }
    }

    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DrxGlassCard(modifier = Modifier.weight(1f)) {
          Text(strings.totalSessions, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(Modifier.height(2.dp))
          Text("$totalSessionCount", style = DrxTypographyTokens.statNumberCompact, color = drxColors.silver)
        }
        DrxGlassCard(modifier = Modifier.weight(1f)) {
          Text(strings.avgSession, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(Modifier.height(2.dp))
          Text(TimeAndSizeFormatters.formatDurationReadable(avgMs, isIndo), style = DrxTypographyTokens.statNumberCompact, color = drxColors.silver)
        }
      }
    }

    // Last Played Game Highlight Card
    if (globalStats?.lastPlayedGameName != null && globalStats.lastPlayedPkg != null) {
      item {
        DrxGlassCard(
          accentColor = drxColors.neonCyan,
          onClick = { onOpenGameStats(globalStats.lastPlayedPkg) },
          modifier = Modifier.testTag("global_last_played_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(strings.lastPlayedGameLabel, style = MaterialTheme.typography.labelMedium, color = drxColors.neonCyan)
              Text(globalStats.lastPlayedGameName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
              Text(
                text = TimeAndSizeFormatters.formatDateTimeWithZone(globalStats.lastPlayedAtMs, isIndo),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Icon(Icons.Default.BarChart, contentDescription = strings.viewGameStatsBtn, tint = drxColors.neonCyan)
          }
        }
      }
    }

    // Weekly / Monthly Activity Chart
    item {
      DrxGlassCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          DrxSectionHeader(
            title = if (isMonthlyChart) strings.monthlyChartLabel else strings.weeklyChartLabel,
            modifier = Modifier.weight(1f),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DrxFilterPill(
              label = strings.filter7Days,
              selected = !isMonthlyChart,
              onClick = { isMonthlyChart = false },
            )
            DrxFilterPill(
              label = strings.filter30Days,
              selected = isMonthlyChart,
              onClick = { isMonthlyChart = true },
            )
          }
        }
        Spacer(Modifier.height(12.dp))
        DrxActivityBarChart(buckets = chartBuckets)
      }
    }

    // Ranked Games (Tap to view detailed Game Statistics)
    if (globalStats != null && globalStats.rankedGames.isNotEmpty()) {
      item {
        DrxSectionHeader(title = strings.myGamesSection)
      }
      items(globalStats.rankedGames, key = { "rank_${it.packageName}" }) { rank ->
        DrxGlassCard(
          onClick = { onOpenGameStats(rank.packageName) },
          contentPadding = PaddingValues(14.dp),
          modifier = Modifier.testTag("stats_game_row_${rank.packageName}"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(rank.appName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
              val lastTxt =
                if (rank.lastPlayedAtMs != null) TimeAndSizeFormatters.formatDateTimeWithZone(rank.lastPlayedAtMs, isIndo)
                else strings.lastPlayedNever
              Text(
                text = "${rank.sessionOrLaunchCount}x • $lastTxt",
                style = DrxTypographyTokens.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
              Text(
                text = TimeAndSizeFormatters.formatDurationReadable(rank.totalPlaytimeMs, isIndo),
                style = DrxTypographyTokens.numericReadout,
                color = drxColors.neonCyan,
              )
              if (rank.isEstimated) {
                Text(strings.estimatedBadge, style = MaterialTheme.typography.labelSmall, color = drxColors.warningAmber)
              }
            }
          }
        }
      }
    }

    // Filterable Session History + Clear History
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          DrxSectionHeader(title = strings.sessionHistoryTitle, modifier = Modifier.weight(1f))
          if (sessions.isNotEmpty()) {
            DrxSecondaryButton(
              label = strings.clearHistoryBtn,
              icon = Icons.Default.DeleteSweep,
              onClick = onClearHistory,
              modifier = Modifier.height(38.dp).testTag("clear_all_sessions_btn"),
            )
          }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          val filters =
            listOf(
              StatsTimeFilter.TODAY to strings.filterToday,
              StatsTimeFilter.DAYS_7 to strings.filter7Days,
              StatsTimeFilter.DAYS_30 to strings.filter30Days,
              StatsTimeFilter.ALL to strings.filterAll,
            )
          items(filters) { (filter, label) ->
            DrxFilterPill(
              label = label,
              selected = timeFilter == filter,
              onClick = { timeFilter = filter },
            )
          }
        }

        val uniqueGamesInSessions = remember(sessions) { sessions.map { it.gamePackageName to it.gameName }.distinctBy { it.first } }
        if (uniqueGamesInSessions.size > 1) {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
              DrxFilterPill(
                label = strings.filterAll,
                selected = selectedGameFilterPkg == null,
                onClick = { selectedGameFilterPkg = null },
              )
            }
            items(uniqueGamesInSessions) { (pkg, name) ->
              DrxFilterPill(
                label = name,
                selected = selectedGameFilterPkg == pkg,
                onClick = { selectedGameFilterPkg = pkg },
              )
            }
          }
        }
      }
    }

    if (filteredSessions.isEmpty()) {
      item {
        DrxGlassCard {
          Text(strings.noSessionsYet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    } else {
      items(filteredSessions, key = { it.id }) { s ->
        val dateText = TimeAndSizeFormatters.formatDate(s.startedAt, isIndo)
        val startClock = TimeAndSizeFormatters.formatClockShort(s.startedAt)
        val endClock = TimeAndSizeFormatters.formatClockShort(s.endedAt)
        val durText = TimeAndSizeFormatters.formatDurationReadable(s.durationMs, isIndo)
        DrxGlassCard(onClick = { onOpenGameStats(s.gamePackageName) }) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("$dateText • ${s.gameName}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(
                "$startClock–$endClock • Peak ${String.format(Locale.US, "%.1f", s.peakTempC)}°C",
                style = DrxTypographyTokens.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Text(
              durText,
              style = DrxTypographyTokens.numericReadout,
              color = drxColors.neonCyan,
            )
          }
        }
      }
    }
  }
}
