package com.example.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

data class SocialPlatformStatus(
  val platformKey: String,
  val displayMetric: String?, // Null means Data Unavailable / N/A (never fabricated)
  val isReachable: Boolean,
  val profileDisplayName: String? = null,
  val note: String? = null,
)

sealed class SocialStatsState {
  data class Loading(val lastUpdatedMs: Long? = null) : SocialStatsState()

  data class Loaded(
    val youtubeSubscribers: SocialPlatformStatus,
    val tiktokFollowers: SocialPlatformStatus,
    val supporterProfile: SocialPlatformStatus,
    val lastUpdatedMs: Long,
  ) : SocialStatsState()

  data class Error(
    val message: String,
    val lastUpdatedMs: Long?,
  ) : SocialStatsState()
}

object SocialLinks {
  const val YOUTUBE_NAME = "DerreXSA"
  const val YOUTUBE_HANDLE = "@DerreXSA"
  const val YOUTUBE_URL = "https://www.youtube.com/@DerreXSA"

  const val TIKTOK_USERNAME = "@ahmdmlyadi_"
  const val TIKTOK_URL = "https://www.tiktok.com/@ahmdmlyadi_"

  const val WHATSAPP_NAME = "DRX Official Channel"
  const val WHATSAPP_URL = "https://whatsapp.com/channel/0029Vb9MpkcBFLgMrfQwpm0R"

  const val SUPPORTER_NAME = "NANAY"
  const val SUPPORTER_TIKTOK = "@naila27302"
  const val SUPPORTER_URL = "https://www.tiktok.com/@naila27302"
}

class SocialMetricsRepository(private val context: Context) {

  fun isNetworkAvailable(): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  suspend fun fetchSocialMetrics(): SocialStatsState =
    withContext(Dispatchers.IO) {
      val now = System.currentTimeMillis()
      if (!isNetworkAvailable()) {
        return@withContext SocialStatsState.Error(
          message = "No active internet connection",
          lastUpdatedMs = now,
        )
      }

      try {
        val ytDeferred = async { queryEndpoint("youtube", SocialLinks.YOUTUBE_URL) }
        val ttDeferred = async { queryEndpoint("tiktok", SocialLinks.TIKTOK_URL) }
        val supDeferred = async { queryEndpoint("supporter", SocialLinks.SUPPORTER_URL) }

        val yt = ytDeferred.await()
        val tt = ttDeferred.await()
        val sup = supDeferred.await()

        if (!yt.isReachable && !tt.isReachable && !sup.isReachable) {
          SocialStatsState.Error(
            message = "Unable to reach social endpoints",
            lastUpdatedMs = now,
          )
        } else {
          SocialStatsState.Loaded(
            youtubeSubscribers = yt,
            tiktokFollowers = tt,
            supporterProfile = sup,
            lastUpdatedMs = now,
          )
        }
      } catch (t: Throwable) {
        SocialStatsState.Error(
          message = t.message ?: "Network error",
          lastUpdatedMs = now,
        )
      }
    }

  /**
   * Probes public URL reachability without scraping private APIs or fabricating follower counts.
   * Official subscriber/follower numbers require authenticated YouTube Data API v3 / TikTok Display API keys,
   * so if they are not legitimately provided, `displayMetric` stays null and the UI renders `Data Unavailable` / `N/A`.
   */
  private fun queryEndpoint(key: String, urlStr: String): SocialPlatformStatus {
    var conn: HttpURLConnection? = null
    return try {
      val url = URL(urlStr)
      conn =
        (url.openConnection() as HttpURLConnection).apply {
          requestMethod = "HEAD"
          connectTimeout = 4500
          readTimeout = 4500
          instanceFollowRedirects = true
          setRequestProperty("User-Agent", "DRX-GameTurboPro/1.0 Android")
        }
      val code = conn.responseCode
      val reachable = code in 200..399
      SocialPlatformStatus(
        platformKey = key,
        displayMetric = null, // Never fabricate subscriber/follower metrics without official API credentials
        isReachable = reachable,
        profileDisplayName =
          when (key) {
            "supporter" -> SocialLinks.SUPPORTER_NAME
            "youtube" -> SocialLinks.YOUTUBE_NAME
            else -> SocialLinks.TIKTOK_USERNAME
          },
        note = if (reachable) "HTTP $code • Official API required" else "HTTP $code",
      )
    } catch (_: Throwable) {
      SocialPlatformStatus(
        platformKey = key,
        displayMetric = null,
        isReachable = false,
        profileDisplayName = null,
        note = "Unreachable",
      )
    } finally {
      try {
        conn?.disconnect()
      } catch (_: Throwable) {
      }
    }
  }
}
