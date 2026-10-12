# sg.7 verification and deployment notes

## Corrections

- Sneak + attack is intercepted before hit-target selection, so shoulder release
  works when aiming at air, blocks or entities. Server verifies current passenger
  and owner, chooses free space near player feet, honors canceled dismounts,
  resets motion/fall distance and detaches before normal positional tracking.
  It never repositions a rat on a stale request or uses the rat as player vehicle.
- Cage destinations now require a real bottom floor, a tube or a resting
  decoration. Existing unsupported upper residents descend to a supported cell
  rather than remaining suspended. Cage admission uses supported cells and the
  visible floor height (2/16 block). Controlled travel still prevents false falls.
  Upper igloos/hammocks and tubes remain accessible; decoration visits still rest.
- Horizontal tunnel floors: only missing air/fluid support is replaced by
  existing no-drop platforms at the configured tunnel elevation. Natural solid
  and partial floors are preserved. Entry/nearby return footing is repaired.
  Ore removed from the tunnel floor triggers footing restoration too.
- Generated cheese wall torches: light 14, no survival loot or item registration.
  Both horizontal walls every five blocks when there is a solid roof and wall
  support. Downward shafts get four wall lights every five layers when enclosed.
  No ceiling/wall support means no placement in an open cave. Placement honors
  protection, entity occupancy and loaded chunks. The native torch-wall model
  uses the original cheese block texture; no new bitmap artwork is needed.
- Assigned bands are preferred, not synchronization barriers. Idle workers may
  borrow unreserved blocks and inspect up to eight upcoming slices. Global
  progress advances only past entirely empty/ignored slices. Expiring exclusive
  target reservations still prevent two workers mining the same block.
- Ore Hunter: a bounded vein claim prevents two queues from contesting the same
  connected ore across worker bands. Claims expire, and existing live block
  leases are honored. Outside work requires supported approach and a reachable
  path back before moving away. An excursion anchor is saved for return to the
  tunnel before deposit; anchors survive NBT and clear on completed deposit.
  Targets beyond mining reach cannot be mined remotely through a fallback path.

Safety limits remain: no forced chunks, claim/griefing checks, no teleport rescue
through walls, no container mining, bounded ore search and unreachable-target
timeouts. Legitimate pauses can still occur if all nearby work is leased, terrain
is protected, a destination is full, chunks unload or a return route is obstructed.
This is not a claim that arbitrary mod/terrain combinations can never stall.

The discarded screenshot was not used as evidence of erroneous horizontal
platforms: the user confirmed it was a DOWN quarry. No existing generated blocks
were deleted, and no player worlds were edited during development.

## Verification

Java 17 build/data generation succeeded. Initial regression identified the house
rest pose mismatch after floor-height changes; admission was aligned to the same
height. Later DOWN-light test fixture was cleared at its actual shaft depth to
exclude natural stone occupying intended torch positions below its air volume.

`build/sg7-long-vein-tests.log`: all 49 required GameTests passed. Includes old
cargo/ownership/migration/AI tests plus shoulder detachment, settling from upper
Cage cells, light spacing/emission/no loot, four DOWN lights, cave crossing and
deposit, borrowing/advance without a shared-front barrier, unique vein ownership
and two Ore Hunters mining a longer cross-band external vein with floor ore and
returning all 13 expected drops. Exact item counts remain asserted.

Final-source repeat `build/sg7-final-repeat-tests.log`: all 49 required tests
passed again; build/reobfuscation successful. JAR contains the torch blockstate
and model, and excludes development test classes and the regression fixture.

Native-client input, lighting appearance, shoulder replication and Cage rendering
still require in-game verification. Automated server tests do not establish them.
Retest the actual modpack with multi-level Cage, Shift+left-click at each hit type,
workers in a cavern, recall with Ore Hunter, long/vertical veins and occupied or
protected floor holes. All players/server must use sg.7 together.

## Artifact / compatibility

Artifact: `build/libs/Rats-1.20.1-8.1.3-sg.7.jar`.

SHA-256: `643fc958dba10a5e6bc98ab07a5c36741920f048061b400dd4588ba7d163d4d9`.

No registry IDs were renamed. New generated torch block means a lossless downgrade
to older builds is NOT guaranteed. Original profile remains untouched. Installer
supports sg.7 with the same mod hash checks, closed-client guard and saves/jar
backup safeguards. Not installed during development.
