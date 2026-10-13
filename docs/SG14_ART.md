# sg.14 — arte e apresentação

O jack-o-ratern usa o bloco de queijo azul como referência de paleta e uma face
de rato esculpida, com olhos e dentes luminosos. A textura frontal é opaca,
16×16, nearest-neighbor, sem borda, perspectiva, texto ou gradientes.

Prompt utilizado na geração:

> Minecraft Rats mod 16x16 pixel-art BLOCK FRONT texture. ONE seamless
> full-square opaque texture tile for a jack-o-ratern made of blue cheese:
> pale cyan-blue cheese surface, dark blue mold pores, carved rat-shaped face,
> two small amber luminous eyes, amber mouth and two tiny front rat teeth.
> Authentic vanilla pixel grid, limited palette, edge-to-edge tile, no margins,
> perspective, text or transparency.

Normalização pelo script existente `scripts/NormalizeBlockArt.java`; arquivo:
`assets/rats/textures/block/rat_farm_light_front.png`.
O reservatório tem laterais de queijo azul e superfície de água vanilla;
é infraestrutura interna sem item nem drops.

Tocha da quarry: manter a transparência do PNG e renderização cutout, sem
quadrados opacos. Pisos da cage: UV/modelos estendidos até os limites dos
blocos, sem frestas entre módulos. Telas: painéis claros e slots de 18 pixels,
inventário do jogador abaixo e controles fora das áreas dos slots.

Os assets foram inspecionados; a apresentação dentro do cliente Minecraft
continua a exigir validação pela usuária. Nenhum cliente foi iniciado.
