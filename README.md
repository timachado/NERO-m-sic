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
