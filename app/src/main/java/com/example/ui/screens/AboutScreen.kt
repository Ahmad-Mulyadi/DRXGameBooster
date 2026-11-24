package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.data.model.DrxUpdateCatalog
import com.example.data.prefs.DrxUserPrefs
import com.example.data.remote.SocialLinks
import com.example.data.remote.SocialStatsState
import com.example.i18n.LocalStrings
import com.example.ui.theme.DrxHairlineDivider
import com.example.ui.theme.DrxScreenHeader
import com.example.ui.theme.DrxSectionHeader
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.DrxTypographyTokens
import com.example.ui.theme.LocalDrxColors
import com.example.ui.viewmodel.DrxMainViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AboutScreen(
  prefs: DrxUserPrefs,
  viewModel: DrxMainViewModel,
  onOpenUpdateLog: () -> Unit = {},
  onOpenUpdateCenter: (() -> Unit)? = null,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val strings = LocalStrings.current
  val drxColors = LocalDrxColors.current
  val context = LocalContext.current
  val socialStats by viewModel.socialStats.collectAsStateWithLifecycle()

  var activeModal by remember { mutableStateOf<AboutModalType?>(null) }
  var visibleStage by remember { mutableStateOf(false) }

  LaunchedEffect(Unit) {
    visibleStage = true
    if (socialStats is SocialStatsState.Loading) {
      viewModel.refreshSocialStats()
    }
  }

  val copyText: (String) -> Unit = { value ->
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("DRX", value))
    Toast.makeText(context, "${strings.copiedToClipboardLabel}: $value", Toast.LENGTH_SHORT).show()
  }

  LazyColumn(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .testTag("about_lazy_column"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    // HEADER
    item {
      DrxScreenHeader(
        title = strings.aboutTitle,
        subtitle = strings.aboutSubtitle,
        onBack = onBack,
      )
    }

    // DEVELOPER PROFILE — DERREX
    item {
      AnimatedVisibility(
        visible = visibleStage,
        enter = fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 16 },
      ) {
        val cardShape = RoundedCornerShape(24.dp)
        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(cardShape)
              .background(
                Brush.verticalGradient(
                  listOf(drxColors.elevatedSurface, drxColors.cardSurface)
                )
              )
              .border(1.dp, drxColors.glassBorder, cardShape)
              .padding(20.dp)
              .testTag("about_developer_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            DerrexGlowingEmblem(size = 76.dp, animationsEnabled = prefs.animationsEnabled)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              DrxStatusPill(
                text = strings.officialDeveloperBadge,
                color = drxColors.neonCyan,
              )
              Spacer(Modifier.height(6.dp))
              Text(
                text = "DERREX",
                style = MaterialTheme.typography.headlineLarge.copy(
                  fontWeight = FontWeight.SemiBold,
                  letterSpacing = 1.2.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = strings.officialDeveloperIdentity,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }

          Spacer(Modifier.height(16.dp))
          DrxHairlineDivider()
          Spacer(Modifier.height(14.dp))

          Text(
            text = strings.developerBioParagraph1,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(Modifier.height(8.dp))
          Text(
            text = strings.developerBioParagraph2,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // SOCIAL CONNECTIONS — DERREX
    item {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DrxSectionHeader(title = strings.socialConnectionsTitle)

        SocialConnectionCard(
          platformLabel = "YOUTUBE",
          accountTitle = SocialLinks.YOUTUBE_NAME,
          accountSubtitle = SocialLinks.YOUTUBE_HANDLE,
          metricText =
            when (val st = socialStats) {
              is SocialStatsState.Loaded -> st.youtubeSubscribers.displayMetric ?: strings.dataUnavailableLabel
              is SocialStatsState.Loading -> "..."
              is SocialStatsState.Error -> strings.dataUnavailableLabel
            },
          ctaLabel = "Visit Channel",
          accentColor = Color(0xFFFF0033),
          iconContent = { YouTubeBrandIcon() },
          onOpenLink = { openExternalUrl(context, SocialLinks.YOUTUBE_URL) },
          onCopyHandle = { copyText(SocialLinks.YOUTUBE_HANDLE) },
          modifier = Modifier.testTag("social_youtube_card"),
        )

        SocialConnectionCard(
          platformLabel = "TIKTOK",
          accountTitle = SocialLinks.TIKTOK_USERNAME,
          accountSubtitle = "Official Creator Profile",
          metricText =
            when (val st = socialStats) {
              is SocialStatsState.Loaded -> st.tiktokFollowers.displayMetric ?: strings.dataUnavailableLabel
              is SocialStatsState.Loading -> "..."
              is SocialStatsState.Error -> strings.dataUnavailableLabel
            },
          ctaLabel = "Visit Profile",
          accentColor = Color(0xFF25F4EE),
          iconContent = { TikTokBrandIcon() },
          onOpenLink = { openExternalUrl(context, SocialLinks.TIKTOK_URL) },
          onCopyHandle = { copyText(SocialLinks.TIKTOK_USERNAME) },
          modifier = Modifier.testTag("social_tiktok_card"),
        )

        SocialConnectionCard(
          platformLabel = "WHATSAPP CHANNEL",
          accountTitle = SocialLinks.WHATSAPP_NAME,
          accountSubtitle = "Official Updates & Releases",
          metricText = null,
          ctaLabel = "Join Channel",
          accentColor = Color(0xFF25D366),
          iconContent = { WhatsAppBrandIcon() },
          onOpenLink = { openExternalUrl(context, SocialLinks.WHATSAPP_URL) },
          onCopyHandle = { copyText(SocialLinks.WHATSAPP_URL) },
          modifier = Modifier.testTag("social_whatsapp_card"),
        )
      }
    }

    // SUPPORTED BY — NANAY (LOCKED READ-ONLY PROFILE)
    item {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DrxSectionHeader(title = strings.supportedByTitle)

        val supporterShape = RoundedCornerShape(24.dp)
        val supporterDisplayName =
          (socialStats as? SocialStatsState.Loaded)?.supporterProfile?.profileDisplayName
            ?: SocialLinks.SUPPORTER_NAME

        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(supporterShape)
              .background(
                Brush.verticalGradient(
                  listOf(drxColors.elevatedSurface, drxColors.cardSurface)
                )
              )
              .border(1.dp, drxColors.electricBlue.copy(alpha = 0.28f), supporterShape)
              .padding(20.dp)
              .testTag("about_supporter_card"),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            SupporterAvatarBadge(
              avatarPath = prefs.supporterAvatarPath,
              size = 68.dp,
              glowAlpha = 0.5f,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                DrxStatusPill(
                  text = strings.officialSupporterBadge,
                  color = drxColors.electricBlue,
                )
                Spacer(Modifier.width(8.dp))
                TikTokBrandIcon(modifier = Modifier.size(22.dp))
              }
              Spacer(Modifier.height(6.dp))
              Text(
                text = supporterDisplayName,
                style = MaterialTheme.typography.headlineMedium.copy(
                  fontWeight = FontWeight.SemiBold,
                  letterSpacing = 1.0.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                  Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { copyText(SocialLinks.SUPPORTER_TIKTOK) }
                    .padding(vertical = 2.dp),
              ) {
                Text(
                  text = SocialLinks.SUPPORTER_TIKTOK,
                  style = MaterialTheme.typography.bodySmall,
                  color = drxColors.neonCyan,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                  imageVector = Icons.Default.ContentCopy,
                  contentDescription = "Copy ${SocialLinks.SUPPORTER_TIKTOK}",
                  tint = drxColors.neonCyan,
                  modifier = Modifier.size(14.dp),
                )
              }
            }
          }

          Spacer(Modifier.height(12.dp))
          Text(
            text = strings.supporterDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(14.dp))

          GradientActionButton(
            label = "Visit TikTok",
            gradient = Brush.horizontalGradient(listOf(drxColors.electricBlueDeep, drxColors.electricBlue)),
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            onClick = { openExternalUrl(context, SocialLinks.SUPPORTER_URL) },
            modifier = Modifier.fillMaxWidth().testTag("visit_supporter_tiktok_button"),
          )
        }
      }
    }

    // SOCIAL COUNTERS (LIVE / DATA UNAVAILABLE — NO MANUAL FAKE COUNTERS)
    item {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          DrxSectionHeader(title = strings.socialCountersTitle, modifier = Modifier.weight(1f))
          Surface(
            onClick = { viewModel.refreshSocialStats() },
            shape = RoundedCornerShape(12.dp),
            color = drxColors.elevatedSurface,
            border = BorderStroke(1.dp, drxColors.glassBorder),
            modifier = Modifier.height(36.dp).testTag("refresh_social_stats_button"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              if (socialStats is SocialStatsState.Loading) {
                CircularProgressIndicator(
                  modifier = Modifier.size(14.dp),
                  strokeWidth = 2.dp,
                  color = drxColors.neonCyan,
                )
              } else {
                Icon(
                  Icons.Default.Refresh,
                  contentDescription = strings.refreshStatsBtn,
                  tint = drxColors.neonCyan,
                  modifier = Modifier.size(15.dp),
                )
              }
              Spacer(Modifier.width(6.dp))
              Text(
                strings.refreshStatsBtn,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }

        val lastUpdatedMs =
          when (val st = socialStats) {
            is SocialStatsState.Loaded -> st.lastUpdatedMs
            is SocialStatsState.Loading -> st.lastUpdatedMs
            is SocialStatsState.Error -> st.lastUpdatedMs
          }
        val formattedTime =
          lastUpdatedMs?.let {
            SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(it))
          } ?: "N/A"

        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(20.dp))
              .background(drxColors.cardSurface)
              .border(1.dp, drxColors.glassBorder, RoundedCornerShape(20.dp))
              .padding(16.dp)
              .testTag("social_counters_card"),
        ) {
          if (socialStats is SocialStatsState.Error) {
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .background(drxColors.alertCrimson.copy(alpha = 0.12f))
                  .border(1.dp, drxColors.alertCrimson.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                  .padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = drxColors.alertCrimson,
                modifier = Modifier.size(18.dp),
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text = "${strings.dataUnavailableLabel} • ${(socialStats as SocialStatsState.Error).message}",
                style = MaterialTheme.typography.bodySmall,
                color = drxColors.alertCrimson,
              )
            }
            Spacer(Modifier.height(10.dp))
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            val ytApi = (socialStats as? SocialStatsState.Loaded)?.youtubeSubscribers?.displayMetric
            val ytValue =
              when (socialStats) {
                is SocialStatsState.Loading -> "..."
                is SocialStatsState.Loaded -> ytApi ?: strings.dataUnavailableLabel
                is SocialStatsState.Error -> strings.dataUnavailableLabel
              }
            val ttApi = (socialStats as? SocialStatsState.Loaded)?.tiktokFollowers?.displayMetric
            val ttValue =
              when (socialStats) {
                is SocialStatsState.Loading -> "..."
                is SocialStatsState.Loaded -> ttApi ?: strings.dataUnavailableLabel
                is SocialStatsState.Error -> strings.dataUnavailableLabel
              }
            val supValue =
              when (val st = socialStats) {
                is SocialStatsState.Loading -> "..."
                is SocialStatsState.Loaded -> st.supporterProfile.profileDisplayName ?: SocialLinks.SUPPORTER_TIKTOK
                is SocialStatsState.Error -> SocialLinks.SUPPORTER_TIKTOK
              }

            CounterTile(
              title = "YouTube",
              value = ytValue,
              sub = SocialLinks.YOUTUBE_HANDLE,
              accent = Color(0xFFFF0033),
              modifier = Modifier.weight(1f),
            )
            CounterTile(
              title = "TikTok",
              value = ttValue,
              sub = SocialLinks.TIKTOK_USERNAME,
              accent = Color(0xFF25F4EE),
              modifier = Modifier.weight(1f),
            )
            CounterTile(
              title = "Supporter",
              value = supValue,
              sub = SocialLinks.SUPPORTER_TIKTOK,
              accent = drxColors.electricBlue,
              modifier = Modifier.weight(1f),
            )
          }

          Spacer(Modifier.height(10.dp))
          Text(
            text = "${strings.lastUpdatedLabel}: $formattedTime",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // APPLICATION INFORMATION
    item {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DrxSectionHeader(title = strings.appInfoSectionTitle)

        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(20.dp))
              .background(drxColors.cardSurface)
              .border(1.dp, drxColors.glassBorder, RoundedCornerShape(20.dp))
              .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          AppInfoKeyRow("Application Name", "DRX GAME TURBO PRO")
          AppInfoKeyRow("Developer", "DerreX")
          AppInfoKeyRow("Supported By", "Nanay")
          AppInfoKeyRow("Category", "Gaming Companion")
          AppInfoKeyRow("Platform", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
          AppInfoKeyRow("Version", "v${BuildConfig.VERSION_NAME}")
          AppInfoKeyRow("Build Number", "#${BuildConfig.VERSION_CODE}")
          AppInfoKeyRow("Release Date", BuildConfig.BUILD_DATE)

          DrxHairlineDivider(modifier = Modifier.padding(vertical = 6.dp))

          val hasUnreadLog =
            DrxUpdateCatalog.entries.any { !prefs.readUpdateVersions.contains(it.versionNumber) }
          ActionLinkRow(
            label = strings.updateLogTitle,
            icon = Icons.Default.Update,
            accent = drxColors.neonCyan,
            badgeText = if (hasUnreadLog) strings.unreadBadgeLabel else null,
            onClick = onOpenUpdateLog,
            testTag = "about_update_log_btn",
          )
          ActionLinkRow(
            label = strings.privacyPolicyBtn,
            icon = Icons.Outlined.Security,
            accent = drxColors.optimalGreen,
            onClick = { activeModal = AboutModalType.PRIVACY_POLICY },
            testTag = "about_privacy_policy_btn",
          )
          ActionLinkRow(
            label = strings.termsServiceBtn,
            icon = Icons.Outlined.Gavel,
            accent = drxColors.neonCyan,
            onClick = { activeModal = AboutModalType.TERMS_OF_SERVICE },
            testTag = "about_terms_btn",
          )
          ActionLinkRow(
            label = strings.openSourceLicensesBtn,
            icon = Icons.Outlined.Description,
            accent = drxColors.electricBlue,
            onClick = { activeModal = AboutModalType.OPEN_SOURCE_LICENSES },
            testTag = "about_licenses_btn",
          )
          ActionLinkRow(
            label = strings.checkUpdatesBtn,
            icon = Icons.Default.SystemUpdate,
            accent = drxColors.electricBlue,
            onClick = {
              if (onOpenUpdateCenter != null) onOpenUpdateCenter()
              else activeModal = AboutModalType.CHECK_UPDATES
            },
            testTag = "about_check_updates_btn",
          )
        }
      }
    }

    // FOOTER
    item {
      val currentYear = Calendar.getInstance().get(Calendar.YEAR)
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(drxColors.secondaryBg)
            .border(1.dp, drxColors.glassBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .testTag("about_footer"),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = "DRX GAME TURBO PRO",
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
          ),
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
          text = "DEVELOPED BY DERREX • SUPPORTED BY NANAY",
          style = DrxTypographyTokens.caption,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
          text = "\"PLAY SMARTER. GAME FASTER.\"",
          style = MaterialTheme.typography.labelLarge.copy(
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
          ),
          color = drxColors.neonCyan,
          textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
          text = "Copyright © $currentYear DerreX. All Rights Reserved.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    }
  }

  activeModal?.let { modal ->
    AboutDetailModal(
      modalType = modal,
      context = context,
      onDismiss = { activeModal = null },
    )
  }
}

@Composable
private fun SocialConnectionCard(
  platformLabel: String,
  accountTitle: String,
  accountSubtitle: String,
  metricText: String?,
  ctaLabel: String,
  accentColor: Color,
  iconContent: @Composable () -> Unit,
  onOpenLink: () -> Unit,
  onCopyHandle: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val drxColors = LocalDrxColors.current
  val shape = RoundedCornerShape(20.dp)
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(shape)
        .background(drxColors.cardSurface)
        .border(1.dp, drxColors.glassBorder, shape)
        .padding(16.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      iconContent()
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = platformLabel,
          style = MaterialTheme.typography.labelMedium,
          color = accentColor,
        )
        Text(
          text = accountTitle,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = if (metricText != null) "$accountSubtitle • $metricText" else accountSubtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(
        onClick = onCopyHandle,
        modifier = Modifier.size(44.dp),
      ) {
        Icon(
          imageVector = Icons.Default.ContentCopy,
          contentDescription = "Copy $accountTitle",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
      }
    }

    Spacer(Modifier.height(12.dp))
    GradientActionButton(
      label = ctaLabel,
      gradient =
        Brush.horizontalGradient(
          listOf(drxColors.electricBlueDeep, drxColors.electricBlue)
        ),
      icon = Icons.AutoMirrored.Filled.OpenInNew,
      onClick = onOpenLink,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

@Composable
private fun GradientActionButton(
  label: String,
  gradient: Brush,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      if (pressed) 0.98f else 1f,
      animationSpec = tween(100),
      label = "about_btn_scale",
    )

  Box(
    modifier =
      modifier
        .height(46.dp)
        .scale(scale)
        .clip(RoundedCornerShape(14.dp))
        .background(gradient)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
      modifier = Modifier.padding(horizontal = 14.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(16.dp),
      )
      Spacer(Modifier.width(8.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun CounterTile(
  title: String,
  value: String,
  sub: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  val drxColors = LocalDrxColors.current
  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(14.dp))
        .background(drxColors.elevatedSurface)
        .border(1.dp, drxColors.glassBorder, RoundedCornerShape(14.dp))
        .padding(10.dp),
    horizontalAlignment = Alignment.Start,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(4.dp))
    Text(
      text = value,
      style = DrxTypographyTokens.numericReadout,
      color = accent,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(2.dp))
    Text(
      text = sub,
      style = DrxTypographyTokens.caption,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun AppInfoKeyRow(
  label: String,
  value: String,
) {
  val drxColors = LocalDrxColors.current
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (label == "Developer") {
        Icon(
          Icons.Default.Verified,
          contentDescription = null,
          tint = drxColors.neonCyan,
          modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
      }
      Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun ActionLinkRow(
  label: String,
  icon: ImageVector,
  accent: Color,
  onClick: () -> Unit,
  testTag: String,
  badgeText: String? = null,
) {
  val drxColors = LocalDrxColors.current
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(drxColors.elevatedSurface)
        .border(1.dp, drxColors.glassBorder, RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 12.dp)
        .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = accent,
      modifier = Modifier.size(18.dp),
    )
    Spacer(Modifier.width(10.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )
    if (!badgeText.isNullOrBlank()) {
      DrxStatusPill(text = badgeText, color = accent)
      Spacer(Modifier.width(6.dp))
    }
    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(18.dp),
    )
  }
}
