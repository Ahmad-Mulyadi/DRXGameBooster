package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.db.GameEntity
import com.example.data.model.LaunchAnimationQuality
import com.example.i18n.LocalStrings
import com.example.system.LaunchAudioSynthesizer
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.CinematicBlack
import com.example.ui.theme.CinematicElectricBlue
import com.example.ui.theme.CinematicNeonCyan
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.ErrorCoral
import com.example.ui.theme.GraphiteSecondary
import com.example.ui.theme.SilverChrome
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

sealed interface GameLaunchState {
  data class Launching(
    val game: GameEntity,
    val prepProgress: Float,
    val prepStageIndex: Int,
  ) : GameLaunchState

  data class Failed(
    val game: GameEntity,
    val message: String,
  ) : GameLaunchState
}

@Composable
fun GameLaunchOverlay(
  state: GameLaunchState,
  quality: LaunchAnimationQuality,
  soundEnabled: Boolean,
  onSkip: () -> Unit,
  onAnimationCompleted: () -> Unit,
  onRetry: (GameEntity) -> Unit,
  onDismissError: () -> Unit,
) {
  BackHandler {
    when (state) {
      is GameLaunchState.Launching -> onSkip()
      is GameLaunchState.Failed -> onDismissError()
    }
  }

  val strings = LocalStrings.current
  val audioSynth = remember { LaunchAudioSynthesizer() }

  DisposableEffect(Unit) {
    onDispose { audioSynth.stop() }
  }

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(CinematicBlack)
        .systemBarsPadding()
        .testTag("game_launch_overlay"),
  ) {
    when (state) {
      is GameLaunchState.Launching -> {
        val totalDurationMs =
          when (quality) {
            LaunchAnimationQuality.HIGH -> 2500
            LaunchAnimationQuality.BALANCED -> 1950
            LaunchAnimationQuality.MINIMAL -> 1250
          }
        val timeline = remember { Animatable(0f) }
        var finishedTriggered by remember { mutableStateOf(false) }

        LaunchedEffect(state.game.packageName, quality) {
          if (soundEnabled) {
            audioSynth.playLaunchSequence(totalDurationMs / 1000f)
          }
          timeline.snapTo(0f)
          timeline.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = totalDurationMs, easing = LinearEasing),
          )
          if (!finishedTriggered) {
            finishedTriggered = true
            delay(120L)
            onAnimationCompleted()
          }
        }

        val t = timeline.value

        val lineReveal = (t / 0.18f).coerceIn(0f, 1f)
        val logoAlpha = ((t - 0.06f) / 0.24f).coerceIn(0f, 1f)
        val logoScale = 0.85f + 0.15f * FastOutSlowInEasing.transform(((t - 0.06f) / 0.30f).coerceIn(0f, 1f))
        val shimmerProgress = ((t - 0.14f) / 0.35f).coerceIn(-0.2f, 1.3f)

        val chargeProgress = ((t - 0.24f) / 0.50f).coerceIn(0f, 1f)
        val chargeRingAlpha =
          when {
            t < 0.22f -> 0f
            t < 0.30f -> (t - 0.22f) / 0.08f
            t > 0.78f -> (1f - ((t - 0.78f) / 0.16f)).coerceIn(0f, 1f)
            else -> 1f
          }
        val ringContractScale =
          if (t > 0.76f) 1f - 0.65f * ((t - 0.76f) / 0.18f).coerceIn(0f, 1f) else 1f
        val finalFlashAlpha =
          if (t in 0.76f..0.90f) sin(PI * ((t - 0.76f) / 0.14f)).toFloat() * 0.75f else 0f
        val revealTextAlpha = ((t - 0.76f) / 0.12f).coerceIn(0f, 1f)
        val blueSweepProgress = ((t - 0.88f) / 0.12f).coerceIn(0f, 1f)

        val stageText =
          when {
            t < 0.24f -> strings.boostStageEngine
            t < 0.48f -> strings.boostStageSession
            t < 0.72f -> strings.boostStageResources
            t < 0.88f -> strings.boostStageTools
            else -> strings.readyToPlay
          }

        val displayChargePct =
          ((chargeProgress * 0.7f + state.prepProgress * 0.3f) * 100f)
            .roundToInt()
            .coerceIn(0, 100)

        Canvas(modifier = Modifier.fillMaxSize()) {
          val cx = size.width / 2f
          val cy = size.height / 2f - 28.dp.toPx()

          if (lineReveal > 0f && t < 0.45f) {
            val lineFade = if (t > 0.28f) (1f - (t - 0.28f) / 0.17f).coerceIn(0f, 1f) else 1f
            val halfW = (size.width * 0.42f) * lineReveal
            drawLine(
              brush =
                Brush.horizontalGradient(
                  listOf(Color.Transparent, CinematicElectricBlue, CinematicNeonCyan, CinematicElectricBlue, Color.Transparent),
                  startX = cx - halfW,
                  endX = cx + halfW,
                ),
              start = Offset(cx - halfW, cy),
              end = Offset(cx + halfW, cy),
              strokeWidth = 2.dp.toPx(),
              alpha = lineFade,
            )
          }

          val glowRadius = (130.dp.toPx() + 36.dp.toPx() * chargeProgress) * logoScale
          drawCircle(
            brush =
              Brush.radialGradient(
                colors =
                  listOf(
                    CinematicElectricBlue.copy(alpha = (0.16f + 0.24f * chargeProgress) * logoAlpha),
                    CinematicNeonCyan.copy(alpha = 0.07f * chargeProgress),
                    Color.Transparent,
                  ),
                center = Offset(cx, cy),
                radius = glowRadius,
              ),
            center = Offset(cx, cy),
            radius = glowRadius,
          )

          if (chargeRingAlpha > 0.01f) {
            val ringRadius = 112.dp.toPx() * ringContractScale
            drawCircle(
              color = CinematicElectricBlue.copy(alpha = 0.18f * chargeRingAlpha),
              radius = ringRadius,
              center = Offset(cx, cy),
              style = Stroke(width = 1.5.dp.toPx()),
            )
            drawArc(
              brush =
                Brush.sweepGradient(
                  listOf(CinematicElectricBlue, CinematicNeonCyan, SilverChrome, CinematicElectricBlue),
                  center = Offset(cx, cy),
                ),
              startAngle = -90f,
              sweepAngle = 360f * chargeProgress,
              useCenter = false,
              topLeft = Offset(cx - ringRadius, cy - ringRadius),
              size = Size(ringRadius * 2f, ringRadius * 2f),
              style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
              alpha = chargeRingAlpha,
            )

            val orbitRadius = ringRadius + 11.dp.toPx()
            val orbitAngle = t * 520f
            drawArc(
              color = CinematicNeonCyan.copy(alpha = 0.55f * chargeRingAlpha),
              startAngle = orbitAngle,
              sweepAngle = 68f,
              useCenter = false,
              topLeft = Offset(cx - orbitRadius, cy - orbitRadius),
              size = Size(orbitRadius * 2f, orbitRadius * 2f),
              style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round),
            )

            val particleCount =
              when (quality) {
                LaunchAnimationQuality.HIGH -> 8
                LaunchAnimationQuality.BALANCED -> 4
                LaunchAnimationQuality.MINIMAL -> 0
              }
            for (i in 0 until particleCount) {
              val ang = ((i * (360f / particleCount)) + t * 180f) * (PI / 180.0)
              val pRadius = orbitRadius + 8.dp.toPx() + sin(t * 10.0 + i) * 5.dp.toPx()
              val px = cx + (cos(ang) * pRadius).toFloat()
              val py = cy + (sin(ang) * pRadius).toFloat()
              drawCircle(
                color = if (i % 2 == 0) CinematicNeonCyan else SilverChrome,
                radius = 1.8.dp.toPx(),
                center = Offset(px, py),
                alpha = 0.7f * chargeRingAlpha,
              )
            }
          }

          if (finalFlashAlpha > 0.01f) {
            drawCircle(
              brush =
                Brush.radialGradient(
                  listOf(CinematicNeonCyan.copy(alpha = finalFlashAlpha), Color.Transparent),
                  center = Offset(cx, cy),
                  radius = 160.dp.toPx(),
                ),
              center = Offset(cx, cy),
              radius = 160.dp.toPx(),
            )
          }

          if (blueSweepProgress > 0.01f) {
            val sweepX = size.width * blueSweepProgress
            drawRect(
              brush =
                Brush.horizontalGradient(
                  listOf(Color.Transparent, CinematicElectricBlue.copy(alpha = 0.45f), CinematicNeonCyan.copy(alpha = 0.65f), Color.Transparent),
                  startX = sweepX - 180f,
                  endX = sweepX + 180f,
                ),
              size = size,
            )
          }
        }

        // Skip Animation Pill (top-right, >=48dp touch target)
        Surface(
          modifier =
            Modifier
              .align(Alignment.TopEnd)
              .padding(16.dp)
              .minimumInteractiveComponentSize()
              .clip(RoundedCornerShape(50))
              .clickable {
                audioSynth.stop()
                onSkip()
              }
              .testTag("skip_launch_animation_button"),
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

        // Center DRX Monogram Logo + Metallic Silver Shimmer + Sequence Texts
        Column(
          modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(
            modifier =
              Modifier
                .size(144.dp)
                .scale(logoScale)
                .alpha(logoAlpha)
                .clip(RoundedCornerShape(30.dp))
                .border(1.dp, CinematicElectricBlue.copy(alpha = 0.35f), RoundedCornerShape(30.dp)),
            contentAlignment = Alignment.Center,
          ) {
            Image(
              painter = painterResource(id = R.drawable.drx_logo),
              contentDescription = "DRX Launch Monogram",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )

            if (shimmerProgress in -0.1f..1.25f && quality != LaunchAnimationQuality.MINIMAL) {
              Canvas(modifier = Modifier.fillMaxSize()) {
                val offsetX = size.width * shimmerProgress
                drawLine(
                  brush =
                    Brush.linearGradient(
                      colors =
                        listOf(
                          Color.Transparent,
                          SilverChrome.copy(alpha = 0.30f),
                          CinematicNeonCyan.copy(alpha = 0.24f),
                          Color.Transparent,
                        ),
                      start = Offset(offsetX - 50f, 0f),
                      end = Offset(offsetX + 50f, size.height),
                    ),
                  start = Offset(offsetX - 30f, -20f),
                  end = Offset(offsetX + 30f, size.height + 20f),
                  strokeWidth = 38.dp.toPx(),
                )
              }
            }
          }

          Spacer(Modifier.height(34.dp))

          Text(
            text = state.game.appName.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = SilverChrome.copy(alpha = logoAlpha),
          )

          Spacer(Modifier.height(10.dp))

          AnimatedContent(
            targetState = stageText,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
            label = "launch_stage_text",
          ) { text ->
            Text(
              text = text,
              style = MaterialTheme.typography.labelLarge,
              color = if (text == strings.readyToPlay) CinematicNeonCyan else TextSecondary,
            )
          }

          Spacer(Modifier.height(14.dp))

          LinearProgressIndicator(
            progress = { (displayChargePct / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.width(210.dp).height(3.5.dp).clip(RoundedCornerShape(50)),
            color = CinematicNeonCyan,
            trackColor = SurfaceElevated,
          )
          Spacer(Modifier.height(6.dp))
          Text(
            text = "$displayChargePct%",
            style = DrxTypographyTokens.numericReadout,
            color = CinematicElectricBlue,
          )

          Spacer(Modifier.height(14.dp))

          Column(
            modifier = Modifier.alpha(revealTextAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = strings.appName,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = SilverChrome,
            )
            Spacer(Modifier.height(2.dp))
            Text(
              text = strings.readyToPlay,
              style = MaterialTheme.typography.titleMedium,
              color = CinematicNeonCyan,
            )
          }
        }
      }

      is GameLaunchState.Failed -> {
        Column(
          modifier = Modifier.align(Alignment.Center).padding(28.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = strings.launchErrorTitle,
            tint = ErrorCoral,
            modifier = Modifier.size(52.dp),
          )
          Spacer(Modifier.height(14.dp))
          Text(
            text = strings.launchErrorTitle,
            style = MaterialTheme.typography.titleLarge,
            color = SilverChrome,
          )
          Spacer(Modifier.height(6.dp))
          Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
          Spacer(Modifier.height(20.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            DrxSecondaryButton(
              label = strings.closeLabel,
              onClick = onDismissError,
              modifier = Modifier.weight(1f),
            )
            DrxPrimaryButton(
              label = strings.retryLaunch,
              onClick = { onRetry(state.game) },
              accentColor = CinematicElectricBlue,
              modifier = Modifier.weight(1f).testTag("retry_launch_button"),
            )
          }
        }
      }
    }
  }
}
