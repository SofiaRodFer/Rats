# sg.6 quarry crews, Cage UI/physics and Ore Hunter

## Behavior and compatibility

- Basic: 5×5, 256 horizontal blocks / world bottom downward, at most two assigned
  rats. Advanced: 7×7, 512 blocks either direction, at most four assigned rats.
- Column bands partition the current loaded Harvest crew. Exclusive expiring
  reservations cover both tunnel blocks and external vein blocks. Returning
  rats keep their assignments. Death, reassignment and dimension changes free
  worker slots; chunk unloading preserves them. No forced chunk loads.
- Return/deposit button recalls only the viewer's loaded workers with cargo.
  They deposit and resume Harvest, retaining Ore Hunter work. A full destination
  keeps cargo rather than dropping/deleting it. Unloaded rats cannot move until
  their chunk is loaded. Their names/colors still appear in the roster.
- Quarry storage remains one 8×8 grid with the same 64 indices and NBT items.
  Rats tab replaces only the storage view; player inventory remains available.
- Widening revisits old tunnels: old per-direction progress resets once when
  the saved width differs, while inventory/direction/length are preserved.
- Cage-controlled vertical traversal suppresses gravity/fall tracking inside
  cages and tubes, not fall damage globally. Release resets motion/fall distance
  and restores normal navigation/gravity. Multi-level test uses an ordinary rat,
  without Quarry or any other fall-immunity upgrade.
- Cage left pane is names/genders/rat-model portraits. Details, rename, home,
  release and read-only inventory appear only after selection. Pair selector
  cycles eligible opposite-sex owned adults; its tooltip shows the full name.
  Pair selector, breeding action and lantern icon are hidden without a lantern.
  The server still checks lantern, owner, cooldown, capacity and current residents.
- Ore Hunter works only alongside Quarry, including combined upgrades through
  the original upgrade lookup. Connected ore uses Forge's `forge:ores` tag and
  26-neighbor connectivity, including diagonals. It follows exposed wall veins
  outside the tunnel, then resumes quarry work. Pending positions survive save
  and deposits; changing the quarry clears old work.
- Safety bounds: 512 queued ores, within 16 blocks of each discovery, loaded
  chunks only, claim/griefing checks, no container mining. Adjacent fluid faces
  are sealed before exposing external ore. Unreachable ore times out after 200
  goal ticks rather than trapping the worker forever. Blocked/protected/oversized
  or unloaded portions are not a promise of an unconditionally complete vein.
- Default Ore Hunter recipe (chosen because none was supplied): diamond at top
  center; iron ingot / iron pickaxe / iron ingot in the middle; blue cheese /
  basic rat upgrade / blue cheese at bottom. Existing upgrade recipes unchanged.

New Ore Hunter registry ID means a lossless downgrade to older jars is NOT
guaranteed. Existing IDs were not renamed. Keep backups; original worlds/profile
were not touched. All players/server must use protocol/build sg.6 together.

## Evidence

Java 17 compilation and data generation succeeded. After fixing issues exposed
by earlier runs, `build/sg6-crew-repeat.log` passed all 40 required GameTests,
including live two/four-worker mining/deposits, exposed wall vein extraction,
worker capacity/partition/recall ownership, migration, cargo persistence and
ordinary-rat 3×3×3 habitat survival. The downward fixture now covers the full
5×5 width instead of retaining a partial old 4×4 floor. Successful live quarry
tests stop their workers to avoid continued activity contaminating later checks.

Final-source run `build/sg6-controlled-final-tests.log`: all 41 required tests
passed, followed by successful build/reobfuscation. This includes the new
512-ore bound test. The development fixture now caps its ceiling with barriers
to exclude falling terrain from outside the cleared volume; the horizontal
single-worker deadline matches the 6000-tick advanced/crew tests. Exact deposited
item counts remain asserted, not weakened. Earlier failed runs are retained.

Artifact: `build/libs/Rats-1.20.1-8.1.3-sg.6.jar`

SHA-256: `972818f5a6cb0c399b9f86f548b3e16959233c2080728f65dad1f4bddc3bd2a9`

JAR inspection confirms new models, textures and recipes are included; development
GameTest classes and the fixture are excluded. Independent repeat in another
fresh world (`build/sg6-controlled-repeat-tests.log`): all 41 required tests
passed again; build successful. No source changes between those final runs.

Textures are 16×16 RGBA, built-in image generation followed by deterministic
nearest-neighbor normalization with exact original frame/corners. Source files:
`src/main/resources/assets/rats/textures/item/rat_upgrade_silver_tray.png` and
`rat_upgrade_ore_hunter.png`; generation specification: `UPGRADE_ART_STYLE.md`.

## Native-client verification still required

No claim of verified native-client rendering or a three-player session. Inspect
Cage portraits and selection, remove/replace the lantern while the menu is open,
rename and release, test shoulder admission at every level of a multi-level
Cage. Check quarry tabs, full crew rejection and recall during mining/deposit,
including a full quarry. Test veins farther than the simple regression vein,
vertical veins and modded ores with lava, protection and chunk boundaries.

Not installed yet; Minecraft was open during development. Use the guarded
`scripts/update-test-rats.cjs` only after closing the client, with backups.
