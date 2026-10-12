# sg.9 execution plan

1. Fix quarry state transitions: consume Ore Hunter return waypoints once,
   bound approach attempts even when moving in circles, defer inaccessible work,
   and test return/mining cycles with real goal scheduling.
2. Restrict Cage headroom traversal to access routes. Settle legacy unsupported
   residents safely, including while the inventory is open. Inset upper floors
   to avoid coplanar bars and give ladder traversal a climbing pose.
3. Replace read-only mixed inspection with ten cargo slots and three upgrade
   slots plus the player inventory. Revalidate ownership, residency, capacity
   and upgrade conflicts server-side for every edit; preserve disabled slots.
4. Add opt-in automatic breeding to the lantern, off in old saves. Require
   available capacity, same-owner adult pairs and at most floor(adults / 10)
   living babies. Spawn one per automatic event, retaining parent cooldowns.
5. Run isolated server regressions, build sg.9 and document exact results and
   remaining native-client checks. Do not change player worlds or install until
   authorized. Keep existing IDs/NBT and commit each logical change separately.
