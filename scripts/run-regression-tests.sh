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
bash gradlew "-PgameTestRunDirectory=$taskTestDir" Minecraft_GameTestServer_1.20.1 "$@" --console=plain
