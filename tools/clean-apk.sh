#!/usr/bin/env bash
# Strip build/VCS metadata from a release APK and re-sign it.
#
# app/build.gradle.kts already removes the Play dependency blob and the per-library
# `*.version` markers, but AGP injects two files AFTER packaging that no DSL removes:
#   META-INF/version-control-info.textproto          (git revision + repo path)
#   META-INF/com/android/build/gradle/app-metadata.properties  (AGP version)
# This deletes them from the archive and re-signs, so the shipped APK leaks neither.
#
# Usage:  tools/clean-apk.sh [path/to/app-release.apk] [output.apk]
# Reads signing config from keystore.properties at the repo root. Needs Android build-tools
# (zipalign, apksigner) on PATH or under $ANDROID_HOME.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${1:-$ROOT/app/build/outputs/apk/release/app-release.apk}"
OUT="${2:-$ROOT/app/build/outputs/apk/release/app-release-clean.apk}"

props="$ROOT/keystore.properties"
[ -f "$props" ] || { echo "missing keystore.properties"; exit 1; }
get() { grep -E "^$1=" "$props" | cut -d= -f2-; }
KS="$(get storeFile)"; KP="$(get storePassword)"; ALIAS="$(get keyAlias)"; KEYP="$(get keyPassword)"

BT="$(ls -d "${ANDROID_HOME:-/opt/android-sdk}"/build-tools/* 2>/dev/null | sort -V | tail -1)"
ZIPALIGN="$(command -v zipalign || echo "$BT/zipalign")"
APKSIGNER="$(command -v apksigner || echo "$BT/apksigner")"

tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
cp "$SRC" "$tmp/in.apk"
zip -q -d "$tmp/in.apk" \
    "META-INF/version-control-info.textproto" \
    "META-INF/com/android/build/gradle/app-metadata.properties" || true
"$ZIPALIGN" -f -p 4 "$tmp/in.apk" "$tmp/aligned.apk"
"$APKSIGNER" sign --ks "$KS" --ks-key-alias "$ALIAS" \
    --ks-pass "pass:$KP" --key-pass "pass:$KEYP" "$tmp/aligned.apk"
cp "$tmp/aligned.apk" "$OUT"
"$APKSIGNER" verify "$OUT" >/dev/null && echo "clean, signed APK -> $OUT"
