#!/usr/bin/env bash
# Usage: tools/publish-apk.sh <clean.apk> "<release notes>"
# Uploads the APK to tizbin and writes latest.json (read by the in-app updater).
set -euo pipefail
APK="$1"; NOTES="${2:-}"
VC=$(grep -oP 'versionCode = \K\d+' app/build.gradle.kts)
VN=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
SHA=$(sha256sum "$APK" | cut -d' ' -f1)
rsync -q "$APK" tizbin:/var/www/tizbin/hamrah/Hamrah.apk
python3 - "$VC" "$VN" "$SHA" "$NOTES" <<'PY' | ssh tizbin 'cat > /var/www/tizbin/hamrah/latest.json'
import json, sys
vc, vn, sha, notes = sys.argv[1:5]
print(json.dumps({"versionCode": int(vc), "versionName": vn, "url": "https://tizbin.org/hamrah/Hamrah.apk", "sha256": sha, "notes": notes}, ensure_ascii=False))
PY
echo "published $VN ($VC) sha256=$SHA"
