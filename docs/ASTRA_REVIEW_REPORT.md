# Revisão Astra — Rats sg.14

Revisor: subagente GPT-6 Astra, esforço médio, solicitado pela usuária.
Base inspecionada: branch `1.20`, HEAD `6e1b787`; revisão estática em 13/10/2026.
A auditoria inicial abaixo é anterior aos reparos; o adendo final registra as
correções sg.15 e a reavaliação. O subagente não executou Gradle, cliente,
instalação ou push. As referências dos achados iniciais são relativas a
`src/main/java/com/github/alexthe666/rats/`, salvo indicação contrária.

## Resultado e alcance

Os sistemas novos têm boas salvaguardas de conservação de carga, reservas e
compatibilidade. A maior dívida concreta está nos caminhos legados de rede,
inventário e ciclo de vida, que podem contornar as salvaguardas novas. Há também
falhas menores de jogabilidade e manutenção. Os 131 GameTests anteriores
documentados não cobrem os cenários abaixo; não foram reexecutados nesta revisão.

Inventário amplo: 705 arquivos Java. Foram rastreados os registros/callers de
todos os pacotes, os fluxos principais de storage/tubos/farm/cage/quarry/ovos,
carga, NBT, upgrades, menus e utilitários da IA original. Leitura aprofundada dos
arquivos responsáveis pelos achados e dos módulos novos citados na cobertura ao
fim. Isso não equivale a revisar linha a linha os 705 arquivos nem a provar
ausência de bugs. Modelos, shaders, animações, bosses, worldgen e todos os
upgrades individuais tiveram cobertura parcial por inventário/busca, não auditoria
exaustiva. Nenhuma verificação visual foi feita, respeitando AGENTS.md.

Prioridades: P1 = segurança, crash ou paralisação importante; P2 = comportamento
incorreto reproduzível; P3 = defeito localizado de menor impacto ou latente.

## Defeitos concretos a corrigir

### R1 — P1 — Pacotes legados aceitam a direção errada

`server/message/RatsNetworkHandler.java:28` registra os pacotes antigos sem
`NetworkDirection`, ao contrário dos sete novos registros. Handlers como
`ManageRatStaffPacket:44`, `OpenRatScreenPacket:33`, `UpdateCurdlerFluidPacket:31`
acessam `Minecraft`; um cliente pode enviar seus discriminadores ao servidor.
Além de possível erro por classes de cliente em servidor dedicado,
`ChangeRatlantisStatusPacket:21` permite alterar a flag global de datapack pelo
payload de cliente. A direção deve ser explícita para todos os registros.

Mapa rastreado pelos callers atuais:

| Servidor → cliente | Cliente → servidor |
|---|---|
| ChangeRatlantisStatus, ManageRatStaff, OpenRatScreen, SyncPlague, SyncThrownBlock, UpdateCurdlerFluid, UpdateRatFluid, UpdateRatMusic, UpdateSelectedRat | ClearRatRecipe, CycleRatRecipe, DismountRat, RatCommand, RatUpgradeVisibility, SetDancingRat, SyncArmSwing, SyncRatStaff, SyncRatTag, UpdateMobFilter |

Preservar IDs/discriminadores existentes. Testar registro/direção de todos os
pacotes e rejeição do sentido oposto antes do handler; carga de servidor dedicado
não deve inicializar classes cliente. `SetDancingRat` merece a validação especial
descrita em R3, pois seu caller é o evento de jukebox, não um comando do dono.

### R2 — P2 — Sincronização de fluido/bloco arremessado não funciona no cliente

`UpdateRatFluidPacket.java:33` e `SyncThrownBlockPacket.java:27` usam
`context.getSender()`, que é nulo no recebimento cliente. Seus callers, porém,
são exclusivamente server-side: RatPickupGoal:223, RatDepositGoal:244,
RatMilkCowGoal:64; PsychicRatUpgradeItem:93 e NeoRatlantean:224. Portanto os
updates legítimos são ignorados. No sentido inverso atualmente aceito, fluido
arbitrário pode ser injetado em qualquer rato pelo ID.

Usar handler cliente isolado e entidade do mundo cliente; tornar ambos S2C.
Trocar broadcasts `ALL` por tracking da entidade/chunk correspondente: IDs de
entidades e posições de blocos não são identificadores entre dimensões.
`UpdateCurdlerFluid` também usa `ALL` e só contém posição, podendo atualizar um
curdler da outra dimensão nas mesmas coordenadas. Teste que aplicar um update
legítimo altera apenas o espelho cliente correto; verificação estrutural de
distribuição tracking sem abrir cliente, registrando o limite da prova visual.

### R3 — P1 — Mutações de rato e receita sem autorização do servidor

`RatCommandPacket.java:28`, `RatUpgradeVisibilityPacket.java:31` e
`SyncRatTagPacket.java:33` aceitam qualquer TamedRat pelo ID. Um jogador pode
parar o rato alheio, mudar sua aparência ou reescrever sua patrulha.
`ClearRatRecipePacket.java:28` consulta um BlockPos arbitrário e limpa uma grade
sem exigir o menu correspondente, distância ou chunk carregado; o efeito é
sabotagem de configuração (a grade é ghost, não afirmar perda de itens reais).
`CycleRatRecipePacket:26` não revalida menu ainda válido/posição do payload.

Revalidar dono, vida, mundo e contexto correto da operação no servidor.
Comandos/visibilidade devem referir o rato do menu aberto válido. Patrulha e
staff devem conservar o uso remoto legítimo: rato selecionado do dono e staff
correta/contexto da interação; não impor raio curto rato↔jogador indiscriminado.
`SyncRatStaffPacket:44` já verifica dono mas não contexto nem limites de dados.
Separar a validação reutilizável do handler (DRY e testabilidade).

`SetDancingRatPacket:31` também aceita estado arbitrário de qualquer rato.
Como a dança é acionada por música no mundo e inclusive observadores podem
enviar o callback, exigir apenas ownership quebraria música legítima. Validar
jukebox real carregada/tocando/perto do rato e movimentos válidos, ou derivar
essa decisão no servidor. Testar dono, estrangeiro, menu obsoleto, jukebox
inexistente, e staff remota válida como controle positivo.

### R4 — P1 — Foice aceita ItemStack e dano escolhidos pelo cliente

`SyncArmSwingPacket.java:23` repassa `packet.stack()` diretamente para
`ForgeEvents.handleArmSwing:443`. O método só confere se esse stack é a foice
e calcula dano de seus AttributeModifiers. Um cliente pode disparar sem possuir
a foice e fornecer modificadores falsificados. O payload não deve ser autoridade
sobre equipamento ou dano; resolver a mão/equipamento real do servidor,
validar ação/cadência e ignorar stack e atributos enviados. Manter a interação
normal de ataque/uso. Testar mãos vazias + foice forjada, equipamento verdadeiro
e repetição no mesmo tick, sem gerar rajadas por pacote.

### R5 — P1 — Raio arbitrário pode provocar busca cúbica gigantesca

`InventoryRat.java:316` grava qualquer inteiro em SEARCH_RADIUS.
`SyncRatStaffPacket:61` fornece `extraData` sem clamp; o NBT SearchRadius também
passa pelo setter (`InventoryRat:123`). O slider só limita no cliente.
`RatFarmGoal.java:132` e outros goals materializam todo o cubo com `.toList()`.
Um raio 10.000 ou negativo patológico produz trabalho/memória incompatível com
o servidor. Centralizar clamp 0..RatConfig.maxRatRadius no setter e usar default
para tag antiga ausente, sem mudar raio válido. Testar extremos, NBT antigo,
configuração e pacote; não executar o cubo gigante para demonstrar a falha.

### R6 — P1 — Pickpocket falha com uma única oferta

`entity/ai/goal/harvest/RatPickpocketGoal.java:52` chama
`nextInt(villager.getOffers().size() - 1)`. Uma oferta, que passa o guard
`!isEmpty`, causa `nextInt(0)`; com várias, a última jamais é escolhida.
Usar limite exclusivo correto `size()`. Testar villager com uma oferta e,
com fonte aleatória determinística, seleção do último índice. Não depender
de sorte de uma rodada para validar distribuição.

### R7 — P2 — Menu original do rato ignora validade e limites novos de carga

`inventory/RatMenu.java:125` delega stillValid ao RatContainer, que herda
SimpleContainer sem validação de entidade/dono/distância. Abrir o menu e depois
transferir o rato via RatPapers, matar/remover a entidade ou afastar-se mantém
acesso ao inventário. Os slots também não têm guard próprio para menu obsoleto.
`RatMenu:32` é slot comum de 64 itens para a mão: pode-se inserir uma stack mesmo
sem Platter/Tray/Keeper, contornando `RatCargo.limit`. Extras verificam apenas
Tray na inserção, sem filtro. `quickMoveStack:130` não verifica índice nem ator.

Vincular RatMenu ao viewer/rato real no servidor e conferir validade nos caminhos
de mutação; tornar capacidade e filtro consistentes com cargo. Preservar
extração de cargas antigas acima do limite: nunca truncar/apagar NBT. Ao trocar
upgrade, usar política explícita de conservação (bloquear redução enquanto
carregado ou ejetar somente com conservação demonstrada), alinhada à cage.
Cliente precisa receber limites suficientes para prever a mesma ação.
Testar carga 1/stack/10 stacks, itens de stack máxima 16/1, owner transfer, rato
morto/distante, shift-click, swap, drag e recuperação de carga antiga.
`RatQuarryMenu.java:57` também omite viewer/stillValid/mayPickup no quick move;
adicionar o mesmo guard defensivo já existente em farm/cage/storage. Não afirmar
que vanilla permite cliente escolher outro Player: essa é uma inconsistência
de API e de defesa em profundidade, distinta da falha real de RatMenu.

### R8 — P2 — Vagas de trabalhadores ficam presas após remoção fora do chunk

`TamedRat.java:845` só desatribui farm/quarry se o chunk da estação está carregado.
`RatFarmingBlockEntity.java:71` e `RatQuarryBlockEntity.java:258` preservam UUID
quando `ServerLevel.getEntity` retorna null, corretamente para unload legítimo.
Se o rato morrer ou mudar de dimensão enquanto a estação está descarregada,
porém, esse UUID nunca volta e ocupa uma das 2/4 vagas indefinidamente.
Storage também só registra morte em LivingDeathEvent (`StorageEvents:11`):
discard e mudança de dimensão podem deixar worker/tarefa presos. Angel cancela
o evento de morte antes desse listener e descarta o original ao criar outra
entidade com UUID novo (ForgeEvents:155), outro caminho importante.

Registrar desvinculação persistente por estação/rede no ciclo de remoção e
consumir ao carregar; manter UNLOADED_TO_CHUNK/UNLOADED_WITH_PLAYER como unload,
nunca liberar UUID simplesmente por não estar carregado. Definir conservação
de tarefa/carga física na ressurreição/transferência; não reconstruir itens a
partir de reservas. Testar estação carregada/descarregada, morte/discard/troca
de dimensão, unload+reload comum e recuperação persistente após reinício.

### R9 — P2 — Entrada/saída do storage pode escolher água ou lava

`RatStorageGoal.java:150` (docking) só exige collision shape vazio e algum
suporte abaixo. Fluidos têm shape vazio. `exit:128` revalida apenas shape do
bloco, depois move via setPos sem testar fluido/AABB/suporte novamente. Com as
outras três faces obstruídas e água/lava na única face livre, recall leva o
rato diretamente ao fluido; se a saída muda durante a viagem, não reconsidera
uma alternativa segura. Isso diverge da segurança de TubeTravel/CageHabitat.

Centralizar validação do dock usando corpo do rato, fluido, suporte, limites e
chunk carregado; revalidar ao entrar/sair e procurar outra face se a escolhida
se tornar insegura. Respeitar passagem intencional pelo controller/tubo.
Testar frente inundada com lateral livre, todas inseguras (espera sem perda),
remoção do suporte/colocação de obstáculo após início do recall.

### R10 — P2 — Filtro de mobs aceita texto que lança exceção no servidor

`UpdateMobFilterPacket.java:16` lê lista de strings sem limite de elementos
da aplicação; `MobFilterUpgradeItem.java:60` usa `new ResourceLocation(mob)`.
Um payload com `INVALID ID` enviado enquanto segura o filtro passa o guard
e lança exceção no trabalho do servidor. Usar tryParse, aceitar somente IDs
registrados, deduplicar e limitar contagem/tamanho antes de alocar listas.
`SyncRatTagPacket:17` também lê lista irrestrita de GlobalPos; aplicar limite
de patrulha documentado no decode e validação server-side, sem truncamento
silencioso que mude uma rota válida. Testes de payload malformado/grande e de
round-trip válido devem não depender do cliente gráfico.

### R11 — P2 — Teleporte Ender falha abaixo de Y=0

`TamedRat.java:1363` procura piso com `blockpos.getY() > 0`, embora 1.20.1 e a
quarry trabalhem abaixo de zero. Em destino Y=-20 sobre piso seguro, flag1 nunca
fica true e a tentativa é revertida. Usar limite inferior real do mundo e
verificar chunk sem carregar. Testar destino negativo seguro, vazio até limite,
fluido/colisão e dimensão com minY diferente; falha deve preservar posição
original. Não alterar custos/cooldowns do upgrade.

### R12 — P3 — Seleções aleatórias excluem a última opção

`RatUtils.java:109,126` usa `nextInt(size()-1)` para comida/contaminação; há guard
size==1, logo não crash, mas último slot nunca é usado quando existem vários.
`world/MarblePileFeature.java:34,39` repete o erro para direção/eixo e exclui
uma orientação. Corrigir os limites exclusivos, testar fonte determinística no
último índice. Não mudar algoritmo/distribuição além de incluir opções omitidas.

### R13 — P3 — Patrulha se acumula quando NBT é aplicado novamente

`InventoryRat.java:125` adiciona PatrolNodesTag à lista existente sem limpá-la;
readAdditionalSaveData sobre entidade já carregada duplica pontos e não apaga
rota quando a próxima tag não contém patrulha. Limpar antes de carregar, aplicar
validação coerente com R10 e preservar IDs/dimensões válidos. Testar aplicar a
mesma tag duas vezes e depois tag sem patrulha. Verificar também limpar valores
opcionais HomePos/RadiusPos quando ausentes, pois o método mantém estado anterior.

### R14 — P3 — Navegação Pirat desativada e helper com loop latente

`PiratWanderGoal.java:38` fixa vec3d=null, então goal registrado em Pirat:55 nunca
inicia. Ratos piratas não recebem o passeio aquático esperado. O único caller
de `RatUtils.findLowestWater:162` está depois desse retorno, portanto o loop
infinito do helper é hoje LATENTE: ele calcula sempre `pos.below()` sem avançar.
Não alegar travamento atual do servidor por esse caminho. Restaurar escolha
aquática limitada/carregada, com saída segura quando não há água; corrigir
avanço/limite do helper antes de reativar. Testar sem água, coluna de água,
borda descarregada e garantia de término com limite de consultas.

### R15 — P2 — Limpar seleção pela capability não é seguro/sincronizado

Achado complementar sinalizado pelo agente principal e confirmado nesta revisão:
`SelectedRatCapability.java:33` declara `setSelectedRat(@Nullable TamedRat)` e
envia pacote, mas `UpdateSelectedRatPacket.java:17` desreferencia o rato sem
tratar null. Chamar o contrato permitido com null no servidor lança NPE.
`clearSelectedRat:22` evita o NPE ao não enviar pacote, deixando observadores
com seleção antiga. O handler cliente faz cast direto de entidade por ID, sem
verificar tipo, e usa level sem null guard.

Representar seleção vazia com ID sentinela, unificar clear/set null, enviar
somente com host inicializado no lado servidor, validar level/tipo no cliente.
Testar selecionar→limpar, set null, host ainda não associado e entidade ausente
ou de outro tipo, sem exceções ou eco cliente→servidor. Manter seleção local e
observadores coerentes; não transformar ID desconhecido em rato arbitrário.

## SOLID, DRY, KISS e testabilidade

- Preservar a boa separação atual de geometria, inventários, rotas e execução
  dos goals. Não introduzir hierarquias genéricas para pequenos reparos.
- As validações repetidas devem compartilhar pequenos helpers para autoridade
  de pacote e capacidade de slots (R3/R7), evitando divergência entre menu normal
  e cage. Limites pertencem ao modelo/setter, não apenas ao slider (R5).
- Vários arquivos novos comprimem métodos inteiros numa linha (TubeTravel,
  RatFarmingBlockEntity, StorageMenu e pacotes). Isso dificulta ver a ordem de
  extração/depósito, precedência booleana e mudanças de estado. Reformatar os
  métodos tocados e nomear predicados é melhoria pertinente; reformatar todos
  os 705 arquivos seria churn opcional, não requisito desta correção.
- Testes devem exercer ações reais de menu e lifecycle, não apenas helpers que
  repetem a implementação. Usar conservação exata de itens com NBT, limite de
  workers e controles positivos para permissões. Não abrir o cliente.
- Não há evidência suficiente para exigir reescrita do scheduler, abstração de
  todos os upgrades ou mudanças de balanceamento. Tais mudanças são opcionais.

## Documentação e cobertura complementar

`docs/STORAGE_PT_BR.md` ainda afirma que rato fica suspenso quando tubo rompe;
o contrato sg.14 permite saída/reentrada segura. Atualizar guia/versão sem
apagar registros históricos. `CAGE_EXECUTION_PLAN.md` descreve fase anterior
de breeding só explícito e inspeção read-only; assinalar como histórico para não
contradizer auto-breeding/edição atuais. Isso é correção documental, não mudar
código para obedecer especificação antiga.

Lidos/rastreados: StorageInventory/Ledger/Stock/Scheduler/Routes/Events/Task,
RatStorageBlockEntity/Goal; TubeTravel; RatFarmingBlockEntity/Goal/FarmGoal,
FarmLayout/Crops e menu; CageLayout/Habitat, RatCageMenu e lantern; Quarry
BlockEntity/Goal/Safety/Geometry/Audit/Travel e menu; VariantRatSpawnEggItem;
RatCargo, InventoryRat/TamedRat relevantes, rede inteira/callers, RatMenu e
RatCraftingTableMenu, utilitários de ratos e buscas de loops/índices/NBT.
Telas/renderers foram inventariados e callbacks de rede rastreados; aparência,
escala da GUI e animação continuam necessitando validação manual da usuária.

Não foi demonstrada duplicação nos fluxos novos de storage/farm/quarry nem
defeito novo no ovo aleatório: seus guards de owner, variante, sucesso de spawn
e consumo são coerentes. Isso é ausência de achado nesta inspeção, não garantia.
Revalidar os 131 testes anteriores após os reparos, acrescentar os casos acima
e registrar exatamente os resultados e qualquer limitação restante.

## Adendo final — correções sg.15 e reavaliação (13/10/2026)

**Resultado:** os defeitos concretos R1–R16 identificados nesta auditoria foram
tratados. A releitura estática do diff contra `6e1b787`, incluindo os novos
helpers e testes, não identificou outro defeito concreto introduzido pelos
reparos. Isso não é uma certificação de ausência de bugs no mod inteiro.
O agente principal executou os testes; o subagente conferiu o registro final:
`build/sg15-tests3.log:2245` informa **155 testes obrigatórios aprovados**, e a
linha 2268 registra `BUILD SUCCESSFUL`. A execução anterior tinha 152; os três
casos adicionais de autoridade/recall também integram agora a execução de 155.

### R16 — P1 — bloco arremessado duplicava recursos e perdia NBT

Achado durante a revisão dos reparos de R2, em
`server/entity/projectile/ThrownBlock.java`, método `onHit`: o fluxo original
colocava o bloco e depois também criava um item, sobrescrevia o destino sem
validar substituição e montava um composto NBT sem carregá-lo na BlockEntity.
Além disso, `dropBlock=false` dos projéteis psíquicos não sobrevivia ao save/load.
Era possível obter bloco mais item no impacto ou tornar físico um projétil
psíquico recarregado. Classificação P1 pela duplicação de recursos.

Correção implementada: impacto apenas no servidor e uma vez; colocação somente
em destino carregado, dentro dos limites, substituível, sem BlockEntity
existente e compatível com o bloco. Há exatamente uma alternativa: colocar ou
devolver o item físico. O NBT é efetivamente carregado no bloco colocado ou
preservado como `BlockEntityTag` no item de retorno. `DropBlock` é persistido;
a ausência da chave conserva a interpretação legada. Os quatro testes de
`AstraProjectileGameTests` verificam impacto repetido, destino ocupado, carga e
nome de baú, restauração real da BlockEntity e persistência do projétil psíquico.

### Fechamento dos achados e salvaguardas preservadas

| Achado | Correção conferida |
|---|---|
| R1 | Direção explícita nos 26 registros, mesma ordem/discriminadores; protocolo sg.15. |
| R2 | Fluido aplicado no mundo cliente e enviado somente ao tracking correto; curdler envia apenas no servidor. Blocos usam seu `CARRIED_BLOCK` nativo: envio redundante removido e handler legado inofensivo, ainda registrado. |
| R3 | Autoridade compartilhada de menu/staff/mesa, validação de comando e alvo; jukebox precisa estar realmente tocando. Staff preserva seleção própria remota, sem impor alcance de menu ao rato. |
| R4 | Foice usa equipamento real do servidor, ignora stack alegada e limita cadência. |
| R5 | Raio limitado no setter e getter, incluindo NBT/default de configuração. |
| R6/R12 | Seleção aleatória inclui o último elemento e funciona com uma única oferta. |
| R7 | Menu invalida owner/distância/entidade obsoletos; slots respeitam carga real e impedem retirar capacidade necessária. Menu normal e cage compartilham `RatCargo.canReplaceUpgrade`. |
| R8 | Baixa diferida persistente para estações descarregadas; consumo único da baixa; descarregamento legítimo não é morte. Retirement storage idempotente, com reset na nova atribuição. |
| R9 | Dock externo rejeita fluido, falta de piso, colisão alta e tubo; saída é reavaliada se o mundo mudar durante recall. |
| R10 | Decoders limitam listas antes de alocação; IDs inválidos não causam crash. UI acompanha 256 nós/2048 mobs/128 caracteres e permite limpar dados legados com feedback localizado. |
| R11 | Teleporte considera o mínimo real do mundo, inclusive abaixo de Y=0. |
| R13 | Releitura de NBT não acumula patrulha nem conserva campos opcionais ausentes. |
| R14 | Busca aquática limitada e em chunks carregados; descida de coluna progride e termina. |
| R15 | Seleção nula usa sentinela -1, clear sincroniza e cliente valida tipo/ausência de entidade; host ainda não associado é seguro. |
| R16 | Colocação ou retorno único, preservação de NBT e de `dropBlock`, conforme seção acima. |

R7 foi conferido também no nível do item: capacidade respeita
`ItemStack.getMaxStackSize()`, dez posições dependem de Silver Tray e os três
slots de upgrades são avaliados considerando a substituição pretendida,
inclusive upgrades combinados. Carga legada não é apagada para satisfazer o
novo limite; retirar a capacidade é bloqueado enquanto essa carga a exigir.

R8 não remove UUID simplesmente porque `getEntity` retornou nulo. O ledger
registra destruição/mudança de dimensão ou mudança de vínculo com estação
descarregada; `UNLOADED_TO_CHUNK`/`UNLOADED_WITH_PLAYER` não são tratados como
destruição. As baixas carregadas limpam também leases/reservas pertinentes.
Os testes cobrem serialização/consumo único, `discard` real com chunk distante
descarregado, ausência de force-load, reatribuição e unload de worker storage.

**Regressão encontrada e corrigida durante a implementação:** a primeira suite
falhou em `staffLinksChestBeforeItsGuiAndRatEntersFromOutside`. A nova checagem
de segurança aceitava tubo como dock de caminhada, fazendo o rato tentar
entrar pela parede fechada do tubo. Dock externo agora exclui tubos, sem mudar
as regras de `TubeTravel`. O teste preserva a entrega real de itens e ganhou
asserts que distinguem tubo de célula externa suportada. A suite final passou,
incluindo recall que perde o piso da saída e precisa escolher outra lateral.

### Qualidade e limites da verificação final

- SOLID/DRY/KISS: dois helpers pequenos concentram autoridade e capacidade;
  `WorkstationLedger` tem responsabilidade restrita de baixa diferida, não
  cria itens nem força carregamento. Não houve refatoração genérica ou mudança
  de balanceamento sem evidência. A formatação compacta de código legado ainda
  é dívida de legibilidade, não motivo para reescrever o mod nesta entrega.
- Os testes novos exercem ações reais de menu, inventários, projéteis, NBT,
  lifecycle, autoridade remota e recall, além dos testes anteriores. A checagem
  estrutural de rede verifica direção/ordem/tracking; ela não substitui teste
  adversarial completo de tráfego multiplayer real.
- Não foi aberto cliente. Layout, escala, feedback visual, renderização de
  fluidos/projéteis e animações continuam sem validação visual. A compilação e
  execução de servidor não comprovam todos os caminhos gráficos.
- O cenário de chunk remoto realmente descarregado e a persistência do ledger
  são testados, mas não todas as permutações de reinício completo, morte,
  viagem entre dimensões e recuperação de saves antigos de terceiros.
- NBT dos projéteis foi testado com baú vanilla; BlockEntities arbitrárias de
  todos os mods não foram exercitadas. Tampouco foram exauridos todos os
  upgrades, bosses, worldgen ou estados possíveis de mundos antigos.
- As observações iniciais sobre documentação storage/cage foram tratadas como
  atualização de guia e indicação de histórico, não como alteração retroativa
  das regras de jogabilidade. Não há recomendação opcional de arquitetura
  transformada artificialmente em bloqueio da entrega.

Parecer final: os achados demonstráveis desta revisão estão corrigidos e têm
verificação proporcional ao risco; as limitações acima devem acompanhar a
entrega, sem prometer que todas as falhas possíveis foram eliminadas.
