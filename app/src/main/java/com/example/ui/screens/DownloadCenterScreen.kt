package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.model.BuildPipelineState
import com.example.data.model.LanguageMode
import com.example.system.BuildDownloadManager
import com.example.system.TimeAndSizeFormatters
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxHairlineDivider
import com.example.ui.theme.DrxNotificationBanner
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxDiagnosticsViewModel

@Composable
fun DownloadCenterScreen(
  diagnosticsViewModel: DrxDiagnosticsViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val drxColors = LocalDrxColors.current
  val prefs by diagnosticsViewModel.userPrefs.collectAsStateWithLifecycle()
  val pipelineState by diagnosticsViewModel.buildPipelineState.collectAsStateWithLifecycle()
  val statusMessage by diagnosticsViewModel.buildStatusMessage.collectAsStateWithLifecycle()
  val apkStatus by diagnosticsViewModel.apkArtifactStatus.collectAsStateWithLifecycle()
  val aabInfo by diagnosticsViewModel.aabArtifactInfo.collectAsStateWithLifecycle()
  val actionBanner by diagnosticsViewModel.actionBanner.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  LaunchedEffect(Unit) {
    diagnosticsViewModel.refreshExistingApkExport()
  }

  val saveApkLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
      if (uri != null) {
        diagnosticsViewModel.exportApkToSafUri(uri)
      }
    }

  val isBusy =
    pipelineState == BuildPipelineState.CHECKING_SOURCE ||
      pipelineState == BuildPipelineState.BUILDING_APK ||
      pipelineState == BuildPipelineState.VALIDATING ||
      pipelineState == BuildPipelineState.REPLACING_OLD_BUILD

  val pillColor =
    when (pipelineState) {
      BuildPipelineState.READY_TO_DOWNLOAD -> drxColors.optimalGreen
      BuildPipelineState.BUILD_FAILED -> drxColors.alertCrimson
      BuildPipelineState.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
      else -> drxColors.neonCyan
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("download_center_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = if (isIndo) "Pusat Build & Unduhan" else "Build & Download Center",
        subtitle = "DRX GAME TURBO PRO v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        onBack = onBack,
      )
    }

    actionBanner?.let { msg ->
      item {
        DrxNotificationBanner(
          title = "DRX Build Export",
          body = msg,
          accentColor = drxColors.optimalGreen,
          onDismiss = { diagnosticsViewModel.clearActionBanner() },
        )
      }
    }

    // Pipeline Verification Status Card
    item {
      DrxGlassCard(
        accentColor = pillColor,
        highlighted = pipelineState == BuildPipelineState.READY_TO_DOWNLOAD || pipelineState == BuildPipelineState.BUILD_FAILED,
        cornerRadius = 22.dp,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (isIndo) "Status Pipeline Verifikasi" else "Verification Pipeline",
            style = MaterialTheme.typography.titleMedium,
          )
          DrxStatusPill(
            text = pipelineState.label,
            color = pillColor,
            modifier = Modifier.testTag("download_pipeline_pill"),
          )
        }
        Spacer(Modifier.height(8.dp))
        Text(
          text =
            statusMessage.ifBlank {
              if (isIndo) {
                "Siapkan & verifikasi paket APK sebelum disimpan atau dibagikan agar bebas error corrupt."
              } else {
                "Prepare and verify the APK archive (ZIP + AndroidManifest + SHA-256) before saving or sharing."
              }
            },
          style = MaterialTheme.typography.bodySmall,
          color = if (pipelineState == BuildPipelineState.BUILD_FAILED) drxColors.alertCrimson else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        DrxPrimaryButton(
          label =
            when {
              isBusy -> if (isIndo) "Memproses & Memverifikasi..." else "Preparing & Verifying..."
              pipelineState == BuildPipelineState.BUILD_FAILED -> if (isIndo) "Coba Lagi" else "Retry Build Export"
              pipelineState == BuildPipelineState.READY_TO_DOWNLOAD -> if (isIndo) "Bangun Ulang & Verifikasi" else "Rebuild & Re-Verify"
              else -> if (isIndo) "Siapkan APK Terverifikasi" else "Prepare Verified APK"
            },
          icon = if (pipelineState == BuildPipelineState.BUILD_FAILED) Icons.Default.Refresh else Icons.Default.Build,
          enabled = !isBusy,
          onClick = { diagnosticsViewModel.prepareDownloadPackage() },
          modifier = Modifier.fillMaxWidth().testTag("download_prepare_apk_button"),
        )
      }
    }

    // Verified APK Export Card
    item {
      val currentApk = apkStatus
      DrxGlassCard(accentColor = drxColors.electricBlue, highlighted = currentApk != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(BuildDownloadManager.APK_FILE_NAME, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(
              text =
                currentApk?.locationLabel
                  ?: if (isIndo) "Tekan tombol di atas untuk menyiapkan APK terverifikasi" else "Tap Prepare above to stage verified APK",
              style = DrxTypographyTokens.caption,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (currentApk?.isValidArchive == true) {
            DrxStatusPill(text = "VERIFIED", color = drxColors.optimalGreen)
          }
        }

        if (currentApk != null) {
          Spacer(Modifier.height(10.dp))
          DrxHairlineDivider()
          Spacer(Modifier.height(10.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (isIndo) "Ukuran" else "Size", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(TimeAndSizeFormatters.formatBytesAuto(currentApk.sizeBytes), style = MaterialTheme.typography.labelLarge)
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SHA-256 (16)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(currentApk.sha256Short, style = MaterialTheme.typography.labelLarge, color = drxColors.neonCyan)
          }
          Spacer(Modifier.height(6.dp))
          Text(currentApk.signingNote, style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(Modifier.height(12.dp))

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DrxPrimaryButton(
              label = if (isIndo) "Simpan APK" else "Save APK",
              icon = Icons.Default.Download,
              enabled = currentApk.downloadSupported,
              onClick = { saveApkLauncher.launch(BuildDownloadManager.APK_FILE_NAME) },
              modifier = Modifier.weight(1f).testTag("download_save_apk_button"),
            )
            DrxSecondaryButton(
              label = if (isIndo) "Bagikan" else "Share",
              icon = Icons.Default.Share,
              enabled = currentApk.downloadSupported,
              onClick = {
                val share = diagnosticsViewModel.buildShareApkIntent()
                if (share != null) {
                  runCatching {
                    context.startActivity(Intent.createChooser(share, "Share ${BuildDownloadManager.APK_FILE_NAME}"))
                  }
                }
              },
              modifier = Modifier.weight(1f).testTag("download_share_apk_button"),
            )
          }
        }
      }
    }

    // Play Console AAB Disclosure Card
    item {
      DrxGlassCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(aabInfo.fileName, style = MaterialTheme.typography.titleMedium)
            Text(aabInfo.locationLabel, style = DrxTypographyTokens.caption, color = drxColors.neonCyan)
          }
          DrxStatusPill(text = "HOST BUILD", color = drxColors.electricBlue)
        }
        Spacer(Modifier.height(8.dp))
        Text(
          text = aabInfo.signingNote,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item {
      DrxNotificationBanner(
        title = if (isIndo) "Perlindungan Anti-Corrupt" else "Anti-Corrupt Integrity Check",
        body =
          if (isIndo) {
            "Setiap APK yang disiapkan diperiksa header ZIP (PK), kehadiran AndroidManifest.xml, metadata PackageManager, dan checksum SHA-256 sebelum menggantikan ekspor lama."
          } else {
            "Every prepared APK passes ZIP integrity, AndroidManifest presence, PackageManager archive checks, and SHA-256 hashing before replacing prior exports."
          },
        accentColor = drxColors.optimalGreen,
        icon = Icons.Default.VerifiedUser,
      )
    }

    item {
      DrxNotificationBanner(
        title = if (isIndo) "Standar Output Build Proyek" else "Project Artifact Standardization",
        body =
          if (isIndo) {
            "Skrip host auto-build menghasilkan:\n• src/apk/${BuildDownloadManager.APK_VERSIONED_NAME}\n• src/apk/DRX-GAME-TURBO-PRO.apk\n• src/apk/DRX-GAME-TURBO-PRO-DEBUG.apk\n• src/aab/DRX-GAME-TURBO-PRO.aab"
          } else {
            "Host auto-build pipeline outputs:\n• src/apk/${BuildDownloadManager.APK_VERSIONED_NAME}\n• src/apk/DRX-GAME-TURBO-PRO.apk\n• src/apk/DRX-GAME-TURBO-PRO-DEBUG.apk\n• src/aab/DRX-GAME-TURBO-PRO.aab"
          },
        accentColor = drxColors.electricBlue,
        icon = Icons.Outlined.Info,
      )
    }
  }
}
