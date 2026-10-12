# sg.11 — verification and release

## Results

- `build/sg11-level-tests.log`: all 76 required server GameTests passed.
- `build/sg11-final-tests.log`: all 78 required tests passed with the actual
  installed Forge Farmer's Delight 1.3.4 staged/deobfuscated for development.
- `build/sg11-complete-tests.log`: **all 79 required tests passed**, then
  **BUILD SUCCESSFUL in 58s**, 2026-10-12 20:34 local. Covers final server source.
- Subsequent changes only fix the quarry client's Down label/disabled length
  buttons for both tiers. `build/sg11-release-build.log`: successful release
  build/reobfuscation in 11s. No subsequent Java/resource changes.

New coverage includes every width/height 1–16, water/light geometry, ghost cursor
preservation and anti-extraction, out-of-range edits, NBT storage/plans, two
exclusive halves and worker limit, latched returns, real supply fetching,
hill removal/hole filling, two-worker planting/harvesting/deposit and repairs,
ordinary Farmer harvest accumulation, legacy pumpkin seeds, preserved machines,
rotations/bounds and Farmer's Delight tomato/cabbage/onion seed/stage support.
All prior quarry and Cage regressions remain in the suite.

## Failures investigated, not suppressed

The first farm fixture floated its entry side over air and allowed scanning
natural blocks above the fixture roof. Added a real ground entry to the fixture
and made roof/protected obstructions terminate column clearing in production.

The ordinary Farmer test exposed a nested start-delay issue: the wrapper's delay
must not also throttle the crop delegate. The delegate now offers `findWork()`
without a second timer. A remaining failed fixture had no light under its roof;
native neighbor updates correctly destroyed dark crops. The fixture now provides
ground lighting and waits for propagation before creating mature wheat. The
assertion still requires all seven wheat delivered and multi-item accumulation.
Failed runs remain in `build/sg11-tests.log`, `build/sg11-compat-tests.log` and
`build/sg11-compat-final-tests.log`.

## Reproduction

Use Java 17. Stage the already-installed Farmer's Delight jar with
`node scripts/prepare-farmers-delight-tests.cjs /absolute/path/FarmersDelight-1.20.1-1.3.4.jar`.
Then `sh scripts/run-regression-tests.sh -PincludeFarmersDelightTests=true build`.
The optional Gradle dependency uses `build/compat-maven`, never Launcher mods.
Without this property the standard suite works without Farmer's Delight; its
specific compatibility assertion is skipped when that mod is absent.

Staged third-party SHA-256:
`34f03eb671ea1ca579b00c98e4541404cb96d1a292014aed9cc5eb45ad8afbce`.
No third-party binary is included in Rats or committed.

## Artifact and boundaries

Artifact: `build/libs/Rats-1.20.1-8.1.3-sg.11.jar`.
SHA-256: `ae7ca02ffddd1e687a620b6914b62a388cad8a54253bfccd727959717a6a4b26`.
Archive inspection confirms sg.11 metadata/protocol, exact crafting recipe,
source-matching 16x16 textures, dedicated wall-torch sprite and translations.
Development tests/fixtures and the Farmer's Delight binary are excluded.

The installed test profile remains sg.9 with its hash unchanged. No player world
was opened or modified. Runtime assets were visually inspected as image files;
native client appearance, menu interactions and the player's terrain still need
in-game confirmation. The 330x230 farm UI layout fits 720p at GUI scale 3 by
coordinate inspection; this is not a native screenshot test.

Existing IDs/NBT are retained. Farm fields are optional/new; both Down quarry
tiers use world minimum height and horizontal progress/length remains intact.
Protocol sg.11 requires matching clients/server. Upgrading old saves is supported;
downgrading saves containing new blocks/items to the original mod is not lossless.

Usage: `docs/FARMING_PT_BR.md`; art generation: `docs/SG11_ART.md`.
