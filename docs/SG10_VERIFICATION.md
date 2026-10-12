# sg.10 — quarry throughput and recovery

## Corrected causes

- RatQuarryGoal ended its MOVE/LOOK session after every block, imposing the
  harvest-start delay again. It now selects the next exclusive target within
  the running session. Capacity, recalls, pause and completion still end mining.
- Shared blocked/unloaded status and failed safety/path checks incorrectly
  requested a partial deposit. They now retry/defer work without forcing a
  one-item return. Full inventories and drops that do not fit still return.
- A buried Ore Hunter target with no supported exposed approach fell back to
  the rat's own position. A self-path reported success and wasted 200 goal ticks
  per saved target. Such targets now release their claim and yield to next work.
- Loaded former miners could occupy crew slots after losing the quarry upgrade.
  Optional worker metadata and validation release retired assignments. Legacy
  entries without the marker are treated as miners; unloaded UUIDs are retained.
- Each target scan resolves the active crew once, avoiding up to 392 repeated
  full crew/entity lookups. Mining reservations remain exclusive.

No registry IDs, cargo format, ore work format or quarry progress fields were
removed. Worker metadata is optional. Network protocol remains sg.9 because
packet layouts are unchanged. Use matching fork versions on all clients/server.
Forward save compatibility is preserved; lossless downgrade to the original
mod is not promised.

## Evidence and test hygiene

`build/sg10-before-tests.log` reproduced interrupted mining sessions and stale
crew slots against the previous implementation. It also revealed a resource
count failure. Strengthened scheduled tests record peak simultaneous miners and
require every worker to accumulate multiple items, rather than accepting only
eventual group completion.

`build/sg10-final-tests.log` passed all 69 tests, including the resumed enclosed
tunnel with four Ore Hunters, continuous sessions, blocked cargo, retired
workers and the prior Cage/compatibility regressions.

Adding the buried saved-ore test exposed interference between tests: a finished
direct-goal fixture left its rat in Harvest. TamedRat's normal tick clears NoAI
when not respawning, so that rat went on mining outside its fixture. The failed
advanced test stored 54 of 98 blocks, and the foreign rat's saved inventory held
exactly the remaining 44. Added goal accounting confirmed 54 mined, 54 harvested,
zero overflow. This was not cargo silently deleted by the active test miner.
The new direct-goal fixtures now explicitly set their rats to Sit when done.
Failed runs are retained in `build/sg10-complete-tests.log` and
`build/sg10-accounting-tests.log`.

The final run includes the additional buried saved-ore regression (70 tests),
build/reobfuscation and all final source changes:
`build/sg10-isolated-final-tests.log`: **all 70 required tests passed**, followed
by **BUILD SUCCESSFUL in 1m** (2026-10-12, 19:53 local). No subsequent Java changes.

## Delivery boundary

Artifact: `build/libs/Rats-1.20.1-8.1.3-sg.10.jar`.
SHA-256: `0e6987d661df1c054413add31f42d16710e1676dd365ef8821b8f6c4c75f7d70`.
Archive inspection verifies sg.10 metadata, the quarry goal class, and exclusion
of development tests/fixtures. The installed test profile remains sg.9 with its
previous SHA-256 unchanged. Development uses temporary GameTest worlds;
the player's saved worlds were only read for entity diagnostics. No Launcher
profile or installed JAR was changed. Native client/world verification remains
pending; server GameTests are not a claim of testing the user's live game.

The Portuguese quarry specification is `docs/QUARRY_SPECIFICATION_PT_BR.md`.
