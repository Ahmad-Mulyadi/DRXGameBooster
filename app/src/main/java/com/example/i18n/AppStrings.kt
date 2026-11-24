package com.example.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.data.model.GamingProfile
import com.example.data.model.LanguageMode
import com.example.data.model.LaunchAnimationQuality
import com.example.data.model.VoicePreset

interface AppStrings {
  val appName: String
  val developerBy: String
  val greetingSubtitle: String
  // Navigation (5 tabs)
  val navHome: String
  val navGames: String
  val navTools: String
  val navStats: String
  val navActivity: String
  val navProfile: String
  val navVoice: String
  val navSettings: String
  // Intro & Boost Launch
  val introTagline: String
  val boostStageEngine: String
  val boostStageSession: String
  val boostStageResources: String
  val boostStageTools: String
  // Hero & Dashboard
  val heroEyebrow: String
  val heroHeadline: String
  val heroDescription: String
  val devicePerformanceTitle: String
  val deviceOverviewTitle: String
  val gamingModeActive: String
  val gamingModeStandby: String
  val boostNowBtn: String
  val boostingLabel: String
  val boostHonestSummary: String
  val boostCompletedTitle: String
  val cpuLabel: String
  val cpuRestrictedNote: String
  val ramLabel: String
  val storageLabel: String
  val batteryTempLabel: String
  val batteryLevelLabel: String
  val networkLabel: String
  val fpsDisplayLabel: String
  val activeSessionBanner: String
  val stopSaveSession: String
  val myGamesSection: String
  val viewAllBtn: String
  val quickToolsSection: String
  val recentSessionsSection: String
  val appUsageSection: String
  val grantUsageAccessHint: String
  val noGamesYet: String
  val noSessionsYet: String
  // Games & Details
  val gameLibraryTitle: String
  val recentlyPlayedTitle: String
  val gameDetailsTitle: String
  val scanGamesBtn: String
  val addGameManualBtn: String
  val searchGamesPlaceholder: String
  val favoritesOnly: String
  val playBtn: String
  val boostAndPlayBtn: String
  val removeGame: String
  val selectProfile: String
  val selectCategory: String
  val addAppsSheetTitle: String
  // Tools Hub
  val toolsHubTitle: String
  val catGamingTools: String
  val catAudioTools: String
  val catDisplayTools: String
  val catNetworkTools: String
  val catDeviceMonitor: String
  val toolBoosterTitle: String
  val toolBoosterSub: String
  val toolSidebarTitle: String
  val toolSidebarSub: String
  val toolNetworkTitle: String
  val toolNetworkSub: String
  val toolDisplayTitle: String
  val toolDisplaySub: String
  val toolSoundTitle: String
  val toolSoundSub: String
  val toolMonitorTitle: String
  val toolMonitorSub: String
  val toolProfilesTitle: String
  val toolProfilesSub: String
  val toolUtilitiesTitle: String
  val toolUtilitiesSub: String
  val toolStatsTitle: String
  val toolStatsSub: String
  val toolVoiceTitle: String
  val toolVoiceSub: String
  // Booster
  val smartBoosterTitle: String
  val androidHonestyNote: String
  val brightnessControl: String
  val autoBrightness: String
  val rotationControl: String
  val immersiveMode: String
  val dndMode: String
  val mediaVolume: String
  val ringVolume: String
  val systemShortcuts: String
  val grantPermission: String
  val permissionGranted: String
  // Floating Edge Control Center
  val sidebarControlTitle: String
  val enableSidebar: String
  val edgeSideLabel: String
  val edgeLeftLabel: String
  val edgeRightLabel: String
  val handleThicknessLabel: String
  val handleOpacityLabel: String
  val autoHideHandleLabel: String
  val hapticsLabel: String
  val swipeSensitivityLabel: String
  val panelTransparency: String
  val panelSize: String
  val resetPosition: String
  val overlayExplanation: String
  val edgeGestureConflictNote: String
  val pinnedFloatingAppsTitle: String
  val addFloatingAppBtn: String
  val autoCloseFloatingOnExitLabel: String
  val autoCloseFloatingOnExitDesc: String
  // Network
  val networkToolsTitle: String
  val startTest: String
  val testingProgress: String
  val pingLabel: String
  val jitterLabel: String
  val packetLossLabel: String
  val speedLabel: String
  val stabilityLabel: String
  val testHistory: String
  val noVpnNotice: String
  // Display & Touch
  val displayTouchTitle: String
  val resolutionLabel: String
  val refreshRateLabel: String
  val screenTimeoutLabel: String
  val touchTestTitle: String
  val touchTestHint: String
  val activePointers: String
  val touchRateHz: String
  // Sound Center
  val soundCenterTitle: String
  val audioTestBtn: String
  val stopAudioTestBtn: String
  val bassBoostLabel: String
  val virtualSurroundLabel: String
  val safetyVolumeBoost: String
  val equalizerTitle: String
  // Monitor
  val deviceMonitorTitle: String
  val thermalStatusLabel: String
  val deviceInfoTitle: String
  // Profiles
  val profilesTitle: String
  val profilePerformance: String
  val profileBalanced: String
  val profileBatterySaver: String
  val profileCustom: String
  val applyProfile: String
  // Utilities
  val utilitiesTitle: String
  val stopwatchTitle: String
  val timerTitle: String
  val notesTitle: String
  val addNote: String
  val calcTitle: String
  // Statistics
  val statisticsTitle: String
  val totalPlaytime: String
  val totalSessions: String
  val mostPlayedGame: String
  val avgSession: String
  val weeklyUsageChart: String
  // Voice Studio
  val voiceStudioTitle: String
  val voiceLimitationTitle: String
  val voiceLimitationBody: String
  val micTestRecord: String
  val stopMic: String
  val liveMonitorToggle: String
  val playProcessedClip: String
  val pitchLabel: String
  val bassLabel: String
  val trebleLabel: String
  val echoLabel: String
  val reverbLabel: String
  val noiseReductionLabel: String
  val savePresetBtn: String
  val resetPresetBtn: String
  // Settings & Launch Animation
  val settingsTitle: String
  val darkModeLabel: String
  val languageLabel: String
  val animationsLabel: String
  val launchAnimEnabledLabel: String
  val launchAnimQualityLabel: String
  val launchSoundEnabledLabel: String
  val skipAnimation: String
  val readyToPlay: String
  val launchErrorTitle: String
  val retryLaunch: String
  val closeLabel: String
  val boostNotifLabel: String
  val timerNotifLabel: String
  val permissionManagerTitle: String
  val privacySectionTitle: String
  val privacyZeroDataNote: String
  val clearHistoryBtn: String
  val resetSettingsBtn: String
  val aboutTitle: String
  val aboutSubtitle: String
  val officialDeveloperBadge: String
  val officialDeveloperIdentity: String
  val developerBioParagraph1: String
  val developerBioParagraph2: String
  val socialConnectionsTitle: String
  val supportedByTitle: String
  val officialSupporterBadge: String
  val supporterDescription: String
  val socialCountersTitle: String
  val dataUnavailableLabel: String
  val lastUpdatedLabel: String
  val refreshStatsBtn: String
  val appInfoSectionTitle: String
  val checkUpdatesBtn: String
  val privacyPolicyBtn: String
  val termsServiceBtn: String
  val openSourceLicensesBtn: String
  val supporterAvatarSettingTitle: String
  val changeSupporterAvatarBtn: String
  val resetSupporterAvatarBtn: String
  val copiedToClipboardLabel: String
  // Update Log
  val updateLogTitle: String
  val updateLogSubtitle: String
  val whatsNewBtn: String
  val markAllReadBtn: String
  val unreadBadgeLabel: String
  val newUpdateAvailableBanner: String
  // Announcement Center
  val announcementCenterTitle: String
  val announcementCenterSubtitle: String
  val enableAnnouncementsLabel: String
  val frequencyLabel: String
  val quietHoursLabel: String
  val soundLabel: String
  val vibrationLabel: String
  val categorySelectionLabel: String
  val previewNotificationBtn: String
  val announcementHistoryLabel: String
  val clearAnnouncementHistoryBtn: String
  val noAnnouncementHistoryText: String
  val workManagerScheduleNotice: String
  // v1.3.0 Game Statistics & Customization Center
  val viewGameStatsBtn: String
  val gameStatsTitle: String
  val gameInfoSectionTitle: String
  val playtimeSectionTitle: String
  val lastPlayedSectionTitle: String
  val installInfoSectionTitle: String
  val storageInfoSectionTitle: String
  val activityChartTitle: String
  val sessionHistoryTitle: String
  val installedStatus: String
  val notInstalledStatus: String
  val estimatedBadge: String
  val notAvailableLabel: String
  val grantUsagePermissionCta: String
  val usageAccessNotice: String
  val installDateNote: String
  val lastPlayedNever: String
  val sortByName: String
  val sortByPlaytime: String
  val sortByLastPlayed: String
  val filterToday: String
  val filter7Days: String
  val filter30Days: String
  val filterAll: String
  val globalStatsTitle: String
  val totalTrackedGamesLabel: String
  val mostOpenedGameLabel: String
  val lastPlayedGameLabel: String
  val weeklyChartLabel: String
  val monthlyChartLabel: String
  val customizationCenterTitle: String
  val customizationCenterSub: String
  val themePresetsTitle: String
  val customAccentTitle: String
  val wallpaperTitle: String
  val chooseWallpaperBtn: String
  val resetWallpaperBtn: String
  val uiSoundsLabel: String
  val hapticFeedbackLabel: String
  val drxEditionsTitle: String
  val comingSoonBadge: String
  val resetRotationBtn: String
  val soundboardTitle: String
  val startMonitoringBtn: String
  val stopMonitoringBtn: String
  val performanceReportBtn: String
  // v1.9.0 Game Notification Viewer, Insights, Events, Activity Center & Update Center
  val gameNotificationsTitle: String
  val gameNotificationsSub: String
  val notificationInsightsTitle: String
  val notificationInsightsSub: String
  val gameEventsTitle: String
  val gameEventsSub: String
  val activityCenterTitle: String
  val activityCenterSub: String
  val updateCenterTitle: String
  val updateCenterSub: String
  val notifPermissionMissingTitle: String
  val notifPermissionMissingBody: String
  val notifGrantAccessBtn: String
  val notifTabByDate: String
  val notifTabByGame: String
  val notifSearchPlaceholder: String
  val notifEmptyTitle: String
  val notifEmptyBody: String
  val notifViewerDisabledNotice: String
  val notifPrivacyTitle: String
  val notifPrivacyNote: String
  val updateLaterBtn: String
  val updateNowBtn: String
  val updateExitAppBtn: String
  val updateMandatoryNotice: String
  // v1.10.0 Session Intelligence, Stability Watch, Quick Profiles, Media Hub, Smart Update Modal
  val sessionIntelligenceTitle: String
  val sessionIntelligenceSub: String
  val stabilityWatchTitle: String
  val stabilityWatchSub: String
  val quickProfileTitle: String
  val quickProfileSubtitle: String
  val exportReportBtn: String
  val compareSessionsTitle: String
  val weeklySummaryTitle: String
  val personalStatsTitle: String
  val saveReportBtn: String
  val warningHistoryTitle: String
  val diagnosticDetailsTitle: String
  val mediaAllTab: String
  val mediaFavoritesTab: String
  val openFolderBtn: String
  val viewChangelogBtn: String
  val notifDateTodayLabel: String
  val notifDateYesterdayLabel: String
  val notifDate7DaysLabel: String
  // v1.25.5 Performance States & 7 Gaming Intelligence Features
  val perfStateReady: String
  val perfStateMonitoring: String
  val perfStateThermalWarning: String
  val perfStateLimitedMonitoring: String
  val perfStateDeviceIdle: String
  val sessionTimelineTitle: String
  val sessionTimelineSub: String
  val resourceReportTitle: String
  val resourceReportSub: String
  val profileAutomationTitle: String
  val profileAutomationSub: String
  val preGameCheckTitle: String
  val preGameCheckSub: String
  val playAnywayBtn: String
  val touchTestTitle: String
  val touchTestSub: String
  val displayIntelTitle: String
  val displayIntelSub: String
  val focusModeTitle: String
  val focusModeSub: String

  fun profileName(profile: GamingProfile): String =
    when (profile) {
      GamingProfile.PERFORMANCE -> profilePerformance
      GamingProfile.BALANCED -> profileBalanced
      GamingProfile.BATTERY_SAVER -> profileBatterySaver
      GamingProfile.CUSTOM -> profileCustom
    }

  fun qualityLabel(q: LaunchAnimationQuality): String =
    when (q) {
      LaunchAnimationQuality.HIGH -> "High (60 FPS)"
      LaunchAnimationQuality.BALANCED -> "Balanced"
      LaunchAnimationQuality.MINIMAL -> "Minimal"
    }

  fun voicePresetLabel(preset: VoicePreset): String =
    when (preset) {
      VoicePreset.NORMAL -> "Original Voice"
      VoicePreset.DEEP_VOICE -> "Deep Voice"
      VoicePreset.HIGH_PITCH -> "High Pitch"
      VoicePreset.ROBOT -> "Robot"
      VoicePreset.ELECTRONIC -> "Electronic"
      VoicePreset.RADIO -> "Radio"
      VoicePreset.ALIEN -> "Alien"
      VoicePreset.ECHO -> "Echo"
      VoicePreset.REVERB -> "Reverb"
      VoicePreset.CUSTOM -> "Custom Voice"
    }
}

object IndonesianStrings : AppStrings {
  override val appName = "DRX GAME TURBO PRO"
  override val developerBy = "Dikembangkan oleh DerreX"
  override val greetingSubtitle = "Siap bermain dengan performa optimal"
  override val navHome = "Home"
  override val navGames = "Games"
  override val navTools = "Tools"
  override val navStats = "Statistik"
  override val navActivity = "Aktivitas"
  override val navProfile = "Profile"
  override val navVoice = "Voice"
  override val navSettings = "Setelan"
  override val introTagline = "POWER YOUR GAME. OWN YOUR EXPERIENCE."
  override val boostStageEngine = "DRX PERFORMANCE ENGINE"
  override val boostStageSession = "Menyiapkan Sesi Gaming..."
  override val boostStageResources = "Memeriksa Sumber Daya Tersedia..."
  override val boostStageTools = "Menyiapkan Alat Gaming DRX..."
  override val heroEyebrow = "PERFORMANCE STATUS"
  override val heroHeadline = "READY FOR GAMING"
  override val heroDescription = "Optimasi sesi gaming terpadu, kontrol edge-swipe & telemetri real-time."
  override val devicePerformanceTitle = "Performa Perangkat"
  override val deviceOverviewTitle = "Ringkasan Perangkat"
  override val gamingModeActive = "GAMING AKTIF"
  override val gamingModeStandby = "SIAGA"
  override val boostNowBtn = "BOOST NOW"
  override val boostingLabel = "MENGOPTIMALKAN..."
  override val boostHonestSummary = "Membersihkan cache internal DRX & menerapkan profil performa"
  override val boostCompletedTitle = "Optimasi Selesai"
  override val cpuLabel = "CPU"
  override val cpuRestrictedNote = "OS Dibatasi"
  override val ramLabel = "RAM"
  override val storageLabel = "Storage"
  override val batteryTempLabel = "Suhu"
  override val batteryLevelLabel = "Baterai"
  override val networkLabel = "Jaringan"
  override val fpsDisplayLabel = "Layar & UI FPS"
  override val activeSessionBanner = "Sesi Aktif:"
  override val stopSaveSession = "Simpan Sesi"
  override val myGamesSection = "My Games"
  override val viewAllBtn = "Lihat Semua"
  override val quickToolsSection = "Game Tools"
  override val recentSessionsSection = "Riwayat Sesi Bermain"
  override val appUsageSection = "Statistik Penggunaan Aplikasi (24j)"
  override val grantUsageAccessHint = "Izinkan Akses Penggunaan di Setelan untuk membaca waktu pakai aplikasi."
  override val noGamesYet = "Belum ada game. Tambah atau pindai game terpasang."
  override val noSessionsYet = "Belum ada riwayat sesi bermain."
  override val gameLibraryTitle = "Game Library"
  override val recentlyPlayedTitle = "Baru Dimainkan"
  override val gameDetailsTitle = "Detail Game"
  override val scanGamesBtn = "Pindai"
  override val addGameManualBtn = "Tambah"
  override val searchGamesPlaceholder = "Cari game..."
  override val favoritesOnly = "Favorit"
  override val playBtn = "PLAY"
  override val boostAndPlayBtn = "BOOST & PLAY"
  override val removeGame = "Hapus"
  override val selectProfile = "Profil"
  override val selectCategory = "Kategori"
  override val addAppsSheetTitle = "Pilih Aplikasi Terpasang"
  override val toolsHubTitle = "Tools Center"
  override val catGamingTools = "Gaming Tools"
  override val catAudioTools = "Audio Tools"
  override val catDisplayTools = "Display Tools"
  override val catNetworkTools = "Network Tools"
  override val catDeviceMonitor = "Device Monitor"
  override val toolBoosterTitle = "Smart Game Booster"
  override val toolBoosterSub = "RAM, kecerahan, rotasi, DND & immersive"
  override val toolSidebarTitle = "Edge Floating Control Center"
  override val toolSidebarSub = "Swipe tepi layar: multi-app, tools, suara & monitor"
  override val toolNetworkTitle = "Network Monitor"
  override val toolNetworkSub = "Ping TCP, jitter, packet loss & kecepatan"
  override val toolDisplayTitle = "Touch & Display Tools"
  override val toolDisplaySub = "Refresh rate, resolusi & kanvas multi-touch"
  override val toolSoundTitle = "Audio Center"
  override val toolSoundSub = "Equalizer 5-band, bass & uji audio spasial"
  override val toolMonitorTitle = "Device Monitor"
  override val toolMonitorSub = "Grafik RAM, CPU, suhu, baterai & jaringan"
  override val toolProfilesTitle = "Gaming Profiles"
  override val toolProfilesSub = "Performance, Balanced, Battery Saver"
  override val toolUtilitiesTitle = "Gaming Timer & Utilities"
  override val toolUtilitiesSub = "Stopwatch, timer, kalkulator & catatan"
  override val toolStatsTitle = "Play Statistics"
  override val toolStatsSub = "Total waktu main, grafik & sesi"
  override val toolVoiceTitle = "Voice Studio"
  override val toolVoiceSub = "10 preset suara, pitch, EQ & tes mikrofon"
  override val smartBoosterTitle = "Smart Game Booster"
  override val androidHonestyNote =
    "Transparansi Android: Tanpa akses root, Android membatasi overclock paksa dan menutup proses RAM aplikasi lain. DRX mengoptimalkan cache proses sendiri dan menerapkan kecerahan, DND, orientasi, serta profil perangkat secara resmi."
  override val brightnessControl = "Kecerahan Layar"
  override val autoBrightness = "Kecerahan Otomatis"
  override val rotationControl = "Rotasi Otomatis"
  override val immersiveMode = "Mode Immersive"
  override val dndMode = "Do Not Disturb"
  override val mediaVolume = "Volume Media"
  override val ringVolume = "Volume Dering"
  override val systemShortcuts = "Pintasan Sistem"
  override val grantPermission = "Izinkan"
  override val permissionGranted = "Aktif"
  override val sidebarControlTitle = "DRX Floating Control Center"
  override val enableSidebar = "Aktifkan Edge Floating Sidebar"
  override val edgeSideLabel = "Sisi Pemicu Edge Swipe"
  override val edgeLeftLabel = "Kiri (Geser Kanan)"
  override val edgeRightLabel = "Kanan (Geser Kiri)"
  override val handleThicknessLabel = "Ketebalan Handle"
  override val handleOpacityLabel = "Transparansi Handle"
  override val autoHideHandleLabel = "Sembunyikan Otomatis Saat Idle"
  override val hapticsLabel = "Umpan Balik Haptic"
  override val swipeSensitivityLabel = "Sensitivitas Swipe"
  override val panelTransparency = "Transparansi Panel"
  override val panelSize = "Ukuran Panel"
  override val resetPosition = "Reset Posisi"
  override val overlayExplanation =
    "Izin Display Over Other Apps diperlukan untuk menampilkan pemicu edge-swipe, panel 5-tab, dan jendela mini melayang di atas game."
  override val edgeGestureConflictNote =
    "Info Gesture Android: Tekan lama handle untuk geser vertikal menjauhi zona Back Gesture sistem. Tap singkat juga tersedia sebagai alternatif jika gesture tepi terhalang."
  override val pinnedFloatingAppsTitle = "Aplikasi Tersemat di Tab Apps"
  override val addFloatingAppBtn = "Tambah Aplikasi"
  override val autoCloseFloatingOnExitLabel = "Tutup Otomatis Saat Game Selesai"
  override val autoCloseFloatingOnExitDesc =
    "Sembunyikan Floating Control Center otomatis saat keluar dari game atau kembali ke aplikasi DRX."
  override val networkToolsTitle = "Network Monitor"
  override val startTest = "Mulai Uji Jaringan"
  override val testingProgress = "Menguji Ping & Kecepatan..."
  override val pingLabel = "Ping"
  override val jitterLabel = "Jitter"
  override val packetLossLabel = "Loss"
  override val speedLabel = "Speed"
  override val stabilityLabel = "Stabilitas"
  override val testHistory = "Riwayat Pengujian"
  override val noVpnNotice = "Pengujian berjalan langsung tanpa VPN atau manipulasi routing."
  override val displayTouchTitle = "Touch & Display Tools"
  override val resolutionLabel = "Resolusi"
  override val refreshRateLabel = "Refresh Rate"
  override val screenTimeoutLabel = "Batas Waktu Layar"
  override val touchTestTitle = "Kanvas Multi-Touch"
  override val touchTestHint = "Sentuh dan geser di area ini untuk menguji multi-touch & laju Hz"
  override val activePointers = "Sentuhan"
  override val touchRateHz = "Laju Sentuh"
  override val soundCenterTitle = "Audio Center"
  override val audioTestBtn = "Putar Uji Audio Spasial"
  override val stopAudioTestBtn = "Hentikan Uji Audio"
  override val bassBoostLabel = "Bass Boost"
  override val virtualSurroundLabel = "Virtual Surround"
  override val safetyVolumeBoost = "Volume Booster (Aman +9dB)"
  override val equalizerTitle = "Equalizer 5-Band"
  override val deviceMonitorTitle = "Device Monitor"
  override val thermalStatusLabel = "Status Termal"
  override val deviceInfoTitle = "Spesifikasi Perangkat"
  override val profilesTitle = "Gaming Profiles"
  override val profilePerformance = "Performance"
  override val profileBalanced = "Balanced"
  override val profileBatterySaver = "Battery Saver"
  override val profileCustom = "Custom"
  override val applyProfile = "Terapkan Profil"
  override val utilitiesTitle = "Gaming Tools"
  override val stopwatchTitle = "Stopwatch Gaming"
  override val timerTitle = "Gaming Timer"
  override val notesTitle = "Catatan Taktis"
  override val addNote = "Simpan Catatan"
  override val calcTitle = "Kalkulator Gaming"
  override val statisticsTitle = "Statistik Bermain"
  override val totalPlaytime = "Total Main"
  override val totalSessions = "Total Sesi"
  override val mostPlayedGame = "Tersering"
  override val avgSession = "Rata-rata"
  override val weeklyUsageChart = "Aktivitas 7 Hari"
  override val voiceStudioTitle = "Voice Studio"
  override val voiceLimitationTitle = "Catatan Routing Audio Android"
  override val voiceLimitationBody =
    "Mode 1 (Voice Preview), Mode 2 (Voice Studio rekam/proses), dan Mode 3 (Integrasi Live pada perangkat/rute audio yang mendukung). Android membatasi suntikan mikrofon global ke chat game eksternal tanpa HAL OEM/root."
  override val micTestRecord = "Mulai Rekam / Uji"
  override val stopMic = "Berhenti"
  override val liveMonitorToggle = "Voice Preview (Live Headset)"
  override val playProcessedClip = "Putar Hasil"
  override val pitchLabel = "Pitch"
  override val bassLabel = "Bass Control"
  override val trebleLabel = "Treble Control"
  override val echoLabel = "Echo"
  override val reverbLabel = "Reverb"
  override val noiseReductionLabel = "Noise Reduction"
  override val savePresetBtn = "Simpan Custom"
  override val resetPresetBtn = "Reset Default"
  override val settingsTitle = "Pengaturan"
  override val darkModeLabel = "Tema Elite Dark"
  override val languageLabel = "Bahasa"
  override val animationsLabel = "Animasi & Motion 60FPS"
  override val launchAnimEnabledLabel = "Animasi Pembukaan Game"
  override val launchAnimQualityLabel = "Kualitas Animasi Launch"
  override val launchSoundEnabledLabel = "Suara Intro Pembukaan Game"
  override val skipAnimation = "Lewati"
  override val readyToPlay = "READY TO PLAY"
  override val launchErrorTitle = "Gagal Membuka Game"
  override val retryLaunch = "Coba Lagi"
  override val closeLabel = "Tutup"
  override val boostNotifLabel = "Notifikasi Optimasi"
  override val timerNotifLabel = "Notifikasi Timer"
  override val permissionManagerTitle = "Manajer Izin"
  override val privacySectionTitle = "Privasi & Keamanan"
  override val privacyZeroDataNote =
    "100% Lokal di Perangkat: Tanpa server eksternal, tanpa root, tanpa Accessibility Service pengintip layar, dan tidak memodifikasi file game."
  override val clearHistoryBtn = "Hapus Riwayat"
  override val resetSettingsBtn = "Reset Setelan"
  override val aboutTitle = "About DRX GAME TURBO PRO"
  override val aboutSubtitle = "Profil Developer Resmi & Informasi Aplikasi"
  override val officialDeveloperBadge = "OFFICIAL DEVELOPER"
  override val officialDeveloperIdentity = "Official Developer of DRX GAME TURBO PRO"
  override val developerBioParagraph1 =
    "DRX GAME TURBO PRO merupakan project yang dikembangkan oleh DerreX dengan tujuan menghadirkan pengalaman gaming companion yang modern, praktis, dan lengkap untuk pengguna Android."
  override val developerBioParagraph2 =
    "Dibangun dengan semangat kreativitas, inovasi, dan teknologi untuk menghadirkan berbagai tools gaming dalam satu aplikasi."
  override val socialConnectionsTitle = "SOCIAL CONNECTIONS"
  override val supportedByTitle = "SUPPORTED BY"
  override val officialSupporterBadge = "OFFICIAL SUPPORTER"
  override val supporterDescription = "Special appreciation to Nanay for supporting DRX GAME TURBO PRO."
  override val socialCountersTitle = "SOCIAL COUNTERS"
  override val dataUnavailableLabel = "Data Unavailable"
  override val lastUpdatedLabel = "Pembaruan Terakhir"
  override val refreshStatsBtn = "Refresh"
  override val appInfoSectionTitle = "APPLICATION INFORMATION"
  override val checkUpdatesBtn = "Check for Updates"
  override val privacyPolicyBtn = "Privacy Policy"
  override val termsServiceBtn = "Terms of Service"
  override val openSourceLicensesBtn = "Open Source Licenses"
  override val supporterAvatarSettingTitle = "Avatar Supporter (Nanay)"
  override val changeSupporterAvatarBtn = "Ganti Foto Avatar"
  override val resetSupporterAvatarBtn = "Gunakan Placeholder"
  override val copiedToClipboardLabel = "Disalin ke clipboard"
  override val updateLogTitle = "DRX Update Log"
  override val updateLogSubtitle = "Riwayat Pembaruan & Catatan Rilis"
  override val whatsNewBtn = "What's New"
  override val markAllReadBtn = "Tandai Dibaca"
  override val unreadBadgeLabel = "NEW"
  override val newUpdateAvailableBanner = "Changelog versi terbaru siap dibaca!"
  override val announcementCenterTitle = "Announcement Center"
  override val announcementCenterSubtitle = "DRX Smart Announcement • Otomatis Acak"
  override val enableAnnouncementsLabel = "Aktifkan DRX Smart Announcement"
  override val frequencyLabel = "Frekuensi Acak Harian"
  override val quietHoursLabel = "Quiet Hours (Tanpa Notifikasi)"
  override val soundLabel = "Suara Notifikasi"
  override val vibrationLabel = "Getaran Notifikasi"
  override val categorySelectionLabel = "Kategori Pengumuman"
  override val previewNotificationBtn = "Preview Notifikasi"
  override val announcementHistoryLabel = "Riwayat Pengumuman"
  override val clearAnnouncementHistoryBtn = "Hapus Riwayat"
  override val noAnnouncementHistoryText = "Belum ada pengumuman yang terkirim."
  override val workManagerScheduleNotice =
    "Penjadwalan menggunakan Android WorkManager secara acak dan hemat baterai. Waktu kemunculan bersifat fleksibel sesuai batas OS/Doze dan tidak memakai exact alarm."
  override val viewGameStatsBtn = "VIEW GAME STATISTICS"
  override val gameStatsTitle = "DRX Game Statistics"
  override val gameInfoSectionTitle = "GAME INFORMATION"
  override val playtimeSectionTitle = "PLAYTIME STATISTICS"
  override val lastPlayedSectionTitle = "LAST PLAYED INFO"
  override val installInfoSectionTitle = "INSTALLATION INFORMATION"
  override val storageInfoSectionTitle = "STORAGE INFORMATION"
  override val activityChartTitle = "GRAFIK AKTIVITAS"
  override val sessionHistoryTitle = "RIWAYAT SESI BERMAIN"
  override val installedStatus = "Installed"
  override val notInstalledStatus = "Not Installed"
  override val estimatedBadge = "Estimated"
  override val notAvailableLabel = "Not Available"
  override val grantUsagePermissionCta = "Izinkan Usage Access"
  override val usageAccessNotice =
    "Izin Usage Access diperlukan Android untuk membaca perkiraan waktu pakai sistem serta ukuran Data & Cache aplikasi."
  override val installDateNote =
    "Catatan: Tanggal pemasangan berasal dari PackageManager (waktu pasang/reinstall di perangkat ini, bukan tanggal transaksi Play Store)."
  override val lastPlayedNever = "Belum pernah dimainkan"
  override val sortByName = "Nama"
  override val sortByPlaytime = "Playtime"
  override val sortByLastPlayed = "Terakhir Main"
  override val filterToday = "Hari Ini"
  override val filter7Days = "7 Hari"
  override val filter30Days = "30 Hari"
  override val filterAll = "Semua"
  override val globalStatsTitle = "DRX Global Game Statistics"
  override val totalTrackedGamesLabel = "Game Terpasang"
  override val mostOpenedGameLabel = "Paling Sering Dibuka"
  override val lastPlayedGameLabel = "Terakhir Dimainkan"
  override val weeklyChartLabel = "Aktivitas Mingguan (7 Hari)"
  override val monthlyChartLabel = "Aktivitas Bulanan (30 Hari)"
  override val customizationCenterTitle = "DRX Customization Center"
  override val customizationCenterSub = "7 Tema Cockpit • Warna Aksen • Wallpaper"
  override val themePresetsTitle = "Tema DRX (7 Preset)"
  override val customAccentTitle = "Warna Aksen Kustom"
  override val wallpaperTitle = "Wallpaper & Latar Belakang"
  override val chooseWallpaperBtn = "Pilih Gambar Latar"
  override val resetWallpaperBtn = "Hapus Wallpaper"
  override val uiSoundsLabel = "Efek Suara UI"
  override val hapticFeedbackLabel = "Getaran Sentuhan (Haptic)"
  override val drxEditionsTitle = "DRX Editions Roadmap"
  override val comingSoonBadge = "Coming Soon"
  override val resetRotationBtn = "Reset Message Rotation"
  override val soundboardTitle = "Quick Soundboard FX"
  override val startMonitoringBtn = "Start Monitoring"
  override val stopMonitoringBtn = "Stop Monitoring"
  override val performanceReportBtn = "Performance Report"
  override val gameNotificationsTitle = "Game Notifications"
  override val gameNotificationsSub = "Riwayat notifikasi game & pengelompokan tanggal/game"
  override val notificationInsightsTitle = "Notification Insights"
  override val notificationInsightsSub = "Statistik, jam aktif & distribusi notifikasi game"
  override val gameEventsTitle = "Game Event Tracker"
  override val gameEventsSub = "Event, hadiah, turnamen & pengingat WorkManager"
  override val activityCenterTitle = "Game Activity Center"
  override val activityCenterSub = "Ringkasan game, notifikasi, event & media terbaru"
  override val updateCenterTitle = "DRX Update Center"
  override val updateCenterSub = "Cek pembaruan, verifikasi APK & kebijakan smart update"
  override val notifPermissionMissingTitle = "Akses Notifikasi Belum Aktif"
  override val notifPermissionMissingBody =
    "Aktifkan Notification Access di Setelan Android agar DRX dapat membaca notifikasi asli dari game. Tanpa izin ini, DRX tidak akan membuat riwayat palsu."
  override val notifGrantAccessBtn = "Buka Akses Notifikasi"
  override val notifTabByDate = "MENURUT TANGGAL"
  override val notifTabByGame = "MENURUT GAME"
  override val notifSearchPlaceholder = "Cari game, judul, atau isi notifikasi..."
  override val notifEmptyTitle = "Belum Ada Notifikasi Game"
  override val notifEmptyBody =
    "Notifikasi dari game terpasang akan tercatat secara lokal di sini sejak akses notifikasi diaktifkan."
  override val notifViewerDisabledNotice = "Pembaca Notifikasi Game sedang dimatikan di Setelan Privasi."
  override val notifPrivacyTitle = "Privasi & Kontrol Notifikasi"
  override val notifPrivacyNote =
    "100% disimpan lokal di perangkat (Room). Tidak memakai AccessibilityService. Pola OTP, PIN, kata sandi, dan token otomatis diblokir/disensor."
  override val updateLaterBtn = "Nanti"
  override val updateNowBtn = "Update Sekarang"
  override val updateExitAppBtn = "Keluar Aplikasi"
  override val updateMandatoryNotice =
    "Pembaruan ini wajib dilanjutkan karena telah dilewati sebelumnya. Perbarui ke versi terbaru untuk melanjutkan."
  override val sessionIntelligenceTitle = "Gaming Session Intelligence"
  override val sessionIntelligenceSub = "Statistik pribadi, perbandingan sesi & tren mingguan"
  override val stabilityWatchTitle = "Game Stability Watch"
  override val stabilityWatchSub = "Pantau tekanan thermal, RAM, jaringan & riwayat peringatan"
  override val quickProfileTitle = "DRX Quick Profile"
  override val quickProfileSubtitle = "Atur mode overlay DRX, kecepatan polling & efek gerak"
  override val exportReportBtn = "Ekspor Laporan"
  override val compareSessionsTitle = "Perbandingan Sesi"
  override val weeklySummaryTitle = "Ringkasan Mingguan"
  override val personalStatsTitle = "Statistik Gaming Pribadi"
  override val saveReportBtn = "Simpan Laporan"
  override val warningHistoryTitle = "Riwayat Peringatan"
  override val diagnosticDetailsTitle = "Detail Diagnostik"
  override val mediaAllTab = "Semua Media"
  override val mediaFavoritesTab = "Favorit"
  override val openFolderBtn = "Buka Folder"
  override val viewChangelogBtn = "Lihat Changelog"
  override val notifDateTodayLabel = "Hari Ini"
  override val notifDateYesterdayLabel = "Kemarin"
  override val notifDate7DaysLabel = "7 Hari"
  override val perfStateReady = "Ready"
  override val perfStateMonitoring = "Monitoring"
  override val perfStateThermalWarning = "Thermal Warning"
  override val perfStateLimitedMonitoring = "Limited Monitoring"
  override val perfStateDeviceIdle = "Device Idle"
  override val sessionTimelineTitle = "DRX Session Timeline"
  override val sessionTimelineSub = "Timeline kejadian sesi, grafik suhu/baterai/RAM & ekspor .txt"
  override val resourceReportTitle = "Game Resource Report"
  override val resourceReportSub = "Analisis penggunaan baterai, RAM, suhu & perbandingan sesi"
  override val profileAutomationTitle = "Profile Automation"
  override val profileAutomationSub = "Profil otomatis per-game, HUD, sidebar & timer sesi"
  override val preGameCheckTitle = "Pre-Game Check"
  override val preGameCheckSub = "Cek kesiapan storage, thermal, baterai, jaringan & izin"
  override val playAnywayBtn = "Mainkan Sekarang"
  override val touchTestTitle = "Touch Response Test"
  override val touchTestSub = "Tap, multi-touch, tracking, gestur & tantangan akurasi sentuh"
  override val displayIntelTitle = "Display Intelligence"
  override val displayIntelSub = "Resolusi, DPI, mode refresh rate layar, ukuran fisik & HDR"
  override val focusModeTitle = "Gaming Focus Mode"
  override val focusModeSub = "Timer fokus, Priority DND (aman untuk alarm) & screen timeout"
}

object EnglishStrings : AppStrings {
  override val appName = "DRX GAME TURBO PRO"
  override val developerBy = "Developed by DerreX"
  override val greetingSubtitle = "Ready for smooth, focused gameplay"
  override val navHome = "Home"
  override val navGames = "Games"
  override val navTools = "Tools"
  override val navStats = "Stats"
  override val navActivity = "Activity"
  override val navProfile = "Profile"
  override val navVoice = "Voice"
  override val navSettings = "Settings"
  override val introTagline = "POWER YOUR GAME. OWN YOUR EXPERIENCE."
  override val boostStageEngine = "DRX PERFORMANCE ENGINE"
  override val boostStageSession = "Preparing Gaming Session..."
  override val boostStageResources = "Checking Available Resources..."
  override val boostStageTools = "Preparing DRX Gaming Tools..."
  override val heroEyebrow = "PERFORMANCE STATUS"
  override val heroHeadline = "READY FOR GAMING"
  override val heroDescription = "Unified game session tuning, edge-swipe control center & live telemetry."
  override val devicePerformanceTitle = "Device Performance"
  override val deviceOverviewTitle = "Device Overview"
  override val gamingModeActive = "GAMING ACTIVE"
  override val gamingModeStandby = "STANDBY"
  override val boostNowBtn = "BOOST NOW"
  override val boostingLabel = "OPTIMIZING..."
  override val boostHonestSummary = "Trims DRX internal cache & applies selected performance profile"
  override val boostCompletedTitle = "Optimization Complete"
  override val cpuLabel = "CPU"
  override val cpuRestrictedNote = "OS Restricted"
  override val ramLabel = "RAM"
  override val storageLabel = "Storage"
  override val batteryTempLabel = "Temp"
  override val batteryLevelLabel = "Battery"
  override val networkLabel = "Network"
  override val fpsDisplayLabel = "Display & UI FPS"
  override val activeSessionBanner = "Active Session:"
  override val stopSaveSession = "Save Session"
  override val myGamesSection = "My Games"
  override val viewAllBtn = "View All"
  override val quickToolsSection = "Game Tools"
  override val recentSessionsSection = "Play Session History"
  override val appUsageSection = "App Usage Stats (24h)"
  override val grantUsageAccessHint = "Grant Usage Access in Settings to view app usage."
  override val noGamesYet = "No games yet. Scan or add installed apps."
  override val noSessionsYet = "No play sessions recorded yet."
  override val gameLibraryTitle = "Game Library"
  override val recentlyPlayedTitle = "Recently Played"
  override val gameDetailsTitle = "Game Details"
  override val scanGamesBtn = "Scan"
  override val addGameManualBtn = "Add"
  override val searchGamesPlaceholder = "Search games..."
  override val favoritesOnly = "Favorites"
  override val playBtn = "PLAY"
  override val boostAndPlayBtn = "BOOST & PLAY"
  override val removeGame = "Remove"
  override val selectProfile = "Profile"
  override val selectCategory = "Category"
  override val addAppsSheetTitle = "Select Installed App"
  override val toolsHubTitle = "Tools Center"
  override val catGamingTools = "Gaming Tools"
  override val catAudioTools = "Audio Tools"
  override val catDisplayTools = "Display Tools"
  override val catNetworkTools = "Network Tools"
  override val catDeviceMonitor = "Device Monitor"
  override val toolBoosterTitle = "Smart Game Booster"
  override val toolBoosterSub = "RAM, brightness, rotation, DND & immersive"
  override val toolSidebarTitle = "Edge Floating Control Center"
  override val toolSidebarSub = "Edge swipe overlay: multi-app, voice, tools & monitor"
  override val toolNetworkTitle = "Network Monitor"
  override val toolNetworkSub = "TCP ping, jitter, packet loss & speed"
  override val toolDisplayTitle = "Touch & Display Tools"
  override val toolDisplaySub = "Refresh rate, resolution & multi-touch canvas"
  override val toolSoundTitle = "Audio Center"
  override val toolSoundSub = "5-band equalizer, bass & spatial test"
  override val toolMonitorTitle = "Device Monitor"
  override val toolMonitorSub = "RAM, CPU, temperature, battery & network charts"
  override val toolProfilesTitle = "Gaming Profiles"
  override val toolProfilesSub = "Performance, Balanced, Battery Saver"
  override val toolUtilitiesTitle = "Gaming Timer & Utilities"
  override val toolUtilitiesSub = "Stopwatch, timer, calculator & notes"
  override val toolStatsTitle = "Play Statistics"
  override val toolStatsSub = "Total playtime, weekly chart & sessions"
  override val toolVoiceTitle = "Voice Studio"
  override val toolVoiceSub = "10 voice presets, pitch, EQ & mic studio"
  override val smartBoosterTitle = "Smart Game Booster"
  override val androidHonestyNote =
    "Android Transparency: Without root, Android prevents force-overclocking or killing other apps' RAM globally. DRX trims its own caches and applies supported brightness, DND, orientation, and device profiles."
  override val brightnessControl = "Screen Brightness"
  override val autoBrightness = "Auto Brightness"
  override val rotationControl = "Auto Rotation"
  override val immersiveMode = "Immersive Mode"
  override val dndMode = "Do Not Disturb"
  override val mediaVolume = "Media Volume"
  override val ringVolume = "Ring Volume"
  override val systemShortcuts = "System Shortcuts"
  override val grantPermission = "Grant"
  override val permissionGranted = "Active"
  override val sidebarControlTitle = "DRX Floating Control Center"
  override val enableSidebar = "Enable Edge Floating Sidebar"
  override val edgeSideLabel = "Edge Swipe Side"
  override val edgeLeftLabel = "Left (Swipe Right)"
  override val edgeRightLabel = "Right (Swipe Left)"
  override val handleThicknessLabel = "Handle Thickness"
  override val handleOpacityLabel = "Handle Opacity"
  override val autoHideHandleLabel = "Auto-Hide When Idle"
  override val hapticsLabel = "Haptic Feedback"
  override val swipeSensitivityLabel = "Swipe Sensitivity"
  override val panelTransparency = "Panel Opacity"
  override val panelSize = "Panel Size"
  override val resetPosition = "Reset Position"
  override val overlayExplanation =
    "Display Over Other Apps permission is required for the edge-swipe handle, 5-tab control center, and floating mini-windows."
  override val edgeGestureConflictNote =
    "Android Gesture Note: Long-press the edge handle to drag vertically away from system back-gesture areas. Short tap also opens the sidebar if edge swipe is intercepted."
  override val pinnedFloatingAppsTitle = "Pinned Apps in Sidebar"
  override val addFloatingAppBtn = "Add App"
  override val autoCloseFloatingOnExitLabel = "Auto-Close When Exiting Game"
  override val autoCloseFloatingOnExitDesc =
    "Automatically dismiss the Floating Control Center when leaving the game or returning to DRX."
  override val networkToolsTitle = "Network Monitor"
  override val startTest = "Run Network Test"
  override val testingProgress = "Measuring Ping & Speed..."
  override val pingLabel = "Ping"
  override val jitterLabel = "Jitter"
  override val packetLossLabel = "Loss"
  override val speedLabel = "Speed"
  override val stabilityLabel = "Stability"
  override val testHistory = "Test History"
  override val noVpnNotice = "Tests run directly without VPN or routing modifications."
  override val displayTouchTitle = "Touch & Display Tools"
  override val resolutionLabel = "Resolution"
  override val refreshRateLabel = "Refresh Rate"
  override val screenTimeoutLabel = "Screen Timeout"
  override val touchTestTitle = "Multi-Touch Canvas"
  override val touchTestHint = "Touch and drag here to test multi-touch & event rate"
  override val activePointers = "Touches"
  override val touchRateHz = "Touch Rate"
  override val soundCenterTitle = "Audio Center"
  override val audioTestBtn = "Play Spatial Audio Test"
  override val stopAudioTestBtn = "Stop Audio Test"
  override val bassBoostLabel = "Bass Boost"
  override val virtualSurroundLabel = "Virtual Surround"
  override val safetyVolumeBoost = "Volume Booster (Safe +9dB)"
  override val equalizerTitle = "5-Band Equalizer"
  override val deviceMonitorTitle = "Device Monitor"
  override val thermalStatusLabel = "Thermal Status"
  override val deviceInfoTitle = "Device Specs"
  override val profilesTitle = "Gaming Profiles"
  override val profilePerformance = "Performance"
  override val profileBalanced = "Balanced"
  override val profileBatterySaver = "Battery Saver"
  override val profileCustom = "Custom"
  override val applyProfile = "Apply Profile"
  override val utilitiesTitle = "Gaming Tools"
  override val stopwatchTitle = "Gaming Stopwatch"
  override val timerTitle = "Gaming Timer"
  override val notesTitle = "Tactical Notes"
  override val addNote = "Save Note"
  override val calcTitle = "Gaming Calculator"
  override val statisticsTitle = "Play Statistics"
  override val totalPlaytime = "Total Play"
  override val totalSessions = "Sessions"
  override val mostPlayedGame = "Top Game"
  override val avgSession = "Avg Session"
  override val weeklyUsageChart = "7-Day Activity"
  override val voiceStudioTitle = "Voice Studio"
  override val voiceLimitationTitle = "Android Audio Routing Notice"
  override val voiceLimitationBody =
    "Mode 1 (Live Voice Preview), Mode 2 (Voice Studio record & process), and Mode 3 (Supported Live Integration on compatible audio routing hardware). Android restricts global mic injection into external games without OEM HAL/root."
  override val micTestRecord = "Start Mic / Record"
  override val stopMic = "Stop"
  override val liveMonitorToggle = "Voice Preview (Live Headset)"
  override val playProcessedClip = "Play Processed"
  override val pitchLabel = "Pitch"
  override val bassLabel = "Bass Control"
  override val trebleLabel = "Treble Control"
  override val echoLabel = "Echo"
  override val reverbLabel = "Reverb"
  override val noiseReductionLabel = "Noise Reduction"
  override val savePresetBtn = "Save Custom"
  override val resetPresetBtn = "Reset Default"
  override val settingsTitle = "Settings"
  override val darkModeLabel = "Elite Dark Theme"
  override val languageLabel = "Language"
  override val animationsLabel = "Smooth 60FPS Motion"
  override val launchAnimEnabledLabel = "Game Launch Intro Animation"
  override val launchAnimQualityLabel = "Launch Animation Quality"
  override val launchSoundEnabledLabel = "Launch Intro Sound Design"
  override val skipAnimation = "Skip"
  override val readyToPlay = "READY TO PLAY"
  override val launchErrorTitle = "Failed to Launch Game"
  override val retryLaunch = "Retry"
  override val closeLabel = "Close"
  override val boostNotifLabel = "Boost Notifications"
  override val timerNotifLabel = "Timer Alerts"
  override val permissionManagerTitle = "Permission Manager"
  override val privacySectionTitle = "Privacy & Security"
  override val privacyZeroDataNote =
    "100% On-Device: No external servers, no root, no Accessibility screen-reading services, and never modifies game files."
  override val clearHistoryBtn = "Clear History"
  override val resetSettingsBtn = "Reset Settings"
  override val aboutTitle = "About DRX GAME TURBO PRO"
  override val aboutSubtitle = "Official Developer Profile & Application Info"
  override val officialDeveloperBadge = "OFFICIAL DEVELOPER"
  override val officialDeveloperIdentity = "Official Developer of DRX GAME TURBO PRO"
  override val developerBioParagraph1 =
    "DRX GAME TURBO PRO merupakan project yang dikembangkan oleh DerreX dengan tujuan menghadirkan pengalaman gaming companion yang modern, praktis, dan lengkap untuk pengguna Android."
  override val developerBioParagraph2 =
    "Dibangun dengan semangat kreativitas, inovasi, dan teknologi untuk menghadirkan berbagai tools gaming dalam satu aplikasi."
  override val socialConnectionsTitle = "SOCIAL CONNECTIONS"
  override val supportedByTitle = "SUPPORTED BY"
  override val officialSupporterBadge = "OFFICIAL SUPPORTER"
  override val supporterDescription = "Special appreciation to Nanay for supporting DRX GAME TURBO PRO."
  override val socialCountersTitle = "SOCIAL COUNTERS"
  override val dataUnavailableLabel = "Data Unavailable"
  override val lastUpdatedLabel = "Last Updated"
  override val refreshStatsBtn = "Refresh"
  override val appInfoSectionTitle = "APPLICATION INFORMATION"
  override val checkUpdatesBtn = "Check for Updates"
  override val privacyPolicyBtn = "Privacy Policy"
  override val termsServiceBtn = "Terms of Service"
  override val openSourceLicensesBtn = "Open Source Licenses"
  override val supporterAvatarSettingTitle = "Supporter Avatar (Nanay)"
  override val changeSupporterAvatarBtn = "Choose Avatar Photo"
  override val resetSupporterAvatarBtn = "Reset Placeholder"
  override val copiedToClipboardLabel = "Copied to clipboard"
  override val updateLogTitle = "DRX Update Log"
  override val updateLogSubtitle = "Release Timeline & Changelog"
  override val whatsNewBtn = "What's New"
  override val markAllReadBtn = "Mark All Read"
  override val unreadBadgeLabel = "NEW"
  override val newUpdateAvailableBanner = "Latest release changelog is ready to view!"
  override val announcementCenterTitle = "Announcement Center"
  override val announcementCenterSubtitle = "DRX Smart Announcement • Automatic Random"
  override val enableAnnouncementsLabel = "Enable DRX Smart Announcement"
  override val frequencyLabel = "Daily Random Frequency"
  override val quietHoursLabel = "Quiet Hours (No Notifications)"
  override val soundLabel = "Notification Sound"
  override val vibrationLabel = "Notification Vibration"
  override val categorySelectionLabel = "Announcement Categories"
  override val previewNotificationBtn = "Preview Notification"
  override val announcementHistoryLabel = "Announcement History"
  override val clearAnnouncementHistoryBtn = "Clear History"
  override val noAnnouncementHistoryText = "No announcements delivered yet."
  override val workManagerScheduleNotice =
    "Scheduled via battery-friendly Android WorkManager without exact alarms. Delivery times are randomized and subject to OS battery/Doze policies."
  override val viewGameStatsBtn = "VIEW GAME STATISTICS"
  override val gameStatsTitle = "DRX Game Statistics"
  override val gameInfoSectionTitle = "GAME INFORMATION"
  override val playtimeSectionTitle = "PLAYTIME STATISTICS"
  override val lastPlayedSectionTitle = "LAST PLAYED INFO"
  override val installInfoSectionTitle = "INSTALLATION INFORMATION"
  override val storageInfoSectionTitle = "STORAGE INFORMATION"
  override val activityChartTitle = "ACTIVITY CHART"
  override val sessionHistoryTitle = "PLAY SESSION HISTORY"
  override val installedStatus = "Installed"
  override val notInstalledStatus = "Not Installed"
  override val estimatedBadge = "Estimated"
  override val notAvailableLabel = "Not Available"
  override val grantUsagePermissionCta = "Grant Usage Access"
  override val usageAccessNotice =
    "Android Usage Access permission is required to read system foreground playtime estimates and app Data & Cache storage sizes."
  override val installDateNote =
    "Note: Install date comes from PackageManager (time installed/reinstalled on this device, distinct from Play Store purchase history)."
  override val lastPlayedNever = "Never played yet"
  override val sortByName = "Name"
  override val sortByPlaytime = "Playtime"
  override val sortByLastPlayed = "Last Played"
  override val filterToday = "Today"
  override val filter7Days = "7 Days"
  override val filter30Days = "30 Days"
  override val filterAll = "All"
  override val globalStatsTitle = "DRX Global Game Statistics"
  override val totalTrackedGamesLabel = "Installed Games"
  override val mostOpenedGameLabel = "Most Opened"
  override val lastPlayedGameLabel = "Last Played"
  override val weeklyChartLabel = "Weekly Activity (7 Days)"
  override val monthlyChartLabel = "Monthly Activity (30 Days)"
  override val customizationCenterTitle = "DRX Customization Center"
  override val customizationCenterSub = "7 Cockpit Themes • Accent Color • Wallpaper"
  override val themePresetsTitle = "DRX Themes (7 Presets)"
  override val customAccentTitle = "Custom Accent Color"
  override val wallpaperTitle = "Custom Wallpaper"
  override val chooseWallpaperBtn = "Choose Background"
  override val resetWallpaperBtn = "Clear Wallpaper"
  override val uiSoundsLabel = "UI Sound Effects"
  override val hapticFeedbackLabel = "Haptic Feedback"
  override val drxEditionsTitle = "DRX Editions Roadmap"
  override val comingSoonBadge = "Coming Soon"
  override val resetRotationBtn = "Reset Message Rotation"
  override val soundboardTitle = "Quick Soundboard FX"
  override val startMonitoringBtn = "Start Monitoring"
  override val stopMonitoringBtn = "Stop Monitoring"
  override val performanceReportBtn = "Performance Report"
  override val gameNotificationsTitle = "Game Notifications"
  override val gameNotificationsSub = "Game notification history grouped by date or game"
  override val notificationInsightsTitle = "Notification Insights"
  override val notificationInsightsSub = "Real counts, peak hours & per-game notification share"
  override val gameEventsTitle = "Game Event Tracker"
  override val gameEventsSub = "Events, rewards, tournaments & WorkManager reminders"
  override val activityCenterTitle = "Game Activity Center"
  override val activityCenterSub = "Unified hub: playtime, notifications, events & media"
  override val updateCenterTitle = "DRX Update Center"
  override val updateCenterSub = "Check updates, verify APK & smart update policy"
  override val notifPermissionMissingTitle = "Notification Access Not Enabled"
  override val notifPermissionMissingBody =
    "Enable Notification Access in Android Settings so DRX can read real system notifications from games. DRX never fabricates notification history."
  override val notifGrantAccessBtn = "Grant Notification Access"
  override val notifTabByDate = "BY DATE"
  override val notifTabByGame = "BY GAME"
  override val notifSearchPlaceholder = "Search game, title, or notification text..."
  override val notifEmptyTitle = "No Game Notifications Yet"
  override val notifEmptyBody =
    "Notifications posted by installed games will be recorded locally here once Notification Access is granted."
  override val notifViewerDisabledNotice = "Game Notification Viewer is paused in Privacy Controls."
  override val notifPrivacyTitle = "Notification Privacy & Controls"
  override val notifPrivacyNote =
    "100% local Room storage on-device. No AccessibilityService used. OTP, PIN, password & token patterns are automatically blocked or redacted."
  override val updateLaterBtn = "Later"
  override val updateNowBtn = "Update Now"
  override val updateExitAppBtn = "Exit App"
  override val updateMandatoryNotice =
    "This update is now required after previous skips. Update to the latest version to continue using DRX."
  override val sessionIntelligenceTitle = "Gaming Session Intelligence"
  override val sessionIntelligenceSub = "Personal gaming stats, session comparison & weekly trends"
  override val stabilityWatchTitle = "Game Stability Watch"
  override val stabilityWatchSub = "Monitor thermal pressure, RAM, network & warning history"
  override val quickProfileTitle = "DRX Quick Profile"
  override val quickProfileSubtitle = "Controls DRX HUD mode, telemetry polling & motion intensity"
  override val exportReportBtn = "Export Report"
  override val compareSessionsTitle = "Session Comparison"
  override val weeklySummaryTitle = "Weekly Summary"
  override val personalStatsTitle = "Personal Gaming Statistics"
  override val saveReportBtn = "Save Report"
  override val warningHistoryTitle = "Warning History"
  override val diagnosticDetailsTitle = "Diagnostic Details"
  override val mediaAllTab = "All Media"
  override val mediaFavoritesTab = "Favorites"
  override val openFolderBtn = "Open Folder"
  override val viewChangelogBtn = "View Changelog"
  override val notifDateTodayLabel = "Today"
  override val notifDateYesterdayLabel = "Yesterday"
  override val notifDate7DaysLabel = "7 Days"
  override val perfStateReady = "Ready"
  override val perfStateMonitoring = "Monitoring"
  override val perfStateThermalWarning = "Thermal Warning"
  override val perfStateLimitedMonitoring = "Limited Monitoring"
  override val perfStateDeviceIdle = "Device Idle"
  override val sessionTimelineTitle = "DRX Session Timeline"
  override val sessionTimelineSub = "Chronological session events, temp/battery/RAM chart & .txt export"
  override val resourceReportTitle = "Game Resource Report"
  override val resourceReportSub = "Per-game battery estimate, RAM, thermal load & comparison"
  override val profileAutomationTitle = "Profile Automation"
  override val profileAutomationSub = "Per-game auto profile, HUD layout, sidebar & session timer"
  override val preGameCheckTitle = "Pre-Game Check"
  override val preGameCheckSub = "Storage, battery, thermal, network, overlay & telemetry check"
  override val playAnywayBtn = "Play Anyway"
  override val touchTestTitle = "Touch Response Test"
  override val touchTestSub = "Tap, multi-touch, tracking, gesture & accuracy target test"
  override val displayIntelTitle = "Display Intelligence"
  override val displayIntelSub = "Resolution, density, supported Display.Mode Hz, size & HDR"
  override val focusModeTitle = "Gaming Focus Mode"
  override val focusModeSub = "Focus timer, Priority DND (alarm-safe) & screen timeout"
}

fun stringsFor(mode: LanguageMode): AppStrings =
  when (mode) {
    LanguageMode.INDONESIAN -> IndonesianStrings
    LanguageMode.ENGLISH -> EnglishStrings
  }

val LocalStrings = staticCompositionLocalOf<AppStrings> { IndonesianStrings }
