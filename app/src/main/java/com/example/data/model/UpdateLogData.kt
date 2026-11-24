package com.example.data.model

enum class UpdateBadgeType(val label: String) {
  NEW_FEATURE("NEW FEATURE"),
  IMPROVEMENT("IMPROVEMENT"),
  FIXED("FIXED"),
  SECURITY("SECURITY"),
}

data class AppUpdateEntry(
  val versionNumber: String,
  val releaseDate: String,
  val title: String,
  val badges: List<UpdateBadgeType>,
  val newFeatures: List<String>,
  val improvements: List<String>,
  val bugFixes: List<String>,
  val performanceImprovements: List<String>,
  val additionalNotes: String,
)

object DrxUpdateCatalog {

  val entries: List<AppUpdateEntry> =
    listOf(
      AppUpdateEntry(
        versionNumber = "1.20.0",
        releaseDate = "2026-11-24",
        title = "DRX Elite Blue UI/UX Overhaul & Cinematic Brand Experience",
        badges = listOf(UpdateBadgeType.NEW_FEATURE, UpdateBadgeType.IMPROVEMENT, UpdateBadgeType.SECURITY),
        newFeatures =
          listOf(
            "Merombak identitas visual ke DRX Elite Blue (#050609 Deep Black, #0B0E14 Dark Graphite, #111722 Elevated Surface, #DCE4F0 Metallic Silver, #087BFF Electric Blue, #13D9FF Neon Cyan) bergaya modern iOS-inspired gaming flagship.",
            "Menghadirkan DRX Cinematic Logo Intro 6 adegan (ambient glow, shimmer metallic, energy orbit, tagline resmi POWER YOUR GAME. OWN YOUR EXPERIENCE., dan tombol Skip) yang menghormati setelan Reduced Motion & Low-RAM.",
            "Merancang ulang Home Dashboard menjadi lebih ringkas: Header berlogo DRX + pill v1.20.0, Hero READY FOR GAMING, grid telemetri 6-KPI real-time (UI FPS, CPU, RAM, Baterai, Suhu, Jaringan + Storage), serta Quick Tools 6 pintasan ringkas.",
            "Menyatukan 5 tab kapsul mengambang di bawah layar (Home, Games, Tools, Activity, Profile) serta menyelaraskan gaya Floating Edge Control Center & Mini-Windows.",
          ),
        improvements =
          listOf(
            "Menyelaraskan animasi Game Boost Launch Flow dengan warna DRX Elite Blue dan tahapan persiapan sesi yang transparan.",
            "Menghilangkan tombol duplikat pada header Dashboard dan Profile agar fokus pada hierarki visual yang bersih dan nyaman disentuh.",
          ),
        bugFixes =
          listOf(
            "Menjaga koordinasi antara animasi pembuka DRX dan Smart Update Modal agar dialog pembaruan tidak menutupi intro.",
          ),
        performanceImprovements =
          listOf(
            "Mengoptimalkan efek animasi ringan berbasis Compose Animatable & Canvas dengan bypass otomatis saat mode hemat animasi aktif.",
          ),
        additionalNotes =
          "Menggunakan logo resmi DRX apa adanya tanpa distorsi dan tetap mempertahankan 100% kejujuran metrik OS Android tanpa root maupun modifikasi file game.",
      ),
      AppUpdateEntry(
        versionNumber = "1.10.0",
        releaseDate = "2026-11-23",
        title = "DRX Fluid Motion Redesign, Session Intelligence, Stability Watch & Smart APK Updater",
        badges = listOf(UpdateBadgeType.NEW_FEATURE, UpdateBadgeType.IMPROVEMENT, UpdateBadgeType.SECURITY),
        newFeatures =
          listOf(
            "Menghadirkan DRX Fluid Motion Design System global yang ringan (60 FPS) dan otomatis menghormati setelan penghemat baterai / Reduce Motion OS.",
            "Menambahkan DRX Gaming Session Intelligence untuk total & rata-rata durasi bermain, sesi terlama, game paling sering & terakhir dimainkan, grafik harian/mingguan, perbandingan 2 sesi maupun minggu ini vs 7 hari sebelumnya, statistik jam puncak, serta ekspor laporan.",
            "Menambahkan DRX Game Stability Watch yang memantau RAM nyata, thermal OS, suhu/level baterai, penyimpanan, dan jaringan dengan kartu PERFORMANCE WARNING serta riwayat peringatan Room v5.",
            "Menambahkan 4 DRX Gaming Quick Profile (Balanced, Performance Monitoring, Battery Saver, Minimal Overlay) untuk mengatur mode HUD, interval polling telemetri, dan efek gerak DRX secara instan.",
            "Menyempurnakan DRX Smart Game Library (badge notifikasi & info versi/ukuran), Media Hub (tab All/Screenshots/Recordings/Favorites, Preview modal, Open Folder, multi-select), Smart Notification Control (filter Event/Reward/Update/Tanggal/Game), dan Smart Update Modal dengan tombol View Changelog.",
          ),
        improvements =
          listOf(
            "Menambah animasi slide-in spring/overshoot pada Floating Edge Control Center, transisi tab mulus, waveform mikrofon nyata pada Screen Recorder (datar saat mute/idle), serta dialog scale + fade.",
            "Menyelaraskan skrip drx_apk_detector.py dan UpdatePolicyEngine untuk memindai src/apk/, memverifikasi SHA-256 & sertifikat penandatanganan, dan mencegah downgrade versionCode.",
          ),
        bugFixes =
          listOf(
            "Memastikan indikator pulse peringatan hanya aktif saat kondisi Warning/Critical terdeteksi secara nyata dan berhenti saat status normal.",
            "Menjaga akurasi durasi rekaman layar MediaStore & menampilkan keterbatasan izin Android OS secara transparan.",
          ),
        performanceImprovements =
          listOf(
            "Menambahkan Room Database v5 (MIGRATION_4_5) untuk tabel stability_warnings serta interval polling telemetri adaptif mengikuti Quick Profile aktif.",
          ),
        additionalNotes =
          "Semua metrik performa, peringatan stabilitas, notifikasi game, dan analisis sesi berasal 100% dari API Android resmi dan penyimpanan lokal Room/DataStore tanpa modifikasi file game maupun sistem anti-cheat.",
      ),
      AppUpdateEntry(
        versionNumber = "1.9.0",
        releaseDate = "2026-11-23",
        title = "DRX Game Notification Viewer & Smart Update Center",
        badges = listOf(UpdateBadgeType.NEW_FEATURE, UpdateBadgeType.IMPROVEMENT, UpdateBadgeType.SECURITY),
        newFeatures =
          listOf(
            "Menghadirkan DRX Game Notification Viewer berbasis NotificationListenerService resmi Android untuk membaca & mengelola riwayat notifikasi game dengan tab Menurut Tanggal dan Menurut Game.",
            "Menambahkan DRX Notification Insights berisi total notifikasi, game paling aktif, jam tersibuk, grafik harian & mingguan, serta distribusi per-game langsung dari data lokal Room.",
            "Menambahkan DRX Game Event Tracker untuk klasifikasi otomatis Event, Reward, Update, Maintenance, Tournament, dan Reminder lengkap dengan penjadwal pengingat WorkManager.",
            "Menghadirkan DRX Game Activity Center sebagai pusat ringkasan game terakhir dimainkan, total playtime, notifikasi game, event terbaru, rekaman layar, dan tangkapan layar.",
            "Menambahkan DRX Update Center dengan deteksi APK rilis vs debug, verifikasi arsip + SHA-256 + tanda tangan paket, instalasi via PackageInstaller/FileProvider, serta Smart Update Skip Policy (2 kali tunda opsional, ke-3 wajib diperbarui).",
          ),
        improvements =
          listOf(
            "Menambahkan kontrol privasi notifikasi di Setelan & menu Game Notifications: Add as Game, Exclude Game, Clear History, dan pengaman otomatis yang memblokir/menyensor pola OTP, PIN, serta password.",
            "Menstandarkan sistem tipografi modern Inter & Manrope di seluruh halaman baru dan memperbaiki seluruh peringatan Android Lint & Kotlin hingga bersih.",
          ),
        bugFixes =
          listOf(
            "Memastikan halaman Game Notifications & Insights menampilkan status izin dan empty state secara jujur tanpa data notifikasi palsu.",
            "Memperbarui skrip drx_auto_build.sh dengan keluaran APK terversi DRX-GAME-TURBO-PRO-v1.9.0.apk beserta SHA256SUMS.txt dan manifest APK.",
          ),
        performanceImprovements =
          listOf(
            "Menyimpan riwayat notifikasi 100% lokal di Room Database v4 (MIGRATION_3_4) dengan deduplikasi key serta pembersihan cache thumbnail terkelola.",
          ),
        additionalNotes =
          "DRX hanya mengumpulkan notifikasi game sejak akses notifikasi diaktifkan oleh pengguna, tanpa AccessibilityService dan tanpa modifikasi file game.",
      ),
      AppUpdateEntry(
        versionNumber = "1.3.0",
        releaseDate = "2026-11-21",
        title = "DRX Game Statistics & Customization Center",
        badges = listOf(UpdateBadgeType.NEW_FEATURE, UpdateBadgeType.IMPROVEMENT, UpdateBadgeType.SECURITY),
        newFeatures =
          listOf(
            "Menghadirkan DRX Game Statistics per-game: Info Paket, Version Code, Tanggal Pertama Kali Dipasang, Last Update, Sumber Instalasi, Ukuran APK, dan Storage Data/Cache nyata lewat StorageStatsManager.",
            "Menambahkan penggabungan cerdas DRX Room Session + Android UsageStatsManager (berlabel Estimated) tanpa penghitungan ganda untuk statistik Hari Ini, Kemarin, 7 Hari, 30 Hari, Rata-rata, Sesi Terlama, dan Hari Tersibuk.",
            "Memperbarui Global Game Statistics dengan metrik total game, game paling sering dimainkan & dibuka, grafik 7 hari & 30 hari, serta filter riwayat sesi.",
            "Menambahkan DRX Customization Center dengan 7 preset tema (DRX Default, AMOLED Black, Cyberpunk Blue, RGB Gaming, Purple Neon, Red Inferno, Minimal Dark), aksen kustom, wallpaper Photo Picker, dan roadmap Editions.",
            "Memperluas DRX Daily Messages menjadi 105+ pesan unik dalam 7 kategori plus generator kombinasi kalimat, anti-kategori berulang berturut-turut, dan tombol Reset Message Rotation.",
          ),
        improvements =
          listOf(
            "Menambahkan pengurutan Game Library (Nama, Playtime, Terakhir Main), tampilan kartu berstatistik, dan tombol VIEW GAME STATISTICS.",
            "Menambahkan Start/Stop Monitoring serta Performance Report modal di Device Monitor, dan Soundboard FX di Voice Studio.",
            "Menampilkan zona waktu lokal akurat termasuk WIB untuk zona Asia/Jakarta dan tooltip Info penjelas metrik.",
          ),
        bugFixes =
          listOf(
            "Memastikan ukuran data/cache yang dibatasi OS tampil jujur sebagai Not Available tanpa angka estimasi palsu.",
          ),
        performanceImprovements =
          listOf(
            "Batch query UsageStatsManager dan caching ikon aplikasi untuk scrolling Game Library yang tetap mulus.",
          ),
        additionalNotes =
          "Izin Usage Access bersifat opsional namun direkomendasikan agar UsageStats & StorageStatsManager dapat membaca data statistik aplikasi penuh.",
      ),
      AppUpdateEntry(
        versionNumber = "1.2.0",
        releaseDate = "2026-11-20",
        title = "DRX Update Log & DRX Smart Announcement",
        badges = listOf(UpdateBadgeType.NEW_FEATURE, UpdateBadgeType.IMPROVEMENT),
        newFeatures =
          listOf(
            "Menambahkan halaman DRX Update Log lengkap dengan timeline kronologis terbaru hingga terlama, tombol What's New, dan status dibaca lokal.",
            "Menghadirkan DRX Smart Announcement berbasis WorkManager yang menjadwalkan 1–3 notifikasi acak per hari ke panel notifikasi Android.",
            "Menambahkan halaman Announcement Center di Setelan untuk mengatur frekuensi harian, Quiet Hours, filter 6 kategori, suara/getaran, preview notifikasi, dan riwayat pengumuman Room.",
            "Menyertakan pintasan Update Log berlabel NEW di Dashboard saat ada catatan rilis yang belum dibaca.",
          ),
        improvements =
          listOf(
            "Menambah BroadcastReceiver untuk BOOT_COMPLETED, TIMEZONE_CHANGED, dan TIME_SET agar jadwal acak tetap terjaga.",
            "Menambahkan dukungan deep-link ketukan notifikasi menuju halaman terkait di aplikasi.",
            "Menyegarkan halaman About dengan akses langsung ke DRX Update Log.",
          ),
        bugFixes =
          listOf(
            "Memastikan saluran notifikasi DRX Announcements mendukung perubahan setelan suara dan getaran pada Android 8–16.",
          ),
        performanceImprovements =
          listOf(
            "Menggunakan penjadwalan WorkManager hemat baterai tanpa exact alarm atau background loop aktif terus-menerus.",
            "Menjaga penanda duplikasi pesan agar pengumuman yang baru tampil tidak langsung berulang.",
          ),
        additionalNotes =
          "Catatan rilis ini hanya mencatat fitur nyata yang terpasang di DRX GAME TURBO PRO v1.2.0. Pastikan izin notifikasi aktif di Android 13+.",
      ),
      AppUpdateEntry(
        versionNumber = "1.1.0",
        releaseDate = "2026-11-12",
        title = "About DRX Developer Profile & Elite Dark UI",
        badges = listOf(UpdateBadgeType.NEW_FEATURE, UpdateBadgeType.IMPROVEMENT, UpdateBadgeType.SECURITY),
        newFeatures =
          listOf(
            "Menghadirkan halaman About DRX GAME TURBO PRO bergaya AMOLED hitam dengan aksen neon biru dan ungu untuk profil resmi DerreX.",
            "Menambahkan kartu Social Connections (YouTube DerreXSA, TikTok @ahmdmlyadi_, WhatsApp DRX Official Channel) beserta tombol salin & buka link.",
            "Menambahkan kartu apresiasi Supported By NANAY (@naila27302) dengan avatar custom via Android Photo Picker di Setelan.",
            "Menambahkan panel Social Counters yang menampilkan status koneksi nyata serta tanggal build aplikasi dinamis.",
          ),
        improvements =
          listOf(
            "Memperbarui modal Privacy Policy, Terms of Service, Open Source Licenses, dan Check for Updates.",
            "Menyelaraskan navigasi About tunggal dari Profile dan Setelan tanpa halaman duplikat.",
          ),
        bugFixes =
          listOf(
            "Mengatasi tampilan metrik sosial yang membingungkan dengan label Data Unavailable yang jujur saat jaringan offline atau dibatasi.",
          ),
        performanceImprovements =
          listOf(
            "Menggunakan pengecekan HTTP HEAD ringan untuk Social Counters dengan fallback aman dan bebas blocking UI.",
          ),
        additionalNotes =
          "Semua data pribadi pengguna tetap 100% berada di perangkat menggunakan DataStore & Room.",
      ),
      AppUpdateEntry(
        versionNumber = "1.0.0",
        releaseDate = "2026-11-01",
        title = "DRX GAME TURBO PRO Initial Suite",
        badges = listOf(UpdateBadgeType.NEW_FEATURE, UpdateBadgeType.SECURITY),
        newFeatures =
          listOf(
            "Dashboard Elite Dark Technology dengan tombol BOOST NOW, 4 gauge telemetri (CPU, RAM, Baterai, Suhu), Storage bar, dan My Games.",
            "Game Library dengan pemindaian aplikasi launcher terpasang, profil per-game, favorit, pencarian, serta Game Launch Overlay 60 FPS.",
            "Edge-Swipe Floating Control Center 5-tab (Home, Apps, Voice, Tools, Monitor) serta DRX Floating Mini-Windows (Calculator, Notes, Timer, HUD).",
            "Voice Studio 10 preset suara real-time via AudioRecord, Gaming Sound Center 5-Band EQ, Network Monitor TCP, Multi-Touch Canvas, dan Statistics.",
          ),
        improvements =
          listOf(
            "Antarmuka dwibahasa penuh (Indonesia & English) dengan dukungan layar compact maupun tablet wide-screen NavigationRail.",
          ),
        bugFixes =
          listOf(
            "Menambahkan penanganan izin Android secara eksplisit (Overlay, Write Settings, DND, Usage Stats, Audio, Notifications) lengkap dengan transparansi batas OS.",
          ),
        performanceImprovements =
          listOf(
            "Penyimpanan sesi & catatan game lokal lewat Room Database serta manajemen preferensi reaktif menggunakan DataStore.",
          ),
        additionalNotes =
          "Dibangun dengan Kotlin, Jetpack Compose, dan Material 3 oleh DerreX tanpa memerlukan akses root.",
      ),
    )

  val latestVersion: String
    get() = entries.first().versionNumber
}
