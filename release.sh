#!/bin/bash
# Sync versions from version.json and optionally build release artifacts.
# Usage:
#   ./release.sh              # sync + local bootJar + sdk build
#   ./release.sh --sync-only  # metadata only (CI uses this before build)
set -euo pipefail

cd "$(dirname "$0")"

SYNC_ONLY=false
if [ "${1:-}" = "--sync-only" ]; then
  SYNC_ONLY=true
fi

echo "=== LiveScreenLog release helper ==="

if ! command -v node >/dev/null 2>&1; then
  echo "node is required to read version.json" >&2
  exit 1
fi

VERSION=$(node -e "console.log(require('./version.json').version)")
if [ -z "$VERSION" ]; then
  echo "version.json missing version" >&2
  exit 1
fi
echo "Target version: $VERSION"

node -e "
const fs = require('fs');
const v = process.argv[1];
for (const p of ['./sdk/package.json', './frontend/package.json']) {
  const pkg = JSON.parse(fs.readFileSync(p, 'utf8'));
  pkg.version = v;
  fs.writeFileSync(p, JSON.stringify(pkg, null, 2) + '\n');
  console.log('  ✓', p);
}
let gradle = fs.readFileSync('./build.gradle', 'utf8');
gradle = gradle.replace(/version\\s*=\\s*['\"][^'\"]*['\"]/, \"version = '\" + v + \"'\");
fs.writeFileSync('./build.gradle', gradle);
console.log('  ✓ build.gradle');
" "$VERSION"

if [ "$SYNC_ONLY" = true ]; then
  echo "Sync-only done (no build)."
  exit 0
fi

echo "Building server JAR (includes dashboard + SDK static)…"
./gradlew copySdkToStatic bootJar -x test

echo "Building SDK package outputs…"
if [ -f sdk/package-lock.json ]; then
  (cd sdk && npm ci && npm run build)
else
  (cd sdk && npm install && npm run build)
fi

mkdir -p dist/release
JAR=$(ls build/libs/livescreenlog-*.jar | head -1)
cp "$JAR" "dist/release/livescreenlog-${VERSION}.jar"
cp sdk/dist/livescreenlog.js "dist/release/livescreenlog-${VERSION}.js"
cp "dist/release/livescreenlog-${VERSION}.jar" dist/release/livescreenlog.jar
cp "dist/release/livescreenlog-${VERSION}.js" dist/release/livescreenlog.js
if command -v sha256sum >/dev/null 2>&1; then
  (cd dist/release && sha256sum livescreenlog-${VERSION}.jar livescreenlog-${VERSION}.js livescreenlog.jar livescreenlog.js > SHA256SUMS.txt)
elif command -v shasum >/dev/null 2>&1; then
  (cd dist/release && shasum -a 256 livescreenlog-${VERSION}.jar livescreenlog-${VERSION}.js livescreenlog.jar livescreenlog.js > SHA256SUMS.txt)
fi

echo ""
echo "=== Local artifacts in dist/release/ ==="
ls -la dist/release
echo ""
echo "Publish via git tag (CI):"
echo "  git add version.json build.gradle sdk/package.json frontend/package.json"
echo "  git commit -m \"chore: release ${VERSION}\""
echo "  git tag -a v${VERSION} -m \"v${VERSION}\""
echo "  git push origin main   # or your release branch"
echo "  git push origin v${VERSION}"
echo ""
echo "Downloads after CI:"
echo "  https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.jar"
echo "  https://github.com/0xdc05f/livescreenlog/releases/latest/download/livescreenlog.js"
