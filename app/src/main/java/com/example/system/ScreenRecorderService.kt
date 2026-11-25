package com.example.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.example.MainActivity
import com.example.R
import com.example.data.model.BitrateTier
import com.example.data.model.MediaGalleryItem
import com.example.data.model.MediaItemType
import com.example.data.model.ScreenshotFormat
import com.example.data.prefs.DrxPreferences
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ScreenRecordAudioMode {
  NONE,
  SYSTEM_AUDIO,
  MIC_AUDIO,
  INTERNAL_AND_MIC,
}

data class RecordedVideoInfo(
  val uriString: String,
  val displayName: String,
  val durationSec: Int,
  val sizeBytes: Long,
  val resolutionLabel: String,
  val timestampMs: Long,
)

data class RecordingEncoderCapability(
  val maxSupportedFpsAt1080p: Int,
  val supports60Fps: Boolean,
  val note: String,
)

data class ScreenRecorderUiState(
  val isRecording: Boolean = false,
  val isPaused: Boolean = false,
  val elapsedSeconds: Int = 0,
  val liveFileSizeBytes: Long = 0L,
  val resolutionPreset: Int = 720, // 480, 720, 1080, 0 = Original
  val targetFps: Int = 30, // 24, 30, 60
  val bitrateTier: BitrateTier = BitrateTier.MEDIUM,
  val customBitrateMbps: Int = 8,
  val audioMode: ScreenRecordAudioMode =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ScreenRecordAudioMode.SYSTEM_AUDIO
    else ScreenRecordAudioMode.NONE,
  val statusMessage: String? = null,
  val errorMessage: String? = null,
  val lastSavedVideo: RecordedVideoInfo? = null,
  val lastCapturedScreenshot: MediaGalleryItem? = null,
  val recentVideos: List<RecordedVideoInfo> = emptyList(),
  val micAmplitudeLevel: Float = 0f,
  val countdownRemainingSec: Int = 0,
)

object ScreenRecorderStateHolder {
  private val _state = MutableStateFlow(ScreenRecorderUiState())
  val state: StateFlow<ScreenRecorderUiState> = _state.asStateFlow()

  fun updateConfig(
    resolutionPreset: Int? = null,
    targetFps: Int? = null,
    audioMode: ScreenRecordAudioMode? = null,
    bitrateTier: BitrateTier? = null,
    customBitrateMbps: Int? = null,
  ) {
    _state.update { cur ->
      cur.copy(
        resolutionPreset = resolutionPreset ?: cur.resolutionPreset,
        targetFps = targetFps ?: cur.targetFps,
        audioMode = audioMode ?: cur.audioMode,
        bitrateTier = bitrateTier ?: cur.bitrateTier,
        customBitrateMbps = customBitrateMbps?.coerceIn(2, 24) ?: cur.customBitrateMbps,
      )
    }
  }

  internal fun setRecordingStarted(status: String) {
    _state.update {
      it.copy(
        isRecording = true,
        isPaused = false,
        elapsedSeconds = 0,
        liveFileSizeBytes = 0L,
        micAmplitudeLevel = 0f,
        countdownRemainingSec = 0,
        statusMessage = status,
        errorMessage = null,
      )
    }
  }

  internal fun setPaused(paused: Boolean, status: String?) {
    _state.update {
      it.copy(
        isPaused = paused,
        micAmplitudeLevel = if (paused) 0f else it.micAmplitudeLevel,
        statusMessage = status,
      )
    }
  }

  internal fun updateElapsedAndSize(seconds: Int, bytes: Long) {
    _state.update { it.copy(elapsedSeconds = seconds, liveFileSizeBytes = bytes) }
  }

  internal fun updateMicAmplitude(level: Float) {
    _state.update { it.copy(micAmplitudeLevel = level.coerceIn(0f, 1f)) }
  }

  internal fun setCountdownRemaining(seconds: Int) {
    _state.update { it.copy(countdownRemainingSec = seconds.coerceAtLeast(0)) }
  }

  internal fun setRecordingFinished(
    saved: RecordedVideoInfo?,
    recent: List<RecordedVideoInfo>,
    status: String?,
    error: String?,
  ) {
    _state.update {
      it.copy(
        isRecording = false,
        isPaused = false,
        elapsedSeconds = 0,
        liveFileSizeBytes = 0L,
        micAmplitudeLevel = 0f,
        countdownRemainingSec = 0,
        lastSavedVideo = saved ?: it.lastSavedVideo,
        recentVideos = recent,
        statusMessage = status,
        errorMessage = error,
      )
    }
  }

  internal fun setScreenshotCaptured(item: MediaGalleryItem?, status: String?, error: String?) {
    _state.update {
      it.copy(
        lastCapturedScreenshot = item ?: it.lastCapturedScreenshot,
        statusMessage = status,
        errorMessage = error,
      )
    }
  }

  fun setRecentVideos(videos: List<RecordedVideoInfo>) {
    _state.update { it.copy(recentVideos = videos) }
  }

  fun clearMessages() {
    _state.update { it.copy(statusMessage = null, errorMessage = null) }
  }
}

class ScreenRecorderService : Service() {

  companion object {
    const val ACTION_START = "com.example.action.SCREEN_REC_START"
    const val ACTION_STOP = "com.example.action.SCREEN_REC_STOP"
    const val ACTION_PAUSE = "com.example.action.SCREEN_REC_PAUSE"
    const val ACTION_RESUME = "com.example.action.SCREEN_REC_RESUME"
    const val ACTION_CAPTURE_SCREENSHOT = "com.example.action.SCREENSHOT_CAPTURE"
    const val ACTION_CAPTURE_SCREENSHOT_DURING_REC = "com.example.action.SCREENSHOT_CAPTURE_DURING_REC"

    const val EXTRA_RESULT_CODE = "extra_result_code"
    const val EXTRA_RESULT_DATA = "extra_result_data"
    const val EXTRA_RESOLUTION_PRESET = "extra_resolution_preset"
    const val EXTRA_TARGET_FPS = "extra_target_fps"
    const val EXTRA_AUDIO_MODE = "extra_audio_mode"
    const val EXTRA_BITRATE_BPS = "extra_bitrate_bps"
    const val EXTRA_COUNTDOWN_SEC = "extra_countdown_sec"
    const val EXTRA_SCREENSHOT_FORMAT = "extra_screenshot_format"

    private const val CHANNEL_ID = "drx_screen_recorder_channel"
    private const val NOTIFICATION_ID = 7019

    fun querySupportedRecordingCapabilities(): RecordingEncoderCapability {
      return try {
        val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        var maxFps = 30
        for (info in list.codecInfos) {
          if (!info.isEncoder) continue
          if (info.supportedTypes.none { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) }) continue
          val caps = runCatching { info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC) }.getOrNull() ?: continue
          val vCaps = caps.videoCapabilities ?: continue
          val supported60 = runCatching { vCaps.areSizeAndRateSupported(1080, 1920, 60.0) || vCaps.areSizeAndRateSupported(1920, 1080, 60.0) }.getOrDefault(false)
          if (supported60) {
            maxFps = 60
            break
          }
          val fpsRange = vCaps.supportedFrameRates
          if (fpsRange.upper >= 60) maxFps = maxOf(maxFps, 60)
        }
        RecordingEncoderCapability(
          maxSupportedFpsAt1080p = maxFps,
          supports60Fps = maxFps >= 60,
          note = if (maxFps >= 60) "Hardware AVC supports up to 60 FPS." else "Device AVC caps 1080p below 60 FPS; 30 FPS recommended.",
        )
      } catch (_: Throwable) {
        RecordingEncoderCapability(30, false, "Standard 30 FPS H.264 profile")
      }
    }

    fun resolveEffectiveBitrateBps(state: ScreenRecorderUiState): Int =
      when (state.bitrateTier) {
        BitrateTier.CUSTOM -> state.customBitrateMbps.coerceIn(2, 24) * 1_000_000
        else -> state.bitrateTier.bps
      }

    fun startRecording(
      context: Context,
      resultCode: Int,
      data: Intent,
      resolutionPreset: Int = ScreenRecorderStateHolder.state.value.resolutionPreset,
      targetFps: Int = ScreenRecorderStateHolder.state.value.targetFps,
      audioMode: ScreenRecordAudioMode = ScreenRecorderStateHolder.state.value.audioMode,
      bitrateBps: Int = resolveEffectiveBitrateBps(ScreenRecorderStateHolder.state.value),
      countdownSec: Int = 0,
    ) {
      val intent =
        Intent(context, ScreenRecorderService::class.java).apply {
          action = ACTION_START
          putExtra(EXTRA_RESULT_CODE, resultCode)
          putExtra(EXTRA_RESULT_DATA, data)
          putExtra(EXTRA_RESOLUTION_PRESET, resolutionPreset)
          putExtra(EXTRA_TARGET_FPS, targetFps)
          putExtra(EXTRA_AUDIO_MODE, audioMode.name)
          putExtra(EXTRA_BITRATE_BPS, bitrateBps)
          putExtra(EXTRA_COUNTDOWN_SEC, countdownSec.coerceIn(0, 10))
        }
      ContextCompat.startForegroundService(context, intent)
    }

    fun captureScreenshot(
      context: Context,
      resultCode: Int,
      data: Intent,
      format: ScreenshotFormat = ScreenshotFormat.PNG,
    ) {
      val intent =
        Intent(context, ScreenRecorderService::class.java).apply {
          action = ACTION_CAPTURE_SCREENSHOT
          putExtra(EXTRA_RESULT_CODE, resultCode)
          putExtra(EXTRA_RESULT_DATA, data)
          putExtra(EXTRA_SCREENSHOT_FORMAT, format.name)
        }
      ContextCompat.startForegroundService(context, intent)
    }

    fun captureScreenshotDuringRecording(
      context: Context,
      format: ScreenshotFormat = ScreenshotFormat.PNG,
    ) {
      val intent =
        Intent(context, ScreenRecorderService::class.java).apply {
          action = ACTION_CAPTURE_SCREENSHOT_DURING_REC
          putExtra(EXTRA_SCREENSHOT_FORMAT, format.name)
        }
      runCatching { context.startService(intent) }
    }

    fun pauseRecording(context: Context) {
      runCatching { context.startService(Intent(context, ScreenRecorderService::class.java).setAction(ACTION_PAUSE)) }
    }

    fun resumeRecording(context: Context) {
      runCatching { context.startService(Intent(context, ScreenRecorderService::class.java).setAction(ACTION_RESUME)) }
    }

    fun stopRecording(context: Context) {
      val intent = Intent(context, ScreenRecorderService::class.java).setAction(ACTION_STOP)
      try {
        context.startService(intent)
      } catch (_: Throwable) {
        ScreenRecorderStateHolder.setRecordingFinished(
          saved = null,
          recent = loadExistingRecordings(context),
          status = null,
          error = null,
        )
      }
    }

    fun loadExistingRecordings(context: Context): List<RecordedVideoInfo> {
      return DrxMediaStorageManager.loadGalleryItems(context)
        .filter { it.type == MediaItemType.VIDEO }
        .map {
          RecordedVideoInfo(
            uriString = it.uriString,
            displayName = it.displayName,
            durationSec = it.durationSec,
            sizeBytes = it.sizeBytes,
            resolutionLabel = if (it.width > 0 && it.height > 0) "${it.width}x${it.height}" else "MP4",
            timestampMs = it.timestampMs,
          )
        }
    }

    fun deleteRecording(context: Context, uriString: String): Boolean {
      val deleted = DrxMediaStorageManager.deleteMediaItem(context, uriString)
      ScreenRecorderStateHolder.setRecentVideos(loadExistingRecordings(context))
      return deleted
    }

    fun formatDuration(seconds: Int): String {
      val safe = seconds.coerceAtLeast(0)
      val hrs = safe / 3600
      val mins = (safe % 3600) / 60
      val secs = safe % 60
      return if (hrs > 0) String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
      else String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    internal fun resolveVerifiedDurationSec(
      containerDurationMs: Long,
      muxedDurationMs: Long,
      elapsedWallMs: Long,
    ): Int {
      val safeElapsedMs = elapsedWallMs.coerceAtLeast(1_000L)
      val upperSaneBoundMs = safeElapsedMs + 15_000L
      val preferredMs =
        when {
          containerDurationMs in 500L..upperSaneBoundMs -> containerDurationMs
          muxedDurationMs in 500L..upperSaneBoundMs -> muxedDurationMs
          else -> safeElapsedMs
        }
      return (preferredMs / 1000.0).roundToInt().coerceAtLeast(1)
    }
  }

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var timerJob: Job? = null
  private var countdownJob: Job? = null
  private var countdownOverlayView: TextView? = null

  private var mediaProjection: MediaProjection? = null
  private var mediaRecorder: MediaRecorder? = null
  private var muxerEngine: SystemAudioVideoMuxerEngine? = null
  private var virtualDisplay: VirtualDisplay? = null

  private var pendingMediaStoreUri: Uri? = null
  private var outputPfd: ParcelFileDescriptor? = null
  private var legacyOutputFile: File? = null
  private var currentFileName: String = ""
  private var currentResolutionLabel: String = ""
  private var currentAudioMode: ScreenRecordAudioMode = ScreenRecordAudioMode.NONE
  private var activeRunStartMs: Long = 0L
  private var accumulatedElapsedMs: Long = 0L
  private var isPausedState = false
  private var isStopping = false
  private var callbackRegistered = false

  private val projectionCallback =
    object : MediaProjection.Callback() {
      override fun onStop() {
        stopRecordingInternal(fromSystemCallback = true)
      }
    }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP -> stopRecordingInternal(fromSystemCallback = false)
      ACTION_PAUSE -> handlePause()
      ACTION_RESUME -> handleResume()
      ACTION_START -> handleStartIntent(intent)
      ACTION_CAPTURE_SCREENSHOT -> handleScreenshotIntent(intent, duringRecording = false)
      ACTION_CAPTURE_SCREENSHOT_DURING_REC -> handleScreenshotIntent(intent, duringRecording = true)
      else -> {
        ensureForegroundNotification("Screen Recorder standby", elapsedSec = 0)
        stopSelf()
      }
    }
    return START_NOT_STICKY
  }

  private fun handlePause() {
    if (isPausedState || isStopping) return
    val activeMuxer = muxerEngine
    if (activeMuxer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      activeMuxer.pause()
      isPausedState = true
      if (activeRunStartMs > 0L) {
        accumulatedElapsedMs += (SystemClock.elapsedRealtime() - activeRunStartMs).coerceAtLeast(0L)
        activeRunStartMs = 0L
      }
      ScreenRecorderStateHolder.setPaused(true, "Recording paused")
      ensureForegroundNotification("PAUSED • $currentResolutionLabel", elapsedSec = (accumulatedElapsedMs / 1000L).toInt())
    } else {
      val rec = mediaRecorder ?: return
      try {
        rec.pause()
        isPausedState = true
        if (activeRunStartMs > 0L) {
          accumulatedElapsedMs += (SystemClock.elapsedRealtime() - activeRunStartMs).coerceAtLeast(0L)
          activeRunStartMs = 0L
        }
        ScreenRecorderStateHolder.setPaused(true, "Recording paused")
        ensureForegroundNotification("PAUSED • $currentResolutionLabel", elapsedSec = (accumulatedElapsedMs / 1000L).toInt())
      } catch (_: Throwable) {
      }
    }
  }

  private fun handleResume() {
    if (!isPausedState || isStopping) return
    val activeMuxer = muxerEngine
    if (activeMuxer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      activeMuxer.resume()
      isPausedState = false
      activeRunStartMs = SystemClock.elapsedRealtime()
      ScreenRecorderStateHolder.setPaused(false, "Recording resumed")
    } else {
      val rec = mediaRecorder ?: return
      try {
        rec.resume()
        isPausedState = false
        activeRunStartMs = SystemClock.elapsedRealtime()
        ScreenRecorderStateHolder.setPaused(false, "Recording resumed")
      } catch (_: Throwable) {
      }
    }
  }

  @SuppressLint("WrongConstant")
  private fun handleScreenshotIntent(intent: Intent, duringRecording: Boolean) {
    val format =
      runCatching {
        ScreenshotFormat.valueOf(intent.getStringExtra(EXTRA_SCREENSHOT_FORMAT) ?: ScreenshotFormat.PNG.name)
      }.getOrDefault(ScreenshotFormat.PNG)

    if (duringRecording) {
      val activeProj = mediaProjection
      if (activeProj == null || (mediaRecorder == null && muxerEngine == null)) {
        ScreenRecorderStateHolder.setScreenshotCaptured(
          null,
          null,
          "Active recording projection unavailable; retry screenshot.",
        )
        return
      }
      captureFrameFromProjection(
        projection = activeProj,
        ownsProjection = false,
        format = format,
      )
      return
    }

    createNotificationChannel()
    if (!ensureForegroundNotification("Capturing DRX Screenshot...", elapsedSec = 0)) {
      ScreenRecorderStateHolder.setScreenshotCaptured(null, null, "Foreground MediaProjection permission denied.")
      stopSelf()
      return
    }
    val existingProj = mediaProjection
    if (existingProj != null && (mediaRecorder != null || muxerEngine != null)) {
      captureFrameFromProjection(
        projection = existingProj,
        ownsProjection = false,
        format = format,
      )
      return
    }

    val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
    val resultData: Intent? =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(EXTRA_RESULT_DATA)
      }
    if (resultCode != Activity.RESULT_OK || resultData == null) {
      ScreenRecorderStateHolder.setScreenshotCaptured(null, null, "Screenshot consent was cancelled.")
      stopForegroundCompat()
      stopSelf()
      return
    }

    val pm = getSystemService(MediaProjectionManager::class.java)
    val projection = runCatching { pm?.getMediaProjection(resultCode, resultData) }.getOrNull()
    if (projection == null) {
      ScreenRecorderStateHolder.setScreenshotCaptured(null, null, "Unable to obtain MediaProjection for screenshot.")
      stopForegroundCompat()
      stopSelf()
      return
    }

    captureFrameFromProjection(
      projection = projection,
      ownsProjection = true,
      format = format,
    )
  }

  @SuppressLint("WrongConstant")
  private fun captureFrameFromProjection(
    projection: MediaProjection,
    ownsProjection: Boolean,
    format: ScreenshotFormat,
  ) {
    val (w, h, dpi) = computeSafeCaptureGeometry(0) // Orientation-aware full display size
    val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
    val finished = java.util.concurrent.atomic.AtomicBoolean(false)
    val processing = java.util.concurrent.atomic.AtomicBoolean(false)
    val blankAttempts = java.util.concurrent.atomic.AtomicInteger(0)
    val mainHandler = Handler(Looper.getMainLooper())

    var tempCallbackRegistered = false
    val tempProjectionCallback =
      object : MediaProjection.Callback() {
        override fun onStop() {
          if (finished.compareAndSet(false, true)) {
            runCatching { reader.close() }
            ScreenRecorderStateHolder.setScreenshotCaptured(
              null,
              null,
              "Screen capture stopped by system before a valid frame was ready.",
            )
            if (ownsProjection) {
              stopForegroundCompat()
              stopSelf()
            }
          }
        }
      }

    if (ownsProjection) {
      runCatching {
        projection.registerCallback(tempProjectionCallback, mainHandler)
        tempCallbackRegistered = true
      }
    }

    var vdRef: VirtualDisplay? = null
    fun releaseCaptureResources() {
      mainHandler.post {
        runCatching { vdRef?.release() }
        vdRef = null
        runCatching { reader.close() }
        if (ownsProjection) {
          if (tempCallbackRegistered) {
            runCatching { projection.unregisterCallback(tempProjectionCallback) }
          }
          runCatching { projection.stop() }
          stopForegroundCompat()
          stopSelf()
        }
      }
    }

    val vd =
      runCatching {
        projection.createVirtualDisplay(
          if (ownsProjection) "DRX_Screenshot" else "DRX_Screenshot_Aux",
          w,
          h,
          dpi,
          DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
          reader.surface,
          null,
          mainHandler,
        )
      }.getOrNull()

    if (vd == null) {
      runCatching { reader.close() }
      if (ownsProjection) {
        if (tempCallbackRegistered) {
          runCatching { projection.unregisterCallback(tempProjectionCallback) }
        }
        runCatching { projection.stop() }
        stopForegroundCompat()
        stopSelf()
      }
      val msg =
        if (!ownsProjection) "Device restricted secondary screenshot VirtualDisplay while recording."
        else "VirtualDisplay could not initialize for screenshot."
      ScreenRecorderStateHolder.setScreenshotCaptured(null, null, msg)
      return
    }
    vdRef = vd

    reader.setOnImageAvailableListener({ imgReader ->
      if (finished.get() || !processing.compareAndSet(false, true)) return@setOnImageAvailableListener
      val image = runCatching { imgReader.acquireLatestImage() }.getOrNull()
      if (image == null) {
        processing.set(false)
        return@setOnImageAvailableListener
      }

      serviceScope.launch(Dispatchers.IO) {
        var copiedBitmap: Bitmap? = null
        try {
          val plane = image.planes[0]
          val buffer = plane.buffer
          val pixelStride = plane.pixelStride.coerceAtLeast(4)
          val rowStride = plane.rowStride
          val rowPadding = (rowStride - pixelStride * w).coerceAtLeast(0)
          val bmpWidth = (w + rowPadding / pixelStride).coerceAtLeast(w)
          val rawBmp = createBitmap(bmpWidth, h)
          rawBmp.copyPixelsFromBuffer(buffer)
          val cropped =
            if (bmpWidth != w) {
              Bitmap.createBitmap(rawBmp, 0, 0, w, h).also {
                if (it != rawBmp) rawBmp.recycle()
              }
            } else {
              rawBmp
            }
          copiedBitmap = cropped

          if (ScreenshotEngine.isBlankOrBlackBitmap(cropped)) {
            val tries = blankAttempts.incrementAndGet()
            if (tries < 8) {
              // Discard warm-up black frame and wait for next rendered frame
              return@launch
            }
            if (finished.compareAndSet(false, true)) {
              ScreenRecorderStateHolder.setScreenshotCaptured(
                item = null,
                status = null,
                error = "Screen appears blank or protected (FLAG_SECURE). Blank frame was not saved.",
              )
              releaseCaptureResources()
            }
            return@launch
          }

          if (!finished.compareAndSet(false, true)) return@launch

          val fileName = ScreenshotEngine.buildUniqueScreenshotFilename(format = format)
          val safUri = runCatching { DrxPreferences(this@ScreenRecorderService).prefsFlow.first().safRootTreeUri }.getOrDefault("")
          val saved =
            DrxMediaStorageManager.saveBitmapScreenshot(
              context = this@ScreenRecorderService,
              bitmap = cropped,
              fileName = fileName,
              safTreeUri = safUri,
              format = format,
            )
          ScreenRecorderStateHolder.setScreenshotCaptured(
            item = saved,
            status = if (saved != null) "Screenshot saved: ${saved.displayName}" else null,
            error = if (saved == null) "Failed to write verified screenshot to storage." else null,
          )
          releaseCaptureResources()
        } catch (t: Throwable) {
          if (finished.compareAndSet(false, true)) {
            ScreenRecorderStateHolder.setScreenshotCaptured(null, null, "Screenshot error: ${t.message ?: "Unknown"}")
            releaseCaptureResources()
          }
        } finally {
          runCatching { copiedBitmap?.recycle() }
          runCatching { image.close() }
          processing.set(false)
        }
      }
    }, mainHandler)

    // Safety timeout if OS yields only blank frames or stalls
    mainHandler.postDelayed({
      if (finished.compareAndSet(false, true)) {
        val hadBlankFrames = blankAttempts.get() > 0
        ScreenRecorderStateHolder.setScreenshotCaptured(
          null,
          null,
          if (hadBlankFrames) "Capture blocked (FLAG_SECURE or blank surface). No blank file saved."
          else "Screenshot capture timed out before a valid frame arrived.",
        )
        releaseCaptureResources()
      }
    }, 2800L)
  }

  private fun handleStartIntent(intent: Intent) {
    createNotificationChannel()
    if (!ensureForegroundNotification("Preparing screen capture...", elapsedSec = 0)) {
      ScreenRecorderStateHolder.setRecordingFinished(
        saved = null,
        recent = loadExistingRecordings(this),
        status = null,
        error = "Foreground screen projection permission rejected by OS.",
      )
      stopSelf()
      return
    }

    if (mediaProjection != null && (mediaRecorder != null || muxerEngine != null)) return

    val (enoughSpace, availBytes) = DrxMediaStorageManager.checkStorageCapacityForRecording(this)
    if (!enoughSpace) {
      failStart(
        "Insufficient storage (${TimeAndSizeFormatters.formatBytesAuto(availBytes)} free). At least 250 MB free is required."
      )
      return
    }

    val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
    val resultData: Intent? =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
      } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(EXTRA_RESULT_DATA)
      }

    if (resultCode != Activity.RESULT_OK || resultData == null) {
      failStart("Screen capture consent was cancelled.")
      return
    }

    val rawPreset = intent.getIntExtra(EXTRA_RESOLUTION_PRESET, 720)
    val resolutionPreset = if (rawPreset == 0) 0 else rawPreset.coerceIn(480, 1080)
    val targetFps = intent.getIntExtra(EXTRA_TARGET_FPS, 30).coerceIn(24, 60)
    val bitrateBps = intent.getIntExtra(EXTRA_BITRATE_BPS, 6_000_000).coerceIn(2_000_000, 24_000_000)
    val requestedMode =
      runCatching {
        ScreenRecordAudioMode.valueOf(intent.getStringExtra(EXTRA_AUDIO_MODE) ?: ScreenRecordAudioMode.NONE.name)
      }.getOrDefault(ScreenRecordAudioMode.NONE)
    val countdownSec = intent.getIntExtra(EXTRA_COUNTDOWN_SEC, 0).coerceIn(0, 10)

    countdownJob?.cancel()
    countdownJob =
      serviceScope.launch {
        if (countdownSec > 0) {
          for (sec in countdownSec downTo 1) {
            ScreenRecorderStateHolder.setCountdownRemaining(sec)
            showCountdownOverlayBadge(sec)
            ensureForegroundNotification("DRX REC starts in ${sec}s...", elapsedSec = 0)
            delay(1000L)
          }
          removeCountdownOverlayBadge()
          ScreenRecorderStateHolder.setCountdownRemaining(0)
        }
        beginRecordingAfterCountdown(
          resultCode = resultCode,
          resultData = resultData,
          resolutionPreset = resolutionPreset,
          targetFps = targetFps,
          bitrateBps = bitrateBps,
          requestedMode = requestedMode,
        )
      }
  }

  private fun beginRecordingAfterCountdown(
    resultCode: Int,
    resultData: Intent,
    resolutionPreset: Int,
    targetFps: Int,
    bitrateBps: Int,
    requestedMode: ScreenRecordAudioMode,
  ) {
    val audioPermGranted =
      ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    val pm = getSystemService(MediaProjectionManager::class.java)
    if (pm == null) {
      failStart("MediaProjectionManager unavailable on this device.")
      return
    }

    val projection =
      try {
        pm.getMediaProjection(resultCode, resultData)
      } catch (t: Throwable) {
        failStart("Failed to obtain MediaProjection: ${t.message ?: "Permission error"}")
        return
      } ?: run {
        failStart("Screen capture token is null or expired.")
        return
      }

    mediaProjection = projection
    try {
      projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
      callbackRegistered = true
    } catch (_: Throwable) {
      callbackRegistered = false
    }

    val (encWidth, encHeight, densityDpi) = computeSafeCaptureGeometry(resolutionPreset)
    currentResolutionLabel = "${encWidth}x${encHeight}"

    var activeModeLabel = "Video Only"
    var startedOk = false

    val wantsInternal =
      requestedMode == ScreenRecordAudioMode.SYSTEM_AUDIO ||
        requestedMode == ScreenRecordAudioMode.INTERNAL_AND_MIC
    if (wantsInternal && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && audioPermGranted) {
      startedOk =
        startSystemAudioMuxerPipeline(
          projection = projection,
          width = encWidth,
          height = encHeight,
          densityDpi = densityDpi,
          fps = targetFps,
          bitrateBps = bitrateBps,
          includeMicMix = requestedMode == ScreenRecordAudioMode.INTERNAL_AND_MIC,
        )
      if (startedOk) {
        activeModeLabel =
          if (requestedMode == ScreenRecordAudioMode.INTERNAL_AND_MIC) "Internal + Mic Audio"
          else "Internal Audio + Video"
      }
    }

    if (!startedOk && requestedMode == ScreenRecordAudioMode.MIC_AUDIO && audioPermGranted) {
      startedOk =
        startRecorderPipeline(
          projection = projection,
          width = encWidth,
          height = encHeight,
          densityDpi = densityDpi,
          fps = targetFps,
          bitrateBps = bitrateBps,
          withMicAudio = true,
        )
      if (startedOk) activeModeLabel = "Mic Audio + Video"
    }

    if (!startedOk) {
      startedOk =
        startRecorderPipeline(
          projection = projection,
          width = encWidth,
          height = encHeight,
          densityDpi = densityDpi,
          fps = targetFps,
          bitrateBps = bitrateBps,
          withMicAudio = false,
        )
      if (startedOk) {
        activeModeLabel =
          if (requestedMode != ScreenRecordAudioMode.NONE && !audioPermGranted) {
            "Video Only (Audio perm not granted)"
          } else if (wantsInternal) {
            "Video Only (Internal audio restricted/unavailable)"
          } else {
            "Video Only"
          }
      }
    }

    if (!startedOk) {
      cleanupOutputOnFailure()
      failStart("Video encoder could not initialize (${encWidth}x${encHeight}). Try 720p / 30 FPS.")
      return
    }

    currentAudioMode =
      when {
        !startedOk -> ScreenRecordAudioMode.NONE
        requestedMode == ScreenRecordAudioMode.INTERNAL_AND_MIC && muxerEngine != null -> ScreenRecordAudioMode.INTERNAL_AND_MIC
        requestedMode == ScreenRecordAudioMode.SYSTEM_AUDIO && muxerEngine != null -> ScreenRecordAudioMode.SYSTEM_AUDIO
        requestedMode == ScreenRecordAudioMode.MIC_AUDIO && mediaRecorder != null && audioPermGranted -> ScreenRecordAudioMode.MIC_AUDIO
        else -> ScreenRecordAudioMode.NONE
      }

    accumulatedElapsedMs = 0L
    activeRunStartMs = SystemClock.elapsedRealtime()
    isPausedState = false
    ScreenRecorderStateHolder.setRecordingStarted(
      "Recording ${encWidth}x${encHeight} @ ${targetFps}fps • $activeModeLabel"
    )
    ensureForegroundNotification("REC 00:00 • ${encWidth}x${encHeight}", elapsedSec = 0)
    startElapsedTicker()
  }

  private fun startSystemAudioMuxerPipeline(
    projection: MediaProjection,
    width: Int,
    height: Int,
    densityDpi: Int,
    fps: Int,
    bitrateBps: Int,
    includeMicMix: Boolean,
  ): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    return try {
      cleanupRecorderOnly()
      cleanupOutputOnFailure()
      if (!prepareOutputFile()) return false

      val fd = outputPfd?.fileDescriptor
      val path = legacyOutputFile?.absolutePath
      val engine =
        SystemAudioVideoMuxerEngine(
          projection = projection,
          outputFd = fd,
          outputPath = path,
          width = width,
          height = height,
          densityDpi = densityDpi,
          fps = fps,
          videoBitrateBps = bitrateBps,
          includeMicMix = includeMicMix,
          onMicAmplitude = { level -> ScreenRecorderStateHolder.updateMicAmplitude(level) },
        )
      val vd = engine.start()
      if (vd == null) {
        engine.stopAndRelease()
        cleanupOutputOnFailure()
        false
      } else {
        muxerEngine = engine
        virtualDisplay = vd
        true
      }
    } catch (_: Throwable) {
      cleanupRecorderOnly()
      cleanupOutputOnFailure()
      false
    }
  }

  private fun startRecorderPipeline(
    projection: MediaProjection,
    width: Int,
    height: Int,
    densityDpi: Int,
    fps: Int,
    bitrateBps: Int,
    withMicAudio: Boolean,
  ): Boolean {
    return try {
      cleanupRecorderOnly()
      cleanupOutputOnFailure()
      if (!prepareOutputFile()) return false

      val recorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this)
        else @Suppress("DEPRECATION") MediaRecorder()

      if (withMicAudio) recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
      recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
      recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

      val fd = outputPfd?.fileDescriptor
      if (fd != null) recorder.setOutputFile(fd)
      else recorder.setOutputFile((legacyOutputFile ?: return false).absolutePath)

      recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
      recorder.setVideoEncodingBitRate(bitrateBps)
      recorder.setVideoFrameRate(fps)
      recorder.setVideoSize(width, height)

      if (withMicAudio) {
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioEncodingBitRate(128_000)
        recorder.setAudioSamplingRate(44_100)
      }

      recorder.prepare()
      val vd =
        projection.createVirtualDisplay(
          "DRX_ScreenRecorder",
          width,
          height,
          densityDpi,
          DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
          recorder.surface,
          null,
          null,
        ) ?: run {
          recorder.reset()
          recorder.release()
          return false
        }

      recorder.start()
      mediaRecorder = recorder
      virtualDisplay = vd
      true
    } catch (_: Throwable) {
      cleanupRecorderOnly()
      false
    }
  }

  private fun prepareOutputFile(): Boolean {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    currentFileName = "DRX_Recording_$stamp.mp4"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      return try {
        val values =
          ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, currentFileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, DrxMediaStorageManager.MEDIASTORE_VIDEO_REL_PATH)
            put(MediaStore.Video.Media.IS_PENDING, 1)
          }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        val pfd = contentResolver.openFileDescriptor(uri, "rw")
        if (pfd == null) {
          contentResolver.delete(uri, null, null)
          return false
        }
        pendingMediaStoreUri = uri
        outputPfd = pfd
        true
      } catch (_: Throwable) {
        false
      }
    }

    return try {
      val file = File(DrxMediaStorageManager.legacyRecordingDir(this), currentFileName)
      legacyOutputFile = file
      true
    } catch (_: Throwable) {
      false
    }
  }

  private fun computeSafeCaptureGeometry(presetHeight: Int): Triple<Int, Int, Int> {
    val wm = getSystemService(WindowManager::class.java)
    var rawW = 1080
    var rawH = 1920
    var densityDpi = resources.displayMetrics.densityDpi.coerceAtLeast(160)

    if (wm != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = wm.currentWindowMetrics.bounds
        rawW = bounds.width().coerceAtLeast(320)
        rawH = bounds.height().coerceAtLeast(320)
      } else {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay?.getRealMetrics(metrics)
        if (metrics.widthPixels > 0 && metrics.heightPixels > 0) {
          rawW = metrics.widthPixels
          rawH = metrics.heightPixels
          densityDpi = metrics.densityDpi.coerceAtLeast(160)
        }
      }
    }

    val shortEdge = minOf(rawW, rawH)
    val scale = if (presetHeight > 0 && shortEdge > presetHeight) presetHeight.toFloat() / shortEdge.toFloat() else 1f
    val targetW = (((rawW * scale).toInt() / 16) * 16).coerceAtLeast(320)
    val targetH = (((rawH * scale).toInt() / 16) * 16).coerceAtLeast(320)
    return Triple(targetW, targetH, densityDpi)
  }

  private fun startElapsedTicker() {
    timerJob?.cancel()
    timerJob =
      serviceScope.launch {
        var tick = 0
        while (isActive && (mediaRecorder != null || muxerEngine != null)) {
          val liveRunMs =
            if (!isPausedState && activeRunStartMs > 0L) (SystemClock.elapsedRealtime() - activeRunStartMs).coerceAtLeast(0L)
            else 0L
          val elapsedSec = ((accumulatedElapsedMs + liveRunMs) / 1000L).toInt().coerceAtLeast(0)

          if (currentAudioMode == ScreenRecordAudioMode.MIC_AUDIO && mediaRecorder != null && !isPausedState) {
            val amp = runCatching { mediaRecorder?.maxAmplitude ?: 0 }.getOrDefault(0)
            ScreenRecorderStateHolder.updateMicAmplitude((amp / 32767f).coerceIn(0f, 1f))
          } else if (isPausedState || currentAudioMode == ScreenRecordAudioMode.NONE || currentAudioMode == ScreenRecordAudioMode.SYSTEM_AUDIO) {
            ScreenRecorderStateHolder.updateMicAmplitude(0f)
          }

          if (tick % 5 == 0) {
            val bytes =
              runCatching { outputPfd?.statSize?.takeIf { it > 0L } }.getOrNull()
                ?: legacyOutputFile?.length()?.coerceAtLeast(0L)
                ?: 0L
            ScreenRecorderStateHolder.updateElapsedAndSize(elapsedSec, bytes)
            val sizeLabel = if (bytes > 0L) " • ${TimeAndSizeFormatters.formatBytesAuto(bytes)}" else ""
            val prefix = if (isPausedState) "PAUSED" else "REC"
            ensureForegroundNotification("$prefix ${formatDuration(elapsedSec)}$sizeLabel", elapsedSec = elapsedSec)
          }
          tick++
          delay(200L)
        }
      }
  }

  private fun stopRecordingInternal(fromSystemCallback: Boolean) {
    if (countdownJob?.isActive == true && mediaRecorder == null && muxerEngine == null) {
      countdownJob?.cancel()
      countdownJob = null
      removeCountdownOverlayBadge()
      ScreenRecorderStateHolder.setCountdownRemaining(0)
      ScreenRecorderStateHolder.setRecordingFinished(
        saved = null,
        recent = loadExistingRecordings(this),
        status = "Recording countdown cancelled.",
        error = null,
      )
      stopForegroundCompat()
      stopSelf()
      return
    }
    if (isStopping) return
    isStopping = true
    countdownJob?.cancel()
    countdownJob = null
    removeCountdownOverlayBadge()
    timerJob?.cancel()
    timerJob = null
    currentAudioMode = ScreenRecordAudioMode.NONE
    ScreenRecorderStateHolder.updateMicAmplitude(0f)

    val liveRunMs =
      if (!isPausedState && activeRunStartMs > 0L) (SystemClock.elapsedRealtime() - activeRunStartMs).coerceAtLeast(0L)
      else 0L
    val activeElapsedMs = (accumulatedElapsedMs + liveRunMs).coerceAtLeast(1_000L)

    var stopSuccess = false
    var muxedDurationMs = 0L
    val activeMuxer = muxerEngine
    if (activeMuxer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      stopSuccess = activeMuxer.stopAndRelease()
      muxedDurationMs = activeMuxer.recordedDurationMs()
      muxerEngine = null
    } else {
      try {
        mediaRecorder?.apply {
          try {
            stop()
            stopSuccess = true
          } catch (_: Throwable) {
            stopSuccess = false
          }
        }
      } catch (_: Throwable) {
      }
    }

    cleanupRecorderOnly()

    if (!fromSystemCallback) {
      runCatching {
        if (callbackRegistered) {
          mediaProjection?.unregisterCallback(projectionCallback)
          callbackRegistered = false
        }
      }
      runCatching { mediaProjection?.stop() }
    }
    mediaProjection = null

    runCatching { outputPfd?.close() }
    outputPfd = null

    val savedInfo = finalizeSavedOutput(stopSuccess, activeElapsedMs, muxedDurationMs)
    val recent = loadExistingRecordings(this)
    ScreenRecorderStateHolder.setRecordingFinished(
      saved = savedInfo,
      recent = recent,
      status =
        if (savedInfo != null) "Saved ${savedInfo.displayName} (${formatDuration(savedInfo.durationSec)})"
        else null,
      error =
        if (savedInfo == null) "Recording ended without valid frames (minimum ~1s required)."
        else null,
    )

    isPausedState = false
    isStopping = false
    stopForegroundCompat()
    stopSelf()
  }

  private fun finalizeSavedOutput(
    stopSuccess: Boolean,
    elapsedWallMs: Long,
    muxedDurationMs: Long,
  ): RecordedVideoInfo? {
    val storeUri = pendingMediaStoreUri
    pendingMediaStoreUri = null
    if (storeUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      return try {
        val containerDurMs = queryActualVideoDurationMs(storeUri)
        val verifiedSec = resolveVerifiedDurationSec(containerDurMs, muxedDurationMs, elapsedWallMs)
        val verifiedMs = verifiedSec * 1000L

        val updateValues =
          ContentValues().apply {
            put(MediaStore.Video.Media.IS_PENDING, 0)
            put(MediaStore.Video.Media.DURATION, verifiedMs)
          }
        contentResolver.update(storeUri, updateValues, null, null)
        val size = queryUriSize(contentResolver, storeUri)
        if (!stopSuccess && size <= 0L) {
          contentResolver.delete(storeUri, null, null)
          null
        } else {
          serviceScope.launch(Dispatchers.IO) {
            val safUri = runCatching { DrxPreferences(this@ScreenRecorderService).prefsFlow.first().safRootTreeUri }.getOrDefault("")
            if (safUri.isNotBlank()) {
              DrxMediaStorageManager.mirrorFileToSafIfSet(
                this@ScreenRecorderService,
                safUri,
                DrxMediaStorageManager.SUBFOLDER_RECORDER,
                currentFileName,
                "video/mp4",
                storeUri,
              )
            }
          }
          RecordedVideoInfo(
            uriString = storeUri.toString(),
            displayName = currentFileName,
            durationSec = verifiedSec,
            sizeBytes = size,
            resolutionLabel = currentResolutionLabel,
            timestampMs = System.currentTimeMillis(),
          )
        }
      } catch (_: Throwable) {
        null
      }
    }

    val file = legacyOutputFile
    legacyOutputFile = null
    if (file != null && file.exists() && file.length() > 0L) {
      val uri =
        runCatching { FileProvider.getUriForFile(this, "$packageName.fileprovider", file) }.getOrNull()
          ?: Uri.fromFile(file)
      val containerDurMs = queryActualVideoDurationMs(uri)
      val verifiedSec = resolveVerifiedDurationSec(containerDurMs, muxedDurationMs, elapsedWallMs)
      return RecordedVideoInfo(
        uriString = uri.toString(),
        displayName = file.name,
        durationSec = verifiedSec,
        sizeBytes = file.length(),
        resolutionLabel = currentResolutionLabel,
        timestampMs = file.lastModified(),
      )
    } else if (file != null && file.exists()) {
      runCatching { file.delete() }
    }
    return null
  }

  private fun queryActualVideoDurationMs(uri: Uri): Long {
    val mmr = MediaMetadataRetriever()
    return try {
      mmr.setDataSource(this, uri)
      mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    } catch (_: Throwable) {
      0L
    } finally {
      runCatching { mmr.release() }
    }
  }

  private fun queryUriSize(resolver: ContentResolver, uri: Uri): Long {
    return try {
      resolver.query(uri, arrayOf(MediaStore.Video.Media.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getLong(0).coerceAtLeast(0L) else 0L
      } ?: 0L
    } catch (_: Throwable) {
      0L
    }
  }

  private fun cleanupRecorderOnly() {
    runCatching { virtualDisplay?.release() }
    virtualDisplay = null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      runCatching { muxerEngine?.stopAndRelease() }
    }
    muxerEngine = null
    runCatching { mediaRecorder?.reset() }
    runCatching { mediaRecorder?.release() }
    mediaRecorder = null
  }

  private fun cleanupOutputOnFailure() {
    runCatching { outputPfd?.close() }
    outputPfd = null
    pendingMediaStoreUri?.let { uri -> runCatching { contentResolver.delete(uri, null, null) } }
    pendingMediaStoreUri = null
    legacyOutputFile?.let { file -> if (file.exists()) runCatching { file.delete() } }
    legacyOutputFile = null
  }

  private fun failStart(message: String) {
    cleanupRecorderOnly()
    runCatching {
      if (callbackRegistered) {
        mediaProjection?.unregisterCallback(projectionCallback)
        callbackRegistered = false
      }
      mediaProjection?.stop()
    }
    mediaProjection = null
    ScreenRecorderStateHolder.setRecordingFinished(
      saved = null,
      recent = loadExistingRecordings(this),
      status = null,
      error = message,
    )
    stopForegroundCompat()
    stopSelf()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = getSystemService(NotificationManager::class.java) ?: return
      val ch =
        NotificationChannel(
          CHANNEL_ID,
          getString(R.string.screen_recorder_channel_name),
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          description = getString(R.string.screen_recorder_channel_desc)
          setShowBadge(false)
        }
      nm.createNotificationChannel(ch)
    }
  }

  private fun ensureForegroundNotification(statusText: String, elapsedSec: Int): Boolean {
    val openIntent =
      PendingIntent.getActivity(
        this,
        310,
        Intent(this, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val stopIntent =
      PendingIntent.getService(
        this,
        311,
        Intent(this, ScreenRecorderService::class.java).apply { action = ACTION_STOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val pauseResumeIntent =
      PendingIntent.getService(
        this,
        312,
        Intent(this, ScreenRecorderService::class.java).apply {
          action = if (isPausedState) ACTION_RESUME else ACTION_PAUSE
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val notification: Notification =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("DRX Screen Recorder")
        .setContentText(statusText)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setUsesChronometer(elapsedSec > 0 && !isPausedState)
        .setContentIntent(openIntent)
        .addAction(0, if (isPausedState) "Resume" else "Pause", pauseResumeIntent)
        .addAction(0, "Stop", stopIntent)
        .build()

    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
      } else {
        startForeground(NOTIFICATION_ID, notification)
      }
      true
    } catch (_: Throwable) {
      false
    }
  }

  private fun showCountdownOverlayBadge(secondsRemaining: Int) {
    if (!android.provider.Settings.canDrawOverlays(this)) return
    val wm = getSystemService(WindowManager::class.java) ?: return
    val existing = countdownOverlayView
    if (existing != null && existing.isAttachedToWindow) {
      existing.text = "DRX REC • $secondsRemaining"
      return
    }
    val overlayType =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
      else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
    val density = resources.displayMetrics.density
    val params =
      WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        overlayType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
          WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
      ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = (42f * density).roundToInt()
      }
    val badge =
      TextView(this).apply {
        text = "DRX REC • $secondsRemaining"
        setTextColor("#F5F8FF".toColorInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = DrxOverlayFonts.manropeBold(this@ScreenRecorderService)
        fontFeatureSettings = "tnum"
        setPadding(
          (18f * density).roundToInt(),
          (8f * density).roundToInt(),
          (18f * density).roundToInt(),
          (8f * density).roundToInt(),
        )
        background =
          android.graphics.drawable.GradientDrawable().apply {
            setColor("#EB101722".toColorInt())
            setStroke((1.5f * density).roundToInt(), "#FF5C70".toColorInt())
            cornerRadius = 999f * density
          }
      }
    try {
      wm.addView(badge, params)
      countdownOverlayView = badge
    } catch (_: Throwable) {
      countdownOverlayView = null
    }
  }

  private fun removeCountdownOverlayBadge() {
    val view = countdownOverlayView ?: return
    countdownOverlayView = null
    val wm = getSystemService(WindowManager::class.java) ?: return
    try {
      if (view.isAttachedToWindow) wm.removeView(view)
    } catch (_: Throwable) {
    }
  }

  private fun stopForegroundCompat() {
    runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
  }

  override fun onDestroy() {
    countdownJob?.cancel()
    removeCountdownOverlayBadge()
    timerJob?.cancel()
    cleanupRecorderOnly()
    runCatching {
      if (callbackRegistered) {
        mediaProjection?.unregisterCallback(projectionCallback)
        callbackRegistered = false
      }
      mediaProjection?.stop()
    }
    mediaProjection = null
    serviceScope.cancel()
    super.onDestroy()
  }
}
