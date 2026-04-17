#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

windows_env() {
  cmd.exe /C "echo %$1%" 2>/dev/null | tr -d '\r'
}

java_home_win="${VCC_WINDOWS_JAVA_HOME:-C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot}"
java_exe="${VCC_WINDOWS_JAVA_EXE:-/mnt/c/Program Files/Eclipse Adoptium/jdk-17.0.17.10-hotspot/bin/java.exe}"
default_userprofile="$(windows_env USERPROFILE)"
default_localappdata="$(windows_env LOCALAPPDATA)"
gradle_user_home_win="${VCC_GRADLE_USER_HOME:-${default_userprofile}\\.gradle}"
project_cache_win="${VCC_GRADLE_PROJECT_CACHE:-${default_localappdata}\\Temp\\vcc-build\\.gradle}"

if [[ -z "$gradle_user_home_win" || -z "$project_cache_win" ]]; then
  echo "Unable to infer Windows Gradle cache paths. Set VCC_GRADLE_USER_HOME and VCC_GRADLE_PROJECT_CACHE." >&2
  exit 1
fi

cd "$repo_root"

JAVA_HOME="$java_home_win" \
"$java_exe" \
  "-Dgradle.user.home=$gradle_user_home_win" \
  -classpath gradle/wrapper/gradle-wrapper.jar \
  org.gradle.wrapper.GradleWrapperMain \
  --project-cache-dir "$project_cache_win" \
  --no-daemon \
  "$@"
