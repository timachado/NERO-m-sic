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
