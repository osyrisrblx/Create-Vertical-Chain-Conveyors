#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

windows_env() {
  cmd.exe /C "echo %$1%" 2>/dev/null | tr -d '\r'
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command '$1' is not available." >&2
    exit 1
  fi
}

java_home_win="${VCC_WINDOWS_JAVA_HOME:-C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot}"
java_exe="${VCC_WINDOWS_JAVA_EXE:-/mnt/c/Program Files/Eclipse Adoptium/jdk-21.0.9.10-hotspot/bin/java.exe}"
default_userprofile="$(windows_env USERPROFILE)"
default_localappdata="$(windows_env LOCALAPPDATA)"
gradle_user_home_win="${VCC_GRADLE_USER_HOME:-${default_userprofile}\\.gradle}"
project_cache_win="${VCC_GRADLE_PROJECT_CACHE:-${default_localappdata}\\Temp\\vcc-build\\.gradle}"
workspace_win="${VCC_GRADLE_WORKSPACE:-${default_localappdata}\\Temp\\vcc-build\\workspace}"
workspace_unix="$(wslpath -u "$workspace_win")"

if [[ -z "$gradle_user_home_win" || -z "$project_cache_win" || -z "$workspace_win" ]]; then
  echo "Unable to infer Windows Gradle paths. Set VCC_GRADLE_USER_HOME, VCC_GRADLE_PROJECT_CACHE, and VCC_GRADLE_WORKSPACE." >&2
  exit 1
fi

cd "$repo_root"

require_cmd rsync
require_cmd wslpath

mkdir -p "$workspace_unix"

rsync -a --delete \
  --exclude '.git/' \
  --exclude '.gradle/' \
  --exclude 'build/' \
  "$repo_root"/ "$workspace_unix"/

set +e
cmd.exe /V:ON /C \
  "cd /d $workspace_win && set \"JAVA_HOME=$java_home_win\" && gradlew.bat --project-cache-dir $project_cache_win --no-daemon $*"
gradle_status=$?
set -e

if [[ $gradle_status -eq 0 ]]; then
  mkdir -p "$repo_root/build"

  if [[ -d "$workspace_unix/build/libs" ]]; then
    mkdir -p "$repo_root/build/libs"
    rsync -a --delete "$workspace_unix/build/libs/" "$repo_root/build/libs/"
  fi

  if [[ -d "$workspace_unix/build/test-results" ]]; then
    mkdir -p "$repo_root/build/test-results"
    rsync -a --delete "$workspace_unix/build/test-results/" "$repo_root/build/test-results/"
  fi

  if [[ -d "$workspace_unix/build/reports/tests" ]]; then
    mkdir -p "$repo_root/build/reports/tests"
    rsync -a --delete "$workspace_unix/build/reports/tests/" "$repo_root/build/reports/tests/"
  fi
fi

exit "$gradle_status"
