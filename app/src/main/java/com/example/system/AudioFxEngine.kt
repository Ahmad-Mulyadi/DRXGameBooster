package com.example.system

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import com.example.data.model.SoundPreset
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

data class EqualizerBandUi(
  val index: Short,
  val centerFreqHz: Int,
  val levelMb: Short,
  val minMb: Short,
  val maxMb: Short,
)

class AudioFxEngine {

  private var audioTrack: AudioTrack? = null
  private var equalizer: Equalizer? = null
  private var bassBoost: BassBoost? = null
  @Suppress("DEPRECATION")
  private var virtualizer: Virtualizer? = null
  private var loudnessEnhancer: LoudnessEnhancer? = null

  @Volatile private var isPlayingTest = false
  private var playThread: Thread? = null

  var bassSupported: Boolean = false
    private set

  var virtualizerSupported: Boolean = false
    private set

  var eqSupported: Boolean = false
    private set

  fun initializeSession(): List<EqualizerBandUi> {
    if (audioTrack != null && equalizer != null) {
      return readBands()
    }
    release()

    val sampleRate = 44100
    val rawMinBuf =
      AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT,
      )
    if (rawMinBuf <= 0) {
      eqSupported = false
      bassSupported = false
      virtualizerSupported = false
      return defaultFallbackBands()
    }
    val minBuf = rawMinBuf.coerceAtLeast(4096)

    val track =
      try {
        AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_GAME)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(sampleRate)
              .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
              .build()
          )
          .setBufferSizeInBytes(minBuf * 2)
          .setTransferMode(AudioTrack.MODE_STREAM)
          .build()
      } catch (_: Throwable) {
        null
      }

    if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
      try {
        track?.release()
      } catch (_: Throwable) {
      }
      eqSupported = false
      bassSupported = false
      virtualizerSupported = false
      return defaultFallbackBands()
    }

    audioTrack = track
    val sessionId = track.audioSessionId

    try {
      val eq = Equalizer(0, sessionId)
      eq.enabled = true
      equalizer = eq
      eqSupported = eq.numberOfBands > 0
    } catch (_: Throwable) {
      eqSupported = false
    }

    try {
      val bb = BassBoost(0, sessionId)
      bassSupported = bb.strengthSupported
      bb.enabled = true
      bassBoost = bb
    } catch (_: Throwable) {
      bassSupported = false
    }

    @Suppress("DEPRECATION")
    try {
      val vz = Virtualizer(0, sessionId)
      virtualizerSupported = vz.strengthSupported
      vz.enabled = false
      virtualizer = vz
    } catch (_: Throwable) {
      virtualizerSupported = false
    }

    try {
      val le = LoudnessEnhancer(sessionId)
      le.enabled = false
      loudnessEnhancer = le
    } catch (_: Throwable) {
      loudnessEnhancer = null
    }

    return readBands()
  }

  fun readBands(): List<EqualizerBandUi> {
    val eq = equalizer ?: return defaultFallbackBands()
    return try {
      val count = eq.numberOfBands.toInt()
      val range = eq.bandLevelRange
      val minMb = range.getOrElse(0) { (-1500).toShort() }
      val maxMb = range.getOrElse(1) { 1500.toShort() }
      (0 until count).map { i ->
        val idx = i.toShort()
        EqualizerBandUi(
          index = idx,
          centerFreqHz = (eq.getCenterFreq(idx) / 1000).coerceAtLeast(20),
          levelMb = eq.getBandLevel(idx),
          minMb = minMb,
          maxMb = maxMb,
        )
      }
    } catch (_: Throwable) {
      defaultFallbackBands()
    }
  }

  private fun defaultFallbackBands(): List<EqualizerBandUi> =
    listOf(60, 230, 910, 3600, 14000).mapIndexed { idx, hz ->
      EqualizerBandUi(idx.toShort(), hz, 0, (-1500).toShort(), 1500.toShort())
    }

  fun setBandLevel(bandIndex: Short, levelMb: Short) {
    try {
      equalizer?.let { eq ->
        if (bandIndex < eq.numberOfBands) {
          val range = eq.bandLevelRange
          val clamped = levelMb.toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort()
          eq.setBandLevel(bandIndex, clamped)
        }
      }
    } catch (_: Throwable) {
    }
  }

  fun setBassStrength(strength0To1000: Int) {
    try {
      bassBoost?.let { bb ->
        if (bb.strengthSupported) {
          bb.enabled = strength0To1000 > 0
          bb.setStrength(strength0To1000.coerceIn(0, 1000).toShort())
        }
      }
    } catch (_: Throwable) {
    }
  }

  @Suppress("DEPRECATION")
  fun setVirtualSurround(enabled: Boolean, strength0To1000: Int = 700) {
    try {
      virtualizer?.let { vz ->
        vz.enabled = enabled
        if (vz.strengthSupported && enabled) {
          vz.setStrength(strength0To1000.coerceIn(0, 1000).toShort())
        }
      }
    } catch (_: Throwable) {
    }
  }

  fun setSafeVolumeBoost(gainPercent: Int) {
    // Safety-capped to max +900 mB (+9 dB) to prevent speaker/hearing distortion
    try {
      loudnessEnhancer?.let { le ->
        val clamped = gainPercent.coerceIn(0, 100)
        le.enabled = clamped > 0
        le.setTargetGain(((clamped / 100f) * 900f).toInt())
      }
    } catch (_: Throwable) {
    }
  }

  fun applyPreset(preset: SoundPreset): List<EqualizerBandUi> {
    val bands = readBands()
    val targets =
      when (preset) {
        SoundPreset.BALANCED -> listOf(0, 0, 0, 0, 0)
        SoundPreset.FPS_FOOTSTEPS -> listOf(-300, -100, 300, 900, 750)
        SoundPreset.MOBA_VOICE -> listOf(-200, 200, 650, 450, 150)
        SoundPreset.BASS_IMPACT -> listOf(950, 600, 100, 150, 250)
        SoundPreset.TREBLE_CLARITY -> listOf(-150, 0, 350, 800, 1000)
        SoundPreset.CUSTOM -> return bands
      }
    bands.forEachIndexed { idx, band ->
      val valMb = targets.getOrElse(idx) { 0 }.toShort()
      setBandLevel(band.index, valMb)
    }
    return readBands()
  }

  fun startSpatialAudioTest(onFinished: () -> Unit) {
    if (isPlayingTest) {
      stopAudioTest()
      onFinished()
      return
    }
    initializeSession()
    val track = audioTrack
    if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
      isPlayingTest = false
      onFinished()
      return
    }
    isPlayingTest = true
    playThread =
      thread(start = true, name = "DrxAudioTest") {
        val sampleRate = 44100
        val chunkFrames = 1024
        val buffer = ShortArray(chunkFrames * 2)
        var frameIndex = 0L
        val totalFrames = sampleRate * 4L // 4-second stereo sweep + footstep simulation
        try {
          track.play()
          while (isPlayingTest && frameIndex < totalFrames) {
            for (i in 0 until chunkFrames) {
              val t = (frameIndex + i).toDouble() / sampleRate
              // Pan left -> right -> center
              val pan =
                when {
                  t < 1.3 -> 0.15
                  t < 2.6 -> 0.85
                  else -> 0.5
                }
              // Footstep rhythmic transient every 0.35s + harmonic gaming synth chord
              val beatPhase = (t % 0.35)
              val transientEnv = if (beatPhase < 0.08) kotlin.math.exp(-beatPhase * 45.0) else 0.0
              val subBass = sin(2.0 * PI * 90.0 * t) * 0.35
              val midStep = sin(2.0 * PI * 440.0 * t) * transientEnv * 0.45
              val highClick = sin(2.0 * PI * 3200.0 * t) * transientEnv * 0.35
              val sample = ((subBass + midStep + highClick) * 16000.0).coerceIn(-32000.0, 32000.0)

              val left = (sample * (1.0 - pan * 0.7)).toInt().toShort()
              val right = (sample * (0.3 + pan * 0.7)).toInt().toShort()
              buffer[i * 2] = left
              buffer[i * 2 + 1] = right
            }
            track.write(buffer, 0, buffer.size)
            frameIndex += chunkFrames
          }
          if (track.state == AudioTrack.STATE_INITIALIZED) {
            track.pause()
            track.flush()
          }
        } catch (_: Throwable) {
        } finally {
          isPlayingTest = false
          onFinished()
        }
      }
  }

  fun stopAudioTest() {
    isPlayingTest = false
    try {
      audioTrack?.pause()
      audioTrack?.flush()
    } catch (_: Throwable) {
    }
    playThread?.join(200)
    playThread = null
  }

  fun release() {
    stopAudioTest()
    try {
      equalizer?.release()
    } catch (_: Throwable) {}
    try {
      bassBoost?.release()
    } catch (_: Throwable) {}
    try {
      virtualizer?.release()
    } catch (_: Throwable) {}
    try {
      loudnessEnhancer?.release()
    } catch (_: Throwable) {}
    try {
      audioTrack?.release()
    } catch (_: Throwable) {}
    equalizer = null
    bassBoost = null
    virtualizer = null
    loudnessEnhancer = null
    audioTrack = null
  }
}
