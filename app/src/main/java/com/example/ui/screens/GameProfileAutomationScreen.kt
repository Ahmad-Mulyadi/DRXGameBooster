package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.GameProfileAutomationEntity
import com.example.data.model.GamingProfile
import com.example.data.model.LanguageMode
import com.example.i18n.LocalStrings
import com.example.system.safeRoundToInt
import com.example.ui.theme.DrxCustomModal
import com.example.ui.theme.DrxCustomSwitch
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxMainViewModel

@Composable
fun GameProfileAutomationScreen(
  viewModel: DrxMainViewModel,
  onBack: () -> Unit,
  initialPackageName: String? = null,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val activity = LocalActivity.current

  val prefs by viewModel.userPrefs.collectAsStateWithLifecycle()
  val automations by viewModel.gameProfileAutomations.collectAsStateWithLifecycle()
  val games by viewModel.games.collectAsStateWithLifecycle()
  val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
  val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
  val banner by viewModel.lastAutoAppliedProfileBanner.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  val candidateApps =
    remember(games, installedApps) {
      val map = linkedMapOf<String, String>()
      games.forEach { map[it.packageName] = it.appName }
      installedApps.forEach { if (!map.containsKey(it.packageName)) map[it.packageName] = it.appName }
      map.entries.map { it.key to it.value }.sortedBy { it.second.lowercase() }
    }

  var editingEntity by remember(initialPackageName, automations, candidateApps) {
    val existing =
      initialPackageName?.let { pkg -> automations.find { it.packageName == pkg } }
        ?: initialPackageName?.let { pkg ->
          val name = candidateApps.find { it.first == pkg }?.second ?: pkg.substringAfterLast('.')
          GameProfileAutomationEntity(packageName = pkg, appName = name)
        }
    mutableStateOf(existing)
  }
  var duplicateSource by remember { mutableStateOf<GameProfileAutomationEntity?>(null) }

  LazyColumn(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("automation_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.profileAutomationTitle,
        subtitle = strings.profileAutomationSub,
        onBack = onBack,
        trailing = {
          DrxPrimaryButton(
            label = if (isIndo) "Tambah" else "New",
            icon = Icons.Default.Add,
            onClick = {
              val firstUnconfigured =
                candidateApps.firstOrNull { (pkg, _) -> automations.none { it.packageName == pkg } }
                  ?: candidateApps.firstOrNull()
              editingEntity =
                GameProfileAutomationEntity(
                  packageName = firstUnconfigured?.first ?: "",
                  appName = firstUnconfigured?.second ?: "",
                )
            },
            modifier = Modifier.height(42.dp).testTag("automation_add_button"),
          )
        },
      )
    }

    if (!banner.isNullOrBlank()) {
      item {
        DrxGlassCard(accentColor = drxColors.optimalGreen, highlighted = true) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = banner.orEmpty(),
              style = MaterialTheme.typography.bodyMedium,
              color = drxColors.optimalGreen,
              modifier = Modifier.weight(1f),
            )
            DrxSecondaryButton(
              label = strings.closeLabel,
              onClick = { viewModel.dismissAutoAppliedBanner() },
              modifier = Modifier.height(36.dp),
            )
          }
        }
      }
    }

    item {
      DrxGlassCard(accentColor = drxColors.electricBlue, cornerRadius = 18.dp) {
        Text(
          text =
            if (isIndo) {
              "Profil otomatis diterapkan saat meluncurkan game dari DRX atau saat game terdeteksi di latar depan (jika Usage Access aktif). Tidak mengubah file internal game."
            } else {
              "Game profiles auto-apply when launching from DRX or when foregrounded (with Usage Access). Never alters game files or anti-cheat."
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    if (automations.isEmpty()) {
      item {
        DrxGlassCard {
          Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Icon(
              Icons.Default.AutoAwesome,
              contentDescription = null,
              tint = drxColors.neonCyan,
              modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
              text = if (isIndo) "Belum ada profil game otomatis" else "No Game Profile Automations Yet",
              style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text =
                if (isIndo) "Buat aturan profil HUD, sidebar, & mode layar khusus tiap game."
                else "Create per-game presets for profile, HUD layout, floating sidebar & session timer.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      items(automations, key = { it.packageName }) { item ->
        val prof = GamingProfile.entries.find { it.id == item.gamingProfileId } ?: GamingProfile.BALANCED
        DrxGlassCard(
          accentColor = if (item.enabled) drxColors.electricBlue else drxColors.glassBorder,
          highlighted = item.enabled,
          cornerRadius = 22.dp,
          modifier = Modifier.testTag("automation_card_${item.packageName}"),
          onClick = { editingEntity = item },
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
                  .background(drxColors.elevatedSurface),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                Icons.Default.SportsEsports,
                contentDescription = item.appName,
                tint = drxColors.neonCyan,
                modifier = Modifier.size(20.dp),
              )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = item.appName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = "${strings.profileName(prof)} • HUD: ${item.hudLayoutId.uppercase()}",
                style = DrxTypographyTokens.caption,
                color = drxColors.neonCyan,
              )
            }
            DrxCustomSwitch(
              checked = item.enabled,
              onCheckedChange = { checked ->
                viewModel.upsertGameProfileAutomation(item.copy(enabled = checked))
              },
            )
          }

          Spacer(Modifier.height(10.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (item.fpsMonitorEnabled) DrxStatusPill("HUD FPS", drxColors.neonCyan)
            if (item.floatingSidebarEnabled) DrxStatusPill("Sidebar", drxColors.electricBlue)
            if (item.sessionTimerEnabled) DrxStatusPill("Timer", drxColors.optimalGreen)
          }

          Spacer(Modifier.height(12.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrxPrimaryButton(
              label = if (isIndo) "Terapkan" else "Apply",
              icon = Icons.Default.PlayArrow,
              onClick = { viewModel.applyGameProfileAutomation(item, activity) },
              modifier = Modifier.weight(1f).height(42.dp),
            )
            DrxSecondaryButton(
              label = if (isIndo) "Duplikat" else "Duplicate",
              icon = Icons.Default.ContentCopy,
              onClick = { duplicateSource = item },
              modifier = Modifier.weight(1f).height(42.dp),
            )
            IconButton(onClick = { viewModel.deleteGameProfileAutomation(item.packageName) }) {
              Icon(
                Icons.Default.DeleteOutline,
                contentDescription = strings.removeGame,
                tint = drxColors.alertCrimson,
              )
            }
          }
        }
      }
    }
  }

  // Add / Edit Modal
  val draftInit = editingEntity
  if (draftInit != null) {
    var selectedPkg by remember(draftInit) { mutableStateOf(draftInit.packageName) }
    var selectedAppName by remember(draftInit) { mutableStateOf(draftInit.appName) }
    var enabled by remember(draftInit) { mutableStateOf(draftInit.enabled) }
    var autoApplyFg by remember(draftInit) { mutableStateOf(draftInit.autoApplyOnForeground) }
    var profileId by remember(draftInit) { mutableStateOf(draftInit.gamingProfileId) }
    var hudLayoutId by remember(draftInit) { mutableStateOf(draftInit.hudLayoutId) }
    var fpsEnabled by remember(draftInit) { mutableStateOf(draftInit.fpsMonitorEnabled) }
    var sidebarEnabled by remember(draftInit) { mutableStateOf(draftInit.floatingSidebarEnabled) }
    var netEnabled by remember(draftInit) { mutableStateOf(draftInit.networkMonitorEnabled) }
    var quickToolsEnabled by remember(draftInit) { mutableStateOf(draftInit.quickToolsEnabled) }
    var recorderEnabled by remember(draftInit) { mutableStateOf(draftInit.recorderShortcutEnabled) }
    var screenshotEnabled by remember(draftInit) { mutableStateOf(draftInit.screenshotShortcutEnabled) }
    var timerEnabled by remember(draftInit) { mutableStateOf(draftInit.sessionTimerEnabled) }
    var displayModeId by remember(draftInit) { mutableIntStateOf(draftInit.performanceDisplayModeId) }

    DrxCustomModal(
      title = if (isIndo) "Atur Otomatisasi Game" else "Configure Game Profile",
      onDismiss = { editingEntity = null },
    ) {
      Column(
        modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (candidateApps.isNotEmpty()) {
          Text(
            text = if (isIndo) "Pilih Game / Aplikasi" else "Target Game / App",
            style = MaterialTheme.typography.labelLarge,
            color = drxColors.neonCyan,
          )
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(candidateApps, key = { it.first }) { (pkg, name) ->
              DrxFilterPill(
                label = name,
                selected = selectedPkg == pkg,
                onClick = {
                  selectedPkg = pkg
                  selectedAppName = name
                },
              )
            }
          }
        }

        Text(
          text = strings.selectProfile,
          style = MaterialTheme.typography.labelLarge,
          color = drxColors.neonCyan,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
          listOf(GamingProfile.PERFORMANCE, GamingProfile.BALANCED, GamingProfile.BATTERY_SAVER).forEach { p ->
            DrxFilterPill(
              label = strings.profileName(p),
              selected = profileId == p.id,
              onClick = { profileId = p.id },
              modifier = Modifier.weight(1f),
            )
          }
        }

        Text(
          text = if (isIndo) "Tata Letak HUD" else "HUD Layout",
          style = MaterialTheme.typography.labelLarge,
          color = drxColors.neonCyan,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
          listOf("mini" to "Mini", "standard" to "Standard", "full" to "Full").forEach { (id, label) ->
            DrxFilterPill(
              label = label,
              selected = hudLayoutId == id,
              onClick = { hudLayoutId = id },
              modifier = Modifier.weight(1f),
            )
          }
        }

        val modes = telemetry.displayInfo.supportedModes
        if (modes.isNotEmpty()) {
          Text(
            text = if (isIndo) "Mode Refresh Rate Layar" else "Display Refresh Mode",
            style = MaterialTheme.typography.labelLarge,
            color = drxColors.neonCyan,
          )
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
              DrxFilterPill(
                label = if (isIndo) "Default OS" else "OS Default",
                selected = displayModeId <= 0,
                onClick = { displayModeId = 0 },
              )
            }
            items(modes, key = { it.modeId }) { mode ->
              DrxFilterPill(
                label = "${mode.refreshRateHz.safeRoundToInt(60)} Hz",
                selected = displayModeId == mode.modeId,
                onClick = { displayModeId = mode.modeId },
              )
            }
          }
        }

        AutomationToggleRow(if (isIndo) "Aktifkan Profil Ini" else "Rule Enabled", enabled) { enabled = it }
        AutomationToggleRow(if (isIndo) "Otomatis Saat Game Foreground" else "Auto-Apply on Foreground", autoApplyFg) { autoApplyFg = it }
        AutomationToggleRow(if (isIndo) "HUD FPS Monitor" else "HUD FPS Monitor", fpsEnabled) { fpsEnabled = it }
        AutomationToggleRow(if (isIndo) "Floating Sidebar Otomatis" else "Floating Sidebar", sidebarEnabled) { sidebarEnabled = it }
        AutomationToggleRow(if (isIndo) "Monitor Jaringan" else "Network Monitor", netEnabled) { netEnabled = it }
        AutomationToggleRow(if (isIndo) "Pintasan Quick Tools" else "Quick Tools", quickToolsEnabled) { quickToolsEnabled = it }
        AutomationToggleRow(if (isIndo) "Pintasan Perekam Layar" else "Recorder Shortcut", recorderEnabled) { recorderEnabled = it }
        AutomationToggleRow(if (isIndo) "Pintasan Screenshot" else "Screenshot Shortcut", screenshotEnabled) { screenshotEnabled = it }
        AutomationToggleRow(if (isIndo) "Timer Sesi Gaming" else "Session Timer", timerEnabled) { timerEnabled = it }

        Spacer(Modifier.height(6.dp))
        DrxPrimaryButton(
          label = if (isIndo) "Simpan Otomatisasi" else "Save Automation",
          enabled = selectedPkg.isNotBlank(),
          onClick = {
            viewModel.upsertGameProfileAutomation(
              GameProfileAutomationEntity(
                packageName = selectedPkg,
                appName = selectedAppName.ifBlank { selectedPkg.substringAfterLast('.') },
                enabled = enabled,
                gamingProfileId = profileId,
                hudLayoutId = hudLayoutId,
                fpsMonitorEnabled = fpsEnabled,
                floatingSidebarEnabled = sidebarEnabled,
                networkMonitorEnabled = netEnabled,
                quickToolsEnabled = quickToolsEnabled,
                recorderShortcutEnabled = recorderEnabled,
                screenshotShortcutEnabled = screenshotEnabled,
                sessionTimerEnabled = timerEnabled,
                performanceDisplayModeId = displayModeId,
                autoApplyOnForeground = autoApplyFg,
              )
            )
            editingEntity = null
          },
          modifier = Modifier.fillMaxWidth().testTag("automation_save_button"),
        )
      }
    }
  }

  // Duplicate Target Picker Modal
  val dupSrc = duplicateSource
  if (dupSrc != null) {
    val otherApps = remember(candidateApps, dupSrc) { candidateApps.filter { it.first != dupSrc.packageName } }
    DrxCustomModal(
      title = if (isIndo) "Duplikat ke Game Lain" else "Duplicate Profile To",
      onDismiss = { duplicateSource = null },
    ) {
      if (otherApps.isEmpty()) {
        Text(
          text = if (isIndo) "Tidak ada game lain terdeteksi." else "No other detected games available.",
          style = MaterialTheme.typography.bodyMedium,
        )
      } else {
        Column(
          modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          otherApps.forEach { (pkg, name) ->
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = drxColors.elevatedSurface,
              border = BorderStroke(1.dp, drxColors.glassBorder),
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .clickable {
                    viewModel.duplicateGameProfileAutomation(dupSrc, pkg, name)
                    duplicateSource = null
                  },
            ) {
              Text(
                text = "$name ($pkg)",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AutomationToggleRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )
    DrxCustomSwitch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
