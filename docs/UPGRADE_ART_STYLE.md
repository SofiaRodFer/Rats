# Rats upgrade artwork specification

Use the original `src/main/resources/assets/rats/textures/item/rat_upgrade_platter.png`
as the style/frame reference, not a modern inventory icon or a gray square.

- Final texture: 16×16 RGBA PNG, no text, margins, blur or smooth gradients.
- Gold/yellow cheese plaque background; original dark silhouette and highlights.
- Original corner fittings at all four corners. One centered readable emblem.
- Silver Tray: silver oval serving tray, white highlights/dark rim, small violet
  amethyst accent. Ore Hunter: gray ore with turquoise flecks and an iron pickaxe.
- Keep the inventory model `minecraft:item/generated`; the background belongs
  to the upgrade texture, not to a separate GUI overlay.

Generation used the built-in image tool with the original platter as reference.
Prompt template: “Minecraft Rats mod 16×16 logical pixel-art upgrade icon.
Preserve square yellow/gold cheese background, dark edge silhouette and golden
corner rivets. Replace only the central emblem with [subject]. No words, margins,
smooth shading or modern UI. Enlarge pixel grid with nearest-neighbor.”

Generated images often approximate borders: do not ship those approximations.
Normalize with Java 17:

```
java scripts/NormalizeUpgradeArt.java generated.png \
  src/main/resources/assets/rats/textures/item/rat_upgrade_platter.png output.png
```

The normalizer uses nearest-neighbor scaling and copies the exact original
outer two pixels, 4×4 corner fittings and transparent silhouette. Inspect the
final 16px PNG and the inventory in game; an enlarged AI preview alone is not
evidence of correct in-game readability. Both new textures were inspected as
16px assets; native-client inventory verification is still pending.
