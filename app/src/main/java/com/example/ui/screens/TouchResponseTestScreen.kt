package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.model.LanguageMode
import com.example.data.model.TouchTestMode
import com.example.i18n.LocalStrings
import com.example.system.safeRoundToInt
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.random.Random

@Composable
fun TouchResponseTestScreen(
  language: LanguageMode,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val isIndo = language == LanguageMode.INDONESIAN

  var selectedMode by remember { mutableStateOf(TouchTestMode.MULTI_TOUCH) }
  val activePointers = remember { mutableStateMapOf<Long, Offset>() }
  val trail = remember { mutableListOf<Offset>() }
  var canvasSize by remember { mutableStateOf(IntSize.Zero) }

  var touchEventCount by remember { mutableIntStateOf(0) }
  var lastWindowStart by remember { mutableLongStateOf(System.currentTimeMillis()) }
  var measuredTouchHz by remember { mutableFloatStateOf(0f) }

  var tapCount by remember { mutableIntStateOf(0) }
  var peakPointers by remember { mutableIntStateOf(0) }
  var lastTapPoint by remember { mutableStateOf<Offset?>(null) }

  var gestureStartPos by remember { mutableStateOf<Offset?>(null) }
  var gestureStartMs by remember { mutableLongStateOf(0L) }
  var lastGestureLabel by remember { mutableStateOf("-") }
  var lastSwipeVelocityPxSec by remember { mutableFloatStateOf(0f) }

  var targetCenterNorm by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
  var targetSpawnMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
  var accuracyHits by remember { mutableIntStateOf(0) }
  var accuracyMisses by remember { mutableIntStateOf(0) }
  var avgReactionMs by remember { mutableIntStateOf(0) }
  var totalReactionMs by remember { mutableLongStateOf(0L) }
  var avgAccuracyErrorPx by remember { mutableFloatStateOf(0f) }
  var totalAccuracyErrorPx by remember { mutableFloatStateOf(0f) }

  fun resetStats() {
    activePointers.clear()
    trail.clear()
    measuredTouchHz = 0f
    touchEventCount = 0
    tapCount = 0
    peakPointers = 0
    lastTapPoint = null
    lastGestureLabel = "-"
    lastSwipeVelocityPxSec = 0f
    accuracyHits = 0
    accuracyMisses = 0
    avgReactionMs = 0
    totalReactionMs = 0L
    avgAccuracyErrorPx = 0f
    totalAccuracyErrorPx = 0f
    targetCenterNorm = Offset(0.5f, 0.5f)
    targetSpawnMs = System.currentTimeMillis()
  }

  LazyColumn(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("touch_response_screen"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.touchResponseTestTitle,
        subtitle = strings.touchResponseTestSub,
        onBack = onBack,
        trailing = {
          DrxSecondaryButton(
            label = if (isIndo) "Reset" else "Reset",
            icon = Icons.Default.Refresh,
            onClick = { resetStats() },
            modifier = Modifier.height(42.dp).testTag("touch_test_reset_button"),
          )
        },
      )
    }

    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(TouchTestMode.entries) { mode ->
          DrxFilterPill(
            label = if (isIndo) mode.labelId else mode.labelEn,
            selected = selectedMode == mode,
            onClick = {
              selectedMode = mode
              trail.clear()
              if (mode == TouchTestMode.ACCURACY) {
                targetSpawnMs = System.currentTimeMillis()
              }
            },
            modifier = Modifier.testTag("touch_mode_${mode.id}"),
          )
        }
      }
    }

    item {
      DrxGlassCard(accentColor = drxColors.electricBlue, cornerRadius = 22.dp) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (isIndo) selectedMode.labelId else selectedMode.labelEn,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          )
          DrxStatusPill(
            text = "${strings.activePointers}: ${activePointers.size} • ${measuredTouchHz.safeRoundToInt()} Hz",
            color = drxColors.neonCyan,
          )
        }
        Spacer(Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          TouchStatMini(
            label = if (isIndo) "Total Tap" else "Taps",
            value = "$tapCount",
            modifier = Modifier.weight(1f),
          )
          TouchStatMini(
            label = if (isIndo) "Maks Jari" else "Peak Fingers",
            value = "$peakPointers",
            modifier = Modifier.weight(1f),
          )
          TouchStatMini(
            label = if (isIndo) "Event Rate" else "Event Rate",
            value = "${measuredTouchHz.safeRoundToInt()} Hz",
            modifier = Modifier.weight(1f),
          )
        }

        if (selectedMode == TouchTestMode.GESTURE) {
          Spacer(Modifier.height(8.dp))
          Text(
            text =
              if (isIndo) {
                "Gestur Terakhir: $lastGestureLabel (${lastSwipeVelocityPxSec.toInt()} px/s)"
              } else {
                "Last Gesture: $lastGestureLabel (${lastSwipeVelocityPxSec.toInt()} px/s)"
              },
            style = DrxTypographyTokens.numericReadout,
            color = drxColors.neonCyan,
          )
        } else if (selectedMode == TouchTestMode.ACCURACY) {
          Spacer(Modifier.height(8.dp))
          Text(
            text =
              String.format(
                Locale.US,
                if (isIndo) "Tepat: %d • Meleset: %d • Rata Reaksi: %d ms • Jarak: %.1f px"
                else "Hits: %d • Miss: %d • Avg Reaction: %d ms • Offset: %.1f px",
                accuracyHits,
                accuracyMisses,
                avgReactionMs,
                avgAccuracyErrorPx,
              ),
            style = DrxTypographyTokens.caption,
            color = drxColors.neonCyan,
          )
        }

        Spacer(Modifier.height(10.dp))

        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(310.dp)
              .clip(RoundedCornerShape(18.dp))
              .background(drxColors.secondaryBg)
              .border(1.dp, drxColors.glassBorder, RoundedCornerShape(18.dp))
              .onSizeChanged { canvasSize = it }
              .testTag("multi_touch_canvas")
              .pointerInput(selectedMode, canvasSize) {
                awaitPointerEventScope {
                  while (true) {
                    val event = awaitPointerEvent()
                    val now = System.currentTimeMillis()
                    touchEventCount++
                    val elapsed = now - lastWindowStart
                    if (elapsed >= 450L) {
                      measuredTouchHz = (touchEventCount * 1000f) / elapsed.toFloat()
                      touchEventCount = 0
                      lastWindowStart = now
                    }

                    if (event.type == PointerEventType.Press) {
                      tapCount++
                      val firstPress = event.changes.firstOrNull { it.pressed }?.position
                      if (firstPress != null) {
                        lastTapPoint = firstPress
                        gestureStartPos = firstPress
                        gestureStartMs = now

                        if (selectedMode == TouchTestMode.ACCURACY && canvasSize.width > 0 && canvasSize.height > 0) {
                          val cx = targetCenterNorm.x * canvasSize.width
                          val cy = targetCenterNorm.y * canvasSize.height
                          val dist = hypot((firstPress.x - cx).toDouble(), (firstPress.y - cy).toDouble()).toFloat()
                          val hitRadiusPx = 36.dp.toPx()
                          if (dist <= hitRadiusPx) {
                            accuracyHits++
                            val react = (now - targetSpawnMs).coerceIn(30L, 10_000L)
                            totalReactionMs += react
                            avgReactionMs = (totalReactionMs / accuracyHits).toInt()
                            totalAccuracyErrorPx += dist
                            avgAccuracyErrorPx = totalAccuracyErrorPx / accuracyHits
                            targetCenterNorm =
                              Offset(
                                x = 0.15f + Random.nextFloat() * 0.70f,
                                y = 0.15f + Random.nextFloat() * 0.70f,
                              )
                            targetSpawnMs = now
                          } else {
                            accuracyMisses++
                          }
                        }
                      }
                    }

                    activePointers.clear()
                    event.changes.forEach { change ->
                      if (change.pressed) {
                        activePointers[change.id.value] = change.position
                        trail.add(change.position)
                        if (trail.size > 90) trail.removeAt(0)
                      }
                    }
                    if (activePointers.size > peakPointers) {
                      peakPointers = activePointers.size
                    }

                    if (event.type == PointerEventType.Release && activePointers.isEmpty()) {
                      val releasePos = event.changes.firstOrNull()?.position
                      val startPos = gestureStartPos
                      if (selectedMode == TouchTestMode.GESTURE && releasePos != null && startPos != null) {
                        val dx = releasePos.x - startPos.x
                        val dy = releasePos.y - startPos.y
                        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        val dtSec = ((now - gestureStartMs).coerceAtLeast(16L)) / 1000f
                        lastSwipeVelocityPxSec = dist / dtSec
                        lastGestureLabel =
                          if (dist < 24f) {
                            "Tap"
                          } else if (abs(dx) > abs(dy)) {
                            if (dx > 0) "Swipe Right" else "Swipe Left"
                          } else {
                            val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                            if (dy > 0) "Swipe Down (${angle.toInt()}°)" else "Swipe Up (${angle.toInt()}°)"
                          }
                      }
                      if (selectedMode != TouchTestMode.TRACKING) {
                        trail.clear()
                      }
                    }
                  }
                }
              }
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 36.dp.toPx()
            var x = step
            while (x < size.width) {
              drawLine(drxColors.glassBorder.copy(alpha = 0.22f), Offset(x, 0f), Offset(x, size.height))
              x += step
            }
            var y = step
            while (y < size.height) {
              drawLine(drxColors.glassBorder.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y))
              y += step
            }

            if (selectedMode == TouchTestMode.ACCURACY) {
              val targetCenter = Offset(targetCenterNorm.x * size.width, targetCenterNorm.y * size.height)
              drawCircle(drxColors.electricBlue.copy(alpha = 0.20f), radius = 36.dp.toPx(), center = targetCenter)
              drawCircle(drxColors.neonCyan, radius = 26.dp.toPx(), center = targetCenter, style = Stroke(2.5.dp.toPx()))
              drawCircle(drxColors.optimalGreen, radius = 6.dp.toPx(), center = targetCenter)
            }

            for (i in 1 until trail.size) {
              drawLine(
                color = drxColors.electricBlue.copy(alpha = 0.72f),
                start = trail[i - 1],
                end = trail[i],
                strokeWidth = 3.dp.toPx(),
              )
            }

            lastTapPoint?.let { tapPt ->
              if (activePointers.isEmpty() && selectedMode == TouchTestMode.TAP) {
                drawCircle(
                  color = drxColors.electricBlue.copy(alpha = 0.45f),
                  radius = 18.dp.toPx(),
                  center = tapPt,
                  style = Stroke(2.dp.toPx()),
                )
              }
            }

            activePointers.values.forEach { pt ->
              drawLine(drxColors.neonCyan.copy(alpha = 0.45f), Offset(0f, pt.y), Offset(size.width, pt.y))
              drawLine(drxColors.neonCyan.copy(alpha = 0.45f), Offset(pt.x, 0f), Offset(pt.x, size.height))
              drawCircle(drxColors.neonCyan.copy(alpha = 0.22f), radius = 32.dp.toPx(), center = pt)
              drawCircle(
                drxColors.neonCyan,
                radius = 20.dp.toPx(),
                center = pt,
                style = Stroke(width = 2.5.dp.toPx()),
              )
            }
          }
        }
      }
    }

    item {
      DrxGlassCard(cornerRadius = 18.dp) {
        Text(
          text =
            if (isIndo) {
              "Catatan Transparansi: Laju Hz yang diukur adalah laju event pointer Compose di dalam aplikasi, bukan mengubah touch sampling rate hardware sistem secara global."
            } else {
              "Transparency Note: Measured Hz reflects real Android pointer events inside this canvas and does not modify system-wide hardware touch sampling."
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun TouchStatMini(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  val drxColors = LocalDrxColors.current
  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(14.dp))
        .background(drxColors.elevatedSurface)
        .padding(horizontal = 10.dp, vertical = 8.dp),
  ) {
    Text(label, style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
      text = value,
      style = DrxTypographyTokens.numericReadout.copy(fontWeight = FontWeight.Bold),
      color = drxColors.neonCyan,
    )
  }
}
