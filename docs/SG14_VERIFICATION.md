# sg.14 — verificação e limites

## Resultado automatizado

Suíte independente final: **131 testes obrigatórios aprovados**, zero falhas.
`build/sg14-independent-third.log:2242`: All 131 required tests passed.
`build/sg14-independent-third.log:2265`: BUILD SUCCESSFUL in 1m18s.

Servidor de desenvolvimento, mundos novos por rodada, Java 17. Nenhum cliente
Minecraft iniciado. Nenhum mundo ou jar do launcher alterado.

Compatibilidade exercitada com os jars instalados reais, copiados somente para
staging de desenvolvimento: Iron Chests 14.4.4, Sophisticated Storage 1.5.0.2137,
Sophisticated Core 1.5.4.2362, Farmer's Delight 1.3.4, Alex's Mobs 1.22.9 e
Citadel 2.6.3. O mod não incorpora esses jars. Avisos de advancements opcionais
do Sophisticated Backpacks ausente não foram contados como aprovação de testes.

## Cobertura

- Tubos: colisão desativada dentro/restaurada fora, seis faces, vertical,
  preferência por tubos, trechos quebrados, saída/reentrada, obstáculos/fluidos,
  raio, cache negativo inclusive AABB, carga com NBT e reload durante transporte.
- Storage: entrada física via staff/controller, ligações, entrega/retirada,
  dez trabalhadores sem barreira, capacidade/filtros, output read-only,
  recall, crafting, reservas, baús reais e conexão movida durante entrega,
  incluindo tarefa e rato recarregados de NBT com face antiga.
- Fazenda: duas camadas limpas com máquinas/baús preservados, nivelamento,
  água/luz na mesma célula, fonte contida em borda aberta, hidratação real
  dentro/fora do raio vanilla, cultura convertida em ponto de água sem perda
  de colheita, vegetação de duas alturas, reparos, bone meal consumido,
  dois trabalhadores, modelos 1/2/4 com sementes distintas e NBT, ghost cursor,
  cliques alternativos, distância e ator estrangeiro.
- Corvos: predicate real do AIAvoidPumpkins de Alex's Mobs reconhece o
  jack-o-ratern, com pedra como controle negativo.
- Cage: pisos/capacidade/escadas, proprietário filtrado no servidor,
  mudança de dono invalida seleção/edição, inventário do jogador visível,
  upgrades/carga, Keeper mantém capacidade ao trocar upgrade não relacionado.
- Quarry: quatro Ore Hunters, áreas/reservas exclusivas, mineração/depósito,
  retorno, alvo inacessível adiado sem perda permanente, infraestrutura,
  limites até fundo do mundo e carga/tray. Ovos antigos e novo ovo aleatório
  mantêm proprietário e variantes válidas.

## Revisões e problemas reparados

Dois subagentes revisaram transporte/storage e fazenda/cage/quarry após a
implementação. Reparos: busca repetida em rota impossível e colisão física,
goal de habitat interrompendo Harvest em tubos, faces sided incorretas,
outlines incluindo ar, conexão de baú movida com tarefa antiga, água marcada
pronta antes da luz, reparo de água bloqueado pelo próprio jack, flores/grama
tratadas como culturas, cultura bloqueando novo ponto de água, gate de
capacidade ignorando Keeper, controles sobre slots e viewport após resize.

Um terceiro subagente criou testes independentes e executou três rodadas.
Encontrou falha real de quick-move sem validar o viewer; o guard foi aplicado
aos menus da fazenda, cage e storage. Um setup de reload foi corrigido para
não confundir promoção legítima de cargo slot9 para mainhand com perda de NBT.
O teste sg.13 de tubo quebrado exigia imobilidade: foi atualizado para o novo
contrato, mantendo sobrevivência, conservação exata de64 itens, zero drops
soltos, colisão fora do tubo, checagem obrigatória após80ticks e entrega final.
Nenhuma falha de perda/duplicação foi ocultada removendo essas invariantes.

## Retrocompatibilidade e validação pendente

IDs antigos e NBT continuam legíveis. Planos antigos revalidam infraestrutura;
ovos antigos de cor específica continuam utilizáveis, embora a aba criativa
ofereça apenas o novo ovo aleatório. Isso é compatibilidade ao atualizar,
não garantia de downgrade: uma versão antiga não conhece blocos novos.

Os testes não provam ausência de todo bug. Apresentação das telas/texturas,
animação de escadas, desempenho em mundos grandes e jogabilidade no mundo da
usuária ainda precisam de validação no cliente. Uma rota destruída sem saída
fisicamente segura pode aguardar reparo; ratos não atravessam obstáculos
externos nem carregam chunks à força.

Perfil `tres amigos teste` permanece com44 jars e sg.13, SHA256
`c1a9512486cfc55ac1506288173624009c555ad0e7638915f5cff35214d38b08`.
A sg.14 não foi instalada.

## Artefato

`build/libs/Rats-1.20.1-8.1.3-sg.14.jar`

SHA256: `cfbf759318d8f53e3f8e2055336542c991cea35b90a8bce7d52c51e94e517119`.

Data generation concluída separadamente antes do build; build/reobfuscation
final em `build/sg14-release-final.log`: BUILD SUCCESSFUL in7s.
Jar inspecionado: versão1.20.1-8.1.3-sg.14, recursos novos presentes,
PT-BR idêntico à fonte, tocha cutout, sem classes de teste, fixture, caches de
datagen ou jars de compatibilidade incorporados.
