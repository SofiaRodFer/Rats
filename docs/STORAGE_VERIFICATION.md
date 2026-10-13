# sg.12 verification

## Results

- `build/sg12-safety-final-tests.log`: **all 101 required GameTests passed**, BUILD
  SUCCESSFUL in 1m 38s. 22 storage tests plus existing quarry/cage/farm regressions.
- `build/sg12-data.log` and subsequent data generation: successful models,
  blockstates, loot, recipes, advancements and English translations.
- `build/sg12-final-package.log`: **BUILD SUCCESSFUL in 9s**, including final
  client locale-aware sorting and reobfuscated release jar.
- Static jar inspection: five PNGs exactly equal to sources and all 16×16;
  four main crafting patterns and vanilla chest ingredients correct; generated
  language/resources exactly equal to packaged resources; Portuguese keys present;
  network protocol `sg.12`; dev test classes/fixture excluded; third-party mod
  classes/binaries not embedded.

Artifact: `build/libs/Rats-1.20.1-8.1.3-sg.12.jar`, 8,733,292 bytes.

SHA-256:
`f7096acf9df4daa67b840f35dc9ae26a9b296a44f6f5c6ecd0c6c94f5a635267`

Launcher deployment **not performed** in this implementation turn. The isolated
test profile still has the verified sg.11 artifact. The original Rats jar's hash
is unchanged. No player world was opened or modified by this work.

## Actual compatibility fixtures

Development-only `-PincludeStorageTests` stages and deobfuscates copies of the
user's existing installed Forge jars, without changing them or shipping them:

- Iron Chests 1.20.1-14.4.4:
  `6bc75f288d2e8158d2e23c6881a1b341a894c38bab129ca0f20db6abd59d373f`
- Sophisticated Core 1.20.1-1.5.4.2362:
  `e4a1f4f41293af59bb7486c41a965e3585d614217e934059abcca19a095eb594`
- Sophisticated Storage 1.20.1-1.5.0.2137:
  `c9deb82c461efd4877b2bd7936536ad4fdcc7890ef180118e12e04bb53523b31`

Iron/diamond chests of both mods were tested through actual side capabilities.
The real Sophisticated stack upgrade was installed into its upgrade handler and
tested with a >64 stored stack. Vanilla and Sophisticated double chests were
linked through both halves, counted once and tested after unlinking an alias.
Farmer's Delight 1.3.4 was also loaded for the prior farming compatibility test.

## Covered storage scenarios

- Simulation leaves contents unchanged; actual oversized slots, filters and
  limited destinations are respected.
- NBT variants stay separate, six-face access and Euclidean range include height.
- Ten assigned workers accepted, eleventh refused; source/destination reservations
  prevent duplicate collection. Two and ten workers physically finish full loads.
- Real physical input delivery, withdrawal, vertical climbing to a chest bottom
  face and Silver Tray mixed-item cargo in one trip.
- Broken routes, including the tube directly under the worker: cargo preserved,
  no fatal fall, repair resumes the delivery.
- Idle controller habitat is safe; recall exits physically and unregisters.
- Functional real 3×3 crafting, result consumption, cake bucket remainders,
  closure/reload persistence and separate player sessions.
- Invalid quantities/overbooking rejected; round-robin request fairness.
- Worker death frees its task/capacity and drops its held parcel exactly once.
- Partial insertion counts only accepted items; actual controller and entity NBT
  reload resumes the remaining delivery without recollecting or duplicating.
- Old/missing new tags default safely; disconnected chests cease being available.
- Replaced/unlinked input is not used by a previously reserved task; ownership
  and input/output roles are revalidated immediately before inventory access.
- Confirmed worker deaths persist in the ledger even when the controller is
  unloaded; restart cannot leave a known dead UUID permanently occupying a slot.

## Failures found and repaired

Initial parallel-space accounting subtracted reservations from an unnecessarily
small simulated offer, effectively blocking the second worker. Simulation now
includes reserved offers before subtracting reserved space.

A controller was initially a full suffocating cube. A stalled rat could die while
waiting inside it. It now has a hollow collision habitat and a non-suffocating
predicate, with explicit storage movement/fall guards. Dedicated tests cover
idle survival and tube removal underneath a loaded worker.

The ordinary planter regression fixture had a narrow floor over a void; scattered
wheat could fall off it. Its grounded margin was enlarged, retaining the exact
seven-wheat accumulation/deposit assertion. No planter production behavior was
changed to mask this fixture issue.

A first real Sophisticated-stack assertion inspected slot 0, which still contained
diamonds from the previous compatibility case. It now verifies the actual occupied
cobblestone slot and total, preserving the >64-stack requirement.

Packaging verification caught old generated recipe resources in an intermediate
jar: invoking data generation and build together can process resources before
the generator updates them. The final package was built in a separate invocation
after generation; byte-for-byte source-versus-jar checks confirmed the correction.

## Reproduction

With Java 17 and the installed fixture versions available, from the repository:

```sh
node scripts/prepare-storage-tests.cjs '/path/to/test-profile/mods'
node scripts/prepare-farmers-delight-tests.cjs '/path/to/FarmersDelight-1.20.1-1.3.4.jar'
rtk proxy sh scripts/run-regression-tests.sh -PincludeStorageTests -PincludeFarmersDelightTests
rtk proxy bash gradlew Minecraft_Data_1.20.1 --console=plain
rtk proxy bash gradlew build --console=plain
```

Keep data generation and the final packaging invocation separate. Set `JAVA_HOME`
to the local Java 17 installation if it is not already configured.

## Explicitly not verified

Native client interaction, rendered rat animations, plaque legibility, GUI scale,
and appearance in the user's actual world are **not claimed tested**. The user
explicitly chose to perform those checks after completion. Inventory capability
tests cover the versions above, not every storage mod/version or transforming
upgrade combination. No downgrade to original Rats after using new content is
guaranteed; restore the pre-update world backup for that rollback.
