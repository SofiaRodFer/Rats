# Quarry — funcionamento combinado

| | Básica | Avançada |
|---|---|---|
| Seção | 5×5 | 7×7 |
| Ratos simultâneos | Até 2 | Até 4 |
| Comprimento horizontal máximo | 256 blocos | 512 blocos |
| Profundidade vertical | Até o limite inferior do mundo | Até 512 blocos, limitada pelo mundo |

## Configuração e equipe

Direções: Down, North, South, West ou East; nunca para cima. Comprimento
horizontal ajustável em passos de 16, mínimo de 16. O progresso é separado por
direção: terminar uma direção não exige trocar o bloco para começar outra.
A UI oferece pausa/retomada, progresso, inventário contínuo de 64 slots, aba de
ratos atribuídos e botão de retorno/depósito para os ratos do jogador com carga.

O rato precisa de upgrade Quarry, comando Harvest e posição de depósito no
bloco da quarry. Os ratos trabalham simultaneamente, com faixas preferenciais
de colunas e reservas exclusivas por bloco. Não existe turno global de um rato
por vez. Pode haver ajuda fora da faixa, mas nunca disputa de uma reserva válida.
Um rato retornando ou reparando não deve obrigar os demais a parar.

## Carga e retorno

Sem upgrade de carga: um item. Platter: uma stack. Silver Tray: dez slots de
stacks, incluindo tipos diferentes; o limite de stack de cada item é respeitado.
A bandeja não substitui o upgrade Quarry.

A mineração continua entre blocos. O rato retorna quando a carga enche, quando
os próximos drops não cabem, quando termina a escavação, quando a quarry é
pausada ou por ordem do jogador. Reserva de outro rato, reparo bloqueado ou
chunk descarregado não é ordem para depositar uma carga parcial.
Os itens não são apagados para destravar. Sobras no chão podem ser recolhidas
ao passar, respeitando filtros e capacidade. Uma quarry cheia deve ser esvaziada.

## Segurança, luzes e reparos

Túneis horizontais recebem plataformas nos buracos do piso, na altura correta
para continuar andando e retornar. Pisos naturais são preservados. Na descida,
há um caminho de plataformas em espiral para permitir o retorno.

Água/lava é isolada com revestimento de queijo: não deve haver revestimento
indiscriminado em túneis secos. Plataformas, revestimentos e luzes construídos
pelo rato não fornecem drops no survival.

Trechos fechados recebem luzes de queijo a cada cinco blocos nas paredes
laterais; na descida, nas quatro faces quando houver suporte. Cavernas abertas
não ganham automaticamente paredes só para receber luzes.

A quarry audita trechos já percorridos, inclusive direções antigas, e pode
mandar um rato reparar piso, retorno, contenção de líquidos e iluminação.
Reparos têm reserva exclusiva e tentativas limitadas. Falhar não deve bloquear
todos os trabalhadores indefinidamente. Máquinas, áreas protegidas, limites do
mundo e chunks descarregados são respeitados. Blocos acima da força configurada
do rato não são quebrados; obstáculos permanentes precisam de intervenção.

## Ore Hunter

Quarry + Ore Hunter procura minério conectado ao que foi exposto, inclusive
fora da seção normal, e depois retoma a quarry. As reservas de minério são
compartilhadas com as de mineração normal para evitar disputa de veias que
atravessam faixas. Trabalho pendente e ponto de retorno são salvos; consumir o
ponto de retorno não pode prender o rato em um vaivém.

A implementação tem limites: até 512 alvos por busca e até 16 blocos em cada
eixo desde o ponto exposto, sem gerar/carregar chunks novos. Portanto, veia
inteira não é uma promessa para depósitos arbitrariamente grandes, inacessíveis
ou protegidos. Alvos sem rota segura são adiados ou retirados da tarefa, sem
apagar a carga já coletada.

## Compatibilidade

Atualizações preservam IDs, carga, upgrades, configuração e progresso. As
correções devem funcionar em quarries novas e antigas sem quebrar/recolocar o
bloco. Isso não garante downgrade sem perdas para o Rats original, que não
conhece os novos itens deste fork. Os três jogadores e servidor devem usar a
mesma versão. GameTests não substituem a confirmação no mundo do jogador.
