#!/usr/bin/env bash
set -euo pipefail

root_build="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/build.gradle"
settings_gradle="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/settings.gradle"

app_version_settings="$(awk -F'\"' '/id \"com.android.application\"/{print $4; exit}' "${settings_gradle}")"
lib_version_settings="$(awk -F'\"' '/id \"com.android.library\"/{print $4; exit}' "${settings_gradle}")"
app_version_build="$(awk -F'\"' '/id \"com.android.application\"/{print $4; exit}' "${root_build}")"
lib_version_build="$(awk -F'\"' '/id \"com.android.library\"/{print $4; exit}' "${root_build}")"

if [[ -z "${app_version_settings}" || -z "${lib_version_settings}" || -z "${app_version_build}" || -z "${lib_version_build}" ]]; then
  echo "Unable to resolve AGP versions from settings.gradle or build.gradle." >&2
  exit 1
fi

if [[ "${app_version_settings}" != "${app_version_build}" || "${lib_version_settings}" != "${lib_version_build}" ]]; then
  echo "AGP versions must match between settings.gradle and build.gradle." >&2
  echo "settings.gradle: app=${app_version_settings}, lib=${lib_version_settings}" >&2
  echo "build.gradle: app=${app_version_build}, lib=${lib_version_build}" >&2
  exit 1
fi

if grep -nE "^\s*android\s*\{|^\s*repositories\s*\{" "${root_build}"; then
  echo "Root build.gradle must not declare android or repositories blocks." >&2
  exit 1
fi

./gradlew :app:assembleDebug
./gradlew :sdk:assemble
./gradlew :sdk:verifySdkDependencies
./gradlew :sdk:verifySdkJmrtdResolution
./gradlew assemble
