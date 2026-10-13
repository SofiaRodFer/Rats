# Storage artwork — built-in imagegen

Mode: built-in image generation (no API key or CLI fallback). Five distinct
assets were generated individually, inspected, normalized with nearest-neighbor
to 16×16, and inspected again. Original upgrade frame/corner pixels are reused
exactly with `scripts/NormalizeUpgradeArt.java`; other tiles use
`scripts/NormalizeBlockArt.java`. No existing texture was overwritten.

## Saved runtime assets (absolute paths)

- `/Users/sofiarodfer/Documents/Codex/2026-10-03/users-sofiarodfer-downloads-tres-guardioes/work/rats-source/src/main/resources/assets/rats/textures/block/rat_storage_controller.png`
- `/Users/sofiarodfer/Documents/Codex/2026-10-03/users-sofiarodfer-downloads-tres-guardioes/work/rats-source/src/main/resources/assets/rats/textures/block/rat_storage_input.png`
- `/Users/sofiarodfer/Documents/Codex/2026-10-03/users-sofiarodfer-downloads-tres-guardioes/work/rats-source/src/main/resources/assets/rats/textures/block/rat_storage_output.png`
- `/Users/sofiarodfer/Documents/Codex/2026-10-03/users-sofiarodfer-downloads-tres-guardioes/work/rats-source/src/main/resources/assets/rats/textures/item/cheese_storage_staff.png`
- `/Users/sofiarodfer/Documents/Codex/2026-10-03/users-sofiarodfer-downloads-tres-guardioes/work/rats-source/src/main/resources/assets/rats/textures/item/rat_upgrade_storage_keeper.png`

Source images are retained under
`/Users/sofiarodfer/.codex/generated_images/01a102d8-9214-75b1-b931-5bd538295044/`:
`exec-ac6d6ee7-2e93-4d65-9d11-bc4fa70d822e.png` (controller),
`exec-132c8795-b12a-4526-8ee0-5cb5cff8b085.png` (input),
`exec-0fdb2c28-6b29-4f58-be8f-b61dae2eebbe.png` (output),
`exec-376467ca-f911-4975-a7b6-bfb8c21c3be6.png` (staff),
`exec-efa08a76-e36c-49ce-9e25-75dd55a8f76a.png` (keeper).

## Final generation prompt set

Controller: “Use case: stylized-concept. Asset type: Minecraft 16x16 block FRONT
texture, enlarged pixel art. ONE opaque square tile only, flat orthographic face
of Rat Storage Controller: vanilla-like oak cabinet brown planks, iron corner
bands, small cyan blue-cheese panel at top center, large black rat-sized doorway
at bottom center with a blue-cheese lintel. Low-resolution chunky pixels aligned
on a strict 16x16 grid, directly usable on cube face, pixel art no painted details,
no 3D perspective, no letters or text, no borders outside tile, no blur.”

Input: “Use case: stylized-concept. Asset type: Minecraft block FRONT texture.
ONE opaque square flat orthographic 16x16 pixel-art tile enlarged nearest-neighbor:
Rat Storage Input. Oak wooden parcel chest with gray iron corner straps, blue
cheese cyan patches and a large readable cyan arrow pointing DOWN toward a black
receiving slot. Full tile edge to edge, brown cabinet material, no outside
background. Strict chunky 16x16 pixel grid, flat straight-on texture not 3D block
render, no lettering, no text, no watermarks, no blur.”

Output: “Use case: stylized-concept. Asset type: Minecraft block FRONT texture.
ONE opaque square flat orthographic 16x16 pixel-art tile enlarged nearest-neighbor:
Rat Storage Output. Oak wooden parcel chest with gray iron corner straps, blue
cheese cyan patches and a large readable cyan arrow pointing UP out of a black
delivery drawer. Full tile edge to edge, brown cabinet material, no outside
background. Strict chunky 16x16 pixel grid, flat straight-on texture not 3D block
render, no lettering, no text, no watermarks, no blur.”

Staff (transparent): “Use case: stylized-concept. Asset type: Minecraft 16x16
item sprite enlarged nearest-neighbor. ONE Cheese Storage Staff: slender diagonal
brown wooden staff running bottom-left to upper-right, topped with cyan-blue
cheese cube and a tiny brown chest clasp. Same simple silhouette as Minecraft
Rats cheese staff, strict chunky 16x16 grid, readable at sixteen pixels, no yellow
upgrade frame, isolated item with genuinely transparent background, no lettering,
no text, no perspective scene, no antialiasing, no glow or watermark.”

Keeper (transparent outside icon): “Use case: stylized-concept. Asset type:
Minecraft Rats mod 16x16 pixel-art upgrade sprite displayed enlarged with
nearest-neighbor pixels. Create ONE square icon for Storage Keeper. Match
original Rats upgrade style: golden yellow square backing, dark ochre outline,
bright gold corner ornaments at all four corners, small blue cheese highlights,
centered tiny brown wooden chest resting on a silver platter. Strict very
low-resolution chunky pixel grid, readable at 16 pixels, flat front-on sprite,
no letters, no text, no perspective background, no blur, no antialiasing, no
watermark. The yellow backing is part of the icon; outside icon transparent.”

Plaques reuse the original blue-cheese block material and native Minecraft font;
they are code-native attached geometry, not generated sign images. Tube
connectors reuse existing six-axis Rats models/textures, now also connecting to
inventory capability faces. Native in-game appearance remains for user testing.
