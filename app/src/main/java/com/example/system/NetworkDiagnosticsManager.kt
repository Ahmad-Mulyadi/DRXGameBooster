package com.example.system

import com.example.data.model.NetworkDiagnosticResult
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class NetworkDiagnosticsManager {

  suspend fun measureSinglePing(host: String, port: Int = 443, timeoutMs: Int = 1800): Float? =
    withContext(Dispatchers.IO) {
      val cleanHost = host.trim().ifBlank { "1.1.1.1" }
      val startNs = System.nanoTime()
      try {
        Socket().use { socket ->
          socket.connect(InetSocketAddress(cleanHost, port), timeoutMs)
        }
        val rttMs = (System.nanoTime() - startNs) / 1_000_000f
        rttMs.coerceAtLeast(0.5f)
      } catch (_: Throwable) {
        // Fallback to port 53 (DNS) or 80 if 443 refused
        if (port == 443) {
          try {
            val fallbackStart = System.nanoTime()
            Socket().use { socket ->
              socket.connect(InetSocketAddress(cleanHost, 53), timeoutMs)
            }
            ((System.nanoTime() - fallbackStart) / 1_000_000f).coerceAtLeast(0.5f)
          } catch (_: Throwable) {
            null
          }
        } else {
          null
        }
      }
    }

  suspend fun runFullDiagnostics(
    host: String,
    probeCount: Int = 10,
    includeSpeedTest: Boolean = true,
    networkType: String,
    onProgress: (Float) -> Unit = {},
  ): NetworkDiagnosticResult =
    withContext(Dispatchers.IO) {
      val samples = mutableListOf<Float>()
      var lost = 0
      val count = probeCount.coerceIn(4, 20)

      for (i in 0 until count) {
        val rtt = measureSinglePing(host)
        if (rtt != null) {
          samples.add(rtt)
        } else {
          lost++
        }
        onProgress((i + 1).toFloat() / (count + if (includeSpeedTest) 4 else 0))
        delay(90)
      }

      val avgPing = if (samples.isNotEmpty()) samples.average().toFloat() else 0f
      val jitter =
        if (samples.size >= 2) {
          var deltaSum = 0f
          for (i in 1 until samples.size) {
            deltaSum += abs(samples[i] - samples[i - 1])
          }
          deltaSum / (samples.size - 1)
        } else {
          0f
        }
      val packetLossPct = (lost.toFloat() / count.toFloat()) * 100f

      val downloadMbps =
        if (includeSpeedTest && samples.isNotEmpty()) {
          measureDownloadMbps { p ->
            val base = count.toFloat() / (count + 4)
            onProgress(base + p * (4f / (count + 4)))
          }
        } else {
          0f
        }
      onProgress(1f)

      val stabilityScore =
        if (samples.isEmpty()) {
          0
        } else {
          val pingPenalty = ((avgPing - 20f).coerceAtLeast(0f) * 0.35f)
          val jitterPenalty = (jitter * 1.4f)
          val lossPenalty = (packetLossPct * 2.5f)
          (100f - pingPenalty - jitterPenalty - lossPenalty).roundToInt().coerceIn(5, 100)
        }

      NetworkDiagnosticResult(
        host = host.trim().ifBlank { "1.1.1.1" },
        pingMs = avgPing,
        jitterMs = jitter,
        packetLossPercent = packetLossPct,
        downloadMbps = downloadMbps,
        stabilityScore = stabilityScore,
        samplesMs = samples,
        networkType = networkType,
      )
    }

  private fun measureDownloadMbps(onFraction: (Float) -> Unit): Float {
    val testUrls =
      listOf(
        "https://speed.cloudflare.com/__down?bytes=1500000",
        "https://proof.ovh.net/files/1Mb.dat",
      )
    for (urlStr in testUrls) {
      var conn: HttpURLConnection? = null
      try {
        val url = URL(urlStr)
        conn = (url.openConnection() as HttpURLConnection).apply {
          connectTimeout = 3500
          readTimeout = 5000
          requestMethod = "GET"
          setRequestProperty("User-Agent", "DRXGameTurboPro/1.0")
        }
        conn.connect()
        if (conn.responseCode in 200..299) {
          val input: InputStream = conn.inputStream
          val buffer = ByteArray(16 * 1024)
          var totalBytes = 0L
          val startNs = System.nanoTime()
          val maxDurationNs = 3_500_000_000L
          while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            totalBytes += read
            val elapsedNs = System.nanoTime() - startNs
            onFraction((elapsedNs.toFloat() / maxDurationNs).coerceIn(0f, 1f))
            if (elapsedNs > maxDurationNs) break
          }
          input.close()
          val elapsedSec = ((System.nanoTime() - startNs) / 1_000_000_000.0).coerceAtLeast(0.05)
          if (totalBytes > 0) {
            val bits = totalBytes * 8.0
            return (bits / elapsedSec / 1_000_000.0).toFloat()
          }
        }
      } catch (_: Throwable) {
        // Try next endpoint
      } finally {
        conn?.disconnect()
      }
    }
    return 0f
  }
}
