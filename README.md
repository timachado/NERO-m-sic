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
