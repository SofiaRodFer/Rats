# sg.6 execution plan

1. Cage physics: separate controlled habitat traversal from gravity/fall tracking;
   test a connected 3×3×3 cage with ordinary rats (no fall-immunity upgrades).
2. Quarry coordination: 5×5 / 7×7, 2 / 4 workers, persistent worker slots,
   disjoint column bands and expiring exclusive block reservations. Recall is
   owner-scoped and deposits cargo without changing the Harvest command.
3. Quarry worker tab and Cage master/detail UI: show actual rat models, names
   and gender; show pairing only with a breeding lantern, revalidate on server.
4. Ore Hunter: follow connected ore blocks beyond the tunnel, bounded to 512
   blocks within 16 blocks of the discovery. Do not force chunks, bypass claims,
   mine containers or expose unsafe fluid faces. Preserve vein work on deposits.
5. Upgrade textures matching the original gold/corner/central-emblem style;
   document a reproducible asset specification and add recipe/localization.
6. Java 17 build, isolated GameTests and artifact checks; native visual checks
   are pending until verified in a client. No automatic edits to player worlds.

Compatibility: retain existing registry IDs and item slot indices. Widening a
quarry resets its per-direction progress once, so older narrow tunnels are
revisited and expanded rather than silently skipped. New Ore Hunter is a new
registry item: lossless downgrade to older jars is not promised.
