package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.DrxThemePreset
import com.example.data.model.GamingProfile
import com.example.data.model.HudDisplayMode
import com.example.data.model.LanguageMode
import com.example.data.model.LaunchAnimationQuality
import com.example.data.model.ProfileConfig
import com.example.data.model.QuickOverlayProfile
import com.example.data.model.SoundPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.drxDataStore: DataStore<Preferences> by preferencesDataStore(name = "drx_prefs")

data class DrxUserPrefs(
  val darkMode: Boolean = true,
  val language: LanguageMode = LanguageMode.INDONESIAN,
  val animationsEnabled: Boolean = true,
  val launchAnimationEnabled: Boolean = true,
  val launchAnimationQuality: LaunchAnimationQuality = LaunchAnimationQuality.HIGH,
  val launchSoundEnabled: Boolean = true,
  val gamesGridView: Boolean = false,
  val activeProfile: GamingProfile = GamingProfile.BALANCED,
  val customBrightnessPercent: Int = 80,
  val customAutoBrightness: Boolean = false,
  val customEnableDnd: Boolean = true,
  val customOrientationMode: Int = 0,
  val customImmersiveMode: Boolean = true,
  val customSoundPreset: SoundPreset = SoundPreset.FPS_FOOTSTEPS,
  val customAutoFloatingSidebar: Boolean = true,
  // Floating Edge Control Center prefs
  val floatingSidebarEnabled: Boolean = false,
  val floatingAlpha: Float = 0.90f,
  val floatingScale: Float = 1.0f,
  val floatingPosX: Int = 0,
  val floatingPosY: Int = 260,
  val floatingEdgeSide: Int = 0, // 0 = Left (Swipe L->R), 1 = Right (Swipe R->L)
  val floatingHandleThicknessDp: Int = 12, // 8..22 dp
  val floatingHandleAlpha: Float = 0.72f, // 0.25..1.0
  val floatingAutoHide: Boolean = true,
  val floatingAutoCloseOnExit: Boolean = true,
  val floatingHapticsEnabled: Boolean = true,
  val floatingSwipeSensitivity: Float = 1.0f,
  val floatingPinnedApps: Set<String> = emptySet(),
  val miniWindowX: Int = 60,
  val miniWindowY: Int = 180,
  val miniWindowW: Int = 280,
  val miniWindowH: Int = 310,
  val miniWindowAlpha: Float = 0.94f,
  // Custom saved Voice Studio preset
  val customVoicePitch: Float = 1.0f,
  val customVoiceBass: Float = 1.0f,
  val customVoiceTreble: Float = 1.0f,
  val customVoiceEcho: Float = 0f,
  val customVoiceReverb: Float = 0f,
  val customVoiceRobot: Float = 0f,
  // Notifications & boost tracking
  val boostNotificationsEnabled: Boolean = true,
  val timerNotificationsEnabled: Boolean = true,
  val totalBoostCount: Int = 0,
  val lastBoostTimestamp: Long = 0L,
  val gamingModeActive: Boolean = false,
  val supporterAvatarPath: String = "",
  // Update log & Smart Announcement preferences
  val readUpdateVersions: Set<String> = emptySet(),
  val announcementsEnabled: Boolean = true,
  val announcementMinPerDay: Int = 1,
  val announcementMaxPerDay: Int = 3,
  val quietHoursStartHour: Int = 22,
  val quietHoursEndHour: Int = 7,
  val announcementSoundEnabled: Boolean = true,
  val announcementVibrationEnabled: Boolean = true,
  val enabledAnnouncementCategories: Set<String> = emptySet(),
  val recentAnnouncementIds: List<String> = emptyList(),
  val lastAnnouncementCategoryId: String = "",
  val scheduledSlotsSummary: String = "",
  val lastScheduleDateKey: String = "",
  // Customization Center & Theme System
  val themePreset: DrxThemePreset = DrxThemePreset.DRX_DEFAULT,
  val customAccentArgb: Long = 0L,
  val customWallpaperPath: String = "",
  val uiSoundsEnabled: Boolean = false,
  val hapticFeedbackEnabled: Boolean = true,
  // Optional Manual Social Stats fallback
  val manualYtSubscribers: String = "",
  val manualTtFollowers: String = "",
  // Live HUD Overlay & Diagnostics / Storage prefs
  val hudOverlayEnabled: Boolean = false,
  val hudMode: HudDisplayMode = HudDisplayMode.STANDARD,
  val hudOpacity: Float = 0.90f,
  val hudAutoHide: Boolean = false,
  val hudPosX: Int = 24,
  val hudPosY: Int = 140,
  val safRootTreeUri: String = "",
  val lagRamWarningMb: Int = 0, // 0 = auto device-calibrated
  val lagThermalWarningC: Float = 40.0f,
  val lagPingWarningMs: Int = 90,
  val lagStorageWarningMb: Int = 1024,
  // v1.9.0 Game Notification Viewer & Privacy Controls
  val gameNotificationViewerEnabled: Boolean = true,
  val excludedNotificationPackages: Set<String> = emptySet(),
  val customNotificationGamePackages: Set<String> = emptySet(),
  // v1.9.0 Smart Update Center & Skip Policy
  val skippedUpdateVersionCode: Int = 0,
  val updateSkipCount: Int = 0,
  val lastOfferedUpdateVersionCode: Int = 0,
  val customUpdateManifestUrl: String = "",
  val cachedUpdateManifestJson: String = "",
  val lastUpdateCheckAtMs: Long = 0L,
  val lastNotifiedUpdateVersionCode: Int = 0,
  // v1.10.0 Quick Overlay Profile, Media Hub Favorites & Recorder Countdown
  val quickOverlayProfile: QuickOverlayProfile = QuickOverlayProfile.BALANCED,
  val favoriteMediaUris: Set<String> = emptySet(),
  val recorderCountdownSec: Int = 3,
  // v1.25.5 Display Intelligence, Pre-Game Check & Gaming Focus Mode
  val preferredDisplayModeId: Int = 0,
  val preGameCheckBeforeLaunch: Boolean = true,
  val focusSessionTargetMinutes: Int = 45,
  val lastFocusSummaryText: String = "",
) {
  fun resolveProfileConfig(profile: GamingProfile = activeProfile): ProfileConfig =
    when (profile) {
      GamingProfile.PERFORMANCE ->
        ProfileConfig(
          profile = GamingProfile.PERFORMANCE,
          brightnessPercent = 90,
          autoBrightness = false,
          enableDnd = true,
          orientationMode = 0,
          immersiveMode = true,
          soundPreset = SoundPreset.FPS_FOOTSTEPS,
          autoFloatingSidebar = true,
        )
      GamingProfile.BALANCED ->
        ProfileConfig(
          profile = GamingProfile.BALANCED,
          brightnessPercent = 65,
          autoBrightness = true,
          enableDnd = false,
          orientationMode = 0,
          immersiveMode = true,
          soundPreset = SoundPreset.BALANCED,
          autoFloatingSidebar = true,
        )
      GamingProfile.BATTERY_SAVER ->
        ProfileConfig(
          profile = GamingProfile.BATTERY_SAVER,
          brightnessPercent = 40,
          autoBrightness = false,
          enableDnd = false,
          orientationMode = 0,
          immersiveMode = false,
          soundPreset = SoundPreset.BALANCED,
          autoFloatingSidebar = false,
        )
      GamingProfile.CUSTOM ->
        ProfileConfig(
          profile = GamingProfile.CUSTOM,
          brightnessPercent = customBrightnessPercent,
          autoBrightness = customAutoBrightness,
          enableDnd = customEnableDnd,
          orientationMode = customOrientationMode,
          immersiveMode = customImmersiveMode,
          soundPreset = customSoundPreset,
          autoFloatingSidebar = customAutoFloatingSidebar,
        )
    }
}

class DrxPreferences(private val context: Context) {
  private object Keys {
    val DARK_MODE = booleanPreferencesKey("dark_mode")
    val LANGUAGE = stringPreferencesKey("language")
    val ANIMATIONS = booleanPreferencesKey("animations_enabled")
    val LAUNCH_ANIM = booleanPreferencesKey("launch_anim_enabled")
    val LAUNCH_QUALITY = stringPreferencesKey("launch_anim_quality")
    val LAUNCH_SOUND = booleanPreferencesKey("launch_sound_enabled")
    val GAMES_GRID_VIEW = booleanPreferencesKey("games_grid_view")
    val ACTIVE_PROFILE = stringPreferencesKey("active_profile")
    val CUSTOM_BRIGHTNESS = intPreferencesKey("custom_brightness")
    val CUSTOM_AUTO_BRIGHTNESS = booleanPreferencesKey("custom_auto_brightness")
    val CUSTOM_DND = booleanPreferencesKey("custom_dnd")
    val CUSTOM_ORIENTATION = intPreferencesKey("custom_orientation")
    val CUSTOM_IMMERSIVE = booleanPreferencesKey("custom_immersive")
    val CUSTOM_SOUND_PRESET = stringPreferencesKey("custom_sound_preset")
    val CUSTOM_AUTO_SIDEBAR = booleanPreferencesKey("custom_auto_sidebar")
    val FLOATING_ENABLED = booleanPreferencesKey("floating_enabled")
    val FLOATING_ALPHA = floatPreferencesKey("floating_alpha")
    val FLOATING_SCALE = floatPreferencesKey("floating_scale")
    val FLOATING_X = intPreferencesKey("floating_x")
    val FLOATING_Y = intPreferencesKey("floating_y")
    val FLOATING_EDGE_SIDE = intPreferencesKey("floating_edge_side")
    val FLOATING_HANDLE_THICKNESS = intPreferencesKey("floating_handle_thickness")
    val FLOATING_HANDLE_ALPHA = floatPreferencesKey("floating_handle_alpha")
    val FLOATING_AUTO_HIDE = booleanPreferencesKey("floating_auto_hide")
    val FLOATING_AUTO_CLOSE_ON_EXIT = booleanPreferencesKey("floating_auto_close_on_exit")
    val FLOATING_HAPTICS = booleanPreferencesKey("floating_haptics")
    val FLOATING_SWIPE_SENS = floatPreferencesKey("floating_swipe_sens")
    val FLOATING_PINNED_APPS = stringSetPreferencesKey("floating_pinned_apps")
    val MINI_WIN_X = intPreferencesKey("mini_win_x")
    val MINI_WIN_Y = intPreferencesKey("mini_win_y")
    val MINI_WIN_W = intPreferencesKey("mini_win_w")
    val MINI_WIN_H = intPreferencesKey("mini_win_h")
    val MINI_WIN_ALPHA = floatPreferencesKey("mini_win_alpha")
    val CUSTOM_VOICE_PITCH = floatPreferencesKey("custom_voice_pitch")
    val CUSTOM_VOICE_BASS = floatPreferencesKey("custom_voice_bass")
    val CUSTOM_VOICE_TREBLE = floatPreferencesKey("custom_voice_treble")
    val CUSTOM_VOICE_ECHO = floatPreferencesKey("custom_voice_echo")
    val CUSTOM_VOICE_REVERB = floatPreferencesKey("custom_voice_reverb")
    val CUSTOM_VOICE_ROBOT = floatPreferencesKey("custom_voice_robot")
    val BOOST_NOTIFS = booleanPreferencesKey("boost_notifs")
    val TIMER_NOTIFS = booleanPreferencesKey("timer_notifs")
    val TOTAL_BOOSTS = intPreferencesKey("total_boosts")
    val LAST_BOOST_TS = longPreferencesKey("last_boost_ts")
    val GAMING_MODE_ACTIVE = booleanPreferencesKey("gaming_mode_active")
    val SUPPORTER_AVATAR_PATH = stringPreferencesKey("supporter_avatar_path")
    val READ_UPDATE_VERSIONS = stringSetPreferencesKey("read_update_versions")
    val ANNOUNCEMENTS_ENABLED = booleanPreferencesKey("announcements_enabled")
    val ANNOUNCEMENT_MIN_PER_DAY = intPreferencesKey("announcement_min_per_day")
    val ANNOUNCEMENT_MAX_PER_DAY = intPreferencesKey("announcement_max_per_day")
    val QUIET_START_HOUR = intPreferencesKey("quiet_hours_start_hour")
    val QUIET_END_HOUR = intPreferencesKey("quiet_hours_end_hour")
    val ANNOUNCEMENT_SOUND = booleanPreferencesKey("announcement_sound")
    val ANNOUNCEMENT_VIBRATION = booleanPreferencesKey("announcement_vibration")
    val ENABLED_ANNOUNCEMENT_CATS = stringSetPreferencesKey("enabled_announcement_cats")
    val RECENT_ANNOUNCEMENT_IDS = stringPreferencesKey("recent_announcement_ids")
    val LAST_ANNOUNCEMENT_CAT = stringPreferencesKey("last_announcement_cat")
    val SCHEDULED_SLOTS_SUMMARY = stringPreferencesKey("scheduled_slots_summary")
    val LAST_SCHEDULE_DATE_KEY = stringPreferencesKey("last_schedule_date_key")
    val THEME_PRESET = stringPreferencesKey("theme_preset")
    val CUSTOM_ACCENT_ARGB = longPreferencesKey("custom_accent_argb")
    val CUSTOM_WALLPAPER_PATH = stringPreferencesKey("custom_wallpaper_path")
    val UI_SOUNDS_ENABLED = booleanPreferencesKey("ui_sounds_enabled")
    val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
    val MANUAL_YT_SUBS = stringPreferencesKey("manual_yt_subs")
    val MANUAL_TT_FOLLOWERS = stringPreferencesKey("manual_tt_followers")
    val HUD_OVERLAY_ENABLED = booleanPreferencesKey("hud_overlay_enabled")
    val HUD_MODE = stringPreferencesKey("hud_mode")
    val HUD_OPACITY = floatPreferencesKey("hud_opacity")
    val HUD_AUTO_HIDE = booleanPreferencesKey("hud_auto_hide")
    val HUD_POS_X = intPreferencesKey("hud_pos_x")
    val HUD_POS_Y = intPreferencesKey("hud_pos_y")
    val SAF_ROOT_TREE_URI = stringPreferencesKey("saf_root_tree_uri")
    val LAG_RAM_WARNING_MB = intPreferencesKey("lag_ram_warning_mb")
    val LAG_THERMAL_WARNING_C = floatPreferencesKey("lag_thermal_warning_c")
    val LAG_PING_WARNING_MS = intPreferencesKey("lag_ping_warning_ms")
    val LAG_STORAGE_WARNING_MB = intPreferencesKey("lag_storage_warning_mb")
    val GAME_NOTIF_VIEWER_ENABLED = booleanPreferencesKey("game_notif_viewer_enabled")
    val EXCLUDED_NOTIF_PACKAGES = stringSetPreferencesKey("excluded_notif_packages")
    val CUSTOM_NOTIF_GAME_PACKAGES = stringSetPreferencesKey("custom_notif_game_packages")
    val SKIPPED_UPDATE_VERSION_CODE = intPreferencesKey("skipped_update_version_code")
    val UPDATE_SKIP_COUNT = intPreferencesKey("update_skip_count")
    val LAST_OFFERED_UPDATE_VC = intPreferencesKey("last_offered_update_vc")
    val CUSTOM_UPDATE_MANIFEST_URL = stringPreferencesKey("custom_update_manifest_url")
    val CACHED_UPDATE_MANIFEST_JSON = stringPreferencesKey("cached_update_manifest_json")
    val LAST_UPDATE_CHECK_AT_MS = longPreferencesKey("last_update_check_at_ms")
    val LAST_NOTIFIED_UPDATE_VC = intPreferencesKey("last_notified_update_vc")
    val QUICK_OVERLAY_PROFILE = stringPreferencesKey("quick_overlay_profile")
    val FAVORITE_MEDIA_URIS = stringSetPreferencesKey("favorite_media_uris")
    val RECORDER_COUNTDOWN_SEC = intPreferencesKey("recorder_countdown_sec")
    val PREFERRED_DISPLAY_MODE_ID = intPreferencesKey("preferred_display_mode_id")
    val PRE_GAME_CHECK_BEFORE_LAUNCH = booleanPreferencesKey("pre_game_check_before_launch")
    val FOCUS_SESSION_TARGET_MIN = intPreferencesKey("focus_session_target_min")
    val LAST_FOCUS_SUMMARY_TEXT = stringPreferencesKey("last_focus_summary_text")
  }

  val prefsFlow: Flow<DrxUserPrefs> =
    context.drxDataStore.data.map { p ->
      DrxUserPrefs(
        darkMode = p[Keys.DARK_MODE] ?: true,
        language =
          LanguageMode.entries.find { it.code == p[Keys.LANGUAGE] } ?: LanguageMode.INDONESIAN,
        animationsEnabled = p[Keys.ANIMATIONS] ?: true,
        launchAnimationEnabled = p[Keys.LAUNCH_ANIM] ?: true,
        launchAnimationQuality =
          LaunchAnimationQuality.entries.find { it.id == p[Keys.LAUNCH_QUALITY] }
            ?: LaunchAnimationQuality.HIGH,
        launchSoundEnabled = p[Keys.LAUNCH_SOUND] ?: true,
        gamesGridView = p[Keys.GAMES_GRID_VIEW] ?: false,
        activeProfile =
          GamingProfile.entries.find { it.id == p[Keys.ACTIVE_PROFILE] }
            ?: GamingProfile.BALANCED,
        customBrightnessPercent = p[Keys.CUSTOM_BRIGHTNESS] ?: 80,
        customAutoBrightness = p[Keys.CUSTOM_AUTO_BRIGHTNESS] ?: false,
        customEnableDnd = p[Keys.CUSTOM_DND] ?: true,
        customOrientationMode = p[Keys.CUSTOM_ORIENTATION] ?: 0,
        customImmersiveMode = p[Keys.CUSTOM_IMMERSIVE] ?: true,
        customSoundPreset =
          SoundPreset.entries.find { it.id == p[Keys.CUSTOM_SOUND_PRESET] }
            ?: SoundPreset.FPS_FOOTSTEPS,
        customAutoFloatingSidebar = p[Keys.CUSTOM_AUTO_SIDEBAR] ?: true,
        floatingSidebarEnabled = p[Keys.FLOATING_ENABLED] ?: false,
        floatingAlpha = p[Keys.FLOATING_ALPHA] ?: 0.90f,
        floatingScale = p[Keys.FLOATING_SCALE] ?: 1.0f,
        floatingPosX = p[Keys.FLOATING_X] ?: 0,
        floatingPosY = p[Keys.FLOATING_Y] ?: 260,
        floatingEdgeSide = p[Keys.FLOATING_EDGE_SIDE] ?: 0,
        floatingHandleThicknessDp = p[Keys.FLOATING_HANDLE_THICKNESS] ?: 12,
        floatingHandleAlpha = p[Keys.FLOATING_HANDLE_ALPHA] ?: 0.72f,
        floatingAutoHide = p[Keys.FLOATING_AUTO_HIDE] ?: true,
        floatingAutoCloseOnExit = p[Keys.FLOATING_AUTO_CLOSE_ON_EXIT] ?: true,
        floatingHapticsEnabled = p[Keys.FLOATING_HAPTICS] ?: true,
        floatingSwipeSensitivity = p[Keys.FLOATING_SWIPE_SENS] ?: 1.0f,
        floatingPinnedApps = p[Keys.FLOATING_PINNED_APPS] ?: emptySet(),
        miniWindowX = p[Keys.MINI_WIN_X] ?: 60,
        miniWindowY = p[Keys.MINI_WIN_Y] ?: 180,
        miniWindowW = p[Keys.MINI_WIN_W] ?: 280,
        miniWindowH = p[Keys.MINI_WIN_H] ?: 310,
        miniWindowAlpha = p[Keys.MINI_WIN_ALPHA] ?: 0.94f,
        customVoicePitch = p[Keys.CUSTOM_VOICE_PITCH] ?: 1.0f,
        customVoiceBass = p[Keys.CUSTOM_VOICE_BASS] ?: 1.0f,
        customVoiceTreble = p[Keys.CUSTOM_VOICE_TREBLE] ?: 1.0f,
        customVoiceEcho = p[Keys.CUSTOM_VOICE_ECHO] ?: 0f,
        customVoiceReverb = p[Keys.CUSTOM_VOICE_REVERB] ?: 0f,
        customVoiceRobot = p[Keys.CUSTOM_VOICE_ROBOT] ?: 0f,
        boostNotificationsEnabled = p[Keys.BOOST_NOTIFS] ?: true,
        timerNotificationsEnabled = p[Keys.TIMER_NOTIFS] ?: true,
        totalBoostCount = p[Keys.TOTAL_BOOSTS] ?: 0,
        lastBoostTimestamp = p[Keys.LAST_BOOST_TS] ?: 0L,
        gamingModeActive = p[Keys.GAMING_MODE_ACTIVE] ?: false,
        supporterAvatarPath = p[Keys.SUPPORTER_AVATAR_PATH] ?: "",
        readUpdateVersions = p[Keys.READ_UPDATE_VERSIONS] ?: emptySet(),
        announcementsEnabled = p[Keys.ANNOUNCEMENTS_ENABLED] ?: true,
        announcementMinPerDay = (p[Keys.ANNOUNCEMENT_MIN_PER_DAY] ?: 1).coerceIn(1, 3),
        announcementMaxPerDay = (p[Keys.ANNOUNCEMENT_MAX_PER_DAY] ?: 3).coerceIn(1, 3),
        quietHoursStartHour = (p[Keys.QUIET_START_HOUR] ?: 22).coerceIn(0, 23),
        quietHoursEndHour = (p[Keys.QUIET_END_HOUR] ?: 7).coerceIn(0, 23),
        announcementSoundEnabled = p[Keys.ANNOUNCEMENT_SOUND] ?: true,
        announcementVibrationEnabled = p[Keys.ANNOUNCEMENT_VIBRATION] ?: true,
        enabledAnnouncementCategories = p[Keys.ENABLED_ANNOUNCEMENT_CATS] ?: emptySet(),
        recentAnnouncementIds =
          (p[Keys.RECENT_ANNOUNCEMENT_IDS] ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() },
        lastAnnouncementCategoryId = p[Keys.LAST_ANNOUNCEMENT_CAT] ?: "",
        scheduledSlotsSummary = p[Keys.SCHEDULED_SLOTS_SUMMARY] ?: "",
        lastScheduleDateKey = p[Keys.LAST_SCHEDULE_DATE_KEY] ?: "",
        themePreset =
          DrxThemePreset.entries.find { it.id == p[Keys.THEME_PRESET] } ?: DrxThemePreset.DRX_DEFAULT,
        customAccentArgb = p[Keys.CUSTOM_ACCENT_ARGB] ?: 0L,
        customWallpaperPath = p[Keys.CUSTOM_WALLPAPER_PATH] ?: "",
        uiSoundsEnabled = p[Keys.UI_SOUNDS_ENABLED] ?: false,
        hapticFeedbackEnabled = p[Keys.HAPTIC_FEEDBACK_ENABLED] ?: true,
        manualYtSubscribers = p[Keys.MANUAL_YT_SUBS] ?: "",
        manualTtFollowers = p[Keys.MANUAL_TT_FOLLOWERS] ?: "",
        hudOverlayEnabled = p[Keys.HUD_OVERLAY_ENABLED] ?: false,
        hudMode = HudDisplayMode.entries.find { it.id == p[Keys.HUD_MODE] } ?: HudDisplayMode.STANDARD,
        hudOpacity = (p[Keys.HUD_OPACITY] ?: 0.90f).coerceIn(0.35f, 1.0f),
        hudAutoHide = p[Keys.HUD_AUTO_HIDE] ?: false,
        hudPosX = p[Keys.HUD_POS_X] ?: 24,
        hudPosY = p[Keys.HUD_POS_Y] ?: 140,
        safRootTreeUri = p[Keys.SAF_ROOT_TREE_URI] ?: "",
        lagRamWarningMb = (p[Keys.LAG_RAM_WARNING_MB] ?: 0).coerceIn(0, 8192),
        lagThermalWarningC = (p[Keys.LAG_THERMAL_WARNING_C] ?: 40.0f).coerceIn(34f, 52f),
        lagPingWarningMs = (p[Keys.LAG_PING_WARNING_MS] ?: 90).coerceIn(20, 400),
        lagStorageWarningMb = (p[Keys.LAG_STORAGE_WARNING_MB] ?: 1024).coerceIn(200, 16384),
        gameNotificationViewerEnabled = p[Keys.GAME_NOTIF_VIEWER_ENABLED] ?: true,
        excludedNotificationPackages = p[Keys.EXCLUDED_NOTIF_PACKAGES] ?: emptySet(),
        customNotificationGamePackages = p[Keys.CUSTOM_NOTIF_GAME_PACKAGES] ?: emptySet(),
        skippedUpdateVersionCode = p[Keys.SKIPPED_UPDATE_VERSION_CODE] ?: 0,
        updateSkipCount = (p[Keys.UPDATE_SKIP_COUNT] ?: 0).coerceAtLeast(0),
        lastOfferedUpdateVersionCode = p[Keys.LAST_OFFERED_UPDATE_VC] ?: 0,
        customUpdateManifestUrl = p[Keys.CUSTOM_UPDATE_MANIFEST_URL] ?: "",
        cachedUpdateManifestJson = p[Keys.CACHED_UPDATE_MANIFEST_JSON] ?: "",
        lastUpdateCheckAtMs = p[Keys.LAST_UPDATE_CHECK_AT_MS] ?: 0L,
        lastNotifiedUpdateVersionCode = p[Keys.LAST_NOTIFIED_UPDATE_VC] ?: 0,
        quickOverlayProfile = QuickOverlayProfile.fromId(p[Keys.QUICK_OVERLAY_PROFILE] ?: ""),
        favoriteMediaUris = p[Keys.FAVORITE_MEDIA_URIS] ?: emptySet(),
        recorderCountdownSec = (p[Keys.RECORDER_COUNTDOWN_SEC] ?: 3).coerceIn(0, 10),
        preferredDisplayModeId = (p[Keys.PREFERRED_DISPLAY_MODE_ID] ?: 0).coerceAtLeast(0),
        preGameCheckBeforeLaunch = p[Keys.PRE_GAME_CHECK_BEFORE_LAUNCH] ?: true,
        focusSessionTargetMinutes = (p[Keys.FOCUS_SESSION_TARGET_MIN] ?: 45).coerceIn(5, 240),
        lastFocusSummaryText = p[Keys.LAST_FOCUS_SUMMARY_TEXT] ?: "",
      )
    }

  suspend fun setDarkMode(enabled: Boolean) {
    context.drxDataStore.edit { it[Keys.DARK_MODE] = enabled }
  }

  suspend fun setLanguage(mode: LanguageMode) {
    context.drxDataStore.edit { it[Keys.LANGUAGE] = mode.code }
  }

  suspend fun setAnimationsEnabled(enabled: Boolean) {
    context.drxDataStore.edit { it[Keys.ANIMATIONS] = enabled }
  }

  suspend fun setLaunchAnimationEnabled(enabled: Boolean) {
    context.drxDataStore.edit { it[Keys.LAUNCH_ANIM] = enabled }
  }

  suspend fun setLaunchAnimationQuality(quality: LaunchAnimationQuality) {
    context.drxDataStore.edit { it[Keys.LAUNCH_QUALITY] = quality.id }
  }

  suspend fun setLaunchSoundEnabled(enabled: Boolean) {
    context.drxDataStore.edit { it[Keys.LAUNCH_SOUND] = enabled }
  }

  suspend fun setGamesGridView(grid: Boolean) {
    context.drxDataStore.edit { it[Keys.GAMES_GRID_VIEW] = grid }
  }

  suspend fun setActiveProfile(profile: GamingProfile) {
    context.drxDataStore.edit { it[Keys.ACTIVE_PROFILE] = profile.id }
  }

  suspend fun updateCustomConfig(config: ProfileConfig) {
    context.drxDataStore.edit {
      it[Keys.CUSTOM_BRIGHTNESS] = config.brightnessPercent
      it[Keys.CUSTOM_AUTO_BRIGHTNESS] = config.autoBrightness
      it[Keys.CUSTOM_DND] = config.enableDnd
      it[Keys.CUSTOM_ORIENTATION] = config.orientationMode
      it[Keys.CUSTOM_IMMERSIVE] = config.immersiveMode
      it[Keys.CUSTOM_SOUND_PRESET] = config.soundPreset.id
      it[Keys.CUSTOM_AUTO_SIDEBAR] = config.autoFloatingSidebar
    }
  }

  suspend fun setFloatingEnabled(enabled: Boolean) {
    context.drxDataStore.edit { it[Keys.FLOATING_ENABLED] = enabled }
  }

  suspend fun setFloatingAutoCloseOnExit(enabled: Boolean) {
    context.drxDataStore.edit { it[Keys.FLOATING_AUTO_CLOSE_ON_EXIT] = enabled }
  }

  suspend fun setFloatingAppearance(alpha: Float, scale: Float) {
    context.drxDataStore.edit {
      it[Keys.FLOATING_ALPHA] = alpha.coerceIn(0.35f, 1.0f)
      it[Keys.FLOATING_SCALE] = scale.coerceIn(0.8f, 1.3f)
    }
  }

  suspend fun setFloatingPosition(x: Int, y: Int) {
    context.drxDataStore.edit {
      it[Keys.FLOATING_X] = x
      it[Keys.FLOATING_Y] = y
    }
  }

  suspend fun setFloatingEdgeConfig(
    edgeSide: Int,
    handleThicknessDp: Int,
    handleAlpha: Float,
    autoHide: Boolean,
    haptics: Boolean,
    swipeSensitivity: Float,
  ) {
    context.drxDataStore.edit {
      it[Keys.FLOATING_EDGE_SIDE] = edgeSide.coerceIn(0, 1)
      it[Keys.FLOATING_HANDLE_THICKNESS] = handleThicknessDp.coerceIn(8, 24)
      it[Keys.FLOATING_HANDLE_ALPHA] = handleAlpha.coerceIn(0.25f, 1.0f)
      it[Keys.FLOATING_AUTO_HIDE] = autoHide
      it[Keys.FLOATING_HAPTICS] = haptics
      it[Keys.FLOATING_SWIPE_SENS] = swipeSensitivity.coerceIn(0.6f, 1.5f)
    }
  }

  suspend fun togglePinnedFloatingApp(packageName: String) {
    context.drxDataStore.edit { prefs ->
      val current = prefs[Keys.FLOATING_PINNED_APPS]?.toMutableSet() ?: mutableSetOf()
      if (!current.add(packageName)) {
        current.remove(packageName)
      }
      prefs[Keys.FLOATING_PINNED_APPS] = current
    }
  }

  suspend fun setMiniWindowBounds(x: Int, y: Int, w: Int, h: Int, alpha: Float) {
    context.drxDataStore.edit {
      it[Keys.MINI_WIN_X] = x
      it[Keys.MINI_WIN_Y] = y
      it[Keys.MINI_WIN_W] = w.coerceIn(220, 420)
      it[Keys.MINI_WIN_H] = h.coerceIn(210, 480)
      it[Keys.MINI_WIN_ALPHA] = alpha.coerceIn(0.45f, 1.0f)
    }
  }

  suspend fun saveCustomVoicePreset(
    pitch: Float,
    bass: Float,
    treble: Float,
    echo: Float,
    reverb: Float,
    robot: Float,
  ) {
    context.drxDataStore.edit {
      it[Keys.CUSTOM_VOICE_PITCH] = pitch
      it[Keys.CUSTOM_VOICE_BASS] = bass
      it[Keys.CUSTOM_VOICE_TREBLE] = treble
      it[Keys.CUSTOM_VOICE_ECHO] = echo
      it[Keys.CUSTOM_VOICE_REVERB] = reverb
      it[Keys.CUSTOM_VOICE_ROBOT] = robot
    }
  }

  suspend fun setBoostNotifications(enabled: Boolean) {
    context.drxDataStore.edit { it[Keys.BOOST_NOTIFS] = enabled }
  }

  suspend fun setTimerNotifications(enabled: Boolean) {
    context.drxDataStore.edit { it[Keys.TIMER_NOTIFS] = enabled }
  }

  suspend fun recordBoost(timestampMs: Long) {
    context.drxDataStore.edit {
      it[Keys.TOTAL_BOOSTS] = (it[Keys.TOTAL_BOOSTS] ?: 0) + 1
      it[Keys.LAST_BOOST_TS] = timestampMs
      it[Keys.GAMING_MODE_ACTIVE] = true
    }
  }

  suspend fun setGamingModeActive(active: Boolean) {
    context.drxDataStore.edit { it[Keys.GAMING_MODE_ACTIVE] = active }
  }

  suspend fun setSupporterAvatarPath(path: String) {
    context.drxDataStore.edit { it[Keys.SUPPORTER_AVATAR_PATH] = path }
  }

  suspend fun markUpdateVersionRead(version: String) {
    context.drxDataStore.edit { prefs ->
      val current = prefs[Keys.READ_UPDATE_VERSIONS]?.toMutableSet() ?: mutableSetOf()
      current.add(version)
      prefs[Keys.READ_UPDATE_VERSIONS] = current
    }
  }

  suspend fun markAllUpdatesRead(versions: List<String>) {
    context.drxDataStore.edit { prefs ->
      val current = prefs[Keys.READ_UPDATE_VERSIONS]?.toMutableSet() ?: mutableSetOf()
      current.addAll(versions)
      prefs[Keys.READ_UPDATE_VERSIONS] = current
    }
  }

  suspend fun setAnnouncementsEnabled(enabled: Boolean) {
    context.drxDataStore.edit { it[Keys.ANNOUNCEMENTS_ENABLED] = enabled }
  }

  suspend fun setAnnouncementFrequency(minPerDay: Int, maxPerDay: Int) {
    val safeMin = minPerDay.coerceIn(1, 3)
    val safeMax = maxPerDay.coerceIn(safeMin, 3)
    context.drxDataStore.edit {
      it[Keys.ANNOUNCEMENT_MIN_PER_DAY] = safeMin
      it[Keys.ANNOUNCEMENT_MAX_PER_DAY] = safeMax
    }
  }

  suspend fun setQuietHours(startHour: Int, endHour: Int) {
    context.drxDataStore.edit {
      it[Keys.QUIET_START_HOUR] = startHour.coerceIn(0, 23)
      it[Keys.QUIET_END_HOUR] = endHour.coerceIn(0, 23)
    }
  }

  suspend fun setAnnouncementSound(enabled: Boolean) {
    context.drxDataStore.edit { it[Keys.ANNOUNCEMENT_SOUND] = enabled }
  }

  suspend fun setAnnouncementVibration(enabled: Boolean) {
    context.drxDataStore.edit { it[Keys.ANNOUNCEMENT_VIBRATION] = enabled }
  }

  suspend fun toggleAnnouncementCategory(categoryId: String) {
    context.drxDataStore.edit { prefs ->
      val allIds =
        setOf(
          "gaming_tips",
          "feature_discovery",
          "news",
          "motivation",
          "premium_vibes",
          "community",
          "fun",
        )
      val current = (prefs[Keys.ENABLED_ANNOUNCEMENT_CATS]?.takeIf { it.isNotEmpty() } ?: allIds).toMutableSet()
      if (current.contains(categoryId)) {
        if (current.size > 1) {
          current.remove(categoryId)
        }
      } else {
        current.add(categoryId)
      }
      prefs[Keys.ENABLED_ANNOUNCEMENT_CATS] = current
    }
  }

  suspend fun recordSentAnnouncement(messageId: String, categoryId: String) {
    context.drxDataStore.edit { prefs ->
      val list =
        (prefs[Keys.RECENT_ANNOUNCEMENT_IDS] ?: "")
          .split(",")
          .map { it.trim() }
          .filter { it.isNotEmpty() }
          .toMutableList()
      list.remove(messageId)
      list.add(0, messageId)
      prefs[Keys.RECENT_ANNOUNCEMENT_IDS] = list.take(40).joinToString(",")
      prefs[Keys.LAST_ANNOUNCEMENT_CAT] = categoryId
    }
  }

  suspend fun resetAnnouncementRotation() {
    context.drxDataStore.edit { prefs ->
      prefs[Keys.RECENT_ANNOUNCEMENT_IDS] = ""
      prefs[Keys.LAST_ANNOUNCEMENT_CAT] = ""
    }
  }

  suspend fun saveScheduleMetadata(dateKey: String, summary: String) {
    context.drxDataStore.edit {
      it[Keys.LAST_SCHEDULE_DATE_KEY] = dateKey
      it[Keys.SCHEDULED_SLOTS_SUMMARY] = summary
    }
  }

  suspend fun setThemePreset(preset: DrxThemePreset) {
    context.drxDataStore.edit { it[Keys.THEME_PRESET] = preset.id }
  }

  suspend fun setCustomAccentArgb(argb: Long) {
    context.drxDataStore.edit { it[Keys.CUSTOM_ACCENT_ARGB] = argb }
  }

  suspend fun setCustomWallpaperPath(path: String) {
    context.drxDataStore.edit { it[Keys.CUSTOM_WALLPAPER_PATH] = path }
  }

  suspend fun setUiSoundsEnabled(enabled: Boolean) {
    context.drxDataStore.edit { it[Keys.UI_SOUNDS_ENABLED] = enabled }
  }

  suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
    context.drxDataStore.edit { it[Keys.HAPTIC_FEEDBACK_ENABLED] = enabled }
  }

  suspend fun setManualSocialCounters(ytSubs: String, ttFollowers: String) {
    context.drxDataStore.edit {
      it[Keys.MANUAL_YT_SUBS] = ytSubs.trim()
      it[Keys.MANUAL_TT_FOLLOWERS] = ttFollowers.trim()
    }
  }

  suspend fun setHudOverlayConfig(
    enabled: Boolean? = null,
    mode: HudDisplayMode? = null,
    opacity: Float? = null,
    autoHide: Boolean? = null,
    posX: Int? = null,
    posY: Int? = null,
  ) {
    context.drxDataStore.edit { p ->
      enabled?.let { p[Keys.HUD_OVERLAY_ENABLED] = it }
      mode?.let { p[Keys.HUD_MODE] = it.id }
      opacity?.let { p[Keys.HUD_OPACITY] = it.coerceIn(0.35f, 1.0f) }
      autoHide?.let { p[Keys.HUD_AUTO_HIDE] = it }
      posX?.let { p[Keys.HUD_POS_X] = it }
      posY?.let { p[Keys.HUD_POS_Y] = it }
    }
  }

  suspend fun setSafRootTreeUri(uriString: String) {
    context.drxDataStore.edit { it[Keys.SAF_ROOT_TREE_URI] = uriString }
  }

  suspend fun setLagThresholds(
    ramWarningMb: Int,
    thermalWarningC: Float,
    pingWarningMs: Int,
    storageWarningMb: Int,
  ) {
    context.drxDataStore.edit {
      it[Keys.LAG_RAM_WARNING_MB] = ramWarningMb.coerceIn(0, 8192)
      it[Keys.LAG_THERMAL_WARNING_C] = thermalWarningC.coerceIn(34f, 52f)
      it[Keys.LAG_PING_WARNING_MS] = pingWarningMs.coerceIn(20, 400)
      it[Keys.LAG_STORAGE_WARNING_MB] = storageWarningMb.coerceIn(200, 16384)
    }
  }

  suspend fun setGameNotificationViewerEnabled(enabled: Boolean) {
    context.drxDataStore.edit { it[Keys.GAME_NOTIF_VIEWER_ENABLED] = enabled }
  }

  suspend fun setPackageExcludedFromNotifications(packageName: String, excluded: Boolean) {
    context.drxDataStore.edit { p ->
      val current = p[Keys.EXCLUDED_NOTIF_PACKAGES]?.toMutableSet() ?: mutableSetOf()
      val custom = p[Keys.CUSTOM_NOTIF_GAME_PACKAGES]?.toMutableSet() ?: mutableSetOf()
      if (excluded) {
        current.add(packageName)
        custom.remove(packageName)
      } else {
        current.remove(packageName)
      }
      p[Keys.EXCLUDED_NOTIF_PACKAGES] = current
      p[Keys.CUSTOM_NOTIF_GAME_PACKAGES] = custom
    }
  }

  suspend fun setCustomNotificationGamePackage(packageName: String, included: Boolean) {
    context.drxDataStore.edit { p ->
      val custom = p[Keys.CUSTOM_NOTIF_GAME_PACKAGES]?.toMutableSet() ?: mutableSetOf()
      val excluded = p[Keys.EXCLUDED_NOTIF_PACKAGES]?.toMutableSet() ?: mutableSetOf()
      if (included) {
        custom.add(packageName)
        excluded.remove(packageName)
      } else {
        custom.remove(packageName)
      }
      p[Keys.CUSTOM_NOTIF_GAME_PACKAGES] = custom
      p[Keys.EXCLUDED_NOTIF_PACKAGES] = excluded
    }
  }

  suspend fun recordUpdateSkip(targetVersionCode: Int) {
    context.drxDataStore.edit { p ->
      val prevTarget = p[Keys.SKIPPED_UPDATE_VERSION_CODE] ?: 0
      val nextCount =
        if (prevTarget == targetVersionCode) {
          (p[Keys.UPDATE_SKIP_COUNT] ?: 0) + 1
        } else {
          1
        }
      p[Keys.SKIPPED_UPDATE_VERSION_CODE] = targetVersionCode
      p[Keys.LAST_OFFERED_UPDATE_VC] = targetVersionCode
      p[Keys.UPDATE_SKIP_COUNT] = nextCount
    }
  }

  suspend fun syncOfferedUpdateVersion(targetVersionCode: Int, currentVersionCode: Int) {
    context.drxDataStore.edit { p ->
      if (currentVersionCode >= targetVersionCode || targetVersionCode <= 0) {
        p[Keys.SKIPPED_UPDATE_VERSION_CODE] = 0
        p[Keys.UPDATE_SKIP_COUNT] = 0
        p[Keys.LAST_OFFERED_UPDATE_VC] = 0
      } else {
        val lastOffered = p[Keys.LAST_OFFERED_UPDATE_VC] ?: 0
        if (lastOffered != targetVersionCode) {
          p[Keys.SKIPPED_UPDATE_VERSION_CODE] = targetVersionCode
          p[Keys.LAST_OFFERED_UPDATE_VC] = targetVersionCode
          p[Keys.UPDATE_SKIP_COUNT] = 0
        }
      }
    }
  }

  suspend fun resetUpdateSkipPolicy() {
    context.drxDataStore.edit { p ->
      p[Keys.SKIPPED_UPDATE_VERSION_CODE] = 0
      p[Keys.UPDATE_SKIP_COUNT] = 0
      p[Keys.LAST_OFFERED_UPDATE_VC] = 0
    }
  }

  suspend fun setCustomUpdateManifestUrl(url: String) {
    context.drxDataStore.edit { it[Keys.CUSTOM_UPDATE_MANIFEST_URL] = url.trim() }
  }

  suspend fun saveCachedUpdateManifest(rawJson: String, checkedAtMs: Long) {
    context.drxDataStore.edit { p ->
      p[Keys.CACHED_UPDATE_MANIFEST_JSON] = rawJson
      p[Keys.LAST_UPDATE_CHECK_AT_MS] = checkedAtMs
    }
  }

  suspend fun touchUpdateCheckTimestamp(checkedAtMs: Long) {
    context.drxDataStore.edit { it[Keys.LAST_UPDATE_CHECK_AT_MS] = checkedAtMs }
  }

  suspend fun markUpdateVersionNotified(versionCode: Int) {
    context.drxDataStore.edit { it[Keys.LAST_NOTIFIED_UPDATE_VC] = versionCode }
  }

  suspend fun setQuickOverlayProfile(profile: QuickOverlayProfile) {
    context.drxDataStore.edit { p ->
      p[Keys.QUICK_OVERLAY_PROFILE] = profile.id
      p[Keys.HUD_MODE] = profile.hudMode.id
      p[Keys.HUD_OPACITY] = profile.hudOpacity
      p[Keys.HUD_AUTO_HIDE] = profile.compactHud
      p[Keys.ANIMATIONS] = profile.preferAnimations
      p[Keys.BOOST_NOTIFS] = profile.alertBannersEnabled
    }
  }

  suspend fun toggleFavoriteMediaUri(uriString: String): Boolean {
    var added = false
    context.drxDataStore.edit { p ->
      val current = p[Keys.FAVORITE_MEDIA_URIS]?.toMutableSet() ?: mutableSetOf()
      if (uriString in current) {
        current.remove(uriString)
        added = false
      } else {
        current.add(uriString)
        added = true
      }
      p[Keys.FAVORITE_MEDIA_URIS] = current
    }
    return added
  }

  suspend fun setRecorderCountdownSec(seconds: Int) {
    context.drxDataStore.edit { it[Keys.RECORDER_COUNTDOWN_SEC] = seconds.coerceIn(0, 10) }
  }

  suspend fun setPreferredDisplayModeId(modeId: Int) {
    context.drxDataStore.edit { it[Keys.PREFERRED_DISPLAY_MODE_ID] = modeId.coerceAtLeast(0) }
  }

  suspend fun setPreGameCheckBeforeLaunch(enabled: Boolean) {
    context.drxDataStore.edit { it[Keys.PRE_GAME_CHECK_BEFORE_LAUNCH] = enabled }
  }

  suspend fun setFocusSessionTargetMinutes(minutes: Int) {
    context.drxDataStore.edit { it[Keys.FOCUS_SESSION_TARGET_MIN] = minutes.coerceIn(5, 240) }
  }

  suspend fun setLastFocusSummaryText(summary: String) {
    context.drxDataStore.edit { it[Keys.LAST_FOCUS_SUMMARY_TEXT] = summary }
  }

  suspend fun resetAll() {
    context.drxDataStore.edit { it.clear() }
  }
}
