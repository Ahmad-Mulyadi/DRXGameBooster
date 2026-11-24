package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.CrashReportEntity
import com.example.data.db.DrxDatabase
import com.example.data.db.DrxRepository
import com.example.data.db.GameSessionEntity
import com.example.data.db.HealthCheckEntity
import com.example.data.db.LagReportEntity
import com.example.data.db.SessionReportEntity
import com.example.data.db.StabilityWarningEntity
import com.example.data.model.BuildArtifactStatus
import com.example.data.model.BuildPipelineState
import com.example.data.model.CrashDiagnosticAnalysis
import com.example.data.model.GamingHealthReport
import com.example.data.model.LagDiagnosticReport
import com.example.data.model.LanguageMode
import com.example.data.model.MediaGalleryItem
import com.example.data.model.NetworkDiagnosticResult
import com.example.data.model.StabilityWatchAssessment
import com.example.data.model.StorageGallerySummary
import com.example.data.prefs.DrxPreferences
import com.example.data.prefs.DrxUserPrefs
import com.example.system.BuildDownloadManager
import com.example.system.CrashAnalyzerEngine
import com.example.system.DrxMediaStorageManager
import com.example.system.GamingHealthEngine
import com.example.system.LagDetectiveEngine
import com.example.system.NetworkDiagnosticsManager
import com.example.system.ScreenRecorderService
import com.example.system.ScreenRecorderStateHolder
import com.example.system.StabilityWatchEngine
import com.example.system.SystemTweaksManager
import com.example.system.TelemetryMonitor
import com.example.system.safeRoundToInt
import com.example.system.sanitizeFinite
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DrxDiagnosticsViewModel(application: Application) : AndroidViewModel(application) {

  private val appContext = application.applicationContext
  private val database = DrxDatabase.getInstance(appContext)
  val repository = DrxRepository(database)
  val prefs = DrxPreferences(appContext)
  val telemetryMonitor = TelemetryMonitor(appContext)
  val tweaksManager = SystemTweaksManager(appContext)

  private val lagEngine = LagDetectiveEngine()
  private val crashEngine = CrashAnalyzerEngine(appContext, tweaksManager)
  private val healthEngine = GamingHealthEngine()
  private val networkManager = NetworkDiagnosticsManager()
  val buildDownloadManager = BuildDownloadManager(appContext)

  val userPrefs: StateFlow<DrxUserPrefs> =
    prefs.prefsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, DrxUserPrefs())

  val lagReports: StateFlow<List<LagReportEntity>> =
    repository.lagReports.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val crashReports: StateFlow<List<CrashReportEntity>> =
    repository.crashReports.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val healthChecks: StateFlow<List<HealthCheckEntity>> =
    repository.healthChecks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val sessionReports: StateFlow<List<SessionReportEntity>> =
    repository.sessionReports.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val stabilityWarnings: StateFlow<List<StabilityWarningEntity>> =
    repository.stabilityWarnings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _stabilityAssessment = MutableStateFlow<StabilityWatchAssessment?>(null)
  val stabilityAssessment: StateFlow<StabilityWatchAssessment?> = _stabilityAssessment.asStateFlow()

  private val _isRunningStabilityWatch = MutableStateFlow(false)
  val isRunningStabilityWatch: StateFlow<Boolean> = _isRunningStabilityWatch.asStateFlow()

  // Lag Detective
  private val _currentLagReport = MutableStateFlow<LagDiagnosticReport?>(null)
  val currentLagReport: StateFlow<LagDiagnosticReport?> = _currentLagReport.asStateFlow()

  private val _isScanningLag = MutableStateFlow(false)
  val isScanningLag: StateFlow<Boolean> = _isScanningLag.asStateFlow()

  // Crash Analyzer
  private val _currentCrashAnalysis = MutableStateFlow<CrashDiagnosticAnalysis?>(null)
  val currentCrashAnalysis: StateFlow<CrashDiagnosticAnalysis?> = _currentCrashAnalysis.asStateFlow()

  private val _importedLogSummary = MutableStateFlow<String?>(null)
  val importedLogSummary: StateFlow<String?> = _importedLogSummary.asStateFlow()

  // Gaming Health Score
  private val _currentHealthReport = MutableStateFlow<GamingHealthReport?>(null)
  val currentHealthReport: StateFlow<GamingHealthReport?> = _currentHealthReport.asStateFlow()

  private val _isRunningHealthCheck = MutableStateFlow(false)
  val isRunningHealthCheck: StateFlow<Boolean> = _isRunningHealthCheck.asStateFlow()

  // Media Gallery & Storage
  private val _galleryItems = MutableStateFlow<List<MediaGalleryItem>>(emptyList())
  val galleryItems: StateFlow<List<MediaGalleryItem>> = _galleryItems.asStateFlow()

  private val _storageSummary = MutableStateFlow(StorageGallerySummary())
  val storageSummary: StateFlow<StorageGallerySummary> = _storageSummary.asStateFlow()

  // Build & Download Center
  private val _buildPipelineState = MutableStateFlow(BuildPipelineState.IDLE)
  val buildPipelineState: StateFlow<BuildPipelineState> = _buildPipelineState.asStateFlow()

  private val _buildStatusMessage = MutableStateFlow("")
  val buildStatusMessage: StateFlow<String> = _buildStatusMessage.asStateFlow()

  private val _apkArtifactStatus = MutableStateFlow<BuildArtifactStatus?>(null)
  val apkArtifactStatus: StateFlow<BuildArtifactStatus?> = _apkArtifactStatus.asStateFlow()

  private val _aabArtifactInfo =
    MutableStateFlow(buildDownloadManager.buildAabInfoPlaceholder(isIndo = true))
  val aabArtifactInfo: StateFlow<BuildArtifactStatus> = _aabArtifactInfo.asStateFlow()

  private val _actionBanner = MutableStateFlow<String?>(null)
  val actionBanner: StateFlow<String?> = _actionBanner.asStateFlow()

  fun clearActionBanner() {
    _actionBanner.value = null
  }

  fun runLagScan(includeNetworkProbe: Boolean = true) {
    if (_isScanningLag.value) return
    viewModelScope.launch(Dispatchers.IO) {
      _isScanningLag.value = true
      try {
        val currentPrefs = prefs.prefsFlow.first()
        val isIndo = currentPrefs.language == LanguageMode.INDONESIAN
        val snap = telemetryMonitor.readSnapshot()
        val netDiag: NetworkDiagnosticResult? =
          if (includeNetworkProbe && snap.networkSnapshot.isConnected) {
            runCatching {
              networkManager.runFullDiagnostics(
                host = "1.1.1.1",
                probeCount = 4,
                includeSpeedTest = false,
                networkType = snap.networkSnapshot.transportType,
              )
            }.getOrNull()
          } else {
            null
          }
        val mediaItems = DrxMediaStorageManager.loadGalleryItems(appContext)
        val recFootprint = mediaItems.sumOf { it.sizeBytes }
        val thresholdConfig =
          lagEngine.calibrateThresholdConfig(
            telemetry = snap,
            customRamWarningMb = currentPrefs.lagRamWarningMb,
            customThermalC = currentPrefs.lagThermalWarningC,
            customPingMs = currentPrefs.lagPingWarningMs,
            customStorageMb = currentPrefs.lagStorageWarningMb,
          )
        val report =
          lagEngine.analyze(
            telemetry = snap,
            networkDiag = netDiag,
            recordingsFootprintBytes = recFootprint,
            thresholdConfig = thresholdConfig,
            isIndo = isIndo,
          )
        _currentLagReport.value = report
      } catch (t: Throwable) {
        _actionBanner.value = t.message
      } finally {
        _isScanningLag.value = false
      }
    }
  }

  fun updateLagThresholds(
    ramWarningMb: Int,
    thermalWarningC: Float,
    pingWarningMs: Int,
    storageWarningMb: Int,
  ) {
    viewModelScope.launch {
      prefs.setLagThresholds(ramWarningMb, thermalWarningC, pingWarningMs, storageWarningMb)
      runLagScan(includeNetworkProbe = false)
    }
  }

  fun saveCurrentLagReport(gamePackageName: String = "") {
    val report = _currentLagReport.value ?: return
    val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
    viewModelScope.launch(Dispatchers.IO) {
      repository.insertLagReport(
        LagReportEntity(
          timestampMs = report.timestampMs,
          overallStatus = report.overallStatus.label,
          suspectedIssue = report.mainSuspectedIssue,
          severityLabel = report.severityLabel,
          confidencePercent = report.confidencePercent,
          evidenceSummary =
            report.findings.joinToString("\n") { "[${it.status.label}] ${it.title}: ${it.evidence}" },
          recommendation = report.recommendationSummary,
          gamePackageName = gamePackageName,
          ramFreeMb = report.ramFreeMb,
          batteryTempC = report.batteryTempC,
          thermalStatus = report.thermalStatusCode,
          storageFreeBytes = report.storageFreeBytes,
          networkLatencyMs = report.networkPingMs,
          networkType = report.networkType,
        )
      )
      _actionBanner.value =
        if (isIndo) "Laporan Lag Detective disimpan ke Riwayat Diagnostik."
        else "Lag Detective report saved to Diagnostic History."
    }
  }

  fun formatLagReportShareText(): String {
    val report = _currentLagReport.value ?: return ""
    val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
    return lagEngine.formatShareReport(report, isIndo)
  }

  fun deleteLagReport(id: Long) {
    viewModelScope.launch(Dispatchers.IO) { repository.deleteLagReport(id) }
  }

  // Crash Analyzer
  fun analyzeGameExit(
    packageName: String,
    appName: String,
    recentSession: GameSessionEntity?,
  ) {
    viewModelScope.launch(Dispatchers.IO) {
      val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
      val snap = telemetryMonitor.readSnapshot()
      val base =
        crashEngine.analyzeGameExit(
          packageName = packageName,
          appName = appName,
          telemetry = snap,
          recentSession = recentSession,
          isIndo = isIndo,
        )
      _currentCrashAnalysis.value = base.copy(importedLogSummary = _importedLogSummary.value)
    }
  }

  fun importDiagnosticLog(uri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
      val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
      val summary = crashEngine.parseImportedDiagnosticLog(uri, isIndo)
      _importedLogSummary.value = summary
      _currentCrashAnalysis.value = _currentCrashAnalysis.value?.copy(importedLogSummary = summary)
    }
  }

  fun saveCurrentCrashReport() {
    val analysis = _currentCrashAnalysis.value ?: return
    val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
    viewModelScope.launch(Dispatchers.IO) {
      repository.insertCrashReport(
        CrashReportEntity(
          gamePackageName = analysis.gamePackageName,
          gameName = analysis.gameName,
          gameVersion = analysis.gameVersion,
          exitClassification = analysis.exitType.labelEn,
          sessionStartMs = analysis.sessionStartMs,
          sessionEndMs = analysis.sessionEndMs,
          ramFreeMb = analysis.deviceRamFreeMb,
          ramTotalMb = analysis.deviceRamTotalMb,
          storageFreeBytes = analysis.availableStorageBytes,
          batteryLevelPercent = analysis.batteryLevelPercent,
          batteryTempC = analysis.batteryTempC,
          networkStatus = analysis.networkStatus,
          lastPerformanceSummary = analysis.lastPerformanceSummary,
          diagnosticResult = analysis.diagnosticResult,
          preventionTips = analysis.preventionTips.joinToString("\n"),
          importedLogSummary = analysis.importedLogSummary.orEmpty(),
        )
      )
      _actionBanner.value =
        if (isIndo) "Laporan Crash Analyzer disimpan ke Riwayat."
        else "Crash Analyzer report saved to History."
    }
  }

  fun deleteCrashReport(id: Long) {
    viewModelScope.launch(Dispatchers.IO) { repository.deleteCrashReport(id) }
  }

  // Gaming Health Score
  fun runHealthCheck(isFullDiagnostic: Boolean) {
    if (_isRunningHealthCheck.value) return
    viewModelScope.launch(Dispatchers.IO) {
      _isRunningHealthCheck.value = true
      try {
        val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
        val snap = telemetryMonitor.readSnapshot()
        val netDiag =
          if (isFullDiagnostic && snap.networkSnapshot.isConnected) {
            runCatching {
              networkManager.runFullDiagnostics(
                host = "1.1.1.1",
                probeCount = 4,
                includeSpeedTest = false,
                networkType = snap.networkSnapshot.transportType,
              )
            }.getOrNull()
          } else {
            null
          }
        val report =
          healthEngine.evaluateHealth(
            telemetry = snap,
            networkDiag = netDiag,
            isFullDiagnostic = isFullDiagnostic,
            isIndo = isIndo,
          )
        _currentHealthReport.value = report
      } finally {
        _isRunningHealthCheck.value = false
      }
    }
  }

  fun saveCurrentHealthCheck() {
    val report = _currentHealthReport.value ?: return
    val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
    viewModelScope.launch(Dispatchers.IO) {
      repository.insertHealthCheck(
        HealthCheckEntity(
          timestampMs = report.timestampMs,
          isFullDiagnostic = report.isFullDiagnostic,
          overallScoreOrNull = report.overallScoreOrNull,
          readinessTitle = report.readinessTitle,
          categorySummary =
            report.categories.joinToString("\n") {
              "${it.title}: ${it.scoreOrNull?.toString() ?: "N/A"} (${it.statusLabel}) — ${it.detailText}"
            },
          recommendations = report.recommendations.joinToString("\n"),
        )
      )
      _actionBanner.value =
        if (isIndo) "Skor Kesehatan Gaming disimpan ke Riwayat."
        else "Gaming Health Score saved to History."
    }
  }

  fun deleteHealthCheck(id: Long) {
    viewModelScope.launch(Dispatchers.IO) { repository.deleteHealthCheck(id) }
  }

  fun deleteSessionReport(id: Long) {
    viewModelScope.launch(Dispatchers.IO) { repository.deleteSessionReport(id) }
  }

  // Stability Watch
  fun runStabilityCheck(saveWarnings: Boolean = false) {
    if (_isRunningStabilityWatch.value) return
    viewModelScope.launch(Dispatchers.IO) {
      _isRunningStabilityWatch.value = true
      try {
        val currentPrefs = prefs.prefsFlow.first()
        val isIndo = currentPrefs.language == LanguageMode.INDONESIAN
        val snap = telemetryMonitor.readSnapshot()
        val crashes = repository.crashReports.first()
        val assessment =
          StabilityWatchEngine.evaluate(
            telemetry = snap,
            recentCrashes = crashes,
            isIndo = isIndo,
          )
        _stabilityAssessment.value = assessment
        if (saveWarnings && assessment.warnings.isNotEmpty()) {
          StabilityWatchEngine.toEntities(assessment).forEach { entity ->
            repository.insertStabilityWarning(entity)
          }
          _actionBanner.value =
            if (isIndo) "${assessment.warnings.size} peringatan stabilitas disimpan ke riwayat."
            else "${assessment.warnings.size} stability warning(s) saved to history."
        }
      } finally {
        _isRunningStabilityWatch.value = false
      }
    }
  }

  fun saveCurrentStabilityReport() {
    val assessment = _stabilityAssessment.value ?: return
    val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
    viewModelScope.launch(Dispatchers.IO) {
      val entities = StabilityWatchEngine.toEntities(assessment)
      if (entities.isNotEmpty()) {
        entities.forEach { repository.insertStabilityWarning(it) }
      } else {
        repository.insertStabilityWarning(
          StabilityWarningEntity(
            timestampMs = assessment.timestampMs,
            categoryId = "STATUS_OK",
            severity = assessment.overallSeverity.name,
            title = if (isIndo) "Stabilitas Perangkat Normal" else "Device Stability Normal",
            subtitle = assessment.headline,
            explanation = assessment.summaryText,
            recommendation =
              if (isIndo) "Semua parameter perangkat keras berada dalam rentang aman."
              else "All hardware telemetry checks passed within normal gaming operating range.",
            metricEvidence =
              if (isIndo)
                "RAM bebas ${assessment.ramAvailMb} MB • Suhu ${String.format(Locale.US, "%.1f", assessment.batteryTempC)}°C • Penyimpanan bebas ${com.example.system.TimeAndSizeFormatters.formatBytesAuto(assessment.storageFreeBytes)}"
              else
                "RAM free ${assessment.ramAvailMb} MB • Temp ${String.format(Locale.US, "%.1f", assessment.batteryTempC)}°C • Storage free ${com.example.system.TimeAndSizeFormatters.formatBytesAuto(assessment.storageFreeBytes)}",
            ramAvailMb = assessment.ramAvailMb,
            batteryTempC = assessment.batteryTempC,
            thermalStatusCode = assessment.thermalStatusCode,
            networkSummary = assessment.networkSummary,
          )
        )
      }
      _actionBanner.value =
        if (isIndo) "Laporan Stability Watch disimpan ke riwayat."
        else "Stability Watch report saved to history."
    }
  }

  fun deleteStabilityWarning(id: Long) {
    viewModelScope.launch(Dispatchers.IO) { repository.deleteStabilityWarning(id) }
  }

  fun clearStabilityWarnings() {
    viewModelScope.launch(Dispatchers.IO) { repository.clearStabilityWarnings() }
  }

  fun formatStabilityReportText(): String {
    val assessment = _stabilityAssessment.value ?: return ""
    val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
    return StabilityWatchEngine.formatExportReport(assessment, stabilityWarnings.value, isIndo)
  }

  fun toggleFavoriteMedia(uriString: String) {
    viewModelScope.launch { prefs.toggleFavoriteMediaUri(uriString) }
  }

  fun setRecorderCountdownSec(sec: Int) {
    viewModelScope.launch { prefs.setRecorderCountdownSec(sec) }
  }

  // Media Gallery & SAF
  fun refreshGallery() {
    viewModelScope.launch(Dispatchers.IO) {
      val items = DrxMediaStorageManager.loadGalleryItems(appContext)
      val summary = DrxMediaStorageManager.computeStorageSummary(appContext, items)
      _galleryItems.value = items
      _storageSummary.value = summary
      ScreenRecorderStateHolder.setRecentVideos(ScreenRecorderService.loadExistingRecordings(appContext))
    }
  }

  fun deleteMediaItems(items: List<MediaGalleryItem>) {
    viewModelScope.launch(Dispatchers.IO) {
      items.forEach { DrxMediaStorageManager.deleteMediaItem(appContext, it.uriString) }
      val next = DrxMediaStorageManager.loadGalleryItems(appContext)
      _galleryItems.value = next
      _storageSummary.value = DrxMediaStorageManager.computeStorageSummary(appContext, next)
      ScreenRecorderStateHolder.setRecentVideos(ScreenRecorderService.loadExistingRecordings(appContext))
    }
  }

  fun renameMediaItem(item: MediaGalleryItem, newBaseName: String) {
    viewModelScope.launch(Dispatchers.IO) {
      val ok = DrxMediaStorageManager.renameMediaItem(appContext, item, newBaseName)
      if (!ok) {
        val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
        _actionBanner.value =
          if (isIndo) "Gagal mengganti nama (dibatasi izin MediaStore untuk file eksternal)."
          else "Unable to rename item (MediaStore ownership restriction)."
      }
      refreshGallery()
    }
  }

  fun saveSafTreeUri(uri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        appContext.contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
      }
      prefs.setSafRootTreeUri(uri.toString())
      DrxMediaStorageManager.ensureSafSubfolders(appContext, uri.toString())
      val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
      _actionBanner.value =
        if (isIndo) "Folder DRXGameBooster (Screenshot & ScreenRecorder) tersambung."
        else "DRXGameBooster folder (Screenshot & ScreenRecorder) linked."
    }
  }

  // Download Center
  fun refreshExistingApkExport() {
    viewModelScope.launch {
      val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
      _aabArtifactInfo.value = buildDownloadManager.buildAabInfoPlaceholder(isIndo)
      val existing = buildDownloadManager.inspectCachedVerifiedExport()
      if (existing != null) {
        _apkArtifactStatus.value = existing
        _buildPipelineState.value = BuildPipelineState.READY_TO_DOWNLOAD
        _buildStatusMessage.value =
          if (isIndo) "APK terverifikasi siap disimpan atau dibagikan."
          else "Verified APK export ready to save or share."
      }
    }
  }

  fun prepareDownloadPackage() {
    if (_buildPipelineState.value == BuildPipelineState.CHECKING_SOURCE ||
      _buildPipelineState.value == BuildPipelineState.BUILDING_APK ||
      _buildPipelineState.value == BuildPipelineState.VALIDATING ||
      _buildPipelineState.value == BuildPipelineState.REPLACING_OLD_BUILD
    ) {
      return
    }
    viewModelScope.launch {
      val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
      _aabArtifactInfo.value = buildDownloadManager.buildAabInfoPlaceholder(isIndo)
      val res =
        buildDownloadManager.prepareVerifiedApkExport { state, msg ->
          _buildPipelineState.value = state
          _buildStatusMessage.value = msg
        }
      res.onSuccess { artifact ->
        _apkArtifactStatus.value = artifact
      }.onFailure { err ->
        _buildPipelineState.value = BuildPipelineState.BUILD_FAILED
        _buildStatusMessage.value = err.message ?: "APK preparation failed"
      }
    }
  }

  fun exportApkToSafUri(destUri: Uri): Boolean {
    val path = _apkArtifactStatus.value?.exportFilePath ?: return false
    val ok = buildDownloadManager.copyExportedApkToUri(destUri, File(path))
    val isIndo = userPrefs.value.language == LanguageMode.INDONESIAN
    _actionBanner.value =
      if (ok) {
        if (isIndo) "APK terverifikasi berhasil disimpan ke perangkat." else "Verified APK exported successfully."
      } else {
        if (isIndo) "Gagal menulis file APK ke lokasi tujuan." else "Failed to write APK file to destination."
      }
    return ok
  }

  fun buildShareApkIntent(): Intent? {
    val path = _apkArtifactStatus.value?.exportFilePath ?: return null
    return buildDownloadManager.buildShareApkIntent(File(path))
  }
}
