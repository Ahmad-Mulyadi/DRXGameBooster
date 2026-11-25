package com.example.system

import com.example.data.model.SystemTelemetry
import com.example.data.model.ThermalGuardSnapshot
import com.example.data.model.ThermalTier

object ThermalGuardEngine {

  fun classify(tempC: Float, thermalStatusCode: Int): ThermalTier =
    when {
      tempC >= 43.0f || thermalStatusCode >= 3 -> ThermalTier.CRITICAL
      tempC >= 40.0f || thermalStatusCode == 2 -> ThermalTier.ELEVATED
      tempC >= 36.0f || thermalStatusCode == 1 -> ThermalTier.WARM
      else -> ThermalTier.NORMAL
    }

  fun thermalStatusLabel(code: Int, isIndo: Boolean): String =
    when (code) {
      0 -> if (isIndo) "Nominal (0)" else "None / Nominal (0)"
      1 -> if (isIndo) "Ringan (1)" else "Light (1)"
      2 -> if (isIndo) "Sedang (2)" else "Moderate (2)"
      3 -> if (isIndo) "Berat (3)" else "Severe (3)"
      4 -> if (isIndo) "Kritis (4)" else "Critical (4)"
      5 -> if (isIndo) "Darurat (5)" else "Emergency (5)"
      6 -> if (isIndo) "Shutdown (6)" else "Shutdown (6)"
      else -> if (isIndo) "Tidak Tersedia" else "Unavailable"
    }

  fun evaluate(
    telemetry: SystemTelemetry,
    sessionPeakTempC: Float,
    isIndo: Boolean,
  ): ThermalGuardSnapshot {
    val safeTemp = telemetry.batteryInfo.temperatureC.sanitizeFinite(30f)
    val safePeak = maxOf(safeTemp, sessionPeakTempC.sanitizeFinite(safeTemp))
    val tier = classify(safeTemp, telemetry.thermalStatusCode)
    val charging = telemetry.batteryInfo.isCharging
    val statusLbl = thermalStatusLabel(telemetry.thermalStatusCode, isIndo)

    val tips = buildList {
      if (charging && (tier == ThermalTier.WARM || tier == ThermalTier.ELEVATED || tier == ThermalTier.CRITICAL)) {
        add(
          if (isIndo) "Lepaskan charger selama sesi game berat untuk mengurangi panas pengisian daya."
          else "Unplug charger during heavy gameplay to remove charging heat."
        )
      }
      if (tier == ThermalTier.ELEVATED || tier == ThermalTier.CRITICAL) {
        add(
          if (isIndo) "Turunkan kecerahan layar dan pertimbangkan profil Balanced atau frame cap 60 FPS di dalam game."
          else "Lower screen brightness and switch to Balanced or cap in-game FPS to reduce SoC load."
        )
        add(
          if (isIndo) "Beri jeda pendinginan 5–10 menit di ruangan bersirkulasi baik (lepas casing tebal bila perlu)."
          else "Rest the device for 5–10 minutes in good airflow (remove thick protective case if needed)."
        )
      } else if (tier == ThermalTier.WARM) {
        add(
          if (isIndo) "Suhu mulai menghangat — tutup aplikasi latar belakang yang tidak dipakai."
          else "Temperature is warming up — keep background apps minimal."
        )
      } else {
        add(
          if (isIndo) "Suhu perangkat aman untuk sesi bermain optimal."
          else "Device thermal headroom looks healthy for optimal gameplay."
        )
      }
      add(
        if (isIndo) "Transparansi Android: DRX membaca sensor & PowerManager OS tanpa kontrol kipas atau undervolt perangkat keras."
        else "Android Note: DRX reads real OS battery & PowerManager sensors; Android exposes no app-level active cooling control."
      )
    }

    return ThermalGuardSnapshot(
      batteryTempC = safeTemp,
      thermalStatusCode = telemetry.thermalStatusCode,
      thermalStatusLabel = statusLbl,
      tier = tier,
      isCharging = charging,
      sessionPeakTempC = safePeak,
      adviceList = tips,
    )
  }
}
