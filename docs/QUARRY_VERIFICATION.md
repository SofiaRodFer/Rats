# Verificação da implementação

Data: 2026-10-11. Minecraft 1.20.1, Java 17, ambiente de desenvolvimento do próprio Rats.

## Comandos executados

```sh
node scripts/generate-quarry-test-fixture.cjs
JAVA_HOME="/caminho/do/jdk17" bash gradlew Minecraft_Data_1.20.1 --console=plain
JAVA_HOME="/caminho/do/jdk17" bash gradlew Minecraft_GameTestServer_1.20.1 build --console=plain
git diff --check
```

Resultado final: **BUILD SUCCESSFUL; All 9 required tests passed**.

Os GameTests rodam entidades, inventários e blocos reais no servidor Minecraft, em mundo de desenvolvimento separado. As classes e a estrutura de teste são excluídas do JAR distribuível.

## Cenários que passaram

1. Seções exatas de 4×4/6×6 nas cinco direções, incluindo coordenadas dos limites de 256/512 blocos.
2. Dez stacks com NBT distinto; recusa do décimo primeiro; persistência e depósito de todos os itens.
3. Limites de 16 para pérolas, item não empilhável com durabilidade, simulação sem mutação e destino cheio sem perda.
4. Reservas distintas para dois ratos, invalidação ao trocar direção e progresso separado persistido.
5. Vedação de água/lava, drenagem da seção e ausência de drops da contenção.
6. Rato escavando duas seções básicas, atravessando o restante vazio até o limite configurado de 16 e depositando os 32 blocos.
7. Mesmo fluxo na Advanced Quarry, com 72 blocos escavados e depositados.
8. Escavação vertical e retorno com carga pelo poço; rato vivo e carga depositada.
9. Rato com plantação madura inacessível: liberação da tarefa e agendamento de nova tentativa.

Checagens adicionais: receitas JSON iguais às matrizes solicitadas; três PNGs 16×16 conferidos visualmente; chaves de tradução PT-BR com correspondentes em inglês; conteúdo do JAR inclui modelos/receitas/artes novos e exclui os testes.

## Limites da validação

- Não houve revisão manual da interação/renderização das telas no cliente.
- Não foi percorrido um túnel inteiro de 256/512 blocos por jogadores; os limites foram conferidos por geometria, e a IA por túneis curtos.
- Não houve sessão com três jogadores nem teste de compatibilidade do modpack completo.
- Proteções de terceiros, longos trajetos com chunks descarregados, líquidos modificados e todos os upgrades combinados ainda exigem teste de integração.
- A recuperação automática reduz travamentos por alvos inacessíveis/estados pendentes; não é uma promessa de eliminar todos os bugs de IA.

## Artefato

JAR local: `build/libs/Rats-1.20.1-8.1.3-sg.1.jar`.

SHA-256 desta compilação:

```text
d86bd3ac3bd8c804f6f0cb8eb3fcc62d2725ff42d565e2d2df1dfe0dd37e51c6
```

O modpack instalado e seus mundos não foram modificados. Para uma sessão multiplayer futura, os três jogadores e o servidor devem usar a mesma compilação.
