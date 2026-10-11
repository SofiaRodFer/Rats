# Tamed egg verification — 8.1.3-sg.3

## Executed on 2026-10-11

- Java 17 compilation with warnings-as-errors; resource generation successful.
- `Minecraft_GameTestServer_1.20.1 build`: **all 30 required tests passed**.
  Log: `build/eggs-final-verification.log`. The 23 quarry/Cage/AI regressions
  remain enabled and seven new tests exercise actual server-world spawn paths.
- All 13 built-in variants spawn adult tamed rats with the using player's UUID;
  survival consumption, creative retention, naming, water use, hostile EntityTag
  rejection, unknown variants, ownerless use, unchanged spawners, powered
  dispensers, ordinary wild eggs, offspring refusal and NBT roundtrips verified.
- Initial all-variants test accidentally selected an adjacent previously spawned
  rat; corrected fixture isolation, then the complete suite passed.
- JAR includes the custom egg class and both languages, excludes development
  test classes/template. `git diff --check` and installer syntax check passed.
- Official launcher visibly displays **tres amigos teste**, Forge
  `1.20.1-forge-47.4.26`. Profile uses the original Java 17 and 2–6 GB arguments.
- Installed artifact hash matches build. All 43 non-Rats jars match the original
  pack byte-for-byte, with **44 jars total**. Existing launcher profiles retained.
  No worlds/accounts copied; original modpack and worlds remain untouched.

## Artifact and instance

`build/libs/Rats-1.20.1-8.1.3-sg.3.jar`

SHA-256: `c44a6ca270238023804b5d14baefa6d8a0e170ab396130abeaf7419a63ab1f80`

Game directory: `/Users/sofiarodfer/Library/Application Support/minecraft/tres amigos teste`

The instance's `RATS_TEST_INSTALLATION.json` records all original/installed mod
hashes and the launcher metadata backup. Its `rollback/` directory retains the
original Rats jar outside `mods/`. Do not place both jars in `mods/`.
Network protocol is `sg.3`: all three clients and a server must use this build.

## Using the eggs

Creative Rats tab: thirteen separate tagged eggs, including breeding-only
variants. Names are translated into English and Portuguese. The original egg
model is reused with variant tints, preserving its established visual style.
Use on a block or water; the player actually using the egg becomes owner. The
wild egg remains available. Example command (Minecraft 1.20.1):

```mcfunction
/give @s rats:rat_spawn_egg{RatsTamedEggVariant:"rats:blue"}
```

Dispensers/spawners do not have an owner, so tagged eggs refuse those operations
without consumption. Tagged eggs are not offspring eggs; click a block instead.

## Honest downgrade boundary

No new registry IDs are introduced by this feature. Saved spawned rats retain
the original `rats:tamed_rat`, `Owner` and `ColorVariant` format. Original branch
source was inspected and supports these fields. An unused egg retains the
original `rats:rat_spawn_egg` ID; old Rats ignores the marker and uses it as a
wild egg, not a tamed egg. Unknown item tags are not missing registry entries.

An actual world roundtrip through the old binary was **not** run. This is not a
promise that the whole fork can be downgraded losslessly. Earlier advanced quarry,
silver tray and lining IDs are absent from original Rats; extra cargo and
home/quarry configuration can be discarded if the old version saves a world.
Back up first and only downgrade a copy after removing/exporting new content,
emptying extra rat cargo and restoring original-compatible blocks/items.

Native-client creative listing/tint/tooltips and interactive spawning have not
been playtested. The server GameTests are the evidence for behavior; launcher
profile visibility is the only native UI verification claimed here. No existing
player world was opened, and no multiplayer ownership session was performed.
