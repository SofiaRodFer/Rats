# sg.9 — Cage inventory/breeding and quarry loop fixes

## Changes

Ore Hunter return anchors are consumed on arrival, rather than repeatedly
pulling a returning rat away from the quarry. Obstructed/unreachable saved anchors
are discarded without discarding cargo. Inventory deposit latches the return
state, including when triggered by fullness. Quarry approach and deposit attempts
are bounded even if a rat keeps moving in circles; deferred blocks and a short
retry backoff release the movement lock without teleporting or deleting items.
These address identifiable loops, not a guarantee that every possible modded
terrain/pathfinding failure has been eliminated.

Cage headroom is not a horizontal walking floor. Cage-to-Cage vertical travel
uses an access ladder, and routes revalidate their edges after topology changes.
Old unsupported positions settle towards a real floor, including while an
inventory prevents ordinary movement AI. Tube access and supported decorations
remain available. Upper shelf model edges are inset beyond all exterior bar
planes, using original materials. Ladders remain compact/vertical but a synced
65-degree climbing body pose replaces flat-looking ladder traversal.

The detail pane now separates **10 cargo cells** and **3 upgrade cells**. Unusable
cargo cells remain visible/dark, but are disabled. The player's full inventory is
available via a left-pane tab, keeping the 280×238 UI footprint. Helmet/banner
equipment remains intact and accessible through the existing rat inventory UI.
Ownership and resident membership are checked on the server; normal and
shift-click insertion respect stack limits, conflicts and combined upgrades.
Removing/replacing a capacity upgrade is refused until its cargo fits without
it. Selection changes are refused while holding a cursor stack. Client slot
prediction and server validation use matching constraints; vanilla menu packets
perform transfers, not copies of inspection items.

Automatic breeding is opt-in on the habitat's canonical breeding lantern and
defaults off in old saves. It considers only pairs owned by the enabling player,
requires adult male/female parents with expired cooldowns and available Cage
space, and creates **one** baby per event. The total living baby ceiling is
`floor(adults / 10)` across the habitat. Manual breeding keeps its existing
litter/cooldown rules; automatic breeding stops when its quota is already met.
Other lanterns cannot run parallel automatic events. Removing the lantern
stops its automation; no standalone scheduler/global habitat cache is added.
The owner can disable it even after releasing all residents.

## Compatibility and verification scope

Existing registry IDs, rat cargo/equipment NBT and quarry progress/configuration
are retained. `AutoBreedOwner` is optional; missing data means off. The climbing
flag is transient synced entity data. sg.9 does not introduce new registry IDs.
The network protocol is bumped to sg.9: all clients and server must update
together, rather than decoding the new Cage roster with an older layout.
This supports upgrades of existing saves, not lossless downgrades to the original
mod, which does not know items/cargo introduced in earlier fork versions.

Java 17 compilation and data generation succeeded. Earlier regression rounds
passed all 60, then 63, then 65 required server GameTests. The new tests cover
one-way return anchors, unreachable old anchors, unsupported headroom, live
capacity-limited inventory edits, ownership/residency/conflicting upgrades,
automatic baby quotas, full cages, foreign parents, optional NBT and roster wire
round-trips. The suite retains prior live mining/deposit tests with 2/4 workers,
cross-band Ore Hunter veins, cave floors and historical maintenance.

The first Cage run exposed a UUID-owner comparison issue in test/mock-player
paths and a stale old assertion that also included newly added player slots.
Rat mutation checks now use tame status + owner UUID; the original non-owner
extraction protection is still asserted specifically on all 13 rat slots.

Strengthening the settlement assertion to require the actual 0.125-block floor
height exposed a conflict with vertical tube departure: the floor correction
pulled a climbing rat back down. UP/DOWN tube ports now explicitly count as
access cells, preserving the unchanged tube round-trip regression. The failed
intermediate run is retained in `build/sg9-floor-final-tests.log`.

Asset geometry checks confirm the inset upper floor edges avoid exterior bar
planes. This is not a native client screenshot or shader/rendering check.
Native GUI interactions, actual climbing appearance, clipping and the user's
particular quarry worlds still need in-game confirmation. No player world,
Launcher profile or installed mod is changed during development.

Final source run: `build/sg9-tube-final-tests.log` — **all 65 required tests
passed**, followed by successful build/reobfuscation. No subsequent code changes.
Archive inspection confirms sg.9 metadata/protocol, source-matching upper shelf
models and translations, and exclusion of development tests/fixtures. The test
profile manifest still points to sg.8; sg.9 has not been installed.

Artifact: `build/libs/Rats-1.20.1-8.1.3-sg.9.jar`.

SHA-256: `9badd989fd440cdb896339c60a3a829ddb2d7d798f73e4ccb59cfe0b4fafedcf`.
