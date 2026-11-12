#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

VERSION="${1:-}"
CHANGELOG="${2:-}"
YES="${3:-}"

if [ -z "$VERSION" ]; then
  echo "usage: scripts/release.sh v1.2.3 \"changelog\" [--yes]"
  exit 1
fi

VERSION="${VERSION#v}"
CODE=$(awk -F. '{printf "%d%02d%02d", $1, $2, $3}' <<< "$VERSION")

if [ -n "$(git status --porcelain)" ]; then
  echo "Working tree is dirty. Commit or stash first."
  exit 1
fi

if [ "$(git branch --show-current)" != "main" ]; then
  echo "Release from main."
  exit 1
fi

if [ -z "$YES" ]; then
  read -r -p "Build and publish $VERSION ($CODE)? [y/N] " answer
  [ "$answer" = "y" ] || exit 1
fi

export JAVA_HOME="${JAVA_HOME:-$HOME/.local/opt/temurin-25}"

sed -i "s/versionCode = [0-9]*/versionCode = $CODE/" app/build.gradle.kts
sed -i "s/versionName = \"[^\"]*\"/versionName = \"$VERSION\"/" app/build.gradle.kts

./gradlew :app:assembleDirectRelease --no-build-cache --max-workers=1 --no-daemon

APK=app/build/outputs/apk/direct/release/app-direct-release.apk
META=app/build/outputs/apk/direct/release/output-metadata.json

if [ ! -f "$APK" ]; then
  echo "Release APK not found."
  exit 1
fi

REAL_CODE=$(jq -r '.elements[0].versionCode' "$META")
REAL_NAME=$(jq -r '.elements[0].versionName' "$META")
SHA=$(sha256sum "$APK" | cut -d' ' -f1)

[ "$REAL_CODE" = "$CODE" ] || { echo "versionCode mismatch: $REAL_CODE"; exit 1; }
[ "$REAL_NAME" = "$VERSION" ] || { echo "versionName mismatch: $REAL_NAME"; exit 1; }

jq -n \
  --argjson versionCode "$REAL_CODE" \
  --arg versionName "$REAL_NAME" \
  --arg apkUrl "https://github.com/Logisite/upcroc/releases/latest/download/app-direct-release.apk" \
  --arg sha256 "$SHA" \
  --arg changelog "${CHANGELOG:-Upcroc $VERSION}" \
  '{versionCode: $versionCode, versionName: $versionName, apkUrl: $apkUrl, sha256: $sha256, changelog: $changelog}' \
  > latest.json

sed -i "s/\"versionCode\": [0-9]*/\"versionCode\": $REAL_CODE/; s/\"versionName\": \"[^\"]*\"/\"versionName\": \"$REAL_NAME\"/; s#/releases/download/v[^/]*/#/releases/download/v$VERSION/#" latest.example.json

git add app/build.gradle.kts latest.example.json
if ! git diff --cached --quiet; then
  git commit -m "release: $VERSION"
  git push origin main
fi

gh release create "v$VERSION" \
  "$APK" \
  latest.json \
  --title "Upcroc $VERSION" \
  --notes "${CHANGELOG:-Upcroc $VERSION}" \
  --latest

rm -f latest.json
echo "Released v$VERSION"
