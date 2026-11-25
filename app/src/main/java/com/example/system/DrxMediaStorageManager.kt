package com.example.system

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.example.data.model.MediaGalleryItem
import com.example.data.model.MediaItemType
import com.example.data.model.ScreenshotFormat
import com.example.data.model.StorageGallerySummary
import java.io.File

object DrxMediaStorageManager {

  const val ROOT_FOLDER_NAME = "DRXGameBooster"
  const val SUBFOLDER_SCREENSHOT = "Screenshot"
  const val SUBFOLDER_RECORDER = "ScreenRecorder"
  const val LEGACY_RECORDER_SUBDIR = "DRX Game Turbo"

  const val MEDIASTORE_VIDEO_REL_PATH = "Movies/$ROOT_FOLDER_NAME/$SUBFOLDER_RECORDER"
  const val MEDIASTORE_IMAGE_REL_PATH = "Pictures/$ROOT_FOLDER_NAME/$SUBFOLDER_SCREENSHOT"

  const val MIN_REQUIRED_RECORDING_BYTES = 250L * 1024L * 1024L // 250 MB safety floor

  fun checkStorageCapacityForRecording(
    context: Context,
    minBytes: Long = MIN_REQUIRED_RECORDING_BYTES,
  ): Pair<Boolean, Long> {
    return try {
      val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: Environment.getDataDirectory()
      val stat = StatFs(targetDir.path)
      val avail = stat.availableBytes.coerceAtLeast(0L)
      (avail >= minBytes) to avail
    } catch (_: Throwable) {
      true to 0L
    }
  }

  fun legacyScreenshotDir(context: Context): File {
    val ext = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    val base = ext ?: File(context.filesDir, "screenshots")
    val dir = File(base, "$ROOT_FOLDER_NAME/$SUBFOLDER_SCREENSHOT")
    if (!dir.exists()) dir.mkdirs()
    return dir
  }

  fun legacyRecordingDir(context: Context): File {
    val ext = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
    val base = ext ?: File(context.filesDir, "recordings")
    val dir = File(base, "$ROOT_FOLDER_NAME/$SUBFOLDER_RECORDER")
    if (!dir.exists()) dir.mkdirs()
    return dir
  }

  fun ensureSafSubfolders(context: Context, treeUriString: String): Boolean {
    if (treeUriString.isBlank()) return false
    return try {
      val treeUri = treeUriString.toUri()
      val docId = DocumentsContract.getTreeDocumentId(treeUri)
      val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
      val boosterDir = findOrCreateSafFolder(context, treeUri, rootDocUri, ROOT_FOLDER_NAME) ?: return false
      findOrCreateSafFolder(context, treeUri, boosterDir, SUBFOLDER_SCREENSHOT)
      findOrCreateSafFolder(context, treeUri, boosterDir, SUBFOLDER_RECORDER)
      true
    } catch (_: Throwable) {
      false
    }
  }

  private fun findOrCreateSafFolder(
    context: Context,
    treeUri: Uri,
    parentDocUri: Uri,
    folderName: String,
  ): Uri? {
    val childrenUri =
      DocumentsContract.buildChildDocumentsUriUsingTree(
        treeUri,
        DocumentsContract.getDocumentId(parentDocUri),
      )
    context.contentResolver.query(
      childrenUri,
      arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
      ),
      null,
      null,
      null,
    )?.use { cursor ->
      while (cursor.moveToNext()) {
        val name = cursor.getString(1)
        val mime = cursor.getString(2)
        if (name == folderName && mime == DocumentsContract.Document.MIME_TYPE_DIR) {
          return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0))
        }
      }
    }
    return DocumentsContract.createDocument(
      context.contentResolver,
      parentDocUri,
      DocumentsContract.Document.MIME_TYPE_DIR,
      folderName,
    )
  }

  fun saveBitmapScreenshot(
    context: Context,
    bitmap: Bitmap,
    fileName: String,
    safTreeUri: String = "",
    format: ScreenshotFormat = ScreenshotFormat.PNG,
  ): MediaGalleryItem? {
    if (bitmap.width <= 0 || bitmap.height <= 0) return null
    if (ScreenshotEngine.isBlankOrBlackBitmap(bitmap)) return null

    val compressFormat =
      if (format == ScreenshotFormat.JPEG) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
    val compressQuality = if (format == ScreenshotFormat.JPEG) 95 else 100
    val mimeType = format.mimeType

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      try {
        val values =
          ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, MEDIASTORE_IMAGE_REL_PATH)
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
            put(MediaStore.Images.Media.IS_PENDING, 1)
          }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
          val compressed =
            context.contentResolver.openOutputStream(uri)?.use { out ->
              bitmap.compress(compressFormat, compressQuality, out).also { out.flush() }
            } ?: false
          if (!compressed) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            return null
          }
          values.clear()
          values.put(MediaStore.Images.Media.IS_PENDING, 0)
          context.contentResolver.update(uri, values, null, null)
          val size = queryFileSize(context, uri)
          if (size <= 0L) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            return null
          }
          mirrorFileToSafIfSet(context, safTreeUri, SUBFOLDER_SCREENSHOT, fileName, mimeType, uri)
          return MediaGalleryItem(
            uriString = uri.toString(),
            displayName = fileName,
            type = MediaItemType.SCREENSHOT,
            sizeBytes = size,
            timestampMs = System.currentTimeMillis(),
            width = bitmap.width,
            height = bitmap.height,
            folderLabel = MEDIASTORE_IMAGE_REL_PATH,
          )
        }
      } catch (_: Throwable) {
      }
    }

    return try {
      val file = File(legacyScreenshotDir(context), fileName)
      val ok = file.outputStream().use {
        bitmap.compress(compressFormat, compressQuality, it).also { _ -> it.flush() }
      }
      if (!ok || !file.exists() || file.length() <= 0L) {
        runCatching { file.delete() }
        return null
      }
      val uri =
        runCatching {
          FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: Uri.fromFile(file)
      MediaGalleryItem(
        uriString = uri.toString(),
        displayName = file.name,
        type = MediaItemType.SCREENSHOT,
        sizeBytes = file.length(),
        timestampMs = file.lastModified(),
        width = bitmap.width,
        height = bitmap.height,
        folderLabel = "$ROOT_FOLDER_NAME/$SUBFOLDER_SCREENSHOT",
      )
    } catch (_: Throwable) {
      null
    }
  }

  fun mirrorFileToSafIfSet(
    context: Context,
    safTreeUri: String,
    subFolder: String,
    displayName: String,
    mimeType: String,
    sourceUri: Uri,
  ) {
    if (safTreeUri.isBlank()) return
    try {
      val treeUri = safTreeUri.toUri()
      val rootId = DocumentsContract.getTreeDocumentId(treeUri)
      val rootDoc = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
      val booster = findOrCreateSafFolder(context, treeUri, rootDoc, ROOT_FOLDER_NAME) ?: return
      val targetDir = findOrCreateSafFolder(context, treeUri, booster, subFolder) ?: return
      val dest = DocumentsContract.createDocument(context.contentResolver, targetDir, mimeType, displayName) ?: return
      context.contentResolver.openInputStream(sourceUri)?.use { input ->
        context.contentResolver.openOutputStream(dest)?.use { output ->
          input.copyTo(output)
        }
      }
    } catch (_: Throwable) {
    }
  }

  fun loadGalleryItems(context: Context): List<MediaGalleryItem> {
    val all = mutableListOf<MediaGalleryItem>()
    all += loadVideos(context)
    all += loadScreenshots(context)
    return all.sortedByDescending { it.timestampMs }
  }

  private fun loadVideos(context: Context): List<MediaGalleryItem> {
    val items = mutableListOf<MediaGalleryItem>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      try {
        val projection =
          arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.RELATIVE_PATH,
          )
        val selection =
          "(${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?) AND ${MediaStore.Video.Media.IS_PENDING} = 0"
        val args = arrayOf("%$ROOT_FOLDER_NAME%", "%$LEGACY_RECORDER_SUBDIR%")
        context.contentResolver.query(
          MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
          projection,
          selection,
          args,
          "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { c ->
          val idIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
          val nameIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
          val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
          val durIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
          val wIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
          val hIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
          val dateIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
          val relIdx = c.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
          while (c.moveToNext() && items.size < 60) {
            val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(idIdx).toString())
            val durMs = c.getLong(durIdx)
            val durSec =
              if (durMs > 0L) (durMs / 1000L).toInt()
              else queryVideoDurationSec(context, uri)
            items +=
              MediaGalleryItem(
                uriString = uri.toString(),
                displayName = c.getString(nameIdx) ?: "DRX_Video.mp4",
                type = MediaItemType.VIDEO,
                sizeBytes = c.getLong(sizeIdx).coerceAtLeast(0L),
                timestampMs = c.getLong(dateIdx) * 1000L,
                durationSec = durSec,
                width = c.getInt(wIdx),
                height = c.getInt(hIdx),
                folderLabel = c.getString(relIdx) ?: MEDIASTORE_VIDEO_REL_PATH,
              )
          }
        }
      } catch (_: Throwable) {
      }
    }

    if (items.isEmpty()) {
      listOf(legacyRecordingDir(context)).forEach { dir ->
        dir.listFiles { f -> f.isFile && f.name.endsWith(".mp4", true) && f.length() > 0L }
          ?.forEach { f ->
            val uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f) }.getOrNull() ?: Uri.fromFile(f)
            items +=
              MediaGalleryItem(
                uriString = uri.toString(),
                displayName = f.name,
                type = MediaItemType.VIDEO,
                sizeBytes = f.length(),
                timestampMs = f.lastModified(),
                durationSec = queryVideoDurationSec(context, uri),
                folderLabel = "$ROOT_FOLDER_NAME/$SUBFOLDER_RECORDER",
              )
          }
      }
    }
    return items
  }

  private fun loadScreenshots(context: Context): List<MediaGalleryItem> {
    val items = mutableListOf<MediaGalleryItem>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      try {
        val projection =
          arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.RELATIVE_PATH,
          )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.IS_PENDING} = 0"
        val args = arrayOf("%$ROOT_FOLDER_NAME%")
        context.contentResolver.query(
          MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
          projection,
          selection,
          args,
          "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
          val idIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
          val nameIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
          val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
          val wIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
          val hIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
          val dateIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
          val relIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
          while (c.moveToNext() && items.size < 60) {
            val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(idIdx).toString())
            items +=
              MediaGalleryItem(
                uriString = uri.toString(),
                displayName = c.getString(nameIdx) ?: "DRX_Screenshot.png",
                type = MediaItemType.SCREENSHOT,
                sizeBytes = c.getLong(sizeIdx).coerceAtLeast(0L),
                timestampMs = c.getLong(dateIdx) * 1000L,
                width = c.getInt(wIdx),
                height = c.getInt(hIdx),
                folderLabel = c.getString(relIdx) ?: MEDIASTORE_IMAGE_REL_PATH,
              )
          }
        }
      } catch (_: Throwable) {
      }
    }

    if (items.isEmpty()) {
      legacyScreenshotDir(context)
        .listFiles { f ->
          f.isFile &&
            (f.name.endsWith(".png", true) || f.name.endsWith(".jpg", true) || f.name.endsWith(".jpeg", true)) &&
            f.length() > 0L
        }
        ?.forEach { f ->
          val uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f) }.getOrNull() ?: Uri.fromFile(f)
          items +=
            MediaGalleryItem(
              uriString = uri.toString(),
              displayName = f.name,
              type = MediaItemType.SCREENSHOT,
              sizeBytes = f.length(),
              timestampMs = f.lastModified(),
              folderLabel = "$ROOT_FOLDER_NAME/$SUBFOLDER_SCREENSHOT",
            )
        }
    }
    return items
  }

  private fun queryVideoDurationSec(context: Context, uri: Uri): Int {
    val mmr = MediaMetadataRetriever()
    return try {
      mmr.setDataSource(context, uri)
      val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
      (durMs / 1000L).toInt().coerceAtLeast(0)
    } catch (_: Throwable) {
      0
    } finally {
      runCatching { mmr.release() }
    }
  }

  private fun queryFileSize(context: Context, uri: Uri): Long {
    return try {
      context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getLong(0).coerceAtLeast(0L) else 0L
      } ?: 0L
    } catch (_: Throwable) {
      0L
    }
  }

  fun loadThumbnailBitmap(context: Context, item: MediaGalleryItem): Bitmap? {
    return try {
      val uri = item.uriString.toUri()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.contentResolver.loadThumbnail(uri, Size(240, 240), null)
      } else if (item.type == MediaItemType.SCREENSHOT) {
        context.contentResolver.openInputStream(uri)?.use {
          BitmapFactory.decodeStream(it)
        }
      } else {
        @Suppress("DEPRECATION")
        uri.path?.let { ThumbnailUtils.createVideoThumbnail(it, MediaStore.Images.Thumbnails.MINI_KIND) }
      }
    } catch (_: Throwable) {
      null
    }
  }

  fun computeStorageSummary(
    context: Context,
    items: List<MediaGalleryItem>,
  ): StorageGallerySummary {
    val screenshots = items.count { it.type == MediaItemType.SCREENSHOT }
    val videos = items.filter { it.type == MediaItemType.VIDEO }
    val totalBytes = items.sumOf { it.sizeBytes }
    val largest = videos.maxByOrNull { it.sizeBytes }
    val stat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
    val avail = stat?.availableBytes ?: 0L
    val tot = stat?.totalBytes ?: 0L
    return StorageGallerySummary(
      screenshotCount = screenshots,
      videoCount = videos.size,
      totalMediaBytes = totalBytes,
      largestVideoName = largest?.displayName,
      largestVideoBytes = largest?.sizeBytes ?: 0L,
      availableDeviceBytes = avail,
      totalDeviceBytes = tot,
    )
  }

  fun deleteMediaItem(context: Context, uriString: String): Boolean {
    return try {
      context.contentResolver.delete(uriString.toUri(), null, null) > 0
    } catch (_: Throwable) {
      false
    }
  }

  fun renameMediaItem(context: Context, item: MediaGalleryItem, newBaseName: String): Boolean {
    val clean = newBaseName.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
    if (clean.isBlank()) return false
    val ext =
      when {
        item.type == MediaItemType.VIDEO -> ".mp4"
        item.displayName.endsWith(".jpg", true) -> ".jpg"
        item.displayName.endsWith(".jpeg", true) -> ".jpeg"
        else -> ".png"
      }
    val finalName = if (clean.endsWith(ext, true)) clean else "$clean$ext"
    return try {
      val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, finalName) }
      context.contentResolver.update(item.uriString.toUri(), values, null, null) > 0
    } catch (_: Throwable) {
      false
    }
  }

  fun buildShareIntent(context: Context, item: MediaGalleryItem): Intent {
    val mime =
      when {
        item.type == MediaItemType.VIDEO -> "video/mp4"
        item.displayName.endsWith(".jpg", true) || item.displayName.endsWith(".jpeg", true) -> "image/jpeg"
        else -> "image/png"
      }
    return Intent(Intent.ACTION_SEND).apply {
      type = mime
      putExtra(Intent.EXTRA_STREAM, item.uriString.toUri())
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  }

  fun buildMultiShareIntent(items: List<MediaGalleryItem>): Intent {
    val uris = ArrayList(items.map { it.uriString.toUri() })
    val mixed = items.map { it.type }.toSet().size > 1
    val mime =
      when {
        mixed -> "*/*"
        items.firstOrNull()?.type == MediaItemType.VIDEO -> "video/mp4"
        else -> "image/png"
      }
    return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
      type = mime
      putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  }
}
