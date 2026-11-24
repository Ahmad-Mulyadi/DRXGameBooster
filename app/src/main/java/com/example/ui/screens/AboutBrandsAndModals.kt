package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.BuildConfig
import com.example.data.remote.SocialLinks
import com.example.system.DrxImageUtils
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.DrxCustomModal
import com.example.ui.theme.DrxPrimaryButton
import com.example.ui.theme.DrxSecondaryButton
import com.example.ui.theme.DrxStatusPill
import com.example.ui.theme.GraphiteBg
import com.example.ui.theme.LocalDrxColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val NeonPurple = Color(0xFF7B8BFF)
internal val NeonPurpleDeep = Color(0xFF4F62C7)
internal val AmoledBlack = GraphiteBg
internal val MetallicBorder = BorderHairline

internal enum class AboutModalType {
  PRIVACY_POLICY,
  TERMS_OF_SERVICE,
  OPEN_SOURCE_LICENSES,
  CHECK_UPDATES,
}

internal fun openExternalUrl(context: Context, url: String) {
  try {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
  } catch (_: Throwable) {
    Toast.makeText(context, url, Toast.LENGTH_SHORT).show()
  }
}

@Composable
internal fun DerrexGlowingEmblem(
  size: Dp = 80.dp,
  animationsEnabled: Boolean = true,
) {
  val drxColors = LocalDrxColors.current
  val accentAlpha = if (animationsEnabled) 0.72f else 0.56f

  Box(
    modifier = Modifier.size(size),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val center = Offset(this.size.width / 2f, this.size.height / 2f)
      val radius = this.size.minDimension / 2f

      drawCircle(
        brush =
          Brush.radialGradient(
            listOf(
              drxColors.electricBlue.copy(alpha = 0.22f),
              Color.Transparent,
            )
          ),
        radius = radius,
        center = center,
      )

      val frameW = this.size.width * 0.80f
      val frameH = this.size.height * 0.80f
      val left = (this.size.width - frameW) / 2f
      val top = (this.size.height - frameH) / 2f
      drawRoundRect(
        color = drxColors.elevatedSurface,
        topLeft = Offset(left, top),
        size = Size(frameW, frameH),
        cornerRadius = CornerRadius(frameW * 0.28f, frameW * 0.28f),
      )
      drawRoundRect(
        brush =
          Brush.linearGradient(
            listOf(
              drxColors.neonCyan.copy(alpha = accentAlpha),
              drxColors.electricBlue,
            )
          ),
        topLeft = Offset(left, top),
        size = Size(frameW, frameH),
        cornerRadius = CornerRadius(frameW * 0.28f, frameW * 0.28f),
        style = Stroke(width = 1.6.dp.toPx()),
      )
    }
    Text(
      text = "DRX",
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp),
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
internal fun SupporterAvatarBadge(
  avatarPath: String,
  size: Dp = 70.dp,
  glowAlpha: Float = 0.5f,
) {
  val drxColors = LocalDrxColors.current
  val customBitmap by
    produceState<ImageBitmap?>(initialValue = null, key1 = avatarPath) {
      value =
        if (avatarPath.isBlank()) {
          null
        } else {
          withContext(Dispatchers.IO) {
            DrxImageUtils.decodeSampledImageBitmap(avatarPath, maxWidthPx = 512, maxHeightPx = 512)
          }
        }
    }

  Box(
    modifier =
      Modifier
        .size(size)
        .clip(CircleShape)
        .background(drxColors.elevatedSurface)
        .border(
          width = 1.5.dp,
          brush =
            Brush.linearGradient(
              listOf(
                drxColors.electricBlue.copy(alpha = glowAlpha),
                drxColors.neonCyan.copy(alpha = glowAlpha),
              )
            ),
          shape = CircleShape,
        ),
    contentAlignment = Alignment.Center,
  ) {
    val avatarBmp = customBitmap
    if (avatarBmp != null) {
      Image(
        bitmap = avatarBmp,
        contentDescription = "Nanay Supporter Avatar",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().clip(CircleShape),
      )
    } else {
      Text(
        text = "N",
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
internal fun YouTubeBrandIcon(modifier: Modifier = Modifier.size(40.dp)) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = Color(0xFFFF0033).copy(alpha = 0.14f),
    border = BorderStroke(1.dp, Color(0xFFFF0033).copy(alpha = 0.35f)),
    modifier = modifier,
  ) {
    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
      drawRoundRect(
        color = Color(0xFFFF0033),
        topLeft = Offset(0f, size.height * 0.15f),
        size = Size(size.width, size.height * 0.70f),
        cornerRadius = CornerRadius(size.width * 0.22f, size.width * 0.22f),
      )
      val tri =
        Path().apply {
          moveTo(size.width * 0.40f, size.height * 0.34f)
          lineTo(size.width * 0.68f, size.height * 0.50f)
          lineTo(size.width * 0.40f, size.height * 0.66f)
          close()
        }
      drawPath(tri, color = Color.White)
    }
  }
}

@Composable
internal fun TikTokBrandIcon(modifier: Modifier = Modifier.size(40.dp)) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = Color(0xFF10141C),
    border = BorderStroke(1.dp, Color(0x28FFFFFF)),
    modifier = modifier,
  ) {
    Canvas(modifier = Modifier.fillMaxSize().padding(9.dp)) {
      val cyan = Color(0xFF25F4EE)
      val pink = Color(0xFFFE2C55)
      fun drawNote(dx: Float, dy: Float, color: Color) {
        val stemX = size.width * 0.54f + dx
        drawLine(
          color = color,
          start = Offset(stemX, size.height * 0.16f + dy),
          end = Offset(stemX, size.height * 0.68f + dy),
          strokeWidth = 2.4.dp.toPx(),
          cap = StrokeCap.Round,
        )
        drawCircle(
          color = color,
          radius = size.minDimension * 0.20f,
          center = Offset(size.width * 0.36f + dx, size.height * 0.68f + dy),
          style = Stroke(width = 2.2.dp.toPx()),
        )
        val flag =
          Path().apply {
            moveTo(stemX, size.height * 0.18f + dy)
            quadraticTo(
              size.width * 0.68f + dx,
              size.height * 0.34f + dy,
              size.width * 0.84f + dx,
              size.height * 0.34f + dy,
            )
          }
        drawPath(flag, color = color, style = Stroke(width = 2.3.dp.toPx(), cap = StrokeCap.Round))
      }
      drawNote(-1.2.dp.toPx(), -0.8.dp.toPx(), cyan)
      drawNote(1.2.dp.toPx(), 0.8.dp.toPx(), pink)
      drawNote(0f, 0f, Color.White)
    }
  }
}

@Composable
internal fun WhatsAppBrandIcon(modifier: Modifier = Modifier.size(40.dp)) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = Color(0xFF25D366).copy(alpha = 0.14f),
    border = BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.35f)),
    modifier = modifier,
  ) {
    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
      val green = Color(0xFF25D366)
      val r = size.minDimension * 0.42f
      val center = Offset(size.width * 0.52f, size.height * 0.48f)
      drawCircle(
        color = green,
        radius = r,
        center = center,
        style = Stroke(width = 2.2.dp.toPx()),
      )
      val tail =
        Path().apply {
          moveTo(center.x - r * 0.62f, center.y + r * 0.55f)
          lineTo(center.x - r * 0.96f, center.y + r * 0.98f)
          lineTo(center.x - r * 0.18f, center.y + r * 0.85f)
        }
      drawPath(
        tail,
        color = green,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
      )
      val handset =
        Path().apply {
          moveTo(center.x - r * 0.32f, center.y - r * 0.34f)
          quadraticTo(
            center.x - r * 0.15f,
            center.y + r * 0.25f,
            center.x + r * 0.36f,
            center.y + r * 0.32f,
          )
        }
      drawPath(
        handset,
        color = Color.White,
        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
      )
    }
  }
}

@Composable
internal fun AboutDetailModal(
  modalType: AboutModalType,
  context: Context,
  onDismiss: () -> Unit,
) {
  val drxColors = LocalDrxColors.current
  val title =
    when (modalType) {
      AboutModalType.PRIVACY_POLICY -> "Privacy Policy"
      AboutModalType.TERMS_OF_SERVICE -> "Terms of Service"
      AboutModalType.OPEN_SOURCE_LICENSES -> "Open Source Licenses"
      AboutModalType.CHECK_UPDATES -> "Check for Updates"
    }

  DrxCustomModal(title = title, onDismiss = onDismiss) {
    Column(
      modifier = Modifier.fillMaxWidth().height(290.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      when (modalType) {
        AboutModalType.PRIVACY_POLICY -> {
          DrxStatusPill(text = "100% LOCAL PROCESSING", color = drxColors.optimalGreen)
          Text(
            "DRX GAME TURBO PRO dibangun dengan prinsip privasi penuh di perangkat pengguna:",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            "1. Semua riwayat sesi game, profil performa, catatan taktis, dan preferensi disimpan secara lokal menggunakan Android Room & DataStore.\n\n" +
              "2. Aplikasi tidak mengumpulkan data pribadi, tidak memindai isi layar dengan AccessibilityService, dan tidak mengirim telemetri pengguna ke server pelacakan.\n\n" +
              "3. Izin sensitif (Overlay, Write Settings, Do Not Disturb, Usage Access, Record Audio, Notifications) hanya aktif atas persetujuan eksplisit pengguna dan dapat dicabut kapan saja melalui Setelan Android.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        AboutModalType.TERMS_OF_SERVICE -> {
          DrxStatusPill(text = "ANDROID SYSTEM COMPLIANCE", color = drxColors.neonCyan)
          Text(
            "Ketentuan Penggunaan DRX GAME TURBO PRO oleh DerreX:",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            "1. DRX GAME TURBO PRO adalah aplikasi Gaming Companion resmi yang hanya memanfaatkan API resmi Android tanpa root, tanpa modifikasi file game (.apk/.obb), dan tanpa intervensi tidak sah.\n\n" +
              "2. Fitur seperti penggunaan CPU (%) atau injeksi audio ke proses eksternal mengikuti kebijakan keamanan Android OEM pada masing-masing perangkat.\n\n" +
              "3. Tautan sosial resmi milik DerreX dan Nanay ditujukan untuk informasi komunitas dan pembaruan resmi.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        AboutModalType.OPEN_SOURCE_LICENSES -> {
          DrxStatusPill(text = "APACHE LICENSE 2.0", color = drxColors.electricBlue)
          val libs =
            listOf(
              "Jetpack Compose & Material 3 (androidx.compose.*)" to "Google / Android Open Source Project — Apache 2.0",
              "AndroidX Navigation Compose" to "Google / AOSP — Apache 2.0",
              "AndroidX Room & DataStore" to "Google / AOSP — Apache 2.0",
              "Kotlinx Coroutines" to "JetBrains — Apache 2.0",
              "Manrope & Inter Typography" to "Google Fonts — SIL Open Font License 1.1",
            )
          libs.forEach { (name, license) ->
            Column(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .background(drxColors.elevatedSurface)
                  .padding(12.dp)
            ) {
              Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
              Text(license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
        AboutModalType.CHECK_UPDATES -> {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = drxColors.optimalGreen)
            Spacer(Modifier.width(8.dp))
            Text(
              "Build Terpasang: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          Text(
            "Package ID: ${BuildConfig.APPLICATION_ID}\nRelease Date: ${BuildConfig.BUILD_DATE}\nDeveloper: DerreX",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            "Aplikasi ini berada pada build produksi saat ini. Periksa rilis terbaru secara resmi melalui Google Play Store atau DRX Official Channel.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(4.dp))
          DrxPrimaryButton(
            label = "Cek di Google Play Store",
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            onClick = {
              openExternalUrl(
                context,
                "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}",
              )
            },
            modifier = Modifier.fillMaxWidth(),
          )
          DrxSecondaryButton(
            label = "DRX Official WhatsApp Channel",
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            onClick = { openExternalUrl(context, SocialLinks.WHATSAPP_URL) },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}
