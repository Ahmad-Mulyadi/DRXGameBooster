package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.i18n.LocalStrings
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CinematicBlack
import com.example.ui.theme.CinematicElectricBlue
import com.example.ui.theme.CinematicNeonCyan
import com.example.ui.theme.DrxAnimatedGifLogo
import com.example.ui.theme.GraphiteSecondary
import com.example.ui.theme.SilverChrome
import com.example.ui.theme.TextMain
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.rememberDrxAnimationsActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun DrxCinematicIntroOverlay(
  animationsEnabled: Boolean,
  isLowRamDevice: Boolean,
  onSkip: () -> Unit,
  onFinished: () -> Unit,
) {
  val strings = LocalStrings.current
  val motionActive = rememberDrxAnimationsActive(animationsEnabled) && !isLowRamDevice
  val totalDurationMs = if (motionActive) 2750 else 140
  val timeline = remember { Animatable(0f) }
  var completed by remember { mutableStateOf(false) }

  LaunchedEffect(motionActive) {
    timeline.snapTo(0f)
    timeline.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = totalDurationMs, easing = LinearEasing),
    )
    if (!completed) {
      completed = true
      if (motionActive) delay(60L)
      onFinished()
    }
  }

  val t = timeline.value

  // 6-scene choreographed envelope
  // Scene 1 (0.00..0.16): Deep black canvas + distant ambient electric blue glow
  val ambientGlow =
    if (!motionActive) 0.35f
    else (t / 0.25f).coerceIn(0f, 1f) * (1f - ((t - 0.84f) / 0.16f).coerceIn(0f, 1f))

  // Scene 2 (0.12..0.40): Crisp electric blue horizontal vector lines & cyan sweep
  val lineProgress = if (!motionActive) 0f else ((t - 0.10f) / 0.22f).coerceIn(0f, 1f)
  val lineAlpha =
    if (!motionActive || t > 0.48f) 0f
    else if (t > 0.30f) (1f - (t - 0.30f) / 0.18f).coerceIn(0f, 1f)
    else lineProgress

  // Scene 3 (0.20..0.62): Authentic DRX logo reveal with metallic silver shimmer & subtle light particles
  val logoAlpha = if (!motionActive) 1f else ((t - 0.18f) / 0.20f).coerceIn(0f, 1f)
  val logoScale =
    if (!motionActive) 1f
    else 0.84f + 0.16f * FastOutSlowInEasing.transform(((t - 0.18f) / 0.28f).coerceIn(0f, 1f))
  val shimmerPos = if (!motionActive) -1f else ((t - 0.30f) / 0.30f).coerceIn(-0.25f, 1.35f)

  // Scene 4 (0.36..0.78): Light-trail Energy Orbit following the DRX emblem
  val orbitAlpha =
    when {
      !motionActive -> 0f
      t < 0.34f -> 0f
      t < 0.46f -> (t - 0.34f) / 0.12f
      t > 0.78f -> (1f - (t - 0.78f) / 0.14f).coerceIn(0f, 1f)
      else -> 1f
    }

  // Scene 5 (0.52..0.88): Brand title & tagline smooth upward slide + fade
  val titleAlpha = if (!motionActive) 1f else ((t - 0.50f) / 0.18f).coerceIn(0f, 1f)
  val titleOffsetY =
    if (!motionActive) 0f
    else (1f - FastOutSlowInEasing.transform(((t - 0.50f) / 0.20f).coerceIn(0f, 1f))) * 18f

  // Scene 6 (0.84..1.00): Smooth glow fade & seamless transition into Dashboard
  val exitFadeAlpha =
    if (!motionActive) 1f
    else if (t > 0.84f) (1f - (t - 0.84f) / 0.16f).coerceIn(0f, 1f) else 1f

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .alpha(exitFadeAlpha)
        .background(CinematicBlack)
        .systemBarsPadding()
        .testTag("drx_cinematic_intro_overlay"),
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val cx = size.width / 2f
      val cy = size.height / 2f - 36.dp.toPx()

      // Scene 1: Distant focal ambient electric blue glow
      val glowRadius = 175.dp.toPx() * (0.75f + 0.25f * logoScale)
      drawCircle(
        brush =
          Brush.radialGradient(
            colors =
              listOf(
                CinematicElectricBlue.copy(alpha = 0.22f * ambientGlow),
                CinematicNeonCyan.copy(alpha = 0.08f * ambientGlow),
                Color.Transparent,
              ),
            center = Offset(cx, cy),
            radius = glowRadius,
          ),
        center = Offset(cx, cy),
        radius = glowRadius,
      )

      // Scene 2: Electric blue horizon lines + cyan energy sweep
      if (lineAlpha > 0.01f) {
        val halfW = size.width * 0.40f * lineProgress
        drawLine(
          brush =
            Brush.horizontalGradient(
              colors =
                listOf(
                  Color.Transparent,
                  CinematicElectricBlue,
                  CinematicNeonCyan,
                  CinematicElectricBlue,
                  Color.Transparent,
                ),
              startX = cx - halfW,
              endX = cx + halfW,
            ),
          start = Offset(cx - halfW, cy),
          end = Offset(cx + halfW, cy),
          strokeWidth = 2.2.dp.toPx(),
          alpha = lineAlpha,
        )
      }

      // Scene 4: Light-trail Energy Orbit & subtle micro-particles around emblem
      if (orbitAlpha > 0.01f) {
        val orbitRadius = 96.dp.toPx()
        drawCircle(
          color = CinematicElectricBlue.copy(alpha = 0.16f * orbitAlpha),
          radius = orbitRadius,
          center = Offset(cx, cy),
          style = Stroke(width = 1.2.dp.toPx()),
        )

        val sweepStart = t * 440f - 90f
        drawArc(
          brush =
            Brush.sweepGradient(
              colors =
                listOf(
                  Color.Transparent,
                  CinematicElectricBlue,
                  CinematicNeonCyan,
                  SilverChrome,
                ),
              center = Offset(cx, cy),
            ),
          startAngle = sweepStart,
          sweepAngle = 128f,
          useCenter = false,
          topLeft = Offset(cx - orbitRadius, cy - orbitRadius),
          size = Size(orbitRadius * 2f, orbitRadius * 2f),
          style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
          alpha = orbitAlpha,
        )

        val outerRadius = orbitRadius + 12.dp.toPx()
        drawArc(
          color = CinematicNeonCyan.copy(alpha = 0.48f * orbitAlpha),
          startAngle = -t * 320f,
          sweepAngle = 56f,
          useCenter = false,
          topLeft = Offset(cx - outerRadius, cy - outerRadius),
          size = Size(outerRadius * 2f, outerRadius * 2f),
          style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round),
        )

        // Subtle metallic & cyan micro-particles
        val particleCount = 6
        for (i in 0 until particleCount) {
          val rad = ((i * (360f / particleCount)) + t * 150f) * (PI / 180.0)
          val dist = outerRadius + 8.dp.toPx() + sin(t * 9.0 + i) * 4.dp.toPx()
          val px = cx + (cos(rad) * dist).toFloat()
          val py = cy + (sin(rad) * dist).toFloat()
          drawCircle(
            color = if (i % 2 == 0) CinematicNeonCyan else SilverChrome,
            radius = 1.7.dp.toPx(),
            center = Offset(px, py),
            alpha = 0.68f * orbitAlpha,
          )
        }
      }
    }

    // Top-right Skip Pill (48dp+ accessible target)
    Surface(
      modifier =
        Modifier
          .align(Alignment.TopEnd)
          .padding(16.dp)
          .minimumInteractiveComponentSize()
          .clip(RoundedCornerShape(50))
          .clickable {
            if (!completed) {
              completed = true
              onSkip()
            }
          }
          .testTag("skip_intro_animation_button"),
      shape = RoundedCornerShape(50),
      color = GraphiteSecondary.copy(alpha = 0.92f),
      border = BorderStroke(1.dp, BorderHairline),
    ) {
      Text(
        text = strings.skipAnimation,
        style = MaterialTheme.typography.labelLarge,
        color = SilverChrome,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
      )
    }

    // Center Authentic DRX Emblem + Shimmer + Brand Title & Tagline
    Column(
      modifier =
        Modifier
          .align(Alignment.Center)
          .padding(horizontal = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Box(
        modifier =
          Modifier
            .size(196.dp)
            .scale(logoScale)
            .alpha(if (motionActive) 1f else logoAlpha),
        contentAlignment = Alignment.Center,
      ) {
        DrxAnimatedGifLogo(
          animationsEnabled = motionActive,
          contentDescription = "DRX GAME TURBO PRO Logo",
          modifier = Modifier.fillMaxSize(),
        )
      }

      Spacer(Modifier.height(34.dp))

      Column(
        modifier =
          Modifier
            .alpha(titleAlpha)
            .offset { IntOffset(0, titleOffsetY.dp.toPx().roundToInt()) },
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = strings.appName,
          style =
            MaterialTheme.typography.headlineMedium.copy(
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.6.sp,
            ),
          color = TextMain,
          textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
          text = strings.introTagline,
          style =
            MaterialTheme.typography.labelLarge.copy(
              letterSpacing = 1.2.sp,
              fontWeight = FontWeight.SemiBold,
            ),
          color = CinematicNeonCyan,
          textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text = strings.developerBy,
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondary,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}
