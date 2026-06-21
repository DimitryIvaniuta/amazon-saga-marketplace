#!/usr/bin/env bash
set -euo pipefail
VERSION=9.5.1
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/bootstrap/gradle-${VERSION}"
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
if [[ ! -x "$BASE/bin/gradle" ]]; then
  mkdir -p "$(dirname "$BASE")"
  ARCHIVE="$(dirname "$BASE")/gradle-${VERSION}-bin.zip"
  curl --fail --location --retry 3 "https://services.gradle.org/distributions/gradle-${VERSION}-bin.zip" -o "$ARCHIVE"
  unzip -q -o "$ARCHIVE" -d "$(dirname "$BASE")"
fi
exec "$BASE/bin/gradle" "$@"
