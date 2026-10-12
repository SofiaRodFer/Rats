# sg.10 — quarry throughput and stalled crew

1. Reproduce with real goal scheduling: simultaneous 2/4-rat mining, cargo
   accumulation before returning, blocked faces and old persisted workers.
2. Keep a mining session alive between blocks; separate terminal completion from
   a temporarily blocked/shared frontier. Do not return one-item cargo merely
   because another worker holds a target or a safety repair must retry.
3. Bound recovery without losing cargo/reservations. Validate loaded legacy
   assignments against the quarry upgrade, retaining unloaded workers.
4. Run isolated server GameTests and build sg.10. Document exactly what passes,
   consolidate the quarry specification, and commit logical changes separately.
   Do not install or open the player's worlds during development.
