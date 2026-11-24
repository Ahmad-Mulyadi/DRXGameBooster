package com.example.data.model

import kotlin.random.Random

enum class AnnouncementCategory(
  val id: String,
  val displayNameId: String,
  val displayNameEn: String,
  val notificationTitle: String,
) {
  GAMING_TIPS("gaming_tips", "DRX Gaming Tips", "DRX Gaming Tips", "DRX GAMING TIPS"),
  FEATURE_DISCOVERY("feature_discovery", "DRX Feature Discovery", "DRX Feature Discovery", "DRX SYSTEM"),
  NEWS("news", "System Announcement", "System Announcement", "DRX SYSTEM ANNOUNCEMENT"),
  MOTIVATION("motivation", "Motivation", "Motivation", "DRX MOTIVATION"),
  PREMIUM_VIBES("premium_vibes", "DRX Premium Vibes", "DRX Premium Vibes", "DRX PRO VIBES"),
  COMMUNITY("community", "DRX Community", "DRX Community", "DRX COMMUNITY"),
  FUN("fun", "Fun Message", "Fun Message", "DRX DAILY FUN");

  fun localizedName(isIndo: Boolean): String = if (isIndo) displayNameId else displayNameEn
}

data class AnnouncementMessage(
  val id: String,
  val category: AnnouncementCategory,
  val body: String,
  val targetRoute: String = "home",
)

object AnnouncementSentenceGenerator {
  private val openingsByCategory =
    mapOf(
      AnnouncementCategory.GAMING_TIPS to listOf(
        "DRX Pro Tip:",
        "Persiapan Match:",
        "Optimasi Sesi:",
        "Tips Taktis DRX:",
      ),
      AnnouncementCategory.FEATURE_DISCOVERY to listOf(
        "Jelajahi DRX:",
        "Fitur Tersembunyi:",
        "Tahukah Kamu?",
        "Coba Sekarang:",
      ),
      AnnouncementCategory.NEWS to listOf(
        "DRX System:",
        "Pembaruan Lokal:",
        "Status Sistem:",
        "DRX Info:",
      ),
      AnnouncementCategory.MOTIVATION to listOf(
        "Fokus Arena:",
        "Mentalitas Juara:",
        "Siap Tempur?",
        "Semangat Push Rank:",
      ),
      AnnouncementCategory.PREMIUM_VIBES to listOf(
        "Elite Dark Mode:",
        "DRX Cockpit:",
        "Precision Gaming:",
        "Next-Level Setup:",
      ),
      AnnouncementCategory.COMMUNITY to listOf(
        "DRX Family:",
        "Dari DerreX:",
        "Komunitas DRX:",
        "Apresiasi DRX:",
      ),
      AnnouncementCategory.FUN to listOf(
        "Break Time Check:",
        "Daily Vibe:",
        "Arena Calling:",
        "Gaming Check:",
      ),
    )

  private val bodiesByCategory =
    mapOf(
      AnnouncementCategory.GAMING_TIPS to listOf(
        "Cek ping TCP dan jitter jaringan sebelum terjun ke mode ranked",
        "Aktifkan Do Not Disturb via Smart Booster agar notifikasi tidak mengganggu fokus",
        "Atur profil per-game secara spesifik untuk efisiensi baterai dan performa",
        "Pantau suhu baterai di Device Monitor saat sesi bermain panjang",
      ),
      AnnouncementCategory.FEATURE_DISCOVERY to listOf(
        "Buka Game Statistics untuk melihat total waktu main, ukuran APK, dan riwayat sesi",
        "Sesuaikan 7 tema warna di DRX Customization Center sesuai gaya cockpit favoritmu",
        "Gunakan Floating Control Center 5-tab langsung dari tepi layar saat bermain",
        "Coba Soundboard dan 10 efek Voice Studio secara langsung dengan mikrofon",
      ),
      AnnouncementCategory.NEWS to listOf(
        "DRX Update Log v1.3.0 kini hadir dengan Game Statistics dan DRX Customization Center",
        "Penjadwalan harian bekerja hemat baterai melalui Android WorkManager tanpa background loop",
        "Seluruh riwayat sesi dan statistik tetap tersimpan lokal dan aman di perangkatmu",
        "Integrasi Usage Access opsional kini menyajikan data aktivitas 7 hari dan 30 hari",
      ),
      AnnouncementCategory.MOTIVATION to listOf(
        "Setiap sesi latihan membangun refleks lebih tajam dan keputusan lebih tenang",
        "Konsistensi dan ketenangan strategi selalu memberi keunggulan di momen penentuan",
        "Evaluasi statistik bermainmu dan terus tingkatkan performa dari hari ke hari",
        "Jangan terburu-buru di late game — kontrol ritme dan menangkan pertandingan",
      ),
      AnnouncementCategory.PREMIUM_VIBES to listOf(
        "Desain Elite Dark Technology menjaga mata nyaman dengan aksen visual presisi",
        "Pengalaman dashboard bersih, responsif, dan ringan di berbagai perangkat Android",
        "Tema AMOLED Black hingga Cyberpunk Blue siap menyempurnakan tampilan gaming-mu",
        "Setiap kontrol dirancang cepat diakses untuk pengalaman companion flagship",
      ),
      AnnouncementCategory.COMMUNITY to listOf(
        "Terima kasih telah mendukung karya DerreX dan keluarga besar DRX GAME TURBO PRO",
        "Kunjungi halaman About untuk terhubung ke YouTube DerreXSA, TikTok, dan WhatsApp Channel",
        "Apresiasi khusus kepada Nanay (@naila27302) yang terus mendukung pengembangan DRX",
        "Masukan komunitas menjadi bahan bakar inovasi setiap pembaruan DRX",
      ),
      AnnouncementCategory.FUN to listOf(
        "Sudah minum air putih sebelum lanjut matchmaking berikutnya?",
        "Jari mulai panas? Istirahatkan mata 2 menit dan cek suhu perangkat",
        "Ayo cek game mana yang paling sering kamu mainkan minggu ini di Game Statistics",
        "Waktunya pamer skill terbaikmu hari ini — Play Smarter, Game Faster",
      ),
    )

  private val closingsByCategory =
    mapOf(
      AnnouncementCategory.GAMING_TIPS to listOf("— Siap menuju kemenangan!", "• Optimalkan setup sekarang."),
      AnnouncementCategory.FEATURE_DISCOVERY to listOf("• Ketuk untuk mencobanya!", "— Eksplorasi lebih jauh di DRX."),
      AnnouncementCategory.NEWS to listOf("• Cek detail di aplikasi.", "— Tetap up to date bersama DRX."),
      AnnouncementCategory.MOTIVATION to listOf("— Stay focused, play smart!", "• Waktunya tunjukkan performa terbaik."),
      AnnouncementCategory.PREMIUM_VIBES to listOf("— Crafted for precision gamers.", "• DRX Elite Dark Technology."),
      AnnouncementCategory.COMMUNITY to listOf("— Power Your Game!", "• Bersama komunitas DRX."),
      AnnouncementCategory.FUN to listOf("— Good luck, have fun!", "• Sampai jumpa di arena!"),
    )

  fun generateCombinedMessage(
    category: AnnouncementCategory,
    random: Random = Random.Default,
  ): AnnouncementMessage {
    val openings = openingsByCategory[category] ?: listOf("DRX:")
    val bodies = bodiesByCategory[category] ?: listOf("Optimalkan sesi bermainmu hari ini")
    val closings = closingsByCategory[category] ?: listOf("— DRX GAME TURBO PRO")

    val o = openings[random.nextInt(openings.size)]
    val b = bodies[random.nextInt(bodies.size)]
    val c = closings[random.nextInt(closings.size)]
    val route =
      when (category) {
        AnnouncementCategory.GAMING_TIPS -> "tool_booster"
        AnnouncementCategory.FEATURE_DISCOVERY -> "tools"
        AnnouncementCategory.NEWS -> "update_log"
        AnnouncementCategory.PREMIUM_VIBES -> "customization"
        AnnouncementCategory.COMMUNITY -> "about"
        AnnouncementCategory.FUN -> "tool_stats"
        AnnouncementCategory.MOTIVATION -> "games"
      }
    return AnnouncementMessage(
      id = "combo_${category.id}_${o.hashCode() and 0xFFF}_${b.hashCode() and 0xFFF}",
      category = category,
      body = "$o $b $c",
      targetRoute = route,
    )
  }
}

object AnnouncementCatalog {

  val allMessages: List<AnnouncementMessage> =
    listOf(
      // 1. DRX Gaming Tips (16)
      AnnouncementMessage("tip_01", AnnouncementCategory.GAMING_TIPS, "DRX TIP: Tutup aplikasi yang tidak diperlukan sebelum memulai sesi gaming.", "tool_booster"),
      AnnouncementMessage("tip_02", AnnouncementCategory.GAMING_TIPS, "Gaming session dimulai? Pastikan pengaturan audio dan display sudah sesuai.", "tool_display"),
      AnnouncementMessage("tip_03", AnnouncementCategory.GAMING_TIPS, "Periksa koneksi jaringan dan TCP ping sebelum masuk pertandingan ranked.", "tool_network"),
      AnnouncementMessage("tip_04", AnnouncementCategory.GAMING_TIPS, "Gunakan DRX Floating Sidebar untuk akses tools dan mini-windows lebih cepat.", "tool_sidebar"),
      AnnouncementMessage("tip_05", AnnouncementCategory.GAMING_TIPS, "Aktifkan Do Not Disturb lewat Smart Game Booster agar notifikasi chat tidak mengganggu fokus.", "tool_booster"),
      AnnouncementMessage("tip_06", AnnouncementCategory.GAMING_TIPS, "Suhu perangkat terasa hangat? Beralihlah ke profil Balanced atau Battery Saver sementara.", "tool_monitor"),
      AnnouncementMessage("tip_07", AnnouncementCategory.GAMING_TIPS, "Sesuaikan kecerahan layar optimal agar baterai lebih hemat dan mata tetap nyaman.", "tool_booster"),
      AnnouncementMessage("tip_08", AnnouncementCategory.GAMING_TIPS, "Gunakan Gaming Sound Center dengan preset FPS Footsteps untuk detail arah audio lebih tajam.", "tool_sound"),
      AnnouncementMessage("tip_09", AnnouncementCategory.GAMING_TIPS, "Cek Ping RTT & Jitter lewat Network Monitor sebelum bermain mode kompetitif.", "tool_network"),
      AnnouncementMessage("tip_10", AnnouncementCategory.GAMING_TIPS, "Kunci rotasi layar sesuai kebutuhan agar layar tidak berputar mendadak saat bermain.", "tool_booster"),
      AnnouncementMessage("tip_11", AnnouncementCategory.GAMING_TIPS, "Pastikan sisa ruang penyimpanan internal cukup lapang agar loading aset game tetap lancar.", "tool_stats"),
      AnnouncementMessage("tip_12", AnnouncementCategory.GAMING_TIPS, "Bersihkan cache internal DRX dengan satu ketukan BOOST NOW sebelum meluncurkan game.", "home"),
      AnnouncementMessage("tip_13", AnnouncementCategory.GAMING_TIPS, "Pantau kestabilan sentuhan layarmu di Multi-Touch Canvas sebelum turnamen.", "tool_display"),
      AnnouncementMessage("tip_14", AnnouncementCategory.GAMING_TIPS, "Gunakan profil Performance saat daya baterai penuh dan pengisian daya tidak terpasang.", "tool_profiles"),
      AnnouncementMessage("tip_15", AnnouncementCategory.GAMING_TIPS, "Pasang Gaming Timer 45 menit agar sesi bermain tetap terjaga dan tidak kelelahan.", "tool_utilities"),
      AnnouncementMessage("tip_16", AnnouncementCategory.GAMING_TIPS, "Periksa riwayat suhu puncak tiap sesi game di halaman Game Statistics untuk mencegah thermal throttling.", "tool_stats"),

      // 2. DRX Feature Discovery (16)
      AnnouncementMessage("feat_01", AnnouncementCategory.FEATURE_DISCOVERY, "Sudah mencoba Edge Swipe? Tarik dari sisi layar untuk membuka DRX Control Center 5-tab.", "tool_sidebar"),
      AnnouncementMessage("feat_02", AnnouncementCategory.FEATURE_DISCOVERY, "Voice Studio siap digunakan untuk mencoba 10 preset suara dan efek soundboard.", "tool_voice"),
      AnnouncementMessage("feat_03", AnnouncementCategory.FEATURE_DISCOVERY, "Urutkan Game Library berdasarkan Nama, Total Playtime, atau Terakhir Dimainkan.", "games"),
      AnnouncementMessage("feat_04", AnnouncementCategory.FEATURE_DISCOVERY, "Buka Game Details lalu ketuk VIEW GAME STATISTICS untuk melihat ukuran APK dan statistik 30 hari.", "games"),
      AnnouncementMessage("feat_05", AnnouncementCategory.FEATURE_DISCOVERY, "Tahukah kamu? Mini-Windows Kalkulator, Timer, dan Catatan dapat digeser di atas game lewat Sidebar.", "tool_sidebar"),
      AnnouncementMessage("feat_06", AnnouncementCategory.FEATURE_DISCOVERY, "Uji sensitivitas layar dan laju sentuhan Hz perangkatmu dengan Kanvas Multi-Touch.", "tool_display"),
      AnnouncementMessage("feat_07", AnnouncementCategory.FEATURE_DISCOVERY, "Mulai dan hentikan sesi rekaman Device Monitor serta ekspor ringkasan Performance Report.", "tool_monitor"),
      AnnouncementMessage("feat_08", AnnouncementCategory.FEATURE_DISCOVERY, "Atur profil performa khusus untuk setiap game di halaman Game Details.", "games"),
      AnnouncementMessage("feat_09", AnnouncementCategory.FEATURE_DISCOVERY, "Coba Equalizer 5-Band & Virtual Surround di Audio Center untuk pengalaman suara maksimal.", "tool_sound"),
      AnnouncementMessage("feat_10", AnnouncementCategory.FEATURE_DISCOVERY, "Gunakan Gaming Timer untuk mengatur batas waktu istirahat di sela-sela sesi bermain.", "tool_utilities"),
      AnnouncementMessage("feat_11", AnnouncementCategory.FEATURE_DISCOVERY, "Kini tersedia 7 tema cockpit di DRX Customization Center, termasuk AMOLED Black dan Cyberpunk Blue.", "customization"),
      AnnouncementMessage("feat_12", AnnouncementCategory.FEATURE_DISCOVERY, "Pasang wallpaper latar kustom dari galerimu langsung lewat Android Photo Picker di Customization Center.", "customization"),
      AnnouncementMessage("feat_13", AnnouncementCategory.FEATURE_DISCOVERY, "Ketuk ikon info kecil di kartu statistik untuk memahami sumber data DRX vs UsageStats Android.", "tool_stats"),
      AnnouncementMessage("feat_14", AnnouncementCategory.FEATURE_DISCOVERY, "Simpan catatan strategi atau sensitivitas aim per game di Tactical Notes.", "tool_utilities"),
      AnnouncementMessage("feat_15", AnnouncementCategory.FEATURE_DISCOVERY, "Gunakan tombol Reset Message Rotation di Notification Center untuk menyegarkan antrean pesan harian.", "announcement_center"),
      AnnouncementMessage("feat_16", AnnouncementCategory.FEATURE_DISCOVERY, "Aktifkan Game Launch Intro Animation bergaya 60 FPS untuk sensasi pembukaan match yang sinematik.", "settings"),

      // 3. System Announcement (15)
      AnnouncementMessage("news_01", AnnouncementCategory.NEWS, "DRX UPDATE v1.3.0: Game Statistics lengkap & DRX Customization Center kini tersedia!", "update_log"),
      AnnouncementMessage("news_02", AnnouncementCategory.NEWS, "DRX SYSTEM UPDATE: Periksa halaman Update Log untuk melihat rincian fitur terbaru.", "update_log"),
      AnnouncementMessage("news_03", AnnouncementCategory.NEWS, "DRX SYSTEM: Seluruh pemrosesan data dan riwayat bermain tetap 100% lokal di perangkatmu.", "about"),
      AnnouncementMessage("news_04", AnnouncementCategory.NEWS, "DRX DAILY MESSAGES: Kini dilengkapi 100+ pesan terkurasi dengan perlindungan anti-duplikasi.", "announcement_center"),
      AnnouncementMessage("news_05", AnnouncementCategory.NEWS, "DRX UPDATE LOG: Cek daftar pembaruan resmi versi terbaru langsung dari aplikasi.", "update_log"),
      AnnouncementMessage("news_06", AnnouncementCategory.NEWS, "DRX PROFILE: Kunjungi halaman About untuk terhubung dengan kanal resmi DerreX.", "about"),
      AnnouncementMessage("news_07", AnnouncementCategory.NEWS, "Voice Studio kini mendukung 10 preset suara, pad soundboard, dan meter level mikrofon real-time.", "tool_voice"),
      AnnouncementMessage("news_08", AnnouncementCategory.NEWS, "Edge Floating Control Center memiliki 5 tab lengkap: Home, Apps, Voice, Tools, dan Monitor.", "tool_sidebar"),
      AnnouncementMessage("news_09", AnnouncementCategory.NEWS, "DRX SYSTEM: Kamu bisa menyesuaikan Quiet Hours notifikasi di Notification Center kapan saja.", "announcement_center"),
      AnnouncementMessage("news_10", AnnouncementCategory.NEWS, "DRX STATS ENGINE: Menggabungkan sesi lokal DRX dan Android UsageStats tanpa penghitungan ganda.", "tool_stats"),
      AnnouncementMessage("news_11", AnnouncementCategory.NEWS, "Transparansi Storage: DRX menampilkan ukuran APK nyata dan hanya membaca data/cache jika diizinkan Android.", "games"),
      AnnouncementMessage("news_12", AnnouncementCategory.NEWS, "DRX EDITIONS: Lihat roadmap fitur DRX FREE, DRX PRO, dan DRX CREATOR di Customization Center.", "customization"),
      AnnouncementMessage("news_13", AnnouncementCategory.NEWS, "Penjadwalan WorkManager otomatis menyesuaikan ulang setelah perangkat restart atau ganti zona waktu.", "announcement_center"),
      AnnouncementMessage("news_14", AnnouncementCategory.NEWS, "Zona waktu lokal termasuk WIB terdeteksi otomatis untuk akurasi jadwal dan riwayat bermain.", "tool_stats"),
      AnnouncementMessage("news_15", AnnouncementCategory.NEWS, "Terima kasih telah memperbarui ke DRX GAME TURBO PRO generasi terbaru!", "update_log"),

      // 4. Motivation (16)
      AnnouncementMessage("mot_01", AnnouncementCategory.MOTIVATION, "Stay focused. Play smart. Keep improving.", "home"),
      AnnouncementMessage("mot_02", AnnouncementCategory.MOTIVATION, "Setiap pertandingan adalah kesempatan untuk berkembang.", "home"),
      AnnouncementMessage("mot_03", AnnouncementCategory.MOTIVATION, "Your game. Your strategy. Your experience.", "games"),
      AnnouncementMessage("mot_04", AnnouncementCategory.MOTIVATION, "Keep your setup ready for the next match.", "tool_booster"),
      AnnouncementMessage("mot_05", AnnouncementCategory.MOTIVATION, "Ketepatan strategi dan ketenangan pikiran selalu mengalahkan terburu-buru.", "home"),
      AnnouncementMessage("mot_06", AnnouncementCategory.MOTIVATION, "Satu langkah taktis hari ini membawa kemenangan besar berikutnya.", "games"),
      AnnouncementMessage("mot_07", AnnouncementCategory.MOTIVATION, "Latih konsistensimu, cek statistik bermainmu, dan terus tingkatkan ritme permainan.", "tool_stats"),
      AnnouncementMessage("mot_08", AnnouncementCategory.MOTIVATION, "Play smarter, jaga stamina baterai & suhu perangkat agar sesi panjang tetap nyaman.", "tool_monitor"),
      AnnouncementMessage("mot_09", AnnouncementCategory.MOTIVATION, "Persiapan perangkat yang rapi adalah separuh dari fokus kemenangan.", "tool_booster"),
      AnnouncementMessage("mot_10", AnnouncementCategory.MOTIVATION, "Ready for battle? Mulai game favoritmu lewat launcher DRX sekarang.", "games"),
      AnnouncementMessage("mot_11", AnnouncementCategory.MOTIVATION, "Kekalahan bukan akhir — pelajari pola, perbaiki eksekusi, dan bangkit lebih kuat.", "home"),
      AnnouncementMessage("mot_12", AnnouncementCategory.MOTIVATION, "Refleks cepat lahir dari latihan disiplin dan pikiran yang jernih.", "games"),
      AnnouncementMessage("mot_13", AnnouncementCategory.MOTIVATION, "Fokus pada hal yang bisa kamu kendalikan: komunikasi tim, posisi, dan timing.", "home"),
      AnnouncementMessage("mot_14", AnnouncementCategory.MOTIVATION, "Pemain hebat tidak hanya mengandalkan mekanik, tapi juga konsistensi membaca peta.", "home"),
      AnnouncementMessage("mot_15", AnnouncementCategory.MOTIVATION, "Jadikan setiap hari satu persen lebih tajam dari hari kemarin.", "tool_stats"),
      AnnouncementMessage("mot_16", AnnouncementCategory.MOTIVATION, "Disiplin dalam persiapan, tenang dalam tekanan, presisi dalam eksekusi.", "home"),

      // 5. DRX Premium Vibes (15)
      AnnouncementMessage("pro_01", AnnouncementCategory.PREMIUM_VIBES, "ELITE DARK TECHNOLOGY — Antarmuka cockpit gelap presisi tanpa clutter yang mengganggu.", "customization"),
      AnnouncementMessage("pro_02", AnnouncementCategory.PREMIUM_VIBES, "Rasakan transisi antarmuka mulus 60 FPS dengan palet Black Obsidian & Electric Blue.", "customization"),
      AnnouncementMessage("pro_03", AnnouncementCategory.PREMIUM_VIBES, "Ubah nuansa ruang kendalimu ke Cyberpunk Blue, Purple Neon, atau Red Inferno kapan saja.", "customization"),
      AnnouncementMessage("pro_04", AnnouncementCategory.PREMIUM_VIBES, "Material glassmorphism minimalis menjaga fokus tetap pada informasi penting perangkat.", "home"),
      AnnouncementMessage("pro_05", AnnouncementCategory.PREMIUM_VIBES, "Mode AMOLED Black dirancang hemat daya pada layar OLED serta nyaman di ruangan redup.", "customization"),
      AnnouncementMessage("pro_06", AnnouncementCategory.PREMIUM_VIBES, "Setiap indikator gauge terkalibrasi untuk pembacaan instan dalam hitungan milidetik.", "tool_monitor"),
      AnnouncementMessage("pro_07", AnnouncementCategory.PREMIUM_VIBES, "Custom Accent & Wallpaper memberi sentuhan personal eksklusif pada setup gaming-mu.", "customization"),
      AnnouncementMessage("pro_08", AnnouncementCategory.PREMIUM_VIBES, "Minimal Dark hadir untuk gamer yang menyukai ketenangan visual bersih dan elegan.", "customization"),
      AnnouncementMessage("pro_09", AnnouncementCategory.PREMIUM_VIBES, "Kombinasi tipografi teknis dan jarak lega membuat statistik panjang mudah dipindai.", "tool_stats"),
      AnnouncementMessage("pro_10", AnnouncementCategory.PREMIUM_VIBES, "Cinematic Game Launch Intro siap menyambut sesi bermain dengan identitas DRX.", "settings"),
      AnnouncementMessage("pro_11", AnnouncementCategory.PREMIUM_VIBES, "Dukungan layar tablet dan foldable memastikan panel statistik tetap proporsional dan rapi.", "tool_stats"),
      AnnouncementMessage("pro_12", AnnouncementCategory.PREMIUM_VIBES, "RGB Gaming theme menghadirkan energi kompetitif dengan aksen emerald dan electric blue.", "customization"),
      AnnouncementMessage("pro_13", AnnouncementCategory.PREMIUM_VIBES, "Kontrol haptic yang halus memberi konfirmasi fisik di setiap tombol aksi utama.", "customization"),
      AnnouncementMessage("pro_14", AnnouncementCategory.PREMIUM_VIBES, "Dibangun dengan prinsip kejujuran data — performa flagship tanpa janji palsu.", "about"),
      AnnouncementMessage("pro_15", AnnouncementCategory.PREMIUM_VIBES, "DRX GAME TURBO PRO: Craftsmanship digital cockpit oleh DerreX untuk Android.", "about"),

      // 6. DRX Community (15)
      AnnouncementMessage("com_01", AnnouncementCategory.COMMUNITY, "DRX COMMUNITY: Terima kasih telah menjadi bagian dari perjalanan DRX.", "about"),
      AnnouncementMessage("com_02", AnnouncementCategory.COMMUNITY, "Keep supporting DRX GAME TURBO PRO!", "about"),
      AnnouncementMessage("com_03", AnnouncementCategory.COMMUNITY, "More improvements, better gaming experience.", "update_log"),
      AnnouncementMessage("com_04", AnnouncementCategory.COMMUNITY, "DRX FAMILY — Power Your Game.", "about"),
      AnnouncementMessage("com_05", AnnouncementCategory.COMMUNITY, "Bergabunglah dengan DRX Official Channel di WhatsApp melalui halaman About.", "about"),
      AnnouncementMessage("com_06", AnnouncementCategory.COMMUNITY, "Terima kasih kepada seluruh komunitas dan Nanay atas dukungan bagi DRX GAME TURBO PRO.", "about"),
      AnnouncementMessage("com_07", AnnouncementCategory.COMMUNITY, "Punya ide fitur baru? Bagikan masukanmu di komunitas resmi DerreX!", "about"),
      AnnouncementMessage("com_08", AnnouncementCategory.COMMUNITY, "DRX COMMUNITY: Ikuti YouTube DerreXSA dan TikTok @ahmdmlyadi_ untuk info pembaruan.", "about"),
      AnnouncementMessage("com_09", AnnouncementCategory.COMMUNITY, "Bersama membangun pengalaman gaming companion Android yang bersih, jujur, dan modern.", "about"),
      AnnouncementMessage("com_10", AnnouncementCategory.COMMUNITY, "DRX GAME TURBO PRO — Dibuat oleh DerreX untuk para gamer Android.", "about"),
      AnnouncementMessage("com_11", AnnouncementCategory.COMMUNITY, "Bagikan tangkapan layar setup favoritmu bersama teman mabar hari ini.", "customization"),
      AnnouncementMessage("com_12", AnnouncementCategory.COMMUNITY, "Setiap pembaruan DRX lahir dari umpan balik nyata para pengguna Android di Indonesia & global.", "update_log"),
      AnnouncementMessage("com_13", AnnouncementCategory.COMMUNITY, "Dukung kreator independen Android dengan membagikan DRX GAME TURBO PRO ke skuadmu.", "about"),
      AnnouncementMessage("com_14", AnnouncementCategory.COMMUNITY, "Cek profil supporter resmi @naila27302 dan dukung terus ekosistem DRX.", "about"),
      AnnouncementMessage("com_15", AnnouncementCategory.COMMUNITY, "Komunitas solid dimulai dari permainan yang sportif dan saling menghargai.", "home"),

      // 7. Fun Message (15)
      AnnouncementMessage("fun_01", AnnouncementCategory.FUN, "Siap push rank hari ini?", "games"),
      AnnouncementMessage("fun_02", AnnouncementCategory.FUN, "Jangan lupa istirahat sejenak di sela-sela sesi gaming.", "tool_utilities"),
      AnnouncementMessage("fun_03", AnnouncementCategory.FUN, "DRX siap menemani sesi bermainmu hari ini!", "home"),
      AnnouncementMessage("fun_04", AnnouncementCategory.FUN, "Mode serius aktif? Ketuk BOOST NOW dulu sebelum terjun ke arena.", "home"),
      AnnouncementMessage("fun_05", AnnouncementCategory.FUN, "Minum air putih dulu biar refleks tetap cepat di late game!", "home"),
      AnnouncementMessage("fun_06", AnnouncementCategory.FUN, "Coba suara Robot atau Alien di Voice Studio untuk seru-seruan sebelum bermain.", "tool_voice"),
      AnnouncementMessage("fun_07", AnnouncementCategory.FUN, "Suhu jari sudah panas? Cek temperatur baterai di Device Monitor biar tetap aman.", "tool_monitor"),
      AnnouncementMessage("fun_08", AnnouncementCategory.FUN, "Pastikan baterai cukup sebelum mulai matchmaking panjang hari ini!", "home"),
      AnnouncementMessage("fun_09", AnnouncementCategory.FUN, "DRX SYSTEM: Cek ping dulu sebelum menyalahkan lag!", "tool_network"),
      AnnouncementMessage("fun_10", AnnouncementCategory.FUN, "Selamat bermain — Play Smarter, Game Faster bersama DRX!", "home"),
      AnnouncementMessage("fun_11", AnnouncementCategory.FUN, "Lose streak? Saatnya tarik napas, putar timer 10 menit, dan reset fokus!", "tool_utilities"),
      AnnouncementMessage("fun_12", AnnouncementCategory.FUN, "Win streak terasa lebih manis dengan statistik tercatat rapi di Game Statistics!", "tool_stats"),
      AnnouncementMessage("fun_13", AnnouncementCategory.FUN, "Jangan biarkan chat mantan membuyarkan war — aktifkan DND di Booster sekarang!", "tool_booster"),
      AnnouncementMessage("fun_14", AnnouncementCategory.FUN, "Coba Soundboard Airhorn atau Victory Fanfare di Voice Studio untuk merayakan clutch!", "tool_voice"),
      AnnouncementMessage("fun_15", AnnouncementCategory.FUN, "Hari baru, match baru, kesempatan MVP baru. Gas terus!", "games"),
    )
}
