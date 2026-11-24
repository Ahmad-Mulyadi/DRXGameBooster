#!/usr/bin/env python3
"""
DRX GAME TURBO PRO — Automatic APK Change Detector & Update Manifest Generator

Scans `src/apk/` for APK files, inspects real APK metadata via Android SDK build-tools
(`aapt2` and `apksigner` / `keytool`), computes SHA-256 checksums and file sizes,
compares against prior published state (`src/apk/.apk-detector-state.json`), flags
same/lower versionCode replacements or signing certificate mismatches, and generates
`src/update-manifest.json` and `src/apk/update-manifest.json`.
"""

from __future__ import annotations

import datetime as dt
import glob
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile
from typing import Any, Dict, List, Optional


ROOT_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
APK_DIR = os.environ.get("DRX_APK_DIR", os.path.join(ROOT_DIR, "src", "apk"))
SRC_DIR = os.path.dirname(APK_DIR)
STATE_FILE = os.path.join(APK_DIR, ".apk-detector-state.json")
OUT_MANIFEST_SRC = os.path.join(SRC_DIR, "update-manifest.json")
OUT_MANIFEST_APK = os.path.join(APK_DIR, "update-manifest.json")
BUILD_GRADLE = os.path.join(ROOT_DIR, "app", "build.gradle.kts")
UPDATE_LOG_KT = os.path.join(
    ROOT_DIR, "app", "src", "main", "java", "com", "example", "data", "model", "UpdateLogData.kt"
)


def find_sdk_tool(name: str) -> Optional[str]:
    direct = shutil.which(name)
    if direct:
        return direct
    sdk_root = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME") or "/opt/android/sdk"
    candidates = sorted(glob.glob(os.path.join(sdk_root, "build-tools", "*", name)), reverse=True)
    for path in candidates:
        if os.path.isfile(path) and os.access(path, os.X_OK):
            return path
    return None


def read_gradle_baseline() -> Dict[str, Any]:
    app_id = "com.derrex.gameturbo"
    version_code = 0
    version_name = "1.0.0"
    if os.path.isfile(BUILD_GRADLE):
        with open(BUILD_GRADLE, "r", encoding="utf-8") as fh:
            text = fh.read()
        m_id = re.search(r'applicationId\s*=\s*"([^"]+)"', text)
        m_vc = re.search(r"versionCode\s*=\s*(\d+)", text)
        m_vn = re.search(r'versionName\s*=\s*"([^"]+)"', text)
        if m_id:
            app_id = m_id.group(1).strip()
        if m_vc:
            version_code = int(m_vc.group(1))
        if m_vn:
            version_name = m_vn.group(1).strip()
    return {
        "appId": app_id,
        "versionCode": version_code,
        "versionName": version_name,
    }


def extract_release_notes(version_name: str) -> str:
    env_notes = os.environ.get("DRX_RELEASE_NOTES", "").strip()
    if env_notes:
        return env_notes
    if os.path.isfile(UPDATE_LOG_KT):
        with open(UPDATE_LOG_KT, "r", encoding="utf-8") as fh:
            content = fh.read()
        pattern = re.compile(
            r'versionNumber\s*=\s*"' + re.escape(version_name) + r'".*?title\s*=\s*"([^"]+)"',
            re.DOTALL,
        )
        match = pattern.search(content)
        if match:
            title = match.group(1).strip()
            block_end = content.find("AppUpdateEntry(", match.end())
            block = content[match.start() : block_end if block_end != -1 else len(content)]
            feats = re.findall(r'newFeatures\s*=\s*listOf\((.*?)\)', block, re.DOTALL)
            bullets: List[str] = []
            if feats:
                for item in re.findall(r'"([^"]{10,})"', feats[0])[:3]:
                    bullets.append(item.strip())
            if bullets:
                return f"{title}: " + " ".join(bullets)
            return title
    return f"DRX GAME TURBO PRO v{version_name} verified package."


def compute_sha256(file_path: str) -> str:
    h = hashlib.sha256()
    with open(file_path, "rb") as fh:
        while True:
            chunk = fh.read(65536)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


def verify_apk_zip(file_path: str) -> bool:
    try:
        with zipfile.ZipFile(file_path, "r") as zf:
            names = set(zf.namelist())
            has_manifest = any(n.lower() == "androidmanifest.xml" for n in names)
            if not has_manifest or len(names) < 3:
                return False
            bad = zf.testzip()
            return bad is None
    except Exception:
        return False


def inspect_badging(aapt2_bin: Optional[str], apk_path: str) -> Dict[str, Any]:
    info: Dict[str, Any] = {
        "appId": "",
        "versionCode": 0,
        "versionName": "",
        "minimumSupportedVersion": 24,
    }
    if not aapt2_bin:
        return info
    try:
        res = subprocess.run(
            [aapt2_bin, "dump", "badging", apk_path],
            capture_output=True,
            text=True,
            timeout=20,
            check=False,
        )
        out = res.stdout or ""
        m_pkg = re.search(
            r"package:\s+name='([^']+)'\s+versionCode='(\d+)'\s+versionName='([^']*)'",
            out,
        )
        if m_pkg:
            info["appId"] = m_pkg.group(1).strip()
            info["versionCode"] = int(m_pkg.group(2))
            info["versionName"] = m_pkg.group(3).strip()
        m_sdk = re.search(r"sdkVersion:'(\d+)'|minSdkVersion:'(\d+)'", out)
        if m_sdk:
            sdk_val = m_sdk.group(1) or m_sdk.group(2)
            if sdk_val:
                info["minimumSupportedVersion"] = int(sdk_val)
    except Exception:
        pass
    return info


def inspect_signing_cert(apksigner_bin: Optional[str], apk_path: str) -> Dict[str, Any]:
    cert_sha256 = ""
    cert_dn = ""
    verified = False
    if apksigner_bin:
        try:
            res = subprocess.run(
                [apksigner_bin, "verify", "--print-certs", apk_path],
                capture_output=True,
                text=True,
                timeout=20,
                check=False,
            )
            combined = (res.stdout or "") + "\n" + (res.stderr or "")
            m_sha = re.search(r"certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", combined)
            m_dn = re.search(r"certificate DN:\s*(.+)", combined)
            if m_sha:
                cert_sha256 = m_sha.group(1).replace(":", "").strip().lower()
            if m_dn:
                cert_dn = m_dn.group(1).strip()
            verified = res.returncode == 0 and bool(cert_sha256)
        except Exception:
            pass

    if not cert_sha256:
        # Fallback: inspect PKCS#7 block in META-INF via keytool
        try:
            keytool_bin = shutil.which("keytool")
            if keytool_bin:
                with zipfile.ZipFile(apk_path, "r") as zf:
                    cert_entries = [
                        n for n in zf.namelist()
                        if n.upper().startswith("META-INF/")
                        and n.upper().endswith((".RSA", ".DSA", ".EC"))
                    ]
                    if cert_entries:
                        cert_bytes = zf.read(cert_entries[0])
                        res = subprocess.run(
                            [keytool_bin, "-printcert"],
                            input=cert_bytes,
                            capture_output=True,
                            timeout=15,
                            check=False,
                        )
                        txt = (res.stdout or b"").decode("utf-8", errors="replace")
                        m_sha = re.search(r"SHA256:\s*([0-9A-Fa-f:]+)", txt)
                        m_owner = re.search(r"Owner:\s*(.+)", txt)
                        if m_sha:
                            cert_sha256 = m_sha.group(1).replace(":", "").strip().lower()
                            verified = True
                        if m_owner:
                            cert_dn = m_owner.group(1).strip()
        except Exception:
            pass

    return {
        "signingCertSha256": cert_sha256,
        "signingCertDn": cert_dn,
        "signatureVerified": verified,
    }


def load_previous_state() -> Dict[str, Any]:
    if not os.path.isfile(STATE_FILE):
        return {"files": {}, "highestPublishedVersionCode": 0, "primaryCertSha256": ""}
    try:
        with open(STATE_FILE, "r", encoding="utf-8") as fh:
            data = json.load(fh)
        if isinstance(data, dict):
            data.setdefault("files", {})
            data.setdefault("highestPublishedVersionCode", 0)
            data.setdefault("primaryCertSha256", "")
            return data
    except Exception:
        pass
    return {"files": {}, "highestPublishedVersionCode": 0, "primaryCertSha256": ""}


def scan_and_generate() -> Dict[str, Any]:
    os.makedirs(APK_DIR, exist_ok=True)
    baseline = read_gradle_baseline()
    expected_app_id = baseline["appId"]
    aapt2_bin = find_sdk_tool("aapt2")
    apksigner_bin = find_sdk_tool("apksigner")
    base_url = os.environ.get("DRX_UPDATE_BASE_URL", "").strip().rstrip("/")
    today = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%d")

    prev_state = load_previous_state()
    prev_files: Dict[str, Any] = prev_state.get("files", {})
    prev_highest_vc = int(prev_state.get("highestPublishedVersionCode", 0) or 0)
    prev_cert = str(prev_state.get("primaryCertSha256", "") or "").lower()

    apk_names = sorted(
        f for f in os.listdir(APK_DIR)
        if f.lower().endswith(".apk")
        and not f.startswith(".")
        and not f.lower().endswith(".tmp.apk")
        and os.path.isfile(os.path.join(APK_DIR, f))
    )

    warnings: List[str] = []
    valid_artifacts: List[Dict[str, Any]] = []
    new_file_state: Dict[str, Any] = {}
    any_change_detected = False

    for fname in apk_names:
        full_path = os.path.join(APK_DIR, fname)
        file_size = os.path.getsize(full_path)
        sha256 = compute_sha256(full_path)
        zip_ok = verify_apk_zip(full_path)
        badging = inspect_badging(aapt2_bin, full_path)
        sig_info = inspect_signing_cert(apksigner_bin, full_path)

        app_id = badging["appId"] or ""
        vc = int(badging["versionCode"] or 0)
        vn = badging["versionName"] or ""
        min_sdk = int(badging["minimumSupportedVersion"] or 24)
        cert_sha = sig_info["signingCertSha256"]
        sig_ok = bool(sig_info["signatureVerified"])
        apk_type = "debug" if "debug" in fname.lower() else "release"

        prev_entry = prev_files.get(fname)
        if prev_entry is None:
            change_status = "NEW"
            any_change_detected = True
        elif (
            prev_entry.get("sha256") != sha256
            or int(prev_entry.get("versionCode", 0) or 0) != vc
            or str(prev_entry.get("signingCertSha256", "")).lower() != cert_sha
        ):
            change_status = "REPLACED"
            any_change_detected = True
        else:
            change_status = "UNCHANGED"

        item_warnings: List[str] = []
        eligible = True

        if not zip_ok:
            eligible = False
            item_warnings.append(f"{fname}: invalid ZIP structure or missing AndroidManifest.xml.")
        if not app_id or app_id != expected_app_id:
            eligible = False
            item_warnings.append(
                f"{fname}: applicationId '{app_id or 'unknown'}' does not match '{expected_app_id}'."
            )
        if vc <= 0:
            eligible = False
            item_warnings.append(f"{fname}: unreadable or non-positive versionCode ({vc}).")
        if not sig_ok or not cert_sha:
            eligible = False
            item_warnings.append(f"{fname}: APK signature verification failed or missing certificate.")

        # Check downgrade or unbumped versionCode on replacement
        prev_slot_vc = int(prev_entry.get("versionCode", 0) or 0) if prev_entry else 0
        if change_status == "REPLACED" and vc > 0:
            compare_ref = max(prev_slot_vc, prev_highest_vc)
            if compare_ref > 0 and vc <= compare_ref:
                eligible = False
                item_warnings.append(
                    f"Developer Warning: Replacement APK '{fname}' changed checksum without raising "
                    f"versionCode (detected {vc} <= previous {compare_ref}). Android refuses updates "
                    f"with same/lower versionCode; increment versionCode in app/build.gradle.kts."
                )

        if change_status == "NEW" and prev_highest_vc > 0 and vc > 0 and vc < prev_highest_vc:
            eligible = False
            item_warnings.append(
                f"Developer Warning: New APK '{fname}' has lower versionCode ({vc} < published {prev_highest_vc}); "
                f"excluded from installable updates."
            )

        if prev_cert and cert_sha and cert_sha != prev_cert:
            eligible = False
            item_warnings.append(
                f"Developer Warning: Signing certificate mismatch on '{fname}' "
                f"({cert_sha[:12]}... vs baseline {prev_cert[:12]}...). Android blocks installation with mismatched keys."
            )

        warnings.extend(item_warnings)

        download_url = f"{base_url}/{fname}" if base_url else ""
        artifact = {
            "appId": app_id or expected_app_id,
            "applicationId": app_id or expected_app_id,
            "versionName": vn or baseline["versionName"],
            "versionCode": vc,
            "releaseDate": today,
            "releaseNotes": extract_release_notes(vn or baseline["versionName"]),
            "apkType": apk_type,
            "releaseType": apk_type,
            "apkFileName": fname,
            "localFileName": fname,
            "downloadUrl": download_url,
            "fileSize": file_size,
            "fileSizeBytes": file_size,
            "sha256": sha256,
            "signingCertSha256": cert_sha,
            "signingCertificateFingerprint": cert_sha,
            "minimumSupportedVersion": min_sdk,
            "minSdkVersion": min_sdk,
            "isMandatorySuggested": False,
            "updateEligible": eligible,
            "detectedChangeStatus": change_status,
            "developerWarning": " ".join(item_warnings),
        }

        new_file_state[fname] = {
            "sha256": sha256,
            "fileSize": file_size,
            "versionCode": vc,
            "versionName": vn,
            "appId": app_id,
            "signingCertSha256": cert_sha,
            "lastSeenUtc": today,
        }

        if zip_ok and app_id == expected_app_id and vc > 0:
            valid_artifacts.append(artifact)

    # Choose highest eligible artifact, preferring release over debug on ties
    eligible_artifacts = [a for a in valid_artifacts if a.get("updateEligible")]
    pool = eligible_artifacts if eligible_artifacts else valid_artifacts
    pool.sort(
        key=lambda a: (
            int(a["versionCode"]),
            1 if a["apkType"] == "release" else 0,
            1 if "v" + a["versionName"] in a["apkFileName"] else 0,
        ),
        reverse=True,
    )

    primary = pool[0] if pool else {
        "appId": expected_app_id,
        "applicationId": expected_app_id,
        "versionName": baseline["versionName"],
        "versionCode": baseline["versionCode"],
        "releaseDate": today,
        "releaseNotes": extract_release_notes(baseline["versionName"]),
        "apkType": "release",
        "releaseType": "release",
        "apkFileName": "",
        "localFileName": "",
        "downloadUrl": "",
        "fileSize": 0,
        "fileSizeBytes": 0,
        "sha256": "",
        "signingCertSha256": "",
        "signingCertificateFingerprint": "",
        "minimumSupportedVersion": 24,
        "minSdkVersion": 24,
        "isMandatorySuggested": False,
        "updateEligible": False,
        "detectedChangeStatus": "NONE",
        "developerWarning": "No valid APK files found in src/apk/.",
    }

    combined_warning = " | ".join(dict.fromkeys(w for w in warnings if w))
    # Persist highest published versionCode and baseline signing cert cleanly
    next_highest_vc = max(
        prev_highest_vc,
        max((int(a["versionCode"]) for a in eligible_artifacts), default=0),
    )
    next_cert = prev_cert or str(primary.get("signingCertSha256", "") or "").lower()

    manifest_doc: Dict[str, Any] = {
        "appId": primary["appId"],
        "applicationId": primary["applicationId"],
        "versionName": primary["versionName"],
        "versionCode": primary["versionCode"],
        "apkFileName": primary["apkFileName"],
        "localFileName": primary["localFileName"],
        "apkType": primary["apkType"],
        "releaseType": primary["releaseType"],
        "downloadUrl": primary["downloadUrl"],
        "fileSize": primary["fileSize"],
        "fileSizeBytes": primary["fileSizeBytes"],
        "sha256": primary["sha256"],
        "signingCertSha256": primary["signingCertSha256"],
        "signingCertificateFingerprint": primary["signingCertificateFingerprint"],
        "releaseDate": primary["releaseDate"],
        "releaseNotes": primary["releaseNotes"],
        "minimumSupportedVersion": primary["minimumSupportedVersion"],
        "minSdkVersion": primary["minSdkVersion"],
        "isMandatorySuggested": False,
        "distributionConfigured": bool(base_url),
        "updateEligible": bool(primary.get("updateEligible", False)),
        "detectedChangeStatus": "CHANGED" if any_change_detected else "UNCHANGED",
        "developerWarning": combined_warning or primary.get("developerWarning", ""),
        "generatedAtUtc": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "artifacts": valid_artifacts,
    }

    with open(OUT_MANIFEST_SRC, "w", encoding="utf-8") as fh:
        json.dump(manifest_doc, fh, indent=2, ensure_ascii=False)
        fh.write("\n")

    with open(OUT_MANIFEST_APK, "w", encoding="utf-8") as fh:
        json.dump(manifest_doc, fh, indent=2, ensure_ascii=False)
        fh.write("\n")

    with open(STATE_FILE, "w", encoding="utf-8") as fh:
        json.dump(
            {
                "files": new_file_state,
                "highestPublishedVersionCode": next_highest_vc,
                "primaryCertSha256": next_cert,
                "updatedAtUtc": manifest_doc["generatedAtUtc"],
            },
            fh,
            indent=2,
            ensure_ascii=False,
        )
        fh.write("\n")

    publish_hook = os.environ.get("DRX_PUBLISH_HOOK", "").strip()
    if publish_hook and os.path.isfile(publish_hook):
        subprocess.run(
            [publish_hook],
            env={
                **os.environ,
                "DRX_MANIFEST_PATH": OUT_MANIFEST_APK,
                "DRX_APK_DIR": APK_DIR,
            },
            check=False,
        )

    return manifest_doc


if __name__ == "__main__":
    result = scan_and_generate()
    print(
        f"[DRX APK DETECTOR] status={result['detectedChangeStatus']} "
        f"appId={result['appId']} version={result['versionName']} "
        f"(vc={result['versionCode']}) file={result['apkFileName']} "
        f"size={result['fileSize']} sha256={result['sha256'][:16]}..."
    )
    if result.get("developerWarning"):
        print(f"[DRX APK DETECTOR WARNING] {result['developerWarning']}", file=sys.stderr)
