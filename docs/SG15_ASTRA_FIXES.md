# sg.15 — revisão Astra aplicada

## Resultado

Subagente GPT-6 Astra, esforço médio, auditou o mod e voltou a revisar os
reparos. Relatório original e complemento: [ASTRA_REVIEW_REPORT.md](ASTRA_REVIEW_REPORT.md).
Foram corrigidos os defeitos concretos identificados, sem reescrever o mod inteiro.

- Rede: direção explícita nos 26 pacotes, mesmos discriminadores; autorização
  por menu/dono/staff selecionada; foice usa equipamento e cadência do servidor.
- Dados: limites de raio, patrulha e filtros; IDs inválidos não causam exceção;
  telas impedem enviar listas inválidas e oferecem recuperação explícita.
- Inventário: capacidade/filtro consistentes no menu normal e na gaiola;
  redução de capacidade bloqueada enquanto houver carga incompatível.
  Cargas antigas nunca são truncadas para caber no limite atual.
- Trabalhadores: desvinculação persistente em remoção/troca de dimensão,
  inclusive estação descarregada; unload normal não é morte.
  Retirement do storage é idempotente, sem recriar reservas como itens.
- Navegação: docking externo seco, apoiado e sem colisão; reavalia a saída
  quando muda; não confunde tubo fechado com entrada para navegação terrestre.
  Teleporte Ender encontra chão abaixo de Y=0. Passeio aquático Pirat restaurado
  com busca limitada e helper que termina.
- Conservação: bloco arremessado é colocado OU devolvido como item, nunca ambos;
  não sobrescreve destino ocupado, preserva dados do bloco e flag não-física.
  Estado visual usa metadata nativa, não consulta uma posição já removida.
- Aleatoriedade/NBT: última oferta/slot/orientação pode ser escolhida; leitura
  repetida de patrulha substitui dados, não acrescenta duplicatas.

SOLID/DRY/KISS: autoridade dos pacotes e capacidade futura têm pequenos helpers
compartilhados. Não foram introduzidas hierarquias genéricas ou refatorações
globais sem evidência. Testes exercitam menus, impactos e goals reais, além de codecs.

## Evidência reproduzível

Suíte final: **155 testes obrigatórios aprovados**, zero falhas.
Log: `build/sg15-tests3.log`, linhas 2245 e 2268.
Inclui os 131 casos anteriores e 24 novas regressões de utilitários, segurança,
lifecycle, inventário, projéteis e mudança de saída durante recall.

Primeira rodada detectou uma regressão real: docking selecionava tubo fechado.
A causa foi corrigida e o teste existente ampliado; segunda rodada passou 152
casos, terceira ampliou os controles positivos e passou 155.
Dois testes novos inicialmente usavam dono mock fora do registro de jogadores;
a fixture foi corrigida para exercitar ownership real, sem relaxar permissões.

Com Java 17:

```sh
node scripts/check-audit-network.cjs
rtk proxy bash gradlew Minecraft_GameTestServer_1.20.1 \
  -PincludeStorageTests -PincludeFarmersDelightTests -PincludeAlexsMobsTests \
  -PgameTestRunDirectory=build/sg15-audit-run3 --console=plain
```

Usar diretório de mundo novo por repetição. Verificação estrutural de rede
confirma 26 direções, IDs preservados, broadcasts de fluido por tracking e
metadata de projétil; não simula ataque de rede em cliente real.
Mods de compatibilidade no staging: Iron Chests 14.4.4, Sophisticated Storage
1.5.0.2137/Core 1.5.4.2362, Farmer's Delight 1.3.4, Alex's Mobs 1.22.9/Citadel
2.6.3. Avisos de advancements opcionais do Sophisticated Backpacks ausente não
equivalem a falha/aprovação de um teste.

## Compatibilidade e limites da conclusão

Artefato: `build/libs/Rats-1.20.1-8.1.3-sg.15.jar`.
SHA-256: `712e8736d1b78d09f249d11de31a1749d0aac20f13081b0c87fb1bab708b15ca`.
Datagen concluído em `build/sg15-data.log`; build separado concluído em
`build/sg15-build.log`. Manifest confirma sg.15, traduções audit em inglês/
português presentes; arquivo não incorpora testes, fixtures, caches ou jars
dos mods de compatibilidade.

Registros e IDs de conteúdo preservados. Novos campos têm defaults para saves
anteriores; versões anteriores não são modificadas nem mundos migrados no launcher.
Não é promessa de downgrade seguro para Rats upstream, que não contém os novos
blocos/upgrades. Protocolo sg.15 exige a mesma versão nos clientes e servidor.

Nenhum cliente Minecraft foi aberto; perfil instalado permanece sg.13.
Mundos de desenvolvimento são descartáveis, separados dos saves da usuária.
Interfaces, animações, espelhos cliente de fluidos e multiplayer com clientes
reais ainda precisam de teste manual. Auditoria de 705 arquivos por inventário/
fluxos não foi leitura linha a linha de todos os modelos/upgrades/bosses;
não prova ausência de todo bug possível.
