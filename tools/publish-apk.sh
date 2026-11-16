#!/usr/bin/env bash
# Publishes a release APK to the in-app updater of «همراهِ طیف».
#
#   tools/publish-apk.sh <signed.apk> "<release notes>"             # dry run: checks everything, changes nothing
#   tools/publish-apk.sh <signed.apk> "<release notes>" --publish   # really publish
#
# The app reads  https://tizbin.org/apps/hamrah.json  (update/AppUpdater.kt: MANIFEST_URL) and downloads
# the "url" in it, checking "sha256". So this writes exactly two files on tizbin:
#   /var/www/tizbin/apps/hamrah.apk   and   /var/www/tizbin/apps/hamrah.json
# It does NOT touch /var/www/tizbin/hamrah/ (Hamrah.apk there is a symlink to apps/hamrah.apk and
# latest.json belongs to the old package's "moved" stub).
#
# Order of work: the APK must already be cleaned (tools/clean-apk.sh) and THEN signed with
# /root/keys/sign-app.sh (clean-apk re-signs with the old key only). This script refuses an APK
# that is not signed by the Hamsohbat key, has the wrong package, or is not newer than the live one.
set -euo pipefail

APK="${1:?usage: tools/publish-apk.sh <signed.apk> \"<release notes>\" [--publish]}"
NOTES="${2:-}"
MODE="${3:-}"
HOST="${HAMRAH_PUBLISH_HOST:-tizbin}"
DIR=/var/www/tizbin/apps
MANIFEST_URL=https://tizbin.org/apps/hamrah.json
APK_URL=https://tizbin.org/apps/hamrah.apk
PKG=com.rasta.hamrah
SIGNER_CN="CN=Hamsohbat"

[ -f "$APK" ] || { echo "no such file: $APK"; exit 1; }
BT="$(ls -d "${ANDROID_HOME:-/opt/android-sdk}"/build-tools/* 2>/dev/null | sort -V | tail -1)"
AAPT2="$(command -v aapt2 || echo "$BT/aapt2")"
APKSIGNER="$(command -v apksigner || echo "$BT/apksigner")"

badge="$("$AAPT2" dump badging "$APK")"
badge="$(grep -m1 '^package: ' <<<"$badge")"
pkg="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$badge")"
VC="$(sed -n "s/.* versionCode='\([0-9]*\)'.*/\1/p" <<<"$badge")"
VN="$(sed -n "s/.* versionName='\([^']*\)'.*/\1/p" <<<"$badge")"
[ "$pkg" = "$PKG" ] || { echo "wrong package: '$pkg' (expected $PKG)"; exit 1; }
[ -n "$VC" ] && [ -n "$VN" ] || { echo "could not read the version from the APK"; exit 1; }

certs="$("$APKSIGNER" verify --print-certs "$APK")"
grep -q "certificate DN:.*$SIGNER_CN" <<<"$certs" || {
    echo "refusing: the APK is not signed with the Hamsohbat key. Run /root/keys/sign-app.sh on it first."
    exit 1
}

SHA="$(sha256sum "$APK" | cut -d' ' -f1)"
live="$(curl -fsS --max-time 20 "$MANIFEST_URL?t=$(date +%s)")" || { echo "cannot read the live manifest"; exit 1; }
LIVE_VC="$(python3 -c 'import json,sys; print(int(json.loads(sys.argv[1])["versionCode"]))' "$live")"
echo "live: versionCode $LIVE_VC   new: $VN ($VC)  sha256=$SHA"
[ "$VC" -gt "$LIVE_VC" ] || { echo "refusing: versionCode $VC is not newer than the live $LIVE_VC"; exit 1; }

manifest="$(python3 - "$VC" "$VN" "$SHA" "$NOTES" "$APK_URL" "$PKG" <<'PY'
import json, sys
vc, vn, sha, notes, url, pkg = sys.argv[1:7]
print(json.dumps({"versionCode": int(vc), "versionName": vn, "url": url, "sha256": sha,
                  "notes": notes, "package": pkg}, ensure_ascii=False))
PY
)"

if [ "$MODE" != "--publish" ]; then
    echo "dry run (nothing changed). Would write $HOST:$DIR/hamrah.apk and hamrah.json:"
    echo "$manifest"
    echo "add --publish to really publish."
    exit 0
fi

STAMP="$(date +%Y%m%d%H%M%S)"
# Upload beside the target, verify the bytes on the server, keep the previous pair, then swap both
# with renames (the APK first, so the manifest never points at bytes that are not there yet).
rsync -q "$APK" "$HOST:$DIR/.hamrah.apk.new"
printf '%s\n' "$manifest" | ssh "$HOST" "cat > $DIR/.hamrah.json.new"
ssh "$HOST" "set -e; cd $DIR
  test \"\$(sha256sum .hamrah.apk.new | cut -d' ' -f1)\" = '$SHA'
  test ! -L hamrah.apk
  cp -p hamrah.apk hamrah.apk.prev-$STAMP; cp -p hamrah.json hamrah.json.prev-$STAMP
  chmod 644 .hamrah.apk.new .hamrah.json.new
  mv -f .hamrah.apk.new hamrah.apk; mv -f .hamrah.json.new hamrah.json"

# Read back what users will actually get (through the CDN, cache-busted) and compare.
got="$(curl -fsS --max-time 30 "$MANIFEST_URL?t=$(date +%s)")"
got_vc="$(python3 -c 'import json,sys; print(int(json.loads(sys.argv[1])["versionCode"]))' "$got")"
got_sha="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["sha256"])' "$got")"
dl_sha="$(curl -fsSL --max-time 300 "$APK_URL?t=$(date +%s)" | sha256sum | cut -d' ' -f1)"
if [ "$got_vc" != "$VC" ] || [ "$got_sha" != "$SHA" ] || [ "$dl_sha" != "$SHA" ]; then
    echo "PUBLISH CHECK FAILED: manifest vc=$got_vc sha=$got_sha, downloaded apk sha=$dl_sha (expected $VC / $SHA)"
    echo "previous files are kept on $HOST as $DIR/hamrah.{apk,json}.prev-$STAMP"
    exit 1
fi
echo "published $VN ($VC): live manifest and downloaded APK both match sha256=$SHA"
echo "rollback: ssh $HOST 'cd $DIR && cp -p hamrah.apk.prev-$STAMP hamrah.apk && cp -p hamrah.json.prev-$STAMP hamrah.json'"
