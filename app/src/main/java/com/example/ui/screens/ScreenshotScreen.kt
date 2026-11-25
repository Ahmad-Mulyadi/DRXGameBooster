package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LanguageMode
import com.example.data.model.MediaGalleryItem
import com.example.data.model.MediaItemType
import com.example.data.model.ScreenshotCaptureMode
import com.example.data.model.ScreenshotFormat
import com.example.i18n.LocalStrings
import com.example.system.DrxMediaStorageManager
import com.example.system.ScreenRecordConsentActivity
import com.example.system.ScreenRecorderStateHolder
import com.example.system.ScreenshotEngine
import com.example.system.TimeAndSizeFormatters
import com.example.ui.theme.DrxCustomModal
import com.example.ui.theme.DrxDangerButton
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxNotificationBanner
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxSectionHeader
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxDiagnosticsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ScreenshotScreen(
  diagnosticsViewModel: DrxDiagnosticsViewModel,
  onOpenGallery: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val context = LocalContext.current
  val activity = LocalActivity.current
  val scope = rememberCoroutineScope()

  val prefs by diagnosticsViewModel.userPrefs.collectAsStateWithLifecycle()
  val recState by ScreenRecorderStateHolder.state.collectAsStateWithLifecycle()
  val galleryItems by diagnosticsViewModel.galleryItems.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  var captureMode by remember { mutableStateOf(ScreenshotCaptureMode.APP_WINDOW) }
  var format by remember { mutableStateOf(ScreenshotFormat.PNG) }
  var isCapturing by remember { mutableStateOf(false) }
  var showFlash by remember { mutableStateOf(false) }
  var localError by remember { mutableStateOf<String?>(null) }
  var localBanner by remember { mutableStateOf<String?>(null) }
  var previewItem by remember { mutableStateOf<MediaGalleryItem?>(null) }
  var renameItem by remember { mutableStateOf<MediaGalleryItem?>(null) }
  var renameInput by remember { mutableStateOf("") }
  var deleteItem by remember { mutableStateOf<MediaGalleryItem?>(null) }

  val safLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      if (uri != null) diagnosticsViewModel.saveSafTreeUri(uri)
    }

  LaunchedEffect(Unit) {
    diagnosticsViewModel.refreshGallery()
  }

  LaunchedEffect(recState.lastCapturedScreenshot?.uriString) {
    if (recState.lastCapturedScreenshot != null) {
      diagnosticsViewModel.refreshGallery()
    }
  }

  val screenshotsOnly =
    remember(galleryItems) {
      galleryItems.filter { it.type == MediaItemType.SCREENSHOT }
    }
  val latestShot = recState.lastCapturedScreenshot ?: screenshotsOnly.firstOrNull()

  fun triggerCapture() {
    localError = null
    localBanner = null
    ScreenRecorderStateHolder.clearMessages()
    if (captureMode == ScreenshotCaptureMode.APP_WINDOW) {
      isCapturing = true
      ScreenshotEngine.captureAppWindow(
        activity = activity,
        format = format,
        safTreeUri = prefs.safRootTreeUri,
        scope = scope,
      ) { saved, err ->
        isCapturing = false
        if (saved != null) {
          showFlash = true
          localBanner =
            if (isIndo) "Tangkapan layar tersimpan: ${saved.displayName}"
            else "Screenshot saved: ${saved.displayName}"
          diagnosticsViewModel.refreshGallery()
          scope.launch {
            delay(220L)
            showFlash = false
          }
        } else {
          localError = err ?: if (isIndo) "Gagal mengambil screenshot." else "Failed to capture screenshot."
        }
      }
    } else {
      context.startActivity(
        Intent(context, ScreenRecordConsentActivity::class.java).apply {
          putExtra(ScreenRecordConsentActivity.EXTRA_CAPTURE_MODE, ScreenRecordConsentActivity.MODE_SCREENSHOT)
          putExtra(ScreenRecordConsentActivity.EXTRA_SCREENSHOT_FORMAT, format.name)
        }
      )
    }
  }

  fun openScreenshotFolder() {
    val opened =
      if (prefs.safRootTreeUri.isNotBlank()) {
        runCatching {
          val treeUri = prefs.safRootTreeUri.toUri()
          val intent =
            Intent(Intent.ACTION_VIEW).apply {
              setDataAndType(treeUri, DocumentsContract.Document.MIME_TYPE_DIR)
              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
          context.startActivity(intent)
          true
        }.getOrDefault(false)
      } else {
        false
      }
    if (!opened) {
      safLauncher.launch(null)
    }
  }

  Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("screenshot_studio_list"),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        DrxScreenHeader(
          title = strings.screenshotStudioTitle,
          subtitle = strings.screenshotStudioSub,
          onBack = onBack,
        )
      }

      val errMsg = localError ?: recState.errorMessage
      if (!errMsg.isNullOrBlank()) {
        item {
          DrxNotificationBanner(
            title = if (isIndo) "Catatan Tangkapan Layar" else "Screenshot Notice",
            body = errMsg,
            accentColor = drxColors.alertCrimson,
            icon = Icons.Default.CameraAlt,
            onDismiss = {
              localError = null
              ScreenRecorderStateHolder.clearMessages()
            },
          )
        }
      }

      val okMsg = localBanner ?: recState.statusMessage
      if (!okMsg.isNullOrBlank()) {
        item {
          DrxNotificationBanner(
            title = if (isIndo) "Berhasil" else "Captured",
            body = okMsg,
            accentColor = drxColors.optimalGreen,
            icon = Icons.Default.Image,
            onDismiss = {
              localBanner = null
              ScreenRecorderStateHolder.clearMessages()
            },
          )
        }
      }

      // Capture Mode + Format Controls
      item {
        DrxGlassCard(accentColor = drxColors.electricBlue, cornerRadius = 22.dp) {
          Text(
            text = if (isIndo) "Mode Tangkapan" else "Capture Mode",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
          )
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            DrxFilterPill(
              label = if (isIndo) "Jendela DRX" else "App Window",
              selected = captureMode == ScreenshotCaptureMode.APP_WINDOW,
              onClick = { captureMode = ScreenshotCaptureMode.APP_WINDOW },
              modifier = Modifier.weight(1f).testTag("screenshot_mode_window"),
            )
            DrxFilterPill(
              label = if (isIndo) "Layar Penuh (OS)" else "Full Screen",
              selected = captureMode == ScreenshotCaptureMode.FULL_SCREEN,
              onClick = { captureMode = ScreenshotCaptureMode.FULL_SCREEN },
              modifier = Modifier.weight(1f).testTag("screenshot_mode_fullscreen"),
            )
          }
          Spacer(Modifier.height(6.dp))
          Text(
            text =
              if (captureMode == ScreenshotCaptureMode.APP_WINDOW) {
                if (isIndo) "Mengambil tampilan jendela DRX aktif langsung via PixelCopy tanpa dialog izin tambahan."
                else "Captures the active DRX window directly via PixelCopy with zero extra permission prompt."
              } else {
                if (isIndo) "Menggunakan MediaProjection resmi Android dengan deteksi frame hitam otomatis (menolak frame kosong & aplikasi FLAG_SECURE)."
                else "Uses Android MediaProjection with multi-frame blank-screen guard (rejects uninitialized black frames & FLAG_SECURE surfaces)."
              },
            style = DrxTypographyTokens.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )

          Spacer(Modifier.height(14.dp))
          Text(
            text = if (isIndo) "Format Output" else "Output Format",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
          )
          Spacer(Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenshotFormat.entries.forEach { fmt ->
              DrxFilterPill(
                label = fmt.label,
                selected = format == fmt,
                onClick = { format = fmt },
                modifier = Modifier.weight(1f).testTag("screenshot_format_${fmt.name.lowercase()}"),
              )
            }
          }

          Spacer(Modifier.height(14.dp))
          DrxPrimaryButton(
            label =
              if (isCapturing) (if (isIndo) "Mengambil..." else "Capturing...")
              else if (isIndo) "Ambil Screenshot (${format.label})" else "Capture Screenshot (${format.label})",
            icon = Icons.Default.CameraAlt,
            enabled = !isCapturing,
            onClick = ::triggerCapture,
            modifier = Modifier.fillMaxWidth().testTag("screenshot_capture_button"),
          )
        }
      }

      // Latest Screenshot Preview Card
      item {
        DrxSectionHeader(
          title = if (isIndo) "Hasil Screenshot Terbaru" else "Latest Screenshot",
          actionLabel = if (isIndo) "Galeri" else "Gallery",
          onAction = onOpenGallery,
        )
        Spacer(Modifier.height(8.dp))
        if (latestShot == null) {
          DrxGlassCard {
            Text(
              text =
                if (isIndo) "Belum ada screenshot yang tersimpan di folder Pictures/DRXGameBooster/Screenshot."
                else "No screenshots saved in Pictures/DRXGameBooster/Screenshot yet.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          LatestScreenshotPreviewCard(
            item = latestShot,
            isIndo = isIndo,
            onPreview = { previewItem = latestShot },
            onShare = {
              val shareIntent = DrxMediaStorageManager.buildShareIntent(context, latestShot)
              context.startActivity(Intent.createChooser(shareIntent, "Share Screenshot"))
            },
            onRename = {
              renameItem = latestShot
              renameInput = latestShot.displayName.substringBeforeLast('.')
            },
            onDelete = { deleteItem = latestShot },
            onOpenFolder = ::openScreenshotFolder,
            onCaptureAgain = ::triggerCapture,
          )
        }
      }

      if (screenshotsOnly.isNotEmpty()) {
        item {
          DrxSectionHeader(
            title = if (isIndo) "Riwayat Screenshot (${screenshotsOnly.size})" else "Recent Screenshots (${screenshotsOnly.size})",
            actionLabel = if (isIndo) "Muat Ulang" else "Refresh",
            onAction = { diagnosticsViewModel.refreshGallery() },
          )
        }
        items(screenshotsOnly.take(10), key = { it.uriString }) { shot ->
          DrxGlassCard(
            onClick = { previewItem = shot },
            contentPadding = PaddingValues(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              ScreenshotThumbnail(uriString = shot.uriString, modifier = Modifier.size(56.dp))
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = shot.displayName,
                  style = MaterialTheme.typography.titleSmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text =
                    "${shot.width}×${shot.height} • ${TimeAndSizeFormatters.formatBytesAuto(shot.sizeBytes)} • " +
                      TimeAndSizeFormatters.formatDateTimeWithZone(shot.timestampMs, isIndo),
                  style = DrxTypographyTokens.caption,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              IconButton(
                onClick = {
                  val shareIntent = DrxMediaStorageManager.buildShareIntent(context, shot)
                  context.startActivity(Intent.createChooser(shareIntent, "Share Screenshot"))
                }
              ) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = drxColors.neonCyan)
              }
            }
          }
        }
      }
    }

    // Subtle capture flash feedback
    AnimatedVisibility(
      visible = showFlash,
      enter = fadeIn(),
      exit = fadeOut(),
    ) {
      Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.28f)))
    }
  }

  // Full-Screen Preview Modal
  previewItem?.let { item ->
    DrxCustomModal(
      title = item.displayName,
      onDismiss = { previewItem = null },
    ) {
      ScreenshotThumbnail(
        uriString = item.uriString,
        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 380.dp),
        fullRes = true,
      )
      Spacer(Modifier.height(10.dp))
      Text(
        text = "${item.width}×${item.height} • ${TimeAndSizeFormatters.formatBytesAuto(item.sizeBytes)} • ${item.folderLabel}",
        style = DrxTypographyTokens.caption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(12.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        DrxSecondaryButton(
          label = if (isIndo) "Bagikan" else "Share",
          icon = Icons.Default.Share,
          onClick = {
            val shareIntent = DrxMediaStorageManager.buildShareIntent(context, item)
            context.startActivity(Intent.createChooser(shareIntent, "Share Screenshot"))
          },
          modifier = Modifier.weight(1f),
        )
        DrxPrimaryButton(
          label = strings.closeLabel,
          onClick = { previewItem = null },
          modifier = Modifier.weight(1f),
        )
      }
    }
  }

  // Rename Modal
  renameItem?.let { item ->
    DrxCustomModal(
      title = if (isIndo) "Ubah Nama Screenshot" else "Rename Screenshot",
      onDismiss = { renameItem = null },
    ) {
      OutlinedTextField(
        value = renameInput,
        onValueChange = { renameInput = it },
        singleLine = true,
        label = { Text(if (isIndo) "Nama File Baru" else "New File Name") },
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(12.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        DrxSecondaryButton(
          label = strings.closeLabel,
          onClick = { renameItem = null },
          modifier = Modifier.weight(1f),
        )
        DrxPrimaryButton(
          label = if (isIndo) "Simpan" else "Save",
          onClick = {
            if (renameInput.isNotBlank()) {
              diagnosticsViewModel.renameMediaItem(item, renameInput)
            }
            renameItem = null
          },
          modifier = Modifier.weight(1f),
        )
      }
    }
  }

  // Delete Confirmation Modal
  deleteItem?.let { item ->
    DrxCustomModal(
      title = if (isIndo) "Hapus Screenshot?" else "Delete Screenshot?",
      onDismiss = { deleteItem = null },
    ) {
      Text(
        text = item.displayName,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(14.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        DrxSecondaryButton(
          label = strings.closeLabel,
          onClick = { deleteItem = null },
          modifier = Modifier.weight(1f),
        )
        DrxDangerButton(
          label = if (isIndo) "Hapus" else "Delete",
          icon = Icons.Default.DeleteOutline,
          onClick = {
            diagnosticsViewModel.deleteMediaItems(listOf(item))
            deleteItem = null
          },
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun LatestScreenshotPreviewCard(
  item: MediaGalleryItem,
  isIndo: Boolean,
  onPreview: () -> Unit,
  onShare: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
  onOpenFolder: () -> Unit,
  onCaptureAgain: () -> Unit,
) {
  val drxColors = LocalDrxColors.current
  DrxGlassCard(
    accentColor = drxColors.neonCyan,
    highlighted = true,
    cornerRadius = 22.dp,
    modifier = Modifier.testTag("latest_screenshot_card"),
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(190.dp)
          .clip(RoundedCornerShape(16.dp))
          .clickable(onClick = onPreview),
    ) {
      ScreenshotThumbnail(uriString = item.uriString, modifier = Modifier.fillMaxSize(), fullRes = false)
      Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, drxColors.glassBorder),
        modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(Icons.Default.Fullscreen, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
          Text(
            text = if (isIndo) "Pratinjau" else "Preview",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
          )
        }
      }
    }

    Spacer(Modifier.height(10.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = item.displayName,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text =
            "${item.width}×${item.height} • ${TimeAndSizeFormatters.formatBytesAuto(item.sizeBytes)} • " +
              TimeAndSizeFormatters.formatDateTimeWithZone(item.timestampMs, isIndo),
          style = DrxTypographyTokens.caption,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      DrxStatusPill(
        text = if (item.displayName.endsWith(".jpg", true) || item.displayName.endsWith(".jpeg", true)) "JPEG" else "PNG",
        color = drxColors.neonCyan,
      )
    }

    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      DrxSecondaryButton(
        label = if (isIndo) "Bagikan" else "Share",
        icon = Icons.Default.Share,
        onClick = onShare,
        modifier = Modifier.weight(1f).height(42.dp),
      )
      DrxSecondaryButton(
        label = if (isIndo) "Ubah Nama" else "Rename",
        icon = Icons.Default.DriveFileRenameOutline,
        onClick = onRename,
        modifier = Modifier.weight(1f).height(42.dp),
      )
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      DrxSecondaryButton(
        label = if (isIndo) "Folder" else "Folder",
        icon = Icons.Default.FolderOpen,
        onClick = onOpenFolder,
        modifier = Modifier.weight(1f).height(42.dp),
      )
      DrxDangerButton(
        label = if (isIndo) "Hapus" else "Delete",
        icon = Icons.Default.DeleteOutline,
        onClick = onDelete,
        modifier = Modifier.weight(1f).height(42.dp),
      )
    }
    Spacer(Modifier.height(8.dp))
    DrxPrimaryButton(
      label = if (isIndo) "Ambil Lagi" else "Capture Again",
      icon = Icons.Default.Refresh,
      onClick = onCaptureAgain,
      modifier = Modifier.fillMaxWidth().height(44.dp).testTag("screenshot_capture_again_button"),
    )
  }
}

@Composable
private fun ScreenshotThumbnail(
  uriString: String,
  modifier: Modifier = Modifier,
  fullRes: Boolean = false,
) {
  val context = LocalContext.current
  val drxColors = LocalDrxColors.current
  val bitmap by
    produceState<Bitmap?>(initialValue = null, key1 = uriString, key2 = fullRes) {
      value =
        withContext(Dispatchers.IO) {
          runCatching {
            val uri = uriString.toUri()
            val opts =
              BitmapFactory.Options().apply {
                inSampleSize = if (fullRes) 1 else 2
              }
            context.contentResolver.openInputStream(uri)?.use { stream ->
              BitmapFactory.decodeStream(stream, null, opts)
            }
          }.getOrNull()
        }
    }

  val bmp = bitmap
  if (bmp != null) {
    Image(
      bitmap = bmp.asImageBitmap(),
      contentDescription = "Screenshot Preview",
      contentScale = if (fullRes) ContentScale.Fit else ContentScale.Crop,
      modifier = modifier.clip(RoundedCornerShape(14.dp)),
    )
  } else {
    Box(
      modifier = modifier.clip(RoundedCornerShape(14.dp)).background(drxColors.elevatedSurface),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Default.Image, contentDescription = null, tint = drxColors.neonCyan)
    }
  }
}
