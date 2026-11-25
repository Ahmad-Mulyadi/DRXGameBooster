package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.i18n.LocalStrings
import com.example.ui.theme.DrxAnimatedGifLogo
import com.example.ui.theme.DrxCustomProgressBar
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.LocalDrxColors
import com.example.ui.theme.rememberDrxAnimationsActive

@Composable
fun DrxBoostAnimationOverlay(
  stageIndex: Int,
  isReadyToPlay: Boolean,
  animationsEnabled: Boolean,
  isIndo: Boolean,
  onSkip: () -> Unit,
) {
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val motion = rememberDrxAnimationsActive(animationsEnabled)

  val infiniteTransition = rememberInfiniteTransition(label = "drx_boost_anim")
  val orbitAngle by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec = infiniteRepeatable(animation = tween(1600, easing = LinearEasing)),
      label = "orbit_angle",
    )
  val pulseScale by
    infiniteTransition.animateFloat(
      initialValue = 0.96f,
      targetValue = 1.06f,
      animationSpec = infiniteRepeatable(
        animation = tween(720, easing = LinearEasing),
        repeatMode = RepeatMode.Reverse,
      ),
      label = "pulse_scale",
    )
  val sweepFraction by
    infiniteTransition.animateFloat(
      initialValue = -0.35f,
      targetValue = 1.35f,
      animationSpec = infiniteRepeatable(animation = tween(1150, easing = LinearEasing)),
      label = "light_sweep",
    )

  val activeAngle = if (motion) orbitAngle else 45f
  val activeScale = if (motion) pulseScale else 1f
  val activeSweep = if (motion) sweepFraction else 0.5f

  val stageLabel =
    when {
      isReadyToPlay -> strings.readyToPlay
      stageIndex == 0 ->
        if (isIndo) "Membersihkan cache internal DRX..." else "Trimming DRX internal cache..."
      stageIndex == 1 ->
        if (isIndo) "Menerapkan profil & pengaturan performa..." else "Applying selected DRX performance profile..."
      else ->
        if (isIndo) "Memverifikasi telemetri & kesiapan memori..." else "Refreshing live telemetry & RAM headroom..."
    }
  val progress =
    when {
      isReadyToPlay -> 1f
      stageIndex <= 0 -> 0.32f
      stageIndex == 1 -> 0.68f
      else -> 0.92f
    }

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            listOf(
              drxColors.secondaryBg.copy(alpha = 0.97f),
              Color(0xFF050609).copy(alpha = 0.98f),
            )
          )
        )
        .systemBarsPadding()
        .testTag("drx_boost_animation_overlay"),
  ) {
    // Subtle Electric Blue radial sweep backdrop
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = Offset(size.width / 2f, size.height * 0.44f)
      drawCircle(
        brush =
          Brush.radialGradient(
            colors = listOf(
              drxColors.electricBlue.copy(alpha = 0.24f),
              drxColors.neonCyan.copy(alpha = 0.08f),
              Color.Transparent,
            ),
            center = center,
            radius = size.minDimension * 0.55f,
          ),
        radius = size.minDimension * 0.55f,
        center = center,
      )
      val sweepX = size.width * activeSweep
      drawRect(
        brush =
          Brush.linearGradient(
            colors = listOf(
              Color.Transparent,
              drxColors.electricBlue.copy(alpha = 0.10f),
              drxColors.neonCyan.copy(alpha = 0.14f),
              Color.Transparent,
            ),
            start = Offset(sweepX - 180f, 0f),
            end = Offset(sweepX + 180f, size.height),
          )
      )
    }

    // Skip button top-right
    Surface(
      shape = RoundedCornerShape(50),
      color = drxColors.cardSurface.copy(alpha = 0.85f),
      border = BorderStroke(1.dp, drxColors.glassBorder),
      modifier =
        Modifier
          .align(Alignment.TopEnd)
          .padding(16.dp)
          .minimumInteractiveComponentSize()
          .clip(RoundedCornerShape(50))
          .clickable(onClick = onSkip)
          .testTag("drx_boost_skip_button"),
    ) {
      Text(
        text = if (isReadyToPlay) strings.closeLabel else strings.skipAnimation,
        style = MaterialTheme.typography.labelLarge,
        color = drxColors.silver,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
      )
    }

    Column(
      modifier =
        Modifier
          .align(Alignment.Center)
          .widthIn(max = 360.dp)
          .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(188.dp),
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val strokeW = 3.5.dp.toPx()
          val inset = 12.dp.toPx()
          val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
          drawArc(
            color = drxColors.glassBorder,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = strokeW),
          )
          drawArc(
            brush = Brush.sweepGradient(listOf(drxColors.electricBlue, drxColors.neonCyan, drxColors.electricBlue)),
            startAngle = activeAngle,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = strokeW * 1.4f, cap = StrokeCap.Round),
          )
          drawArc(
            color = drxColors.neonCyan.copy(alpha = 0.72f),
            startAngle = -activeAngle * 0.8f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = Offset(inset + 14.dp.toPx(), inset + 14.dp.toPx()),
            size = Size(arcSize.width - 28.dp.toPx(), arcSize.height - 28.dp.toPx()),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
          )
        }

        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.size(132.dp).scale(activeScale),
        ) {
          DrxAnimatedGifLogo(
            animationsEnabled = motion,
            contentDescription = strings.appName,
            modifier = Modifier.fillMaxSize().clip(CircleShape),
          )
        }
      }

      Spacer(Modifier.height(22.dp))

      DrxStatusPill(
        text =
          if (isReadyToPlay) strings.readyToPlay
          else if (isIndo) "DRX BOOST AKTIF" else "DRX BOOST ENGINE",
        color = if (isReadyToPlay) drxColors.optimalGreen else drxColors.neonCyan,
      )

      Spacer(Modifier.height(12.dp))

      Text(
        text = stageLabel,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("drx_boost_stage_label"),
      )

      Spacer(Modifier.height(16.dp))

      DrxCustomProgressBar(
        progress = progress,
        height = 7.dp,
        startColor = if (isReadyToPlay) drxColors.optimalGreen else drxColors.electricBlue,
        endColor = if (isReadyToPlay) drxColors.optimalGreen else drxColors.neonCyan,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}
