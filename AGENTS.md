# Preferências de teste e instalação

- Não iniciar o cliente Minecraft para testes. A usuária realizará a validação
  visual e de jogabilidade dentro do jogo quando necessário (2026-10-13).
- Compilação, inspeção estática e testes automatizados de servidor sem janela
  devem usar diretórios de desenvolvimento, nunca os mundos do launcher.
- Não executar duas tarefas Gradle que recompilam o mesmo output enquanto um
  GameTest estiver rodando. Use um diretório de mundo novo a cada rodada.
- Instalar novos jars apenas mediante pedido da usuária, somente no perfil
  `tres amigos teste`, com backup dos saves e do jar anterior e cliente fechado.
