package com.example.system

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import com.example.data.model.VoicePreset
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

enum class VoiceSfxType(val label: String) {
  AIRHORN("Airhorn"),
  VICTORY("Victory"),
  LASER_PING("Laser"),
  TACTICAL_BEEP("Tactical"),
  BOOST_CHIME("Ready"),
}

data class VoiceEffectParams(
  val preset: VoicePreset = VoicePreset.NORMAL,
  val pitchFactor: Float = 1.0f, // 0.6f .. 1.75f
  val robotMix: Float = 0f, // 0f .. 1f
  val echoAmount: Float = 0f, // 0f .. 0.8f
  val reverbAmount: Float = 0f, // 0f .. 0.8f
  val eqLowGain: Float = 1.0f, // 0.3f .. 1.8f (Bass)
  val eqMidGain: Float = 1.0f, // Mid
  val eqHighGain: Float = 1.0f, // Treble
  val noiseReduction: Boolean = true,
) {
  companion object {
    fun forPreset(preset: VoicePreset): VoiceEffectParams =
      when (preset) {
        VoicePreset.NORMAL -> VoiceEffectParams(preset = preset)
        VoicePreset.DEEP_VOICE ->
          VoiceEffectParams(
            preset = preset,
            pitchFactor = 0.72f,
            eqLowGain = 1.55f,
            eqMidGain = 0.95f,
            eqHighGain = 0.75f,
            reverbAmount = 0.2f,
          )
        VoicePreset.HIGH_PITCH ->
          VoiceEffectParams(
            preset = preset,
            pitchFactor = 1.42f,
            eqLowGain = 0.7f,
            eqHighGain = 1.35f,
          )
        VoicePreset.ROBOT ->
          VoiceEffectParams(
            preset = preset,
            pitchFactor = 0.95f,
            robotMix = 0.85f,
            echoAmount = 0.15f,
            reverbAmount = 0.25f,
            eqMidGain = 1.35f,
          )
        VoicePreset.ELECTRONIC ->
          VoiceEffectParams(
            preset = preset,
            pitchFactor = 1.12f,
            robotMix = 0.55f,
            echoAmount = 0.28f,
            reverbAmount = 0.35f,
            eqLowGain = 1.25f,
            eqHighGain = 1.45f,
          )
        VoicePreset.RADIO ->
          VoiceEffectParams(
            preset = preset,
            pitchFactor = 1.04f,
            robotMix = 0.2f,
            eqLowGain = 0.35f,
            eqMidGain = 1.65f,
            eqHighGain = 0.45f,
          )
        VoicePreset.ALIEN ->
          VoiceEffectParams(
            preset = preset,
            pitchFactor = 1.30f,
            robotMix = 0.65f,
            echoAmount = 0.32f,
            reverbAmount = 0.50f,
            eqLowGain = 0.75f,
            eqHighGain = 1.50f,
          )
        VoicePreset.ECHO ->
          VoiceEffectParams(
            preset = preset,
            pitchFactor = 1.0f,
            echoAmount = 0.62f,
            reverbAmount = 0.2f,
          )
        VoicePreset.REVERB ->
          VoiceEffectParams(
            preset = preset,
            pitchFactor = 0.98f,
            reverbAmount = 0.68f,
            echoAmount = 0.2f,
          )
        VoicePreset.CUSTOM ->
          VoiceEffectParams(
            preset = preset,
            pitchFactor = 1.08f,
            eqLowGain = 1.15f,
            eqHighGain = 1.15f,
            reverbAmount = 0.18f,
          )
      }
  }
}

class VoiceStudioEngine {

  private val sampleRate = 22050
  private val echoBuffer = FloatArray(sampleRate) // up to 1s delay
  private var echoWriteIdx = 0

  private val comb1 = FloatArray(743)
  private val comb2 = FloatArray(1051)
  private val comb3 = FloatArray(1499)
  private var c1Idx = 0
  private var c2Idx = 0
  private var c3Idx = 0

  private var lpState = 0f
  private var hpPrevIn = 0f
  private var hpPrevOut = 0f
  private var robotPhase = 0.0

  @Volatile var params: VoiceEffectParams = VoiceEffectParams()
  @Volatile private var isRunning = false
  @Volatile private var isPlayingClip = false
  private var workerThread: Thread? = null
  private var playbackThread: Thread? = null

  private val maxRecordSamples = sampleRate * 15
  private var recordedSamples = ShortArray(0)

  val isNoiseSuppressorSupported: Boolean
    get() = NoiseSuppressor.isAvailable()

  @SuppressLint("MissingPermission")
  fun startCapture(
    liveMonitor: Boolean,
    onAmplitudeFrame: (List<Float>, Float) -> Unit,
    onError: (String) -> Unit,
    onClipReady: (Boolean) -> Unit,
  ) {
    stopAll()
    val minRecBuf =
      AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
      ).coerceAtLeast(1024)

    val record: AudioRecord
    try {
      record =
        AudioRecord(
          MediaRecorder.AudioSource.VOICE_COMMUNICATION,
          sampleRate,
          AudioFormat.CHANNEL_IN_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          minRecBuf,
        )
    } catch (e: Throwable) {
      onError(e.message ?: "AudioRecord init failed")
      return
    }

    if (record.state != AudioRecord.STATE_INITIALIZED) {
      record.release()
      onError("Microphone hardware unavailable")
      return
    }

    val suppressor: NoiseSuppressor? =
      try {
        if (NoiseSuppressor.isAvailable()) {
          NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = params.noiseReduction }
        } else null
      } catch (_: Throwable) {
        null
      }

    val track: AudioTrack? =
      if (liveMonitor) {
        try {
          val minPlayBuf =
            AudioTrack.getMinBufferSize(
              sampleRate,
              AudioFormat.CHANNEL_OUT_MONO,
              AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(1024)
          val builder =
            AudioTrack.Builder()
              .setAudioAttributes(
                AudioAttributes.Builder()
                  .setUsage(AudioAttributes.USAGE_MEDIA)
                  .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                  .build()
              )
              .setAudioFormat(
                AudioFormat.Builder()
                  .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                  .setSampleRate(sampleRate)
                  .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                  .build()
              )
              .setBufferSizeInBytes(minPlayBuf)
              .setTransferMode(AudioTrack.MODE_STREAM)
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
          }
          builder.build().takeIf { it.state == AudioTrack.STATE_INITIALIZED }
        } catch (_: Throwable) {
          null
        }
      } else null

    resetDspState()
    isRunning = true
    val clipCollector = ArrayList<Short>(sampleRate * 5)

    workerThread =
      thread(start = true, name = "DrxVoiceDsp") {
        val frameSize = 512
        val inBuf = ShortArray(frameSize)
        val outBuf = ShortArray(frameSize)
        try {
          record.startRecording()
          track?.play()
          while (isRunning) {
            val read = record.read(inBuf, 0, frameSize)
            if (read <= 0) continue

            suppressor?.enabled = params.noiseReduction
            processDspFrame(inBuf, outBuf, read, params)

            if (liveMonitor) {
              track?.write(outBuf, 0, read)
            }
            if (clipCollector.size < maxRecordSamples) {
              for (i in 0 until read) {
                clipCollector.add(outBuf[i])
              }
            }

            // Compute 24 visualizer bars + peak dB
            val bars = computeVisualizerBars(outBuf, read, 24)
            var sumSq = 0.0
            for (i in 0 until read) {
              val norm = outBuf[i] / 32768.0
              sumSq += norm * norm
            }
            val rms = kotlin.math.sqrt(sumSq / read).toFloat().coerceIn(0f, 1f)
            onAmplitudeFrame(bars, rms)
          }
        } catch (e: Throwable) {
          onError(e.message ?: "Voice DSP error")
        } finally {
          recordedSamples = clipCollector.toShortArray()
          onClipReady(recordedSamples.isNotEmpty())
          try {
            record.stop()
          } catch (_: Throwable) {}
          record.release()
          try {
            suppressor?.release()
          } catch (_: Throwable) {}
          try {
            track?.stop()
          } catch (_: Throwable) {}
          track?.release()
        }
      }
  }

  fun playRecordedClip(
    onAmplitudeFrame: (List<Float>, Float) -> Unit,
    onFinished: () -> Unit,
  ) {
    val clip = recordedSamples
    if (clip.isEmpty()) {
      onFinished()
      return
    }
    stopPlayback()
    isPlayingClip = true
    val minPlayBuf =
      AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
      ).coerceAtLeast(2048)

    val track =
      try {
        AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(sampleRate)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build()
          )
          .setBufferSizeInBytes(minPlayBuf * 2)
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
      isPlayingClip = false
      onFinished()
      return
    }

    playbackThread =
      thread(start = true, name = "DrxVoicePlayback") {
        val chunk = 512
        var offset = 0
        try {
          track.play()
          while (isPlayingClip && offset < clip.size) {
            val len = minOf(chunk, clip.size - offset)
            track.write(clip, offset, len)
            val slice = clip.copyOfRange(offset, offset + len)
            val bars = computeVisualizerBars(slice, len, 24)
            val peak = ((slice.maxOfOrNull { abs(it.toInt()) } ?: 0) / 32768f).sanitizeFinite(0f)
            onAmplitudeFrame(bars, peak)
            offset += len
          }
          track.stop()
        } catch (_: Throwable) {
        } finally {
          isPlayingClip = false
          try {
            track.release()
          } catch (_: Throwable) {
          }
          onFinished()
        }
      }
  }

  fun stopCapture() {
    isRunning = false
    workerThread?.join(400)
    workerThread = null
  }

  fun stopPlayback() {
    isPlayingClip = false
    playbackThread?.join(300)
    playbackThread = null
  }

  fun stopAll() {
    stopCapture()
    stopPlayback()
  }

  fun hasRecordedClip(): Boolean = recordedSamples.isNotEmpty()

  fun playSoundboardSfx(type: VoiceSfxType) {
    thread(start = true, name = "DrxSfxPad") {
      try {
        val sr = 22050
        val durationSec =
          when (type) {
            VoiceSfxType.VICTORY -> 0.55f
            VoiceSfxType.AIRHORN -> 0.48f
            else -> 0.28f
          }
        val count = (sr * durationSec).toInt()
        val buf = ShortArray(count)
        for (i in 0 until count) {
          val t = i.toDouble() / sr
          val env = (1.0 - (i.toDouble() / count)).coerceIn(0.0, 1.0)
          val sample =
            when (type) {
              VoiceSfxType.AIRHORN ->
                (sin(2 * PI * 370 * t) + sin(2 * PI * 466 * t) * 0.8 + sin(2 * PI * 554 * t) * 0.6) * 0.32 * env
              VoiceSfxType.VICTORY -> {
                val freq = if (t < 0.18) 523.25 else if (t < 0.34) 659.25 else 783.99
                sin(2 * PI * freq * t) * 0.45 * env
              }
              VoiceSfxType.LASER_PING -> {
                val sweepFreq = 1400.0 - (t / durationSec) * 980.0
                sin(2 * PI * sweepFreq * t) * 0.42 * env
              }
              VoiceSfxType.TACTICAL_BEEP ->
                if ((i / (sr / 14)) % 2 == 0) sin(2 * PI * 880 * t) * 0.4 else 0.0
              VoiceSfxType.BOOST_CHIME ->
                (sin(2 * PI * 660 * t) + sin(2 * PI * 990 * t) * 0.6) * 0.35 * env
            }
          buf[i] = (sample.coerceIn(-0.95, 0.95) * 32767.0).roundToInt().toShort()
        }
        val track =
          AudioTrack.Builder()
            .setAudioAttributes(
              AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            )
            .setAudioFormat(
              AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sr)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            )
            .setBufferSizeInBytes(buf.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(buf, 0, buf.size)
        track.play()
        Thread.sleep((durationSec * 1000).toLong() + 120L)
        track.stop()
        track.release()
      } catch (_: Throwable) {
      }
    }
  }

  private fun resetDspState() {
    echoBuffer.fill(0f)
    comb1.fill(0f)
    comb2.fill(0f)
    comb3.fill(0f)
    echoWriteIdx = 0
    c1Idx = 0
    c2Idx = 0
    c3Idx = 0
    lpState = 0f
    hpPrevIn = 0f
    hpPrevOut = 0f
    robotPhase = 0.0
  }

  internal fun processDspFrame(
    input: ShortArray,
    output: ShortArray,
    length: Int,
    cfg: VoiceEffectParams,
  ) {
    // 1. Pitch shift via windowed linear resampling within the frame
    val pitch = cfg.pitchFactor.coerceIn(0.55f, 1.85f)
    val echoDelaySamples = (sampleRate * 0.24f).toInt().coerceIn(1, echoBuffer.size - 1)
    val robotStep = 2.0 * PI * 48.0 / sampleRate

    for (i in 0 until length) {
      val srcPos = (i * pitch) % length
      val idx0 = srcPos.toInt().coerceIn(0, length - 1)
      val idx1 = (idx0 + 1).coerceAtMost(length - 1)
      val frac = srcPos - idx0
      var s = ((input[idx0] * (1f - frac) + input[idx1] * frac) / 32768f)

      // 2. Noise gate when noiseReduction enabled
      if (cfg.noiseReduction && abs(s) < 0.014f) {
        s *= 0.15f
      }

      // 3. Simple 3-band EQ decomposition
      lpState += 0.12f * (s - lpState)
      val lowBand = lpState
      val hpOut = 0.85f * (hpPrevOut + s - hpPrevIn)
      hpPrevIn = s
      hpPrevOut = hpOut
      val highBand = hpOut
      val midBand = s - lowBand * 0.6f - highBand * 0.5f
      s = (lowBand * cfg.eqLowGain + midBand * cfg.eqMidGain + highBand * cfg.eqHighGain) * 0.72f

      // 4. Robot ring modulation
      if (cfg.robotMix > 0.01f) {
        val mod = sin(robotPhase).toFloat()
        robotPhase = (robotPhase + robotStep) % (2.0 * PI)
        s = s * (1f - cfg.robotMix) + (s * mod * 1.4f) * cfg.robotMix
      }

      // 5. Echo delay line
      val echoReadIdx = (echoWriteIdx - echoDelaySamples + echoBuffer.size) % echoBuffer.size
      val delayed = echoBuffer[echoReadIdx]
      echoBuffer[echoWriteIdx] = (s + delayed * cfg.echoAmount * 0.55f).coerceIn(-1f, 1f)
      echoWriteIdx = (echoWriteIdx + 1) % echoBuffer.size
      s += delayed * cfg.echoAmount * 0.75f

      // 6. Multi-tap reverb
      if (cfg.reverbAmount > 0.01f) {
        val r1 = comb1[c1Idx]
        val r2 = comb2[c2Idx]
        val r3 = comb3[c3Idx]
        comb1[c1Idx] = (s + r1 * 0.45f).coerceIn(-1f, 1f)
        comb2[c2Idx] = (s + r2 * 0.42f).coerceIn(-1f, 1f)
        comb3[c3Idx] = (s + r3 * 0.38f).coerceIn(-1f, 1f)
        c1Idx = (c1Idx + 1) % comb1.size
        c2Idx = (c2Idx + 1) % comb2.size
        c3Idx = (c3Idx + 1) % comb3.size
        s += ((r1 + r2 + r3) / 3f) * cfg.reverbAmount * 0.85f
      }

      output[i] = (s.coerceIn(-0.98f, 0.98f) * 32767f).roundToInt().toShort()
    }
  }

  private fun computeVisualizerBars(buf: ShortArray, length: Int, barCount: Int): List<Float> {
    if (length <= 0) return List(barCount) { 0.05f }
    val step = (length / barCount).coerceAtLeast(1)
    return (0 until barCount).map { b ->
      val start = b * step
      val end = minOf(length, start + step)
      var peak = 0
      for (i in start until end) {
        val a = abs(buf[i].toInt())
        if (a > peak) peak = a
      }
      (peak / 24000f).coerceIn(0.05f, 1f)
    }
  }
}
