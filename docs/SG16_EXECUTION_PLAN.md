# sg.16 — plano e critérios de aceite

1. Fazenda: nova configuração quadrada 2–16 (retângulos salvos preservados até
   edição), grade de umidificadores luminosos a cada quatro células, no solo.
   Pausar workers enquanto qualquer menu estiver aberto; executar após fechar.
   Limpar planejamento; presets substituem marcações. UI mostra direção e
   orientação da divisão. Falta de terra não bloqueia células cultiváveis.
   Preservar máquinas, claims, inventários e drops em limpezas/migrações.
2. Storage: snapshots só após menu efetivamente aberto; testar remontagem no
   cliente e primeira atualização. Entrada/idle ocultos no controller, saída
   restaura estado; carga continua física. Caixa ±20 em X/Y/Z (41 blocos entre
   extremos), mesma geometria no linking/rotas/outlines.
   Tags têm precedência sobre BlockItem; destinos estáveis e reservas não
   espalham uma pilha se o primeiro baú ainda comporta o restante.
3. Tubos: apenas tarefas explícitas usam tubos; quarry/fazenda não adotam tubo
   acidental próximo ao depósito. Velocidade vinculada ao atributo normal,
   animação locomotora e subida; nenhum limite geométrico artificial para
   transporte genérico, mantendo buscas por orçamento/cache.
4. Continuidade: tickets Forge para trabalhos ativos, próximos passos e
   estações, atualização amortizada, desligamento/desvinculação libera tickets.
   Não prometer processamento com servidor desligado, nem carregar mundo inteiro.
5. Quarry/pesca: provar discovery/extração de veia e retorno com múltiplos ratos;
   tochas por suporte independente em cada lado, distância cinco. Pesca deve
   chegar ao ponto acima da margem e concluir cooldown sem reset constante.
6. Sophisticated Backpacks: integração opcional com API real da versão instalada;
   upgrade/aba/slots nativos, vínculo validado por dono ao usar upgrade no controller, transferência
   imediata e resto conservado no slot se input cheio/offline/destruído.
   Receita cara com golden rat skull, ender eye, netherite e upgrade base.
7. Testes headless: legado/novo layout, hidratação real, menu aberto/fechado,
   sem terra, limpeza protegida, presets, primeiro snapshot, idle/recall,
   consolidação/reservas/NBT, alcance cúbico, tubos acidentais/vertical,
   veia na fronteira, suporte de uma parede, pesca, lifecycle de tickets e
   backpack input cheio/link inválido. Revisão e build finais; não abrir cliente,
   não instalar sem pedido separado. Commits por fluxo coerente.
