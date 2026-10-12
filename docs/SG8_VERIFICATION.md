# sg.8 — quarry maintenance and alternating Cage floors

## Quarry rules and repair workflow

The quarry records historical built extents separately from active mining
progress. Loading older NBT seeds history from `QuarryProgress` before any width
migration resets active work. New optional history/cursor fields do not replace
inventory, length, pause state, direction, worker IDs or per-direction progress.
Saves without any recorded route cannot reveal which old caves were excavated;
those routes become known as rats revisit them during normal quarry work.

With assigned workers and Harvest enabled, the server inspects at most four
built slices per second across all five directions. It checks entry bridges,
horizontal footing, DOWN spiral steps, fluid containment and missing lights where
enclosure/support permits them. The scan is read-only and never loads chunks.
One expiring repair lease lets a rat return to the affected old slice, repair it
and return toward the quarry before resuming ordinary work. Mining reservations
and Ore Hunter queues are not used as repair targets.

Repairs preserve solid player blocks, chests, existing lights and block entities.
Claim/griefing checks and entity occupancy still apply. Inaccessible or protected
tasks back off rather than holding a rat indefinitely. Pause stops maintenance.
Repairs are gradual, not an instantaneous world rewrite; unloaded chunks or
unavailable workers postpone them. No automatic demolition of old platforms.

## Cage capacity, floors and paths

The lowest **Cage** Y in each connected cage/tube habitat is level 1; tubes do not
shift this base or add spaces. Levels 1,3,5… add two spaces per Cage block; even
levels add headroom only. A full 3×3 footprint has capacities 18,18,36,36,54 for
heights 1 through 5. Existing over-capacity rats remain alive/intact; admission
and breeding wait for available space. Removing/splitting blocks recomputes the
capacity from topology, without a stale global cache.

Existing Cage IDs gain derived `shelf`/`ladder` properties, not new blocks/items.
Upper odd floors and one access ladder per floor component are generated from
the connected structure. Offset layouts get a connected ladder route. Paths
cannot climb through a solid shelf: they use the opening and align first.
Tube ports retain a central opening and are tested in both directions. Residents
rest on supported floor sections, not in unsupported headroom. Decorations and
their item NBT survive refresh; deferred layout updates do not validate neighbours
against a half-updated set of shelves.

Placement/connection changes schedule refresh; random ticks, use and rat visits
also refresh older layouts. Incomplete/unloaded habitats are not rewritten.
The existing 1024-node safety bound and world height limits remain.

## Visual assets

Wider bars are native model geometry using an opaque metal strip from the
original `iron_bars.png`, which is unchanged and still used by unrelated blocks.
Four vertical bars and three rails replace the dense texture plane. Shelf and
ladder materials also come from the original mod. Scripts reproduce the models.

`build/cage-bars-preview.png` is a **front-projection asset preview**, not a game
screenshot. It reads the actual texture/model: original front open area 44%, new
61%. Tube-face art/ports remain intact. Native lighting/rendering still needs
client verification.

## Verification / compatibility

Java 17 compilation/data generation succeeded. `build/sg8-layout-routing-tests.log`
passed all 59 required GameTests, including earlier mining/cargo/AI coverage,
odd capacity through seven levels, live ladder access, offset floors, tube
round-trips, legacy palettes with optional/unknown properties, decoration item
preservation, old-route audit leases, physical repair in an inactive direction
without inventory/config loss, and respect for player/protected support blocks.

Earlier failure in the stronger decoration test exposed neighbour validation
during a partial layout refresh. Derived-property writes now preserve known
connectivity and validate pending floor support. Test assertions were retained.
Final entry-bridge coverage run `build/sg8-delivery-tests.log`: all 59 required
tests passed and build/reobfuscation succeeded. Independent fresh-world repeat
`build/sg8-delivery-repeat-tests.log`: all 59 required tests passed again. No
source changes between those final runs. Archive inspection confirms tier models
and maintenance/layout classes are included, while development tests/fixtures
are excluded.

No new registry IDs were introduced by sg.8. Minecraft 1.20.1's NBT reader accepts
old palettes and ignores unknown optional properties (tested). This supports
upgrading old saves; it is not a test of every earlier mod JAR in a native client.
Downgrades lose new derived behaviour/history, and versions unaware of the
fork's earlier added items/blocks/cargo cannot be promised lossless. Backups remain
necessary. All players/server must use sg.8 together.

Artifact: `build/libs/Rats-1.20.1-8.1.3-sg.8.jar`.

SHA-256: `799797fad2e87a5a4247c869ee14c498e325d487fb23ca57f19e0aa3198d2a6c`.

No player saves
or original launcher profile were changed. Not installed during development.
The guarded updater now accepts sg.8 and still requires the client to be closed.
