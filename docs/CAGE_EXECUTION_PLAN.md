# Cage overhaul — execution plan

Target: fork `SofiaRodFer/Rats`, branch `1.20`. Never push upstream.

## Small commit sequence

1. This plan and acceptance criteria.
2. Loaded, bounded connected-habitat discovery, capacity and safe admission/release.
3. Server-authoritative Cage menu, roster, naming, inventory/upgrade inspection,
   individual release and explicit male/female breeding.
4. Persistent assigned homes, habitat movement and decoration interaction.
5. Focused AI/integration fixes, including navigator churn and fluid immunity.
6. Regression GameTests, build, verification record and experimental version.

## Design decisions

- Six-face connected cages and tubes form one habitat. Only Cage blocks add
  capacity (two each); tubes connect habitats but add no capacity.
- No forced chunk loads. Incomplete/oversized habitats cannot accept new rats or
  breed; existing rats are never deleted when cages shrink. Discovery is bounded
  to 1,024 habitat blocks to prevent cyclic/unbounded world scans.
- Owner-only mutations; server rechecks membership, distance, sex, age, cooldown
  and capacity on every action. Inventory inspection is read-only and includes
  Silver Tray cargo. No client-supplied inventory mutations.
- Breeding is explicit, not random/automatic. A request produces one capacity-
  bounded litter; existing lantern/parent cooldowns persist. Babies count toward
  capacity and remain in the habitat.
- Empty-hand right click admits shoulder rats (adults and babies) if space exists;
  otherwise opens the interface. No click gesture releases rats. Shift-click
  decoration removal remains compatible.
- Assigned home is a dimension + Cage position persisted on the rat. Wander
  returns only to a loaded, intact habitat with room; unreachable paths back off.
- Native code-drawn Minecraft-style panels, original Cage/lantern/decor item art,
  no new block models or external texture dependencies.

## Verification

Automated: connected cycles/tubes, capacity including babies, incomplete safety,
breeding validation/cooldowns, home NBT, navigator identity, decoration effects,
and existing quarry/Silver Tray regression tests. Build with Java 17 and warnings
as errors. Manual client checks: roster selection, pagination, names, inventory
tooltips, release, paired breeding, house/food/water visits, tube travel and
Wander return; multiplayer ownership and chunk unload/reload. Record precisely
which checks ran; do not claim unobserved gameplay or all possible stalls fixed.
