# sg.11 — farming station and world-bottom quarries

1. Remove the advanced quarry's downward cap; retain horizontal limits/NBT.
2. Add Rat Farming Block, requested recipe, 54-slot storage, dimensions up to
   16x16, facing, two-worker exclusive half-field leases and saved ghost seeds.
3. Add storage/layout UI with reserved water/light cells. Ghost painting never
   consumes seeds; actual planting needs real seeds. Validate menu/distance/index.
4. Prepare flat dirt: minimum water coverage, ground light >=9, harvesting,
   planting, supplies and deposit. Replanter repairs soil, water and lights.
   Never bulldoze builds, load chunks or discard cargo to recover a task.
5. Improve existing Farmer/planter outside the block: accumulate cargo and latch
   return until deposit. Retain bonemeal, ordinary planting and crop support.
6. Generate/inspect pixel farm textures and a proper blue-cheese torch sprite.
7. Add geometry, lighting, NBT, ghost safety, live two-worker/repair/return tests;
   run prior regressions, build sg.11, record hash and commit small stages.
   No installation/player-world opening/native client verification claimed.

User clarification: the station DOES level its plot to the ground immediately
below the station. Clear natural terrain, preserve non-terrain/machines/protected
blocks, and use real stored dirt to fill missing ground (no free dirt generation).

Assumptions: real dirt/seeds supplied by the player. Generated
water/light infrastructure requires no consumable buckets/torches; lights have
no drops. Resizing/facing leaves old terrain untouched. Plan supports farmland
crops, not plants requiring sand, adjacent water or soul sand.
