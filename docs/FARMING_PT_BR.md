# Bloco de Fazenda dos Ratos — sg.11

## Uso

1. Faça o bloco com a receita `queijo/feno/queijo`, `feno/composteira/feno`,
   `queijo/feno/queijo`. São blocos de queijo comum, não queijo azul.
2. Coloque-o sobre o chão, escolha largura/comprimento de 1 a 16 e direção na
   interface. O terreno-alvo fica na altura do chão imediatamente abaixo do
   bloco, em frente dele. A primeira linha da grade é a mais próxima do bloco.
3. Coloque terra e sementes reais no armazém (54 slots). Na aba Armazém, pegue
   uma semente com o cursor, mude para Plantação e clique nas células desejadas.
   O cursor não perde sementes: são apenas marcações, não estoque. Botão direito
   apaga a marca. Água (azul) e luz (amarelo) são reservadas automaticamente.
4. Equipe até dois ratos com Farmer/Agricultor (o planter existente), coloque-os
   em Harvest/Colher e use o cheese staff para selecionar este bloco como destino
   de depósito. Atribuir um terceiro é recusado. A área de trabalho passa a ser
   o retângulo do bloco, sem precisar mudar manualmente o radius staff.

## Nivelamento e trabalho

Os ratos removem terreno natural acima do plano e levam seus drops ao armazém.
Buracos são preenchidos com terra real, retirada do estoque/carga, não terra
infinita. Água e luzes são construídas automaticamente; luzes geradas não dão
drops. Não há custo de baldes/tochas para a infraestrutura. Um bloco de terra
também pode ser necessário como suporte abaixo de uma fonte sobre um vazio.

Máquinas, containers, áreas protegidas e materiais que não são terreno não são
demolidos. Telhados/obstruções identificáveis interrompem a limpeza da coluna.
Pedra/terra colocada por jogadores é indistinguível de terreno natural: não
inclua construções desses materiais no retângulo escolhido. Chunks não são
carregados/gerados à força. Obstáculos permanentes precisam de intervenção.

Os dois trabalhadores recebem metades do retângulo e reservas exclusivas de
tarefas. Com apenas um ativo, ele pode trabalhar toda a área. O rato busca
suprimentos fisicamente no bloco, ara, planta sementes reais e colhe cultivos
maduros. Com Replanter, também recompõe solo desarado, água e luzes perdidas.
Replantio segue as sementes marcadas e depende de estoque real.

A carga respeita a capacidade: um item sem melhoria, uma stack com Platter,
dez stacks com Silver Tray. Colheitas se acumulam; o rato retorna quando precisa
liberar espaço ou não tem mais trabalho útil. Uma vez decidido o depósito,
plantio/coleta ficam suspensos até os itens realmente serem depositados. Estoque
cheio ou filtros que recusam itens não apagam a carga. Overflow fica no chão
com tempo de vida estendido e pode ser recolhido dentro da área.

A irrigação usa o mínimo de fontes para a cobertura de quatro blocos em cada
eixo: em 16×16, quatro fontes. As luzes no chão são distribuídas por cobertura
para fornecer luz >=9 às células de cultivo, sem ocupar todas as linhas. O
algoritmo de luz é guloso, não uma promessa de ótimo matemático global. Confira
a orientação antes de iniciar: girar/redimensionar não apaga o terreno antigo.

## Compatibilidade e limites

O plano aceita cultivos de farmland, incluindo trigo, cenoura, batata, beterraba
e os cultivos de Farmer's Delight verificados (tomate, repolho, cebola). Tomate
em brotação não é tratado como colheita madura. Cactos, cana, nether wart e
arroz precisam de outros substratos; abóbora/melancia exigem espaço para frutos
e não fazem parte deste plano compacto. O planter comum fora do bloco mantém
sementes de stems e bonemeal e ganha o ciclo de acumulação/depósito.

Quarries básica e avançada agora descem até o fundo do mundo. Os limites
horizontais continuam 256/512 e as seções 5×5/7×7. A tocha mantém seu ID, mas
ganhou sprite próprio, cabo de queijo azul e aparência estreita de tocha.

NBT antigo continua legível; o retorno do farmer é um campo opcional, falso
quando ausente. Novos blocos não são conhecidos por versões antigas: não se
promete downgrade sem perdas. Os três clientes e servidor devem usar sg.11.
Testes de servidor não substituem a verificação visual/funcional no seu mundo.
