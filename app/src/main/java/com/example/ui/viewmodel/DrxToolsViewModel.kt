package com.example.ui.viewmodel

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.db.DrxDatabase
import com.example.data.db.DrxRepository
import com.example.data.db.GamingNoteEntity
import com.example.data.db.NetworkTestEntity
import com.example.data.model.NetworkDiagnosticResult
import com.example.data.model.SoundPreset
import com.example.data.model.VoicePreset
import com.example.system.AudioFxEngine
import com.example.system.EqualizerBandUi
import com.example.system.NetworkDiagnosticsManager
import com.example.system.VoiceEffectParams
import com.example.system.VoiceSfxType
import com.example.system.VoiceStudioEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DrxToolsViewModel(application: Application) : AndroidViewModel(application) {

  private val appContext = application.applicationContext
  private val repository = DrxRepository(DrxDatabase.getInstance(appContext))

  val networkManager = NetworkDiagnosticsManager()
  val audioFxEngine = AudioFxEngine()
  val voiceStudioEngine = VoiceStudioEngine()

  // Network Diagnostics State
  val networkHistory: StateFlow<List<NetworkTestEntity>> =
    repository.recentNetworkTests.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList(),
    )

  private val _isTestingNetwork = MutableStateFlow(false)
  val isTestingNetwork: StateFlow<Boolean> = _isTestingNetwork.asStateFlow()

  private val _networkTestProgress = MutableStateFlow(0f)
  val networkTestProgress: StateFlow<Float> = _networkTestProgress.asStateFlow()

  private val _latestNetworkResult = MutableStateFlow<NetworkDiagnosticResult?>(null)
  val latestNetworkResult: StateFlow<NetworkDiagnosticResult?> = _latestNetworkResult.asStateFlow()

  // Sound Center State
  private val _eqBands = MutableStateFlow<List<EqualizerBandUi>>(emptyList())
  val eqBands: StateFlow<List<EqualizerBandUi>> = _eqBands.asStateFlow()

  private val _soundPreset = MutableStateFlow(SoundPreset.BALANCED)
  val soundPreset: StateFlow<SoundPreset> = _soundPreset.asStateFlow()

  private val _bassStrength = MutableStateFlow(350)
  val bassStrength: StateFlow<Int> = _bassStrength.asStateFlow()

  private val _virtualSurround = MutableStateFlow(false)
  val virtualSurround: StateFlow<Boolean> = _virtualSurround.asStateFlow()

  private val _volumeBoostPercent = MutableStateFlow(0)
  val volumeBoostPercent: StateFlow<Int> = _volumeBoostPercent.asStateFlow()

  private val _isPlayingAudioTest = MutableStateFlow(false)
  val isPlayingAudioTest: StateFlow<Boolean> = _isPlayingAudioTest.asStateFlow()

  // Voice Studio State
  private val _voiceParams = MutableStateFlow(VoiceEffectParams())
  val voiceParams: StateFlow<VoiceEffectParams> = _voiceParams.asStateFlow()

  private val _isMicActive = MutableStateFlow(false)
  val isMicActive: StateFlow<Boolean> = _isMicActive.asStateFlow()

  private val _liveMonitor = MutableStateFlow(false)
  val liveMonitor: StateFlow<Boolean> = _liveMonitor.asStateFlow()

  private val _hasRecordedClip = MutableStateFlow(false)
  val hasRecordedClip: StateFlow<Boolean> = _hasRecordedClip.asStateFlow()

  private val _isPlayingVoiceClip = MutableStateFlow(false)
  val isPlayingVoiceClip: StateFlow<Boolean> = _isPlayingVoiceClip.asStateFlow()

  private val _voiceVisualizerBars = MutableStateFlow(List(24) { 0.06f })
  val voiceVisualizerBars: StateFlow<List<Float>> = _voiceVisualizerBars.asStateFlow()

  private val _micInputLevel = MutableStateFlow(0f)
  val micInputLevel: StateFlow<Float> = _micInputLevel.asStateFlow()

  private val _voiceError = MutableStateFlow<String?>(null)
  val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

  // Utilities: Stopwatch, Timer, Notes
  private val _stopwatchMs = MutableStateFlow(0L)
  val stopwatchMs: StateFlow<Long> = _stopwatchMs.asStateFlow()

  private val _stopwatchRunning = MutableStateFlow(false)
  val stopwatchRunning: StateFlow<Boolean> = _stopwatchRunning.asStateFlow()

  private val _stopwatchLaps = MutableStateFlow<List<Long>>(emptyList())
  val stopwatchLaps: StateFlow<List<Long>> = _stopwatchLaps.asStateFlow()

  private val _timerRemainingSec = MutableStateFlow(15 * 60)
  val timerRemainingSec: StateFlow<Int> = _timerRemainingSec.asStateFlow()

  private val _timerRunning = MutableStateFlow(false)
  val timerRunning: StateFlow<Boolean> = _timerRunning.asStateFlow()

  val notes: StateFlow<List<GamingNoteEntity>> =
    repository.allNotes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private var stopwatchJob: Job? = null
  private var timerJob: Job? = null

  init {
    _eqBands.value = audioFxEngine.initializeSession()
  }

  // Network Actions
  fun runNetworkDiagnostics(host: String, includeSpeedTest: Boolean, networkType: String) {
    if (_isTestingNetwork.value) return
    viewModelScope.launch {
      _isTestingNetwork.value = true
      _networkTestProgress.value = 0.05f
      try {
        val res =
          networkManager.runFullDiagnostics(
            host = host,
            probeCount = 10,
            includeSpeedTest = includeSpeedTest,
            networkType = networkType,
          ) { p ->
            _networkTestProgress.value = p
          }
        _latestNetworkResult.value = res
        repository.insertNetworkTest(
          NetworkTestEntity(
            host = res.host,
            pingMs = res.pingMs,
            jitterMs = res.jitterMs,
            packetLossPercent = res.packetLossPercent,
            downloadMbps = res.downloadMbps,
            stabilityScore = res.stabilityScore,
            networkType = res.networkType,
          )
        )
      } catch (_: Throwable) {
      } finally {
        _isTestingNetwork.value = false
      }
    }
  }

  // Sound Center Actions
  fun selectSoundPreset(preset: SoundPreset) {
    _soundPreset.value = preset
    _eqBands.value = audioFxEngine.applyPreset(preset)
  }

  fun setEqBand(index: Short, levelMb: Short) {
    _soundPreset.value = SoundPreset.CUSTOM
    audioFxEngine.setBandLevel(index, levelMb)
    _eqBands.value = audioFxEngine.readBands()
  }

  fun setBassStrength(strength: Int) {
    _bassStrength.value = strength
    audioFxEngine.setBassStrength(strength)
  }

  fun setVirtualSurround(enabled: Boolean) {
    _virtualSurround.value = enabled
    audioFxEngine.setVirtualSurround(enabled)
  }

  fun setVolumeBoost(percent: Int) {
    _volumeBoostPercent.value = percent
    audioFxEngine.setSafeVolumeBoost(percent)
  }

  fun toggleSpatialAudioTest() {
    if (_isPlayingAudioTest.value) {
      audioFxEngine.stopAudioTest()
      _isPlayingAudioTest.value = false
    } else {
      _isPlayingAudioTest.value = true
      audioFxEngine.startSpatialAudioTest { _isPlayingAudioTest.value = false }
    }
  }

  // Voice Studio Actions
  fun selectVoicePreset(preset: VoicePreset, customSaved: VoiceEffectParams? = null) {
    val next =
      if (preset == VoicePreset.CUSTOM && customSaved != null) {
        customSaved.copy(preset = VoicePreset.CUSTOM)
      } else {
        VoiceEffectParams.forPreset(preset)
      }
    _voiceParams.value = next
    voiceStudioEngine.params = next
  }

  fun updateVoiceParams(transform: (VoiceEffectParams) -> VoiceEffectParams) {
    val next = transform(_voiceParams.value).copy(preset = VoicePreset.CUSTOM)
    _voiceParams.value = next
    voiceStudioEngine.params = next
  }

  fun resetVoicePreset() {
    val next = VoiceEffectParams.forPreset(VoicePreset.NORMAL)
    _voiceParams.value = next
    voiceStudioEngine.params = next
  }

  fun setLiveMonitor(enabled: Boolean) {
    _liveMonitor.value = enabled
  }

  fun toggleMicCapture() {
    if (_isMicActive.value) {
      voiceStudioEngine.stopCapture()
      _isMicActive.value = false
      _micInputLevel.value = 0f
      _voiceVisualizerBars.value = List(24) { 0.06f }
    } else {
      val granted =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) {
        _voiceError.value = "Microphone permission is required."
        _isMicActive.value = false
        return
      }
      _voiceError.value = null
      _isMicActive.value = true
      voiceStudioEngine.params = _voiceParams.value
      voiceStudioEngine.startCapture(
        liveMonitor = _liveMonitor.value,
        onAmplitudeFrame = { bars, rms ->
          _voiceVisualizerBars.value = bars
          _micInputLevel.value = rms
        },
        onError = { msg ->
          _voiceError.value = msg
          _isMicActive.value = false
          _micInputLevel.value = 0f
        },
        onClipReady = { ready -> _hasRecordedClip.value = ready },
      )
    }
  }

  fun togglePlayVoiceClip() {
    if (_isPlayingVoiceClip.value) {
      voiceStudioEngine.stopPlayback()
      _isPlayingVoiceClip.value = false
      _micInputLevel.value = 0f
    } else {
      if (_isMicActive.value) {
        voiceStudioEngine.stopCapture()
        _isMicActive.value = false
      }
      _isPlayingVoiceClip.value = true
      voiceStudioEngine.playRecordedClip(
        onAmplitudeFrame = { bars, peak ->
          _voiceVisualizerBars.value = bars
          _micInputLevel.value = peak
        },
        onFinished = {
          _isPlayingVoiceClip.value = false
          _micInputLevel.value = 0f
          _voiceVisualizerBars.value = List(24) { 0.06f }
        },
      )
    }
  }

  fun triggerSoundboardSfx(sfx: VoiceSfxType) {
    voiceStudioEngine.playSoundboardSfx(sfx)
  }

  // Utilities Actions
  fun toggleStopwatch() {
    if (_stopwatchRunning.value) {
      _stopwatchRunning.value = false
      stopwatchJob?.cancel()
    } else {
      _stopwatchRunning.value = true
      val start = System.currentTimeMillis() - _stopwatchMs.value
      stopwatchJob =
        viewModelScope.launch {
          while (isActive && _stopwatchRunning.value) {
            _stopwatchMs.value = System.currentTimeMillis() - start
            delay(50L)
          }
        }
    }
  }

  fun lapStopwatch() {
    _stopwatchLaps.value = listOf(_stopwatchMs.value) + _stopwatchLaps.value
  }

  fun resetStopwatch() {
    _stopwatchRunning.value = false
    stopwatchJob?.cancel()
    _stopwatchMs.value = 0L
    _stopwatchLaps.value = emptyList()
  }

  fun setTimerMinutes(minutes: Int) {
    _timerRunning.value = false
    timerJob?.cancel()
    _timerRemainingSec.value = (minutes.coerceIn(1, 180)) * 60
  }

  fun toggleTimer(notifyWhenDone: Boolean) {
    if (_timerRunning.value) {
      _timerRunning.value = false
      timerJob?.cancel()
    } else {
      if (_timerRemainingSec.value <= 0) _timerRemainingSec.value = 15 * 60
      _timerRunning.value = true
      timerJob =
        viewModelScope.launch {
          while (isActive && _timerRunning.value && _timerRemainingSec.value > 0) {
            delay(1000L)
            _timerRemainingSec.value = (_timerRemainingSec.value - 1).coerceAtLeast(0)
            if (_timerRemainingSec.value == 0) {
              _timerRunning.value = false
              if (notifyWhenDone) postTimerDoneNotification()
            }
          }
        }
    }
  }

  fun saveNote(title: String, content: String, gameTag: String) {
    if (title.isBlank() && content.isBlank()) return
    viewModelScope.launch {
      repository.upsertNote(
        GamingNoteEntity(
          title = title.ifBlank { "Tactical Note" },
          content = content,
          gameTag = gameTag,
        )
      )
    }
  }

  fun deleteNote(id: Long) {
    viewModelScope.launch { repository.deleteNote(id) }
  }

  private fun postTimerDoneNotification() {
    try {
      val nm = appContext.getSystemService(NotificationManager::class.java)
      val channelId = "drx_timer_alerts"
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        nm.createNotificationChannel(
          NotificationChannel(
            channelId,
            appContext.getString(R.string.timer_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
          ).apply {
            description = appContext.getString(R.string.timer_channel_desc)
          }
        )
      }
      val notif =
        NotificationCompat.Builder(appContext, channelId)
          .setSmallIcon(R.mipmap.ic_launcher)
          .setContentTitle("DRX GAME TURBO PRO")
          .setContentText("Gaming Timer finished!")
          .setAutoCancel(true)
          .build()
      nm.notify(7003, notif)
    } catch (_: Throwable) {
    }
  }

  override fun onCleared() {
    audioFxEngine.release()
    voiceStudioEngine.stopAll()
    super.onCleared()
  }
}
