# Cage / horizontal quarry corrections — 8.1.3-sg.4

## Report and changes

The user reported disappearing/high-altitude rats on shoulder-to-Cage admission,
and a horizontal quarry rat with Silver Tray stopping and constructing dry cheese
walls. No player world was modified during investigation. Read-only inspection
confirmed a horizontal advanced quarry at progress zero in the isolated test save.

Small commits implement:

- Cage admission clears inherited velocity, jumping/flying/rest/wheel state and
  old running goals, switches navigation immediately and resets old coordinates.
  Clients receive the updated passenger list before normal position tracking.
  Vanilla owns its position codec; manual teleport packets are not mixed with
  subsequent relative movement. Canceled dismounts refuse admission.
- Shoulder dismount packets require the requesting owner to still be the actual
  vehicle. Late packets after admission cannot move the rat out of its Cage.
  Removed reversed passenger/vehicle location calls and client-side prediction.
- Dry air/replaceable space no longer triggers an entire lining shell or entry
  floor. Lining seals fluids and air immediately adjacent to fluids only. The
  original DOWN-only return platforms remain: these are separate from lining.
  Existing generated walls are not automatically deleted from player worlds.
- Horizontal approach uses the current tunnel level rather than forcing the
  rat into unmined stone at the quarry base or onto an unreachable ceiling.
  Mining reach covers the configured cross-section but only a nearby front.
  Horizontal travel never invokes downward spiral stairs/repairs.
- Successfully mined blocks reset the stationary-work watchdog. Actual progress
  must not be mistaken for stalled work just because the rat has not walked.

## Evidence and test harness corrections

The pre-fix run failed both new shoulder-velocity and dry-lining tests:
`build/bug-reproduction.log`. A buried horizontal entrance with Silver Tray also
failed before approach changes: `build/buried-entry-reproduction.log`.

Repeated testing exposed two independent fixture defects: persistent development
entities contaminated capacity/ownership assertions, and a structure with zero
block entries did not clear randomly generated terrain. The regression runner
now creates a new world for each run, and the structure explicitly places air in
all 24×12×24 cells. No player worlds are used by these tests.

`build/sg4-clean-fixture-tests.log`: **all 33 required GameTests passed**, Java 17
warnings-as-errors compilation, build and reobfuscation successful. The original
30 tests remain, plus shoulder admission with live ticks and absolute-position
tracker/late-packet assertions, dry horizontal lining, and buried entrance with
Silver Tray. Dry mining tests use natural stone floors, not generated lining.
JAR inspection confirms development test classes/structure are excluded.

Independent repeat: **all 33 required tests passed again**, build successful,
recorded separately in `build/sg4-clean-fixture-repeat.log`.
Use `JAVA_HOME=<Java 17> sh scripts/run-regression-tests.sh build` to reproduce.

## Artifact / pending installation

`build/libs/Rats-1.20.1-8.1.3-sg.4.jar`

SHA-256: `6370e6d2d9cd5d75bf886615fc839b3ba6638b29a85bbdd3f36d60db1d4d8149`

Protocol `sg.4`: all players/server must use the same build. No registry IDs or
save-format migrations were added by these bug fixes. Earlier fork downgrade
limitations still apply.

**Not installed yet**: Minecraft remains open and the user chose to close it
later. The test profile retains sg.3; the original profile is untouched. After
the user closes Minecraft, `scripts/update-test-rats.cjs` can validate the pack,
back up the test saves/manifest/previous jar, replace only Rats and record its
hash. It refuses an open client and changed mods. Do not load two Rats jars.

Native-client admission/rendering and the exact reported sky-height symptom have
not been observed/reproduced in a client. Server physics and tracking packets
are tested; this is not a claim of a verified three-player session or a guarantee
that all terrain/mod interactions can never stall. Retest in the actual modpack
after installation, especially existing tunnels and deep return paths.
