package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R

@Composable
fun DrxGlassCard(
  modifier: Modifier = Modifier,
  accentColor: Color = LocalDrxColors.current.electricBlue,
  highlighted: Boolean = false,
  cornerRadius: Dp = 22.dp,
  contentPadding: PaddingValues = PaddingValues(16.dp),
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val drxColors = LocalDrxColors.current
  val shape = RoundedCornerShape(cornerRadius)
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (onClick != null && isPressed) 0.985f else 1f,
      animationSpec = tween(120),
      label = "card_press",
    )

  val borderColor =
    if (highlighted) {
      accentColor.copy(alpha = 0.44f)
    } else {
      drxColors.glassBorder
    }

  val containerColor =
    if (highlighted && drxColors.isDark) drxColors.elevatedSurface else drxColors.cardSurface

  val interactiveModifier =
    if (onClick != null) {
      modifier
        .scale(scale)
        .clip(shape)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    } else {
      modifier
    }

  Surface(
    modifier = interactiveModifier,
    shape = shape,
    color = containerColor,
    border = BorderStroke(1.dp, borderColor),
    tonalElevation = 0.dp,
  ) {
    Column(modifier = Modifier.padding(contentPadding), content = content)
  }
}

@Composable
fun DrxGroupedCard(
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 22.dp,
  content: @Composable ColumnScope.() -> Unit,
) {
  val drxColors = LocalDrxColors.current
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(cornerRadius),
    color = drxColors.cardSurface,
    border = BorderStroke(1.dp, drxColors.glassBorder),
    tonalElevation = 0.dp,
  ) {
    Column(content = content)
  }
}

@Composable
fun DrxHairlineDivider(modifier: Modifier = Modifier) {
  val drxColors = LocalDrxColors.current
  HorizontalDivider(
    modifier = modifier,
    thickness = 1.dp,
    color = drxColors.glassBorder,
  )
}

@Composable
fun DrxScreenHeader(
  title: String,
  subtitle: String? = null,
  onBack: (() -> Unit)? = null,
  trailing: @Composable (() -> Unit)? = null,
) {
  val drxColors = LocalDrxColors.current
  Row(
    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (onBack != null) {
      Surface(
        onClick = onBack,
        shape = CircleShape,
        color = drxColors.cardSurface,
        border = BorderStroke(1.dp, drxColors.glassBorder),
        modifier = Modifier.size(44.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp),
          )
        }
      }
      Spacer(Modifier.width(12.dp))
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (!subtitle.isNullOrBlank()) {
        Spacer(Modifier.height(2.dp))
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    if (trailing != null) {
      Spacer(Modifier.width(8.dp))
      trailing()
    }
  }
}

@Composable
fun DrxSectionHeader(
  title: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val drxColors = LocalDrxColors.current
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
    )
    if (actionLabel != null && onAction != null) {
      Text(
        text = actionLabel,
        style = MaterialTheme.typography.labelLarge,
        color = drxColors.neonCyan,
        modifier =
          Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onAction)
            .padding(horizontal = 10.dp, vertical = 6.dp),
      )
    }
  }
}

@Composable
fun DrxLogoBadge(size: Dp = 40.dp, modifier: Modifier = Modifier) {
  val drxColors = LocalDrxColors.current
  Image(
    painter = painterResource(id = R.drawable.drx_logo),
    contentDescription = "DRX GAME TURBO PRO Logo",
    contentScale = ContentScale.Crop,
    modifier =
      modifier
        .size(size)
        .clip(RoundedCornerShape(13.dp))
        .border(1.dp, drxColors.electricBlue.copy(alpha = 0.28f), RoundedCornerShape(13.dp)),
  )
}

@Composable
fun DrxBoostNowButton(
  label: String,
  isBoosting: Boolean,
  animationsEnabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val drxColors = LocalDrxColors.current
  val haptic = LocalHapticFeedback.current
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (isPressed && animationsEnabled) 0.972f else 1f,
      animationSpec = tween(120),
      label = "boost_scale",
    )

  val shape = RoundedCornerShape(50)
  val glowColor = drxColors.electricBlue

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(54.dp)
        .scale(scale)
        .drawBehind {
          drawRoundRect(
            brush =
              Brush.radialGradient(
                colors =
                  listOf(
                    glowColor.copy(alpha = if (isPressed) 0.28f else 0.18f),
                    Color.Transparent,
                  ),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = size.width * 0.56f,
              ),
            cornerRadius = CornerRadius(27.dp.toPx(), 27.dp.toPx()),
          )
        }
        .clip(shape)
        .background(
          Brush.horizontalGradient(
            colors = listOf(drxColors.electricBlueDeep, drxColors.electricBlue)
          )
        )
        .border(1.dp, drxColors.neonCyan.copy(alpha = 0.34f), shape)
        .clickable(
          interactionSource = interactionSource,
          indication = null,
          enabled = !isBoosting,
        ) {
          haptic.performHapticFeedback(HapticFeedbackType.LongPress)
          onClick()
        },
    contentAlignment = Alignment.Center,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (isBoosting) {
        CircularProgressIndicator(
          modifier = Modifier.size(18.dp),
          strokeWidth = 2.dp,
          color = Color.White,
        )
      } else {
        Icon(
          imageVector = Icons.Default.Bolt,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(20.dp),
        )
      }
      Spacer(Modifier.width(10.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
        color = Color.White,
      )
    }
  }
}

@Composable
fun DrxPrimaryButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: ImageVector? = null,
  accentColor: Color = LocalDrxColors.current.electricBlue,
) {
  val drxColors = LocalDrxColors.current
  val haptic = LocalHapticFeedback.current
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (isPressed && enabled) 0.976f else 1f,
      animationSpec = tween(110),
      label = "btn_scale",
    )
  val shape = RoundedCornerShape(50)

  Box(
    modifier =
      modifier
        .height(48.dp)
        .scale(scale)
        .clip(shape)
        .background(if (enabled) accentColor else drxColors.elevatedSurface)
        .border(
          1.dp,
          if (enabled) Color.White.copy(alpha = 0.16f) else drxColors.glassBorder,
          shape,
        )
        .clickable(
          interactionSource = interactionSource,
          indication = null,
          enabled = enabled,
        ) {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onClick()
        },
    contentAlignment = Alignment.Center,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun DrxSecondaryButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: ImageVector? = null,
) {
  val drxColors = LocalDrxColors.current
  val shape = RoundedCornerShape(50)
  val interactionSource = remember { MutableInteractionSource() }
  Surface(
    onClick = onClick,
    enabled = enabled,
    interactionSource = interactionSource,
    shape = shape,
    color = if (enabled) drxColors.elevatedSurface else drxColors.cardSurface,
    border = BorderStroke(1.dp, drxColors.glassBorder),
    modifier = modifier.height(48.dp).drxPressScale(interactionSource, enabled = enabled),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (enabled) MaterialTheme.colorScheme.onBackground else drxColors.mutedText,
          modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(6.dp))
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) MaterialTheme.colorScheme.onBackground else drxColors.mutedText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun DrxDangerButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: ImageVector? = null,
) {
  val drxColors = LocalDrxColors.current
  val shape = RoundedCornerShape(50)
  val interactionSource = remember { MutableInteractionSource() }
  Surface(
    onClick = onClick,
    enabled = enabled,
    interactionSource = interactionSource,
    shape = shape,
    color = if (enabled) drxColors.alertCrimson.copy(alpha = 0.16f) else drxColors.cardSurface,
    border = BorderStroke(1.dp, if (enabled) drxColors.alertCrimson.copy(alpha = 0.45f) else drxColors.glassBorder),
    modifier = modifier.height(48.dp).drxPressScale(interactionSource, enabled = enabled),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (enabled) drxColors.alertCrimson else drxColors.mutedText,
          modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(6.dp))
      }
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) drxColors.alertCrimson else drxColors.mutedText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun DrxCustomSwitch(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val drxColors = LocalDrxColors.current
  val haptic = LocalHapticFeedback.current
  val thumbOffset by
    animateDpAsState(
      targetValue = if (checked) 22.dp else 3.dp,
      animationSpec = spring(stiffness = Spring.StiffnessMedium),
      label = "switch_thumb",
    )
  val trackColor by
    animateColorAsState(
      targetValue =
        if (!enabled) drxColors.elevatedSurface.copy(alpha = 0.5f)
        else if (checked) drxColors.electricBlue
        else drxColors.elevatedSurface,
      animationSpec = tween(160),
      label = "switch_track",
    )

  Box(
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .width(48.dp)
        .height(28.dp)
        .clip(RoundedCornerShape(50))
        .background(trackColor)
        .border(
          1.dp,
          if (checked && enabled) drxColors.neonCyan.copy(alpha = 0.42f) else drxColors.glassBorder,
          RoundedCornerShape(50),
        )
        .clickable(enabled = enabled, role = Role.Switch) {
          haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
          onCheckedChange(!checked)
        },
    contentAlignment = Alignment.CenterStart,
  ) {
    Box(
      modifier =
        Modifier
          .offset(x = thumbOffset)
          .size(22.dp)
          .clip(CircleShape)
          .background(if (enabled) Color.White else drxColors.mutedText)
    )
  }
}

@Composable
fun DrxCustomSlider(
  value: Float,
  onValueChange: (Float) -> Unit,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val drxColors = LocalDrxColors.current
  Slider(
    value = value,
    onValueChange = onValueChange,
    valueRange = valueRange,
    enabled = enabled,
    colors =
      SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = drxColors.electricBlue,
        inactiveTrackColor = drxColors.elevatedSurface,
        disabledThumbColor = drxColors.mutedText,
        disabledActiveTrackColor = drxColors.glassBorder,
      ),
    modifier = modifier,
  )
}

@Composable
fun DrxCustomProgressBar(
  progress: Float,
  modifier: Modifier = Modifier,
  startColor: Color = LocalDrxColors.current.electricBlue,
  endColor: Color = LocalDrxColors.current.neonCyan,
  height: Dp = 5.dp,
) {
  val drxColors = LocalDrxColors.current
  val animStart by animateColorAsState(startColor, tween(220), label = "prog_start")
  val animEnd by animateColorAsState(endColor, tween(220), label = "prog_end")
  val animatedProgress by
    animateFloatAsState(
      targetValue = progress.coerceIn(0f, 1f),
      animationSpec = tween(260),
      label = "drx_progress",
    )
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(height)
        .clip(RoundedCornerShape(50))
        .background(drxColors.elevatedSurface)
  ) {
    if (animatedProgress > 0.01f) {
      Box(
        modifier =
          Modifier
            .fillMaxWidth(animatedProgress)
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(animStart, animEnd)))
      )
    }
  }
}

@Composable
fun DrxCircularGauge(
  label: String,
  valueText: String,
  subText: String,
  progress: Float,
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  val drxColors = LocalDrxColors.current
  val animAccent by animateColorAsState(accentColor, tween(240), label = "gauge_color")
  val animatedProg by
    animateFloatAsState(
      targetValue = progress.coerceIn(0f, 1f),
      animationSpec = tween(260),
      label = "gauge_progress",
    )
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier.size(66.dp),
      contentAlignment = Alignment.Center,
    ) {
      Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
        val strokeW = 5.dp.toPx()
        drawArc(
          color = drxColors.elevatedSurface,
          startAngle = 145f,
          sweepAngle = 250f,
          useCenter = false,
          style = Stroke(width = strokeW, cap = StrokeCap.Round),
        )
        if (animatedProg > 0.02f) {
          drawArc(
            color = animAccent.copy(alpha = 0.22f),
            startAngle = 145f,
            sweepAngle = 250f * animatedProg,
            useCenter = false,
            style = Stroke(width = strokeW * 1.85f, cap = StrokeCap.Round),
          )
        }
        drawArc(
          color = animAccent,
          startAngle = 145f,
          sweepAngle = 250f * animatedProg,
          useCenter = false,
          style = Stroke(width = strokeW, cap = StrokeCap.Round),
        )
      }
      Text(
        text = valueText,
        style =
          MaterialTheme.typography.titleMedium.copy(
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum",
          ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
      )
    }
    Spacer(Modifier.height(4.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
    )
    Text(
      text = subText,
      style = DrxTypographyTokens.caption,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun DrxStatusPill(
  text: String,
  color: Color,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .clip(RoundedCornerShape(50))
        .background(color.copy(alpha = 0.12f))
        .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(50))
        .padding(horizontal = 10.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Canvas(modifier = Modifier.size(6.dp)) {
      drawCircle(color = color, center = Offset(size.width / 2f, size.height / 2f))
    }
    Spacer(Modifier.width(6.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = color,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun DrxNotificationBanner(
  title: String,
  body: String,
  accentColor: Color = LocalDrxColors.current.electricBlue,
  icon: ImageVector? = null,
  onDismiss: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val drxColors = LocalDrxColors.current
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    color = drxColors.cardSurface,
    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.36f)),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.Top,
    ) {
      if (icon != null) {
        Box(
          modifier =
            Modifier
              .size(34.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(accentColor.copy(alpha = 0.14f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp),
          )
        }
        Spacer(Modifier.width(12.dp))
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
          text = body,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (onDismiss != null) {
        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
          Icon(
            Icons.Default.Close,
            contentDescription = "Dismiss",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}

@Composable
fun DrxCustomModal(
  title: String,
  onDismiss: () -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  val drxColors = LocalDrxColors.current
  Dialog(onDismissRequest = onDismiss) {
    DrxAnimatedModalContainer {
      Surface(
        shape = RoundedCornerShape(28.dp),
        color = drxColors.cardSurface,
        border = BorderStroke(1.dp, drxColors.glassBorder),
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(20.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = title,
              style = MaterialTheme.typography.titleLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
              onClick = onDismiss,
              shape = CircleShape,
              color = drxColors.elevatedSurface,
              modifier = Modifier.size(36.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  Icons.Default.Close,
                  contentDescription = "Close",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
          Spacer(Modifier.height(14.dp))
          content()
        }
      }
    }
  }
}

data class DrxNavItemUi(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val selected: Boolean,
  val onClick: () -> Unit,
)

@Composable
fun DrxCustomNavigationBar(
  items: List<DrxNavItemUi>,
  modifier: Modifier = Modifier,
) {
  val drxColors = LocalDrxColors.current
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .background(Color.Transparent)
        .navigationBarsPadding()
        .padding(horizontal = 14.dp, vertical = 10.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      color = drxColors.secondaryBg.copy(alpha = 0.94f),
      border = BorderStroke(1.dp, drxColors.glassBorder),
      shape = RoundedCornerShape(28.dp),
      shadowElevation = 10.dp,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        items.forEach { item ->
          val activeContentColor by
            animateColorAsState(
              targetValue =
                if (item.selected) drxColors.neonCyan
                else MaterialTheme.colorScheme.onSurfaceVariant,
              animationSpec = tween(180),
              label = "nav_color",
            )
          val pillBgColor by
            animateColorAsState(
              targetValue =
                if (item.selected) drxColors.electricBlue.copy(alpha = 0.22f)
                else Color.Transparent,
              animationSpec = tween(180),
              label = "nav_pill_bg",
            )
          val pillBorderColor by
            animateColorAsState(
              targetValue =
                if (item.selected) drxColors.electricBlue.copy(alpha = 0.45f)
                else Color.Transparent,
              animationSpec = tween(180),
              label = "nav_pill_border",
            )
          Column(
            modifier =
              Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(22.dp))
                .background(pillBgColor)
                .border(1.dp, pillBorderColor, RoundedCornerShape(22.dp))
                .clickable(onClick = item.onClick)
                .padding(vertical = 6.dp, horizontal = 4.dp)
                .testTag("nav_${item.route}"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Icon(
              imageVector = if (item.selected) item.selectedIcon else item.unselectedIcon,
              contentDescription = item.label,
              tint = activeContentColor,
              modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
              text = item.label,
              style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Medium
              ),
              color = activeContentColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}

@Composable
fun ThinLineSparkline(
  samples: List<Float>,
  lineColor: Color,
  minVal: Float = 0f,
  maxVal: Float = 100f,
  modifier: Modifier = Modifier,
) {
  val drxColors = LocalDrxColors.current
  Canvas(modifier = modifier.fillMaxWidth().height(52.dp)) {
    drawLine(
      color = drxColors.glassBorder,
      start = Offset(0f, size.height - 1.dp.toPx()),
      end = Offset(size.width, size.height - 1.dp.toPx()),
      strokeWidth = 1.dp.toPx(),
    )
    if (samples.size < 2) return@Canvas
    val span = (maxVal - minVal).coerceAtLeast(1f)
    val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
    val linePath = Path()
    val fillPath = Path()
    samples.forEachIndexed { idx, value ->
      val norm = ((value - minVal) / span).coerceIn(0f, 1f)
      val x = idx * stepX
      val y = size.height - (norm * (size.height - 6.dp.toPx())) - 3.dp.toPx()
      if (idx == 0) {
        linePath.moveTo(x, y)
        fillPath.moveTo(x, size.height)
        fillPath.lineTo(x, y)
      } else {
        linePath.lineTo(x, y)
        fillPath.lineTo(x, y)
      }
    }
    fillPath.lineTo((samples.size - 1) * stepX, size.height)
    fillPath.close()
    drawPath(
      path = fillPath,
      brush =
        Brush.verticalGradient(
          listOf(lineColor.copy(alpha = 0.16f), Color.Transparent)
        ),
    )
    drawPath(
      path = linePath,
      color = lineColor,
      style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
  }
}

@Composable
fun DrxInfoTooltipIcon(
  title: String,
  description: String,
  modifier: Modifier = Modifier,
) {
  var showDialog by remember { mutableStateOf(false) }

  IconButton(
    onClick = { showDialog = true },
    modifier = modifier.size(36.dp),
  ) {
    Icon(
      imageVector = Icons.Outlined.Info,
      contentDescription = title,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(16.dp),
    )
  }

  if (showDialog) {
    DrxCustomModal(
      title = title,
      onDismiss = { showDialog = false },
    ) {
      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
