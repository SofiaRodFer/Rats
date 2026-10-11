# Tamed eggs / isolated launcher profile

1. Keep the existing `rats:rat_spawn_egg` ID and add tagged creative variants for
   every registered RatVariant. Keep the wild egg unchanged. Spawn only through
   a player action; assign the actual player's UUID server-side before insertion.
   Refuse dispensers, spawners and offspring interactions for tagged eggs.
2. Reuse the original two-layer egg model with variant-colored shells, translated
   names and ownership/usage tooltips. Add server GameTests for all variants,
   ownership, hostile NBT, consumption, invalid variants and persistence.
3. Generate resources, run the full test suite, build/reobfuscate sg.3, then clone
   only modpack configuration/mods into `tres amigos teste`. Preserve every other
   mod's bytes, exclude worlds/accounts, back up launcher metadata and verify it.

## Downgrade boundary

These eggs introduce no new item/entity/variant IDs. Older Rats treats unused
tagged eggs as ordinary wild rat eggs; already spawned rats use its existing
`rats:tamed_rat`, `Owner` and `ColorVariant` save fields.

This is not a blanket promise of safe downgrade for earlier fork additions:
advanced quarry, silver tray and quarry lining are new registry entries and
extra cargo/home/quarry state is not understood by original Rats. Back up worlds
before opening them with another version. Never downgrade/save the only copy.
No existing world will be copied or opened by the test-profile installer.
