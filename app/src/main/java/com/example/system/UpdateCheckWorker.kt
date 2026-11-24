package com.example.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.BuildConfig
import com.example.MainActivity
import com.example.R
import com.example.data.prefs.DrxPreferences
import com.example.ui.screens.ToolRoutes
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class UpdateCheckWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  companion object {
    const val CHANNEL_ID = "drx_app_updates"
    private const val WORK_NAME = "drx_periodic_update_check"
    private const val NOTIFICATION_ID = 9401

    fun enqueuePeriodicCheck(context: Context) {
      val constraints =
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.CONNECTED)
          .build()
      val req =
        PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
          .setConstraints(constraints)
          .addTag(WORK_NAME)
          .build()
      runCatching {
        WorkManager.getInstance(context)
          .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
      }
    }

    fun postUpdateNotificationIfAllowed(
      context: Context,
      versionName: String,
      versionCode: Int,
      releaseNotes: String,
    ): Boolean {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted =
          ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return false
      }

      val nm = context.getSystemService(NotificationManager::class.java) ?: return false
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel =
          NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.update_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
          ).apply {
            description = context.getString(R.string.update_channel_desc)
          }
        nm.createNotificationChannel(channel)
      }

      val openIntent =
        Intent(context, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
          putExtra("open_route", ToolRoutes.UPDATE_CENTER)
        }
      val pi =
        PendingIntent.getActivity(
          context,
          NOTIFICATION_ID,
          openIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

      val summary = releaseNotes.trim().ifBlank { "DRX GAME TURBO PRO v$versionName (Build #$versionCode) is ready." }
      val notif =
        NotificationCompat.Builder(context, CHANNEL_ID)
          .setSmallIcon(R.drawable.ic_drx_notification)
          .setContentTitle("DRX Update Available: v$versionName")
          .setContentText(summary)
          .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
          .setContentIntent(pi)
          .setAutoCancel(true)
          .setPriority(NotificationCompat.PRIORITY_DEFAULT)
          .build()

      nm.notify(NOTIFICATION_ID, notif)
      return true
    }
  }

  override suspend fun doWork(): Result {
    return try {
      val prefs = DrxPreferences(applicationContext)
      val current = prefs.prefsFlow.first()
      val url = current.customUpdateManifestUrl.trim().ifBlank { BuildConfig.DEFAULT_UPDATE_MANIFEST_URL.trim() }
      if (url.isBlank()) return Result.success()

      val manager = DrxUpdateManager(applicationContext)
      if (!manager.isNetworkAvailable()) return Result.retry()

      val rawRes = manager.fetchRemoteManifestRaw(url)
      rawRes.fold(
        onSuccess = { rawJson ->
          val now = System.currentTimeMillis()
          prefs.saveCachedUpdateManifest(rawJson, now)
          val list =
            UpdatePolicyEngine.resolveDownloadUrlsAgainstManifest(
              UpdatePolicyEngine.parseManifestJson(rawJson),
              url,
            )
          val best = UpdatePolicyEngine.selectBestManifest(list)
          val existingFile = best?.let { manager.findExistingLocalUpdateApk(it) }
          val eval =
            UpdatePolicyEngine.evaluateManifestAgainstInstalled(
              manifest = best,
              installedPackageName = applicationContext.packageName,
              installedVersionCode = BuildConfig.VERSION_CODE,
              installedSigningCertSha256s = manager.getInstalledSigningCertDigests(),
              hasLocalStagedFile = existingFile != null,
              hasRemoteSourceConfigured = true,
              isOnline = true,
            )
          if (eval.isInstallableUpdate &&
            best != null &&
            best.versionCode > BuildConfig.VERSION_CODE &&
            best.versionCode != current.lastNotifiedUpdateVersionCode
          ) {
            if (postUpdateNotificationIfAllowed(
                context = applicationContext,
                versionName = best.versionName,
                versionCode = best.versionCode,
                releaseNotes = best.releaseNotes,
              )
            ) {
              prefs.markUpdateVersionNotified(best.versionCode)
            }
          }
          Result.success()
        },
        onFailure = { Result.retry() },
      )
    } catch (_: Throwable) {
      Result.success()
    }
  }
}
