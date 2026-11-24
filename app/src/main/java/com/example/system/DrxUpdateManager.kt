package com.example.system

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.example.BuildConfig
import com.example.data.model.ApkVerificationResult
import com.example.data.model.UpdateManifest
import com.example.data.model.UpdateOfferPolicyState
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class ManifestEvaluationStatus {
  UP_TO_DATE,
  UPDATE_AVAILABLE,
  SOURCE_NOT_CONFIGURED,
  OFFLINE,
  REJECTED_SAME_OR_LOWER_VERSION,
  REJECTED_APP_ID_MISMATCH,
  REJECTED_SIGNATURE_MISMATCH,
  REJECTED_DEVELOPER_WARNING,
  REJECTED_MIN_SDK,
}

data class ManifestEvaluationResult(
  val status: ManifestEvaluationStatus,
  val isInstallableUpdate: Boolean,
  val reason: String,
)

object UpdatePolicyEngine {

  fun selectBestManifest(manifests: List<UpdateManifest>): UpdateManifest? {
    if (manifests.isEmpty()) return null
    return manifests.maxWithOrNull { a, b ->
      val cmpCode = a.versionCode.compareTo(b.versionCode)
      if (cmpCode != 0) {
        cmpCode
      } else {
        val aScore = apkTypePriority(a.apkType)
        val bScore = apkTypePriority(b.apkType)
        aScore.compareTo(bScore)
      }
    }
  }

  private fun apkTypePriority(apkType: String): Int =
    when {
      apkType.equals("release", ignoreCase = true) -> 2
      apkType.equals("debug", ignoreCase = true) -> 1
      else -> 0
    }

  fun evaluateSkipPolicy(
    currentVersionCode: Int,
    targetVersionCode: Int,
    lastOfferedVersionCode: Int,
    skipCount: Int,
  ): UpdateOfferPolicyState {
    if (targetVersionCode <= currentVersionCode || targetVersionCode <= 0) {
      return UpdateOfferPolicyState.NONE
    }
    if (lastOfferedVersionCode != targetVersionCode) {
      return UpdateOfferPolicyState.OPTIONAL_OFFER_1
    }
    return when {
      skipCount <= 0 -> UpdateOfferPolicyState.OPTIONAL_OFFER_1
      skipCount == 1 -> UpdateOfferPolicyState.OPTIONAL_OFFER_2
      else -> UpdateOfferPolicyState.MANDATORY_REQUIRED
    }
  }

  fun evaluateManifestAgainstInstalled(
    manifest: UpdateManifest?,
    installedPackageName: String,
    installedVersionCode: Int,
    installedSigningCertSha256s: Set<String> = emptySet(),
    deviceApiLevel: Int = Build.VERSION.SDK_INT,
    hasLocalStagedFile: Boolean = false,
    hasRemoteSourceConfigured: Boolean = false,
    isOnline: Boolean = true,
  ): ManifestEvaluationResult {
    if (manifest == null) {
      return when {
        !isOnline && hasRemoteSourceConfigured ->
          ManifestEvaluationResult(
            status = ManifestEvaluationStatus.OFFLINE,
            isInstallableUpdate = false,
            reason = "Offline and no cached update manifest is available.",
          )
        !hasRemoteSourceConfigured ->
          ManifestEvaluationResult(
            status = ManifestEvaluationStatus.SOURCE_NOT_CONFIGURED,
            isInstallableUpdate = false,
            reason = "Update Source Not Configured.",
          )
        else ->
          ManifestEvaluationResult(
            status = ManifestEvaluationStatus.UP_TO_DATE,
            isInstallableUpdate = false,
            reason = "No update manifest entries found.",
          )
      }
    }

    if (manifest.appId.isNotBlank() && manifest.appId != installedPackageName) {
      return ManifestEvaluationResult(
        status = ManifestEvaluationStatus.REJECTED_APP_ID_MISMATCH,
        isInstallableUpdate = false,
        reason = "Package ID mismatch (${manifest.appId} vs $installedPackageName).",
      )
    }

    if (manifest.developerWarning.isNotBlank() && manifest.versionCode <= installedVersionCode) {
      return ManifestEvaluationResult(
        status = ManifestEvaluationStatus.REJECTED_SAME_OR_LOWER_VERSION,
        isInstallableUpdate = false,
        reason = manifest.developerWarning,
      )
    }

    if (manifest.developerWarning.isNotBlank()) {
      return ManifestEvaluationResult(
        status = ManifestEvaluationStatus.REJECTED_DEVELOPER_WARNING,
        isInstallableUpdate = false,
        reason = manifest.developerWarning,
      )
    }

    if (manifest.versionCode < installedVersionCode) {
      return ManifestEvaluationResult(
        status = ManifestEvaluationStatus.REJECTED_SAME_OR_LOWER_VERSION,
        isInstallableUpdate = false,
        reason =
          "Developer Warning: Target APK versionCode (${manifest.versionCode}) is lower than installed ($installedVersionCode). Downgrade blocked.",
      )
    }

    if (manifest.versionCode == installedVersionCode) {
      if (!hasRemoteSourceConfigured && manifest.downloadUrl.isBlank() && !hasLocalStagedFile) {
        return ManifestEvaluationResult(
          status = ManifestEvaluationStatus.UP_TO_DATE,
          isInstallableUpdate = false,
          reason = "Installed v${BuildConfig.VERSION_NAME} (#$installedVersionCode) is up to date.",
        )
      }
      return ManifestEvaluationResult(
        status = ManifestEvaluationStatus.UP_TO_DATE,
        isInstallableUpdate = false,
        reason = "Installed v${BuildConfig.VERSION_NAME} (#$installedVersionCode) is up to date.",
      )
    }

    // manifest.versionCode > installedVersionCode
    if (deviceApiLevel < manifest.minSdkVersion) {
      return ManifestEvaluationResult(
        status = ManifestEvaluationStatus.REJECTED_MIN_SDK,
        isInstallableUpdate = false,
        reason = "Update requires Android API ${manifest.minSdkVersion}+ (device API $deviceApiLevel).",
      )
    }

    if (
      manifest.signingCertSha256.isNotBlank() &&
        installedSigningCertSha256s.isNotEmpty() &&
        installedSigningCertSha256s.none { it.equals(manifest.signingCertSha256.trim(), ignoreCase = true) }
    ) {
      return ManifestEvaluationResult(
        status = ManifestEvaluationStatus.REJECTED_SIGNATURE_MISMATCH,
        isInstallableUpdate = false,
        reason =
          "Signing certificate mismatch: update APK is signed by a different key and cannot be installed cleanly.",
      )
    }

    val hasArtifactLocation = manifest.downloadUrl.isNotBlank() || hasLocalStagedFile
    if (!hasArtifactLocation) {
      return ManifestEvaluationResult(
        status = ManifestEvaluationStatus.SOURCE_NOT_CONFIGURED,
        isInstallableUpdate = false,
        reason = "Update Source Not Configured (no HTTPS download URL or local APK staged).",
      )
    }

    if (!isOnline && !hasLocalStagedFile) {
      return ManifestEvaluationResult(
        status = ManifestEvaluationStatus.OFFLINE,
        isInstallableUpdate = false,
        reason = "Offline: Update v${manifest.versionName} (#${manifest.versionCode}) cached. Connect to download.",
      )
    }

    return ManifestEvaluationResult(
      status = ManifestEvaluationStatus.UPDATE_AVAILABLE,
      isInstallableUpdate = true,
      reason = "Update v${manifest.versionName} (#${manifest.versionCode}) available (${manifest.apkType}).",
    )
  }

  fun parseManifestJson(rawJson: String): List<UpdateManifest> {
    val trimmed = rawJson.trim()
    if (trimmed.isEmpty()) return emptyList()
    val items = mutableListOf<UpdateManifest>()
    if (trimmed.startsWith("[")) {
      val arr = JSONArray(trimmed)
      for (i in 0 until arr.length()) {
        arr.optJSONObject(i)?.let { items += parseSingleObject(it) }
      }
    } else {
      val root = JSONObject(trimmed)
      val arr = root.optJSONArray("artifacts") ?: root.optJSONArray("manifests")
      if (arr != null && arr.length() > 0) {
        for (i in 0 until arr.length()) {
          arr.optJSONObject(i)?.let { items += parseSingleObject(it) }
        }
      }
      if (root.has("versionCode") || root.has("versionName")) {
        items += parseSingleObject(root)
      }
    }
    return items.distinctBy { "${it.versionCode}_${it.apkType}_${it.localFileName}" }
  }

  fun resolveDownloadUrlsAgainstManifest(
    manifests: List<UpdateManifest>,
    manifestUrl: String,
  ): List<UpdateManifest> {
    val trimmedManifest = manifestUrl.trim()
    if (trimmedManifest.isBlank()) return manifests
    val baseUri = runCatching { java.net.URI(trimmedManifest) }.getOrNull() ?: return manifests
    return manifests.map { m ->
      val currentDl = m.downloadUrl.trim()
      val resolved =
        when {
          currentDl.startsWith("https://", ignoreCase = true) ||
            currentDl.startsWith("http://", ignoreCase = true) -> currentDl
          currentDl.isNotBlank() -> runCatching { baseUri.resolve(currentDl).toString() }.getOrDefault(currentDl)
          m.localFileName.isNotBlank() -> runCatching { baseUri.resolve(m.localFileName).toString() }.getOrDefault("")
          else -> ""
        }
      if (resolved == m.downloadUrl) m else m.copy(downloadUrl = resolved)
    }
  }

  private fun parseSingleObject(obj: JSONObject): UpdateManifest {
    val fileName =
      obj.optString("apkFileName", "").ifBlank { obj.optString("localFileName", "") }
    val sizeVal =
      if (obj.has("fileSize") && obj.optLong("fileSize", 0L) > 0L) {
        obj.optLong("fileSize", 0L)
      } else {
        obj.optLong("fileSizeBytes", 0L)
      }
    val minSdk =
      if (obj.has("minimumSupportedVersion") && obj.optInt("minimumSupportedVersion", 0) > 0) {
        obj.optInt("minimumSupportedVersion", 24)
      } else {
        obj.optInt("minSdkVersion", 24)
      }
    val releaseType =
      obj.optString("releaseType", "").ifBlank { obj.optString("apkType", "release") }
    val appId =
      obj.optString("applicationId", "").ifBlank { obj.optString("appId", BuildConfig.APPLICATION_ID) }
    val signingCert =
      obj.optString("signingCertificateFingerprint", "").ifBlank { obj.optString("signingCertSha256", "") }

    return UpdateManifest(
      versionName = obj.optString("versionName", BuildConfig.VERSION_NAME),
      versionCode = obj.optInt("versionCode", BuildConfig.VERSION_CODE),
      releaseDate = obj.optString("releaseDate", BuildConfig.BUILD_DATE),
      releaseNotes = obj.optString("releaseNotes", ""),
      apkType = releaseType,
      downloadUrl = obj.optString("downloadUrl", ""),
      localFileName = fileName,
      fileSizeBytes = sizeVal,
      sha256 = obj.optString("sha256", ""),
      isMandatorySuggested = obj.optBoolean("isMandatorySuggested", false),
      minSdkVersion = minSdk,
      appId = appId,
      signingCertSha256 = signingCert,
      developerWarning = obj.optString("developerWarning", ""),
    )
  }
}

class DrxUpdateManager(private val context: Context) {

  fun updatesDir(): File {
    val dir = File(context.filesDir, "updates")
    if (!dir.exists()) dir.mkdirs()
    return dir
  }

  fun isNetworkAvailable(): Boolean {
    return runCatching {
      val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
      val network = cm.activeNetwork ?: return false
      val caps = cm.getNetworkCapabilities(network) ?: return false
      caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)
  }

  fun loadBundledManifestRaw(): String {
    return try {
      context.assets.open("update-manifest.json").bufferedReader().use { it.readText() }
    } catch (_: Throwable) {
      ""
    }
  }

  fun loadBundledManifests(): List<UpdateManifest> {
    val raw = loadBundledManifestRaw()
    return if (raw.isBlank()) emptyList() else runCatching { UpdatePolicyEngine.parseManifestJson(raw) }.getOrDefault(emptyList())
  }

  suspend fun fetchRemoteManifestRaw(urlString: String): Result<String> =
    withContext(Dispatchers.IO) {
      val trimmed = urlString.trim()
      if (trimmed.isBlank()) {
        return@withContext Result.failure(IllegalArgumentException("Manifest URL is empty."))
      }
      val uri = runCatching { trimmed.toUri() }.getOrNull()
      val scheme = uri?.scheme?.lowercase(Locale.US)
      val host = uri?.host?.lowercase(Locale.US).orEmpty()
      val isAllowedHttp =
        scheme == "https" ||
          (scheme == "http" && (host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"))
      if (!isAllowedHttp) {
        return@withContext Result.failure(SecurityException("Only HTTPS update manifests are permitted."))
      }

      var conn: HttpURLConnection? = null
      try {
        conn = (URL(trimmed).openConnection() as HttpURLConnection).apply {
          connectTimeout = 8000
          readTimeout = 8000
          requestMethod = "GET"
          setRequestProperty("Accept", "application/json")
        }
        val code = conn.responseCode
        if (code !in 200..299) {
          return@withContext Result.failure(IllegalStateException("HTTP $code while loading manifest."))
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val parsed = UpdatePolicyEngine.parseManifestJson(body)
        if (parsed.isEmpty()) {
          Result.failure(IllegalStateException("Remote manifest contained no valid entries."))
        } else {
          Result.success(body)
        }
      } catch (t: Throwable) {
        Result.failure(t)
      } finally {
        conn?.disconnect()
      }
    }

  suspend fun fetchRemoteManifests(urlString: String): Result<List<UpdateManifest>> {
    return fetchRemoteManifestRaw(urlString).mapCatching { body ->
      UpdatePolicyEngine.parseManifestJson(body)
    }
  }

  suspend fun findExistingLocalUpdateApk(manifest: UpdateManifest): File? =
    withContext(Dispatchers.IO) {
      val candidates = buildList {
        if (manifest.localFileName.isNotBlank()) {
          add(File(updatesDir(), manifest.localFileName))
          add(File(File(context.cacheDir, "exports"), manifest.localFileName))
        }
        add(File(updatesDir(), "DRX-GAME-TURBO-PRO-v${manifest.versionName}.apk"))
        add(File(updatesDir(), BuildDownloadManager.APK_FILE_NAME))
        add(File(File(context.cacheDir, "exports"), BuildDownloadManager.APK_FILE_NAME))
      }
      candidates.firstOrNull { it.exists() && it.length() > 1024L && validateZipArchive(it) }
    }

  suspend fun downloadOrStageApk(
    manifest: UpdateManifest,
    onProgress: (Float, Long, Long) -> Unit,
  ): Result<File> =
    withContext(Dispatchers.IO) {
      val outName =
        manifest.localFileName.ifBlank { "DRX-GAME-TURBO-PRO-v${manifest.versionName}.apk" }
      val targetFile = File(updatesDir(), outName)
      val tempFile = File(updatesDir(), "$outName.download")

      val remoteUrl = manifest.downloadUrl.trim()
      if (remoteUrl.isNotBlank()) {
        val uri = runCatching { remoteUrl.toUri() }.getOrNull()
        val scheme = uri?.scheme?.lowercase(Locale.US)
        val host = uri?.host?.lowercase(Locale.US).orEmpty()
        val isAllowedUrl =
          scheme == "https" ||
            (scheme == "http" && (host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"))
        if (!isAllowedUrl) {
          return@withContext Result.failure(SecurityException("Download URL must use HTTPS."))
        }

        var conn: HttpURLConnection? = null
        try {
          if (tempFile.exists()) tempFile.delete()
          conn = (URL(remoteUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
          }
          val code = conn.responseCode
          if (code !in 200..299) {
            return@withContext Result.failure(IllegalStateException("Download failed with HTTP $code."))
          }
          val totalBytes = conn.contentLengthLong.takeIf { it > 0L } ?: manifest.fileSizeBytes
          var downloaded = 0L
          conn.inputStream.buffered().use { input ->
            tempFile.outputStream().buffered().use { output ->
              val buf = ByteArray(16 * 1024)
              while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                output.write(buf, 0, read)
                downloaded += read
                val progress = if (totalBytes > 0L) (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else -1f
                onProgress(progress, downloaded, totalBytes)
              }
              output.flush()
            }
          }
          if (downloaded <= 1024L) {
            tempFile.delete()
            return@withContext Result.failure(IllegalStateException("Downloaded file is empty or incomplete."))
          }
          if (targetFile.exists()) targetFile.delete()
          if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
          }
          return@withContext Result.success(targetFile)
        } catch (t: Throwable) {
          if (tempFile.exists()) tempFile.delete()
          return@withContext Result.failure(t)
        } finally {
          conn?.disconnect()
        }
      }

      // Check existing local staged APK
      val existingLocal = findExistingLocalUpdateApk(manifest)
      if (existingLocal != null) {
        onProgress(1f, existingLocal.length(), existingLocal.length())
        return@withContext Result.success(existingLocal)
      }

      Result.failure(
        IllegalStateException(
          "Update Source Not Configured: set an HTTPS manifest/download URL or select a verified local APK."
        )
      )
    }

  suspend fun importApkFromUri(uri: Uri): Result<File> =
    withContext(Dispatchers.IO) {
      val dest = File(updatesDir(), "imported_update_${System.currentTimeMillis()}.apk")
      try {
        context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
          dest.outputStream().buffered().use { output ->
            input.copyTo(output)
            output.flush()
          }
        } ?: return@withContext Result.failure(IllegalStateException("Unable to read selected APK file."))

        if (!validateZipArchive(dest)) {
          dest.delete()
          return@withContext Result.failure(IllegalStateException("Selected file is not a valid APK archive."))
        }
        Result.success(dest)
      } catch (t: Throwable) {
        if (dest.exists()) dest.delete()
        Result.failure(t)
      }
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

  fun computeSha256Full(file: File): String {
    return try {
      val digest = MessageDigest.getInstance("SHA-256")
      file.inputStream().buffered().use { input ->
        val buf = ByteArray(16 * 1024)
        while (true) {
          val n = input.read(buf)
          if (n <= 0) break
          digest.update(buf, 0, n)
        }
      }
      digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Throwable) {
      ""
    }
  }

  fun verifyApkFile(
    apkFile: File,
    expectedPackageName: String = context.packageName,
    expectedSha256: String = "",
    expectedSigningCertSha256: String = "",
    requireMinimumVersionCode: Long = 0L,
    requireStrictlyHigherThanVersionCode: Long = 0L,
    requireSignatureMatch: Boolean = true,
  ): ApkVerificationResult {
    if (!apkFile.exists() || apkFile.length() <= 1024L) {
      return ApkVerificationResult(isValid = false, errorReason = "APK file is missing or too small.")
    }
    if (!validateZipArchive(apkFile)) {
      return ApkVerificationResult(isValid = false, errorReason = "Invalid APK ZIP structure or missing AndroidManifest.xml.")
    }

    val sha256Full = computeSha256Full(apkFile)
    if (sha256Full.isBlank()) {
      return ApkVerificationResult(isValid = false, errorReason = "Failed to compute SHA-256 checksum.")
    }
    if (expectedSha256.isNotBlank() && !sha256Full.equals(expectedSha256.trim(), ignoreCase = true)) {
      return ApkVerificationResult(
        isValid = false,
        fileSizeBytes = apkFile.length(),
        sha256Full = sha256Full,
        errorReason = "SHA-256 checksum mismatch with update manifest.",
      )
    }

    val pm = context.packageManager
    val flags =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES.toLong()
      else {
        @Suppress("DEPRECATION")
        PackageManager.GET_SIGNATURES.toLong()
      }

    val archiveInfo: PackageInfo? =
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags))
        } else {
          @Suppress("DEPRECATION")
          pm.getPackageArchiveInfo(apkFile.absolutePath, flags.toInt())
        }
      } catch (_: Throwable) {
        null
      }

    if (archiveInfo == null) {
      return ApkVerificationResult(
        isValid = false,
        fileSizeBytes = apkFile.length(),
        sha256Full = sha256Full,
        errorReason = "PackageManager could not parse APK archive metadata.",
      )
    }

    val pkgName = archiveInfo.packageName.orEmpty()
    if (expectedPackageName.isNotBlank() && pkgName != expectedPackageName) {
      return ApkVerificationResult(
        isValid = false,
        packageName = pkgName,
        fileSizeBytes = apkFile.length(),
        sha256Full = sha256Full,
        appIdMatches = false,
        errorReason = "Package ID mismatch ($pkgName vs $expectedPackageName).",
      )
    }

    val apkVersionCode =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archiveInfo.longVersionCode
      else {
        @Suppress("DEPRECATION")
        archiveInfo.versionCode.toLong()
      }

    if (requireStrictlyHigherThanVersionCode > 0L && apkVersionCode <= requireStrictlyHigherThanVersionCode) {
      return ApkVerificationResult(
        isValid = false,
        packageName = pkgName,
        versionName = archiveInfo.versionName.orEmpty(),
        versionCode = apkVersionCode,
        fileSizeBytes = apkFile.length(),
        sha256Full = sha256Full,
        sameOrLowerVersionCode = true,
        errorReason =
          "APK versionCode ($apkVersionCode) must be higher than installed ($requireStrictlyHigherThanVersionCode).",
      )
    }

    if (requireMinimumVersionCode > 0L && apkVersionCode < requireMinimumVersionCode) {
      return ApkVerificationResult(
        isValid = false,
        packageName = pkgName,
        versionName = archiveInfo.versionName.orEmpty(),
        versionCode = apkVersionCode,
        fileSizeBytes = apkFile.length(),
        sha256Full = sha256Full,
        sameOrLowerVersionCode = true,
        errorReason = "APK versionCode ($apkVersionCode) is older than expected ($requireMinimumVersionCode).",
      )
    }

    val minSdk = archiveInfo.applicationInfo?.minSdkVersion ?: 24
    if (Build.VERSION.SDK_INT < minSdk) {
      return ApkVerificationResult(
        isValid = false,
        packageName = pkgName,
        versionName = archiveInfo.versionName.orEmpty(),
        versionCode = apkVersionCode,
        fileSizeBytes = apkFile.length(),
        sha256Full = sha256Full,
        errorReason = "APK requires Android API $minSdk+ (current ${Build.VERSION.SDK_INT}).",
      )
    }

    val archiveDigests = extractSignatureDigests(archiveInfo)
    val archivePrimaryDigest = archiveDigests.firstOrNull().orEmpty()
    if (expectedSigningCertSha256.isNotBlank() &&
      archiveDigests.isNotEmpty() &&
      archiveDigests.none { it.equals(expectedSigningCertSha256.trim(), ignoreCase = true) }
    ) {
      return ApkVerificationResult(
        isValid = false,
        packageName = pkgName,
        versionName = archiveInfo.versionName.orEmpty(),
        versionCode = apkVersionCode,
        fileSizeBytes = apkFile.length(),
        sha256Full = sha256Full,
        signatureMatchesInstalled = false,
        signingCertSha256 = archivePrimaryDigest,
        errorReason = "Signing certificate does not match manifest signingCertSha256.",
      )
    }

    val sigMatch = checkSigningMatchesInstalled(archiveInfo)
    if (sigMatch == false && requireSignatureMatch) {
      return ApkVerificationResult(
        isValid = false,
        packageName = pkgName,
        versionName = archiveInfo.versionName.orEmpty(),
        versionCode = apkVersionCode,
        fileSizeBytes = apkFile.length(),
        sha256Full = sha256Full,
        signatureMatchesInstalled = false,
        signingCertSha256 = archivePrimaryDigest,
        errorReason =
          "Signing certificate differs from installed app; installation blocked to protect existing data.",
      )
    }

    return ApkVerificationResult(
      isValid = true,
      packageName = pkgName,
      versionName = archiveInfo.versionName.orEmpty(),
      versionCode = apkVersionCode,
      fileSizeBytes = apkFile.length(),
      sha256Full = sha256Full,
      signatureMatchesInstalled = sigMatch,
      signingCertSha256 = archivePrimaryDigest,
      errorReason = if (sigMatch == false) "Warning: Archive signing certificate differs from installed app." else "",
    )
  }

  fun getInstalledSigningCertDigests(): Set<String> {
    return try {
      val pm = context.packageManager
      val installedInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pm.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
          )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          @Suppress("DEPRECATION")
          pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
          @Suppress("DEPRECATION")
          pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
      extractSignatureDigests(installedInfo)
    } catch (_: Throwable) {
      emptySet()
    }
  }

  private fun checkSigningMatchesInstalled(archiveInfo: PackageInfo): Boolean? {
    return try {
      val installedSigs = getInstalledSigningCertDigests()
      val archiveSigs = extractSignatureDigests(archiveInfo)
      if (installedSigs.isEmpty() || archiveSigs.isEmpty()) null
      else installedSigs.any { archiveSigs.contains(it) }
    } catch (_: Throwable) {
      null
    }
  }

  private fun extractSignatureDigests(info: PackageInfo): Set<String> {
    val sigBytesList =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val signingInfo = info.signingInfo
        val arr = signingInfo?.apkContentsSigners ?: signingInfo?.signingCertificateHistory
        arr?.map { it.toByteArray() } ?: emptyList()
      } else {
        @Suppress("DEPRECATION")
        info.signatures?.map { it.toByteArray() } ?: emptyList()
      }
    return sigBytesList.map { bytes ->
      MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }.toSet()
  }

  fun canRequestPackageInstalls(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
    } else {
      true
    }
  }

  fun openUnknownSourcesSettings() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val intent =
        Intent(
          Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
          "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      runCatching { context.startActivity(intent) }
    }
  }

  fun installApkViaSession(apkFile: File): Result<Unit> {
    if (!apkFile.exists() || apkFile.length() <= 1024L) {
      return Result.failure(IllegalStateException("APK file not found."))
    }
    return try {
      val installer = context.packageManager.packageInstaller
      val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
      params.setSize(apkFile.length())
      val sessionId = installer.createSession(params)
      val session = installer.openSession(sessionId)
      session.use { s ->
        s.openWrite("drx_update_apk", 0, apkFile.length()).use { out ->
          apkFile.inputStream().buffered().use { input ->
            input.copyTo(out)
          }
          s.fsync(out)
        }
        val callbackIntent = Intent(context, DrxPackageInstallReceiver::class.java)
        val pendingFlags =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
          } else {
            PendingIntent.FLAG_UPDATE_CURRENT
          }
        val statusPending = PendingIntent.getBroadcast(context, sessionId, callbackIntent, pendingFlags)
        s.commit(statusPending.intentSender)
      }
      Result.success(Unit)
    } catch (t: Throwable) {
      Result.failure(t)
    }
  }

  fun buildFileProviderInstallIntent(apkFile: File): Intent? {
    if (!apkFile.exists()) return null
    val uri =
      runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
      }.getOrNull() ?: return null
    return Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, "application/vnd.android.package-archive")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }
}
