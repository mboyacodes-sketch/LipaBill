#!/usr/bin/env bash
# Build an internal release APK and print the OTA manifest to paste onto
# https://lipabill.app/ota/latest.json (and upload the APK next to it).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"

./gradlew :app:assembleInternalRelease

APK="$(ls -1t app/build/outputs/apk/internal/release/*.apk | head -1)"
test -f "$APK"

VERSION_CODE="$(python3 - <<'PY'
import re
text=open("app/build.gradle.kts").read()
print(re.search(r"versionCode\s*=\s*(\d+)", text).group(1))
PY
)"
VERSION_NAME="$(python3 - <<'PY'
import re
text=open("app/build.gradle.kts").read()
print(re.search(r'versionName\s*=\s*"([^"]+)"', text).group(1))
PY
)"

SHA="$(shasum -a 256 "$APK" | awk '{print $1}')"
OUT_NAME="LipaBill-${VERSION_NAME}-internal.apk"
HOST_URL="https://lipabill.app/ota/${OUT_NAME}"

mkdir -p ota/dist
cp -f "$APK" "ota/dist/${OUT_NAME}"

cat > ota/dist/latest.json <<EOF
{
  "versionCode": ${VERSION_CODE},
  "versionName": "${VERSION_NAME}",
  "apkUrl": "${HOST_URL}",
  "sha256": "${SHA}",
  "mandatory": false,
  "notes": ""
}
EOF

echo
echo "Built: ota/dist/${OUT_NAME}"
echo "Manifest: ota/dist/latest.json"
echo
echo "Upload both files to the lipabill.app Vercel project under /ota/:"
echo "  /ota/${OUT_NAME}"
echo "  /ota/latest.json"
echo
cat ota/dist/latest.json
