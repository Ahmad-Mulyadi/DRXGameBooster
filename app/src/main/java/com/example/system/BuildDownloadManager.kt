package com.example.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.model.BuildArtifactStatus
import com.example.data.model.BuildPipelineState
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BuildDownloadManager(private val context: Context) {

  companion object {
    const val APK_FILE_NAME = "DRX-GAME-TURBO-PRO.apk"
    const val APK_VERSIONED_NAME = "DRX-GAME-TURBO-PRO-v${BuildConfig.VERSION_NAME}.apk"
    const val APK_DEBUG_NAME = "DRX-GAME-TURBO-PRO-DEBUG.apk"
    const val AAB_FILE_NAME = "DRX-GAME-TURBO-PRO.aab"
  }

  fun exportDir(): File {
    val dir = File(context.cacheDir, "exports")
    if (!dir.exists()) dir.mkdirs()
    return dir
  }

  fun validateZipArchive(file: File): Boolean {
    if (!file.exists() || file.length() <= 1024L) return false
    return try {
      val header = ByteArray(4)
      file.inputStream().use { input ->
        if (input.read(header) < 4) return false
      }
      if (header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) return false
      var entryCount = 0
      var hasManifest = false
      ZipInputStream(file.inputStream().buffered()).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
          entryCount++
          if (entry.name.equals("AndroidManifest.xml", ignoreCase = true) ||
            entry.name.endsWith("AndroidManifest.xml", ignoreCase = true)
          ) {
            hasManifest = true
          }
          zis.closeEntry()
          entry = zis.nextEntry
        }
      }
      entryCount > 2 && hasManifest
    } catch (_: Throwable) {
      false
    }
  }

  fun computeSha256Short(file: File): String {
    return try {
      val digest = MessageDigest.getInstance("SHA-256")
      file.inputStream().buffered().use { input ->
        val buf = ByteArray(8192)
        while (true) {
          val n = input.read(buf)
          if (n <= 0) break
          digest.update(buf, 0, n)
        }
      }
      digest.digest().joinToString("") { "%02x".format(it) }.take(16)
    } catch (_: Throwable) {
      "unavailable"
    }
  }

  suspend fun inspectCachedVerifiedExport(): BuildArtifactStatus? =
    withContext(Dispatchers.IO) {
      val targetFile = File(exportDir(), APK_FILE_NAME)
      if (!targetFile.exists() || targetFile.length() <= 1024L) return@withContext null
      if (!validateZipArchive(targetFile) || !verifyPackageArchive(targetFile)) {
        targetFile.delete()
        return@withContext null
      }
      val shaShort = computeSha256Short(targetFile)
      val hasSplits = !context.applicationInfo?.splitSourceDirs.isNullOrEmpty()
      val splitNote = if (hasSplits) " (base APK of split install)" else ""
      BuildArtifactStatus(
        title = "Installable APK (On-Device Package Export)",
        fileName = APK_FILE_NAME,
        locationLabel = "exports/$APK_FILE_NAME (FileProvider + SAF)$splitNote",
        sizeBytes = targetFile.length(),
        isValidArchive = true,
        sha256Short = shaShort,
        signingNote = "Signed with active installation key • v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        downloadSupported = true,
        exportFilePath = targetFile.absolutePath,
      )
    }

  suspend fun prepareVerifiedApkExport(
    onStateChange: (BuildPipelineState, String) -> Unit,
  ): Result<BuildArtifactStatus> =
    withContext(Dispatchers.IO) {
      try {
        onStateChange(BuildPipelineState.CHECKING_SOURCE, "Checking installed package source...")
        val sourceApkPath = context.applicationInfo?.sourceDir
        if (sourceApkPath.isNullOrBlank()) {
          onStateChange(BuildPipelineState.BUILD_FAILED, "Package sourceDir unavailable in current runtime.")
          return@withContext Result.failure(IllegalStateException("Package sourceDir unavailable"))
        }
        val sourceFile = File(sourceApkPath)
        if (!sourceFile.exists() || sourceFile.length() <= 0L) {
          onStateChange(BuildPipelineState.BUILD_FAILED, "Installed APK file not found on filesystem.")
          return@withContext Result.failure(IllegalStateException("Source APK missing"))
        }

        val dir = exportDir()
        val availBytes =
          runCatching { StatFs(dir.absolutePath).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (availBytes < sourceFile.length() + 4L * 1024L * 1024L) {
          onStateChange(BuildPipelineState.BUILD_FAILED, "Insufficient internal storage for verified APK export.")
          return@withContext Result.failure(IllegalStateException("Insufficient storage space"))
        }

        onStateChange(BuildPipelineState.BUILDING_APK, "Staging $APK_FILE_NAME...")
        val tempFile = File(dir, "$APK_FILE_NAME.tmp")
        val targetFile = File(dir, APK_FILE_NAME)
        if (tempFile.exists()) tempFile.delete()
        val copiedBytes =
          sourceFile.inputStream().buffered().use { input ->
            tempFile.outputStream().buffered().use { output ->
              val n = input.copyTo(output)
              output.flush()
              n
            }
          }
        if (copiedBytes != sourceFile.length()) {
          tempFile.delete()
          onStateChange(BuildPipelineState.BUILD_FAILED, "Incomplete staging copy ($copiedBytes / ${sourceFile.length()} bytes).")
          return@withContext Result.failure(IllegalStateException("Incomplete APK copy"))
        }

        onStateChange(BuildPipelineState.VALIDATING, "Verifying ZIP integrity & AndroidManifest...")
        if (!validateZipArchive(tempFile)) {
          tempFile.delete()
          onStateChange(BuildPipelineState.BUILD_FAILED, "ZIP archive integrity verification failed.")
          return@withContext Result.failure(IllegalStateException("Invalid APK archive"))
        }

        val archivePkgOk = verifyPackageArchive(tempFile)
        if (!archivePkgOk) {
          tempFile.delete()
          onStateChange(BuildPipelineState.BUILD_FAILED, "APK manifest verification failed.")
          return@withContext Result.failure(IllegalStateException("Manifest check failed"))
        }

        onStateChange(BuildPipelineState.REPLACING_OLD_BUILD, "Replacing previous verified export atomically...")
        if (targetFile.exists()) targetFile.delete()
        if (!tempFile.renameTo(targetFile)) {
          tempFile.copyTo(targetFile, overwrite = true)
          tempFile.delete()
        }

        val shaShort = computeSha256Short(targetFile)
        val hasSplits = !context.applicationInfo?.splitSourceDirs.isNullOrEmpty()
        val splitNote = if (hasSplits) " (base APK of split install)" else ""
        onStateChange(BuildPipelineState.READY_TO_DOWNLOAD, "Verified $APK_FILE_NAME (${TimeAndSizeFormatters.formatBytesAuto(targetFile.length())})")
        Result.success(
          BuildArtifactStatus(
            title = "Installable APK (On-Device Package Export)",
            fileName = APK_FILE_NAME,
            locationLabel = "exports/$APK_FILE_NAME (FileProvider + SAF)$splitNote",
            sizeBytes = targetFile.length(),
            isValidArchive = true,
            sha256Short = shaShort,
            signingNote = "Signed with active installation key • v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            downloadSupported = true,
            exportFilePath = targetFile.absolutePath,
          )
        )
      } catch (t: Throwable) {
        onStateChange(BuildPipelineState.BUILD_FAILED, "Export failed: ${t.message ?: "Unknown error"}")
        Result.failure(t)
      }
    }

  private fun verifyPackageArchive(apkFile: File): Boolean {
    return try {
      val pm = context.packageManager
      val info =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(0L))
        } else {
          @Suppress("DEPRECATION")
          pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
        }
      if (info == null) return true
      val versionMatches =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          info.longVersionCode == BuildConfig.VERSION_CODE.toLong()
        } else {
          true
        }
      info.packageName == context.packageName && versionMatches
    } catch (_: Throwable) {
      true
    }
  }

  fun copyExportedApkToUri(destUri: Uri, apkFile: File): Boolean {
    if (!apkFile.exists() || apkFile.length() <= 0L) return false
    return try {
      val expectedBytes = apkFile.length()
      var copiedBytes = 0L
      apkFile.inputStream().buffered().use { input ->
        context.contentResolver.openOutputStream(destUri)?.buffered()?.use { output ->
          copiedBytes = input.copyTo(output)
          output.flush()
        } ?: return false
      }
      copiedBytes == expectedBytes
    } catch (_: Throwable) {
      false
    }
  }

  fun buildShareApkIntent(apkFile: File): Intent? {
    if (!apkFile.exists()) return null
    val uri =
      runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
      }.getOrNull() ?: return null
    return Intent(Intent.ACTION_SEND).apply {
      type = "application/vnd.android.package-archive"
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  }

  fun buildAabInfoPlaceholder(isIndo: Boolean): BuildArtifactStatus {
    return BuildArtifactStatus(
      title = "Android App Bundle (.aab)",
      fileName = AAB_FILE_NAME,
      locationLabel = "Workspace output: src/aab/$AAB_FILE_NAME",
      sizeBytes = 0L,
      isValidArchive = false,
      sha256Short = "Gradle Host Build",
      signingNote =
        if (isIndo) {
          "AAB Play Console dibangun lewat pipeline Gradle host (src/aab/$AAB_FILE_NAME). Gunakan APK Export di atas untuk instalasi langsung di perangkat."
        } else {
          "Play Console AAB is produced by the host Gradle release pipeline (src/aab/$AAB_FILE_NAME). Use APK Export above for direct device installation."
        },
      downloadSupported = false,
      exportFilePath = null,
    )
  }
}
