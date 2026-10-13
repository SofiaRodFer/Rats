# Storage design references (sg.16)

Consulted upstream source, not community forks, on 2026-10-13. No upstream
implementation was copied into Rats; these are architectural comparisons.

- [Tom's Simple Storage](https://github.com/tom5454/Toms-Storage):
  `InventoryConnectorBlockEntity` refreshes inventory discovery on staggered
  ticks, reuses face-specific inventory adapters, and invalidates detached
  connections. A tube topology must not flood-fill all surrounding air.
- [Refined Storage](https://github.com/refinedmods/refinedstorage):
  `ItemStorageCache` separates network membership from item totals and batches
  changes. `ItemExternalStorageCache` tracks content/count/slot-count changes;
  external inventory contents cannot be assumed immutable.
- [AE2 StorageService](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/main/src/main/java/appeng/me/service/StorageService.java):
  separates mounted storage providers from their cached contents.

Rats-specific constraints: the cache is presentation only, requests revalidate
actual capabilities, parcels remain in real rat cargo, and destination space
is reserved before pickup. A disconnected/full chest never authorizes deleting
items or teleporting inventory across the world. The initial menu snapshot is
sent only after the player's active container is installed, never cached as
delivered during constructor callbacks.

The references' instant transfers are not suitable for Rats' physical couriers.
Use their lifecycle/cache boundaries, not their gameplay implementation.
