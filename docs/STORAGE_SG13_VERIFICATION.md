# sg.13 — correções e evidências

## Causas identificadas

- `ChestBlock.use` consumia o clique antes de `Item.useOn`, abrindo o baú em vez
  de conectá-lo. A storage staff agora usa `onItemUseFirst`.
- Os testes sg.12 colocavam o rato já dentro do controller. A entrada real
  dependia de uma frente livre e da precisão que a navegação vanilla não
  garante. O novo teste começa quatro blocos fora, usa a staff real e bloqueia
  a frente com o input. O docking escolhe uma face livre com chão; a aproximação
  final usa passos limitados com colisão verificada.
- A mesma frente bloqueada impedia retirar ratos. Agora o menu tem botões
  reais, e a saída encontra outra face livre. As cargas continuam preservadas
  no recovery antes da desconexão.
- A tela decodificava NBT, calculava nomes e ordenava todos os itens por frame.
  Agora reconstrói o catálogo apenas em snapshot novo/pesquisa alterada.
  Os viewers compartilham o catálogo por dez ticks; snapshots iguais não são
  retransmitidos. Pedidos autoritativos continuam verificando estoque atual.
- Output aceitava cliques e automação. Slots rejeitam colocação, quick-move
  rejeita entrada e a capability pública é somente extração. A doca interna
  dos ratos permanece gravável.

## Interface e seleção

Slots usam a textura vanilla do inventário; bancada usa sua seta e slot de
resultado vanilla. Catálogo 12×5 (60) ou 9×5 (45) no modo compacto. Busca,
quantidade, retirada comum e bancada funcional permanecem lado a lado.

Busca de ingredientes para receitas foi removida. Pedidos/sessões legados não
são apagados; recebimentos antigos são visíveis somente quando têm itens.

Staff mostra contornos no controller, endpoints e ratos; linhas indicam as
conexões dos tubos. Três círculos de raio 20 mostram a fronteira esférica real.
O overlay é condicionado à staff selecionada, dimensão e item na mão; sua
sincronização não envia itens/sessões privadas nem faz scans de inventário.

## Limites de validação

Um cliente de desenvolvimento chegou à tela inicial, mas a automação não
controla a janela JVM e o processo de shell não tem permissão de Acessibilidade.
Nenhum mundo do launcher foi aberto. O cliente de desenvolvimento foi
encerrado. **A tela e os contornos não foram validados visualmente dentro de
um mundo**. Conforme pedido da usuária, não iniciar novos clientes: esse teste
fica com ela. Não alegar eliminação total de lag sem medição no seu modpack.

Rodadas intermediárias não contam como validação final: uma FakePlayer não
registrada não resolvia como dona do rato; um diretório de teste reutilizado
continha entidades antigas; uma compilação paralela substituiu classes de um
servidor em execução. Use player registrado, mundo novo e tarefas sequenciais.

## Testes novos

- Clique através de `ServerPlayerGameMode.useItemOn`: seleciona controller e
  conecta baú sem abrir a GUI do baú; staff registra rato externo; transporte
  físico desde a entrada até o destino.
- Ação real do menu agenda recall; rato sai por outra face com frente obstruída.
- Output rejeita inserção manual/Shift e capability externa, mantém retirada
  e entrega interna. A antiga ação de refil de receita não cria pedidos.

## Rodada final

`build/sg13-tests5.log`: **All 104 required tests passed**, **BUILD SUCCESSFUL
in 2m 26s**. Diretório isolado `build/sg13-tests-fresh5`.

Inclui Iron Chests 14.4.4, Sophisticated Storage 1.5.0.2137, Core 1.5.4.2362 e
Farmer's Delight 1.3.4 reais, além das regressões de quarry, cage e farming.

Reproduzir com JDK 17:

```
rtk proxy bash gradlew Minecraft_GameTestServer_1.20.1 \
  -PincludeStorageTests -PincludeFarmersDelightTests \
  -PgameTestRunDirectory=build/sg13-novo-mundo --console=plain
```

Use um diretório que ainda não exista para não reaproveitar entidades.
