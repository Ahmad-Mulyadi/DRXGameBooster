package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.data.model.UpdateManifest
import com.example.data.model.UpdateOfferPolicyState
import com.example.data.prefs.DrxPreferences
import com.example.system.DrxUpdateManager
import com.example.system.ManifestEvaluationStatus
import com.example.system.UpdateCheckWorker
import com.example.system.UpdatePolicyEngine
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DrxApkDetectorAndUpdaterTests {

  @Test
  fun `scenario A unchanged apk version stays UP_TO_DATE and offers no update`() {
    val currentCert = "37744d29b11d9c94c255800d024040b8548f6cc8945e606ec8a594781f292e8c"
    val manifest =
      UpdateManifest(
        versionName = "1.9.0",
        versionCode = BuildConfig.VERSION_CODE,
        releaseDate = "2026-11-23",
        releaseNotes = "Current release",
        apkType = "release",
        downloadUrl = "",
        localFileName = "DRX-GAME-TURBO-PRO-v1.9.0.apk",
        fileSizeBytes = 21_657_569L,
        sha256 = "abc123",
        minSdkVersion = 24,
        appId = BuildConfig.APPLICATION_ID,
        signingCertSha256 = currentCert,
      )

    val eval =
      UpdatePolicyEngine.evaluateManifestAgainstInstalled(
        manifest = manifest,
        installedPackageName = BuildConfig.APPLICATION_ID,
        installedVersionCode = BuildConfig.VERSION_CODE,
        installedSigningCertSha256s = setOf(currentCert),
        hasLocalStagedFile = false,
        hasRemoteSourceConfigured = false,
        isOnline = true,
      )

    assertEquals(ManifestEvaluationStatus.UP_TO_DATE, eval.status)
    assertFalse(eval.isInstallableUpdate)
  }

  @Test
  fun `scenario B higher versionCode replacement detects v1_10_0 with size and checksum`() {
    val currentCert = "37744d29b11d9c94c255800d024040b8548f6cc8945e606ec8a594781f292e8c"
    val json =
      """
      {
        "appId": "${BuildConfig.APPLICATION_ID}",
        "versionName": "1.10.0",
        "versionCode": 20,
        "apkFileName": "DRX-GAME-TURBO-PRO-v1.10.0.apk",
        "downloadUrl": "https://updates.example.com/drx/DRX-GAME-TURBO-PRO-v1.10.0.apk",
        "fileSize": 22345678,
        "sha256": "deadbeef0123456789abcdef0123456789abcdef0123456789abcdef01234567",
        "signingCertSha256": "$currentCert",
        "releaseDate": "2026-12-01",
        "releaseNotes": "Smart Auto Update & Engine Optimizations",
        "minimumSupportedVersion": 24
      }
      """.trimIndent()

    val parsed = UpdatePolicyEngine.parseManifestJson(json)
    assertEquals(1, parsed.size)
    val target = parsed.first()
    assertEquals("1.10.0", target.versionName)
    assertEquals(20, target.versionCode)
    assertEquals("DRX-GAME-TURBO-PRO-v1.10.0.apk", target.apkFileName)
    assertEquals(22_345_678L, target.fileSize)
    assertEquals(currentCert, target.signingCertSha256)

    val eval =
      UpdatePolicyEngine.evaluateManifestAgainstInstalled(
        manifest = target,
        installedPackageName = BuildConfig.APPLICATION_ID,
        installedVersionCode = BuildConfig.VERSION_CODE,
        installedSigningCertSha256s = setOf(currentCert),
        hasLocalStagedFile = false,
        hasRemoteSourceConfigured = true,
        isOnline = true,
      )

    assertEquals(ManifestEvaluationStatus.UPDATE_AVAILABLE, eval.status)
    assertTrue(eval.isInstallableUpdate)
  }

  @Test
  fun `scenario C and D same or lower versionCode replacement is rejected with developer warning`() {
    val sameVcWithWarning =
      UpdateManifest(
        versionName = "1.9.0",
        versionCode = BuildConfig.VERSION_CODE,
        releaseDate = "2026-11-23",
        releaseNotes = "Rebuilt without bumping versionCode",
        apkType = "release",
        downloadUrl = "https://updates.example.com/drx/DRX.apk",
        localFileName = "DRX-GAME-TURBO-PRO-v1.9.0.apk",
        developerWarning =
          "Developer Warning: Replacement APK changed checksum without raising versionCode.",
      )
    val evalSame =
      UpdatePolicyEngine.evaluateManifestAgainstInstalled(
        manifest = sameVcWithWarning,
        installedPackageName = BuildConfig.APPLICATION_ID,
        installedVersionCode = BuildConfig.VERSION_CODE,
        hasRemoteSourceConfigured = true,
        isOnline = true,
      )
    assertEquals(ManifestEvaluationStatus.REJECTED_SAME_OR_LOWER_VERSION, evalSame.status)
    assertFalse(evalSame.isInstallableUpdate)
    assertTrue(evalSame.reason.contains("versionCode"))

    val lowerVc =
      UpdateManifest(
        versionName = "1.3.0",
        versionCode = (BuildConfig.VERSION_CODE - 1).coerceAtLeast(1),
        releaseDate = "2026-11-21",
        releaseNotes = "Old build",
        apkType = "release",
        downloadUrl = "https://updates.example.com/drx/DRX-old.apk",
      )
    val evalLower =
      UpdatePolicyEngine.evaluateManifestAgainstInstalled(
        manifest = lowerVc,
        installedPackageName = BuildConfig.APPLICATION_ID,
        installedVersionCode = BuildConfig.VERSION_CODE,
        hasRemoteSourceConfigured = true,
        isOnline = true,
      )
    assertEquals(ManifestEvaluationStatus.REJECTED_SAME_OR_LOWER_VERSION, evalLower.status)
    assertFalse(evalLower.isInstallableUpdate)
  }

  @Test
  fun `scenario E mismatched signing certificate or mismatched appId blocks update and verifyApkFile rejects`() {
    val installedCert = "37744d29b11d9c94c255800d024040b8548f6cc8945e606ec8a594781f292e8c"
    val attackerCert = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    val wrongCertManifest =
      UpdateManifest(
        versionName = "1.10.0",
        versionCode = 20,
        releaseDate = "2026-12-01",
        releaseNotes = "Resigned APK",
        apkType = "release",
        downloadUrl = "https://updates.example.com/drx/resigned.apk",
        appId = BuildConfig.APPLICATION_ID,
        signingCertSha256 = attackerCert,
      )

    val evalCert =
      UpdatePolicyEngine.evaluateManifestAgainstInstalled(
        manifest = wrongCertManifest,
        installedPackageName = BuildConfig.APPLICATION_ID,
        installedVersionCode = BuildConfig.VERSION_CODE,
        installedSigningCertSha256s = setOf(installedCert),
        hasRemoteSourceConfigured = true,
        isOnline = true,
      )
    assertEquals(ManifestEvaluationStatus.REJECTED_SIGNATURE_MISMATCH, evalCert.status)
    assertFalse(evalCert.isInstallableUpdate)

    val wrongPkgManifest =
      wrongCertManifest.copy(appId = "com.other.fakeapp", signingCertSha256 = installedCert)
    val evalPkg =
      UpdatePolicyEngine.evaluateManifestAgainstInstalled(
        manifest = wrongPkgManifest,
        installedPackageName = BuildConfig.APPLICATION_ID,
        installedVersionCode = BuildConfig.VERSION_CODE,
        installedSigningCertSha256s = setOf(installedCert),
        hasRemoteSourceConfigured = true,
        isOnline = true,
      )
    assertEquals(ManifestEvaluationStatus.REJECTED_APP_ID_MISMATCH, evalPkg.status)
    assertFalse(evalPkg.isInstallableUpdate)

    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = DrxUpdateManager(context)
    val realApk = File("src/apk/DRX-GAME-TURBO-PRO-v1.9.0.apk")
    if (realApk.exists()) {
      val sameVcReject =
        manager.verifyApkFile(
          apkFile = realApk,
          expectedPackageName = BuildConfig.APPLICATION_ID,
          requireStrictlyHigherThanVersionCode = BuildConfig.VERSION_CODE.toLong(),
        )
      assertFalse(sameVcReject.isValid)
      assertTrue(sameVcReject.sameOrLowerVersionCode)
    }
  }

  @Test
  fun `scenario F hosting unconfigured or offline returns honest non-installable status`() {
    val unconfiguredManifest =
      UpdateManifest(
        versionName = "1.10.0",
        versionCode = 20,
        releaseDate = "2026-12-01",
        releaseNotes = "Missing URL",
        apkType = "release",
        downloadUrl = "",
      )

    val evalUnconfigured =
      UpdatePolicyEngine.evaluateManifestAgainstInstalled(
        manifest = unconfiguredManifest,
        installedPackageName = BuildConfig.APPLICATION_ID,
        installedVersionCode = BuildConfig.VERSION_CODE,
        hasLocalStagedFile = false,
        hasRemoteSourceConfigured = false,
        isOnline = true,
      )
    assertEquals(ManifestEvaluationStatus.SOURCE_NOT_CONFIGURED, evalUnconfigured.status)
    assertFalse(evalUnconfigured.isInstallableUpdate)

    val onlineManifest =
      unconfiguredManifest.copy(downloadUrl = "https://updates.example.com/drx/v1.10.0.apk")
    val evalOffline =
      UpdatePolicyEngine.evaluateManifestAgainstInstalled(
        manifest = onlineManifest,
        installedPackageName = BuildConfig.APPLICATION_ID,
        installedVersionCode = BuildConfig.VERSION_CODE,
        hasLocalStagedFile = false,
        hasRemoteSourceConfigured = true,
        isOnline = false,
      )
    assertEquals(ManifestEvaluationStatus.OFFLINE, evalOffline.status)
    assertFalse(evalOffline.isInstallableUpdate)
  }

  @Test
  fun `scenario G update installed resets skip counter cache and evaluates UP_TO_DATE`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = DrxPreferences(context)
    prefs.recordUpdateSkip(20)
    prefs.recordUpdateSkip(20)
    assertEquals(2, prefs.prefsFlow.first().updateSkipCount)

    // Installed version reaches 20 -> skip count resets to 0
    prefs.syncOfferedUpdateVersion(targetVersionCode = 20, currentVersionCode = 20)
    val after = prefs.prefsFlow.first()
    assertEquals(0, after.updateSkipCount)
    assertEquals(0, after.skippedUpdateVersionCode)
    assertEquals(0, after.lastOfferedUpdateVersionCode)

    assertEquals(
      UpdateOfferPolicyState.NONE,
      UpdatePolicyEngine.evaluateSkipPolicy(
        currentVersionCode = 20,
        targetVersionCode = 20,
        lastOfferedVersionCode = after.lastOfferedUpdateVersionCode,
        skipCount = after.updateSkipCount,
      ),
    )

    runCatching { WorkManager.initialize(context, Configuration.Builder().build()) }
    UpdateCheckWorker.enqueuePeriodicCheck(context)
  }
}
