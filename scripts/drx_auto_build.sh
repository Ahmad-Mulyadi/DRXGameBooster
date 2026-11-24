#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SRC_DIR="$ROOT_DIR/src"
APK_DIR="$SRC_DIR/apk"
AAB_DIR="$SRC_DIR/aab"
LOGS_DIR="$SRC_DIR/logs"
LOG_FILE="$LOGS_DIR/build-log.txt"
STAGING_DIR="$ROOT_DIR/.drx_build_staging"

VERSION_NAME="$(sed -nE 's/.*versionName *= *"([^"]+)".*/\1/p' "$ROOT_DIR/app/build.gradle.kts" | head -n1)"
VERSION_NAME="${VERSION_NAME:-1.20.0}"

APK_VERSIONED_NAME="DRX-GAME-TURBO-PRO-v${VERSION_NAME}.apk"
APK_VERSIONED_OUT="$APK_DIR/$APK_VERSIONED_NAME"
APK_OUT="$APK_DIR/DRX-GAME-TURBO-PRO.apk"
APK_DEBUG_OUT="$APK_DIR/DRX-GAME-TURBO-PRO-DEBUG.apk"
AAB_OUT="$AAB_DIR/DRX-GAME-TURBO-PRO.aab"
README_OUT="$SRC_DIR/README-BUILD.txt"

mkdir -p "$APK_DIR" "$AAB_DIR" "$LOGS_DIR"
rm -rf "$STAGING_DIR"
mkdir -p "$STAGING_DIR"

cleanup_on_exit() {
  rm -rf "$STAGING_DIR"
}
trap cleanup_on_exit EXIT

TMP_LOG="$STAGING_DIR/build-log.tmp"
touch "$TMP_LOG"

log_line() {
  echo "[$(date -u '+%Y-%m-%d %H:%M:%S UTC')] $*" | tee -a "$TMP_LOG"
}

sanitize_log_secrets() {
  sed -i -E 's/(STORE_PASSWORD|KEY_PASSWORD)=[^[:space:]]+/\1=[REDACTED]/g' "$1" || true
}

verify_zip_artifact() {
  local file="$1"
  local label="$2"
  local min_bytes=102400
  if [ ! -f "$file" ]; then
    log_line "ERROR: Missing $label at $file"
    return 1
  fi
  local size
  size="$(wc -c < "$file")"
  if [ "$size" -lt "$min_bytes" ]; then
    log_line "ERROR: $label size too small ($size bytes < $min_bytes bytes)"
    return 1
  fi
  if ! unzip -tq "$file" >> "$TMP_LOG" 2>&1; then
    log_line "ERROR: ZIP integrity check failed for $label"
    return 1
  fi
  local zip_entries
  zip_entries="$(unzip -Z1 "$file" 2>/dev/null || true)"
  if [[ "${zip_entries,,}" != *"androidmanifest.xml"* ]]; then
    log_line "ERROR: AndroidManifest.xml missing in $label"
    return 1
  fi
  if command -v aapt2 >/dev/null 2>&1 && [[ "$file" == *.apk ]]; then
    aapt2 dump badging "$file" 2>/dev/null | head -n 3 >> "$TMP_LOG" || true
  fi
  log_line "VERIFIED: $label ($size bytes, SHA-256 $(sha256sum "$file" | awk '{print substr($1,1,16)}'))"
  return 0
}

log_line "=== DRX GAME TURBO PRO AUTO-BUILD STARTED ==="

HAS_RELEASE_KEYSTORE=0
KEYSTORE_TARGET="${KEYSTORE_PATH:-$ROOT_DIR/my-upload-key.jks}"
if [ -f "$KEYSTORE_TARGET" ] && [ -n "${STORE_PASSWORD:-}" ] && [ -n "${KEY_PASSWORD:-}" ]; then
  HAS_RELEASE_KEYSTORE=1
  log_line "Release keystore detected at $KEYSTORE_TARGET — building signed release artifacts."
else
  log_line "No release upload keystore + passwords found — running tests, debug APK, and R8 release intermediary AAB."
fi

GRADLE_TASKS=":app:testDebugUnitTest :app:assembleDebug :app:packageReleaseBundle"
if [ "$HAS_RELEASE_KEYSTORE" -eq 1 ]; then
  GRADLE_TASKS=":app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:bundleRelease"
fi

log_line "Executing: gradle $GRADLE_TASKS --console=plain"
if ! gradle $GRADLE_TASKS --console=plain >> "$TMP_LOG" 2>&1; then
  log_line "BUILD FAILED: Gradle command failed. Preserving previous validated artifacts."
  sanitize_log_secrets "$TMP_LOG"
  cp "$TMP_LOG" "$LOG_FILE"
  exit 1
fi

DEBUG_APK_SRC="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ "$HAS_RELEASE_KEYSTORE" -eq 1 ]; then
  PRIMARY_APK_SRC="$ROOT_DIR/app/build/outputs/apk/release/app-release.apk"
  PRIMARY_AAB_SRC="$ROOT_DIR/app/build/outputs/bundle/release/app-release.aab"
  SIGNING_APK_NOTE="Release-signed with developer upload keystore"
  SIGNING_AAB_NOTE="Release-signed Play Console AAB with R8 minification & resource shrinking"
else
  PRIMARY_APK_SRC="$DEBUG_APK_SRC"
  PRIMARY_AAB_SRC="$ROOT_DIR/app/build/intermediates/intermediary_bundle/release/packageReleaseBundle/intermediary-bundle.aab"
  SIGNING_APK_NOTE="Debug-signed APK (valid for direct installation/testing; upload keystore not present)"
  SIGNING_AAB_NOTE="Unsigned R8-minified & resource-shrunk release intermediary AAB (sign with my-upload-key.jks before Play Console upload)"
fi

STAGED_VERSIONED_APK="$STAGING_DIR/$APK_VERSIONED_NAME"
STAGED_APK="$STAGING_DIR/DRX-GAME-TURBO-PRO.apk"
STAGED_DEBUG_APK="$STAGING_DIR/DRX-GAME-TURBO-PRO-DEBUG.apk"
STAGED_AAB="$STAGING_DIR/DRX-GAME-TURBO-PRO.aab"

cp "$PRIMARY_APK_SRC" "$STAGED_VERSIONED_APK"
cp "$PRIMARY_APK_SRC" "$STAGED_APK"
cp "$DEBUG_APK_SRC" "$STAGED_DEBUG_APK"
cp "$PRIMARY_AAB_SRC" "$STAGED_AAB"

verify_zip_artifact "$STAGED_VERSIONED_APK" "$APK_VERSIONED_NAME"
verify_zip_artifact "$STAGED_APK" "DRX-GAME-TURBO-PRO.apk"
verify_zip_artifact "$STAGED_DEBUG_APK" "DRX-GAME-TURBO-PRO-DEBUG.apk"
verify_zip_artifact "$STAGED_AAB" "DRX-GAME-TURBO-PRO.aab"

# Atomic replacement only after all builds, tests, and archive validations succeed
mv -f "$STAGED_VERSIONED_APK" "$APK_VERSIONED_OUT"
mv -f "$STAGED_APK" "$APK_OUT"
mv -f "$STAGED_DEBUG_APK" "$APK_DEBUG_OUT"
mv -f "$STAGED_AAB" "$AAB_OUT"

# Remove obsolete legacy build names and older versioned APKs cleanly
rm -f "$APK_DIR/DRX-APP-RELEASE.apk" "$AAB_DIR/DRX-APP-RELEASE.aab" "$AAB_DIR/DRX-APP-DEBUG.aab"
find "$APK_DIR" -maxdepth 1 -type f -name 'DRX-GAME-TURBO-PRO-v*.apk' ! -name "$APK_VERSIONED_NAME" -delete || true

APK_VERSIONED_SIZE="$(wc -c < "$APK_VERSIONED_OUT")"
APK_SIZE="$(wc -c < "$APK_OUT")"
APK_DEBUG_SIZE="$(wc -c < "$APK_DEBUG_OUT")"
AAB_SIZE="$(wc -c < "$AAB_OUT")"
APK_VERSIONED_SHA="$(sha256sum "$APK_VERSIONED_OUT" | awk '{print $1}')"
APK_SHA="$(sha256sum "$APK_OUT" | awk '{print $1}')"
APK_DEBUG_SHA="$(sha256sum "$APK_DEBUG_OUT" | awk '{print $1}')"
AAB_SHA="$(sha256sum "$AAB_OUT" | awk '{print $1}')"
BUILD_STAMP="$(date -u '+%Y-%m-%d %H:%M:%S UTC')"

sanitize_log_secrets "$TMP_LOG"
cp "$TMP_LOG" "$LOG_FILE"

# Run Automatic APK Change Detector & Update Manifest Generator
DETECTOR_SCRIPT="$ROOT_DIR/scripts/drx_apk_detector.py"
if [ -f "$DETECTOR_SCRIPT" ] && command -v python3 >/dev/null 2>&1; then
  log_line "Running Automatic APK Change Detector: $DETECTOR_SCRIPT"
  python3 "$DETECTOR_SCRIPT" >> "$TMP_LOG" 2>&1 || log_line "WARNING: APK detector reported warnings (see build-log)."
fi

(
  cd "$SRC_DIR"
  SUM_TARGETS=("apk/$APK_VERSIONED_NAME" "apk/DRX-GAME-TURBO-PRO.apk" "apk/DRX-GAME-TURBO-PRO-DEBUG.apk" "aab/DRX-GAME-TURBO-PRO.aab")
  [ -f "$SRC_DIR/update-manifest.json" ] && SUM_TARGETS+=("update-manifest.json")
  sha256sum "${SUM_TARGETS[@]}" > "$SRC_DIR/SHA256SUMS.txt"
)

cat > "$README_OUT" <<EOF
===============================================================================
DRX GAME TURBO PRO — STANDARDIZED BUILD & RELEASE REPORT
===============================================================================
Generated: $BUILD_STAMP

1. STANDARDIZED ARTIFACT OUTPUTS (v$VERSION_NAME)
-------------------------------------------------------------------------------
- src/apk/$APK_VERSIONED_NAME
    Size   : $APK_VERSIONED_SIZE bytes
    SHA256 : $APK_VERSIONED_SHA
    Status : VERIFIED ZIP + AndroidManifest.xml
    Signing: $SIGNING_APK_NOTE

- src/apk/DRX-GAME-TURBO-PRO.apk
    Size   : $APK_SIZE bytes
    SHA256 : $APK_SHA
    Status : VERIFIED ZIP + AndroidManifest.xml
    Signing: $SIGNING_APK_NOTE

- src/apk/DRX-GAME-TURBO-PRO-DEBUG.apk
    Size   : $APK_DEBUG_SIZE bytes
    SHA256 : $APK_DEBUG_SHA
    Status : VERIFIED ZIP + AndroidManifest.xml
    Signing: Debug-signed with debug.keystore

- src/aab/DRX-GAME-TURBO-PRO.aab
    Size   : $AAB_SIZE bytes
    SHA256 : $AAB_SHA
    Status : VERIFIED ZIP + AndroidManifest.xml
    Signing: $SIGNING_AAB_NOTE

- src/update-manifest.json & src/apk/update-manifest.json
    Notes  : Auto-generated APK metadata manifest (appId, versionCode, versionName,
             fileSize, sha256, signingCertSha256, releaseNotes, minSdkVersion).

- src/SHA256SUMS.txt
    Notes  : Standard SHA-256 manifest (verify via: cd src && sha256sum -c SHA256SUMS.txt)

- src/logs/build-log.txt
    Notes  : Complete Gradle test + build + APK detector + verification log.

2. PIPELINE COMMANDS EXECUTED
-------------------------------------------------------------------------------
gradle $GRADLE_TASKS --console=plain
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
EOF

log_line "=== AUTO-BUILD COMPLETED SUCCESSFULLY ==="
cp "$TMP_LOG" "$LOG_FILE"

