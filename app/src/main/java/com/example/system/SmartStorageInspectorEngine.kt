package com.example.system

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import com.example.data.db.GameEntity
import com.example.data.model.GameAppStorageEntry
import com.example.data.model.MediaItemType
import com.example.data.model.SmartStorageSnapshot
import java.io.File
import java.util.UUID

object SmartStorageInspectorEngine {

  fun computeDirectorySizeBytes(dir: File?): Long {
    if (dir == null || !dir.exists()) return 0L
    if (dir.isFile) return dir.length().coerceAtLeast(0L)
    var sum = 0L
    dir.listFiles()?.forEach { child ->
      sum += computeDirectorySizeBytes(child)
    }
    return sum.coerceAtLeast(0L)
  }

  fun computeDrxCacheBytes(context: Context): Long {
    val internal = computeDirectorySizeBytes(context.cacheDir)
    val external = computeDirectorySizeBytes(context.externalCacheDir)
    return (internal + external).coerceAtLeast(0L)
  }

  fun clearDrxCache(context: Context): Long {
    val before = computeDrxCacheBytes(context)
    listOfNotNull(context.cacheDir, context.externalCacheDir).forEach { dir ->
      dir.listFiles()?.forEach { child ->
        runCatching { child.deleteRecursively() }
      }
    }
    val after = computeDrxCacheBytes(context)
    return (before - after).coerceAtLeast(0L)
  }

  fun inspectStorage(
    context: Context,
    games: List<GameEntity>,
    hasUsageAccess: Boolean,
  ): SmartStorageSnapshot {
    val stat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
    val totalBytes = stat?.totalBytes?.coerceAtLeast(0L) ?: 0L
    val freeBytes = stat?.availableBytes?.coerceAtLeast(0L) ?: 0L
    val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

    val drxCache = computeDrxCacheBytes(context)
    val media = DrxMediaStorageManager.loadGalleryItems(context)
    val screenshots = media.filter { it.type == MediaItemType.SCREENSHOT }
    val recordings = media.filter { it.type == MediaItemType.VIDEO }

    val gameEntries = mutableListOf<GameAppStorageEntry>()
    var gamesSumOrNull: Long? = null
    if (hasUsageAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && games.isNotEmpty()) {
      val ssm = runCatching { context.getSystemService(StorageStatsManager::class.java) }.getOrNull()
      if (ssm != null) {
        var acc = 0L
        var anyMeasured = false
        val uuid: UUID = StorageManager.UUID_DEFAULT
        val user = Process.myUserHandle()
        games.forEach { game ->
          runCatching {
            val stats = ssm.queryStatsForPackage(uuid, game.packageName, user)
            val tot = (stats.appBytes + stats.dataBytes + stats.cacheBytes).coerceAtLeast(0L)
            acc += tot
            anyMeasured = true
            gameEntries += GameAppStorageEntry(game.packageName, game.appName, tot)
          }
        }
        if (anyMeasured) gamesSumOrNull = acc
      }
    }

    return SmartStorageSnapshot(
      deviceTotalBytes = totalBytes,
      deviceUsedBytes = usedBytes,
      deviceFreeBytes = freeBytes,
      drxCacheBytes = drxCache,
      screenshotsCount = screenshots.size,
      screenshotsBytes = screenshots.sumOf { it.sizeBytes },
      recordingsCount = recordings.size,
      recordingsBytes = recordings.sumOf { it.sizeBytes },
      gameAppsTotalBytes = gamesSumOrNull,
      gameAppsCount = games.size,
      topGamesStorage = gameEntries.sortedByDescending { it.totalBytes }.take(8),
      hasUsageAccessForApps = hasUsageAccess,
    )
  }
}
