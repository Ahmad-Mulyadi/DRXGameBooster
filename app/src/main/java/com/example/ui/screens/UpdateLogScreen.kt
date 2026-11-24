package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppUpdateEntry
import com.example.data.model.DrxUpdateCatalog
import com.example.data.model.UpdateBadgeType
import com.example.data.prefs.DrxUserPrefs
import com.example.i18n.LocalStrings
import com.example.ui.theme.DrxGlassCard
import com.example.ui.theme.DrxHairlineDivider
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxMainViewModel

@Composable
fun UpdateLogScreen(
  prefs: DrxUserPrefs,
  viewModel: DrxMainViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val entries = DrxUpdateCatalog.entries
  val latest = entries.firstOrNull()
  var expandedVersion by remember { mutableStateOf(latest?.versionNumber) }

  val unreadCount = entries.count { !prefs.readUpdateVersions.contains(it.versionNumber) }

  LazyColumn(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("update_log_lazy_column"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      DrxScreenHeader(
        title = strings.updateLogTitle,
        subtitle = strings.updateLogSubtitle,
        onBack = onBack,
        trailing = {
          if (unreadCount > 0) {
            DrxStatusPill(
              text = "$unreadCount ${strings.unreadBadgeLabel}",
              color = drxColors.neonCyan,
            )
          }
        },
      )
    }

    // What's New & Mark All Read Actions
    item {
      DrxGlassCard(
        accentColor = drxColors.electricBlue,
        highlighted = true,
        cornerRadius = 22.dp,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = drxColors.neonCyan,
            modifier = Modifier.size(20.dp),
          )
          Spacer(Modifier.width(8.dp))
          Text(
            text = "Current Release: v${DrxUpdateCatalog.latestVersion}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        Spacer(Modifier.height(6.dp))
        Text(
          text = latest?.title.orEmpty(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          DrxPrimaryButton(
            label = strings.whatsNewBtn,
            icon = Icons.Default.NewReleases,
            onClick = {
              latest?.let {
                expandedVersion = it.versionNumber
                viewModel.markUpdateRead(it.versionNumber)
              }
            },
            modifier = Modifier.weight(1f).testTag("whats_new_button"),
          )
          DrxSecondaryButton(
            label = strings.markAllReadBtn,
            icon = Icons.Default.Check,
            enabled = unreadCount > 0,
            onClick = { viewModel.markAllUpdatesRead() },
            modifier = Modifier.weight(1f).testTag("mark_all_updates_read_button"),
          )
        }
      }
    }

    // Timeline Entries (Newest -> Oldest)
    itemsIndexed(entries, key = { _, item -> item.versionNumber }) { index, entry ->
      val isUnread = !prefs.readUpdateVersions.contains(entry.versionNumber)
      val isExpanded = expandedVersion == entry.versionNumber
      val isLast = index == entries.lastIndex

      UpdateTimelineCard(
        entry = entry,
        isUnread = isUnread,
        isExpanded = isExpanded,
        isLast = isLast,
        unreadBadgeLabel = strings.unreadBadgeLabel,
        onToggle = {
          expandedVersion = if (isExpanded) null else entry.versionNumber
          if (isUnread) {
            viewModel.markUpdateRead(entry.versionNumber)
          }
        },
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpdateTimelineCard(
  entry: AppUpdateEntry,
  isUnread: Boolean,
  isExpanded: Boolean,
  isLast: Boolean,
  unreadBadgeLabel: String,
  onToggle: () -> Unit,
) {
  val drxColors = LocalDrxColors.current
  val nodeColor = if (isUnread) drxColors.neonCyan else drxColors.electricBlue

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(IntrinsicSize.Min)
        .testTag("update_entry_${entry.versionNumber}"),
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.width(24.dp).fillMaxHeight(),
    ) {
      Spacer(Modifier.height(16.dp))
      Box(
        modifier =
          Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(nodeColor.copy(alpha = 0.22f))
            .border(1.5.dp, nodeColor, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        if (isUnread) {
          Box(
            modifier = Modifier.size(5.dp).clip(CircleShape).background(drxColors.neonCyan)
          )
        }
      }
      if (!isLast) {
        Box(
          modifier =
            Modifier
              .width(1.5.dp)
              .weight(1f)
              .padding(top = 6.dp)
              .background(
                Brush.verticalGradient(
                  listOf(nodeColor.copy(alpha = 0.4f), drxColors.glassBorder)
                )
              )
        )
      }
    }

    Spacer(Modifier.width(8.dp))

    val cardShape = RoundedCornerShape(20.dp)
    Column(
      modifier =
        Modifier
          .weight(1f)
          .clip(cardShape)
          .background(drxColors.cardSurface)
          .border(
            width = 1.dp,
            color =
              when {
                isExpanded -> drxColors.electricBlue.copy(alpha = 0.55f)
                isUnread -> drxColors.neonCyan.copy(alpha = 0.45f)
                else -> drxColors.glassBorder
              },
            shape = cardShape,
          )
          .clickable(onClick = onToggle)
          .padding(16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "v${entry.versionNumber}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(Modifier.width(8.dp))
          Text(
            text = "• ${entry.releaseDate}",
            style = DrxTypographyTokens.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (isUnread) {
            Spacer(Modifier.width(8.dp))
            DrxStatusPill(text = unreadBadgeLabel, color = drxColors.neonCyan)
          }
        }
        Icon(
          imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
          contentDescription = "Toggle details",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(Modifier.height(6.dp))
      Text(
        text = entry.title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
      )

      Spacer(Modifier.height(10.dp))
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        entry.badges.forEach { badge ->
          val badgeColor =
            when (badge) {
              UpdateBadgeType.NEW_FEATURE -> drxColors.neonCyan
              UpdateBadgeType.IMPROVEMENT -> drxColors.electricBlue
              UpdateBadgeType.FIXED -> drxColors.optimalGreen
              UpdateBadgeType.SECURITY -> drxColors.warningAmber
            }
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = badgeColor.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f)),
          ) {
            Text(
              text = badge.label,
              style = MaterialTheme.typography.labelSmall,
              color = badgeColor,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
        }
      }

      AnimatedVisibility(
        visible = isExpanded,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          DrxHairlineDivider()
          if (entry.newFeatures.isNotEmpty()) {
            ChangelogSectionBlock("New Features", entry.newFeatures, drxColors.neonCyan)
          }
          if (entry.improvements.isNotEmpty()) {
            ChangelogSectionBlock("Improvements", entry.improvements, drxColors.electricBlue)
          }
          if (entry.bugFixes.isNotEmpty()) {
            ChangelogSectionBlock("Bug Fixes", entry.bugFixes, drxColors.optimalGreen)
          }
          if (entry.performanceImprovements.isNotEmpty()) {
            ChangelogSectionBlock("Performance Improvements", entry.performanceImprovements, drxColors.warningAmber)
          }
          if (entry.additionalNotes.isNotBlank()) {
            Column(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .background(drxColors.elevatedSurface)
                  .padding(12.dp)
            ) {
              Text("Additional Notes", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
              Spacer(Modifier.height(4.dp))
              Text(
                text = entry.additionalNotes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ChangelogSectionBlock(
  header: String,
  bullets: List<String>,
  accentColor: Color,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(header, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = accentColor)
    bullets.forEach { line ->
      Text(
        text = "• $line",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}
