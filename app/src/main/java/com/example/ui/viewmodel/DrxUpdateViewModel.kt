package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.model.ApkVerificationResult
import com.example.data.model.DrxUpdatePipelineState
import com.example.data.model.UpdateManifest
import com.example.data.model.UpdateOfferPolicyState
import com.example.data.prefs.DrxPreferences
import com.example.data.prefs.DrxUserPrefs
import com.example.system.DrxUpdateManager
import com.example.system.ManifestEvaluationStatus
import com.example.system.PackageInstallStatusBus
import com.example.system.PackageInstallStatusEvent
import com.example.system.UpdateCheckWorker
import com.example.system.UpdatePolicyEngine
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DrxUpdateUiState(
  val pipelineState: DrxUpdatePipelineState = DrxUpdatePipelineState.IDLE,
  val selectedManifest: UpdateManifest? = null,
  val availableManifests: List<UpdateManifest> = emptyList(),
  val policyState: UpdateOfferPolicyState = UpdateOfferPolicyState.NONE,
  val downloadProgress: Float = 0f,
  val downloadedBytes: Long = 0L,
  val totalBytes: Long = 0L,
  val stagedApkFile: File? = null,
  val verificationResult: ApkVerificationResult? = null,
  val statusMessage: String = "",
  val errorMessage: String? = null,
  val manifestSourceLabel: String = "assets/update-manifest.json",
  val canInstallPackages: Boolean = true,
  val dialogDismissedThisSession: Boolean = false,
  val isUpdateAvailable: Boolean = false,
  val isSourceConfigured: Boolean = false,
  val isOffline: Boolean = false,
  val developerWarningMessage: String? = null,
  val lastCheckedAtMs: Long = 0L,
  val usingCachedManifest: Boolean = false,
)

class DrxUpdateViewModel(application: Application) : AndroidViewModel(application) {

  private val appContext = application.applicationContext
  private val prefs = DrxPreferences(appContext)
  val updateManager = DrxUpdateManager(appContext)

  companion object {
    private const val AUTO_CHECK_CACHE_TTL_MS = 15 * 60 * 1000L
  }

  val userPrefs: StateFlow<DrxUserPrefs> =
    prefs.prefsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, DrxUserPrefs())

  val installEvents: StateFlow<PackageInstallStatusEvent?> = PackageInstallStatusBus.latestEvent

  private val _uiState =
    MutableStateFlow(
      DrxUpdateUiState(canInstallPackages = updateManager.canRequestPackageInstalls())
    )
  val uiState: StateFlow<DrxUpdateUiState> = _uiState.asStateFlow()

  private var activeCheckJob: kotlinx.coroutines.Job? = null

  init {
    refreshPermissionAndCheckBundled()
  }

  fun refreshInstallPermissionState() {
    _uiState.value =
      _uiState.value.copy(canInstallPackages = updateManager.canRequestPackageInstalls())
  }

  fun refreshPermissionAndCheckBundled() {
    checkForUpdatesAuto(forceRefresh = true)
  }

  fun checkForUpdatesAuto(forceRefresh: Boolean = false) {
    if (!forceRefresh && activeCheckJob?.isActive == true) {
      refreshInstallPermissionState()
      return
    }
    if (!forceRefresh) {
      val nowSync = System.currentTimeMillis()
      val stateLastChecked = _uiState.value.lastCheckedAtMs
      if (_uiState.value.pipelineState != DrxUpdatePipelineState.IDLE &&
        stateLastChecked > 0L &&
        (nowSync - stateLastChecked) in 0 until AUTO_CHECK_CACHE_TTL_MS
      ) {
        refreshInstallPermissionState()
        return
      }
    }
    activeCheckJob?.cancel()
    activeCheckJob = viewModelScope.launch(Dispatchers.IO) {
      refreshInstallPermissionState()
      val currentPrefs = prefs.prefsFlow.first()
      val now = System.currentTimeMillis()
      val customUrl =
        currentPrefs.customUpdateManifestUrl.trim().ifBlank { BuildConfig.DEFAULT_UPDATE_MANIFEST_URL.trim() }

      val latestCheckMs = maxOf(currentPrefs.lastUpdateCheckAtMs, _uiState.value.lastCheckedAtMs)
      val cacheFresh =
        !forceRefresh &&
          _uiState.value.pipelineState != DrxUpdatePipelineState.IDLE &&
          latestCheckMs > 0L &&
          (now - latestCheckMs) in 0 until AUTO_CHECK_CACHE_TTL_MS
      if (cacheFresh) return@launch

      if (customUrl.isNotBlank()) {
        checkForUpdatesFromUrlInternal(url = customUrl, currentPrefs = currentPrefs)
      } else {
        checkBundledManifestInternal()
      }
    }
  }

  fun checkBundledManifest() {
    activeCheckJob?.cancel()
    activeCheckJob = viewModelScope.launch(Dispatchers.IO) {
      checkBundledManifestInternal()
    }
  }

  private suspend fun checkBundledManifestInternal() {
    val now = System.currentTimeMillis()
    _uiState.value =
      _uiState.value.copy(
        pipelineState = DrxUpdatePipelineState.CHECKING,
        errorMessage = null,
        isOffline = false,
        manifestSourceLabel = "assets/update-manifest.json",
      )
    val manifests = updateManager.loadBundledManifests()
    prefs.touchUpdateCheckTimestamp(now)
    applyManifestList(
      manifests = manifests,
      sourceLabel = "assets/update-manifest.json",
      hasRemoteSourceConfigured = false,
      isOnline = updateManager.isNetworkAvailable(),
      usingCached = false,
      checkedAtMs = now,
    )
  }

  fun checkForUpdatesFromUrl(url: String) {
    activeCheckJob?.cancel()
    activeCheckJob = viewModelScope.launch(Dispatchers.IO) {
      val trimmed = url.trim()
      if (trimmed.isBlank()) {
        prefs.setCustomUpdateManifestUrl("")
        checkBundledManifestInternal()
        return@launch
      }
      val currentPrefs = prefs.prefsFlow.first()
      checkForUpdatesFromUrlInternal(url = trimmed, currentPrefs = currentPrefs)
    }
  }

  private suspend fun checkForUpdatesFromUrlInternal(url: String, currentPrefs: DrxUserPrefs) {
    val now = System.currentTimeMillis()
    _uiState.value =
      _uiState.value.copy(
        pipelineState = DrxUpdatePipelineState.CHECKING,
        errorMessage = null,
        manifestSourceLabel = url,
      )

    val online = updateManager.isNetworkAvailable()
    if (!online) {
      val cachedRaw = currentPrefs.cachedUpdateManifestJson
      val cachedList =
        if (cachedRaw.isNotBlank()) {
          runCatching {
            UpdatePolicyEngine.resolveDownloadUrlsAgainstManifest(
              UpdatePolicyEngine.parseManifestJson(cachedRaw),
              url,
            )
          }.getOrNull().orEmpty()
        } else emptyList()
      applyManifestList(
        manifests = cachedList,
        sourceLabel = url,
        hasRemoteSourceConfigured = true,
        isOnline = false,
        usingCached = cachedList.isNotEmpty(),
        checkedAtMs = currentPrefs.lastUpdateCheckAtMs.takeIf { it > 0L } ?: now,
      )
      return
    }

    val res = updateManager.fetchRemoteManifestRaw(url)
    res.fold(
      onSuccess = { rawBody ->
        prefs.setCustomUpdateManifestUrl(url)
        prefs.saveCachedUpdateManifest(rawBody, now)
        val manifests =
          UpdatePolicyEngine.resolveDownloadUrlsAgainstManifest(
            UpdatePolicyEngine.parseManifestJson(rawBody),
            url,
          )
        applyManifestList(
          manifests = manifests,
          sourceLabel = url,
          hasRemoteSourceConfigured = true,
          isOnline = true,
          usingCached = false,
          checkedAtMs = now,
        )
      },
      onFailure = { err ->
        val cachedRaw = currentPrefs.cachedUpdateManifestJson
        val cachedList =
          if (cachedRaw.isNotBlank()) {
            runCatching {
              UpdatePolicyEngine.resolveDownloadUrlsAgainstManifest(
                UpdatePolicyEngine.parseManifestJson(cachedRaw),
                url,
              )
            }.getOrNull().orEmpty()
          } else emptyList()
        if (cachedList.isNotEmpty()) {
          applyManifestList(
            manifests = cachedList,
            sourceLabel = url,
            hasRemoteSourceConfigured = true,
            isOnline = false,
            usingCached = true,
            checkedAtMs = currentPrefs.lastUpdateCheckAtMs.takeIf { it > 0L } ?: now,
            overrideError = err.message ?: "Remote manifest unreachable; showing cached metadata.",
          )
        } else {
          _uiState.value =
            _uiState.value.copy(
              pipelineState = DrxUpdatePipelineState.FAILED,
              isUpdateAvailable = false,
              isSourceConfigured = true,
              isOffline = !updateManager.isNetworkAvailable(),
              lastCheckedAtMs = now,
              errorMessage = err.message ?: "Failed to fetch update manifest.",
              statusMessage = err.message ?: "Failed to check for updates.",
            )
        }
      },
    )
  }

  fun testManifestJson(rawJson: String, sourceLabel: String = "Custom JSON Manifest") {
    activeCheckJob?.cancel()
    activeCheckJob = viewModelScope.launch(Dispatchers.IO) {
      val now = System.currentTimeMillis()
      _uiState.value =
        _uiState.value.copy(
          pipelineState = DrxUpdatePipelineState.CHECKING,
          errorMessage = null,
          manifestSourceLabel = sourceLabel,
        )
      val parsed = runCatching { UpdatePolicyEngine.parseManifestJson(rawJson) }.getOrNull().orEmpty()
      if (parsed.isEmpty()) {
        _uiState.value =
          _uiState.value.copy(
            pipelineState = DrxUpdatePipelineState.FAILED,
            isUpdateAvailable = false,
            errorMessage = "Manifest JSON format invalid or empty.",
          )
      } else {
        prefs.saveCachedUpdateManifest(rawJson, now)
        prefs.touchUpdateCheckTimestamp(now)
        applyManifestList(
          manifests = parsed,
          sourceLabel = sourceLabel,
          hasRemoteSourceConfigured = true,
          isOnline = true,
          usingCached = false,
          checkedAtMs = now,
        )
      }
    }
  }

  private suspend fun applyManifestList(
    manifests: List<UpdateManifest>,
    sourceLabel: String,
    hasRemoteSourceConfigured: Boolean,
    isOnline: Boolean,
    usingCached: Boolean,
    checkedAtMs: Long,
    overrideError: String? = null,
  ) {
    val best = UpdatePolicyEngine.selectBestManifest(manifests)
    val currentVc = BuildConfig.VERSION_CODE
    val existingFile = best?.let { updateManager.findExistingLocalUpdateApk(it) }
    val existingVerif =
      if (best != null && existingFile != null) {
        updateManager.verifyApkFile(
          apkFile = existingFile,
          expectedPackageName = appContext.packageName,
          expectedSha256 = best.sha256,
          expectedSigningCertSha256 = best.signingCertSha256,
          requireMinimumVersionCode = 0L,
          requireStrictlyHigherThanVersionCode = 0L,
          requireSignatureMatch = true,
        )
      } else {
        null
      }

    val eval =
      UpdatePolicyEngine.evaluateManifestAgainstInstalled(
        manifest = best,
        installedPackageName = appContext.packageName,
        installedVersionCode = currentVc,
        installedSigningCertSha256s = updateManager.getInstalledSigningCertDigests(),
        hasLocalStagedFile = (existingFile != null && existingVerif?.isValid == true),
        hasRemoteSourceConfigured = hasRemoteSourceConfigured,
        isOnline = isOnline,
      )

    val isSourceConfigured =
      hasRemoteSourceConfigured || (best?.downloadUrl?.isNotBlank() == true) || (existingFile != null && existingVerif?.isValid == true)

    val targetForSkip = if (eval.isInstallableUpdate && best != null) best.versionCode else 0
    prefs.syncOfferedUpdateVersion(targetForSkip, currentVc)
    val latestPrefs = prefs.prefsFlow.first()

    val policy =
      if (eval.isInstallableUpdate && best != null) {
        UpdatePolicyEngine.evaluateSkipPolicy(
          currentVersionCode = currentVc,
          targetVersionCode = best.versionCode,
          lastOfferedVersionCode = latestPrefs.lastOfferedUpdateVersionCode,
          skipCount = latestPrefs.updateSkipCount,
        )
      } else {
        UpdateOfferPolicyState.NONE
      }

    val nextState =
      when (eval.status) {
        ManifestEvaluationStatus.UP_TO_DATE -> DrxUpdatePipelineState.UP_TO_DATE
        ManifestEvaluationStatus.SOURCE_NOT_CONFIGURED -> DrxUpdatePipelineState.SOURCE_NOT_CONFIGURED
        ManifestEvaluationStatus.OFFLINE -> DrxUpdatePipelineState.OFFLINE
        ManifestEvaluationStatus.REJECTED_SAME_OR_LOWER_VERSION,
        ManifestEvaluationStatus.REJECTED_APP_ID_MISMATCH,
        ManifestEvaluationStatus.REJECTED_SIGNATURE_MISMATCH,
        ManifestEvaluationStatus.REJECTED_DEVELOPER_WARNING,
        ManifestEvaluationStatus.REJECTED_MIN_SDK -> DrxUpdatePipelineState.REJECTED_WARNING
        ManifestEvaluationStatus.UPDATE_AVAILABLE -> {
          if (existingFile != null && existingVerif?.isValid == true) {
            DrxUpdatePipelineState.READY_TO_INSTALL
          } else {
            DrxUpdatePipelineState.AVAILABLE
          }
        }
      }

    val warningMsg =
      when (nextState) {
        DrxUpdatePipelineState.REJECTED_WARNING -> eval.reason
        else -> null
      }

    val installable =
      nextState == DrxUpdatePipelineState.AVAILABLE || nextState == DrxUpdatePipelineState.READY_TO_INSTALL

    if (installable &&
      best != null &&
      best.versionCode > currentVc &&
      best.versionCode != latestPrefs.lastNotifiedUpdateVersionCode
    ) {
      val posted =
        UpdateCheckWorker.postUpdateNotificationIfAllowed(
          context = appContext,
          versionName = best.versionName,
          versionCode = best.versionCode,
          releaseNotes = best.releaseNotes,
        )
      if (posted) {
        prefs.markUpdateVersionNotified(best.versionCode)
      }
    }

    _uiState.value =
      _uiState.value.copy(
        pipelineState = nextState,
        selectedManifest = best,
        availableManifests = manifests,
        policyState = policy,
        stagedApkFile = if (existingVerif?.isValid == true) existingFile else null,
        verificationResult = existingVerif,
        manifestSourceLabel = sourceLabel,
        errorMessage = overrideError ?: warningMsg,
        developerWarningMessage = warningMsg,
        isUpdateAvailable = installable,
        isSourceConfigured = isSourceConfigured,
        isOffline = nextState == DrxUpdatePipelineState.OFFLINE || !isOnline,
        lastCheckedAtMs = checkedAtMs,
        usingCachedManifest = usingCached,
        statusMessage =
          when (nextState) {
            DrxUpdatePipelineState.READY_TO_INSTALL ->
              "Verified ${existingFile?.name} ready for installation."
            else -> eval.reason
          },
      )
  }

  fun startDownloadAndVerify() {
    val manifest = _uiState.value.selectedManifest ?: return
    val eval =
      UpdatePolicyEngine.evaluateManifestAgainstInstalled(
        manifest = manifest,
        installedPackageName = appContext.packageName,
        installedVersionCode = BuildConfig.VERSION_CODE,
        installedSigningCertSha256s = updateManager.getInstalledSigningCertDigests(),
        hasLocalStagedFile = false,
        hasRemoteSourceConfigured = manifest.downloadUrl.isNotBlank(),
        isOnline = updateManager.isNetworkAvailable(),
      )
    if (!eval.isInstallableUpdate && manifest.downloadUrl.isBlank()) {
      viewModelScope.launch(Dispatchers.IO) {
        val local = updateManager.findExistingLocalUpdateApk(manifest)
        if (local == null) {
          _uiState.value =
            _uiState.value.copy(
              pipelineState =
                when (eval.status) {
                  ManifestEvaluationStatus.OFFLINE -> DrxUpdatePipelineState.OFFLINE
                  ManifestEvaluationStatus.SOURCE_NOT_CONFIGURED -> DrxUpdatePipelineState.SOURCE_NOT_CONFIGURED
                  else -> DrxUpdatePipelineState.REJECTED_WARNING
                },
              isUpdateAvailable = false,
              errorMessage = eval.reason,
              statusMessage = eval.reason,
            )
          return@launch
        }
        runDownloadAndVerifyInternal(manifest)
      }
      return
    }
    viewModelScope.launch(Dispatchers.IO) {
      runDownloadAndVerifyInternal(manifest)
    }
  }

  private suspend fun runDownloadAndVerifyInternal(manifest: UpdateManifest) {
    if (manifest.downloadUrl.isNotBlank() && !updateManager.isNetworkAvailable()) {
      _uiState.value =
        _uiState.value.copy(
          pipelineState = DrxUpdatePipelineState.OFFLINE,
          isOffline = true,
          errorMessage = "Device is offline. Connect to internet and tap Check Again.",
          statusMessage = "Offline: unable to download APK.",
        )
      return
    }

    _uiState.value =
      _uiState.value.copy(
        pipelineState = DrxUpdatePipelineState.DOWNLOADING,
        downloadProgress = 0f,
        downloadedBytes = 0L,
        totalBytes = manifest.fileSizeBytes,
        errorMessage = null,
        statusMessage = "Downloading ${manifest.localFileName.ifBlank { "APK" }}...",
      )

    val dlResult =
      updateManager.downloadOrStageApk(manifest) { progress, downloaded, total ->
        _uiState.value =
          _uiState.value.copy(
            downloadProgress = progress,
            downloadedBytes = downloaded,
            totalBytes = total,
          )
      }

    dlResult.fold(
      onSuccess = { file ->
        _uiState.value =
          _uiState.value.copy(
            pipelineState = DrxUpdatePipelineState.VERIFYING,
            statusMessage = "Verifying ZIP, AndroidManifest, SHA-256 & package signature...",
          )
        val verif =
          updateManager.verifyApkFile(
            apkFile = file,
            expectedPackageName = appContext.packageName,
            expectedSha256 = manifest.sha256,
            expectedSigningCertSha256 = manifest.signingCertSha256,
            requireMinimumVersionCode = manifest.versionCode.toLong(),
            requireStrictlyHigherThanVersionCode = BuildConfig.VERSION_CODE.toLong(),
            requireSignatureMatch = true,
          )
        if (verif.isValid) {
          _uiState.value =
            _uiState.value.copy(
              pipelineState = DrxUpdatePipelineState.READY_TO_INSTALL,
              stagedApkFile = file,
              verificationResult = verif,
              errorMessage = null,
              developerWarningMessage = null,
              isUpdateAvailable = true,
              statusMessage = "Verified ${file.name} (${verif.sha256Full.take(16)}).",
            )
        } else {
          runCatching { file.delete() }
          // Failed verification never counts as user skip!
          val isPolicyReject = verif.sameOrLowerVersionCode || verif.signatureMatchesInstalled == false || !verif.appIdMatches
          _uiState.value =
            _uiState.value.copy(
              pipelineState =
                if (isPolicyReject) DrxUpdatePipelineState.REJECTED_WARNING
                else DrxUpdatePipelineState.FAILED,
              stagedApkFile = null,
              verificationResult = verif,
              isUpdateAvailable = !isPolicyReject,
              developerWarningMessage = if (isPolicyReject) verif.errorReason else null,
              errorMessage = verif.errorReason,
              statusMessage = verif.errorReason,
            )
        }
      },
      onFailure = { err ->
        // Download failure NEVER counts as user skip
        _uiState.value =
          _uiState.value.copy(
            pipelineState = DrxUpdatePipelineState.FAILED,
            errorMessage = err.message ?: "Download failed.",
            statusMessage = err.message ?: "Download failed.",
          )
      },
    )
  }

  fun importAndVerifyLocalApk(uri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
      _uiState.value =
        _uiState.value.copy(
          pipelineState = DrxUpdatePipelineState.VERIFYING,
          errorMessage = null,
          developerWarningMessage = null,
          statusMessage = "Inspecting selected APK archive...",
        )
      val imported = updateManager.importApkFromUri(uri)
      imported.fold(
        onSuccess = { file ->
          val verif =
            updateManager.verifyApkFile(
              apkFile = file,
              expectedPackageName = appContext.packageName,
              expectedSha256 = "",
              expectedSigningCertSha256 = "",
              requireMinimumVersionCode = 0L,
              requireStrictlyHigherThanVersionCode = BuildConfig.VERSION_CODE.toLong(),
              requireSignatureMatch = true,
            )
          if (verif.isValid) {
            val manifest =
              UpdateManifest(
                versionName = verif.versionName.ifBlank { BuildConfig.VERSION_NAME },
                versionCode = verif.versionCode.toInt(),
                releaseDate = BuildConfig.BUILD_DATE,
                releaseNotes = "Local APK selected & verified on-device.",
                apkType = if (file.name.contains("DEBUG", ignoreCase = true)) "debug" else "release",
                localFileName = file.name,
                fileSizeBytes = verif.fileSizeBytes,
                sha256 = verif.sha256Full,
                appId = verif.packageName.ifBlank { BuildConfig.APPLICATION_ID },
                signingCertSha256 = verif.signingCertSha256,
              )
            _uiState.value =
              _uiState.value.copy(
                pipelineState = DrxUpdatePipelineState.READY_TO_INSTALL,
                selectedManifest = manifest,
                stagedApkFile = file,
                verificationResult = verif,
                manifestSourceLabel = "Local File (${file.name})",
                isUpdateAvailable = true,
                isSourceConfigured = true,
                errorMessage = null,
                developerWarningMessage = null,
                statusMessage = "Verified v${manifest.versionName} (#${manifest.versionCode}) ready to install.",
              )
          } else {
            runCatching { file.delete() }
            val isPolicyReject = verif.sameOrLowerVersionCode || verif.signatureMatchesInstalled == false || !verif.appIdMatches
            _uiState.value =
              _uiState.value.copy(
                pipelineState =
                  if (isPolicyReject) DrxUpdatePipelineState.REJECTED_WARNING
                  else DrxUpdatePipelineState.FAILED,
                verificationResult = verif,
                isUpdateAvailable = false,
                developerWarningMessage = if (isPolicyReject) verif.errorReason else null,
                errorMessage = verif.errorReason,
                statusMessage = verif.errorReason,
              )
          }
        },
        onFailure = { err ->
          _uiState.value =
            _uiState.value.copy(
              pipelineState = DrxUpdatePipelineState.FAILED,
              errorMessage = err.message ?: "Failed to inspect APK file.",
              statusMessage = err.message ?: "Failed to inspect APK file.",
            )
        },
      )
    }
  }

  fun installVerifiedApk(): Boolean {
    val file = _uiState.value.stagedApkFile ?: return false
    val currentVerif = _uiState.value.verificationResult
    if (currentVerif == null || !currentVerif.isValid || currentVerif.signatureMatchesInstalled == false) {
      _uiState.value =
        _uiState.value.copy(
          pipelineState = DrxUpdatePipelineState.REJECTED_WARNING,
          errorMessage = "APK verification failed or signing certificate mismatch; installation blocked.",
        )
      return false
    }

    if (!updateManager.canRequestPackageInstalls()) {
      _uiState.value =
        _uiState.value.copy(
          canInstallPackages = false,
          errorMessage = "Allow 'Install unknown apps' permission in Android Settings first.",
        )
      updateManager.openUnknownSourcesSettings()
      return false
    }

    _uiState.value =
      _uiState.value.copy(
        pipelineState = DrxUpdatePipelineState.INSTALLING,
        errorMessage = null,
        statusMessage = "Launching Android PackageInstaller...",
      )
    val sessionResult = updateManager.installApkViaSession(file)
    if (sessionResult.isSuccess) return true

    val fallbackIntent: Intent? = updateManager.buildFileProviderInstallIntent(file)
    if (fallbackIntent != null) {
      return try {
        appContext.startActivity(fallbackIntent)
        true
      } catch (t: Throwable) {
        _uiState.value =
          _uiState.value.copy(
            pipelineState = DrxUpdatePipelineState.FAILED,
            errorMessage = t.message ?: "Unable to launch package installer.",
          )
        false
      }
    }
    return false
  }

  fun skipCurrentUpdateOffer() {
    val target = _uiState.value.selectedManifest ?: return
    if (_uiState.value.policyState == UpdateOfferPolicyState.MANDATORY_REQUIRED) {
      return
    }
    viewModelScope.launch {
      prefs.recordUpdateSkip(target.versionCode)
      val updated = prefs.prefsFlow.first()
      val nextPolicy =
        UpdatePolicyEngine.evaluateSkipPolicy(
          currentVersionCode = BuildConfig.VERSION_CODE,
          targetVersionCode = target.versionCode,
          lastOfferedVersionCode = updated.lastOfferedUpdateVersionCode,
          skipCount = updated.updateSkipCount,
        )
      _uiState.value =
        _uiState.value.copy(
          policyState = nextPolicy,
          dialogDismissedThisSession = true,
        )
    }
  }

  fun resetUpdateSkipCounter() {
    viewModelScope.launch {
      prefs.resetUpdateSkipPolicy()
      checkForUpdatesAuto(forceRefresh = true)
    }
  }

  fun dismissOptionalDialogForSession() {
    if (_uiState.value.policyState != UpdateOfferPolicyState.MANDATORY_REQUIRED) {
      _uiState.value = _uiState.value.copy(dialogDismissedThisSession = true)
    }
  }

  fun clearInstallEvent() {
    PackageInstallStatusBus.clear()
  }
}
