# Cage / AI verification — 8.1.3-sg.2

## Executed (2026-10-11)

- Java 17 compilation with the project's warnings-as-errors configuration.
- `Minecraft_Data_1.20.1`: successful resource generation.
- `Minecraft_GameTestServer_1.20.1 build`: all **23 required GameTests passed**;
  build/reobfuscation successful. Log: `build/cage-final-verification.log`.
- Independent repeat: all **23 required GameTests passed** again;
  `build/cage-repeat-verification.log`.
- JAR contents checked: Cage screen/menu present; development test classes and
  the empty test structure are excluded.
- `git diff --check`: no whitespace errors.

The 14 Cage/AI tests cover connected tubes and topology changes, babies counting
toward capacity, valid/invalid breeding and persisted cooldowns, full-habitat
breeding refusal, safe exterior release, owner-only naming/home changes,
read-only inspection slots, home NBT and corrupt command/direction values,
navigator identity, Wander returning home, live cross-cell movement while in
SIT, bounded cyclic tree traversal, oversized cyclic habitats failing closed,
deferred drops not starving harvesting, distributed admission and house rest.

The existing nine quarry/cargo tests remain enabled, including horizontal and
advanced mining/deposit, downward return, unreachable-work recovery, persistence,
exact geometry, reservations and fluid safety. Failures during development
exposed a real return-path issue: accuracy-one waypoints stopped before adjacent
stairs, including stairs skipped during excavation because a rat occupied them.
The final implementation uses exact endpoints and protected local stair repairs.

## Artifact

`build/libs/Rats-1.20.1-8.1.3-sg.2.jar`

SHA-256: `9ea6e4ae80cd8d755184ed14f7dc09e14038e1d8d581e59ca81fbcce4d7f4222`

Experimental: not installed over the user's modpack or production world.
The network protocol is `sg.2`; server and all three players must use this same
build. Original Rats / sg.1 clients deliberately cannot connect with a mismatched
protocol. Existing block/item registry IDs, Cage decorations, wheel energy,
quarry storage and Silver Tray cargo remain compatible.

## Interaction and intentional limits

Empty-hand right click admits owned shoulder rats if room exists; when no rat
can be admitted, it opens management, including when carrying rats beside a full
habitat. No click removes rats; release is
individual in the UI. Shift-click decoration removal remains available. Select
a resident, choose another adult with **Pair**, then breed. A lantern and spare
capacity are required; each click creates one bounded litter, not an automatic
repeating breeding loop. Inspect items/upgrades without transferring them.

Home assignment persists by dimension and Cage position. A Wander rat can return
to that loaded habitat, with admission rechecked at arrival. Manual release waits
60 seconds before home return is eligible. Failed paths back off instead of
forcing chunk loads. Removing Cage blocks never deletes overflow residents;
admission and breeding wait until space is available. Tube blocks add no capacity.
Habitat discovery caps at 1,024 connected Cage/tube blocks; incomplete or oversized
habitats refuse admission/breeding. UI shows this condition rather than pretending
it discovered an entire unloaded habitat.

Houses/hammocks support synchronized resting; food/water decorations receive
animated visits. Bowls remain original decorative items: no new food stock,
infinite items or free healing system is introduced. Wheels keep their original
energy behavior and no longer retain a released/dead rat reference.

## Not yet verified in the native client

Computer-use reported the Mac locked; automatic unlock failed. No client visuals,
click interactions, or multiplayer session were observed, and no such test is
claimed. Still required before production use:

1. Inspect screen layout, Portuguese/English labels, pagination, names, item and
   combined-upgrade tooltips, selection, pair selection and per-rat release.
2. Observe houses, bowls, vertical Cage/tube connections and wheel use in-game.
3. Three-player ownership, stale UI actions and unload/reload/dimension changes.
4. Long mining/farming/tree/transport sessions, large colonies and protection-mod
   integrations in the actual modpack; long shafts/tunnels from sg.1's checklist.

The AI review fixes specific demonstrated risks, not a guarantee that every
possible mod interaction or future task can never stall. Original nonfatal
development warnings still occur; GameTest success and build exit status are the
verification criteria, not an assertion of a warning-free runtime.
