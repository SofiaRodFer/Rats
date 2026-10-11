# Quarry e confiabilidade dos ratos — Minecraft 1.20.1

Implementação no fork SofiaRodFer/Rats, branch `1.20`, em commits pequenos.

## Regras de comportamento

- `rat_quarry` mantém seu identificador para preservar mundos e receitas existentes; passa a ser Basic Quarry.
- Basic: seção exata 4×4; para baixo até o limite inferior do mundo; horizontal até 256 blocos.
- Advanced: seção 6×6; até 512 blocos em cada direção, limitada pela altura do mundo.
- Direções: baixo, norte, sul, leste e oeste. O avanço de cada direção fica salvo separadamente, permitindo trocar de direção no mesmo bloco.
- Escavação por camadas, somente em chunks carregados; proteção de terrenos, blocos inquebráveis e inventários respeitados. Vários ratos reservam alvos distintos.
- Silver Tray: dez slots de carga no total, incluindo a mão, com limites reais de stack e comparação de NBT. Não mistura itens com dados diferentes.
- Mineração continua enquanto houver espaço útil; depósito ocorre quando não cabe o próximo drop, ao completar a obra ou pausar a quarry. Itens excedentes ficam no mundo e são recolhidos no trajeto.
- Água/lava são vedadas antes de abrir a próxima frente; pisos e paredes de contenção usam queijo de construção sem drops. Bloqueios de proteção interrompem a frente em vez de arriscar o rato.
- Recuperação de tarefas descarta temporariamente alvos sem progresso e tenta novamente; não teleporta ratos nem destrói blocos protegidos.

## Sequência de commits

1. **Plano** — documentar escopo, decisões e critérios de aceitação.
2. **Confiabilidade** — recuperação por falta de progresso, reset de estados e cooldowns, exclusão temporária de alvos inacessíveis.
3. **Geometria e persistência** — tiers, direções, limites, progresso por direção e reservas transitórias de alvos.
4. **Configuração** — menu sincronizado no servidor: direção, distância, pausa e estado; acesso aos 64 slots existentes.
5. **Silver Tray** — registro, receita exata, carga persistida, coleta e depósito de múltiplos stacks; devolução da carga ao retirar upgrade ou morrer.
6. **Mineração** — frente incremental, transporte por capacidade, coleta no trajeto e limpeza de animações/reservas.
7. **Líquidos** — bloco de contenção sem drops, vedação preventiva e acesso seguro à frente.
8. **Recursos e artes** — texturas pixel-art derivadas da paleta/geometria original, modelos, receitas, loot, tags e traduções; datagen reproduzível.
9. **Verificação** — compilação/build, checagens de geometria, persistência e transporte; registrar separadamente cenários verificados e pendentes no jogo.

## Receitas

Advanced Quarry: `BNB / BQB / BBB`, B = bloco de queijo azul, N = lingote de netherita, Q = quarry básica.

Silver Tray: ` D / APA / CAC`, D = diamante, A = fragmento de ametista, P = upgrade Platter, C = queijo azul.

## Aceitação no jogo

- Medir 4×4 e 6×6 nas cinco direções; conferir fim em 256/512 e limite inferior.
- Salvar/reabrir, alternar direção, pausar/retomar e operar com dois ratos.
- Transportar dez stacks distintos, incluindo itens não empilháveis/NBT; retirar upgrade, morrer e salvar com carga.
- Encher a quarry, liberar espaço e confirmar retomada sem perda/duplicação.
- Abrir frente contra água, lava, blocos waterlogged, cascalho e cavidades; conferir piso, paredes e retorno.
- Deixar alvos inacessíveis em plantação, madeira e coleta; liberar caminho e confirmar recuperação.
- Conferir singleplayer e servidor, receitas e artes no inventário/mundo.

Não declarar validação dentro do jogo sem observar estes cenários. Não substituir o jar do modpack durante o desenvolvimento.
