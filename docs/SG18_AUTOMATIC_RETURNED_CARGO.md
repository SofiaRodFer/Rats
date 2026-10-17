# sg.18 — carga devolvida integrada ao sistema

- Removida a grade “Recovered cargo” da aba System, inclusive seus slots de
  servidor; a lista de componentes/fila usa o espaço liberado.
- Os 54 slots internos existentes continuam salvos, sem apagar ou migrar itens.
  Passam a ser uma fonte normal de estoque para o catálogo e solicitações.
- Itens devolvidos são organizados automaticamente pelos ratos usando as mesmas
  regras de classificação, consolidação, capacidade por slot e reservas.
  Não precisam passar manualmente pelo input, nem exigem um input para isso.
- Também podem ser solicitados normalmente ao output, mesmo sem baú disponível.
- Se não houver capacidade/rota ou trabalhador disponível, permanecem salvos;
  não são descartados nem teleportados aos baús. O transporte continua físico.
- A bandeja de prata pode agrupar itens diferentes da carga interna, respeitando
  a capacidade cumulativa do destino e as reservas dos demais trabalhadores.
- Os estados dos ratos agora descrevem devolução ao sistema/espera por espaço,
  sem apresentar uma segunda área de armazenamento para o jogador gerenciar.

## Compatibilidade e testes

O container do controller passou de 109 para 55 slots (crafting, recebimentos
legados e inventário do jogador). Input/output mantêm seus slots. Protocolo sg.18
impede que clientes antigos interpretem os índices novos incorretamente.
NBT, IDs e conteúdo interno anteriores são preservados. Inclui a correção dos
tubos da sg.17, sem alterar a regra dos modos de trabalho.

Seis regressões novas em `AutomaticReturnedCargoGameTests`:

1. Carga salva antiga aparece no catálogo com variantes NBT distintas.
2. Rato real distribui dois itens com silver tray, sem input e sem duplicação.
3. Baú cheio conserva a carga e a distribuição retoma quando há espaço.
4. Solicitação normal retira carga interna e entrega ao output sem nenhum baú.
5. Não existem slots internos expostos; cliques no inventário e crafting continuam
   corretos após a mudança de índices.
6. Dois ratos reservam exatamente 64 + 36 itens; um terceiro não duplica a fonte.

Suíte completa: **192/192 testes obrigatórios aprovados**, com Core, Backpacks,
Storage, Iron Chests, Farmer's Delight e Alex's Mobs, em mundo de desenvolvimento
novo (`build/sg18-returned1.log`). Sem cliente Minecraft e sem alterações nos
mundos/perfil do launcher. Aparência visual ainda depende da validação da usuária.
Instalação somente mediante novo pedido.

## Artefato

`build/libs/Rats-1.20.1-8.1.3-sg.18.jar`; build e datagen registrados em
`build/sg18-build.log` e `build/sg18-build-final.log`. Traduções novas conferidas
dentro do jar; testes, fixtures, caches e bibliotecas Sophisticated não embutidos.

SHA-256: `8a0e52115151466935c2b59a91ae83f3ef011c5a5c1a0614cf2c1b68ca6cbac1`.
