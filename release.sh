#!/bin/bash
set -e

# Change directory to project root
cd "$(dirname "$0")"

echo "=== LiveScreenLog Version Synchronizer ==="

# Read version from version.json
VERSION=$(node -e "console.log(require('./version.json').version)")
echo "Target version to sync: $VERSION"

# 1. Update sdk/package.json
node -e "
const fs = require('fs');
const pkg = JSON.parse(fs.readFileSync('./sdk/package.json', 'utf8'));
pkg.version = '$VERSION';
fs.writeFileSync('./sdk/package.json', JSON.stringify(pkg, null, 2) + '\n');
"
echo "  ✓ Updated sdk/package.json version to $VERSION"

# 2. Update frontend/package.json
node -e "
const fs = require('fs');
const pkg = JSON.parse(fs.readFileSync('./frontend/package.json', 'utf8'));
pkg.version = '$VERSION';
fs.writeFileSync('./frontend/package.json', JSON.stringify(pkg, null, 2) + '\n');
"
echo "  ✓ Updated frontend/package.json version to $VERSION"

# 3. Update build.gradle version
node -e "
const fs = require('fs');
let gradle = fs.readFileSync('./build.gradle', 'utf8');
gradle = gradle.replace(/version\s*=\s*['\"].*?['\"]/, \"version = '$VERSION'\");
fs.writeFileSync('./build.gradle', gradle);
"
echo "  ✓ Updated build.gradle version to $VERSION"

echo "=== Local Version Sync Completed ==="
echo "Building the project artifacts..."

# Run gradle build to ensure compile works and livescreenlog.js copies to static
./gradlew copySdkToStatic bootJar -x test

echo ""
echo "=== Release Build Successful ==="
echo "If you want to tag this release, run the following commands:"
echo "  git add version.json build.gradle frontend/package.json sdk/package.json src/main/resources/static/livescreenlog.js"
echo "  git commit -m \"chore: release version $VERSION\""
echo "  git tag -a v$VERSION -m \"version $VERSION\""
