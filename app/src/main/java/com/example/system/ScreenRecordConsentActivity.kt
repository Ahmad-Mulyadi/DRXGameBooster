package com.example.system

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.data.prefs.DrxPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Lightweight transparent Activity isolated in its own empty taskAffinity so starting
 * screen recording or screenshots from FloatingSidebarService never brings MainActivity
 * back to the foreground over the user's active game.
 */
class ScreenRecordConsentActivity : ComponentActivity() {

  companion object {
    const val EXTRA_CAPTURE_MODE = "extra_capture_mode"
    const val EXTRA_COUNTDOWN_SEC = "extra_countdown_sec"
    const val MODE_RECORD = "record"
    const val MODE_SCREENSHOT = "screenshot"
  }

  private var captureMode: String = MODE_RECORD
  private var requestedCountdownSec: Int = 3

  private val projectionLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      val data = result.data
      if (result.resultCode == Activity.RESULT_OK && data != null) {
        if (captureMode == MODE_SCREENSHOT) {
          ScreenRecorderService.captureScreenshot(this, result.resultCode, data)
          Toast.makeText(this, "Capturing DRX Screenshot...", Toast.LENGTH_SHORT).show()
        } else {
          val state = ScreenRecorderStateHolder.state.value
          ScreenRecorderService.startRecording(
            context = this,
            resultCode = result.resultCode,
            data = data,
            resolutionPreset = state.resolutionPreset,
            targetFps = state.targetFps,
            audioMode = state.audioMode,
            bitrateBps = ScreenRecorderService.resolveEffectiveBitrateBps(state),
            countdownSec = requestedCountdownSec,
          )
          val msg =
            if (requestedCountdownSec > 0) "DRX Screen Recorder starting in ${requestedCountdownSec}s..."
            else "DRX Screen Recorder started"
          Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
      } else {
        Toast.makeText(this, "Screen capture permission cancelled", Toast.LENGTH_SHORT).show()
      }
      finishWithoutTransition()
    }

  private val permissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      launchMediaProjectionPrompt()
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    captureMode = intent?.getStringExtra(EXTRA_CAPTURE_MODE) ?: MODE_RECORD
    requestedCountdownSec =
      if (intent?.hasExtra(EXTRA_COUNTDOWN_SEC) == true) {
        intent.getIntExtra(EXTRA_COUNTDOWN_SEC, 3).coerceIn(0, 10)
      } else {
        runCatching { runBlocking { DrxPreferences(applicationContext).prefsFlow.first().recorderCountdownSec } }
          .getOrDefault(3)
      }

    val needed = buildList {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this@ScreenRecordConsentActivity, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
      ) {
        add(Manifest.permission.POST_NOTIFICATIONS)
      }
      if (captureMode == MODE_RECORD &&
        ScreenRecorderStateHolder.state.value.audioMode != ScreenRecordAudioMode.NONE &&
        ContextCompat.checkSelfPermission(this@ScreenRecordConsentActivity, Manifest.permission.RECORD_AUDIO) !=
          PackageManager.PERMISSION_GRANTED
      ) {
        add(Manifest.permission.RECORD_AUDIO)
      }
    }

    if (needed.isNotEmpty()) {
      try {
        permissionLauncher.launch(needed.toTypedArray())
      } catch (_: Throwable) {
        launchMediaProjectionPrompt()
      }
    } else {
      launchMediaProjectionPrompt()
    }
  }

  private fun launchMediaProjectionPrompt() {
    try {
      val pm = getSystemService(MediaProjectionManager::class.java)
      if (pm == null) {
        Toast.makeText(this, "MediaProjection unavailable", Toast.LENGTH_SHORT).show()
        finishWithoutTransition()
        return
      }
      projectionLauncher.launch(pm.createScreenCaptureIntent())
    } catch (_: Throwable) {
      Toast.makeText(this, "Unable to open screen capture prompt", Toast.LENGTH_SHORT).show()
      finishWithoutTransition()
    }
  }

  private fun finishWithoutTransition() {
    finish()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
    } else {
      @Suppress("DEPRECATION")
      overridePendingTransition(0, 0)
    }
  }
}
