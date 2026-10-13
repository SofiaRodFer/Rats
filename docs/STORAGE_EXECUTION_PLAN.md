# Rat Storage System — sg.12

## Approved scope

Physical inventories, physical rat transport, no teleportation of items. The
controller is an index, not infinite storage. Storage and functional 3x3 crafting
share one side-by-side page; other pages show workers and system diagnostics.
Players test the complete addition at the end, not between implementation steps.
Automated tests and build checks remain part of development.

One controller, ten adult owned rats with Storage Keeper; one assignment per rat
and endpoint. Inputs/outputs (54 slots) share a face with the controller and use
internal docking connections. Remote chests require a continuous tube route.
Endpoints and tube routes stay within Euclidean radius 20 in the same dimension.
Unloaded chunks are unavailable; no implicit chunk loading.

Stock identity includes complete item NBT, damage and enchantments. Counts use
longs in the catalog, not oversized real ItemStacks. Reservations subtract from
available stock. Revalidate actual insertion/extraction at each endpoint. Keep
all extracted cargo physically in rat inventory, including after reload/failure.
No destructive inventory writes through another mod's handler.

Independent stateful worker goal: select, travel to source, collect useful batch,
travel to destination, deposit. Ten parallel workers, independent bounded retries;
no waiting for every worker, no objective switching during delivery. Persistent
assignment/cargo/task identity; transient routes reconstructed after reload.
Withdrawal > input distribution > explicit low-priority rebalance.

Categories: terrain/building, ores/metals/gems, wood, food/farming, creature
drops, equipment, other. Prefer same item, then same category, then empty chest,
then explicit overflow. Existing mixed inventories are not silently moved.
Blue-cheese category plaque is a non-dropping attached visual, not world block
replacement. Double chests counted once.

Staff selects controller, links endpoint/rat, sneak unlinks/inspects. Menu actions
are server-authoritative, owner/shared-player authorized, distance/container
validated, bounded and rate-limited. Removing a rat recalls cargo safely before
physical exit at controller; broken routes expose pending removal.

Crafting is manual, per-player, actual ingredients. Assist requests bring missing
ingredients to a persistent per-player receiving buffer. Recipe remainders and
closed sessions never lose items. No autocrafting or recipe dependency tree.

## Harder recipes (each line is a crafting row)

- Controller: blue cheese block / diamond / blue cheese block;
  ender pearl / crafting table / ender pearl;
  gold ingot / chest / gold ingot.
- Input: blue cheese / diamond / blue cheese;
  gold ingot / hopper / gold ingot;
  iron ingot / chest / iron ingot.
- Output: blue cheese / diamond / blue cheese;
  gold ingot / dropper / gold ingot;
  iron ingot / chest / iron ingot.
- Keeper: blue cheese / ender pearl / blue cheese;
  diamond / rat upgrade platter / diamond;
  gold ingot / chest / gold ingot.
- Staff retains previous proposal using comparator, cheese staff, blue cheese,
  iron ingot. No new intermediate crafting items, no netherite dependency.

## Commit sequence / evidence

1. Specification and safe inventory/routing primitives.
2. Registered blocks, staff, keeper, persistence and linking.
3. Dedicated parallel transport and recovery.
4. Catalog/request packets, combined storage/crafting UI and administration.
5. Categories, plaques, crafting assistance and diagnostics.
6. Original-style art, recipes and translations.
7. Automated tests, actual compatibility fixtures, release build and handoff.

Tests include six faces and vertical routes, full/filtered/oversized inventories,
NBT variants, two simultaneous requests, ten workers, break route/block while
loaded, partial insertion, death and reload, crafting remainders and closure,
old worlds/default missing tags, quarry/farm/cage regressions. Iron Chests and
Sophisticated Storage compatibility requires tests with the installed versions,
not merely successful Forge capability lookup. Native gameplay/visual validation
is explicitly deferred to the user; never claim it has been performed.
