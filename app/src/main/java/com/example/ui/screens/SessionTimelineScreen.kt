package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.SessionTimelineEventEntity
import com.example.data.model.LanguageMode
import com.example.data.model.SessionTimelineEventType
import com.example.i18n.LocalStrings
import com.example.system.TimeAndSizeFormatters
import com.example.ui.theme.DrxCustomModal
import com.example.ui.theme.DrxDangerButton
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxMainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionTimelineScreen(
  viewModel: DrxMainViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val context = LocalContext.current

  val events by viewModel.sessionTimelineEvents.collectAsStateWithLifecycle()
  val sessions by viewModel.allSessions.collectAsStateWithLifecycle()
  val prefs by viewModel.userPrefs.collectAsStateWithLifecycle()
  val isIndo = prefs.language == LanguageMode.INDONESIAN

  val groupedSessions =
    remember(events) {
      events
        .groupBy { it.sessionId }
        .entries
        .sortedByDescending { entry -> entry.value.maxOfOrNull { it.timestampMs } ?: 0L }
    }

  var selectedSessionId by remember(groupedSessions) {
    mutableStateOf(groupedSessions.firstOrNull()?.key)
  }
  var showCompareModal by remember { mutableStateOf(false) }

  val selectedEvents =
    remember(groupedSessions, selectedSessionId) {
      groupedSessions.firstOrNull { it.key == selectedSessionId }?.value?.sortedBy { it.timestampMs }.orEmpty()
    }
  val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
  val dateFmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale.US) }

  LazyColumn(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("session_timeline_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.sessionTimelineTitle,
        subtitle = strings.sessionTimelineSub,
        onBack = onBack,
      )
    }

    if (groupedSessions.isEmpty()) {
      item {
        DrxGlassCard(accentColor = drxColors.electricBlue) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Icon(Icons.Default.Timeline, contentDescription = null, tint = drxColors.neonCyan, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(8.dp))
            Text(
              text = if (isIndo) "Belum ada timeline sesi tercatat" else "No Session Timeline Recorded Yet",
              style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text =
                if (isIndo) {
                  "Timeline hanya dibuat dari event asli saat Anda meluncurkan game dan monitoring aktif."
                } else {
                  "Timelines are recorded strictly from real events when launching games while monitoring is active."
                },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      // Session selector chips
      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(groupedSessions, key = { it.key }) { entry ->
            val first = entry.value.minByOrNull { it.timestampMs }
            val label =
              "${first?.gameName ?: "Game"} • ${first?.let { dateFmt.format(Date(it.timestampMs)) } ?: ""}"
            DrxFilterPill(
              label = label,
              selected = entry.key == selectedSessionId,
              onClick = { selectedSessionId = entry.key },
              modifier = Modifier.testTag("timeline_session_chip_${entry.key}"),
            )
          }
        }
      }

      // Action toolbar: Export .txt, Compare, Delete session, Clear all
      item {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          DrxPrimaryButton(
            label = if (isIndo) "Ekspor .txt" else "Export .txt",
            icon = Icons.Default.Share,
            onClick = {
              val exportTxt = viewModel.buildTimelineExportText(selectedEvents.ifEmpty { events })
              val send =
                Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_SUBJECT, "DRX Session Timeline")
                  putExtra(Intent.EXTRA_TEXT, exportTxt)
                }
              runCatching { context.startActivity(Intent.createChooser(send, "Export Timeline (.txt)")) }
            },
            modifier = Modifier.weight(1f).testTag("timeline_export_btn"),
          )
          DrxSecondaryButton(
            label = if (isIndo) "Bandingkan" else "Compare",
            icon = Icons.AutoMirrored.Filled.CompareArrows,
            enabled = sessions.size >= 2,
            onClick = { showCompareModal = true },
            modifier = Modifier.weight(1f).testTag("timeline_compare_btn"),
          )
        }
      }

      // Summary + Progression Graph Card (Temp, Battery, RAM)
      if (selectedEvents.isNotEmpty()) {
        val firstEv = selectedEvents.first()
        val lastEv = selectedEvents.last()
        val durationMs = (lastEv.timestampMs - firstEv.timestampMs).coerceAtLeast(0L)
        val peakTemp = selectedEvents.maxOf { it.batteryTempC }
        val warningCount = selectedEvents.count { it.eventType == SessionTimelineEventType.THERMAL_WARNING.code }

        item {
          DrxGlassCard(accentColor = drxColors.neonCyan, cornerRadius = 22.dp) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(firstEv.gameName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                  text = "${dateFmt.format(Date(firstEv.timestampMs))} -> ${timeFmt.format(Date(lastEv.timestampMs))} (${TimeAndSizeFormatters.formatDurationCompact(durationMs, isIndo)})",
                  style = DrxTypographyTokens.caption,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(
                onClick = {
                  selectedSessionId?.let { sid -> viewModel.deleteTimelineSession(sid) }
                },
                modifier = Modifier.testTag("timeline_delete_session_btn"),
              ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Session", tint = drxColors.alertCrimson)
              }
            }

            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              DrxStatusPill(text = String.format(Locale.US, "Peak %.1f°C", peakTemp), color = if (peakTemp >= 41f) drxColors.warningAmber else drxColors.neonCyan)
              DrxStatusPill(text = "Bat ${firstEv.batteryPercent}% -> ${lastEv.batteryPercent}%", color = drxColors.optimalGreen)
              if (warningCount > 0) {
                DrxStatusPill(text = "$warningCount Warn", color = drxColors.alertCrimson)
              }
            }

            if (selectedEvents.size >= 2) {
              Spacer(Modifier.height(14.dp))
              Text(
                text = if (isIndo) "Grafik Kondisi Sesi (Suhu °C • Baterai % • RAM %)" else "Session Condition Progression (Temp °C • Battery % • RAM %)",
                style = MaterialTheme.typography.labelMedium,
                color = drxColors.silver,
              )
              Spacer(Modifier.height(8.dp))
              SessionMultiSeriesChart(
                events = selectedEvents,
                tempColor = drxColors.warningAmber,
                batteryColor = drxColors.optimalGreen,
                ramColor = drxColors.neonCyan,
                gridColor = drxColors.glassBorder,
              )
              Spacer(Modifier.height(6.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDotLabel("Temp", drxColors.warningAmber)
                LegendDotLabel("Battery", drxColors.optimalGreen)
                LegendDotLabel("RAM Used", drxColors.neonCyan)
              }
            }
          }
        }

        // Lag Correlation Summary Card (v1.25.5)
        item {
          val hasThermal =
            selectedEvents.any {
              it.eventType == SessionTimelineEventType.THERMAL_WARNING.code ||
                it.eventType == SessionTimelineEventType.TEMP_INCREASED.code
            }
          val hasRamPressure = selectedEvents.any { it.eventType == SessionTimelineEventType.MEMORY_PRESSURE.code }
          val hasFpsDip = selectedEvents.any { it.eventType == SessionTimelineEventType.FPS_DROP.code }
          val hasNetChange = selectedEvents.any { it.eventType == SessionTimelineEventType.NETWORK_CHANGED.code }
          val hasRecording = selectedEvents.any { it.eventType == SessionTimelineEventType.RECORDING_STARTED.code }

          val insightText =
            buildList {
              if (hasThermal && hasFpsDip) {
                add(
                  if (isIndo) "Kenaikan suhu terdeteksi bersamaan dengan penurunan UI FPS."
                  else "Elevated temperature observed alongside a UI FPS dip."
                )
              }
              if (hasRamPressure) {
                add(
                  if (isIndo) "Tekanan RAM tinggi terjadi selama sesi ini."
                  else "Memory pressure occurred during this session."
                )
              }
              if (hasNetChange) {
                add(
                  if (isIndo) "Perubahan koneksi jaringan tercatat di tengah sesi."
                  else "Network transport or connectivity state changed mid-session."
                )
              }
              if (hasRecording) {
                add(
                  if (isIndo) "Perekaman layar aktif selama sesi berlangsung."
                  else "Screen recording ran during this session."
                )
              }
              if (isEmpty()) {
                add(
                  if (isIndo) "Tidak ada korelasi tekanan thermal, RAM, atau drop FPS selama sesi ini."
                  else "No overlapping thermal, RAM pressure, or UI FPS dip signals observed."
                )
              }
            }.joinToString(" ")

          DrxGlassCard(accentColor = drxColors.electricBlue, cornerRadius = 18.dp) {
            Text(
              text = if (isIndo) "Analisis Korelasi Lag" else "Lag Timeline Correlation",
              style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
              color = drxColors.neonCyan,
            )
            Spacer(Modifier.height(4.dp))
            Text(
              text = insightText,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        // Chronological Event Timeline List
        items(selectedEvents, key = { it.id }) { ev ->
          TimelineEventRow(ev = ev, timeText = timeFmt.format(Date(ev.timestampMs)))
        }
      }

      item {
        DrxDangerButton(
          label = if (isIndo) "Hapus Semua Riwayat Timeline" else "Clear All Timeline History",
          icon = Icons.Default.DeleteOutline,
          onClick = { viewModel.clearTimelineEvents() },
          modifier = Modifier.fillMaxWidth().testTag("timeline_clear_all_btn"),
        )
      }
    }
  }

  if (showCompareModal && sessions.size >= 2) {
    val left = sessions[0]
    val right = sessions[1]
    val metrics = remember(left, right) { viewModel.compareGameSessions(left, right) }
    DrxCustomModal(
      title = if (isIndo) "Bandingkan 2 Sesi Terakhir" else "Compare Recent 2 Sessions",
      onDismiss = { showCompareModal = false },
    ) {
      Text(
        text = "${left.gameName}  vs  ${right.gameName}",
        style = MaterialTheme.typography.titleSmall,
        color = drxColors.neonCyan,
      )
      Spacer(Modifier.height(10.dp))
      metrics.forEach { m ->
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(m.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.2f))
          Text(m.leftValue, style = DrxTypographyTokens.numericReadout, modifier = Modifier.weight(0.9f))
          Text(m.rightValue, style = DrxTypographyTokens.numericReadout, modifier = Modifier.weight(0.9f))
          Text(m.deltaText, style = MaterialTheme.typography.labelMedium, color = drxColors.electricBlue)
        }
      }
    }
  }
}

@Composable
private fun LegendDotLabel(label: String, color: Color) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
    Spacer(Modifier.width(5.dp))
    Text(label, style = DrxTypographyTokens.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
private fun SessionMultiSeriesChart(
  events: List<SessionTimelineEventEntity>,
  tempColor: Color,
  batteryColor: Color,
  ramColor: Color,
  gridColor: Color,
) {
  Canvas(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(120.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(LocalDrxColors.current.secondaryBg)
  ) {
    val w = size.width
    val h = size.height
    val pad = 10.dp.toPx()
    val usableW = (w - pad * 2f).coerceAtLeast(10f)
    val usableH = (h - pad * 2f).coerceAtLeast(10f)

    for (i in 0..3) {
      val y = pad + usableH * (i / 3f)
      drawLine(gridColor, Offset(pad, y), Offset(w - pad, y), strokeWidth = 1.dp.toPx())
    }

    if (events.size < 2) return@Canvas
    val stepX = usableW / (events.size - 1).coerceAtLeast(1)

    fun drawSeries(values: List<Float>, minV: Float, maxV: Float, color: Color) {
      val span = (maxV - minV).coerceAtLeast(1f)
      val path = Path()
      values.forEachIndexed { idx, v ->
        val norm = ((v - minV) / span).coerceIn(0f, 1f)
        val x = pad + idx * stepX
        val y = (pad + usableH) - (norm * usableH)
        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
      }
      drawPath(path, color = color, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    drawSeries(events.map { it.batteryTempC }, 20f, 55f, tempColor)
    drawSeries(events.map { it.batteryPercent.toFloat() }, 0f, 100f, batteryColor)
    drawSeries(events.map { it.ramUsedPercent }, 0f, 100f, ramColor)
  }
}

@Composable
private fun TimelineEventRow(
  ev: SessionTimelineEventEntity,
  timeText: String,
) {
  val drxColors = LocalDrxColors.current
  val (icon, accent) =
    when (ev.eventType) {
      SessionTimelineEventType.LAUNCHED.code -> Icons.Default.PlayArrow to drxColors.electricBlue
      SessionTimelineEventType.MONITORING_STARTED.code -> Icons.Default.Bolt to drxColors.neonCyan
      SessionTimelineEventType.TEMP_INCREASED.code -> Icons.Default.Thermostat to drxColors.warningAmber
      SessionTimelineEventType.THERMAL_WARNING.code -> Icons.Default.WarningAmber to drxColors.alertCrimson
      SessionTimelineEventType.BATTERY_DROP.code -> Icons.Default.BatteryAlert to drxColors.warningAmber
      SessionTimelineEventType.NETWORK_CHANGED.code -> Icons.Default.Wifi to drxColors.electricBlue
      SessionTimelineEventType.MEMORY_PRESSURE.code -> Icons.Default.Memory to drxColors.warningAmber
      SessionTimelineEventType.RECORDING_STARTED.code -> Icons.Default.FiberManualRecord to drxColors.alertCrimson
      SessionTimelineEventType.FPS_DROP.code -> Icons.Default.Speed to drxColors.alertCrimson
      SessionTimelineEventType.SESSION_ENDED.code -> Icons.Default.Flag to drxColors.optimalGreen
      else -> Icons.Default.Timeline to drxColors.silver
    }

  DrxGlassCard(accentColor = accent, cornerRadius = 18.dp, contentPadding = PaddingValues(14.dp)) {
    Row(verticalAlignment = Alignment.Top) {
      Box(
        modifier =
          Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(icon, contentDescription = ev.title, tint = accent, modifier = Modifier.size(18.dp))
      }
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(
            text = ev.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          Spacer(Modifier.width(8.dp))
          Text(
            text = timeText,
            style = DrxTypographyTokens.caption,
            color = drxColors.neonCyan,
          )
        }
        Spacer(Modifier.height(2.dp))
        Text(
          text = ev.detail,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text =
            String.format(
              Locale.US,
              "Bat %d%% • %.1f°C • RAM %d MB free • %s",
              ev.batteryPercent,
              ev.batteryTempC,
              ev.ramFreeMb,
              ev.networkStatus,
            ),
          style = DrxTypographyTokens.caption,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
