# sg.16 — teste independente

Data: 2026-10-13. Resultado: **182/182 testes obrigatórios passaram em
cada uma das duas configurações**; após a correção final do clique de vínculo,
a rodada de compatibilidade repetida também passou **182/182**, em servidor
GameTest sem janela. Os logs
confirmam a conclusão dos GameTests; o sucesso do Gradle isoladamente não foi
usado como prova de execução.

| Configuração | Evidência no log | Resultado |
| --- | --- | --- |
| Base, sem as integrações opcionais | `build/sg16-tests-base4.log`, linha 1040, às 14:42:47: `All 182 required tests passed :)` | 182/182; Gradle também concluiu |
| Compatibilidade com integrações | `build/sg16-tests6.log`, linha 2666, às 14:44:59: `All 182 required tests passed :)` | 182/182; Gradle também concluiu |
| Compatibilidade após correção do clique de vínculo | `build/sg16-tests7.log`, linha 2672, às 14:48:42: `All 182 required tests passed :)` | 182/182; Gradle também concluiu |

A rodada de compatibilidade carregou Sophisticated Core 1.5.4.2362,
Sophisticated Backpacks 3.26.6.2176, Sophisticated Storage 1.5.0.2137,
Iron Chests 14.4.4, Farmer's Delight 1.3.4 e Alex's Mobs 1.22.9 para Minecraft
1.20.1. Os arquivos e versões aparecem na descoberta de mods do segundo log.

## Regressões acrescentadas pelo testador independente

Os cinco testes novos estão em `Sg16IndependentGameTests.java`; o código que
depende da API opcional fica em `Sg16IndependentBackpackChecks.java`, uma classe
sem anotação de GameTest, chamada somente quando Backpacks está carregado.

| Teste | Comportamento verificado |
| --- | --- |
| `genericTubeRouteExceedsSixtyFourWithoutChangingStorageRadius` | Rota vertical carregada de 80 blocos funciona com o orçamento genérico sem raio geométrico; a mesma rota é rejeitada quando o chamador impõe raio 20. |
| `longGenericDepositWaitsForBrokenTubeThenResumesExactTaggedCargo` | O `RatDepositGoal` real avança pelo tubo, pausa após quebra de dois segmentos sem suporte, conserva 17 diamantes com NBT, retoma após reparo e entrega os 17 itens além de 64 blocos. |
| `forgeTicketsActuallySlideAndReleasePerWorker` | Consulta os tickets efetivos persistidos por Forge, com proprietário UUID e categoria `TickingEntities`; confirma nove chunks, deslocamento de 48 blocos sem acumular a janela antiga, remoção ao sentar e liberação explícita idempotente. |
| `pendingReservationsConsolidateButKeepDifferentNbtSeparate` | Reservas simultâneas de 64 + 36 itens escolhem o mesmo baú antes de qualquer extração; um terceiro trabalhador não duplica a fonte; outro NBT recebe destino separado enquanto as reservas anteriores existem. |
| `nativeBackpackRejectsReplacedNetworkAndMissingController` | Com a API nativa, rede diferente na mesma posição, dimensão inválida e controller destruído recusam transferência; 11 itens com NBT permanecem no upgrade após recriação do wrapper. Sem o mod, verifica o item alternativo seguro e não vestível. |

Essas regressões passaram nas duas rodadas. A confirmação da API nativa vale
para a rodada de compatibilidade; na rodada base, esse teste executa apenas o
ramo de ausência do mod. A suíte preexistente cobre os demais critérios do
plano, incluindo fazenda, quarry, pesca, armazenamento e mochila com entrada
parcialmente cheia.

## Achados e limites

As regressões independentes não encontraram uma falha comportamental adicional
nas fontes finais. Durante a validação da ausência dos mods, o coordenador
identificou e corrigiu dependências de classloading na inicialização e na
descoberta de testes; a tabela mantém a evidência das rodadas base4, compat6
e da confirmação final compat7.

Na revisão final, o coordenador encontrou outra falha concreta: ao clicar no
controller com o upgrade nativo de mochila, o bloco abria sua GUI e consumia a
interação antes de `Item.useOn` registrar o vínculo. A correção faz o bloco
retornar `PASS` nessa interação. Conferi tanto o guard em `RatStorageBlock.use`
quanto o teste atualizado em `OptionalBackpackChecks`: ele exige `PASS` no
handler do bloco, chama o `useOn` real do upgrade e confirma a rede e o dono
gravados a partir do controller. A rodada compat7 confirmou todos os 182
testes após essa alteração. A rodada base4 antecede essa última correção do
clique; não é apresentada como nova execução das mesmas fontes finais.

O teste de 80 blocos é vertical e mantém a coluna carregada. Não comprova
travessia horizontal longa entre chunks descarregados, bifurcações extensas,
atingimento do orçamento de busca ou escolha de fronteira em rede parcialmente
descarregada. O movimento da goal é exercitado diretamente em passos; isso não
substitui uma viagem inteira sob escalonamento normal da IA.

Os tickets são tickets Forge efetivos, não apenas o cálculo de chunks
desejados. A verificação não reinicia o servidor, não mede recuperação de
tickets após reinício, nem demonstra progressão de trabalho com todos os
jogadores distantes. A consulta usa o formato de persistência da versão Forge
instalada e isola o UUID do trabalhador para não confundir tickets de outros
testes.

Os casos de reserva usam o scheduler real e inventários físicos, mas não
acompanham uma entrega concorrente completa com três trabalhadores. O teste
nativo de mochila não abre a interface gráfica nem verifica o estado offline
por descarregamento real de chunks. Entrada cheia/parcial e conservação de
resto são cobertas por outro teste da suíte.

Nenhum cliente Minecraft foi aberto; aparência, animação, posicionamento de
abas, interação visual, primeiro snapshot no cliente e jogabilidade no perfil
instalado continuam para validação da usuária. O testador independente não
executou Gradle em paralelo, não alterou produção, não instalou jars, não
acessou mundos do launcher e não fez commits.
