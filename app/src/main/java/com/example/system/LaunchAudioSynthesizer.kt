package com.example.system

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Generates clean, subtle procedural sci-fi audio cues for the DRX Game Launch sequence:
 * 1. Deep cinematic startup sub-bass
 * 2. Soft digital activation chime
 * 3. Rising futuristic energy charging sweep
 * 4. Subtle metallic impact + clean confirmation tone
 */
class LaunchAudioSynthesizer {

  @Volatile private var active = false
  private var playThread: Thread? = null
  private var audioTrack: AudioTrack? = null

  fun playLaunchSequence(durationSec: Float = 2.4f) {
    stop()
    active = true
    val sampleRate = 44100
    val totalSamples = (sampleRate * durationSec).toInt().coerceAtLeast(sampleRate)
    val minBuf =
      AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
      ).coerceAtLeast(4096)

    playThread =
      thread(start = true, name = "DrxLaunchAudio") {
        var track: AudioTrack? = null
        try {
          track =
            AudioTrack.Builder()
              .setAudioAttributes(
                AudioAttributes.Builder()
                  .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                  .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                  .build()
              )
              .setAudioFormat(
                AudioFormat.Builder()
                  .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                  .setSampleRate(sampleRate)
                  .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                  .build()
              )
              .setBufferSizeInBytes(minBuf * 2)
              .setTransferMode(AudioTrack.MODE_STREAM)
              .build()
          if (track.state != AudioTrack.STATE_INITIALIZED) {
            return@thread
          }
          audioTrack = track
          track.play()

          val chunk = 1024
          val pcm = ShortArray(chunk)
          var idx = 0
          while (active && idx < totalSamples) {
            val count = minOf(chunk, totalSamples - idx)
            for (i in 0 until count) {
              val t = (idx + i).toDouble() / sampleRate
              val normT = t / durationSec

              // 1. Deep cinematic startup hum (0.0 -> 1.2s)
              val subEnv = exp(-t * 2.2) * (1.0 - exp(-t * 18.0))
              val subBass = sin(2.0 * PI * 68.0 * t) * subEnv * 0.34

              // 2. Soft digital activation chime (~0.25s)
              val actT = (t - 0.22).coerceAtLeast(0.0)
              val actEnv = if (t >= 0.22) exp(-actT * 9.0) else 0.0
              val activation = sin(2.0 * PI * 740.0 * actT) * actEnv * 0.14

              // 3. Rising energy charging sweep (0.4s -> 1.85s)
              val sweepWindow = if (t in 0.4..1.85) sin(PI * ((t - 0.4) / 1.45)) else 0.0
              val sweepFreq = 180.0 + normT * 420.0
              val sweep = sin(2.0 * PI * sweepFreq * t) * sweepWindow * 0.12

              // 4. Subtle metallic impact + clean confirmation chord (~1.85s)
              val impactT = (t - 1.85).coerceAtLeast(0.0)
              val impactEnv = if (t >= 1.85) exp(-impactT * 7.5) else 0.0
              val metallicImpact =
                (sin(2.0 * PI * 140.0 * impactT) * 0.22 +
                  sin(2.0 * PI * 988.0 * impactT) * 0.14 +
                  sin(2.0 * PI * 1480.0 * impactT) * 0.09) * impactEnv

              val mixed = (subBass + activation + sweep + metallicImpact) * 14000.0
              pcm[i] = mixed.coerceIn(-28000.0, 28000.0).roundToInt().toShort()
            }
            if (!active) break
            track.write(pcm, 0, count)
            idx += count
          }
          track.stop()
        } catch (_: Throwable) {
        } finally {
          try {
            track?.release()
          } catch (_: Throwable) {}
          audioTrack = null
          active = false
        }
      }
  }

  fun stop() {
    active = false
    try {
      audioTrack?.pause()
      audioTrack?.flush()
    } catch (_: Throwable) {}
    playThread?.join(150)
    playThread = null
  }
}
