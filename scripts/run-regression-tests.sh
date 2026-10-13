#!/bin/sh
set -eu
taskRepoDir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
mkdir -p "$taskRepoDir/build"
taskTestDir=$(mktemp -d "$taskRepoDir/build/gametest-XXXXXX")
# Preserve development configuration, but never reuse its entities/world.
for taskEntry in config defaultconfigs mods; do
  if [ -d "$taskRepoDir/run/$taskEntry" ]; then
    cp -R "$taskRepoDir/run/$taskEntry" "$taskTestDir/$taskEntry"
  fi
done
printf 'Isolated test world: %s\n' "$taskTestDir"
cd "$taskRepoDir"
taskTestLog="$taskTestDir/gradle.log"
if ! bash gradlew "-PgameTestRunDirectory=$taskTestDir" Minecraft_GameTestServer_1.20.1 "$@" --console=plain > "$taskTestLog" 2>&1; then
  tail -80 "$taskTestLog" >&2
  exit 1
fi
# Forge's launch task may exit zero after a mod bootstrap failure. Successful
# Gradle execution alone is not evidence that any GameTest actually ran.
if command -v rg >/dev/null 2>&1; then
  rg 'All [1-9][0-9]* required tests passed' "$taskTestLog" || { tail -80 "$taskTestLog" >&2; exit 1; }
else
  grep -E 'All [1-9][0-9]* required tests passed' "$taskTestLog" || { tail -80 "$taskTestLog" >&2; exit 1; }
fi
printf 'Full diagnostics: %s\n' "$taskTestLog"
