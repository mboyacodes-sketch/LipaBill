#!/usr/bin/env bash
# Build an internal release APK and print the OTA manifest to paste onto
# https://lipabill.app/ota/latest.json (and upload the APK next to it).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"

# Internal builds (2.2.0+) reject latest.json unless latest.json.sig verifies.
# OTA_SIGNING_KEY overrides the path (CI writes the key to a temp file).
SIGNING_KEY="${OTA_SIGNING_KEY:-keystore/ota-manifest-signing.pem}"
if [[ ! -f "$SIGNING_KEY" ]]; then
  echo "Missing OTA manifest signing key: $SIGNING_KEY" >&2
  exit 1
fi

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

openssl dgst -sha256 -sign "$SIGNING_KEY" -out ota/dist/latest.json.sig.der ota/dist/latest.json
base64 < ota/dist/latest.json.sig.der | tr -d '\n' > ota/dist/latest.json.sig
openssl pkey -in "$SIGNING_KEY" -pubout -out ota/dist/ota-manifest-public.pem
openssl dgst -sha256 -verify ota/dist/ota-manifest-public.pem \
  -signature ota/dist/latest.json.sig.der ota/dist/latest.json >/dev/null
rm -f ota/dist/latest.json.sig.der ota/dist/ota-manifest-public.pem

echo
echo "Built: ota/dist/${OUT_NAME}"
echo "Manifest: ota/dist/latest.json (+ latest.json.sig)"
echo
echo "Upload all three files to the lipabill.app Vercel project under /ota/:"
echo "  /ota/${OUT_NAME}"
echo "  /ota/latest.json"
echo "  /ota/latest.json.sig"
echo "Upload the .sig whenever latest.json changes, or 2.2.0+ installs stop seeing updates."
echo
cat ota/dist/latest.json
