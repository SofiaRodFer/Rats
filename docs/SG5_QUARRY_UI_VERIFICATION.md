# Continuous quarry storage — 8.1.3-sg.5

Both basic and advanced quarries now show their 64 storage slots in one 8×8
grid. Configuration controls remain on the right, without a second inventory.
The player inventory moves below the expanded grid. Screen height is 280 GUI
pixels; width is unchanged at 276.

This is a layout-only change. Slot indices, item storage, NBT, registry IDs,
mining behavior and shift-click ranges are unchanged. No world migration is
required from sg.4. Earlier fork downgrade limitations still apply.

Java 17 `bash gradlew build` succeeded. The isolated-world regression run
`build/sg5-regression-tests.log` passed all 34 required GameTests. The new test
checks both quarry tiers, every slot position and its original contents, player
inventory placement, and item conservation when shift-click fills the player's
inventory. Native-client rendering has not been verified yet.

Artifact: `build/libs/Rats-1.20.1-8.1.3-sg.5.jar`

SHA-256: `bcb5aa04e2647298a29a86832106d5d4f4c65951c12c3cf9cd66413e977a74d5`

Not installed: the client was still running during preparation. The isolated
profile retains sg.4. `scripts/update-test-rats.cjs` now accepts sg.5 with the
same closed-client, mod hash, backup and isolated-profile safeguards as sg.4.
All players and the server should use the same build.
