package com.example.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PackageInstallStatusEvent(
  val statusCode: Int = PackageInstaller.STATUS_FAILURE,
  val message: String = "",
  val requiresUserAction: Boolean = false,
  val succeeded: Boolean = false,
  val timestampMs: Long = 0L,
)

object PackageInstallStatusBus {
  private val _latestEvent = MutableStateFlow<PackageInstallStatusEvent?>(null)
  val latestEvent: StateFlow<PackageInstallStatusEvent?> = _latestEvent.asStateFlow()

  fun publish(event: PackageInstallStatusEvent) {
    _latestEvent.value = event
  }

  fun clear() {
    _latestEvent.value = null
  }
}

class DrxPackageInstallReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
    val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()

    when (status) {
      PackageInstaller.STATUS_PENDING_USER_ACTION -> {
        @Suppress("DEPRECATION")
        val confirmIntent =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
          } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
          }
        if (confirmIntent != null) {
          confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          runCatching { context.startActivity(confirmIntent) }
        }
        PackageInstallStatusBus.publish(
          PackageInstallStatusEvent(
            statusCode = status,
            message = "Awaiting Android system package installer confirmation.",
            requiresUserAction = true,
            succeeded = false,
            timestampMs = System.currentTimeMillis(),
          )
        )
      }
      PackageInstaller.STATUS_SUCCESS -> {
        PackageInstallStatusBus.publish(
          PackageInstallStatusEvent(
            statusCode = status,
            message = "Installation completed successfully.",
            requiresUserAction = false,
            succeeded = true,
            timestampMs = System.currentTimeMillis(),
          )
        )
      }
      PackageInstaller.STATUS_FAILURE_ABORTED -> {
        PackageInstallStatusBus.publish(
          PackageInstallStatusEvent(
            statusCode = status,
            message = msg.ifBlank { "Installation cancelled by user." },
            requiresUserAction = false,
            succeeded = false,
            timestampMs = System.currentTimeMillis(),
          )
        )
      }
      else -> {
        PackageInstallStatusBus.publish(
          PackageInstallStatusEvent(
            statusCode = status,
            message = msg.ifBlank { "Package installation failed (status=$status)." },
            requiresUserAction = false,
            succeeded = false,
            timestampMs = System.currentTimeMillis(),
          )
        )
      }
    }
  }
}
