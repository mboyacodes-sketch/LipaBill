#!/usr/bin/env bash
# Build play debug, install, restore Accessibility (survives adb replace), launch.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}/platform-tools/adb"
PKG=com.lipabill.app
COMPONENT="$PKG/$PKG.ussd.UssdAccessibilityService"
APK="$ROOT/app/build/outputs/apk/play/debug/app-play-debug.apk"

cd "$ROOT"
./gradlew :app:assemblePlayDebug -q
"$ADB" install -r "$APK"

# Keep LipaBill in enabled_accessibility_services after package replace.
current="$("$ADB" shell settings get secure enabled_accessibility_services | tr -d '\r')"
if [[ -z "$current" || "$current" == "null" ]]; then
  next="$COMPONENT"
elif [[ "$current" == *"$COMPONENT"* ]]; then
  next="$current"
else
  next="$current:$COMPONENT"
fi
"$ADB" shell settings put secure enabled_accessibility_services "$next"
"$ADB" shell settings put secure accessibility_enabled 1

"$ADB" shell am force-stop "$PKG"
# am start only. monkey injects a rotation event and rewrites the system Auto-rotate toggle.
"$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null
echo "Installed + Accessibility restored for $COMPONENT"
