#!/bin/bash
# bump-version.sh — Bump Xed-Editor version
# Usage: ./scripts/bump-version.sh <patch|minor|major|manual X.Y.Z>

set -euo pipefail

VERSION_FILE="version.properties"
CHANGELOG_FILE="CHANGELOG.md"
BUILD_FILE="app/build.gradle.kts"

if [ ! -f "$VERSION_FILE" ]; then
  echo "Error: $VERSION_FILE not found. Run from project root."
  exit 1
fi

# Read current version
CURRENT_NAME=$(grep "^versionName" "$VERSION_FILE" | cut -d= -f2 | tr -d ' ')
CURRENT_CODE=$(grep "^versionCode" "$VERSION_FILE" | cut -d= -f2 | tr -d ' ')

IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_NAME"

case "${1:-patch}" in
  patch)
    PATCH=$((PATCH + 1))
    NEW_NAME="${MAJOR}.${MINOR}.${PATCH}"
    ;;
  minor)
    MINOR=$((MINOR + 1))
    PATCH=0
    NEW_NAME="${MAJOR}.${MINOR}.${PATCH}"
    ;;
  major)
    MAJOR=$((MAJOR + 1))
    MINOR=0
    PATCH=0
    NEW_NAME="${MAJOR}.${MINOR}.${PATCH}"
    ;;
  manual)
    if [ -z "${2:-}" ]; then
      echo "Usage: $0 manual <version>"
      exit 1
    fi
    NEW_NAME="$2"
    ;;
  *)
    echo "Usage: $0 <patch|minor|major|manual X.Y.Z>"
    exit 1
    ;;
esac

NEW_CODE=$((CURRENT_CODE + 1))

echo "Current: v${CURRENT_NAME} (code ${CURRENT_CODE})"
echo "New:     v${NEW_NAME} (code ${NEW_CODE})"

# Update version.properties
if [[ "$OSTYPE" == "darwin"* ]]; then
  sed -i '' "s/^versionName=.*/versionName=${NEW_NAME}/" "$VERSION_FILE"
  sed -i '' "s/^versionCode=.*/versionCode=${NEW_CODE}/" "$VERSION_FILE"
else
  sed -i "s/^versionName=.*/versionName=${NEW_NAME}/" "$VERSION_FILE"
  sed -i "s/^versionCode=.*/versionCode=${NEW_CODE}/" "$VERSION_FILE"
fi

echo "✅ version.properties updated"
echo ""
echo "Next steps:"
echo "  1. Add release notes to CHANGELOG.md under '## ${NEW_NAME}'"
echo "  2. Commit: git add version.properties CHANGELOG.md && git commit -m \"chore: bump to v${NEW_NAME}\""
echo "  3. Tag:    git tag v${NEW_NAME}"
echo "  4. Push:   git push && git push --tags"
echo ""
echo "Or run the release workflow from GitHub Actions to automate everything."
