#!/usr/bin/env bash
# Copy gitignored private sources into the LipaBill-private companion checkout and push.
# CI overlays that repo onto this one before building (.github/workflows/ci.yml).
# Usage: scripts/sync-private-sources.sh [path-to-LipaBill-private-checkout]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="${1:-$ROOT/../LipaBill-private}"

# Keep in sync with the "Private IP" block in .gitignore.
PATHS=(
  app/src/main/java/com/lipabill/app/ussd
  app/src/internal/java/com/lipabill/app/ussd
  app/src/play/java/com/lipabill/app/ussd
  app/src/main/res/xml/ussd_accessibility_service.xml
  app/src/main/java/com/lipabill/app/data/parser
  app/src/main/java/com/lipabill/app/security/AppAtRestCrypto.kt
  app/src/test/java/com/lipabill/app/data/parser
  # Gitignored inputs that unit tests check for drift.
  docs/play-store-listing.md
  docs/play-store-sms-permission-review.md
  web/p/fixtures.json
)

if [[ ! -d "$DEST/.git" ]]; then
  echo "No git checkout at $DEST (clone LipaBill-private there first)" >&2
  exit 1
fi

cd "$ROOT"
for p in "${PATHS[@]}"; do
  rm -rf "${DEST:?}/$p"
  if [[ -e "$p" ]]; then
    mkdir -p "$DEST/$(dirname "$p")"
    cp -R "$p" "$DEST/$p"
  fi
done

cd "$DEST"
git add -A
if git diff --cached --quiet; then
  echo "Private sources already up to date"
  exit 0
fi
git commit -m "Sync private sources from LipaBill $(git -C "$ROOT" rev-parse --short HEAD)"
git push -u origin HEAD
