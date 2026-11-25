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
