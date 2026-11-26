# NERO Music — critérios para 1.0.0-RC1

Status: estabilização pré-RC, 0.9.66-dev. Não é aprovação de distribuição.

Correções 0.9.66: unificação dos comandos de reprodução do miniplayer, player, letras e widget; pausa durante buffering, nova preparação após erro e reinício após fim da faixa por ação explícita. Mensagens para falhas de rede, HTTP, arquivo e permissão. Sem novas tentativas automáticas, sem alteração da fila ou fonte de streaming. Testes de estados adicionados; teste físico de rede móvel, Wi-Fi, modo avião, retorno da conexão e segundo plano pendente.

Correções 0.9.65: metadados do MediaController por eventos, atualização de posição somente em primeiro plano durante reprodução; atualização inicial ao retomar, listener removido ao sair. Transição de imagem preservada no player grande e desativada em miniaturas. Sem alterações de tamanho, qualidade, cache ou serviço de reprodução. Medições de CPU, memória, fluidez e validação de PiP/segundo plano ainda pendentes em aparelho.

Correções 0.9.64: um único timeout de consulta de assinatura, com ausência de resposta tratada como não verificada; tela de planos distingue catálogo experimental e release sem catálogo. Sem mudanças no checkout, no SQL ou na fonte musical.

## Evidência de CI
Execução #299 (commit 708fc9a21e069280aa965eba8be1c7ac6b331c31): assembleDebug, lintDebug, testDebugUnitTest e verificação de release sem assinatura concluídos com sucesso. Execuções #292–298 também concluídas com sucesso. Isso não comprova testes em aparelho, configuração de produção ou assinatura comercial. A nova versão acrescenta teste Python da referência Commerce Connect ao CI; resultado ainda pendente.

## Bloqueios atuais
- NativeMusicCatalog no release retorna enabled=false; Smart Mix e Daily Mix online dependem dessa fonte. Não habilitar o extrator experimental em distribuição comercial. Definir fonte autorizada e verificar paridade de recursos antes de vender benefícios.
- O checkout é uma prévia desativada. O responsável pela T.I. Machado configurará a loja manualmente; nenhum produto, gateway ou configuração de produção foi modificado. A integração servidor-servidor, ativação, trial, renovação, estorno e isolamento ainda exigem homologação.
- SQL de assinatura é rascunho não aplicado. Verificar estado da assinatura com duas contas de teste, expiração e falhas de rede; não liberar Premium por resposta de checkout.
- O pipeline de APK assinado é manual e restrito à main e ambiente nero-release. Não foi executado nesta revisão, não há APK comercial assinado verificado. Não mesclar nem publicar sem autorização.

## Matriz Premium no Android
- [ ] Temas: Aurora gratuito; validar Ocean, Violet, Gold e Midnight Café, logout e expiração.
- [ ] Replay: consentimento, duas contas, pausa, limpeza, 7/30/90 dias e ausência de dados antigos de outra conta.
- [ ] Backup: exportação JSON, importação na mesma conta, rejeitar outra conta e arquivo inválido, mesclar sem apagar dados, repetir sem duplicar.
- [ ] Smart Mix: seleção de até seis artistas, modos, limite de faixas, exclusões, falhas de rede.
- [ ] Daily Mix: quatro momentos, rotação diária, ausência de duplicatas e de resultados fictícios.
- [ ] Assinatura: ausência, ativa, expirada, revogada, trial e carência; falhar de forma segura sem servidor.
- [ ] Commerce: seletor de planos, botão de contratação desativado até homologação, nenhuma cobrança no teste.


## Testes de aceitação em Android
- [ ] Atualização sobre APK anterior sem perda de biblioteca ou preferências.
- [ ] Busca, artistas, álbuns, imagens em alta resolução e biografias em português.
- [ ] Reprodução local, catálogo autorizado, fila, rádio, favoritos e playlists.
- [ ] Downloads autorizados, Wi-Fi obrigatório quando ativado, qualidade e reprodução offline.
- [ ] Fechar pelos recentes, reabrir, controles do sistema, retomada, bloqueio de tela.
- [ ] Bluetooth, Android Auto e Chromecast compatível em equipamentos reais.
- [ ] Login Google, renovação de sessão, foto/nome, logout e compartilhamento.
- [ ] Preferências de qualidade e navegação da conta.

## Segurança e privacidade
- [ ] Revisar permissões, logs, credenciais de assinatura, isolamento de dados e políticas de acesso.
- [ ] Validar inventário de dados remotos, retenção, revogação de sessões e exclusão autenticada no servidor.
- [ ] Confirmar canal de atendimento e procedimento de solicitação de exclusão; fluxo atual apenas copia uma mensagem e abre o site, sem enviar ou apagar dados.
- [ ] Aprovar e publicar política de privacidade e termos definitivos, com identificação e contato do responsável.
- [ ] Verificar licença de marca, catálogo, letras, imagens, streaming e downloads antes da distribuição.

## Empacotamento comercial
- [ ] Definir fonte autorizada de conteúdo. O catálogo experimental e alguns recursos de integração estão disponíveis somente no debug.
- [ ] Validar o build release real: não presumir paridade com debug.
- [ ] Assinar com chave de produção protegida, verificar assinatura e atualização em aparelho.
- [ ] Gerar APK, SHA-256, notas de versão e página de distribuição direta.
- [ ] Aprovação explícita antes de publicar ou mesclar a branch.

Não habilitar extração experimental em release para contornar licenciamento. Não distribuir APK debug como versão comercial.

## 0.9.68-dev — revisão visual de gravação do Android
Replay em tela cheia rolável, com Fechar sempre visível; botão direto de Daily Mix reutiliza a consulta manual existente, com feedback de progresso; Discovery oferece busca pelos artistas quando a consulta não encontra álbuns; rádio local explica a ausência de arquivos e orienta uso da rádio online experimental quando disponível. Não altera o catálogo, não habilita fonte experimental no release, não altera dados ou assinatura. Validação visual em aparelho e compilação desta versão ainda pendentes.

## 0.9.69-dev — paridade de recursos do candidato assinado
A pedido do proprietário, debug e release compartilham a implementação existente do catálogo e dependências, sem substituição por outro catálogo. Busca, clipes, artistas, álbuns, mixes, rádio, downloads e controles existentes preservados no código. Cast, Android Auto e equalizador disponíveis nas duas variantes; release continua não debuggable. Sem alteração de chaves, OAuth, checkout ou banco. O candidato assinado é para homologação privada; direitos de distribuição comercial da fonte musical e funcionamento em aparelho não validados. Sem alegação de parceria ou autorização da plataforma de origem.

## 0.9.70-dev — restauração Ouça novamente
A seção passa a permanecer visível com estado vazio. Histórico recente local separado por conta, independente do opt-in de sincronização, com controle de pausa em NERO Cloud e limpeza pela tela de histórico. Registros locais e sincronizados são mesclados por data, sem importar automaticamente dados de visitante ou Replay de outra conta. Preserva catálogo e demais recursos. Sem mudanças em OAuth, banco ou assinatura; testes de mesclagem adicionados, compilação e homologação pendentes.

## 0.9.71-dev — inspeção Android
Gravação confirma busca com resultados e sessão Google conectada. Ajustes: ícone monocromático do símbolo NERO via DefaultMediaNotificationProvider.setSmallIcon; mantém capa e comandos de mídia. Busca completa após submissão em vez de disparar busca de faixas a cada digitação, sugestões permanecem; seção local vazia só no filtro local; títulos com mais espaço e ações em linha separada. Conta exibe carregamento durante recuperação da sessão, sem mostrar temporariamente botões de login. Perfil Google continua abrindo página oficial, identificado no texto. Sem alteração de assinatura, catálogo ou credenciais. Aparência do ícone depende da System UI; testes de aparelho pendentes.

## 0.9.72-dev — gêneros e conta interna
Vídeo mostra busca `pagode novidades` seguida de resultados de outros gêneros e recomendações de Clayton & Romário. Atalhos usam curadoria de artistas por gênero, filtram músicas, álbuns, artistas e clipes, não derivam seção de recomendações do primeiro resultado. Buscas livres permanecem sem restrição. A curadoria é inicial, não classificação oficial do catálogo; pode excluir colaborações e artistas ainda não incluídos. Conta NERO agora abre tela interna com Perfil, Plano e nuvem e Privacidade. Dados Google somente leitura; controle de sincronização, plano, histórico e saída reutilizam recursos existentes. Sem alteração de credenciais, banco, pagamentos ou catálogo original. Testes de gênero adicionados; compilação e aparelho pendentes.

## 0.9.73-dev — créditos e fotos de artistas
Créditos de colaboração são apresentados com feat.; nomes oficiais conhecidos com and são preservados, assim como duplas com e/&. Busca e armazenamento de referências conservam os créditos brutos no MediaItem. Perfil consulta artista principal e convidados separadamente, usa imagem de canal identificado ou thumbnail livre da Wikipédia com identificação musical; fallback de capa explicitamente rotulado. Botões de perfil abrem artista individual. Testes de normalização adicionados; compilação e imagem em aparelho pendentes. Nenhum catálogo, banco, assinatura ou checkout alterado.

## 0.9.74-dev — títulos das coleções
Normalização de colaboração nos títulos do Daily Mix básico e Premium, incluindo cache existente sem regeneração; títulos de origem do mix no player e artistas dos rankings Replay também padronizados. Preserva nomes oficiais conhecidos, chaves de cache, preferências, referências e catálogo. Testes de títulos adicionados. Workflow automático assina a nova versão após testes; validação no Android pendente.

## 0.9.75-dev — NERO Transfer online
Importador existente mantém ZIP/JSON, metadados e ordem original. Busca explícita por playlist usa somente título/artista, com vinculação automática conservadora de correspondências únicas, revisão manual no próprio NERO, progresso e cancelamento com preservação dos resultados. Fila mista local/online na ordem original. Ação explícita copia referências online únicas para biblioteca de visitante ou conta, sem sobrescrever playlists; itens faltantes e duplicatas permanecem na importação. Limites existentes da biblioteca preservados. Nenhuma transferência de áudio/assinatura Spotify ou envio do ZIP. Testes sintéticos adicionados; exportação real do usuário e homologação pendentes. Sem alteração de credenciais, pagamentos ou banco de produção.

## 0.9.76-dev — nomes das playlists importadas
O diálogo Salvar como playlist NERO preenche o nome original do Spotify (até o limite existente de 100 caracteres) e permite editar antes de salvar. Playlists online da conta ou visitante também podem ser renomeadas depois, preservando ID, ordem e referências; alterações usam a sincronização existente. Testes de nome e preservação adicionados. Sem alteração de catálogo, credenciais, banco ou assinatura. Compilação e teste em aparelho pendentes.

## 0.9.77-dev — comunicação dos planos
Tela de planos com identidade independente NERO, sem menção nominal a YouTube Music Premium e sem jargão de homologação. Mantém aviso de assinatura indisponível, benefícios previstos, ausência de cobrança, restrições de download e preços existentes. Alteração exclusivamente textual; nenhuma mudança de catálogo, direitos, pagamento, credenciais ou produção. Compilação e homologação visual pendentes.

## 0.9.78-dev — consistência da marca
Padroniza referências visíveis à marca principal NERO Music nas telas de conta, planos, descoberta, busca, playlists, transferência, compartilhamento e detalhes. Mantém submarcas NERO Premium, Free, Cloud, Transfer, Replay e Mixes; logo Aurora Neon e identificadores técnicos permanecem intactos. Informações sobre fontes externas, importação Spotify, destino de links e limitações de acesso/download permanecem explícitas onde pertinentes. Sem mudança funcional, pagamentos ou credenciais. Compilação e revisão visual em aparelho pendentes.

## 0.9.79-dev — primeira etapa Radar e DJ
NERO Radar gratuito na home, opt-in por conta/dispositivo, consultas de lançamentos datados em janela de sete dias por até seis artistas e dois gêneros inferidos. NERO DJ por busca e regras, degustação de cinco faixas e até 30 com verificação Premium existente; sem IA externa, sem ativação de cobrança. Testes sintéticos adicionados. Notificações em segundo plano, Together, Studio ampliado, Replay 365 e Smart Library permanecem pendentes. Sem alteração dos planos/preços ou do catálogo existente. Compilação e teste em Android pendentes.

## 0.9.80-dev — Radar alerts e Studio
Alertas gratuitos opt-in com permissão Android, WorkManager periódico diário sujeito ao sistema, validação local da identidade sem refresh de tokens, preferências por conta, primeiro levantamento silencioso e deduplicação. Notificação abre o aplicativo (não a faixa diretamente). Studio amplia temas Ruby Night e Glacier, atualiza preferência do player via listener e aplica cores às animações decorativas. Não há mudança de cobrança, catálogo ou outras funções. Testes de deduplicação adicionados; build e validação de notificações/temas em aparelho pendentes. Replay 365, Together e Smart Library continuam pendentes.

## 0.9.81-dev — Replay 365 e Smart Library
Replay Premium com janela de até 365 dias, gráfico mensal e retenção de até 12 mil registros por conta no aparelho; dados já descartados não são recuperados. Replay Free preserva limite de 90 dias. Smart Library analisa playlists online da conta, identifica referências repetidas e versões possíveis, propõe coleções por gêneros inferidos, exige seleção e confirmação Premium antes de copiar; originais permanecem. Testes adicionados. Together pendente. Compilação e teste em dispositivo pendentes. Sem alterações de cobrança ou produção.

## 0.9.82-dev — Together (integração em preparação)
Interface Android de salas privadas, fila por votos, convite por código de 128 bits, criação Premium e participação gratuita com Google; consultas a cada 15 segundos com diálogo aberto. Reprodução individual; sem áudio sincronizado. SQL de staging em supabase/setup/nero_together_draft.sql, não aplicado. Limites: 12 membros, 100 faixas, 24 h, 5 salas/dia por anfitrião. RPC autenticado, tabelas privadas com RLS e sem privilégios diretos; criação valida plano no servidor. Antes de habilitar em produção: teste SQL em ambiente isolado, testes de acesso cruzado, retenção/limpeza de salas expiradas, limites de abuso, teste multiaparelho e autorização de implantação. Sem mudança em banco, pagamentos ou credenciais. Compilação pendente.

## 0.9.83-dev — compartilhamento independente do servidor
Corrige o bloqueio do diálogo: WhatsApp/redes sociais compartilham metadados; Status/Stories exportam imagem sem depender de link protegido. Ações de link protegido continuam separadas, com login e expiração, desabilitadas quando o host/auth não estão configurados; não há URL inventada nem redirecionamento à fonte. Diálogo rolável. Testes de legenda adicionados. Consulta somente leitura ao Supabase ativo não encontrou os RPCs NERO de compartilhamento; não foi confirmada correspondência com a configuração do APK, não houve alteração de infraestrutura. Links reais continuam pendentes de configuração e validação do backend e domínio. Compilação e testes de Android pendentes.

## 0.9.84-dev — compartilhamento normal
Remove a criação e os botões de links protegidos. Compartilhar música usa o seletor Android, título, artista e link público real da fonte quando disponível. Copiar link ou texto local, card de Stories sem áudio, playlist completa como arquivo JSON importável no NERO. Sem exigência de login para enviar; importação usa a biblioteca ativa, guest ou conta. Mantém leitura de links antigos apenas por compatibilidade com links já emitidos, sem criar novos. Não inventa domínio NERO nem modifica servidor ou credenciais. Compilação e teste em aparelho pendentes.

## 0.9.85-dev — preparação para Premium
Tela de planos mostra estado da assinatura consultado do servidor, validade, restauração e solicitação de trial (somente conta Free verificada). SQL de staging para ativação única em 168 horas, não aplicado. Contratação paga continua bloqueada; WPVibe não possui site conectado, não foi possível inventariar WooCommerce/Efí. Sem alteração de produção, preços, credenciais ou cobrança. Build e teste em ambiente isolado pendentes.

## 0.9.90-dev — acabamento funcional
Together guarda a última sala somente em memória do processo para retomada após navegação na mesma conta; reconsulta no servidor, permite esquecer localmente sem encerrar a sala, não transfere o código entre usuários. Reiniciar o processo pode exigir o convite novamente. Sem áudio sincronizado; RPC de staging ainda não aplicado. Planos e Minha Conta mostram aviso de vencimento em até três dias ou 24 horas com base no vencimento retornado pelo servidor; não é notificação em segundo plano nem concessão local. Radar não inicializa ou atualiza baseline parcial após falhas de consulta e remove notificação existente quando desabilitado. Teste unitário de limites de aviso adicionado. Mantém checkout 0.9.88 e consulta ON_RESUME 0.9.89. A análise do Core 2.8.4 confirma que a rotina NERO permanece bloqueada para venda; o espelho financeiro não está implantado. O modelo Brother usa email de cobrança associado à conta Google verificada, mas o Core analisado não demonstra prova de titularidade do email WooCommerce. Não copiar essa lacuna nem introduzir etapa manual. Necessário validar mecanismo já existente de autenticação do comprador, reconciliação de pedidos e sandbox. Nenhuma alteração ao site, banco ou pagamentos. CI e aparelho pendentes.

## 0.9.91-dev — acabamento do núcleo musical
Fila: salva com a identidade capturada na abertura do diálogo, fecha ao trocar de conta ou perder identidade, bloqueia cliques repetidos durante o salvamento e confere a identidade antes da gravação. Arquivos locais continuam na biblioteca local conforme arquitetura existente. Letras sincronizadas: alternância explícita entre acompanhar música e leitura livre, busca binária do verso ativo com testes de seek e timestamps iguais; mantém importação, fonte incorporada, busca existente e controles do player. Não altera fontes, catálogo, preços, checkout ou backend. Compilação e teste de aparelho pendentes. A decisão vigente é finalizar o núcleo funcional antes de publicar a oferta comercial; integração financeira permanece em homologação.

## 0.9.92-dev — cancelamento de downloads
A biblioteca Downloads agora oferece cancelamento explícito de uma transferência ou lote ativo, usando o Job da operação e bloqueando início concorrente pelo mesmo OfflineManager. O lote verifica cancelamento entre faixas. Operações existentes limpam temporários em finally; downloads já concluídos não são removidos. A leitura de rede bloqueante pode demorar até o timeout antes de encerrar; não é pausa/retomada de bytes. Nenhuma alteração na fonte de áudio, catálogo, checkout ou backend. Compilação e testes de cancelamento real no Android pendentes. A 0.9.91-dev passou no CI debug e assinatura release, run 45769474823.

## 0.9.93-dev — recuperação explícita do player
Botão Tentar novamente no player imersivo e na tela principal após erro. A ação verifica erro atual, estado idle, mídia atual e comandos Media3, prepara e reproduz a mesma fila, sem substituir itens ou programar repetição automática. A recuperação do vídeo em tela cheia continua com os controles existentes. Cancelamento explícito recarrega downloads concluídos antes de liberar outra operação; remoção bloqueada durante o Job. Sem mudanças em catálogo, checkout, produção ou credenciais. Compilação e testes de aparelho pendentes.

## 0.9.94-dev — estabilidade da sincronização da biblioteca
Histórico de uma mesma faixa ouvido em dois dispositivos usa o registro com timestamp mais recente, sem bloquear toda a biblioteca por conflito de horários. Conflito entre exclusão e nova escuta, metadados incompatíveis com timestamp igual e conflitos de playlists permanecem explícitos. Operações de coleção sem mudança não regravam o cache nem disparam novos envios; pull idêntico não regrava o cache. Revalidação da conta ativa e identidade antes de aplicar respostas e gravar sincronização. Testes adicionados para registro mais recente nas duas direções e preservação de conflito exclusão/escuta. Nenhuma alteração no schema, catálogo ou comércio. CI e homologação multiaparelho pendentes. Backend remoto ainda não verificado como operacional.

## 0.9.95-dev — política Free/Premium
Implementada política de reprodução aprovada nesta conversa: listas aleatórias obrigatórias para Free, início aleatório de listas, bloqueio de seek, anterior/próxima, repetição e ordenação manual. Mantém play/pause, seleção de conteúdo, biblioteca e favoritos. MediaSession restringe comandos por autorização verificada; widget e ação de repetição aplicam mesma política. Lease em memória, revalidação periódica, logout e falha de consulta voltam ao Free. Testes de política e índice inicial adicionados. Nenhuma mudança de preços, pagamentos, banco ou produção. Testes CI, Android real, Bluetooth, Android Auto, Cast, PiP, vídeo fullscreen, downgrade, sessão offline e retorno do checkout pendentes. A escolha de uma nova faixa pela busca permanece permitida; não equivale a impedir toda troca de música. Não anunciar paridade exata com o Spotify atual: regra específica NERO aprovada.

## 0.9.96-dev — referência visual do proprietário
Player reorganizado conforme captura Spotify fornecida: capa quadrada ampla, cabeçalho de origem compacto, progresso branco visível mesmo quando não interativo, linha principal aleatório/anterior/play-próxima/timer, controles secundários dispositivos/repetição/equalizador/compartilhar/fila. Mantém fundo e temas NERO, recursos de vídeo, letras, downloads, favoritos, compartilhamento e rádio. No print a próxima faixa aparece disponível, mas a decisão explícita anterior do proprietário bloqueia próxima/anterior no Free; mantida política NERO e ambos ficam cinza. Corrigido erro de compilação 0.9.94/95 na chamada merge de artistas (parâmetro key nomeado), identificado nos logs 139748477284. Sem publicação, mudanças comerciais ou banco. Build e validação visual no Android pendentes.

## 0.9.97-dev — próxima faixa no Free
Libera comandos Media3 de próxima faixa na política central, widget e callbacks do player, mantendo anterior e seek bloqueados. Próxima branca no layout, anterior cinza. Testes de permissão atualizados. Lint 0.9.96 relatou UnsafeOptInUsageError pelo marcador UnstableApi em todo NeroPlaybackAccess; substituído por opt-in localizado nas duas funções de comandos que usam API Media3 experimental, sem propagar marcador aos consumidores estáveis. Não desabilitar lint. Build 0.9.96 compilou e passou unit tests, mas não foi aprovado no lint. Novo CI e homologação de dispositivo ainda pendentes. Sem alterações comerciais.

## 0.9.98-dev — consistência dos controles
Player principal, miniplayer e vídeo em tela cheia compartilham disponibilidade de próxima faixa derivada do MediaController; próxima fica disponível para Free e Premium se houver próxima mídia e comando permitido. No vídeo em tela cheia o Free também vê anterior cinza e progresso sem interação, mantendo play/pause, timer existente no player e demais recursos. Não altera a política de seleção, fila aleatória, catálogo ou backend. 0.9.97 estava em execução no momento da revisão; build da nova versão e teste Android/Bluetooth/Cast ainda pendentes. Sem mudança comercial.

## 0.9.99-dev — fechamento do núcleo de biblioteca
Corrige conflito desnecessário ao renomear a playlist em um dispositivo e alterar faixas em outro. Mantém conflitos verdadeiros sem sobrescrita. Testes de merge bidirecional, ordem e conflitos. 0.9.97 debug confirmado com sucesso, signed cancelado por versão posterior. 0.9.98 em CI no início desta revisão. 0.9.99 ainda não homologada. Decisão de congelamento: não adicionar funcionalidades fora do escopo antes de um candidato assinado e teste real de Free/Premium, player, biblioteca, offline, login e sincronização. Pendências servidor: identificar projeto NERO configurado e verificar RPCs; Together/trial/comércio permanecem drafts, sem publicação ou modificação do banco. Exportação Spotify real ainda depende do arquivo do usuário. Não chamar 1.0.0 comercial antes de verificar pagamento e entitlement com a estrutura existente, teste de aparelho e autorização de publicação.

## 26/11/2026 — backend NERO autorizado no projeto confirmado
Projeto confirmado pelo proprietário: dwpcddiramxlhavdmmyn. Pré-verificação não encontrou objetos NERO. Migrações aditivas aplicadas e confirmadas no histórico: 20261126072708 nero_isolated_cloud_and_plan_read_v1 e 20261126072734 nero_trial_and_together_v1. SQL aplicado preservado em supabase/deployed (não reexecutar). Cloud em schema privado, RPCs mantêm contrato Android, escrita somente via função com revisão e validação de envelope. Plano somente leitura por titular; trial de 168h uma vez por UID; Together valida Premium/trial/carência elegível para anfitrião e regras de membros. Verificação de metadados: cinco RPCs públicas security invoker, authenticated execute, anon sem execute; RLS em todas as sete tabelas novas; nenhum acesso direto authenticated às seis tabelas privadas; nenhuma escrita direta authenticated nos planos. Consulta administrativa sem sessão retorna plano free e biblioteca revision 0. Advisor INFO rls_enabled_no_policy em estrutura privada deliberadamente sem acesso direto. Nenhum teste com conta real, nenhum pedido, nenhum pagamento, nenhuma autorização comercial concedida manualmente. Primeira tentativa de migração falhou por delimitador SQL; pré-verificação confirmou zero objetos antes da tentativa corrigida. Não alterar schemas de outros apps ou configurações de Auth. 0.9.99-dev compilação debug e candidato assinado concluídos: run 45774713197 artifact 15095139769. Ainda testar login, sincronização entre dispositivos, trial, Together e Free/Premium no Android. Backend financeiro Core e recebimento de snapshots permanecem não implantados; publicação só após homologação.


## 0.9.104-dev — fechamento funcional, sem publicação
0.9.102-dev: candidato assinado concluído (run 45781256664, artifact 15099115435); proprietário informou aprovação do restante do app após teste e solicitou simplificar compartilhamento e normalizar créditos do Status. 0.9.103-dev incorpora essas correções; execução 45783182371 ainda deve ser confirmada antes de substituir seu APK. Backend de links instalado com migração 20261126080719; plugin App Links instalado segundo relato do proprietário; domínio configurado no Android, verificação HTTP e abertura em segundo aparelho ainda pendentes. Nesta revisão Together recebe diálogo rolável, bloqueio da fila desatualizada após erro, nova checagem de identidade no play e mensagens de recuperação. Privacidade informa metadados dos links, acesso autenticado, salas e elegibilidade; não afirma remoção automática dos registros expirados. Atualiza descrição do trial implantado sem conceder acesso local. Termos definitivos, canal de exclusão, retenção efetiva, teste multiaparelho, Bluetooth/Cast/Auto e integração comercial continuam pendentes. NERO Connect remoto não tem implementação presente na árvore desta branch, somente documentação histórica; não anunciar como funcional. Nenhum pagamento, alteração de banco, credencial, publicação ou habilitação de checkout nesta revisão.


## 1.0.0 — fechamento técnico para distribuição pelo proprietário
- Baseline 0.9.108-dev: candidato assinado e CI aprovados (run 45787687406, artifact 15101631571). Proprietário confirmou funcionamento no seu Android. Não extrapolar essa aprovação a todos os dispositivos ou cenários não demonstrados.
- Testes comerciais isolados aprovados no PostgreSQL descartável (run 45787916752), preservados no PR 7. Simulações de trial, ativação, renovação, duplicidade, revisão antiga, revogação e isolamento de contas; nenhuma cobrança real.
- Decisão explícita do proprietário de 26/11/2026: catálogo/licenciamento resolvido segundo sua confirmação; checkout existente do App Commerce Core, igual ao Brother Matrizes e Bíblia EBD, será disponibilizado após cadastro/publicação do NERO no WordPress pelo proprietário. Não bloquear o empacotamento Android por falta da página comercial publicada; não trocar gateway, preços ou produtos. APK continua consultando autorização do servidor, sem conceder Premium por retorno do navegador. Comunicação e concessão efetiva pós-pagamento ainda não verificadas em produção. Não implantar o draft de snapshots ou alterar dados financeiros nesta etapa.
- VersionName 1.0.0, versionCode 164, mesmo applicationId, certificado e configuração de autenticação. Recursos aprovados preservados. O pipeline assinado gera APK não debuggable, relatório de certificado, versão, commit e SHA-256. A compilação desta revisão deve concluir com sucesso antes de disponibilizar o pacote. Publicação no site é ação separada do proprietário.
- Homologação adicional recomendada após publicação: checkout por plano, confirmação do Premium, renovação/estorno, links HTTPS em segundo aparelho e controle Connect/Cloud/Together entre aparelhos. Não apresentar essas verificações como executadas sem evidência.

## Build 171 — NERO DJ e rotas de áudio
Seleção inicial neutra, alternância/desmarcação de estilo e bloqueio de mix sem pedido ou estilo; testes unitários acrescentados. Tela de dispositivos consulta rotas de mídia do Android durante sua exibição, oferece troca explícita apenas nas rotas expostas pela plataforma, confirma seleção por leitura posterior e mantém seletor nativo e reconexão Bluetooth como alternativas. Sem permissões adicionais, APIs ocultas, manipulação de áudio de chamada, interrupção intencional da fila ou alteração do Firebase. Teste físico Bluetooth → telefone → Bluetooth pendente; disponibilidade de rotas depende da versão e fabricante Android.

## Build 172 — ampliação de gêneros
44 categorias (12 existentes + 32 novas), artistas de referência e aliases normalizados em lista compartilhada por pesquisa, lançamentos, Radar e NERO DJ; a seleção de gênero no DJ prioriza a correspondência mais específica. Tags de lançamentos são termos de consulta ao MusicBrainz, não garantia de cobertura; gêneros com tags iguais podem consultar os mesmos lançamentos. A classificação é curada por artista e não certifica o gênero de cada faixa. Nenhuma alteração no catálogo de áudio, planos, Firebase ou pagamentos. Testes de regressão adicionados; CI e validação em aparelho pendentes.

## Build 173 — vitrine de gêneros e identidade Premium
Corrige a lista fixa de 12 cards em PremiumGenres, compartilhada pela Home e Busca: 12 gêneros prioritários, botão para exibir as 44 categorias, títulos em até duas linhas. Imagens consultadas somente para a vitrine inicial; demais cards usam acabamento gráfico sem exigir 32 consultas extras. Nome Premium NERO Music na tela comercial e navegação. Modal de catálogo descreve experiência independente e fontes externas, sem afirmar propriedade do acervo. Não altera fornecedores, atribuições exigidas, checkout, regras Free/Premium, Firebase ou reprodução. Teste em aparelho pendente.

## Build 174 — Explore álbuns
Substitui consulta fixa de sertanejo por seleção de seis gêneros, pelo menos quatro de uma rotação ampla, até dois influenciados por artistas seguidos/favoritos/histórico da conta ativa, sem enviar histórico individual à consulta de álbuns. Rotação diária e atualização manual, limite por artista, deduplicação por ID e título/artista, cache de consultas públicas por 30 minutos, filtros dos 44 gêneros e página dedicada com retorno ao álbum. Separa resultados sem data verificada de lançamentos com datas registradas no MusicBrainz. Sem alterar origem de reprodução, Firebase, assinatura, planos ou checkout. Cobertura real depende da fonte; testes em aparelho pendentes.
