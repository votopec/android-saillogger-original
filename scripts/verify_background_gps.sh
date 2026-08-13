#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB="${ADB:-/Users/ivan/Library/Android/sdk/platform-tools/adb}"
GRADLEW="${GRADLEW:-$ROOT_DIR/gradlew}"
JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
ANDROID_HOME="${ANDROID_HOME:-/Users/ivan/Library/Android/sdk}"
PACKAGE="com.ibsailing.saillogger"
ACTIVITY="$PACKAGE/.MainActivity"
APK="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
REMOTE_FILES="/sdcard/Android/data/$PACKAGE/files"
OUT_DIR="${OUT_DIR:-/private/tmp/saillogger-background-gps}"

if [[ $# -gt 0 ]]; then
  SERIAL="$1"
else
  SERIAL="$("$ADB" devices | awk 'NR > 1 && $2 == "device" {print $1; exit}')"
fi

if [[ -z "${SERIAL:-}" ]]; then
  echo "No connected Android device/emulator. Start an emulator first."
  exit 1
fi

adb_device() {
  "$ADB" -s "$SERIAL" "$@"
}

cleanup() {
  adb_device shell cmd location providers remove-test-provider gps >/dev/null 2>&1 || true
  adb_device shell appops set com.android.shell android:mock_location deny >/dev/null 2>&1 || true
}
trap cleanup EXIT

mkdir -p "$OUT_DIR"

(
  cd "$ROOT_DIR"
  JAVA_HOME="$JAVA_HOME" ANDROID_HOME="$ANDROID_HOME" "$GRADLEW" assembleDebug
)

adb_device install -r "$APK" >/dev/null
adb_device shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION >/dev/null 2>&1 || true
adb_device shell pm grant "$PACKAGE" android.permission.ACCESS_COARSE_LOCATION >/dev/null 2>&1 || true
adb_device shell pm grant "$PACKAGE" android.permission.ACCESS_BACKGROUND_LOCATION >/dev/null 2>&1 || true
adb_device shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
adb_device shell cmd deviceidle whitelist +"$PACKAGE" >/dev/null 2>&1 || true
adb_device shell cmd location set-location-enabled true
adb_device shell appops set com.android.shell android:mock_location allow
adb_device shell cmd location providers remove-test-provider gps >/dev/null 2>&1 || true
adb_device shell cmd location providers add-test-provider gps --requiresSatellite --supportsAltitude --supportsSpeed --supportsBearing --powerRequirement 3
adb_device shell cmd location providers set-test-provider-enabled gps true
adb_device shell cmd location providers set-test-provider-location gps --location 45.32800,14.44350 --accuracy 3

adb_device shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
adb_device shell am start -n "$ACTIVITY" >/dev/null
sleep 3

# Coordinates match the default Pixel_Tablet emulator in portrait/letterboxed mode.
adb_device shell input tap 1280 440
sleep 3
adb_device shell input keyevent KEYCODE_HOME
sleep 1

route=(
  "45.32810,14.44370"
  "45.32824,14.44395"
  "45.32839,14.44425"
  "45.32855,14.44455"
  "45.32870,14.44485"
  "45.32886,14.44515"
)

for point in "${route[@]}"; do
  adb_device shell cmd location providers set-test-provider-location gps --location "$point" --accuracy 3
  sleep 1
done

adb_device shell am start -n "$ACTIVITY" >/dev/null
sleep 2
adb_device shell input touchscreen tap 1780 65
sleep 1
adb_device shell input touchscreen tap 760 60
sleep 3

latest_file="$(adb_device shell "ls -t $REMOTE_FILES/*.csv 2>/dev/null | head -1" | tr -d '\r')"
if [[ -z "$latest_file" ]]; then
  echo "No CSV file was written."
  exit 1
fi

local_file="$OUT_DIR/$(basename "$latest_file")"
adb_device pull "$latest_file" "$local_file" >/dev/null

gps_rows="$(awk -F, 'NR > 7 && $2 != "" {count++} END {print count + 0}' "$local_file")"
if ! grep -q "14.44515,45.32886" "$local_file"; then
  echo "CSV was written, but final background GPS point was not found: $local_file"
  exit 1
fi

echo "Background GPS verification passed on $SERIAL"
echo "CSV: $local_file"
echo "GPS rows: $gps_rows"
