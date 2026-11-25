# NERO Music — direção 0.9.42.1-dev

Versão focada em música: Início, Buscar e Biblioteca (músicas curtidas e downloads dentro da Biblioteca). Preserva reprodução padrão Media3, busca, playlists e importação, letras, rádio, recomendações, histórico, clipes, equalizador e widget. Bluetooth continua pela saída de áudio do Android. Android Auto (músicas locais, downloads e favoritos offline) e Google Cast (faixas compatíveis de áudio livre) foram restaurados em debug, com testes físicos pendentes. NERO Identify, Connect, Together, backup Cloud experimental e efeitos de áudio experimentais permanecem retirados do código ativo; o snapshot anterior está em `archive/experimental-0.9.41.3`. Nenhuma biblioteca ou credencial foi apagada. O histórico de versões abaixo descreve experimentos passados, não a disponibilidade atual. Compilação e testes físicos da nova versão pendentes.

# NERO Music

O som da sua liberdade.

Aplicativo Android da T.I. Machado. Identidade Aurora Neon.

## 0.2.0-dev
- Biblioteca local, busca local, favoritos, Media3 e player em segundo plano.
- Home de descoberta, gêneros, pesquisa de áudios musicais no Wikimedia Commons com filtro conservador de licença, créditos e reprodução online.
- Busca no YouTube aberta por link no cliente oficial/navegador; não extrai áudio nem baixa vídeos.
- Downloads do catálogo aberto no armazenamento privado do app, com metadados de licença e reprodução offline.
- Catálogo comercial, grade de álbuns, busca YouTube interna e playlists próprias ainda pendentes.
- APK de teste via Actions; a nova versão precisa compilar e ser validada em aparelho real.

## 0.2.1-dev
- Pesquisa direcionada ao YouTube Music oficial por deep link, com fallback para navegador, sem extração de áudio.
- Busca do catálogo aberto e downloads autorizados mantidos.
- Cores explícitas Aurora Neon na navegação inferior.
- Resultados internos do YouTube Music e downloads desse serviço não implementados; downloads oficiais são gerenciados pelo próprio YouTube Music.

## 0.3.0-dev
- Exploração interna de metadados por músicas, álbuns e artistas, disponível apenas em builds debug, para avaliação não comercial do MusicBrainz. A API pública gratuita não é habilitada em release.
- Grade de álbuns e consulta de capas via Cover Art Archive quando disponíveis; as capas pertencem aos respectivos titulares.
- Limite de uma solicitação por segundo e cache de consultas em memória.
- Reprodução de resultados comerciais direcionada ao YouTube Music oficial. Não há streaming nem download de faixas comerciais pelo NERO.
- Catálogo aberto, downloads autorizados e player local mantidos.
- Uso comercial da API e dos conteúdos depende das permissões e condições aplicáveis.

## 0.3.1-dev
- Home com carrossel de álbuns e seleção editorial por artista, somente no build debug; não representa ranking nem recomendação personalizada.
- Busca online na aba Buscar, com filtro para manter a pesquisa local.
- Atalhos de catálogo com termo pré-preenchido e cartões de gêneros Aurora Neon.
- Preserva o catálogo aberto e downloads autorizados; YouTube Music permanece externo. Lançamentos reais, personalização e catálogo comercial integrado ainda pendentes.

## 0.4.0-dev
- Playlists persistentes de faixas locais: criar, renomear, adicionar, retirar, excluir lista sem apagar áudio e reproduzir em fila.
- Histórico das últimas 100 faixas distintas reproduzidas no serviço Media3, inclusive em segundo plano; reprodução direta de registros locais disponíveis.
- Histórico e playlists armazenados no dispositivo, sem conta ou servidor. Histórico de reprodução externa no YouTube Music não é capturado. Playlists de conteúdo online ainda pendentes.
- APK de desenvolvimento, sujeito a compilação e teste em dispositivo.

## 0.5.0-dev — conta NERO
- Tela Minha conta, acesso como visitante, cadastro e login por e-mail/senha, recuperação de senha, verificação de e-mail, login Google via Credential Manager, logout e exclusão da própria conta (pode exigir reautenticação).
- Firebase Auth é opcional até configuração real; nenhum login simulado. Dados musicais permanecem locais; sincronização não implementada.
- Para habilitar: crie projeto Firebase exclusivo do NERO, registre Android com applicationId br.com.timachado.neromusic, habilite Email/Password e Google em Authentication, configure SHA-1/SHA-256 do certificado que efetivamente assinará o APK, baixe google-services.json atualizado. Coloque em app/google-services.json (ignorado pelo Git) ou configure o secret de Actions NERO_GOOGLE_SERVICES_JSON com o conteúdo do arquivo. Não use service accounts, chaves privadas ou credenciais administrativas no cliente. Para Google, mantenha uma chave de assinatura estável entre builds e registre as impressões do certificado; não publique uma chave de assinatura no repositório. A configuração do Firebase e os testes de login real ainda estão pendentes.

## 0.5.1-dev
- Tela informativa de planos Free/Premium acessível em Minha conta, com benefícios futuros identificados como em desenvolvimento.
- Sem checkout, preços, cobrança ou desbloqueio Premium. Distribuição direta prevista pelo site da T.I. Machado; ver docs/subscriptions.md.

## 0.6.0-dev
- Equalizador Android ligado à sessão de áudio Media3 do NERO, controles das bandas disponíveis no dispositivo, ganhos limitados a ±6 dB e predefinições Plano, Graves, Voz e Brilho.
- Ajustes salvos localmente. Efeito liberado somente no debug para validação; nenhuma assinatura ativa nem concessão Premium. Não altera áudio externo. Compatibilidade e efeito audível dependem do aparelho e precisam de teste real.

## 0.6.1-dev
- Home com Ouça novamente (faixas locais acessíveis) e Feito para você (outras faixas locais de artistas presentes no histórico ou favoritos, ordenadas por pontuação de recência e favoritos).
- Personalização por regras no dispositivo, sem envio do histórico para terceiros e sem fingir recomendações quando não há dados suficientes. Não inclui recomendações de um catálogo comercial, aprendizado de máquina ou sincronização em nuvem.

## 0.7.0-dev
- NERO Cloud: backup manual experimental no Firestore por UID, somente debug; salva metadados de favoritos locais e playlists e permite restauração por correspondência única de título/artista/duração, mesclando dados. Não copia arquivos, histórico ou IDs locais entre aparelhos.
- Regras propostas em firebase/firestore.rules; NÃO implantadas. Exige Firebase configurado e testes de isolamento; conta e backup devem ser excluídos integralmente antes da liberação comercial. Não há sincronização automática nem cobrança.

## 0.8.0-dev
- Biblioteca local por álbuns, artistas e gêneros (gêneros indexados no Android 11+), grade de capas locais e listas de faixas, com reprodução em fila.
- Capas do aparelho no player e listas, com fallback; busca local também por álbum e gênero.
- Letras no player: importação LRC sincronizado ou TXT UTF-8, destaque e rolagem pela posição real, toque na linha para buscar posição, armazenamento privado por faixa. Sem busca automática online; apenas arquivos autorizados pelo usuário.
- Preparação comercial suspensa até validação funcional. Ver docs/music-roadmap.md.

## 0.8.1-dev
- Catálogo experimental: páginas de artistas, discografia, páginas de álbuns, seleção de edição oficial, ordem das faixas e duração quando disponível. Busca de reprodução no YouTube Music externo.
- Consultas por identificadores MusicBrainz com limitação compartilhada de uma requisição por segundo. Continua somente debug, sem licença comercial de catálogo ou streaming.

## 0.8.2-dev
- NERO Transfer: seleção de arquivos Playlist*.json da exportação pessoal Spotify, prévia e confirmação, armazenamento local atômico, preservação da ordem e itens sem correspondência. Reimportação idêntica não duplica listas. Reprodução apenas de arquivos locais com correspondência única; abertura opcional do item no Spotify.
- Corrige falha lint ProduceStateDoesNotAssignValue em LocalCollections.kt com remember + LaunchedEffect. Build ainda precisa ser confirmado. OAuth direto e catálogo de áudio comercial não integrados.

## 0.8.3-dev
- NERO Transfer aceita ZIP oficial ou JSON, lê em memória com limites de tamanho descompactado, número de entradas e itens, sem extrair arquivos ao sistema. Dados não relacionados a playlists são descartados. Pacotes acima dos limites devem ser descompactados manualmente e importados em partes.
- Reprodução das faixas locais correspondentes em fila, na ordem original ou aleatória, com início por qualquer faixa disponível e contagem de itens ainda não encontrados. Referências não correspondentes são preservadas. Nenhum áudio é transferido; integração OAuth e catálogo online autorizado continuam pendentes.

## 0.8.4-dev
- NERO Transfer permite filtrar itens faltantes, pesquisar na biblioteca do aparelho e vincular ou desvincular manualmente uma faixa, após confirmação explícita. Vínculos privados são revalidados pelo identificador, URI e metadados locais; não alteram o arquivo original nem a playlist exportada. A fila preserva a ordem dos itens disponíveis.
- Busca opcional de faixa faltante abre YouTube Music oficial mediante toque do usuário. Não implica disponibilidade, reprodução integrada ou download. Nenhum OAuth, serviço comercial ou cobrança foi ativado.

## 0.8.5-dev
- NERO Radio local: fila finita de até 40 faixas selecionadas por artistas e gêneros do histórico e favoritos, ou a partir da música atual. Sem streaming externo nem promessa de rádio infinita. Não sugere faixas não relacionadas quando faltam sinais. Capas locais nas recomendações.
- Testes unitários do algoritmo de rádio adicionados ao CI; resultados dependem da nova execução.

## 0.8.6-dev
- Fila de reprodução conectada ao MediaController: lista real do serviço, faixa atual, reprodução de qualquer item, mover e remover próximas faixas, limpar próximas após confirmação; não apaga arquivos. Edição de ordem desabilitada quando o modo aleatório está ativo.
- Menu de músicas locais para adicionar ao fim ou reproduzir em seguida. Fila de sessão, não persistente após encerramento definitivo do serviço; testes em aparelho pendentes.

## 0.8.7-dev
- NERO Lyrics procura automaticamente letras USLT em arquivos locais ID3v2.3/v2.4 compatíveis, com prioridade ao LRC/TXT importado. Letras incorporadas sem tempos são exibidas como texto; marcações LRC existentes são sincronizadas. Tags com flags especiais e formatos não suportados são ignorados com segurança. Não implementa SYLT nem busca online; nenhum texto é enviado a terceiros.
- Cinco testes unitários do leitor ID3 adicionados ao CI. Formatos e experiência no aparelho ainda precisam de validação.

## 0.8.8-dev
- Seção Lançamentos experimental: consulta MusicBrainz por data inicial de publicação oficial, período de 7/30/90 dias e tags de gênero, com capas e navegação por álbum. Amostra de até 50 resultados ordenada localmente por data; não é ranking, catálogo completo nem garantia de disponibilidade. Mantém restrição DEBUG, limite de requisições compartilhado e busca externa no YouTube Music. Integração real ainda precisa de testes em aparelho.

## 0.8.9-dev
- Player imersivo Aurora Neon: capa adaptativa, título/álbum/artista, controles grandes, letras, favoritos, fila, equalizador, rádio e menu de ações. Seek confirmado ao soltar o controle. Miniplayer com capa local. Layout rolável para telas menores.
- Temporizador de sono na sessão do serviço de áudio (10–90 min), pausa ao vencer e opção de cancelar. Não persiste quando o serviço encerra; validação em aparelho necessária.
- Referência do tutorial do usuário documentada em docs/music-roadmap.md. Vídeo e Music Flow não integrados; catálogo e letras online ainda pendentes.

## 0.9.0-dev
- Playlists locais com capas reais, mosaico quando há quatro capas de faixas distintas, busca por nome, ordenação alfabética opcional, página de detalhes com contagem de faixas e duração disponível, reprodução sequencial e aleatória. Capas nas linhas de faixas. Preserva criação, edição, remoção e histórico. Dados não alterados até ações explícitas do usuário.
- Segundo tutorial do usuário (Spotify) documentado no roadmap; não foram adicionados anúncios ou fontes externas sem aprovação.

## 0.9.1-dev
- Busca unificada em NERO: cartões de gêneros, resultados locais, abas de músicas/artistas/álbuns do MusicBrainz (debug), áudio aberto com reprodução e download autorizados. Consulta com debounce e limite compartilhado. Capas quando há referência de lançamento.
- Playlists online locais: guardar referências MusicBrainz sem baixar o áudio, criar/selecionar listas, abrir pesquisa externa no YouTube Music e retirar referência mediante confirmação. Separadas das playlists locais e importadas. Não há fila de áudio comercial nem API YouTube Music ativa.

## 0.9.2-dev
- Salvar uma faixa ou as gravações identificadas da edição de um álbum diretamente em playlists online próprias. Usa identificadores reais de gravações MusicBrainz, créditos e referência opcional à capa; preserva ordem da inclusão e não duplica o mesmo ID. Capas nas listas online quando disponíveis. Compatível com referências antigas sem capa.
- Continua sendo armazenamento de metadados; abrir faixa encaminha ao YouTube Music oficial, sem integração direta da API ou streaming comercial. Importação Spotify permanece independente.

## 0.9.3-dev
- Busca principal e cartões de gêneros do início encaminham à pesquisa interna NERO, preservando o termo. Removido o redirecionamento externo como ação principal da home. A aba Tudo inclui amostra de áudio aberto e resultados locais e MusicBrainz (debug). Resultados MusicBrainz com correspondência local única por título/artista permitem reprodução interna; demais referências podem ser salvas e o link externo é opcional.
- Sem integração direta do catálogo ou áudio comercial YouTube Music. Assinatura comum Premium não comprova licença de redistribuição; eventual contrato específico deve definir direitos e interface técnica autorizada.

## 0.9.4-dev
- Salvar áudios de licença aberta da pesquisa nas mesmas playlists de referências online. Reprodução interna de faixas autorizadas ou correspondências locais únicas, em fila sequencial/aleatória; indicação explícita de disponibilidade e fonte/licença para cada áudio aberto. Preferência por cópia offline validada quando disponível. Referências sem áudio ficam preservadas; formato compatível com listas anteriores. Não ativa catálogo YouTube Music nem usa credenciais de assinatura.

## 0.9.5-dev
- Adicionar faixa diretamente do player à playlist: local ou áudio aberto autorizado, preservando as listas existentes. Referência de áudio aberto transportada nos metadados Media3, validada antes de salvar. Pesquisa local com toque para reproduzir e ação de playlist. Nenhum novo provedor de streaming ativado; compilação e testes no aparelho pendentes.

## 0.9.6-dev
- YouTube: pesquisa nativa de vídeos musicais com API oficial via backend autenticado, player de vídeo incorporado e pausa do player de áudio. Integração preparada, não ativada: endpoint e projeto Firebase ainda precisam ser configurados. Não equivale a streaming do catálogo YouTube Music. Ver docs/youtube-catalog.md.

## 0.9.7-dev
- Busca alternativa pela página oficial YouTube dentro do NERO, sem exigir configuração de servidor. Não é integração nativa de áudio; funcionamento da página e reprodução depende de validação física. Adaptador oficial existente preservado. Revisão do BEAT flow em docs/beatflow-youtube-reference.md.

## 0.9.8-dev
- Uso pessoal: busca nativa experimental no YouTube Music usando NewPipeExtractor 0.26.5 (mesma biblioteca do BEAT flow), filtro MUSIC_SONGS, resultados no NERO, reprodução de áudio disponível no Media3 e referências de faixa salvas em playlists. Resolve o endereço a cada toque; não guarda URLs temporárias, não usa credenciais, não oferece download nem contorna restrições de conta. Reproduz faixa online individual; fila automática online ainda pendente. Fonte não oficial pode falhar. Só debug; sem ativação de servidor/API oficial. NewPipeExtractor é GPL-3.0, consultar condições antes de distribuir aplicativo combinado; uso pessoal de teste solicitado. Fonte: https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.26.5. Nenhum lançamento comercial autorizado.

## 0.9.9-dev
- Correção candidata para conflito de classes Protobuf em debug; mantém integração musical experimental e Firebase. Ver docs/build-099.md. Validação pendente.

## 0.9.10-dev
- Novidades musicais na home: vitrine dinâmica de até 12 lançamentos dos últimos 30 dias encontrados no MusicBrainz. Atualiza ao abrir, com cache compartilhado de 15 min e consulta periódica enquanto a tela está ativa, botão de atualização manual e preservação dos resultados visíveis se uma nova consulta falhar. Tela completa com filtro, data da última consulta e atualização. Toque na vitrine encaminha à busca interna por título e artista; não há correspondência automática garantida nem ranking YouTube Music. Sem servidor novo, sem tarefas de fundo e sem atualização de APK por música. Prévia debug, teste em aparelho pendente.

## 0.9.11-dev
- Pesquisa e playlists online entregam fila Media3 completa de referências; resolução do áudio no loader do serviço no momento de reprodução, sem aguardar resolver todas as músicas. Cache de endereços em memória de 5 min. Busca parte da faixa selecionada e continua pela ordem dos resultados. Sem autoplay infinito ao fim da fila; falhas individuais ainda exigem ação. Mini-player com capa online. Home com hero, cards de gêneros em gradiente e sugestões de pesquisa; ainda não é parada oficial nem contém fotos nos cards. Fonte do catálogo identificada. Compilação e teste físico pendentes.

## 0.9.12-dev
- Pesquisa com resultado principal, artistas encontrados nas faixas, carrossel de músicas adicionais do primeiro artista quando disponível e navegação por toque. Gêneros da home e busca ganham miniaturas reais de faixas representativas do catálogo, com gradiente e fallback. Cache de busca de 15 minutos (até 32 consultas) reduz requisições. As sugestões são derivadas de resultados, não rankings oficiais nem artistas similares confirmados. Não substitui o provedor de metadados MusicBrainz por Spotify; ainda requer avaliação de integração. Compilação e testes físicos pendentes.

## 0.9.13-dev
- Paradas digitais Brasil: integração candidata com feed público de marketing Apple Music (50 músicas mais tocadas), posição original, capas, Top 10 em carrossel, expansão e busca interna por título/artista. Fonte indicada e link oficial. Consulta ao abrir e a cada hora quando tela ativa, cache em memória, atualização manual, erro sem dados fictícios. Endpoint não validado ao vivo durante implementação; teste Android pendente. Não é ranking Spotify/YouTube ou multicanal. Sem serviço novo.

## 0.9.14-dev
- Página NERO de artista derivada de resultados reais, com arte de faixa, lista filtrada pelo crédito exato, reprodução em fila e inclusão individual ou em conjunto nas playlists. Pesquisa, páginas de álbum e lançamentos agora encaminham ações de busca para o NERO; não garantem correspondência exata do áudio. Preserva MusicBrainz como fonte de metadados identificada, sem integração Spotify ativa. Testes unitários adicionados para normalização e deduplicação de artista. Compilação e teste Android pendentes.

## 0.9.15-dev
- Corrige colisão artistKey da 0.9.14. Busca abre diretamente no campo com teclado, sugestões online do YouTube durante digitação, prévias de até seis faixas, histórico local de termos e confirmação IME. Consulta de músicas sinaliza carregamento durante debounce. Artistas/álbuns da pesquisa principal passam a usar filtros nativos YouTube Music, com página de álbum e fila; home substitui vitrine antiga por álbuns dessa fonte. MusicBrainz permanece apenas como fallback e fonte de datas de lançamento; Spotify continua importação, não API de metadados. Compilação e testes físicos pendentes.

## 0.9.16-dev
- Remove chamadas MusicBrainz da busca e home ativas; fonte musical principal YouTube Music. Descoberta de lançamentos via consultas de álbuns por gênero e ano, cache 15 minutos, atualização automática em tela ativa e navegação à página de álbum com fila. Não é feed oficial cronológico; datas não verificadas, portanto retirados filtros de 7/30/90 dias. Paradas Apple Music, áudio aberto e importação Spotify preservados. Referências antigas mantidas, com busca interna para reencontrar músicas. Código legado MusicBrainz permanece inativo e capas antigas podem usar Cover Art Archive. Build/teste físico pendentes.

## 0.9.17-dev
- Corrige chave de abertura ausente em OnlineListsScreen (falha da 0.9.16). Marca NERO Music nos títulos de catálogo, álbuns e novidades; fonte técnica disponível nas informações do catálogo e nos componentes oficiais. Favoritos online persistidos em arquivo atômico separado, ação no player e nos resultados completos de músicas, aba Favoritos unificada com músicas locais e reprodução em fila. Preserva favoritos locais e playlists existentes. Sem sincronização em nuvem, sem mudanças no provedor. Compilação e teste físico pendentes.

## 0.9.18-dev
- Histórico online em Ouça novamente; mix sob demanda com até três artistas do histórico e favoritos, sem transmitir arquivo de histórico. Rádio online por consulta ao artista atual e exclusão de IDs repetidos. Autoplay online opcional (desligado por padrão) no menu do player: ao terminar a fila e sem repeat ou temporizador ativo, busca até dez faixas adicionais, evita IDs do histórico e respeita mudanças de fila e pausa. Não é recomendação oficial Spotify nem ranking; baseada em buscas. Testes de compilação e reprodução física pendentes.

## 0.9.19-dev
- Consulta de letras LRCLIB ao abrir a tela, apenas debug de uso pessoal, com prioridade para arquivo importado e letra incorporada. Correspondência estrita de título, artista e duração (±2 s), prefere LRC sincronizado, sem persistir conteúdo remoto. Envia título/artista da faixa consultada, sem áudio ou histórico. Erros de acesso e ausência tratados, importação mantida. Disponibilidade e direitos de conteúdo não garantidos pela API; sem chave incorporada, sem ativação comercial. API pode exigir credencial conforme configuração vigente. Testes unitários de correspondência adicionados. Build e teste de rede/dispositivo pendentes. Docs https://lrclib.net/docs.

## 0.9.20-dev
- Compartilhamento nativo Android pelo player: texto/link público de origem quando disponível ou PNG vertical 1080×1920 com marca NERO, capa quando disponível e título/artista. Sem áudio no card, sem postagem automática. Playlists locais, online e importadas do Spotify podem ser compartilhadas como texto (até 50 faixas) ou arquivo JSON NERO (até 1.000 referências, 1 MB); importação em Biblioteca > Online com confirmação e nova lista, sem sobrescrever existentes. Identificadores online válidos são preservados; demais referências precisam ser pesquisadas ou associadas a arquivo local. Não há link público próprio, servidor de compartilhamento ou sincronização entre aparelhos. FileProvider privado limitado à pasta de cache de compartilhamento. Compilação e teste físico pendentes.

## Compartilhamento autenticado (0.9.21-dev)
Código NERO aleatório de 128 bits; Firebase Auth Google obrigatório para criar/ler, Firestore Source.SERVER, expiração de sete dias validada pelo servidor; sem listagem pública. Qualquer usuário Google com o código pode abrir; não é restrição por e-mail. Compartilhamento por código colado em Biblioteca > Online; não é link HTTPS clicável. A arte de status continua pública, sem áudio; o código vai na legenda, que pode não ser preservada pelo app de destino. A cópia importada é local e permanece acessível após expiração. Sem envio de áudio, senhas ou tokens. Regras adicionadas ao repositório, não implantadas. Requer projeto Firebase exclusivo, Google provider, OAuth SHA e config do Android, Firestore ativo e publicação das regras. Testar com emulador: visitante e email/password negados, Google com código válido permitido, list/update negados, código expirado negado, delete apenas dono. Não ativar billing nem alterar projeto alheio. Build e validação física pendentes.

## 0.9.22-dev
- HTTPS App Links, abertura direta com autenticação Google, página de apoio preparada e correção da sintaxe Firestore. Ativação de domínio, Firebase, certificado e regras pendente. Ver docs/https-sharing.md.

## 0.9.23-dev
- Menu de compartilhamento com prévia visual NERO, copiar link HTTPS, compartilhamento nativo Android, card de Status/Stories e menu de faixa na busca. Reutiliza o link criado enquanto o diálogo permanece aberto. Ao abrir App Link, consulta automaticamente se já houver sessão Google; visitantes precisam autenticar. Lista de faixas exibida somente após leitura protegida do servidor. Compartilhamento privado por link de 7 dias, cópia estática da playlist; sem atualização colaborativa ou link público permanente. Ativação do Firebase/domínio/certificado e teste real ainda pendentes; card estático sem áudio, apps de destino podem descartar legenda.

## 0.9.24-dev
- Prévia pública mínima separada da playlist protegida, gravação em lote, regras de acesso por link e expiração. Tela de recebimento com apresentação antes do login Google, sem IDs musicais ou áudio expostos. Página web genérica preservada. Configuração/implantação Firebase e validação pendentes. Ver docs/public-share-preview.md.

## 0.9.25-dev
- Biblioteca abre na lista unificada de playlists online, locais e importadas, com músicas curtidas em destaque, capas compostas, busca, ordem por nome, criação de playlist online e atalhos para importação e arquivos locais. Preserva dados anteriores. Navegação de vídeos com identidade NERO e identificação do player oficial nos detalhes. Compilação e teste de Android pendentes.

## 0.9.26-dev
- Android account/share now uses Supabase Auth Google and protected Supabase RPC. Native credential nonce, encrypted session, public preview only, exact link access. SQL deployment draft and setup notes added. No project selected or changed yet. Build and backend validation pending.

## 0.9.27-dev
- Paradas digitais: consulta à playlist oficial Top 100 músicas — Brasil do YouTube Music Global Charts, preservando a ordem de origem, com cache de uma hora e atualização manual. Usa o extrator público já existente no debug; fonte identificada, sem ranking inventado. Disponibilidade no dispositivo e build pendentes. O release não inclui o extrator experimental.

## 0.9.28-dev
- Player com origem da fila em metadados MediaItem, cabeçalho clicável, prévia de letra, músicas do álbum confirmado por ID e sugestões por artista; correção de paginação das paradas. Build e teste físico pendentes. Não altera playlists salvas.

## 0.9.29-dev
- Busca destaca a faixa atual por ID exato, com indicação verde e controle pausa/continuar sem recriar a fila. Toque na faixa ativa abre o player. Card Tocando agora usa estado real da sessão. Remove aba Vídeos, bloco de busca de vídeos e navegação web/embedded do fluxo de pesquisa. Mantém a reprodução musical nativa experimental, as informações da fonte/licença e os recursos existentes; não implementa vídeo nativo. Build e teste Android pendentes.

## 0.9.30-dev
- Native music video mode in debug: Clips search uses music-video catalog filter; selected clip opens Media3 PlayerView in NERO, no browser or external app. Music/video toggle for same recording preserves timeline index, position and play intent using replaceMediaItem. Audio and video use separate resolver cache keys. Video limited to compatible publicly accessible muxed MP4 (prefer <=720p); no video-only merging, no restricted stream or download, no automatic match of official video when recording differs. A source may provide static artwork or no compatible video; return to Music on failure. Video autoplay extension disabled at queue end; audio autoplay preserved. Build/device/network validation pending.

## 0.9.31-dev
- Tela cheia imersiva de clipes em paisagem, restauração da orientação e barras ao sair, controles de reprodução e seleção de limite de qualidade por faixa (Auto até 720p, 360/480/720/1080p). Escolhe melhor MP4 muxado compatível até o limite; não garante disponibilidade de 1080p nem adaptação dinâmica de bitrate. Mantém índice, posição e metadados da fila ao trocar qualidade, com cache separado. Mesmo MediaController; sem player externo. Build e validação física pendentes.

## 0.9.32-dev
- PiP explícito via botão em vídeo normal e tela cheia, usando a mesma sessão Media3 e renderização isolada no modo flutuante; preserva player aberto ao voltar. Depende de suporte/permissão do Android. Cores dinâmicas extraídas de miniatura local/remota, com fundo escuro, acentos Aurora Neon fixos e transição de 500ms. Sem mudanças de catálogo, autenticação ou dados. Build e testes físicos pendentes.

## 0.9.33-dev
- Paradas permanecem no NERO: reproduz faixa pelo ID original e fila das posições disponíveis; botão externo substituído por explicação interna da fonte. Perfil do artista com avatar e descrição fornecidos pelo perfil correspondente no catálogo, busca por nome exato único e cache de 15 minutos; sem biografia inventada, sem capa de álbum apresentada como retrato, sem ouvintes mensais inventados. Página completa acessível a partir do player e pesquisa, com álbuns atribuídos ao artista e faixas. Builds e teste real pendentes.

## 0.9.34-dev
- Download pelo player para áudios com licença aberta e origem validada, progresso, lista Offline com tamanho, fila local e atalho de descoberta. Download protegido por mutex e cancelamento cooperativo; mantém armazenamento existente. Faixa local indicada como já disponível, sem duplicar arquivos. Catálogo experimental YouTube não possui autorização de download no NERO; não armazena suas mídias. Busca por voz via reconhecedor do Android, consulta a partir da transcrição; serviço de reconhecimento pode processar voz conforme configuração do aparelho. Não é reconhecimento de música ambiente. Build e teste físico pendentes.


## 0.9.35.1-dev — NERO Audio transições
- NERO Audio acrescenta crossfade experimental de 0–8 s (0 por padrão), com dois decodificadores apenas para pares de arquivos locais compatíveis. A sessão principal mantém fila, metadados e foco; a secundária pré-carrega a próxima faixa e é liberada no retorno à sessão principal. Pausa, seek, mudança de fila, falha e perda de foco interrompem a sobreposição. Não funciona em streaming, clipes, reprodução aleatória, repetição, temporizador, equalização ou nivelamento ativos. O handoff pode causar pequena descontinuidade e exige validação física. Gapless usa a fila padrão do Media3; não é um efeito novo nem garantia de reprodução sem pausas.
- Testes unitários da curva complementar; verificar em Android com arquivos locais: 0/3/8 s, troca de faixa, pausa, seek, fila editada, retirada de fones, modo avião, retorno do player e uso de áudio em segundo plano. Testar duas faixas consecutivas de um álbum contínuo com crossfade desligado para avaliar gapless.


## 0.9.36-dev — NERO Identify
- Busca inclui botão de reconhecimento musical separado da pesquisa por voz. Somente debug: captura explícita de oito segundos em primeiro plano, permissão Android, PCM mono 16 kHz em memória, envio HTTPS ao AudD com credencial pública de demonstração documentada. Sem gravação permanente nem captura contínua. Mostra título, artista e álbum retornados, com busca no NERO; não inventa resultados. Cancelar/fechar interrompe a captura; uma requisição já iniciada pode terminar no provedor. Não é integração comercial, a cota de teste é compartilhada e pode estar indisponível. Não inserir tokens privados no APK. Produção exige serviço autenticado com segredo protegido, cotas, política de privacidade e aprovação de custos. Documentação: https://docs.audd.io/ . Testes unitários do WAV; testar permissão, recusa, cancelamento, identificação, ausência de resultado e rede em aparelho real. Nenhum backend ou pagamento ativado.


## 0.9.37-dev — Mix & Replay
- Replay registra tempo estimado de reprodução da sessão principal em intervalos de 5 segundos, localmente, sem contar pausas ou seek; intervalos curtos e a faixa auxiliar de crossfade podem não ser contabilizados. Não recupera minutos antigos. Rankings de faixas e artistas, filtros 7/30/90 dias, opção de desligar e limpar, retenção de até 90 dias na consulta, poda e limite de 3.000 registros. Limpar histórico também limpa Replay. Mix prioriza artistas mais ouvidos em 30 dias, complementa por favoritos/recentes e exclui referências ouvidas; consulta apenas nomes de artistas quando solicitado. Não utiliza IA generativa nem promete recomendações de humor ou retrospectiva anual. Testes unitários dos rankings, agrupamento e ordenação; compilação e uso físico pendentes.


## 0.9.38-dev — NERO Widget
- Widget Android de tela inicial redimensionável com identidade Aurora Neon, título/artista da faixa atual, reprodução/pausa, anterior/próxima e atalho para o app. Usa exclusivamente a sessão Media3 ativa, sem segundo player e sem iniciar reprodução em sessão vazia. Sem sessão, abra o aplicativo e escolha uma faixa. Atualização orientada por eventos de reprodução, sem polling. Testar adição pelo launcher, atualização de faixa, pausa, fila, redimensionamento e encerramento do serviço em aparelho real. Chromecast, Android Auto e controle remoto entre dispositivos ainda não implementados.


## 0.9.38.1-dev — NERO Auto
- Prévia Android Auto (debug): MediaLibraryService sobre o mesmo player, biblioteca de músicas locais, downloads validados e favoritos disponíveis offline. Seleção por identificador conhecido; não aceita URI arbitrária de controladores externos. Sessão restrita ao UID do app ou controladores confiáveis pelo Android. Serviço exportado e descriptor do carro apenas no debug. Sem clipes na biblioteca do carro, sem interface personalizada ou reprodução de vídeo no painel. Até 1.500 itens por origem, com paginação. Busca por voz no carro, playlists completas e streaming no carro ainda não implementados. Validar conexão, permissões, fila, seleção e reprodução com DHU/central compatível e veículo parado; distribuição direta pode exigir configuração de desenvolvedor para testes. Sem certificação Android Auto nem aprovação comercial. Referência: https://developer.android.com/media/implement/surfaces/cars


## 0.9.38.2-dev — NERO Cast
- Prévia isolada do Google Cast para TV compatível: seleção de receptor, envio explícito da faixa atual de áudio livre validada, pausa local, controles remotos de reprodução/pausa e encerramento. Usa SDK oficial e Default Media Receiver. A transmissão termina ao fechar a tela; retomada local manual. Não expõe arquivos locais ou privados, não envia URLs temporárias do catálogo experimental, não oferece fila remota ou espelhamento. Compatibilidade depende do formato, da rede e de acesso HTTP/CORS pelo receptor. Somente debug; compilação e teste físico pendentes. Referência: https://developers.google.com/cast/docs/android_sender/integrate
