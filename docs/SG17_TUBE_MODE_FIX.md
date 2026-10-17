# sg.17 — tubos não devem sequestrar tarefas

## Plano aplicado

1. Separar permissão de procurar tubos da permissão física de sair de um tubo.
   Apenas Transport com vínculo/carga válido e trabalhadores do storage procuram
   tubos. Movimento interno das gaiolas permanece compatível.
2. Usar essa política no pickup/deposit e preservar a face configurada do baú
   para outros modos. Um pescador com captura não vira transportador por isso.
3. Recuperar trabalhadores presos por versões anteriores: busca carregada e
   limitada por uma saída com chão, sem teleporte nem descarte de inventário.
   Sem saída segura, manter posição/carga; tentar novamente após intervalo.
4. Testar todos os comandos, depósito com tubo atrás do baú, recuperação com
   paredes fechadas e mudança de comando, e IA completa pescando após sair.
   Rodar também as regressões de transporte/storage, sem abrir o cliente.

## Compatibilidade

Não altera registros, receitas, inventários ou NBT existente. A recuperação
também funciona em ratos de mundos anteriores. Novos campos são apenas estado
transitório das goals. O protocolo sg.17 exige versões iguais no multiplayer.
Instalação no perfil do launcher somente após pedido separado.

Pescadores e outros produtores em Harvest precisam de depósito acessível pelo
chão. Para um baú alcançável apenas por tubos, use um transportador separado;
ter um depósito configurado não transforma Harvest em Transport.

## Limites

Não é uma revisão geral de navegação. A saída é procurada em tubos carregados,
com orçamento de busca e tentativas espaçadas. Tubo completamente fechado, sem
chão externo seguro ou saída apenas em chunks descarregados pode exigir abrir
um acesso; o rato não atravessa paredes nem é teleportado para fingir recuperação.
Tubos ligados a uma gaiola ficam sob as regras do habitat, não são tratados como
trabalhadores soltos que devem ser retirados automaticamente.
Testes de servidor não substituem verificação visual pela usuária.

## Evidências

- `build/sg17-tube-mode2.log`: 186/186 testes obrigatórios aprovados.
- `build/sg17-tube-mode3.log`: repetição final, 186/186 aprovados, já incluindo
  a proteção para tubos conectados a gaiolas. Mundos de desenvolvimento novos.
- Compatibilidade carregada: Core, Backpacks, Storage, Iron Chests, Farmer's
  Delight e Alex's Mobs nas mesmas versões da rodada sg.16.
- `node scripts/check-audit-network.cjs`: 26 direções e IDs de mensagens
  preservados; verificação estrutural aprovada.

Os quatro testes novos verificam todos os comandos com carga/vínculos, face do
depósito, depósito a pé com tubo junto ao baú, pescador inicialmente preso
executando pesca e depósito sob a IA real, saída bloqueada sem perda de NBT,
gaiola conectada, troca para Transport durante recuperação e restauração da
física após sair. Não é somente uma simulação de geração de loot.

Na primeira rodada, dois testes antigos exigiam HARVEST usando tubos. Foram
atualizados para TRANSPORT sem remover suas exigências de quebra/reentrada,
subida, recarga NBT e entrega exata de 64 itens. A regressão nova cobre HARVEST
separadamente, exigindo que o pescador não reentre no tubo.

## Artefato

`build/libs/Rats-1.20.1-8.1.3-sg.17.jar`; build aprovado em
`build/sg17-build.log`. Manifest confirma sg.17; o jar não contém testes,
fixtures, caches nem bibliotecas Sophisticated embutidas.

SHA-256: `27e29ba22535f1ae6bef693356828f2b0dc13d7130fee2313bfa2823d1629393`.
O jar foi gerado localmente; o perfil do launcher não foi alterado.
