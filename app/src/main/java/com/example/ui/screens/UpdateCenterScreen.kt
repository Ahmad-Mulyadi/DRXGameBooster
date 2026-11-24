package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.model.DrxUpdatePipelineState
import com.example.data.model.LanguageMode
import com.example.data.model.UpdateOfferPolicyState
import com.example.i18n.LocalStrings
import com.example.system.TimeAndSizeFormatters
import com.example.ui.theme.DrxAnimatedModalContainer
import com.example.ui.theme.DrxCustomProgressBar
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxHairlineDivider
import com.example.ui.theme.DrxNotificationBanner
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxUpdateUiState
import com.example.ui.viewmodel.DrxUpdateViewModel

@Composable
fun UpdateCenterScreen(
  updateViewModel: DrxUpdateViewModel,
  onOpenUpdateLog: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val prefs by updateViewModel.userPrefs.collectAsStateWithLifecycle()
  val uiState by updateViewModel.uiState.collectAsStateWithLifecycle()
  val installEvent by updateViewModel.installEvents.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  var manifestUrlInput by remember(prefs.customUpdateManifestUrl) {
    mutableStateOf(prefs.customUpdateManifestUrl)
  }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    updateViewModel.refreshInstallPermissionState()
    updateViewModel.checkForUpdatesAuto(forceRefresh = false)
  }

  val pickApkLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.OpenDocument()
    ) { uri ->
      if (uri != null) {
        updateViewModel.importAndVerifyLocalApk(uri)
      }
    }

  val statusColor =
    when (uiState.pipelineState) {
      DrxUpdatePipelineState.UP_TO_DATE,
      DrxUpdatePipelineState.READY_TO_INSTALL -> drxColors.optimalGreen
      DrxUpdatePipelineState.FAILED,
      DrxUpdatePipelineState.REJECTED_WARNING -> drxColors.alertCrimson
      DrxUpdatePipelineState.SOURCE_NOT_CONFIGURED,
      DrxUpdatePipelineState.OFFLINE -> drxColors.warningAmber
      DrxUpdatePipelineState.AVAILABLE -> drxColors.neonCyan
      else -> drxColors.electricBlue
    }

  val statusBadgeLabel =
    when (uiState.pipelineState) {
      DrxUpdatePipelineState.UP_TO_DATE -> if (isIndo) "TERBARU" else "UP TO DATE"
      DrxUpdatePipelineState.SOURCE_NOT_CONFIGURED -> "NOT CONFIGURED"
      DrxUpdatePipelineState.OFFLINE -> "OFFLINE"
      DrxUpdatePipelineState.REJECTED_WARNING -> if (isIndo) "DITOLAK" else "BLOCKED"
      DrxUpdatePipelineState.AVAILABLE -> "NEW UPDATE"
      DrxUpdatePipelineState.READY_TO_INSTALL -> if (isIndo) "SIAP PASANG" else "READY"
      else -> uiState.pipelineState.name
    }

  LazyColumn(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("update_center_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.updateCenterTitle,
        subtitle = strings.updateCenterSub,
        onBack = onBack,
      )
    }

    installEvent?.let { evt ->
      item {
        DrxNotificationBanner(
          title =
            if (evt.succeeded) (if (isIndo) "Instalasi Selesai" else "Install Succeeded")
            else (if (isIndo) "Status Installer" else "Installer Status"),
          body = evt.message,
          accentColor = if (evt.succeeded) drxColors.optimalGreen else drxColors.neonCyan,
          onDismiss = { updateViewModel.clearInstallEvent() },
        )
      }
    }

    // Current Installed Version Card
    item {
      DrxGlassCard(accentColor = drxColors.electricBlue, cornerRadius = 22.dp) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = if (isIndo) "Versi Terpasang Saat Ini" else "Current Version",
              style = MaterialTheme.typography.labelMedium,
              color = drxColors.neonCyan,
            )
            Text(
              text = "v${BuildConfig.VERSION_NAME} (Build #${BuildConfig.VERSION_CODE})",
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
              text = "${BuildConfig.APPLICATION_ID} • ${BuildConfig.BUILD_DATE}",
              style = DrxTypographyTokens.caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          DrxStatusPill(text = statusBadgeLabel, color = statusColor)
        }
        Spacer(Modifier.height(10.dp))
        Text(
          text = uiState.statusMessage,
          style = MaterialTheme.typography.bodySmall,
          color =
            when (uiState.pipelineState) {
              DrxUpdatePipelineState.FAILED,
              DrxUpdatePipelineState.REJECTED_WARNING -> drxColors.alertCrimson
              DrxUpdatePipelineState.OFFLINE,
              DrxUpdatePipelineState.SOURCE_NOT_CONFIGURED -> drxColors.warningAmber
              else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (uiState.lastCheckedAtMs > 0L) {
          Spacer(Modifier.height(4.dp))
          Text(
            text =
              "${if (isIndo) "Pemeriksaan terakhir" else "Last checked"}: ${TimeAndSizeFormatters.formatDateTimeWithZone(uiState.lastCheckedAtMs, isIndo)}${if (uiState.usingCachedManifest) " (Cached)" else ""}",
            style = DrxTypographyTokens.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          val checkBtnText =
            if (uiState.isOffline || uiState.pipelineState == DrxUpdatePipelineState.OFFLINE || uiState.pipelineState == DrxUpdatePipelineState.FAILED) {
              if (isIndo) "Cek Ulang" else "Check Again"
            } else {
              if (isIndo) "Periksa Pembaruan" else "Check Updates"
            }
          DrxPrimaryButton(
            label = checkBtnText,
            icon = Icons.Default.Refresh,
            onClick = {
              if (manifestUrlInput.isBlank()) updateViewModel.checkBundledManifest()
              else updateViewModel.checkForUpdatesFromUrl(manifestUrlInput)
            },
            modifier = Modifier.weight(1f).testTag("update_check_btn"),
          )
          DrxSecondaryButton(
            label = strings.whatsNewBtn,
            icon = Icons.Default.Update,
            onClick = onOpenUpdateLog,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    // Offline Banner
    if (uiState.pipelineState == DrxUpdatePipelineState.OFFLINE) {
      item {
        DrxGlassCard(
          accentColor = drxColors.warningAmber,
          highlighted = true,
          modifier = Modifier.testTag("update_offline_card"),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.CloudOff,
              contentDescription = "Offline",
              tint = drxColors.warningAmber,
            )
            Spacer(Modifier.width(10.dp))
            Text(
              text = if (isIndo) "Offline — Menampilkan Status Terakhir" else "Offline",
              style = MaterialTheme.typography.titleMedium,
              color = drxColors.warningAmber,
            )
          }
          Spacer(Modifier.height(6.dp))
          Text(
            text =
              if (isIndo) {
                "Koneksi internet tidak tersedia. Hubungkan jaringan lalu tekan Cek Ulang untuk mengambil manifest terbaru."
              } else {
                "No internet connection detected. Connect to a network and tap Check Again to fetch the latest update manifest."
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Source Not Configured Notice (zero fake download buttons!)
    if (uiState.pipelineState == DrxUpdatePipelineState.SOURCE_NOT_CONFIGURED ||
      (!uiState.isSourceConfigured && uiState.pipelineState == DrxUpdatePipelineState.UP_TO_DATE)
    ) {
      item {
        DrxGlassCard(
          accentColor = drxColors.warningAmber,
          modifier = Modifier.testTag("update_source_not_configured_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Update Source Not Configured",
              style = MaterialTheme.typography.titleMedium,
              color = drxColors.warningAmber,
            )
            DrxStatusPill(text = "HTTPS BRIDGE", color = drxColors.warningAmber)
          }
          Spacer(Modifier.height(6.dp))
          Text(
            text =
              if (isIndo) {
                "Sumber update OTA jarak jauh belum diatur. Unggah file src/apk/update-manifest.json & APK ke hosting HTTPS Anda lalu masukkan URL manifest di bawah, atau pilih APK terverifikasi secara lokal."
              } else {
                "Remote HTTPS update hosting is not configured yet. Host src/apk/update-manifest.json and the APK over HTTPS and enter its URL below, or pick a verified local APK."
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Developer Rejection / Policy Warning Card (same/lower versionCode, cert mismatch, appId mismatch)
    uiState.developerWarningMessage?.let { warn ->
      item {
        DrxGlassCard(
          accentColor = drxColors.alertCrimson,
          highlighted = true,
          modifier = Modifier.testTag("update_developer_warning_card"),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Warning,
              contentDescription = "Update Rejected",
              tint = drxColors.alertCrimson,
            )
            Spacer(Modifier.width(10.dp))
            Text(
              text = if (isIndo) "Pembaruan Ditolak / Peringatan Developer" else "Update Rejected / Developer Warning",
              style = MaterialTheme.typography.titleMedium,
              color = drxColors.alertCrimson,
            )
          }
          Spacer(Modifier.height(6.dp))
          Text(
            text = warn,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Target Manifest & Download / Verification / Install Card
    uiState.selectedManifest?.let { target ->
      item {
        val isReleaseType = target.apkType.equals("release", ignoreCase = true)
        DrxGlassCard(
          accentColor = if (isReleaseType) drxColors.optimalGreen else drxColors.warningAmber,
          highlighted = uiState.isUpdateAvailable,
          modifier = Modifier.testTag("update_target_manifest_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "${if (isIndo) "Versi Terbaru" else "Latest Version"}: v${target.versionName} (#${target.versionCode})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
              )
              Text(
                text = "${target.appId} • ${target.releaseDate}",
                style = DrxTypographyTokens.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              if (target.fileSizeBytes > 0L) {
                Text(
                  text = "APK Size: ${TimeAndSizeFormatters.formatBytesAuto(target.fileSizeBytes)} • Min SDK ${target.minSdkVersion}",
                  style = DrxTypographyTokens.caption,
                  color = drxColors.neonCyan,
                )
              }
            }
            DrxStatusPill(
              text = target.apkType.uppercase(),
              color = if (isReleaseType) drxColors.optimalGreen else drxColors.warningAmber,
            )
          }

          if (target.releaseNotes.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
              text = "What's New: ${target.releaseNotes}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          if (target.sha256.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
              text = "Manifest SHA-256: ${target.sha256.take(24)}...",
              style = DrxTypographyTokens.caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (target.signingCertSha256.isNotBlank()) {
            Text(
              text = "Manifest Cert SHA-256: ${target.signingCertSha256.take(20)}...",
              style = DrxTypographyTokens.caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          if (uiState.pipelineState == DrxUpdatePipelineState.DOWNLOADING) {
            Spacer(Modifier.height(10.dp))
            DrxCustomProgressBar(progress = uiState.downloadProgress.coerceIn(0f, 1f))
            Spacer(Modifier.height(4.dp))
            Text(
              text =
                if (uiState.totalBytes > 0L) {
                  "${TimeAndSizeFormatters.formatBytesAuto(uiState.downloadedBytes)} / ${TimeAndSizeFormatters.formatBytesAuto(uiState.totalBytes)}"
                } else {
                  TimeAndSizeFormatters.formatBytesAuto(uiState.downloadedBytes)
                },
              style = DrxTypographyTokens.caption,
              color = drxColors.neonCyan,
            )
          }

          uiState.verificationResult?.let { verif ->
            Spacer(Modifier.height(10.dp))
            DrxHairlineDivider()
            Spacer(Modifier.height(8.dp))
            Text(
              text =
                if (verif.isValid) "APK Verified • ${verif.packageName} v${verif.versionName} (#${verif.versionCode})"
                else "Verification Blocked: ${verif.errorReason}",
              style = MaterialTheme.typography.labelMedium,
              color = if (verif.isValid) drxColors.optimalGreen else drxColors.alertCrimson,
            )
            if (verif.sha256Full.isNotBlank()) {
              Text(
                text = "SHA-256: ${verif.sha256Full.take(24)}...",
                style = DrxTypographyTokens.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            verif.signatureMatchesInstalled?.let { matches ->
              Text(
                text =
                  if (matches) (if (isIndo) "Sertifikat penandatanganan cocok dengan aplikasi terpasang." else "Signing certificate matches installed app.")
                  else (if (isIndo) "DITOLAK: Sertifikat penandatanganan berbeda dari aplikasi terpasang." else "BLOCKED: Signing certificate differs from installed app."),
                style = DrxTypographyTokens.caption,
                color = if (matches) drxColors.optimalGreen else drxColors.alertCrimson,
              )
            }
          }

          // Strict action gating: never show fake Download/Install buttons for unconfigured, offline, rejected, or equal/older APKs
          if (uiState.isUpdateAvailable &&
            target.versionCode > BuildConfig.VERSION_CODE &&
            uiState.pipelineState != DrxUpdatePipelineState.REJECTED_WARNING
          ) {
            Spacer(Modifier.height(12.dp))
            when (uiState.pipelineState) {
              DrxUpdatePipelineState.AVAILABLE -> {
                DrxPrimaryButton(
                  label = if (isIndo) "Unduh & Verifikasi Update" else "Download Update",
                  icon = Icons.Default.Download,
                  onClick = { updateViewModel.startDownloadAndVerify() },
                  modifier = Modifier.fillMaxWidth().testTag("update_download_btn"),
                )
              }
              DrxUpdatePipelineState.FAILED -> {
                DrxPrimaryButton(
                  label = if (isIndo) "Coba Unduh Lagi" else "Retry Download",
                  icon = Icons.Default.Refresh,
                  onClick = { updateViewModel.startDownloadAndVerify() },
                  modifier = Modifier.fillMaxWidth().testTag("update_download_btn"),
                )
              }
              DrxUpdatePipelineState.READY_TO_INSTALL,
              DrxUpdatePipelineState.INSTALLING -> {
                DrxPrimaryButton(
                  label = if (isIndo) "Pasang Pembaruan (PackageInstaller)" else "Install Update (PackageInstaller)",
                  icon = Icons.Default.SystemUpdate,
                  accentColor = drxColors.optimalGreen,
                  onClick = { updateViewModel.installVerifiedApk() },
                  modifier = Modifier.fillMaxWidth().testTag("update_install_btn"),
                )
              }
              else -> {}
            }
          }
        }
      }
    }

    // Install from Local Project / Device APK (SAF) & Unknown Sources Permission
    item {
      DrxGlassCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (isIndo) "Pasang APK Lokal & Sumber HTTPS" else "Local APK & HTTPS Source",
            style = MaterialTheme.typography.titleMedium,
          )
          DrxStatusPill(
            text = if (uiState.canInstallPackages) (if (isIndo) "Diizinkan" else "Allowed") else (if (isIndo) "Butuh Izin" else "Needs Access"),
            color = if (uiState.canInstallPackages) drxColors.optimalGreen else drxColors.warningAmber,
          )
        }
        Spacer(Modifier.height(6.dp))
        Text(
          text =
            if (isIndo) {
              "Pilih file APK dari penyimpanan perangkat untuk verifikasi Package ID, versi lebih baru, SHA-256, dan kesesuaian tanda tangan sebelum dipasang lewat PackageInstaller."
            } else {
              "Select an APK from device storage to verify Package ID, newer versionCode, SHA-256, and signing certificate match before installing via PackageInstaller."
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          DrxSecondaryButton(
            label = if (isIndo) "Pilih File APK" else "Pick Local APK",
            icon = Icons.Default.FolderOpen,
            onClick = { pickApkLauncher.launch(arrayOf("application/vnd.android.package-archive")) },
            modifier = Modifier.weight(1f).testTag("update_import_apk_btn"),
          )
          if (!uiState.canInstallPackages) {
            DrxPrimaryButton(
              label = strings.grantPermission,
              icon = Icons.Default.Security,
              onClick = { updateViewModel.updateManager.openUnknownSourcesSettings() },
              modifier = Modifier.weight(1f),
            )
          }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
          value = manifestUrlInput,
          onValueChange = { manifestUrlInput = it },
          label = { Text(if (isIndo) "URL Manifest HTTPS" else "HTTPS Update Manifest URL") },
          placeholder = { Text("https://.../update-manifest.json") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("update_manifest_url_input"),
        )
      }
    }

    // Smart Update Skip Policy Status
    item {
      DrxGlassCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Smart Update Skip Policy",
            style = MaterialTheme.typography.titleMedium,
          )
          DrxStatusPill(
            text = "${prefs.updateSkipCount}/2 skips",
            color = if (uiState.policyState == UpdateOfferPolicyState.MANDATORY_REQUIRED) drxColors.alertCrimson else drxColors.neonCyan,
          )
        }
        Spacer(Modifier.height(6.dp))
        Text(
          text =
            if (isIndo) "Penawaran ke-1 & ke-2 bersifat opsional (Update Sekarang / Nanti). Pada penawaran ke-3 untuk versi target yang sama, pembaruan menjadi wajib. Gagal unduh tidak pernah dihitung sebagai skip."
            else "1st & 2nd update offers are optional (Update Now / Later). On the 3rd offer for the same target versionCode, update becomes required. Download failures never increment skip count.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
fun SmartUpdateOfferModal(
  uiState: DrxUpdateUiState,
  isIndo: Boolean,
  onUpdateNow: () -> Unit,
  onLater: () -> Unit,
  onExitApp: () -> Unit,
  onViewChangelog: () -> Unit = {},
) {
  val target = uiState.selectedManifest ?: return
  val drxColors = LocalDrxColors.current
  val isMandatory = uiState.policyState == UpdateOfferPolicyState.MANDATORY_REQUIRED
  val strings = LocalStrings.current
  val notesBullets =
    remember(target.releaseNotes) {
      target.releaseNotes
        .split('\n', ';', '•')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(4)
    }

  Dialog(
    onDismissRequest = { if (!isMandatory) onLater() },
    properties =
      DialogProperties(
        dismissOnBackPress = !isMandatory,
        dismissOnClickOutside = !isMandatory,
      ),
  ) {
    DrxAnimatedModalContainer {
      DrxGlassCard(
        accentColor = if (isMandatory) drxColors.alertCrimson else drxColors.electricBlue,
        highlighted = true,
        cornerRadius = 24.dp,
        modifier = Modifier.fillMaxWidth().testTag("smart_update_offer_modal"),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = if (isMandatory) Icons.Default.Warning else Icons.Default.SystemUpdate,
            contentDescription = null,
            tint = if (isMandatory) drxColors.alertCrimson else drxColors.neonCyan,
          )
          Spacer(Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text =
                if (isMandatory) (if (isIndo) "Pembaruan Wajib" else "Required Update")
                else "DRX UPDATE AVAILABLE",
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
              text = "Version ${target.versionName} (Build #${target.versionCode}) • ${target.apkType.uppercase()}",
              style = DrxTypographyTokens.caption,
              color = drxColors.neonCyan,
            )
          }
        }

        Spacer(Modifier.height(6.dp))
        Text(
          text = if (isIndo) "Pengalaman baru DRX telah siap." else "A new DRX experience is ready.",
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
          color = drxColors.silver,
        )

        Spacer(Modifier.height(10.dp))
        if (isMandatory) {
          Text(
            text = strings.updateMandatoryNotice,
            style = MaterialTheme.typography.bodySmall,
            color = drxColors.alertCrimson,
          )
          Spacer(Modifier.height(6.dp))
        }
        if (notesBullets.isNotEmpty()) {
          Text(
            text = if (isIndo) "Apa yang Baru:" else "What's New:",
            style = MaterialTheme.typography.labelLarge,
            color = drxColors.neonCyan,
          )
          Spacer(Modifier.height(4.dp))
          notesBullets.forEach { bullet ->
            Text(
              text = "• $bullet",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        if (uiState.pipelineState == DrxUpdatePipelineState.DOWNLOADING ||
          uiState.pipelineState == DrxUpdatePipelineState.VERIFYING
        ) {
          Spacer(Modifier.height(10.dp))
          DrxCustomProgressBar(progress = uiState.downloadProgress.coerceIn(0f, 1f))
          Spacer(Modifier.height(4.dp))
          Text(
            text = uiState.statusMessage,
            style = DrxTypographyTokens.caption,
            color = drxColors.neonCyan,
          )
        }

        uiState.errorMessage?.let { err ->
          Spacer(Modifier.height(8.dp))
          Text(
            text = err,
            style = MaterialTheme.typography.bodySmall,
            color = drxColors.alertCrimson,
          )
        }

        Spacer(Modifier.height(14.dp))
        val isBusy =
          uiState.pipelineState == DrxUpdatePipelineState.DOWNLOADING ||
            uiState.pipelineState == DrxUpdatePipelineState.VERIFYING
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          if (isMandatory) {
            DrxSecondaryButton(
              label = strings.updateExitAppBtn,
              onClick = onExitApp,
              modifier = Modifier.weight(1f).testTag("smart_update_exit_btn"),
            )
          } else {
            DrxSecondaryButton(
              label = strings.updateLaterBtn,
              onClick = onLater,
              modifier = Modifier.weight(1f).testTag("smart_update_later_btn"),
            )
          }
          DrxPrimaryButton(
            label =
              when {
                isBusy -> if (isIndo) "Memproses..." else "Processing..."
                uiState.stagedApkFile != null -> if (isIndo) "Instal Update" else "Install Update"
                uiState.errorMessage != null -> if (isIndo) "Coba Lagi" else "Retry"
                else -> if (isIndo) "Unduh Update" else "Download Update"
              },
            enabled = !isBusy,
            accentColor = if (isMandatory) drxColors.alertCrimson else drxColors.electricBlue,
            onClick = onUpdateNow,
            modifier = Modifier.weight(1f).testTag("smart_update_now_btn"),
          )
        }

        if (!isMandatory) {
          Spacer(Modifier.height(8.dp))
          DrxSecondaryButton(
            label = strings.viewChangelogBtn,
            icon = Icons.Default.Update,
            onClick = onViewChangelog,
            modifier = Modifier.fillMaxWidth().height(42.dp).testTag("smart_update_changelog_btn"),
          )
        }
      }
    }
  }
}
