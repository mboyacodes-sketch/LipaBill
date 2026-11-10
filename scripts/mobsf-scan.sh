#!/usr/bin/env bash
# Full MobSF static analysis of a built APK, run locally in Docker.
# The APK contains the private modules, so the report stays on this machine
# (ota/dist/ and mobsf-reports/ are gitignored).
#
# Usage: scripts/mobsf-scan.sh [path/to/app.apk]
#   default: newest internal release APK under app/build/outputs/apk/
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

IMAGE="opensecurity/mobile-security-framework-mobsf:v4.6.4"
PORT="${MOBSF_PORT:-8008}"
OUT_DIR="$ROOT/mobsf-reports"

APK="${1:-$(ls -1t app/build/outputs/apk/internal/release/*.apk 2>/dev/null | head -1 || true)}"
if [[ -z "$APK" || ! -f "$APK" ]]; then
  echo "No APK found. Build one first: ./gradlew :app:assembleInternalRelease" >&2
  exit 1
fi

API_KEY="$(openssl rand -hex 32)"
NAME="lipabill-mobsf-$$"
cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

docker run -d --rm --name "$NAME" -p "127.0.0.1:${PORT}:8000" \
  -e MOBSF_API_KEY="$API_KEY" "$IMAGE" >/dev/null

echo "Waiting for MobSF on 127.0.0.1:${PORT}…"
for _ in $(seq 1 90); do
  if curl -fsS "http://127.0.0.1:${PORT}/api/v1/scans" -H "Authorization: $API_KEY" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

API="http://127.0.0.1:${PORT}/api/v1"
UPLOAD="$(curl -fsS -F "file=@${APK}" "$API/upload" -H "Authorization: $API_KEY")"
HASH="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["hash"])' <<<"$UPLOAD")"

echo "Scanning $(basename "$APK") (hash $HASH)…"
curl -fsS -X POST --data "hash=${HASH}" "$API/scan" -H "Authorization: $API_KEY" >/dev/null

mkdir -p "$OUT_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
JSON="$OUT_DIR/mobsf-${STAMP}.json"
PDF="$OUT_DIR/mobsf-${STAMP}.pdf"
curl -fsS -X POST --data "hash=${HASH}" "$API/report_json" -H "Authorization: $API_KEY" -o "$JSON"
curl -fsS -X POST --data "hash=${HASH}" "$API/download_pdf" -H "Authorization: $API_KEY" -o "$PDF" || true
SCORE="$(curl -fsS -X POST --data "hash=${HASH}" "$API/scorecard" -H "Authorization: $API_KEY" || true)"

python3 - "$JSON" "$SCORE" <<'PY'
import json, sys
report = json.load(open(sys.argv[1]))
try:
    score = json.loads(sys.argv[2])
except Exception:
    score = {}
print()
print(f"Security score: {score.get('security_score', report.get('security_score', '?'))}")
for level in ("high", "warning"):
    items = score.get(level) or []
    print(f"{level.upper()}: {len(items)}")
    for item in items[:15]:
        print(f"  - {item.get('title', item)}")
PY

echo
echo "Report: $JSON"
[[ -s "$PDF" ]] && echo "PDF:    $PDF"
