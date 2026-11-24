package com.example.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

object DrxMotionTokens {
  const val DURATION_FAST = 130
  const val DURATION_NORMAL = 240
  const val DURATION_CHART = 420
  const val STAGGER_STEP_MS = 38L
}

fun isSystemAnimatorDisabled(context: Context): Boolean {
  return runCatching {
    val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    scale <= 0f
  }.getOrDefault(false)
}

@Composable
fun rememberDrxAnimationsActive(userAnimationsEnabled: Boolean): Boolean {
  val context = LocalContext.current
  val osReduced = remember(context) { isSystemAnimatorDisabled(context) }
  return userAnimationsEnabled && !osReduced
}

@Composable
fun DrxStaggeredEntrance(
  index: Int = 0,
  animationsEnabled: Boolean = true,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val active = rememberDrxAnimationsActive(animationsEnabled)
  if (!active) {
    androidx.compose.foundation.layout.Box(modifier = modifier) { content() }
    return
  }

  var visible by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    val waitMs = (index.coerceIn(0, 8) * DrxMotionTokens.STAGGER_STEP_MS)
    if (waitMs > 0L) delay(waitMs)
    visible = true
  }

  AnimatedVisibility(
    visible = visible,
    enter =
      fadeIn(tween(DrxMotionTokens.DURATION_NORMAL, easing = FastOutSlowInEasing)) +
        slideInVertically(
          animationSpec =
            spring(
              dampingRatio = Spring.DampingRatioLowBouncy,
              stiffness = Spring.StiffnessMediumLow,
            ),
          initialOffsetY = { (it * 0.14f).toInt().coerceIn(8, 32) },
        ),
    exit = fadeOut(tween(120)),
    modifier = modifier,
  ) {
    content()
  }
}

fun Modifier.drxPressScale(
  interactionSource: InteractionSource,
  enabled: Boolean = true,
  animationsEnabled: Boolean = true,
  pressedScale: Float = 0.975f,
): Modifier = composed {
  val active = rememberDrxAnimationsActive(animationsEnabled)
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (enabled && active && isPressed) pressedScale else 1f,
      animationSpec =
        if (active) spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        else tween(0),
      label = "drx_press_scale",
    )
  this.graphicsLayer {
    scaleX = scale
    scaleY = scale
  }
}

@Composable
fun rememberDrxAnimatedFloat(
  targetValue: Float,
  animationsEnabled: Boolean = true,
  durationMs: Int = DrxMotionTokens.DURATION_CHART,
  label: String = "drx_float",
): Float {
  val active = rememberDrxAnimationsActive(animationsEnabled)
  val animated by
    animateFloatAsState(
      targetValue = targetValue,
      animationSpec = if (active) tween(durationMs, easing = FastOutSlowInEasing) else tween(0),
      label = label,
    )
  return animated
}

@Composable
fun rememberDrxAnimatedInt(
  targetValue: Int,
  animationsEnabled: Boolean = true,
  durationMs: Int = DrxMotionTokens.DURATION_NORMAL,
  label: String = "drx_int",
): Int {
  val active = rememberDrxAnimationsActive(animationsEnabled)
  val animated by
    animateIntAsState(
      targetValue = targetValue,
      animationSpec = if (active) tween(durationMs, easing = FastOutSlowInEasing) else tween(0),
      label = label,
    )
  return animated
}

/**
 * Only animates a subtle alert pulse when [isPulsing] is strictly true (e.g., active recording or
 * real Warning/Critical device condition). Never creates idle background animation loops.
 */
@Composable
fun rememberDrxConditionalPulseAlpha(
  isPulsing: Boolean,
  animationsEnabled: Boolean = true,
  minAlpha: Float = 0.48f,
  maxAlpha: Float = 1f,
  periodMs: Int = 900,
): Float {
  val active = rememberDrxAnimationsActive(animationsEnabled)
  if (!isPulsing || !active) return maxAlpha
  val transition = rememberInfiniteTransition(label = "drx_conditional_pulse")
  val alpha by
    transition.animateFloat(
      initialValue = minAlpha,
      targetValue = maxAlpha,
      animationSpec =
        infiniteRepeatable(
          animation = tween(periodMs, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "drx_pulse_alpha",
    )
  return alpha
}

@Composable
fun DrxAnimatedModalContainer(
  animationsEnabled: Boolean = true,
  content: @Composable () -> Unit,
) {
  val active = rememberDrxAnimationsActive(animationsEnabled)
  if (!active) {
    content()
    return
  }
  var entered by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { entered = true }
  AnimatedVisibility(
    visible = entered,
    enter =
      fadeIn(tween(180)) +
        scaleIn(
          initialScale = 0.92f,
          animationSpec =
            spring(
              dampingRatio = Spring.DampingRatioLowBouncy,
              stiffness = Spring.StiffnessMedium,
            ),
        ),
  ) {
    content()
  }
}
