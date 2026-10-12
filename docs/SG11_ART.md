# sg.11 pixel assets

Built-in image generation was used, with original vanilla torch/composter/hay
tiles and Rats' blue-cheese texture as material references. Source generations:

- `exec-a92ca51e-d689-49f9-8d64-7f256e06da09.png`: transparent flat torch;
  preserve narrow vanilla silhouette and orange/yellow flame, change shaft to
  pale blue/cyan mottled blue cheese. No cube, perspective, text or backdrop.
- `exec-de19047b-7f49-4a2e-8756-eaf5faf3efe8.png`: opaque flat composter side;
  warm vertical wooden slats, hay band, restrained cheese panel and rivets,
  vanilla/Rats pixel material style; no icon frame, text or perspective.

The prompts requested 16x16 logical pixels with nearest-neighbor enlargement.
`scripts/NormalizeBlockArt.java` performs nearest-neighbor normalization only.
Runtime files are in `src/main/resources/assets/rats/textures/block/`:
`rat_quarry_torch.png` and `rat_farming_side.png`, both 16x16 RGBA. The farm uses
the vanilla composter model with this side texture and a hay top rim. The torch
uses the vanilla wall-torch model and its own transparent sprite, rather than
mapping an entire opaque cheese-block texture onto a torch.

Native client appearance still requires verification. Do not call this an
in-game tested render. Generated source files remain in Codex generated_images;
the runtime assets are copied/normalized into the repository, not linked there.

## Final prompt set (built-in tool)

Torch: "Minecraft 1.20.1 block torch texture sprite. Retain the tiny centered
16x16 vanilla torch silhouette/UV placement, shaft x7..8/y8..15, flame
x6..9/y2..7. Change only the wooden shaft to pale blue/cyan mottled blue cheese
matching Rats' source texture. Keep vanilla orange/yellow flame and transparent
area. Flat pixel sprite, no perspective/model render, props, words, frame or
antialiasing. Nearest-neighbor enlarged tile with genuine transparency."

Farm: "One seamless opaque 16x16 flat Minecraft block side texture. Original
vanilla composter planks and hay-bale straw as style/material references.
Vertical warm brown wooden slats, dark brown top/bottom rim, golden woven hay
band in lower third, restrained cheese-yellow rivets/holes and integrated
central cheese panel. Authentic vanilla/Rats material style, not an inventory
upgrade frame. Orthographic full-square surface, no perspective, cube, margins,
text or antialiasing; nearest-neighbor enlargement."
