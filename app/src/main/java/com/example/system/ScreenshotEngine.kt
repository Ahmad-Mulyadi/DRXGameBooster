package com.example.system

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.core.graphics.createBitmap
import com.example.data.model.MediaGalleryItem
import com.example.data.model.ScreenshotFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ScreenshotEngine {

  private val sequenceCounter = AtomicInteger(0)

  fun buildUniqueScreenshotFilename(
    timestampMs: Long = System.currentTimeMillis(),
    format: ScreenshotFormat = ScreenshotFormat.PNG,
  ): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(timestampMs))
    val seq = (sequenceCounter.incrementAndGet() % 1000).toString().padStart(3, '0')
    return "DRX_Screenshot_${stamp}_$seq.${format.ext}"
  }

  /**
   * Inspects pixel samples across the bitmap to guarantee we never save a blank, transparent,
   * or uninitialized black frame from VirtualDisplay warm-up or FLAG_SECURE surfaces.
   */
  fun isBlankOrBlackBitmap(bitmap: Bitmap): Boolean {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return true

    val cols = 20
    val rows = 20
    var opaqueSamples = 0
    var nonBlackSamples = 0

    for (r in 1..rows) {
      val y = ((h * r) / (rows + 1)).coerceIn(0, h - 1)
      for (c in 1..cols) {
        val x = ((w * c) / (cols + 1)).coerceIn(0, w - 1)
        val px = bitmap.getPixel(x, y)
        val alpha = Color.alpha(px)
        if (alpha > 8) {
          opaqueSamples++
          val red = Color.red(px)
          val green = Color.green(px)
          val blue = Color.blue(px)
          if (red > 4 || green > 4 || blue > 4) {
            nonBlackSamples++
            if (nonBlackSamples >= 2) return false
          }
        }
      }
    }
    return opaqueSamples == 0 || nonBlackSamples == 0
  }

  /**
   * Mode 1: Captures the active DRX Application Window directly without requiring MediaProjection.
   * Uses PixelCopy on API 26+ (accurate hardware-accelerated Compose/Surface capture) with a
   * View Canvas software fallback.
   */
  fun captureAppWindow(
    activity: Activity?,
    format: ScreenshotFormat,
    safTreeUri: String,
    scope: CoroutineScope,
    onResult: (MediaGalleryItem?, String?) -> Unit,
  ) {
    if (activity == null || activity.isFinishing || activity.isDestroyed) {
      onResult(null, "Active DRX window unavailable for capture.")
      return
    }
    val window = activity.window
    val decorView = window?.decorView
    if (window == null || decorView == null || decorView.width <= 0 || decorView.height <= 0) {
      onResult(null, "Window layout not ready yet.")
      return
    }

    val width = decorView.width
    val height = decorView.height
    val bitmap =
      runCatching { createBitmap(width, height, Bitmap.Config.ARGB_8888) }.getOrNull()
        ?: run {
          onResult(null, "Unable to allocate screenshot bitmap.")
          return
        }

    fun persistBitmap(bmp: Bitmap) {
      scope.launch(Dispatchers.IO) {
        val result: Pair<MediaGalleryItem?, String?> =
          try {
            if (isBlankOrBlackBitmap(bmp)) {
              null to "Captured frame appeared blank or blocked (FLAG_SECURE)."
            } else {
              val fileName = buildUniqueScreenshotFilename(format = format)
              val saved =
                DrxMediaStorageManager.saveBitmapScreenshot(
                  context = activity.applicationContext,
                  bitmap = bmp,
                  fileName = fileName,
                  safTreeUri = safTreeUri,
                  format = format,
                )
              if (saved != null) {
                saved to null
              } else {
                null to "Failed to write screenshot to MediaStore."
              }
            }
          } catch (t: Throwable) {
            null to (t.message ?: "Screenshot save failed.")
          } finally {
            runCatching { bmp.recycle() }
          }
        withContext(Dispatchers.Main) {
          val (savedItem, err) = result
          ScreenRecorderStateHolder.setScreenshotCaptured(
            item = savedItem,
            status = savedItem?.let { "Screenshot saved: ${it.displayName}" },
            error = err,
          )
          onResult(savedItem, err)
        }
      }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      try {
        PixelCopy.request(
          window,
          bitmap,
          { copyResult ->
            if (copyResult == PixelCopy.SUCCESS && !isBlankOrBlackBitmap(bitmap)) {
              persistBitmap(bitmap)
            } else {
              // Fallback to software view draw if hardware PixelCopy returned black/error
              val canvasOk =
                runCatching {
                  val canvas = Canvas(bitmap)
                  decorView.draw(canvas)
                  !isBlankOrBlackBitmap(bitmap)
                }.getOrDefault(false)
              if (canvasOk) {
                persistBitmap(bitmap)
              } else {
                runCatching { bitmap.recycle() }
                onResult(null, "App window capture blocked or yielded blank frame.")
              }
            }
          },
          Handler(Looper.getMainLooper()),
        )
      } catch (_: Throwable) {
        val canvasOk =
          runCatching {
            val canvas = Canvas(bitmap)
            decorView.draw(canvas)
            !isBlankOrBlackBitmap(bitmap)
          }.getOrDefault(false)
        if (canvasOk) {
          persistBitmap(bitmap)
        } else {
          runCatching { bitmap.recycle() }
          onResult(null, "Unable to capture DRX window.")
        }
      }
    } else {
      val canvasOk =
        runCatching {
          val canvas = Canvas(bitmap)
          decorView.draw(canvas)
          !isBlankOrBlackBitmap(bitmap)
        }.getOrDefault(false)
      if (canvasOk) {
        persistBitmap(bitmap)
      } else {
        runCatching { bitmap.recycle() }
        onResult(null, "Unable to capture DRX window.")
      }
    }
  }
}
