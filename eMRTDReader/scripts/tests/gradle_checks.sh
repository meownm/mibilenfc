#!/usr/bin/env bash
set -euo pipefail

root_build="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/build.gradle"

if grep -nE "^\s*android\s*\{|^\s*repositories\s*\{" "${root_build}"; then
  echo "Root build.gradle must not declare android or repositories blocks." >&2
  exit 1
fi

./gradlew :app:assembleDebug
./gradlew :sdk:assemble
./gradlew :sdk:verifySdkDependencies
./gradlew :sdk:verifySdkJmrtdResolution
./gradlew assemble
