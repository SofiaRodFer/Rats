# sg.8 execution plan

1. Bounded, persistent-cursor quarry audits of built slices in every direction;
   one repair lease, physical return/repair and backoff for inaccessible/protected
   work. Keep inventory/config and historical extents when loading older NBT.
2. Cage capacity based on the lowest cage Y of each connected habitat: two spaces
   per cage on odd numbered levels; tubes and even levels add none. Over-capacity
   existing residents remain; admission/breeding pauses until there is room.
3. Derive shelves and interior ladders using optional properties on existing Cage
   IDs. Recompute after topology changes/use/rat visits without forced chunks.
   New floors have an access opening; movement aligns with the ladder before
   climbing and rests on the solid section of the floor.
4. Wider-spaced native model bars preserving original metal material, and matching
   shelf/ladder models. Avoid changing textures shared by unrelated blocks.
5. Test old NBT, 1–N tiers, topology splits, decorated cages, capacity, floor access,
   maintenance across inactive directions, protection and cargo conservation.

Upgrade compatibility is tested; older versions ignore new optional NBT/properties
but cannot reproduce new behavior. Previous fork item/block downgrade limitations
remain. No player saves are modified in development; deployment remains guarded.
