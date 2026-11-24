package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DrxEdition
import com.example.data.model.DrxThemePreset
import com.example.data.model.LanguageMode
import com.example.data.prefs.DrxUserPrefs
import com.example.i18n.LocalStrings
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxHairlineDivider
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxSectionHeader
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxMainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomizationScreen(
  prefs: DrxUserPrefs,
  viewModel: DrxMainViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val context = LocalContext.current
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  val wallpaperPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        viewModel.updateCustomWallpaperFromUri(uri)
      }
    }

  val accentOptions: List<Pair<String, Long>> =
    listOf(
      "Preset Default" to 0L,
      "Electric Blue" to 0xFF087BFFL,
      "Neon Cyan" to 0xFF13D9FFL,
      "Emerald" to 0xFF55D69AL,
      "Indigo" to 0xFF7B8BFFL,
      "Coral Red" to 0xFFF07878L,
      "Amber Gold" to 0xFFF5BB65L,
      "Rose" to 0xFFF4607AL,
    )

  LazyColumn(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("customization_screen_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.customizationCenterTitle,
        subtitle = strings.customizationCenterSub,
        onBack = onBack,
      )
    }

    // 7 DRX Theme Presets
    item {
      DrxGlassCard(accentColor = drxColors.electricBlue, highlighted = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Palette, contentDescription = null, tint = drxColors.neonCyan, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(8.dp))
          DrxSectionHeader(title = strings.themePresetsTitle)
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          DrxThemePreset.entries.forEach { preset ->
            val selected = prefs.themePreset == preset
            val primaryColor = Color(preset.primaryHex.toInt())
            val secondaryColor = Color(preset.secondaryHex.toInt())
            val bgColor = Color(preset.bgHex.toInt())
            Surface(
              onClick = { viewModel.setThemePreset(preset) },
              shape = RoundedCornerShape(16.dp),
              color = if (selected) primaryColor.copy(alpha = 0.14f) else drxColors.secondaryBg,
              border = BorderStroke(1.dp, if (selected) secondaryColor.copy(alpha = 0.65f) else drxColors.glassBorder),
              modifier = Modifier.fillMaxWidth().testTag("theme_preset_${preset.id}"),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(
                  modifier =
                    Modifier
                      .size(26.dp)
                      .clip(CircleShape)
                      .background(bgColor)
                      .border(2.5.dp, primaryColor, CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Box(
                  modifier = Modifier.size(14.dp).clip(CircleShape).background(secondaryColor)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                  text = preset.displayName,
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.weight(1f),
                )
                if (selected) {
                  Icon(Icons.Default.Check, contentDescription = "Active", tint = secondaryColor, modifier = Modifier.size(20.dp))
                }
              }
            }
          }
        }
      }
    }

    // Custom Accent Color Picker
    item {
      DrxGlassCard {
        DrxSectionHeader(title = strings.customAccentTitle)
        Spacer(Modifier.height(10.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          accentOptions.forEach { (label, argb) ->
            val selected = prefs.customAccentArgb == argb
            val swatch = if (argb == 0L) Color(prefs.themePreset.primaryHex.toInt()) else Color(argb.toInt())
            Surface(
              onClick = { viewModel.setCustomAccentArgb(argb) },
              shape = RoundedCornerShape(50),
              color = if (selected) swatch.copy(alpha = 0.22f) else drxColors.elevatedSurface,
              border = BorderStroke(1.dp, if (selected) swatch else drxColors.glassBorder),
              modifier = Modifier.height(40.dp).testTag("accent_option_${label.lowercase().replace(' ', '_')}"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(swatch))
                Spacer(Modifier.width(6.dp))
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelMedium,
                  color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }

    // Custom Background Wallpaper (Zero-permission Photo Picker)
    item {
      DrxGlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Wallpaper, contentDescription = null, tint = drxColors.neonCyan, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(8.dp))
          DrxSectionHeader(title = strings.wallpaperTitle)
        }
        Spacer(Modifier.height(8.dp))
        Text(
          text =
            if (prefs.customWallpaperPath.isNotBlank()) {
              if (isIndo) "Wallpaper kustom aktif dengan lapisan gelap agar teks tetap tajam."
              else "Custom background active with adaptive dark scrim for optimal legibility."
            } else {
              if (isIndo) "Gunakan latar tema atau pilih gambar pribadi via Android Photo Picker."
              else "Using cockpit preset background, or select custom wallpaper via Android Photo Picker."
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          DrxPrimaryButton(
            label = strings.chooseWallpaperBtn,
            icon = Icons.Default.Wallpaper,
            onClick = {
              try {
                wallpaperPicker.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              } catch (_: Throwable) {
                Toast.makeText(
                  context,
                  if (isIndo) "Pemilih gambar tidak tersedia di perangkat ini." else "Image picker is unavailable on this device.",
                  Toast.LENGTH_SHORT,
                ).show()
              }
            },
            modifier = Modifier.weight(1f).testTag("choose_wallpaper_btn"),
          )
          DrxSecondaryButton(
            label = strings.resetWallpaperBtn,
            enabled = prefs.customWallpaperPath.isNotBlank(),
            onClick = { viewModel.clearCustomWallpaper() },
            modifier = Modifier.weight(1f).testTag("reset_wallpaper_btn"),
          )
        }
      }
    }

    // Interaction Sound & Haptics
    item {
      DrxGlassCard {
        SettingToggleRow(
          label = strings.uiSoundsLabel,
          checked = prefs.uiSoundsEnabled,
          onCheckedChange = { viewModel.setUiSoundsEnabled(it) },
          modifier = Modifier.testTag("ui_sounds_switch"),
        )
        Spacer(Modifier.height(4.dp))
        DrxHairlineDivider()
        Spacer(Modifier.height(4.dp))
        SettingToggleRow(
          label = strings.hapticFeedbackLabel,
          checked = prefs.hapticFeedbackEnabled,
          onCheckedChange = { viewModel.setHapticFeedbackEnabled(it) },
          modifier = Modifier.testTag("haptic_feedback_switch"),
        )
      }
    }

    // DRX Editions Roadmap (Honest FREE active + PRO/CREATOR Coming Soon)
    item {
      DrxGlassCard(accentColor = drxColors.electricBlue) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = drxColors.electricBlue, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(8.dp))
          DrxSectionHeader(title = strings.drxEditionsTitle)
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          DrxEdition.entries.forEach { edition ->
            val isFree = edition == DrxEdition.FREE
            val desc =
              when (edition) {
                DrxEdition.FREE ->
                  if (isIndo) "Semua fitur inti, Game Statistics, Floating Control Center, Voice Studio & 7 Tema aktif gratis."
                  else "Full core booster, Game Statistics, Floating Control Center, Voice Studio & 7 Themes unlocked."
                DrxEdition.PRO ->
                  if (isIndo) "Preset automasi per-game lanjutan & sinkronisasi profil cloud."
                  else "Advanced per-game automation profiles & encrypted cloud backup."
                DrxEdition.CREATOR ->
                  if (isIndo) "Overlay streaming statistik FPS & toolkit kreator DerreX."
                  else "Live creator stream telemetry overlay & DerreX creator studio pack."
              }
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(16.dp))
                  .background(drxColors.secondaryBg)
                  .border(
                    1.dp,
                    if (isFree) drxColors.optimalGreen.copy(alpha = 0.45f) else drxColors.glassBorder,
                    RoundedCornerShape(16.dp),
                  )
                  .padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = edition.badge,
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                  color = if (isFree) drxColors.neonCyan else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                  text = desc,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Spacer(Modifier.width(8.dp))
              DrxStatusPill(
                text = if (isFree) "ACTIVE" else strings.comingSoonBadge,
                color = if (isFree) drxColors.optimalGreen else drxColors.electricBlue,
              )
            }
          }
        }
      }
    }
  }
}
