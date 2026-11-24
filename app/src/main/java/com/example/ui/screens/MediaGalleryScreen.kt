package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.DocumentsContract
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.LanguageMode
import com.example.data.model.MediaGalleryItem
import com.example.data.model.MediaItemType
import com.example.i18n.LocalStrings
import com.example.system.DrxMediaStorageManager
import com.example.system.ScreenRecorderService
import com.example.system.TimeAndSizeFormatters
import com.example.ui.theme.DrxAnimatedModalContainer
import com.example.ui.theme.DrxCustomModal
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxNotificationBanner
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxSectionHeader
import com.example.ui.theme.DrxStaggeredEntrance
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.theme.rememberDrxAnimationsActive
import com.example.ui.viewmodel.DrxDiagnosticsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class GalleryFilter {
  ALL,
  SCREENSHOTS,
  RECORDINGS,
  FAVORITES,
}

private enum class GallerySort {
  NEWEST,
  LARGEST,
}

@Composable
fun MediaGalleryScreen(
  diagnosticsViewModel: DrxDiagnosticsViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val drxColors = LocalDrxColors.current
  val strings = LocalStrings.current
  val prefs by diagnosticsViewModel.userPrefs.collectAsStateWithLifecycle()
  val itemsList by diagnosticsViewModel.galleryItems.collectAsStateWithLifecycle()
  val storageSummary by diagnosticsViewModel.storageSummary.collectAsStateWithLifecycle()
  val actionBanner by diagnosticsViewModel.actionBanner.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN
  val animationsActive = rememberDrxAnimationsActive(prefs.animationsEnabled)

  var filter by remember { mutableStateOf(GalleryFilter.ALL) }
  var sortOrder by remember { mutableStateOf(GallerySort.NEWEST) }
  var multiSelectMode by remember { mutableStateOf(false) }
  val selectedUris = remember { mutableStateListOf<String>() }
  var pendingDeleteItems by remember { mutableStateOf<List<MediaGalleryItem>?>(null) }
  var renameTarget by remember { mutableStateOf<MediaGalleryItem?>(null) }
  var renameText by remember { mutableStateOf("") }
  var previewTarget by remember { mutableStateOf<MediaGalleryItem?>(null) }

  val safLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      if (uri != null) diagnosticsViewModel.saveSafTreeUri(uri)
    }

  LaunchedEffect(Unit) {
    diagnosticsViewModel.refreshGallery()
  }

  val filteredItems =
    remember(itemsList, filter, sortOrder, prefs.favoriteMediaUris) {
      val base =
        when (filter) {
          GalleryFilter.ALL -> itemsList
          GalleryFilter.SCREENSHOTS -> itemsList.filter { it.type == MediaItemType.SCREENSHOT }
          GalleryFilter.RECORDINGS -> itemsList.filter { it.type == MediaItemType.VIDEO }
          GalleryFilter.FAVORITES -> itemsList.filter { prefs.favoriteMediaUris.contains(it.uriString) }
        }
      when (sortOrder) {
        GallerySort.NEWEST -> base.sortedByDescending { it.timestampMs }
        GallerySort.LARGEST -> base.sortedByDescending { it.sizeBytes }
      }
    }

  fun openStorageFolder() {
    val opened =
      if (prefs.safRootTreeUri.isNotBlank()) {
        runCatching {
          val treeUri = prefs.safRootTreeUri.toUri()
          val docId = DocumentsContract.getTreeDocumentId(treeUri)
          val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
          context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
              setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
          )
          true
        }.getOrDefault(false)
      } else {
        false
      }

    if (!opened) {
      val fallbackOpened =
        runCatching {
          context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
              setDataAndType("content://com.android.externalstorage.documents/root/primary".toUri(), DocumentsContract.Document.MIME_TYPE_DIR)
            }
          )
          true
        }.getOrDefault(false)

      if (!fallbackOpened) {
        Toast.makeText(
          context,
          if (isIndo) "Tautkan folder SAF terlebih dahulu atau buka lewat File Manager (Movies/Pictures/DRXGameBooster)."
          else "Link your SAF folder or view Movies/Pictures/DRXGameBooster in your File Manager.",
          Toast.LENGTH_LONG,
        ).show()
      }
    }
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("media_gallery_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = "DRX Media Hub",
        subtitle =
          if (isIndo) "Screenshot, Rekaman Layar, Favorit & Penyimpanan"
          else "Screenshots, Recordings, Favorites & Storage",
        onBack = onBack,
        trailing = {
          IconButton(onClick = { diagnosticsViewModel.refreshGallery() }) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = drxColors.neonCyan)
          }
        },
      )
    }

    actionBanner?.let { msg ->
      item {
        DrxNotificationBanner(
          title = "DRX Media Hub",
          body = msg,
          accentColor = drxColors.optimalGreen,
          onDismiss = { diagnosticsViewModel.clearActionBanner() },
        )
      }
    }

    // Storage Summary Card
    item {
      DrxStaggeredEntrance(index = 0, animationsEnabled = animationsActive) {
        DrxGlassCard(accentColor = drxColors.electricBlue, highlighted = true, cornerRadius = 22.dp) {
          Text(
            text = if (isIndo) "Ringkasan Penyimpanan Media" else "Smart Storage Breakdown",
            style = MaterialTheme.typography.titleMedium,
            color = drxColors.neonCyan,
          )
          Spacer(Modifier.height(10.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
              Text(if (isIndo) "Screenshot" else "Screenshots", style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text("${storageSummary.screenshotCount}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(if (isIndo) "Rekaman" else "Recordings", style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text("${storageSummary.videoCount}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
            Column(modifier = Modifier.weight(1.2f)) {
              Text(if (isIndo) "Total Media" else "Total Media", style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(TimeAndSizeFormatters.formatBytesAuto(storageSummary.totalMediaBytes), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = drxColors.neonCyan)
            }
          }
          Spacer(Modifier.height(8.dp))
          Text(
            text =
              "${if (isIndo) "Sisa Ruang Perangkat" else "Device Free Space"}: ${TimeAndSizeFormatters.formatBytesAuto(storageSummary.availableDeviceBytes)}" +
                (storageSummary.largestVideoName?.let {
                  " • ${if (isIndo) "Terbesar" else "Largest"}: $it (${TimeAndSizeFormatters.formatBytesAuto(storageSummary.largestVideoBytes)})"
                } ?: ""),
            style = DrxTypographyTokens.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(10.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrxSecondaryButton(
              label =
                if (prefs.safRootTreeUri.isBlank()) (if (isIndo) "Tautkan SAF" else "Link SAF")
                else (if (isIndo) "SAF Aktif" else "Change SAF"),
              icon = Icons.Default.FolderOpen,
              onClick = { safLauncher.launch(null) },
              modifier = Modifier.weight(1f).testTag("gallery_link_saf_button"),
            )
            DrxSecondaryButton(
              label = strings.openFolderBtn,
              icon = Icons.AutoMirrored.Filled.OpenInNew,
              onClick = { openStorageFolder() },
              modifier = Modifier.weight(1f).testTag("gallery_open_folder_button"),
            )
          }
        }
      }
    }

    // Filter + Sort + Multi-Select Controls
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          DrxFilterPill(
            label = strings.mediaAllTab,
            selected = filter == GalleryFilter.ALL,
            onClick = { filter = GalleryFilter.ALL },
            modifier = Modifier.weight(1f).testTag("gallery_tab_all"),
          )
          DrxFilterPill(
            label = "Screenshots",
            selected = filter == GalleryFilter.SCREENSHOTS,
            onClick = { filter = GalleryFilter.SCREENSHOTS },
            modifier = Modifier.weight(1f).testTag("gallery_tab_screenshots"),
          )
          DrxFilterPill(
            label = "Recordings",
            selected = filter == GalleryFilter.RECORDINGS,
            onClick = { filter = GalleryFilter.RECORDINGS },
            modifier = Modifier.weight(1f).testTag("gallery_tab_recordings"),
          )
          DrxFilterPill(
            label = strings.mediaFavoritesTab,
            selected = filter == GalleryFilter.FAVORITES,
            onClick = { filter = GalleryFilter.FAVORITES },
            modifier = Modifier.weight(1f).testTag("gallery_tab_favorites"),
          )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          DrxFilterPill(
            label = if (isIndo) "Terbaru" else "Newest",
            selected = sortOrder == GallerySort.NEWEST,
            onClick = { sortOrder = GallerySort.NEWEST },
            modifier = Modifier.weight(1f),
          )
          DrxFilterPill(
            label = if (isIndo) "Terbesar" else "Largest",
            selected = sortOrder == GallerySort.LARGEST,
            onClick = { sortOrder = GallerySort.LARGEST },
            modifier = Modifier.weight(1f),
          )
          DrxSecondaryButton(
            label =
              if (multiSelectMode) "${selectedUris.size} ✓"
              else if (isIndo) "Pilih" else "Select",
            icon = Icons.Default.Checklist,
            onClick = {
              multiSelectMode = !multiSelectMode
              if (!multiSelectMode) selectedUris.clear()
            },
            modifier = Modifier.height(38.dp).testTag("gallery_multiselect_button"),
          )
        }
      }
    }

    if (multiSelectMode && selectedUris.isNotEmpty()) {
      item {
        val chosen = filteredItems.filter { selectedUris.contains(it.uriString) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          DrxSecondaryButton(
            label = if (isIndo) "Bagikan (${chosen.size})" else "Share (${chosen.size})",
            icon = Icons.Default.Share,
            onClick = {
              if (chosen.isNotEmpty()) {
                runCatching {
                  context.startActivity(
                    Intent.createChooser(DrxMediaStorageManager.buildMultiShareIntent(chosen), "Share DRX Media")
                  )
                }
              }
            },
            modifier = Modifier.weight(1f),
          )
          DrxPrimaryButton(
            label = if (isIndo) "Hapus (${chosen.size})" else "Delete (${chosen.size})",
            icon = Icons.Default.DeleteOutline,
            accentColor = drxColors.alertCrimson,
            onClick = { pendingDeleteItems = chosen },
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    if (filteredItems.isEmpty()) {
      item {
        DrxGlassCard {
          DrxSectionHeader(
            title =
              when (filter) {
                GalleryFilter.FAVORITES -> if (isIndo) "Belum Ada Media Favorit" else "No Favorite Media Yet"
                else -> if (isIndo) "Galeri Masih Kosong" else "No DRX Media Found"
              }
          )
          Spacer(Modifier.height(6.dp))
          Text(
            text =
              if (filter == GalleryFilter.FAVORITES) {
                if (isIndo) "Ketuk ikon bintang pada screenshot atau rekaman untuk menyimpannya sebagai favorit."
                else "Tap the star button on any screenshot or recording to bookmark it here."
              } else if (isIndo) {
                "Rekaman layar dari DRX Pro Recorder akan disimpan di Movies/DRXGameBooster/ScreenRecorder dan screenshot di Pictures/DRXGameBooster/Screenshot."
              } else {
                "Videos from DRX Pro Recorder are stored in Movies/DRXGameBooster/ScreenRecorder and screenshots in Pictures/DRXGameBooster/Screenshot."
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      itemsIndexed(filteredItems, key = { _, it -> it.uriString }) { idx, item ->
        val isSelected = selectedUris.contains(item.uriString)
        val isFavorite = prefs.favoriteMediaUris.contains(item.uriString)
        val thumb by
          produceState<Bitmap?>(initialValue = null, key1 = item.uriString) {
            value = withContext(Dispatchers.IO) { DrxMediaStorageManager.loadThumbnailBitmap(context, item) }
          }

        DrxStaggeredEntrance(index = idx.coerceAtMost(6), animationsEnabled = animationsActive) {
          DrxGlassCard(
            accentColor = if (isSelected) drxColors.neonCyan else drxColors.electricBlue,
            highlighted = isSelected,
            contentPadding = PaddingValues(12.dp),
            onClick = {
              if (multiSelectMode) {
                if (isSelected) selectedUris.remove(item.uriString) else selectedUris.add(item.uriString)
              } else {
                previewTarget = item
              }
            },
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier =
                  Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(drxColors.elevatedSurface)
                    .border(1.dp, drxColors.glassBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
              ) {
                val bmp = thumb
                if (bmp != null) {
                  Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                  )
                } else {
                  Icon(
                    imageVector = if (item.type == MediaItemType.VIDEO) Icons.Default.Videocam else Icons.Default.Image,
                    contentDescription = item.displayName,
                    tint = drxColors.neonCyan,
                    modifier = Modifier.size(26.dp),
                  )
                }
              }

              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = item.displayName,
                  style = MaterialTheme.typography.titleSmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                  text =
                    buildString {
                      append(TimeAndSizeFormatters.formatBytesAuto(item.sizeBytes))
                      if (item.durationSec > 0) append(" • ${ScreenRecorderService.formatDuration(item.durationSec)}")
                      if (item.width > 0 && item.height > 0) append(" • ${item.width}x${item.height}")
                    },
                  style = DrxTypographyTokens.caption,
                  color = drxColors.silver,
                )
                Text(
                  text = TimeAndSizeFormatters.formatDateTimeWithZone(item.timestampMs, isIndo),
                  style = DrxTypographyTokens.caption,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              if (multiSelectMode) {
                Icon(
                  imageVector = Icons.Default.CheckCircle,
                  contentDescription = "Select",
                  tint = if (isSelected) drxColors.neonCyan else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
              } else {
                IconButton(onClick = { diagnosticsViewModel.toggleFavoriteMedia(item.uriString) }) {
                  Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) drxColors.warningAmber else drxColors.silver,
                  )
                }
                IconButton(onClick = {
                  renameTarget = item
                  renameText = item.displayName.substringBeforeLast('.')
                }) {
                  Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename", tint = drxColors.silver)
                }
                IconButton(onClick = {
                  runCatching {
                    context.startActivity(
                      Intent.createChooser(DrxMediaStorageManager.buildShareIntent(context, item), "Share Media")
                    )
                  }
                }) {
                  Icon(Icons.Default.Share, contentDescription = "Share", tint = drxColors.electricBlue)
                }
                IconButton(onClick = { pendingDeleteItems = listOf(item) }) {
                  Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = drxColors.alertCrimson)
                }
              }
            }
          }
        }
      }
    }
  }

  // In-app Preview / Playback Modal
  val previewItem = previewTarget
  if (previewItem != null) {
    DrxAnimatedModalContainer(animationsEnabled = animationsActive) {
      DrxCustomModal(
        title = previewItem.displayName,
        onDismiss = { previewTarget = null },
      ) {
        if (previewItem.type == MediaItemType.VIDEO) {
          var videoViewRef by remember(previewItem.uriString) { mutableStateOf<VideoView?>(null) }
          DisposableEffect(previewItem.uriString) {
            onDispose {
              runCatching { videoViewRef?.stopPlayback() }
            }
          }
          Box(
            modifier =
              Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 300.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(drxColors.elevatedSurface)
                .border(1.dp, drxColors.glassBorder, RoundedCornerShape(14.dp)),
          ) {
            AndroidView(
              factory = { ctx ->
                VideoView(ctx).apply {
                  val controller = MediaController(ctx)
                  controller.setAnchorView(this)
                  setMediaController(controller)
                  setVideoURI(previewItem.uriString.toUri())
                  setOnPreparedListener { mp ->
                    mp.isLooping = false
                    start()
                  }
                  setOnErrorListener { _, _, _ -> true }
                  videoViewRef = this
                }
              },
              modifier = Modifier.fillMaxWidth().height(240.dp),
            )
          }
        } else {
          val fullBmp by
            produceState<Bitmap?>(initialValue = null, key1 = previewItem.uriString) {
              value =
                withContext(Dispatchers.IO) {
                  runCatching {
                    context.contentResolver.openInputStream(previewItem.uriString.toUri())?.use { input ->
                      val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                      BitmapFactory.decodeStream(input, null, opts)
                    }
                  }.getOrNull() ?: DrxMediaStorageManager.loadThumbnailBitmap(context, previewItem)
                }
            }
          Box(
            modifier =
              Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(drxColors.elevatedSurface)
                .border(1.dp, drxColors.glassBorder, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
          ) {
            val bmp = fullBmp
            if (bmp != null) {
              Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = previewItem.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
              )
            } else {
              Icon(Icons.Default.Image, contentDescription = null, tint = drxColors.neonCyan, modifier = Modifier.size(44.dp))
            }
          }
        }

        Spacer(Modifier.height(10.dp))
        Text(
          text =
            buildString {
              append(TimeAndSizeFormatters.formatBytesAuto(previewItem.sizeBytes))
              if (previewItem.durationSec > 0) append(" • ${ScreenRecorderService.formatDuration(previewItem.durationSec)}")
              if (previewItem.width > 0 && previewItem.height > 0) append(" • ${previewItem.width}x${previewItem.height}")
              append("\n")
              append(TimeAndSizeFormatters.formatDateTimeWithZone(previewItem.timestampMs, isIndo))
            },
          style = DrxTypographyTokens.caption,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          DrxSecondaryButton(
            label = if (isIndo) "Buka Eksternal" else "Open External",
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            onClick = {
              runCatching {
                val mime = if (previewItem.type == MediaItemType.VIDEO) "video/mp4" else "image/png"
                context.startActivity(
                  Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(previewItem.uriString.toUri(), mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                  }
                )
              }
            },
            modifier = Modifier.weight(1f),
          )
          DrxPrimaryButton(
            label = if (isIndo) "Bagikan" else "Share",
            icon = Icons.Default.Share,
            onClick = {
              runCatching {
                context.startActivity(
                  Intent.createChooser(DrxMediaStorageManager.buildShareIntent(context, previewItem), "Share Media")
                )
              }
            },
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }

  // Delete Confirmation Modal
  val toDelete = pendingDeleteItems
  if (toDelete != null) {
    DrxCustomModal(
      title = if (isIndo) "Hapus Media?" else "Delete Media?",
      onDismiss = { pendingDeleteItems = null },
    ) {
      Text(
        text =
          if (isIndo) "Hapus ${toDelete.size} item terpilih secara permanen dari penyimpanan?"
          else "Permanently delete ${toDelete.size} selected item(s) from storage?",
        style = MaterialTheme.typography.bodyMedium,
      )
      Spacer(Modifier.height(14.dp))
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DrxSecondaryButton(
          label = if (isIndo) "Batal" else "Cancel",
          onClick = { pendingDeleteItems = null },
          modifier = Modifier.weight(1f),
        )
        DrxPrimaryButton(
          label = if (isIndo) "Hapus" else "Delete",
          accentColor = drxColors.alertCrimson,
          onClick = {
            diagnosticsViewModel.deleteMediaItems(toDelete)
            selectedUris.removeAll(toDelete.map { it.uriString }.toSet())
            pendingDeleteItems = null
          },
          modifier = Modifier.weight(1f),
        )
      }
    }
  }

  // Rename Modal
  val renaming = renameTarget
  if (renaming != null) {
    DrxCustomModal(
      title = if (isIndo) "Ubah Nama File" else "Rename File",
      onDismiss = { renameTarget = null },
    ) {
      OutlinedTextField(
        value = renameText,
        onValueChange = { renameText = it },
        singleLine = true,
        label = { Text(if (isIndo) "Nama baru" else "New filename") },
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(14.dp))
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DrxSecondaryButton(
          label = if (isIndo) "Batal" else "Cancel",
          onClick = { renameTarget = null },
          modifier = Modifier.weight(1f),
        )
        DrxPrimaryButton(
          label = if (isIndo) "Simpan" else "Save",
          onClick = {
            diagnosticsViewModel.renameMediaItem(renaming, renameText)
            renameTarget = null
          },
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}
