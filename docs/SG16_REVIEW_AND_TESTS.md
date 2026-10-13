# Revisão sg.16

Dois revisores independentes examinaram storage e tarefas. Achados aplicados:

- Reserva cumulativa por slot para bandejas, incluindo parcelas em trânsito;
  filtros e stacks ampliadas são consultados no handler real, sem mutá-lo.
- Consolidação precede categoria mixed. Destino retirado inicia recuperação
  após 200 ticks; controlador cheio conserva carga e tarefa.
- Layout de fazendas antigas persiste entre saves e é sincronizado no menu.
- Fontes cobrem também grupos parciais finais; teste de luz real subterrânea.
- Hitbox de sementes não intercepta botões dos modos.
- Pescador com carga parcial continua trabalhando; hook temporário é descartado.
- Tickets exigem tarefa/upgrade/vínculo válido, liberam estações destruídas e
  não registram uma solicitação de carregamento que falhou.
- Auditoria histórica de minério é compartilhada pela quarry, limitada a uma
  seção por segundo; usa as mesmas reservas de veia dos trabalhos correntes.
- Controller deixa o clique com o upgrade de mochila chegar ao item, em vez de
  abrir a GUI antes do vínculo. Regressão cobre PASS do bloco e useOn real.

As validações são em servidor GameTest sem cliente gráfico. Animações, aparência
e interação visual continuam pendentes de teste da usuária; nenhuma afirmação
de validação visual se baseia apenas em leitura de código.

## Reproduzir

Java 17. A integração de mochila compila contra releases públicos fixos do
Modrinth (Core XQog8w6A; Backpacks 1umN9rSN), sem embuti-los no jar do Rats.
Para os testes de compatibilidade completos, preparar os jars instalados com
os scripts `prepare-storage-tests.cjs`, `prepare-farmers-delight-tests.cjs` e
`prepare-alexsmobs-tests.cjs` disponíveis em `scripts/`.

Executar somente uma tarefa Gradle por vez:

```sh
bash gradlew Minecraft_GameTestServer_1.20.1 -PincludeStorageTests \
  -PincludeFarmersDelightTests -PincludeAlexsMobsTests \
  -PgameTestRunDirectory=build/sg16-compat-fresh --console=plain
bash gradlew Minecraft_GameTestServer_1.20.1 \
  -PgameTestRunDirectory=build/sg16-base-fresh --console=plain
bash gradlew Minecraft_Data_1.20.1 build --console=plain
```

Resultados comprovados nos logs:

- Primeira suíte completa após correções: **177/177**, `build/sg16-tests5.log`.
- Rodada final com regressões independentes, sem mods opcionais: **182/182**,
  `build/sg16-tests-base4.log`.
- Rodada final com Backpacks, Core, Storage, Iron Chests, Farmer's Delight e
  Alex's Mobs: **182/182**, `build/sg16-tests6.log`.
- Repetição após validar o clique de vínculo da mochila: **182/182**,
  `build/sg16-tests7.log`.

A rodada sem opcionais detectou carregamento antecipado da classe pai da API
Backpacks no registro. Corrigido com pontes isoladas; helpers de testes opcionais
também foram isolados. O script de regressões agora exige o marcador de testes
aprovados, porque o launcher Forge pode terminar com exit zero após falhar no
bootstrap. Não basta confiar em `BUILD SUCCESSFUL`.

O perfil instalado permanece sg.15 até novo pedido. Novos e antigos mundos podem
ser carregados pela sg.16 preservando os registros existentes. Isso não garante
que itens novos existam ao voltar para um jar antigo: downgrade requer restaurar
um backup, especialmente antes de usar o novo upgrade de mochila.

## Artefato

`build/libs/Rats-1.20.1-8.1.3-sg.16.jar`, build final bem-sucedido registrado em
`build/sg16-build-final.log`; datagen em `build/sg16-build.log`.

SHA-256: `f0a5d997cf2bb80de1e5873807ebf8f726fe993843bb7444a97e1d9e2fd94700`.
Manifest confirma sg.16; traduções novas existem no jar; classes/fixtures de
GameTest, caches de datagen e bibliotecas Sophisticated não foram embutidos.
Protocolo sg.16 exige a mesma versão nos clientes e servidor.

Para vincular a mochila: antes de instalar o upgrade nela, segure o upgrade e
clique com o botão direito no controller de sua propriedade. Então instale-o
na mochila e use seus nove slots de entrada na aba nativa. Se o input estiver
cheio ou o vínculo indisponível, os itens restantes continuam no upgrade.
