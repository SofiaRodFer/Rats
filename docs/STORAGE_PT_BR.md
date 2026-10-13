# Rat Storage System — guia da sg.13

## Montagem

1. Posicione o **Rat Storage Controller** sobre chão seguro. A face com a toca é
   a entrada/saída preferida dos ratos: deixe uma face horizontal livre com
   chão abaixo. Quando a frente está ocupada, o rato tenta outra face livre.
2. Encoste **Input** e **Output** em outras faces do controller. Cada um possui
   54 slots reais. É possível conectar mais de uma entrada/saída.
3. Construa uma rede contínua de **Rat Tubes** entre as outras portas do
   controller e cada baú. Os tubos aceitam conexões horizontais e verticais;
   o acesso ao inventário acontece pela face realmente ligada ao tubo.
4. Use a **Cheese Storage Staff** no controller para selecioná-lo, depois nos
   inputs, outputs e baús para vinculá-los. Inputs/outputs usam conexão interna
   pela face compartilhada; os baús remotos exigem tubos.
5. Coloque **Storage Keeper** em um rato adulto seu e use a staff nele.
   Leve-o até a toca. O rato entra fisicamente e passa a receber tarefas sozinho;
   não é necessário configurar pickup, deposit ou radius individualmente.

Alcance: **20 blocos em linha reta**, incluindo altura, na mesma dimensão.
Tanto endpoints quanto trajetos de tubos ficam dentro dessa esfera. O sistema
não carrega chunks automaticamente. Cada rato/baú pertence a um só sistema.
Máximo de **10 ratos**, **128 endpoints** e **64 pedidos pendentes** por controller.
Um pedido aceita de 1 a 100.000 itens, limitado ao estoque disponível.

O controller é um catálogo, **não armazenamento infinito**. Os itens reais ficam
nos baús, inputs, outputs e inventários dos ratos. Quantidades do catálogo usam
64 bits e não ficam limitadas a 64. Itens com NBT, durabilidade, encantamentos ou
nomes diferentes são contabilizados separadamente.

## Tela principal: armazenamento e crafting juntos

- À esquerda: 60 slots de catálogo (45 no layout compacto), em ordem alfabética
  pelo nome localizado. Roda do mouse
  percorre a lista; a pesquisa aceita nome ou `@mod`.
- Clique num item, informe a quantidade e escolha **Solicitar**.
- **Trocar saída** percorre os outputs vinculados; o pedido guarda a saída
  escolhida no momento em que foi feito.
- Disponível exclui reservas e slots que não permitem extração. O tooltip mostra
  disponível, reservado, em transporte e aguardando distribuição no input.
- Itens no input ainda não estão disponíveis para retirada; precisam chegar aos
  baús. Itens entregues no output não voltam automaticamente ao armazenamento.
  O jogador pode retirar itens do output, mas não colocar itens nele.

À direita está a **bancada real 3×3**, com resultado e consumo de ingredientes,
incluindo retornos de receitas, como baldes. Cada jogador possui sua própria
grade. Ao fechar a tela, os ingredientes permanecem salvos.

Não existe mais o botão de buscar ingredientes para receitas. Use itens reais
do seu inventário, como numa crafting table. Itens/pedidos das antigas sessões
são preservados; quando houver sobras, a área de itens preservados fica visível
para retirá-las. Não há autocrafting.

Em escalas maiores da GUI, a tela usa layout compacto: catálogo e bancada
continuam lado a lado e o recebimento vira uma grade 3×3.

## Organização e transporte

Categorias: construção, minérios/metais/gemas, madeira, agricultura/alimentos,
drops de criaturas, equipamentos e outros. A prioridade é completar uma pilha
do mesmo item, usar um baú da categoria, classificar um baú vazio e, por último,
usar um baú marcado como misto/overflow.

Baús que já contêm itens entram como **Mistos**. **Organizar itens existentes**,
na aba Sistema, autoriza a redistribuição de seu conteúdo. A categoria inicial
é a mais abundante e permanece estável para evitar transferências em círculo.
Reorganização tem prioridade inferior a retiradas e distribuição do input. Se
faltar espaço, os itens ficam onde estão até surgir um destino possível.

Na lista de baús da aba Sistema, **Shift + clique** marca o baú como misto/overflow
(somente o dono do controller). Clique em **Baús / Pedidos** para alternar as
listas. Placas de queijo azul mostram a categoria na frente dos baús; são um
visual acoplado, não substituem blocos/placas do jogador e não geram drops.

Storage Keeper carrega uma pilha respeitando o limite natural do item. Com
Silver Tray, até dez pilhas, inclusive itens diferentes da mesma categoria numa
viagem de distribuição. Baús que comportam pilhas maiores não aumentam a carga
do rato: ele faz várias viagens. Filtros e capacidades reais são respeitados.

Ratos trabalham simultaneamente com reservas por tarefa; não existe uma barreira
aguardando todos terminarem. Pedidos alternam entre si para que uma retirada
grande não monopolize todos os trabalhadores. Uma entrega não muda de objetivo
no meio da viagem. Comida transportada não é consumida pelo rato.

## Administração e recuperação

A aba **Ratos** mostra aparência, nome e estado. **Retirar rato** pode ser
usado pelo dono do rato ou do controller. Cargas pendentes voltam ao inventário
de recuperação do controller antes da saída física. Um pedido não é considerado
entregue quando seus itens vão para recuperação.

- Destino cheio: a carga continua com o rato; ele tenta novamente.
- Tubo quebrado: interrompe o trajeto. Se o tubo sob o rato for removido, ele
  fica suspenso em segurança até o reparo — isso é intencional, não uma cage.
- Chunk descarregado: estoque indisponível, sem busca/carregamento forçado.
- Rato morto: sua carga física segue os drops normais e sua tarefa é liberada;
  o sistema não cria itens para repor perdas.
- Controller destruído: seus buffers e ingredientes são dropados; ratos
  carregados são desvinculados, mantendo suas cargas físicas.
- Inventário de recuperação cheio ou saída obstruída: a remoção espera que
  você libere espaço/percurso, sem apagar ou teletransportar o rato.

Você pode resgatar manualmente um rato com a interação de ombro existente e
levá-lo de volta à toca. A staff com Shift pede remoção, não coloca na cabeça.
Enquanto registrado, a IA de storage toma prioridade sobre tarefas antigas.
Ao sair normalmente, o comando anterior é restaurado.

Com a storage staff na mão (principal ou secundária), clique no controller
para selecionar o sistema: contorno verde no controller, azul nos componentes,
amarelo nos ratos, linhas indicando os tubos e uma esfera de raio 20. Os
contornos desaparecem ao trocar de item. O clique normal da staff num baú
conecta-o sem abrir seu inventário; Shift + clique desconecta quando não há
tarefa em andamento nele.

## Compatibilidade e mundos

Integração por `ForgeCapabilities.ITEM_HANDLER`, respeitando a face, simulação,
extração e inserção reais. Testes usam as versões instaladas de Iron Chests
14.4.4, Sophisticated Storage 1.5.0.2137 e Core 1.5.4.2362, inclusive upgrade de
pilhas e baús duplos. Upgrades do baú que transformam ou eliminam itens mantêm
seus efeitos próprios; não equivalem a um inventário comum de conservação.

Dados novos são opcionais nos ratos antigos; registros existentes permanecem.
Use a mesma **sg.13** nos três clientes e no servidor. O protocolo recusa versões
diferentes. Mundos antigos podem avançar para esta versão, mas voltar ao Rats
original depois de usar blocos/upgrades novos exige restaurar o backup anterior.

Os testes automatizados não substituem sua validação dentro do jogo de
aparência, interação, escalas da GUI e trajetos no seu mundo.
