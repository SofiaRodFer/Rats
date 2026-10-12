# sg.7 execution plan

1. Handle sneak + attack at the client input layer (air, block or entity), and
   detach owned passenger rats to a collision-free position near player feet.
   Preserve server authority, canceled dismounts and stale-packet protections.
2. Horizontal quarries: bridge missing floor at the configured tunnel elevation,
   never at arbitrary rat Y. Preserve solid floors; honor claims/entities/fluids.
   Illuminate enclosed tunnels on both side walls every five blocks using a
   generated cheese wall torch with no survival drops.
3. Prefer assigned column bands, but borrow unreserved work instead of idling.
   Search a bounded next-slice window when current targets are all leased. Keep
   exclusive reservations and global progress only for fully cleared slices.
4. Regression tests: dismount validation/location, floors over voids, natural
   floors, light spacing/support/drops, blocked worker and shared lease safety,
   plus the complete previous suite. Compile and document pending client checks.

No automatic removal of generated blocks from player saves. New torch block ID
means a lossless downgrade is not guaranteed. Deploy only with the client closed
and with backups, into the isolated test profile.
