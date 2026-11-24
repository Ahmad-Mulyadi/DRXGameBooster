package com.example.system

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlin.math.roundToInt

object DrxImageUtils {

  /**
   * Decodes a local image file safely off the UI thread with downsampling so large camera/gallery
   * photos never trigger OutOfMemoryError or "Canvas: trying to draw too large bitmap" crashes.
   */
  fun decodeSampledImageBitmap(
    path: String,
    maxWidthPx: Int = 1080,
    maxHeightPx: Int = 1920,
  ): ImageBitmap? {
    if (path.isBlank()) return null
    return try {
      val file = File(path)
      if (!file.exists() || !file.canRead() || file.length() < 12L) return null

      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(path, bounds)
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

      var sampleSize = 1
      val targetW = maxWidthPx.coerceAtLeast(64)
      val targetH = maxHeightPx.coerceAtLeast(64)
      while ((bounds.outWidth / sampleSize) > targetW * 2 || (bounds.outHeight / sampleSize) > targetH * 2) {
        sampleSize *= 2
      }

      val decodeOpts =
        BitmapFactory.Options().apply {
          inSampleSize = sampleSize
          inPreferredConfig = Bitmap.Config.ARGB_8888
        }
      val bmp = BitmapFactory.decodeFile(path, decodeOpts) ?: return null
      if (bmp.width <= 0 || bmp.height <= 0) {
        bmp.recycle()
        return null
      }
      bmp.asImageBitmap()
    } catch (_: Throwable) {
      null
    }
  }
}

fun Float.sanitizeFinite(fallback: Float = 0f): Float =
  if (isFinite()) this else fallback

fun Float.safeRoundToInt(fallback: Int = 0): Int =
  if (isFinite()) roundToInt() else fallback
