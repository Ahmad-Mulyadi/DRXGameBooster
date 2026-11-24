package com.example.system

import android.annotation.SuppressLint
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Bundle
import android.view.Surface
import androidx.annotation.RequiresApi
import java.io.FileDescriptor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Re-bases encoder track presentation timestamps (which on Android VirtualDisplay Surface encoders
 * typically use monotonic device uptime such as 100+ hours since boot) onto a 0-based active
 * recording timeline, subtracting pause durations cleanly and guarding against encoder clock-domain
 * jumps (e.g., frame 0 at 0us followed by frame 1 at device uptime us).
 */
internal class MuxerTrackTimestampNormalizer(
  private val subtractPauseDelta: Boolean,
) {
  private var initialized = false
  private var baseRawPtsUs = 0L
  private var basePausedUs = 0L
  var lastOutputPtsUs: Long = -1L
    private set

  fun nextPtsUs(
    rawPtsUs: Long,
    currentPausedUs: Long,
    activeElapsedUs: Long,
  ): Long {
    val safeElapsedUs = activeElapsedUs.coerceAtLeast(0L)
    if (!initialized) {
      initialized = true
      val anchorUs = safeElapsedUs.coerceIn(0L, MAX_INITIAL_ANCHOR_US)
      baseRawPtsUs = rawPtsUs - anchorUs
      basePausedUs = currentPausedUs.coerceAtLeast(0L)
      lastOutputPtsUs = anchorUs
      return anchorUs
    }

    val rawDeltaUs = rawPtsUs - baseRawPtsUs
    val pauseDeltaUs =
      if (subtractPauseDelta) (currentPausedUs - basePausedUs).coerceAtLeast(0L)
      else 0L
    val candidateUs = rawDeltaUs - pauseDeltaUs

    // Guard against clock-domain switches (e.g. frame 0 = 0us, frame 1 = device uptime 100h+)
    if (candidateUs < 0L || abs(candidateUs - safeElapsedUs) > MAX_ELAPSED_DRIFT_US) {
      val reanchoredUs = safeElapsedUs.coerceAtLeast((lastOutputPtsUs + 1L).coerceAtLeast(0L))
      baseRawPtsUs = rawPtsUs - reanchoredUs
      basePausedUs = currentPausedUs.coerceAtLeast(0L)
      lastOutputPtsUs = reanchoredUs
      return reanchoredUs
    }

    val monotonicUs =
      if (candidateUs <= lastOutputPtsUs) lastOutputPtsUs + 1L
      else candidateUs
    lastOutputPtsUs = monotonicUs
    return monotonicUs
  }

  private companion object {
    const val MAX_INITIAL_ANCHOR_US = 500_000L
    const val MAX_ELAPSED_DRIFT_US = 2_000_000L
  }
}

/**
 * Android 10+ (API 29+) MediaCodec (H.264 Surface + AAC) & MediaMuxer engine that captures
 * internal system/game audio (or mixed Internal + Mic audio) alongside screen video, with
 * pause/resume PTS alignment support.
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class SystemAudioVideoMuxerEngine(
  private val projection: MediaProjection,
  private val outputFd: FileDescriptor?,
  private val outputPath: String?,
  private val width: Int,
  private val height: Int,
  private val densityDpi: Int,
  private val fps: Int,
  private val videoBitrateBps: Int,
  private val includeMicMix: Boolean = false,
  private val onMicAmplitude: (Float) -> Unit = {},
) {
  private val running = AtomicBoolean(false)
  private val paused = AtomicBoolean(false)
  private val pauseLock = Any()
  private val muxerLock = Any()

  private var muxerStarted = false
  private var videoTrackIndex = -1
  private var audioTrackIndex = -1
  private var wroteAnyVideo = false

  private var pauseStartedNs = 0L
  private var totalPausedNs = 0L
  @Volatile private var muxingStartedNs = 0L
  @Volatile private var muxingStartedPausedUs = 0L

  private val videoPtsNormalizer = MuxerTrackTimestampNormalizer(subtractPauseDelta = true)
  private val audioPtsNormalizer = MuxerTrackTimestampNormalizer(subtractPauseDelta = false)

  private var videoCodec: MediaCodec? = null
  private var audioCodec: MediaCodec? = null
  private var playbackRecord: AudioRecord? = null
  private var micRecord: AudioRecord? = null
  private var inputSurface: Surface? = null
  private var muxer: MediaMuxer? = null

  private var videoDrainThread: Thread? = null
  private var audioFeedThread: Thread? = null
  private var audioDrainThread: Thread? = null

  @SuppressLint("MissingPermission")
  fun start(): VirtualDisplay? {
    val sampleRate = 44_100
    val channelConfig = AudioFormat.CHANNEL_IN_STEREO
    val channelCount = 2
    val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
      .coerceAtLeast(4096)

    val captureConfig =
      AudioPlaybackCaptureConfiguration.Builder(projection)
        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
        .addMatchingUsage(AudioAttributes.USAGE_GAME)
        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
        .build()

    val internalRec =
      AudioRecord.Builder()
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .build()
        )
        .setBufferSizeInBytes(minBuf * 2)
        .setAudioPlaybackCaptureConfig(captureConfig)
        .build()

    if (internalRec.state != AudioRecord.STATE_INITIALIZED) {
      internalRec.release()
      return null
    }
    playbackRecord = internalRec

    if (includeMicMix) {
      val mic =
        try {
          AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
              AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .build()
        } catch (_: Throwable) {
          null
        }
      if (mic != null && mic.state == AudioRecord.STATE_INITIALIZED) {
        micRecord = mic
      } else {
        mic?.release()
      }
    }

    val vFormat =
      MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, videoBitrateBps)
        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
      }
    val vCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    vCodec.configure(vFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    val surface = vCodec.createInputSurface()
    videoCodec = vCodec
    inputSurface = surface

    val aFormat =
      MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
      }
    val aCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    aCodec.configure(aFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    audioCodec = aCodec

    val mediaMuxer =
      if (outputFd != null) MediaMuxer(outputFd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
      else MediaMuxer(outputPath!!, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    muxer = mediaMuxer

    val vd =
      projection.createVirtualDisplay(
        "DRX_ScreenRecorder_SysAudio",
        width,
        height,
        densityDpi,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        surface,
        null,
        null,
      ) ?: return null

    synchronized(pauseLock) {
      totalPausedNs = 0L
      pauseStartedNs = 0L
      paused.set(false)
    }
    muxingStartedNs = 0L
    muxingStartedPausedUs = 0L
    running.set(true)
    vCodec.start()
    aCodec.start()
    internalRec.startRecording()
    micRecord?.startRecording()

    val startNs = System.nanoTime()
    videoDrainThread = Thread({ drainVideoLoop() }, "DRX-VideoDrain").apply { start() }
    audioFeedThread = Thread({ feedAudioLoop(startNs) }, "DRX-AudioFeed").apply { start() }
    audioDrainThread = Thread({ drainAudioLoop(startNs) }, "DRX-AudioDrain").apply { start() }
    return vd
  }

  fun pause() {
    synchronized(pauseLock) {
      if (!paused.get()) {
        pauseStartedNs = System.nanoTime()
        paused.set(true)
      }
    }
  }

  fun resume() {
    synchronized(pauseLock) {
      if (paused.get()) {
        val delta = (System.nanoTime() - pauseStartedNs).coerceAtLeast(0L)
        totalPausedNs += delta
        pauseStartedNs = 0L
        paused.set(false)
      }
    }
  }

  fun recordedDurationMs(): Long {
    val maxPtsUs = maxOf(videoPtsNormalizer.lastOutputPtsUs, audioPtsNormalizer.lastOutputPtsUs)
    return if (maxPtsUs > 0L) (maxPtsUs / 1000L) else 0L
  }

  private fun snapshotClockState(): Triple<Boolean, Long, Long> {
    val nowNs = System.nanoTime()
    synchronized(pauseLock) {
      val isPausedNow = paused.get()
      val effectivePausedNs =
        if (isPausedNow && pauseStartedNs > 0L) {
          totalPausedNs + (nowNs - pauseStartedNs).coerceAtLeast(0L)
        } else {
          totalPausedNs
        }
      return Triple(isPausedNow, effectivePausedNs / 1000L, nowNs)
    }
  }

  private fun computeActiveElapsedUs(nowNs: Long, currentPausedUs: Long): Long {
    val originNs = muxingStartedNs
    if (originNs <= 0L) return 0L
    val wallUs = ((nowNs - originNs) / 1000L).coerceAtLeast(0L)
    val pausedSinceMuxStartUs = (currentPausedUs - muxingStartedPausedUs).coerceAtLeast(0L)
    return (wallUs - pausedSinceMuxStartUs).coerceAtLeast(0L)
  }

  private fun maybeStartMuxerLocked() {
    if (!muxerStarted && videoTrackIndex >= 0 && audioTrackIndex >= 0) {
      val (_, pausedUs, nowNs) = snapshotClockState()
      muxingStartedNs = nowNs
      muxingStartedPausedUs = pausedUs
      muxer?.start()
      muxerStarted = true
      runCatching {
        val params = Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) }
        videoCodec?.setParameters(params)
      }
    }
  }

  private fun drainVideoLoop() {
    val codec = videoCodec ?: return
    val info = MediaCodec.BufferInfo()
    try {
      while (true) {
        val outIdx = codec.dequeueOutputBuffer(info, 15_000L)
        if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
          if (!running.get()) break
        } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          synchronized(muxerLock) {
            if (videoTrackIndex < 0) {
              videoTrackIndex = muxer?.addTrack(codec.outputFormat) ?: -1
              maybeStartMuxerLocked()
            }
          }
        } else if (outIdx >= 0) {
          val buf = codec.getOutputBuffer(outIdx)
          if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            info.size = 0
          }
          if (info.size > 0 && buf != null) {
            val (isPausedNow, currentPausedUs, nowNs) = snapshotClockState()
            if (!isPausedNow) {
              synchronized(muxerLock) {
                if (muxerStarted && videoTrackIndex >= 0) {
                  val activeElapsedUs = computeActiveElapsedUs(nowNs, currentPausedUs)
                  val pts =
                    videoPtsNormalizer.nextPtsUs(
                      rawPtsUs = info.presentationTimeUs,
                      currentPausedUs = currentPausedUs,
                      activeElapsedUs = activeElapsedUs,
                    )
                  info.presentationTimeUs = pts
                  buf.position(info.offset)
                  buf.limit(info.offset + info.size)
                  muxer?.writeSampleData(videoTrackIndex, buf, info)
                  wroteAnyVideo = true
                }
              }
            }
          }
          codec.releaseOutputBuffer(outIdx, false)
          if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
        }
      }
    } catch (_: Throwable) {
    }
  }

  private fun feedAudioLoop(startNs: Long) {
    val internalRec = playbackRecord ?: return
    val micRec = micRecord
    val codec = audioCodec ?: return
    val internalBuf = ByteArray(4096)
    val micBuf = if (micRec != null) ByteArray(4096) else null
    var lastQueuedAudioPtsUs = -1L

    try {
      while (running.get()) {
        val readInt = internalRec.read(internalBuf, 0, internalBuf.size)
        val readMic =
          if (micRec != null && micBuf != null && readInt > 0) {
            micRec.read(micBuf, 0, readInt)
          } else {
            0
          }
        val (isPausedNow, currentPausedUs, nowNs) = snapshotClockState()
        if (isPausedNow) {
          onMicAmplitude(0f)
          continue
        }
        if (readInt > 0) {
          if (micBuf != null && readMic > 0) {
            onMicAmplitude(computeNormalizedRms(micBuf, readMic))
            mixPcm16InPlace(internalBuf, micBuf, minOf(readInt, readMic))
          } else {
            onMicAmplitude(0f)
          }
          val inIdx = codec.dequeueInputBuffer(15_000L)
          if (inIdx >= 0) {
            val inBuf = codec.getInputBuffer(inIdx)
            if (inBuf != null) {
              inBuf.clear()
              val toWrite = minOf(readInt, inBuf.remaining())
              inBuf.put(internalBuf, 0, toWrite)
              val rawPtsUs = (((nowNs - startNs) / 1000L) - currentPausedUs).coerceAtLeast(0L)
              val ptsUs =
                if (rawPtsUs <= lastQueuedAudioPtsUs) lastQueuedAudioPtsUs + 1L else rawPtsUs
              lastQueuedAudioPtsUs = ptsUs
              codec.queueInputBuffer(inIdx, 0, toWrite, ptsUs, 0)
            }
          }
        }
      }
      val eosIdx = codec.dequeueInputBuffer(15_000L)
      if (eosIdx >= 0) {
        val (_, currentPausedUs, nowNs) = snapshotClockState()
        val rawPtsUs = (((nowNs - startNs) / 1000L) - currentPausedUs).coerceAtLeast(0L)
        val ptsUs = if (rawPtsUs <= lastQueuedAudioPtsUs) lastQueuedAudioPtsUs + 1L else rawPtsUs
        codec.queueInputBuffer(eosIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
      }
    } catch (_: Throwable) {
    } finally {
      onMicAmplitude(0f)
    }
  }

  companion object {
    internal fun computeNormalizedRms(pcm: ByteArray, lengthBytes: Int): Float {
      var sumSquares = 0.0
      var samples = 0
      var i = 0
      while (i + 1 < lengthBytes) {
        val s = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toDouble()
        sumSquares += s * s
        samples++
        i += 2
      }
      if (samples == 0) return 0f
      val rms = sqrt(sumSquares / samples.toDouble())
      return (rms / 12_000.0).toFloat().coerceIn(0f, 1f)
    }
  }

  private fun mixPcm16InPlace(dest: ByteArray, mic: ByteArray, lengthBytes: Int) {
    var i = 0
    while (i + 1 < lengthBytes) {
      val s1 = ((dest[i + 1].toInt() shl 8) or (dest[i].toInt() and 0xFF)).toShort().toInt()
      val s2 = ((mic[i + 1].toInt() shl 8) or (mic[i].toInt() and 0xFF)).toShort().toInt()
      val mixed = ((s1 * 0.85f) + (s2 * 0.85f)).toInt().coerceIn(-32768, 32767)
      dest[i] = (mixed and 0xFF).toByte()
      dest[i + 1] = ((mixed ushr 8) and 0xFF).toByte()
      i += 2
    }
  }

  private fun drainAudioLoop(startNs: Long) {
    val codec = audioCodec ?: return
    val info = MediaCodec.BufferInfo()
    try {
      while (true) {
        val outIdx = codec.dequeueOutputBuffer(info, 15_000L)
        if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
          if (!running.get()) break
        } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          synchronized(muxerLock) {
            if (audioTrackIndex < 0) {
              audioTrackIndex = muxer?.addTrack(codec.outputFormat) ?: -1
              maybeStartMuxerLocked()
            }
          }
        } else if (outIdx >= 0) {
          val buf = codec.getOutputBuffer(outIdx)
          if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            info.size = 0
          }
          if (info.size > 0 && buf != null) {
            val (isPausedNow, currentPausedUs, nowNs) = snapshotClockState()
            if (!isPausedNow) {
              synchronized(muxerLock) {
                if (muxerStarted && audioTrackIndex >= 0) {
                  val activeElapsedUs = computeActiveElapsedUs(nowNs, currentPausedUs)
                  val candidateRawPts =
                    if (info.presentationTimeUs >= 0L) info.presentationTimeUs
                    else (((nowNs - startNs) / 1000L) - currentPausedUs).coerceAtLeast(0L)
                  val pts =
                    audioPtsNormalizer.nextPtsUs(
                      rawPtsUs = candidateRawPts,
                      currentPausedUs = currentPausedUs,
                      activeElapsedUs = activeElapsedUs,
                    )
                  info.presentationTimeUs = pts
                  buf.position(info.offset)
                  buf.limit(info.offset + info.size)
                  muxer?.writeSampleData(audioTrackIndex, buf, info)
                }
              }
            }
          }
          codec.releaseOutputBuffer(outIdx, false)
          if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
        }
      }
    } catch (_: Throwable) {
    }
  }

  fun stopAndRelease(): Boolean {
    if (!running.compareAndSet(true, false) && muxer == null && videoCodec == null) {
      return false
    }
    try {
      videoCodec?.signalEndOfInputStream()
    } catch (_: Throwable) {
    }
    try {
      playbackRecord?.stop()
    } catch (_: Throwable) {
    }
    try {
      micRecord?.stop()
    } catch (_: Throwable) {
    }

    runCatching { audioFeedThread?.join(1500L) }
    runCatching { videoDrainThread?.join(1500L) }
    runCatching { audioDrainThread?.join(1500L) }

    var savedOk = false
    synchronized(muxerLock) {
      if (muxerStarted) {
        try {
          muxer?.stop()
          savedOk = wroteAnyVideo
        } catch (_: Throwable) {
          savedOk = false
        }
      }
      try {
        muxer?.release()
      } catch (_: Throwable) {
      }
      muxer = null
      muxerStarted = false
    }

    runCatching { playbackRecord?.release() }
    playbackRecord = null
    runCatching { micRecord?.release() }
    micRecord = null
    runCatching { videoCodec?.stop() }
    runCatching { videoCodec?.release() }
    videoCodec = null
    runCatching { audioCodec?.stop() }
    runCatching { audioCodec?.release() }
    audioCodec = null
    runCatching { inputSurface?.release() }
    inputSurface = null
    return savedOk
  }
}
