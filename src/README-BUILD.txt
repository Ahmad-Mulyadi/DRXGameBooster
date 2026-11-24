===============================================================================
DRX GAME TURBO PRO — STANDARDIZED BUILD & RELEASE REPORT
===============================================================================
Generated: 2026-11-24 10:40:16 UTC

1. STANDARDIZED ARTIFACT OUTPUTS (v1.20.0)
-------------------------------------------------------------------------------
- src/apk/DRX-GAME-TURBO-PRO-v1.20.0.apk
    Size   : 21937237 bytes
    SHA256 : 3778cf24b4d88b146d6ae10f430f29f66b11a789fe7e4274c4ce32c601a25300
    Status : VERIFIED ZIP + AndroidManifest.xml
    Signing: Debug-signed APK (valid for direct installation/testing; upload keystore not present)

- src/apk/DRX-GAME-TURBO-PRO.apk
    Size   : 21937237 bytes
    SHA256 : 3778cf24b4d88b146d6ae10f430f29f66b11a789fe7e4274c4ce32c601a25300
    Status : VERIFIED ZIP + AndroidManifest.xml
    Signing: Debug-signed APK (valid for direct installation/testing; upload keystore not present)

- src/apk/DRX-GAME-TURBO-PRO-DEBUG.apk
    Size   : 21937237 bytes
    SHA256 : 3778cf24b4d88b146d6ae10f430f29f66b11a789fe7e4274c4ce32c601a25300
    Status : VERIFIED ZIP + AndroidManifest.xml
    Signing: Debug-signed with debug.keystore

- src/aab/DRX-GAME-TURBO-PRO.aab
    Size   : 59489486 bytes
    SHA256 : fd785df096587938f62e5933e16d20d9ddcde8308673d4b74e6560b94918ca1b
    Status : VERIFIED ZIP + AndroidManifest.xml
    Signing: Unsigned R8-minified & resource-shrunk release intermediary AAB (sign with my-upload-key.jks before Play Console upload)

- src/update-manifest.json & src/apk/update-manifest.json
    Notes  : Auto-generated APK metadata manifest (appId, versionCode, versionName,
             fileSize, sha256, signingCertSha256, releaseNotes, minSdkVersion).

- src/SHA256SUMS.txt
    Notes  : Standard SHA-256 manifest (verify via: cd src && sha256sum -c SHA256SUMS.txt)

- src/logs/build-log.txt
    Notes  : Complete Gradle test + build + APK detector + verification log.

2. PIPELINE COMMANDS EXECUTED
-------------------------------------------------------------------------------
gradle :app:testDebugUnitTest :app:assembleDebug :app:packageReleaseBundle --console=plain
unzip -tq verified on all APK/AAB artifacts prior to atomic replacement.
python3 scripts/drx_apk_detector.py

3. HTTPS SMART UPDATE DISTRIBUTION BRIDGE
-------------------------------------------------------------------------------
End-user devices cannot read local developer project folders directly over the
internet. To enable automatic OTA updates for installed users:
  1) Export DRX_UPDATE_BASE_URL="https://updates.example.com/drx/apk"
  2) Re-run ./scripts/drx_auto_build.sh (or python3 scripts/drx_apk_detector.py)
  3) Upload src/apk/*.apk and src/apk/update-manifest.json to your HTTPS host
     (or set DRX_PUBLISH_HOOK=/path/to/upload.sh).
  4) Point the app Update Center manifest URL to https://.../update-manifest.json.

4. DOWNLOAD & EXPORT WORKFLOW (IF BROWSER FILE DOWNLOAD TIMES OUT)
-------------------------------------------------------------------------------
1) In-App Export: Open Tools -> Download Center -> Prepare Verified APK -> Save APK (SAF) or Share.
2) Workspace ZIP: Use AI Studio's Export as ZIP / Push to GitHub to download verified binaries intact.
3) Integrity Verification: Check SHA-256 against src/SHA256SUMS.txt.

5. PLAY CONSOLE RELEASE SIGNING NOTES
-------------------------------------------------------------------------------
To generate a Play-Store-signed release APK & AAB when ready:
  export KEYSTORE_PATH=/path/to/my-upload-key.jks
  export STORE_PASSWORD=...
  export KEY_PASSWORD=...
  ./scripts/drx_auto_build.sh
===============================================================================
