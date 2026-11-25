package com.example.ui.theme

import android.graphics.Canvas as AndroidCanvas
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.RawRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import kotlinx.coroutines.delay

/**
 * Renders the authentic borderless DRX animated GIF reveal ([R.raw.drx_logo_reveal]) natively
 * using [AnimatedImageDrawable] on API 28+ and [Movie] on API 24-27, with deterministic fallback
 * to the borderless static emblem ([R.drawable.drx_logo_noborder]) when Reduce Motion is active
 * or when running in JVM/Robolectric/Preview environments.
 */
@Composable
fun DrxAnimatedGifLogo(
  animationsEnabled: Boolean,
  modifier: Modifier = Modifier,
  @RawRes gifResId: Int = R.raw.drx_logo_reveal,
  contentDescription: String = "DRX GAME TURBO PRO Logo",
) {
  val context = LocalContext.current
  val isInspection = LocalInspectionMode.current
  val motionActive = rememberDrxAnimationsActive(animationsEnabled) && !isInspection

  if (!motionActive) {
    Image(
      painter = painterResource(id = R.drawable.drx_logo_noborder),
      contentDescription = contentDescription,
      contentScale = ContentScale.Fit,
      modifier = modifier,
    )
    return
  }

  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    val animatedDrawable =
      remember(context, gifResId) {
        runCatching {
          val source = ImageDecoder.createSource(context.resources, gifResId)
          ImageDecoder.decodeDrawable(source) as? AnimatedImageDrawable
        }.getOrNull()
      }

    if (animatedDrawable != null) {
      DisposableEffect(animatedDrawable) {
        animatedDrawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        animatedDrawable.start()
        onDispose { runCatching { animatedDrawable.stop() } }
      }
      AndroidView(
        factory = { ctx ->
          ImageView(ctx).apply {
            layoutParams =
              ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
              )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setImageDrawable(animatedDrawable)
            importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
          }
        },
        update = { view ->
          if (view.drawable !== animatedDrawable) {
            view.setImageDrawable(animatedDrawable)
          }
          if (!animatedDrawable.isRunning) {
            animatedDrawable.start()
          }
        },
        modifier = modifier,
      )
      return
    }
  }

  @Suppress("DEPRECATION")
  val movie =
    remember(context, gifResId) {
      runCatching {
        context.resources.openRawResource(gifResId).use { Movie.decodeStream(it) }
      }.getOrNull()
    }

  if (movie != null && movie.duration() > 0) {
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(movie) {
      val start = SystemClock.uptimeMillis()
      while (true) {
        elapsedMs = (SystemClock.uptimeMillis() - start) % movie.duration().coerceAtLeast(1)
        delay(25L)
      }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val mw = movie.width().coerceAtLeast(1).toFloat()
        val mh = movie.height().coerceAtLeast(1).toFloat()
        val scale = minOf(size.width / mw, size.height / mh)
        val dx = (size.width - mw * scale) / 2f
        val dy = (size.height - mh * scale) / 2f
        movie.setTime(elapsedMs.toInt())
        drawIntoCanvas { canvas ->
          val native: AndroidCanvas = canvas.nativeCanvas
          native.save()
          native.translate(dx, dy)
          native.scale(scale, scale)
          movie.draw(native, 0f, 0f)
          native.restore()
        }
      }
    }
  } else {
    Image(
      painter = painterResource(id = R.drawable.drx_logo_noborder),
      contentDescription = contentDescription,
      contentScale = ContentScale.Fit,
      modifier = modifier,
    )
  }
}
